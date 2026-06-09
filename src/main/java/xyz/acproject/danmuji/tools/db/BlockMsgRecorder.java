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

    static {
        Thread writer = new Thread(() -> {
            List<BlockMessage> batch = new ArrayList<>();
            while (true) {
                try {
                    BlockMessage first = queue.poll(1, TimeUnit.SECONDS);
                    if (first != null) {
                        batch.add(first);
                        queue.drainTo(batch, 200);
                        flushBatch(batch);
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
        if (bm == null || bm.getUid() == null) return;
        queue.offer(bm);
    }

    private static void flushBatch(List<BlockMessage> batch) {
        if (batch.isEmpty()) return;
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
        } catch (Exception e) {
            LOGGER.error("BlockMsgRecorder flush error", e);
        }
    }
}
