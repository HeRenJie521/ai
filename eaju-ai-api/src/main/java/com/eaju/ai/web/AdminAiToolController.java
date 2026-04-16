package com.eaju.ai.web;

import com.eaju.ai.dto.admin.AiToolDto;
import com.eaju.ai.dto.admin.AiToolSaveRequestDto;
import com.eaju.ai.persistence.entity.AiToolEntity;
import com.eaju.ai.service.AiToolService;
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

@RestController
@RequestMapping("/api/admin/tools")
@Validated
public class AdminAiToolController {

    private final AiToolService service;

    public AdminAiToolController(AiToolService service) {
        this.service = service;
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
                body.getHttpMethod(), body.getUrl(),
                body.getHeadersJson(), body.getBodyTemplate(), body.getParamsSchemaJson());
        return toDto(e);
    }

    @PutMapping("/{id}")
    public AiToolDto update(@PathVariable("id") Long id,
                            @RequestBody AiToolSaveRequestDto body) {
        AiToolEntity e = service.update(
                id, body.getName(), body.getLabel(), body.getDescription(),
                body.getHttpMethod(), body.getUrl(),
                body.getHeadersJson(), body.getBodyTemplate(), body.getParamsSchemaJson(),
                body.getEnabled());
        return toDto(e);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    private static AiToolDto toDto(AiToolEntity e) {
        AiToolDto dto = new AiToolDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setLabel(e.getLabel());
        dto.setDescription(e.getDescription());
        dto.setHttpMethod(e.getHttpMethod());
        dto.setUrl(e.getUrl());
        dto.setHeadersJson(e.getHeadersJson());
        dto.setBodyTemplate(e.getBodyTemplate());
        dto.setParamsSchemaJson(e.getParamsSchemaJson());
        dto.setEnabled(e.isEnabled());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
