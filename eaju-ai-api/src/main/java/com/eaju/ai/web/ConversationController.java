package com.eaju.ai.web;

import com.eaju.ai.dto.ChatMessageDto;
import com.eaju.ai.dto.conversation.ConversationResponseDto;
import com.eaju.ai.security.CallerPrincipal;
import com.eaju.ai.service.ChatConversationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ChatConversationService chatConversationService;

    public ConversationController(ChatConversationService chatConversationService) {
        this.chatConversationService = chatConversationService;
    }

    @GetMapping
    public List<ConversationResponseDto> list(
            Authentication authentication,
            @RequestParam(name = "agentId", required = false) Long agentId,
            @RequestParam(name = "all", required = false, defaultValue = "false") boolean all) {
        Long integrationId = CallerPrincipal.integrationId(authentication);
        if (integrationId != null) {
            return chatConversationService.listForIntegration(integrationId, CallerPrincipal.userId(authentication));
        }
        // token 中携带的 appId（embed 登录）优先；其次用请求参数 agentId（/home 普通用户选择 Agent）
        Long appId = CallerPrincipal.appId(authentication);
        Long effectiveAppId = appId != null ? appId : agentId;
        if (effectiveAppId != null) {
            return chatConversationService.listForApp(effectiveAppId, CallerPrincipal.userId(authentication));
        }
        Long apiKeyId = CallerPrincipal.apiKeyId(authentication);
        if (apiKeyId != null) {
            return chatConversationService.listForApiKey(apiKeyId);
        }
        // all=true：/home 页全量展示（含 app/embed 类型会话）
        if (all) {
            return chatConversationService.listAllForUser(CallerPrincipal.userId(authentication));
        }
        return chatConversationService.listForUser(CallerPrincipal.userId(authentication));
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessageDto> messages(Authentication authentication, @PathVariable("sessionId") String sessionId) {
        Long apiKeyId = CallerPrincipal.apiKeyId(authentication);
        if (apiKeyId != null) {
            return chatConversationService.loadMessagesForApiKey(apiKeyId, sessionId);
        }
        return chatConversationService.loadMessagesForUser(CallerPrincipal.userId(authentication), sessionId);
    }

    @PostMapping
    public ConversationResponseDto create(
            Authentication authentication,
            @RequestParam(name = "agentId", required = false) Long agentId) {
        Long tokenAppId = CallerPrincipal.appId(authentication);
        Long effectiveAppId = tokenAppId != null ? tokenAppId : agentId;
        return chatConversationService.createNew(
                CallerPrincipal.userId(authentication),
                CallerPrincipal.apiKeyId(authentication),
                effectiveAppId);
    }

    @DeleteMapping("/{sessionId}")
    public void delete(Authentication authentication, @PathVariable("sessionId") String sessionId) {
        Long integrationId = CallerPrincipal.integrationId(authentication);
        if (integrationId != null) {
            chatConversationService.deleteForIntegration(integrationId, sessionId);
            return;
        }
        Long apiKeyId = CallerPrincipal.apiKeyId(authentication);
        if (apiKeyId != null) {
            chatConversationService.deleteForApiKey(apiKeyId, sessionId);
            return;
        }
        chatConversationService.deleteForUser(CallerPrincipal.userId(authentication), sessionId);
    }
}
