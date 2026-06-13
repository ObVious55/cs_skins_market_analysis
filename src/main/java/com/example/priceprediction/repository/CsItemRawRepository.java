package com.example.priceprediction.repository;

import com.example.priceprediction.entity.CsItemRawEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface CsItemRawRepository extends JpaRepository<CsItemRawEntity, Long> {

    boolean existsByNameId(Long nameId);

    @Query(value = "SELECT name_id FROM cs_item_raw", nativeQuery = true)
    Set<Long> findAllNameIds();

    List<CsItemRawEntity> findByCnNameContaining(String keyword);

    List<CsItemRawEntity> findByEnNameContaining(String keyword);

    @Query(value = """
        SELECT *
        FROM cs_item_raw i
        WHERE (:weapon IS NULL OR i.cn_name LIKE CONCAT('%', :weapon, '%'))
          AND (:skin IS NULL OR i.cn_name LIKE CONCAT('%', :skin, '%'))
          AND (:exterior IS NULL OR i.cn_name LIKE CONCAT('%', :exterior, '%'))
        """, nativeQuery = true)
    List<CsItemRawEntity> searchByCnNameKeywords(
            @Param("weapon") String weapon,
            @Param("skin") String skin,
            @Param("exterior") String exterior,
            Pageable pageable
    );

    @Query(value = """
        SELECT *
        FROM cs_item_raw i
        WHERE i.cn_name LIKE CONCAT('%', :keyword, '%')
           OR i.en_name LIKE CONCAT('%', :keyword, '%')
           OR i.map_key LIKE CONCAT('%', :keyword, '%')
        """, nativeQuery = true)
    List<CsItemRawEntity> searchByAnyName(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}