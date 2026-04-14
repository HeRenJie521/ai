package com.eaju.ai.web;

import com.eaju.ai.dto.admin.LlmProviderAdminResponseDto;
import com.eaju.ai.dto.admin.LlmProviderCreateRequestDto;
import com.eaju.ai.dto.admin.LlmProviderUpdateRequestDto;
import com.eaju.ai.service.AdminLlmProviderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/llm-providers")
@Validated
public class AdminLlmProviderController {

    private final AdminLlmProviderService adminLlmProviderService;

    public AdminLlmProviderController(AdminLlmProviderService adminLlmProviderService) {
        this.adminLlmProviderService = adminLlmProviderService;
    }

    @GetMapping
    public List<LlmProviderAdminResponseDto> list() {
        return adminLlmProviderService.listAll();
    }

    @GetMapping("/{id}")
    public LlmProviderAdminResponseDto get(@PathVariable("id") Long id) {
        return adminLlmProviderService.getById(id);
    }

    @PostMapping
    public LlmProviderAdminResponseDto create(@Valid @RequestBody LlmProviderCreateRequestDto dto) {
        return adminLlmProviderService.create(dto);
    }

    @PutMapping("/{id}")
    public LlmProviderAdminResponseDto update(
            @PathVariable("id") Long id,
            @RequestBody LlmProviderUpdateRequestDto dto) {
        return adminLlmProviderService.update(id, dto);
    }
}
