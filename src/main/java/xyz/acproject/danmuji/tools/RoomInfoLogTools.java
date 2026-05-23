package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.system.ApplicationHome;
import xyz.acproject.danmuji.conf.PublicDataConf;

import java.io.*;
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
    private static volatile String csvPath;
    private static volatile boolean running;

    private static final ThreadLocal<SimpleDateFormat> MINUTE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm"));

    static {
        initCsvPath();
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
        scheduler.scheduleWithFixedDelay(RoomInfoLogTools::tick, 60, 60, TimeUnit.SECONDS);
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

    private static void initCsvPath() {
        ApplicationHome home = new ApplicationHome(RoomInfoLogTools.class);
        File jarDir = home.getSource().getParentFile();
        Long id = PublicDataConf.ROOMID;
        String room = id != null ? id.toString() : "unknown";
        csvPath = new File(jarDir, "Danmuji_log/直播间信息_" + room + ".csv").getAbsolutePath();
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
        File file = new File(csvPath);
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
        File file = new File(csvPath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
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
        }
    }
}
