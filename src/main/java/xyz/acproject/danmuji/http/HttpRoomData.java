package xyz.acproject.danmuji.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.CollectionUtils;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.room_data.*;
import xyz.acproject.danmuji.entity.server_data.Conf;
import xyz.acproject.danmuji.entity.user_data.UserNav;
import xyz.acproject.danmuji.tools.CurrencyTools;
import xyz.acproject.danmuji.tools.file.FileTools;
import xyz.acproject.danmuji.tools.file.LogFileTools;
import xyz.acproject.danmuji.utils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author BanqiJane
 * @ClassName HttpRoomData
 * @Description TODO
 * @date 2020年8月10日 下午12:28:59
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class HttpRoomData {
    private static Logger LOGGER = LogManager.getLogger(HttpRoomData.class);
    private static Map<Long, Integer> pnScoreMap = loadNegativeBlackPositiveWhiteScores();

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
     * 处理用户关注列表：判断可见性，输出结果，如果可见且有关注数据则写入文件
     */
    public static void processFollowings(long vmid, String uname) {
        JSONObject firstPage = httpGetFollowings(vmid, 1, 50);
        StringBuilder logSb = new StringBuilder(100);

        logSb.append(new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()))
                .append(" https://space.bilibili.com/")
                .append(vmid)
                .append("/dynamic ")
                .append(uname);

        short code = firstPage != null ? firstPage.getShort("code") : -1;
        JSONObject data = firstPage != null && code == 0 ? firstPage.getJSONObject("data") : null;
        long total = data != null ? data.getLongValue("total") : -1;

        int totalScore = 0;
        int tempScore = 0;
        JSONArray followingsList = new JSONArray();
        JSONArray matchedList = new JSONArray();

        // 是否可见，是否在黑白名单
        if (firstPage == null || code != 0 || data == null || total == 0) {
           // LOGGER.info("[" + uname + "] 关注：请求失败或无数据");

            SelfTools.appendAt(logSb, 95, "[成分:不可见]");
        } else {

            // 当前观众就在黑白名单里
            if (pnScoreMap.containsKey(vmid)) {
                totalScore = pnScoreMap.get(vmid);
                matchedList.add(uname + ":" + totalScore);
            } else {
                JSONArray list = firstPage.getJSONObject("data").getJSONArray("list");

                // 判断与每个关注人的关系，并计算总分
                for (Object obj : list) {
                    JSONObject user = (JSONObject) obj;
                    long mid = user.getLong("mid");
                    String followedName = user.getString("uname");
                    followingsList.add(followedName + ":" + mid);
                    if (pnScoreMap.containsKey(mid)) { // 在黑白名单
                        tempScore = pnScoreMap.get(mid);
                        totalScore += tempScore;
                        matchedList.add(followedName + ":" + tempScore);
                    }
                }
            }

            logSb.append(" [分数:").append(totalScore).append("]");

            if (totalScore > 0) {
                SelfTools.appendAt(logSb, 110, "[成分:己方偏多]");
            } else if (totalScore < 0) {
                SelfTools.appendAt(logSb, 90, "[成分:野猪偏多]");
            } else {
                SelfTools.appendAt(logSb, 105, "[成分:需要确认]");
            }
            logSb.append(" [比例:").append(matchedList.size()).append("/").append(total) .append("]")
                    .append(",黑白名单:").append(matchedList.toJSONString())
                    .append(" 🍉🍉 关注列表:").append(followingsList.toJSONString());
        }

        //通过日志查看后手动打开浏览器
        LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb));
    }

    /**
     * 加载正白负黑表，返回 uid -> score 的映射
     */
    private static Map<Long, Integer> loadNegativeBlackPositiveWhiteScores() {
        Map<Long, Integer> map = new HashMap<>();
        try {
            FileTools fileTools = new FileTools();
            File file = new File(fileTools.getBaseJarPath(), "负黑正白判定表.json");
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

    /**
     * 处理用户粉丝列表：判断可见性，输出结果，如果可见且有粉丝数据则写入文件
     */
    public static long processFollowers(long vmid, String uname) {
        JSONObject firstPage = httpGetFollowers(vmid, 1, 50);
        JSONObject output = new JSONObject(true);
        output.put("print_time", JodaTimeUtils.getCurrentDateTimeString());
        output.put("viewer_uid", vmid);
        output.put("viewer_name", uname);

        if (firstPage == null) {
            LOGGER.info("[" + uname + "] 粉丝：请求失败");
            output.put("type", "粉丝：请求失败");
            LogFileTools.getlogFileTools().logFollowersFile(output.toJSONString());
            return -1;
        }
        short code = firstPage.getShort("code");
        if (code == 22115) {
            LOGGER.info("[" + uname + "] 粉丝：不可见");
            output.put("type", "粉丝：不可见");
            LogFileTools.getlogFileTools().logFollowersFile(output.toJSONString());
            return -1;
        }
        if (code != 0) {
            LOGGER.info("[" + uname + "] 粉丝：请求失败(" + firstPage.getString("message") + ")");
            output.put("type", "粉丝：请求失败");
            LogFileTools.getlogFileTools().logFollowersFile(output.toJSONString());
            return -1;
        }
        JSONObject data = firstPage.getJSONObject("data");
        if (data == null) {
            LOGGER.info("[" + uname + "] 粉丝：0");
            output.put("type", "粉丝：0");
            LogFileTools.getlogFileTools().logFollowersFile(output.toJSONString());
            return 0;
        }
        long total = data.getLongValue("total");
        LOGGER.info("[" + uname + "] 粉丝：" + total);
        if (total == 0) {
            output.put("followings", "[" + uname + "] 粉丝：0 total");
            output.put("type", "粉丝：0");
            LogFileTools.getlogFileTools().logFollowersFile(output.toJSONString());
            return 0;
        }


        output.put("type", "followers");
        output.put("total", total);

        JSONArray followersList = new JSONArray();
        int totalPages = (int) Math.ceil((float) total / 50F);
        totalPages = Math.min(totalPages, 5);
        for (int p = 1; p <= totalPages; p++) {
            JSONObject pageData;
            if (p == 1) {
                pageData = firstPage;
            } else {
                pageData = httpGetFollowers(vmid, p, 50);
                if (pageData == null || pageData.getShort("code") != 0) {
                    continue;
                }
            }
            JSONArray list = pageData.getJSONObject("data").getJSONArray("list");
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (Object obj : list) {
                JSONObject user = (JSONObject) obj;
                JSONObject item = new JSONObject(true);
                item.put("uid", user.getLong("mid"));
                item.put("name", user.getString("uname"));
                followersList.add(item);
            }
        }
        output.put("followers_list", followersList);
        LogFileTools.getlogFileTools().logFollowersFile(output.toJSONString());
        return total;
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
                        e.printStackTrace();
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
