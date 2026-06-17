package xyz.acproject.danmuji.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨直播间观众活动摘要工具。
 * 查询所有事件表，按主播聚合各事件类型的计数，生成紧凑摘要字符串。
 */
public class ViewerActivitySummary {
    private static final Logger LOGGER = LogManager.getLogger(ViewerActivitySummary.class);

    /** 简单缓存：避免短时间内对同一 uid 重复查询。 */
    private static final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 120_000; // 2 分钟

    private static class CacheEntry {
        final String summary;
        final long timestamp;
        CacheEntry(String summary, long timestamp) {
            this.summary = summary;
            this.timestamp = timestamp;
        }
    }

    private ViewerActivitySummary() {}

    /**
     * 为指定 uid 构建跨直播间活动摘要。
     * @return 摘要字符串，如 "[主播A:弹幕3,进入2,送礼1] [主播B:弹幕1] 等更多直播间"；无数据返回 ""
     */
    public static String buildSummary(long uid) {
        if (uid <= 0) return "";

        // 1. 检查缓存
        CacheEntry cached = cache.get(uid);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.summary;
        }

        List<RoomSummary> rooms = new ArrayList<>();
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(buildSql())) {
            for (int i = 1; i <= 8; i++) {
                ps.setLong(i, uid);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String anchor = rs.getString("anchor_name");
                    int d  = rs.getInt("danmaku");
                    int en = rs.getInt("enter_events");
                    int g  = rs.getInt("gift");
                    int l  = rs.getInt("likes");
                    int f  = rs.getInt("follow");
                    int gb = rs.getInt("guard");
                    int sc = rs.getInt("sc");
                    int fp = rs.getInt("footprint");
                    int total = d + en + g + l + f + gb + sc + fp;
                    if (total > 0) {
                        rooms.add(new RoomSummary(anchor, d, en, g, l, f, gb, sc, fp, total));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ViewerActivitySummary.buildSummary error for uid={}: {}", uid, e.getMessage());
            // 将空结果也缓存下来，避免持续查询失败的 uid
            String empty = "";
            cache.put(uid, new CacheEntry(empty, System.currentTimeMillis()));
            return empty;
        }

        String summary = formatSummary(rooms);
        cache.put(uid, new CacheEntry(summary, System.currentTimeMillis()));
        return summary;
    }

    // ---- SQL ----

    private static String buildSql() {
        return "SELECT anchor_name," +
            " SUM(CASE WHEN src='danmaku' THEN cnt ELSE 0 END) AS danmaku," +
            " SUM(CASE WHEN src='enter'   THEN cnt ELSE 0 END) AS enter_events," +
            " SUM(CASE WHEN src='gift'    THEN cnt ELSE 0 END) AS gift," +
            " SUM(CASE WHEN src='like'    THEN cnt ELSE 0 END) AS likes," +
            " SUM(CASE WHEN src='follow'  THEN cnt ELSE 0 END) AS follow," +
            " SUM(CASE WHEN src='guard'   THEN cnt ELSE 0 END) AS guard," +
            " SUM(CASE WHEN src='sc'      THEN cnt ELSE 0 END) AS sc," +
            " SUM(CASE WHEN src='footprint' THEN cnt ELSE 0 END) AS footprint" +
            " FROM (" +
            "  SELECT COALESCE(NULLIF(anchor_name,''),'未知') AS anchor_name, 'danmaku' AS src, COUNT(*) AS cnt" +
            "    FROM danmaku WHERE uid=? GROUP BY anchor_name" +
            "  UNION ALL" +
            "  SELECT COALESCE(NULLIF(anchor_name,''),'未知'), 'enter', COUNT(*)" +
            "    FROM enter_events WHERE uid=? GROUP BY anchor_name" +
            "  UNION ALL" +
            "  SELECT COALESCE(NULLIF(anchor_name,''),'未知'), 'gift', COUNT(*)" +
            "    FROM gift_detail WHERE uid=? GROUP BY anchor_name" +
            "  UNION ALL" +
            "  SELECT COALESCE(NULLIF(anchor_name,''),COALESCE(NULLIF(room_name,''),'未知')), 'like', COUNT(*)" +
            "    FROM like_record WHERE uid=? GROUP BY anchor_name, room_name" +
            "  UNION ALL" +
            "  SELECT COALESCE(NULLIF(anchor_name,''),'未知'), 'follow', COUNT(*)" +
            "    FROM follow_events WHERE uid=? GROUP BY anchor_name" +
            "  UNION ALL" +
            "  SELECT COALESCE(NULLIF(anchor_name,''),'未知'), 'guard', COUNT(*)" +
            "    FROM guard_buy WHERE uid=? GROUP BY anchor_name" +
            "  UNION ALL" +
            "  SELECT COALESCE(NULLIF(anchor_name,''),'未知'), 'sc', COUNT(*)" +
            "    FROM super_chat WHERE uid=? GROUP BY anchor_name" +
            "  UNION ALL" +
            "  SELECT COALESCE(NULLIF(anchor_name,''),'未知'), 'footprint', COUNT(*)" +
            "    FROM footprint WHERE uid=? GROUP BY anchor_name" +
            " ) GROUP BY anchor_name" +
            " ORDER BY (" +
            "  SUM(CASE WHEN src='danmaku' THEN cnt ELSE 0 END)+" +
            "  SUM(CASE WHEN src='enter'   THEN cnt ELSE 0 END)+" +
            "  SUM(CASE WHEN src='gift'    THEN cnt ELSE 0 END)+" +
            "  SUM(CASE WHEN src='like'    THEN cnt ELSE 0 END)+" +
            "  SUM(CASE WHEN src='follow'  THEN cnt ELSE 0 END)+" +
            "  SUM(CASE WHEN src='guard'   THEN cnt ELSE 0 END)+" +
            "  SUM(CASE WHEN src='sc'      THEN cnt ELSE 0 END)+" +
            "  SUM(CASE WHEN src='footprint' THEN cnt ELSE 0 END)" +
            " ) DESC" +
            " LIMIT 4";
    }

    // ---- 格式化 ----

    private static String formatSummary(List<RoomSummary> rooms) {
        if (rooms == null || rooms.isEmpty()) return "";

        StringBuilder sb = new StringBuilder( );
        int show = Math.min(rooms.size(), 5);
        for (int i = 0; i < show; i++) {
            RoomSummary r = rooms.get(i);
            sb.append("[").append(r.anchorName).append(":");
            List<String> parts = new ArrayList<>();
            if (r.danmaku > 0)   parts.add("弹幕" + r.danmaku);
            if (r.enterEvents > 0) parts.add("进入" + r.enterEvents);
            if (r.gift > 0)      parts.add("送礼" + r.gift);
            if (r.likes > 0)     parts.add("点赞" + r.likes);
            if (r.follow > 0)    parts.add("关注" + r.follow);
            if (r.guard > 0)     parts.add("上舰" + r.guard);
            if (r.sc > 0)        parts.add("SC" + r.sc);
            if (r.footprint > 0) parts.add("足迹" + r.footprint);
            if (parts.isEmpty()) parts.add("活跃" + r.total);
            sb.append(String.join(",", parts));
            sb.append("]  ");
        }
        if (rooms.size() > 5) {
            sb.append("等更多直播间");
        }
        return sb.toString().trim();
    }

    // ---- 数据类 ----

    private static class RoomSummary {
        final String anchorName;
        final int danmaku, enterEvents, gift, likes, follow, guard, sc, footprint, total;

        RoomSummary(String anchorName, int danmaku, int enterEvents, int gift, int likes,
                    int follow, int guard, int sc, int footprint, int total) {
            this.anchorName = anchorName;
            this.danmaku = danmaku;
            this.enterEvents = enterEvents;
            this.gift = gift;
            this.likes = likes;
            this.follow = follow;
            this.guard = guard;
            this.sc = sc;
            this.footprint = footprint;
            this.total = total;
        }
    }
}
