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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss"));

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

    /** 动态API令牌桶限流器（默认0.5 QPS，从AccountPoolConf读取） */
    static volatile TokenBucketRateLimiter dynamicRateLimiter =
            new TokenBucketRateLimiter(0.5, 1.0);

    /** 卡片API令牌桶限流器（默认1.0 QPS，从AccountPoolConf读取） */
    static volatile TokenBucketRateLimiter cardRateLimiter =
            new TokenBucketRateLimiter(1.0, 2.0);

    /** Cookie池管理器 */
    static final CookiePoolManager cookiePool = CookiePoolManager.getInstance();

    /** API缓存管理器 */
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

    private static CompletableFuture<JSONObject> asyncHttpGetFollowings(long vmid, int page, int pageSize) {
        Map<String, String> headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + vmid + "/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        Map<String, String> datas = new HashMap<>(3);
        datas.put("vmid", String.valueOf(vmid));
        datas.put("pn", String.valueOf(page));
        datas.put("ps", String.valueOf(pageSize));
        return asyncHttpGetBody("https://api.bilibili.com/x/relation/followings", headers, datas)
                .thenApply(body -> body != null ? JSONObject.parseObject(body) : null);
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

        // 2. 获取令牌（阻塞等待，最长30秒超时）
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
                        // 检查是否触发限流（B站返回的非正常响应）
                        String resultStartStr = "{\"code\":0,\"message\":\"OK\"";
                        if (result.contains(resultStartStr)) {
                            // 正常响应，缓存结果
                            apiCache.put(cacheKey, result);
                        } else {
                            // 异常响应，标记该Cookie被限流
                            cookiePool.markRateLimited(usedCookie);
                            LOGGER.warn("动态API异常响应 mid={}，已标记Cookie冷却", mid);
                        }
                    }
                    return result;
                });
    }

    private static CompletableFuture<JSONObject> asyncHttpGetMedalWall(long targetId) {
        Map<String, String> headers = new HashMap<>(3);
        headers.put("referer", "https://space.bilibili.com/" + targetId + "/");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        Map<String, String> datas = new HashMap<>(1);
        datas.put("target_id", String.valueOf(targetId));
        return asyncHttpGetBody("https://api.live.bilibili.com/xlive/web-ucenter/user/MedalWall", headers, datas)
                .thenApply(body -> body != null ? JSONObject.parseObject(body) : null);
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

        // 2. 获取令牌（阻塞等待，最长30秒超时）
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

    /**
     * 处理用户关注列表：判断可见性，输出结果，如果可见且有关注数据则写入文件
     */
    public static CompletableFuture<Pair<Integer, String>> processFollowings(long vmid, String uname) {
        // 日志前缀（同步构建）
        StringBuilder logSb = new StringBuilder(100);
        logSb.append(TIME_FORMAT.get().format(System.currentTimeMillis()))
                .append(" ")
                .append("https://space.bilibili.com/")
                .append(vmid)
                .append(" ")
                .append(uname)
                .append(" ");

        // 粉丝勋章墙打印
        CompletableFuture<Pair<Integer, String>> medalWallScore = asyncHttpGetMedalWall(vmid).thenCompose(medalData -> processMedalWall(medalData, logSb));

        // 2. 异步获取用户关注列表
        return asyncHttpGetFollowings(vmid, 1, 50).thenCompose(firstPage -> {
            short code = firstPage != null ? firstPage.getShort("code") : -1;
            JSONObject data = firstPage != null && code == 0 ? firstPage.getJSONObject("data") : null;
            long total = data != null ? data.getLongValue("total") : 0;

            if (firstPage == null || code != 0 || data == null || total == 0) {
                return processHiddenFollowings(vmid, logSb);
            } else {
                return processVisibleFollowings(vmid, logSb, firstPage);
            }
        });
    }

    /**
     * 处理粉丝勋章墙数据，匹配pnScoreMap计算勋章黑白分
     * @return Pair(勋章总分, 得分类型)
     */
    private static CompletableFuture<Pair<Integer, String>> processMedalWall(JSONObject medalData, StringBuilder logSb) {
        SelfTools.appendAt(logSb, 80, "");

        if (medalData == null || medalData.getIntValue("code") != 0) {
            LogFileTools.getlogFileTools().logTestFile(logSb + " 灯牌:API异常");
            return CompletableFuture.completedFuture(Pair.of(0, ""));
        }

        JSONObject data = medalData.getJSONObject("data");
        if (data == null || data.getIntValue("close_space_medal") == 1) {
         //   LogFileTools.getlogFileTools().logFollowingsFile(logSb + " 灯牌:隐藏");

            return CompletableFuture.completedFuture(Pair.of(-1, "[灯牌隐藏-1]"));
        }

        int count = data.getIntValue("count");
        JSONArray list = data.getJSONArray("list");

        if (count <= 0 || list == null || list.isEmpty() ) {
           // LogFileTools.getlogFileTools().logTestFile(logSb + " 灯牌:0");
            return CompletableFuture.completedFuture(Pair.of(0, ""));
        }

        int totalMedalScore = 0;
        int totalLifeMedalScore = 0;
        logSb.append(" [灯牌数:").append(count).append("]");
        String blackWhiteType = null;

        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            JSONObject medalInfo = item.getJSONObject("medal_info");
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

            // 匹配pnScoreMap: 用勋章主播ID(target_id)查找黑白名单分数
            int currentMedalScore = 0;

            //
            int level = medal.getLevel() != null ? medal.getLevel() : 0;
            int currentMedalLevelScore = (int)  (Math.log( level) /  Math.log(2));
            currentMedalScore += currentMedalLevelScore;

            //
            Integer blackWhiteZhuboScore = pnScoreMap.get(medal.getTargetId());
            if(blackWhiteZhuboScore!=null){
                logSb.append( " [").append(medal.getTargetName())
                        .append(":").append(blackWhiteZhuboScore)
                        .append(", ").append(medal.getMedalName())
                        .append(":").append(medal.getLevel())
                        .append("->").append(currentMedalLevelScore);

                int guardLevel = medal.getGuardLevel() != null ? medal.getGuardLevel() : 0;  /* 大航海等级: 0非舰长, 1总督, 2提督, 3舰长 */
                if(guardLevel!=0){
                    currentMedalScore +=guardLevel; // 大航海等级打分
                   // logSb.append(", 舰长+").append(guardLevel);
                }

                if(medal.getOfficial() != null && medal.getOfficial() != 0){   // 认证 buff
                    currentMedalScore += 1;
                  //  logSb.append(" 认证+1");
                }

                if(medal.getWearingStatus() != null && medal.getWearingStatus() == 1){  // 佩戴 buff
                    currentMedalScore += 1;
                   // logSb.append(", 佩戴+");
                }


                if( blackWhiteZhuboScore > 0){
                    currentMedalScore = currentMedalScore + blackWhiteZhuboScore;
                    blackWhiteType = "白牌分:"+currentMedalScore;
                    logSb.append(" ").append(blackWhiteType).append("]");
                } else if( blackWhiteZhuboScore < 0){
                    currentMedalScore  = -( currentMedalScore - blackWhiteZhuboScore);
                    blackWhiteType = "黑牌分:"+currentMedalScore;
                    logSb.append(" ").append(blackWhiteType).append("]");
                }

                totalMedalScore += currentMedalScore;
            } else {
                totalLifeMedalScore += currentMedalScore;
            }


        }
        logSb.append(" [生活分:").append(totalLifeMedalScore).append( "]")
                .append(" [灯牌黑白分:").append(totalMedalScore).append("]");

        if (totalMedalScore != 0) {

           // LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb));

            return CompletableFuture.completedFuture(Pair.of(totalMedalScore, blackWhiteType));
        }

        return CompletableFuture.completedFuture(Pair.of(0, ""));
    }

    /**
     * 关注列表不可见 → 通过空间动态判断
     */
    private static CompletableFuture<Pair<Integer, String>> processHiddenFollowings(long vmid, StringBuilder logSb) {
        StringBuilder logSbEnd = new StringBuilder(100);
        // 1 当前观众就在本地黑白名单里
        Integer pnScore = pnScoreMap.get(vmid);
        if (pnScore != null) {
            String blackWhiteType = "[已在名单，关注隐藏: " + pnScore + "]";
            logSb.append(blackWhiteType);
            LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb));
            return CompletableFuture.completedFuture(Pair.of(pnScore, blackWhiteType));
        }

        logSb.append(" [关注隐藏-1] ");

        // 全局熔断兜底：如果所有Cookie都在冷却中，跳过API调用
        if (schedulerDynamicColdWait.get()) {
            logSb.append(" [动态API冷却=0] ");
            return proceedToCardCheck(vmid, logSb, logSbEnd, 0, null);
        }

        return asyncHttpGetUserDynamic(vmid).thenCompose(dynData -> {
            String blackWhiteType = null;

            int blackWhiteScore = -1;
            if (dynData == null) {
                blackWhiteScore--;
                blackWhiteType = "[动态解析异常-1]";
                logSb.append(blackWhiteType);
                logSbEnd.append(" [成份:关注隐藏]").append(blackWhiteType);
                return proceedToCardCheck(vmid, logSb, logSbEnd, blackWhiteScore, blackWhiteType);
            }

            String resultStartStr = "{\"code\":0,\"message\":\"OK\"";
            LogFileTools.getlogFileTools().logTestFile(logSb + dynData.substring(0, Math.min(84, dynData.length())));

            if (dynData.contains(resultStartStr)) {
                if (dynData.length() < 100) {
                    blackWhiteScore--;
                    blackWhiteType = "[动态隐藏-1]";  // -2
                } else {
                    int keyWordsScore = getKeyWordsScore(dynData, logSb);
                    blackWhiteScore += keyWordsScore;
                    blackWhiteType = "[动态关键词:" + keyWordsScore + "]"; // -1 如果动态可见正常，则动态0分，总分值-1
                }
                logSb.append(blackWhiteType);

            } else {
                // API异常 — CookiePool已经在asyncHttpGetUserDynamic中标记了当前Cookie冷却
                // 如果所有子账号都在冷却且没有可用账号，触发全局熔断
                if (cookiePool.getTotalAvailableCount() == 0
                        && schedulerDynamicColdWait.compareAndSet(false, true)) {
                    System.out.println("动态API: 所有账号Cookie均已冷却，触发全局熔断15分钟。建议添加更多子账号或降低请求频率。");
                    schedulerDynamicService.schedule(() -> {
                        schedulerDynamicColdWait.set(false);
                        System.out.println("动态API: 15分钟全局熔断已解除，当前时间：" + System.currentTimeMillis());
                    }, 15, TimeUnit.MINUTES);
                }
                LogFileTools.getlogFileTools().logTestFile(logSb + " 用户动态API 调用超过频次，已标记Cookie冷却");
            }

            return proceedToCardCheck(vmid, logSb, logSbEnd, blackWhiteScore, blackWhiteType);
        });
    }


    private static int getKeyWordsScore(String dataStr, StringBuilder logSb) {
        int blackWhiteScore = 0;

        //黑名单 -2， 白名单+1 （白名单 用于热爱生活的路人）
        if (PublicDataConf.centerSetConf.getBlack() != null) {
            for (String s : PublicDataConf.centerSetConf.getBlack().getNames()) {
                if (StringUtils.isBlank(s)) continue;
                if (StringUtils.contains(dataStr, s)) {
                    blackWhiteScore -= 2;
                    logSb.append(" [").append(s).append("-1]");
                }
            }
            for (String s : PublicDataConf.centerSetConf.getWhite().getNames()) {
                if (StringUtils.isBlank(s)) continue;
                if (StringUtils.contains(dataStr, s)) {
                    blackWhiteScore++;
                    logSb.append(" [").append(s).append("+1]");
                }
            }
        } else {
            logSb.append(" [key没获取到]");
        }
        return blackWhiteScore;
    }

    /**
     * 关注列表可见 → 逐关注人打分
     */
    private static CompletableFuture<Pair<Integer, String>> processVisibleFollowings(long vmid, StringBuilder logSb, JSONObject firstPage) {
        StringBuilder logSbEnd = new StringBuilder(100);
        JSONArray list = firstPage.getJSONObject("data").getJSONArray("list");

        int blackWhiteScore = 0;   // 列表可见为0， 不可见为-1
        StringBuilder blackWhiteType = new StringBuilder(100);
        int blackCount = 0;
        int whiteCount = 0;
        int followersNameSignScore = 0;
        JSONArray followingsList = new JSONArray();
        JSONArray matchedList = new JSONArray();

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
                if (score < 0) {
                    blackCount++;
                } else {
                    whiteCount++;
                }
            } else {
                // 通过关注列表的人的名字，签名来判断
                followersNameSignScore += getKeyWordsScore(String.valueOf(obj), logSb);
            }
        }

