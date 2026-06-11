package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.tools.file.ProFileTools;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * CSV → SQLite 一次性迁移。
 * 首次启动时扫描日志目录下所有 CSV 文件，将历史数据批量导入 SQLite 对应表。
 * 迁移完成后在 _migration_log 记录，后续启动自动跳过。
 */
public class DanmujiMigration {
    private static final Logger LOGGER = LogManager.getLogger(DanmujiMigration.class);
    private static final int BATCH_SIZE = 500;

    private DanmujiMigration() {}

    /**
     * 由 DanmujiDatabase.init() 调用。检查每张表是否需要迁移，需要则执行。
     */
    public static void migrateIfNeeded() {
        try {
            // 先确保数据库能连接
            Connection conn = DanmujiDatabase.getConnection();
            conn.close();
        } catch (Exception e) {
            LOGGER.warn("DanmujiMigration: cannot connect to DB, skip migration: {}", e.getMessage());
            return;
        }

        String logDir = LogPathConf.getLogDir();
        if (logDir == null || logDir.isEmpty()) {
            LOGGER.warn("DanmujiMigration: log dir is null, skip migration");
            return;
        }

        migrateRoomInfo(logDir);
        migrateDanmaku(logDir);
        migrateGiftSummary(logDir);
        migrateVisitorSummary(logDir);
        migrateMatchSummary(logDir);
        migrateFollowSummary(logDir);
        migrateStrangerViewer(logDir);
        migrateFootprint(logDir);
        migrateBlackWhiteList();

        LOGGER.info("DanmujiMigration: all migration checks complete");
    }

    // ======================== 通用工具 ========================

