package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("device_transfer_requests")
public class V2DeviceTransferRequestEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private Long sourceAccountId;
    private Long targetAccountId;
    private String targetUnionid;
    private String status;
    private Date requestedAt;
    private Date acceptedAt;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
