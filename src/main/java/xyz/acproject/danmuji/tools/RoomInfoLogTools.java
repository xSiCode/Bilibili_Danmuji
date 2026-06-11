package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;
import xyz.acproject.danmuji.tools.db.DanmujiMigration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RoomInfoLogTools {
    private static final Logger LOGGER = LogManager.getLogger(RoomInfoLogTools.class);

    private static final LinkedHashMap<String, long[]> roomInfoMap = new LinkedHashMap<>();
    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;
    private static volatile boolean running;
    private static volatile ScheduledExecutorService roomInfoScheduler;
    private static volatile Thread shutdownHook;

    private static final ThreadLocal<SimpleDateFormat> MINUTE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm"));

    static {
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
    }

    /** 从指定文件重载内存数据（合并后调用，防止旧数据覆盖合并结果） */
    public static synchronized void reloadFromFile(String path) {
        synchronized (roomInfoMap) { roomInfoMap.clear(); }
        if (!loadFromSqlite(path)) {
            loadFromCsvLegacy(path);
        }
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        tick();
        // 停止可能残留的旧调度器
        ScheduledExecutorService oldScheduler = roomInfoScheduler;
        if (oldScheduler != null) {
            oldScheduler.shutdownNow();
        }
        if (shutdownHook != null) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); } catch (IllegalStateException ignored) {}
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "roominfo-timer");
            t.setDaemon(true);
            return t;
        });
        roomInfoScheduler = scheduler;
        scheduler.scheduleWithFixedDelay(() -> {
            tick();
            flushToCsv();
        }, 10, 10, TimeUnit.SECONDS);  // 10秒采样，比原来60秒提升6倍实时性
        Thread hook = new Thread(() -> {
            running = false;
            scheduler.shutdown();
            flushToCsv();
        }, "roominfo-csv-shutdown");
        shutdownHook = hook;
        Runtime.getRuntime().addShutdownHook(hook);
    }

    public static synchronized void stop() {
        running = false;
        flushToCsv();
        ScheduledExecutorService scheduler = roomInfoScheduler;
        if (scheduler != null) {
            scheduler.shutdown();
            roomInfoScheduler = null;
        }
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
        return LogPathConf.getLogDir() + File.separator + roomKey() + "_" + name + "_1_直播间信息.csv";
    }

    private static synchronized void tick() {
        if (!running) return;
        String nowKey = MINUTE_FORMAT.get().format(new Date());
        long w = PublicDataConf.ROOM_WATCHER != null ? PublicDataConf.ROOM_WATCHER : 0L;
        long o = PublicDataConf.ROOM_ONLINE__RANK_COUNT != null ? PublicDataConf.ROOM_ONLINE__RANK_COUNT : 0L;
        long l = PublicDataConf.ROOM_LIKE != null ? PublicDataConf.ROOM_LIKE : 0L;
        // 任意值为 0 则丢弃该次采样，防止数据抖动（刚开播/断线重连时的瞬态零值）
        if (w == 0L || o == 0L || l == 0L) return;
        roomInfoMap.put(nowKey, new long[]{w, o, l});
    }

    private static void loadFromCsv() {
        if (!loadFromSqlite(currentCsvPath())) {
            loadFromCsvLegacy(currentCsvPath());
        }
    }

    private static boolean loadFromSqlite(String csvPath) {
        String filename = new File(csvPath).getName();
        String[] info = DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return false;
        long roomId;
        try { roomId = Long.parseLong(info[0]); } catch (NumberFormatException e) { return false; }
        String anchorName = info[1];

        String sql = "SELECT time_key, watch_count, online_count, like_count FROM room_info_series WHERE room_id = ? AND anchor_name = ? ORDER BY time_key";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, roomId);
            ps.setString(2, anchorName);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                synchronized (roomInfoMap) {
                    while (rs.next()) {
                        long[] vals = new long[]{rs.getLong("watch_count"), rs.getLong("online_count"), rs.getLong("like_count")};
                        roomInfoMap.put(rs.getString("time_key"), vals);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("load room info from SQLite failed, fallback to CSV: {}", e.getMessage());
            return false;
        }
    }

    private static void loadFromCsvLegacy(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 4);
                if (parts.length >= 4) {
                    long[] vals = new long[3];
                    vals[0] = Long.parseLong(parts[1]);
                    vals[1] = Long.parseLong(parts[2]);
                    vals[2] = Long.parseLong(parts[3]);
                    String timeKey = parts[0].length() >= 16 ? parts[0].substring(0, 16) : parts[0];
                    synchronized (roomInfoMap) { roomInfoMap.put(timeKey, vals); }
                }
            }
        } catch (Exception e) { LOGGER.error("load room info CSV failed", e); }
    }

    /**
     * 每分钟进入/退出人数（两项独立计算，始终 >= 0）。
     * 进入数 = 观看[t] - 观看[t-1]（累计观看增量）
     * 退出数 = 在线[t-1] - 在线[t]（在线人数下降量）
     */
    public static List<Map.Entry<String, long[]>> getEntryExitList() {
        List<Map.Entry<String, long[]>> raw;
        synchronized (roomInfoMap) {
            raw = new ArrayList<>(roomInfoMap.entrySet());
        }
        List<Map.Entry<String, long[]>> result = new ArrayList<>();
        if (raw.size() < 2) return result;
        for (int i = 1; i < raw.size(); i++) {
            long prevWatch = raw.get(i - 1).getValue()[0];
            long currWatch = raw.get(i).getValue()[0];
            long prevOnline = raw.get(i - 1).getValue()[1];
            long currOnline = raw.get(i).getValue()[1];

            long entry = Math.max(0, currWatch - prevWatch);
            long exit  = Math.max(0, prevOnline - currOnline);

            result.add(new AbstractMap.SimpleEntry<>(raw.get(i).getKey(), new long[]{entry, exit}));
        }
        return result;
    }

    /** 返回内存中的直播间数据（实时，无 CSV 读取延迟） */
    public static List<Map.Entry<String, long[]>> getRoomInfoList() {
        synchronized (roomInfoMap) {
            return new ArrayList<>(roomInfoMap.entrySet());
        }
    }

    /** 立即将内存数据刷入 CSV（供删除操作等需要即时持久化的场景调用） */
    public static synchronized void flushNow() {
        flushToCsv();
    }

    public static synchronized void removeByTimeKey(String timeKey) {
        if (timeKey == null || timeKey.isEmpty()) return;
        // 先尝试精确匹配
        if (roomInfoMap.remove(timeKey) != null) return;
        // 秒级 key（19 位）降级为分钟前缀匹配（16 位），兼容新旧格式
        String prefix = timeKey.length() >= 16 ? timeKey.substring(0, 16) : timeKey;
        roomInfoMap.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    private static synchronized void flushToCsv() {
        String rk = roomKey();
        if ("unknown".equals(rk)) return;
        if (!rk.equals(lastRoomId)) {
            String oldPrefix = lastRoomId + "_" + lastAnchorName;
            String oldPath = LogPathConf.getLogDir() + File.separator + oldPrefix + "_1_直播间信息.csv";
            doFlush(oldPath);
            roomInfoMap.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv();
        }
        doFlush(currentCsvPath());
    }

    private static void doFlush(String path) {
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File tmpFile = new File(path + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
            writer.write('﻿');
            writer.write("时间,观看数,在线数,点赞数");
            writer.newLine();
            for (Map.Entry<String, long[]> e : roomInfoMap.entrySet()) {
                long[] v = e.getValue();
                writer.write(e.getKey() + "," + v[0] + "," + v[1] + "," + v[2]);
                writer.newLine();
            }
        } catch (Exception ex) {
            LOGGER.error("flush room info CSV failed", ex);
            return;
        }
        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("move room info CSV failed", e);
        }

        flushToSqlite(path);
    }

    private static void flushToSqlite(String csvPath) {
        String filename = new File(csvPath).getName();
        String[] info = DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return;
        long roomId = Long.parseLong(info[0]);
        String anchorName = info[1];

        List<Map.Entry<String, long[]>> entries;
        synchronized (roomInfoMap) {
            entries = new ArrayList<>(roomInfoMap.entrySet());
        }

        try (Connection c = DanmujiDatabase.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 先删后插（全量覆写）
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM room_info_series WHERE room_id = ? AND anchor_name = ?")) {
                    del.setLong(1, roomId);
                    del.setString(2, anchorName);
                    del.executeUpdate();
                }
                String sql = "INSERT INTO room_info_series(room_id,anchor_name,time_key,watch_count,online_count,like_count) VALUES (?,?,?,?,?,?)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (Map.Entry<String, long[]> e : entries) {
                        long[] v = e.getValue();
                        ps.setLong(1, roomId);
                        ps.setString(2, anchorName);
                        ps.setString(3, e.getKey());
                        ps.setLong(4, v[0]);
                        ps.setLong(5, v[1]);
                        ps.setLong(6, v[2]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (Exception e2) {
                try { c.rollback(); } catch (Exception ignored) {}
                throw e2;
            }
        } catch (Exception e) {
            LOGGER.error("flush room info to SQLite failed", e);
        }
    }
}
