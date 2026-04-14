package com.eaju.ai.dto.admin;

import java.util.ArrayList;
import java.util.List;

public class ApiKeyUsageDto {

    private long turnCount;
    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalTokens;
    private List<ModelUsageRowDto> byModel = new ArrayList<ModelUsageRowDto>();
    private List<RecentTurnDto> recentTurns = new ArrayList<RecentTurnDto>();

    public long getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(long turnCount) {
        this.turnCount = turnCount;
    }

    public long getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public void setTotalPromptTokens(long totalPromptTokens) {
        this.totalPromptTokens = totalPromptTokens;
    }

    public long getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public void setTotalCompletionTokens(long totalCompletionTokens) {
        this.totalCompletionTokens = totalCompletionTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public List<ModelUsageRowDto> getByModel() {
        return byModel;
    }

    public void setByModel(List<ModelUsageRowDto> byModel) {
        this.byModel = byModel;
    }

    public List<RecentTurnDto> getRecentTurns() {
        return recentTurns;
    }

    public void setRecentTurns(List<RecentTurnDto> recentTurns) {
        this.recentTurns = recentTurns;
    }
}
