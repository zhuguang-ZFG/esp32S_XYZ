package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("activation_codes")
public class V2ActivationCodeEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceSn;
    private String deviceId;
    private String activationCode;
    private String status;
    private Date expiresAt;
    private Date usedAt;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
