package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("firmware_releases")
public class V2FirmwareReleaseEntity {
    @TableId
    private String releaseId;
    private String channel;
    private String version;
    private String url;
    private String sha256;
    private String signature;
    private Integer rolloutPercent;
    private Integer failureThresholdPercent;
    private Integer installCount;
    private Integer failureCount;
    private String status;
    private Date publishedAt;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
