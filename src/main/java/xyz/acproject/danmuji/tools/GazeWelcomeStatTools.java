package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.tools.file.ProFileTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 欢迎凝视姬触发统计 — 内存计数 + set/CSV 持久化。
 * 统计键：(uid, 欢迎词模板原文)；不按房间隔离。
 */
public class GazeWelcomeStatTools {
    private static final Logger LOGGER = LogManager.getLogger(GazeWelcomeStatTools.class);

    private static final ConcurrentHashMap<String, StatEntry> statMap = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gaze-welcome-stat-flush");
        t.setDaemon(true);
        return t;
    });

    static {
        loadFromCsv();
        flushScheduler.scheduleWithFixedDelay(GazeWelcomeStatTools::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            flushScheduler.shutdown();
            flushToCsv();
        }, "gaze-welcome-stat-shutdown"));
    }

    private static String csvPath() {
        return ProFileTools.getStoreDir() + "/set/欢迎凝视统计.csv";
    }

    private static String key(long uid, String welcomeText) {
        return uid + "\u0001" + welcomeText;
    }

    /** 记录一次触发：同一 (uid, 欢迎词) 计数 +1 */
    public static void record(long uid, String uname, String welcomeText) {
        if (uid <= 0 || welcomeText == null || welcomeText.isEmpty()) return;
        String k = key(uid, welcomeText);
        statMap.compute(k, (key, v) -> {
            long now = System.currentTimeMillis();
            if (v == null) return new StatEntry(uid, uname, welcomeText, 1, now);
            v.uname = uname;
            v.count++;
            v.latestTime = now;
            return v;
        });
    }

    /** 当前全部统计快照 */
    public static List<StatEntry> getStats() {
        return new ArrayList<>(statMap.values());
    }

    /** 清空统计并立即重写 CSV（仅保留表头） */
    public static void clear() {
        statMap.clear();
        flushToCsv();
    }

    // ==================== CSV 读写 ====================

    private static synchronized void loadFromCsv() {
        File file = new File(csvPath());
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // 表头（可能带BOM）
            if (line != null && line.startsWith("\uFEFF")) line = line.substring(1);
            while ((line = reader.readLine()) != null) {
                List<String> fields = parseCsvLine(line);
                if (fields.size() < 4) continue;
                try {
                    long uid = Long.parseLong(fields.get(0).trim());
                    String uname = fields.get(1);
                    String welcomeText = fields.get(2);
                    int count = Integer.parseInt(fields.get(3).trim());
                    long latestTime = fields.size() > 4 && !fields.get(4).trim().isEmpty()
                            ? parseTime(fields.get(4).trim()) : 0;
                    if (uid <= 0 || welcomeText == null || welcomeText.isEmpty() || count <= 0) continue;
                    statMap.compute(key(uid, welcomeText), (k, v) -> {
                        if (v == null) return new StatEntry(uid, uname, welcomeText, count, latestTime);
                        v.count += count;
                        if (latestTime > v.latestTime) { v.latestTime = latestTime; v.uname = uname; }
                        return v;
                    });
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.error("load 欢迎凝视统计.csv failed", e);
        }
    }

    private static synchronized void flushToCsv() {
        try {
            File file = new File(csvPath());
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            File tmp = new File(file.getAbsolutePath() + ".tmp");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                writer.write("uid,用户名,欢迎词,次数,最后触发时间\n");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                for (StatEntry e : statMap.values()) {
                    writer.write(e.uid + "," + esc(e.uname) + "," + esc(e.welcomeText) + ","
                            + e.count + "," + (e.latestTime > 0 ? sdf.format(new Date(e.latestTime)) : "") + "\n");
                }
                writer.flush();
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.error("flush 欢迎凝视统计.csv failed", e);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') inQuotes = true;
                else if (c == ',') { fields.add(sb.toString()); sb.setLength(0); }
                else sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields;
    }

    private static long parseTime(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    public static class StatEntry {
        public final long uid;
        public volatile String uname;
        public final String welcomeText;
        public volatile int count;
        public volatile long latestTime;

        StatEntry(long uid, String uname, String welcomeText, int count, long latestTime) {
            this.uid = uid;
            this.uname = uname != null ? uname : "";
            this.welcomeText = welcomeText;
            this.count = count;
            this.latestTime = latestTime;
        }
    }
}
