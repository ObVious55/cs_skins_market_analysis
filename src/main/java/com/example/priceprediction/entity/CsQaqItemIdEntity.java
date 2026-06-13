package com.example.priceprediction.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "cs_qaq_item_id",
        indexes = {
                @Index(name = "idx_qaq_item_id", columnList = "item_id"),
                @Index(name = "idx_qaq_cn_name", columnList = "cn_name"),
                @Index(name = "idx_qaq_market_hash_name", columnList = "market_hash_name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_qaq_item_id", columnNames = "item_id")
        }
)
public class CsQaqItemIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文件中的 id 字段
     */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /**
     * 中文饰品名
     */
    @Column(name = "cn_name", nullable = false, length = 512)
    private String cnName;

    /**
     * Steam 官方英文 market_hash_name
     */
    @Column(name = "market_hash_name", nullable = false, length = 512)
    private String marketHashName;

    protected CsQaqItemIdEntity() {
    }

    public CsQaqItemIdEntity(Long itemId, String cnName, String marketHashName) {
        this.itemId = itemId;
        this.cnName = cnName;
        this.marketHashName = marketHashName;
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getCnName() {
        return cnName;
    }

    public String getMarketHashName() {
        return marketHashName;
    }
}