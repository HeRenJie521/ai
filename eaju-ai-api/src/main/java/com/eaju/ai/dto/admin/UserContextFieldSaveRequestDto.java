package com.eaju.ai.dto.admin;

import javax.validation.constraints.NotBlank;

public class UserContextFieldSaveRequestDto {

    @NotBlank
    private String fieldKey;

    @NotBlank
    private String label;

    private String description;

    private Boolean enabled;

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
