package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 点赞记录 DB 记录器 — LIKE_INFO_V3_CLICK 消息时记录到 like_record 表。
 * 记录点赞人 uid/uname、直播间 room_id/room_name、主播 ruid/anchor_name。
 */
public class LikeRecorder {
    private static final Logger LOGGER = LogManager.getLogger(LikeRecorder.class);
    private static final LinkedBlockingQueue<LikeEntry> queue = new LinkedBlockingQueue<>(20000);

    static {
        Thread writer = new Thread(() -> {
            List<LikeEntry> batch = new ArrayList<>();
            while (true) {
                try {
                    LikeEntry first = queue.poll(1, TimeUnit.SECONDS);
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
        }, "db-like-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private LikeRecorder() {}

    /**
     * 记录一条点赞。
     * @param uid        点赞人 UID
     * @param uname      点赞人名字
     * @param ruid       主播 UID（被点赞的直播间主播）
     * @param roomId     直播间 ID
     * @param roomName   直播间名字
     * @param anchorName 主播名字
     * @param timestamp  点赞时间戳（毫秒）
     */
    public static void record(Long uid, String uname, Long ruid, Long roomId,
                              String roomName, String anchorName, Long timestamp) {
        if (uid == null) return;
        queue.offer(new LikeEntry(uid, uname, ruid, roomId, roomName, anchorName, timestamp));
    }

    private static void flushBatch(List<LikeEntry> batch) {
        if (batch.isEmpty()) return;
        String sql =
            "INSERT INTO like_record(room_id,room_name,anchor_name,uid,uname,ruid,timestamp) " +
            "VALUES (?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (LikeEntry e : batch) {
                int i = 1;
                ps.setLong(i++, e.roomId != null ? e.roomId : 0L);
                ps.setString(i++, e.roomName);
                ps.setString(i++, e.anchorName);
                ps.setLong(i++, e.uid);
                ps.setString(i++, e.uname);
                ps.setObject(i++, e.ruid);
                ps.setLong(i++, e.timestamp != null ? e.timestamp : System.currentTimeMillis());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception ex) {
            LOGGER.error("LikeRecorder flush error", ex);
        }
    }

    /**
     * 点赞数据项。
     */
    private static class LikeEntry {
        final Long uid;
        final String uname;
        final Long ruid;
        final Long roomId;
        final String roomName;
        final String anchorName;
        final Long timestamp;

        LikeEntry(Long uid, String uname, Long ruid, Long roomId,
                  String roomName, String anchorName, Long timestamp) {
            this.uid = uid;
            this.uname = uname;
            this.ruid = ruid;
            this.roomId = roomId;
            this.roomName = roomName;
            this.anchorName = anchorName;
            this.timestamp = timestamp;
        }
    }
}
