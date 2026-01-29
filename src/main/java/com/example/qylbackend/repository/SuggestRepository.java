package com.example.qylbackend.repository;

import com.example.qylbackend.model.Suggest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 建议接口
 */
@Repository
public interface SuggestRepository extends JpaRepository<Suggest, Long> {

} 