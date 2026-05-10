package xyz.acproject.danmuji.entity.user_data;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNav {

    @JSONField(name = "wbi_img")
    private WbiImg wbiImg;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public final static class WbiImg{
        @JSONField(name = "img_url")
        private String imgUrl;
        @JSONField(name = "sub_url")
        private String subUrl;
    }
}
