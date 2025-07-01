package com.example.qylbackend.repository;

import com.example.qylbackend.model.Ad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 广告数据仓库接口
 */
@Repository
public interface AdRepository extends JpaRepository<Ad, Long> {
} 