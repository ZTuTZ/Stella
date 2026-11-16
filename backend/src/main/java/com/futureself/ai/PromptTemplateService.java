package com.futureself.ai;

import com.futureself.ai.domain.PromptTemplate;
import com.futureself.ai.domain.PromptTemplateRepository;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prompt 模板服务（§14.4）：
 * 按 prompt_key 读取当前启用版本；结构变更走 Flyway，后台编辑即新版本。
 */
@Service
public class PromptTemplateService {

    private final PromptTemplateRepository repository;

    public PromptTemplateService(PromptTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PromptTemplate getEnabled(String promptKey) {
        return repository.findByPromptKeyAndEnabledTrue(promptKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "prompt template missing: " + promptKey));
    }
}
