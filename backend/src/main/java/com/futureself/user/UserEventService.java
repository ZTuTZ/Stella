package com.futureself.user;

import com.futureself.user.domain.UserEvent;
import com.futureself.user.domain.UserEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 产品事件记录（实施文档 §34/§35）。
 * 只记录产品行为与实体引用，不记录故事正文；写入失败不影响主流程。
 * 事件：APP_OPEN / ONBOARDING_COMPLETED / GOAL_CREATED / FOLLOWUP_COMPLETED /
 * STORY_REQUESTED / STORY_TEXT_READY / AUDIO_READY / STORY_OPENED / AUDIO_PLAY_STARTED /
 * AUDIO_PLAY_COMPLETED / STORY_FAVORITED。
 */
@Service
public class UserEventService {

    public static final String EVENT_APP_OPEN = "APP_OPEN";
    public static final String EVENT_ONBOARDING_COMPLETED = "ONBOARDING_COMPLETED";
    public static final String EVENT_GOAL_CREATED = "GOAL_CREATED";
    public static final String EVENT_FOLLOWUP_COMPLETED = "FOLLOWUP_COMPLETED";
    public static final String EVENT_STORY_REQUESTED = "STORY_REQUESTED";
    public static final String EVENT_STORY_TEXT_READY = "STORY_TEXT_READY";
    public static final String EVENT_AUDIO_READY = "AUDIO_READY";
    public static final String EVENT_STORY_OPENED = "STORY_OPENED";
    public static final String EVENT_STORY_FAVORITED = "STORY_FAVORITED";

    private static final Logger log = LoggerFactory.getLogger(UserEventService.class);

    private final UserEventRepository repository;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public UserEventService(UserEventRepository repository, tools.jackson.databind.ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(long userId, String eventName, String entityType, Long entityId, Map<String, Object> properties) {
        try {
            UserEvent event = new UserEvent();
            event.setUserId(userId);
            event.setEventName(eventName);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            event.setOccurredAt(LocalDateTime.now());
            if (properties != null && !properties.isEmpty()) {
                event.setPropertiesJson(objectMapper.writeValueAsString(properties));
            }
            repository.save(event);
        } catch (RuntimeException e) {
            log.warn("user event persist failed: {} {}", eventName, e.getClass().getSimpleName());
        }
    }
}
