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

public class FollowingCountTools {
    private static final Logger LOGGER = LogManager.getLogger(FollowingCountTools.class);

    private static final ConcurrentHashMap<Long, FollowingRecord> followingMap = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "following-csv-flush");
        t.setDaemon(true);
        return t;
    });

    private static volatile String lastRoom;
    private static String jarDir;

    static {
        initBase();
        lastRoom = roomKey();
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(FollowingCountTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "following-csv-shutdown"));
    }

    private static void initBase() {
        ApplicationHome home = new ApplicationHome(FollowingCountTools.class);
        jarDir = home.getSource().getParentFile().getAbsolutePath();
    }

    private static String roomKey() {
        Long id = PublicDataConf.ROOMID;
        return id != null ? id.toString() : "unknown";
    }

    private static String currentCsvPath() {
        return jarDir + File.separator + "Danmuji_log" + File.separator + roomKey() + "_6_关注人信息.csv";
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
                FollowingRecord record = parseLine(line);
                if (record != null) {
                    followingMap.put(record.uid, record);
                }
            }
        } catch (Exception e) {
            LOGGER.error("load following CSV failed", e);
        }
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
        if (!rk.equals(lastRoom)) {
            String oldPath = jarDir + File.separator + "Danmuji_log" + File.separator + lastRoom + "_6_关注人信息.csv";
            doFlush(oldPath);
            followingMap.clear();
            lastRoom = rk;
            loadFromCsv(currentCsvPath());
        }
        doFlush(currentCsvPath());
    }

    private static void doFlush(String path) {
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
        } catch (IOException e) {
            LOGGER.error("move following CSV failed", e);
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static class FollowingRecord {
        final long uid;
        volatile String name;
        volatile int count;
        volatile long latestTime;

        FollowingRecord(long uid, String name, int count, long latestTime) {
            this.uid = uid;
            this.name = name;
            this.count = count;
            this.latestTime = latestTime;
        }
    }
}
