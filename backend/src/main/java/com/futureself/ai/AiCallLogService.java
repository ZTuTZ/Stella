package com.futureself.ai;

import com.futureself.ai.domain.AiCallLog;
import com.futureself.ai.domain.AiCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 调用日志服务（§10.9/§23）。
 * 文本/音频/审核每次调用都记录耗时、成败与脱敏摘要；失败也记录（success=false）。
 * 日志写入失败不得影响主流程（吞异常 + warn）。
 */
@Service
public class AiCallLogService {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogService.class);

    private final AiCallLogRepository repository;

    public AiCallLogService(AiCallLogRepository repository) {
        this.repository = repository;
    }

    public record CallRecord(
            String requestId,
            Long userId,
            Long generationTaskId,
            String providerType,
            String provider,
            String model,
            String operation,
            long latencyMs,
            boolean success,
            Integer inputTokens,
            Integer outputTokens,
            String errorCode,
            String errorMessage,
            String requestSummaryJson,
            String responseSummaryJson) {
    }

    public void record(CallRecord call) {
        try {
            AiCallLog entity = new AiCallLog();
            entity.setRequestId(call.requestId() == null ? "" : call.requestId());
            entity.setUserId(call.userId());
            entity.setGenerationTaskId(call.generationTaskId());
            entity.setProviderType(call.providerType());
            entity.setProvider(call.provider());
            entity.setModel(call.model());
            entity.setOperation(call.operation());
            entity.setLatencyMs(call.latencyMs());
            entity.setSuccess(call.success());
            entity.setInputTokens(call.inputTokens());
            entity.setOutputTokens(call.outputTokens());
            entity.setErrorCode(call.errorCode());
            entity.setErrorMessage(truncate(call.errorMessage(), 1000));
            entity.setRequestSummaryJson(call.requestSummaryJson());
            entity.setResponseSummaryJson(call.responseSummaryJson());
            repository.save(entity);
        } catch (RuntimeException e) {
            log.warn("ai call log persist failed: {}", e.getClass().getSimpleName());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
