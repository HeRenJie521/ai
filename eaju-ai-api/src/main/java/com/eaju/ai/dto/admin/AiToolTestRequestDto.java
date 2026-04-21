package com.eaju.ai.dto.admin;

import java.util.Map;

public class AiToolTestRequestDto {

    /** 用于替换工具参数中 valueType=context 字段的测试值，key 为 fieldKey */
    private Map<String, Object> testContext;

    /** 模拟 LLM 传入的工具参数 JSON 字符串，可为空 */
    private String toolArgs;

    /** 模拟 extended_parameters 中的 APIKey 扩展参数，key 为 fieldKey */
    private Map<String, String> extendedParams;

    public Map<String, Object> getTestContext() { return testContext; }
    public void setTestContext(Map<String, Object> testContext) { this.testContext = testContext; }

    public String getToolArgs() { return toolArgs; }
    public void setToolArgs(String toolArgs) { this.toolArgs = toolArgs; }

    public Map<String, String> getExtendedParams() { return extendedParams; }
    public void setExtendedParams(Map<String, String> extendedParams) { this.extendedParams = extendedParams; }
}
