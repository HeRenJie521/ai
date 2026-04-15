package com.eaju.ai.web;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.admin.ApiKeyCreateRequestDto;
import com.eaju.ai.dto.admin.ApiKeyCreateResponseDto;
import com.eaju.ai.dto.admin.ApiKeyPatchRequestDto;
import com.eaju.ai.dto.admin.ApiKeyResponseDto;
import com.eaju.ai.dto.admin.ApiKeyUsageDto;
import com.eaju.ai.dto.conversation.ConversationResponseDto;
import com.eaju.ai.persistence.entity.ApiKeyEntity;
import com.eaju.ai.service.ApiKeyAuditService;
import com.eaju.ai.service.ApiKeyService;
import com.eaju.ai.service.ChatConversationService;
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
@RequestMapping("/api/admin/api-keys")
@Validated
public class AdminApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ApiKeyAuditService apiKeyAuditService;
    private final ChatConversationService chatConversationService;

    public AdminApiKeyController(
            ApiKeyService apiKeyService,
            ApiKeyAuditService apiKeyAuditService,
            ChatConversationService chatConversationService) {
        this.apiKeyService = apiKeyService;
        this.apiKeyAuditService = apiKeyAuditService;
        this.chatConversationService = chatConversationService;
    }

    @GetMapping
    public List<ApiKeyResponseDto> list() {
        List<ApiKeyResponseDto> out = new ArrayList<>();
        for (ApiKeyEntity e : apiKeyService.listAll()) {
            out.add(toRow(e));
        }
        return out;
    }

    @PostMapping
    public ApiKeyCreateResponseDto create(@Valid @RequestBody ApiKeyCreateRequestDto body) {
        ApiKeyService.CreatedApiKey created = apiKeyService.create(
                body.getName(), body.getType(), body.getAllowedOrigins());
        ApiKeyEntity e = created.getEntity();
        ApiKeyCreateResponseDto dto = new ApiKeyCreateResponseDto();
        copyToRow(e, dto);
        dto.setPlainSecret(created.getPlainSecret());
        return dto;
    }

    @PatchMapping("/{id}")
    public ApiKeyResponseDto patch(@PathVariable("id") Long id,
                                   @RequestBody ApiKeyPatchRequestDto body) {
        ApiKeyEntity e = apiKeyService.update(id, body.getName(), body.getEnabled(),
                body.getAllowedOrigins());
        return toRow(e);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        apiKeyService.delete(id);
    }

    @GetMapping("/{id}/usage")
    public ApiKeyUsageDto usage(@PathVariable("id") Long id) {
        return apiKeyAuditService.buildUsage(id);
    }

    @GetMapping("/{id}/conversations")
    public List<ConversationResponseDto> conversations(@PathVariable("id") Long id) {
        if (!apiKeyService.findById(id).isPresent()) {
            throw new IllegalArgumentException("API Key 不存在");
        }
        return chatConversationService.listForApiKey(id);
    }

    @GetMapping("/{id}/sessions/{sessionId}/messages")
    public List<ChatMessageDto> sessionMessages(
            @PathVariable("id") Long id,
            @PathVariable("sessionId") String sessionId) {
        if (!apiKeyService.findById(id).isPresent()) {
            throw new IllegalArgumentException("API Key 不存在");
        }
        return apiKeyAuditService.loadMessagesWithTimestamps(id, sessionId);
    }

    private ApiKeyResponseDto toRow(ApiKeyEntity e) {
        ApiKeyResponseDto dto = new ApiKeyResponseDto();
        copyToRow(e, dto);
        return dto;
    }

    private void copyToRow(ApiKeyEntity e, ApiKeyResponseDto dto) {
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setSecretPrefix(e.getSecretPrefix());
        dto.setEnabled(e.isEnabled());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        dto.setType(e.getType());
        dto.setAllowedOrigins(e.getAllowedOrigins());
    }
}
