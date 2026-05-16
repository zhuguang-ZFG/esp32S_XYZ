package xiaozhi.modules.appv2.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("device_supplies")
public class V2DeviceSupplyEntity {
    @TableId
    private String deviceId;
    private Date penInstalledAt;
    private Integer penInkPercentEst;
    private BigDecimal penMileageMm;
    private String paperSlotState;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
}
