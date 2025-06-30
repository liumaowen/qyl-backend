package com.example.qylbackend.repository;

import com.example.qylbackend.model.AppVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AppVersion 数据仓库接口
 */
@Repository
public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {

    /**
     * Spring Data JPA 的神奇之处:
     * 这个方法名会自动翻译成 SQL 查询，用于查找 createdAt 字段值最大（最新）的那一条记录。
     * @return 一个可能包含最新AppVersion的Optional对象
     */
    Optional<AppVersion> findTopByOrderByCreatedAtDesc();
} 