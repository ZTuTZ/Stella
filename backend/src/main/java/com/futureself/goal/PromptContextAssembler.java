package com.futureself.goal;

import com.futureself.ai.text.PromptFiller;
import com.futureself.goal.domain.ManifestationGoal;
import com.futureself.profile.api.ImportantPerson;
import com.futureself.profile.api.LifeProfileResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 用户上下文组装（实施文档 §33）：
 * 空字段不发送、限制总长、不发管理员备注、不把 openid/userId 等技术标识发给模型。
 * FOLLOWUP 与 STORY 共用。
 */
@Component
public class PromptContextAssembler {

    private static final int CONTEXT_FIELD_LIMIT = 600;

    public Map<String, String> basePlaceholders(LifeProfileResponse profile, ManifestationGoal goal) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("currentSituation", PromptFiller.truncate(profile.currentSituation(), CONTEXT_FIELD_LIMIT));
        p.put("focusAreas", joinOrEmpty(profile.focusAreas()));
        p.put("importantPeople", joinPeopleOrEmpty(profile.importantPeople()));
        p.put("preferences", PromptFiller.truncate(joinPreferences(profile.preferences()), CONTEXT_FIELD_LIMIT));
        p.put("goalCategory", orEmpty(goal.getCategory()));
        p.put("goalTitle", orEmpty(goal.getTitle()));
        p.put("goalDescription", PromptFiller.truncate(goal.getDescription(), 1200));
        p.put("targetTimeText", orEmpty(goal.getTargetTimeText()));
        return p;
    }

    public Map<String, String> storyPlaceholders(String displayName, LifeProfileResponse profile, ManifestationGoal goal) {
        Map<String, String> p = basePlaceholders(profile, goal);
        p.put("displayName", orEmpty(displayName));
        p.put("followupAnswers", joinAnswersOrEmpty(goal));
        return p;
    }

    /** 从 details_json 读取的追问回答（List<Map>），拼接为“问题 → 回答”多行文本。 */
    @SuppressWarnings("unchecked")
    public String joinAnswersOrEmpty(ManifestationGoal goal) {
        Map<String, Object> details = JsonSupport.parseObject(goal.getDetailsJson());
        Object answers = details.get("answers");
        if (!(answers instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> answer)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- ").append(String.valueOf(answer.get("questionId")))
                    .append(" → ").append(String.valueOf(answer.get("answer")));
        }
        return PromptFiller.truncate(sb.toString(), 1500);
    }

    private String joinOrEmpty(List<String> items) {
        return items == null || items.isEmpty() ? "" : String.join("、", items);
    }

    private String joinPeopleOrEmpty(List<ImportantPerson> people) {
        if (people == null || people.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ImportantPerson person : people) {
            if (!sb.isEmpty()) {
                sb.append("；");
            }
            sb.append(person.name()).append("（").append(person.relation()).append("）");
            if (person.note() != null && !person.note().isBlank()) {
                sb.append("：").append(PromptFiller.truncate(person.note(), 60));
            }
        }
        return sb.toString();
    }

    private String joinPreferences(Map<String, String> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : preferences.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append("；");
            }
            sb.append(entry.getKey()).append("：").append(entry.getValue());
        }
        return sb.toString();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
