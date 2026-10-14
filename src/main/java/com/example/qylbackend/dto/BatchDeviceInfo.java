package com.example.qylbackend.dto;

import com.example.qylbackend.model.DeviceInfo;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 批量设备信息 DTO
 * 用于接收前端批量上报的埋点数据
 */
@Data
public class BatchDeviceInfo {

    private Map<String, Object> commonData;  // 通用数据（设备信息、会话ID等）

    private List<DeviceInfo> events;         // 事件列表

    private Integer batchSize;               // 批量大小

    private String timestamp;                // 时间戳
}
