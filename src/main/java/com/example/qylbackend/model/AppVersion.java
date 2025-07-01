package com.example.qylbackend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * App版本信息模型 (Entity)
 * 用于存储APP版本信息，提供给客户端检查更新
 */
@Entity
@Data
public class AppVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String versionName; // 版本名称, e.g., "1.0.2"

    private String updateLog; // 更新日志

    private String downloadUrl; // APK下载地址
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt; // 发布日期和时间
} 