package xyz.acproject.danmuji.service;

import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.system.ApplicationHome;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.controller.DanmuWebsocket;
import xyz.acproject.danmuji.entity.base.WsPackage;
import xyz.acproject.danmuji.http.HttpUserData;
import xyz.acproject.danmuji.tools.VisitorCountTools;
import xyz.acproject.danmuji.utils.JodaTimeUtils;
import xyz.acproject.danmuji.utils.SpringUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class StrangerViewerService {
    private static final Logger LOGGER = LogManager.getLogger(StrangerViewerService.class);

    private static final ConcurrentHashMap<Long, StrangerRecord> recordMap = new ConcurrentHashMap<>();
    private static final Set<Long> blockedUids = ConcurrentHashMap.newKeySet();
    private static final Set<Long> dirtyUids = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService mdScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stranger-md-flush");
        t.setDaemon(true);
        return t;
    });
    private static final ScheduledExecutorService avatarExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stranger-avatar-dl");
        t.setDaemon(true);
        return t;
    });

    private static String jarDir;
    private static volatile boolean mdScheduled = false;
    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;

    static {
        initBase();
    }

    private static void initBase() {
        ApplicationHome home = new ApplicationHome(StrangerViewerService.class);
        jarDir = home.getSource().getParentFile().getAbsolutePath();
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
    }

    private static String roomKey() {
        Long id = PublicDataConf.ROOMID;
        return id != null ? id.toString() : "unknown";
    }

    private static String safeFileName(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String mdFilePath() {
        String name = safeFileName(PublicDataConf.ANCHOR_NAME);
        return jarDir + File.separator + "Danmuji_log" + File.separator + roomKey() + "_" + name + "_7_陌生观众.md";
    }

    private static String avatarDir() {
        String name = safeFileName(PublicDataConf.ANCHOR_NAME);
        return jarDir + File.separator + "Danmuji_log" + File.separator + roomKey() + "_" + name + "_陌生观众头像";
    }

    public static void addRecord(long uid, String name, String face, int score, String scoreTypes ) {
        if (uid <= 0) return;

        // 检测房间切换
        String rk = roomKey();
        if (!rk.equals(lastRoomId)) {
            synchronized (StrangerViewerService.class) {
                if (!rk.equals(lastRoomId)) {
                    recordMap.clear();
                    dirtyUids.clear();
                    lastRoomId = rk;
                    lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
                }
            }
        }

        int[] cv = VisitorCountTools.getCountAndSession(uid);
        int count = cv[0];
        int session = cv[1];

        // Auto-blocked users (score < 0) are tracked as blocked initially
        if (score < 0) {
            blockedUids.add(uid);
        }

        StrangerRecord record = new StrangerRecord(uid, name, face, score, scoreTypes, count, session );
        recordMap.put(uid, record);
        dirtyUids.add(uid);

        // Download avatar
        downloadAvatar(uid, face);

        // Push to frontend via WebSocket
        pushToFrontend(record);

        // Start MD flush scheduler if not already started
        if (!mdScheduled) {
            synchronized (StrangerViewerService.class) {
                if (!mdScheduled) {
                    mdScheduler.scheduleWithFixedDelay(StrangerViewerService::flushToMd, 60, 60, TimeUnit.SECONDS);
                    mdScheduled = true;
                }
            }
        }
    }

    private static void downloadAvatar(long uid, String faceUrl) {
        if (faceUrl == null || faceUrl.isEmpty()) return;
        String dir = avatarDir();
        File dirFile = new File(dir);
        if (!dirFile.exists()) dirFile.mkdirs();

        File targetFile = new File(dirFile, uid + ".jpg");
        if (targetFile.exists()) return; // already downloaded

        avatarExecutor.execute(() -> {
            try {
                URL url = new URL(faceUrl);
                try (InputStream in = url.openStream()) {
                    Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to download avatar for uid={}: {}", uid, e.getMessage());
            }
        });
    }

    private static void pushToFrontend(StrangerRecord record) {
        try {
            DanmuWebsocket ws = SpringUtils.getBean(DanmuWebsocket.class);
            if (ws == null) return;
            JSONObject data = new JSONObject();
            data.put("uid", record.uid);
            data.put("name", record.name);
            data.put("face", record.face);
            data.put("score", record.score);
            data.put("scoreTypes", record.scoreTypes);
            data.put("count", record.count);
            data.put("session", record.session);
            data.put("blocked", blockedUids.contains(record.uid));
            data.put("time", JodaTimeUtils.formatDateTime(System.currentTimeMillis()));
            ws.sendMessage(WsPackage.toJson("stranger_viewer", (short) 0, data));
        } catch (Exception e) {
            LOGGER.debug("pushToFrontend error: {}", e.getMessage());
        }
    }

    private static synchronized void flushToMd() {
        String rk = roomKey();
        if (!rk.equals(lastRoomId)) {
            // 房间切换：写出旧房间最终 MD
            String oldAnchor = lastAnchorName;
            if (oldAnchor != null) {
                String oldPath = jarDir + File.separator + "Danmuji_log" + File.separator
                        + lastRoomId + "_" + oldAnchor + "_7_陌生观众.md";
                List<StrangerRecord> oldRecords = new ArrayList<>(recordMap.values());
                oldRecords.sort(Comparator.comparingLong(a -> a.time));
                writeMarkdown(oldPath, oldRecords);
            }
            recordMap.clear();
            dirtyUids.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            return;
        }
        if (dirtyUids.isEmpty()) return;
        Set<Long> snapshot = new HashSet<>(dirtyUids);
        dirtyUids.clear();
        List<StrangerRecord> allRecords = new ArrayList<>(recordMap.values());
        allRecords.sort(Comparator.comparingLong(a -> a.time));
        writeMarkdown(mdFilePath(), allRecords);
    }

    private static void writeMarkdown(String path, List<StrangerRecord> records) {
        String avatarSubDir = new File(avatarDir()).getName();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "UTF-8"))) {
            writer.write("# 实时陌生观众看板");
            writer.newLine();
            writer.newLine();
            writer.write("更新时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.newLine();
            writer.newLine();
            writer.write("| 时间 | 头像 | id | 观众名 | 打分 | 次数 | 场次 |");
            writer.newLine();
            writer.write("|------|------|----|--------|------|------|------|");
            writer.newLine();
            for (StrangerRecord r : records) {
                StringBuilder sb = new StringBuilder();
                sb.append("| ");
                sb.append(JodaTimeUtils.formatDateTime(r.time));
                sb.append(" | ");
                sb.append("![avatar](").append(avatarSubDir).append("/").append(r.uid).append(".jpg)");
                sb.append(" | ");
                sb.append(r.uid);
                sb.append(" | ");
                sb.append(escapeMd(r.name));
                sb.append(" | ");
                sb.append(r.score);
                sb.append(" | ");
                sb.append(r.count);
                sb.append(" | ");
                sb.append(r.session);
                sb.append(" |");
                writer.write(sb.toString());
                writer.newLine();
            }
        } catch (Exception e) {
            LOGGER.error("writeMarkdown error", e);
        }
    }

    private static String escapeMd(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }

    public static Map<String, Object> getPageData(int page, int pageSize, String search, String sortField, String sortOrder) {
        List<StrangerRecord> all = new ArrayList<>(recordMap.values());

        // Filter by search first (reduce sort cost)
        if (search != null && !search.isEmpty()) {
            String lower = search.toLowerCase();
            List<StrangerRecord> filtered = new ArrayList<>();
            for (StrangerRecord r : all) {
                if (r.name.toLowerCase().contains(lower)
                        || r.scoreTypes.toLowerCase().contains(lower)
                        || String.valueOf(r.uid).contains(lower)) {
                    filtered.add(r);
                }
            }
            all = filtered;
        }

        // Sort filtered dataset
        boolean asc = !"desc".equalsIgnoreCase(sortOrder);
        Comparator<StrangerRecord> cmp;
        if ("score".equals(sortField)) {
            cmp = Comparator.comparingInt(a -> a.score);
        } else if ("count".equals(sortField)) {
            cmp = Comparator.comparingInt(a -> a.count);
        } else if ("session".equals(sortField)) {
            cmp = Comparator.comparingInt(a -> a.session);
        } else if ("name".equals(sortField)) {
            cmp = Comparator.comparing(a -> a.name);
        } else if ("scoreTypes".equals(sortField)) {
            cmp = Comparator.comparing(a -> a.scoreTypes);
        } else {
            cmp = Comparator.comparingLong(a -> a.time);
        }
        all.sort(asc ? cmp : cmp.reversed());

        int total = all.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        if (page < 1) page = 1;
        if (totalPages > 0 && page > totalPages) page = totalPages;

        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<JSONObject> rows = new ArrayList<>();
        for (int i = from; i < to; i++) {
            StrangerRecord r = all.get(i);
            JSONObject row = new JSONObject();
            row.put("time", JodaTimeUtils.formatDateTime(r.time));
            row.put("uid", r.uid);
            row.put("name", r.name);
            row.put("face", r.face);
            row.put("score", r.score);
            row.put("scoreTypes", r.scoreTypes);
            row.put("count", r.count);
            row.put("session", r.session);
            row.put("blocked", blockedUids.contains(r.uid));
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("totalPages", totalPages);
        result.put("currentPage", totalPages > 0 ? page : 0);
        return result;
    }

    public static boolean toggleBlock(long uid) {
        if (blockedUids.contains(uid)) {
            // unblock
            try {
                HttpUserData.httpPostDeleteBadList(uid);
                blockedUids.remove(uid);
                pushBlockUpdate(uid, false);
                return false;
            } catch (Exception e) {
                LOGGER.error("unblock error uid={}", uid, e);
                return true; // still blocked
            }
        } else {
            // block
            try {
                HttpUserData.httpPostAddBadList(uid);
                blockedUids.add(uid);
                pushBlockUpdate(uid, true);
                return true;
            } catch (Exception e) {
                LOGGER.error("block error uid={}", uid, e);
                return false; // still not blocked
            }
        }
    }

    private static void pushBlockUpdate(long uid, boolean blocked) {
        try {
            DanmuWebsocket ws = SpringUtils.getBean(DanmuWebsocket.class);
            if (ws == null) return;
            JSONObject data = new JSONObject();
            data.put("uid", uid);
            data.put("blocked", blocked);
            ws.sendMessage(WsPackage.toJson("stranger_block", (short) 0, data));
        } catch (Exception e) {
            LOGGER.debug("pushBlockUpdate error: {}", e.getMessage());
        }
    }

    public static boolean isBlocked(long uid) {
        return blockedUids.contains(uid);
    }

    static class StrangerRecord {
        final long uid;
        volatile String name;
        volatile String face;
        volatile int score;
        volatile String scoreTypes;
        volatile int count;
        volatile int session;
        volatile long time;

        StrangerRecord(long uid, String name, String face, int score, String scoreTypes, int count, int session ) {
            this.uid = uid;
            this.name = name;
            this.face = face;
            this.score = score;
            this.scoreTypes = scoreTypes;
            this.count = count;
            this.session = session;
            this.time = System.currentTimeMillis();
        }
    }
}
