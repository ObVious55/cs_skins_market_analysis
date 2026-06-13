package com.example.priceprediction.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter // 放在类上，全类字段自动生成 Getter
@Setter // 放在类上，全类字段自动生成 Setter
@Entity // <--- 关键：必须加上这个，JPA 才能识别此类
@Table(name = "users", indexes = {
        @Index(name = "idx_steam_id", columnList = "steamId")
})
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String steamId;

    @Column(length = 50)
    private String nickname;

    @Column(length = 500)
    private String avatarUrl;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastLoginAt = LocalDateTime.now();
    }
}
