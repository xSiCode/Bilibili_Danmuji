package xyz.acproject.danmuji.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;
import xyz.acproject.danmuji.tools.file.ProFileTools;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.*;

/**
 * 本地黑白名单服务 — 内存缓存 + CSV 持久化
 *
 * 黑名单: set/本地黑名单.csv
 * 白名单: set/本地白名单.csv
 *
 * CSV 列: id, name, createTime, updateTime, score, scoreType, roomId, count
 */
public class LocalBlackWhiteListService {
    private static final Logger LOGGER = LogManager.getLogger(LocalBlackWhiteListService.class);

    // === 内存缓存 ===
    private static final ConcurrentHashMap<Long, BlackWhiteEntry> blacklistCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, BlackWhiteEntry> whitelistCache = new ConcurrentHashMap<>();
    private static final Set<Long> dirtyBlacks = ConcurrentHashMap.newKeySet();
    private static final Set<Long> dirtyWhites = ConcurrentHashMap.newKeySet();

    private static final ScheduledExecutorService flushScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "bwlist-csv-flush");
                t.setDaemon(true);
                return t;
            });

    private static volatile boolean loaded = false;

    static {
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(LocalBlackWhiteListService::flushAll, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            try { flushScheduler.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            flushAll();
        }, "bwlist-csv-shutdown"));
    }

    // ==================== CSV 路径 ====================

    private static String blackCsvPath() {
        return ProFileTools.getStoreDir() + "/set/本地黑名单.csv";
    }

    private static String whiteCsvPath() {
        return ProFileTools.getStoreDir() + "/set/本地白名单.csv";
    }

    // ==================== 公开查询 API ====================

    public static boolean isInBlacklist(long uid) {
        return blacklistCache.containsKey(uid);
    }

    public static boolean isInWhitelist(long uid) {
        return whitelistCache.containsKey(uid);
    }

    public static void incrementBlackCount(long uid) {
        BlackWhiteEntry e = blacklistCache.get(uid);
        if (e != null) {
            e.count++;
            e.updateTime = System.currentTimeMillis();
            dirtyBlacks.add(uid);
        }
    }

    public static void incrementWhiteCount(long uid) {
        BlackWhiteEntry e = whitelistCache.get(uid);
        if (e != null) {
            e.count++;
            e.updateTime = System.currentTimeMillis();
            dirtyWhites.add(uid);
        }
    }

    // ==================== 添加 API ====================

    public static void addToBlacklist(long uid, String name, int score, String scoreType, long roomId) {
        BlackWhiteEntry existing = blacklistCache.get(uid);
        long now = System.currentTimeMillis();
        if (existing != null) {
            existing.count++;
            existing.updateTime = now;
            if (name != null && !name.isEmpty()) existing.name = name;
            if (scoreType != null && !scoreType.isEmpty()) {
                existing.score = score;
                existing.scoreType = scoreType;
            }
        } else {
            blacklistCache.put(uid, new BlackWhiteEntry(uid, name, now, now, score, scoreType, roomId, 1));
            // 如果同时在白名单，移除白名单（黑名单优先级更高）
            whitelistCache.remove(uid);
            dirtyWhites.add(uid);
        }
        dirtyBlacks.add(uid);
    }

    public static void addToWhitelist(long uid, String name, int score, String scoreType, long roomId) {
        BlackWhiteEntry existing = whitelistCache.get(uid);
        long now = System.currentTimeMillis();
        if (existing != null) {
            existing.count++;
            existing.updateTime = now;
            if (name != null && !name.isEmpty()) existing.name = name;
            if (scoreType != null && !scoreType.isEmpty()) {
                existing.score = score;
                existing.scoreType = scoreType;
            }
        } else {
            whitelistCache.put(uid, new BlackWhiteEntry(uid, name, now, now, score, scoreType, roomId, 1));
        }
        dirtyWhites.add(uid);
    }

    // ==================== 删除 / 移动 API ====================

    public static void removeFromBlacklist(long uid) {
        blacklistCache.remove(uid);
        dirtyBlacks.add(uid);
    }

    public static void removeFromWhitelist(long uid) {
        whitelistCache.remove(uid);
        dirtyWhites.add(uid);
    }

    public static void moveToBlacklist(long uid) {
        BlackWhiteEntry entry = whitelistCache.remove(uid);
        dirtyWhites.add(uid);
        if (entry != null) {
            entry.count++;
            entry.updateTime = System.currentTimeMillis();
            blacklistCache.put(uid, entry);
            dirtyBlacks.add(uid);
        }
    }

    public static void moveToWhitelist(long uid) {
        BlackWhiteEntry entry = blacklistCache.remove(uid);
        dirtyBlacks.add(uid);
        if (entry != null) {
            entry.count++;
            entry.updateTime = System.currentTimeMillis();
            whitelistCache.put(uid, entry);
            dirtyWhites.add(uid);
        }
    }

    // ==================== 查询 / 分页 API ====================

    public static List<BlackWhiteEntry> getBlacklist() {
        return new ArrayList<>(blacklistCache.values());
    }

    public static List<BlackWhiteEntry> getWhitelist() {
        return new ArrayList<>(whitelistCache.values());
    }

    public static List<BlackWhiteEntry> searchBlacklist(String query) {
        List<BlackWhiteEntry> result = new ArrayList<>();
        String lower = query.toLowerCase();
        for (BlackWhiteEntry e : blacklistCache.values()) {
            if (String.valueOf(e.uid).contains(lower)
                    || (e.name != null && e.name.toLowerCase().contains(lower))
                    || (e.scoreType != null && e.scoreType.toLowerCase().contains(lower))) {
                result.add(e);
            }
        }
        return result;
    }

    public static List<BlackWhiteEntry> searchWhitelist(String query) {
        List<BlackWhiteEntry> result = new ArrayList<>();
        String lower = query.toLowerCase();
        for (BlackWhiteEntry e : whitelistCache.values()) {
            if (String.valueOf(e.uid).contains(lower)
                    || (e.name != null && e.name.toLowerCase().contains(lower))
                    || (e.scoreType != null && e.scoreType.toLowerCase().contains(lower))) {
                result.add(e);
            }
        }
        return result;
    }

    // ==================== CSV 读写 ====================

    private static synchronized void loadFromCsv() {
        if (loaded) return;
        // 优先从 SQLite 加载
        boolean sqliteOk = false;
        try {
            sqliteOk = loadFromSqlite();
        } catch (Exception e) {
            LOGGER.warn("load bwlist from SQLite failed, fallback to CSV: {}", e.getMessage());
        }
        if (!sqliteOk) {
            File blackFile = new File(blackCsvPath());
            if (blackFile.exists()) loadSingleCsv(blackFile, blacklistCache);
            File whiteFile = new File(whiteCsvPath());
            if (whiteFile.exists()) loadSingleCsv(whiteFile, whitelistCache);
        }
        loaded = true;
    }

    /** 从 SQLite 加载，成功返回 true */
    private static boolean loadFromSqlite() {
        String sql = "SELECT list_type, uid, name, create_time, update_time, score, score_type, room_id, count FROM black_white_list";
        try (Connection c = DanmujiDatabase.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                String listType = rs.getString("list_type");
                long uid = rs.getLong("uid");
                String name = rs.getString("name");
                long createTime = rs.getLong("create_time");
                long updateTime = rs.getLong("update_time");
                int score = rs.getInt("score");
                String scoreType = rs.getString("score_type");
                long roomId = rs.getLong("room_id");
                int count = rs.getInt("count");
                BlackWhiteEntry entry = new BlackWhiteEntry(uid, name, createTime, updateTime, score, scoreType, roomId, count);
                ConcurrentHashMap<Long, BlackWhiteEntry> cache = "black".equals(listType) ? blacklistCache : whitelistCache;
                BlackWhiteEntry existing = cache.get(uid);
                if (existing != null) {
                    existing.count += entry.count;
                    if (entry.updateTime > existing.updateTime) {
                        existing.updateTime = entry.updateTime;
                        existing.name = orNewer(existing.name, entry.name);
                        existing.score = entry.score != 0 ? entry.score : existing.score;
                        existing.scoreType = orNewer(existing.scoreType, entry.scoreType);
                        existing.roomId = entry.roomId != 0 ? entry.roomId : existing.roomId;
                    }
                    if (entry.createTime < existing.createTime) {
                        existing.createTime = entry.createTime;
                    }
                } else {
                    if (entry.count == 0) entry.count = 1;
                    if (entry.createTime == 0) entry.createTime = entry.updateTime;
                    cache.put(uid, entry);
                }
            }
            return hasData;
        } catch (Exception e) {
            LOGGER.error("load bwlist from SQLite failed: {}", e.getMessage());
            return false;
        }
    }

    /** 从 CSV 加载（兜底） */
    private static void loadSingleCsv(File file, ConcurrentHashMap<Long, BlackWhiteEntry> cache) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine(); // skip header (may have BOM)
            while ((line = reader.readLine()) != null) {
                BlackWhiteEntry entry = parseLine(line);
                if (entry != null) {
                    BlackWhiteEntry existing = cache.get(entry.uid);
                    if (existing != null) {
                        // 合并重复 uid: count 累加, 保留最新的 updateTime/name/score
                        existing.count += entry.count;
                        if (entry.updateTime > existing.updateTime) {
                            existing.updateTime = entry.updateTime;
                            existing.name = orNewer(existing.name, entry.name);
                            existing.score = entry.score != 0 ? entry.score : existing.score;
                            existing.scoreType = orNewer(existing.scoreType, entry.scoreType);
                            existing.roomId = entry.roomId != 0 ? entry.roomId : existing.roomId;
                        }
                        if (entry.createTime < existing.createTime) {
                            existing.createTime = entry.createTime;
                        }
                    } else {
                        if (entry.count == 0) entry.count = 1;
                        if (entry.createTime == 0) entry.createTime = entry.updateTime;
                        cache.put(entry.uid, entry);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("load bwlist CSV failed: {}", file.getAbsolutePath(), e);
        }
    }

    private static String orNewer(String oldVal, String newVal) {
        return (newVal != null && !newVal.isEmpty()) ? newVal : oldVal;
    }

    private static BlackWhiteEntry parseLine(String line) {
        if (line == null || line.isEmpty()) return null;
        try {
            List<String> fields = parseCsvLine(line);
            if (fields.size() < 1 || fields.get(0).trim().isEmpty()) return null;
            long uid = Long.parseLong(fields.get(0).trim());
            String name = fields.size() > 1 ? fields.get(1) : "";
            long createTime = fields.size() > 2 ? parseTimeSafe(fields.get(2)) : 0L;
            long updateTime = fields.size() > 3 ? parseTimeSafe(fields.get(3)) : 0L;
            int score = fields.size() > 4 ? parseIntSafe(fields.get(4)) : 0;
            String scoreType = fields.size() > 5 ? fields.get(5) : "";
            long roomId = fields.size() > 6 ? parseLongSafe(fields.get(6)) : 0L;
            int count = fields.size() > 7 ? parseIntSafe(fields.get(7)) : 0;
            if (updateTime == 0) updateTime = System.currentTimeMillis();
            if (createTime == 0) createTime = updateTime;
            return new BlackWhiteEntry(uid, name, createTime, updateTime, score, scoreType, roomId, count);
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseTimeSafe(String s) {
        if (s == null || s.trim().isEmpty()) return 0L;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(s.trim()).getTime();
        } catch (Exception e) {
            // fallback: try as raw millis
            return parseLongSafe(s);
        }
    }

    private static long parseLongSafe(String s) {
        if (s == null || s.trim().isEmpty()) return 0L;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; }
    }

    private static int parseIntSafe(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static String fmtTs(long ts) {
        if (ts <= 0) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new java.util.Date(ts));
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

    // ==================== 刷盘 ====================

    public static void flushAll() {
        flushSingleCsv(blackCsvPath(), blacklistCache, dirtyBlacks);
        flushSingleCsv(whiteCsvPath(), whitelistCache, dirtyWhites);
    }

    private static synchronized void flushSingleCsv(String path, ConcurrentHashMap<Long, BlackWhiteEntry> cache, Set<Long> dirtySet) {
        try {
            File file = new File(path);
            if (cache.isEmpty()) {
                // 缓存已清空：删除 CSV 文件，避免下次启动加载旧数据
                if (file.exists()) file.delete();
                dirtySet.clear();
                return;
            }
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

            // 收集所有条目（全量覆写以保证去重）
            List<BlackWhiteEntry> all = new ArrayList<>(cache.values());
            all.sort(Comparator.comparingLong(a -> a.createTime));

            File tmpFile = new File(path + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                writer.write('﻿'); // BOM
                writer.write("id,name,createTime,updateTime,score,scoreType,roomId,count");
                writer.newLine();
                for (BlackWhiteEntry e : all) {
                    writer.write(String.valueOf(e.uid));
                    writer.write(',');
                    writer.write(escapeCsv(e.name));
                    writer.write(',');
                    writer.write(fmtTs(e.createTime));
                    writer.write(',');
                    writer.write(fmtTs(e.updateTime));
                    writer.write(',');
                    writer.write(String.valueOf(e.score));
                    writer.write(',');
                    writer.write(escapeCsv(e.scoreType));
                    writer.write(',');
                    writer.write(String.valueOf(e.roomId));
                    writer.write(',');
                    writer.write(String.valueOf(e.count));
                    writer.newLine();
                }
            }
            Files.move(tmpFile.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirtySet.clear();

            // 同步写入 SQLite
            flushToSqlite(path, all);
        } catch (Exception e) {
            LOGGER.error("flush bwlist CSV failed: {}", path, e);
        }
    }

    private static void flushToSqlite(String csvPath, List<BlackWhiteEntry> all) {
        String listType = csvPath.contains("白名单") ? "white" : "black";
        String sql = "INSERT OR REPLACE INTO black_white_list(list_type,uid,name,create_time,update_time,score,score_type,room_id,count) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (BlackWhiteEntry e : all) {
                    ps.setString(1, listType);
                    ps.setLong(2, e.uid);
                    ps.setString(3, e.name != null ? e.name : "");
                    ps.setLong(4, e.createTime);
                    ps.setLong(5, e.updateTime);
                    ps.setInt(6, e.score);
                    ps.setString(7, e.scoreType != null ? e.scoreType : "");
                    ps.setLong(8, e.roomId);
                    ps.setInt(9, e.count);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (Exception e2) {
                try { c.rollback(); } catch (Exception ignored) {}
                throw e2;
            }
        } catch (Exception e) {
            LOGGER.error("flush bwlist to SQLite failed: {}", csvPath, e);
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ==================== 导入 ====================

    public static synchronized int importCsv(String csvContent, boolean isBlack) {
        String[] lines = csvContent.split("\n");
        ConcurrentHashMap<Long, BlackWhiteEntry> target = isBlack ? blacklistCache : whitelistCache;
        int imported = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("id,") || trimmed.startsWith("﻿id,")) continue;
            BlackWhiteEntry entry = parseLine(trimmed);
            if (entry != null) {
                BlackWhiteEntry existing = target.get(entry.uid);
                if (existing != null) {
                    existing.count += Math.max(entry.count, 1);
                    if (entry.updateTime > existing.updateTime) {
                        existing.updateTime = entry.updateTime;
                        existing.name = orNewer(existing.name, entry.name);
                    }
                } else {
                    if (entry.count == 0) entry.count = 1;
                    target.put(entry.uid, entry);
                }
                if (isBlack) dirtyBlacks.add(entry.uid);
                else dirtyWhites.add(entry.uid);
                imported++;
            }
        }
        if (imported > 0) flushAll();
        return imported;
    }

    /** 从指定文件重载（合并后调用，防止旧数据覆盖合并结果） */
    public static synchronized void reloadFromFile(String path, boolean isBlack) {
        ConcurrentHashMap<Long, BlackWhiteEntry> target = isBlack ? blacklistCache : whitelistCache;
        target.clear();
        // 优先从 SQLite 重载
        if (!loadFromSqlite()) {
            File file = new File(path);
            if (file.exists()) loadSingleCsv(file, target);
        }
    }

    // ==================== 数据对象 ====================

    public static class BlackWhiteEntry {
        public final long uid;
        public volatile String name;
        public volatile long createTime;
        public volatile long updateTime;
        public volatile int score;
        public volatile String scoreType;
        public volatile long roomId;
        public volatile int count;

        BlackWhiteEntry(long uid, String name, long createTime, long updateTime,
                        int score, String scoreType, long roomId, int count) {
            this.uid = uid;
            this.name = (name != null) ? name : "";
            this.createTime = createTime;
            this.updateTime = updateTime;
            this.score = score;
            this.scoreType = (scoreType != null) ? scoreType : "";
            this.roomId = roomId;
            this.count = count;
        }
    }
}
