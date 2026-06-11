package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GiftLogTools {
    private static final Logger LOGGER = LogManager.getLogger(GiftLogTools.class);

    private static final ConcurrentHashMap<String, GiftRecord> giftMap = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gift-csv-flush");
        t.setDaemon(true);
        return t;
    });

    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;

    static {
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(GiftLogTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "gift-csv-shutdown"));
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
        return LogPathConf.getLogDir() + File.separator + roomKey() + "_" + name + "_3_礼物信息.csv";
    }

    private static String key(long uid, String giftName) {
        return uid + "_" + giftName;
    }

    public static void logGift(long uid, String uname, String giftName, long price, long timestamp) {
        long timestampMillis = timestamp * 1000; // 返回的是以s为单位，而不是毫秒
        String k = key(uid, giftName);
        giftMap.compute(k, (key, v) -> {
            if (v == null) {
                return new GiftRecord(uid, uname, giftName, price, price, 1, timestampMillis);
            }
            v.uname = uname;
            long singlePrice = price > 0 ? price : v.price;
            v.price = singlePrice;
            v.totalPrice += price;
            v.count++;
            v.latestTime = timestampMillis;
            return v;
        });
        // 通知 WebSocket 客户端数据已更新（节流：每秒最多一次）
        long now = System.currentTimeMillis();
        if (now - lastGiftNotify > 1000) {
            lastGiftNotify = now;
            xyz.acproject.danmuji.controller.DanmuWebsocket.notifyDataUpdate("gift");
        }
    }
    private static volatile long lastGiftNotify = 0;

    private static void loadFromCsv() {
        // 优先从 SQLite 加载
        if (!loadFromSqlite(currentCsvPath())) {
            loadFromCsvLegacy(currentCsvPath());
        }
    }

    /** 从指定文件重载内存数据（合并后调用，防止旧数据覆盖合并结果） */
    public static synchronized void reloadFromFile(String path) {
        giftMap.clear();
        if (!loadFromSqlite(path)) {
            loadFromCsvLegacy(path);
        }
    }

    /** 从 SQLite 加载，成功返回 true */
    private static boolean loadFromSqlite(String csvPath) {
        String filename = new File(csvPath).getName();
        String[] info = xyz.acproject.danmuji.tools.db.DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return false;
        long roomId;
        try { roomId = Long.parseLong(info[0]); } catch (NumberFormatException e) { return false; }
        String anchorName = info[1];

        String sql = "SELECT uid, uname, gift_name, total_price, count, latest_time FROM gift_summary WHERE room_id = ? AND anchor_name = ?";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, roomId);
            ps.setString(2, anchorName);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long uid = rs.getLong("uid");
                    String uname = rs.getString("uname");
                    String giftName = rs.getString("gift_name");
                    long totalPrice = rs.getLong("total_price");
                    int count = rs.getInt("count");
                    long latestTime = rs.getLong("latest_time");
                    giftMap.put(key(uid, giftName), new GiftRecord(uid, uname, giftName, 0, totalPrice, count, latestTime));
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("load gift from SQLite failed, falling back to CSV: {}", e.getMessage());
            return false;
        }
    }

    /** 从 CSV 文件加载（兜底） */
    private static void loadFromCsvLegacy(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                GiftRecord record = parseLine(line);
                if (record != null) {
                    giftMap.put(key(record.uid, record.giftName), record);
                }
            }
        } catch (Exception e) {
            LOGGER.error("load gift CSV failed", e);
        }
    }

    private static GiftRecord parseLine(String line) {
        if (line == null || line.isEmpty()) return null;
        try {
            List<String> fields = parseCsvLine(line);
            if (fields.size() < 6) return null;
            String timeStr = fields.get(0);
            long uid = Long.parseLong(fields.get(1));
            String uname = fields.get(2);
            String giftName = fields.get(3);
            long totalPrice = Long.parseLong(fields.get(4));
            int count = Integer.parseInt(fields.get(5));
            long time = JodaTimeUtils.parse(timeStr, "yyyy-MM-dd HH:mm:ss").getTime();
            return new GiftRecord(uid, uname, giftName, 0, totalPrice, count, time);
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
        if ("unknown".equals(rk)) return; // 房间未连接，跳过写入避免生成 unknown 文件
        if (!rk.equals(lastRoomId)) {
            String oldPrefix = lastRoomId + "_" + lastAnchorName;
            String oldPath = LogPathConf.getLogDir() + File.separator + oldPrefix + "_3_礼物信息.csv";
            doFlush(oldPath);
            giftMap.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv();
        }
        doFlush(currentCsvPath());
    }

    private static void doFlush(String path) {
        List<GiftRecord> records = new ArrayList<>(giftMap.values());
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File tmpFile = new File(path + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
            writer.write('﻿');
            writer.write("最新时间,id,名字,赠送礼物名字,总金额,赠礼次数");
            writer.newLine();
            for (GiftRecord r : records) {
                writer.write(JodaTimeUtils.formatDateTime(r.latestTime) + ",");
                writer.write(r.uid + ",");
                writer.write(escapeCsv(r.uname) + ",");
                writer.write(escapeCsv(r.giftName) + ",");
                writer.write(r.totalPrice + ",");
                writer.write(String.valueOf(r.count));
                writer.newLine();
            }
        } catch (Exception e) {
            LOGGER.error("flush gift CSV failed", e);
            return;
        }
        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("move gift CSV failed", e);
        }

        // 同步写入 SQLite
        flushToSqlite(path, records);
    }

    private static void flushToSqlite(String csvPath, List<GiftRecord> records) {
        // 从 CSV 文件名解析 roomId / anchorName
        String filename = new File(csvPath).getName();
        String[] info = xyz.acproject.danmuji.tools.db.DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return;
        long roomId = Long.parseLong(info[0]);
        String anchorName = info[1];

        String sql = "INSERT OR REPLACE INTO gift_summary(room_id,anchor_name,uid,uname,gift_name,total_price,count,latest_time) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (GiftRecord r : records) {
                ps.setLong(1, roomId);
                ps.setString(2, anchorName);
                ps.setLong(3, r.uid);
                ps.setString(4, r.uname != null ? r.uname : "");
                ps.setString(5, r.giftName != null ? r.giftName : "");
                ps.setLong(6, r.totalPrice);
                ps.setInt(7, r.count);
                ps.setLong(8, r.latestTime);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            LOGGER.error("flush gift to SQLite failed", e);
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** 返回内存中的礼物聚合数据（实时，无 CSV 读取延迟） */
    public static List<GiftRecord> getGiftList() {
        return new ArrayList<>(giftMap.values());
    }

    public static int getGiftCount() {
        return giftMap.size();
    }

    public static class GiftRecord {
        public final long uid;
        public volatile String uname;
        public final String giftName;
        public volatile long price;
        public volatile long totalPrice;
        public volatile int count;
        public volatile long latestTime;

        GiftRecord(long uid, String uname, String giftName, long price, long totalPrice, int count, long latestTime) {
            this.uid = uid;
            this.uname = uname;
            this.giftName = giftName;
            this.price = price;
            this.totalPrice = totalPrice;
            this.count = count;
            this.latestTime = latestTime;
        }
    }
}
