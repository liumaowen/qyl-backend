package com.example.qylbackend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Entity
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;     // 用户名（字母+数字，唯一）

    private String password;     // 密码（MD5加密）

    private String deviceId;     // 注册时的设备ID（用于迁移会员状态）

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;      // 创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginAt;    // 最后登录时间

    // ===== 瞬乐币积分系统 =====
    private Integer points = 0;  // 瞬乐币余额

    @Column(unique = true)
    private String inviteCode;   // 邀请码（8位字母数字，唯一）

    private Long invitedBy;      // 邀请人用户ID

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime vipExpireAt;  // VIP到期时间（null=非VIP，付费¥10=now+1年，积分兑换=now+N天）
}
