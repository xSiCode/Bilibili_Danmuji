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

    static {
        Thread writer = new Thread(() -> {
            List<WelcomeGuard> batch = new ArrayList<>();
            while (true) {
                try {
                    WelcomeGuard first = queue.poll(1, TimeUnit.SECONDS);
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
        }, "db-welcome-guard-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private WelcomeGuardRecorder() {}

    public static void record(WelcomeGuard wg) {
        if (wg == null || wg.getUid() == null) return;
        queue.offer(wg);
    }

    private static void flushBatch(List<WelcomeGuard> batch) {
        if (batch.isEmpty()) return;
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
        } catch (Exception e) {
            LOGGER.error("WelcomeGuardRecorder flush error", e);
        }
    }
}
