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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

        return "index";
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
            //整流回复姬
            if(centerSetConf.getRectifier()==null&&PublicDataConf.centerSetConf.getRectifier()!=null){
                centerSetConf.setRectifier(PublicDataConf.centerSetConf.getRectifier());
            }
            if(centerSetConf.getRectifier()==null&&PublicDataConf.centerSetConf.getRectifier()==null){
                centerSetConf.setRectifier(new RectifierSetConf());
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
