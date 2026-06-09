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
 * 进入事件 DB 记录器 — msg_type=1 时记录到 enter_events 表。
 */
public class EnterRecorder {
    private static final Logger LOGGER = LogManager.getLogger(EnterRecorder.class);
    private static final LinkedBlockingQueue<Interact> queue = new LinkedBlockingQueue<>(20000);

    static {
        Thread writer = new Thread(() -> {
            List<Interact> batch = new ArrayList<>();
            while (true) {
                try {
                    Interact first = queue.poll(1, TimeUnit.SECONDS);
                    if (first != null) {
                        batch.add(first);
                        queue.drainTo(batch, 500);
                        flushBatch(batch);
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "db-enter-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private EnterRecorder() {}

    public static void record(Interact interact) {
        if (interact == null || interact.getUid() == null) return;
        queue.offer(interact);
    }

    private static void flushBatch(List<Interact> batch) {
        if (batch.isEmpty()) return;
        String sql =
            "INSERT INTO enter_events(room_id,anchor_name,uid,uname,uname_color,timestamp," +
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
        } catch (Exception e) {
            LOGGER.error("EnterRecorder flush error", e);
        }
    }

    private static Long parseLongSafe(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}
