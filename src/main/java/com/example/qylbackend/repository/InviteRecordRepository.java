package com.example.qylbackend.repository;

import com.example.qylbackend.model.InviteRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InviteRecordRepository extends JpaRepository<InviteRecord, Long> {
    List<InviteRecord> findByInviterIdOrderByCreatedAtDesc(Long inviterId, Pageable pageable);
    Integer countByInviterId(Long inviterId);

    // 查询邀请人今天的邀请数量（用于每日限制检查）
    Integer countByInviterIdAndCreatedAtAfter(Long inviterId, LocalDateTime afterTime);

    // 查询邀请人某设备的邀请记录（用于设备去重）
    Integer countByInviterIdAndInviteeDeviceId(Long inviterId, String inviteeDeviceId);
}
