package xyz.acproject.danmuji.conf;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import xyz.acproject.danmuji.conf.set.*;
import xyz.acproject.danmuji.tools.BASE64Encoder;

import java.io.IOException;
import java.io.Serializable;

/**
 * @author BanqiJane
 * @ClassName CenterSetConf
 * @Description TODO
 * @date 2020年8月10日 下午12:21:29
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class CenterSetConf implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = 1162255349476806991L;
    //是否开启弹幕
    @JSONField(name = "is_barrage")
    private boolean is_barrage = true;
    //弹幕显示舰长和老爷图标
    @JSONField(name = "is_barrage_guard")
    private boolean is_barrage_guard = true;
    //弹幕显示舰长和老爷图标
    @JSONField(name = "is_barrage_vip")
    private boolean is_barrage_vip = true;
    //弹幕显示房管图标
    @JSONField(name = "is_barrage_manager")
    private boolean is_barrage_manager = true;
    //弹幕显示勋章图标
    @JSONField(name = "is_barrage_medal")
    private boolean is_barrage_medal = false;
    //弹幕显示用户等级图标
    @JSONField(name = "is_barrage_ul")
    private boolean is_barrage_ul = false;
    //是否屏蔽非当前房间勋章弹幕
    @JSONField(name = "is_barrage_anchor_shield")
    private boolean is_barrage_anchor_shield = false;
    //信息是否显示房管禁言消息
    @JSONField(name = "is_block")
    private boolean is_block = true;
    //信息是否显示礼物消息
    @JSONField(name = "is_gift")
    private boolean is_gift = true;
    //信息是否显示免费礼物消息
    @JSONField(name = "is_gift_free")
    private boolean is_gift_free = true;
    //信息是否显示欢迎老爷舰长进入直播间消息
    @JSONField(name = "is_welcome_ye")
    private boolean is_welcome_ye = true;
    //信息是否显示全部欢迎
    @JSONField(name = "is_welcome_all")
    private boolean is_welcome_all = false;
    //是否开启关注显示
    @JSONField(name = "is_follow_dm")
    private boolean is_follow_dm = true;
    //是否开启日志线程
    @JSONField(name = "is_log")
    private boolean is_log = true;
    //是否开启观众记录
    @JSONField(name = "is_watcher_log")
    private boolean is_watcher_log = true;
    //是否开启足迹留印（轻量级记录，跳过所有INTERACT_WORD_V2处理，零API调用）
    @JSONField(name = "is_footprint_record")
    private boolean is_footprint_record = true;
    //是否控制台打印
    @JSONField(name = "is_cmd")
    private boolean is_cmd = true;
    //房间号
    private Long roomid = 0l;
    //是否自动连接
    @JSONField(name = "is_auto")
    private boolean is_auto = true;
    //window是否自动打开设置页面 默认open
    @JSONField(name = "win_auto_openSet")
    private boolean win_auto_openSet = true;

    @JSONField(name = "auto_save_set")
    private boolean auto_save_set = true;

    private String connect_docket = "ws://localhost:23333/danmu/sub";
    //是否开启礼物感谢线程对象体 black
    @JSONField(name = "thank_gift")
    private ThankGiftSetConf thank_gift;
    //是否开启广告公告线程对象体
    @JSONField(name = "advert")
    private AdvertSetConf advert;
    //是否开启感谢关注线程对象体 black
    @JSONField(name = "follow")
    private ThankFollowSetConf follow;
    //是否开启自动回复线程对象体 black
    @JSONField(name = "reply")
    private AutoReplySetConf reply;
    //是否开启欢迎进入直播间线程对象体 black
    @JSONField(name = "welcome")
    private ThankWelcomeSetConf welcome;
    //直播状态姬
    @JSONField(name="live_status")
    private LiveStatusSetConf live_status;
    //定时姬
    @JSONField(name="timer")
    private TimerSetConf timer;
    //弹幕话术姬
    @JSONField(name="danmaku_store")
    private DanmakuStoreSetConf danmaku_store;
    //欢迎凝视姬
    @JSONField(name="gaze_welcome")
    private GazeWelcomeSetConf gaze_welcome;
    //负黑自动拉黑姬
    @JSONField(name="auto_block")
    private AutoBlockSetConf auto_block;
    //关键词检测姬
    @JSONField(name="key_word")
    private KeyWordSetConf key_word;
    //本地黑白名单姬
    @JSONField(name="local_black_white_list")
    private LocalBlackWhiteListSetConf localBlackWhiteList;
    @JSONField(name = "edition",serialize = false)
    private String edition = "";




    public static CenterSetConf getInitCenterSetConf(){
        CenterSetConf centerSetConf = new CenterSetConf();
        centerSetConf.setThank_gift(new ThankGiftSetConf());
        centerSetConf.setAdvert(new AdvertSetConf());
        centerSetConf.setFollow(new ThankFollowSetConf());
        centerSetConf.setReply(new AutoReplySetConf());
        centerSetConf.setWelcome(new ThankWelcomeSetConf());
        centerSetConf.setLive_status(new LiveStatusSetConf());
        centerSetConf.setTimer(new TimerSetConf());
        centerSetConf.setDanmaku_store(new DanmakuStoreSetConf());
        centerSetConf.setGaze_welcome(new GazeWelcomeSetConf());
        centerSetConf.setAuto_block(new AutoBlockSetConf());
        centerSetConf.setKey_word(new KeyWordSetConf());
        centerSetConf.setLocalBlackWhiteList(new LocalBlackWhiteListSetConf());
        return centerSetConf;
    }


    public CenterSetConf(ThankGiftSetConf thank_gift, AdvertSetConf advert,
                         ThankFollowSetConf follow, AutoReplySetConf reply, ThankWelcomeSetConf welcome, LiveStatusSetConf live_status, TimerSetConf timer, DanmakuStoreSetConf danmaku_store, GazeWelcomeSetConf gaze_welcome, AutoBlockSetConf auto_block, KeyWordSetConf key_word, LocalBlackWhiteListSetConf localBlackWhiteList) {
        super();
        this.thank_gift = thank_gift;
        this.advert = advert;
        this.follow = follow;
        this.reply = reply;
        this.welcome = welcome;
        this.live_status = live_status;
        this.timer = timer;
        this.danmaku_store = danmaku_store;
        this.gaze_welcome = gaze_welcome;
        this.auto_block = auto_block;
        this.key_word = key_word;
        this.localBlackWhiteList = localBlackWhiteList;
    }

    public String toJson() {
        return JSON.toJSONString(this, true);
    }

    /**
     * 从 profile 字符串解析 CenterSetConf，兼容明文 JSON 和旧的 base64 编码。
     * @param content profile 中 set 字段的值
     * @return is not null
     */
    public static CenterSetConf of(String content){
        Assert.notNull(content, "content must cannot be null");
        // 1. 先尝试直接解析明文 JSON
        try {
            return JSONObject.parseObject(content, CenterSetConf.class);
        } catch (JSONException e) {
            // 不是有效 JSON，尝试 base64 解码（兼容旧格式）
        }
        // 2. 尝试 base64 解码
        BASE64Encoder base64Encoder = new BASE64Encoder();
        try {
            return JSONObject.parseObject(new String(base64Encoder.decode(content)), CenterSetConf.class);
        } catch (IOException|JSONException e) {
            log.error(e.getMessage(), e);
            return new CenterSetConf();
        }
    }
}
