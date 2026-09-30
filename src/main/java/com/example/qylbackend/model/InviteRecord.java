package com.example.qylbackend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 邀请记录
 */
@Entity
@Data
@Table(name = "invite_record")
public class InviteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long inviterId;          // 邀请人ID

    @Column(nullable = false)
    private Long inviteeId;          // 被邀请人ID

    @Column(nullable = false)
    private String inviteeDeviceId;  // 被邀请人设备ID（防刷）

    @Column(nullable = false)
    private Integer inviterPoints;   // 邀请人获得积分

    @Column(nullable = false)
    private Integer inviteePoints;   // 被邀请人获得积分

    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt; // 创建时间
}
