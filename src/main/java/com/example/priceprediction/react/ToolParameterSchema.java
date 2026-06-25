package com.example.priceprediction.react;

public class ToolParameterSchema {

    private final String name;
    private final boolean required;
    private final boolean numeric;
    private final String description;

    public ToolParameterSchema(String name, boolean required, boolean numeric, String description) {
        this.name = name;
        this.required = required;
        this.numeric = numeric;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isNumeric() {
        return numeric;
    }

    public String getDescription() {
        return description;
    }
}
