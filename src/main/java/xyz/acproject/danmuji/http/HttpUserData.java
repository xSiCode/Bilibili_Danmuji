package xyz.acproject.danmuji.http;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.Headers;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.login_data.LoginData;
import xyz.acproject.danmuji.entity.login_data.Qrcode;
import xyz.acproject.danmuji.entity.user_data.*;
import xyz.acproject.danmuji.entity.user_in_room_barrageMsg.UserBarrageMsg;
import xyz.acproject.danmuji.tools.CurrencyTools;
import xyz.acproject.danmuji.tools.file.LogFileTools;
import xyz.acproject.danmuji.utils.JodaTimeUtils;
import xyz.acproject.danmuji.utils.OkHttp3Utils;
import xyz.acproject.danmuji.utils.UrlUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author BanqiJane
 * @ClassName HttpUserData
 * @Description TODO
 * @date 2020年8月10日 下午12:29:05
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class HttpUserData {
    private static Logger LOGGER = LogManager.getLogger(HttpUserData.class);
    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss"));

    /**
     * 初始化 获取用户信息+判断是否登陆状态
     */
    public static void httpGetUser() {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(2);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        try {
            data = OkHttp3Utils.getHttp3Utils().httpGet("https://account.bilibili.com/home/USERInfo", headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return;
        jsonObject = JSONObject.parseObject(data);
        short code = jsonObject.getShort("code");
        if (code == 0) {
            LOGGER.info("已经登录");
        } else if (code == -101) {
            LOGGER.info("未登录");
        } else {
            LOGGER.error("未知错误,原因:{}", jsonObject.getString("message"));
        }
    }


    public static UserNav httpGetUserNav() {
        UserNav userNav = null;
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(2);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils().httpGet("https://api.bilibili.com/x/web-interface/nav", headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;
        jsonObject = JSONObject.parseObject(data);
        short code = jsonObject.getShort("code");
        if (code == 0) {
            userNav = JSONObject.parseObject(jsonObject.getString("data"), UserNav.class);
            // 保存WBI签名密钥
            if (userNav != null && userNav.getWbiImg() != null) {
                String imgUrl = userNav.getWbiImg().getImgUrl();
                String subUrl = userNav.getWbiImg().getSubUrl();
                if (StringUtils.isNotBlank(imgUrl)) {
                    String[] parts = imgUrl.split("/");
                    String filename = parts[parts.length - 1];
                    PublicDataConf.WBI_IMG_KEY = filename.substring(0, filename.lastIndexOf('.'));
                }
                if (StringUtils.isNotBlank(subUrl)) {
                    String[] parts = subUrl.split("/");
                    String filename = parts[parts.length - 1];
                    PublicDataConf.WBI_SUB_KEY = filename.substring(0, filename.lastIndexOf('.'));
                }
            }
        } else {
            LOGGER.error("获取用户nav信息失败,原因:{}", jsonObject.getString("message"));
        }
        return userNav;
    }


    /**
     * 获取登陆二维码 旧版本 已废弃
     *
     * @return
     */
    @Deprecated
    public static Qrcode httpGetQrcode() {
        String data = null;
        JSONObject jsonObject = null;
        Qrcode qrcode = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(2);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://passport.bilibili.com/qrcode/getLoginUrl", headers, null).body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return qrcode;
        jsonObject = JSONObject.parseObject(data);
        short code = jsonObject.getShort("code");
        if (code == 0) {
            qrcode = JSONObject.parseObject(jsonObject.getString("data"), Qrcode.class);
        } else {
            LOGGER.error("获取二维码失败,原因:{}", jsonObject.getString("message"));
        }
        return qrcode;
    }

    public static Qrcode httpGenerateQrcode() {
        String data = null;
        JSONObject jsonObject = null;
        Qrcode qrcode = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("Referer", "https://www.bilibili.com/");
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header", headers, null).body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return qrcode;
        jsonObject = JSONObject.parseObject(data);
        short code = jsonObject.getShort("code");
        if (code == 0) {
            qrcode = JSONObject.parseObject(jsonObject.getString("data"), Qrcode.class);
        } else {
            LOGGER.error("获取二维码失败,原因:{}", jsonObject.getString("message"));
        }
        return qrcode;
    }

    /**
     * 判断扫码状态 扫码确定后 获取用户cookie 旧版本 已废弃
     *
     * @param logindata
     * @return
     */
    @Deprecated
    public static String httpPostCookie(LoginData logindata) {
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            return "";
        }
        String data = null;
        Response response = null;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://passport.bilibili.com/login");
        params = new HashMap<>(3);
        params.put("oauthKey", logindata.getOauthKey());
        params.put("gourl", logindata.getGourl());
        try {
            response = OkHttp3Utils.getHttp3Utils().httpPostForm("https://passport.bilibili.com/qrcode/getLoginInfo",
                    headers, params);
            data = response.body().string();
            if (JSONObject.parseObject(data).getBoolean("status")) {
                Headers headers2 = response.headers();
                List<String> cookies = headers2.values("Set-Cookie");
                Set<String> cookieSet = new HashSet<>();
                for (String string : cookies) {
                    cookieSet.add(string.substring(0, string.indexOf(";")));
                }
                StringBuilder stringBuilder = new StringBuilder(100);
                Iterator<String> iterable = cookieSet.iterator();
                while (iterable.hasNext()) {
                    stringBuilder.append(iterable.next());
                    if (iterable.hasNext()) {
                        stringBuilder.append(";");
                    }
                }
                PublicDataConf.USERCOOKIE = stringBuilder.toString();
                if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
                    //处理token
                    CurrencyTools.parseCookie(PublicDataConf.USERCOOKIE);
                    if (PublicDataConf.ROOMID != null) {
                        httpGetUserBarrageMsg();
                    }
                    LOGGER.info("扫码登录成功");
                }
            }
        } catch (Exception e1) {
            // TODO 自动生成的 catch 块
            LOGGER.error("扫码登录失败抛出异常:" + e1);
        }

        return data;
    }

    /**
     * HTTP 二维码轮询
     * 86101 未扫
     * 86090 扫了未确认
     *
     * @param key 钥匙
     * @return {@link String}
     */
    public static String httpQrcodePoll(String key) {
        String data = null;
        JSONObject jsonObject = null;
        Response response = null;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("Referer", "https://www.bilibili.com/");
        params = new HashMap<>(3);
        params.put("qrcode_key", key);
        params.put("source", "main-fe-header");
        try {
            response = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://passport.bilibili.com/x/passport-login/web/qrcode/poll", headers, params);
            data = response.body().string();
            if (JSONObject.parseObject(data).getJSONObject("data").getIntValue("code") == 0) {
                Headers headers2 = response.headers();
                List<String> cookies = headers2.values("Set-Cookie");
                Set<String> cookieSet = new HashSet<>();
                for (String string : cookies) {
                    cookieSet.add(string.substring(0, string.indexOf(";")));
                }
                StringBuilder stringBuilder = new StringBuilder(100);
                Iterator<String> iterable = cookieSet.iterator();
                while (iterable.hasNext()) {
                    stringBuilder.append(iterable.next());
                    if (iterable.hasNext()) {
                        stringBuilder.append(";");
                    }
                }
                PublicDataConf.USERCOOKIE = stringBuilder.toString();
                if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
                    //处理token
                    CurrencyTools.parseCookie(PublicDataConf.USERCOOKIE);
                    //房间号非空则去获取用户弹幕长度
                    if (PublicDataConf.ROOMID != null) {
                        httpGetUserBarrageMsg();
                    }
                    LOGGER.info("扫码登录成功");
                }
            }
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        return data;
    }


    public static UserCookie httpBuvid34(UserCookie userCookie) {
        if (userCookie == null) {
            userCookie = new UserCookie();
        }
        String data = null;
        JSONObject jsonObject = null;
        Qrcode qrcode = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("Referer", "https://www.bilibili.com/");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/frontend/finger/spi", headers, null).body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return userCookie;
        jsonObject = JSONObject.parseObject(data);
        short code = jsonObject.getShort("code");
        if (code == 0) {
            userCookie.setBuvid3(jsonObject.getJSONObject("data").getString("b_3"));
            userCookie.setBuvid4(jsonObject.getJSONObject("data").getString("b_4"));
        } else {
            LOGGER.error("获取buvid34失败,原因:{}", jsonObject.getString("message"));
        }
        return userCookie;
    }

    /**
     * 单点登录系统获取
     */
    public static List<String> httpPostSsoList() {
        String data = null;
        JSONObject jsonObject = null;
        List<String> ssoList = new ArrayList<>();
        Map<String, String> headers = null;
        headers = new HashMap<>(3);
        Map<String, String> params = null;
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        headers.put("Referer", "https://www.bilibili.com/");
        params = new HashMap<>(2);
        params.put("csrf", "");
        try {
            data = OkHttp3Utils.getHttp3Utils().httpPostForm("https://passport.bilibili.com/x/passport-login/web/sso/list", headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return ssoList;
        jsonObject = JSONObject.parseObject(data);
        JSONObject dataObject = jsonObject.getJSONObject("data");
        List<String> ssos = JSONArray.parseArray(dataObject.getString("sso"), String.class);
        return ssos != null ? ssos : ssoList;
    }

    /**
     * 获取用户信息 需要cookie 初始化
     */
    public static void httpGetUserInfo() {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils().httpGet("https://api.live.bilibili.com/User/getUserInfo", headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return;
        jsonObject = JSONObject.parseObject(data);
        if (jsonObject.getString("code").equals("REPONSE_OK")) {
            PublicDataConf.USER = new User();
            PublicDataConf.USER = JSONObject.parseObject(jsonObject.getString("data"), User.class);
            LOGGER.info("已经登录，获取信息成功");
        } else if (jsonObject.getShort("code") == -500) {
            LOGGER.info("未登录，请登录,原因:{}", jsonObject.getString("message"));
            PublicDataConf.USERCOOKIE = null;
            PublicDataConf.USER = null;
        } else {
            LOGGER.error("未知错误,原因:{}", jsonObject.getString("message"));
            PublicDataConf.USERCOOKIE = null;
            PublicDataConf.USER = null;
        }
    }

    /**
     * 获取用户在目标房间所能发送弹幕的最大长度
     */
    public static void httpGetUserBarrageMsg() {
        if (CurrencyTools.parseRoomId() == 0) return;
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByUser?room_id="
                            + CurrencyTools.parseRoomId(), headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return;
        jsonObject = JSONObject.parseObject(data);
        short code = jsonObject.getShort("code");
        if (code == 0) {
            LOGGER.info("获取本房间可发送弹幕长度+是否是管理员 成功");
            PublicDataConf.USERBARRAGEMESSAGE = JSONObject
                    .parseObject((((JSONObject) jsonObject.get("data")).getString("property")), UserBarrageMsg.class);


        } else if (code == -101) {
            LOGGER.info("未登录，请登录");
        } else if (code == -400) {
            LOGGER.info("房间号不存在或者未输入房间号");
        } else {
            LOGGER.error("未知错误,原因:{}", jsonObject.getString("message"));
        }
    }

    /**
     * 获取用户在目标房间所能发送弹幕的最大长度
     */
    public static UserBarrageMsg httpGetUserBarrageMsg(Long roomId) {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + roomId);
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByUser?room_id="
                            + roomId, headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return null;
        jsonObject = JSONObject.parseObject(data);
        short code = jsonObject.getShort("code");
        if (code == 0) {
            LOGGER.info("获取本房间可发送弹幕长度成功");
            UserBarrageMsg barrageMsg = JSONObject
                    .parseObject((((JSONObject) jsonObject.get("data")).getString("property")), UserBarrageMsg.class);
            return barrageMsg;
        } else if (code == -101) {
            LOGGER.info("未登录，请登录");
        } else if (code == -400) {
            LOGGER.info("房间号不存在或者未输入房间号");
        } else {
            LOGGER.error("未知错误,原因:{}", jsonObject.getString("message"));
        }
        return null;
    }

    /**
     * 发送弹幕
     *
     * @param msg 弹幕信息
     * @return
     */
    public static Short httpPostSendBarrage(String msg) {
        JSONObject jsonObject = null;
        String data = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        if (PublicDataConf.USERBARRAGEMESSAGE == null || PublicDataConf.COOKIE == null)
            return code;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        if (StringUtils.isBlank(msg)) {
            LOGGER.error("发送弹幕失败,原因:弹幕非空");
            return -400;
        }
        params = new HashMap<>(10);
        params.put("color", PublicDataConf.USERBARRAGEMESSAGE.getDanmu().getColor().toString());
        params.put("fontsize", "25");
        params.put("mode", PublicDataConf.USERBARRAGEMESSAGE.getDanmu().getMode().toString());
        params.put("msg", UrlUtils.URLEncoderString(msg, "utf-8"));
        params.put("rnd", String.valueOf(System.currentTimeMillis()).substring(0, 10));
        params.put("roomid", PublicDataConf.ROOMID.toString());
        params.put("bubble", PublicDataConf.USERBARRAGEMESSAGE.getBubble().toString());
        params.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
        params.put("csrf", PublicDataConf.COOKIE.getBili_jct());
        try {
            data = OkHttp3Utils.getHttp3Utils().httpPostForm("https://api.live.bilibili.com/msg/send", headers, params)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return code;
        jsonObject = JSONObject.parseObject(data);
//		System.out.println(jsonObject.toJSONString().toString());
        if (jsonObject != null) {
            code = jsonObject.getShort("code");
            if (code == 0) {
                if (StringUtils.isBlank(jsonObject.getString("message").trim())) {
//				LOGGER.info("发送弹幕成功");
                } else if (jsonObject.getString("message").equals("msg in 1s")
                        || jsonObject.getString("message").equals("msg repeat")) {
                    LOGGER.info("发送弹幕失败，尝试重新发送" + jsonObject.getString("message"));
                    PublicDataConf.barrageString.offer(msg);
                } else {
                    String message = jsonObject.getString("message");
                    if ("f".equals(message) || "k".equals(message))
                        message = "触发破站关键字，请检查发送弹幕是否含有破站屏蔽词或者非法词汇";
                    LOGGER.error("发送弹幕失败,原因:" + message);
                    code = -402;
                }
            } else if (code == -111) {
                LOGGER.error("发送弹幕失败,原因:" + jsonObject.getString("message"));
            } else if (code == -500) {
                LOGGER.error("发送弹幕失败,原因:" + jsonObject.getString("message"));
            } else if (code == 11000) {
                LOGGER.error("发送弹幕失败,原因:弹幕含有关键字或者弹幕颜色不存在:" + jsonObject.getString("message"));
            } else {
                LOGGER.error("发送弹幕失败,原因:{}", jsonObject.getString("message"));
            }
        } else {
            return code;
        }
        return code;
    }

    /**
     * 发送弹幕
     *
     * @param msg 弹幕信息
     * @return
     */
    public static Short httpPostSendBarrage(String msg, Long roomId) {
        JSONObject jsonObject = null;
        String data = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        UserBarrageMsg userBarrageMsg = httpGetUserBarrageMsg(roomId);
        if (userBarrageMsg == null || PublicDataConf.COOKIE == null)
            return code;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + roomId);
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(10);
        params.put("color", userBarrageMsg.getDanmu().getColor().toString());
        params.put("fontsize", "25");
        params.put("mode", userBarrageMsg.getDanmu().getMode().toString());
        params.put("msg", UrlUtils.URLEncoderString(msg, "utf-8"));
        params.put("rnd", String.valueOf(System.currentTimeMillis()).substring(0, 10));
        params.put("roomid", roomId.toString());
        params.put("bubble", userBarrageMsg.getBubble().toString());
        params.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
        params.put("csrf", PublicDataConf.COOKIE.getBili_jct());
        try {
            data = OkHttp3Utils.getHttp3Utils().httpPostForm("https://api.live.bilibili.com/msg/send", headers, params)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return code;
        jsonObject = JSONObject.parseObject(data);
//		System.out.println(jsonObject.toJSONString().toString());
        if (jsonObject != null) {
            code = jsonObject.getShort("code");
            if (code == 0) {
                if (StringUtils.isBlank(jsonObject.getString("message").trim())) {
//				LOGGER.info("发送弹幕成功");
                } else if (jsonObject.getString("message").equals("msg in 1s")
                        || jsonObject.getString("message").equals("msg repeat")) {
                    LOGGER.info("发送弹幕失败，尝试重新发送" + jsonObject.getString("message"));
                } else {
                    LOGGER.error("发送弹幕失败,原因:" + jsonObject.getString("message"));
                    code = -402;
                }
            } else if (code == -111) {
                LOGGER.error("发送弹幕失败,原因:" + jsonObject.getString("message"));
            } else if (code == -500) {
                LOGGER.error("发送弹幕失败,原因:" + jsonObject.getString("message"));
            } else if (code == 11000) {
                LOGGER.error("发送弹幕失败,原因:弹幕含有关键字或者弹幕颜色不存在:" + jsonObject.getString("message"));
            } else {
                LOGGER.error("发送弹幕失败,原因:{}", jsonObject.getString("message"));
            }
        } else {
            return code;
        }
        return code;
    }

    /**
     * 发送私聊
     *
     * @param recId 接受人uid
     * @param msg   信息
     * @return
     */
    public static Short httpPostSendMsg(long recId, String msg) {
        JSONObject jsonObject = null;
        String data = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        if (PublicDataConf.COOKIE == null)
            return code;
        if (PublicDataConf.USER.getUid() == recId)
            return code;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://message.bilibili.com/");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(16);
        params.put("msg[sender_uid]", PublicDataConf.USER.getUid().toString());
        params.put("msg[receiver_id]", String.valueOf(recId));
        params.put("msg[receiver_type]", "1");
        params.put("msg[msg_type]", "1");
        params.put("msg[msg_status]", "0");
        params.put("msg[content]", UrlUtils.URLEncoderString("{\"content\":\"" + msg + "\"}", "utf-8"));
        params.put("msg[timestamp]", String.valueOf(System.currentTimeMillis()).substring(0, 10));
        params.put("msg[new_face_version]", "1");
        params.put("msg[dev_id]", UUID.randomUUID().toString());
        params.put("from_firework", "0");
        params.put("build", "0");
        params.put("mobi_app", "web");
        params.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
        params.put("csrf", PublicDataConf.COOKIE.getBili_jct());
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpPostForm("https://api.vc.bilibili.com/web_im/v1/web_im/send_msg", headers, params).body()
                    .string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return code;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            // 发送私聊成功
        } else {
            LOGGER.error("发送私聊失败,原因:{}", jsonObject.getString("message"));
        }
        return code;
    }


    /**
     * 送礼
     *
     * @param userBag 用户包
     * @param ruid    ruid
     * @param roomid  roomid
     * @return {@link Short}
     */
    public static Short httpPostSendBag(UserBag userBag, long ruid, long roomid) {
        JSONObject jsonObject = null;
        String data = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        if (PublicDataConf.COOKIE == null)
            return code;
//		if (PublicDataConf.USER.getUid() == recId)
//			return code;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(17);
        params.put("uid", String.valueOf(PublicDataConf.USER.getUid()));
        params.put("gift_id", String.valueOf(userBag.getGift_id()));
        params.put("ruid", String.valueOf(ruid));
        params.put("send_ruid", "0");
        params.put("gift_num", String.valueOf(userBag.getGift_num()));
        params.put("bag_id", String.valueOf(userBag.getBag_id()));
        params.put("platform", "pc");
        params.put("biz_code", "Live");
        params.put("biz_id", String.valueOf(roomid));
        params.put("rnd", String.valueOf(JodaTimeUtils.getTimestamp()));
        params.put("metadata", "");
        params.put("price", "0");
        params.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
        params.put("csrf", PublicDataConf.COOKIE.getBili_jct());
        params.put("visit_id", "");
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpPostForm("https://api.live.bilibili.com/xlive/revenue/v1/gift/sendBag", headers, params).body()
                    .string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return code;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            // 发送私聊成功
            LOGGER.info("赠送礼物成功,赠送房间:{},赠送主播id:{},送出礼物:{},个数:{},亲密度:{}", roomid, ruid, userBag.getGift_name(), userBag.getGift_num(), userBag.getFeed() * userBag.getGift_num());
        } else {
            LOGGER.error("赠送礼物失败,原因:{}", jsonObject.getString("message"));
        }
//        LOGGER.info("赠送礼物成功,赠送房间:{},赠送主播:{},送出礼物:{},个数:{},亲密度:{}",roomid,ruid,userBag.getGift_name(),userBag.getGift_num(),userBag.getFeed()*userBag.getGift_num());
        return 1;
    }

    /**
     * 禁言/拉黑用户
     *
     * @param uid  被禁言人uid
     * @param hour 禁言时间 单位小时，-1为永久，0为本场结束
     * @return
     */
    public static Short httpPostAddBlock(long uid, short hour) {
        JSONObject jsonObject = null;
        String data = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        if (hour < -1) {
            hour = -1;
        }
        if (hour > 720) {
            hour = 720;
        }
        if (PublicDataConf.COOKIE == null)
            return code;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(7);
        params.put("room_id", PublicDataConf.ROOMID.toString());
        params.put("tuid", String.valueOf(uid));
        params.put("hour", String.valueOf(hour));
        params.put("mobile_app", "web");
        params.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
        params.put("csrf", PublicDataConf.COOKIE.getBili_jct());
        params.put("visit_id", "");
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpPostForm("https://api.live.bilibili.com/xlive/web-ucenter/v1/banned/AddSilentUser", headers,
                            params)
                    .body().string();
        } catch (Exception e) {
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return code;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            LOGGER.info("禁言成功: uid={}, hour={}", uid, hour);
        } else {
            LOGGER.error("禁言失败,原因:{}", jsonObject.getString("message"));
        }
        return code;
    }


    /**
     * 获取用户卡片信息（综合，用于观众记录输出）
     *
     * @param uid 用户uid
     * @return JSONObject 包含所有可获取的用户信息，api失败则返回null
     */
    public static JSONObject httpGetUserCardInfo(long uid) {
        JSONObject result = new JSONObject();
        Map<String, String> headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }

        // 2. 检查关注列表/粉丝列表是否可见（通过API请求判断）
        try {
            String followData = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/relation/followings?vmid=" + uid + "&pn=1&ps=1", headers, null)
                    .body().string();
            if (followData != null) {
                JSONObject foJo = JSONObject.parseObject(followData);
                result.put("follow_list_visible", foJo.getShort("code") == 0);


                // LOGGER.info( "https://api.bilibili.com/x/relation/followings?vmid=" + uid + "&pn=1&ps=1",foJo);
            }
        } catch (Exception e) {
            result.put("follow_list_visible", false);
        }

        // 3. 用户卡片接口（粉丝数、关注数）
        headers.put("referer", "https://space.bilibili.com/" + uid);
        try {
            String data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/web-interface/card?mid=" + uid, headers, null)
                    .body().string();
            if (data != null) {
                JSONObject jsonObject = JSONObject.parseObject(data);
                if (jsonObject.getShort("code") == 0) {
                    JSONObject cardData = jsonObject.getJSONObject("data");
                    JSONObject card = cardData.getJSONObject("card");
                    if (card != null) {
                        result.put("fans", card.getLong("fans"));
                        result.put("attention", card.getLong("attention"));
                        JSONObject levelInfo = card.getJSONObject("level_info");
                        if (levelInfo != null) {
                            result.put("current_level", levelInfo.getInteger("current_level"));
                        }
                    }
                    result.put("following", cardData.getBoolean("following"));
                    result.put("archive_count", cardData.getInteger("archive_count"));
                    result.put("article_count", cardData.getInteger("article_count"));
                }

                // LOGGER.info( "https://api.bilibili.com/x/web-interface/card?mid=" + uid,data);
                //  LOGGER.info( "https://api.bilibili.com/x/web-interface/card?mid=" + uid,jsonObject);
            }
        } catch (Exception e) {
            LOGGER.error("获取用户卡片信息失败:{}", e.getMessage());
        }

        // 1. 空间信息接口
        try {
            String data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/space/acc/info?mid=" + uid, headers, null)
                    .body().string();
            if (data != null) {
                JSONObject jsonObject = JSONObject.parseObject(data);
                if (jsonObject.getShort("code") == 0) {
                    JSONObject spaceData = jsonObject.getJSONObject("data");
                    result.put("name", spaceData.getString("name"));
                    result.put("sex", spaceData.getString("sex"));
                    result.put("sign", spaceData.getString("sign"));
                    result.put("level", spaceData.getInteger("level"));
                    // 认证信息
                    JSONObject official = spaceData.getJSONObject("official");
                    if (official != null) {
                        result.put("official_title", official.getString("title"));
                        result.put("official_type", official.getInteger("type"));
                    }
                    // 会员信息
                    JSONObject vip = spaceData.getJSONObject("vip");
                    if (vip != null) {
                        result.put("vip_status", vip.getInteger("status"));
                        result.put("vip_type", vip.getInteger("type"));
                        JSONObject label = vip.getJSONObject("label");
                        if (label != null) {
                            result.put("vip_label", label.getString("text"));
                        }
                    }
                    // 关注关系（登录用户与目标用户的关系）
                    JSONObject relation = spaceData.getJSONObject("relation");
                    if (relation != null) {
                        result.put("relation_status", relation.getInteger("status"));
                    }
                    // 直播间信息
                    JSONObject liveRoom = spaceData.getJSONObject("live_room");
                    if (liveRoom != null) {
                        result.put("live_room_id", liveRoom.get("roomid"));
                        result.put("live_status", liveRoom.getInteger("liveStatus"));
                    }

                    //   LOGGER.info( "https://api.bilibili.com/x/space/acc/info?mid=",data);
                    //   LOGGER.info( "https://api.bilibili.com/x/space/acc/info?mid=",jsonObject);
                }
            } else {
                LOGGER.error("获取用户空间信息失败");
            }
        } catch (Exception e) {
            LOGGER.error("获取用户空间信息失败:{}", e.getMessage());
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * 退出 删除cookie
     */
    public static void quit() {
        Map<String, String> headers = null;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://www.bilibili.com/");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            OkHttp3Utils.getHttp3Utils().httpGet("https://passport.bilibili.com/login?act=exit", headers, null);
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
        }
    }

    /**
     * 签到
     *
     * @return
     */
    public static void httpGetDoSign() {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = null;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://link.bilibili.com/p/center/index");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils().httpGet("https://api.live.bilibili.com/xlive/web-ucenter/v1/sign/DoSign", headers, null)
                    .body().string();
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return;
        jsonObject = JSONObject.parseObject(data);
        int code = jsonObject.getShort("code");
        if (code == 0) {
            LOGGER.info(((JSONObject) jsonObject.get("data")).getString("specialText"));
        } else if (code == 1011040) {
            LOGGER.info(jsonObject.get("message"));
        } else {
            LOGGER.error("签到失败，原因:{}", jsonObject.getString("message"));
        }
    }

    public static List<UserMedal> httpGetMedalList() {
        String data = null;
        JSONObject jsonObject = null;
        JSONArray jsonArray = null;
        List<UserMedal> userMedals = new ArrayList<>();
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(3);
        try {
            int nowPage = 1;
            while (true) {
                params.put("page", String.valueOf(nowPage));
                params.put("page_size", "10");
                data = OkHttp3Utils.getHttp3Utils()
                        .httpGet("https://api.live.bilibili.com/xlive/app-ucenter/v1/user/GetMyMedals", headers, params)
                        .body().string();
                if (data == null)
                    return null;
                jsonObject = JSONObject.parseObject(data);
                code = jsonObject.getShort("code");
                if (code == 0) {
                    int totalPage = jsonObject.getJSONObject("data").getJSONObject("page_info").getInteger("total_page");
                    if (totalPage != 0) {
                        jsonArray = jsonObject.getJSONObject("data").getJSONArray("items");
                        if (jsonArray != null) {
                            List<UserMedal> userMedalList = jsonArray.toJavaList(UserMedal.class);
                            userMedals.addAll(userMedalList);
                        }
                    }
                    if (nowPage == totalPage) {
                        break;
                    }
                } else {
                    LOGGER.error("获取勋章失败，原因:{}", jsonObject.getString("message"));
                    break;
                }
                nowPage++;
            }
        } catch (Exception e) {
            // TODO 自动生成的 catch 块
            LOGGER.error(e);
            data = null;
        }
        return userMedals;
    }

    //https://api.live.bilibili.com/xlive/web-room/v1/gift/bag_list

    public static List<UserBag> httpGetBagList(Long roomid) {
        String data = null;
        JSONObject jsonObject = null;
        JSONArray jsonArray = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(3);
        params.put("t", String.valueOf(JodaTimeUtils.getcurrMills()));
        params.put("room_id", String.valueOf(roomid));
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.live.bilibili.com/xlive/web-room/v1/gift/bag_list", headers, params)
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
            jsonArray = jsonObject.getJSONObject("data").getJSONArray("list");
            if (jsonArray != null) {
                List<UserBag> userBagList = jsonArray.toJavaList(UserBag.class);
                return userBagList;
            }
        } else {
            LOGGER.error("获取礼物包失败，原因:{}", jsonObject.getString("message"));
        }
        return null;
    }


    /**
     * 拉黑用户（加入黑名单）
     *
     * @param fid 被拉黑用户uid
     * @return
     */
    public static Short httpPostAddBadList(long fid) {
        JSONObject jsonObject = null;
        String data = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        if (PublicDataConf.COOKIE == null)
            return code;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://space.bilibili.com/");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(5);
        params.put("fid", String.valueOf(fid));
        params.put("act", "5");
        params.put("re_src", "11");
        params.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
        params.put("csrf", PublicDataConf.COOKIE.getBili_jct());
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpPostForm("https://api.bilibili.com/x/relation/modify", headers, params)
                    .body().string();
        } catch (Exception e) {
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return code;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            LOGGER.info("拉黑用户成功:{}", fid);
        } else {
            LOGGER.error("拉黑用户失败,原因:{}", jsonObject.getString("message"));
        }
        StringBuilder sb = new StringBuilder(100);
        sb.append(TIME_FORMAT.get().format(System.currentTimeMillis()))
                .append("  https://space.bilibili.com/")
                .append(fid)
                .append(" [auto black] api return: ").append(data);
        LogFileTools.getlogFileTools().logTestFile(sb.toString());


        return code;
    }

    /**
     * 取消拉黑用户（移出黑名单）
     *
     * @param fid 被拉黑用户uid
     * @return
     */
    public static Short httpPostDeleteBadList(long fid) {
        JSONObject jsonObject = null;
        String data = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        if (PublicDataConf.COOKIE == null)
            return code;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://space.bilibili.com/");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(5);
        params.put("fid", String.valueOf(fid));
        params.put("act", "6");
        params.put("re_src", "11");
        params.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
        params.put("csrf", PublicDataConf.COOKIE.getBili_jct());
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpPostForm("https://api.bilibili.com/x/relation/modify", headers, params)
                    .body().string();
        } catch (Exception e) {
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return code;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            LOGGER.info("取消拉黑用户成功:{}", fid);
        } else {
            LOGGER.error("取消拉黑用户失败,原因:{}", jsonObject.getString("message"));
        }
        return code;
    }

    /**
     * 获取B站拉黑列表(小黑屋)
     *
     * @param pn 页码
     * @param ps 每页条数
     * @return JSONObject {total, list: [{mid, uname, face, sign, mtime}]}
     */
    public static JSONObject httpGetBiliBadList(int pn, int ps) {
        JSONObject result = new JSONObject();
        result.put("total", 0);
        result.put("list", new JSONArray());
        if (PublicDataConf.COOKIE == null) {
            result.put("error", "未登录，请先登录B站账号");
            return result;
        }
        Map<String, String> headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://space.bilibili.com/");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        Map<String, String> params = new HashMap<>(2);
        params.put("pn", String.valueOf(pn));
        params.put("ps", String.valueOf(ps));
        try {
            String data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/relation/blacks", headers, params)
                    .body().string();
            JSONObject jsonObject = JSONObject.parseObject(data);
            short code = jsonObject.getShort("code");
            if (code == 0) {
                JSONObject dataObj = jsonObject.getJSONObject("data");
                result.put("total", dataObj.getInteger("total"));
                JSONArray list = dataObj.getJSONArray("list");
                JSONArray simplified = new JSONArray();
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        JSONObject user = list.getJSONObject(i);
                        JSONObject item = new JSONObject();
                        item.put("mid", user.getLong("mid"));
                        item.put("uname", user.getString("uname"));
                        item.put("face", user.getString("face"));
                        item.put("sign", user.getString("sign"));
                        item.put("mtime", user.getLong("mtime"));
                        simplified.add(item);
                    }
                }
                result.put("list", simplified);
            } else {
                result.put("error", jsonObject.getString("message"));
                LOGGER.error("获取B站拉黑列表失败:{}", jsonObject.getString("message"));
            }
        } catch (Exception e) {
            LOGGER.error("获取B站拉黑列表异常", e);
            result.put("error", "网络异常");
        }
        return result;
    }

    /**
     * 解除禁言
     *
     * @param uid 被禁言用户uid
     * @return
     */
    public static Short httpPostDeleteBlock(long uid) {
        JSONObject jsonObject = null;
        String data = null;
        short code = -1;
        Map<String, String> headers = null;
        Map<String, String> params = null;
        if (PublicDataConf.COOKIE == null)
            return code;
        headers = new HashMap<>(4);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://live.bilibili.com/" + CurrencyTools.parseRoomId());
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        params = new HashMap<>(5);
        params.put("room_id", PublicDataConf.ROOMID.toString());
        params.put("tuid", String.valueOf(uid));
        params.put("csrf_token", PublicDataConf.COOKIE.getBili_jct());
        params.put("csrf", PublicDataConf.COOKIE.getBili_jct());
        params.put("visit_id", "");
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpPostForm("https://api.live.bilibili.com/xlive/web-ucenter/v1/banned/DelSilentUser", headers,
                            params)
                    .body().string();
        } catch (Exception e) {
            LOGGER.error(e);
            data = null;
        }
        if (data == null)
            return code;
        jsonObject = JSONObject.parseObject(data);
        code = jsonObject.getShort("code");
        if (code == 0) {
            LOGGER.info("解除禁言成功: uid={}", uid);
        } else {
            LOGGER.error("解除禁言失败,原因:{}", jsonObject.getString("message"));
        }
        return code;
    }

    /**
     * 根据UID获取用户名
     *
     * @param uid 用户uid
     * @return 用户名，失败返回null
     */
    public static String httpGetUserNameByUid(long uid) {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try { //     过期，很容易请求频繁 https://api.bilibili.com/x/space/acc/info?mid=
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/space/acc/info?mid=" + uid, headers, null)
                    .body().string();
        } catch (Exception e) {
            LOGGER.error("获取用户名失败:{}", e.getMessage());
            return null;
        }
        if (data == null) return null;
        jsonObject = JSONObject.parseObject(data);
        if (jsonObject.getShort("code") == 0) {

            return jsonObject.getJSONObject("data").getString("name");
        }
        return null;
    }

    /**
     * 根据用户名搜索最多前3位用户，按粉丝数降序排列，补充card和upstat数据
     *
     * @param name 用户名
     * @return JSONArray 包含最多3个用户信息 {uid, uname, face, fans, attention, likes, play_count}
     */
    public static JSONArray httpSearchUserByName(String name) {
        String data = null;
        JSONObject jsonObject = null;
        Map<String, String> headers = new HashMap<>(3);
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
        headers.put("referer", "https://www.bilibili.com/");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            headers.put("cookie", PublicDataConf.USERCOOKIE);
        }
        try {
            data = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/web-interface/search/type?keyword="
                            + UrlUtils.URLEncoderString(name, "utf-8") + "&search_type=bili_user&page=1", headers, null)
                    .body().string();
        } catch (Exception e) {
            LOGGER.error("搜索用户失败:{}", e.getMessage());
            return null;
        }
        if (data == null) return null;
        jsonObject = JSONObject.parseObject(data);
        if (jsonObject.getShort("code") != 0) return null;
        JSONArray result = jsonObject.getJSONObject("data").getJSONArray("result");
        if (result == null || result.isEmpty()) return null;

        // 按粉丝数降序排列
        List<JSONObject> sorted = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            sorted.add(result.getJSONObject(i));
        }
        sorted.sort((a, b) -> {
            long fa = a.getLong("fans") != null ? a.getLong("fans") : 0;
            long fb = b.getLong("fans") != null ? b.getLong("fans") : 0;
            return Long.compare(fb, fa);
        });

        // 取前3
        int limit = Math.min(3, sorted.size());
        JSONArray retList = new JSONArray();
        for (int i = 0; i < limit; i++) {
            JSONObject item = sorted.get(i);
            long mid = item.getLong("mid");
            JSONObject enriched = new JSONObject();
            enriched.put("uid", mid);
            enriched.put("uname", item.getString("uname"));
            enriched.put("face", item.getString("upic"));
            enriched.put("fans", item.getLong("fans") != null ? item.getLong("fans") : 0);

            // 获取关注数和播放/获赞数
            enrichUserStats(mid, enriched, headers);
            retList.add(enriched);
        }
        return retList;
    }

    private static void enrichUserStats(long mid, JSONObject enriched, Map<String, String> headers) {
        // 获取card数据（关注数、作品数）
        try {
            String cardData = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/web-interface/card?mid=" + mid, headers, null)
                    .body().string();
            if (cardData != null) {
                JSONObject cardJson = JSONObject.parseObject(cardData);
                if (cardJson.getShort("code") == 0) {
                    JSONObject dataObj = cardJson.getJSONObject("data");
                    JSONObject card = dataObj.getJSONObject("card");
                    if (card != null) {
                        enriched.put("attention", card.getLong("attention") != null ? card.getLong("attention") : 0);
                    }
                    enriched.put("archive_count", dataObj.getInteger("archive_count") != null ? dataObj.getInteger("archive_count") : 0);
                }
            }
        } catch (Exception e) {
            enriched.put("attention", 0);
            enriched.put("archive_count", 0);
        }

        // 获取关注列表是否可见
        try {
            String followData = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/relation/followings?vmid=" + mid + "&pn=1&ps=1", headers, null)
                    .body().string();
            if (followData != null) {
                JSONObject foJo = JSONObject.parseObject(followData);
                enriched.put("follow_list_visible", foJo.getShort("code") == 0);
            }
        } catch (Exception e) {
            enriched.put("follow_list_visible", false);
        }

        // 获取upstat数据（获赞数、播放数）
        try {
            String upstatData = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.bilibili.com/x/space/upstat?mid=" + mid, headers, null)
                    .body().string();
            if (upstatData != null) {
                JSONObject upstatJson = JSONObject.parseObject(upstatData);
                if (upstatJson.getShort("code") == 0) {
                    JSONObject upstatDataObj = upstatJson.getJSONObject("data");
                    if (upstatDataObj != null) {
                        enriched.put("likes", upstatDataObj.getLong("likes") != null ? upstatDataObj.getLong("likes") : 0);
                        JSONObject archive = upstatDataObj.getJSONObject("archive");
                        enriched.put("play_count", archive != null && archive.getLong("view") != null ? archive.getLong("view") : 0);
                    }
                }
            }
        } catch (Exception e) {
            enriched.put("likes", 0);
            enriched.put("play_count", 0);
        }
        enriched.put("latest_video_date", 0);

        // 获取最新动态日期
        try {
            Map<String, String> dynHeaders = new HashMap<>(headers);
            dynHeaders.put("referer", "https://space.bilibili.com/" + mid);
            String dynData = OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/space_history?host_uid=" + mid + "&offset_dynamic_id=0&need_top=1", dynHeaders, null)
                    .body().string();
            if (dynData != null) {
                JSONObject dynJson = JSONObject.parseObject(dynData);
                if (dynJson.getShort("code") == 0) {
                    JSONArray cards = dynJson.getJSONObject("data").getJSONArray("cards");
                    if (cards != null && !cards.isEmpty()) {
                        JSONObject firstCard = cards.getJSONObject(0);
                        JSONObject desc = firstCard.getJSONObject("desc");
                        if (desc != null && desc.getLong("timestamp") != null) {
                            enriched.put("latest_dynamic_date", desc.getLong("timestamp"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取最新动态日期失败 mid={}: {}", mid, e.getMessage());
            enriched.put("latest_dynamic_date", 0);
        }
    }
}
