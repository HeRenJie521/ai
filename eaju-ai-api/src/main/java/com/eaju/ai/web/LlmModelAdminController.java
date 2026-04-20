package com.eaju.ai.web;

import com.eaju.ai.dto.admin.LlmModelAdminResponseDto;
import com.eaju.ai.dto.admin.LlmModelSaveRequestDto;
import com.eaju.ai.service.AdminLlmModelService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/llm-models")
@Validated
public class LlmModelAdminController {

    private final AdminLlmModelService adminLlmModelService;

    public LlmModelAdminController(AdminLlmModelService adminLlmModelService) {
        this.adminLlmModelService = adminLlmModelService;
    }

    /** 查询全部模型，或按 providerId 过滤 */
    @GetMapping
    public List<LlmModelAdminResponseDto> list(
            @RequestParam(name = "providerId", required = false) Long providerId) {
        if (providerId != null) {
            return adminLlmModelService.listByProvider(providerId);
        }
        return adminLlmModelService.listAll();
    }

    @GetMapping("/{id}")
    public LlmModelAdminResponseDto get(@PathVariable("id") Long id) {
        return adminLlmModelService.getById(id);
    }

    @PostMapping
    public LlmModelAdminResponseDto create(@Valid @RequestBody LlmModelSaveRequestDto dto) {
        return adminLlmModelService.create(dto);
    }

    @PutMapping("/{id}")
    public LlmModelAdminResponseDto update(@PathVariable("id") Long id,
                                            @RequestBody LlmModelSaveRequestDto dto) {
        return adminLlmModelService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        adminLlmModelService.delete(id);
    }
}
