package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("entitlements")
public class V2EntitlementEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private String resourceType;
    private String resourceId;
    private String source;
    private Date expiresAt;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