    private static boolean isMigrated(String tableName) {
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM _migration_log WHERE table_name = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void markMigrated(String tableName) {
        try (Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT OR REPLACE INTO _migration_log(table_name) VALUES (?)")) {
            ps.setString(1, tableName);
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("markMigrated failed for {}: {}", tableName, e.getMessage());
        }
    }

    /**
     * 通用 CSV 行解析（处理引号转义）
     */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null || line.isEmpty()) return fields;
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(ch);
                }
            }
        }
        fields.add(sb.toString());
        return fields;
    }

    /**
     * 从文件名解析 roomId 和 anchorName
     * 格式: {roomId}_{anchorName}_{N}_{中文名}.csv
     */
    private static long[] parseRoomAndAnchor(String filename) {
        // 去掉后缀
        String base = filename;
        if (base.endsWith(".csv")) base = base.substring(0, base.length() - 4);
        // 按 _ 分割，提取 roomId（索引0）和 anchorName（索引1 到倒数第二个_之前的所有部分）
        // 文件名示例: 27034701_胡律师看法_1_直播间信息
        // 或: 27034701_胡律师看法_116132301_11_足迹留印
        String[] parts = base.split("_");
        if (parts.length < 3) return null;
        try {
            long roomId = Long.parseLong(parts[0]);
            // anchorName 是从 parts[1] 到倒数第二个 _ 前的所有部分连接
            // 对于 3 段: parts[0]=roomId, parts[1]=anchorName, parts[2]=序号
            // 对于 4 段: parts[0]=roomId, parts[1]=anchorName, parts[2]=序号, parts[3]=名称
            // 对于更多段: roomId_anchorName_part1_part2...（anchorName 可能不含下划线，也可能含）
            // 启发式: 倒数两个 part 是序号和中文名，其余中间部分是 anchorName
            int lastPartIdx = parts.length - 1;
            int secondLastPartIdx = parts.length - 2;

            // 如果倒数第二个部分是纯数字（序号），那么序号之前直到 parts[1] 都是 anchorName
            StringBuilder anchorSb = new StringBuilder();
            int anchorEnd;
            if (parts[secondLastPartIdx].matches("\\d+")) {
                anchorEnd = secondLastPartIdx;
            } else {
                // 旧格式，只有 3 段: roomId_anchorName_中文名
                // 或更多段但序号不是纯数字
                anchorEnd = parts.length - 1;
            }
            for (int i = 1; i < anchorEnd; i++) {
                if (anchorSb.length() > 0) anchorSb.append('_');
                anchorSb.append(parts[i]);
            }
            String anchorName = anchorSb.toString();

            // 对于足迹文件: 可能有 auid 嵌入在中间
            // 格式: {roomId}_{anchorName}_{auid}_11_足迹留印
            long auid = 0;
            // 检查是否有额外的数字段（auid）
            // 如果倒数第二个 part 是 "11"（足迹编号）且倒数第三个是纯数字，那可能是 auid
            if (parts.length >= 4 && "11".equals(parts[parts.length - 2])) {
                // 倒数第三个可能是 auid
                try {
                    auid = Long.parseLong(parts[parts.length - 3]);
                } catch (NumberFormatException ignored) {}
            }

            return new long[]{roomId, auid, anchorName.hashCode()}; // 用 anchorName 字符串本身不太方便，返回到调用方处理
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从文件名解析 roomId 和 anchorName 字符串
     */
    public static String[] parseRoomAnchorStr(String filename) {
        String base = filename;
        if (base.endsWith(".csv")) base = base.substring(0, base.length() - 4);
        String[] parts = base.split("_");
        if (parts.length < 3) return null;

        StringBuilder anchorSb = new StringBuilder();
        // 倒数第二个如果是纯数字（序号），anchor从parts[1]到倒数第三个
        int anchorEnd;
        if (parts.length >= 3 && parts[parts.length - 2].matches("\\d+")) {
            anchorEnd = parts.length - 2;
        } else {
            anchorEnd = parts.length - 1;
        }
        for (int i = 1; i < anchorEnd; i++) {
            if (anchorSb.length() > 0) anchorSb.append('_');
            anchorSb.append(parts[i]);
        }
        return new String[]{parts[0], anchorSb.toString()};
    }

    // ======================== 各表迁移 ========================

    private static void migrateRoomInfo(String logDir) {
        String tag = "room_info_series";
        if (isMigrated(tag)) return;

        File[] files = new File(logDir).listFiles(f -> f.getName().endsWith("_1_直播间信息.csv"));
        if (files == null || files.length == 0) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT OR IGNORE INTO room_info_series(room_id,anchor_name,time_key,watch_count,online_count,like_count) VALUES (?,?,?,?,?,?)";
        for (File f : files) {
            String[] info = parseRoomAnchorStr(f.getName());
            if (info == null) continue;
            long roomId = Long.parseLong(info[0]);
            String anchorName = info[1];

            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                 Connection c = DanmujiDatabase.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                String line = r.readLine(); // skip header
                int batch = 0;
                while ((line = r.readLine()) != null) {
                    String[] parts = line.split(",", 4);
                    if (parts.length < 4) continue;
                    // 时间字段可能含引号，去引号
                    String timeKey = parts[0].replace("\"", "");
                    if (timeKey.length() >= 16) timeKey = timeKey.substring(0, 16); // 截断到分钟精度
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setString(3, timeKey);
                    ps.setLong(4, Long.parseLong(parts[1]));
                    ps.setLong(5, Long.parseLong(parts[2]));
                    ps.setLong(6, Long.parseLong(parts[3]));
                    ps.addBatch();
                    if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
                }
                if (batch > 0) ps.executeBatch();
                total++;
            } catch (Exception e) {
                LOGGER.warn("DanmujiMigration: room_info {} failed: {}", f.getName(), e.getMessage());
            }
        }
        markMigrated(tag);
        LOGGER.info("DanmujiMigration: room_info migrated {} files", total);
    }

    private static void migrateDanmaku(String logDir) {
        String tag = "danmaku_from_csv";
        if (isMigrated(tag)) return;

        File[] files = new File(logDir).listFiles(f -> f.getName().endsWith("_2_弹幕信息.csv"));
        if (files == null || files.length == 0) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT OR IGNORE INTO danmaku(room_id,anchor_name,timestamp,uid,uname,content) VALUES (?,?,?,?,?,?)";
        for (File f : files) {
            String[] info = parseRoomAnchorStr(f.getName());
            if (info == null) continue;
            long roomId = Long.parseLong(info[0]);
            String anchorName = info[1];

            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                 Connection c = DanmujiDatabase.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                String line = r.readLine(); // skip header
                int batch = 0;
                while ((line = r.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 4) continue;
                    // 字段顺序: 发送时间, id, 名字, 弹幕
                    long ts = parseTimeMillis(fields.get(0));
                    long uid = Long.parseLong(fields.get(1));
                    String uname = fields.get(2);
                    String content = fields.get(3);
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setLong(3, ts > 0 ? ts : System.currentTimeMillis());
                    ps.setLong(4, uid);
                    ps.setString(5, uname);
                    ps.setString(6, content);
                    ps.addBatch();
                    if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
                }
                if (batch > 0) ps.executeBatch();
                total++;
            } catch (Exception e) {
                LOGGER.warn("DanmujiMigration: danmaku {} failed: {}", f.getName(), e.getMessage());
            }
        }
        markMigrated(tag);
        LOGGER.info("DanmujiMigration: danmaku migrated {} files", total);
    }

    private static void migrateGiftSummary(String logDir) {
        String tag = "gift_summary";
        if (isMigrated(tag)) return;

        File[] files = new File(logDir).listFiles(f -> f.getName().endsWith("_3_礼物信息.csv"));
        if (files == null || files.length == 0) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT OR REPLACE INTO gift_summary(room_id,anchor_name,uid,uname,gift_name,total_price,count,latest_time) VALUES (?,?,?,?,?,?,?,?)";
        for (File f : files) {
            String[] info = parseRoomAnchorStr(f.getName());
            if (info == null) continue;
            long roomId = Long.parseLong(info[0]);
            String anchorName = info[1];

            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                 Connection c = DanmujiDatabase.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                String line = r.readLine(); // skip header
                int batch = 0;
                while ((line = r.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 6) continue;
                    // 最新时间,id,名字,赠送礼物名字,总金额,赠礼次数
                    long uid = Long.parseLong(fields.get(1));
                    String uname = fields.get(2);
                    String giftName = fields.get(3);
                    long totalPrice = Long.parseLong(fields.get(4));
                    int count = Integer.parseInt(fields.get(5));
                    long latestTime = parseTimeMillis(fields.get(0));
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setLong(3, uid);
                    ps.setString(4, uname);
                    ps.setString(5, giftName);
                    ps.setLong(6, totalPrice);
                    ps.setInt(7, count);
                    ps.setLong(8, latestTime > 0 ? latestTime : System.currentTimeMillis());
                    ps.addBatch();
                    if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
                }
                if (batch > 0) ps.executeBatch();
                total++;
            } catch (Exception e) {
                LOGGER.warn("DanmujiMigration: gift_summary {} failed: {}", f.getName(), e.getMessage());
            }
        }
        markMigrated(tag);
        LOGGER.info("DanmujiMigration: gift_summary migrated {} files", total);
    }

    private static void migrateVisitorSummary(String logDir) {
        String tag = "visitor_summary";
        if (isMigrated(tag)) return;

        File[] files = new File(logDir).listFiles(f -> f.getName().endsWith("_4_观众信息.csv"));
        if (files == null || files.length == 0) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT OR REPLACE INTO visitor_summary(room_id,anchor_name,uid,uname,score,score_type,count,in_pn_table,session,latest_entry_time) VALUES (?,?,?,?,?,?,?,?,?,?)";
        for (File f : files) {
            String[] info = parseRoomAnchorStr(f.getName());
            if (info == null) continue;
            long roomId = Long.parseLong(info[0]);
            String anchorName = info[1];

            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                 Connection c = DanmujiDatabase.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                String line = r.readLine();
                int batch = 0;
                while ((line = r.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 6) continue;
                    // 最近,id,观众,打分,打分类型,次数,判定表,场次
                    long uid = Long.parseLong(fields.get(1));
                    String uname = fields.get(2);
                    int score = Integer.parseInt(fields.get(3));
                    String scoreType = fields.get(4);
                    int count = Integer.parseInt(fields.get(5));
                    int inPnTable = fields.size() >= 7 && "是".equals(fields.get(6)) ? 1 : 0;
                    int session = 1;
                    if (fields.size() >= 8) {
                        try { session = Integer.parseInt(fields.get(7)); } catch (NumberFormatException ignored) {}
                        if (session == 0) session = 1;
                    }
                    long latestTime = parseTimeMillis(fields.get(0));
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setLong(3, uid);
                    ps.setString(4, uname);
                    ps.setInt(5, score);
                    ps.setString(6, scoreType);
                    ps.setInt(7, count);
                    ps.setInt(8, inPnTable);
                    ps.setInt(9, session);
                    ps.setLong(10, latestTime > 0 ? latestTime : System.currentTimeMillis());
                    ps.addBatch();
                    if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
                }
                if (batch > 0) ps.executeBatch();
                total++;
            } catch (Exception e) {
                LOGGER.warn("DanmujiMigration: visitor_summary {} failed: {}", f.getName(), e.getMessage());
            }
        }
        markMigrated(tag);
        LOGGER.info("DanmujiMigration: visitor_summary migrated {} files", total);
    }

    private static void migrateMatchSummary(String logDir) {
        String tag = "match_summary";
        if (isMigrated(tag)) return;

        File[] files = new File(logDir).listFiles(f -> f.getName().endsWith("_5_匹配信息.csv"));
        if (files == null || files.length == 0) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT OR REPLACE INTO match_summary(room_id,anchor_name,matched_uid,matched_name,score,count,latest_match_time) VALUES (?,?,?,?,?,?,?)";
        for (File f : files) {
            String[] info = parseRoomAnchorStr(f.getName());
            if (info == null) continue;
            long roomId = Long.parseLong(info[0]);
            String anchorName = info[1];

            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                 Connection c = DanmujiDatabase.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                String line = r.readLine();
                int batch = 0;
                while ((line = r.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 5) continue;
                    // 最近匹配,匹配id,匹配名,匹配分,匹配次数
                    long uid = Long.parseLong(fields.get(1));
                    String name = fields.get(2);
                    int score = Integer.parseInt(fields.get(3));
                    int count = Integer.parseInt(fields.get(4));
                    long latestTime = parseTimeMillis(fields.get(0));
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setLong(3, uid);
                    ps.setString(4, name);
                    ps.setInt(5, score);
                    ps.setInt(6, count);
                    ps.setLong(7, latestTime > 0 ? latestTime : System.currentTimeMillis());
                    ps.addBatch();
                    if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
                }
                if (batch > 0) ps.executeBatch();
                total++;
            } catch (Exception e) {
                LOGGER.warn("DanmujiMigration: match_summary {} failed: {}", f.getName(), e.getMessage());
            }
        }
        markMigrated(tag);
        LOGGER.info("DanmujiMigration: match_summary migrated {} files", total);
    }

    private static void migrateFollowSummary(String logDir) {
        String tag = "follow_summary";
        if (isMigrated(tag)) return;

        File[] files = new File(logDir).listFiles(f -> f.getName().endsWith("_6_关注人信息.csv"));
        if (files == null || files.length == 0) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT OR REPLACE INTO follow_summary(room_id,anchor_name,uid,uname,count,latest_time) VALUES (?,?,?,?,?,?)";
        for (File f : files) {
            String[] info = parseRoomAnchorStr(f.getName());
            if (info == null) continue;
            long roomId = Long.parseLong(info[0]);
            String anchorName = info[1];

            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                 Connection c = DanmujiDatabase.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                String line = r.readLine();
                int batch = 0;
                while ((line = r.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 4) continue;
                    // 最新时间,id,名字,次数
                    long uid = Long.parseLong(fields.get(1));
                    String name = fields.get(2);
                    int count = Integer.parseInt(fields.get(3));
                    long latestTime = parseTimeMillis(fields.get(0));
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setLong(3, uid);
                    ps.setString(4, name);
                    ps.setInt(5, count);
                    ps.setLong(6, latestTime > 0 ? latestTime : System.currentTimeMillis());
                    ps.addBatch();
                    if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
                }
                if (batch > 0) ps.executeBatch();
                total++;
            } catch (Exception e) {
                LOGGER.warn("DanmujiMigration: follow_summary {} failed: {}", f.getName(), e.getMessage());
            }
        }
        markMigrated(tag);
        LOGGER.info("DanmujiMigration: follow_summary migrated {} files", total);
    }

    private static void migrateStrangerViewer(String logDir) {
        String tag = "stranger_viewer";
        if (isMigrated(tag)) return;

        File[] files = new File(logDir).listFiles(f -> f.getName().endsWith("_7_陌生观众.csv"));
        if (files == null || files.length == 0) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT OR REPLACE INTO stranger_viewer(room_id,anchor_name,uid,name,face,score,score_types,count,session,blocked,time) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        for (File f : files) {
            String[] info = parseRoomAnchorStr(f.getName());
            if (info == null) continue;
            long roomId = Long.parseLong(info[0]);
            String anchorName = info[1];

            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                 Connection c = DanmujiDatabase.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                String line = r.readLine();
                int batch = 0;
                while ((line = r.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 9) continue;
                    // 时间,id,观众名,头像URL,打分,签名,次数,场次,是否拉黑
                    long uid = Long.parseLong(fields.get(1));
                    String name = fields.get(2);
                    String face = fields.get(3);
                    int score = Integer.parseInt(fields.get(4));
                    String scoreTypes = fields.get(5);
                    int count = Integer.parseInt(fields.get(6));
                    int session = 1;
                    try { session = Integer.parseInt(fields.get(7)); } catch (NumberFormatException ignored) {}
                    if (session == 0) session = 1;
                    int blocked = "是".equals(fields.get(8)) ? 1 : 0;
                    long time = parseTimeMillis(fields.get(0));
                    ps.setLong(1, roomId);
                    ps.setString(2, anchorName);
                    ps.setLong(3, uid);
                    ps.setString(4, name);
                    ps.setString(5, face);
                    ps.setInt(6, score);
                    ps.setString(7, scoreTypes);
                    ps.setInt(8, count);
                    ps.setInt(9, session);
                    ps.setInt(10, blocked);
                    ps.setLong(11, time > 0 ? time : System.currentTimeMillis());
                    ps.addBatch();
                    if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
                }
                if (batch > 0) ps.executeBatch();
                total++;
            } catch (Exception e) {
                LOGGER.warn("DanmujiMigration: stranger_viewer {} failed: {}", f.getName(), e.getMessage());
            }
        }
        markMigrated(tag);
        LOGGER.info("DanmujiMigration: stranger_viewer migrated {} files", total);
    }

    private static void migrateFootprint(String logDir) {
        String tag = "footprint";
        if (isMigrated(tag)) return;

        // 匹配两种格式: *_11_足迹留印.csv（旧）和 *_*_11_足迹留印.csv（新含auid）
        File[] files = new File(logDir).listFiles(f -> f.getName().endsWith("_11_足迹留印.csv"));
        if (files == null || files.length == 0) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT INTO footprint(room_id,anchor_name,auid,uid,uname,utime) VALUES (?,?,?,?,?,?)";
        for (File f : files) {
            // 从文件名解析 roomId, anchorName, auid
            String name = f.getName();
            String base = name.endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
            String[] parts = base.split("_");
            if (parts.length < 3) continue;

            long roomId;
            try { roomId = Long.parseLong(parts[0]); } catch (NumberFormatException e) { continue; }

            // 确定 auid 和 anchorName
            // 新格式: roomId_anchorName_auid_11_足迹留印 → parts: [roomId, ..., auid, "11", "足迹留印"]
            // 旧格式: roomId_anchorName_11_足迹留印 → parts: [roomId, ..., "11", "足迹留印"]
            long auid = 0;
            int idx11 = -1;
            for (int i = 0; i < parts.length; i++) {
                if ("11".equals(parts[i])) { idx11 = i; break; }
            }
            StringBuilder anchorSb = new StringBuilder();
            if (idx11 > 1 && parts[idx11 - 1].matches("\\d+")) {
                // 新格式: parts[idx11-1] 是 auid
                try { auid = Long.parseLong(parts[idx11 - 1]); } catch (NumberFormatException ignored) {}
                for (int i = 1; i < idx11 - 1; i++) {
                    if (anchorSb.length() > 0) anchorSb.append('_');
                    anchorSb.append(parts[i]);
                }
            } else {
                for (int i = 1; i < idx11; i++) {
                    if (anchorSb.length() > 0) anchorSb.append('_');
                    anchorSb.append(parts[i]);
                }
            }
            String anchorName = anchorSb.toString();

            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                 Connection c = DanmujiDatabase.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                String line;
                int batch = 0;
                // 足迹文件可能有 # 注释头，需要跳过
                while ((line = r.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                    // 解析: uid,"uname",utime
                    List<String> fields = parseCsvLine(trimmed);
                    if (fields.size() < 3) continue;
                    try {
                        long uid = Long.parseLong(fields.get(0));
                        String uname = fields.get(1);
                        long utime = Long.parseLong(fields.get(2));
                        ps.setLong(1, roomId);
                        ps.setString(2, anchorName);
                        ps.setLong(3, auid);
                        ps.setLong(4, uid);
                        ps.setString(5, uname);
                        ps.setLong(6, utime);
                        ps.addBatch();
                        if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
                    } catch (NumberFormatException ignored) {}
                }
                if (batch > 0) ps.executeBatch();
                total++;
            } catch (Exception e) {
                LOGGER.warn("DanmujiMigration: footprint {} failed: {}", f.getName(), e.getMessage());
            }
        }
        markMigrated(tag);
        LOGGER.info("DanmujiMigration: footprint migrated {} files", total);
    }

    private static void migrateBlackWhiteList() {
        String tag = "black_white_list";
        if (isMigrated(tag)) return;

        String storeDir = ProFileTools.getStoreDir();
        if (storeDir == null || storeDir.isEmpty()) { markMigrated(tag); return; }

        int total = 0;
        String sql = "INSERT OR REPLACE INTO black_white_list(list_type,uid,name,create_time,update_time,score,score_type,room_id,count) VALUES (?,?,?,?,?,?,?,?,?)";

        // 黑名单
        File blackFile = new File(storeDir + File.separator + "set" + File.separator + "本地黑名单.csv");
        total += migrateSingleBWList(blackFile, "black", sql);
        // 白名单
        File whiteFile = new File(storeDir + File.separator + "set" + File.separator + "本地白名单.csv");
        total += migrateSingleBWList(whiteFile, "white", sql);

        markMigrated(tag);
        LOGGER.info("DanmujiMigration: black_white_list migrated {} files", total);
    }

    private static int migrateSingleBWList(File file, String listType, String sql) {
        if (!file.exists()) return 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
             Connection c = DanmujiDatabase.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            String line = r.readLine(); // skip header
            int batch = 0;
            while ((line = r.readLine()) != null) {
                List<String> fields = parseCsvLine(line);
                if (fields.isEmpty() || fields.get(0).trim().isEmpty()) continue;
                // id,name,createTime,updateTime,score,scoreType,roomId,count
                long uid = Long.parseLong(fields.get(0).trim());
                String name = fields.size() > 1 ? fields.get(1) : "";
                long createTime = fields.size() > 2 ? parseTimeOrMillis(fields.get(2)) : 0L;
                long updateTime = fields.size() > 3 ? parseTimeOrMillis(fields.get(3)) : 0L;
                int score = fields.size() > 4 ? parseIntSafe(fields.get(4)) : 0;
                String scoreType = fields.size() > 5 ? fields.get(5) : "";
                long roomId = fields.size() > 6 ? parseLongSafe(fields.get(6)) : 0L;
                int count = fields.size() > 7 ? parseIntSafe(fields.get(7)) : 0;
                if (updateTime == 0) updateTime = System.currentTimeMillis();
                if (createTime == 0) createTime = updateTime;

                ps.setString(1, listType);
                ps.setLong(2, uid);
                ps.setString(3, name);
                ps.setLong(4, createTime);
                ps.setLong(5, updateTime);
                ps.setInt(6, score);
                ps.setString(7, scoreType);
                ps.setLong(8, roomId);
                ps.setInt(9, count);
                ps.addBatch();
                if (++batch >= BATCH_SIZE) { ps.executeBatch(); batch = 0; }
            }
            if (batch > 0) ps.executeBatch();
            return 1;
        } catch (Exception e) {
            LOGGER.warn("DanmujiMigration: black_white_list {} failed: {}", file.getName(), e.getMessage());
            return 0;
        }
    }

    // ======================== 时间/数字解析辅助 ========================

    private static long parseTimeMillis(String s) {
        if (s == null || s.trim().isEmpty()) return 0L;
        try {
            // 尝试 "yyyy-MM-dd HH:mm:ss" 格式
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(s.trim()).getTime();
        } catch (Exception e1) {
            // 尝试毫秒时间戳
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException e2) { return 0L; }
        }
    }

    private static long parseTimeOrMillis(String s) {
        return parseTimeMillis(s);
    }

    private static int parseIntSafe(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static long parseLongSafe(String s) {
        if (s == null || s.trim().isEmpty()) return 0L;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; }
    }
}
