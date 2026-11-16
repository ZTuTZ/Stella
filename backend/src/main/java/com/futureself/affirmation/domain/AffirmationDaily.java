package com.futureself.affirmation.domain;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日肯定句（affirmation_daily，实施文档 §10.7/§32.7）。
 * UNIQUE(user_id, local_date)：同一用户同一天仅一份，并发由唯一索引兜底。
 */
@Entity
@Table(name = "affirmation_daily")
public class AffirmationDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_json", nullable = false, columnDefinition = "json")
    private String itemsJson;

    @Column(name = "prompt_template_id")
    private Long promptTemplateId;

    @Column(nullable = false, length = 60)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public void setLocalDate(LocalDate localDate) {
        this.localDate = localDate;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
