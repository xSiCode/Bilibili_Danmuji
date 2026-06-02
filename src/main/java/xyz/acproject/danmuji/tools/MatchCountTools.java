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
    private static String jarDir;

    static {
        initBase();
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(MatchCountTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "match-csv-shutdown"));
    }

    private static void initBase() {
        ApplicationHome home = new ApplicationHome(MatchCountTools.class);
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
        return jarDir + File.separator + "Danmuji_log" + File.separator + roomKey() + "_" + name + "_5_匹配信息.csv";
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
        loadFromCsv(currentCsvPath());
    }

    private static void loadFromCsv(String path) {
        File file = new File(path);
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
        String rk = roomKey();
        if (!rk.equals(lastRoomId)) {
            String oldPrefix = lastRoomId + "_" + lastAnchorName;
            String oldPath = jarDir + File.separator + "Danmuji_log" + File.separator + oldPrefix + "_5_匹配信息.csv";
            doFlush(oldPath);
            matchMap.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv(currentCsvPath());
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
