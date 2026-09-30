package com.example.qylbackend.service;

import com.example.qylbackend.model.PointsRecord;
import com.example.qylbackend.model.User;
import com.example.qylbackend.repository.PointsRecordRepository;
import com.example.qylbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PointsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointsRecordRepository pointsRecordRepository;

    /**
     * 查询用户积分余额
     */
    public Map<String, Object> getBalance(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        Map<String, Object> result = new HashMap<>();
        if (user == null) {
            result.put("success", false);
            result.put("msg", "用户不存在");
            return result;
        }
        result.put("success", true);
        result.put("points", user.getPoints());
        return result;
    }

    /**
     * 增加积分（带流水记录）
     */
    @Transactional
    public void addPoints(Long userId, Integer amount, String type, String description) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        Integer balanceBefore = user.getPoints();
        user.setPoints(balanceBefore + amount);
        userRepository.save(user);

        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setAmount(amount);
        record.setType(type);
        record.setDescription(description);
        record.setBalanceBefore(balanceBefore);
        record.setBalanceAfter(user.getPoints());
        record.setCreatedAt(LocalDateTime.now());
        pointsRecordRepository.save(record);
    }

    /**
     * 查询积分流水
     */
    public Map<String, Object> getRecords(Long userId, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("msg", "用户不存在");
            return result;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        List<PointsRecord> records = pointsRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Integer total = pointsRecordRepository.countByUserId(userId);

        result.put("success", true);
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 兑换VIP（扣减积分，延长VIP有效期）
     */
    @Transactional
    public Map<String, Object> exchangeVip(Long userId, Integer days) {
        Map<String, Object> result = new HashMap<>();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("msg", "用户不存在");
            return result;
        }

        Integer cost = days * 100; // 100瞬乐币=1天
        if (user.getPoints() < cost) {
            result.put("success", false);
            result.put("msg", "瞬乐币不足");
            return result;
        }

        // 扣减积分
        addPoints(userId, -cost, "EXCHANGE", "兑换" + days + "天VIP");

        // 计算新的VIP到期时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newExpireTime;

        if (user.getVipExpireAt() != null && user.getVipExpireAt().isAfter(now)) {
            // 已有一年VIP且未过期，在现有基础上累加
            newExpireTime = user.getVipExpireAt().plusDays(days);
        } else {
            // VIP已过期或从未有过VIP
            newExpireTime = now.plusDays(days);
        }

        user.setVipExpireAt(newExpireTime);
        userRepository.save(user);

        result.put("success", true);
        result.put("msg", "兑换成功");
        result.put("expireAt", newExpireTime.toString());
        return result;
    }
}
