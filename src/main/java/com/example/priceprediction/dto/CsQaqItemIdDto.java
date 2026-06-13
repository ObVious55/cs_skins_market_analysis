package com.example.priceprediction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CsQaqItemIdDto {

    @JsonProperty("id")
    private Long itemId;

    private String name;

    @JsonProperty("market_hash_name")
    private String marketHashName;
}