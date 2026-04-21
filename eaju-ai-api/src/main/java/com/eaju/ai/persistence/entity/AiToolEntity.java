package com.eaju.ai.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_tool")
public class AiToolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 工具唯一名称（英文），用于 LLM function calling 的 name 字段 */
    @Column(nullable = false, unique = true, length = 128)
    private String name;

    /** 工具显示名 */
    @Column(nullable = false, length = 256)
    private String label;

    /** 工具功能描述，LLM 据此决定何时调用 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** 关联的接口定义 ID，通过此字段关联到 api_definition 表获取 httpMethod、url、contentType 等信息 */
    @Column(name = "api_definition_id")
    private Long apiDefinitionId;

    /** 请求头 JSON 对象，支持 {{var}} 模板变量 */
    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    /** 请求体模板（自由格式，与 dataParamsJson 二选一） */
    @Column(name = "body_template", columnDefinition = "TEXT")
    private String bodyTemplate;

    /**
     * DMS 固定格式请求的方法名，如 miniAppMenuFunctionQuery。
     * 若设置，则请求体将自动构建为 {"data":{...},"methodName":"..."}
     */
    @Column(name = "method_name", length = 256)
    private String methodName;

    /**
     * data 对象的参数配置，JSON 数组，每项格式：
     * {"key":"userId","valueType":"context","fieldKey":"esusMobile"}  — 引用用户数据字段
     * {"key":"functionName","valueType":"static","value":"智蚁轨迹"}   — 静态值
     */
    @Column(name = "data_params_json", columnDefinition = "TEXT")
    private String dataParamsJson;

    /**
     * 出参字段说明，JSON 树，最多 3 级，每节点格式：
     * {"key":"ebcdCode","label":"数据字典编码","fieldType":"String","description":"可用于数据提交","children":[]}
     * 工具调用完成后追加到 LLM tool 消息，帮助 AI 理解返回字段含义。
     */
    @Column(name = "response_params_json", columnDefinition = "TEXT")
    private String responseParamsJson;

    /**
     * 传给 LLM 的 parameters JSON Schema，例如：
     * {"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"}},"required":["query"]}
     */
    @Column(name = "params_schema_json", nullable = false, columnDefinition = "TEXT")
    private String paramsSchemaJson;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant n = Instant.now();
        if (createdAt == null) createdAt = n;
        updatedAt = n;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getApiDefinitionId() { return apiDefinitionId; }
    public void setApiDefinitionId(Long apiDefinitionId) { this.apiDefinitionId = apiDefinitionId; }

    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getDataParamsJson() { return dataParamsJson; }
    public void setDataParamsJson(String dataParamsJson) { this.dataParamsJson = dataParamsJson; }

    public String getResponseParamsJson() { return responseParamsJson; }
    public void setResponseParamsJson(String responseParamsJson) { this.responseParamsJson = responseParamsJson; }

    public String getParamsSchemaJson() { return paramsSchemaJson; }
    public void setParamsSchemaJson(String paramsSchemaJson) { this.paramsSchemaJson = paramsSchemaJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
