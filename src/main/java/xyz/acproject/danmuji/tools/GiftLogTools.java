package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.system.ApplicationHome;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    private static volatile String lastRoom;
    private static String jarDir;

    static {
        initBase();
        lastRoom = roomKey();
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(GiftLogTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "gift-csv-shutdown"));
    }

    private static void initBase() {
        ApplicationHome home = new ApplicationHome(GiftLogTools.class);
        jarDir = home.getSource().getParentFile().getAbsolutePath();
    }

    private static String roomKey() {
        Long id = PublicDataConf.ROOMID;
        return id != null ? id.toString() : "unknown";
    }

    private static String currentCsvPath() {
        return jarDir + File.separator + "Danmuji_log" + File.separator + roomKey() + "_3_礼物信息.csv";
    }

    private static String key(long uid, String giftName) {
        return uid + "_" + giftName;
    }

    public static void logGift(long uid, String uname, String giftName, long price, long timestamp) {
        String k = key(uid, giftName);
        giftMap.compute(k, (key, v) -> {
            if (v == null) {
                return new GiftRecord(uid, uname, giftName, price, price, 1, timestamp);
            }
            v.uname = uname;
            long singlePrice = price > 0 ? price : v.price;
            v.price = singlePrice;
            v.totalPrice += price;
            v.count++;
            v.latestTime = timestamp;
            return v;
        });
    }

    private static void loadFromCsv() {
        loadFromCsv(currentCsvPath());
    }

    private static void loadFromCsv(String path) {
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
        if (!rk.equals(lastRoom)) {
            // room switched: flush old data to old file, then load new room
            String oldPath = jarDir + File.separator + "Danmuji_log" + File.separator + lastRoom + "_3_礼物信息.csv";
            doFlush(oldPath);
            giftMap.clear();
            lastRoom = rk;
            loadFromCsv(currentCsvPath());
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
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static class GiftRecord {
        final long uid;
        volatile String uname;
        final String giftName;
        volatile long price;
        volatile long totalPrice;
        volatile int count;
        volatile long latestTime;

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
