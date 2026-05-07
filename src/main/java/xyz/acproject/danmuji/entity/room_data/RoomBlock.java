package xyz.acproject.danmuji.entity.room_data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * @author Jane
 * @ClassName RoomBlock
 * @Description TODO
 * @date 2021/4/12 18:13
 * @Copyright:2021
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomBlock implements Serializable {
    private String admin_name;
    private Long adminid;
    private String block_end_time;
    private String ctime;
    private Long id;
    private String msg;
    private String msg_time;
    private Long roomid;
    private Short type;
    private Long uid;
    private String uname;
    private Long blockTimeStamp;
    private Long createTimeStamp;
    private Long msgTimeStamp;


}
