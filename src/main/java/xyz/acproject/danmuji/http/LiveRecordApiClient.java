package xyz.acproject.danmuji.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.tools.file.LogFileTools;
import xyz.acproject.danmuji.utils.OkHttp3Utils;
import okhttp3.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * 直播间记录分析 API 客户端。
 *
 * 调用独立运行的 Python Flask 服务器 (默认 localhost:21213) 获取
 * 已导入的直播录制数据的查询结果。
 *
 * 使用方式：
 * <pre>
 *   LiveRecordApiClient client = new LiveRecordApiClient("http://127.0.0.1:21213");
 *   JSONArray summary = client.getUserSummary(87107837);
 *   JSONObject timeStats = client.getUserTimeStats(87107837);
 * </pre>
 */
public class LiveRecordApiClient {

    private static final Logger LOGGER = LogManager.getLogger(LiveRecordApiClient.class);

    /** 默认 API 基础地址 */
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:21213";

    private final String baseUrl;

    public LiveRecordApiClient() {
        this(DEFAULT_BASE_URL);
    }

    public LiveRecordApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // ======================== 用户维度 ========================

    /**
     * 查询用户在所有直播间的 event_type 分类统计。
     * @param uid 用户 UID
     * @return 按直播间分组的统计数组，最后一条为合计行；失败返回 null
     */
    public JSONArray getUserSummary(long uid) {
        String json = get("/api/user/" + uid + "/summary");
        return extractDataArray(json);
    }

    /**
     * 查询用户阵营判定（五级梯次：判定表→行为分→关注图分→DB→API）。
     * @param uid 用户 UID
     * @return 包含 score/camp/source/contradiction 等字段的对象
     */
    public JSONObject getUserCascade(long uid) {
        String json = get("/api/audience/" + uid + "/cascade");
        return extractDataObject(json);
    }

    /**
     * 查询用户在各直播间的观看时长统计。
     * @param uid 用户 UID
     * @return 每场次的时长明细数组
     */
    public JSONArray getUserTimeStats(long uid) {
        return getUserTimeStats(uid, null);
    }

    /**
     * 查询用户在各直播间的观看时长统计（可选限定直播间）。
     * @param uid 用户 UID
     * @param roomId 直播间 ID（可选，null 表示所有直播间）
     * @return 每场次的时长明细数组
     */
    public JSONArray getUserTimeStats(long uid, Long roomId) {
        Map<String, String> params = new HashMap<>();
        if (roomId != null) params.put("room_id", String.valueOf(roomId));
        String json = get("/api/user/" + uid + "/timestats", params);
        return extractDataArray(json);
    }

    /**
     * 分页查询用户事件明细。
     * @param uid 用户 UID
     * @param page 页码 (从1开始)
     * @param size 每页条数
     * @return 包含 items/total/page 的分页对象
     */
    public JSONObject getUserEvents(long uid, int page, int size) {
        Map<String, String> params = new HashMap<>();
        params.put("page", String.valueOf(page));
        params.put("size", String.valueOf(size));
        String json = get("/api/user/" + uid + "/events", params);
        return extractDataObject(json);
    }

    /**
     * 分页查询用户事件明细（带过滤）。
     */
    public JSONObject getUserEvents(long uid, int page, int size, Long roomId, Integer eventType) {
        Map<String, String> params = new HashMap<>();
        params.put("page", String.valueOf(page));
        params.put("size", String.valueOf(size));
        if (roomId != null) params.put("room_id", String.valueOf(roomId));
        if (eventType != null) params.put("type", String.valueOf(eventType));
        String json = get("/api/user/" + uid + "/events", params);
        return extractDataObject(json);
    }

    // ======================== 直播间维度 ========================

    /**
     * 查询直播间的所有录制场次。
     * @param roomId 直播间 ID
     * @return 场次数组
     */
    public JSONArray getRoomSessions(long roomId) {
        String json = get("/api/room/" + roomId + "/sessions");
        return extractDataArray(json);
    }

