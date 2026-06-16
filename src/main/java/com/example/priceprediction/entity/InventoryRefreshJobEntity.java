package com.example.priceprediction.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "inventory_refresh_job", indexes = {
        @Index(name = "idx_inventory_refresh_job_steam_id", columnList = "steam_id"),
        @Index(name = "idx_inventory_refresh_job_status", columnList = "status")
})
public class InventoryRefreshJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(name = "steam_id", nullable = false, length = 50)
    private String steamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryRefreshJobStatus status;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
