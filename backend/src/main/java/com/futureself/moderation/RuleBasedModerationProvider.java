package com.futureself.moderation;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * V1 规则审核（实施文档 §19.3）：最小关键词兜底，仅阻断明显高风险内容。
 * 规则可后续替换/补充；接口不变。
 */
@Service
public class RuleBasedModerationProvider implements ModerationProvider {

    /** 自伤/伤人/报复等高风险表达。 */
    private static final List<String> SELF_HARM_OR_VIOLENCE = List.of(
            "自杀", "自残", "伤害自己", "结束自己的生命", "杀死", "杀了他", "杀了她", "伤人", "报复社会");

    /** 对他人操控、跟踪、监视、报复。 */
    private static final List<String> CONTROL_OR_STALK = List.of(
            "跟踪", "监视", "控制他", "控制她", "让他后悔", "让她后悔", "毁掉他", "毁掉她", "偷拍");

    /** 明显违法违规。 */
    private static final List<String> ILLEGAL = List.of(
            "制毒", "毒品", "枪支", "爆炸物", "洗钱", "诈骗话术");

    /** 医疗治愈/投资稳赚等确定性承诺。 */
    private static final List<String> DETERMINISTIC_CLAIM = List.of(
            "稳赚", "包治", "治愈癌症", "百病全消", "一定暴富", "必定发财", "翻倍保证");

    @Override
    public ModerationResult check(ModerationRequest request) {
        String text = request.text() == null ? "" : request.text();
        if (hit(text, SELF_HARM_OR_VIOLENCE)) {
            return ModerationResult.block("SELF_HARM_OR_VIOLENCE", "self-harm or violence expression");
        }
        if (hit(text, CONTROL_OR_STALK)) {
            return ModerationResult.block("CONTROL_OR_STALK", "controlling/stalking/revenge intent");
        }
        if (hit(text, ILLEGAL)) {
            return ModerationResult.block("ILLEGAL", "illegal activity");
        }
        if (hit(text, DETERMINISTIC_CLAIM)) {
            return ModerationResult.block("DETERMINISTIC_CLAIM", "deterministic medical/financial guarantee");
        }
        return ModerationResult.pass();
    }

    private boolean hit(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }
}
