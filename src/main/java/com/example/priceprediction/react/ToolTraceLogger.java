package com.example.priceprediction.react;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ToolTraceLogger {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void log(ToolTraceStep step, ToolObservation observation) {
        try {
            log.info(
                    "react_tool_trace step={} observation={}",
                    objectMapper.writeValueAsString(step),
                    objectMapper.writeValueAsString(observation)
            );
        } catch (JsonProcessingException e) {
            log.info(
                    "react_tool_trace step={} tool={} status={}",
                    step.getStep(),
                    step.getToolName(),
                    observation.getStatus()
            );
        }
    }
}
