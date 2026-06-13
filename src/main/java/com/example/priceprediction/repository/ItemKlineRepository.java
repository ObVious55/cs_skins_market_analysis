package com.example.priceprediction.repository;

import com.example.priceprediction.entity.ItemKlineEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemKlineRepository extends JpaRepository<ItemKlineEntity, Long> {

    Optional<ItemKlineEntity> findByGoodIdAndPlatAndPeriodsAndTimestamp(
            String goodId,
            Integer plat,
            String periods,
            Long timestamp
    );

    List<ItemKlineEntity> findByGoodIdAndPlatAndPeriodsOrderByTimestampDesc(
            String goodId,
            Integer plat,
            String periods,
            Pageable pageable
    );
}
