package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class AutoBlackListSetConf {

    @JSONField(name = "enabled")
    private boolean enabled;

    @JSONField(name = "black_score")
    private int black_score = -1;

    @JSONField(name = "auto_black_users")
    private List<AutoBlackUser> autoBlackUsers;

    public List<AutoBlackUser> getAutoBlackUsers() {
        if (autoBlackUsers == null) return new ArrayList<>();
        return autoBlackUsers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoBlackUser {
        @JSONField(name = "uid")
        private Long uid;
        @JSONField(name = "uname")
        private String uname;
        @JSONField(name = "score")
        private int score;
        @JSONField(name = "time")
        private String time;
    }
}
