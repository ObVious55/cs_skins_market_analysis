package com.example.priceprediction.react;

import java.util.List;

public class RegisteredTool {

    private final String name;
    private final String description;
    private final List<ToolParameterSchema> parameters;
    private final boolean externalDataRequired;

    public RegisteredTool(
            String name,
            String description,
            List<ToolParameterSchema> parameters,
            boolean externalDataRequired
    ) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.externalDataRequired = externalDataRequired;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<ToolParameterSchema> getParameters() {
        return parameters;
    }

    public boolean isExternalDataRequired() {
        return externalDataRequired;
    }
}
