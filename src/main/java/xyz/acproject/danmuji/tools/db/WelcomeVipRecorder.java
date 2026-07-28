package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.Welcome.WelcomeVip;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 欢迎老爷 DB 记录器 — WELCOME 事件写入 welcome_vip 表。
 */
public class WelcomeVipRecorder {
    private static final Logger LOGGER = LogManager.getLogger(WelcomeVipRecorder.class);
    private static final LinkedBlockingQueue<WelcomeVip> queue = new LinkedBlockingQueue<>(5000);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    static {
        Thread writer = new Thread(() -> {
            List<WelcomeVip> batch = new ArrayList<>();
            while (true) {
                try {
                    WelcomeVip first = queue.poll(1, TimeUnit.SECONDS);
                    if (first != null) {
                        batch.add(first);
                        queue.drainTo(batch, 200);
                        flushBatchWithRetry(batch);
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "db-welcome-vip-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private WelcomeVipRecorder() {}

    public static void record(WelcomeVip wv) {
        if (wv == null || wv.getUid() == null) {
            LOGGER.warn("WelcomeVipRecorder: dropped null uid");
            return;
        }
        if (!queue.offer(wv)) {
            LOGGER.warn("WelcomeVipRecorder: queue full, dropping VIP from={}", wv.getUname());
        }
    }

    private static void flushBatchWithRetry(List<WelcomeVip> batch) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (flushBatch(batch)) return;
            LOGGER.warn("WelcomeVipRecorder: flush failed (attempt {}), retrying", attempt + 1);
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException e) { break; }
        }
        int requeued = 0;
        for (WelcomeVip wv : batch) {
            if (queue.offer(wv)) requeued++;
        }
        LOGGER.warn("WelcomeVipRecorder: requeued {}/{} after all retries", requeued, batch.size());
    }

    private static boolean flushBatch(List<WelcomeVip> batch) {
        if (batch.isEmpty()) return true;
        String sql =
            "INSERT INTO welcome_vip(room_id,anchor_name,uid,uname,vip,svip,is_admin,timestamp) " +
            "VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (WelcomeVip wv : batch) {
                int i = 1;
                ps.setObject(i++, PublicDataConf.ROOMID);
                ps.setString(i++, PublicDataConf.ANCHOR_NAME);
                ps.setLong(i++, wv.getUid());
                ps.setString(i++, wv.getUname());
                ps.setInt(i++, wv.getVip() != null ? wv.getVip() : 0);
                ps.setInt(i++, wv.getSvip() != null ? wv.getSvip() : 0);
                ps.setInt(i++, wv.getIs_admin() != null && wv.getIs_admin() ? 1 : 0);
                ps.setLong(i++, System.currentTimeMillis());
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (Exception e) {
            LOGGER.error("WelcomeVipRecorder flush error", e);
            return false;
        }
    }
}
