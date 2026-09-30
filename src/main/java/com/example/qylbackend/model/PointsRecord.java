package com.example.qylbackend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 积分流水记录
 */
@Entity
@Data
@Table(name = "points_record")
public class PointsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;             // 用户ID

    @Column(nullable = false)
    private Integer amount;          // 积分变动（正数增加，负数减少）

    @Column(nullable = false)
    private String type;             // 类型：SIGNIN/INVITE/REWARD/EXCHANGE

    @Column(nullable = false)
    private String description;      // 描述

    @Column(nullable = false)
    private Integer balanceBefore;   // 变动前余额

    @Column(nullable = false)
    private Integer balanceAfter;    // 变动后余额

    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt; // 创建时间
}
