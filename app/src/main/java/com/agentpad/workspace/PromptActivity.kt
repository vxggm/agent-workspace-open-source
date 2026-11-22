package com.agentpad.workspace

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentpad.workspace.ai.PromptStore
import com.agentpad.workspace.ui.*

/**
 * 自定义提示词页（多方案 + 不限字数）。
 *
 * 两处结构性变化，其它行为与旧版一致（只叠加、不替换）：
 *
 *  1. **不限字数**：旧版在输入回调里用 `it.length <= MAX_LENGTH` 拦截，
 *     现在改为直接接受任何长度；界面上只显示字符数，不再显示「/ 上限」。
 *
 *  2. **多方案**：全局与会话各自拥有一组方案（可命名、可新增、可删除、可切换）。
 *     同一作用域同一时刻只有一个方案生效 —— 多方案是「切换风格」，
 *     不是「全部叠加」，否则会互相打架且撑爆上下文。
 */
class PromptActivity : ComponentActivity() {

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val sessionId = intent.getStringExtra("session_id").orEmpty()
        setContent {
            AgentTheme {
                Content(sessionId = sessionId.ifBlank { null })
            }
        }
    }

    @Composable
    private fun Content(sessionId: String?) {
        // 每作用域的重载计数器：改动方案（增删改切）后自增，触发重读
        var tick by remember { mutableStateOf(0) }

        val globalScope = PromptStore.SCOPE_GLOBAL
        val sessionScope = sessionId

        SubPage(
            title = "自定义提示词",
            subtitle = "在原有工具提示词之上再叠加一层，不替换内置规则 · 不限字数 · 支持多方案",
            onBack = { finish() },
        ) {
            // ⚠ SubPage 自身已是 verticalScroll 容器（AgentUI.kt:486 scrollable 默认 true）。
            // 这里绝不能再套 Column(verticalScroll) —— 嵌套滚动会给内层无限高度约束，
            // 真机上直接 IllegalStateException 闪退（API 29 OPPO 实测）。
            SchemeEditor(
                scope = globalScope,
                title = "全局提示词",
                hint = "对所有会话生效。可以写你的偏好、说话风格、通用约束。",
                placeholder = "例如：回答尽量简短；优先用中文；不要主动安装系统级软件……",
                tick = tick,
                onChanged = { tick++ },
            )

            if (sessionScope != null) {
                Spacer(Modifier.height(20.dp))
                SchemeEditor(
                    scope = sessionScope,
                    title = "本会话提示词",
                    hint = "只对当前会话生效，优先级高于全局提示词。",
                    placeholder = "例如：这个项目用 Kotlin；所有命令都在工作区根目录下执行……",
                    tick = tick,
                    onChanged = { tick++ },
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("说明")
            AgentCard {
                Column {
                    listOf(
                        "叠加位置：系统提示词末尾的 <user_custom_instructions> 区块。",
                        "不会覆盖工具调用格式、文件交付、计划等内置规则。",
                        "同一作用域同时只启用一套方案；切换方案立即对下一次请求生效。",
                        "全局与会话同时存在时，会话提示词更具体，放在后面。",
                        "内容留空即不生效，不额外消耗上下文。",
                        "字数不限；上下文压力交给请求侧的压缩机制处理。",
                    ).forEach {
                        Text(
                            "· $it", fontSize = 12.sp, color = AColors.Muted,
                            lineHeight = 18.sp, modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }

    /**
     * 一个作用域的方案编辑器。
     *
     * 结构：方案横向切换条 → 当前方案编辑器 → 方案管理按钮。
     * 输入实时写盘（每次改动立刻 upsert），不需要单独的「保存」按钮，
     * 避免旧版「改完忘保存」的坑。
     */
    @Composable
    private fun SchemeEditor(
        scope: String,
        title: String,
        hint: String,
        placeholder: String,
        tick: Int,
        onChanged: () -> Unit,
    ) {
        // tick 变化时重读方案列表
        val schemes = remember(scope, tick) { PromptStore.schemes(this, scope) }
        val activeId = remember(scope, tick) {
            PromptStore.activeSchemeId(this, scope) ?: schemes.firstOrNull()?.id
        }
        val active = schemes.firstOrNull { it.id == activeId } ?: schemes.firstOrNull()
        var draft by remember(scope, activeId) { mutableStateOf(active?.text.orEmpty()) }

        SectionLabel(title)
        Text(
            hint, fontSize = 12.sp, color = AColors.Muted,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // ---- 方案切换条 ----
        AgentCard {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "方案（${schemes.size}）",
                        fontSize = 12.sp, color = AColors.InkSoft, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "＋ 新建",
                        fontSize = 12.sp, color = AColors.Accent,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 横向列表：方案多了也能滑动
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    schemes.forEach { s ->
                        val isActive = s.id == activeId
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isActive) AColors.Accent else AColors.SurfaceAlt)
                                .clickable {
                                    PromptStore.setActiveScheme(this@PromptActivity, scope, s.id)
                                    onChanged()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                s.name,
                                fontSize = 12.sp,
                                color = if (isActive) Color.White else AColors.InkSoft,
                            )
                        }
                    }
                    if (schemes.isEmpty()) {
                        Text("还没有方案，输入内容会自动创建", fontSize = 12.sp, color = AColors.Faint)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("新建方案") { createScheme(scope, onChanged) }
                    if (active != null) {
                        SecondaryButton("重命名") { renameScheme(scope, active, onChanged) }
                        SecondaryButton("删除") { deleteScheme(scope, active, onChanged) }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- 正文编辑 ----
        AgentCard {
            AgentTextField(
                value = draft,
                onValueChange = { v ->
                    // ⚠ 不限字数：这里不再做任何长度判断
                    draft = v
                    // 输入即落盘：没有方案就自动建一个
                    val cur = PromptStore.activeScheme(this@PromptActivity, scope)
                    if (cur == null) {
                        PromptStore.upsertScheme(
                            this@PromptActivity, scope,
                            PromptStore.Scheme("s_" + System.currentTimeMillis(), "方案 1", v),
                        )
                    } else {
                        PromptStore.upsertScheme(this@PromptActivity, scope, cur.copy(text = v))
                    }
                },
                placeholder = placeholder,
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            )
            Text(
                "${draft.length} 字符",
                fontSize = 11.sp, color = AColors.Faint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    // ------------------------------------------------------------------
    // 方案管理（原生弹窗，与项目其它页一致）
    // ------------------------------------------------------------------

    private fun createScheme(scope: String, onChanged: () -> Unit) {
        val input = EditText(this).apply {
            hint = "方案名称"
            setText("方案 ${PromptStore.schemes(this@PromptActivity, scope).size + 1}")
        }
        AlertDialog.Builder(this)
            .setTitle("新建方案")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "未命名方案" }
                val id = "s_" + System.currentTimeMillis()
                PromptStore.upsertScheme(this, scope, PromptStore.Scheme(id, name, ""))
                PromptStore.setActiveScheme(this, scope, id)
                onChanged()
                Toast.makeText(this, "已新建并切换到「$name」", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun renameScheme(scope: String, scheme: PromptStore.Scheme, onChanged: () -> Unit) {
        val input = EditText(this).apply { setText(scheme.name) }
        AlertDialog.Builder(this)
            .setTitle("重命名方案")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim().ifBlank { scheme.name }
                PromptStore.upsertScheme(this, scope, scheme.copy(name = name))
                onChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteScheme(scope: String, scheme: PromptStore.Scheme, onChanged: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("删除方案")
            .setMessage("确定删除「${scheme.name}」？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                PromptStore.deleteScheme(this, scope, scheme.id)
                onChanged()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}