package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

public class UserContextFieldSaveRequestDto {

    @NotBlank
    private String fieldKey;

    @NotBlank
    private String label;

    /** String / Object / Array */
    private String fieldType;

    /** dot-notation 路径，如 data.esusMobile */
    private String parseExpression;

    private String description;

    private Boolean enabled;

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }

    public String getParseExpression() { return parseExpression; }
    public void setParseExpression(String parseExpression) { this.parseExpression = parseExpression; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
