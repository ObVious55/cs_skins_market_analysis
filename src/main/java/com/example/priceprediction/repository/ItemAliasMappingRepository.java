package com.example.priceprediction.repository;

import com.example.priceprediction.entity.ItemAliasMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemAliasMappingRepository extends JpaRepository<ItemAliasMappingEntity, Long> {

    Optional<ItemAliasMappingEntity> findByNormalizedAlias(String normalizedAlias);

    List<ItemAliasMappingEntity> findByStatus(String status);
}
