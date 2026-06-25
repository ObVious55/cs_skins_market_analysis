package com.example.priceprediction.service;

public class AgentTaskState {

    private String memoryId;
    private String currentTask;
    private String currentItemId;
    private String currentPrimaryName;
    private String currentMarketHashName;
    private boolean itemConfirmed;
    private String confirmationSource;
    private long confirmedAtMillis;
    private String lastToolName;
    private String lastToolStatus;
    private String nextAction;
    private long updatedAtMillis;

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(String currentTask) {
        this.currentTask = currentTask;
    }

    public String getCurrentItemId() {
        return currentItemId;
    }

    public void setCurrentItemId(String currentItemId) {
        this.currentItemId = currentItemId;
    }

    public String getCurrentPrimaryName() {
        return currentPrimaryName;
    }

    public void setCurrentPrimaryName(String currentPrimaryName) {
        this.currentPrimaryName = currentPrimaryName;
    }

    public String getCurrentMarketHashName() {
        return currentMarketHashName;
    }

    public void setCurrentMarketHashName(String currentMarketHashName) {
        this.currentMarketHashName = currentMarketHashName;
    }

    public boolean isItemConfirmed() {
        return itemConfirmed;
    }

    public void setItemConfirmed(boolean itemConfirmed) {
        this.itemConfirmed = itemConfirmed;
    }

    public String getConfirmationSource() {
        return confirmationSource;
    }

    public void setConfirmationSource(String confirmationSource) {
        this.confirmationSource = confirmationSource;
    }

    public long getConfirmedAtMillis() {
        return confirmedAtMillis;
    }

    public void setConfirmedAtMillis(long confirmedAtMillis) {
        this.confirmedAtMillis = confirmedAtMillis;
    }

    public String getLastToolName() {
        return lastToolName;
    }

    public void setLastToolName(String lastToolName) {
        this.lastToolName = lastToolName;
    }

    public String getLastToolStatus() {
        return lastToolStatus;
    }

    public void setLastToolStatus(String lastToolStatus) {
        this.lastToolStatus = lastToolStatus;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }
}
