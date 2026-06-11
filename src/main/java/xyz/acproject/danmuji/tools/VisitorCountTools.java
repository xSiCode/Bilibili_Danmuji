package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.http.HttpRoomData;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;
import xyz.acproject.danmuji.tools.db.DanmujiMigration;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VisitorCountTools {
    private static final Logger LOGGER = LogManager.getLogger(VisitorCountTools.class);

    private static final ConcurrentHashMap<Long, VisitorRecord> visitorMap = new ConcurrentHashMap<>();
    private static final Set<Long> dirtyUids = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "visitor-csv-flush");
        t.setDaemon(true);
        return t;
    });

    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;

    static {
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(VisitorCountTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "visitor-csv-shutdown"));
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
        return LogPathConf.getLogDir() + File.separator + roomKey() + "_" + name + "_4_观众信息.csv";
    }

    public static void recordVisitor(long uid, String uname, int score, String scoreType) {
        visitorMap.compute(uid, (k, v) -> {
            if (v == null) {
                dirtyUids.add(uid);
                return new VisitorRecord(uid, uname, score, scoreType, 1, System.currentTimeMillis(),
                        HttpRoomData.isUidInPnScoreMap(uid), 1);
            }
            v.uname = uname;
            v.score = score;
            v.scoreType = scoreType;
            v.count++;
            long now = System.currentTimeMillis();
            if (now - v.latestEntryTime >= 3 * 60 * 60 * 1000L) {
                v.session++;
            }
            v.latestEntryTime = now;
            v.inPnTable = HttpRoomData.isUidInPnScoreMap(uid);
            dirtyUids.add(uid);
            return v;
        });
        // 通知 WebSocket 客户端数据已更新（节流：每秒最多一次）
        long now = System.currentTimeMillis();
        if (now - lastVisitorNotify > 1000) {
            lastVisitorNotify = now;
            xyz.acproject.danmuji.controller.DanmuWebsocket.notifyDataUpdate("visitor");
        }
    }
    private static volatile long lastVisitorNotify = 0;

    public static int[] getCountAndSession(long uid) {
        VisitorRecord v = visitorMap.get(uid);
        if (v == null) return new int[]{0, 0};
        return new int[]{v.count, v.session};
    }

    private static void loadFromCsv() {
        if (!loadFromSqlite(currentCsvPath())) {
            loadFromCsvLegacy(currentCsvPath());
        }
    }

    /** 从指定文件重载内存数据（合并后调用，防止旧数据覆盖合并结果） */
    public static synchronized void reloadFromFile(String path) {
        visitorMap.clear();
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

        String sql = "SELECT uid, uname, score, score_type, count, in_pn_table, session, latest_entry_time FROM visitor_summary WHERE room_id = ? AND anchor_name = ?";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, roomId);
            ps.setString(2, anchorName);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long uid = rs.getLong("uid");
                    int session = rs.getInt("session");
                    if (session == 0) session = 1;
                    visitorMap.put(uid, new VisitorRecord(uid, rs.getString("uname"),
                        rs.getInt("score"), rs.getString("score_type"), rs.getInt("count"),
                        rs.getLong("latest_entry_time"), rs.getInt("in_pn_table") == 1, session));
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("load visitor from SQLite failed, fallback to CSV: {}", e.getMessage());
            return false;
        }
    }

    private static void loadFromCsvLegacy(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                VisitorRecord record = parseLine(line);
                if (record != null) {
                    if (record.session == 0) record.session = 1;
                    visitorMap.put(record.uid, record);
                }
            }
        } catch (Exception e) { LOGGER.error("load visitor CSV failed", e); }
    }

    private static VisitorRecord parseLine(String line) {
        if (line == null || line.isEmpty()) return null;
        try {
            List<String> fields = parseCsvLine(line);
            if (fields.size() < 6) return null;
            String timeStr = fields.get(0);
            long uid = Long.parseLong(fields.get(1));
            String uname = fields.get(2);
            int score = Integer.parseInt(fields.get(3));
            String scoreType = fields.get(4);
            int count = Integer.parseInt(fields.get(5));
            boolean inPnTable;
            if (fields.size() >= 7) {
                inPnTable = "是".equals(fields.get(6));
            } else {
                // old format compatibility: derive from current pnScoreMap
                inPnTable = HttpRoomData.isUidInPnScoreMap(uid);
            }
            int session = 0;
            if (fields.size() >= 8) {
                try { session = Integer.parseInt(fields.get(7)); } catch (NumberFormatException e) {}
            }
            long time = JodaTimeUtils.parse(timeStr, "yyyy-MM-dd HH:mm:ss").getTime();
            return new VisitorRecord(uid, uname, score, scoreType, count, time, inPnTable, session);
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
            // 房间切换：完整写出旧房间的所有记录
            String oldPrefix = lastRoomId + "_" + lastAnchorName;
            String oldPath = LogPathConf.getLogDir() + File.separator + oldPrefix + "_4_观众信息.csv";
            doFlushFull(oldPath);
            visitorMap.clear();
            dirtyUids.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv();
        }
        doFlushAppend(currentCsvPath());
    }

    private static void doFlushFull(String path) {
        List<VisitorRecord> records = new ArrayList<>(visitorMap.values());
        records.sort((a, b) -> Long.compare(a.latestEntryTime, b.latestEntryTime));
        writeCsvFile(path, records, false);
    }

    private static void doFlushAppend(String path) {
        List<VisitorRecord> records = new ArrayList<>();
        for (Long uid : dirtyUids) {
            VisitorRecord r = visitorMap.get(uid);
            if (r != null) records.add(r);
        }
        if (records.isEmpty()) return;
        records.sort((a, b) -> Long.compare(a.latestEntryTime, b.latestEntryTime));
        writeCsvFile(path, records, true);
        dirtyUids.clear();
    }

    private static void writeCsvFile(String path, List<VisitorRecord> records, boolean append) {
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File tmpFile = new File(path + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
            if (append && file.exists()) {
                // 复制原文件内容到临时文件，但跳过本次要更新的 uid 对应的旧行
                Set<Long> appendUids = new HashSet<>();
                for (VisitorRecord r : records) appendUids.add(r.uid);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                    String l;
                    boolean isHeader = true;
                    while ((l = reader.readLine()) != null) {
                        if (isHeader) { isHeader = false; writer.write(l); writer.newLine(); continue; }
                        List<String> fields = parseCsvLine(l);
                        if (fields.size() >= 8) {
                            try {
                                long rowUid = Long.parseLong(fields.get(1));
                                if (appendUids.contains(rowUid)) continue; // 跳过旧行
                            } catch (NumberFormatException e) {}
                        }
                        writer.write(l);
                        writer.newLine();
                    }
                }
            } else {
                writer.write('﻿');
                writer.write("最近,id,观众,打分,打分类型,次数,判定表,场次");
                writer.newLine();
            }
            for (VisitorRecord r : records) {
                writer.write(JodaTimeUtils.formatDateTime(r.latestEntryTime) + ",");
                writer.write(r.uid + ",");
                writer.write(escapeCsv(r.uname) + ",");
                writer.write(r.score + ",");
                writer.write(escapeCsv(r.scoreType) + ",");
                writer.write(r.count + ",");
                writer.write(r.inPnTable ? "是" : "否");
                writer.write("," + r.session);
                writer.newLine();
            }
        } catch (Exception e) {
            LOGGER.error("flush visitor CSV failed", e);
            return;
        }
        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("move visitor CSV failed", e);
        }

        flushToSqlite(path, records);
    }

    private static void flushToSqlite(String csvPath, List<VisitorRecord> records) {
        String filename = new File(csvPath).getName();
        String[] info = DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return;
        long roomId = Long.parseLong(info[0]);
        String anchorName = info[1];

        String sql = "INSERT OR REPLACE INTO visitor_summary(room_id,anchor_name,uid,uname,score,score_type,count,in_pn_table,session,latest_entry_time) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (VisitorRecord r : records) {
                ps.setLong(1, roomId);
                ps.setString(2, anchorName);
                ps.setLong(3, r.uid);
                ps.setString(4, r.uname != null ? r.uname : "");
                ps.setInt(5, r.score);
                ps.setString(6, r.scoreType != null ? r.scoreType : "");
                ps.setInt(7, r.count);
                ps.setInt(8, r.inPnTable ? 1 : 0);
                ps.setInt(9, r.session > 0 ? r.session : 1);
                ps.setLong(10, r.latestEntryTime);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            LOGGER.error("flush visitor to SQLite failed", e);
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** 返回内存中的观众数据（实时，无 CSV 读取延迟） */
    public static List<VisitorRecord> getVisitorList() {
        return new ArrayList<>(visitorMap.values());
    }

    public static int getVisitorCount() {
        return visitorMap.size();
    }

    /** 从内存中移除指定观众记录并立即刷盘（供删除操作使用） */
    public static void removeByUid(long uid) {
        visitorMap.remove(uid);
        dirtyUids.remove(uid);
    }

    /** 立即将内存全量刷入 CSV（全量覆写，确保删除操作即时持久化） */
    public static void flushNow() {
        doFlushFull(currentCsvPath());
    }

    public static class VisitorRecord {
        public final long uid;
        public volatile String uname;
        public volatile int score;
        public volatile String scoreType;
        public volatile int count;
        public volatile long latestEntryTime;
        public volatile boolean inPnTable;
        public volatile int session;

        VisitorRecord(long uid, String uname, int score, String scoreType, int count, long latestEntryTime, boolean inPnTable, int session) {
            this.uid = uid;
            this.uname = uname;
            this.score = score;
            this.scoreType = scoreType;
            this.count = count;
            this.latestEntryTime = latestEntryTime;
            this.inPnTable = inPnTable;
            this.session = session;
        }
    }
}
