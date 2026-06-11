package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;
import xyz.acproject.danmuji.tools.db.DanmujiMigration;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MatchCountTools {
    private static final Logger LOGGER = LogManager.getLogger(MatchCountTools.class);

    private static final ConcurrentHashMap<Long, MatchRecord> matchMap = new ConcurrentHashMap<>();
    // 脏 UID 集合：仅当有变更时才在定时刷盘中写出
    private static final Set<Long> dirtyUids = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "match-csv-flush");
        t.setDaemon(true);
        return t;
    });

    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;

    static {
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(MatchCountTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "match-csv-shutdown"));
    }

    private static String roomKey() {
        Long id = PublicDataConf.ROOMID;
        return id != null ? id.toString() : "unknown";
    }

    private static String safeFileName(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String currentCsvPath() {
        String name = safeFileName(PublicDataConf.ANCHOR_NAME);
        return LogPathConf.getLogDir() + File.separator + roomKey() + "_" + name + "_5_匹配信息.csv";
    }

    public static void recordMatch(long matchedUid, String matchedName, int score) {
        matchMap.compute(matchedUid, (k, v) -> {
            if (v == null) {
                return new MatchRecord(matchedUid, matchedName, score, 1, System.currentTimeMillis());
            }
            v.matchedName = matchedName;
            v.score = score;
            v.count++;
            v.latestMatchTime = System.currentTimeMillis();
            return v;
        });
        dirtyUids.add(matchedUid);
        long now = System.currentTimeMillis();
        if (now - lastMatchNotify > 1000) {
            lastMatchNotify = now;
            xyz.acproject.danmuji.controller.DanmuWebsocket.notifyDataUpdate("match");
        }
    }
    private static volatile long lastMatchNotify = 0;

    public static List<MatchRecord> getMatchList() {
        return new ArrayList<>(matchMap.values());
    }

    public static int getMatchCount() {
        return matchMap.size();
    }

    public static void removeByUid(long matchedUid) {
        matchMap.remove(matchedUid);
    }

    public static void flushNow() {
        flushToCsv();
    }

    private static void loadFromCsv() {
        if (!loadFromSqlite(currentCsvPath())) {
            loadFromCsvLegacy(currentCsvPath());
        }
    }

    /** 从指定文件重载内存数据（合并后调用，防止旧数据覆盖合并结果） */
    public static synchronized void reloadFromFile(String path) {
        matchMap.clear();
        if (!loadFromSqlite(path)) {
            loadFromCsvLegacy(path);
        }
    }

    private static boolean loadFromSqlite(String csvPath) {
        String filename = new File(csvPath).getName();
        String[] info = DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return false;
        long roomId;
        try { roomId = Long.parseLong(info[0]); } catch (NumberFormatException e) { return false; }
        String anchorName = info[1];

        String sql = "SELECT matched_uid, matched_name, score, count, latest_match_time FROM match_summary WHERE room_id = ? AND anchor_name = ?";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, roomId);
            ps.setString(2, anchorName);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    matchMap.put(rs.getLong("matched_uid"),
                        new MatchRecord(rs.getLong("matched_uid"), rs.getString("matched_name"),
                            rs.getInt("score"), rs.getInt("count"), rs.getLong("latest_match_time")));
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("load match from SQLite failed, fallback to CSV: {}", e.getMessage());
            return false;
        }
    }

    private static void loadFromCsvLegacy(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                MatchRecord record = parseLine(line);
                if (record != null) matchMap.put(record.matchedUid, record);
            }
        } catch (Exception e) { LOGGER.error("load match CSV failed", e); }
    }

    private static MatchRecord parseLine(String line) {
        if (line == null || line.isEmpty()) return null;
        try {
            List<String> fields = parseCsvLine(line);
            if (fields.size() < 5) return null;
            String timeStr = fields.get(0);
            long uid = Long.parseLong(fields.get(1));
            String name = fields.get(2);
            int score = Integer.parseInt(fields.get(3));
            int count = Integer.parseInt(fields.get(4));
            long time = JodaTimeUtils.parse(timeStr, "yyyy-MM-dd HH:mm:ss").getTime();
            return new MatchRecord(uid, name, score, count, time);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString());
        return fields;
    }

    private static synchronized void flushToCsv() {
        String rk = roomKey();
        if ("unknown".equals(rk)) return;
        if (!rk.equals(lastRoomId)) {
            String oldPrefix = lastRoomId + "_" + lastAnchorName;
            String oldPath = LogPathConf.getLogDir() + File.separator + oldPrefix + "_5_匹配信息.csv";
            doFlush(oldPath);
            matchMap.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv();
        }
        doFlush(currentCsvPath());
    }

    private static void doFlush(String path) {
        // 无变更时跳过，避免无意义的全量重写
        if (dirtyUids.isEmpty()) return;
        List<MatchRecord> records = new ArrayList<>(matchMap.values());
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File tmpFile = new File(path + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
            writer.write('﻿');
            writer.write("最近匹配,匹配id,匹配名,匹配分,匹配次数");
            writer.newLine();
            for (MatchRecord r : records) {
                writer.write(JodaTimeUtils.formatDateTime(r.latestMatchTime) + ",");
                writer.write(r.matchedUid + ",");
                writer.write(escapeCsv(r.matchedName) + ",");
                writer.write(r.score + ",");
                writer.write(String.valueOf(r.count));
                writer.newLine();
            }
        } catch (Exception e) {
            LOGGER.error("flush match CSV failed", e);
            return;
        }
        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirtyUids.clear();
        } catch (IOException e) {
            LOGGER.error("move match CSV failed", e);
        }

        flushToSqlite(path, records);
    }

    private static void flushToSqlite(String csvPath, List<MatchRecord> records) {
        String filename = new File(csvPath).getName();
        String[] info = DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return;
        long roomId = Long.parseLong(info[0]);
        String anchorName = info[1];

        String sql = "INSERT OR REPLACE INTO match_summary(room_id,anchor_name,matched_uid,matched_name,score,count,latest_match_time) VALUES (?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (MatchRecord r : records) {
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setLong(3, r.matchedUid);
                    ps.setString(4, r.matchedName != null ? r.matchedName : "");
                    ps.setInt(5, r.score);
                    ps.setInt(6, r.count);
                    ps.setLong(7, r.latestMatchTime);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (Exception e2) {
                try { c.rollback(); } catch (Exception ignored) {}
                throw e2;
            }
        } catch (Exception e) {
            LOGGER.error("flush match to SQLite failed", e);
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static class MatchRecord {
        final long matchedUid;
        volatile String matchedName;
        volatile int score;
        volatile int count;
        volatile long latestMatchTime;

        MatchRecord(long matchedUid, String matchedName, int score, int count, long latestMatchTime) {
            this.matchedUid = matchedUid;
            this.matchedName = matchedName;
            this.score = score;
            this.count = count;
            this.latestMatchTime = latestMatchTime;
        }
    }
}
