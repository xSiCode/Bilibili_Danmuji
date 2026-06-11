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

public class FollowingCountTools {
    private static final Logger LOGGER = LogManager.getLogger(FollowingCountTools.class);

    private static final ConcurrentHashMap<Long, FollowingRecord> followingMap = new ConcurrentHashMap<>();
    // 脏 UID 集合：仅当有变更时才在定时刷盘中写出
    private static final Set<Long> dirtyUids = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "following-csv-flush");
        t.setDaemon(true);
        return t;
    });

    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;

    static {
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(FollowingCountTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "following-csv-shutdown"));
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
        return LogPathConf.getLogDir() + File.separator + roomKey() + "_" + name + "_6_关注人信息.csv";
    }

    public static void recordFollowing(long followedUid, String followedName) {
        followingMap.compute(followedUid, (k, v) -> {
            if (v == null) {
                return new FollowingRecord(followedUid, followedName, 1, System.currentTimeMillis());
            }
            v.name = followedName;
            v.count++;
            v.latestTime = System.currentTimeMillis();
            return v;
        });
        dirtyUids.add(followedUid);
        // 通知 WebSocket 客户端（节流）
        long now = System.currentTimeMillis();
        if (now - lastFollowNotify > 1000) {
            lastFollowNotify = now;
            xyz.acproject.danmuji.controller.DanmuWebsocket.notifyDataUpdate("follow");
        }
    }
    private static volatile long lastFollowNotify = 0;

    public static List<FollowingRecord> getFollowingList() {
        return new ArrayList<>(followingMap.values());
    }

    public static int getFollowingCount() {
        return followingMap.size();
    }

    private static void loadFromCsv() {
        if (!loadFromSqlite(currentCsvPath())) {
            loadFromCsvLegacy(currentCsvPath());
        }
    }

    /** 从指定文件重载内存数据（合并后调用，防止旧数据覆盖合并结果） */
    public static synchronized void reloadFromFile(String path) {
        followingMap.clear();
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

        String sql = "SELECT uid, uname, count, latest_time FROM follow_summary WHERE room_id = ? AND anchor_name = ?";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, roomId);
            ps.setString(2, anchorName);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    followingMap.put(rs.getLong("uid"),
                        new FollowingRecord(rs.getLong("uid"), rs.getString("uname"),
                            rs.getInt("count"), rs.getLong("latest_time")));
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("load following from SQLite failed, fallback to CSV: {}", e.getMessage());
            return false;
        }
    }

    private static void loadFromCsvLegacy(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                FollowingRecord record = parseLine(line);
                if (record != null) followingMap.put(record.uid, record);
            }
        } catch (Exception e) { LOGGER.error("load following CSV failed", e); }
    }

    private static FollowingRecord parseLine(String line) {
        if (line == null || line.isEmpty()) return null;
        try {
            List<String> fields = parseCsvLine(line);
            if (fields.size() < 4) return null;
            String timeStr = fields.get(0);
            long uid = Long.parseLong(fields.get(1));
            String name = fields.get(2);
            int count = Integer.parseInt(fields.get(3));
            long time = JodaTimeUtils.parse(timeStr, "yyyy-MM-dd HH:mm:ss").getTime();
            return new FollowingRecord(uid, name, count, time);
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
            String oldPath = LogPathConf.getLogDir() + File.separator + oldPrefix + "_6_关注人信息.csv";
            doFlush(oldPath);
            followingMap.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv();
        }
        doFlush(currentCsvPath());
    }

    private static void doFlush(String path) {
        // 无变更时跳过，避免无意义的全量重写
        if (dirtyUids.isEmpty()) return;
        List<FollowingRecord> records = new ArrayList<>(followingMap.values());
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File tmpFile = new File(path + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
            writer.write('﻿');
            writer.write("最新时间,id,名字,次数");
            writer.newLine();
            for (FollowingRecord r : records) {
                writer.write(JodaTimeUtils.formatDateTime(r.latestTime) + ",");
                writer.write(r.uid + ",");
                writer.write(escapeCsv(r.name) + ",");
                writer.write(String.valueOf(r.count));
                writer.newLine();
            }
        } catch (Exception e) {
            LOGGER.error("flush following CSV failed", e);
            return;
        }
        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirtyUids.clear();
        } catch (IOException e) {
            LOGGER.error("move following CSV failed", e);
        }

        flushToSqlite(path, records);
    }

    private static void flushToSqlite(String csvPath, List<FollowingRecord> records) {
        String filename = new File(csvPath).getName();
        String[] info = DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return;
        long roomId = Long.parseLong(info[0]);
        String anchorName = info[1];

        String sql = "INSERT OR REPLACE INTO follow_summary(room_id,anchor_name,uid,uname,count,latest_time) VALUES (?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (FollowingRecord r : records) {
                ps.setLong(1, roomId);
                ps.setString(2, anchorName);
                ps.setLong(3, r.uid);
                ps.setString(4, r.name != null ? r.name : "");
                ps.setInt(5, r.count);
                ps.setLong(6, r.latestTime);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            LOGGER.error("flush following to SQLite failed", e);
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static class FollowingRecord {
        public final long uid;
        public volatile String name;
        public volatile int count;
        public volatile long latestTime;

        FollowingRecord(long uid, String name, int count, long latestTime) {
            this.uid = uid;
            this.name = name;
            this.count = count;
            this.latestTime = latestTime;
        }
    }
}
