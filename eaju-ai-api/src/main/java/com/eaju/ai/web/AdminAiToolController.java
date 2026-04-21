package com.eaju.ai.web;

import com.eaju.ai.dto.admin.AiToolDto;
import com.eaju.ai.dto.admin.AiToolSaveRequestDto;
import com.eaju.ai.dto.admin.AiToolTestRequestDto;
import com.eaju.ai.persistence.entity.ApiDefinitionEntity;
import com.eaju.ai.persistence.entity.AiToolEntity;
import com.eaju.ai.persistence.repository.ApiDefinitionRepository;
import com.eaju.ai.service.AiToolService;
import com.eaju.ai.service.ToolCallExecutor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tools")
@Validated
public class AdminAiToolController {

    private final AiToolService service;
    private final ToolCallExecutor toolCallExecutor;
    private final ApiDefinitionRepository apiDefinitionRepository;

    public AdminAiToolController(AiToolService service, ToolCallExecutor toolCallExecutor, ApiDefinitionRepository apiDefinitionRepository) {
        this.service = service;
        this.toolCallExecutor = toolCallExecutor;
        this.apiDefinitionRepository = apiDefinitionRepository;
    }

    @GetMapping
    public List<AiToolDto> list() {
        List<AiToolDto> out = new ArrayList<>();
        for (AiToolEntity e : service.listAll()) {
            out.add(toDto(e));
        }
        return out;
    }

    @PostMapping
    public AiToolDto create(@Valid @RequestBody AiToolSaveRequestDto body) {
        AiToolEntity e = service.create(
                body.getName(), body.getLabel(), body.getDescription(),
                body.getApiDefinitionId(),
                body.getHeadersJson(), body.getBodyTemplate(),
                body.getMethodName(), body.getDataParamsJson(),
                body.getResponseParamsJson(), body.getParamsSchemaJson());
        return toDto(e);
    }

    @PutMapping("/{id}")
    public AiToolDto update(@PathVariable("id") Long id,
                            @RequestBody AiToolSaveRequestDto body) {
        AiToolEntity e = service.update(
                id, body.getName(), body.getLabel(), body.getDescription(),
                body.getApiDefinitionId(),
                body.getHeadersJson(), body.getBodyTemplate(),
                body.getMethodName(), body.getDataParamsJson(),
                body.getResponseParamsJson(), body.getParamsSchemaJson(), body.getEnabled());
        return toDto(e);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    /** 测试工具调用，testContext 提供 context 类型参数的测试值 */
    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable("id") Long id,
                                    @RequestBody(required = false) AiToolTestRequestDto body) {
        AiToolEntity tool = service.listAll().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("工具不存在：" + id));

        Map<String, Object> testCtx = (body != null && body.getTestContext() != null)
                ? body.getTestContext() : Collections.emptyMap();
        String toolArgs = (body != null && body.getToolArgs() != null) ? body.getToolArgs() : "{}";
        Map<String, String> extendedParams = (body != null) ? body.getExtendedParams() : null;

        String requestBody = toolCallExecutor.buildRequestBody(tool, toolArgs, testCtx, extendedParams);

        long start = System.currentTimeMillis();
        // 测试接口使用 executeWithRawResult，返回过滤后的真实响应数据
        String result = toolCallExecutor.executeWithRawResult(tool, toolArgs, testCtx, extendedParams);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("requestBody", requestBody);
        resp.put("result", result);
        resp.put("elapsedMs", elapsed);
        return resp;
    }

    private AiToolDto toDto(AiToolEntity e) {
        AiToolDto dto = new AiToolDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setLabel(e.getLabel());
        dto.setDescription(e.getDescription());
        dto.setApiDefinitionId(e.getApiDefinitionId());
        
        // 从关联的接口定义中获取 URL、HTTP Method、Content-Type
        if (e.getApiDefinitionId() != null) {
            ApiDefinitionEntity apiDef = apiDefinitionRepository.findById(e.getApiDefinitionId()).orElse(null);
            if (apiDef != null) {
                dto.setUrl(apiDef.getRequestUrl());
                dto.setHttpMethod(apiDef.getHttpMethod());
                dto.setContentType(apiDef.getContentType());
            }
        }
        
        dto.setHeadersJson(e.getHeadersJson());
        dto.setBodyTemplate(e.getBodyTemplate());
        dto.setMethodName(e.getMethodName());
        dto.setDataParamsJson(e.getDataParamsJson());
        dto.setResponseParamsJson(e.getResponseParamsJson());
        dto.setParamsSchemaJson(e.getParamsSchemaJson());
        dto.setEnabled(e.isEnabled());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
