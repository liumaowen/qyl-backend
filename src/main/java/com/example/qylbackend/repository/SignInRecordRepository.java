package com.example.qylbackend.repository;

import com.example.qylbackend.model.SignInRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SignInRecordRepository extends JpaRepository<SignInRecord, Long> {
    Optional<SignInRecord> findByUserIdAndSignDate(Long userId, LocalDate date);
    List<SignInRecord> findByUserIdOrderBySignDateDesc(Long userId, Pageable pageable);
}
