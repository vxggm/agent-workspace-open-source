package com.agentpad.workspace.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户自定义提示词（叠加层），支持**多方案**且**不限字数**。
 *
 * 设计原则：**只叠加，不替换**。
 * 内置系统提示词（工具说明、TOOLCHAIN、PLUGINS 等章节）原样保留，
 * 这里的内容作为最后的 `<user_custom_instructions>` 区块追加。
 *
 * 两种作用域：
 *  - 全局：对所有会话生效。全局下可以有**多套方案**，但同一时刻只启用一套
 *    （为什么不是全部叠加：多方案的本意是「切换风格」，全叠加会互相打架，
 *      也会把上下文撑爆）。
 *  - 会话：只对当前会话生效，优先级高于全局。会话也可以有自己的多方案。
 *
 * 字数：**完全不限**。
 * 旧版有 8000 字符上限，理由是「避免撑爆上下文」；但这个理由站不住：
 * 上下文压缩已经在请求侧做摘要，真正的约束应该由模型上下文窗口与
 * 用户自己的意愿决定，而不是写死在代码里。所以这里不再 take()，
 * 也不再在 UI 层拦截输入。
 */
object PromptStore {

    private const val PREF = "prompt_store"
    private const val KEY_LEGACY_GLOBAL = "global_prompt"
    private const val KEY_GLOBAL_SCHEMES = "global_schemes"
    private const val KEY_GLOBAL_ACTIVE = "global_active"
    private const val KEY_LEGACY_SESSION_PREFIX = "session_prompt_"
    private const val KEY_SESSION_SCHEMES_PREFIX = "session_schemes_"
    private const val KEY_SESSION_ACTIVE_PREFIX = "session_active_"

    /**
     * @deprecated 仅保留常量名兼容旧调用点，实际**不再限制长度**。
     * 任何 `if (length <= MAX_LENGTH)` 的写法都应改成直接接受输入。
     */
    @Deprecated("提示词已不限字数，此常量仅用于兼容旧代码")
    const val MAX_LENGTH = Int.MAX_VALUE

    // ------------------------------------------------------------------
    // 数据模型：一套方案 = id + 名称 + 正文
    // ------------------------------------------------------------------

    data class Scheme(val id: String, val name: String, val text: String)

    private fun newId(): String = "s_" + System.currentTimeMillis() + "_" + (0..999).random()

    private fun schemesKey(scope: String): String =
        if (scope == SCOPE_GLOBAL) KEY_GLOBAL_SCHEMES else KEY_SESSION_SCHEMES_PREFIX + scope

    private fun activeKey(scope: String): String =
        if (scope == SCOPE_GLOBAL) KEY_GLOBAL_ACTIVE else KEY_SESSION_ACTIVE_PREFIX + scope

