package com.eaju.ai.dto.admin;

import java.util.List;

public class AppToolBindRequestDto {

    /** 绑定的工具 id 列表（顺序即 sortOrder），传空列表表示清除所有绑定 */
    private List<Long> toolIds;

    public List<Long> getToolIds() { return toolIds; }
    public void setToolIds(List<Long> toolIds) { this.toolIds = toolIds; }
}
