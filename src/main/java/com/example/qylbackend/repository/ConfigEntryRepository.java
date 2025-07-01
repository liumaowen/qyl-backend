package com.example.qylbackend.repository;

import com.example.qylbackend.model.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 配置表数据仓库接口
 */
@Repository
public interface ConfigEntryRepository extends JpaRepository<ConfigEntry, Long> {
    ConfigEntry findByKey(String key);
} 