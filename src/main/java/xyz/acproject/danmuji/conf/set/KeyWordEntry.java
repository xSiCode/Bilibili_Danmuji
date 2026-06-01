package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author xsicode
 * @ClassName KeyWordEntry
 * @Description 关键词检测姬 — 单条关键词→分值映射
 * @date 2026/6/1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyWordEntry implements Serializable, Comparable<KeyWordEntry> {

    private static final long serialVersionUID = 1L;

    @JSONField(name = "keyword")
    private String keyword;

    @JSONField(name = "score")
    private Integer score;

    @Override
    public int compareTo(KeyWordEntry o) {
        if (this.keyword == null && o.keyword == null) return 0;
        if (this.keyword == null) return -1;
        if (o.keyword == null) return 1;
        return this.keyword.compareTo(o.keyword);
    }
}
