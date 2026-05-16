package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("accounts")
public class V2AccountEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String unionid;
    private String openid;
    private String displayName;
    private String status;
    private String primarySessionId;
    private Date primarySessionClaimedAt;
    private Date deletedAt;
    private Date auditRetainUntil;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
