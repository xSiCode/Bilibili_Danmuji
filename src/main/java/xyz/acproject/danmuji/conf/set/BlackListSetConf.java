package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.HashSet;

/**
 * @author Admin
 * @ClassName BlackListSetConf
 * @Description TODO
 * @date 2023/1/13 10:09
 * @Copyright:2023
 */
@Data
public class BlackListSetConf{

    @JSONField(name = "names")
    private HashSet<String> names;

    public HashSet<String> getNames() {
        if(names==null)return new HashSet<>();
        return names;
    }
}
