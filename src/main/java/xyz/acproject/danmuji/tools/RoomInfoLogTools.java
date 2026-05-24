package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.system.ApplicationHome;
import xyz.acproject.danmuji.conf.PublicDataConf;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
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
    private static String jarDir;
    private static volatile boolean running;

    private static final ThreadLocal<SimpleDateFormat> MINUTE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm"));

    static {
        initBase();
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        tick();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "roominfo-timer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            tick();
            flushToCsv();
        }, 180, 180, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            scheduler.shutdown();
            flushToCsv();
        }, "roominfo-csv-shutdown"));
    }

    public static synchronized void stop() {
        running = false;
        flushToCsv();
    }

    private static void initBase() {
        ApplicationHome home = new ApplicationHome(RoomInfoLogTools.class);
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
        return jarDir + File.separator + "Danmuji_log" + File.separator + roomKey() + "_" + name + "_1_直播间信息.csv";
    }

    private static synchronized void tick() {
        if (!running) return;
        String nowKey = MINUTE_FORMAT.get().format(new Date());
        long w = PublicDataConf.ROOM_WATCHER != null ? PublicDataConf.ROOM_WATCHER : 0L;
        long o = PublicDataConf.ROOM_ONLINE__RANK_COUNT != null ? PublicDataConf.ROOM_ONLINE__RANK_COUNT : 0L;
        long l = PublicDataConf.ROOM_LIKE != null ? PublicDataConf.ROOM_LIKE : 0L;
        roomInfoMap.put(nowKey, new long[]{w, o, l});
    }

    private static void loadFromCsv() {
        loadFromCsv(currentCsvPath());
    }

    private static void loadFromCsv(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine(); // skip header + BOM
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 4);
                if (parts.length >= 4) {
                    long[] vals = new long[3];
                    vals[0] = Long.parseLong(parts[1]);
                    vals[1] = Long.parseLong(parts[2]);
                    vals[2] = Long.parseLong(parts[3]);
                    roomInfoMap.put(parts[0], vals);
                }
            }
        } catch (Exception e) {
            LOGGER.error("load room info CSV failed", e);
        }
    }

    private static synchronized void flushToCsv() {
        String rk = roomKey();
        if (!rk.equals(lastRoomId)) {
            String oldPrefix = lastRoomId + "_" + lastAnchorName;
            String oldPath = jarDir + File.separator + "Danmuji_log" + File.separator + oldPrefix + "_1_直播间信息.csv";
            doFlush(oldPath);
            roomInfoMap.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv(currentCsvPath());
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
    }
}
