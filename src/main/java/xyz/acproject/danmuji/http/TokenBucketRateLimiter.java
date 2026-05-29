package xyz.acproject.danmuji.http;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 令牌桶限流器 — 主动控制 API 请求频率，避免触发平台封禁。
 * 线程安全，支持阻塞 acquire() 和非阻塞 tryAcquire()。
 *
 * @author BanqiJane
 */
public class TokenBucketRateLimiter {

    /** 每秒生成的令牌数 */
    private final double permitsPerSecond;

    /** 最大突发令牌数（允许的短时突发量） */
    private final double maxBurstTokens;

    /** 当前可用令牌数 */
    private double currentTokens;

    /** 上次补充令牌的时间（纳秒） */
    private volatile long lastRefillNanos;

    /** 统计：总请求数 */
    private final AtomicLong totalRequests = new AtomicLong(0);

    /** 统计：被限流跳过的请求数 */
    private final AtomicLong throttledRequests = new AtomicLong(0);

    /**
     * @param permitsPerSecond 每秒允许的请求数
     */
    public TokenBucketRateLimiter(double permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
        // 突发容量 = 1秒的令牌量，至少允许1个突发
        this.maxBurstTokens = Math.max(permitsPerSecond, 1.0);
        this.currentTokens = maxBurstTokens;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * @param permitsPerSecond 每秒允许的请求数
     * @param maxBurstTokens   最大突发令牌数
     */
    public TokenBucketRateLimiter(double permitsPerSecond, double maxBurstTokens) {
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurstTokens = maxBurstTokens;
        this.currentTokens = maxBurstTokens;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * 补充令牌（基于时间流逝）
     */
    private synchronized void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        // 限制最大补充量，防止长时间空闲后突发大量请求
        currentTokens = Math.min(maxBurstTokens,
                currentTokens + elapsedSeconds * permitsPerSecond);
        lastRefillNanos = now;
    }

    /**
     * 非阻塞获取一个令牌。
     *
     * @return true 如果获取成功，false 如果当前没有可用令牌
     */
    public synchronized boolean tryAcquire() {
        refill();
        if (currentTokens >= 1.0) {
            currentTokens -= 1.0;
            totalRequests.incrementAndGet();
            return true;
        }
        throttledRequests.incrementAndGet();
        return false;
    }

    /**
     * 阻塞等待直到获取一个令牌。
     * 使用 LockSupport.parkNanos 进行细粒度等待，不占用 CPU。
     */
    public void acquire() {
        while (true) {
            if (tryAcquire()) {
                return;
            }
            // 计算需要等待的时间（纳秒）
            long waitNanos;
            synchronized (this) {
                double needed = 1.0 - currentTokens;
                waitNanos = (long) (needed / permitsPerSecond * 1_000_000_000L);
            }
            // 至少等 50ms，避免高频自旋
            waitNanos = Math.max(waitNanos, 50_000_000L);
            // 最多等 1秒（防止极端情况长时间阻塞）
            waitNanos = Math.min(waitNanos, 1_000_000_000L);
            LockSupport.parkNanos(waitNanos);
        }
    }

    /**
     * 阻塞等待获取令牌，带超时。
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true 如果在超时前获取到令牌，false 如果超时
     */
    public boolean acquire(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (true) {
            if (tryAcquire()) {
                return true;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            long waitNanos = Math.min(remaining, 100_000_000L); // 最多等100ms再检查
            LockSupport.parkNanos(waitNanos);
        }
    }

    /**
     * 获取当前可用的令牌数（用于监控）
     */
    public synchronized double getAvailableTokens() {
        refill();
        return currentTokens;
    }

    /**
     * 获取总请求数
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * 获取被限流跳过的请求数
     */
    public long getThrottledRequests() {
        return throttledRequests.get();
    }

    /**
     * 获取配置的速率
     */
    public double getPermitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * 重置统计计数器
     */
    public void resetStats() {
        totalRequests.set(0);
        throttledRequests.set(0);
    }

    @Override
    public String toString() {
        return String.format("TokenBucket[rate=%.2f/s, available=%.2f, total=%d, throttled=%d]",
                permitsPerSecond, getAvailableTokens(), totalRequests.get(), throttledRequests.get());
    }
}
