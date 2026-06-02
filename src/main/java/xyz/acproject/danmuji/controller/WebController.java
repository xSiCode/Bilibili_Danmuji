package xyz.acproject.danmuji.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.acproject.danmuji.component.ServerAddressComponent;
import xyz.acproject.danmuji.component.TaskRegisterComponent;
import xyz.acproject.danmuji.component.ThreadComponent;
import xyz.acproject.danmuji.conf.CenterSetConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.conf.set.*;
import xyz.acproject.danmuji.entity.base.Response;
import xyz.acproject.danmuji.entity.login_data.LoginData;
import xyz.acproject.danmuji.entity.login_data.Qrcode;
import xyz.acproject.danmuji.entity.room_data.RoomInit;
import xyz.acproject.danmuji.entity.room_data.Room;
import xyz.acproject.danmuji.http.HttpRoomData;
import xyz.acproject.danmuji.http.HttpUserData;
import xyz.acproject.danmuji.service.ClientService;
import xyz.acproject.danmuji.service.DanmujiInitService;
import xyz.acproject.danmuji.service.SetService;
import xyz.acproject.danmuji.tools.CurrencyTools;
import xyz.acproject.danmuji.tools.ParseSetStatusTools;
import xyz.acproject.danmuji.tools.file.FileTools;
import xyz.acproject.danmuji.tools.file.FootprintFileTools;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.FileBatch;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.FootprintRecord;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.ParseResult;
import xyz.acproject.danmuji.tools.file.FootprintFileTools.SessionMeta;
import xyz.acproject.danmuji.tools.file.JsonFileTools;
import xyz.acproject.danmuji.utils.FastJsonUtils;
import xyz.acproject.danmuji.utils.QrcodeUtils;
import xyz.acproject.danmuji.utils.SpringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import xyz.acproject.danmuji.tools.BarrageLogTools;
import xyz.acproject.danmuji.tools.file.LogFileTools;
import xyz.acproject.danmuji.tools.FollowingCountTools;
import xyz.acproject.danmuji.tools.MatchCountTools;
import xyz.acproject.danmuji.tools.GiftLogTools;
import xyz.acproject.danmuji.tools.VisitorCountTools;
import xyz.acproject.danmuji.tools.RoomInfoLogTools;
import xyz.acproject.danmuji.utils.OkHttp3Utils;
import xyz.acproject.danmuji.thread.FootprintReplayThread;
import xyz.acproject.danmuji.thread.core.ParseMessageThread;

