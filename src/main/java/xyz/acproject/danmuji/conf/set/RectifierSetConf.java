package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.acproject.danmuji.conf.base.OpenSetConf;

import java.io.Serializable;

/**
 * @author BanqiJane
 * @ClassName RectifierSetConf
 * @Description 整流回复姬设置
 * @date 2026年5月11日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RectifierSetConf extends OpenSetConf implements Serializable {

    private static final long serialVersionUID = 3058267193412568901L;

    @JSONField(name = "interval")
    private int interval = 30;

    @JSONField(name = "welcome_open")
    private boolean welcome_open = false;
    @JSONField(name = "welcome_text")
    private String welcome_text = "欢迎%uNames%进入直播间~";

    @JSONField(name = "follow_open")
    private boolean follow_open = false;
    @JSONField(name = "follow_text")
    private String follow_text = "谢谢%uNames%的关注~";

    @JSONField(name = "gift_open")
    private boolean gift_open = false;
    @JSONField(name = "gift_text")
    private String gift_text = "感谢%uName%的%GiftName%~";

    public boolean anyActionOpen() {
        return welcome_open || follow_open || gift_open;
    }
}
