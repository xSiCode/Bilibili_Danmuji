package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * 本地黑白名单姬配置
 * @author BanqiJane
 */
@Data
public class LocalBlackWhiteListSetConf {

    @JSONField(name = "is_open")
    private boolean is_open = false;
}
