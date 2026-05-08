package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import xyz.acproject.danmuji.conf.base.OpenSetConf;

import java.io.Serializable;

/**
 * @author BanqiJane
 * @ClassName TimerSet
 * @Description 定时姬单个定时条目
 * @date 2026年5月9日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TimerSet extends OpenSetConf implements Serializable {

    private static final long serialVersionUID = 5910428537786936910L;

    @JSONField(name = "time")
    private String time = "00:00";

    @JSONField(name = "text")
    private String text;
}
