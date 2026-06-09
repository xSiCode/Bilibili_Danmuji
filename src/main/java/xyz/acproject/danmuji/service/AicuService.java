package xyz.acproject.danmuji.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AICU 用户数据查询服务
 * 通过 aicu.cc API 获取 B站用户的评论、视频弹幕、直播弹幕数据，并持久化到本地 SQLite
 */
@Service
public class AicuService {
    private static final Logger LOGGER = LogManager.getLogger(AicuService.class);

    private static final String API_BASE = "https://api.aicu.cc";
    private static final long DELAY_MS = 1500L;
    private static final int PAGE_SIZE = 100;

    // 长超时 OkHttpClient（aicu.cc 部分接口响应慢，60s 超时）
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private volatile boolean tablesEnsured = false;

    /**
     * 确保 AICU 相关表存在（每次 JVM 启动后首次调用时执行）
     */
    private void ensureTables() {
        if (tablesEnsured) return;
        synchronized (this) {
            if (tablesEnsured) return;
            try (Connection conn = DanmujiDatabase.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aicu_usermark (" +
                    "  uid        BIGINT PRIMARY KEY," +
                    "  data_json  TEXT," +
                    "  fetch_time BIGINT" +
                    ")"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aicu_reply (" +
                    "  uid         BIGINT NOT NULL," +
                    "  pn          INTEGER NOT NULL DEFAULT 1," +
                    "  data_json   TEXT," +
                    "  total_count INTEGER DEFAULT 0," +
                    "  fetch_time  BIGINT," +
                    "  PRIMARY KEY (uid, pn)" +
                    ")"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aicu_videodm (" +
                    "  uid         BIGINT NOT NULL," +
                    "  pn          INTEGER NOT NULL DEFAULT 1," +
                    "  data_json   TEXT," +
                    "  total_count INTEGER DEFAULT 0," +
                    "  fetch_time  BIGINT," +
                    "  PRIMARY KEY (uid, pn)" +
                    ")"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aicu_livedm (" +
                    "  uid         BIGINT NOT NULL," +
                    "  pn          INTEGER NOT NULL DEFAULT 1," +
                    "  data_json   TEXT," +
                    "  total_count INTEGER DEFAULT 0," +
                    "  fetch_time  BIGINT," +
                    "  PRIMARY KEY (uid, pn)" +
                    ")"
                );
                tablesEnsured = true;
                LOGGER.info("AicuService: all 4 tables ensured at {}", DanmujiDatabase.getDbPath());
            } catch (Exception e) {
                LOGGER.error("AicuService ensureTables failed: {}", e.getMessage(), e);
            }
        }
    }

    // ======================== 公开方法 ========================

    /**
     * 删除某 uid 的全部 AICU 数据，返回删除行数
     */
    public int deleteData(Long uid) {
        ensureTables();
        int total = 0;
        for (String table : new String[]{"aicu_usermark", "aicu_reply", "aicu_videodm", "aicu_livedm"}) {
            try (Connection conn = DanmujiDatabase.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE uid = ?")) {
                ps.setLong(1, uid);
                total += ps.executeUpdate();
            } catch (Exception e) {
                LOGGER.error("deleteData {} error for uid={}: {}", table, uid, e.getMessage());
            }
        }
        LOGGER.info("AICU deleted {} rows for uid={}", total, uid);
        return total;
    }

    /**
     * 按用户名模糊搜索本地库找 UID：优先查 danmaku 表，再查 aicu_usermark 的 hname
     */
    public List<Map<String, Object>> findUidByUname(String uname) {
        ensureTables();
        List<Map<String, Object>> result = new ArrayList<>();
        if (StringUtils.isBlank(uname)) return result;
        String escaped = uname.trim().replace("'", "''");
        String lower = uname.trim().toLowerCase();

        // 1. 搜索本地 danmaku 表
        String sql = "SELECT DISTINCT uid, uname FROM danmaku WHERE uname LIKE '%" + escaped
                + "%' GROUP BY uid ORDER BY MAX(timestamp) DESC LIMIT 10";
        try (Connection conn = DanmujiDatabase.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uid", rs.getLong("uid"));
                row.put("uname", rs.getString("uname"));
                result.add(row);
            }
        } catch (Exception e) {
            LOGGER.error("findUidByUname danmaku error for '{}': {}", uname, e.getMessage());
        }

        // 2. 搜索 aicu_usermark 表中的历史用户名
        try (Connection conn = DanmujiDatabase.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uid, data_json FROM aicu_usermark")) {
            while (rs.next()) {
                long uid = rs.getLong("uid");
                // 去重
                boolean already = result.stream().anyMatch(r -> ((Long) r.get("uid")) == uid);
                if (already) continue;

                String json = rs.getString("data_json");
                if (json != null) {
                    try {
                        JSONObject obj = JSONObject.parseObject(json);
                        JSONArray hnameArr = obj.getJSONArray("hname");
                        if (hnameArr != null) {
                            for (Object o : hnameArr) {
                                String name = o != null ? o.toString() : "";
                                if (name.toLowerCase().contains(lower)) {
                                    Map<String, Object> row = new LinkedHashMap<>();
                                    row.put("uid", uid);
                                    row.put("uname", name);
                                    result.add(row);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LOGGER.error("findUidByUname aicu_usermark error for '{}': {}", uname, e.getMessage());
        }

        return result;
    }

    /**
     * 从本地 SQLite 读取缓存数据
     */
    public JSONObject getCached(Long uid, boolean brief) {
        ensureTables();
        JSONObject result = new JSONObject();
        result.put("uid", uid);
        result.put("fromCache", true);

        // usermark
        JSONObject usermark = loadSingle("aicu_usermark", uid);
        result.put("usermark", usermark != null ? usermark.getJSONObject("data") : null);

        if (brief) {
            // 简略模式：每类只读第一页
            JSONObject rp = loadFirstPage("aicu_reply", uid);
            result.put("reply", buildPageResult(rp, rp.getIntValue("total")));
            JSONObject vp = loadFirstPage("aicu_videodm", uid);
            result.put("videodm", buildPageResult(vp, vp.getIntValue("total")));
            JSONObject lp = loadFirstPage("aicu_livedm", uid);
            result.put("livedm", buildPageResult(lp, lp.getIntValue("total")));
            int rt = rp.getIntValue("total"), vt = vp.getIntValue("total"), lt = lp.getIntValue("total");
            result.put("cached", rt > 0 || vt > 0 || lt > 0);
            result.put("moreToFetch", false);
        } else {
            // 全量模式：读所有页
            JSONObject replyPages = loadPages("aicu_reply", uid);
            int replyTotal = replyPages.getIntValue("total");
            result.put("reply", buildPageResult(replyPages, replyTotal));

            JSONObject videodmPages = loadPages("aicu_videodm", uid);
            int videodmTotal = videodmPages.getIntValue("total");
            result.put("videodm", buildPageResult(videodmPages, videodmTotal));

            JSONObject livedmPages = loadPages("aicu_livedm", uid);
            int livedmTotal = livedmPages.getIntValue("total");
            result.put("livedm", buildPageResult(livedmPages, livedmTotal));

            result.put("cached", replyTotal > 0 || videodmTotal > 0 || livedmTotal > 0);
            result.put("moreToFetch", false);
        }
        return result;
    }

    /**
     * API 搜索：始终从 aicu.cc 拉取（结果入库覆盖旧数据），brief 控制是否后台拉全量
     */
    public JSONObject search(Long uid, boolean brief) {
        ensureTables();
        LOGGER.info("AICU search for uid={}: fetching from aicu.cc (brief={})", uid, brief);
        JSONObject result = new JSONObject();
        result.put("uid", uid);
        result.put("fromCache", false);

        long nowSec = System.currentTimeMillis() / 1000;

        // 1. usermark
        JSONObject usermarkData = null;
        try {
            String url = API_BASE + "/api/v3/user/getusermark?uid=" + uid;
            JSONObject resp = fetchJson(url);
            if (resp != null && resp.getIntValue("code") == 0 && resp.containsKey("data")) {
                usermarkData = resp.getJSONObject("data");
                upsertSingle("aicu_usermark", uid, usermarkData.toJSONString(), nowSec);
            }
            delay();
        } catch (Exception e) {
            LOGGER.error("AICU usermark fetch failed for uid={}: {}", uid, e.getMessage());
        }
        result.put("usermark", usermarkData);

        // 2. 拉第一页（每个独立 try-catch，单个失败不影响其他）
        String replyUrlTpl  = API_BASE + "/api/v3/search/getreply?uid=" + uid + "&pn=%d&ps=" + PAGE_SIZE + "&mode=1";
        String videodmUrlTpl= API_BASE + "/api/v3/search/getvideodm?uid=" + uid + "&pn=%d&ps=" + PAGE_SIZE;

        JSONObject replyP1 = new JSONObject(); replyP1.put("pages", new JSONObject()); replyP1.put("total", 0);
        JSONObject videodmP1 = new JSONObject(); videodmP1.put("pages", new JSONObject()); videodmP1.put("total", 0);
        JSONObject livedmP1 = new JSONObject(); livedmP1.put("pages", new JSONObject()); livedmP1.put("total", 0);

        try { replyP1 = fetchPage1(uid, "reply", replyUrlTpl, "replies", "aicu_reply", nowSec);
        } catch (Exception e) { LOGGER.error("AICU reply fetch failed: {}", e.getMessage(), e); }
        delay();

        try { videodmP1 = fetchPage1(uid, "videodm", videodmUrlTpl, "videodmlist", "aicu_videodm", nowSec);
        } catch (Exception e) { LOGGER.error("AICU videodm fetch failed: {}", e.getMessage(), e); }
        delay();

        try { livedmP1 = fetchLiveDmPage1(uid, nowSec);
        } catch (Exception e) { LOGGER.error("AICU livedm fetch failed: {}", e.getMessage(), e); }

        enrichWithCursor(replyP1,   "aicu_reply",   uid);
        enrichWithCursor(videodmP1, "aicu_videodm", uid);
        enrichWithCursor(livedmP1,  "aicu_livedm",  uid);

        result.put("reply",   replyP1);
        result.put("videodm", videodmP1);
        result.put("livedm",  livedmP1);

        if (brief) {
            result.put("moreToFetch", false);
        } else {
            result.put("moreToFetch", true);
            // 后台线程拉取剩余页
            final long finalNowSec = nowSec;
            new Thread(() -> {
                try {
                    LOGGER.info("AICU background: fetching remaining pages for uid={}", uid);
                    fetchRemainingPages(uid, "reply", replyUrlTpl, "replies", "aicu_reply", finalNowSec);
                    fetchRemainingPages(uid, "videodm", videodmUrlTpl, "videodmlist", "aicu_videodm", finalNowSec);
                    fetchLiveDmRemaining(uid, finalNowSec);
                    LOGGER.info("AICU background: all remaining pages done for uid={}", uid);
                } catch (Exception e) {
                    LOGGER.error("AICU background fetch error for uid={}: {}", uid, e.getMessage(), e);
                }
            }, "aicu-bg-" + uid).start();
        }

        return result;
    }

    /**
     * 强制拉取某一页（用户点击分页时优先调用）
     */
    public JSONObject fetchPage(Long uid, String type, int pn) {
        ensureTables();
        long nowSec = System.currentTimeMillis() / 1000;
        if ("reply".equals(type)) {
            return fetchSinglePage(uid, "reply",
                API_BASE + "/api/v3/search/getreply?uid=" + uid + "&pn=%d&ps=" + PAGE_SIZE + "&mode=1",
                pn, "replies", "aicu_reply", nowSec);
        } else if ("videodm".equals(type)) {
            return fetchSinglePage(uid, "videodm",
                API_BASE + "/api/v3/search/getvideodm?uid=" + uid + "&pn=%d&ps=" + PAGE_SIZE,
                pn, "videodmlist", "aicu_videodm", nowSec);
        } else if ("livedm".equals(type)) {
            return fetchLiveDmSinglePage(uid, pn, nowSec);
        }
        return new JSONObject();
    }

    // ======================== 私有方法 ========================

    /**
     * 限流延迟
     */
    private void delay() {
        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * HTTP GET → JSONObject
     */
    private JSONObject fetchJson(String url) throws Exception {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.aicu.cc/")
                .get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();
            String body = response.body() != null ? response.body().string() : null;
            if (!response.isSuccessful() || body == null) {
                String preview = body != null && body.length() > 0 ? body.substring(0, Math.min(300, body.length())) : "(empty)";
                LOGGER.warn("AICU fetchJson HTTP {} bodyLen={} preview=[{}] url={}", code, body != null ? body.length() : 0, preview, url);
                return null;
            }
            LOGGER.info("AICU fetchJson OK HTTP {} bodyLen={}", code, body.length());
            return JSONObject.parseObject(body);
        } catch (Exception e) {
            LOGGER.error("AICU fetchJson failed for {}: {}", url, e.getMessage());
            throw e;
        }
    }

    // ===== 增量爬取：第一页（立即返回） =====

    private JSONObject fetchPage1(Long uid, String type, String urlTemplate,
                                   String listKey, String table, long nowSec) {
        return fetchSinglePage(uid, type, urlTemplate, 1, listKey, table, nowSec);
    }

    private JSONObject fetchSinglePage(Long uid, String type, String urlTemplate, int pn,
                                        String listKey, String table, long nowSec) {
        JSONObject result = new JSONObject();
        result.put("pages", new JSONObject());
        try {
            String url = String.format(urlTemplate, pn);
            JSONObject resp = fetchJson(url);
            if (resp == null || resp.getIntValue("code") != 0) return result;
            JSONObject data = resp.getJSONObject("data");
            if (data == null) return result;

            JSONObject cursor = data.getJSONObject("cursor");
            int total = cursor != null ? cursor.getIntValue("all_count") : 0;
            boolean isEnd = cursor != null && cursor.getBooleanValue("is_end");

            JSONArray list = data.getJSONArray(listKey);
            if (list != null && !list.isEmpty()) {
                result.getJSONObject("pages").put(String.valueOf(pn), list);
                savePage(table, uid, pn, data.toJSONString(), total, nowSec);
                LOGGER.info("AICU {} page {} saved for uid={} (items={}, total={}, end={})",
                        type, pn, uid, list.size(), total, isEnd);
            }
            result.put("total", total);
        } catch (Exception e) {
            LOGGER.error("AICU {} page {} fetch failed for uid={}: {}", type, pn, uid, e.getMessage());
        }
        return result;
    }

    private JSONObject fetchLiveDmPage1(Long uid, long nowSec) {
        return fetchLiveDmSinglePage(uid, 1, nowSec);
    }

    private JSONObject fetchLiveDmSinglePage(Long uid, int pn, long nowSec) {
        JSONObject result = new JSONObject();
        result.put("pages", new JSONObject());
        try {
            String url = API_BASE + "/api/v3/search/getlivedm?uid=" + uid + "&pn=" + pn + "&ps=" + PAGE_SIZE;
            JSONObject resp = fetchJson(url);
            if (resp == null || resp.getIntValue("code") != 0) return result;
            JSONObject data = resp.getJSONObject("data");
            if (data == null) return result;

            JSONObject cursor = data.getJSONObject("cursor");
            int total = cursor != null ? cursor.getIntValue("all_count") : 0;
            boolean isEnd = cursor != null && cursor.getBooleanValue("is_end");

            JSONArray list = data.getJSONArray("list");
            if (list != null && !list.isEmpty()) {
                result.getJSONObject("pages").put(String.valueOf(pn), list);
                savePage("aicu_livedm", uid, pn, data.toJSONString(), total, nowSec);
                LOGGER.info("AICU livedm page {} saved for uid={} (rooms={}, total={}, end={})",
                        pn, uid, list.size(), total, isEnd);
            }
            result.put("total", total);
        } catch (Exception e) {
            LOGGER.error("AICU livedm page {} fetch failed for uid={}: {}", pn, uid, e.getMessage());
        }
        return result;
    }

    // ===== 增量爬取：剩余页（后台线程） =====

    private void fetchRemainingPages(Long uid, String type, String urlTemplate,
                                      String listKey, String table, long nowSec) {
        int pn = 2;
        while (true) {
            delay();
            JSONObject pageResult = fetchSinglePage(uid, type, urlTemplate, pn, listKey, table, nowSec);
            JSONObject pages = pageResult.getJSONObject("pages");
            if (pages == null || pages.isEmpty()) break;
            int total = pageResult.getIntValue("total");
            if (total > 0 && pn * PAGE_SIZE >= total) break;
            pn++;
        }
    }

    private void fetchLiveDmRemaining(Long uid, long nowSec) {
        int pn = 2;
        while (true) {
            delay();
            JSONObject pageResult = fetchLiveDmSinglePage(uid, pn, nowSec);
            JSONObject pages = pageResult.getJSONObject("pages");
            if (pages == null || pages.isEmpty()) break;
            int total = pageResult.getIntValue("total");
            if (total > 0 && pn * PAGE_SIZE >= total) break;
            pn++;
        }
    }

    /**
     * 从 DB 读取 cursor 信息补充 total 到结果中（前端分页需要）
     */
    private void enrichWithCursor(JSONObject pageResult, String table, Long uid) {
        if (pageResult.getIntValue("total") > 0) return; // 已有 total 就不查
        String sql = "SELECT MAX(total_count) AS t FROM " + table + " WHERE uid = ?";
        try (Connection conn = DanmujiDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) pageResult.put("total", rs.getInt("t"));
            }
        } catch (Exception e) {
            LOGGER.error("enrichWithCursor {} error: {}", table, e.getMessage());
        }
    }

    /**
     * 保存单页数据到 SQLite
     */
    private void savePage(String table, Long uid, int pn, String json, int totalCount, long fetchTime) {
        String sql = "INSERT OR REPLACE INTO " + table + " (uid, pn, data_json, total_count, fetch_time) VALUES (?,?,?,?,?)";
        try (Connection conn = DanmujiDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            ps.setInt(2, pn);
            ps.setString(3, json);
            ps.setInt(4, totalCount);
            ps.setLong(5, fetchTime);
            int rows = ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("savePage {} error for uid={} pn={}: {}", table, uid, pn, e.getMessage(), e);
        }
    }

    /**
     * 保存/更新单条记录到 SQLite
     */
    private void upsertSingle(String table, Long uid, String json, long fetchTime) {
        String sql = "INSERT OR REPLACE INTO " + table + " (uid, data_json, fetch_time) VALUES (?,?,?)";
        try (Connection conn = DanmujiDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            ps.setString(2, json);
            ps.setLong(3, fetchTime);
            int rows = ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("upsertSingle {} error for uid={}: {}", table, uid, e.getMessage(), e);
        }
    }

    /**
     * 读取单条记录
     */
    private JSONObject loadSingle(String table, Long uid) {
        String sql = "SELECT data_json, fetch_time FROM " + table + " WHERE uid = ?";
        try (Connection conn = DanmujiDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    JSONObject row = new JSONObject();
                    String json = rs.getString("data_json");
                    row.put("data", json != null ? JSONObject.parseObject(json) : null);
                    row.put("fetch_time", rs.getLong("fetch_time"));
                    return row;
                }
            }
        } catch (Exception e) {
            LOGGER.error("loadSingle {} error for uid={}: {}", table, uid, e.getMessage());
        }
        return null;
    }

    /**
     * 只读第一页（简略模式）
     */
    private JSONObject loadFirstPage(String table, Long uid) {
        JSONObject result = new JSONObject();
        result.put("pages", new JSONObject());
        int totalCount = 0;
        String sql = "SELECT pn, data_json, total_count FROM " + table + " WHERE uid = ? AND pn = 1";
        try (Connection conn = DanmujiDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("data_json");
                    totalCount = rs.getInt("total_count");
                    if (json != null) {
                        result.getJSONObject("pages").put("1", JSONObject.parseObject(json));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("loadFirstPage {} error for uid={}: {}", table, uid, e.getMessage());
        }
        result.put("total", totalCount);
        return result;
    }

    /**
     * 读取某 uid 的所有分页数据
     */
    private JSONObject loadPages(String table, Long uid) {
        JSONObject result = new JSONObject();
        result.put("pages", new JSONObject());
        int totalCount = 0;
        String sql = "SELECT pn, data_json, total_count FROM " + table + " WHERE uid = ? ORDER BY pn";
        try (Connection conn = DanmujiDatabase.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pn = rs.getInt("pn");
                    String json = rs.getString("data_json");
                    totalCount = Math.max(totalCount, rs.getInt("total_count"));
                    if (json != null) {
                        JSONObject pageData = JSONObject.parseObject(json);
                        result.getJSONObject("pages").put(String.valueOf(pn), pageData);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("loadPages {} error for uid={}: {}", table, uid, e.getMessage());
        }
        result.put("total", totalCount);
        return result;
    }

    /**
     * 将分页数据构建为前端友好的结构
     */
    private JSONObject buildPageResult(JSONObject pagesObj, int total) {
        JSONObject result = new JSONObject();
        result.put("total", total);
        result.put("pages", pagesObj.getJSONObject("pages"));
        return result;
    }
}
