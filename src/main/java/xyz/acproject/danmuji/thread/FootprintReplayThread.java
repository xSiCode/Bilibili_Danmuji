package xyz.acproject.danmuji.thread;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.CenterSetConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.FootprintRecord;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.SessionMeta;
import xyz.acproject.danmuji.thread.core.ParseMessageThread;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 足迹还原重放线程
 * 支持双速模式：
 *   TIME_MULTIPLIER — 倍数播放（基于原始时间戳间隔）
 *   FIXED_RATE      — 定速播放（每秒 N 人）
 * 支持暂停/恢复/停止控制
 * 支持独立运行（无需活跃直播间连接）
 * 重放期间临时替换直播间上下文（ROOMID/ANCHOR_NAME/AUID），结束后恢复
 */
public class FootprintReplayThread extends Thread {
    private static final Logger LOGGER = LogManager.getLogger(FootprintReplayThread.class);

    public enum SpeedMode {
        TIME_MULTIPLIER,  // 倍数播放
        FIXED_RATE        // 定速播放
    }

    private final List<FootprintRecord> records;
    private final ParseMessageThread parseThread;
    private final SessionMeta sessionMeta;

    private volatile SpeedMode speedMode = SpeedMode.TIME_MULTIPLIER;
    private volatile double speedValue = 1.0;
    private volatile boolean paused = false;
    private volatile boolean stopped = false;
    private volatile int currentIndex = 0;
    private volatile long startTimeMs;
    private volatile long baseTimestamp;
    private volatile String currentUname = "";
    private volatile long currentUid = 0;

    /**
     * @param records     足迹记录列表
     * @param parseThread ParseMessageThread 实例（可为 null）
     * @param sessionMeta 会话元数据
     */
    public FootprintReplayThread(List<FootprintRecord> records, ParseMessageThread parseThread,
                                  SessionMeta sessionMeta) {
        this.records = records;
        this.parseThread = parseThread;
        this.sessionMeta = sessionMeta;
        setName("FootprintReplayThread");
        setDaemon(false);
    }

