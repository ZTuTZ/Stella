package com.futureself.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * AI 调用诊断日志（ai_call_log，实施文档 §10.9/§23）。
 * 只保存脱敏摘要，不保存 API key 与完整用户正文。
 */
@Entity
@Table(name = "ai_call_log", indexes = {
        @Index(name = "idx_ai_log_request", columnList = "request_id"),
        @Index(name = "idx_ai_log_task", columnList = "generation_task_id"),
        @Index(name = "idx_ai_log_created", columnList = "created_at")
})
public class AiCallLog {

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_AUDIO = "AUDIO";
    public static final String TYPE_MODERATION = "MODERATION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "generation_task_id")
    private Long generationTaskId;

    @Column(name = "provider_type", nullable = false, length = 20)
    private String providerType;

    @Column(nullable = false, length = 60)
    private String provider;

    @Column(length = 100)
    private String model;

    @Column(nullable = false, length = 100)
    private String operation;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_summary_json", columnDefinition = "json")
    private String requestSummaryJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_summary_json", columnDefinition = "json")
    private String responseSummaryJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGenerationTaskId() {
        return generationTaskId;
    }

    public void setGenerationTaskId(Long generationTaskId) {
        this.generationTaskId = generationTaskId;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRequestSummaryJson() {
        return requestSummaryJson;
    }

    public void setRequestSummaryJson(String requestSummaryJson) {
        this.requestSummaryJson = requestSummaryJson;
    }

    public String getResponseSummaryJson() {
        return responseSummaryJson;
    }

    public void setResponseSummaryJson(String responseSummaryJson) {
        this.responseSummaryJson = responseSummaryJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
