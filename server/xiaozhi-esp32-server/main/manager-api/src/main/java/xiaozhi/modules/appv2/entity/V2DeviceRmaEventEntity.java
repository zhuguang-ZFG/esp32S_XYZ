package xiaozhi.modules.appv2.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("device_rma_events")
public class V2DeviceRmaEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String deviceSn;
    private Long operatorAccountId;
    private String action;
    private String fromDeviceStatus;
    private String toDeviceStatus;
    private String fromBindingStatus;
    private String toBindingStatus;
    private Boolean factoryCleaned;
    private String evidenceRef;
    private String ticketRef;
    private Date createdAt;
}
