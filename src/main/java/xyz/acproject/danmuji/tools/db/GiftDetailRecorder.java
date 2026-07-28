package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.danmu_data.Gift;
import xyz.acproject.danmuji.entity.danmu_data.RedPackage;
import xyz.acproject.danmuji.entity.superchat.MedalInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 礼物明细 DB 记录器 — 每条送礼记录追加到 gift_detail 表。
 * 支持 SEND_GIFT 和 POPULARITY_RED_POCKET_NEW 两种来源。
 */
public class GiftDetailRecorder {
    private static final Logger LOGGER = LogManager.getLogger(GiftDetailRecorder.class);
    private static final LinkedBlockingQueue<Gift> queue = new LinkedBlockingQueue<>(20000);
    private static final int MAX_RETRIES = 3;          // 单批失败最大重试次数
    private static final long RETRY_DELAY_MS = 500;    // 重试间隔

    static {
        Thread writer = new Thread(() -> {
            List<Gift> batch = new ArrayList<>();
            while (true) {
                try {
                    Gift first = queue.poll(1, TimeUnit.SECONDS);
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
        }, "db-gift-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private GiftDetailRecorder() {}

    public static void record(Gift gift) {
        if (gift == null || gift.getUid() == null) {
            LOGGER.warn("GiftDetailRecorder: dropped gift with null uid name={}",
                    gift != null ? gift.getGiftName() : "null_gift");
            return;
        }
        if (!queue.offer(gift)) {
            LOGGER.warn("GiftDetailRecorder: queue full, dropping gift name={} from={}",
                    gift.getGiftName(), gift.getUname());
        }
    }

    /**
     * 记录红包礼物（从 RedPackage 字段组装为 Gift 再写入）
     */
    public static void recordRedPackage(RedPackage rp) {
        if (rp == null || rp.getUid() == null) return;
        Gift g = new Gift();
        g.setUid(rp.getUid());
        g.setUname(rp.getUname());
        g.setGiftName(rp.getGift_name());
        g.setGiftId(rp.getGift_id() != null ? rp.getGift_id().intValue() : null);
        g.setNum(rp.getNum());
        g.setPrice(rp.getPrice());
        g.setAction(rp.getAction());
        g.setTimestamp(rp.getStart_time() != null ? rp.getStart_time() * 1000 : null);
        g.setCoin_type((short) 1); // 红包都是金瓜子
        g.setTotal_coin(rp.getNum() != null && rp.getPrice() != null ?
                (long) rp.getNum() * rp.getPrice() : null);
        g.setMedal_info(rp.getMedal_info());
        if (!queue.offer(g)) {
            LOGGER.warn("GiftDetailRecorder: queue full, dropping red package name={} from={}",
                    g.getGiftName(), g.getUname());
        }
    }

    private static void flushBatchWithRetry(List<Gift> batch) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (flushBatch(batch)) return;
            LOGGER.warn("GiftDetailRecorder: batch flush failed (attempt {}), retrying in {}ms",
                    attempt + 1, RETRY_DELAY_MS);
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException e) { break; }
        }
        // 全部重试失败 → 放回队列，不丢
        LOGGER.error("GiftDetailRecorder: all {} retries failed, requeuing {} gifts",
                MAX_RETRIES, batch.size());
        int requeued = 0;
        for (Gift g : batch) {
            if (queue.offer(g)) requeued++;
        }
        LOGGER.warn("GiftDetailRecorder: requeued {}/{} gifts for retry", requeued, batch.size());
    }

    private static boolean flushBatch(List<Gift> batch) {
        if (batch.isEmpty()) return true;
        String sql =
            "INSERT INTO gift_detail(room_id,anchor_name,uid,uname,face,gift_id,gift_name,gift_type," +
            "num,price,total_coin,coin_type,action,guard_level,medal_level,medal_name,medal_anchor,medal_color,timestamp,source) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Gift g : batch) {
                MedalInfo m = g.getMedal_info();
                Long ts = g.getTimestamp();
                if (ts != null && ts < 100000000000L) ts = ts * 1000;
                int i = 1;
                ps.setObject(i++, PublicDataConf.ROOMID);
                ps.setString(i++, PublicDataConf.ANCHOR_NAME);
                ps.setLong(i++, g.getUid());
                ps.setString(i++, g.getUname());
                ps.setString(i++, g.getFace());
                ps.setObject(i++, g.getGiftId());
                ps.setString(i++, g.getGiftName());
                ps.setObject(i++, g.getGiftType());
                ps.setInt(i++, g.getNum() != null ? g.getNum() : 1);
                ps.setObject(i++, g.getPrice());
                ps.setObject(i++, g.getTotal_coin());
                ps.setInt(i++, g.getCoin_type() != null ? g.getCoin_type() : 0);
                ps.setString(i++, g.getAction());
                ps.setInt(i++, g.getGuard_level() != null ? g.getGuard_level() : 0);
                ps.setObject(i++, m != null ? m.getMedal_level() : null);
                ps.setString(i++, m != null ? m.getMedal_name() : null);
                ps.setString(i++, m != null ? m.getAnchor_uname() : null);
                ps.setString(i++, m != null ? m.getMedal_color() : null);
                ps.setLong(i++, ts != null ? ts : System.currentTimeMillis());
                ps.setString(i++, "gift");
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (Exception e) {
            LOGGER.error("GiftDetailRecorder flush error (SQLITE_BUSY?), batch size={}", batch.size(), e);
            return false;
        }
    }
}
