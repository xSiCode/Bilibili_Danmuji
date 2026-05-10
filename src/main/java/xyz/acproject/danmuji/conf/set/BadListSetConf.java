package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class BadListSetConf {

    @JSONField(name = "bad_users")
    private List<BadUser> badUsers;

    public List<BadUser> getBadUsers() {
        if (badUsers == null) return new ArrayList<>();
        return badUsers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BadUser {
        @JSONField(name = "uid")
        private Long uid;
        @JSONField(name = "uname")
        private String uname;
    }
}
