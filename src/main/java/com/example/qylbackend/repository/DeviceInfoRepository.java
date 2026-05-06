package com.example.qylbackend.repository;

import com.example.qylbackend.model.DeviceInfo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 设备信息接口
 */
@Repository
public interface DeviceInfoRepository extends JpaRepository<DeviceInfo, Long> {
    List<DeviceInfo> findByDeviceId(String deviceId);
} 