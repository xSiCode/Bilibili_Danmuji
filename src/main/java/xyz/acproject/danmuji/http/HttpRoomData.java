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


    /**
     * 处理用户关注列表：判断可见性，输出结果，如果可见且有关注数据则写入文件
     */
    public static Pair<Integer, String> processFollowings(long vmid, String uname) {
        /**
         * 步骤：
         * 1. 获取用户卡片信息
         * 2. 获取用户关注列表
         */

        long total = 0;
        int tempScore = 0;
        int blackWhiteScore = 0;
        String blackWhiteType = null;

        long fans = -1;
        long attention = -1;
        long archiveCount = -1;
        long likeNum = -1;
        boolean following = false;
        int currentLevel = -1;

        StringBuilder logSb = new StringBuilder(100);
        logSb.append(new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()))
                .append(" ")
                .append("https://space.bilibili.com/")
                .append(vmid)
                .append(" [")
                .append(uname)
                .append("] ");

        //用户卡片信息 判断
        JSONObject dataX = httpGetUserCard(vmid);
        if (dataX != null && dataX.getShort("code") == 0) {
            JSONObject dataCard = dataX.getJSONObject("data");

            if (dataCard != null) {
                //数据获取
                JSONObject cardInfo = dataCard.getJSONObject("card");

                fans = cardInfo != null ? cardInfo.getLongValue("fans") : -2;
                attention = cardInfo != null ? cardInfo.getLongValue("attention") : -2;

                if (cardInfo != null) {
                    JSONObject levelInfo = cardInfo.getJSONObject("level_info");
                    if (levelInfo != null) {
                        currentLevel = levelInfo.getIntValue("current_level");
                    }
                }

                following = dataCard.getBooleanValue("following");
                archiveCount = dataCard.getLongValue("archive_count");
                likeNum = dataCard.getLongValue("like_num");

                // 条件判断
                if (following) {  // 哔哩哔哩 已关注
                    blackWhiteScore = 2;
                    blackWhiteType = "[已关注]";
                    logSb.append(blackWhiteType);
                } else if (fans < 50 && attention > 4000) {
                    //疑似人机，拉黑处理
                    blackWhiteScore = -2;
                    blackWhiteType = "[拉黑:疑似人机]";
                    logSb.append(blackWhiteType);
                } else if (currentLevel < 2) {
                    blackWhiteScore = -2;
                    blackWhiteType = "[拉黑:LV:" + currentLevel + "]";
                    logSb.append(blackWhiteType);
                }

                if (fans > 1000 || archiveCount > 50 || likeNum > 10_000) {
                    //粉丝数高，投稿高，获赞高
                    logSb.append(" [KOL:").append(fans / 1000 + archiveCount / 100 + likeNum / 10_000).append("]");
                }

                logSb.append(" [LV:").append(currentLevel)
                        .append("] [投稿:").append(archiveCount)
                        .append("] [关注:").append(attention)
                        .append("] [粉丝:").append(fans)
                        .append("] [获赞:").append(likeNum);
            } else {
                logSb.append("[error 用户card异常]");
            }
        } else {
            logSb.append("[error api返回异常]");
        }

        // 当前观众就在本地黑白名单里
        if (pnScoreMap.containsKey(vmid)) {
            blackWhiteScore = pnScoreMap.get(vmid);
            blackWhiteType = "[已在黑白名单:"+blackWhiteScore+"]";
            logSb.append(blackWhiteType);
        }

        if (blackWhiteScore != 0) { // 说明已经经过了判断
            LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb.append("[已处理]")));

            return Pair.of(blackWhiteScore, blackWhiteType);
        }

        // 用户关注数判断
        JSONObject firstPage = httpGetFollowings(vmid, 1, 50);
        short code = firstPage != null ? firstPage.getShort("code") : -1;
        JSONObject data = firstPage != null && code == 0 ? firstPage.getJSONObject("data") : null;
        total = data != null ? data.getLongValue("total") : 0;

        JSONArray followingsList = new JSONArray();
        JSONArray matchedList = new JSONArray();

        // 关注列表不可见
        if (firstPage == null || code != 0 || data == null || total == 0) {
            // 通过空间动态判断
            String dynData = httpGetUserDynamic(vmid);

            if (dynData.length() < 168) {
                //关注不可见，且没有动态，直接拉黑     无法查看返回的字符串是84或122长度 。 内容：{"code":0,"message":"OK","ttl":1,"data":{"has_more":0,"cards":null,"next_offset":0}
                blackWhiteScore = -2;
                blackWhiteType = "[拉黑:关注不可见且没有动态]";
                logSb.append(blackWhiteType);
            } else if (dynData.length() > 1450) {  // 至少有个动态的字符长度大约是1723字符
                // 动态判断：使用黑名单姬的自定义屏蔽名字，包含匹配，匹配母串为dynData
                if (PublicDataConf.centerSetConf.getBlack() != null) {
                    for (String s : PublicDataConf.centerSetConf.getBlack().getNames()) {
                        if (StringUtils.isBlank(s)) continue;
                        if (StringUtils.contains(dynData, s)) {
                            blackWhiteScore = -2;
                            blackWhiteType = "[拉黑:关注不可见且动态含违禁词:" + s + "]";
                            logSb.append(blackWhiteType);
                            break;
                        }
                    }
                }
            } else {
                logSb.append(" [error:不应该出现本记录，需要修复]");
            }

            if (blackWhiteScore != 0) { // 说明已经经过了判断
                LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb.append("[已处理]")) );

                return Pair.of(blackWhiteScore, blackWhiteType);
            }

            SelfTools.appendAt(logSb, 110, "[成分:关注不可见]");
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
                    blackWhiteScore += tempScore;
                    matchedList.add(followedName + ":" + tempScore);
                }
            }

            if (blackWhiteScore > 0) {
                SelfTools.appendAt(logSb, 120, "[成分:己方偏多]");
            } else if (blackWhiteScore < 0) {
                blackWhiteType = "[拉黑:野猪偏多]";
            } else {
                // 关注列表分析正常后，再通过空间动态判断， 只判断是否有违禁词
                String dynData = httpGetUserDynamic(vmid);

                // 动态判断：使用黑名单姬的自定义屏蔽名字，包含匹配，匹配母串为dynData
                if (PublicDataConf.centerSetConf.getBlack() != null) {
                    for (String s : PublicDataConf.centerSetConf.getBlack().getNames()) {
                        if (StringUtils.isBlank(s)) continue;
                        if (StringUtils.contains(dynData, s)) {
                            blackWhiteScore = -2;
                            blackWhiteType = "[拉黑:关注正常，但动态含违禁词:" + s + "]";
                            logSb.append(blackWhiteType);
                            break;
                        }
                    }
                }

                if (blackWhiteScore == 0){
                    SelfTools.appendAt(logSb, 110, "[成分:需要手动确认]");
                }
            }

            if (blackWhiteScore < 0) { // 说明已经经过了判断
                LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb.append("[已处理]")) );

                return Pair.of(blackWhiteScore, blackWhiteType);
            }

            // 日志打印
            logSb.append("] [黑白分:").append(blackWhiteScore)
                    .append("] [匹配数:").append(matchedList.size())
                    .append("] 黑白名单:").append(matchedList.toJSONString())
                    .append(" 🍉🍉 关注列表:").append(followingsList.toJSONString());
        }

        //通过日志查看后手动打开浏览器
        LogFileTools.getlogFileTools().logFollowingsFile(String.valueOf(logSb));

        return  Pair.of(blackWhiteScore, blackWhiteType);
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
