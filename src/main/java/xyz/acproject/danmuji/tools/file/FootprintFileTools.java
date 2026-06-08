package xyz.acproject.danmuji.tools.file;

import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 足迹留印 / 足迹还原 文件工具类
 * CSV 格式（标准引号转义）：每行 uid,"uname",utime
 * 文件头部以 # 注释行记录直播间上下文信息（房间号、主播名、主播UID）
 * 批量写入架构，确保消息处理热路径上零 I/O 延迟
 */
public class FootprintFileTools {
    private static final Logger LOGGER = LogManager.getLogger(FootprintFileTools.class);
    private volatile static FootprintFileTools instance;

    // 批量写入队列 + 后台写入线程（遵循 LogFileTools 模式）
    private static final LinkedBlockingQueue<LogEntry> batchQueue = new LinkedBlockingQueue<>(20000);

    // 跟踪哪些文件已写入头部信息（本 JVM 会话内）
    private final Set<String> headerWrittenFiles = ConcurrentHashMap.newKeySet();

    private static class LogEntry {
        final String filePath;
        final String line;
        LogEntry(String filePath, String line) {
            this.filePath = filePath;
            this.line = line;
        }
    }

    // 路径缓存
    private volatile String filePathCache;
    private volatile String filePathKey;

    static {
        Thread writer = new Thread(() -> {
            while (true) {
                try {
                    LogEntry first = batchQueue.poll(1, TimeUnit.SECONDS);
                    if (first != null) {
                        java.util.Map<String, java.util.List<String>> batches = new java.util.HashMap<>();
                        java.util.List<String> list = new ArrayList<>();
                        list.add(first.line);
                        batches.put(first.filePath, list);

                        java.util.List<LogEntry> more = new ArrayList<>();
                        batchQueue.drainTo(more, 500);
                        for (LogEntry e : more) {
                            batches.computeIfAbsent(e.filePath, k -> new ArrayList<>()).add(e.line);
                        }

                        for (java.util.Map.Entry<String, java.util.List<String>> batch : batches.entrySet()) {
                            try (BufferedWriter bw = new BufferedWriter(
                                    new OutputStreamWriter(new FileOutputStream(batch.getKey(), true), "UTF-8"), 8192)) {
                                for (String ln : batch.getValue()) {
                                    bw.write(ln);
                                    bw.newLine();
                                }
                            } catch (Exception e) {
                                LOGGER.error("FootprintFileTools flush error", e);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        writer.setDaemon(true);
        writer.setName("FootprintFileWriter");
        writer.start();
    }

    private FootprintFileTools() {}

    public static FootprintFileTools getInstance() {
        if (instance == null) {
            synchronized (FootprintFileTools.class) {
                if (instance == null) {
                    instance = new FootprintFileTools();
                }
            }
        }
        return instance;
    }

    private String getBaseDir() {
        String p = LogPathConf.getLogDir() + "/";
        new File(p).mkdirs();
        return p;
    }

    private static String safeFileName(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String resolveFilePath() {
        String anchor = safeFileName(PublicDataConf.ANCHOR_NAME);
        long auid = PublicDataConf.AUID != null ? PublicDataConf.AUID : 0L;
        String key = PublicDataConf.ROOMID + "_" + anchor + "_" + auid;
        if (filePathCache == null || !key.equals(filePathKey)) {
            filePathCache = getBaseDir() + key + "_11_足迹留印.csv";
            filePathKey = key;
        }
        return filePathCache;
    }

    /**
     * 记录一条足迹（非阻塞，入队后批量写入）
     * 文件元数据（ROOMID, ANCHOR_NAME, AUID）已编码在文件名中，不再写 JSON 头部
     * @param timestamp 事件时间戳（毫秒）
     * @param uid       用户 UID
     * @param uname     用户名
     */
    public void record(long timestamp, long uid, String uname) {
        String filePath = resolveFilePath();

        // CSV 格式：uid,"uname",utime  —— uname 用双引号包裹以处理含逗号的情况
        String escapedUname = uname != null ? uname.replace("\"", "\"\"") : "";
        String line = uid + ",\"" + escapedUname + "\"," + timestamp;
        batchQueue.offer(new LogEntry(filePath, line));
    }

    /**
     * 构建 CSV 头部注释行，记录当前直播间上下文信息
     * 格式：# {"roomId":12345,"anchorName":"xxx","auid":67890}
     */
    private String buildHeaderLine() {
        JSONObject meta = new JSONObject();
        meta.put("roomId", PublicDataConf.ROOMID != null ? PublicDataConf.ROOMID : 0);
        meta.put("anchorName", PublicDataConf.ANCHOR_NAME != null ? PublicDataConf.ANCHOR_NAME : "");
        meta.put("auid", PublicDataConf.AUID != null ? PublicDataConf.AUID : 0);
        return "# " + meta.toString();
    }

    /**
     * 读取并解析足迹 CSV 文件，同时返回文件头部记录的直播间上下文
     * @return ParseResult 包含元数据和记录列表
     */
    public ParseResult readFileWithMeta(String filePath) throws IOException {
        SessionMeta meta = new SessionMeta();
        List<FootprintRecord> records = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) return new ParseResult(meta, records);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // 解析头部注释行
                if (trimmed.startsWith("#")) {
                    parseHeaderLine(trimmed, meta);
                    continue;
                }

                try {
                    FootprintRecord rec = parseLine(trimmed);
                    if (rec != null) {
                        records.add(rec);
                    }
                } catch (Exception e) {
                    LOGGER.warn("FootprintFileTools skip malformed line {}: {}", lineNum, trimmed);
                }
            }
        }
        return new ParseResult(meta, records);
    }

    /**
     * 读取并解析足迹 CSV 文件（仅记录，不含元数据）
     */
    public List<FootprintRecord> readFile(String filePath) throws IOException {
        return readFileWithMeta(filePath).records;
    }

    /**
     * 解析头部 JSON 注释行：# {"roomId":...,"anchorName":...,"auid":...}
     */
    private void parseHeaderLine(String line, SessionMeta meta) {
        try {
            String jsonStr = line.substring(1).trim(); // 去掉 #
            if (jsonStr.startsWith("{")) {
                JSONObject obj = JSONObject.parseObject(jsonStr);
                if (obj.containsKey("roomId")) {
                    meta.roomId = obj.getLong("roomId");
                }
                if (obj.containsKey("anchorName")) {
                    meta.anchorName = obj.getString("anchorName");
                }
                if (obj.containsKey("auid")) {
                    meta.auid = obj.getLong("auid");
                }
            }
        } catch (Exception e) {
            // 非 JSON 注释行，忽略
        }
    }

    /**
     * 解析单行 CSV：uid,"uname",utime
     * 返回 null 表示 uid 无效（调用方应跳过该行）
     */
    private FootprintRecord parseLine(String line) {
        // 跳过注释行
        if (line.startsWith("#")) return null;

        long uid = 0;
        String uname = "";
        long utime = 0;

        int firstComma = line.indexOf(',');
        if (firstComma < 0) {
            // 只有 uid
            try {
                uid = Long.parseLong(line.trim());
            } catch (NumberFormatException e) {
                return null; // uid 无效
            }
            return new FootprintRecord(0, uid, "");
        }

        // 解析 uid
        String uidStr = line.substring(0, firstComma).trim();
        try {
            uid = Long.parseLong(uidStr);
        } catch (NumberFormatException e) {
            return null; // uid 无效
        }

        String remaining = line.substring(firstComma + 1);

        // 解析 "uname"（双引号包裹）
        if (!remaining.isEmpty() && remaining.charAt(0) == '"') {
            int endQuote = findClosingQuote(remaining, 1);
            if (endQuote >= 0) {
                uname = remaining.substring(1, endQuote).replace("\"\"", "\"");
                remaining = remaining.substring(endQuote + 1);
            } else {
                // 引号未闭合，取剩余全部作为 uname
                uname = remaining.substring(1).replace("\"\"", "\"");
                remaining = "";
            }
        } else if (!remaining.isEmpty()) {
            // 无引号包裹的 uname（兼容手工编辑的简化格式）
            int nextComma = remaining.indexOf(',');
            if (nextComma >= 0) {
                uname = remaining.substring(0, nextComma).trim();
                remaining = remaining.substring(nextComma);
            } else {
                uname = remaining.trim();
                remaining = "";
            }
        }

        // 解析 utime（跳过前导逗号）
        if (!remaining.isEmpty()) {
            if (remaining.charAt(0) == ',') {
                remaining = remaining.substring(1).trim();
            }
            if (!remaining.isEmpty()) {
                try {
                    utime = Long.parseLong(remaining);
                } catch (NumberFormatException e) {
                    // utime 解析失败，使用默认值 0
                }
            }
        }

        return new FootprintRecord(utime, uid, uname);
    }

    /**
     * 从 start 位置开始查找闭合的双引号
     */
    private int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '"') {
                // 检查是否是转义引号 ""
                if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    i++; // 跳过转义引号
                    continue;
                }
                return i;
            }
        }
        return -1; // 未闭合
    }

    /**
     * 列出所有足迹 CSV 文件
     */
    public List<String> listFiles() {
        List<String> result = new ArrayList<>();
        File dir = new File(getBaseDir());
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith("_11_足迹留印.csv"));
            if (files != null) {
                for (File f : files) {
                    result.add(f.getAbsolutePath());
                }
            }
        }
        return result;
    }

    /**
     * 删除足迹文件
     */
    public boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            // 清除路径缓存和头部写入标记
            if (filePath.equals(filePathCache)) {
                filePathCache = null;
                filePathKey = null;
            }
            headerWrittenFiles.remove(filePath);
            return file.delete();
        }
        return false;
    }

    /**
     * 从足迹文件名解析直播间上下文
     * 新格式: {roomId}_{anchorName}_{auid}_11_足迹留印.csv
     * 旧格式: {roomId}_{anchorName}_11_足迹留印.csv（向后兼容，auid 为 0）
     * anchorName 可能包含下划线；从右向左解析保证正确拆分
     * 例如: "27887575_是Winter喵_10850097_11_足迹留印.csv" → roomId=27887575, anchorName="是Winter喵", auid=10850097
     *
     * @param fileName 文件名（不含路径）
     * @return SessionMeta，解析失败时 hasData() 返回 false
     */
    public static SessionMeta parseFileNameForContext(String fileName) {
        SessionMeta meta = new SessionMeta();
        if (fileName == null || fileName.isEmpty()) return meta;

        // 去掉 _11_足迹留印.csv 后缀
        String suffix = "_11_足迹留印.csv";
        if (!fileName.endsWith(suffix)) return meta;
        String core = fileName.substring(0, fileName.length() - suffix.length());
        if (core.isEmpty()) return meta;

        // 从右向左解析：最后一个 _ 之后如果是纯数字，则为 auid（新格式）
        int lastUnderscore = core.lastIndexOf('_');
        if (lastUnderscore > 0) {
            String auidStr = core.substring(lastUnderscore + 1);
            try {
                meta.auid = Long.parseLong(auidStr);
                core = core.substring(0, lastUnderscore);
            } catch (NumberFormatException e) {
                // 老格式：最后一段不是数字，整个 core 就是 roomId_anchorName
            }
        }

        // 第一个 _ 之前是 roomId（纯数字），之后是 anchorName
        int firstUnderscore = core.indexOf('_');
        if (firstUnderscore <= 0) return meta;

        String roomIdStr = core.substring(0, firstUnderscore);
        String anchorName = core.substring(firstUnderscore + 1);

        try {
            meta.roomId = Long.parseLong(roomIdStr);
        } catch (NumberFormatException e) {
            return meta; // roomId 解析失败
        }
        meta.anchorName = anchorName;
        return meta;
    }

    /**
     * 足迹记录数据类
     */
    public static class FootprintRecord {
        public final long utime;
        public final long uid;
        public final String uname;

        public FootprintRecord(long utime, long uid, String uname) {
            this.utime = utime;
            this.uid = uid;
            this.uname = uname != null ? uname : "";
        }
    }

    /**
     * 直播间会话元数据（从 CSV 头部注释行解析）
     */
    public static class SessionMeta {
        public long roomId = 0;
        public String anchorName = "";
        public long auid = 0;

        public boolean hasData() {
            return roomId != 0 || !anchorName.isEmpty() || auid != 0;
        }
    }

    /**
     * readFileWithMeta 的返回结果
     */
    public static class ParseResult {
        public final SessionMeta meta;
        public final List<FootprintRecord> records;

        public ParseResult(SessionMeta meta, List<FootprintRecord> records) {
            this.meta = meta;
            this.records = records;
        }
    }

    /**
     * 文件批次：一个足迹文件及其解析结果
     */
    public static class FileBatch {
        public final String fileName;
        public final SessionMeta meta;
        public final List<FootprintRecord> records;

        public FileBatch(String fileName, SessionMeta meta, List<FootprintRecord> records) {
            this.fileName = fileName;
            this.meta = meta;
            this.records = records;
        }
    }
}