//        if (blackWhiteScore > 0) {
//            logSbEnd.append("[成分:关注正面]");
//        } else if (blackWhiteScore < 0) {
//            logSbEnd.append("[成分:关注负面]");
//        } else {
//            logSbEnd.append("[成分:关注普通]");
//        }
        blackWhiteScore += followersNameSignScore;

        // 1 当前观众就在本地黑白名单里
        Integer pnScore = pnScoreMap.get(vmid);
        if (null != pnScore) {
            blackWhiteScore = pnScore;
            blackWhiteType.append("[已在名单，关注可见: ").append(pnScore).append("]");
            logSb.append(blackWhiteType);
        }

        if (!matchedList.isEmpty()) {
            logSbEnd.append(" [关注黑白分:").append(blackWhiteScore).append("]")
                    .append(" [分裂度:").append(blackCount * whiteCount)
                    .append("] [匹配数:").append(matchedList.size())
                    .append("] 黑白名单:").append(matchedList.toJSONString());
        }
        logSbEnd.append(" 🍉🍉 关注列表:").append(followingsList.toJSONString());

        if (blackWhiteScore != 0) {
            SelfTools.appendAt(logSb, 270, logSbEnd.toString());
            LogFileTools.getlogFileTools().logFollowingsFile(logSb.toString());
            return CompletableFuture.completedFuture(Pair.of(blackWhiteScore, blackWhiteType.toString()));
        }

        return proceedToCardCheck(vmid, logSb, logSbEnd, blackWhiteScore, blackWhiteType.toString());
    }

    /**
     * 3. 用户卡片信息判断 → 最终打分
     */
    private static CompletableFuture<Pair<Integer, String>> proceedToCardCheck(long vmid, StringBuilder logSb, StringBuilder logSbEnd,
                                                                               int blackWhiteScore, String blackWhiteType) {
        // 全局熔断兜底：如果所有Cookie都在冷却中，跳过卡片API调用
        if (schedulercardJOColdWait.get()) {
            SelfTools.appendAt(logSb, 270, logSbEnd.toString());
            LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb));

            return CompletableFuture.completedFuture(Pair.of(blackWhiteScore, blackWhiteType));
        }

        return asyncHttpGetUserCard(vmid).thenApply(dataX -> {
            int score = blackWhiteScore;
            String type = blackWhiteType;

            if (dataX != null && dataX.getShort("code") == 0) {
                JSONObject dataJO = dataX.getJSONObject("data");

                if (dataJO != null) {
                    JSONObject cardJO = dataJO.getJSONObject("card");

                    if (cardJO != null) {
                        String name = cardJO.getString("name");
                        String sex = cardJO.getString("sex");
                        String sign = cardJO.getString("sign");
                        String face = cardJO.getString("face"); // 头像链接  "https://i1.hdslb.com/bfs/face/0daf1ee1cd504a33ffe3b995a6692b3054319291.jpg",
                        long fans = cardJO.getLongValue("fans");
                        long attention = cardJO.getLongValue("attention");

                        boolean following = dataJO.getBooleanValue("following");
                        long archiveCount = dataJO.getLongValue("archive_count");
                        long article_count = dataJO.getLongValue("article"); // 专栏
                        long likeNum = dataJO.getLongValue("like_num");

                        SelfTools.appendAt(logSb, 180, "");
                        logSb.append("[投稿:").append(archiveCount)
                                .append("][关注:").append(attention)
                                .append("][粉丝:").append(fans)
                                .append("][获赞:").append(likeNum)
                                .append("]");

                        long KolLevel = fans / 1000 + archiveCount / 100 + article_count / 50 + likeNum / 10_0000;
                        if (KolLevel != 0) {
                            score++;
                            type = "[KOL+1]";
                            logSb.append(" [KOL:").append(KolLevel).append("]").append(type);
                        }

                        if (following) {
                            score += 2;
                            type = "[已关注+2]";
                            logSb.append(type);
                        } else if ((fans < 50 && attention > 4500) || (attention > 4990)) {
                            score--;
                            type = "[疑似人机-1]";
                            logSb.append(type);
                        } else if (attention == 0 && fans == 0) {
                            score--;
                            type = "[疑似人机-1]";
                            logSb.append(type);
                        }

                        JSONObject levelInfo = cardJO.getJSONObject("level_info");
                        if (levelInfo != null) {
                            int currentLevel = levelInfo.getIntValue("current_level");  //

                            if (currentLevel == 0) {
                                score = score - 2;
                                type = "[Lv0 -2]";
                                logSb.append(type);
                            } else if (currentLevel <= 2 && (name.startsWith("bili_") || sex.equals("保密"))) {
                                score = score - 1;
                                type = "[Lv" + currentLevel + " -1]";
                                logSb.append(type);
                            } else if (currentLevel <= 4) {
                                logSb.append(" [Lv").append(currentLevel).append("]");
                            } else if (currentLevel <= 6) {
                                score = score + 1;
                                type = "[Lv" + currentLevel + " +1]";
                                logSb.append(type);
                            }
                        }

                        JSONObject official = cardJO.getJSONObject("Official");
                        if (official != null) {
                            String officialTitle = official.getString("title");  //

                            if (!officialTitle.isEmpty()) { //认证用户
                                score += 1;
                                type = "[认证+1]";
                                logSb.append("[").append(officialTitle).append("]").append(type);
                            }
                        }

                        JSONObject vip = cardJO.getJSONObject("vip");
                        if (vip != null) {
                            JSONObject label = vip.getJSONObject("label");
                            if (label != null) {
                                String vipLabelText = label.getString("text"); //

                                if (StringUtils.contains(vipLabelText, "大会员")) {
                                    score = score + 1;
                                    type = "[大会员+1]";
                                    logSb.append(type);
                                }
                            }
                        }
                        score += getKeyWordsScore(name + sign, logSb); // 姓名，签名
                        type = " [个人黑白分:" + score + "]";
                        // 实时陌生观众看板
                        xyz.acproject.danmuji.service.StrangerViewerService.addRecord(vmid, name, face, score,  sign  );

                        logSbEnd.insert(0, type);
                        SelfTools.appendAt(logSb, 270, logSbEnd.toString());
                        LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb));
                    }
                } else {
                    logSb.append("[用户信息解析异常]");
                }
            } else {
                // API异常 — CookiePool已经在asyncHttpGetUserCard中标记了当前Cookie冷却
                // 如果所有子账号都在冷却且没有可用账号，触发全局熔断
                if (cookiePool.getTotalAvailableCount() == 0
                        && schedulercardJOColdWait.compareAndSet(false, true)) {
                    schedulercardJOService.schedule(() -> {
                        schedulercardJOColdWait.set(false);
                        System.out.println("卡片API: 5分钟全局熔断已解除，当前时间：" + System.currentTimeMillis());
                    }, 5, TimeUnit.MINUTES);
                }
                LogFileTools.getlogFileTools().logTestFile(logSb + " 用户卡片信息API 调用超过频次，已标记Cookie冷却");
            }


            return Pair.of(score, type);
        });
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
     * 当用户在UI中修改账号池配置时调用。
     */
    public static void syncRateLimiterConfig(xyz.acproject.danmuji.conf.set.AccountPoolConf conf) {
        if (conf == null) return;
        double newDynamicRate = conf.getDynamicRate();
        double newCardRate = conf.getCardRate();
        if (newDynamicRate > 0 && Math.abs(newDynamicRate - dynamicRateLimiter.getPermitsPerSecond()) > 0.001) {
            dynamicRateLimiter = new TokenBucketRateLimiter(newDynamicRate, Math.max(newDynamicRate, 1.0));
            LOGGER.info("动态API限流器已更新: {} req/s", newDynamicRate);
        }
        if (newCardRate > 0 && Math.abs(newCardRate - cardRateLimiter.getPermitsPerSecond()) > 0.001) {
            cardRateLimiter = new TokenBucketRateLimiter(newCardRate, Math.max(newCardRate * 2, 2.0));
            LOGGER.info("卡片API限流器已更新: {} req/s", newCardRate);
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
