package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.danmu_data.Guard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 上舰 DB 记录器 — GUARD_BUY 事件写入 guard_buy 表。
 */
public class GuardBuyRecorder {
    private static final Logger LOGGER = LogManager.getLogger(GuardBuyRecorder.class);
    private static final LinkedBlockingQueue<Guard> queue = new LinkedBlockingQueue<>(5000);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    static {
        Thread writer = new Thread(() -> {
            List<Guard> batch = new ArrayList<>();
            while (true) {
                try {
                    Guard first = queue.poll(1, TimeUnit.SECONDS);
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
        }, "db-guard-buy-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private GuardBuyRecorder() {}

    public static void record(Guard guard) {
        if (guard == null || guard.getUid() == null) {
            LOGGER.warn("GuardBuyRecorder: dropped null uid");
            return;
        }
        if (!queue.offer(guard)) {
            LOGGER.warn("GuardBuyRecorder: queue full, dropping guard from={}", guard.getUsername());
        }
    }

    private static void flushBatchWithRetry(List<Guard> batch) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (flushBatch(batch)) return;
            LOGGER.warn("GuardBuyRecorder: flush failed (attempt {}), retrying", attempt + 1);
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException e) { break; }
        }
        int requeued = 0;
        for (Guard g : batch) {
            if (queue.offer(g)) requeued++;
        }
        LOGGER.warn("GuardBuyRecorder: requeued {}/{} after all retries", requeued, batch.size());
    }

    private static boolean flushBatch(List<Guard> batch) {
        if (batch.isEmpty()) return true;
        String sql =
            "INSERT INTO guard_buy(room_id,anchor_name,uid,uname,guard_level,num,price,gift_name,start_time,end_time) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Guard g : batch) {
                // Guard 中时间是秒
                Long start = g.getStart_time();
                Long end = g.getEnd_time();
                if (start != null && start < 100000000000L) start = start * 1000;
                if (end != null && end < 100000000000L) end = end * 1000;
                int i = 1;
                ps.setObject(i++, PublicDataConf.ROOMID);
                ps.setString(i++, PublicDataConf.ANCHOR_NAME);
                ps.setLong(i++, g.getUid());
                ps.setString(i++, g.getUsername());
                ps.setObject(i++, g.getGuard_level());
                ps.setObject(i++, g.getNum());
                ps.setObject(i++, g.getPrice());
                ps.setString(i++, g.getGift_name());
                ps.setObject(i++, start);
                ps.setObject(i++, end);
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (Exception e) {
            LOGGER.error("GuardBuyRecorder flush error", e);
            return false;
        }
    }
}
