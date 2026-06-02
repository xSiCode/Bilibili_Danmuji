package xyz.acproject.danmuji.thread;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.CenterSetConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.FileBatch;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.FootprintRecord;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.SessionMeta;
import xyz.acproject.danmuji.thread.core.ParseMessageThread;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 足迹还原重放线程（支持多文件批次）
 * 双速模式：TIME_MULTIPLIER（倍数）/ FIXED_RATE（定速）
 * 文件边界处自动切换直播间上下文
 */
public class FootprintReplayThread extends Thread {
    private static final Logger LOGGER = LogManager.getLogger(FootprintReplayThread.class);

    public enum SpeedMode {
        TIME_MULTIPLIER,  // 倍数播放
        FIXED_RATE        // 定速播放
    }

    private final List<FileBatch> batches;
    private final ParseMessageThread parseThread;

    private volatile SpeedMode speedMode = SpeedMode.TIME_MULTIPLIER;
    private volatile double speedValue = 1.0;
    private volatile boolean paused = false;
    private volatile boolean stopped = false;
    private volatile int currentIndex = 0;       // 当前文件内的记录索引
    private volatile int currentBatchIndex = 0;  // 当前文件批次索引
    private volatile long startTimeMs;
    private volatile long baseTimestamp;
    private volatile String currentUname = "";
    private volatile long currentUid = 0;

    /**
     * @param batches     文件批次列表（按回放顺序）
     * @param parseThread ParseMessageThread 实例（可为 null）
     */
    public FootprintReplayThread(List<FileBatch> batches, ParseMessageThread parseThread) {
        this.batches = batches;
        this.parseThread = parseThread;
        setName("FootprintReplayThread");
        setDaemon(false);
    }

    @Override
    public void run() {
        if (batches.isEmpty()) return;

        try {
            for (int bi = 0; bi < batches.size(); bi++) {
                if (stopped) break;
                FileBatch batch = batches.get(bi);
                currentBatchIndex = bi;

                // 切换直播间上下文
                swapBatchContext(batch.meta);

                List<FootprintRecord> records = batch.records;
                if (records.isEmpty()) continue;

                // 找到第一个非零时间戳作为播放基准（每个文件独立）
                startTimeMs = System.currentTimeMillis();
                baseTimestamp = findBaseTimestamp(records);
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

                    // 计算并等待
                    long sleepMs = calculateDelay(rec);
                    if (sleepMs > 0) {
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

                    executeReplay(rec);
                }
            }
        } finally {
            // 不恢复上下文，保留最后一批文件的直播间信息
            awaitWatcherTasks();
        }
        stopped = true;
    }

    private void swapBatchContext(SessionMeta meta) {
        if (meta == null || !meta.hasData()) return;
        if (meta.roomId != 0) PublicDataConf.ROOMID = meta.roomId;
        if (meta.anchorName != null && !meta.anchorName.isEmpty()) PublicDataConf.ANCHOR_NAME = meta.anchorName;
        if (meta.auid != 0) PublicDataConf.AUID = meta.auid;
        LOGGER.info("FootprintReplay: batch context roomId={} anchorName={}",
                PublicDataConf.ROOMID, PublicDataConf.ANCHOR_NAME);
    }

    private long calculateDelay(FootprintRecord rec) {
        if (speedMode == SpeedMode.FIXED_RATE) {
            if (speedValue <= 0) return 0;
            return (long) (1000.0 / speedValue);
        } else {
            if (rec.utime == 0 || baseTimestamp == 0 || speedValue <= 0) return 0;
            long elapsedSinceFirst = rec.utime - baseTimestamp;
            long realElapsed = System.currentTimeMillis() - startTimeMs;
            long targetDelay = (long) (elapsedSinceFirst / speedValue);
            return targetDelay - realElapsed;
        }
    }

    private long findBaseTimestamp(List<FootprintRecord> records) {
        for (FootprintRecord rec : records) {
            if (rec.utime > 0) return rec.utime;
        }
        return 0;
    }

    private void executeReplay(FootprintRecord rec) {
        CenterSetConf conf = PublicDataConf.centerSetConf;
        if (conf == null) conf = new CenterSetConf();

        ParseMessageThread thread = parseThread;
        if (thread == null) thread = PublicDataConf.parseMessageThread;
        if (thread == null) thread = new ParseMessageThread();

        StringBuilder sb = new StringBuilder(100);
        try {
            thread.audienceProcessingPublic(sb, rec.uid, rec.uname, conf);
        } catch (Exception e) {
            LOGGER.error("FootprintReplayThread error uid={}", rec.uid, e);
        }
    }

    private void awaitWatcherTasks() {
        try {
            ExecutorService executor = ParseMessageThread.getWatcherExecutor();
            if (executor != null && !executor.isShutdown()) {
                Thread.interrupted();
                executor.submit(() -> {}).get(30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOGGER.warn("FootprintReplay: awaitWatcherTasks timeout or interrupted", e);
        }
    }

    // ===== 控制方法 =====

    public void pauseReplay()  { this.paused = true; }
    public void resumeReplay() {
        if (currentBatchIndex < batches.size()) {
            List<FootprintRecord> records = batches.get(currentBatchIndex).records;
            if (currentIndex < records.size()) {
                FootprintRecord rec = records.get(currentIndex);
                if (rec.utime > 0) this.baseTimestamp = rec.utime;
            }
        }
        this.startTimeMs = System.currentTimeMillis();
        this.paused = false;
    }

    public void stopReplay() {
        this.stopped = true;
        this.paused = false;
        interrupt();
    }

    // ===== 状态查询 =====

    public boolean isRunning() { return !stopped && isAlive(); }
    public boolean isPaused()  { return paused; }
    public boolean isStopped() { return stopped; }
    public int getCurrentIndex()      { return currentIndex; }
    public int getTotalCount()        { return batches.stream().mapToInt(b -> b.records.size()).sum(); }
    public int getCurrentBatchIndex() { return currentBatchIndex; }
    public int getTotalBatchCount()   { return batches.size(); }
    public String getCurrentFileName() {
        if (currentBatchIndex >= 0 && currentBatchIndex < batches.size())
            return batches.get(currentBatchIndex).fileName;
        return "";
    }
    public SpeedMode getSpeedMode()   { return speedMode; }
    public double getSpeedValue()     { return speedValue; }
    public String getCurrentUname()   { return currentUname; }
    public long getCurrentUid()       { return currentUid; }

    // ===== 速度控制 =====

    public void setSpeed(SpeedMode mode, double value) {
        this.speedMode = mode;
        this.speedValue = (mode == SpeedMode.TIME_MULTIPLIER)
                ? Math.max(0.1, Math.min(10.0, value))
                : Math.max(0.1, Math.min(100.0, value));
        if (currentBatchIndex < batches.size()) {
            List<FootprintRecord> records = batches.get(currentBatchIndex).records;
            if (currentIndex < records.size() && records.get(currentIndex).utime > 0)
                this.baseTimestamp = records.get(currentIndex).utime;
        }
        this.startTimeMs = System.currentTimeMillis();
    }
}
