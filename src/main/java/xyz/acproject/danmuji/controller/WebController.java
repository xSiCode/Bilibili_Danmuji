package xyz.acproject.danmuji.controller;

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
import xyz.acproject.danmuji.http.HttpRoomData;
import xyz.acproject.danmuji.http.HttpUserData;
import xyz.acproject.danmuji.service.ClientService;
import xyz.acproject.danmuji.service.DanmujiInitService;
import xyz.acproject.danmuji.service.SetService;
import xyz.acproject.danmuji.tools.CurrencyTools;
import xyz.acproject.danmuji.tools.ParseSetStatusTools;
import xyz.acproject.danmuji.tools.file.FileTools;
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
import xyz.acproject.danmuji.tools.RoomInfoLogTools;

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

    @RequestMapping(value = {"/", "index"})
    public String index(HttpServletRequest req, Model model) {
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

        return "index";
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

            List<String> lines = new ArrayList<>();
            String headerLine = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                headerLine = reader.readLine(); // header with BOM
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 4);
                    if (parts.length >= 4 && parts[0].equals(timeKey)) {
                        continue; // skip this row
                    }
                    lines.add(line);
                }
            }

            // atomic write
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
            // 同步移除 RoomInfoLogTools 内存中的记录，防止下次 flush 时恢复
            RoomInfoLogTools.removeByTimeKey(timeKey);
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
                return Response.success(stats, req);
            }

            Set<String> userIds = new HashSet<>();
            Map<String, Integer> senderCounts = new LinkedHashMap<>();
            long totalChars = 0;
            Map<String, Integer> wordFreq = new LinkedHashMap<>();
            Map<String, int[]> intervalMap = new LinkedHashMap<>();
            Map<String, Set<String>> intervalUnique = new LinkedHashMap<>();

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
                    .limit(5)
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
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : true;
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
            if (PublicDataConf.ROOM_INFO != null && PublicDataConf.ROOM_INFO.getLive_start_time() != null) {
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
            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(getDanmujiLogDir(), filePath);
            }
            if (!file.exists()) return Response.success(false, req);

            List<String> lines = new ArrayList<>();
            String headerLine = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                headerLine = reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() >= 7 && fields.get(0).equals(timeKey) && fields.get(1).equals(uidKey)) {
                        continue;
                    }
                    lines.add(line);
                }
            }
            File tmpFile = new File(filePath + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                if (headerLine != null) { writer.write(headerLine); writer.newLine(); }
                for (String l : lines) { writer.write(l); writer.newLine(); }
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
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : true;
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
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : true;
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
            boolean asc = (sortField != null && !sortField.isEmpty()) ? "asc".equalsIgnoreCase(sortOrder) : true;
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
}
