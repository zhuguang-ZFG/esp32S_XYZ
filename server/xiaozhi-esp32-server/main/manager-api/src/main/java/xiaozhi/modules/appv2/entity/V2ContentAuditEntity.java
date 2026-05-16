package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("content_audit")
public class V2ContentAuditEntity {
    @TableId
    private Long id;
    private Long accountId;
    private String deviceId;
    private String path;
    private String rawHash;
    private String ruleHit;
    private Date ts;
    @TableField("created_at")
    private Date createdAt;
}
