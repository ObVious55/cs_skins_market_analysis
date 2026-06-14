package com.example.priceprediction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "item_alias_mapping",
        indexes = {
                @Index(name = "idx_item_alias_status", columnList = "status"),
                @Index(name = "idx_item_alias_item_id", columnList = "item_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_item_alias_normalized", columnNames = "normalized_alias")
        }
)
public class ItemAliasMappingEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CONFLICT = "CONFLICT";
    public static final String SOURCE_USER_CONFIRMED = "USER_CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alias", nullable = false, length = 256)
    private String alias;

    @Column(name = "normalized_alias", nullable = false, length = 256)
    private String normalizedAlias;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "cn_name", nullable = false, length = 512)
    private String cnName;

    @Column(name = "market_hash_name", nullable = false, length = 512)
    private String marketHashName;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "hit_count", nullable = false)
    private Long hitCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getNormalizedAlias() {
        return normalizedAlias;
    }

    public void setNormalizedAlias(String normalizedAlias) {
        this.normalizedAlias = normalizedAlias;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getCnName() {
        return cnName;
    }

    public void setCnName(String cnName) {
        this.cnName = cnName;
    }

    public String getMarketHashName() {
        return marketHashName;
    }

    public void setMarketHashName(String marketHashName) {
        this.marketHashName = marketHashName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Long getHitCount() {
        return hitCount;
    }

    public void setHitCount(Long hitCount) {
        this.hitCount = hitCount;
    }
}
