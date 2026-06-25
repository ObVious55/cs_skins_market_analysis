package com.example.priceprediction.harness;

import java.util.ArrayList;
import java.util.List;

public class RealAgentHarnessCase {

    private String caseName;
    private String query;
    private String memoryId;
    private String expectedItemId;
    private List<String> expectedTools = new ArrayList<>();
    private boolean shouldAskConfirmation;
    private boolean allowPriceInAnswer;

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getExpectedItemId() {
        return expectedItemId;
    }

    public void setExpectedItemId(String expectedItemId) {
        this.expectedItemId = expectedItemId;
    }

    public List<String> getExpectedTools() {
        return expectedTools;
    }

    public void setExpectedTools(List<String> expectedTools) {
        this.expectedTools = expectedTools == null ? new ArrayList<>() : expectedTools;
    }

    public boolean isShouldAskConfirmation() {
        return shouldAskConfirmation;
    }

    public void setShouldAskConfirmation(boolean shouldAskConfirmation) {
        this.shouldAskConfirmation = shouldAskConfirmation;
    }

    public boolean isAllowPriceInAnswer() {
        return allowPriceInAnswer;
    }

    public void setAllowPriceInAnswer(boolean allowPriceInAnswer) {
        this.allowPriceInAnswer = allowPriceInAnswer;
    }
}
