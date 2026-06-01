package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.HashSet;

/**
 * @author Admin
 * @ClassName WhiteListSetConf
 * @Description TODO
 * @date 2024/5/21 10:09
 * @Copyright:2024
 */
@Data
public class WhiteListSetConf{

    @JSONField(name = "names")
    private HashSet<String> names;

    public HashSet<String> getNames() {
        if(names==null)return new HashSet<>();
        return names;
    }
}
