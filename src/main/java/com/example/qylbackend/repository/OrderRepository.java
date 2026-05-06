package com.example.qylbackend.repository;

import com.example.qylbackend.model.Order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 设备信息接口
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByDeviceIdAndState(String deviceId,String state);
    List<Order> findByNoAndState(String no,String state);
} 