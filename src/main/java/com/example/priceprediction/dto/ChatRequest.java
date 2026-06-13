package com.example.priceprediction.dto;

import  lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String steamId;
    private boolean followUp;
}
