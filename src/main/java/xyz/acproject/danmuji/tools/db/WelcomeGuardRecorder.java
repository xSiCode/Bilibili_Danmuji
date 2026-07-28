package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.Welcome.WelcomeGuard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 欢迎舰长 DB 记录器 — WELCOME_GUARD 事件写入 welcome_guard 表。
 */
public class WelcomeGuardRecorder {
    private static final Logger LOGGER = LogManager.getLogger(WelcomeGuardRecorder.class);
    private static final LinkedBlockingQueue<WelcomeGuard> queue = new LinkedBlockingQueue<>(5000);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    static {
        Thread writer = new Thread(() -> {
            List<WelcomeGuard> batch = new ArrayList<>();
            while (true) {
                try {
                    WelcomeGuard first = queue.poll(1, TimeUnit.SECONDS);
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
        }, "db-welcome-guard-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private WelcomeGuardRecorder() {}

    public static void record(WelcomeGuard wg) {
        if (wg == null || wg.getUid() == null) {
            LOGGER.warn("WelcomeGuardRecorder: dropped null uid");
            return;
        }
        if (!queue.offer(wg)) {
            LOGGER.warn("WelcomeGuardRecorder: queue full, dropping guard from={}", wg.getUsername());
        }
    }

    private static void flushBatchWithRetry(List<WelcomeGuard> batch) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (flushBatch(batch)) return;
            LOGGER.warn("WelcomeGuardRecorder: flush failed (attempt {}), retrying", attempt + 1);
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException e) { break; }
        }
        int requeued = 0;
        for (WelcomeGuard wg : batch) {
            if (queue.offer(wg)) requeued++;
        }
        LOGGER.warn("WelcomeGuardRecorder: requeued {}/{} after all retries", requeued, batch.size());
    }

    private static boolean flushBatch(List<WelcomeGuard> batch) {
        if (batch.isEmpty()) return true;
        String sql =
            "INSERT INTO welcome_guard(room_id,anchor_name,uid,uname,guard_level,timestamp) " +
            "VALUES (?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (WelcomeGuard wg : batch) {
                int i = 1;
                ps.setObject(i++, PublicDataConf.ROOMID);
                ps.setString(i++, PublicDataConf.ANCHOR_NAME);
                ps.setLong(i++, wg.getUid());
                ps.setString(i++, wg.getUsername());
                ps.setObject(i++, wg.getGuard_level());
                ps.setLong(i++, System.currentTimeMillis());
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (Exception e) {
            LOGGER.error("WelcomeGuardRecorder flush error", e);
            return false;
        }
    }
}
