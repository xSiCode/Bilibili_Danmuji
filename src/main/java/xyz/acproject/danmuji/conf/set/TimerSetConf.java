package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.acproject.danmuji.conf.base.OpenSetConf;

import java.io.Serializable;
import java.util.LinkedHashSet;

/**
 * @author BanqiJane
 * @ClassName TimerSetConf
 * @Description 定时姬设置
 * @date 2026年5月9日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimerSetConf extends OpenSetConf implements Serializable {

    private static final long serialVersionUID = 8209005436712347891L;

    @JSONField(name = "timerSets")
    private LinkedHashSet<TimerSet> timerSets = new LinkedHashSet<>();
}
