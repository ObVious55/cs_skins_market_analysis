package com.example.priceprediction.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cs_item_raw")
public class CsItemRawEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_key", nullable = false, length = 500)
    private String mapKey;

    @Column(name = "en_name", nullable = false, length = 500)
    private String enName;

    @Column(name = "cn_name", nullable = false, length = 500)
    private String cnName;

    @Column(name = "name_id", nullable = false, unique = true)
    private Long nameId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public CsItemRawEntity() {
    }

    public CsItemRawEntity(String mapKey, String enName, String cnName, Long nameId) {
        this.mapKey = mapKey;
        this.enName = enName;
        this.cnName = cnName;
        this.nameId = nameId;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getMapKey() {
        return mapKey;
    }

    public void setMapKey(String mapKey) {
        this.mapKey = mapKey;
    }

    public String getEnName() {
        return enName;
    }

    public void setEnName(String enName) {
        this.enName = enName;
    }

    public String getCnName() {
        return cnName;
    }

    public void setCnName(String cnName) {
        this.cnName = cnName;
    }

    public Long getNameId() {
        return nameId;
    }

    public void setNameId(Long nameId) {
        this.nameId = nameId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}