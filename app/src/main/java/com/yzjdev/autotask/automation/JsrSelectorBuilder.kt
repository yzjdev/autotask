package com.yzjdev.autotask.automation

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 节点快照 → jsoup 语法选择器。
 *
 * 生成规则(与 `jsr/JsoupNodes` 的匹配能力对齐,生成的串能直接喂回去):
 *  - 目标节点:标签(类名简单名) + `#vid` + `[text=...]`/`[desc=...]` 精确属性,
 *    没有任何特征时退化为 `:eq(同级序号)`;
 *  - 祖先链:从父到目标逐级生成,直到有唯一特征的祖先(有 id 或文本)为止,
 *    用后代空格连接 —— 路径越短越抗界面改版;
 *  - `:eq(n)` 用同级序号消歧,同级同名节点多于一个时自动附加。
 */
internal object JsrSelectorBuilder {

    private const val MAX_DEPTH = 5   // 最多向上取几级祖先
    private const val MAX_TEXT = 24   // 文本属性截断长度

    /** 从活节点生成选择器;节点已失效返回 null */
    fun build(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val chain = ArrayList<Part>()
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth <= MAX_DEPTH) {
            val part = partOf(current) ?: return null
            chain.add(part)
            // 有唯一特征(id 或带文本)的祖先就停,不用一路走到根
            if (part.id != null || (!part.text.isNullOrBlank() && depth > 0)) break
            current = runCatching { current.parent }.getOrNull()
            depth++
        }
        if (chain.isEmpty()) return null

        return chain.reversed().joinToString(" ") { part -> render(part) }
    }

    // ---- 单级 ----

    private class Part(
        val tag: String,
        val id: String?,
        val text: String?,
        val desc: String?,
        val eqIndex: Int,       // 同级序号;-1 表示未知
        val eqNeeded: Boolean,  // 同级是否有同名兄弟,决定要不要 :eq
    )

    private fun partOf(node: AccessibilityNodeInfo): Part? {
        val info = runCatching {
            val cls = node.className?.toString() ?: return null
            Part(
                tag = cls.substringAfterLast('.'),
                id = node.viewIdResourceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() },
                text = node.text?.toString()?.takeIf { it.isNotBlank() },
                desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() },
                eqIndex = siblingIndex(node),
                eqNeeded = hasSameTagSibling(node),
            )
        }.getOrNull()
        return info
    }

    private fun render(part: Part): String = buildString {
        append(part.tag)
        part.id?.let { append('#').append(it) }
        // id 已唯一时不再叠文本属性,串更短
        if (part.id == null) {
            part.text?.let { append("[text=").append(escape(it)).append(']') }
            if (part.text == null) {
                part.desc?.let { append("[desc=").append(escape(it)).append(']') }
            }
        }
        if (part.eqNeeded && part.eqIndex >= 0) {
            append(":eq(").append(part.eqIndex).append(')')
        }
    }

    private fun escape(value: String): String {
        val clipped = if (value.length > MAX_TEXT) value.take(MAX_TEXT) else value
        return "\"" + clipped.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private fun siblingIndex(node: AccessibilityNodeInfo): Int {
        val parent = runCatching { node.parent }.getOrNull() ?: return -1
        val count = runCatching { parent.childCount }.getOrDefault(0)
        for (index in 0 until count) {
            val child = runCatching { parent.getChild(index) }.getOrNull() ?: continue
            if (child == node) return index
        }
        return -1
    }

    private fun hasSameTagSibling(node: AccessibilityNodeInfo): Boolean {
        val tag = runCatching { node.className?.toString()?.substringAfterLast('.') }.getOrNull() ?: return false
        val parent = runCatching { node.parent }.getOrNull() ?: return false
        val count = runCatching { parent.childCount }.getOrDefault(0)
        var same = 0
        for (index in 0 until count) {
            val child = runCatching { parent.getChild(index) }.getOrNull() ?: continue
            val childTag = runCatching { child.className?.toString()?.substringAfterLast('.') }.getOrNull()
            if (childTag == tag && ++same > 1) return true
        }
        return false
    }
}
