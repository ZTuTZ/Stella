package com.futureself.profile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.profile.api.ImportantPerson;
import com.futureself.profile.api.LifeProfileResponse;
import com.futureself.profile.api.LifeProfileUpdateRequest;
import com.futureself.profile.domain.LifeProfile;
import com.futureself.profile.domain.LifeProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Life Profile 服务（实施文档 §9.2/§32）。
 * 一个用户只有一份档案；每次更新 profile_version +1；
 * 修改只影响后续生成，不追溯已生成故事。
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /** §5.2 步骤 A 允许的关注领域。 */
    public static final Set<String> ALLOWED_FOCUS_AREAS = Set.of(
            "事业", "财富", "爱情", "家庭", "自信", "健康生活方式", "学习成长", "旅行", "其他");

    private final LifeProfileRepository repository;
    private final ObjectMapper objectMapper;

    public ProfileService(LifeProfileRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LifeProfile ensureProfile(long userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            LifeProfile profile = new LifeProfile();
            profile.setUserId(userId);
            return repository.save(profile);
        });
    }

    @Transactional(readOnly = true)
    public LifeProfileResponse getProfile(long userId) {
        LifeProfile profile = repository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
        return toResponse(profile);
    }

    /** 全量替换式更新（PUT 语义）；校验失败抛 VALIDATION_FAILED；成功后版本 +1。 */
    @Transactional
    public LifeProfileResponse updateProfile(long userId, LifeProfileUpdateRequest request) {
        LifeProfile profile = ensureProfile(userId);
        applyAndValidate(profile, request);
        profile.incrementVersion();
        LifeProfile saved = repository.save(profile);
        log.debug("life profile updated: userId={} version={}", userId, saved.getProfileVersion());
        return toResponse(saved);
    }

    private void applyAndValidate(LifeProfile profile, LifeProfileUpdateRequest request) {
        profile.setCurrentSituation(request.currentSituation());

        List<String> focusAreas = request.focusAreas() == null ? List.of() : request.focusAreas();
        if (focusAreas.stream().anyMatch(area -> !ALLOWED_FOCUS_AREAS.contains(area))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "focusAreas contains unknown area");
        }
        profile.setFocusAreasJson(toJson(focusAreas));

        profile.setImportantPeopleJson(toJson(request.importantPeople() == null ? List.of() : request.importantPeople()));
        profile.setPreferencesJson(toJson(request.preferences() == null ? Map.of() : request.preferences()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "profile payload not serializable");
        }
    }

    private LifeProfileResponse toResponse(LifeProfile profile) {
        return new LifeProfileResponse(
                profile.getUserId(),
                profile.getCurrentSituation(),
                fromJsonList(profile.getFocusAreasJson(), String.class),
                fromJsonList(profile.getImportantPeopleJson(), ImportantPerson.class),
                fromJsonMap(profile.getPreferencesJson()),
                profile.getProfileVersion());
    }

    private <T> List<T> fromJsonList(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (JacksonException e) {
            log.warn("profile json list parse failed: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (JacksonException e) {
            log.warn("profile json map parse failed: {}", e.getClass().getSimpleName());
            return Map.of();
        }
    }
}
