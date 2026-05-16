package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("product_notification_events")
public class V2ProductNotificationEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private Long recipientAccountId;
    private String deviceId;
    private String targetRefType;
    private String targetRefId;
    private String deepLink;
    private String status;
    private Date createdAt;
    private Date sentAt;
    @TableField("updated_at")
    private Date updatedAt;
}
