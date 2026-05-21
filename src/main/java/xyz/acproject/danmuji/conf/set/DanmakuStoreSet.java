package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author BanqiJane
 * @ClassName DanmakuStoreSet
 * @Description 弹幕话术姬单条话术条目
 * @date 2026年5月22日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmakuStoreSet implements Serializable {

    private static final long serialVersionUID = -5472911237563284952L;

    @JSONField(name = "type")
    private String type;

    @JSONField(name = "text")
    private String text;
}
