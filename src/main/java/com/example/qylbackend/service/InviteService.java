package com.example.qylbackend.service;

import com.example.qylbackend.model.InviteRecord;
import com.example.qylbackend.model.User;
import com.example.qylbackend.repository.InviteRecordRepository;
import com.example.qylbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class InviteService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InviteRecordRepository inviteRecordRepository;

    @Autowired
    private PointsService pointsService;

    /**
     * 生成邀请码
     */
    public String generateInviteCode(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;

        if (user.getInviteCode() != null) {
            return user.getInviteCode();
        }

        // 生成8位字母数字邀请码
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }

        user.setInviteCode(code.toString());
        userRepository.save(user);

        return code.toString();
    }

    /**
     * 使用邀请码（注册时调用）
     * @param inviteeId 被邀请人ID
     * @param inviteCode 邀请码
     * @param inviteeDeviceId 被邀请人设备ID（用于防刷）
     */
    @Transactional
    public Map<String, Object> useInviteCode(Long inviteeId, String inviteCode, String inviteeDeviceId) {
        Map<String, Object> result = new HashMap<>();

        // 查找邀请人
        User inviter = userRepository.findByInviteCode(inviteCode).orElse(null);
        if (inviter == null) {
            result.put("success", false);
            result.put("msg", "邀请码无效");
            return result;
        }

        if (inviter.getId().equals(inviteeId)) {
            result.put("success", false);
            result.put("msg", "不能使用自己的邀请码");
            return result;
        }

        User invitee = userRepository.findById(inviteeId).orElse(null);
        if (invitee == null) {
            result.put("success", false);
            result.put("msg", "用户不存在");
            return result;
        }

        // 检查是否已使用过邀请码
        if (invitee.getInvitedBy() != null) {
            result.put("success", false);
            result.put("msg", "已使用过邀请码");
            return result;
        }

        // ========== 防刷机制 ==========

        // 第2层：每日上限检查（每天最多邀请5人）
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        Integer todayCount = inviteRecordRepository.countByInviterIdAndCreatedAtAfter(inviter.getId(), todayStart);
        if (todayCount >= 5) {
            result.put("success", false);
            result.put("msg", "今日邀请已达上限（5人/天）");
            return result;
        }

        // 第3层：总上限检查（累计最多邀请50人）
        Integer totalCount = inviteRecordRepository.countByInviterId(inviter.getId());
        if (totalCount >= 50) {
            result.put("success", false);
            result.put("msg", "邀请奖励已达上限（50人）");
            return result;
        }

        // 第1层：设备去重检查（同设备最多2个账号获得奖励）
        if (inviteeDeviceId != null && !inviteeDeviceId.isEmpty()) {
            Integer deviceCount = inviteRecordRepository.countByInviterIdAndInviteeDeviceId(inviter.getId(), inviteeDeviceId);
            if (deviceCount >= 2) {
                result.put("success", false);
                result.put("msg", "该设备已达到邀请上限");
                return result;
            }
        }

        // ========== 防刷机制结束 ==========

        // 更新被邀请人
        invitee.setInvitedBy(inviter.getId());
        userRepository.save(invitee);

        // 邀请人获得100积分
        pointsService.addPoints(inviter.getId(), 100, "INVITE", "邀请好友奖励（用户ID：" + inviteeId + "）");

        // 被邀请人获得50积分
        pointsService.addPoints(inviteeId, 50, "REWARD", "新用户注册奖励（邀请人ID：" + inviter.getId() + "）");

        // 创建邀请记录
        InviteRecord record = new InviteRecord();
        record.setInviterId(inviter.getId());
        record.setInviteeId(inviteeId);
        record.setInviteeDeviceId(inviteeDeviceId != null ? inviteeDeviceId : "unknown");
        record.setInviterPoints(100);
        record.setInviteePoints(50);
        record.setCreatedAt(LocalDateTime.now());
        inviteRecordRepository.save(record);

        result.put("success", true);
        result.put("inviterPoints", 100);
        result.put("inviteePoints", 50);
        return result;
    }

    /**
     * 查询邀请记录
     */
    public Map<String, Object> getInviteRecords(Long userId, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("msg", "用户不存在");
            return result;
        }

        Pageable pageable = PageRequest.of(page, size);
        List<InviteRecord> records = inviteRecordRepository.findByInviterIdOrderByCreatedAtDesc(userId, pageable);
        Integer total = inviteRecordRepository.countByInviterId(userId);

        result.put("success", true);
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}
