package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.JSONObject;
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
 * @Description 弹幕话术姬设置
 * @date 2026年5月9日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmakuStoreSetConf extends OpenSetConf implements Serializable {

    private static final long serialVersionUID = 6893124578109247653L;

    @JSONField(name = "items")
    private List items = new ArrayList<>();

    public List<DanmakuStoreSet> getItems() {
        if (items == null) return new ArrayList<>();
        List<DanmakuStoreSet> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof DanmakuStoreSet) {
                result.add((DanmakuStoreSet) item);
            } else if (item instanceof String) {
                result.add(new DanmakuStoreSet("", (String) item));
            } else if (item instanceof JSONObject) {
                JSONObject jo = (JSONObject) item;
                result.add(new DanmakuStoreSet(jo.getString("type"), jo.getString("text")));
            }
        }
        return result;
    }
}
