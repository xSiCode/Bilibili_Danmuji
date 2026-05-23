package xyz.acproject.danmuji.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.system.ApplicationHome;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BarrageLogTools {
    private static final Logger LOGGER = LogManager.getLogger(BarrageLogTools.class);

    private static final LinkedBlockingQueue<String> batchQueue = new LinkedBlockingQueue<>(20000);

    private static volatile String csvPath;
    private static volatile boolean headerWritten;
    private static final byte[] BOM = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};

    static {
        initCsvPath();
        headerWritten = new File(csvPath).exists();

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

    private static void initCsvPath() {
        ApplicationHome home = new ApplicationHome(BarrageLogTools.class);
        File jarDir = home.getSource().getParentFile();
        Long id = PublicDataConf.ROOMID;
        String room = id != null ? id.toString() : "unknown";
        csvPath = new File(jarDir, "Danmuji_log/弹幕信息_" + room + ".csv").getAbsolutePath();
    }

    public static void logBarrage(long uid, String uname, String msg, long timestamp) {
        String line = uid + "," + escapeCsv(uname) + "," + escapeCsv(msg) + "," + JodaTimeUtils.formatDateTime(timestamp);
        batchQueue.offer(line);
    }

    private static synchronized void flushBatch(List<String> lines) {
        File file = new File(csvPath);
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
                    writer.write("id,名字,弹幕,发送时间");
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
