package com.eaju.ai.web;

import com.eaju.ai.dto.admin.UserContextFieldDto;
import com.eaju.ai.dto.admin.UserContextFieldSaveRequestDto;
import com.eaju.ai.dto.auth.LoginSessionSnapshot;
import com.eaju.ai.persistence.entity.UserContextFieldEntity;
import com.eaju.ai.security.AuthUserPrincipal;
import com.eaju.ai.service.LoginSessionCacheService;
import com.eaju.ai.service.UserContextFieldService;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/context-fields")
@Validated
public class AdminUserContextFieldController {

    private final UserContextFieldService service;
    private final LoginSessionCacheService loginSessionCacheService;

    public AdminUserContextFieldController(UserContextFieldService service,
                                           LoginSessionCacheService loginSessionCacheService) {
        this.service = service;
        this.loginSessionCacheService = loginSessionCacheService;
    }

    @GetMapping
    public List<UserContextFieldDto> list() {
        List<UserContextFieldDto> out = new ArrayList<>();
        for (UserContextFieldEntity e : service.listAll()) {
            out.add(toDto(e));
        }
        return out;
    }

    @PostMapping
    public UserContextFieldDto create(@Valid @RequestBody UserContextFieldSaveRequestDto body) {
        UserContextFieldEntity e = service.create(
                body.getFieldKey(), body.getLabel(),
                body.getFieldType(), body.getParseExpression(),
                body.getDescription());
        return toDto(e);
    }

    @PutMapping("/{id}")
    public UserContextFieldDto update(@PathVariable("id") Long id,
                                      @RequestBody UserContextFieldSaveRequestDto body) {
        UserContextFieldEntity e = service.update(
                id, body.getFieldKey(), body.getLabel(),
                body.getFieldType(), body.getParseExpression(),
                body.getDescription(), body.getEnabled());
        return toDto(e);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    /** 用当前登录管理员的 DMS 会话数据测试字段解析是否成功 */
    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable("id") Long id, Authentication authentication) {
        UserContextFieldEntity field = service.findById(id);

        // 获取当前管理员 JTI → 从 Redis 取 DMS 登录数据
        String jti = null;
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserPrincipal) {
            jti = ((AuthUserPrincipal) authentication.getPrincipal()).getJti();
        }

        Map<String, Object> resp = new LinkedHashMap<>();

        String dmsJson = null;
        if (StringUtils.hasText(jti)) {
            Optional<LoginSessionSnapshot> snapshot = loginSessionCacheService.get(jti);
            if (snapshot.isPresent()) {
                dmsJson = snapshot.get().getDmsResponseExcerpt();
            }
        }

        if (!StringUtils.hasText(dmsJson)) {
            resp.put("found", false);
            resp.put("value", null);
            resp.put("error", "未找到当前账号的 DMS 登录数据，请确认是否已登录系统");
            return resp;
        }

        Object value = service.testExtract(field.getParseExpression(), field.getFieldType(), dmsJson);
        resp.put("found", value != null);
        resp.put("value", value != null ? value.toString() : null);
        resp.put("expression", field.getParseExpression());
        return resp;
    }

    private static UserContextFieldDto toDto(UserContextFieldEntity e) {
        UserContextFieldDto dto = new UserContextFieldDto();
        dto.setId(e.getId());
        dto.setFieldKey(e.getFieldKey());
        dto.setLabel(e.getLabel());
        dto.setFieldType(e.getFieldType());
        dto.setParseExpression(e.getParseExpression());
        dto.setDescription(e.getDescription());
        dto.setEnabled(e.isEnabled());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
