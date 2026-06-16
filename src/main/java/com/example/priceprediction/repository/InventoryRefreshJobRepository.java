package com.example.priceprediction.repository;

import com.example.priceprediction.entity.InventoryRefreshJobEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRefreshJobRepository extends JpaRepository<InventoryRefreshJobEntity, Long> {

    Optional<InventoryRefreshJobEntity> findByRequestId(String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM InventoryRefreshJobEntity j WHERE j.requestId = :requestId")
    Optional<InventoryRefreshJobEntity> findWithLockByRequestId(@Param("requestId") String requestId);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO inventory_refresh_job
                (request_id, steam_id, status, attempts, created_at, updated_at, started_at)
            VALUES
                (:requestId, :steamId, 'PROCESSING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertProcessingIfAbsent(@Param("requestId") String requestId, @Param("steamId") String steamId);
}
