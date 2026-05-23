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

public class VisitorCountTools {
    private static final Logger LOGGER = LogManager.getLogger(VisitorCountTools.class);

    private static final ConcurrentHashMap<Long, VisitorRecord> visitorMap = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "visitor-csv-flush");
        t.setDaemon(true);
        return t;
    });

    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;
    private static String jarDir;

    static {
        initBase();
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(VisitorCountTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "visitor-csv-shutdown"));
    }

    private static void initBase() {
        ApplicationHome home = new ApplicationHome(VisitorCountTools.class);
        jarDir = home.getSource().getParentFile().getAbsolutePath();
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
        return jarDir + File.separator + "Danmuji_log" + File.separator + roomKey() + "_" + name + "_4_观众信息.csv";
    }

    public static void recordVisitor(long uid, String uname, int score, String scoreType) {
        visitorMap.compute(uid, (k, v) -> {
            if (v == null) {
                return new VisitorRecord(uid, uname, score, scoreType, 1, System.currentTimeMillis());
            }
            v.uname = uname;
            v.score = score;
            v.scoreType = scoreType;
            v.count++;
            v.latestEntryTime = System.currentTimeMillis();
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
                VisitorRecord record = parseLine(line);
                if (record != null) {
                    visitorMap.put(record.uid, record);
                }
            }
        } catch (Exception e) {
            LOGGER.error("load visitor CSV failed", e);
        }
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
            long time = JodaTimeUtils.parse(timeStr, "yyyy-MM-dd HH:mm:ss").getTime();
            return new VisitorRecord(uid, uname, score, scoreType, count, time);
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
        if (!rk.equals(lastRoomId)) {
            String oldPrefix = lastRoomId + "_" + lastAnchorName;
            String oldPath = jarDir + File.separator + "Danmuji_log" + File.separator + oldPrefix + "_4_观众信息.csv";
            doFlush(oldPath);
            visitorMap.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv(currentCsvPath());
        }
        doFlush(currentCsvPath());
    }

    private static void doFlush(String path) {
        List<VisitorRecord> records = new ArrayList<>(visitorMap.values());
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File tmpFile = new File(path + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
            writer.write('﻿');
            writer.write("最近,id,观众,打分,打分类型,次数");
            writer.newLine();
            for (VisitorRecord r : records) {
                writer.write(JodaTimeUtils.formatDateTime(r.latestEntryTime) + ",");
                writer.write(r.uid + ",");
                writer.write(escapeCsv(r.uname) + ",");
                writer.write(r.score + ",");
                writer.write(escapeCsv(r.scoreType) + ",");
                writer.write(String.valueOf(r.count));
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
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static class VisitorRecord {
        final long uid;
        volatile String uname;
        volatile int score;
        volatile String scoreType;
        volatile int count;
        volatile long latestEntryTime;

        VisitorRecord(long uid, String uname, int score, String scoreType, int count, long latestEntryTime) {
            this.uid = uid;
            this.uname = uname;
            this.score = score;
            this.scoreType = scoreType;
            this.count = count;
            this.latestEntryTime = latestEntryTime;
        }
    }
}
