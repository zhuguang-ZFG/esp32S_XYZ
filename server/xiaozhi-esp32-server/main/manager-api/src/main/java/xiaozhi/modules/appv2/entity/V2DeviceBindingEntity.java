package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("device_bindings")
public class V2DeviceBindingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private String deviceId;
    private String bindingStatus;
    private Boolean isPrimary;
    private Date boundAt;
    private Date unboundAt;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
