package com.futureself.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Prompt 模板版本（prompt_template，实施文档 §10.8/§14.4）。
 * 同一 prompt_key 仅一个 enabled=1；后台编辑即新版本，不覆盖历史。
 */
@Entity
@Table(name = "prompt_template")
public class PromptTemplate {

    public static final String KEY_FOLLOWUP = "FOLLOWUP";
    public static final String KEY_STORY = "STORY";
    public static final String KEY_AFFIRMATION = "AFFIRMATION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prompt_key", nullable = false, length = 100)
    private String promptKey;

    @Column(nullable = false)
    private int version;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "mediumtext")
    private String systemPrompt;

    @Column(name = "user_template", nullable = false, columnDefinition = "mediumtext")
    private String userTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema_json", columnDefinition = "json")
    private String outputSchemaJson;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getPromptKey() {
        return promptKey;
    }

    public int getVersion() {
        return version;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserTemplate() {
        return userTemplate;
    }

    public String getOutputSchemaJson() {
        return outputSchemaJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public void setUserTemplate(String userTemplate) {
        this.userTemplate = userTemplate;
    }

    public void setOutputSchemaJson(String outputSchemaJson) {
        this.outputSchemaJson = outputSchemaJson;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
