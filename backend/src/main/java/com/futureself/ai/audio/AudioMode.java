package com.futureself.ai.audio;

/**
 * 音频生成模式（实施文档 §15.2）。
 * NARRATION_MIX：供应商若支持可一次生成“旁白 + 氛围”完整音频；不支持则退化 NARRATION。
 */
public enum AudioMode {
    NARRATION,
    NARRATION_MIX
}
