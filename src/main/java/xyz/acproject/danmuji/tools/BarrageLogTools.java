package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BarrageLogTools {
    private static final Logger LOGGER = LogManager.getLogger(BarrageLogTools.class);

    private static final LinkedBlockingQueue<String> batchQueue = new LinkedBlockingQueue<>(2000);
    // 内存环形缓冲区：保留最近 N 条弹幕供实时查询，避免每次读 CSV 的延迟
    private static final java.util.concurrent.ConcurrentLinkedDeque<String> recentBarrages = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT_BARRAGES = 500;

    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;
    private static volatile boolean headerWritten;
    private static final byte[] BOM = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};

    static {
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        headerWritten = new File(currentCsvPath()).exists();

        Thread writer = new Thread(() -> {
            while (true) {
                try {
                    String first = batchQueue.poll(1, TimeUnit.SECONDS);
                    if (first != null) {
                        List<String> batch = new ArrayList<>();
                        batch.add(first);
                        batchQueue.drainTo(batch, 500);
                        flushBatch(batch);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        writer.setDaemon(true);
        writer.setName("BarrageLogWriter");
        writer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            List<String> remaining = new ArrayList<>();
            batchQueue.drainTo(remaining);
            if (!remaining.isEmpty()) {
                flushBatch(remaining);
            }
        }, "barrage-csv-shutdown"));
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
        return LogPathConf.getLogDir() + File.separator + roomKey() + "_" + name + "_2_弹幕信息.csv";
    }

    public static void logBarrage(long uid, String uname, String msg, long timestamp) {
        String line = JodaTimeUtils.formatDateTime(timestamp) + "," + uid + "," + escapeCsv(uname) + "," + escapeCsv(msg);
        batchQueue.offer(line);
        // 写入内存环形缓冲区供实时查询（避免 CSV 读取延迟）
        recentBarrages.offerLast(line);
        while (recentBarrages.size() > MAX_RECENT_BARRAGES) {
            recentBarrages.pollFirst();
        }
        // 通知 WebSocket 客户端数据已更新（节流：每秒最多一次）
        long now = System.currentTimeMillis();
        if (now - lastBarrageNotify > 1000) {
            lastBarrageNotify = now;
            xyz.acproject.danmuji.controller.DanmuWebsocket.notifyDataUpdate("barrage");
        }
    }
    private static volatile long lastBarrageNotify = 0;

    /** 返回内存中最近 N 条弹幕（实时，无 CSV 读取延迟） */
    public static List<String> getRecentBarrages(int limit) {
        List<String> result = new ArrayList<>(Math.min(limit, recentBarrages.size()));
        java.util.Iterator<String> it = recentBarrages.descendingIterator();
        int count = 0;
        while (it.hasNext() && count < limit) {
            result.add(it.next());
            count++;
        }
        return result;
    }

    public static int getRecentBarrageCount() {
        return recentBarrages.size();
    }

    private static synchronized void flushBatch(List<String> lines) {
        String rk = roomKey();
        if ("unknown".equals(rk)) return;
        if (!rk.equals(lastRoomId)) {
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            headerWritten = new File(currentCsvPath()).exists();
        }
        String path = currentCsvPath();
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        boolean needHeader = !headerWritten && file.length() == 0;
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            if (needHeader) {
                fos.write(BOM);
            }
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"), 8192)) {
                if (needHeader) {
                    writer.write("发送时间,id,名字,弹幕");
                    writer.newLine();
                    headerWritten = true;
                }
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (Exception e) {
            LOGGER.error("flush barrage CSV failed", e);
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
