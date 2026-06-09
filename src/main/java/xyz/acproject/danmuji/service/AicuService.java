package xyz.acproject.danmuji.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import xyz.acproject.danmuji.tools.db.DanmujiDatabase;
import xyz.acproject.danmuji.utils.OkHttp3Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AICU 用户数据查询服务
 * 通过 aicu.cc API 获取 B站用户的评论、视频弹幕、直播弹幕数据，并持久化到本地 SQLite
 */
@Service
public class AicuService {
    private static final Logger LOGGER = LogManager.getLogger(AicuService.class);

    private static final String API_BASE = "https://api.aicu.cc";
    private static final long DELAY_MS = 1500L;       // API 请求间隔（限流）
    private static final long CACHE_TTL_SEC = 7200L;  // 缓存有效期 2 小时
    private static final int PAGE_SIZE = 100;

    // ======================== 公开方法 ========================

    /**
     * 按用户名模糊搜索弹幕表，返回匹配的 UID 列表（去重，最多 10 个）
     */
    public List<Map<String, Object>> findUidByUname(String uname) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (StringUtils.isBlank(uname)) return result;
        String escaped = uname.trim().replace("'", "''");
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
            LOGGER.error("findUidByUname error for '{}': {}", uname, e.getMessage());
        }
        return result;
    }

    /**
     * 从本地 SQLite 读取缓存数据
     */
    public JSONObject getCached(Long uid) {
        JSONObject result = new JSONObject();
        result.put("uid", uid);
        result.put("fromCache", true);

        // usermark
        JSONObject usermark = loadSingle("aicu_usermark", uid);
        result.put("usermark", usermark != null ? usermark.getJSONObject("data") : null);

        // reply
        JSONObject replyPages = loadPages("aicu_reply", uid);
        int replyTotal = replyPages.getIntValue("total");
        result.put("reply", buildPageResult(replyPages, replyTotal));

        // videodm
        JSONObject videodmPages = loadPages("aicu_videodm", uid);
        int videodmTotal = videodmPages.getIntValue("total");
        result.put("videodm", buildPageResult(videodmPages, videodmTotal));

        // livedm
        JSONObject livedmPages = loadPages("aicu_livedm", uid);
        int livedmTotal = livedmPages.getIntValue("total");
        result.put("livedm", buildPageResult(livedmPages, livedmTotal));

        result.put("cached", replyTotal > 0 || videodmTotal > 0 || livedmTotal > 0);
        return result;
    }

    /**
     * 全量搜索：依次拉取 usermark + 评论 + 视频弹幕 + 直播弹幕，增量入库，返回全量结果
     */
    public JSONObject search(Long uid) {
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
                LOGGER.info("AICU usermark saved for uid={}", uid);
            }
            delay();
        } catch (Exception e) {
            LOGGER.error("AICU usermark fetch failed for uid={}: {}", uid, e.getMessage());
        }
        result.put("usermark", usermarkData);

        // 2. 评论（分页爬取）
        JSONObject replyResult = fetchAllPages(uid, "reply",
                API_BASE + "/api/v3/search/getreply?uid=" + uid + "&pn=%d&ps=" + PAGE_SIZE + "&mode=1",
                "replies", "aicu_reply", nowSec);
        result.put("reply", replyResult);

        // 3. 视频弹幕（分页爬取）
        JSONObject videodmResult = fetchAllPages(uid, "videodm",
                API_BASE + "/api/v3/search/getvideodm?uid=" + uid + "&pn=%d&ps=" + PAGE_SIZE,
                "videodmlist", "aicu_videodm", nowSec);
        result.put("videodm", videodmResult);

        // 4. 直播弹幕（分页爬取）
        // livedm 的结构特殊：data.list[] 是房间+弹幕数组，需要特殊处理
        JSONObject livedmResult = fetchLiveDmPages(uid, nowSec);
        result.put("livedm", livedmResult);

        return result;
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
        Response response = OkHttp3Utils.getHttp3Utils().httpGet(url, null, null);
        if (response != null && response.isSuccessful() && response.body() != null) {
            String body = response.body().string();
            response.close();
            return JSONObject.parseObject(body);
        }
        if (response != null) response.close();
        return null;
    }

    /**
     * 通用分页爬取（reply 和 videodm 共用）
     */
    private JSONObject fetchAllPages(Long uid, String type, String urlTemplate,
                                      String listKey, String table, long nowSec) {
        JSONObject result = new JSONObject();
        result.put("pages", new JSONObject());
        int totalCount = 0;
        int pn = 1;
        boolean done = false;

        while (!done) {
            try {
                String url = String.format(urlTemplate, pn);
                JSONObject resp = fetchJson(url);
                if (resp == null || resp.getIntValue("code") != 0) {
                    LOGGER.warn("AICU {} page {} returned null/error for uid={}", type, pn, uid);
                    break;
                }
                JSONObject data = resp.getJSONObject("data");
                if (data == null) break;

                JSONObject cursor = data.getJSONObject("cursor");
                if (cursor != null) {
                    totalCount = cursor.getIntValue("all_count");
                    if (cursor.getBooleanValue("is_end")) done = true;
                }

                JSONArray list = data.getJSONArray(listKey);
                if (list != null && !list.isEmpty()) {
                    result.getJSONObject("pages").put(String.valueOf(pn), list);
                    String json = data.toJSONString();
                    savePage(table, uid, pn, json, totalCount, nowSec);
                    LOGGER.info("AICU {} page {}/{} saved for uid={} (items={})",
                            type, pn, (int) Math.ceil(totalCount / (double) PAGE_SIZE), uid, list.size());
                } else {
                    done = true;
                }

                if (!done) {
                    pn++;
                    delay();
                }
            } catch (Exception e) {
                LOGGER.error("AICU {} page {} fetch failed for uid={}: {}", type, pn, uid, e.getMessage());
                break;
            }
        }
        result.put("total", totalCount);
        return result;
    }

    /**
     * 直播弹幕分页爬取（结构特殊：data.list 是房间+弹幕数组）
     */
    private JSONObject fetchLiveDmPages(Long uid, long nowSec) {
        JSONObject result = new JSONObject();
        result.put("pages", new JSONObject());
        int totalCount = 0;
        int pn = 1;
        boolean done = false;

        while (!done) {
            try {
                String url = API_BASE + "/api/v3/search/getlivedm?uid=" + uid + "&pn=" + pn + "&ps=" + PAGE_SIZE;
                JSONObject resp = fetchJson(url);
                if (resp == null || resp.getIntValue("code") != 0) {
                    LOGGER.warn("AICU livedm page {} returned null/error for uid={}", pn, uid);
                    break;
                }
                JSONObject data = resp.getJSONObject("data");
                if (data == null) break;

                JSONObject cursor = data.getJSONObject("cursor");
                if (cursor != null) {
                    totalCount = cursor.getIntValue("all_count");
                    if (cursor.getBooleanValue("is_end")) done = true;
                }

                JSONArray list = data.getJSONArray("list");
                if (list != null && !list.isEmpty()) {
                    result.getJSONObject("pages").put(String.valueOf(pn), list);
                    String json = data.toJSONString();
                    savePage("aicu_livedm", uid, pn, json, totalCount, nowSec);
                    LOGGER.info("AICU livedm page {}/{} saved for uid={} (rooms={})",
                            pn, (int) Math.ceil(totalCount / (double) PAGE_SIZE), uid, list.size());
                } else {
                    done = true;
                }

                if (!done) {
                    pn++;
                    delay();
                }
            } catch (Exception e) {
                LOGGER.error("AICU livedm page {} fetch failed for uid={}: {}", pn, uid, e.getMessage());
                break;
            }
        }
        result.put("total", totalCount);
        return result;
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
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("savePage {} error for uid={} pn={}: {}", table, uid, pn, e.getMessage());
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
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("upsertSingle {} error for uid={}: {}", table, uid, e.getMessage());
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
