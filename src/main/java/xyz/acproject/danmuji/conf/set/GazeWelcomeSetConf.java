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
 * @ClassName GazeWelcomeSetConf
 * @Description 欢迎凝视姬设置
 * @date 2026年5月12日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GazeWelcomeSetConf extends OpenSetConf implements Serializable {

    private static final long serialVersionUID = 1780764960331573486L;

    @JSONField(name = "cooldown_time")
    private Integer cooldown_time = 3;

    @JSONField(name = "gazeWelcomeSets")
    private LinkedHashSet<GazeWelcomeSet> gazeWelcomeSets = new LinkedHashSet<>();
}
