package xyz.acproject.danmuji.service;

import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.controller.DanmuWebsocket;
import xyz.acproject.danmuji.entity.base.WsPackage;
import xyz.acproject.danmuji.http.HttpUserData;
import xyz.acproject.danmuji.tools.VisitorCountTools;
import xyz.acproject.danmuji.utils.JodaTimeUtils;
import xyz.acproject.danmuji.utils.SpringUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;

public class StrangerViewerService {
    private static final Logger LOGGER = LogManager.getLogger(StrangerViewerService.class);

    private static final ConcurrentHashMap<Long, StrangerRecord> recordMap = new ConcurrentHashMap<>();
    private static final int MAX_MAP_SIZE = 5000;
    private static final Set<Long> blockedUids = ConcurrentHashMap.newKeySet();
    private static final Set<Long> dirtyUids = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService mdScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stranger-csv-flush");
        t.setDaemon(true);
        return t;
    });
    private static volatile String lastRoomId;
    private static volatile String lastAnchorName;
    private static volatile boolean viewingExternalFile = false;

    static {
        lastRoomId = roomKey();
        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
        loadFromCsv();
        mdScheduler.scheduleWithFixedDelay(StrangerViewerService::flushToCsv, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            mdScheduler.shutdown();
            flushToCsv();
        }, "stranger-csv-shutdown"));
    }

    private static String roomKey() {
        Long id = PublicDataConf.ROOMID;
        return id != null ? id.toString() : "unknown";
    }

    private static String safeFileName(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String currentCsvPath() {
        String name = safeFileName(PublicDataConf.ANCHOR_NAME);
        return LogPathConf.getLogDir() + File.separator + roomKey() + "_" + name + "_7_陌生观众.csv";
    }

    public static void addRecord(long uid, String name, String face, int score, String scoreTypes) {
        if (uid <= 0) return;

        // Detect room switch — save old room data BEFORE clearing the map
        String rk = roomKey();
        if (!rk.equals(lastRoomId)) {
            synchronized (StrangerViewerService.class) {
                if (!rk.equals(lastRoomId)) {
                    if (viewingExternalFile) {
                        // External file view: discard external data, switch to live
                        recordMap.clear();
                        dirtyUids.clear();
                        blockedUids.clear();
                        lastRoomId = rk;
                        lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
                        loadFromCsv();
                    } else {
                        // flushToCsv() handles save + clear + load + lastRoomId/lastAnchorName update
                        flushToCsv();
                    }
                    viewingExternalFile = false;
                }
            }
        }
        // Don't pollute historical file view with live data
        if (viewingExternalFile) return;

        int[] cv = VisitorCountTools.getCountAndSession(uid);
        int count = cv[0];
        int session = cv[1];

        // Auto-block threshold from config (default -1)
        int blockThreshold = (PublicDataConf.centerSetConf != null
                && PublicDataConf.centerSetConf.getAuto_block() != null)
                ? PublicDataConf.centerSetConf.getAuto_block().getBlock_score() : -1;
        if (score <= blockThreshold) {
            blockedUids.add(uid);
        } else{
            blockedUids.remove(uid);
        }

        StrangerRecord record = new StrangerRecord(uid, name, face, score, scoreTypes, count, session);
        recordMap.put(uid, record);
        dirtyUids.add(uid);
        // 内存淘汰：超过上限时移除最旧的条目
        if (recordMap.size() > MAX_MAP_SIZE + 500) {
            evictOldest();
        }

        // Push to frontend via WebSocket
        pushToFrontend(record);

        // 立即写入 SQLite（保证查询时能立即看到新观众）
        insertToSqlite(record);
    }

    /** 单条记录立即写入 SQLite */
    private static void insertToSqlite(StrangerRecord r) {
        try {
            Long rid = PublicDataConf.ROOMID;
            if (rid == null) return;
            String an = safeFileName(PublicDataConf.ANCHOR_NAME);
            String sql = "INSERT OR REPLACE INTO stranger_viewer(room_id,anchor_name,uid,name,face,score,score_types,count,session,blocked,time) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
            try (java.sql.Connection c = xyz.acproject.danmuji.tools.db.DanmujiDatabase.getConnection();
                 java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, rid);
                ps.setString(2, an != null ? an : "");
                ps.setLong(3, r.uid);
                ps.setString(4, r.name != null ? r.name : "");
                ps.setString(5, r.face != null ? r.face : "");
                ps.setInt(6, r.score);
                ps.setString(7, r.scoreTypes != null ? r.scoreTypes : "");
                ps.setInt(8, r.count);
                ps.setInt(9, r.session > 0 ? r.session : 1);
                ps.setInt(10, blockedUids.contains(r.uid) ? 1 : 0);
                ps.setLong(11, r.time);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.debug("insertToSqlite error: {}", e.getMessage());
        }
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
            String summary = ViewerActivitySummary.buildSummary(record.uid);
            data.put("scoreTypes", (summary.isEmpty() ? "" : " " + summary) + (record.scoreTypes != null ? record.scoreTypes : "") );
            data.put("count", record.count);
            data.put("session", record.session);
            data.put("blocked", blockedUids.contains(record.uid));
            data.put("time", JodaTimeUtils.formatDateTime(System.currentTimeMillis()));
            ws.sendMessage(WsPackage.toJson("stranger_viewer", (short) 0, data));
        } catch (Exception e) {
            LOGGER.debug("pushToFrontend error: {}", e.getMessage());
        }
    }

    // ==================== CSV persistence ====================

    private static synchronized void flushToCsv() {
        String rk = roomKey();
        if ("unknown".equals(rk)) return;
        if (!rk.equals(lastRoomId)) {
            // Room switch: full-write old room data, then load new room
            String oldAnchor = lastAnchorName;
            if (oldAnchor != null) {
                String oldPath = LogPathConf.getLogDir() + File.separator
                        + lastRoomId + "_" + oldAnchor + "_7_陌生观众.csv";
                doFlushFull(oldPath);
            }
            recordMap.clear();
            dirtyUids.clear();
            lastRoomId = rk;
            lastAnchorName = safeFileName(PublicDataConf.ANCHOR_NAME);
            loadFromCsv();
            return;
        }
        // Normal cycle: append dirty records
        doFlushAppend(currentCsvPath());
    }

    /** Full overwrite — used on room switch. */
    private static void doFlushFull(String path) {
        List<StrangerRecord> records = new ArrayList<>(recordMap.values());
        records.sort(Comparator.comparingLong(a -> a.time));
        writeCsvFile(path, records, false);
    }

    /** Incremental append — only writes dirty records, deduplicating against existing file. */
    private static void doFlushAppend(String path) {
        if (dirtyUids.isEmpty()) return;
        Set<Long> snapshot = new HashSet<>(dirtyUids);
        dirtyUids.clear();
        List<StrangerRecord> records = new ArrayList<>();
        for (Long uid : snapshot) {
            StrangerRecord r = recordMap.get(uid);
            if (r != null) records.add(r);
        }
        if (records.isEmpty()) return;
        records.sort(Comparator.comparingLong(a -> a.time));
        writeCsvFile(path, records, true);
    }

    private static void writeCsvFile(String path, List<StrangerRecord> records, boolean append) {
        File file = new File(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File tmpFile = new File(path + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
            if (append && file.exists()) {
                // Copy old file content to temp, skipping rows for UIDs being updated
                Set<Long> appendUids = new HashSet<>();
                for (StrangerRecord r : records) appendUids.add(r.uid);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                    String line;
                    boolean isHeader = true;
                    while ((line = reader.readLine()) != null) {
                        if (isHeader) {
                            isHeader = false;
                            writer.write(line);
                            writer.newLine();
                            continue;
                        }
                        List<String> fields = parseCsvLine(line);
                        if (fields.size() >= 9) {
                            try {
                                long rowUid = Long.parseLong(fields.get(1));
                                if (appendUids.contains(rowUid)) continue; // skip old row
                            } catch (NumberFormatException ignored) {}
                        }
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } else {
                // Write BOM + header
                writer.write('﻿');
                writer.write("时间,id,观众名,头像URL,打分,签名,次数,场次,是否拉黑");
                writer.newLine();
            }
            for (StrangerRecord r : records) {
                writer.write(JodaTimeUtils.formatDateTime(r.time));
                writer.write(',');
                writer.write(String.valueOf(r.uid));
                writer.write(',');
                writer.write(escapeCsv(r.name));
                writer.write(',');
                writer.write(escapeCsv(r.face));
                writer.write(',');
                writer.write(String.valueOf(r.score));
                writer.write(',');
                writer.write(escapeCsv(r.scoreTypes));
                writer.write(',');
                writer.write(String.valueOf(r.count));
                writer.write(',');
                writer.write(String.valueOf(r.session));
                writer.write(',');
                writer.write(blockedUids.contains(r.uid) ? "是" : "否");
                writer.newLine();
            }
        } catch (Exception e) {
            LOGGER.error("write stranger CSV failed", e);
            return;
        }
        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("move stranger CSV failed", e);
        }

        flushToSqlite(path, records);
    }

    private static void flushToSqlite(String csvPath, List<StrangerRecord> records) {
        String filename = new File(csvPath).getName();
        String[] info = xyz.acproject.danmuji.tools.db.DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return;
        long roomId = Long.parseLong(info[0]);
        String anchorName = info[1];

        String sql = "INSERT OR REPLACE INTO stranger_viewer(room_id,anchor_name,uid,name,face,score,score_types,count,session,blocked,time) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (java.sql.Connection c = xyz.acproject.danmuji.tools.db.DanmujiDatabase.getConnection()) {
            c.setAutoCommit(false);
            try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                for (StrangerRecord r : records) {
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setLong(3, r.uid);
                    ps.setString(4, r.name != null ? r.name : "");
                    ps.setString(5, r.face != null ? r.face : "");
                    ps.setInt(6, r.score);
                    ps.setString(7, r.scoreTypes != null ? r.scoreTypes : "");
                    ps.setInt(8, r.count);
                    ps.setInt(9, r.session > 0 ? r.session : 1);
                    ps.setInt(10, blockedUids.contains(r.uid) ? 1 : 0);
                    ps.setLong(11, r.time);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (Exception e2) {
                try { c.rollback(); } catch (Exception ignored) {}
                throw e2;
            }
        } catch (Exception e) {
            LOGGER.error("flush stranger to SQLite failed", e);
        }
    }

    // ==================== CSV parsing (startup loading) ====================

    private static void loadFromCsv() {
        if (!loadFromSqlite(currentCsvPath())) {
            loadFromCsvLegacy(currentCsvPath());
        }
    }

    /** 从指定文件重载内存数据（合并后调用，防止旧数据覆盖合并结果） */
    public static synchronized void reloadFromFile(String path) {
        recordMap.clear();
        blockedUids.clear();
        if (!loadFromSqlite(path)) {
            loadFromCsvLegacy(path);
        }
    }

    private static boolean loadFromSqlite(String csvPath) {
        String filename = new File(csvPath).getName();
        String[] info = xyz.acproject.danmuji.tools.db.DanmujiMigration.parseRoomAnchorStr(filename);
        if (info == null) return false;
        long roomId;
        try { roomId = Long.parseLong(info[0]); } catch (NumberFormatException e) { return false; }
        String anchorName = info[1];

        String sql = "SELECT uid, name, face, score, score_types, count, session, blocked, time FROM stranger_viewer WHERE room_id = ? AND anchor_name = ? ORDER BY time DESC LIMIT 5000";
        try (java.sql.Connection c = xyz.acproject.danmuji.tools.db.DanmujiDatabase.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, roomId);
            ps.setString(2, anchorName);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long uid = rs.getLong("uid");
                    int session = rs.getInt("session");
                    if (session == 0) session = 1;
                    StrangerRecord r = new StrangerRecord(uid, rs.getString("name"), rs.getString("face"),
                        rs.getInt("score"), rs.getString("score_types"), rs.getInt("count"), session);
                    r.time = rs.getLong("time");
                    recordMap.put(uid, r);
                    if (rs.getInt("blocked") == 1) {
                        blockedUids.add(uid);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("load stranger from SQLite failed, fallback to CSV: {}", e.getMessage());
            return false;
        }
    }

    private static void loadFromCsvLegacy(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine(); // skip header (may have BOM)
            while ((line = reader.readLine()) != null) {
                StrangerRecord record = parseRecord(line);
                if (record != null) {
                    if (record.session == 0) record.session = 1;
                    recordMap.put(record.uid, record);
                }
            }
        } catch (Exception e) {
            LOGGER.error("load stranger CSV failed", e);
        }
    }

    private static StrangerRecord parseRecord(String line) {
        if (line == null || line.isEmpty()) return null;
        try {
            List<String> fields = parseCsvLine(line);
            if (fields.size() < 9) return null;
            // 时间,id,观众名,头像URL,打分,签名,次数,场次,是否拉黑
            String timeStr = fields.get(0);
            long uid = Long.parseLong(fields.get(1));
            String name = fields.get(2);
            String face = fields.get(3);
            int score = Integer.parseInt(fields.get(4));
            String scoreTypes = fields.get(5);
            int count = Integer.parseInt(fields.get(6));
            int session = 0;
            try { session = Integer.parseInt(fields.get(7)); } catch (NumberFormatException ignored) {}
            boolean blocked = "是".equals(fields.get(8));

            long time = JodaTimeUtils.parse(timeStr, "yyyy-MM-dd HH:mm:ss").getTime();
            StrangerRecord record = new StrangerRecord(uid, name, face, score, scoreTypes, count, session);
            record.time = time;
            if (blocked) {
                blockedUids.add(uid);
            }
            return record;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString());
        return fields;
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ==================== Public API ====================

    /** Load data from a CSV file into memory, then return paged results.
     *  If filePath matches current room CSV or is null/empty, reads from the live in-memory recordMap.
     *  If filePath is a different CSV, saves current data first, then loads the external file
     *  and sets viewingExternalFile flag to prevent live data from polluting the historical view. */
    public static Map<String, Object> loadCsvAndGetPage(String filePath, int page, int pageSize,
            String search, String sortField, String sortOrder, String startTime, String endTime) {
        boolean isExternal = filePath != null && !filePath.isEmpty()
                && !filePath.equals(currentCsvPath());
        if (isExternal) {
            synchronized (StrangerViewerService.class) {
                flushToCsv();
                recordMap.clear();
                blockedUids.clear();
                dirtyUids.clear();
                reloadFromFile(filePath);
                viewingExternalFile = true;
            }
            return getPageData(page, pageSize, search, sortField, sortOrder, startTime, endTime);
        }
        viewingExternalFile = false;
        // 当前直播间：直接从 filePath 解析房间 ID 查 SQLite
        long roomId = parseRoomId(filePath);
        if (roomId != 0) {
            return querySqlitePage(roomId, page, pageSize, search, sortField, sortOrder, startTime, endTime);
        }
        return getPageData(page, pageSize, search, sortField, sortOrder, startTime, endTime);
    }

    /** 从文件路径解析 roomId（兼容 null/空时取当前直播间） */
    private static long parseRoomId(String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            String filename = new File(filePath).getName();
            String[] info = xyz.acproject.danmuji.tools.db.DanmujiMigration.parseRoomAnchorStr(filename);
            if (info != null) {
                try { return Long.parseLong(info[0]); } catch (NumberFormatException e) {}
            }
        }
        Long rid = PublicDataConf.ROOMID;
        return rid != null ? rid : 0;
    }

    public static Map<String, Object> getPageData(int page, int pageSize, String search,
            String sortField, String sortOrder, String startTime, String endTime) {

        // 外部文件查看模式：使用内存 recordMap（已从外部文件加载）
        List<StrangerRecord> all = new ArrayList<>(recordMap.values());
        if (all.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("rows", Collections.emptyList());
            empty.put("total", 0);
            empty.put("totalPages", 0);
            empty.put("currentPage", 0);
            return empty;
        }

        // Filter by search
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

        // Filter by time range
        if (startTime != null && !startTime.isEmpty()) {
            all.removeIf(r -> JodaTimeUtils.formatDateTime(r.time).compareTo(startTime) < 0);
        }
        if (endTime != null && !endTime.isEmpty()) {
            all.removeIf(r -> JodaTimeUtils.formatDateTime(r.time).compareTo(endTime) > 0);
        }

        // Sort
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
            rows.add(toRowJson(r));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("totalPages", totalPages);
        result.put("currentPage", totalPages > 0 ? page : 0);
        return result;
    }

    /** 当前直播间模式：直接从 SQLite 分页查询 */
    private static Map<String, Object> querySqlitePage(long roomId, int page, int pageSize, String search,
            String sortField, String sortOrder, String startTime, String endTime) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", Collections.emptyList());
        result.put("total", 0);
        result.put("totalPages", 0);
        result.put("currentPage", page);

        try (java.sql.Connection c = xyz.acproject.danmuji.tools.db.DanmujiDatabase.getConnection()) {
            StringBuilder where = new StringBuilder("WHERE room_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(roomId);

            if (startTime != null && !startTime.isEmpty()) {
                Long sm = parseStrangerTime(startTime);
                if (sm != null) { where.append(" AND time >= ?"); params.add(sm); }
            }
            if (endTime != null && !endTime.isEmpty()) {
                Long em = parseStrangerTime(endTime);
                if (em != null) { where.append(" AND time <= ?"); params.add(em); }
            }
            if (search != null && !search.isEmpty()) {
                where.append(" AND (name LIKE ? OR score_types LIKE ? OR CAST(uid AS TEXT) LIKE ?)");
                String q = "%" + search + "%";
                params.add(q); params.add(q); params.add(q);
            }

            // 排序
            String orderCol = "time";
            if ("score".equals(sortField)) orderCol = "score";
            else if ("count".equals(sortField)) orderCol = "count";
            else if ("session".equals(sortField)) orderCol = "session";
            else if ("name".equals(sortField)) orderCol = "name";
            String dir = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";

            // COUNT
            int total = 0;
            String countSql = "SELECT COUNT(*) FROM stranger_viewer " + where;
            try (java.sql.PreparedStatement ps = c.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (java.sql.ResultSet rs = ps.executeQuery()) { if (rs.next()) total = rs.getInt(1); }
            }

            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (page < 1) page = 1;
            if (totalPages > 0 && page > totalPages) page = totalPages;
            int offset = (page - 1) * pageSize;

            // DATA
            String dataSql = "SELECT uid,name,face,score,score_types,count,session,blocked,time FROM stranger_viewer "
                    + where + " ORDER BY " + orderCol + " " + dir + " LIMIT ? OFFSET ?";
            params.add(pageSize);
            params.add(offset);

            List<JSONObject> rows = new ArrayList<>();
            try (java.sql.PreparedStatement ps = c.prepareStatement(dataSql)) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JSONObject row = new JSONObject();
                        row.put("time", JodaTimeUtils.formatDateTime(rs.getLong("time")));
                        row.put("uid", rs.getLong("uid"));
                        row.put("name", rs.getString("name"));
                        row.put("face", rs.getString("face"));
                        row.put("score", rs.getInt("score"));
                        String svSummary = ViewerActivitySummary.buildSummary(rs.getLong("uid"));
                        row.put("scoreTypes", (svSummary.isEmpty() ? "" : " " + svSummary)
                                + (rs.getString("score_types") != null ? rs.getString("score_types") : ""));
                        row.put("count", rs.getInt("count"));
                        row.put("session", rs.getInt("session"));
                        row.put("blocked", rs.getInt("blocked") == 1);
                        rows.add(row);
                    }
                }
            }

            result.put("rows", rows);
            result.put("total", total);
            result.put("totalPages", totalPages);
            result.put("currentPage", totalPages > 0 ? page : 0);
        } catch (Exception e) {
            LOGGER.warn("querySqlitePage error: {}", e.getMessage());
        }
        return result;
    }

    /** 解析时间字符串为毫秒（兼容 yyyy-MM-dd HH:mm:ss 和 yyyy-MM-dd HH:mm） */
    private static Long parseStrangerTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        try {
            return JodaTimeUtils.parse(timeStr, "yyyy-MM-dd HH:mm:ss").getTime();
        } catch (Exception e) {
            try {
                return JodaTimeUtils.parse(timeStr, "yyyy-MM-dd HH:mm").getTime();
            } catch (Exception e2) { return null; }
        }
    }

    /** 将 StrangerRecord 转为前端 JSON */
    private static JSONObject toRowJson(StrangerRecord r) {
        JSONObject row = new JSONObject();
        row.put("time", JodaTimeUtils.formatDateTime(r.time));
        row.put("uid", r.uid);
        row.put("name", r.name);
        row.put("face", r.face);
        row.put("score", r.score);
        String svSummary = ViewerActivitySummary.buildSummary(r.uid);
        row.put("scoreTypes", (svSummary.isEmpty() ? "" : " " + svSummary) + (r.scoreTypes != null ? r.scoreTypes : ""));
        row.put("count", r.count);
        row.put("session", r.session);
        row.put("blocked", blockedUids.contains(r.uid));
        return row;
    }

    /** Export filtered records from memory as CSV string (BOM + header + rows). */
    public static String exportCsv(String startTime, String endTime, String search) {
        List<StrangerRecord> all = new ArrayList<>(recordMap.values());

        if (search != null && !search.isEmpty()) {
            String lower = search.toLowerCase();
            all.removeIf(r -> !r.name.toLowerCase().contains(lower)
                    && !r.scoreTypes.toLowerCase().contains(lower)
                    && !String.valueOf(r.uid).contains(lower));
        }
        if (startTime != null && !startTime.isEmpty()) {
            all.removeIf(r -> JodaTimeUtils.formatDateTime(r.time).compareTo(startTime) < 0);
        }
        if (endTime != null && !endTime.isEmpty()) {
            all.removeIf(r -> JodaTimeUtils.formatDateTime(r.time).compareTo(endTime) > 0);
        }
        all.sort(Comparator.comparingLong(a -> a.time));

        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // BOM
        sb.append("时间,id,观众名,头像URL,打分,签名,次数,场次,是否拉黑\n");
        for (StrangerRecord r : all) {
            sb.append(JodaTimeUtils.formatDateTime(r.time)).append(',');
            sb.append(r.uid).append(',');
            sb.append(escapeCsv(r.name)).append(',');
            sb.append(escapeCsv(r.face)).append(',');
            sb.append(r.score).append(',');
            String csvSummary = ViewerActivitySummary.buildSummary(r.uid);
            sb.append(escapeCsv((csvSummary.isEmpty() ? "" : " " + csvSummary) + (r.scoreTypes != null ? r.scoreTypes : ""))).append(',');
            sb.append(r.count).append(',');
            sb.append(r.session).append(',');
            sb.append(blockedUids.contains(r.uid) ? "是" : "否").append('\n');
        }
        return sb.toString();
    }

    /** Import records from a CSV string into memory, merging by uid. Returns count of imported rows. */
    public static int importCsv(String csvContent) {
        String[] lines = csvContent.split("\n");
        int imported = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("时间,") || trimmed.startsWith("﻿时间,")) continue;
            try {
                List<String> fields = parseCsvLine(trimmed);
                if (fields.size() < 9) continue;
                long uid = Long.parseLong(fields.get(1));
                String name = fields.get(2);
                String face = fields.get(3);
                int score = Integer.parseInt(fields.get(4));
                String scoreTypes = fields.get(5);
                int count = Integer.parseInt(fields.get(6));
                int session = Integer.parseInt(fields.get(7));
                boolean blocked = "是".equals(fields.get(8));
                long time = JodaTimeUtils.parse(fields.get(0), "yyyy-MM-dd HH:mm:ss").getTime();

                StrangerRecord existing = recordMap.get(uid);
                if (existing == null || time > existing.time) {
                    StrangerRecord record = new StrangerRecord(uid, name, face, score, scoreTypes, count, session);
                    record.time = time;
                    recordMap.put(uid, record);
                }
                if (blocked) blockedUids.add(uid);
                else blockedUids.remove(uid);
                dirtyUids.add(uid);
                imported++;
            } catch (Exception ignored) {}
        }
        if (imported > 0) {
            doFlushFull(currentCsvPath());
        }
        return imported;
    }

    public static boolean toggleBlock(long uid) {
        if (blockedUids.contains(uid)) {
            // unblock
            try {
                HttpUserData.httpPostDeleteBadList(uid);
                blockedUids.remove(uid);
                dirtyUids.add(uid);
                updateBlockedInSqlite(uid, false);
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
                dirtyUids.add(uid);
                updateBlockedInSqlite(uid, true);
                pushBlockUpdate(uid, true);
                return true;
            } catch (Exception e) {
                LOGGER.error("block error uid={}", uid, e);
                return false; // still not blocked
            }
        }
    }

    /** 立即更新 SQLite 中单条记录的 blocked 字段，避免 loadSvData 读到旧值 */
    private static void updateBlockedInSqlite(long uid, boolean blocked) {
        try {
            Long rid = PublicDataConf.ROOMID;
            if (rid == null) return;
            String sql = "UPDATE stranger_viewer SET blocked = ? WHERE room_id = ? AND uid = ?";
            try (java.sql.Connection c = xyz.acproject.danmuji.tools.db.DanmujiDatabase.getConnection();
                 java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, blocked ? 1 : 0);
                ps.setLong(2, rid);
                ps.setLong(3, uid);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.debug("updateBlockedInSqlite error: {}", e.getMessage());
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

    /** 淘汰最旧的条目，使 map 大小降至 MAX_MAP_SIZE */
    private static synchronized void evictOldest() {
        int excess = recordMap.size() - MAX_MAP_SIZE;
        if (excess <= 0) return;
        recordMap.entrySet().stream()
            .sorted((a, b) -> Long.compare(a.getValue().time, b.getValue().time))
            .limit(excess)
            .forEach(e -> {
                recordMap.remove(e.getKey());
                dirtyUids.remove(e.getKey());
            });
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

        StrangerRecord(long uid, String name, String face, int score, String scoreTypes, int count, int session) {
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
