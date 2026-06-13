package com.example.priceprediction.repository;

import com.example.priceprediction.entity.InventoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, Long> {
    List<InventoryItemEntity> findBySteamId(String steamID);

    @Transactional
    @Modifying
    @Query("DELETE FROM InventoryItemEntity i WHERE i.steamId = :steamId")
    void deleteAllBySteamId(@Param("steamId") String steamId);
}
