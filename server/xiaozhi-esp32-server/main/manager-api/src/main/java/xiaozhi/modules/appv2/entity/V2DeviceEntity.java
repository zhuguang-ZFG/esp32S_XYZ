package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("devices")
public class V2DeviceEntity {
    @TableId
    private String id;
    private String deviceSn;
    private String deviceSecret;
    private String model;
    private String hwRev;
    private String fwRev;
    private String workspaceMm;
    private String status;
    private Date lastSeenAt;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
