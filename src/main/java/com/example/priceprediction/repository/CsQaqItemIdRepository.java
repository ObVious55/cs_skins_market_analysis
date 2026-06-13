package com.example.priceprediction.repository;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CsQaqItemIdRepository extends JpaRepository<CsQaqItemIdEntity, Long> {

    boolean existsByItemId(Long itemId);

    Optional<CsQaqItemIdEntity> findByItemId(Long itemId);

    Optional<CsQaqItemIdEntity> findByMarketHashName(String marketHashName);

    List<CsQaqItemIdEntity> findByCnNameContaining(String keyword, Pageable pageable);

    List<CsQaqItemIdEntity> findByMarketHashNameContaining(String keyword, Pageable pageable);

    @Query(value = "SELECT item_id FROM cs_qaq_item_id", nativeQuery = true)
    Set<Long> findAllItemIds();

    @Query(value = """
        SELECT *
        FROM cs_qaq_item_id i
        WHERE i.market_hash_name = :name
           OR i.cn_name = :name
        LIMIT 1
        """, nativeQuery = true)
    Optional<CsQaqItemIdEntity> findFirstByExactName(@Param("name") String name);
}