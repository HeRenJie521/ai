package com.eaju.ai.web;

import com.eaju.ai.dto.admin.AdminAccountDto;
import com.eaju.ai.dto.admin.AdminAccountSaveRequestDto;
import com.eaju.ai.persistence.entity.AdminAccountEntity;
import com.eaju.ai.persistence.repository.AdminAccountRepository;
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

/**
 * 系统管理员账号管理接口
 */
@RestController
@RequestMapping("/api/admin/admin-accounts")
@Validated
public class AdminAccountController {

    private final AdminAccountRepository adminAccountRepository;

    public AdminAccountController(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    /**
     * 获取所有系统管理员列表
     */
    @GetMapping
    public List<AdminAccountDto> list() {
        List<AdminAccountEntity> entities = adminAccountRepository.findAllByOrderByCreatedAtDesc();
        List<AdminAccountDto> result = new ArrayList<>();
        for (AdminAccountEntity e : entities) {
            result.add(toDto(e));
        }
        return result;
    }

    /**
     * 创建系统管理员
     */
    @PostMapping
    public AdminAccountDto create(@Valid @RequestBody AdminAccountSaveRequestDto body) {
        String phone = normalizePhone(body.getPhone());
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }

        if (adminAccountRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("该手机号已存在");
        }

        AdminAccountEntity entity = new AdminAccountEntity();
        entity.setPhone(phone);
        entity.setName(body.getName().trim());
        entity.setEnabled(body.isEnabled());

        entity = adminAccountRepository.save(entity);
        return toDto(entity);
    }

    /**
     * 更新系统管理员
     */
    @PutMapping("/{id}")
    public AdminAccountDto update(@PathVariable("id") Long id,
                                  @Valid @RequestBody AdminAccountSaveRequestDto body) {
        AdminAccountEntity entity = adminAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("管理员账号不存在"));

        String phone = normalizePhone(body.getPhone());
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }

        // 检查手机号是否被其他账号占用
        AdminAccountEntity existingByPhone = adminAccountRepository.findByPhone(phone).orElse(null);
        if (existingByPhone != null && !existingByPhone.getId().equals(id)) {
            throw new IllegalArgumentException("该手机号已被其他账号使用");
        }

        entity.setPhone(phone);
        entity.setName(body.getName().trim());
        entity.setEnabled(body.isEnabled());

        entity = adminAccountRepository.save(entity);
        return toDto(entity);
    }

    /**
     * 删除系统管理员
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        AdminAccountEntity entity = adminAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("管理员账号不存在"));

        // 不允许删除自己（通过当前登录判断在 frontend 做）
        adminAccountRepository.delete(entity);
    }

    /**
     * 批量删除系统管理员
     */
    @PostMapping("/batch-delete")
    public void batchDelete(@RequestBody List<Long> ids) {
        adminAccountRepository.deleteAllById(ids);
    }

    private AdminAccountDto toDto(AdminAccountEntity entity) {
        AdminAccountDto dto = new AdminAccountDto();
        dto.setId(entity.getId());
        dto.setPhone(entity.getPhone());
        dto.setName(entity.getName());
        dto.setEnabled(entity.isEnabled());
        dto.setCreatedAt(entity.getCreatedAt().toEpochMilli());
        dto.setUpdatedAt(entity.getUpdatedAt().toEpochMilli());
        return dto;
    }

    private String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }
}
