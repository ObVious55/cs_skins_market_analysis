package com.example.priceprediction.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_inventory", indexes = {
        // 为 steamId 建立索引，方便按用户快速查询库存
        @Index(name = "idx_steam_id", columnList = "steamId")
})
public class InventoryItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 对应用户的 SteamID，长度与 UserEntity 保持一致
    @Column(nullable = false, length = 50)
    private String steamId;

    // 饰品的唯一实例 ID，设置为唯一索引，防止重复存储同一件饰品
    @Column(unique = true, nullable = false, length = 32)
    private String assetId;

    private String name;

    private String wear; // 磨损等级

    private Integer amount = 1; // 数量，默认为 1

    @Column(columnDefinition = "TEXT")
    private String iconUrl; // 图片链接通常很长，使用 TEXT 类型

    private LocalDateTime updatedAt;

    // 在新增或更新记录前，自动刷新时间戳
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}