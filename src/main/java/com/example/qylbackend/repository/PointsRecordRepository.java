package com.example.qylbackend.repository;

import com.example.qylbackend.model.PointsRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PointsRecordRepository extends JpaRepository<PointsRecord, Long> {
    List<PointsRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Integer countByUserId(Long userId);
}
