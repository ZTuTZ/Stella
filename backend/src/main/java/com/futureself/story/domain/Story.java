package com.futureself.story.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 未来故事（story，实施文档 §10.4）。
 * 记录生成时的 prompt_template_id / provider / model / profile_version，便于回溯（§7.1）。
 */
@Entity
@Table(name = "story", indexes = {
        @Index(name = "idx_story_user_status", columnList = "user_id, status, created_at"),
        @Index(name = "idx_story_goal", columnList = "goal_id")
})
public class Story {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";
    public static final String STATUS_BLOCKED = "BLOCKED";

    public static final String MODERATION_PENDING = "PENDING";
    public static final String MODERATION_PASSED = "PASSED";
    public static final String MODERATION_BLOCKED = "BLOCKED";

    public static final String PERSPECTIVE_SECOND_PERSON = "SECOND_PERSON";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "generation_task_id")
    private Long generationTaskId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String content;

    @Column(nullable = false, length = 20)
    private String perspective = PERSPECTIVE_SECOND_PERSON;

    @Column(name = "prompt_template_id")
    private Long promptTemplateId;

    @Column(nullable = false, length = 60)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "profile_version", nullable = false)
    private int profileVersion;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "moderation_status", nullable = false, length = 20)
    private String moderationStatus = MODERATION_PENDING;

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

    public Long getGoalId() {
        return goalId;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
    }

    public Long getGenerationTaskId() {
        return generationTaskId;
    }

    public void setGenerationTaskId(Long generationTaskId) {
        this.generationTaskId = generationTaskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPerspective() {
        return perspective;
    }

    public void setPerspective(String perspective) {
        this.perspective = perspective;
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

    public int getProfileVersion() {
        return profileVersion;
    }

    public void setProfileVersion(int profileVersion) {
        this.profileVersion = profileVersion;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
