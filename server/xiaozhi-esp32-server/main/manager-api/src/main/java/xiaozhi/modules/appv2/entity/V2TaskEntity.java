package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("tasks")
public class V2TaskEntity {
    @TableId
    private String id;
    private Long accountId;
    private String deviceId;
    private String requestId;
    private String traceId;
    private String capability;
    private String source;
    private String paramsJson;
    private String constraintsJson;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String resultJson;
    private Date startedAt;
    private Date finishedAt;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
