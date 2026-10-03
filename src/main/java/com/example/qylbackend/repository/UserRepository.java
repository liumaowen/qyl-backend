package com.example.qylbackend.repository;

import com.example.qylbackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓库接口
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
    List<User> findByDeviceId(String deviceId);
    Optional<User> findByInviteCode(String inviteCode);
}