    @Override
    public void run() {
        if (records.isEmpty()) return;

        // 注意：PublicDataConf 的直播间上下文已在 WebController.startFootprintReplay 中设置，
        // 此处不再重复设置，直接开始重放

        try {
            startTimeMs = System.currentTimeMillis();
            // 找到第一个非零时间戳作为基准
            baseTimestamp = findBaseTimestamp();
            currentIndex = 0;

            for (int i = 0; i < records.size(); i++) {
                if (stopped) break;

                // 暂停等待
                while (paused && !stopped) {
                    try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                if (stopped) break;

                FootprintRecord rec = records.get(i);
                currentIndex = i;
                currentUid = rec.uid;
                currentUname = rec.uname;

                // 计算等待时间
                long sleepMs = calculateDelay(rec);
                if (sleepMs > 0) {
                    // 分段休眠，支持响应式暂停/停止
                    long slept = 0;
                    while (slept < sleepMs && !stopped && !paused) {
                        long chunk = Math.min(100, sleepMs - slept);
                        try { Thread.sleep(chunk); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                        slept += chunk;
                        if (paused) break;
                    }
                }

                if (stopped) break;
                while (paused && !stopped) {
                    try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                if (stopped) break;

                // 执行 audienceProcessing
                executeReplay(rec);
            }
        } finally {
            // 不恢复上下文：重放设置的直播间信息（ROOMID/ANCHOR_NAME/AUID）
            // 保留在 PublicDataConf 中，后续连接直播间时会自然覆盖。
            // 若恢复为原始值（如 null），则 CompletableFuture 异步回调中
            // 的日志写入会因读到 null 而生成错误的文件名。
            awaitWatcherTasks();
        }
        stopped = true;
    }

    /**
     * 等待 WATCHER_EXECUTOR 中所有已提交的异步任务执行完毕。
     * 通过提交一个哨兵任务并等待其完成来实现，因为 ThreadPoolExecutor 按 FIFO 顺序执行。
     */
    private void awaitWatcherTasks() {
        try {
            ExecutorService executor = ParseMessageThread.getWatcherExecutor();
            if (executor != null && !executor.isShutdown()) {
                // 清除中断标志位，防止 Future.get() 因 pending interrupt 立即抛出
                // InterruptedException 而不等待。stopReplay() 的 interrupt() 用于
                // 打断 sleep，不应影响此处的异步任务等待。
                Thread.interrupted();
                executor.submit(() -> {}).get(30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOGGER.warn("FootprintReplay: awaitWatcherTasks timeout or interrupted", e);
        }
    }

    /**
     * 计算本条记录应等待的毫秒数
     */
    private long calculateDelay(FootprintRecord rec) {
        if (speedMode == SpeedMode.FIXED_RATE) {
            // 定速播放：固定间隔
            if (speedValue <= 0) return 0;
            return (long) (1000.0 / speedValue);
        } else {
            // 倍数播放：基于原始时间戳间隔
            if (rec.utime == 0 || baseTimestamp == 0 || speedValue <= 0) return 0;
            long elapsedSinceFirst = rec.utime - baseTimestamp;
            long realElapsed = System.currentTimeMillis() - startTimeMs;
            long targetDelay = (long) (elapsedSinceFirst / speedValue);
            return targetDelay - realElapsed;
        }
    }

    /**
     * 找到第一个非零时间戳作为播放基准
     */
    private long findBaseTimestamp() {
        for (FootprintRecord rec : records) {
            if (rec.utime > 0) return rec.utime;
        }
        return 0;
    }

    /**
     * 执行单条记录的重放
     */
    private void executeReplay(FootprintRecord rec) {
        CenterSetConf conf = PublicDataConf.centerSetConf;
        if (conf == null) {
            conf = new CenterSetConf();
        }

        // 获取 ParseMessageThread 实例
        ParseMessageThread thread = parseThread;
        if (thread == null) {
            thread = PublicDataConf.parseMessageThread;
        }
        if (thread == null) {
            // 离线模式：创建最小实例
            thread = new ParseMessageThread();
        }

        StringBuilder sb = new StringBuilder(100);
        try {
            thread.audienceProcessingPublic(sb, rec.uid, rec.uname, conf);
        } catch (Exception e) {
            LOGGER.error("FootprintReplayThread error uid={}", rec.uid, e);
        }
    }

    // ===== 控制方法 =====

    public void pauseReplay() {
        this.paused = true;
    }

    public void resumeReplay() {
        // 恢复时重置时间基准，避免大量积压延迟
        if (currentIndex < records.size()) {
            FootprintRecord rec = records.get(currentIndex);
            if (rec.utime > 0) {
                this.baseTimestamp = rec.utime;
            }
        }
        this.startTimeMs = System.currentTimeMillis();
        this.paused = false;
    }

    public void stopReplay() {
        this.stopped = true;
        this.paused = false;
        // 中断可能在休眠中的线程
        interrupt();
    }

    // ===== 状态查询 =====

    public boolean isRunning() {
        return !stopped && isAlive();
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isStopped() {
        return stopped;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getTotalCount() {
        return records.size();
    }

    public SpeedMode getSpeedMode() {
        return speedMode;
    }

    public double getSpeedValue() {
        return speedValue;
    }

    public String getCurrentUname() {
        return currentUname;
    }

    public long getCurrentUid() {
        return currentUid;
    }

    /**
     * 获取当前使用的 SessionMeta（可能为 null）
     */
    public SessionMeta getSessionMeta() {
        return sessionMeta;
    }

    // ===== 速度控制 =====

    /**
     * 设置速度模式
     * @param mode  播放模式
     * @param value 倍数模式下为倍率(0.1-10)，定速模式下为人/秒(0.1-100)
     */
    public void setSpeed(SpeedMode mode, double value) {
        this.speedMode = mode;
        if (mode == SpeedMode.TIME_MULTIPLIER) {
            this.speedValue = Math.max(0.1, Math.min(10.0, value));
        } else {
            this.speedValue = Math.max(0.1, Math.min(100.0, value));
        }
        // 重置时间基准
        if (currentIndex < records.size()) {
            FootprintRecord rec = records.get(currentIndex);
            if (rec.utime > 0) {
                this.baseTimestamp = rec.utime;
            }
        }
        this.startTimeMs = System.currentTimeMillis();
    }
}
