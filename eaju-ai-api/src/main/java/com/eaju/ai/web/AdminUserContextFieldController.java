package com.eaju.ai.web;

import com.eaju.ai.dto.admin.UserContextFieldDto;
import com.eaju.ai.dto.admin.UserContextFieldSaveRequestDto;
import com.eaju.ai.persistence.entity.UserContextFieldEntity;
import com.eaju.ai.service.UserContextFieldService;
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
@RequestMapping("/api/admin/context-fields")
@Validated
public class AdminUserContextFieldController {

    private final UserContextFieldService service;

    public AdminUserContextFieldController(UserContextFieldService service) {
        this.service = service;
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
        UserContextFieldEntity e = service.create(body.getFieldKey(), body.getLabel(), body.getDescription());
        return toDto(e);
    }

    @PutMapping("/{id}")
    public UserContextFieldDto update(@PathVariable("id") Long id,
                                      @RequestBody UserContextFieldSaveRequestDto body) {
        UserContextFieldEntity e = service.update(id, body.getFieldKey(), body.getLabel(),
                body.getDescription(), body.getEnabled());
        return toDto(e);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    private static UserContextFieldDto toDto(UserContextFieldEntity e) {
        UserContextFieldDto dto = new UserContextFieldDto();
        dto.setId(e.getId());
        dto.setFieldKey(e.getFieldKey());
        dto.setLabel(e.getLabel());
        dto.setDescription(e.getDescription());
        dto.setEnabled(e.isEnabled());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
