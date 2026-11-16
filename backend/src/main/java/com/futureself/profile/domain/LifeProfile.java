package com.futureself.profile.domain;

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
 * Life Profile（life_profile，实施文档 §10.2/§32）。
 * 一个用户只有一份；JSON 列以字符串形式映射，序列化由服务层负责。
 * 每次修改 profile_version +1（§32.2）；历史故事记录生成时的版本，不回溯（§32.3）。
 */
@Entity
@Table(name = "life_profile")
public class LifeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "current_situation", columnDefinition = "text")
    private String currentSituation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "focus_areas_json", columnDefinition = "json")
    private String focusAreasJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "important_people_json", columnDefinition = "json")
    private String importantPeopleJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences_json", columnDefinition = "json")
    private String preferencesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_json", columnDefinition = "json")
    private String extraJson;

    @Column(name = "profile_version", nullable = false)
    private int profileVersion = 1;

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

    public String getCurrentSituation() {
        return currentSituation;
    }

    public void setCurrentSituation(String currentSituation) {
        this.currentSituation = currentSituation;
    }

    public String getFocusAreasJson() {
        return focusAreasJson;
    }

    public void setFocusAreasJson(String focusAreasJson) {
        this.focusAreasJson = focusAreasJson;
    }

    public String getImportantPeopleJson() {
        return importantPeopleJson;
    }

    public void setImportantPeopleJson(String importantPeopleJson) {
        this.importantPeopleJson = importantPeopleJson;
    }

    public String getPreferencesJson() {
        return preferencesJson;
    }

    public void setPreferencesJson(String preferencesJson) {
        this.preferencesJson = preferencesJson;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public int getProfileVersion() {
        return profileVersion;
    }

    public void setProfileVersion(int profileVersion) {
        this.profileVersion = profileVersion;
    }

    public void incrementVersion() {
        this.profileVersion = this.profileVersion + 1;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
