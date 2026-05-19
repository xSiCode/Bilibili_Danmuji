package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class AutoBlockSetConf {

    @JSONField(name = "is_auto_block")
    private boolean is_auto_block = false;

    @JSONField(name = "block_score")
    private int block_score = -1;

    @JSONField(name = "block_interval")
    private int block_interval = 3;
}
