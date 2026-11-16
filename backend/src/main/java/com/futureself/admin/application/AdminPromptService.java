package com.futureself.admin.application;

import com.futureself.ai.domain.PromptTemplate;
import com.futureself.ai.domain.PromptTemplateRepository;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 管理（§14.4/§20.3）：查看版本、新建版本、启用/禁用。
 * 同一 prompt_key 仅一个 enabled；新建版本默认禁用，启用时自动禁用同 key 其他版本。
 */
@Service
public class AdminPromptService {

    private final PromptTemplateRepository repository;

    public AdminPromptService(PromptTemplateRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> list() {
        return repository.findAll().stream()
                .sorted((a, b) -> {
                    int byKey = a.getPromptKey().compareTo(b.getPromptKey());
                    return byKey != 0 ? byKey : Integer.compare(b.getVersion(), a.getVersion());
                })
                .map(this::toMap)
                .toList();
    }

    public Map<String, Object> get(long id) {
        PromptTemplate template = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "prompt not found"));
        Map<String, Object> m = toMap(template);
        m.put("systemPrompt", template.getSystemPrompt());
        m.put("userTemplate", template.getUserTemplate());
        m.put("outputSchemaJson", template.getOutputSchemaJson());
        return m;
    }

    /** 新建版本：version = 该 key 当前最大版本 + 1；默认禁用。 */
    @Transactional
    public Map<String, Object> createVersion(String promptKey, String systemPrompt, String userTemplate,
                                             String outputSchemaJson, String notes) {
        int maxVersion = repository.findAll().stream()
                .filter(t -> t.getPromptKey().equals(promptKey))
                .mapToInt(PromptTemplate::getVersion)
                .max().orElse(0);
        PromptTemplate template = new PromptTemplate();
        template.setPromptKey(promptKey);
        template.setVersion(maxVersion + 1);
        template.setSystemPrompt(systemPrompt);
        template.setUserTemplate(userTemplate);
        template.setOutputSchemaJson(outputSchemaJson);
        template.setEnabled(false);
        template.setNotes(notes);
        return toMap(repository.save(template));
    }

    /** 启用某版本：同 key 其他版本全部禁用（事务内，§14.4）。 */
    @Transactional
    public Map<String, Object> enable(long id) {
        PromptTemplate target = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "prompt not found"));
        repository.findAll().stream()
                .filter(t -> t.getPromptKey().equals(target.getPromptKey()) && t.isEnabled())
                .forEach(t -> {
                    t.setEnabled(false);
                    repository.save(t);
                });
        target.setEnabled(true);
        return toMap(repository.save(target));
    }

    @Transactional
    public Map<String, Object> disable(long id) {
        PromptTemplate target = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "prompt not found"));
        target.setEnabled(false);
        return toMap(repository.save(target));
    }

    private Map<String, Object> toMap(PromptTemplate template) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", template.getId());
        m.put("promptKey", template.getPromptKey());
        m.put("version", template.getVersion());
        m.put("enabled", template.isEnabled());
        m.put("notes", template.getNotes());
        m.put("updatedAt", template.getUpdatedAt() == null ? null : template.getUpdatedAt().toString());
        return m;
    }
}
