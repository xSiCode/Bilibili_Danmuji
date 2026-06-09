package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.superchat.SuperChat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 醒目留言 DB 记录器 — SUPER_CHAT_MESSAGE 事件写入 super_chat 表。
 */
public class SuperChatRecorder {
    private static final Logger LOGGER = LogManager.getLogger(SuperChatRecorder.class);
    private static final LinkedBlockingQueue<SuperChat> queue = new LinkedBlockingQueue<>(5000);

    static {
        Thread writer = new Thread(() -> {
            List<SuperChat> batch = new ArrayList<>();
            while (true) {
                try {
                    SuperChat first = queue.poll(1, TimeUnit.SECONDS);
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
        }, "db-super-chat-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private SuperChatRecorder() {}

    public static void record(SuperChat sc) {
        if (sc == null || sc.getUid() == null) return;
        queue.offer(sc);
    }

    private static void flushBatch(List<SuperChat> batch) {
        if (batch.isEmpty()) return;
        String sql =
            "INSERT INTO super_chat(room_id,anchor_name,uid,uname,message,price,keep_time," +
            "start_time,end_time,gift_name,medal_level,medal_name,medal_color,background_color) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (SuperChat sc : batch) {
                // SuperChat 中时间是秒
                Long start = sc.getStart_time();
                Long end = sc.getEnd_time();
                if (start != null && start < 100000000000L) start = start * 1000;
                if (end != null && end < 100000000000L) end = end * 1000;
                String uname = sc.getUser_info() != null ? sc.getUser_info().getUname() : null;
                int i = 1;
                ps.setObject(i++, PublicDataConf.ROOMID);
                ps.setString(i++, PublicDataConf.ANCHOR_NAME);
                ps.setLong(i++, sc.getUid());
                ps.setString(i++, uname);
                ps.setString(i++, sc.getMessage());
                ps.setObject(i++, sc.getPrice());
                ps.setObject(i++, sc.getTime());
                ps.setObject(i++, start);
                ps.setObject(i++, end);
                ps.setString(i++, sc.getGift() != null ? sc.getGift().getGift_name() : null);
                if (sc.getMedal_info() != null) {
                    ps.setObject(i++, sc.getMedal_info().getMedal_level());
                    ps.setString(i++, sc.getMedal_info().getMedal_name());
                    ps.setString(i++, sc.getMedal_info().getMedal_color());
                } else {
                    ps.setNull(i++, java.sql.Types.INTEGER);
                    ps.setNull(i++, java.sql.Types.VARCHAR);
                    ps.setNull(i++, java.sql.Types.VARCHAR);
                }
                ps.setString(i++, sc.getBackground_color());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            LOGGER.error("SuperChatRecorder flush error", e);
        }
    }
}
