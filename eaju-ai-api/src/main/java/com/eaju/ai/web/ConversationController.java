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
    public List<ConversationResponseDto> list(Authentication authentication) {
        Long apiKeyId = CallerPrincipal.apiKeyId(authentication);
        if (apiKeyId != null) {
            return chatConversationService.listForApiKey(apiKeyId);
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
    public ConversationResponseDto create(Authentication authentication) {
        return chatConversationService.createNew(
                CallerPrincipal.userId(authentication),
                CallerPrincipal.apiKeyId(authentication));
    }

    @DeleteMapping("/{sessionId}")
    public void delete(Authentication authentication, @PathVariable("sessionId") String sessionId) {
        Long apiKeyId = CallerPrincipal.apiKeyId(authentication);
        if (apiKeyId != null) {
            chatConversationService.deleteForApiKey(apiKeyId, sessionId);
            return;
        }
        chatConversationService.deleteForUser(CallerPrincipal.userId(authentication), sessionId);
    }
}
