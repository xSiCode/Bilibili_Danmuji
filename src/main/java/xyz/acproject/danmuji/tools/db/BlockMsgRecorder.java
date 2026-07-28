package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.danmu_data.BlockMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 禁言 DB 记录器 — ROOM_BLOCK_MSG 事件写入 block_msg 表。
 */
public class BlockMsgRecorder {
    private static final Logger LOGGER = LogManager.getLogger(BlockMsgRecorder.class);
    private static final LinkedBlockingQueue<BlockMessage> queue = new LinkedBlockingQueue<>(5000);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    static {
        Thread writer = new Thread(() -> {
            List<BlockMessage> batch = new ArrayList<>();
            while (true) {
                try {
                    BlockMessage first = queue.poll(1, TimeUnit.SECONDS);
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
        }, "db-block-msg-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private BlockMsgRecorder() {}

    public static void record(BlockMessage bm) {
        if (bm == null || bm.getUid() == null) {
            LOGGER.warn("BlockMsgRecorder: dropped null uid");
            return;
        }
        if (!queue.offer(bm)) {
            LOGGER.warn("BlockMsgRecorder: queue full, dropping block from={}", bm.getUname());
        }
    }

    private static void flushBatchWithRetry(List<BlockMessage> batch) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (flushBatch(batch)) return;
            LOGGER.warn("BlockMsgRecorder: flush failed (attempt {}), retrying", attempt + 1);
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException e) { break; }
        }
        int requeued = 0;
        for (BlockMessage bm : batch) {
            if (queue.offer(bm)) requeued++;
        }
        LOGGER.warn("BlockMsgRecorder: requeued {}/{} after all retries", requeued, batch.size());
    }

    private static boolean flushBatch(List<BlockMessage> batch) {
        if (batch.isEmpty()) return true;
        String sql =
            "INSERT INTO block_msg(room_id,anchor_name,uid,uname,operator,timestamp) " +
            "VALUES (?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (BlockMessage bm : batch) {
                int i = 1;
                ps.setObject(i++, PublicDataConf.ROOMID);
                ps.setString(i++, PublicDataConf.ANCHOR_NAME);
                ps.setLong(i++, bm.getUid());
                ps.setString(i++, bm.getUname());
                ps.setObject(i++, bm.getOperator());
                ps.setLong(i++, System.currentTimeMillis());
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (Exception e) {
            LOGGER.error("BlockMsgRecorder flush error", e);
            return false;
        }
    }
}
