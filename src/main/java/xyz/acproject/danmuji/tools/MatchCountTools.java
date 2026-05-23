package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.system.ApplicationHome;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MatchCountTools {
    private static final Logger LOGGER = LogManager.getLogger(MatchCountTools.class);

    private static final ConcurrentHashMap<Long, MatchRecord> matchMap = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "match-csv-flush");
        t.setDaemon(true);
        return t;
    });

    private static volatile String csvPath;

    static {
        initCsvPath();
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(MatchCountTools::flushToCsv, 30, 30, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "match-csv-shutdown"));
    }

    private static void initCsvPath() {
        ApplicationHome home = new ApplicationHome(MatchCountTools.class);
        File jarDir = home.getSource().getParentFile();
        csvPath = new File(jarDir, "Danmuji_log/" + roomid() + "_5_匹配信息.csv").getAbsolutePath();
    }

    private static String roomid() {
        Long id = PublicDataConf.ROOMID;
        return id != null ? id.toString() : "unknown";
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
    }

    private static void loadFromCsv() {
        File file = new File(csvPath);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                MatchRecord record = parseLine(line);
                if (record != null) {
                    matchMap.put(record.matchedUid, record);
                }
            }
        } catch (Exception e) {
            LOGGER.error("load match CSV failed", e);
        }
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
        List<MatchRecord> records = new ArrayList<>(matchMap.values());
        File file = new File(csvPath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.write('﻿');
            writer.write("最近匹配,匹配id,匹配名,匹配分,匹配次数");
            writer.newLine();
            for (MatchRecord r : records) {
                writer.write(JodaTimeUtils.formatDateTime(r.latestMatchTime) + ",");
                writer.write(r.matchedUid + ",");
                writer.write(escapeCsv(r.matchedName) + ",");
                writer.write(r.score + ",");
                writer.write(r.count);
                writer.newLine();
            }
        } catch (Exception e) {
            LOGGER.error("flush match CSV failed", e);
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
