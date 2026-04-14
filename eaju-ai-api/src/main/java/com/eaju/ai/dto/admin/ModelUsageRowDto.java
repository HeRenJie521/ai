package com.eaju.ai.dto.admin;

public class ModelUsageRowDto {

    private String model;
    private long turnCount;
    private long totalTokens;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(long turnCount) {
        this.turnCount = turnCount;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }
}
