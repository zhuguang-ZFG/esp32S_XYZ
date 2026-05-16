package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("device_self_check_events")
public class V2DeviceSelfCheckEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String checkId;
    private String scope;
    private String status;
    private String summary;
    private String checksJson;
    private String payloadJson;
    private Date reportedAt;
    private Date createdAt;
}
