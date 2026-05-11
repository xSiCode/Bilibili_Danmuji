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
 * @ClassName GazeWelcomeSet
 * @Description 欢迎凝视姬单条条目
 * @date 2026年5月12日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GazeWelcomeSet extends OpenSetConf implements Serializable {

    private static final long serialVersionUID = 4031306081523152239L;

    @JSONField(name = "username")
    private String username;

    @JSONField(name = "text")
    private String text = "欢迎%uNames%进入直播间~";
}
