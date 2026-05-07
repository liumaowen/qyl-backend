package com.example.qylbackend.repository;

import com.example.qylbackend.model.MyOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 设备信息接口
 */
@Repository
public interface MyOrderRepository extends JpaRepository<MyOrder, Long> {
    List<MyOrder> findByDeviceIdAndState(String deviceId,String state);
    List<MyOrder> findByNoAndState(String no,String state);
} 