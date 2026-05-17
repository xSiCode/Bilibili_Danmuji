package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class PnScoreSetConf {

    @JSONField(name = "enabled")
    private boolean enabled;

    @JSONField(name = "default_scoring")
    private boolean default_scoring;
}
