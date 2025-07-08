package com.example.qylbackend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设备信息 (Entity)
 * 
 */
@Entity
@Data
public class DeviceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;      // 设备唯一标识
    private String deviceModel;   // 设备型号
    private String platform;      // 平台(android/ios/web)
    private String appVersion;    // 应用版本
    private String osVersion;     // 操作系统版本
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime firstUseTime; // 首次使用时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastUseTime;  // 最后使用时间
    private Integer useCount;     // 使用次数
    private String ipAddress;     // IP地址
    private String address;      // IP地理位置 省市县
    private String event;        // 事件
    private String additionaldatastr;        // 事件集合
} 