/**
 * @author BanqiJane
 * @ClassName WebController
 * @Description TODO
 * @date 2020年8月10日 下午12:21:50
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
@Controller
public class WebController {
    private SetService checkService;
    private ClientService clientService;
    @Resource
    private DanmujiInitService danmujiInitService;
    @Resource
    private ServerAddressComponent serverAddressComponent;
    private TaskRegisterComponent taskRegisterComponent;
    private static final Logger LOGGER = LogManager.getLogger(WebController.class);

    // 足迹还原：当前活跃的重放线程
    private volatile FootprintReplayThread activeReplayThread;

    // === 页面拆分：/ 和 /index 保留兼容，新增7个功能页面路由 ===
    @RequestMapping(value = {"/", "index"})
    public String index(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "index";
    }

    @RequestMapping(value = "/settings")
    public String settings(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "settings";
    }

    @RequestMapping(value = "/live-room")
    public String liveRoom(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "live-room";
    }

    @RequestMapping(value = "/audience")
    public String audience(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "audience";
    }

    @RequestMapping(value = "/danmaku")
    public String danmaku(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "danmaku";
    }

    @RequestMapping(value = "/blacklist")
    public String blacklist(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "blacklist";
    }

    @RequestMapping(value = "/dashboard")
    public String dashboard(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "dashboard";
    }

    @RequestMapping(value = "/management")
    public String management(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "management";
    }

    /** 注入所有页面共享的模型属性（状态栏等） */
    private void addCommonModelAttributes(HttpServletRequest req, Model model) {
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            if (req.getSession().getAttribute("status") == null) {
                req.getSession().setAttribute("status", "login");
            }
        }
        model.addAttribute("ANAME", PublicDataConf.ANCHOR_NAME);
        model.addAttribute("AUID", PublicDataConf.AUID);
        model.addAttribute("EDITION", PublicDataConf.VERSION);
        model.addAttribute("ROOMID", PublicDataConf.ROOMID);
        model.addAttribute("HROOMID", PublicDataConf.centerSetConf.getRoomid());
        model.addAttribute("POPU", PublicDataConf.ROOM_POPULARITY);
        model.addAttribute("ROOM_WATCHER", PublicDataConf.ROOM_WATCHER);
        model.addAttribute("LIVE_STATUS", PublicDataConf.lIVE_STATUS);
        model.addAttribute("ROOM_LIKE", PublicDataConf.ROOM_LIKE);
        model.addAttribute("ROOM_ONLINE", PublicDataConf.ROOM_ONLINE__RANK_COUNT);
        model.addAttribute("ROOM_TITLE", PublicDataConf.ROOM_TITLE);

        if (PublicDataConf.USER != null) {
            model.addAttribute("USER", PublicDataConf.USER);
        }

        model.addAttribute("SERVER_PORT", serverAddressComponent.getPort());
        model.addAttribute("ROOM_INFO", PublicDataConf.ROOM_INFO);
        model.addAttribute("BUILD_TIME", getBuildTime());
    }

    private String getBuildTime() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("build-info.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return props.getProperty("build.time", "");
            }
        } catch (Exception ignored) {}
        return "";
    }

    @RequestMapping(value = "/connect")
    public String connect(Model model) {
        model.addAttribute("ROOMID", PublicDataConf.centerSetConf.getRoomid());
        return "connect";
    }

    @RequestMapping(value = "/cookie_set")
    public String cookie_set(Model model) {
//        model.addAttribute("ROOMID", PublicDataConf.centerSetConf.getRoomid());
        return "cookie_set";
    }

    @RequestMapping(value = "/danmu_widget")
    public String danmu_widget(Model model) {
        return "danmu_widget";
    }

    @RequestMapping(value = "/account_pool")
    public String account_pool(HttpServletRequest req, Model model) {
        addCommonModelAttributes(req, model);
        return "account_pool";
    }

    @RequestMapping(value = "/obs_danmaku")
    public String obs_danmaku(Model model) {
        return "obs_danmaku";
    }

    // === Emoji panel: proxy Bilibili emoji API with 24h in-memory cache ===
    private volatile String cachedEmojiJson;
    private volatile long cachedEmojiTime;
    private static final long EMOJI_CACHE_TTL = 24 * 60 * 60 * 1000L;

    @ResponseBody
    @GetMapping(value = "/emoji_panel")
    public String emojiPanel(HttpServletRequest req) {
        // Return cached response if still valid (unless ?refresh=true)
        if (!"true".equals(req.getParameter("refresh"))
                && cachedEmojiJson != null
                && System.currentTimeMillis() - cachedEmojiTime < EMOJI_CACHE_TTL) {
            LOGGER.debug("emojiPanel: cache hit ({} chars, {}s old)",
                    cachedEmojiJson.length(), (System.currentTimeMillis() - cachedEmojiTime) / 1000);
            return cachedEmojiJson;
        }

        String replyBody = fetchEmojiApi("https://api.bilibili.com/x/emote/user/panel/web?business=reply");
        String pkgBody  = fetchEmojiApi("https://api.bilibili.com/x/emote/user/package/web?business=reply");

        String result;
        if (isValidEmojiResponse(replyBody) && isValidEmojiResponse(pkgBody)) {
            result = mergeEmojiResponses(replyBody, pkgBody);
            LOGGER.info("emojiPanel: merged reply+pkg, {} chars", result.length());
        } else if (isValidEmojiResponse(replyBody)) {
            result = replyBody;
            LOGGER.info("emojiPanel: reply only, {} chars", result.length());
        } else if (isValidEmojiResponse(pkgBody)) {
            result = pkgBody;
            LOGGER.info("emojiPanel: pkg only, {} chars", result.length());
        } else {
            LOGGER.warn("emojiPanel: all APIs failed, returning empty");
            return "{}";
        }

        cachedEmojiJson = result;
        cachedEmojiTime = System.currentTimeMillis();
        return result;
    }

    private String mergeEmojiResponses(String a, String b) {
        try {
            com.alibaba.fastjson.JSONObject ja = com.alibaba.fastjson.JSONObject.parseObject(a);
            com.alibaba.fastjson.JSONObject jb = com.alibaba.fastjson.JSONObject.parseObject(b);
            com.alibaba.fastjson.JSONArray pkgs = ja.getJSONObject("data").getJSONArray("packages");
            com.alibaba.fastjson.JSONArray extra = jb.getJSONObject("data").getJSONArray("packages");
            if (extra != null) for (int i = 0; i < extra.size(); i++) pkgs.add(extra.getJSONObject(i));
            return ja.toJSONString();
        } catch (Exception e) { LOGGER.error("emojiPanel merge error", e); return a; }
    }

    private String fetchEmojiApi(String url) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.setRequestProperty("Origin", "https://www.bilibili.com");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE))
                conn.setRequestProperty("Cookie", PublicDataConf.USERCOOKIE);
            int code = conn.getResponseCode();
            java.io.InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = "";
            if (is != null) { java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A"); body = s.hasNext() ? s.next() : ""; s.close(); }
            LOGGER.info("emojiPanel: HTTP {} from {} ({} chars)", code, url, body.length());
            if (code >= 200 && code < 300 && !body.isEmpty()) return body;
            LOGGER.warn("emojiPanel: failed/empty from {} (HTTP {})", url, code);
        } catch (Exception e) { LOGGER.error("emojiPanel: exception {} — {}", url, e.getMessage()); }
        finally { if (conn != null) conn.disconnect(); }
        return null;
    }

    private boolean isValidEmojiResponse(String body) {
        if (body == null || body.isEmpty()) return false;
        try {
            com.alibaba.fastjson.JSONObject j = com.alibaba.fastjson.JSONObject.parseObject(body);
            if (j.getInteger("code") != null && j.getInteger("code") == 0 && j.getJSONObject("data") != null) {
                com.alibaba.fastjson.JSONObject d = j.getJSONObject("data");
                if (d.getJSONArray("packages") != null || d.getJSONArray("all_packages") != null) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    @RequestMapping(value = "/login")
    public String login(HttpServletRequest req) {
        if (req.getSession().getAttribute("status") == null) {
            return "login";
        } else {
            return "redirect:/";
        }

    }

    @RequestMapping(value = "/quit")
    public String quit(HttpServletRequest req) {
        req.getSession().setAttribute("status", null);
        req.getSession().removeAttribute("status");
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            HttpUserData.quit();
            checkService.quit();
        }
        return "login";
    }

    @ResponseBody
    @GetMapping(value = "/qrcode")
    public void qrcode(HttpServletRequest req, HttpServletResponse resp, @RequestParam("url") String url) {
        if (req.getSession().getAttribute("status") != null)
            return;
        QrcodeUtils.creatRrCode(url, 140, 140, resp);
    }

    /** 账号池扫码专用 — 不检查主账号登录状态 */
    @ResponseBody
    @GetMapping(value = "/api/accountPool/qrcodeImage")
    public void accountPoolQrcodeImage(HttpServletRequest req, HttpServletResponse resp,
                                        @RequestParam("url") String url) {
        QrcodeUtils.creatRrCode(url, 180, 180, resp);
    }

    @ResponseBody
    @PostMapping(value = "/qrcodeUrl")
    public Response<?> qrcodeUrl(HttpServletRequest req) {
        if (req.getSession().getAttribute("status") != null)
            return null;
        Qrcode qrcode = HttpUserData.httpGenerateQrcode();
        req.getSession().setAttribute("auth", qrcode.getQrcode_key());
        return Response.success(qrcode.getUrl(), req);
    }

    @ResponseBody
    @PostMapping(value = "/loginCheck")
    public JSONObject loginCheck(HttpServletRequest req) {
        if (req.getSession().getAttribute("status") != null)
            return null;
        JSONObject jsonObject = null;
        String oauthKey = (String) req.getSession().getAttribute("auth");
        LoginData loginData = new LoginData();
        loginData.setOauthKey(oauthKey);
        String jsonString = HttpUserData.httpQrcodePoll(oauthKey);
        jsonObject = JSONObject.parseObject(jsonString);
        if (jsonObject != null) {
            if (jsonObject.getJSONObject("data").getIntValue("code")==0) {
                danmujiInitService.init();
//                checkService.init();
                if (PublicDataConf.USER != null) {
                    req.getSession().setAttribute("status", "login");
                }
            }
        }
        return jsonObject;
    }


    @ResponseBody
    @PostMapping(value = "/customCookie")
    public Response<?> customCookie(String cookie,HttpServletRequest req){
        boolean flag = CurrencyTools.parseCookie(cookie);
        if(flag){
            danmujiInitService.init();
            //弹幕长度刷新
            if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
                HttpUserData.httpGetUserBarrageMsg();
            }
        }
        return Response.success(flag,req);
    }

    @ResponseBody
    @GetMapping(value = "/connectRoom")
    public Response<?> connectRoom(HttpServletRequest req, @RequestParam("roomid") Long roomid) {
        boolean flag = false;
        if (null == PublicDataConf.webSocketProxy || !PublicDataConf.webSocketProxy.isOpen()) {
            try {
                clientService.startConnService(roomid);
            } catch (Exception e) {
                // TODO 自动生成的 catch 块
                LOGGER.error(e);
            }
            if (PublicDataConf.ROOMID != null) {
                PublicDataConf.centerSetConf.setRoomid(PublicDataConf.ROOMID);
                PublicDataConf.ROOMID_LONG = PublicDataConf.ROOMID;
            }
            checkService.connectSet(PublicDataConf.centerSetConf);
        }
        if (PublicDataConf.webSocketProxy != null) {
            if (PublicDataConf.webSocketProxy.isOpen()) {
                flag = true;
            }
        }
        return Response.success(flag, req);
    }

    @ResponseBody
    @GetMapping(value = "/disconnectRoom")
    public Response<?> disconnectRoom(HttpServletRequest req) {
        boolean flag = false;
        flag = clientService.closeConnService();
        return Response.success(flag, req);
    }

    @ResponseBody
    @GetMapping(value = "/connectCheck")
    public Response<?> connectCheck(HttpServletRequest req) {
        boolean flag = false;
        if (PublicDataConf.webSocketProxy != null) {
            if (PublicDataConf.webSocketProxy.isOpen()) {
                flag = true;
            }
        }
        return Response.success(flag, req);
    }

    // === 获取单个直播间状态（直播状态+在线人数）===
    @ResponseBody
    @GetMapping(value = "/getRoomStatus")
    public Response<?> getRoomStatus(@RequestParam("roomid") Long roomid, HttpServletRequest req) {
        long startTime = System.currentTimeMillis();
        LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] 请求开始 roomid=" + roomid);
        JSONObject data = new JSONObject();
        try {
            RoomInit roomInit = HttpRoomData.httpGetRoomInit(roomid);
            long realRoomId = roomid;
            if (roomInit != null) {
                realRoomId = roomInit.getRoom_id() != 0 ? roomInit.getRoom_id() : roomid;
                data.put("liveStatus", roomInit.getLive_status());
                LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] room_init 成功 roomid=" + roomid + " realRoomId=" + realRoomId + " live_status=" + roomInit.getLive_status() + " uid=" + roomInit.getUid());
            } else {
                LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] room_init 返回null roomid=" + roomid);
            }
            // 获取在线人数（使用 public API，不需要 cookie）
            try {
                Map<String, String> headers = new HashMap<>(3);
                headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                headers.put("referer", "https://live.bilibili.com/");
                long apiStart = System.currentTimeMillis();
                String respBody = OkHttp3Utils.getHttp3Utils()
                        .httpGet("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + realRoomId, headers, null)
                        .body().string();
                long apiCost = System.currentTimeMillis() - apiStart;
                if (respBody != null) {
                    JSONObject respJson = JSONObject.parseObject(respBody);
                    short code = respJson.getShort("code");
                    LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] get_info 响应 roomid=" + roomid + " code=" + code + " 耗时=" + apiCost + "ms bodyLen=" + respBody.length());
                    if (code == 0 && respJson.get("data") != null) {
                        JSONObject roomData = (JSONObject) respJson.get("data");
                        data.put("online", roomData.getInteger("online"));
                        // 也可以用这个API的状态覆盖
                        if (roomData.get("live_status") != null) {
                            data.put("liveStatus", roomData.getShort("live_status"));
                        }
                        LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] get_info 解析成功 roomid=" + roomid + " online=" + roomData.getInteger("online") + " live_status=" + roomData.getShort("live_status"));
                    } else {
                        LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] get_info code非0或无data roomid=" + roomid + " code=" + code + " body=" + (respBody.length() > 200 ? respBody.substring(0, 200) : respBody));
                    }
                } else {
                    LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] get_info 返回null roomid=" + roomid + " 耗时=" + apiCost + "ms");
                }
            } catch (Exception e) {
                LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] get_info 异常 roomid=" + roomid + " error=" + e.getMessage());
                LOGGER.debug("获取在线人数失败 roomid={}: {}", realRoomId, e.getMessage());
            }
        } catch (Exception e) {
            LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] room_init 异常 roomid=" + roomid + " error=" + e.getMessage());
            LOGGER.error("获取房间状态失败 roomid={}: {}", roomid, e.getMessage());
        }
        if (!data.containsKey("liveStatus")) data.put("liveStatus", 0);
        if (!data.containsKey("online")) data.put("online", 0);
        long totalCost = System.currentTimeMillis() - startTime;
        LogFileTools.getlogFileTools().logTestFile("[getRoomStatus] 返回结果 roomid=" + roomid + " liveStatus=" + data.getShort("liveStatus") + " online=" + data.getInteger("online") + " 总耗时=" + totalCost + "ms");
        return Response.success(data, req);
    }

    // === 关注直播间列表 ===
    @ResponseBody
    @GetMapping(value = "/getRoomInfo")
    public Response<?> getRoomInfo(@RequestParam("roomid") Long roomid, HttpServletRequest req) {
        JSONObject data = new JSONObject();
        try {
            // 1. 获取房间初始化信息（room_id, uid, live_status）
            RoomInit roomInit = HttpRoomData.httpGetRoomInit(roomid);
            long realRoomId = roomid;
            if (roomInit != null) {
                realRoomId = roomInit.getRoom_id() != 0 ? roomInit.getRoom_id() : roomid;
                data.put("roomId", realRoomId);
                data.put("anchorUid", roomInit.getUid());
                data.put("liveStatus", roomInit.getLive_status());
            }
            // 2. 获取主播名称
            try {
                Room roomData = HttpRoomData.httpGetRoomData(realRoomId);
                if (roomData != null && roomData.getUname() != null) {
                    data.put("anchorName", roomData.getUname());
                }
            } catch (Exception e) {
                LOGGER.warn("获取房间数据失败 roomid={}: {}", roomid, e.getMessage());
            }
            // 3. 直接调用B站API获取房间详细信息（标题、分区）
            try {
                Map<String, String> headers = new HashMap<>(3);
                headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                headers.put("referer", "https://live.bilibili.com/" + realRoomId);
                if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
                    headers.put("cookie", PublicDataConf.USERCOOKIE);
                }
                String respBody = OkHttp3Utils.getHttp3Utils()
                        .httpGet("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=" + realRoomId, headers, null)
                        .body().string();
                if (respBody != null) {
                    JSONObject respJson = JSONObject.parseObject(respBody);
                    if (respJson.getShort("code") == 0 && respJson.get("data") != null) {
                        // B站API中 room_info 是JSON字符串，需要二次解析
                        String roomInfoStr = ((JSONObject) respJson.get("data")).getString("room_info");
                        if (StringUtils.isNotBlank(roomInfoStr)) {
                            JSONObject roomInfo = JSONObject.parseObject(roomInfoStr);
                            data.put("roomName", roomInfo.getString("title"));
                            data.put("areaName", roomInfo.getString("area_name"));
                            data.put("parentAreaName", roomInfo.getString("parent_area_name"));
                            data.put("areaId", roomInfo.getInteger("area_id"));
                            data.put("parentAreaId", roomInfo.getInteger("parentAreaId"));
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("获取房间详情失败 roomid={}: {}", realRoomId, e.getMessage());
            }
            // fallback
            if (!data.containsKey("anchorName")) data.put("anchorName", "未知");
            if (!data.containsKey("roomName")) data.put("roomName", "房间" + roomid);
            if (!data.containsKey("areaName")) data.put("areaName", "");
            if (!data.containsKey("parentAreaName")) data.put("parentAreaName", "");
        } catch (Exception e) {
            LOGGER.error("获取房间信息失败 roomid={}: {}", roomid, e.getMessage());
            data.put("error", e.getMessage());
        }
        return Response.success(data, req);
    }

    @ResponseBody
    @GetMapping(value = "/getWatchedRooms")
    public Response<?> getWatchedRooms(HttpServletRequest req) {
        JSONArray list = new JSONArray();
        FileTools fileTools = new FileTools();
        File file = new File(fileTools.getBaseJarPath(), "set/watched_rooms.json");
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                list = JSONArray.parseArray(sb.toString());
                if (list == null) list = new JSONArray();
            } catch (Exception e) {
                LOGGER.error("读取关注直播间列表失败: {}", e.getMessage());
            }
        }
        return Response.success(list, req);
    }

    @ResponseBody
    @PostMapping(value = "/saveWatchedRooms")
    public Response<?> saveWatchedRooms(@RequestParam("data") String data, HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File dir = new File(fileTools.getBaseJarPath(), "set");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "watched_rooms.json");
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                // pretty-print
                JSONArray arr = JSONArray.parseArray(data);
                bw.write(JSON.toJSONString(arr, true));
                bw.flush();
            }
            return Response.success(true, req);
        } catch (Exception e) {
            LOGGER.error("保存关注直播间列表失败: {}", e.getMessage());
            return Response.success(false, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/heartBeat")
    public Response<?> heartBeat(HttpServletRequest req) {
        JSONObject data = new JSONObject();
        data.put("popu", PublicDataConf.ROOM_POPULARITY);
        data.put("live_status", PublicDataConf.lIVE_STATUS);
        data.put("room_like", PublicDataConf.ROOM_LIKE);
        data.put("room_online", PublicDataConf.ROOM_ONLINE__RANK_COUNT);
        data.put("room_watcher", PublicDataConf.ROOM_WATCHER);
        return Response.success(data, req);
    }


    @ResponseBody
    @GetMapping(value = "/getSet")
    public Response<?> get(HttpServletRequest req) {
        return Response.success(PublicDataConf.centerSetConf, req);
    }

    @ResponseBody
    @PostMapping(value = "/sendSet")
    public Response<?> send(HttpServletRequest req, @RequestParam("set") String set) {
        try {
            CenterSetConf centerSetConf = JSONObject.parseObject(set, CenterSetConf.class);
            //配置不一样 刷新页面
            if(!StringUtils.equals(centerSetConf.getEdition(),PublicDataConf.VERSION))return Response.success(2,req);
            //更改
            //公告
            if(centerSetConf.getAdvert()==null&&PublicDataConf.centerSetConf.getAdvert()!=null){
                centerSetConf.setAdvert(PublicDataConf.centerSetConf.getAdvert());
            }
            if (centerSetConf.getAdvert() == null&&PublicDataConf.centerSetConf.getAdvert()==null) {
                centerSetConf.setAdvert(new AdvertSetConf());
            }
            //关注
            if(centerSetConf.getFollow()==null&&PublicDataConf.centerSetConf.getFollow()!=null){
                centerSetConf.setFollow(PublicDataConf.centerSetConf.getFollow());
            }
            if (centerSetConf.getFollow() == null&&PublicDataConf.centerSetConf.getFollow()==null) {
                centerSetConf.setFollow(new ThankFollowSetConf());
            }
            //谢礼物
            if(centerSetConf.getThank_gift()==null&&PublicDataConf.centerSetConf.getThank_gift()!=null){
                centerSetConf.setThank_gift(PublicDataConf.centerSetConf.getThank_gift());
            }
            if(centerSetConf.getThank_gift()==null&&PublicDataConf.centerSetConf.getThank_gift()==null){
                centerSetConf.setThank_gift(new ThankGiftSetConf());
            }
            //自动回复
            if(centerSetConf.getReply()==null&&PublicDataConf.centerSetConf.getReply()!=null){
                centerSetConf.setReply(PublicDataConf.centerSetConf.getReply());
            }
            if(centerSetConf.getReply()==null&&PublicDataConf.centerSetConf.getReply()==null){
                centerSetConf.setReply(new AutoReplySetConf());
            }
            //欢迎
            if(centerSetConf.getWelcome()==null&&PublicDataConf.centerSetConf.getWelcome()!=null){
                centerSetConf.setWelcome(PublicDataConf.centerSetConf.getWelcome());
            }
            if(centerSetConf.getWelcome()==null&&PublicDataConf.centerSetConf.getWelcome()==null){
                centerSetConf.setWelcome(new ThankWelcomeSetConf());
            }
            //黑名单
            if(centerSetConf.getBlack()==null&&PublicDataConf.centerSetConf.getBlack()!=null){
                centerSetConf.setBlack(PublicDataConf.centerSetConf.getBlack());
            }
            if(centerSetConf.getBlack()==null&&PublicDataConf.centerSetConf.getBlack()==null){
                centerSetConf.setBlack(new BlackListSetConf());
            }
            //直播状态姬
            if(centerSetConf.getLive_status()==null&&PublicDataConf.centerSetConf.getLive_status()!=null){
                centerSetConf.setLive_status(PublicDataConf.centerSetConf.getLive_status());
            }
            if(centerSetConf.getLive_status()==null&&PublicDataConf.centerSetConf.getLive_status()==null){
                centerSetConf.setLive_status(new LiveStatusSetConf());
            }
            //定时姬
            if(centerSetConf.getTimer()==null&&PublicDataConf.centerSetConf.getTimer()!=null){
                centerSetConf.setTimer(PublicDataConf.centerSetConf.getTimer());
            }
            if(centerSetConf.getTimer()==null&&PublicDataConf.centerSetConf.getTimer()==null){
                centerSetConf.setTimer(new TimerSetConf());
            }
            //弹幕话术姬
            if(centerSetConf.getDanmaku_store()==null&&PublicDataConf.centerSetConf.getDanmaku_store()!=null){
                centerSetConf.setDanmaku_store(PublicDataConf.centerSetConf.getDanmaku_store());
            }
            if(centerSetConf.getDanmaku_store()==null&&PublicDataConf.centerSetConf.getDanmaku_store()==null){
                centerSetConf.setDanmaku_store(new DanmakuStoreSetConf());
            }
            //拉黑姬
            if(centerSetConf.getBadList()==null&&PublicDataConf.centerSetConf.getBadList()!=null){
                centerSetConf.setBadList(PublicDataConf.centerSetConf.getBadList());
            }
            if(centerSetConf.getBadList()==null&&PublicDataConf.centerSetConf.getBadList()==null){
                centerSetConf.setBadList(new BadListSetConf());
            }
            //关键词检测姬
            if(centerSetConf.getKey_word()==null&&PublicDataConf.centerSetConf.getKey_word()!=null){
                centerSetConf.setKey_word(PublicDataConf.centerSetConf.getKey_word());
            }
            if(centerSetConf.getKey_word()==null&&PublicDataConf.centerSetConf.getKey_word()==null){
                centerSetConf.setKey_word(new KeyWordSetConf());
            }
            //欢迎凝视姬
            if(centerSetConf.getGaze_welcome()==null&&PublicDataConf.centerSetConf.getGaze_welcome()!=null){
                centerSetConf.setGaze_welcome(PublicDataConf.centerSetConf.getGaze_welcome());
            }
            if(centerSetConf.getGaze_welcome()==null&&PublicDataConf.centerSetConf.getGaze_welcome()==null){
                centerSetConf.setGaze_welcome(new GazeWelcomeSetConf());
            }
            //负黑自动拉黑姬
            if(centerSetConf.getAuto_block()==null&&PublicDataConf.centerSetConf.getAuto_block()!=null){
                centerSetConf.setAuto_block(PublicDataConf.centerSetConf.getAuto_block());
            }
            if(centerSetConf.getAuto_block()==null&&PublicDataConf.centerSetConf.getAuto_block()==null){
                centerSetConf.setAuto_block(new AutoBlockSetConf());
            }
            checkService.changeSet(centerSetConf,true);
        } catch (Exception e) {
            LOGGER.error(e);
            // TODO: handle exception
            return Response.success(0, req);
        }
        return Response.success(1, req);
    }

    @ResponseBody
    @PostMapping(value = "/sendBarrage")
    public Response<?> sendBarrage(HttpServletRequest req, @RequestParam("text") String text) {
        try {
            if (StringUtils.isBlank(text)) {
                return Response.success(0, req);
            }
            ThreadComponent threadComponent = SpringUtils.getBean(ThreadComponent.class);
            threadComponent.startSendBarrageThread();
            if (PublicDataConf.sendBarrageThread != null && !PublicDataConf.sendBarrageThread.FLAG) {
                PublicDataConf.barrageString.offer(text);
            } else {
                return Response.success(0, req);
            }
            return Response.success(1, req);
        } catch (Exception e) {
            LOGGER.error("sendBarrage error", e);
            return Response.success(0, req);
        }
    }

    //隐私模式后移除网络调用
//    @ResponseBody
//    @GetMapping(value = "/getIp")
//    public Response<?> getIp(HttpServletRequest req) {
//        String ip = HttpOtherData.httpGetIp();
//        if (StringUtils.isNotBlank(ip)) {
//            return Response.success(ip, req);
//        } else {
//            return Response.success(null, req);
//        }
//
//    }


    @ResponseBody
    @GetMapping(value = "/block")
    public Response<?> block(@RequestParam("uid") long uid, @RequestParam("time") short time, HttpServletRequest req) {
        short code = -1;
        if (time > 720 && time <= 0) {
            //required time error
            code = 2;
            return Response.success(code, req);
        }
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            code = HttpUserData.httpPostAddBlock(uid, time);
        }
        return Response.success(code, req);
    }

    @ResponseBody
    @GetMapping(value = "/get_uname_by_uid")
    public Response<?> getUnameByUid(@RequestParam("uid") long uid, HttpServletRequest req) {
        String uname = HttpUserData.httpGetUserNameByUid(uid);
        return Response.success(uname, req);
    }

    @ResponseBody
    @GetMapping(value = "/search_uid_by_uname")
    public Response<?> searchUidByUname(@RequestParam("uname") String uname, HttpServletRequest req) {
        JSONArray result = HttpUserData.httpSearchUserByName(uname);
        return Response.success(result, req);
    }




    @ResponseBody
    @GetMapping(value = "/setExport")
    public Response<?> setExport(HttpServletRequest req) {
        boolean flag = JsonFileTools.createJsonFile(PublicDataConf.centerSetConf.toJson());
        if (flag) {
            return Response.success(0, req);
        } else {
            return Response.success(1, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/setExportWeb")
    public void setExportWeb(HttpServletResponse response) throws Exception {
        File file = JsonFileTools.createJsonFileReturnFile(PublicDataConf.centerSetConf.toJson());
        FileInputStream fileInputStream = new FileInputStream(file);
        InputStream fis = new BufferedInputStream(fileInputStream);
        byte[] buffer = new byte[fis.available()];
        fis.read(buffer);
        fis.close();
        response.reset();
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(file.getName(), "UTF-8"));
        response.addHeader("Content-Length", "" + file.length());
        OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
        response.setContentType("application/octet-stream");
        outputStream.write(buffer);
        outputStream.flush();
    }


    //配置文件导入
    @ResponseBody
    @PostMapping(value = "/setImport")
    public Response<?> setImport(@RequestParam("file") MultipartFile file, HttpServletRequest req) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".json")) {
            return Response.success(2, req);
        }
        String jsonString = new BufferedReader(new InputStreamReader(file.getInputStream(), "utf-8"))
                .lines().collect(Collectors.joining(System.lineSeparator()));
        try {
            CenterSetConf centerSetConf = FastJsonUtils.parseObject(jsonString, CenterSetConf.class);
            if (centerSetConf == null) {
                LOGGER.error("setImport: 解析JSON配置失败, filename={}", originalFilename);
                return Response.success(1, req);
            }
            centerSetConf = ParseSetStatusTools.initCenterChildConfig(centerSetConf);
            checkService.changeSet(centerSetConf, true);
        } catch (Exception e) {
            LOGGER.error("setImport error", e);
            return Response.success(1, req);
        }
        return Response.success(0, req);
    }

    @ResponseBody
    @GetMapping(value = "/add_badlist")
    public Response<?> addBadList(@RequestParam("uid") long uid, @RequestParam(value = "uname", required = false) String uname, HttpServletRequest req) {
        short code = -1;
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            code = HttpUserData.httpPostAddBadList(uid);
        }
        if (code == 0) {
            if (StringUtils.isBlank(uname)) {
                uname = "";
            }
            List<BadListSetConf.BadUser> badUsers = PublicDataConf.centerSetConf.getBadList().getBadUsers();
            boolean exists = false;
            for (BadListSetConf.BadUser bu : badUsers) {
                if (bu.getUid() != null && bu.getUid().equals(uid)) {
                    bu.setUname(uname);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                badUsers.add(new BadListSetConf.BadUser(uid, uname));
            }
            checkService.changeSet(PublicDataConf.centerSetConf, false);
        }
        return Response.success(code, req);
    }

    @ResponseBody
    @GetMapping(value = "/del_badlist")
    public Response<?> delBadList(@RequestParam("uid") long uid, HttpServletRequest req) {
        short code = -1;
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            code = HttpUserData.httpPostDeleteBadList(uid);
        }
        if (code == 0) {
            List<BadListSetConf.BadUser> badUsers = PublicDataConf.centerSetConf.getBadList().getBadUsers();
            badUsers.removeIf(bu -> bu.getUid() != null && bu.getUid().equals(uid));
            checkService.changeSet(PublicDataConf.centerSetConf, false);
        }
        return Response.success(code, req);
    }




    @ResponseBody
    @GetMapping(value = "/getBiliBadList")
    public Response<?> getBiliBadList(@RequestParam(defaultValue = "1") int pn,
                                      @RequestParam(defaultValue = "10") int ps,
                                      HttpServletRequest req) {
        try {
            JSONObject result = HttpUserData.httpGetBiliBadList(pn, ps);
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("getBiliBadList error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/getNegativeBlackPositiveWhite")
    public Response<?> getNegativeBlackPositiveWhite(HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File file = new File(fileTools.getBaseJarPath(), "set/负黑正白判定表.json");
            if (!file.exists()) {
                JSONObject empty = new JSONObject();
                empty.put("type", "负黑正白判定表");
                empty.put("followings_list", new com.alibaba.fastjson.JSONArray());
                return Response.success(empty, req);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject jsonObject = JSONObject.parseObject(sb.toString());
            return Response.success(jsonObject, req);
        } catch (Exception e) {
            LOGGER.error("getNegativeBlackPositiveWhite error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/saveNegativeBlackPositiveWhite")
    public Response<?> saveNegativeBlackPositiveWhite(@RequestParam("data") String data, HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File file = new File(fileTools.getBaseJarPath(), "set/负黑正白判定表.json");
            JSONObject inputData = JSONObject.parseObject(data);
            com.alibaba.fastjson.JSONArray inputList = inputData.getJSONArray("followings_list");

            JSONObject result = new JSONObject();
            result.put("type", "负黑正白判定表");
            com.alibaba.fastjson.JSONArray resultList = new com.alibaba.fastjson.JSONArray();
            Set<Long> seenUids = new HashSet<>();

            // reverse order: later entries override earlier ones with same uid
            for (int i = inputList.size() - 1; i >= 0; i--) {
                com.alibaba.fastjson.JSONObject entry = inputList.getJSONObject(i);
                Long uid = entry.getLong("uid");
                if (uid != null && !seenUids.contains(uid)) {
                    seenUids.add(uid);
                    resultList.add(0, entry);
                }
            }

            result.put("followings_list", resultList);

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                writer.write(com.alibaba.fastjson.JSON.toJSONString(result, true));
            }

            HttpRoomData.reloadPnScoreMap();

            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("saveNegativeBlackPositiveWhite error", e);
            return Response.success(1, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/getAutoBlockRecords")
    public Response<?> getAutoBlockRecords(HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File file = new File(fileTools.getBaseJarPath(), "set/负黑自动拉黑记录.json");
            if (!file.exists()) {
                JSONObject empty = new JSONObject();
                empty.put("type", "负黑自动拉黑记录");
                empty.put("records", new com.alibaba.fastjson.JSONArray());
                return Response.success(empty, req);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject jsonObject = JSONObject.parseObject(sb.toString());
            return Response.success(jsonObject, req);
        } catch (Exception e) {
            LOGGER.error("getAutoBlockRecords error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/deleteAutoBlockRecord")
    public Response<?> deleteAutoBlockRecord(@RequestParam("uid") long uid, HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File file = new File(fileTools.getBaseJarPath(), "set/负黑自动拉黑记录.json");
            if (!file.exists()) {
                return Response.success(0, req);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject data = JSONObject.parseObject(sb.toString());
            com.alibaba.fastjson.JSONArray records = data.getJSONArray("records");
            if (records != null) {
                for (int i = records.size() - 1; i >= 0; i--) {
                    JSONObject record = records.getJSONObject(i);
                    if (record.getLong("uid") != null && record.getLong("uid") == uid) {
                        records.remove(i);
                    }
                }
            }
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                writer.write(com.alibaba.fastjson.JSON.toJSONString(data, true));
            }
            // 通知缓存刷新，避免与内存状态不一致
            ParseMessageThread.invalidateAutoBlockCache();
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("deleteAutoBlockRecord error", e);
            return Response.success(1, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/unblockAutoBlockUser")
    public Response<?> unblockAutoBlockUser(@RequestParam("uid") long uid, HttpServletRequest req) {
        short code = -1;
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            code = HttpUserData.httpPostDeleteBadList(uid);
        }
        if (code == 0) {
            try {
                FileTools fileTools = new FileTools();
                File file = new File(fileTools.getBaseJarPath(), "set/负黑自动拉黑记录.json");
                if (file.exists()) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    JSONObject data = JSONObject.parseObject(sb.toString());
                    com.alibaba.fastjson.JSONArray records = data.getJSONArray("records");
                    if (records != null) {
                        for (int i = records.size() - 1; i >= 0; i--) {
                            JSONObject record = records.getJSONObject(i);
                            if (record.getLong("uid") != null && record.getLong("uid") == uid) {
                                records.remove(i);
                            }
                        }
                    }
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                        writer.write(com.alibaba.fastjson.JSON.toJSONString(data, true));
                    }
                    // 通知缓存刷新
                    ParseMessageThread.invalidateAutoBlockCache();
                }
            } catch (Exception e) {
                LOGGER.error("unblockAutoBlockUser delete record error", e);
            }
        }
        return Response.success(code, req);
    }

    // ========== 负黑正白姬 导出/下载/导入 ==========

    @ResponseBody
    @GetMapping(value = "/pnExport")
    public Response<?> pnExport(HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File srcFile = new File(fileTools.getBaseJarPath(), "set/负黑正白判定表.json");
            if (!srcFile.exists()) {
                return Response.success(1, req);
            }
            String destDir = fileTools.getBaseJarPath() + "/set/";
            File destDirFile = new File(destDir);
            if (!destDirFile.exists()) {
                destDirFile.mkdirs();
            }
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            File destFile = new File(destDir, "负黑正白判定表-" + timestamp + ".json");
            java.nio.file.Files.copy(srcFile.toPath(), destFile.toPath());
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("pnExport error", e);
            return Response.success(1, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/pnExportWeb")
    public void pnExportWeb(HttpServletResponse response) throws Exception {
        FileTools fileTools = new FileTools();
        File file = new File(fileTools.getBaseJarPath(), "set/负黑正白判定表.json");
        if (!file.exists()) {
            file.createNewFile();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                JSONObject empty = new JSONObject();
                empty.put("type", "负黑正白判定表");
                empty.put("followings_list", new JSONArray());
                writer.write(com.alibaba.fastjson.JSON.toJSONString(empty, true));
            }
        }
        FileInputStream fileInputStream = new FileInputStream(file);
        BufferedInputStream fis = new BufferedInputStream(fileInputStream);
        byte[] buffer = new byte[fis.available()];
        fis.read(buffer);
        fis.close();
        response.reset();
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("set/负黑正白判定表.json", "UTF-8"));
        response.addHeader("Content-Length", "" + file.length());
        OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
        response.setContentType("application/octet-stream");
        outputStream.write(buffer);
        outputStream.flush();
    }

    @ResponseBody
    @PostMapping(value = "/pnImport")
    public Response<?> pnImport(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".json")) {
                return Response.success(2, req);
            }
            String jsonString = new BufferedReader(new InputStreamReader(file.getInputStream(), "utf-8"))
                    .lines().collect(Collectors.joining(System.lineSeparator()));
            JSONObject jsonObject = JSONObject.parseObject(jsonString);
            if (jsonObject == null || !"负黑正白判定表".equals(jsonObject.getString("type"))) {
                return Response.success(1, req);
            }
            FileTools fileTools = new FileTools();
            File destFile = new File(fileTools.getBaseJarPath(), "set/负黑正白判定表.json");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destFile), "UTF-8"))) {
                writer.write(com.alibaba.fastjson.JSON.toJSONString(jsonObject, true));
            }
            HttpRoomData.reloadPnScoreMap();
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("pnImport error", e);
            return Response.success(1, req);
        }
    }

    // ========== 负黑自动拉黑姬 导出/下载/导入 ==========

    @ResponseBody
    @GetMapping(value = "/abExport")
    public Response<?> abExport(HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File srcFile = new File(fileTools.getBaseJarPath(), "set/负黑自动拉黑记录.json");
            if (!srcFile.exists()) {
                return Response.success(1, req);
            }
            String destDir = fileTools.getBaseJarPath() + "/set/";
            File destDirFile = new File(destDir);
            if (!destDirFile.exists()) {
                destDirFile.mkdirs();
            }
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            File destFile = new File(destDir, "负黑自动拉黑记录-" + timestamp + ".json");
            java.nio.file.Files.copy(srcFile.toPath(), destFile.toPath());
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("abExport error", e);
            return Response.success(1, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/abExportWeb")
    public void abExportWeb(HttpServletResponse response) throws Exception {
        FileTools fileTools = new FileTools();
        File file = new File(fileTools.getBaseJarPath(), "set/负黑自动拉黑记录.json");
        if (!file.exists()) {
            file.createNewFile();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                JSONObject empty = new JSONObject();
                empty.put("type", "负黑自动拉黑记录");
                empty.put("records", new JSONArray());
                writer.write(com.alibaba.fastjson.JSON.toJSONString(empty, true));
            }
        }
        FileInputStream fileInputStream = new FileInputStream(file);
        BufferedInputStream fis = new BufferedInputStream(fileInputStream);
        byte[] buffer = new byte[fis.available()];
        fis.read(buffer);
        fis.close();
        response.reset();
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("负黑自动拉黑记录.json", "UTF-8"));
        response.addHeader("Content-Length", "" + file.length());
        OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
        response.setContentType("application/octet-stream");
        outputStream.write(buffer);
        outputStream.flush();
    }

    @ResponseBody
    @PostMapping(value = "/abImport")
    public Response<?> abImport(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".json")) {
                return Response.success(2, req);
            }
            String jsonString = new BufferedReader(new InputStreamReader(file.getInputStream(), "utf-8"))
                    .lines().collect(Collectors.joining(System.lineSeparator()));
            JSONObject jsonObject = JSONObject.parseObject(jsonString);
            if (jsonObject == null || !"负黑自动拉黑记录".equals(jsonObject.getString("type"))) {
                return Response.success(1, req);
            }
            FileTools fileTools = new FileTools();
            File destFile = new File(fileTools.getBaseJarPath(), "set/负黑自动拉黑记录.json");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destFile), "UTF-8"))) {
                writer.write(com.alibaba.fastjson.JSON.toJSONString(jsonObject, true));
            }
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("abImport error", e);
            return Response.success(1, req);
        }
    }

    // ========== 弹幕话术姬 导出/下载/导入 ==========

    @ResponseBody
    @GetMapping(value = "/getDanmakuStore")
    public Response<?> getDanmakuStore(HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File file = new File(fileTools.getBaseJarPath(), "set/话术.json");
            if (!file.exists()) {
                JSONObject empty = new JSONObject();
                empty.put("type", "话术");
                empty.put("items", new com.alibaba.fastjson.JSONArray());
                return Response.success(empty, req);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject jsonObject = JSONObject.parseObject(sb.toString());
            return Response.success(jsonObject, req);
        } catch (Exception e) {
            LOGGER.error("getDanmakuStore error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/saveDanmakuStore")
    public Response<?> saveDanmakuStore(@RequestParam("data") String data, HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File file = new File(fileTools.getBaseJarPath(), "set/话术.json");
            JSONObject inputData = JSONObject.parseObject(data);
            com.alibaba.fastjson.JSONArray inputList = inputData.getJSONArray("items");

            JSONObject result = new JSONObject();
            result.put("type", "话术");
            com.alibaba.fastjson.JSONArray resultList = new com.alibaba.fastjson.JSONArray();

            if (inputList != null) {
                for (int i = 0; i < inputList.size(); i++) {
                    JSONObject entry = inputList.getJSONObject(i);
                    if (entry != null) {
                        String text = entry.getString("text");
                        if (text != null && !text.trim().isEmpty()) {
                            resultList.add(entry);
                        }
                    }
                }
            }

            result.put("items", resultList);

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                writer.write(com.alibaba.fastjson.JSON.toJSONString(result, true));
            }

            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("saveDanmakuStore error", e);
            return Response.success(1, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/dsExport")
    public Response<?> dsExport(HttpServletRequest req) {
        try {
            FileTools fileTools = new FileTools();
            File srcFile = new File(fileTools.getBaseJarPath(), "set/话术.json");
            if (!srcFile.exists()) {
                return Response.success(1, req);
            }
            String destDir = fileTools.getBaseJarPath() + "/set/";
            File destDirFile = new File(destDir);
            if (!destDirFile.exists()) {
                destDirFile.mkdirs();
            }
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            File destFile = new File(destDir, "话术-" + timestamp + ".json");
            java.nio.file.Files.copy(srcFile.toPath(), destFile.toPath());
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("dsExport error", e);
            return Response.success(1, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/dsExportWeb")
    public void dsExportWeb(HttpServletResponse response) throws Exception {
        FileTools fileTools = new FileTools();
        File file = new File(fileTools.getBaseJarPath(), "set/话术.json");
        if (!file.exists()) {
            file.createNewFile();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                JSONObject empty = new JSONObject();
                empty.put("type", "话术");
                empty.put("items", new JSONArray());
                writer.write(com.alibaba.fastjson.JSON.toJSONString(empty, true));
            }
        }
        FileInputStream fileInputStream = new FileInputStream(file);
        BufferedInputStream fis = new BufferedInputStream(fileInputStream);
        byte[] buffer = new byte[fis.available()];
        fis.read(buffer);
        fis.close();
        response.reset();
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("话术.json", "UTF-8"));
        response.addHeader("Content-Length", "" + file.length());
        OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
        response.setContentType("application/octet-stream");
        outputStream.write(buffer);
        outputStream.flush();
    }

    @ResponseBody
    @PostMapping(value = "/dsImport")
    public Response<?> dsImport(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".json")) {
                return Response.success(2, req);
            }
            String jsonString = new BufferedReader(new InputStreamReader(file.getInputStream(), "utf-8"))
                    .lines().collect(Collectors.joining(System.lineSeparator()));
            JSONObject jsonObject = JSONObject.parseObject(jsonString);
            if (jsonObject == null || !"话术".equals(jsonObject.getString("type"))) {
                return Response.success(1, req);
            }
            FileTools fileTools = new FileTools();
            File destFile = new File(fileTools.getBaseJarPath(), "set/话术.json");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destFile), "UTF-8"))) {
                writer.write(com.alibaba.fastjson.JSON.toJSONString(jsonObject, true));
            }
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("dsImport error", e);
            return Response.success(1, req);
        }
    }

    // ========== 直播间管理 CSV ==========

    private File getDanmujiLogDir() {
        FileTools fileTools = new FileTools();
        return new File(fileTools.getBaseJarPath(), "Danmuji_log");
    }

    private String safeFileName(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void validateFilePath(String filePath) {
        File danmujiLogDir = getDanmujiLogDir();
        File resolved = new File(filePath);
        if (!resolved.isAbsolute()) {
            resolved = new File(danmujiLogDir, filePath);
        }
        String canonicalParent;
        try {
            canonicalParent = danmujiLogDir.getCanonicalPath();
            String canonicalFile = resolved.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalParent + File.separator) && !canonicalFile.equals(canonicalParent)) {
                throw new IllegalArgumentException("Invalid file path");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid file path", e);
        }
    }

    @ResponseBody
    @GetMapping(value = "/listCsvFiles")
    public Response<?> listCsvFiles(HttpServletRequest req) {
        try {
            File danmujiLogDir = getDanmujiLogDir();
            List<Map<String, String>> fileList = new ArrayList<>();
            if (danmujiLogDir.exists() && danmujiLogDir.isDirectory()) {
                File[] files = danmujiLogDir.listFiles((dir, name) -> name.endsWith("直播间信息.csv"));
                if (files != null) {
                    // current room identifier
                    String currentRoomId = PublicDataConf.ROOMID != null ? PublicDataConf.ROOMID.toString() : "";
                    String currentAnchor = safeFileName(PublicDataConf.ANCHOR_NAME);
                    String currentPattern = currentRoomId + "_" + currentAnchor + "_1_直播间信息.csv";

                    for (File f : files) {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("fileName", f.getName());
                        item.put("filePath", f.getAbsolutePath());
                        // parse roomId and anchor from filename: {roomId}_{anchorName}_1_直播间信息.csv
                        String name = f.getName();
                        int idx1 = name.indexOf('_');
                        int idx2 = name.indexOf('_', idx1 + 1);
                        if (idx1 > 0 && idx2 > idx1) {
                            item.put("roomId", name.substring(0, idx1));
                            item.put("anchorName", name.substring(idx1 + 1, idx2));
                        }
                        item.put("isCurrent", f.getName().equals(currentPattern) ? "true" : "false");
                        fileList.add(item);
                    }
                }
            }
            // sort: current file first, then by name
            fileList.sort((a, b) -> {
                if ("true".equals(a.get("isCurrent"))) return -1;
                if ("true".equals(b.get("isCurrent"))) return 1;
                return a.get("fileName").compareTo(b.get("fileName"));
            });
            return Response.success(fileList, req);
        } catch (Exception e) {
            LOGGER.error("listCsvFiles error", e);
            return Response.success(Collections.emptyList(), req);
        }
    }

    // === 内存级实时数据端点（0延迟，绕过CSV文件读写） ===

    @ResponseBody
    @GetMapping(value = "/readRoomLiveData")
    public Response<?> readRoomLiveData(HttpServletRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : RoomInfoLogTools.getRoomInfoList()) {
            long[] v = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("时间", e.getKey());
            row.put("观看数", String.valueOf(v[0]));
            row.put("在线数", String.valueOf(v[1]));
            row.put("点赞数", String.valueOf(v[2]));
            rows.add(row);
        }
        result.put("headers", new String[]{"时间", "观看数", "在线数", "点赞数"});
        result.put("rows", rows);
        result.put("total", rows.size());
        result.put("fromMemory", true);
        return Response.success(result, req);
    }

    @ResponseBody
    @GetMapping(value = "/readBarrageLiveData")
    public Response<?> readBarrageLiveData(@RequestParam(defaultValue = "100") int limit, HttpServletRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : BarrageLogTools.getRecentBarrages(limit)) {
            List<String> fields = parseCsvLine(line);
            if (fields.size() >= 4) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("发送时间", fields.get(0));
                row.put("id", fields.get(1));
                row.put("名字", fields.get(2));
                row.put("弹幕", fields.get(3));
                rows.add(row);
            }
        }
        result.put("headers", new String[]{"发送时间", "id", "名字", "弹幕"});
        result.put("rows", rows);
        result.put("total", rows.size());
        result.put("fromMemory", true);
        return Response.success(result, req);
    }

    @ResponseBody
    @GetMapping(value = "/readGiftLiveData")
    public Response<?> readGiftLiveData(HttpServletRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GiftLogTools.GiftRecord r : GiftLogTools.getGiftList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("最新时间", xyz.acproject.danmuji.utils.JodaTimeUtils.formatDateTime(r.latestTime));
            row.put("id", String.valueOf(r.uid));
            row.put("名字", r.uname);
            row.put("赠送礼物名字", r.giftName);
            row.put("总金额", String.valueOf(r.totalPrice));
            row.put("赠礼次数", String.valueOf(r.count));
            rows.add(row);
        }
        result.put("headers", new String[]{"最新时间", "id", "名字", "赠送礼物名字", "总金额", "赠礼次数"});
        result.put("rows", rows);
        result.put("total", rows.size());
        result.put("fromMemory", true);
        return Response.success(result, req);
    }

    @ResponseBody
    @GetMapping(value = "/readVisitorLiveData")
    public Response<?> readVisitorLiveData(HttpServletRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (VisitorCountTools.VisitorRecord r : VisitorCountTools.getVisitorList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("最近", xyz.acproject.danmuji.utils.JodaTimeUtils.formatDateTime(r.latestEntryTime));
            row.put("id", String.valueOf(r.uid));
            row.put("观众", r.uname);
            row.put("打分", String.valueOf(r.score));
            row.put("打分类型", r.scoreType);
            row.put("次数", String.valueOf(r.count));
            row.put("判定表", r.inPnTable ? "是" : "否");
            row.put("场次", String.valueOf(r.session));
            rows.add(row);
        }
        result.put("headers", new String[]{"最近", "id", "观众", "打分", "打分类型", "次数", "判定表", "场次"});
        result.put("rows", rows);
        result.put("total", rows.size());
        result.put("fromMemory", true);
        return Response.success(result, req);
    }

    @ResponseBody
    @GetMapping(value = "/readFollowLiveData")
    public Response<?> readFollowLiveData(HttpServletRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FollowingCountTools.FollowingRecord r : FollowingCountTools.getFollowingList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("最新时间", xyz.acproject.danmuji.utils.JodaTimeUtils.formatDateTime(r.latestTime));
            row.put("id", String.valueOf(r.uid));
            row.put("名字", r.name);
            row.put("次数", String.valueOf(r.count));
            rows.add(row);
        }
        result.put("headers", new String[]{"最新时间", "id", "名字", "次数"});
        result.put("rows", rows);
        result.put("total", rows.size());
        result.put("fromMemory", true);
        return Response.success(result, req);
    }

    @ResponseBody
    @GetMapping(value = "/readRoomEntryExitData")
    public Response<?> readRoomEntryExitData(HttpServletRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : RoomInfoLogTools.getEntryExitList()) {
            long[] v = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("时间", e.getKey());
            row.put("进入数", String.valueOf(v[0]));
            row.put("退出数", String.valueOf(v[1]));
            rows.add(row);
        }
        result.put("headers", new String[]{"时间", "进入数", "退出数"});
        result.put("rows", rows);
        result.put("total", rows.size());
        return Response.success(result, req);
    }

    @ResponseBody
    @GetMapping(value = "/readCsvData")
    public Response<?> readCsvData(@RequestParam("filePath") String filePath,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "10") int pageSize,
                                   @RequestParam(required = false) String startTime,
                                   @RequestParam(required = false) String endTime,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false) String sortField,
                                   @RequestParam(required = false) String sortOrder,
                                   HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, String>> allRows = new ArrayList<>();
            String[] headers = {"时间", "观看数", "在线数", "点赞数"};
            String firstTime = null;
            String lastTime = null;
            String filteredFirstTime = null;
            String filteredLastTime = null;

            if (!file.exists()) {
                result.put("headers", headers);
                result.put("rows", Collections.emptyList());
                result.put("total", 0);
                result.put("totalPages", 0);
                result.put("currentPage", page);
                result.put("firstTime", "");
                result.put("lastTime", "");
                result.put("filteredFirstTime", "");
                result.put("filteredLastTime", "");
                String liveStartTime = "";
                if (PublicDataConf.ROOM_INFO != null && PublicDataConf.ROOM_INFO.getLive_start_time() != null
                        && PublicDataConf.ROOM_INFO.getLive_start_time() > 0) {
                    liveStartTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(PublicDataConf.ROOM_INFO.getLive_start_time() * 1000L));
                }
                result.put("liveStartTime", liveStartTime);
                return Response.success(result, req);
            }

            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line = reader.readLine(); // skip header + BOM
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 4);
                    if (parts.length >= 4) {
                        String time = parts[0];
                        if (firstTime == null) firstTime = time;
                        lastTime = time;
                        if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                        if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                        if (searchLower != null && !parts[0].toLowerCase().contains(searchLower)
                                && !parts[1].toLowerCase().contains(searchLower)
                                && !parts[2].toLowerCase().contains(searchLower)
                                && !parts[3].toLowerCase().contains(searchLower)) continue;
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("时间", time);
                        row.put("观看数", parts[1]);
                        row.put("在线数", parts[2]);
                        row.put("点赞数", parts[3]);
                        allRows.add(row);
                        if (filteredFirstTime == null) filteredFirstTime = time;
                        filteredLastTime = time;
                    }
                }
            }

            String sf = (sortField != null && !sortField.isEmpty()) ? sortField : "时间";
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : true;
            boolean isDefSort = sortField == null || sortField.isEmpty();
            allRows.sort((a, b) -> {
                int cmp;
                switch (sf) {
                    case "观看数": case "在线数": case "点赞数":
                        cmp = compareField(a.get(sf), b.get(sf), true);
                        break;
                    default:
                        cmp = compareField(a.get("时间"), b.get("时间"), false);
                        break;
                }
                if (cmp == 0 && isDefSort) cmp = compareField(a.get("时间"), b.get("时间"), false);
                return asc ? cmp : -cmp;
            });

            int total = allRows.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (page < 1) page = 1;
            if (page > totalPages && totalPages > 0) page = totalPages;
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<Map<String, String>> pageRows = total > 0 ? allRows.subList(fromIndex, toIndex) : Collections.emptyList();

            result.put("headers", headers);
            result.put("rows", pageRows);
            result.put("total", total);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("firstTime", firstTime != null ? firstTime : "");
            result.put("lastTime", lastTime != null ? lastTime : "");
            result.put("filteredFirstTime", filteredFirstTime != null ? filteredFirstTime : "");
            result.put("filteredLastTime", filteredLastTime != null ? filteredLastTime : "");
            String liveStartTime = "";
            if (PublicDataConf.ROOM_INFO != null && PublicDataConf.ROOM_INFO.getLive_start_time() != null
                    && PublicDataConf.ROOM_INFO.getLive_start_time() > 0) {
                liveStartTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(PublicDataConf.ROOM_INFO.getLive_start_time() * 1000L));
            }
            result.put("liveStartTime", liveStartTime);
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("readCsvData error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/deleteCsvRow")
    public Response<?> deleteCsvRow(@RequestParam("filePath") String filePath,
                                    @RequestParam("timeKey") String timeKey,
                                    HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            if (!file.exists()) {
                return Response.success(false, req);
            }

            // 1. 先移除内存中的记录，防止后续定时 flush 恢复被删行
            RoomInfoLogTools.removeByTimeKey(timeKey);

            // 2. 直接修改 CSV 文件（读写过滤），保证对任意文件路径都生效
            List<String> lines = new ArrayList<>();
            String headerLine = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                headerLine = reader.readLine(); // header with BOM
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 4);
                    // 精确匹配或分钟前缀匹配（兼容新旧 key 格式）
                    if (parts.length >= 1 && (parts[0].equals(timeKey)
                            || (timeKey.length() >= 16 && parts[0].startsWith(timeKey.substring(0, 16))))) {
                        continue;
                    }
                    lines.add(line);
                }
            }

            File tmpFile = new File(filePath + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                if (headerLine != null) {
                    writer.write(headerLine);
                    writer.newLine();
                }
                for (String l : lines) {
                    writer.write(l);
                    writer.newLine();
                }
            }
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Response.success(true, req);
        } catch (Exception e) {
            LOGGER.error("deleteCsvRow error", e);
            return Response.success(false, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/exportFilteredCsv")
    public void exportFilteredCsv(@RequestParam("filePath") String filePath,
                                  @RequestParam(required = false) String startTime,
                                  @RequestParam(required = false) String endTime,
                                  @RequestParam(required = false) String search,
                                  HttpServletResponse response) throws Exception {
        validateFilePath(filePath);
        File file = new File(filePath);
        if (!file.isAbsolute()) {
            file = new File(getDanmujiLogDir(), filePath);
        }

        File tmpFile = File.createTempFile("lrm-export-", ".csv");
        try {
            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                // write BOM + header
                String headerLine = reader.readLine();
                writer.write('﻿');
                writer.write("时间,观看数,在线数,点赞数");
                writer.newLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 4);
                    if (parts.length >= 4) {
                        String time = parts[0];
                        if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                        if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                        if (searchLower != null && !parts[0].toLowerCase().contains(searchLower)
                                && !parts[1].toLowerCase().contains(searchLower)
                                && !parts[2].toLowerCase().contains(searchLower)
                                && !parts[3].toLowerCase().contains(searchLower)) continue;
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }

            String downloadName = file.getName();
            FileInputStream fis = new FileInputStream(tmpFile);
            BufferedInputStream bis = new BufferedInputStream(fis);
            byte[] buffer = new byte[bis.available()];
            bis.read(buffer);
            bis.close();
            response.reset();
            response.setCharacterEncoding("UTF-8");
            response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(downloadName, "UTF-8"));
            response.addHeader("Content-Length", "" + tmpFile.length());
            response.setContentType("application/octet-stream");
            OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
            outputStream.write(buffer);
            outputStream.flush();
        } finally {
            tmpFile.delete();
        }
    }

    @ResponseBody
    @GetMapping(value = "/getCsvStatistics")
    public Response<?> getCsvStatistics(@RequestParam("filePath") String filePath,
                                        @RequestParam(required = false) String startTime,
                                        @RequestParam(required = false) String endTime,
                                        HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            if (!file.exists()) {
                stats.put("cumulativeWatcher", 0L);
                stats.put("cumulativeLike", 0L);
                stats.put("avgOnlineCount", 0);
                stats.put("maxOnlineCount", null);
                stats.put("totalWatchSeconds", 0L);
                stats.put("avgWatchSeconds", 0L);
                return Response.success(stats, req);
            }

            List<String[]> filteredRows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                reader.readLine(); // skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 4);
                    if (parts.length >= 4) {
                        String time = parts[0];
                        if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                        if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                        filteredRows.add(parts);
                    }
                }
            }

            if (filteredRows.isEmpty()) {
                stats.put("cumulativeWatcher", 0L);
                stats.put("cumulativeLike", 0L);
                stats.put("avgOnlineCount", 0);
                stats.put("maxOnlineCount", null);
                stats.put("totalWatchSeconds", 0L);
                stats.put("avgWatchSeconds", 0L);
                return Response.success(stats, req);
            }

            long onlineSum = 0;
            long maxOnline = Long.MIN_VALUE;
            String maxOnlineTime = "";
            long cumulativeWatcher = 0;
            long cumulativeLike = 0;
            // interval is 60 seconds (1 minute) per data point
            final long INTERVAL_SECONDS = 60;

            for (String[] row : filteredRows) {
                long online = Long.parseLong(row[2]);
                onlineSum += online;
                if (online > maxOnline) {
                    maxOnline = online;
                    maxOnlineTime = row[0];
                }
                long watcher = Long.parseLong(row[1]);
                if (watcher > cumulativeWatcher) cumulativeWatcher = watcher;
                long like = Long.parseLong(row[3]);
                if (like > cumulativeLike) cumulativeLike = like;
            }

            long totalWatchSeconds = onlineSum * INTERVAL_SECONDS;
            long avgWatchSeconds = cumulativeWatcher > 0 ? totalWatchSeconds / cumulativeWatcher : 0;

            double avgOnline = (double) onlineSum / filteredRows.size();

            stats.put("cumulativeWatcher", cumulativeWatcher);
            stats.put("cumulativeLike", cumulativeLike);
            stats.put("avgOnlineCount", Math.round(avgOnline));

            Map<String, Object> maxOnlineInfo = new LinkedHashMap<>();
            maxOnlineInfo.put("time", maxOnlineTime);
            maxOnlineInfo.put("count", maxOnline);
            stats.put("maxOnlineCount", maxOnlineInfo);

            stats.put("totalWatchSeconds", totalWatchSeconds);
            stats.put("avgWatchSeconds", avgWatchSeconds);

            return Response.success(stats, req);
        } catch (Exception e) {
            LOGGER.error("getCsvStatistics error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/importCsvFile")
    public Response<?> importCsvFile(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".csv")) {
                return Response.success(2, req);
            }
            // validate it's a 直播间信息 CSV by checking header
            String firstLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
                firstLine = reader.readLine();
            }
            if (firstLine == null || (!firstLine.contains("时间") || !firstLine.contains("观看数"))) {
                return Response.success(3, req);
            }

            File danmujiLogDir = getDanmujiLogDir();
            if (!danmujiLogDir.exists()) {
                danmujiLogDir.mkdirs();
            }
            File destFile = new File(danmujiLogDir, originalFilename);
            file.transferTo(destFile);
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("importCsvFile error", e);
            return Response.success(1, req);
        }
    }

    // ========== 弹幕管理 CSV ==========

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString());
        return fields;
    }

    @ResponseBody
    @GetMapping(value = "/listBarrageCsvFiles")
    public Response<?> listBarrageCsvFiles(HttpServletRequest req) {
        try {
            File danmujiLogDir = getDanmujiLogDir();
            List<Map<String, String>> fileList = new ArrayList<>();
            if (danmujiLogDir.exists() && danmujiLogDir.isDirectory()) {
                File[] files = danmujiLogDir.listFiles((dir, name) -> name.endsWith("弹幕信息.csv"));
                if (files != null) {
                    String currentRoomId = PublicDataConf.ROOMID != null ? PublicDataConf.ROOMID.toString() : "";
                    String currentAnchor = safeFileName(PublicDataConf.ANCHOR_NAME);
                    String currentPattern = currentRoomId + "_" + currentAnchor + "_2_弹幕信息.csv";
                    for (File f : files) {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("fileName", f.getName());
                        item.put("filePath", f.getAbsolutePath());
                        String name = f.getName();
                        int idx1 = name.indexOf('_');
                        int idx2 = name.indexOf('_', idx1 + 1);
                        if (idx1 > 0 && idx2 > idx1) {
                            item.put("roomId", name.substring(0, idx1));
                            item.put("anchorName", name.substring(idx1 + 1, idx2));
                        }
                        item.put("isCurrent", f.getName().equals(currentPattern) ? "true" : "false");
                        fileList.add(item);
                    }
                }
            }
            fileList.sort((a, b) -> {
                if ("true".equals(a.get("isCurrent"))) return -1;
                if ("true".equals(b.get("isCurrent"))) return 1;
                return a.get("fileName").compareTo(b.get("fileName"));
            });
            return Response.success(fileList, req);
        } catch (Exception e) {
            LOGGER.error("listBarrageCsvFiles error", e);
            return Response.success(Collections.emptyList(), req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/readBarrageCsvData")
    public Response<?> readBarrageCsvData(@RequestParam("filePath") String filePath,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String endTime,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String sortField,
                                          @RequestParam(required = false) String sortOrder,
                                          HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, String>> allRows = new ArrayList<>();
            String[] headers = {"发送时间", "id", "名字", "弹幕"};
            String firstTime = null;
            String lastTime = null;

            if (!file.exists()) {
                result.put("headers", headers);
                result.put("rows", Collections.emptyList());
                result.put("total", 0);
                result.put("totalPages", 0);
                result.put("currentPage", page);
                result.put("firstTime", "");
                result.put("lastTime", "");
                return Response.success(result, req);
            }

            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line = reader.readLine(); // skip header + BOM
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 4) continue;
                    String time = fields.get(0);
                    if (firstTime == null) firstTime = time;
                    lastTime = time;
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    if (searchLower != null) {
                        boolean match = false;
                        for (String f : fields) {
                            if (f.toLowerCase().contains(searchLower)) { match = true; break; }
                        }
                        if (!match) continue;
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("发送时间", time);
                    row.put("id", fields.get(1));
                    row.put("名字", fields.get(2));
                    row.put("弹幕", fields.get(3));
                    allRows.add(row);
                }
            }

            String sf = (sortField != null && !sortField.isEmpty()) ? sortField : "发送时间";
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : false;
            boolean isDefSort = sortField == null || sortField.isEmpty();
            allRows.sort((a, b) -> {
                int cmp;
                switch (sf) {
                    case "id":
                        cmp = compareField(a.get(sf), b.get(sf), true);
                        break;
                    case "名字": case "弹幕":
                        cmp = compareField(a.get(sf), b.get(sf), false);
                        break;
                    default:
                        cmp = compareField(a.get("发送时间"), b.get("发送时间"), false);
                        break;
                }
                if (cmp == 0 && isDefSort) cmp = compareField(a.get("id"), b.get("id"), true);
                return asc ? cmp : -cmp;
            });

            int total = allRows.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (page < 1) page = 1;
            if (page > totalPages && totalPages > 0) page = totalPages;
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<Map<String, String>> pageRows = total > 0 ? allRows.subList(fromIndex, toIndex) : Collections.emptyList();

            result.put("headers", headers);
            result.put("rows", pageRows);
            result.put("total", total);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("firstTime", firstTime != null ? firstTime : "");
            result.put("lastTime", lastTime != null ? lastTime : "");
            String liveStartTime = "";
            if (PublicDataConf.ROOM_INFO != null && PublicDataConf.ROOM_INFO.getLive_start_time() != null
                    && PublicDataConf.ROOM_INFO.getLive_start_time() > 0) {
                liveStartTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(PublicDataConf.ROOM_INFO.getLive_start_time() * 1000L));
            }
            result.put("liveStartTime", liveStartTime);
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("readBarrageCsvData error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/deleteBarrageCsvRow")
    public Response<?> deleteBarrageCsvRow(@RequestParam("filePath") String filePath,
                                           @RequestParam("timeKey") String timeKey,
                                           @RequestParam("uidKey") String uidKey,
                                           @RequestParam("msgKey") String msgKey,
                                           HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            if (!file.exists()) {
                return Response.success(false, req);
            }

            List<String> lines = new ArrayList<>();
            String headerLine = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                headerLine = reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() >= 4 && fields.get(0).equals(timeKey)
                            && fields.get(1).equals(uidKey) && fields.get(3).equals(msgKey)) {
                        continue;
                    }
                    lines.add(line);
                }
            }

            File tmpFile = new File(filePath + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                if (headerLine != null) {
                    writer.write(headerLine);
                    writer.newLine();
                }
                for (String l : lines) {
                    writer.write(l);
                    writer.newLine();
                }
            }
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Response.success(true, req);
        } catch (Exception e) {
            LOGGER.error("deleteBarrageCsvRow error", e);
            return Response.success(false, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/exportBarrageFilteredCsv")
    public void exportBarrageFilteredCsv(@RequestParam("filePath") String filePath,
                                         @RequestParam(required = false) String startTime,
                                         @RequestParam(required = false) String endTime,
                                         @RequestParam(required = false) String search,
                                         HttpServletResponse response) throws Exception {
        validateFilePath(filePath);
        File file = new File(filePath);
        if (!file.isAbsolute()) {
            file = new File(getDanmujiLogDir(), filePath);
        }

        String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
        File tmpFile = File.createTempFile("dmgr-export-", ".csv");
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                String headerLine = reader.readLine();
                writer.write('﻿');
                writer.write("发送时间,id,名字,弹幕");
                writer.newLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() >= 4) {
                        String time = fields.get(0);
                        if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                        if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                        if (searchLower != null) {
                            boolean match = false;
                            for (String f : fields) {
                                if (f.toLowerCase().contains(searchLower)) { match = true; break; }
                            }
                            if (!match) continue;
                        }
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }

            String downloadName = file.getName();
            FileInputStream fis = new FileInputStream(tmpFile);
            BufferedInputStream bis = new BufferedInputStream(fis);
            byte[] buffer = new byte[bis.available()];
            bis.read(buffer);
            bis.close();
            response.reset();
            response.setCharacterEncoding("UTF-8");
            response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(downloadName, "UTF-8"));
            response.addHeader("Content-Length", "" + tmpFile.length());
            response.setContentType("application/octet-stream");
            OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
            outputStream.write(buffer);
            outputStream.flush();
        } finally {
            tmpFile.delete();
        }
    }

    @ResponseBody
    @GetMapping(value = "/getBarrageStatistics")
    public Response<?> getBarrageStatistics(@RequestParam("filePath") String filePath,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime,
                                            @RequestParam(defaultValue = "5") int limit,
                                            HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            Map<String, Object> stats = new LinkedHashMap<>();
            if (!file.exists()) {
                stats.put("userCount", 0); stats.put("barrageCount", 0); stats.put("totalChars", 0L);
                stats.put("top5Senders", Collections.emptyList()); stats.put("wordFrequency", Collections.emptyList());
                stats.put("perIntervalData", Collections.emptyList());
                stats.put("danmakuScatter", Collections.emptyList());
                return Response.success(stats, req);
            }

            List<String[]> filteredRows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                reader.readLine(); // skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 4) continue;
                    String time = fields.get(0);
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    filteredRows.add(new String[]{time, fields.get(1), fields.get(2), fields.get(3)});
                }
            }

            if (filteredRows.isEmpty()) {
                stats.put("userCount", 0); stats.put("barrageCount", 0); stats.put("totalChars", 0L);
                stats.put("top5Senders", Collections.emptyList()); stats.put("wordFrequency", Collections.emptyList());
                stats.put("perIntervalData", Collections.emptyList());
                stats.put("danmakuScatter", Collections.emptyList());
                return Response.success(stats, req);
            }

            Set<String> userIds = new HashSet<>();
            Map<String, Integer> senderCounts = new LinkedHashMap<>();
            long totalChars = 0;
            Map<String, Integer> wordFreq = new LinkedHashMap<>();
            Map<String, int[]> intervalMap = new LinkedHashMap<>();
            Map<String, Set<String>> intervalUnique = new LinkedHashMap<>();
            Map<String, List<Map<String, Object>>> senderScatter = new LinkedHashMap<>();
            Map<String, String> uidNameMap = new LinkedHashMap<>();

            int intervalMinutes = 1;

            java.text.SimpleDateFormat bucketSdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
            for (String[] row : filteredRows) {
                String uid = row[1];
                if (uid != null && !uid.isEmpty() && !uid.equals("0")) {
                    userIds.add(uid);
                }
                String name = row[2];
                senderCounts.put(name, senderCounts.getOrDefault(name, 0) + 1);
                String msg = row[3];
                totalChars += msg.length();

                // scatter data: per-sender time+length
                String scatterUid = (uid != null && !uid.isEmpty()) ? uid : name;
                uidNameMap.put(scatterUid, name);
                Map<String, Object> pt = new LinkedHashMap<>();
                pt.put("time", row[0]);
                pt.put("length", msg.length() / 5);
                pt.put("rawLength", msg.length());
                senderScatter.computeIfAbsent(scatterUid, k -> new ArrayList<>()).add(pt);

                String clearedMsg = msg.replaceAll("\\[.*?\\]", "");
                String[] tokens = clearedMsg.split("[^\\u4e00-\\u9fa5a-zA-Z0-9]+");
                for (String token : tokens) {
                    if (token.length() >= 2) {
                        wordFreq.put(token, wordFreq.getOrDefault(token, 0) + 1);
                    }
                }

                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    long ms = sdf.parse(row[0]).getTime();
                    long bucketMs = (ms / (intervalMinutes * 60000L)) * (intervalMinutes * 60000L);
                    String bucketKey = bucketSdf.format(new java.util.Date(bucketMs));
                    int[] iv = intervalMap.get(bucketKey);
                    if (iv == null) {
                        iv = new int[]{0, 0};
                        intervalMap.put(bucketKey, iv);
                        intervalUnique.put(bucketKey, new HashSet<>());
                    }
                    iv[0]++;
                    iv[1] += msg.length();
                    intervalUnique.get(bucketKey).add(msg);
                } catch (Exception ignored) {}
            }

            List<Map<String, Object>> top5Senders = new ArrayList<>();
            senderCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(limit)
                    .forEach(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", e.getKey());
                        item.put("count", e.getValue());
                        top5Senders.add(item);
                    });

            List<Map<String, Object>> wordFreqList = new ArrayList<>();
            wordFreq.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(30)
                    .forEach(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("word", e.getKey());
                        item.put("count", e.getValue());
                        wordFreqList.add(item);
                    });

            List<Map<String, Object>> perIntervalData = new ArrayList<>();
            intervalMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("time", e.getKey());
                        int count = e.getValue()[0];
                        int totalLen = e.getValue()[1];
                        Set<String> uniq = intervalUnique.get(e.getKey());
                        int uniqueCount = uniq != null ? uniq.size() : 1;
                        double avgLen = count > 0 ? (double) totalLen / count : 0;
                        double uniqueRatio = count > 0 ? (double) uniqueCount / count : 0;
                        item.put("count", count);
                        item.put("avgLength", Math.round(avgLen * 10.0) / 10.0);
                        item.put("uniqueRatio", Math.round(uniqueRatio * 100.0) / 100.0);
                        perIntervalData.add(item);
                    });

            stats.put("userCount", userIds.size());
            stats.put("barrageCount", filteredRows.size());
            stats.put("totalChars", totalChars);
            stats.put("top5Senders", top5Senders);
            stats.put("wordFrequency", wordFreqList);
            stats.put("perIntervalData", perIntervalData);
            // danmaku scatter: per sender (color generated by frontend via uid hash)
            List<Map<String, Object>> danmakuScatter = new ArrayList<>();
            for (String uid : senderScatter.keySet()) {
                List<Map<String, Object>> pts = senderScatter.get(uid);
                if (pts.size() < 2) continue;
                Map<String, Object> series = new LinkedHashMap<>();
                series.put("uid", uid);
                series.put("name", uidNameMap.getOrDefault(uid, uid));
                series.put("points", pts);
                danmakuScatter.add(series);
            }
            stats.put("danmakuScatter", danmakuScatter);

            return Response.success(stats, req);
        } catch (Exception e) {
            LOGGER.error("getBarrageStatistics error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/importBarrageCsvFile")
    public Response<?> importBarrageCsvFile(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".csv")) {
                return Response.success(2, req);
            }
            String firstLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
                firstLine = reader.readLine();
            }
            if (firstLine == null || (!firstLine.contains("发送时间") || !firstLine.contains("弹幕"))) {
                return Response.success(3, req);
            }
            File danmujiLogDir = getDanmujiLogDir();
            if (!danmujiLogDir.exists()) {
                danmujiLogDir.mkdirs();
            }
            File destFile = new File(danmujiLogDir, originalFilename);
            file.transferTo(destFile);
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("importBarrageCsvFile error", e);
            return Response.success(1, req);
        }
    }

    // ========== 观众管理 CSV ==========

    @ResponseBody
    @GetMapping(value = "/listVisitorCsvFiles")
    public Response<?> listVisitorCsvFiles(HttpServletRequest req) {
        try {
            File danmujiLogDir = getDanmujiLogDir();
            List<Map<String, String>> fileList = new ArrayList<>();
            if (danmujiLogDir.exists() && danmujiLogDir.isDirectory()) {
                File[] files = danmujiLogDir.listFiles((dir, name) -> name.endsWith("观众信息.csv"));
                if (files != null) {
                    String currentRoomId = PublicDataConf.ROOMID != null ? PublicDataConf.ROOMID.toString() : "";
                    String currentAnchor = safeFileName(PublicDataConf.ANCHOR_NAME);
                    String currentPattern = currentRoomId + "_" + currentAnchor + "_4_观众信息.csv";
                    for (File f : files) {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("fileName", f.getName());
                        item.put("filePath", f.getAbsolutePath());
                        String name = f.getName();
                        int idx1 = name.indexOf('_');
                        int idx2 = name.indexOf('_', idx1 + 1);
                        if (idx1 > 0 && idx2 > idx1) {
                            item.put("roomId", name.substring(0, idx1));
                            item.put("anchorName", name.substring(idx1 + 1, idx2));
                        }
                        item.put("isCurrent", f.getName().equals(currentPattern) ? "true" : "false");
                        fileList.add(item);
                    }
                }
            }
            fileList.sort((a, b) -> {
                if ("true".equals(a.get("isCurrent"))) return -1;
                if ("true".equals(b.get("isCurrent"))) return 1;
                return a.get("fileName").compareTo(b.get("fileName"));
            });
            return Response.success(fileList, req);
        } catch (Exception e) {
            LOGGER.error("listVisitorCsvFiles error", e);
            return Response.success(Collections.emptyList(), req);
        }
    }

    private int compareField(String a, String b, boolean numeric) {
        if (numeric) {
            try {
                return Long.compare(Long.parseLong(a), Long.parseLong(b));
            } catch (NumberFormatException e) {}
        }
        return a.compareTo(b);
    }

    @ResponseBody
    @GetMapping(value = "/readVisitorCsvData")
    public Response<?> readVisitorCsvData(@RequestParam("filePath") String filePath,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String endTime,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String sortField,
                                          @RequestParam(required = false) String sortOrder,
                                          HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, String>> allRows = new ArrayList<>();
            String[] headers = {"最近", "id", "观众", "打分", "打分类型", "次数", "判定表", "场次"};
            String firstTime = null;
            String lastTime = null;
            String filteredFirstTime = null;
            String filteredLastTime = null;

            if (!file.exists()) {
                result.put("headers", headers);
                result.put("rows", Collections.emptyList());
                result.put("total", 0);
                result.put("totalPages", 0);
                result.put("currentPage", page);
                result.put("firstTime", "");
                result.put("lastTime", "");
                result.put("filteredFirstTime", "");
                result.put("filteredLastTime", "");
                return Response.success(result, req);
            }

            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line = reader.readLine(); // skip header + BOM
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 7) continue;
                    String time = fields.get(0);
                    if (firstTime == null) firstTime = time;
                    lastTime = time;
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    if (searchLower != null) {
                        boolean match = false;
                        for (String f : fields) {
                            if (f.toLowerCase().contains(searchLower)) { match = true; break; }
                        }
                        if (!match) continue;
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("最近", time);
                    row.put("id", fields.get(1));
                    row.put("观众", fields.get(2));
                    row.put("打分", fields.get(3));
                    row.put("打分类型", fields.get(4));
                    row.put("次数", fields.get(5));
                    row.put("判定表", fields.get(6));
                    row.put("场次", fields.size() >= 8 ? fields.get(7) : "1");
                    allRows.add(row);
                    if (filteredFirstTime == null) filteredFirstTime = time;
                    filteredLastTime = time;
                }
            }

            // sort: default = 最近 asc, id asc
            String sf = (sortField != null && !sortField.isEmpty()) ? sortField : "最近";
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : false;
            boolean isDefSort = sortField == null || sortField.isEmpty();
            allRows.sort((a, b) -> {
                int cmp;
                switch (sf) {
                    case "id": case "打分": case "次数": case "场次":
                        cmp = compareField(a.get(sf), b.get(sf), true);
                        break;
                    case "打分类型": case "判定表":
                        cmp = compareField(a.get(sf), b.get(sf), false);
                        break;
                    case "观众":
                        cmp = compareField(b.get(sf), a.get(sf), false);
                        break;
                    default: // 最近 (time)
                        cmp = compareField(a.get("最近"), b.get("最近"), false);
                        break;
                }
                if (cmp == 0 && isDefSort) {
                    cmp = compareField(a.get("id"), b.get("id"), true);
                }
                return asc ? cmp : -cmp;
            });

            int total = allRows.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (page < 1) page = 1;
            if (page > totalPages && totalPages > 0) page = totalPages;
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<Map<String, String>> pageRows = total > 0 ? allRows.subList(fromIndex, toIndex) : Collections.emptyList();

            result.put("headers", headers);
            result.put("rows", pageRows);
            result.put("total", total);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("firstTime", firstTime != null ? firstTime : "");
            result.put("lastTime", lastTime != null ? lastTime : "");
            result.put("filteredFirstTime", filteredFirstTime != null ? filteredFirstTime : "");
            result.put("filteredLastTime", filteredLastTime != null ? filteredLastTime : "");
            // live start time for default filter
            String liveStartTime = "";
            if (PublicDataConf.ROOM_INFO != null && PublicDataConf.ROOM_INFO.getLive_start_time() != null
                    && PublicDataConf.ROOM_INFO.getLive_start_time() > 0) {
                liveStartTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(PublicDataConf.ROOM_INFO.getLive_start_time() * 1000L));
            }
            result.put("liveStartTime", liveStartTime);
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("readVisitorCsvData error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/deleteVisitorCsvRow")
    public Response<?> deleteVisitorCsvRow(@RequestParam("filePath") String filePath,
                                           @RequestParam("timeKey") String timeKey,
                                           @RequestParam("uidKey") String uidKey,
                                           HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            // 先移除内存缓存中的记录
            try { VisitorCountTools.removeByUid(Long.parseLong(uidKey)); } catch (NumberFormatException ignored) {}
            // 直接修改 CSV 文件（保留对任意文件路径的支持）
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            if (!file.exists()) return Response.success(false, req);

            List<String> keepLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                boolean isFirst = true;
                while ((line = reader.readLine()) != null) {
                    if (isFirst) { isFirst = false; keepLines.add(line); continue; }
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() >= 7 && fields.get(0).equals(timeKey) && fields.get(1).equals(uidKey)) continue;
                    keepLines.add(line);
                }
            }
            File tmpFile = new File(filePath + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                for (String l : keepLines) { writer.write(l); writer.newLine(); }
            }
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Response.success(true, req);
        } catch (Exception e) {
            LOGGER.error("deleteVisitorCsvRow error", e);
            return Response.success(false, req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/exportVisitorFilteredCsv")
    public void exportVisitorFilteredCsv(@RequestParam("filePath") String filePath,
                                         @RequestParam(required = false) String startTime,
                                         @RequestParam(required = false) String endTime,
                                         @RequestParam(required = false) String search,
                                         HttpServletResponse response) throws Exception {
        validateFilePath(filePath);
        File file = new File(filePath);
        if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
        String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
        File tmpFile = File.createTempFile("vst-export-", ".csv");
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                String headerLine = reader.readLine();
                writer.write('﻿'); writer.write("最近,id,观众,打分,打分类型,次数,判定表,场次"); writer.newLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() >= 7) {
                        String time = fields.get(0);
                        if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                        if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                        if (searchLower != null) {
                            boolean match = false;
                            for (String f : fields) { if (f.toLowerCase().contains(searchLower)) { match = true; break; } }
                            if (!match) continue;
                        }
                        writer.write(line); writer.newLine();
                    }
                }
            }
            String downloadName = file.getName();
            FileInputStream fis = new FileInputStream(tmpFile);
            BufferedInputStream bis = new BufferedInputStream(fis);
            byte[] buffer = new byte[bis.available()];
            bis.read(buffer); bis.close();
            response.reset();
            response.setCharacterEncoding("UTF-8");
            response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(downloadName, "UTF-8"));
            response.addHeader("Content-Length", "" + tmpFile.length());
            response.setContentType("application/octet-stream");
            OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
            outputStream.write(buffer); outputStream.flush();
        } finally { tmpFile.delete(); }
    }

    @ResponseBody
    @GetMapping(value = "/getVisitorStatistics")
    public Response<?> getVisitorStatistics(@RequestParam("filePath") String filePath,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime,
                                            @RequestParam(defaultValue = "15") int limit,
                                            HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            Map<String, Object> stats = new LinkedHashMap<>();
            if (!file.exists()) {
                stats.put("totalVisits", 0L); stats.put("actualPeople", 0); stats.put("avgPerMin", 0.0);
                stats.put("scoreSum", 0L); stats.put("scoreAvg", 0.0);
                stats.put("pnYes", 0); stats.put("pnNo", 0);
                stats.put("perIntervalData", Collections.emptyList());
                stats.put("scatterData", Collections.emptyList());
                stats.put("top15Visitors", Collections.emptyList());
                stats.put("fieldRanking", Collections.emptyList());
                stats.put("scoreDistribution", Collections.emptyList());
                stats.put("visitCountDist", Collections.emptyList());
                stats.put("fieldCountDist", Collections.emptyList());
                return Response.success(stats, req);
            }

            List<String[]> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 7) continue;
                    String time = fields.get(0);
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    rows.add(new String[]{time, fields.get(1), fields.get(2), fields.get(3), fields.get(4), fields.get(5), fields.get(6), fields.size() >= 8 ? fields.get(7) : "1"});
                }
            }

            if (rows.isEmpty()) {
                stats.put("totalVisits", 0L); stats.put("actualPeople", 0); stats.put("avgPerMin", 0.0);
                stats.put("scoreSum", 0L); stats.put("scoreAvg", 0.0);
                stats.put("pnYes", 0); stats.put("pnNo", 0);
                stats.put("perIntervalData", Collections.emptyList());
                stats.put("scatterData", Collections.emptyList());
                stats.put("top15Visitors", Collections.emptyList());
                stats.put("fieldRanking", Collections.emptyList());
                stats.put("scoreDistribution", Collections.emptyList());
                stats.put("visitCountDist", Collections.emptyList());
                stats.put("fieldCountDist", Collections.emptyList());
                return Response.success(stats, req);
            }

            long totalVisits = 0;
            long scoreSum = 0;
            int pnYes = 0, pnNo = 0;
            Map<String, Integer> visitorCounts = new LinkedHashMap<>();
            Map<String, Integer> visitorFieldCounts = new LinkedHashMap<>();
            Map<String, String> visitorLastTime = new LinkedHashMap<>();
            Map<Long, Integer> scoreDist = new LinkedHashMap<>();
            Map<Long, Integer> visitCountDist = new LinkedHashMap<>();
            Map<Long, Integer> fieldCountDist = new LinkedHashMap<>();
            Map<String, int[]> intervalMap = new LinkedHashMap<>();
            List<Map<String, Object>> scatterData = new ArrayList<>();
            java.text.SimpleDateFormat bucketSdf = new java.text.SimpleDateFormat("MM-dd HH:mm");

            for (String[] row : rows) {
                String name = row[2];
                try { visitorCounts.put(name, visitorCounts.getOrDefault(name, 0) + Integer.parseInt(row[5])); } catch (NumberFormatException e) { visitorCounts.put(name, visitorCounts.getOrDefault(name, 0) + 1); }
                try {
                    int fc = Integer.parseInt(row.length >= 8 ? row[7] : "1");
                    visitorFieldCounts.put(name, visitorFieldCounts.getOrDefault(name, 0) + fc);
                } catch (NumberFormatException e) {}
                visitorLastTime.put(name, row[0]); // last time wins
                try { totalVisits += Long.parseLong(row[5]); } catch (NumberFormatException e) {}
                try { scoreSum += Long.parseLong(row[3]); } catch (NumberFormatException e) {}
                if ("是".equals(row[6])) pnYes++; else pnNo++;

                // score distribution
                try { long sc = Long.parseLong(row[3]); scoreDist.put(sc, scoreDist.getOrDefault(sc, 0) + 1); } catch (NumberFormatException e) {}
                // visit count distribution
                try { long vc = Long.parseLong(row[5]); visitCountDist.put(vc, visitCountDist.getOrDefault(vc, 0) + 1); } catch (NumberFormatException e) {}
                // field count distribution
                try { long fc = Long.parseLong(row.length >= 8 ? row[7] : "1"); fieldCountDist.put(fc, fieldCountDist.getOrDefault(fc, 0) + 1); } catch (NumberFormatException e) {}

                // per-interval
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    long ms = sdf.parse(row[0]).getTime();
                    long bucketMs = (ms / 60000L) * 60000L;
                    String bk = bucketSdf.format(new java.util.Date(bucketMs));
                    int[] iv = intervalMap.get(bk);
                    if (iv == null) { iv = new int[2]; intervalMap.put(bk, iv); }
                    try { iv[0] += Integer.parseInt(row[5]); } catch (NumberFormatException e) { iv[0]++; }
                    try { iv[1] += Integer.parseInt(row[3]); } catch (NumberFormatException e) {}
                } catch (Exception ignored) {}

                // scatter data
                Map<String, Object> pt = new LinkedHashMap<>();
                pt.put("time", row[0]);
                try { pt.put("score", Long.parseLong(row[3])); } catch (NumberFormatException e) { pt.put("score", 0); }
                pt.put("name", name);
                scatterData.add(pt);
            }

            long actualPeople = rows.size();
            double timeSpanMin = 0;
            double avgPerMin = 0;
            if (rows.size() > 1) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    long firstMs = sdf.parse(rows.get(0)[0]).getTime();
                    long lastMs = sdf.parse(rows.get(rows.size() - 1)[0]).getTime();
                    timeSpanMin = (lastMs - firstMs) / 60000.0;
                    if (timeSpanMin > 0) avgPerMin = Math.round(totalVisits / timeSpanMin * 10.0) / 10.0;
                } catch (Exception ignored) {}
            }
            double scoreAvg = actualPeople > 0 ? Math.round(scoreSum * 10.0 / actualPeople) / 10.0 : 0;

            // clamp ranking limit
            int effectiveLimit = Math.max(1, Math.min(limit, rows.size() / 2));
            // 进出榜 (by sum of 次数, tie-break time desc)
            List<Map<String, Object>> top15Visitors = new ArrayList<>();
            visitorCounts.entrySet().stream()
                    .sorted((a, b) -> { int c = b.getValue().compareTo(a.getValue()); if (c == 0) c = visitorLastTime.getOrDefault(b.getKey(),"").compareTo(visitorLastTime.getOrDefault(a.getKey(),"")); return c; })
                    .limit(effectiveLimit)
                    .forEach(e -> { Map<String, Object> item = new LinkedHashMap<>(); item.put("name", e.getKey()); item.put("count", e.getValue()); top15Visitors.add(item); });

            // 场次榜 (by sum of 场次, tie-break time desc)
            List<Map<String, Object>> fieldRanking = new ArrayList<>();
            visitorFieldCounts.entrySet().stream()
                    .sorted((a, b) -> { int c = b.getValue().compareTo(a.getValue()); if (c == 0) c = visitorLastTime.getOrDefault(b.getKey(),"").compareTo(visitorLastTime.getOrDefault(a.getKey(),"")); return c; })
                    .limit(effectiveLimit)
                    .forEach(e -> { Map<String, Object> item = new LinkedHashMap<>(); item.put("name", e.getKey()); item.put("count", e.getValue()); fieldRanking.add(item); });

            // score distribution
            List<Map<String, Object>> scoreDistList = new ArrayList<>();
            scoreDist.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("score", e.getKey()); item.put("count", e.getValue());
                scoreDistList.add(item);
            });

            List<Map<String, Object>> visitCountDistList = new ArrayList<>();
            visitCountDist.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("count", e.getKey()); item.put("freq", e.getValue());
                visitCountDistList.add(item);
            });

            List<Map<String, Object>> fieldCountDistList = new ArrayList<>();
            fieldCountDist.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("count", e.getKey()); item.put("freq", e.getValue());
                fieldCountDistList.add(item);
            });

            // per-interval
            List<Map<String, Object>> perIntervalData = new ArrayList<>();
            intervalMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("time", e.getKey());
                item.put("count", e.getValue()[0]);
                item.put("avgScore", e.getValue()[0] > 0 ? Math.round((double) e.getValue()[1] / e.getValue()[0] * 10.0) / 10.0 : 0);
                perIntervalData.add(item);
            });

            stats.put("totalVisits", totalVisits);
            stats.put("actualPeople", actualPeople);
            stats.put("avgPerMin", avgPerMin);
            stats.put("scoreSum", scoreSum);
            stats.put("scoreAvg", scoreAvg);
            stats.put("pnYes", pnYes);
            stats.put("pnNo", pnNo);
            stats.put("perIntervalData", perIntervalData);
            stats.put("scatterData", scatterData);
            stats.put("top15Visitors", top15Visitors);
            stats.put("fieldRanking", fieldRanking);
            stats.put("scoreDistribution", scoreDistList);
            stats.put("visitCountDist", visitCountDistList);
            stats.put("fieldCountDist", fieldCountDistList);

            return Response.success(stats, req);
        } catch (Exception e) {
            LOGGER.error("getVisitorStatistics error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/importVisitorCsvFile")
    public Response<?> importVisitorCsvFile(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".csv")) return Response.success(2, req);
            String firstLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
                firstLine = reader.readLine();
            }
            if (firstLine == null || (!firstLine.contains("最近") || !firstLine.contains("观众") || !firstLine.contains("场次"))) return Response.success(3, req);
            File danmujiLogDir = getDanmujiLogDir();
            if (!danmujiLogDir.exists()) danmujiLogDir.mkdirs();
            File destFile = new File(danmujiLogDir, originalFilename);
            file.transferTo(destFile);
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("importVisitorCsvFile error", e);
            return Response.success(1, req);
        }
    }

    // ========== 匹配管理 CSV ==========

    @ResponseBody
    @GetMapping(value = "/listMatchCsvFiles")
    public Response<?> listMatchCsvFiles(HttpServletRequest req) {
        try {
            File danmujiLogDir = getDanmujiLogDir();
            List<Map<String, String>> fileList = new ArrayList<>();
            if (danmujiLogDir.exists() && danmujiLogDir.isDirectory()) {
                File[] files = danmujiLogDir.listFiles((dir, name) -> name.endsWith("匹配信息.csv"));
                if (files != null) {
                    String currentRoomId = PublicDataConf.ROOMID != null ? PublicDataConf.ROOMID.toString() : "";
                    String currentAnchor = safeFileName(PublicDataConf.ANCHOR_NAME);
                    String currentPattern = currentRoomId + "_" + currentAnchor + "_5_匹配信息.csv";
                    for (File f : files) {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("fileName", f.getName());
                        item.put("filePath", f.getAbsolutePath());
                        String name = f.getName();
                        int idx1 = name.indexOf('_');
                        int idx2 = name.indexOf('_', idx1 + 1);
                        item.put("roomId", idx1 > 0 ? name.substring(0, idx1) : "");
                        item.put("anchorName", idx2 > idx1 ? name.substring(idx1 + 1, idx2) : "");
                        item.put("isCurrent", f.getName().equals(currentPattern) ? "1" : "0");
                        fileList.add(item);
                    }
                }
            }
            fileList.sort((a, b) -> {
                if ("1".equals(a.get("isCurrent")) && !"1".equals(b.get("isCurrent"))) return -1;
                if (!"1".equals(a.get("isCurrent")) && "1".equals(b.get("isCurrent"))) return 1;
                return b.get("fileName").compareTo(a.get("fileName"));
            });
            return Response.success(fileList, req);
        } catch (Exception e) {
            LOGGER.error("listMatchCsvFiles error", e);
            return Response.success(Collections.emptyList(), req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/readMatchCsvData")
    public Response<?> readMatchCsvData(@RequestParam("filePath") String filePath,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String startTime,
                                         @RequestParam(required = false) String endTime,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) String sortField,
                                         @RequestParam(required = false) String sortOrder,
                                         HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, String>> allRows = new ArrayList<>();
            String[] headers = {"最近匹配", "匹配id", "匹配名", "匹配分", "匹配次数"};
            String firstTime = null;
            String lastTime = null;
            String filteredFirstTime = null;
            String filteredLastTime = null;

            if (!file.exists()) {
                result.put("headers", headers);
                result.put("rows", Collections.emptyList());
                result.put("total", 0);
                result.put("totalPages", 0);
                result.put("currentPage", page);
                result.put("firstTime", "");
                result.put("lastTime", "");
                result.put("filteredFirstTime", "");
                result.put("filteredLastTime", "");
                return Response.success(result, req);
            }

            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line = reader.readLine(); // skip header + BOM
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 5) continue;
                    String time = fields.get(0);
                    if (firstTime == null) firstTime = time;
                    lastTime = time;
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    if (searchLower != null) {
                        boolean match = false;
                        for (String f : fields) {
                            if (f.toLowerCase().contains(searchLower)) { match = true; break; }
                        }
                        if (!match) continue;
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("最近匹配", time);
                    row.put("匹配id", fields.get(1));
                    row.put("匹配名", fields.get(2));
                    row.put("匹配分", fields.get(3));
                    row.put("匹配次数", fields.get(4));
                    allRows.add(row);
                    if (filteredFirstTime == null) filteredFirstTime = time;
                    filteredLastTime = time;
                }
            }

            // sort: default = 最近匹配 asc, 匹配id asc
            String sf = (sortField != null && !sortField.isEmpty()) ? sortField : "最近匹配";
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : false;
            boolean isDefSort = sortField == null || sortField.isEmpty();
            allRows.sort((a, b) -> {
                int cmp;
                switch (sf) {
                    case "匹配id": case "匹配分": case "匹配次数":
                        cmp = compareField(a.get(sf), b.get(sf), true);
                        break;
                    case "匹配名":
                        cmp = compareField(b.get(sf), a.get(sf), false);
                        break;
                    default: // 最近匹配 (time)
                        cmp = compareField(a.get("最近匹配"), b.get("最近匹配"), false);
                        break;
                }
                if (cmp == 0 && isDefSort) {
                    cmp = compareField(a.get("匹配id"), b.get("匹配id"), true);
                }
                return asc ? cmp : -cmp;
            });

            int total = allRows.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (page < 1) page = 1;
            if (page > totalPages && totalPages > 0) page = totalPages;
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<Map<String, String>> pageRows = total > 0 ? allRows.subList(fromIndex, toIndex) : Collections.emptyList();

            result.put("headers", headers);
            result.put("rows", pageRows);
            result.put("total", total);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("firstTime", firstTime != null ? firstTime : "");
            result.put("lastTime", lastTime != null ? lastTime : "");
            result.put("filteredFirstTime", filteredFirstTime != null ? filteredFirstTime : "");
            result.put("filteredLastTime", filteredLastTime != null ? filteredLastTime : "");
            String liveStartTime = "";
            if (PublicDataConf.ROOM_INFO != null && PublicDataConf.ROOM_INFO.getLive_start_time() != null
                    && PublicDataConf.ROOM_INFO.getLive_start_time() > 0) {
                liveStartTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(PublicDataConf.ROOM_INFO.getLive_start_time() * 1000L));
            }
            result.put("liveStartTime", liveStartTime);
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("readMatchCsvData error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/deleteMatchCsvRow")
    public Response<?> deleteMatchCsvRow(@RequestParam("filePath") String filePath,
                                          @RequestParam("timeKey") String timeKey,
                                          @RequestParam("uidKey") String uidKey,
                                          HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            // 先移除内存缓存中的记录
            try { MatchCountTools.removeByUid(Long.parseLong(uidKey)); } catch (NumberFormatException ignored) {}
            // 直接修改 CSV 文件（保留对任意文件路径的支持）
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            if (!file.exists()) return Response.success(0, req);

            List<String> keepLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                boolean isFirst = true;
                while ((line = reader.readLine()) != null) {
                    if (isFirst) { isFirst = false; keepLines.add(line); continue; }
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() >= 5 && fields.get(0).equals(timeKey) && fields.get(1).equals(uidKey)) continue;
                    keepLines.add(line);
                }
            }
            File tmpFile = new File(file.getAbsolutePath() + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                for (String l : keepLines) { writer.write(l); writer.newLine(); }
            }
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("deleteMatchCsvRow error", e);
            return Response.success(1, req);
        }
    }

    @GetMapping(value = "/exportMatchFilteredCsv")
    public void exportMatchFilteredCsv(@RequestParam("filePath") String filePath,
                                        @RequestParam(required = false) String startTime,
                                        @RequestParam(required = false) String endTime,
                                        @RequestParam(required = false) String search,
                                        HttpServletResponse response) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            if (!file.exists()) { response.setStatus(404); return; }

            File tmpFile = File.createTempFile("mtch-export-", ".csv");
            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                writer.write('﻿');
                writer.write("最近匹配,匹配id,匹配名,匹配分,匹配次数");
                writer.newLine();
                String line = reader.readLine(); // skip header
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 5) continue;
                    String time = fields.get(0);
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    if (searchLower != null) {
                        boolean match = false;
                        for (String f : fields) { if (f.toLowerCase().contains(searchLower)) { match = true; break; } }
                        if (!match) continue;
                    }
                    writer.write(line); writer.newLine();
                }
            }

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + java.net.URLEncoder.encode(file.getName(), "UTF-8"));
            try (InputStream is = new FileInputStream(tmpFile);
                 OutputStream os = response.getOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            }
            tmpFile.delete();
        } catch (Exception e) {
            LOGGER.error("exportMatchFilteredCsv error", e);
        }
    }

    @ResponseBody
    @GetMapping(value = "/getMatchStatistics")
    public Response<?> getMatchStatistics(@RequestParam("filePath") String filePath,
                                           @RequestParam(required = false) String startTime,
                                           @RequestParam(required = false) String endTime,
                                           @RequestParam(defaultValue = "10") int limit,
                                           HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            Map<String, Object> stats = new LinkedHashMap<>();
            if (!file.exists()) {
                stats.put("totalRecords", 0); stats.put("totalMatches", 0L); stats.put("uniqueUsers", 0);
                stats.put("scoreSum", 0L); stats.put("scoreAvg", 0.0);
                stats.put("scoreDistribution", Collections.emptyList());
                stats.put("matchCountDist", Collections.emptyList());
                stats.put("topMatches", Collections.emptyList());
                return Response.success(stats, req);
            }

            List<String[]> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 5) continue;
                    String time = fields.get(0);
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    rows.add(new String[]{time, fields.get(1), fields.get(2), fields.get(3), fields.get(4)});
                }
            }

            if (rows.isEmpty()) {
                stats.put("totalRecords", 0); stats.put("totalMatches", 0L); stats.put("uniqueUsers", 0);
                stats.put("scoreSum", 0L); stats.put("scoreAvg", 0.0);
                stats.put("scoreDistribution", Collections.emptyList());
                stats.put("matchCountDist", Collections.emptyList());
                stats.put("topMatches", Collections.emptyList());
                return Response.success(stats, req);
            }

            long totalMatches = 0;
            long scoreSum = 0;
            Set<String> uniqueUsers = new HashSet<>();
            Map<String, String> uidNameMap = new LinkedHashMap<>();
            Map<String, Integer> uidCountMap = new LinkedHashMap<>();
            Map<Long, Integer> scoreDist = new LinkedHashMap<>();
            Map<Long, Integer> matchCountDist = new LinkedHashMap<>();

            for (String[] row : rows) {
                String uid = row[1];
                String name = row[2];
                int score = 0, count = 0;
                try { score = Integer.parseInt(row[3]); } catch (NumberFormatException e) {}
                try { count = Integer.parseInt(row[4]); } catch (NumberFormatException e) {}
                uniqueUsers.add(uid);
                uidNameMap.put(uid, name);
                uidCountMap.put(uid, uidCountMap.getOrDefault(uid, 0) + count);
                totalMatches += count;
                scoreSum += score;
                long scKey = (long) score;
                scoreDist.put(scKey, scoreDist.getOrDefault(scKey, 0) + 1);
                long mcKey = (long) count;
                matchCountDist.put(mcKey, matchCountDist.getOrDefault(mcKey, 0) + 1);
            }

            // score distribution
            List<Map<String, Object>> scoreDistList = new ArrayList<>();
            scoreDist.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("score", e.getKey());
                item.put("count", e.getValue());
                scoreDistList.add(item);
            });

            // match count distribution
            List<Map<String, Object>> matchCountDistList = new ArrayList<>();
            matchCountDist.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("count", e.getKey());
                item.put("freq", e.getValue());
                matchCountDistList.add(item);
            });

            // top matches by count sum
            List<Map<String, Object>> topMatches = new ArrayList<>();
            uidCountMap.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(limit)
                    .forEach(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", uidNameMap.getOrDefault(e.getKey(), e.getKey()));
                        item.put("count", e.getValue());
                        topMatches.add(item);
                    });

            stats.put("totalRecords", rows.size());
            stats.put("totalMatches", totalMatches);
            stats.put("uniqueUsers", uniqueUsers.size());
            stats.put("scoreSum", scoreSum);
            stats.put("scoreAvg", uniqueUsers.size() > 0 ? Math.round(scoreSum * 10.0 / uniqueUsers.size()) / 10.0 : 0.0);
            stats.put("scoreDistribution", scoreDistList);
            stats.put("matchCountDist", matchCountDistList);
            stats.put("topMatches", topMatches);
            return Response.success(stats, req);
        } catch (Exception e) {
            LOGGER.error("getMatchStatistics error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/importMatchCsvFile")
    public Response<?> importMatchCsvFile(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".csv")) return Response.success(2, req);
            String firstLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
                firstLine = reader.readLine();
            }
            if (firstLine == null || (!firstLine.contains("最近匹配") || !firstLine.contains("匹配id") || !firstLine.contains("匹配次数")))
                return Response.success(3, req);
            File danmujiLogDir = getDanmujiLogDir();
            if (!danmujiLogDir.exists()) danmujiLogDir.mkdirs();
            File destFile = new File(danmujiLogDir, originalFilename);
            file.transferTo(destFile);
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("importMatchCsvFile error", e);
            return Response.success(1, req);
        }
    }

    // ========== 关注人管理 CSV ==========

    @ResponseBody
    @GetMapping(value = "/listFollowCsvFiles")
    public Response<?> listFollowCsvFiles(HttpServletRequest req) {
        try {
            File danmujiLogDir = getDanmujiLogDir();
            List<Map<String, String>> fileList = new ArrayList<>();
            if (danmujiLogDir.exists() && danmujiLogDir.isDirectory()) {
                File[] files = danmujiLogDir.listFiles((dir, name) -> name.endsWith("关注人信息.csv"));
                if (files != null) {
                    String currentRoomId = PublicDataConf.ROOMID != null ? PublicDataConf.ROOMID.toString() : "";
                    String currentAnchor = safeFileName(PublicDataConf.ANCHOR_NAME);
                    String currentPattern = currentRoomId + "_" + currentAnchor + "_6_关注人信息.csv";
                    for (File f : files) {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("fileName", f.getName());
                        item.put("filePath", f.getAbsolutePath());
                        String name = f.getName();
                        int idx1 = name.indexOf('_');
                        int idx2 = name.indexOf('_', idx1 + 1);
                        item.put("roomId", idx1 > 0 ? name.substring(0, idx1) : "");
                        item.put("anchorName", idx2 > idx1 ? name.substring(idx1 + 1, idx2) : "");
                        item.put("isCurrent", f.getName().equals(currentPattern) ? "1" : "0");
                        fileList.add(item);
                    }
                }
            }
            fileList.sort((a, b) -> {
                if ("1".equals(a.get("isCurrent")) && !"1".equals(b.get("isCurrent"))) return -1;
                if (!"1".equals(a.get("isCurrent")) && "1".equals(b.get("isCurrent"))) return 1;
                return b.get("fileName").compareTo(a.get("fileName"));
            });
            return Response.success(fileList, req);
        } catch (Exception e) {
            LOGGER.error("listFollowCsvFiles error", e);
            return Response.success(Collections.emptyList(), req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/readFollowCsvData")
    public Response<?> readFollowCsvData(@RequestParam("filePath") String filePath,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String endTime,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String sortField,
                                          @RequestParam(required = false) String sortOrder,
                                          HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, String>> allRows = new ArrayList<>();
            String[] headers = {"最新时间", "id", "名字", "次数"};
            String firstTime = null;
            String lastTime = null;
            String filteredFirstTime = null;
            String filteredLastTime = null;

            if (!file.exists()) {
                result.put("headers", headers);
                result.put("rows", Collections.emptyList());
                result.put("total", 0);
                result.put("totalPages", 0);
                result.put("currentPage", page);
                result.put("firstTime", "");
                result.put("lastTime", "");
                result.put("filteredFirstTime", "");
                result.put("filteredLastTime", "");
                return Response.success(result, req);
            }

            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line = reader.readLine(); // skip header + BOM
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 4) continue;
                    String time = fields.get(0);
                    if (firstTime == null) firstTime = time;
                    lastTime = time;
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    if (searchLower != null) {
                        boolean match = false;
                        for (String f : fields) {
                            if (f.toLowerCase().contains(searchLower)) { match = true; break; }
                        }
                        if (!match) continue;
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("最新时间", time);
                    row.put("id", fields.get(1));
                    row.put("名字", fields.get(2));
                    row.put("次数", fields.get(3));
                    allRows.add(row);
                    if (filteredFirstTime == null) filteredFirstTime = time;
                    filteredLastTime = time;
                }
            }

            String sf = (sortField != null && !sortField.isEmpty()) ? sortField : "最新时间";
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : false;
            boolean isDefSort = sortField == null || sortField.isEmpty();
            allRows.sort((a, b) -> {
                int cmp;
                switch (sf) {
                    case "id": case "次数":
                        cmp = compareField(a.get(sf), b.get(sf), true);
                        break;
                    case "名字":
                        cmp = compareField(b.get(sf), a.get(sf), false);
                        break;
                    default: // 最新时间
                        cmp = compareField(a.get("最新时间"), b.get("最新时间"), false);
                        break;
                }
                if (cmp == 0 && isDefSort) {
                    cmp = compareField(a.get("id"), b.get("id"), true);
                }
                return asc ? cmp : -cmp;
            });

            int total = allRows.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (page < 1) page = 1;
            if (page > totalPages && totalPages > 0) page = totalPages;
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<Map<String, String>> pageRows = total > 0 ? allRows.subList(fromIndex, toIndex) : Collections.emptyList();

            result.put("headers", headers);
            result.put("rows", pageRows);
            result.put("total", total);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("firstTime", firstTime != null ? firstTime : "");
            result.put("lastTime", lastTime != null ? lastTime : "");
            result.put("filteredFirstTime", filteredFirstTime != null ? filteredFirstTime : "");
            result.put("filteredLastTime", filteredLastTime != null ? filteredLastTime : "");
            String liveStartTime = "";
            if (PublicDataConf.ROOM_INFO != null && PublicDataConf.ROOM_INFO.getLive_start_time() != null
                    && PublicDataConf.ROOM_INFO.getLive_start_time() > 0) {
                liveStartTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(PublicDataConf.ROOM_INFO.getLive_start_time() * 1000L));
            }
            result.put("liveStartTime", liveStartTime);
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("readFollowCsvData error", e);
            return Response.success(null, req);
        }
    }

    @GetMapping(value = "/exportFollowFilteredCsv")
    public void exportFollowFilteredCsv(@RequestParam("filePath") String filePath,
                                         @RequestParam(required = false) String startTime,
                                         @RequestParam(required = false) String endTime,
                                         @RequestParam(required = false) String search,
                                         HttpServletResponse response) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            if (!file.exists()) { response.setStatus(404); return; }

            File tmpFile = File.createTempFile("flw-export-", ".csv");
            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                writer.write('﻿');
                writer.write("最新时间,id,名字,次数");
                writer.newLine();
                String line = reader.readLine(); // skip header
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 4) continue;
                    String time = fields.get(0);
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    if (searchLower != null) {
                        boolean match = false;
                        for (String f : fields) { if (f.toLowerCase().contains(searchLower)) { match = true; break; } }
                        if (!match) continue;
                    }
                    writer.write(line); writer.newLine();
                }
            }

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + java.net.URLEncoder.encode(file.getName(), "UTF-8"));
            try (InputStream is = new FileInputStream(tmpFile);
                 OutputStream os = response.getOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            }
            tmpFile.delete();
        } catch (Exception e) {
            LOGGER.error("exportFollowFilteredCsv error", e);
        }
    }

    @ResponseBody
    @GetMapping(value = "/getFollowStatistics")
    public Response<?> getFollowStatistics(@RequestParam("filePath") String filePath,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime,
                                            @RequestParam(defaultValue = "10") int limit,
                                            HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            Map<String, Object> stats = new LinkedHashMap<>();
            if (!file.exists()) {
                stats.put("totalRecords", 0); stats.put("uniqueUsers", 0); stats.put("totalFollows", 0L);
                stats.put("countDist", Collections.emptyList());
                stats.put("topFollows", Collections.emptyList());
                return Response.success(stats, req);
            }

            List<String[]> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 4) continue;
                    String time = fields.get(0);
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    rows.add(new String[]{time, fields.get(1), fields.get(2), fields.get(3)});
                }
            }

            if (rows.isEmpty()) {
                stats.put("totalRecords", 0); stats.put("uniqueUsers", 0); stats.put("totalFollows", 0L);
                stats.put("countDist", Collections.emptyList());
                stats.put("topFollows", Collections.emptyList());
                return Response.success(stats, req);
            }

            long totalFollows = 0;
            Set<String> uniqueUsers = new HashSet<>();
            Map<String, String> uidNameMap = new LinkedHashMap<>();
            Map<String, Integer> uidCountMap = new LinkedHashMap<>();
            Map<Long, Integer> countDist = new LinkedHashMap<>();

            for (String[] row : rows) {
                String uid = row[1];
                String name = row[2];
                int count = 0;
                try { count = Integer.parseInt(row[3]); } catch (NumberFormatException e) {}
                uniqueUsers.add(uid);
                uidNameMap.put(uid, name);
                uidCountMap.put(uid, uidCountMap.getOrDefault(uid, 0) + count);
                totalFollows += count;
                long ck = (long) count;
                countDist.put(ck, countDist.getOrDefault(ck, 0) + 1);
            }

            List<Map<String, Object>> countDistList = new ArrayList<>();
            countDist.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("count", e.getKey());
                item.put("freq", e.getValue());
                countDistList.add(item);
            });

            List<Map<String, Object>> topFollows = new ArrayList<>();
            uidCountMap.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(limit)
                    .forEach(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", uidNameMap.getOrDefault(e.getKey(), e.getKey()));
                        item.put("count", e.getValue());
                        topFollows.add(item);
                    });

            stats.put("totalRecords", rows.size());
            stats.put("uniqueUsers", uniqueUsers.size());
            stats.put("totalFollows", totalFollows);
            stats.put("countDist", countDistList);
            stats.put("topFollows", topFollows);
            return Response.success(stats, req);
        } catch (Exception e) {
            LOGGER.error("getFollowStatistics error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/importFollowCsvFile")
    public Response<?> importFollowCsvFile(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".csv")) return Response.success(2, req);
            String firstLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
                firstLine = reader.readLine();
            }
            if (firstLine == null || (!firstLine.contains("最新时间") || !firstLine.contains("id") || !firstLine.contains("次数")))
                return Response.success(3, req);
            File danmujiLogDir = getDanmujiLogDir();
            if (!danmujiLogDir.exists()) danmujiLogDir.mkdirs();
            File destFile = new File(danmujiLogDir, originalFilename);
            file.transferTo(destFile);
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("importFollowCsvFile error", e);
            return Response.success(1, req);
        }
    }

    // ========== 礼物管理 CSV ==========

    @ResponseBody
    @GetMapping(value = "/listGiftCsvFiles")
    public Response<?> listGiftCsvFiles(HttpServletRequest req) {
        try {
            File danmujiLogDir = getDanmujiLogDir();
            List<Map<String, String>> fileList = new ArrayList<>();
            if (danmujiLogDir.exists() && danmujiLogDir.isDirectory()) {
                File[] files = danmujiLogDir.listFiles((dir, name) -> name.endsWith("礼物信息.csv"));
                if (files != null) {
                    String currentRoomId = PublicDataConf.ROOMID != null ? PublicDataConf.ROOMID.toString() : "";
                    String currentAnchor = safeFileName(PublicDataConf.ANCHOR_NAME);
                    String currentPattern = currentRoomId + "_" + currentAnchor + "_3_礼物信息.csv";
                    for (File f : files) {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("fileName", f.getName());
                        item.put("filePath", f.getAbsolutePath());
                        String name = f.getName();
                        int idx1 = name.indexOf('_');
                        int idx2 = name.indexOf('_', idx1 + 1);
                        item.put("roomId", idx1 > 0 ? name.substring(0, idx1) : "");
                        item.put("anchorName", idx2 > idx1 ? name.substring(idx1 + 1, idx2) : "");
                        item.put("isCurrent", f.getName().equals(currentPattern) ? "1" : "0");
                        fileList.add(item);
                    }
                }
            }
            fileList.sort((a, b) -> {
                if ("1".equals(a.get("isCurrent")) && !"1".equals(b.get("isCurrent"))) return -1;
                if (!"1".equals(a.get("isCurrent")) && "1".equals(b.get("isCurrent"))) return 1;
                return b.get("fileName").compareTo(a.get("fileName"));
            });
            return Response.success(fileList, req);
        } catch (Exception e) {
            LOGGER.error("listGiftCsvFiles error", e);
            return Response.success(Collections.emptyList(), req);
        }
    }

    @ResponseBody
    @GetMapping(value = "/readGiftCsvData")
    public Response<?> readGiftCsvData(@RequestParam("filePath") String filePath,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String startTime,
                                        @RequestParam(required = false) String endTime,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false) String sortField,
                                        @RequestParam(required = false) String sortOrder,
                                        HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, String>> allRows = new ArrayList<>();
            String[] headers = {"最新时间", "id", "名字", "赠送礼物名字", "电池", "赠礼次数"};
            String firstTime = null, lastTime = null;
            String filteredFirstTime = null, filteredLastTime = null;

            if (!file.exists()) {
                result.put("headers", headers);
                result.put("rows", Collections.emptyList());
                result.put("total", 0);
                result.put("totalPages", 0);
                result.put("currentPage", page);
                result.put("firstTime", "");
                result.put("lastTime", "");
                result.put("filteredFirstTime", "");
                result.put("filteredLastTime", "");
                return Response.success(result, req);
            }

            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line = reader.readLine();
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 6) continue;
                    String time = fields.get(0);
                    if (firstTime == null) firstTime = time;
                    lastTime = time;
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    if (searchLower != null) {
                        boolean match = false;
                        for (String f : fields) { if (f.toLowerCase().contains(searchLower)) { match = true; break; } }
                        if (!match) continue;
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("最新时间", time);
                    row.put("id", fields.get(1));
                    row.put("名字", fields.get(2));
                    row.put("赠送礼物名字", fields.get(3));
                    try {
                        long rawAmount = Long.parseLong(fields.get(4));
                        row.put("电池", String.valueOf(rawAmount / 100));
                    } catch (NumberFormatException e) {
                        row.put("电池", fields.get(4));
                    }
                    row.put("赠礼次数", fields.get(5));
                    allRows.add(row);
                    if (filteredFirstTime == null) filteredFirstTime = time;
                    filteredLastTime = time;
                }
            }

            String sf = (sortField != null && !sortField.isEmpty()) ? sortField : "最新时间";
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : false;
            boolean isDefSort = sortField == null || sortField.isEmpty();
            allRows.sort((a, b) -> {
                int cmp;
                switch (sf) {
                    case "id": case "电池": case "赠礼次数":
                        cmp = compareField(a.get(sf), b.get(sf), true);
                        break;
                    case "名字": case "赠送礼物名字":
                        cmp = compareField(a.get(sf), b.get(sf), false);
                        break;
                    default:
                        cmp = compareField(a.get("最新时间"), b.get("最新时间"), false);
                        break;
                }
                if (cmp == 0 && isDefSort) cmp = compareField(a.get("id"), b.get("id"), true);
                return asc ? cmp : -cmp;
            });

            int total = allRows.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (page < 1) page = 1;
            if (page > totalPages && totalPages > 0) page = totalPages;
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<Map<String, String>> pageRows = total > 0 ? allRows.subList(fromIndex, toIndex) : Collections.emptyList();

            result.put("headers", headers);
            result.put("rows", pageRows);
            result.put("total", total);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("firstTime", firstTime != null ? firstTime : "");
            result.put("lastTime", lastTime != null ? lastTime : "");
            result.put("filteredFirstTime", filteredFirstTime != null ? filteredFirstTime : "");
            result.put("filteredLastTime", filteredLastTime != null ? filteredLastTime : "");
            String liveStartTime = "";
            if (PublicDataConf.ROOM_INFO != null && PublicDataConf.ROOM_INFO.getLive_start_time() != null
                    && PublicDataConf.ROOM_INFO.getLive_start_time() > 0) {
                liveStartTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(PublicDataConf.ROOM_INFO.getLive_start_time() * 1000L));
            }
            result.put("liveStartTime", liveStartTime);
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("readGiftCsvData error", e);
            return Response.success(null, req);
        }
    }

    @GetMapping(value = "/exportGiftFilteredCsv")
    public void exportGiftFilteredCsv(@RequestParam("filePath") String filePath,
                                       @RequestParam(required = false) String startTime,
                                       @RequestParam(required = false) String endTime,
                                       @RequestParam(required = false) String search,
                                       HttpServletResponse response) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            if (!file.exists()) { response.setStatus(404); return; }
            File tmpFile = File.createTempFile("gft-export-", ".csv");
            String searchLower = (search != null && !search.isEmpty()) ? search.toLowerCase() : null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                writer.write('﻿');
                writer.write("最新时间,id,名字,赠送礼物名字,总金额,赠礼次数");
                writer.newLine();
                String line = reader.readLine();
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 6) continue;
                    String time = fields.get(0);
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    if (searchLower != null) {
                        boolean match = false;
                        for (String f : fields) { if (f.toLowerCase().contains(searchLower)) { match = true; break; } }
                        if (!match) continue;
                    }
                    writer.write(line); writer.newLine();
                }
            }
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + java.net.URLEncoder.encode(file.getName(), "UTF-8"));
            try (InputStream is = new FileInputStream(tmpFile); OutputStream os = response.getOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            }
            tmpFile.delete();
        } catch (Exception e) { LOGGER.error("exportGiftFilteredCsv error", e); }
    }

    @ResponseBody
    @GetMapping(value = "/getGiftStatistics")
    public Response<?> getGiftStatistics(@RequestParam("filePath") String filePath,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String endTime,
                                          @RequestParam(defaultValue = "10") int limit,
                                          HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), filePath);
            Map<String, Object> stats = new LinkedHashMap<>();
            if (!file.exists()) {
                stats.put("totalRecords", 0); stats.put("totalAmount", 0L); stats.put("uniqueUsers", 0); stats.put("uniqueGifts", 0);
                stats.put("amountRanking", Collections.emptyList());
                stats.put("giftNameFreq", Collections.emptyList());
                stats.put("perIntervalData", Collections.emptyList());
                return Response.success(stats, req);
            }

            List<String[]> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() < 6) continue;
                    String time = fields.get(0);
                    if (startTime != null && !startTime.isEmpty() && time.compareTo(startTime) < 0) continue;
                    if (endTime != null && !endTime.isEmpty() && time.compareTo(endTime) > 0) continue;
                    rows.add(new String[]{time, fields.get(1), fields.get(2), fields.get(3), fields.get(4), fields.get(5)});
                }
            }

            if (rows.isEmpty()) {
                stats.put("totalRecords", 0); stats.put("totalAmount", 0L); stats.put("uniqueUsers", 0); stats.put("uniqueGifts", 0);
                stats.put("amountRanking", Collections.emptyList());
                stats.put("giftNameFreq", Collections.emptyList());
                stats.put("perIntervalData", Collections.emptyList());
                return Response.success(stats, req);
            }

            long totalAmount = 0;
            Set<String> uniqueGifts = new HashSet<>();
            Map<String, String> uidNameMap = new LinkedHashMap<>();
            Map<String, Long> uidAmountMap = new LinkedHashMap<>();
            Map<String, Long> giftNameCountMap = new LinkedHashMap<>();
            Map<String, long[]> intervalMap = new LinkedHashMap<>();
            java.text.SimpleDateFormat bucketSdf = new java.text.SimpleDateFormat("MM-dd HH:mm");

            for (String[] row : rows) {
                String uid = row[1];
                String name = row[2];
                String giftName = row[3];
                long amount = 0; int count = 0;
                try { amount = Long.parseLong(row[4]) / 100; } catch (NumberFormatException e) {}
                try { count = Integer.parseInt(row[5]); } catch (NumberFormatException e) {}
                uniqueGifts.add(giftName);
                uidNameMap.put(uid, name);
                uidAmountMap.put(uid, uidAmountMap.getOrDefault(uid, 0L) + amount);
                giftNameCountMap.put(giftName, giftNameCountMap.getOrDefault(giftName, 0L) + count);
                totalAmount += amount;

                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    long ms = sdf.parse(row[0]).getTime();
                    long bucketMs = (ms / 60000L) * 60000L;
                    String bk = bucketSdf.format(new java.util.Date(bucketMs));
                    long[] iv = intervalMap.get(bk);
                    if (iv == null) { iv = new long[1]; intervalMap.put(bk, iv); }
                    iv[0] += amount;
                } catch (Exception ignored) {}
            }

            List<Map<String, Object>> amountRanking = new ArrayList<>();
            uidAmountMap.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(limit)
                    .forEach(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", uidNameMap.getOrDefault(e.getKey(), e.getKey()));
                        item.put("amount", e.getValue());
                        amountRanking.add(item);
                    });

            List<Map<String, Object>> giftNameFreq = new ArrayList<>();
            giftNameCountMap.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", e.getKey());
                        item.put("count", e.getValue());
                        giftNameFreq.add(item);
                    });

            stats.put("totalRecords", rows.size());
            stats.put("totalAmount", totalAmount);
            stats.put("uniqueUsers", uidNameMap.size());
            stats.put("uniqueGifts", uniqueGifts.size());
            List<Map<String, Object>> perIntervalData = new ArrayList<>();
            intervalMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("time", e.getKey());
                        item.put("amount", e.getValue()[0]);
                        perIntervalData.add(item);
                    });

            stats.put("amountRanking", amountRanking);
            stats.put("giftNameFreq", giftNameFreq);
            stats.put("perIntervalData", perIntervalData);
            return Response.success(stats, req);
        } catch (Exception e) {
            LOGGER.error("getGiftStatistics error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/importGiftCsvFile")
    public Response<?> importGiftCsvFile(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".csv")) return Response.success(2, req);
            String firstLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
                firstLine = reader.readLine();
            }
            if (firstLine == null || (!firstLine.contains("最新时间") || !firstLine.contains("赠送礼物名字") || !firstLine.contains("总金额")))
                return Response.success(3, req);
            File danmujiLogDir = getDanmujiLogDir();
            if (!danmujiLogDir.exists()) danmujiLogDir.mkdirs();
            File destFile = new File(danmujiLogDir, originalFilename);
            file.transferTo(destFile);
            return Response.success(0, req);
        } catch (Exception e) {
            LOGGER.error("importGiftCsvFile error", e);
            return Response.success(1, req);
        }
    }

    // ========== 实时陌生观众看板 ==========

    @ResponseBody
    @GetMapping(value = "/strangerViewerData")
    public Response<?> strangerViewerData(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(defaultValue = "time") String sortField,
                                          @RequestParam(defaultValue = "asc") String sortOrder,
                                          HttpServletRequest req) {
        try {
            Map<String, Object> data = xyz.acproject.danmuji.service.StrangerViewerService.getPageData(page, pageSize, search, sortField, sortOrder);
            return Response.success(data, req);
        } catch (Exception e) {
            LOGGER.error("strangerViewerData error", e);
            return Response.success(null, req);
        }
    }

    @ResponseBody
    @PostMapping(value = "/strangerViewerBlock")
    public Response<?> strangerViewerBlock(@RequestParam("uid") long uid, HttpServletRequest req) {
        try {
            boolean blocked = xyz.acproject.danmuji.service.StrangerViewerService.toggleBlock(uid);
            return Response.success(blocked ? 1 : 0, req);
        } catch (Exception e) {
            LOGGER.error("strangerViewerBlock error", e);
            return Response.success(-1, req);
        }
    }

    @Autowired
    public void setCheckService(SetService checkService) {
        this.checkService = checkService;
    }

    @Autowired
    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    @Autowired
    public void setTaskRegisterComponent(TaskRegisterComponent taskRegisterComponent) {
        this.taskRegisterComponent = taskRegisterComponent;
    }

    // ==================== 账号池管理 API ====================

    /**
     * 获取账号池展示数据（不含完整Cookie）
     */
    @ResponseBody
    @GetMapping(value = "/api/accountPool/list")
    public Response<?> accountPoolList(HttpServletRequest req) {
        try {
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            xyz.acproject.danmuji.conf.set.AccountPoolConf conf = pool.getPoolConf();
            com.alibaba.fastjson.JSONObject result = conf != null
                    ? conf.toDisplayJson()
                    : xyz.acproject.danmuji.conf.set.AccountPoolConf.createDefault().toDisplayJson();
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolList error", e);
            return Response.success(null, req);
        }
    }

    /**
     * 获取限流器和缓存统计
     */
    @ResponseBody
    @GetMapping(value = "/api/accountPool/stats")
    public Response<?> accountPoolStats(HttpServletRequest req) {
        try {
            com.alibaba.fastjson.JSONObject stats = xyz.acproject.danmuji.http.HttpRoomData.getRateLimiterStats();
            return Response.success(stats, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolStats error", e);
            return Response.success(null, req);
        }
    }

    /**
     * 添加子账号（自动验证Cookie并填充头像等信息）
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/add")
    public Response<?> accountPoolAdd(@RequestParam("uid") String uid,
                                      @RequestParam("name") String name,
                                      @RequestParam("cookie") String cookie,
                                      HttpServletRequest req) {
        try {
            if (StringUtils.isBlank(cookie)) {
                return Response.success(false, req);
            }
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            xyz.acproject.danmuji.entity.user_data.SubAccount account =
                    new xyz.acproject.danmuji.entity.user_data.SubAccount(uid, name, cookie);

            // 自动验证并填充信息
            String[] result = pool.validateCookie(cookie);
            if ("true".equals(result[0])) {
                if (StringUtils.isBlank(account.getUid()) && StringUtils.isNotBlank(result[1])) {
                    account.setUid(result[1]);
                }
                if (StringUtils.isBlank(account.getName()) && StringUtils.isNotBlank(result[2])) {
                    account.setName(result[2]);
                }
                if (StringUtils.isNotBlank(result[3])) {
                    account.setFace(result[3]);
                }
                if (result.length > 4 && StringUtils.isNotBlank(result[4])) {
                    try { account.setLevel(Integer.parseInt(result[4])); } catch (NumberFormatException ignored) {}
                }
                account.setValidated(true);
                account.setLastValidatedTime(System.currentTimeMillis());
            }

            boolean ok = pool.addAccount(account);
            if (ok) {
                xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
            }
            return Response.success(ok, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolAdd error", e);
            return Response.success(false, req);
        }
    }

    /**
     * 更新子账号（自动验证Cookie并更新头像等信息）
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/update")
    public Response<?> accountPoolUpdate(@RequestParam("uid") String uid,
                                         @RequestParam("name") String name,
                                         @RequestParam("cookie") String cookie,
                                         HttpServletRequest req) {
        try {
            if (StringUtils.isBlank(cookie) || StringUtils.isBlank(uid)) {
                return Response.success(false, req);
            }
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            xyz.acproject.danmuji.entity.user_data.SubAccount account =
                    new xyz.acproject.danmuji.entity.user_data.SubAccount(uid, name, cookie);

            // 自动验证并更新信息
            String[] result = pool.validateCookie(cookie);
            if ("true".equals(result[0])) {
                if (StringUtils.isNotBlank(result[1])) account.setUid(result[1]);
                if (StringUtils.isNotBlank(result[2])) account.setName(result[2]);
                if (StringUtils.isNotBlank(result[3])) account.setFace(result[3]);
                if (result.length > 4 && StringUtils.isNotBlank(result[4])) {
                    try { account.setLevel(Integer.parseInt(result[4])); } catch (NumberFormatException ignored) {}
                }
                account.setValidated(true);
                account.setLastValidatedTime(System.currentTimeMillis());
            }

            // 保留原有统计
            for (xyz.acproject.danmuji.entity.user_data.SubAccount existing : pool.getAllAccounts()) {
                if (uid.equals(existing.getUid())) {
                    account.setEnabled(existing.isEnabled());
                    account.setUseCount(existing.getUseCount());
                    account.setRateLimitedCount(existing.getRateLimitedCount());
                    if (!account.isValidated()) {
                        account.setValidated(existing.isValidated());
                        account.setLastValidatedTime(existing.getLastValidatedTime());
                    }
                    if (StringUtils.isBlank(account.getFace())) {
                        account.setFace(existing.getFace());
                    }
                    break;
                }
            }
            boolean ok = pool.updateAccount(uid, account);
            if (ok) {
                xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
            }
            return Response.success(ok, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolUpdate error", e);
            return Response.success(false, req);
        }
    }

    /**
     * 删除子账号
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/remove")
    public Response<?> accountPoolRemove(@RequestParam("uid") String uid, HttpServletRequest req) {
        try {
            if (StringUtils.isBlank(uid)) {
                return Response.success(false, req);
            }
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            boolean ok = pool.removeAccount(uid);
            if (ok) {
                xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
            }
            return Response.success(ok, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolRemove error", e);
            return Response.success(false, req);
        }
    }

    /**
     * 启用/禁用子账号
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/toggle")
    public Response<?> accountPoolToggle(@RequestParam("uid") String uid,
                                         @RequestParam("enabled") boolean enabled,
                                         HttpServletRequest req) {
        try {
            if (StringUtils.isBlank(uid)) {
                return Response.success(false, req);
            }
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            boolean ok = pool.setAccountEnabled(uid, enabled);
            if (ok) {
                xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
            }
            return Response.success(ok, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolToggle error", e);
            return Response.success(false, req);
        }
    }

    /**
     * 手动清除冷却状态
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/clearCooldown")
    public Response<?> accountPoolClearCooldown(@RequestParam("uid") String uid, HttpServletRequest req) {
        try {
            if (StringUtils.isBlank(uid)) {
                return Response.success(false, req);
            }
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            boolean ok = pool.clearCooldown(uid);
            if (ok) {
                xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
            }
            return Response.success(ok, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolClearCooldown error", e);
            return Response.success(false, req);
        }
    }

    /**
     * 验证Cookie有效性（返回uid, uname, face）
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/validate")
    public Response<?> accountPoolValidate(@RequestParam("cookie") String cookie, HttpServletRequest req) {
        try {
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            String[] result = pool.validateCookie(cookie);
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("valid", "true".equals(result[0]));
            json.put("uid", result[1]);
            json.put("uname", result[2]);
            json.put("face", result.length > 3 ? result[3] : "");
            json.put("level", result.length > 4 ? result[4] : "0");
            return Response.success(json, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolValidate error", e);
            return Response.success(null, req);
        }
    }

    /**
     * 切换主账号：将指定子账号提升为主账号，原主账号降级为子账号。
     * 会更新全局Cookie状态并持久化，影响所有功能模块。
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/switchMain")
    public Response<?> accountPoolSwitchMain(@RequestParam("uid") String uid, HttpServletRequest req) {
        try {
            if (StringUtils.isBlank(uid)) {
                return Response.success(false, req);
            }
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            String[] result = pool.prepareSwitchMain(uid);
            if (result == null) {
                com.alibaba.fastjson.JSONObject err = new com.alibaba.fastjson.JSONObject();
                err.put("success", false);
                err.put("message", "切换失败：目标账号不存在、已禁用或Cookie已失效");
                return Response.success(err, req);
            }

            String newCookie = result[0];
            String newUid = result[1];
            String newName = result[2];
            String newFace = result[3];
            String newLevel = result.length > 4 ? result[4] : "0";

            // 1. 更新全局Cookie
            PublicDataConf.USERCOOKIE = newCookie;

            // 2. 重新解析Cookie
            xyz.acproject.danmuji.tools.CurrencyTools.parseCookie(newCookie);

            // 3. 调用B站API验证并获取完整用户信息
            xyz.acproject.danmuji.http.HttpUserData.httpGetUserInfo();

            // 4. 获取buvid等补充cookie信息
            if (PublicDataConf.USER != null && PublicDataConf.COOKIE != null) {
                PublicDataConf.COOKIE = xyz.acproject.danmuji.http.HttpUserData.httpBuvid34(PublicDataConf.COOKIE);
                if (PublicDataConf.COOKIE != null) {
                    PublicDataConf.USERCOOKIE = PublicDataConf.COOKIE.getCookie();
                }
            }

            // 5. 持久化到主配置文件和账号池文件
            checkService.changeSet(PublicDataConf.centerSetConf, false);

            // 6. 更新限流器配置
            xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());

            // 7. 更新主账号session
            if (PublicDataConf.USER != null) {
                req.getSession().setAttribute("status", "login");
            }

            com.alibaba.fastjson.JSONObject resp = new com.alibaba.fastjson.JSONObject();
            resp.put("success", true);
            resp.put("uid", newUid);
            resp.put("name", newName);
            resp.put("face", newFace != null ? newFace : "");
            resp.put("level", newLevel != null ? newLevel : "0");
            resp.put("message", "已切换主账号为: " + newName + "（原主账号已降级为子账号）");
            LOGGER.info("主账号切换成功: {} -> {}", newUid, newName);
            return Response.success(resp, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolSwitchMain error", e);
            com.alibaba.fastjson.JSONObject err = new com.alibaba.fastjson.JSONObject();
            err.put("success", false);
            err.put("message", "切换异常: " + e.getMessage());
            return Response.success(err, req);
        }
    }

    /**
     * 更新账号池全局配置
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/config")
    public Response<?> accountPoolConfig(@RequestParam("enabled") boolean enabled,
                                         @RequestParam("cooldownSeconds") int cooldownSeconds,
                                         @RequestParam("dynamicRate") double dynamicRate,
                                         @RequestParam("cardRate") double cardRate,
                                         @RequestParam("cacheTtlSeconds") int cacheTtlSeconds,
                                         HttpServletRequest req) {
        try {
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            xyz.acproject.danmuji.conf.set.AccountPoolConf conf = pool.getPoolConf();
            if (conf != null) {
                conf.setEnabled(enabled);
                conf.setCooldownSeconds(Math.max(60, cooldownSeconds));
                conf.setDynamicRate(Math.max(0.1, dynamicRate));
                conf.setCardRate(Math.max(0.1, cardRate));
                conf.setCacheTtlSeconds(Math.max(30, cacheTtlSeconds));
                pool.updatePoolConf(conf);
                xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(conf);
            }
            return Response.success(true, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolConfig error", e);
            return Response.success(false, req);
        }
    }

    // ==================== 主账号操作 API ====================

    /**
     * 停用/启用主账号参与API轮询
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/mainToggle")
    public Response<?> accountPoolMainToggle(@RequestParam("enabled") boolean enabled,
                                             HttpServletRequest req) {
        try {
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            pool.setMainPollingEnabled(enabled);
            xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
            return Response.success(true, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolMainToggle error", e);
            return Response.success(false, req);
        }
    }

    /**
     * 手动清除主账号冷却状态
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/mainClearCooldown")
    public Response<?> accountPoolMainClearCooldown(HttpServletRequest req) {
        try {
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            pool.clearMainCooldown();
            xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
            return Response.success(true, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolMainClearCooldown error", e);
            return Response.success(false, req);
        }
    }

    /**
     * 编辑主账号Cookie
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/mainUpdate")
    public Response<?> accountPoolMainUpdate(@RequestParam("cookie") String cookie,
                                             HttpServletRequest req) {
        try {
            if (StringUtils.isBlank(cookie)) {
                return Response.success(false, req);
            }
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            String[] result = pool.updateMainAccountCookie(cookie);
            com.alibaba.fastjson.JSONObject resp = new com.alibaba.fastjson.JSONObject();
            resp.put("valid", "true".equals(result[0]));
            resp.put("uid", result[1]);
            resp.put("uname", result[2]);
            resp.put("face", result.length > 3 ? result[3] : "");
            resp.put("level", result.length > 4 ? result[4] : "0");
            if ("true".equals(result[0])) {
                // 持久化主配置
                checkService.changeSet(PublicDataConf.centerSetConf, false);
                xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
                resp.put("success", true);
                resp.put("message", "主账号已更新为: " + (result[2] != null ? result[2] : result[1]));
            } else {
                resp.put("success", false);
                resp.put("message", "Cookie无效或已过期");
            }
            return Response.success(resp, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolMainUpdate error", e);
            com.alibaba.fastjson.JSONObject err = new com.alibaba.fastjson.JSONObject();
            err.put("success", false);
            err.put("message", "更新异常: " + e.getMessage());
            return Response.success(err, req);
        }
    }

    /**
     * 删除主账号（清空登录状态）
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/mainRemove")
    public Response<?> accountPoolMainRemove(HttpServletRequest req) {
        try {
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            pool.removeMainAccount();
            // 持久化主配置（清空cookie）
            checkService.changeSet(PublicDataConf.centerSetConf, false);
            xyz.acproject.danmuji.http.HttpRoomData.syncRateLimiterConfig(pool.getPoolConf());
            // 清除session登录状态
            req.getSession().removeAttribute("status");
            com.alibaba.fastjson.JSONObject resp = new com.alibaba.fastjson.JSONObject();
            resp.put("success", true);
            resp.put("message", "主账号已删除，请重新登录");
            return Response.success(resp, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolMainRemove error", e);
            com.alibaba.fastjson.JSONObject err = new com.alibaba.fastjson.JSONObject();
            err.put("success", false);
            err.put("message", "删除异常: " + e.getMessage());
            return Response.success(err, req);
        }
    }

    // ==================== 子账号扫码登录 API ====================

    /**
     * 生成子账号扫码登录的二维码URL。
     * 使用独立的session属性(subQrcodeKey)，不影响主账号登录。
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/qrcodeUrl")
    public Response<?> accountPoolQrcodeUrl(HttpServletRequest req) {
        try {
            xyz.acproject.danmuji.entity.login_data.Qrcode qrcode =
                    xyz.acproject.danmuji.http.HttpUserData.httpGenerateQrcode();
            if (qrcode != null && qrcode.getUrl() != null) {
                req.getSession().setAttribute("subQrcodeKey", qrcode.getQrcode_key());
                return Response.success(qrcode.getUrl(), req);
            }
            return Response.success(null, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolQrcodeUrl error", e);
            return Response.success(null, req);
        }
    }

    /**
     * 轮询子账号扫码登录状态。
     * 登录成功后从Set-Cookie提取cookie并返回，不清除主账号状态。
     * @return {code: 0成功, 86038过期, 86090已扫码待确认, 86101未扫码, -1失败}
     */
    @ResponseBody
    @PostMapping(value = "/api/accountPool/qrcodePoll")
    public Response<?> accountPoolQrcodePoll(HttpServletRequest req) {
        try {
            String oauthKey = (String) req.getSession().getAttribute("subQrcodeKey");
            if (StringUtils.isBlank(oauthKey)) {
                return Response.success(null, req);
            }

            Map<String, String> headers = new HashMap<>(3);
            headers.put("user-agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
            headers.put("Referer", "https://www.bilibili.com/");
            Map<String, String> params = new HashMap<>(3);
            params.put("qrcode_key", oauthKey);
            params.put("source", "main-fe-header");

            okhttp3.Response response = xyz.acproject.danmuji.utils.OkHttp3Utils.getHttp3Utils()
                    .httpGet("https://passport.bilibili.com/x/passport-login/web/qrcode/poll", headers, params);
            String data = response.body().string();
            com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSONObject.parseObject(data);
            com.alibaba.fastjson.JSONObject result = new com.alibaba.fastjson.JSONObject();

            int code = jsonObject.getJSONObject("data").getIntValue("code");
            result.put("code", code);

            if (code == 0) {
                // 登录成功，从Set-Cookie提取cookie
                okhttp3.Headers responseHeaders = response.headers();
                List<String> cookies = responseHeaders.values("Set-Cookie");
                java.util.Set<String> cookieSet = new java.util.HashSet<>();
                for (String s : cookies) {
                    int semicolonIdx = s.indexOf(";");
                    if (semicolonIdx > 0) {
                        cookieSet.add(s.substring(0, semicolonIdx));
                    }
                }
                StringBuilder sb = new StringBuilder(100);
                java.util.Iterator<String> iter = cookieSet.iterator();
                while (iter.hasNext()) {
                    sb.append(iter.next());
                    if (iter.hasNext()) sb.append(";");
                }
                String subCookie = sb.toString();
                result.put("cookie", subCookie);

                // 验证cookie并获取用户信息
                if (StringUtils.isNotBlank(subCookie)) {
                    xyz.acproject.danmuji.http.CookiePoolManager pool =
                            xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
                    String[] validateResult = pool.validateCookie(subCookie);
                    result.put("valid", "true".equals(validateResult[0]));
                    result.put("uid", validateResult[1]);
                    result.put("uname", validateResult[2]);
                    result.put("face", validateResult[3]);
                    result.put("level", validateResult.length > 4 ? validateResult[4] : "0");
                }

                // 清除session中的临时key
                req.getSession().removeAttribute("subQrcodeKey");
            }

            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("accountPoolQrcodePoll error", e);
            return Response.success(null, req);
        }
    }

    // ===== 足迹留印 / 足迹还原 相关端点 =====

    /**
     * 列出所有足迹 CSV 文件
     */
    @ResponseBody
    @GetMapping(value = "/listFootprintFiles")
    public Response<?> listFootprintFiles(HttpServletRequest req) {
        try {
            List<String> files = FootprintFileTools.getInstance().listFiles();
            JSONArray result = new JSONArray();
            for (String fp : files) {
                File f = new File(fp);
                JSONObject item = new JSONObject();
                item.put("fileName", f.getName());
                item.put("filePath", fp);
                item.put("size", f.length());
                result.add(item);
            }
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("listFootprintFiles error", e);
            return Response.success(new JSONArray(), req);
        }
    }

    /**
     * 下载足迹文件
     */
    @ResponseBody
    @GetMapping(value = "/downloadFootprintFile")
    public void downloadFootprintFile(@RequestParam("filePath") String filePath,
                                       HttpServletResponse response) throws Exception {
        validateFilePath(filePath);
        File file = new File(filePath);
        if (!file.isAbsolute()) {
            file = new File(getDanmujiLogDir(), filePath);
        }
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        byte[] buffer = new byte[bis.available()];
        bis.read(buffer);
        bis.close();
        response.reset();
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(file.getName(), "UTF-8"));
        response.addHeader("Content-Length", "" + file.length());
        response.setContentType("application/octet-stream");
        OutputStream os = new BufferedOutputStream(response.getOutputStream());
        os.write(buffer);
        os.flush();
    }

    /**
     * 删除足迹文件
     */
    @ResponseBody
    @PostMapping(value = "/deleteFootprintFile")
    public Response<?> deleteFootprintFile(@RequestParam("filePath") String filePath,
                                            HttpServletRequest req) {
        try {
            validateFilePath(filePath);
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            boolean deleted = FootprintFileTools.getInstance().deleteFile(file.getAbsolutePath());
            return Response.success(deleted ? 0 : 1, req);
        } catch (Exception e) {
            LOGGER.error("deleteFootprintFile error", e);
            return Response.success(2, req);
        }
    }

    /**
     * 上传足迹文件
     */
    @ResponseBody
    @PostMapping(value = "/uploadFootprintFile")
    public Response<?> uploadFootprintFile(@RequestParam("file") MultipartFile file,
                                            HttpServletRequest req) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null ||
                    (!originalFilename.endsWith(".csv") && !originalFilename.endsWith(".txt"))) {
                return Response.success(2, req);  // 格式不对
            }
            File danmujiLogDir = getDanmujiLogDir();
            if (!danmujiLogDir.exists()) danmujiLogDir.mkdirs();
            File destFile = new File(danmujiLogDir, originalFilename);
            file.transferTo(destFile);
            JSONObject result = new JSONObject();
            result.put("fileName", destFile.getName());
            result.put("filePath", destFile.getAbsolutePath());
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("uploadFootprintFile error", e);
            return Response.success(1, req);
        }
    }

    /**
     * 开始足迹重放（单文件，兼容旧接口）
     */
    @ResponseBody
    @PostMapping(value = "/startFootprintReplay")
    public Response<?> startFootprintReplay(@RequestParam("filePath") String filePath,
                                             @RequestParam(defaultValue = "time") String speedMode,
                                             @RequestParam(defaultValue = "1.0") double speedValue,
                                             HttpServletRequest req) {
        // 转为单文件批次
        return startBatchReplayInternal(new String[]{filePath}, speedMode, speedValue, req);
    }

    /**
     * 开始足迹批次重放（多文件）
     */
    @ResponseBody
    @PostMapping(value = "/startBatchFootprintReplay")
    public Response<?> startBatchFootprintReplay(@RequestParam("filePaths[]") String[] filePaths,
                                                  @RequestParam(defaultValue = "time") String speedMode,
                                                  @RequestParam(defaultValue = "1.0") double speedValue,
                                                  HttpServletRequest req) {
        return startBatchReplayInternal(filePaths, speedMode, speedValue, req);
    }

    /**
     * 批次重放内部实现
     */
    private Response<?> startBatchReplayInternal(String[] filePaths, String speedMode,
                                                   double speedValue, HttpServletRequest req) {
        try {
            if (filePaths == null || filePaths.length == 0) {
                return Response.success(1, req);
            }

            // 停止已有重放
            if (activeReplayThread != null && activeReplayThread.isRunning()) {
                activeReplayThread.stopReplay();
            }

            List<FileBatch> batches = new ArrayList<>();
            int totalRecords = 0;
            String firstFileName = null;

            for (String fp : filePaths) {
                validateFilePath(fp);
                File file = new File(fp);
                if (!file.isAbsolute()) file = new File(getDanmujiLogDir(), fp);
                if (!file.exists()) {
                    LOGGER.warn("FootprintBatchReplay: file not found {}", fp);
                    continue;
                }

                ParseResult parseResult = FootprintFileTools.getInstance().readFileWithMeta(file.getAbsolutePath());
                if (parseResult.records.isEmpty()) {
                    LOGGER.warn("FootprintBatchReplay: empty file {}", fp);
                    continue;
                }

                // 合并元数据：文件名解析为主，CSV 头部补充
                SessionMeta fileNameMeta = FootprintFileTools.parseFileNameForContext(file.getName());
                SessionMeta meta = new SessionMeta();
                meta.roomId = fileNameMeta.roomId != 0 ? fileNameMeta.roomId : parseResult.meta.roomId;
                meta.anchorName = !fileNameMeta.anchorName.isEmpty() ? fileNameMeta.anchorName : parseResult.meta.anchorName;
                meta.auid = parseResult.meta.auid;

                batches.add(new FileBatch(file.getName(), meta, parseResult.records));
                totalRecords += parseResult.records.size();
                if (firstFileName == null) firstFileName = file.getName();
            }

            if (batches.isEmpty()) {
                return Response.success(2, req);  // 没有有效文件
            }

            // 设置第一个文件的直播间上下文
            SessionMeta firstMeta = batches.get(0).meta;
            if (firstMeta.roomId != 0) PublicDataConf.ROOMID = firstMeta.roomId;
            if (firstMeta.anchorName != null && !firstMeta.anchorName.isEmpty())
                PublicDataConf.ANCHOR_NAME = firstMeta.anchorName;
            if (firstMeta.auid != 0) PublicDataConf.AUID = firstMeta.auid;

            LOGGER.info("FootprintBatchReplay: {} files, {} records total, first={}",
                    batches.size(), totalRecords, firstFileName);

            ParseMessageThread pmt = PublicDataConf.parseMessageThread;
            activeReplayThread = new FootprintReplayThread(batches, pmt);

            FootprintReplayThread.SpeedMode mode = "fixed".equals(speedMode)
                    ? FootprintReplayThread.SpeedMode.FIXED_RATE
                    : FootprintReplayThread.SpeedMode.TIME_MULTIPLIER;
            activeReplayThread.setSpeed(mode, speedValue);
            activeReplayThread.start();

            JSONObject result = new JSONObject();
            result.put("total", totalRecords);
            result.put("totalBatches", batches.size());
            result.put("fileName", firstFileName);
            result.put("speedMode", "fixed".equals(speedMode) ? "fixed" : "time");
            result.put("speedValue", speedValue);
            return Response.success(result, req);
        } catch (Exception e) {
            LOGGER.error("startBatchFootprintReplay error", e);
            return Response.success(4, req);
        }
    }

    /**
     * 暂停足迹重放
     */
    @ResponseBody
    @PostMapping(value = "/pauseFootprintReplay")
    public Response<?> pauseFootprintReplay(HttpServletRequest req) {
        if (activeReplayThread != null) {
            activeReplayThread.pauseReplay();
            return Response.success(true, req);
        }
        return Response.success(false, req);
    }

    /**
     * 恢复足迹重放
     */
    @ResponseBody
    @PostMapping(value = "/resumeFootprintReplay")
    public Response<?> resumeFootprintReplay(HttpServletRequest req) {
        if (activeReplayThread != null) {
            activeReplayThread.resumeReplay();
            return Response.success(true, req);
        }
        return Response.success(false, req);
    }

    /**
     * 停止足迹重放
     */
    @ResponseBody
    @PostMapping(value = "/stopFootprintReplay")
    public Response<?> stopFootprintReplay(HttpServletRequest req) {
        if (activeReplayThread != null) {
            activeReplayThread.stopReplay();
            return Response.success(true, req);
        }
        return Response.success(false, req);
    }

    /**
     * 设置足迹重放速度
     */
    @ResponseBody
    @PostMapping(value = "/setFootprintReplaySpeed")
    public Response<?> setFootprintReplaySpeed(@RequestParam("speedMode") String speedMode,
                                                @RequestParam("speedValue") double speedValue,
                                                HttpServletRequest req) {
        if (activeReplayThread != null && activeReplayThread.isRunning()) {
            FootprintReplayThread.SpeedMode mode = "fixed".equals(speedMode)
                    ? FootprintReplayThread.SpeedMode.FIXED_RATE
                    : FootprintReplayThread.SpeedMode.TIME_MULTIPLIER;
            activeReplayThread.setSpeed(mode, speedValue);
            return Response.success(true, req);
        }
        return Response.success(false, req);
    }

    /**
     * 获取足迹重放状态（UI 轮询）
     */
    @ResponseBody
    @GetMapping(value = "/getFootprintReplayStatus")
    public Response<?> getFootprintReplayStatus(HttpServletRequest req) {
        JSONObject status = new JSONObject();
        if (activeReplayThread != null) {
            status.put("running", activeReplayThread.isRunning());
            status.put("paused", activeReplayThread.isPaused());
            status.put("stopped", activeReplayThread.isStopped());
            status.put("currentIndex", activeReplayThread.getCurrentIndex());
            status.put("totalCount", activeReplayThread.getTotalCount());
            status.put("speedMode", activeReplayThread.getSpeedMode() == FootprintReplayThread.SpeedMode.FIXED_RATE ? "fixed" : "time");
            status.put("speedValue", activeReplayThread.getSpeedValue());
            status.put("currentUname", activeReplayThread.getCurrentUname());
            status.put("currentUid", activeReplayThread.getCurrentUid());
            status.put("currentBatchIndex", activeReplayThread.getCurrentBatchIndex());
            status.put("totalBatchCount", activeReplayThread.getTotalBatchCount());
            status.put("currentFileName", activeReplayThread.getCurrentFileName());
        } else {
            status.put("running", false);
            status.put("paused", false);
            status.put("stopped", true);
            status.put("currentIndex", 0);
            status.put("totalCount", 0);
            status.put("speedMode", "time");
            status.put("speedValue", 1.0);
            status.put("currentUname", "");
            status.put("currentUid", 0);
            status.put("currentBatchIndex", 0);
            status.put("totalBatchCount", 0);
            status.put("currentFileName", "");
        }
        return Response.success(status, req);
    }
}
