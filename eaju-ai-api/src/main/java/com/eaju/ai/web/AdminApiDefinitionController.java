package com.eaju.ai.web;

import com.eaju.ai.dto.admin.ApiDefinitionDto;
import com.eaju.ai.dto.admin.ApiDefinitionSaveRequestDto;
import com.eaju.ai.persistence.entity.ApiDefinitionEntity;
import com.eaju.ai.service.ApiDefinitionService;
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
import java.util.List;

/**
 * 接口定义管理 Controller
 */
@RestController
@RequestMapping("/api/admin/api-definitions")
@Validated
public class AdminApiDefinitionController {

    private final ApiDefinitionService service;

    public AdminApiDefinitionController(ApiDefinitionService service) {
        this.service = service;
    }

    /**
     * 获取所有接口定义列表
     * GET /api/admin/api-definitions
     */
    @GetMapping
    public List<ApiDefinitionDto> list() {
        List<ApiDefinitionDto> out = new ArrayList<>();
        for (ApiDefinitionEntity e : service.listAll()) {
            out.add(ApiDefinitionDto.fromEntity(e));
        }
        return out;
    }

    /**
     * 创建接口定义
     * POST /api/admin/api-definitions
     */
    @PostMapping
    public ApiDefinitionDto create(@Valid @RequestBody ApiDefinitionSaveRequestDto body) {
        ApiDefinitionEntity e = service.create(
                body.getSystemName(),
                body.getRequestUrl(),
                body.getHttpMethod(),
                body.getContentType(),
                body.getRemark());
        return ApiDefinitionDto.fromEntity(e);
    }

    /**
     * 更新接口定义
     * PUT /api/admin/api-definitions/{id}
     */
    @PutMapping("/{id}")
    public ApiDefinitionDto update(@PathVariable("id") Long id,
                                   @RequestBody ApiDefinitionSaveRequestDto body) {
        ApiDefinitionEntity e = service.update(
                id,
                body.getSystemName(),
                body.getRequestUrl(),
                body.getHttpMethod(),
                body.getContentType(),
                body.getRemark());
        return ApiDefinitionDto.fromEntity(e);
    }

    /**
     * 删除接口定义
     * DELETE /api/admin/api-definitions/{id}
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
