package xyz.acproject.danmuji.entity.user_data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 粉丝勋章墙 - 单个勋章信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedalWallItem implements Serializable {

    /** 主播ID */
    private Long targetId;

    /** 主播名 */
    private String targetName;

    /** 勋章ID */
    private Long medalId;

    /** 勋章名 */
    private String medalName;

    /** 勋章等级 */
    private Integer level;

    /** 大航海等级: 0非舰长, 1总督, 2提督, 3舰长 */
    private Integer guardLevel;

    /** 佩戴状态 */
    private Integer wearingStatus;

    /** 直播状态: 0未直播, 1直播中, 2轮播中 */
    private Integer liveStatus;

    /** 官方认证 */
    private Integer official;

}
