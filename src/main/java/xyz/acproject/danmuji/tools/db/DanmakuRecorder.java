package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.danmu_data.Barrage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 弹幕 DB 记录器 — 每条弹幕追加写入 danmaku 表。
 * 批量写入架构，不阻塞 ParseMessageThread 热路径。
 */
public class DanmakuRecorder {
    private static final Logger LOGGER = LogManager.getLogger(DanmakuRecorder.class);
    private static final LinkedBlockingQueue<Barrage> queue = new LinkedBlockingQueue<>(20000);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    static {
        Thread writer = new Thread(() -> {
            List<Barrage> batch = new ArrayList<>();
            while (true) {
                try {
                    Barrage first = queue.poll(1, TimeUnit.SECONDS);
                    if (first != null) {
                        batch.add(first);
                        queue.drainTo(batch, 500);
                        flushBatchWithRetry(batch);
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "db-danmaku-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private DanmakuRecorder() {}

    public static void record(Barrage barrage) {
        if (barrage == null || barrage.getUid() == null) {
            LOGGER.warn("DanmakuRecorder: dropped null uid");
            return;
        }
        if (!queue.offer(barrage)) {
            LOGGER.warn("DanmakuRecorder: queue full, dropping danmaku from={}", barrage.getUname());
        }
    }

    private static void flushBatchWithRetry(List<Barrage> batch) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (flushBatch(batch)) return;
            LOGGER.warn("DanmakuRecorder: flush failed (attempt {}), retrying", attempt + 1);
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException e) { break; }
        }
        int requeued = 0;
        for (Barrage b : batch) {
            if (queue.offer(b)) requeued++;
        }
        LOGGER.warn("DanmakuRecorder: requeued {}/{} after all retries", requeued, batch.size());
    }

    private static boolean flushBatch(List<Barrage> batch) {
        if (batch.isEmpty()) return true;
        String sql =
            "INSERT INTO danmaku(room_id,anchor_name,uid,uname,content,msg_type,is_emoticon," +
            "emoticon_name,emoticon_url,vip,svip,manager,uidentity,iphone,guard_level," +
            "medal_level,medal_name,medal_anchor,medal_room,ulevel,ulevel_rank,old_title,title,timestamp) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Barrage b : batch) {
                int i = 1;
                ps.setObject(i++, PublicDataConf.ROOMID);
                ps.setString(i++, PublicDataConf.ANCHOR_NAME);
                ps.setLong(i++, b.getUid());
                ps.setString(i++, b.getUname());
                ps.setString(i++, b.getMsg());
                ps.setInt(i++, b.getMsg_type() != null ? b.getMsg_type() : 0);
                ps.setInt(i++, b.getMsg_emoticon() != null ? b.getMsg_emoticon() : 0);
                ps.setString(i++, b.getMsg_emoticon_name());
                ps.setString(i++, b.getMsg_emoticon_url());
                ps.setInt(i++, b.getVip() != null ? b.getVip() : 0);
                ps.setInt(i++, b.getSvip() != null ? b.getSvip() : 0);
                ps.setInt(i++, b.getManager() != null ? b.getManager() : 0);
                ps.setObject(i++, b.getUidentity());
                ps.setInt(i++, b.getIphone() != null ? b.getIphone() : 0);
                ps.setInt(i++, b.getUguard() != null ? b.getUguard() : 0);
                ps.setObject(i++, b.getMedal_level());
                ps.setString(i++, b.getMedal_name());
                ps.setString(i++, b.getMedal_anchor());
                ps.setObject(i++, b.getMedal_room());
                ps.setObject(i++, b.getUlevel());
                ps.setString(i++, b.getUlevel_rank());
                ps.setString(i++, b.getOld_title());
                ps.setString(i++, b.getTitle());
                ps.setLong(i++, b.getTimestamp() != null ? b.getTimestamp() : System.currentTimeMillis());
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (Exception e) {
            LOGGER.error("DanmakuRecorder flush error", e);
            return false;
        }
    }
}
