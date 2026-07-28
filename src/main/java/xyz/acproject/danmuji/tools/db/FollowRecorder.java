package xyz.acproject.danmuji.tools.db;

import com.alibaba.fastjson.JSON;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.danmu_data.Interact;
import xyz.acproject.danmuji.entity.superchat.MedalInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 关注事件 DB 记录器 — msg_type=2 时记录到 follow_events 表。
 */
public class FollowRecorder {
    private static final Logger LOGGER = LogManager.getLogger(FollowRecorder.class);
    private static final LinkedBlockingQueue<Interact> queue = new LinkedBlockingQueue<>(20000);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    static {
        Thread writer = new Thread(() -> {
            List<Interact> batch = new ArrayList<>();
            while (true) {
                try {
                    Interact first = queue.poll(1, TimeUnit.SECONDS);
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
        }, "db-follow-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private FollowRecorder() {}

    public static void record(Interact interact) {
        if (interact == null || interact.getUid() == null) {
            LOGGER.warn("FollowRecorder: dropped null uid");
            return;
        }
        if (!queue.offer(interact)) {
            LOGGER.warn("FollowRecorder: queue full, dropping follow from={}", interact.getUname());
        }
    }

    private static void flushBatchWithRetry(List<Interact> batch) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (flushBatch(batch)) return;
            LOGGER.warn("FollowRecorder: flush failed (attempt {}), retrying", attempt + 1);
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException e) { break; }
        }
        int requeued = 0;
        for (Interact it : batch) {
            if (queue.offer(it)) requeued++;
        }
        LOGGER.warn("FollowRecorder: requeued {}/{} after all retries", requeued, batch.size());
    }

    private static boolean flushBatch(List<Interact> batch) {
        if (batch.isEmpty()) return true;
        String sql =
            "INSERT INTO follow_events(room_id,anchor_name,uid,uname,uname_color,timestamp," +
            "score,medal_level,medal_name,medal_anchor,medal_room,medal_color,guard_level,is_lighted,identities) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Interact it : batch) {
                MedalInfo m = it.getFans_medal();
                int i = 1;
                ps.setObject(i++, it.getRoomid() != null ? it.getRoomid() : PublicDataConf.ROOMID);
                ps.setString(i++, PublicDataConf.ANCHOR_NAME);
                ps.setLong(i++, it.getUid());
                ps.setString(i++, it.getUname());
                ps.setString(i++, it.getUname_color());
                // INTERACT_WORD_V2 时间戳是秒，转为毫秒存储
                Long ts = it.getTimestamp();
                if (ts == null) ts = System.currentTimeMillis();
                else if (ts < 100000000000L) ts = ts * 1000;
                ps.setLong(i++, ts);
                ps.setObject(i++, it.getScore());
                ps.setObject(i++, m != null ? m.getMedal_level() : null);
                ps.setString(i++, m != null ? m.getMedal_name() : null);
                ps.setString(i++, m != null ? m.getAnchor_uname() : null);
                ps.setObject(i++, m != null ? parseLongSafe(m.getAnchor_roomid()) : null);
                ps.setString(i++, m != null ? m.getMedal_color() : null);
                ps.setInt(i++, m != null && m.getGuard_level() != null ? m.getGuard_level() : 0);
                ps.setInt(i++, m != null && m.getIs_lighted() != null ? m.getIs_lighted() : 0);
                ps.setString(i++, it.getIdentities() != null ? JSON.toJSONString(it.getIdentities()) : null);
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (Exception e) {
            LOGGER.error("FollowRecorder flush error", e);
            return false;
        }
    }

    private static Long parseLongSafe(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}