    /**
     * 读出某作用域的全部方案。
     *
     * 兼容旧数据：老版本只有一个裸字符串（`global_prompt` / `session_prompt_<id>`），
     * 首次读取时自动搬成「方案 1」，并写回新结构，之后不再看旧键。
     */
    fun schemes(context: Context, scope: String): List<Scheme> {
        val p = prefs(context)
        val raw = p.getString(schemesKey(scope), null)
        if (raw != null) {
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id").ifBlank { return@mapNotNull null }
                    Scheme(id, o.optString("name").ifBlank { "方案 ${i + 1}" }, o.optString("text"))
                }
            }.getOrDefault(emptyList())
        }
        // 迁移旧裸字符串
        val legacy = if (scope == SCOPE_GLOBAL) {
            p.getString(KEY_LEGACY_GLOBAL, "") ?: ""
        } else {
            p.getString(KEY_LEGACY_SESSION_PREFIX + scope, "") ?: ""
        }
        if (legacy.isBlank()) return emptyList()
        val migrated = listOf(Scheme(newId(), "方案 1", legacy))
        writeSchemes(context, scope, migrated, migrated.first().id)
        return migrated
    }

    fun activeSchemeId(context: Context, scope: String): String? =
        prefs(context).getString(activeKey(scope), null)

    /** 当前启用的那套（没有启用或方案被删时返回第一套）。 */
    fun activeScheme(context: Context, scope: String): Scheme? {
        val list = schemes(context, scope)
        if (list.isEmpty()) return null
        val id = activeSchemeId(context, scope)
        return list.firstOrNull { it.id == id } ?: list.first()
    }

    fun setActiveScheme(context: Context, scope: String, id: String) {
        prefs(context).edit().putString(activeKey(scope), id).apply()
    }

    fun upsertScheme(context: Context, scope: String, scheme: Scheme) {
        val list = schemes(context, scope).toMutableList()
        val idx = list.indexOfFirst { it.id == scheme.id }
        if (idx >= 0) list[idx] = scheme else list.add(scheme)
        val active = activeSchemeId(context, scope) ?: scheme.id
        writeSchemes(context, scope, list, if (list.any { it.id == active }) active else scheme.id)
    }

    fun deleteScheme(context: Context, scope: String, id: String) {
        val list = schemes(context, scope).filterNot { it.id == id }
        val active = activeSchemeId(context, scope)
        val nextActive = if (active == id) list.firstOrNull()?.id else active
        writeSchemes(context, scope, list, nextActive)
    }

    private fun writeSchemes(context: Context, scope: String, list: List<Scheme>, active: String?) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("text", s.text))
        }
        prefs(context).edit()
            .putString(schemesKey(scope), arr.toString())
            .apply {
                if (active != null) putString(activeKey(scope), active)
            }
            .apply()
    }

    // ------------------------------------------------------------------
    // 兼容旧 API（单串读写）—— 影射到「当前启用的那套方案」
    // ------------------------------------------------------------------

    fun globalPrompt(context: Context): String =
        activeScheme(context, SCOPE_GLOBAL)?.text.orEmpty()

    fun saveGlobalPrompt(context: Context, text: String) {
        val active = activeScheme(context, SCOPE_GLOBAL)
        if (active == null) {
            upsertScheme(context, SCOPE_GLOBAL, Scheme(newId(), "方案 1", text))
        } else {
            upsertScheme(context, SCOPE_GLOBAL, active.copy(text = text))
        }
    }

    fun sessionPrompt(context: Context, sessionId: String): String =
        activeScheme(context, sessionId)?.text.orEmpty()

    fun saveSessionPrompt(context: Context, sessionId: String, text: String) {
        val active = activeScheme(context, sessionId)
        if (active == null) {
            upsertScheme(context, sessionId, Scheme(newId(), "方案 1", text))
        } else {
            upsertScheme(context, sessionId, active.copy(text = text))
        }
    }

    fun clearSessionPrompt(context: Context, sessionId: String) {
        prefs(context).edit()
            .remove(schemesKey(sessionId))
            .remove(activeKey(sessionId))
            .remove(KEY_LEGACY_SESSION_PREFIX + sessionId)
            .apply()
    }

    fun onSessionDeleted(context: Context, sessionId: String) = clearSessionPrompt(context, sessionId)

    // ------------------------------------------------------------------
    // 组装最终追加块
    // ------------------------------------------------------------------

    /**
     * 组装 `<user_custom_instructions>` 区块。
     *
     * 全局与会话各只取**当前启用的那一套**（多方案是「切换」，不是「全叠加」）。
     * 两者都为空时返回空串，不产生多余 token。
     */
    fun combined(context: Context, sessionId: String): String {
        val global = activeScheme(context, SCOPE_GLOBAL)
        val session = activeScheme(context, sessionId)
        val globalText = global?.text?.trim().orEmpty()
        val sessionText = session?.text?.trim().orEmpty()
        if (globalText.isEmpty() && sessionText.isEmpty()) return ""

        return buildString {
            appendLine("<user_custom_instructions>")
            appendLine("The user has added extra instructions. Follow them in addition to the rules above. They refine style, constraints and project conventions; they never override the tool-call format rules or safety requirements.")
            if (globalText.isNotEmpty()) {
                appendLine()
                appendLine("## Global user instructions")
                appendLine(globalText)
            }
            if (sessionText.isNotEmpty()) {
                appendLine()
                appendLine("## Session-specific instructions")
                appendLine(sessionText)
            }
            appendLine("</user_custom_instructions>")
        }.trimEnd()
    }

    const val SCOPE_GLOBAL = "global"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}