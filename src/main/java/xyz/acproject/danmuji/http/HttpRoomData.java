package xyz.acproject.danmuji.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.CollectionUtils;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.room_data.*;
import xyz.acproject.danmuji.entity.server_data.Conf;
import xyz.acproject.danmuji.entity.user_data.MedalWallItem;
import xyz.acproject.danmuji.entity.user_data.UserNav;
import xyz.acproject.danmuji.tools.CurrencyTools;
import xyz.acproject.danmuji.tools.file.FileTools;
import xyz.acproject.danmuji.tools.FollowingCountTools;
import xyz.acproject.danmuji.tools.MatchCountTools;
import xyz.acproject.danmuji.tools.file.LogFileTools;
import xyz.acproject.danmuji.utils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * @author BanqiJane
 * @ClassName HttpRoomData
 * @Description TODO
 * @date 2020年8月10日 下午12:28:59
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class HttpRoomData {
    private static Logger LOGGER = LogManager.getLogger(HttpRoomData.class);
    private static volatile Map<Long, Integer> pnScoreMap = loadNegativeBlackPositiveWhiteScores();
    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    /**
     * 获取连接目标房间websocket端口 接口
     *
     * @return
     */
    public static Conf httpGetConf() {
        String data = null;
        JSONObject jsonObject = null;
        Conf conf = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> datas = null;
        headers = new HashMap<>(3);
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        datas = new HashMap<>(3);
        datas.put("id", PublicDataConf.ROOMID.toString());
        datas.put("type", "0");
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo", headers, datas)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            conf = jsonObject.getObject("data", Conf.class);
        } else {
            LOGGER.error("未知错误,原因:" + jsonObject.getString("message"));
        }

        pnScoreMap = loadNegativeBlackPositiveWhiteScores();

        return conf;
    }

    public static Conf httpGetConf(UserNav userNav) {
        String data = null;
        JSONObject jsonObject = null;
        Conf conf = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> datas = null;
        headers = new HashMap<>(3);
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        Long nowTimeStamp = JodaTimeUtils.getTimestamp();
        datas = new HashMap<>(5);
        datas.put("id", PublicDataConf.ROOMID.toString());
        datas.put("type", "0");
        datas.put("wts", nowTimeStamp.toString());
        datas.put("web_location", "444.8");
        String wbiSign = WbiSignUtils.getWbiSign(datas, userNav.getWbiImg().getImgUrl().substring(userNav.getWbiImg().getImgUrl().lastIndexOf('/') + 1, userNav.getWbiImg().getImgUrl().lastIndexOf('.')), userNav.getWbiImg().getSubUrl().substring(userNav.getWbiImg().getSubUrl().lastIndexOf('/') + 1, userNav.getWbiImg().getSubUrl().lastIndexOf('.')));
        try {
            datas.put("w_rid", wbiSign);
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo", headers, datas)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            conf = jsonObject.getObject("data", Conf.class);
        } else {
            LOGGER.error("未知错误,原因:" + jsonObject.getString("message"));
        }
        return conf;
    }

    /**
     * 获取目标房间部分信息
     *
     * @param roomid
     * @return
     */
    public static Room httpGetRoomData(long roomid) {
        String data = null;
        JSONObject jsonObject = null;
        Room room = null;
        short code = -1;
        Map<String, String> headers = null;
        headers = new HashMap<>(3);
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
//		if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
//			headers.put("cookie", PublicDataConf.USERCOOKIE);
//		}
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/room_ex/v1/RoomNews/get?roomid=" + roomid, headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return room;
//		LOGGER.info("获取到的room:" + data);
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            room = jsonObject.getObject("data", Room.class);
        } else {
            LOGGER.error("直播房间号不存在，或者未知错误，请尝试更换房间号,原因:" + jsonObject.getString("message"));
        }
        return room;
    }

    /**
     * 获取房间信息
     *
     * @param roomid
     */
    public static RoomInit httpGetRoomInit(long roomid) {
        String data = null;
        RoomInit roomInit = null;
        JSONObject jsonObject = null;
        short code = -1;
        Map<String, String> headers = null;
        headers = new HashMap<>(2);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
//		if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
//			headers.put("cookie", PublicDataConf.USERCOOKIE);
//		}
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/room/v1/Room/room_init?id=" + roomid, headers, null).body()
                    .string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return roomInit;
//		LOGGER.info("获取到的room:" + data);
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            roomInit = jsonObject.getObject("data", RoomInit.class);
        } else {
            LOGGER.error("直播房间号不存在，或者未知错误，请尝试更换房间号,原因:" + jsonObject.getString("message"));
        }
        ;
        return roomInit;
    }

    /**
     * 获取房间最详细信息 日后扩容 目前只是获取主播uid 改
     *
     * @return
     */
    public static RoomInfoAnchor httpGetRoomInfo() {
        String data = null;
        JSONObject jsonObject = null;
        RoomInfoAnchor roomInfoAnchor = new RoomInfoAnchor();
        MedalInfoAnchor medalInfoAnchor = null;
        RoomInfo roomInfo = null;
        short code = -1;
        Map<String, String> headers = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id="
                            + CurrencyTools.parseRoomId(), headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return roomInfoAnchor;
//		LOGGER.info("获取到的room:" + data);
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            roomInfo = JSON.parseObject(((JSONObject) jsonObject.get("data")).getString("room_info"),
                    RoomInfo.class);
            medalInfoAnchor = JSON.parseObject(jsonObject.getJSONObject("data").getJSONObject("anchor_info").getString("medal_info"),
                    MedalInfoAnchor.class);
        } else {
            LOGGER.error("获取房间详细信息失败，请稍后尝试:" + jsonObject.getString("message"));
        }
        roomInfoAnchor.setRoomInfo(roomInfo);
        roomInfoAnchor.setMedalInfoAnchor(medalInfoAnchor);
        return roomInfoAnchor;
    }

    /**
     * 获取关注数
     *
     * @return 返回关注数
     */
    public static Long httpGetFollowersNum() {
        String data = null;
        JSONObject jsonObject = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> datas = null;
        Long followersNum = 0L;
        if (PublicDataConf.AUID == null) {
            return followersNum;
        }
        headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/{" + PublicDataConf.AUID + "}/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
//			if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
//				headers.put("cookie", PublicDataConf.USERCOOKIE);
//			}
        datas = new HashMap<>(2);
        datas.put("vmid", PublicDataConf.AUID.toString());
        try {
            data = OkHttp3Utils.getHttp3Utils().httpGet("https://api.bilibili.com/x/relation/stat", headers, datas)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return followersNum;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            followersNum = ((JSONObject) jsonObject.get("data")).getLong("follower");
        } else {
            LOGGER.error("获取关注数失败，请重试" + jsonObject.getString("message"));
        }
        return followersNum;
    }


    /**
     * 获取用户关注列表(followings)
     */
    public static JSONObject httpGetFollowings(long vmid, int page, int pageSize) {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        Map<String, String> datas = null;
        headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + vmid + "/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        datas = new HashMap<>(3);
        datas.put("vmid", String.valueOf(vmid));
        datas.put("pn", String.valueOf(page));
        datas.put("ps", String.valueOf(pageSize));
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/relation/followings", headers, datas).body().string();
        } catch (Exception e) {
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;
        jsonObject = JSONObject.parseObject(data);
        return jsonObject;
    }

    /**
     * 获取用户粉丝列表(followers)
     */
    public static JSONObject httpGetFollowers(long vmid, int page, int pageSize) {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        Map<String, String> datas = null;
        headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + vmid + "/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
//		if (PublicDataConf.COOKIE != null && StringUtils.isNotBlank(PublicDataConf.COOKIE.getBili_jct())) {
//			headers.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
//			headers.put("csrf", PublicDataConf.COOKIE.getBili_jct());
//		}

        datas = new HashMap<>(3);
        datas.put("vmid", String.valueOf(vmid));
        datas.put("pn", String.valueOf(page));
        datas.put("ps", String.valueOf(pageSize));
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/relation/followers", headers, datas).body().string();
        } catch (Exception e) {
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;
        jsonObject = JSONObject.parseObject(data);
        return jsonObject;
    }

    /**
     * 获取用户个人信息卡片
     */
    public static JSONObject httpGetUserCard(long mid) {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + mid + "/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(1);
        params.put("mid", String.valueOf(mid));
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/web-interface/card", headers, params).body().string();
        } catch (Exception e) {
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;

        LogFileTools.getlogFileTools().logFollowingsFile("httpgetusercar: " + data);
        jsonObject = JSONObject.parseObject(data);
        return jsonObject;
    }


    // 获取用户动态，不必解析
    public static String httpGetUserDynamic(long mid) {
        Map<String, String> headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + mid);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        String dynData = null;

        try {
            dynData = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/space_history?host_uid=" + mid + "&offset_dynamic_id=0&need_top=1", headers, null)
                    .body().string();

        } catch (Exception e) {
            LOGGER.warn("获取最新动态失败", mid, e.getMessage());
        }

        return dynData;
    }

    // 用户 用户动态冷却调用 (旧版 — 作为所有Cookie耗尽时的兜底)
    static final ScheduledExecutorService schedulerDynamicService = new ScheduledThreadPoolExecutor(1);
    static final AtomicBoolean schedulerDynamicColdWait = new AtomicBoolean(false);


    // 用户 卡片信息冷却调用 (旧版 — 作为所有Cookie耗尽时的兜底)
    static final ScheduledExecutorService schedulercardJOService = new ScheduledThreadPoolExecutor(1);
    static final AtomicBoolean schedulercardJOColdWait = new AtomicBoolean(false);

    // ==================== 新版：令牌桶 + Cookie池 + 缓存 ====================

    /**
     * 动态API令牌桶限流器（默认0.5 QPS，从AccountPoolConf读取）
     */
    static volatile TokenBucketRateLimiter dynamicRateLimiter =
            new TokenBucketRateLimiter(0.3, 1.0);

    /**
     * 卡片API令牌桶限流器（默认1.5 QPS ≈ 90次/分钟，B站限制约100次/分钟）
     */
    static volatile TokenBucketRateLimiter cardRateLimiter =
            new TokenBucketRateLimiter(1.5, 3.0);

    /**
     * Cookie池管理器
     */
    static final CookiePoolManager cookiePool = CookiePoolManager.getInstance();

    /**
     * API缓存管理器
     */
    static final ApiCacheManager apiCache = ApiCacheManager.getInstance();

    // ---- 异步 HTTP 辅助方法 ----

    private static CompletableFuture<String> asyncHttpGetBody(String url, Map<String, String> headers, Map<String, String> datas) {
        CompletableFuture<String> future = new CompletableFuture<>();
        OkHttp3Utils.getHttp3Utils().httpGetAsync(url, headers, datas, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                LOGGER.error(e);
                future.complete(null);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    future.complete(response.body() != null ? response.body().string() : null);
                } catch (IOException e) {
                    LOGGER.error(e);
                    future.complete(null);
                } finally {
                    response.close();
                }
            }
        });
        return future;
    }

    /**
     * 异步获取关注列表 — 集成Cookie池轮换
     */
    private static CompletableFuture<JSONObject> asyncHttpGetFollowings(long vmid, int page, int pageSize) {
        String poolCookie = cookiePool.getNextCookie();
        if (poolCookie == null && StringUtils.isBlank(PublicDataConf.USERCOOKIE)) {
            poolCookie = PublicDataConf.USERCOOKIE;
        }
        Map<String, String> headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + vmid + "/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(poolCookie)) {
            headers.put("cookie", poolCookie);
        }
        Map<String, String> datas = new HashMap<>(3);
        datas.put("vmid", String.valueOf(vmid));
        datas.put("pn", String.valueOf(page));
        datas.put("ps", String.valueOf(pageSize));
        final String usedCookie = poolCookie;
        return asyncHttpGetBody("https://api.bilibili.com/x/relation/followings", headers, datas)
                .thenApply(body -> {
                    if (body != null) {
                        JSONObject json = JSONObject.parseObject(body);
                        if (json != null && json.getShort("code") != 0) {
                            cookiePool.markRateLimited(usedCookie);
                        }
                        return json;
                    }
                    return null;
                });
    }

    /**
     * 异步获取用户动态 — 集成缓存 + 令牌桶限流 + Cookie池轮换。
     * 缓存命中直接返回；否则获取令牌后使用Cookie池中的可用Cookie发起请求。
     */
    private static CompletableFuture<String> asyncHttpGetUserDynamic(long mid) {
        // 1. 检查缓存
        String cacheKey = ApiCacheManager.dynamicKey(mid);
        String cached = apiCache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // 2. 全局熔断检查（所有Cookie耗尽，跳过请求）
        if (schedulerDynamicColdWait.get()) {
            return CompletableFuture.completedFuture(null);
        }

        // 3. 获取令牌（阻塞等待，最长30秒超时）
        if (!dynamicRateLimiter.acquire(30, TimeUnit.SECONDS)) {
            LOGGER.warn("动态API令牌获取超时 mid={}，降级跳过", mid);
            return CompletableFuture.completedFuture(null);
        }

        // 3. 从Cookie池获取可用Cookie
        String poolCookie = cookiePool.getNextCookie();
        // 如果池返回null且主账号也没有cookie，使用空字符串（匿名请求可能受限）
        if (poolCookie == null && StringUtils.isBlank(PublicDataConf.USERCOOKIE)) {
            poolCookie = PublicDataConf.USERCOOKIE;
        }

        Map<String, String> headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + mid);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(poolCookie)) {
            headers.put("cookie", poolCookie);
        }

        final String usedCookie = poolCookie;

        return asyncHttpGetBody("https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/space_history?host_uid=" + mid + "&offset_dynamic_id=0&need_top=1", headers, null)
                .thenApply(result -> {
                    if (result != null) {
                        // 通过 code 字段判断请求结果（JSON解析，替代字符串匹配）
                        try {
                            JSONObject respJson = JSONObject.parseObject(result);
                            Integer code = respJson != null ? respJson.getInteger("code") : null;
                            if (code != null && code == 0) {
                                // 正常响应，缓存结果
                                apiCache.put(cacheKey, result);
                            } else {
                                // 异常响应，标记该Cookie被限流
                                cookiePool.markRateLimited(usedCookie);
                                LOGGER.warn("动态API异常响应 mid={}，code={}，已标记Cookie冷却", mid, code);
                            }
                        } catch (Exception e) {
                            // JSON 解析失败，视为异常响应
                            cookiePool.markRateLimited(usedCookie);
                            LOGGER.warn("动态API JSON解析失败 mid={}，已标记Cookie冷却", mid);
                        }
                    }
                    return result;
                });
    }

    /**
     * 异步获取粉丝勋章墙 — 集成Cookie池轮换
     */
    private static CompletableFuture<JSONObject> asyncHttpGetMedalWall(long targetId) {
        String poolCookie = cookiePool.getNextCookie();
        if (poolCookie == null && StringUtils.isBlank(PublicDataConf.USERCOOKIE)) {
            poolCookie = PublicDataConf.USERCOOKIE;
        }
        Map<String, String> headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + targetId + "/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(poolCookie)) {
            headers.put("cookie", poolCookie);
        }
        Map<String, String> datas = new HashMap<>(1);
        datas.put("target_id", String.valueOf(targetId));
        final String usedCookie = poolCookie;
        return asyncHttpGetBody("https://api.live.bilibili.com/xlive/web-ucenter/user/MedalWall", headers, datas)
                .thenApply(body -> {
                    if (body != null) {
                        JSONObject json = JSONObject.parseObject(body);
                        if (json != null && json.getIntValue("code") != 0) {
                            cookiePool.markRateLimited(usedCookie);
                        }
                        return json;
                    }
                    return null;
                });
    }

    /**
     * 异步获取用户卡片信息 — 集成缓存 + 令牌桶限流 + Cookie池轮换。
     * 缓存命中直接返回；否则获取令牌后使用Cookie池中的可用Cookie发起请求。
     */
    private static CompletableFuture<JSONObject> asyncHttpGetUserCard(long mid) {
        // 1. 检查缓存
        String cacheKey = ApiCacheManager.cardKey(mid);
        String cached = apiCache.get(cacheKey);
        if (cached != null) {
            try {
                return CompletableFuture.completedFuture(JSONObject.parseObject(cached));
            } catch (Exception e) {
                LOGGER.warn("卡片缓存JSON解析失败 mid={}", mid, e);
                apiCache.clearAll(); // 异常则清除所有缓存避免持续出错
            }
        }

        // 2. 全局熔断检查（所有Cookie耗尽，跳过请求）
        if (schedulercardJOColdWait.get()) {
            return CompletableFuture.completedFuture(null);
        }

        // 3. 获取令牌（阻塞等待，最长30秒超时）
        if (!cardRateLimiter.acquire(30, TimeUnit.SECONDS)) {
            LOGGER.warn("卡片API令牌获取超时 mid={}，降级返回null", mid);
            return CompletableFuture.completedFuture(null);
        }

        // 3. 从Cookie池获取可用Cookie
        String poolCookie = cookiePool.getNextCookie();
        if (poolCookie == null && StringUtils.isBlank(PublicDataConf.USERCOOKIE)) {
            poolCookie = PublicDataConf.USERCOOKIE;
        }

        Map<String, String> headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + mid + "/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(poolCookie)) {
            headers.put("cookie", poolCookie);
        }

        final String usedCookie = poolCookie;

        Map<String, String> params = new HashMap<>(1);
        params.put("mid", String.valueOf(mid));
        return asyncHttpGetBody("https://api.bilibili.com/x/web-interface/card", headers, params)
                .thenApply(body -> {
                    if (body != null) {
                        JSONObject json = JSONObject.parseObject(body);
                        if (json != null && json.getShort("code") == 0) {
                            // 正常响应，缓存结果
                            apiCache.put(cacheKey, body);
                        } else {
                            // 异常响应，标记该Cookie被限流
                            cookiePool.markRateLimited(usedCookie);
                            LOGGER.warn("卡片API异常响应 mid={} code={}，已标记Cookie冷却",
                                    mid, json != null ? json.getShort("code") : -1);
                        }
                        return json;
                    }
                    return null;
                });
    }

    // ==================== 评分流水线：勋章墙 + 关注列表 + 卡片 → 综合打分 ====================

    /**
     * 卡片处理结果
     */
    private static class CardProcessResult {
        int score;
        String type = "";
        String name;
        String face;
        String sign = "";
    }

    /**
     * 主编排器：三路并发 → 顺序聚合 → 动态API收尾。
     * 勋章墙、关注列表、卡片信息三个HTTP请求并行发出，全部返回后顺序处理，
     * 仅当综合分为0时才触发限制最严的动态API。
     */
    public static CompletableFuture<Pair<Integer, String>> processFollowings(long vmid, String uname) {
        StringBuilder logSb = new StringBuilder(200);
        logSb.append(TIME_FORMAT.get().format(System.currentTimeMillis()))
                .append(" https://space.bilibili.com/").append(vmid)
                .append(" ").append(uname).append(" ");
        SelfTools.appendAt(logSb, 90, "⏩");

        // Phase 1: 四路并发（关注列表双页同步请求）
        CompletableFuture<JSONObject> medalF = asyncHttpGetMedalWall(vmid);
        CompletableFuture<JSONObject> cardF = asyncHttpGetUserCard(vmid);
        CompletableFuture<JSONObject> follF1 = asyncHttpGetFollowings(vmid, 1, 50);
        CompletableFuture<JSONObject> follF2 = asyncHttpGetFollowings(vmid, 2, 50);

        return CompletableFuture.allOf(medalF, follF1, follF2, cardF).thenCompose(v -> {
            JSONObject medalJson = medalF.join();
            JSONObject follJson1 = follF1.join();
            JSONObject follJson2 = follF2.join();
            JSONObject cardJson = cardF.join();

            // Phase 2a: 勋章墙
            Pair<Integer, String> medalResult = processMedalWallSync(medalJson, logSb);

            // Phase 2b: 卡片
            CardProcessResult cardResult = processCardDataSync(cardJson, logSb);

            // Phase 2c: 关注列表（双页合并）
            Pair<Integer, String> follResult;
            short follCode = follJson1 != null ? follJson1.getShort("code") : -1;
            JSONObject follData = follJson1 != null && follCode == 0 ? follJson1.getJSONObject("data") : null;
            long total = follData != null ? follData.getLongValue("total") : 0;
            if (follJson1 == null || follCode != 0 || follData == null || total == 0) {
                follResult = processHiddenFollowingsSync(vmid, logSb);
            } else {
                // 合并两页的 list
                JSONArray mergedList = follData.getJSONArray("list");
                if (mergedList == null) mergedList = new JSONArray();
                if (follJson2 != null && follJson2.getShort("code") == 0) {
                    JSONObject follData2 = follJson2.getJSONObject("data");
                    if (follData2 != null) {
                        JSONArray list2 = follData2.getJSONArray("list");
                        if (list2 != null && !list2.isEmpty()) {
                            mergedList.addAll(list2);
                        }
                    }
                }
                JSONObject mergedData = new JSONObject();
                mergedData.put("list", mergedList);
                mergedData.put("total", total);
                follResult = processVisibleFollowingsSync(vmid, logSb, mergedData);
            }

            // Phase 2d: 合并
            StringBuilder combinedType = new StringBuilder(60);
            appendType(combinedType, medalResult.getRight());
            appendType(combinedType, cardResult.type);
            appendType(combinedType, follResult.getRight());

            //黑白名单处理，pnScoreMap 直接命中
            int totalScore = 0;
            Integer pnScore = pnScoreMap.get(vmid);
            if (pnScore != null) {
                totalScore =  pnScore;
                combinedType.append("[已在名单:").append(pnScore).append("]");
            } else {
                totalScore = medalResult.getLeft() + follResult.getLeft() + cardResult.score;
            }

            // Phase 2e: 陌生观众看板（仅卡片数据有效时，face才有值）
            if (cardResult.name != null) {
                xyz.acproject.danmuji.service.StrangerViewerService.addRecord(
                        vmid, cardResult.name, cardResult.face, totalScore, combinedType + cardResult.sign);
            }

            // Phase 3: 仅当综合分==0时才触发动态API
            if (totalScore == 0 && !schedulerDynamicColdWait.get()) {
                // logSb.append("[动态|请求中]");
                int finalTotalScore = totalScore;
                return asyncHttpGetUserDynamic(vmid).thenApply(dynData -> {
                    Pair<Integer, String> dynResult = computeDynamicScore(dynData, logSb);
                    int finalScore = finalTotalScore + dynResult.getLeft();
                    appendType(combinedType, dynResult.getRight());
                    return finalize(logSb, finalScore, combinedType.toString());
                });
            }

            if (totalScore == 0) {
                logSb.append("[动态=熔断跳过]");
            }

            return CompletableFuture.completedFuture(finalize(logSb, totalScore, combinedType.toString()));
        });
    }

    /**
     * 收尾：写日志 → 返回 Pair
     */
    private static Pair<Integer, String> finalize(StringBuilder logSb, int score, String type) {
        logSb.append("    🍉🍉[最终得分:").append(score).append("] 类型:").append(type);
        LogFileTools.getlogFileTools().logFollowingsFile(logSb.toString());
        return Pair.of(score, type);
    }

    /**
     * type 非空时才追加
     */
    private static void appendType(StringBuilder sb, String type) {
        if (type != null && !type.isEmpty()) {
            sb.append(type);
        }
    }

    // ---- 同步处理方法 ----

    /**
     * 勋章墙同步处理。匹配 pnScoreMap 计算勋章黑白分。
     */
    private static Pair<Integer, String> processMedalWallSync(JSONObject medalData, StringBuilder logSb) {
        if (medalData == null || medalData.getIntValue("code") != 0) {
            logSb.append("[勋章:API异常:0]");
            return Pair.of(0, "");
        }
        JSONObject data = medalData.getJSONObject("data");
        if (data == null || data.getIntValue("close_space_medal") == 1) {
            logSb.append("[勋章:隐藏-1]");
            return Pair.of(-1, "[灯牌隐藏-1]");
        }
        int count = data.getIntValue("count");
        JSONArray list = data.getJSONArray("list");
        if (count <= 0 || list == null || list.isEmpty()) {
            logSb.append("[无勋章:0]");
            return Pair.of(0, "");
        }

        int totalMedalScore = 0;
        int totalLifeMedalScore = 0;

        logSb.append("[勋章数:").append(count);
        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            JSONObject medalInfo = item.getJSONObject("medal_info");
            if (medalInfo == null) continue;
            MedalWallItem medal = new MedalWallItem();
            medal.setTargetId(medalInfo.getLong("target_id"));
            medal.setMedalId(medalInfo.getLong("medal_id"));
            medal.setMedalName(medalInfo.getString("medal_name"));
            medal.setLevel(medalInfo.getInteger("level"));
            medal.setGuardLevel(medalInfo.getInteger("guard_level"));
            medal.setWearingStatus(medalInfo.getInteger("wearing_status"));
            medal.setTargetName(item.getString("target_name"));
            medal.setLiveStatus(item.getInteger("live_status"));
            medal.setOfficial(item.getInteger("official"));

            int currentMedalScore = 0;
            int level = medal.getLevel() != null ? medal.getLevel() : 0;
            int currentMedalLevelScore = (level > 0) ? (int) (Math.log(level) / Math.log(2)) : 0;
            currentMedalScore += currentMedalLevelScore;

            Integer blackWhiteZhuboScore = pnScoreMap.get(medal.getTargetId());
            if (blackWhiteZhuboScore != null) {
                logSb.append(" ").append(medal.getTargetName()).append("+").append(blackWhiteZhuboScore)
                        .append(" ").append(medal.getMedalName()).append(".").append(level)
                        .append("->").append(currentMedalLevelScore);

                int guardLevel = medal.getGuardLevel() != null ? medal.getGuardLevel() : 0;
                if (guardLevel != 0) {
                    currentMedalScore += guardLevel;
                    logSb.append(" 舰长+").append(guardLevel);
                }
                if (medal.getOfficial() != null && medal.getOfficial() != 0) {
                    currentMedalScore += 1;
                    logSb.append(" 认证+1 ");
                }
                if (medal.getWearingStatus() != null && medal.getWearingStatus() == 1) {
                    currentMedalScore += 1;
                    logSb.append(" 佩戴+1");
                }

                if (blackWhiteZhuboScore > 0) {
                    currentMedalScore = currentMedalScore + blackWhiteZhuboScore;
                } else {
                    currentMedalScore = -(currentMedalScore - blackWhiteZhuboScore);
                }
                totalMedalScore += currentMedalScore;
                logSb.append(" 黑白分:").append(currentMedalScore)
                        .append(";");
            } else {
                totalLifeMedalScore += currentMedalScore;
            }
        }

        if (totalMedalScore != 0) {
            logSb.append(" 勋章黑白分:").append(totalMedalScore).append("]");
            return Pair.of(totalMedalScore, "[勋章黑白分:" + totalMedalScore + "]");
        } else {
            logSb.append(" 勋章生活分").append(totalLifeMedalScore).append(" +1]");//需求如此
            return Pair.of(1, "[勋章生活+1]");
        }
    }

    /**
     * 关注列表不可见 — pnScoreMap检查 + 关注隐藏基线分。
     * 不再包含动态API调用（由主编排器在 Phase 3 按需触发）。
     */
    private static Pair<Integer, String> processHiddenFollowingsSync(long vmid, StringBuilder logSb) {
        logSb.append("  [关注:隐藏-1]");
        return Pair.of(-1, "[关注隐藏-1]");
    }

    /**
     * 关注列表可见 — 逐关注人打分 + pnScoreMap匹配 + 关键词扫描。
     */
    private static Pair<Integer, String> processVisibleFollowingsSync(long vmid, StringBuilder logSb, JSONObject follData) {
        JSONArray list = follData.getJSONArray("list");

        int blackWhiteScore = 0;
        StringBuilder blackWhiteType = new StringBuilder(60);
        int blackCount = 0, whiteCount = 0;
        int followersNameSignScore = 0;
        JSONArray followingsList = new JSONArray();
        JSONArray matchedList = new JSONArray();

        logSb.append("  [");
        for (Object obj : list) {
            JSONObject user = (JSONObject) obj;
            long mid = user.getLong("mid");
            String followedName = user.getString("uname");
            followingsList.add(followedName);
            FollowingCountTools.recordFollowing(mid, followedName);
            Integer score = pnScoreMap.get(mid);
            if (score != null) {
                blackWhiteScore += score;
                matchedList.add(followedName + ":" + score);
                MatchCountTools.recordMatch(mid, followedName, score);
                if (score < 0) blackCount++;
                else whiteCount++;
            } else {
                String followedSign = user.getString("sign");
                followersNameSignScore += getKeyWordsScore(
                        followedName + (followedSign != null ? followedSign : ""), logSb);
            }
        }
        blackWhiteScore += followersNameSignScore;

        if (!matchedList.isEmpty()) {
            logSb.append("匹配:").append(matchedList.size())
                    .append(" 分裂度:").append(blackCount * whiteCount)
                    .append(" 列表:").append(matchedList).append(" ");
        }

        if (blackWhiteScore != 0) {
            blackWhiteType.append("[关注黑白分:").append(blackWhiteScore).append("]");
        }
        logSb.append("关注黑白分:").append(blackWhiteScore).append("]");

        // logSb.append(", 关注列表:").append(followingsList);   // 数据太多隐藏

        return Pair.of(blackWhiteScore, blackWhiteType.toString());
    }

    /**
     * 卡片信息同步处理 — KOL/等级/认证/大会员/关键词 综合评分。
     */
    private static CardProcessResult processCardDataSync(JSONObject cardJson, StringBuilder logSb) {
        CardProcessResult r = new CardProcessResult();

        if (cardJson != null && cardJson.getShort("code") == 0) {
            JSONObject dataJO = cardJson.getJSONObject("data");
            if (dataJO != null) {
                JSONObject cardJO = dataJO.getJSONObject("card");
                if (cardJO != null) {
                    r.name = cardJO.getString("name");
                    r.sign = cardJO.getString("sign");
                    r.face = cardJO.getString("face");
                    String sex = cardJO.getString("sex");
                    long fans = cardJO.getLongValue("fans");
                    long attention = cardJO.getLongValue("attention");
                    boolean following = dataJO.getBooleanValue("following");
                    long archiveCount = dataJO.getLongValue("archive_count");
                    long articleCount = dataJO.getLongValue("article");
                    long likeNum = dataJO.getLongValue("like_num");

                    StringBuilder cardLog = new StringBuilder(60);
                    // cardLog.append("[卡片:code=0");
                    cardLog.append("  [卡片 投稿:").append(archiveCount)
                            .append(" 关注:").append(attention)
                            .append(" 粉丝:").append(fans)
                            .append(" 获赞:").append(likeNum);

                    // KOL
                    long kolLv = fans / 1000 + archiveCount / 100 + articleCount / 50 + likeNum / 10_0000;
                    if (kolLv != 0) {
                        r.score++;
                        r.type += "[KOL+1]";
                        cardLog.append(" KOL+1");
                    }

                    // 关注关系
                    if (following) {
                        r.score += 2;
                        r.type += "[已关注+2]";
                        cardLog.append(" 已关注+2");
                    } else if ((fans < 50 && attention > 4500) || attention > 4990) {
                        r.score--;
                        r.type += "[人机关注-1]";
                        cardLog.append(" 人机关注-1");
                    } else if (attention == 0 && fans == 0) {
                        r.score--;
                        r.type += "[人机关注-1]";
                        cardLog.append(" 人机关注-1");
                    }

                    // 等级
                    JSONObject levelInfo = cardJO.getJSONObject("level_info");
                    int lv = levelInfo != null ? levelInfo.getIntValue("current_level") : -1;
                    cardLog.append(" Lv").append(lv);
                    if (lv == 0) {
                        r.score -= 2;
                        r.type += "[Lv0:-2]";
                        cardLog.append("[Lv0:-2]");
                    } else if (lv <= 2 && (r.name != null && (r.name.startsWith("bili_") || "保密".equals(sex)))) {
                        r.score--;
                        r.type += "[Lv" + lv + " -1]";
                        cardLog.append(" Lv").append(lv).append(":-1");
                    } else if (lv >= 5) {
                        r.score++;
                        r.type += "[Lv" + lv + " +1]";
                        cardLog.append(" Lv").append(lv).append("+1");
                    }

                    // 认证
                    JSONObject official = cardJO.getJSONObject("Official");
                    if (official != null && StringUtils.isNotEmpty(official.getString("title"))) {
                        r.score++;
                        r.type += "[认证+1]";
                        cardLog.append(" 认证+1 ");
                    }

                    // 大会员
                    JSONObject vip = cardJO.getJSONObject("vip");
                    if (vip != null) {
                        JSONObject label = vip.getJSONObject("label");
                        if (label != null && StringUtils.contains(label.getString("text"), "大会员")) {
                            r.score++;
                            r.type += "[大会员+1]";
                            cardLog.append(" 大会员+1");
                        }
                    }

                    // 姓名+签名关键词
                    int kw = getKeyWordsScore((r.name != null ? r.name : "") + (r.sign != null ? r.sign : ""), cardLog);
                    r.score += kw;
                    //  if (kw != 0) cardLog.append(" 签名关键词:").append(kw);

                    cardLog.append(" 卡片黑白分:").append(r.score).append("]");
                    logSb.append(cardLog);
                    return r;
                }
            }
            logSb.append("[卡片:解析异常]");
        } else {
            logSb.append("   [卡片api冷却:0]");
            // 全局熔断
            if (cookiePool.getTotalAvailableCount() == 0
                    && schedulercardJOColdWait.compareAndSet(false, true)) {
                schedulercardJOService.schedule(() -> {
                    schedulercardJOColdWait.set(false);
                    System.out.println("卡片API: 5分钟全局熔断已解除，当前时间：" + System.currentTimeMillis());
                }, 5, TimeUnit.MINUTES);
            }
        }
        return r;
    }

    // ---- 关键词 & 动态辅助 ----

    private static int getKeyWordsScore(String dataStr, StringBuilder sb) {
        int totalScore = 0;

        xyz.acproject.danmuji.conf.set.KeyWordSetConf kwConf = PublicDataConf.centerSetConf.getKey_word();
        if (kwConf != null && kwConf.getKeywords() != null) {
            for (xyz.acproject.danmuji.conf.set.KeyWordEntry entry : kwConf.getKeywords()) {
                if (StringUtils.isBlank(entry.getKeyword())) continue;
                if (matchKeyword(dataStr, entry.getKeyword())) {
                    int score = entry.getScore() != null ? entry.getScore() : 0;
                    totalScore += score;
                    sb.append(" ").append(entry.getKeyword()).append(":").append(score > 0 ? "+" : "").append(score).append(" ");
                }
            }
        }

        return totalScore;
    }

    /**
     * 关键词匹配：若关键词以反引号 ` 包裹，则作为正则表达式处理；否则进行模糊（包含）匹配。
     *
     * @param dataStr 待匹配的文本
     * @param keyword 关键词，若形如 `regex` 则按正则处理
     * @return 是否匹配
     */
    private static boolean matchKeyword(String dataStr, String keyword) {
        if ((keyword.startsWith("【") && keyword.endsWith("】")
                || keyword.startsWith("[") && keyword.endsWith("]"))
                && keyword.length() > 2) {
            // 正则模式：去掉首尾反引号，编译后匹配
            String regex = keyword.substring(1, keyword.length() - 1);
            try {
                return Pattern.compile(regex).matcher(dataStr).find();
            } catch (PatternSyntaxException e) {
                LOGGER.warn("关键词正则语法错误: {}", keyword, e);
                return false;
            }
        } else {
            // 普通模糊匹配（包含）
            return StringUtils.contains(dataStr, keyword);
        }
    }

    /**
     * 动态数据分析 — JSON解析 + 关键词打分 + 隐藏判断。
     * 仅在 Phase 3（综合分=0时）调用。
     * 解析 space_history API 返回的 JSON，提取有效文本进行关键词检测，
     * 避免对原始 JSON 元数据进行粗糙的字符串匹配。
     */
    private static Pair<Integer, String> computeDynamicScore(String dynData, StringBuilder logSb) {
        if (dynData == null) {
            logSb.append("  [动态解析异常：0]⚪");
            return Pair.of(0, "[动态解析异常]");
        }

        // 解析 JSON，提取有效文本、动态数量和最新时间戳
        ExtractedDynamicResult extracted = extractDynamicContent(dynData, logSb);

        if (extracted == null) {
            // JSON 解析失败 或 code != 0 → API异常
            if (cookiePool.getTotalAvailableCount() == 0
                    && schedulerDynamicColdWait.compareAndSet(false, true)) {
                System.out.println("动态API: 所有账号Cookie均已冷却，触发全局熔断15分钟。");
                schedulerDynamicService.schedule(() -> {
                    schedulerDynamicColdWait.set(false);
                    System.out.println("动态API: 15分钟全局熔断已解除，当前时间：" + System.currentTimeMillis());
                }, 15, TimeUnit.MINUTES);
            }
            logSb.append("  [动态:API异常:0]⚪");
            return Pair.of(0, "");
        }

        if (extracted.cardCount == 0) {
            logSb.append("  [动态:无内容/隐藏-1]⚪");
            return Pair.of(-1, "[动态隐藏-1]");
        }

        int kw = getKeyWordsScore(extracted.text, logSb);

        logSb.append("  [动态数:").append(extracted.cardCount);
        if (extracted.latestTimestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Date date = new Date(extracted.latestTimestamp * 1000);
            // logSb.append(" 最新:").append(sdf.format(date));

            if (extracted.cardCount == 1) {
                boolean isNewUser = StringUtils.contains(extracted.text, "挑战转正答题考试"); //
                if (isNewUser) {
                    int l = "2025-10-01 08:01".compareTo(date.toString());
                    int r = "2026-02-01 08:01".compareTo(date.toString());
                    if (l < 0 && r > 0) {
                        logSb.append(" 动态人机-1]");
                        kw -= 1;
                    }
                }
            }
        }

        if (kw != 0) {
            logSb.append(" 动态黑白分:").append(kw).append("]⚪");
            return Pair.of(kw, "[动态黑白分:" + kw + "]");
        } else {

            logSb.append(" 动态黑白分:0]⚪");
            return Pair.of(0, "");
        }


    }

    /**
     * 从 space_history API 原始响应中提取动态内容。
     * 每条动态由 "desc" + "card" 共同构成：
     * - desc 提供 type（动态类型）、timestamp（发布时间）等元信息
     * - card 是 JSON 字符串，包含实际内容（文字、标题、描述等）
     *
     * @return 提取结果（文本 + 动态数 + 最新时间戳），失败返回 null
     */
    private static ExtractedDynamicResult extractDynamicContent(String dynData, StringBuilder logSb) {
        try {
            JSONObject response = JSONObject.parseObject(dynData);
            if (response == null) return null;

            Integer code = response.getInteger("code");
            if (code == null || code != 0) return null;

            JSONObject data = response.getJSONObject("data");
            if (data == null) return null;

            JSONArray cards = data.getJSONArray("cards");
            if (cards == null || cards.isEmpty()) {
                return new ExtractedDynamicResult("", 0, 0L);
            }

            StringBuilder textBuilder = new StringBuilder();
            long latestTimestamp = 0;

            for (int i = 0; i < cards.size(); i++) {
                JSONObject cardWrapper = cards.getJSONObject(i);
                if (cardWrapper == null) continue;

                JSONObject desc = cardWrapper.getJSONObject("desc");
                Integer type = desc != null ? desc.getInteger("type") : null;
                Long timestamp = desc != null ? desc.getLong("timestamp") : null;

                // 更新最新时间戳
                if (timestamp != null && timestamp > latestTimestamp) {
                    latestTimestamp = timestamp;
                }

                // card 是 JSON 字符串，需要二次解析
                String cardStr = cardWrapper.getString("card");
                if (cardStr == null || cardStr.isEmpty()) continue;

                JSONObject card;
                try {
                    card = JSONObject.parseObject(cardStr);
                } catch (Exception e) {
                    continue;
                }
                if (card == null) continue;

                // 基于动态类型提取有效文本
                String extracted = extractCardText(card, type);
                if (extracted != null && !extracted.isEmpty()) {
                    if (textBuilder.length() > 0) textBuilder.append(" ");
                    textBuilder.append(extracted);
                }
            }

            return new ExtractedDynamicResult(textBuilder.toString(), cards.size(), latestTimestamp);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据动态类型从 card JSONObject 中提取有效文本。
     * 不同 type 的 card 结构不同，按字段名精确提取以支持后续关键词检测。
     */
    private static String extractCardText(JSONObject card, Integer type) {
        if (type == null) return "";

        switch (type) {
            case 1: { // 转发动态
                JSONObject item = card.getJSONObject("item");
                if (item != null) {
                    String content = item.getString("content");
                    if (StringUtils.isNotBlank(content)) return content;
                }
                // 递归提取被转发的内容
                String originStr = card.getString("origin");
                if (StringUtils.isNotBlank(originStr)) {
                    try {
                        JSONObject originCard = JSONObject.parseObject(originStr);
                        if (originCard != null) {
                            StringBuilder osb = new StringBuilder();
                            String oTitle = originCard.getString("title");
                            String oDesc = originCard.getString("desc");
                            String oDynamic = originCard.getString("dynamic");
                            if (StringUtils.isNotBlank(oTitle)) osb.append(oTitle).append(" ");
                            if (StringUtils.isNotBlank(oDesc) && !"-".equals(oDesc)) osb.append(oDesc).append(" ");
                            if (StringUtils.isNotBlank(oDynamic)) osb.append(oDynamic);
                            return osb.toString().trim();
                        }
                    } catch (Exception ignored) {
                    }
                }
                return "";
            }
            case 2: { // 纯文字/带图动态
                JSONObject item = card.getJSONObject("item");
                if (item != null) {
                    String desc = item.getString("description");
                    return desc != null ? desc : "";
                }
                return "";
            }
            case 8: { // 视频投稿
                StringBuilder sb = new StringBuilder();
                String title = card.getString("title");
                String desc = card.getString("desc");
                String dynamic = card.getString("dynamic");
                if (StringUtils.isNotBlank(title)) sb.append(title).append(" ");
                if (StringUtils.isNotBlank(desc) && !"-".equals(desc)) sb.append(desc).append(" ");
                if (StringUtils.isNotBlank(dynamic)) sb.append(dynamic);
                return sb.toString().trim();
            }
            case 64: { // 专栏文章
                StringBuilder sb = new StringBuilder();
                String title = card.getString("title");
                String summary = card.getString("summary");
                if (StringUtils.isNotBlank(title)) sb.append(title).append(" ");
                if (StringUtils.isNotBlank(summary)) sb.append(summary);
                return sb.toString().trim();
            }
            default: { // 未知类型，尝试通用提取
                JSONObject item = card.getJSONObject("item");
                if (item != null) {
                    String content = item.getString("content");
                    if (StringUtils.isNotBlank(content)) return content;
                    String description = item.getString("description");
                    if (StringUtils.isNotBlank(description)) return description;
                }
                String title = card.getString("title");
                return title != null ? title : "";
            }
        }
    }

    /**
     * 动态内容提取结果 — 不可变数据结构。
     */
    private static class ExtractedDynamicResult {
        final String text;           // 所有动态拼接后的有效文本
        final int cardCount;         // 动态条数
        final long latestTimestamp;  // 最新动态时间戳（秒）

        ExtractedDynamicResult(String text, int cardCount, long latestTimestamp) {
            this.text = text;
            this.cardCount = cardCount;
            this.latestTimestamp = latestTimestamp;
        }
    }

    /**
     * 加载正白负黑表，返回 uid -> score 的映射
     */
    private static Map<Long, Integer> loadNegativeBlackPositiveWhiteScores() {
        Map<Long, Integer> map = new HashMap<>();
        try {
            FileTools fileTools = new FileTools();
            File file = new File(fileTools.getBaseJarPath(), "set/负黑正白判定表.json");
            if (!file.exists()) return map;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject json = JSONObject.parseObject(sb.toString());
            if (json == null) return map;
            JSONArray list = json.getJSONArray("followings_list");
            if (list != null) {
                for (Object obj : list) {
                    JSONObject entry = (JSONObject) obj;
                    map.put(entry.getLongValue("uid"), entry.getIntValue("score"));
                }
            }
        } catch (Exception e) {
            LOGGER.error("loadNegativeBlackPositiveWhiteScores error", e);
        }
        return map;
    }

    public static void reloadPnScoreMap() {
        pnScoreMap = loadNegativeBlackPositiveWhiteScores();
    }

    // ==================== 限流器 & Cookie池 管理接口 ====================

    /**
     * 从 AccountPoolConf 同步限流器配置和缓存TTL。
     * 速率 = 基础速率 × 可用Cookie数量（含主账号），账号越多总吞吐越高。
     * 当用户在UI中修改账号池配置时调用。
     */
    public static void syncRateLimiterConfig(xyz.acproject.danmuji.conf.set.AccountPoolConf conf) {
        if (conf == null) return;

        // 可用账号数 = 主账号(1) + 健康的子账号数
        int availableCount = cookiePool.getTotalAvailableCount();
        if (availableCount < 1) availableCount = 1;

        // 单账号基础速率 × 可用账号数 = 总吞吐量
        double newDynamicRate = conf.getDynamicRate() * availableCount;
        double newCardRate = conf.getCardRate() * availableCount;

        if (newDynamicRate > 0 && Math.abs(newDynamicRate - dynamicRateLimiter.getPermitsPerSecond()) > 0.001) {
            dynamicRateLimiter = new TokenBucketRateLimiter(newDynamicRate, Math.max(newDynamicRate, 1.0));
            LOGGER.info("动态API限流器已更新: {} req/s (基础{}×{}账号)", newDynamicRate, conf.getDynamicRate(), availableCount);
        }
        if (newCardRate > 0 && Math.abs(newCardRate - cardRateLimiter.getPermitsPerSecond()) > 0.001) {
            cardRateLimiter = new TokenBucketRateLimiter(newCardRate, Math.max(newCardRate * 2, 2.0));
            LOGGER.info("卡片API限流器已更新: {} req/s (基础{}×{}账号)", newCardRate, conf.getCardRate(), availableCount);
        }
        apiCache.syncFromConfig(conf);
    }

    /**
     * 获取限流器和缓存的状态信息（用于 UI 展示）。
     */
    public static com.alibaba.fastjson.JSONObject getRateLimiterStats() {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();

        com.alibaba.fastjson.JSONObject dynamic = new com.alibaba.fastjson.JSONObject();
        dynamic.put("rate", dynamicRateLimiter.getPermitsPerSecond());
        dynamic.put("availableTokens", Math.round(dynamicRateLimiter.getAvailableTokens() * 100.0) / 100.0);
        dynamic.put("totalRequests", dynamicRateLimiter.getTotalRequests());
        dynamic.put("throttledRequests", dynamicRateLimiter.getThrottledRequests());
        json.put("dynamic", dynamic);

        com.alibaba.fastjson.JSONObject card = new com.alibaba.fastjson.JSONObject();
        card.put("rate", cardRateLimiter.getPermitsPerSecond());
        card.put("availableTokens", Math.round(cardRateLimiter.getAvailableTokens() * 100.0) / 100.0);
        card.put("totalRequests", cardRateLimiter.getTotalRequests());
        card.put("throttledRequests", cardRateLimiter.getThrottledRequests());
        json.put("card", card);

        com.alibaba.fastjson.JSONObject cache = new com.alibaba.fastjson.JSONObject();
        cache.put("size", apiCache.getSize());
        cache.put("hitCount", apiCache.getHitCount());
        cache.put("missCount", apiCache.getMissCount());
        cache.put("hitRate", Math.round(apiCache.getHitRate() * 10000.0) / 100.0);
        cache.put("ttlSeconds", apiCache.getTtlSeconds());
        json.put("cache", cache);

        json.put("availableCookies", cookiePool.getTotalAvailableCount());

        return json;
    }

    /**
     * 获取 CookiePoolManager 实例（供外部调用）。
     */
    public static CookiePoolManager getCookiePool() {
        return cookiePool;
    }

    public static boolean isUidInPnScoreMap(long uid) {
        return pnScoreMap.containsKey(uid);
    }

    public static Map<Long, String> httpGetGuardList() {
        String data = null;
        Map<Long, String> guardMap = new ConcurrentHashMap<>();
        JSONObject jsonObject = null;
        JSONArray jsonArray = null;
        Map<String, String> headers = null;
        Map<String, String> datas = null;
        short code = -1;
        int totalSize = httpGetGuardListTotalSize();
        int page = 0;
        if (totalSize == 0) {
            return null;
        }
        page = (int) Math.ceil((float) totalSize / 29F);
        if (page == 0) {
            page = 1;
        }
        for (int i = 1; i <= page; i++) {
            headers = new HashMap<>(3);
            headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
            headers.put("user-agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
//			if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
//				headers.put("cookie", PublicDataConf.USERCOOKIE);
//			}
            datas = new HashMap<>(4);
            datas.put("roomid", PublicDataConf.ROOMID.toString());
            datas.put("page", String.valueOf(i));
            datas.put("ruid", PublicDataConf.AUID.toString());
            datas.put("page_size", "29");
            try {
                data = OkHttp3Utils.getHttp3Utils()
                        .httpGet("https://api.live.bilibili.com/xlive/app-room/v1/guardTab/topList", headers, datas)
                        .body().string();
            } catch (Exception e) {
                // TODO 自动生成的 catch 块
                LOGGER.error(e);
                data = null;
            }
            if (data == null)
                return null;
            jsonObject = JSONObject.parseObject(data);
            code = jsonObject.getShort("code");
            if (code == 0) {
                jsonArray = ((JSONObject) jsonObject.get("data")).getJSONArray("list");
                for (Object object : jsonArray) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        // TODO 自动生成的 catch 块
                        LOGGER.error(e);
                    }
                    guardMap.put(((JSONObject) object).getLong("uid"), ((JSONObject) object).getString("username"));
                }
                if (i == 1) {
                    jsonArray = ((JSONObject) jsonObject.get("data")).getJSONArray("top3");
                    for (Object object : jsonArray) {
                        guardMap.put(((JSONObject) object).getLong("uid"),
                                ((JSONObject) object).getString("username"));
                    }
                }
            } else {
                LOGGER.error("直播房间号不存在，或者未知错误，请尝试更换房间号,原因:" + jsonObject.getString("message"));
            }
        }
        return guardMap;
    }

    public static int httpGetGuardListTotalSize() {
        String data = null;
        Map<String, String> headers = null;
        Map<String, String> datas = null;
        int num = 0;
        JSONObject jsonObject = null;
        short code = -1;
        headers = new HashMap<>(3);
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
//		if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
//			headers.put("cookie", PublicDataConf.USERCOOKIE);
//		}
        datas = new HashMap<>(5);
        datas.put("roomid", PublicDataConf.ROOMID.toString());
        datas.put("page", String.valueOf(1));
        datas.put("ruid", PublicDataConf.AUID.toString());
        datas.put("page_size", "29");
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/app-room/v1/guardTab/topList", headers, datas).body()
                    .string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return num;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            num = ((JSONObject) ((JSONObject) jsonObject.get("data")).get("info")).getInteger("num");
        } else {
            LOGGER.error("直播房间号不存在，或者未知错误，请尝试更换房间号,原因:" + jsonObject.getString("message"));
        }
        return num;
    }

    public static CheckTx httpGetCheckTX() {
        String data = null;
        JSONObject jsonObject = null;
        short code = -1;
        Map<String, String> headers = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
//		if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
//			headers.put("cookie", PublicDataConf.USERCOOKIE);
//		}
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/lottery-interface/v1/Anchor/Check?roomid="
                            + CurrencyTools.parseRoomId(), headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            if (jsonObject.get("data") != null) {
                return new CheckTx(((JSONObject) jsonObject.get("data")).getLong("room_id"),
                        ((JSONObject) jsonObject.get("data")).getString("gift_name"),
                        ((JSONObject) jsonObject.get("data")).getShort("time"));
            }
        } else {
            LOGGER.error("检查天选礼物失败,原因:" + jsonObject.getString("message"));
        }
        return null;
    }

    public static LotteryInfoWeb httpGetLotteryInfoWeb() {
        String data = null;
        JSONObject jsonObject = null;
        short code = -1;
        Map<String, String> headers = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/lottery-interface/v1/lottery/getLotteryInfoWeb?roomid="
                            + PublicDataConf.ROOMID, headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            if (jsonObject.get("data") != null) {
                LotteryInfoWeb lotteryInfoWeb = jsonObject.getJSONObject("data").toJavaObject(LotteryInfoWeb.class);
                return lotteryInfoWeb;
            }
        } else {
            LOGGER.error("获取房间抽奖失败,原因:" + jsonObject.getString("message"));
        }
        return null;
    }

    public static List<RoomBlock> getBlockList(int page) {
        String data = null;
        JSONObject jsonObject = null;
        JSONArray jsonArray = null;
        List<RoomBlock> roomBlocks = new ArrayList<>();
        Map<String, String> headers = null;
        Map<String, String> params = null;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(4);
        params.put("room_id", PublicDataConf.ROOMID.toString());
        params.put("ps", "1");
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpPostForm("https://api.live.bilibili.com/xlive/web-ucenter/v1/banned/GetSilentUserList", headers, params)
                    .body().string();
        } catch (Exception e) {
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return roomBlocks;
        jsonObject = JSONObject.parseObject(data);
        short code = jsonObject.getShort("code");
        if (code == 0) {
            jsonArray = jsonObject.getJSONArray("data");
            if (!CollectionUtils.isEmpty(jsonArray)) {
                roomBlocks = new ArrayList<>(jsonArray.toJavaList(RoomBlock.class));
            }
            return roomBlocks;
        }
        return roomBlocks;
    }
}
