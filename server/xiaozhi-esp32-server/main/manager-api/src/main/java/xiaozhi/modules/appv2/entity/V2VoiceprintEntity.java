package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("voiceprints")
public class V2VoiceprintEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private Long memberId;
    private String deviceId;
    private String provider;
    private String speakerRef;
    private String embeddingHash;
    private Integer sampleDurationMs;
    private String status;
    private Date enrolledAt;
    private Date expiresAt;
    private Date deletedAt;
    private Date auditRetainUntil;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
