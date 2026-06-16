package xyz.acproject.danmuji.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQLite 持久化 API 响应缓存管理器。
 * 为 processFollowings 的 5 个 HTTP 请求（勋章墙、用户卡片、关注列表双页、用户动态）
 * 提供基于 SQLite 的缓存，key 为 vmid + API 类型。
 * 所有 API 统一 30 天 TTL，过期自动清理。
 *
 * @author xsicode
 */
public class SqliteApiCacheManager {

    private static final Logger LOGGER = LogManager.getLogger(SqliteApiCacheManager.class);

    /** 默认 TTL：30 天（秒） */
    public static final int DEFAULT_TTL_SECONDS = 2592000;

    /** 缓存条目上限，超过则触发强制清理 */
    private static final int MAX_CACHE_ENTRIES = 200_000;

    /** 定期清理间隔（分钟） */
    private static final int CLEANUP_INTERVAL_MINUTES = 30;

    /** 缓存命中统计 */
    private static final AtomicLong hitCount = new AtomicLong(0);

    /** 缓存未命中统计 */
    private static final AtomicLong missCount = new AtomicLong(0);

    /** 定期清理调度器 */
    private static final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sqlite-cache-cleanup");
                t.setDaemon(true);
                return t;
            });

    static {
        cleanupScheduler.scheduleWithFixedDelay(
                SqliteApiCacheManager::cleanupExpired,
                CLEANUP_INTERVAL_MINUTES,
                CLEANUP_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cleanupScheduler.shutdown();
            cleanupExpired();
        }, "sqlite-cache-shutdown"));
    }

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
        // 格式：api_type:vmid 或 api_type:vmid:pn
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
     * 从 SQLite 获取缓存数据。
     * 如果条目不存在或已过期，返回 null。
     *
     * @param cacheKey 缓存键
     * @return 缓存的响应体 JSON 字符串，未命中返回 null
     */
    public static String get(String cacheKey) {
        if (cacheKey == null) return null;

        long nowSec = System.currentTimeMillis() / 1000;
        String sql = "SELECT response_body, created_at, ttl_seconds FROM api_cache WHERE cache_key = ?";

        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cacheKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long createdAt = rs.getLong("created_at");
                    int ttl = rs.getInt("ttl_seconds");
                    if (nowSec < createdAt + ttl) {
                        // 未过期，命中
                        hitCount.incrementAndGet();
                        if (hitCount.get() % 100 == 0) {
                            LOGGER.debug("SqliteApiCache 命中: {} (总计命中 {})", cacheKey, hitCount.get());
                        }
                        return rs.getString("response_body");
                    }
                    // 已过期，删除并返回 null
                    LOGGER.debug("SqliteApiCache 过期: {}", cacheKey);
                    deleteExpired(cacheKey);
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
     * @param ttlSeconds   有效时长（秒）
     */
    public static void put(String cacheKey, String responseBody, int ttlSeconds) {
        if (cacheKey == null || responseBody == null) return;

        // 上限控制
        long count = getTotalCount();
        if (count >= MAX_CACHE_ENTRIES) {
            LOGGER.warn("SqliteApiCache 条目数已达上限 {}，触发强制清理", count);
            cleanupExpired();
            long afterClean = getTotalCount();
            if (afterClean >= MAX_CACHE_ENTRIES) {
                LOGGER.warn("SqliteApiCache 清理后仍超限 ({}), 跳过写入: {}", afterClean, cacheKey);
                return;
            }
        }

        long nowSec = System.currentTimeMillis() / 1000;
        String apiType = apiTypeFromKey(cacheKey);
        long vmid = vmidFromKey(cacheKey);

        String sql = "INSERT OR REPLACE INTO api_cache(cache_key, api_type, vmid, response_body, created_at, ttl_seconds) VALUES (?,?,?,?,?,?)";

        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cacheKey);
            ps.setString(2, apiType);
            ps.setLong(3, vmid);
            ps.setString(4, responseBody);
            ps.setLong(5, nowSec);
            ps.setInt(6, ttlSeconds);
            ps.executeUpdate();
            LOGGER.debug("SqliteApiCache 写入: {} ({} bytes)", cacheKey, responseBody.length());
        } catch (Exception e) {
            LOGGER.error("SqliteApiCache put 失败: {}", cacheKey, e);
        }
    }

    /**
     * 存入缓存（使用默认 30 天 TTL）。
     */
    public static void put(String cacheKey, String responseBody) {
        put(cacheKey, responseBody, DEFAULT_TTL_SECONDS);
    }

    // ==================== 清理操作 ====================

    /**
     * 清理所有过期条目。
     * 由定时调度器和 shutdown hook 调用。
     */
    public static void cleanupExpired() {
        long nowSec = System.currentTimeMillis() / 1000;
        String sql = "DELETE FROM api_cache WHERE created_at + ttl_seconds < ?";

        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, nowSec);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                LOGGER.info("SqliteApiCache 过期清理完成，删除了 {} 条过期记录", deleted);
            }
        } catch (Exception e) {
            LOGGER.error("SqliteApiCache 过期清理失败", e);
        }
    }

    /**
     * 按前缀删除缓存条目（可用于手动清除某类 API 的全部缓存）。
     * 注意：SQLite LIKE 走全表扫描，大量数据时较慢，仅用于手动操作。
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
     * 删除单条过期记录。
     */
    private static void deleteExpired(String cacheKey) {
        String sql = "DELETE FROM api_cache WHERE cache_key = ?";
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cacheKey);
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.debug("SqliteApiCache 删除过期条目失败: {}", cacheKey, e);
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
        return String.format("SqliteApiCache[total=%d, hits=%d, misses=%d, rate=%.2f%%]",
                getTotalCount(), hitCount.get(), missCount.get(), getHitRate() * 100);
    }
}
