package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author BanqiJane
 * @ClassName LiveStatusSetConf
 * @Description 直播状态姬设置
 * @date 2026年5月9日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveStatusSetConf implements Serializable {

    private static final long serialVersionUID = 7841290356217845091L;

    // 开播
    @JSONField(name = "is_live_open")
    private boolean is_live_open = false;
    @JSONField(name = "live_text")
    private String live_text;

    // 下播
    @JSONField(name = "is_preparing_open")
    private boolean is_preparing_open = false;
    @JSONField(name = "preparing_text")
    private String preparing_text;

    // 警告
    @JSONField(name = "is_warning_open")
    private boolean is_warning_open = false;
    @JSONField(name = "warning_text")
    private String warning_text;

    // 直播被超管切断
    @JSONField(name = "is_cut_off_open")
    private boolean is_cut_off_open = false;
    @JSONField(name = "cut_off_text")
    private String cut_off_text;

    // 本房间已被封禁
    @JSONField(name = "is_room_lock_open")
    private boolean is_room_lock_open = false;
    @JSONField(name = "room_lock_text")
    private String room_lock_text;

    public boolean anyOpen() {
        return is_live_open || is_preparing_open || is_warning_open || is_cut_off_open || is_room_lock_open;
    }
}
