package com.agentpad.workspace.ai.protocol

/**
 * 配置页里可选的协议族。
 *
 * 这是**本项目自己的 UI 枚举**，不是 RikkaHub 的东西：
 * 参考源码的 `ProviderSetting` 是 sealed class，且带 `@Composable` 字段与
 * `Uuid`，不能直接当界面选项用。这里只保留「用户选哪一类」这一层信息，
 * 真正的请求/解析实现在 `me.rerere.ai` 包（原样照搬）。
 *
 * 值到参考实现的映射：
 *  - [OPENAI_CHAT] / [OPENAI_RESPONSES] / [GROK] → ProviderSetting.OpenAI
 *  - [CLAUDE]                                    → ProviderSetting.Claude
 *  - [GEMINI]                                    → ProviderSetting.Google
 *  - [CHATGPT]                                   → 账号登录型（暂未接线）
 */
enum class ProviderKind(val label: String, val defaultBaseUrl: String) {
    OPENAI_CHAT("OpenAI 兼容（Chat Completions）", "https://api.openai.com/v1"),
    OPENAI_RESPONSES("OpenAI Responses", "https://api.openai.com/v1"),
    CLAUDE("Claude（Messages）", "https://api.anthropic.com/v1"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    GROK("xAI Grok", "https://api.x.ai/v1"),
    CHATGPT("ChatGPT 账号（OAuth）", "https://chatgpt.com/backend-api");

    companion object {
        fun fromId(id: String?): ProviderKind =
            entries.firstOrNull { it.name.equals(id, true) } ?: OPENAI_CHAT
    }
}