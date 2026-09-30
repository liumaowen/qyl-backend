package com.example.qylbackend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 签到记录
 */
@Entity
@Data
@Table(name = "sign_in_record")
public class SignInRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;             // 用户ID

    @Column(nullable = false)
    private LocalDate signDate;      // 签到日期

    @Column(nullable = false)
    private Integer points;          // 获得积分

    @Column(nullable = false)
    private Integer consecutiveDays; // 连续签到天数

    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt; // 创建时间
}
