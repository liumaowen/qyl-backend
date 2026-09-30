package com.example.qylbackend.service;

import com.example.qylbackend.model.SignInRecord;
import com.example.qylbackend.model.User;
import com.example.qylbackend.repository.SignInRecordRepository;
import com.example.qylbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SignInService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SignInRecordRepository signInRecordRepository;

    @Autowired
    private PointsService pointsService;

    /**
     * 每日签到
     */
    @Transactional
    public Map<String, Object> signIn(Long userId) {
        Map<String, Object> result = new HashMap<>();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("msg", "用户不存在");
            return result;
        }

        LocalDate today = LocalDate.now();

        // 检查今天是否已签到
        if (signInRecordRepository.findByUserIdAndSignDate(userId, today).isPresent()) {
            result.put("success", false);
            result.put("msg", "今天已签到");
            return result;
        }

        // 计算连续签到天数
        int consecutiveDays = 1;
        LocalDate yesterday = today.minusDays(1);
        Optional<SignInRecord> yesterdayRecord = signInRecordRepository.findByUserIdAndSignDate(userId, yesterday);

        if (yesterdayRecord.isPresent()) {
            consecutiveDays = yesterdayRecord.get().getConsecutiveDays() + 1;
        }

        // 计算获得积分
        Integer points = 10; // 基础10积分
        StringBuilder desc = new StringBuilder("每日签到+10");

        // 连续7天额外奖励
        if (consecutiveDays % 7 == 0) {
            points += 100;
            desc.append("（连续7天奖励+100）");
        }

        // 连续30天额外奖励
        if (consecutiveDays % 30 == 0) {
            points += 500;
            desc.append("（连续30天奖励+500）");
        }

        // 创建签到记录
        SignInRecord record = new SignInRecord();
        record.setUserId(userId);
        record.setSignDate(today);
        record.setPoints(points);
        record.setConsecutiveDays(consecutiveDays);
        record.setCreatedAt(LocalDateTime.now());
        signInRecordRepository.save(record);

        // 增加积分
        pointsService.addPoints(userId, points, "SIGNIN", desc.toString());

        result.put("success", true);
        result.put("points", points);
        result.put("consecutiveDays", consecutiveDays);
        result.put("totalPoints", user.getPoints() + points);
        return result;
    }

    /**
     * 查询签到信息
     */
    public Map<String, Object> getSignInInfo(Long userId) {
        Map<String, Object> result = new HashMap<>();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("msg", "用户不存在");
            return result;
        }

        LocalDate today = LocalDate.now();

        // 今天是否已签到
        Boolean signedToday = signInRecordRepository.findByUserIdAndSignDate(userId, today).isPresent();

        // 当前连续签到天数
        Integer consecutiveDays = 0;
        Optional<SignInRecord> todayRecord = signInRecordRepository.findByUserIdAndSignDate(userId, today);

        if (todayRecord.isPresent()) {
            consecutiveDays = todayRecord.get().getConsecutiveDays();
        } else {
            // 查询昨天
            LocalDate yesterday = today.minusDays(1);
            Optional<SignInRecord> yesterdayRecord = signInRecordRepository.findByUserIdAndSignDate(userId, yesterday);
            if (yesterdayRecord.isPresent()) {
                consecutiveDays = yesterdayRecord.get().getConsecutiveDays();
            }
        }

        // 最近30天签到记录
        Pageable pageable = PageRequest.of(0, 30);
        List<SignInRecord> recentRecords = signInRecordRepository.findByUserIdOrderBySignDateDesc(userId, pageable);

        result.put("success", true);
        result.put("signedToday", signedToday);
        result.put("consecutiveDays", consecutiveDays);
        result.put("recentRecords", recentRecords);
        return result;
    }
}
