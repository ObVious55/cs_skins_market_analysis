package com.example.priceprediction.repository;

import com.example.priceprediction.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    // 这种命名方式会让 JPA 自动生成 SQL：SELECT * FROM users WHERE steam_id = ?
    Optional<UserEntity> findBySteamId(String steamId);
}