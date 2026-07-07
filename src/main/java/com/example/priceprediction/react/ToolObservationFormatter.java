package com.example.priceprediction.react;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ToolObservationFormatter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson(ToolObservation observation) {
        try {
            return objectMapper.writeValueAsString(observation);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"ERROR\",\"errorMessage\":\"Failed to serialize tool observation\"}";
        }
    }
}
