package com.eaju.ai.web;

import com.eaju.ai.dto.admin.AiAppCreateRequestDto;
import com.eaju.ai.dto.admin.AiAppResponseDto;
import com.eaju.ai.dto.admin.AiAppUpdateRequestDto;
import com.eaju.ai.persistence.entity.AiAppEntity;
import com.eaju.ai.service.AiAppService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-apps")
@Validated
public class AdminAiAppController {

    private final AiAppService aiAppService;

    public AdminAiAppController(AiAppService aiAppService) {
        this.aiAppService = aiAppService;
    }

    @GetMapping
    public List<AiAppResponseDto> list() {
        List<AiAppResponseDto> out = new ArrayList<>();
        for (AiAppEntity e : aiAppService.listAll()) {
            out.add(toDto(e));
        }
        return out;
    }

    @PostMapping
    public AiAppResponseDto create(@Valid @RequestBody AiAppCreateRequestDto body) {
        AiAppEntity e = aiAppService.create(
                body.getName(),
                body.getWelcomeText(),
                body.getSuggestions(),
                body.getSystemRole(),
                body.getSystemTask(),
                body.getSystemConstraints(),
                body.getModelId());
        return toDto(e);
    }

    @PatchMapping("/{id}")
    public AiAppResponseDto update(@PathVariable("id") Long id,
                                   @RequestBody AiAppUpdateRequestDto body) {
        AiAppEntity e = aiAppService.update(
                id,
                body.getName(),
                body.getWelcomeText(),
                body.getSuggestions(),
                body.getSystemRole(),
                body.getSystemTask(),
                body.getSystemConstraints(),
                body.getModelId());
        return toDto(e);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        aiAppService.delete(id);
    }

    private static AiAppResponseDto toDto(AiAppEntity e) {
        AiAppResponseDto dto = new AiAppResponseDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setWelcomeText(e.getWelcomeText());
        dto.setSuggestions(e.getSuggestions());
        dto.setSystemRole(e.getSystemRole());
        dto.setSystemTask(e.getSystemTask());
        dto.setSystemConstraints(e.getSystemConstraints());
        dto.setModelId(e.getModelId());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
