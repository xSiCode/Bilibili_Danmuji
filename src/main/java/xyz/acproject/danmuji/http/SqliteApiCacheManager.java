package xyz.acproject.danmuji.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQLite 持久化 API 响应缓存管理器。
 * 为 processFollowings 的 5 个 HTTP 请求（勋章墙、用户卡片、关注列表双页、用户动态）
 * 提供基于 SQLite 的缓存，key 为 vmid + API 类型。
 * <p>
 * 清理策略：无 TTL，仅通过条数阈值 + 命中次数控制。
 * 超过阈值时优先删除 hit_count=1 的记录（即从未再次被查询到的观众数据）。
 *
 * @author xsicode
 */
public class SqliteApiCacheManager {

    private static final Logger LOGGER = LogManager.getLogger(SqliteApiCacheManager.class);

    /** 缓存条目上限，超过则淘汰 hit_count=1 的低频记录  改为10万条 */
    private static final int MAX_CACHE_ENTRIES = 10_0000;

    /** 缓存命中统计 */
    private static final AtomicLong hitCount = new AtomicLong(0);

    /** 缓存未命中统计 */
    private static final AtomicLong missCount = new AtomicLong(0);

    private SqliteApiCacheManager() {
        // 工具类，禁止实例化
    }

    // ==================== 缓存键生成 ====================

    public static String medalWallKey(long vmid) {
        return "medal_wall:" + vmid;
    }

    public static String cardKey(long vmid) {
        return "card:" + vmid;
    }

    public static String followingsKey(long vmid, int page) {
        return "followings:" + vmid + ":" + page;
    }

    public static String dynamicKey(long vmid) {
        return "dynamic:" + vmid;
    }

    /**
     * 根据 cacheKey 反推 api_type。
     */
    private static String apiTypeFromKey(String cacheKey) {
        if (cacheKey == null) return "unknown";
        int idx = cacheKey.indexOf(':');
        return idx > 0 ? cacheKey.substring(0, idx) : "unknown";
    }

    /**
     * 根据 cacheKey 反推 vmid。
     */
    private static long vmidFromKey(String cacheKey) {
        if (cacheKey == null) return 0;
        String[] parts = cacheKey.split(":");
        if (parts.length >= 2) {
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    // ==================== 缓存操作 ====================

    /**
     * 从 SQLite 获取缓存数据（无 TTL，只要存在即命中）。
     *
     * @param cacheKey 缓存键
     * @return 缓存的响应体 JSON 字符串，未命中返回 null
     */
    public static String get(String cacheKey) {
        if (cacheKey == null) return null;

        String sql = "SELECT response_body FROM api_cache WHERE cache_key = ?";

        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cacheKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    hitCount.incrementAndGet();
                    if (hitCount.get() % 100 == 0) {
                        LOGGER.debug("SqliteApiCache 命中: {} (总计命中 {})", cacheKey, hitCount.get());
                    }
                    // 异步递增 hit_count，不阻塞返回
                    final String key = cacheKey;
                    CompletableFuture.runAsync(() -> incrementHitCount(key));
                    return rs.getString("response_body");
                }
            }
        } catch (Exception e) {
            LOGGER.error("SqliteApiCache get 失败: {}", cacheKey, e);
        }

        missCount.incrementAndGet();
        return null;
    }

    /**
     * 将数据存入 SQLite 缓存。
     *
     * @param cacheKey     缓存键
     * @param responseBody HTTP 响应体 JSON 字符串
     */
    public static void put(String cacheKey, String responseBody) {
        if (cacheKey == null || responseBody == null) return;

        // 上限控制：超过阈值时淘汰 hit_count=1 的记录（从未再次查询的观众）
        long count = getTotalCount();
        if (count >= MAX_CACHE_ENTRIES) {
            int evicted = evictHitCountOne();
            LOGGER.info("SqliteApiCache 容量 {} >= {}，淘汰 hit_count=1 记录: {} 条，剩余 {}",
                    count, MAX_CACHE_ENTRIES, evicted, getTotalCount());
        }

        long nowSec = System.currentTimeMillis() / 1000;
        String apiType = apiTypeFromKey(cacheKey);
        long vmid = vmidFromKey(cacheKey);

        String sql = "INSERT OR REPLACE INTO api_cache(cache_key, api_type, vmid, response_body, created_at, hit_count) VALUES (?,?,?,?,?,1)";

        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cacheKey);
            ps.setString(2, apiType);
            ps.setLong(3, vmid);
            ps.setString(4, responseBody);
            ps.setLong(5, nowSec);
            ps.executeUpdate();
            LOGGER.debug("SqliteApiCache 写入: {} ({} bytes)", cacheKey, responseBody.length());
        } catch (Exception e) {
            LOGGER.error("SqliteApiCache put 失败: {}", cacheKey, e);
        }
    }

    // ==================== 清理操作 ====================

    /**
     * 异步递增指定 key 的命中次数（fire-and-forget）。
     */
    private static void incrementHitCount(String cacheKey) {
        String sql = "UPDATE api_cache SET hit_count = hit_count + 1 WHERE cache_key = ?";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cacheKey);
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.debug("SqliteApiCache hit_count 更新失败: {}", cacheKey, e);
        }
    }

    /**
     * 淘汰所有 hit_count=1 的记录（即写入后再未被查询过的观众数据）。
     * 高频观众的 hit_count 会因反复命中而增长，不受影响。
     *
     * @return 实际删除的记录数
     */
    public static int evictHitCountOne() {
        String sql = "DELETE FROM api_cache WHERE hit_count = 1";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                LOGGER.info("SqliteApiCache 淘汰 hit_count=1 记录: {} 条，剩余 {}", deleted, getTotalCount());
            }
            return deleted;
        } catch (Exception e) {
            LOGGER.error("SqliteApiCache 淘汰 hit_count=1 记录失败", e);
            return 0;
        }
    }

    /**
     * 按前缀删除缓存条目（可用于手动清除某类 API 的全部缓存）。
     */
    public static void invalidateByPrefix(String keyPrefix) {
        if (keyPrefix == null) return;

        String sql = "DELETE FROM api_cache WHERE cache_key LIKE ?";

        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, keyPrefix + "%");
            int deleted = ps.executeUpdate();
            LOGGER.info("SqliteApiCache 按前缀清除: '{}' -> {} 条", keyPrefix, deleted);
        } catch (Exception e) {
            LOGGER.error("SqliteApiCache 按前缀清除失败: {}", keyPrefix, e);
        }
    }

    /**
     * 清空所有缓存。
     */
    public static void clearAll() {
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM api_cache")) {
            int deleted = ps.executeUpdate();
            LOGGER.info("SqliteApiCache 清空全部缓存: {} 条", deleted);
        } catch (Exception e) {
            LOGGER.error("SqliteApiCache 清空失败", e);
        }
    }

    // ==================== 统计 ====================

    public static long getTotalCount() {
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM api_cache");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            LOGGER.error("SqliteApiCache 统计失败", e);
        }
        return 0;
    }

    public static long getHitCountOne() {
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM api_cache WHERE hit_count = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    public static long getHitCount() {
        return hitCount.get();
    }

    public static long getMissCount() {
        return missCount.get();
    }

    public static double getHitRate() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    public static void resetStats() {
        hitCount.set(0);
        missCount.set(0);
    }

    @Override
    public String toString() {
        return String.format("SqliteApiCache[total=%d, hit_count_1=%d, hits=%d, misses=%d, rate=%.2f%%]",
                getTotalCount(), getHitCountOne(), hitCount.get(), missCount.get(), getHitRate() * 100);
    }
}
