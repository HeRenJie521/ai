package com.eaju.ai.web;

import com.eaju.ai.dto.ChatRequestDto;
import com.eaju.ai.security.CallerPrincipal;
import com.eaju.ai.service.ChatConversationService;
import com.eaju.ai.service.ChatService;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

    private final ChatService chatService;
    private final ChatConversationService chatConversationService;

    public ChatController(ChatService chatService, ChatConversationService chatConversationService) {
        this.chatService = chatService;
        this.chatConversationService = chatConversationService;
    }

    /**
     * AI 聊天：JWT 或 X-API-Key；{@code userId} 自动填为当前身份。
     * {@code stream=true} 时返回 SSE；否则阻塞 JSON。
     */
    @PostMapping("/chat")
    public Object chat(@Valid @RequestBody ChatRequestDto request, Authentication authentication) {
        applyPrincipal(request, authentication);
        // API Key / 嵌入集成 / 应用嵌入鉴权：sessionId 未传时自动生成 UUID，确保每次对话都落库
        Long apiKeyId = CallerPrincipal.apiKeyId(authentication);
        Long integrationId = CallerPrincipal.integrationId(authentication);
        Long appId = CallerPrincipal.appId(authentication);
        if ((apiKeyId != null || integrationId != null || appId != null)
                && !StringUtils.hasText(request.getSessionId())) {
            request.setSessionId(UUID.randomUUID().toString());
        }
        String uid = CallerPrincipal.userId(authentication);
        request.setInternalApiKeyId(apiKeyId);
        request.setInternalIntegrationId(integrationId);
        request.setInternalAppId(appId);
        request.setInternalJti(CallerPrincipal.jti(authentication));
        if (StringUtils.hasText(uid) && StringUtils.hasText(request.getSessionId())) {
            chatConversationService.touchOnChatStart(
                    uid,
                    apiKeyId,
                    integrationId,
                    appId,
                    request.getSessionId(),
                    request.getMessages(),
                    request.getProvider(),
                    request.getMode());
        }
        if (Boolean.TRUE.equals(request.getStream())) {
            return chatService.chatStream(request);
        }
        return chatService.chat(request);
    }

    private static void applyPrincipal(ChatRequestDto request, Authentication authentication) {
        String uid = CallerPrincipal.userId(authentication);
        if (StringUtils.hasText(uid) && !StringUtils.hasText(request.getUserId())) {
            request.setUserId(uid);
        }
    }
}
