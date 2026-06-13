package com.example.priceprediction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CsItemRawDto {

    @JsonProperty("en_name")
    private String enName;

    @JsonProperty("cn_name")
    private String cnName;

    @JsonProperty("name_id")
    private Long nameId;

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
}