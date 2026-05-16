package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("members")
public class V2MemberEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private String deviceId;
    private String displayName;
    private String role;
    private String memberType;
    private String status;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
