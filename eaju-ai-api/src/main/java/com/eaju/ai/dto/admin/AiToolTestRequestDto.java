package com.eaju.ai.dto.admin;

import java.util.Map;

public class AiToolTestRequestDto {

    /** 用于替换工具参数中 valueType=context 字段的测试值，key 为 fieldKey */
    private Map<String, Object> testContext;

    /** 模拟 LLM 传入的工具参数 JSON 字符串，可为空 */
    private String toolArgs;

    public Map<String, Object> getTestContext() { return testContext; }
    public void setTestContext(Map<String, Object> testContext) { this.testContext = testContext; }

    public String getToolArgs() { return toolArgs; }
    public void setToolArgs(String toolArgs) { this.toolArgs = toolArgs; }
}