    /**
     * 查询直播间活跃用户排名。
     * @param roomId 直播间 ID
     * @param top 取前 N 名
     * @return 用户排名数组
     */
    public JSONArray getRoomTopUsers(long roomId, int top) {
        Map<String, String> params = new HashMap<>();
        params.put("top", String.valueOf(top));
        String json = get("/api/room/" + roomId + "/users", params);
        return extractDataArray(json);
    }

    // ======================== 搜索 ========================

    /**
     * 按用户名模糊搜索用户。
     * @param name 用户名（模糊匹配）
     * @param limit 最多返回条数
     * @return 用户搜索结果数组
     */
    public JSONArray searchUser(String name, int limit) {
        Map<String, String> params = new HashMap<>();
        params.put("name", name);
        params.put("limit", String.valueOf(limit));
        String json = get("/api/search/user", params);
        return extractDataArray(json);
    }

    // ======================== 系统 ========================

    /**
     * 获取导入状态汇总。
     * @return 包含 total_sessions/total_events/total_users 等字段的对象
     */
    public JSONObject getImportStatus() {
        String json = get("/api/import/status");
        return extractDataObject(json);
    }

    /**
     * 手动触发重新扫描 data/ 目录并导入新文件。
     * @return 导入结果汇总
     */
    public JSONObject refreshImport() {
        String json = post("/api/import/refresh");
        return extractDataObject(json);
    }

    // ======================== 内部 HTTP 方法 ========================

    private String get(String path) {
        return get(path, null);
    }

    private String get(String path, Map<String, String> params) {
        try {
            Response resp = OkHttp3Utils.getHttp3Utils().httpGet(
                    baseUrl + path,
                    null,       // headers
                    params      // query params
            );
            if (resp != null && resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : null;
                resp.close();
                return body;
            }
            if (resp != null) {
                LOGGER.warn("LiveRecordApi GET {} returned {}", path, resp.code());
                resp.close();
            }
        } catch (Exception e) {
            LOGGER.error("LiveRecordApi GET {} failed: {}", path, e.getMessage(), e);
        }
        return null;
    }

    private String post(String path) {
        try {
            Response resp = OkHttp3Utils.getHttp3Utils().httpPostJson(
                    baseUrl + path,
                    null,   // headers
                    "{}"    // empty JSON body
            );
            if (resp != null && resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : null;
                resp.close();
                return body;
            }
            if (resp != null) {
                LOGGER.warn("LiveRecordApi POST {} returned {}", path, resp.code());
                resp.close();
            }
        } catch (Exception e) {
            LOGGER.error("LiveRecordApi POST {} failed: {}", path, e.getMessage(), e);
        }
        return null;
    }

    // ======================== 响应解析 ========================

    /**
     * 从统一 API 响应 {"code":"0", "data": [...]} 中提取 data 数组。
     */
    private JSONArray extractDataArray(String json) {
        if (json == null || json.isEmpty()) return new JSONArray();
        try {
            JSONObject resp = JSON.parseObject(json);
            if ("0".equals(resp.getString("code"))) {
                return resp.getJSONArray("data");
            }
            LOGGER.warn("LiveRecordApi response code={} msg={}", resp.getString("code"), resp.getString("msg"));
        } catch (Exception e) {
            LOGGER.error("LiveRecordApi parse error: {}", e.getMessage(), e);
        }
        return new JSONArray();
    }

    /**
     * 从统一 API 响应 {"code":"0", "data": {...}} 中提取 data 对象。
     */
    private JSONObject extractDataObject(String json) {
        if (json == null || json.isEmpty()) return new JSONObject();
        try {
            JSONObject resp = JSON.parseObject(json);
            if ("0".equals(resp.getString("code"))) {
                return resp.getJSONObject("data");
            }
            LOGGER.warn("LiveRecordApi response code={} msg={}", resp.getString("code"), resp.getString("msg"));
        } catch (Exception e) {
            LOGGER.error("LiveRecordApi parse error: {}", e.getMessage(), e);
        }
        return new JSONObject();
    }
}
