package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * @author xsicode
 * @ClassName KeyWordSetConf
 * @Description 关键词检测姬配置 — 关键词→分值映射列表
 * @date 2026/6/1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyWordSetConf implements Serializable {

    private static final long serialVersionUID = 1L;

    @JSONField(name = "keywords")
    private HashSet<KeyWordEntry> keywords;

    public HashSet<KeyWordEntry> getKeywords() {
        if (keywords != null) {
            return keywords.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return new LinkedHashSet<>();
    }
}
