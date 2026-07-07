package com.example.priceprediction.react;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Component
public class ToolArgumentValidator {

    public Optional<String> validate(RegisteredTool tool, Map<String, String> arguments) {
        for (ToolParameterSchema parameter : tool.getParameters()) {
            String value = arguments.get(parameter.getName());
            if (parameter.isRequired() && !StringUtils.hasText(value)) {
                return Optional.of("Missing required parameter: " + parameter.getName());
            }
            if (parameter.isNumeric() && StringUtils.hasText(value) && !value.trim().matches("\\d+")) {
                return Optional.of("Parameter must be numeric: " + parameter.getName());
            }
        }
        return Optional.empty();
    }
}
