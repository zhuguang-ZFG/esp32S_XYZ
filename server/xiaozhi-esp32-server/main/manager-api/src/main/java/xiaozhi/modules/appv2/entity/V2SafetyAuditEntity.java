package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("safety_audit")
public class V2SafetyAuditEntity {
    @TableId
    private Long id;
    private Long accountId;
    private String deviceId;
    private String capability;
    private String reason;
    private Date ts;
    @TableField("created_at")
    private Date createdAt;
}
