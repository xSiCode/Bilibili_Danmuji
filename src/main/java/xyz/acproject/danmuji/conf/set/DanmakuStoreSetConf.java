package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.acproject.danmuji.conf.base.OpenSetConf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author BanqiJane
 * @ClassName DanmakuStoreSetConf
 * @Description 弹幕暂存姬设置
 * @date 2026年5月9日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmakuStoreSetConf extends OpenSetConf implements Serializable {

    private static final long serialVersionUID = 6893124578109247653L;

    @JSONField(name = "items")
    private List<String> items = new ArrayList<>();

    public List<String> getItems() {
        if (items == null) return new ArrayList<>();
        return items;
    }
}
