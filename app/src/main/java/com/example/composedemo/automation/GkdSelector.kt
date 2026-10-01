package com.example.composedemo.automation

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GKD 风格节点选择器。
 *
 * 单个 [GkdSelector] 描述「一个节点」的匹配条件:
 * 属性断言([props])+ 子级选择器([children],全部命中)+ 索引([index],兄弟序号)。
 * 匹配从窗口根递归下钻,语义对齐 GKD 订阅的 `matches` 字段子集:
 *   [text*="跳过"][visibleToUser=true] > [vid="confirm"]
 *
 * 属性键(对齐 GKD):
 *   vid / id          viewIdResourceName(不含包名)
 *   text              文本
 *   desc              contentDescription
 *   cls / className   类名(全限定)
 *   clickable / scrollable / checkable / checked / selected  布尔属性
 *   childCount        子节点数
 *   visible           isVisibleToUser
 *
 * 运算符: = 等于, != 不等于, *= 包含, ^= 前缀, $= 后缀, ~= 正则。
 */
@Serializable
data class GkdSelector(
    /** 属性断言;全部成立才命中 */
    val props: List<Prop> = emptyList(),
    /** 子级选择器:每个都需在本节点的直接子节点(含其子树)中命中 */
    val children: List<GkdSelector> = emptyList(),
    /** 兄弟位置索引(0 起,-1 = 倒数第一);null = 不限 */
    val index: Int? = null,
) {
    /** 单条属性断言 */
    @Serializable
    data class Prop(
        val key: String,
        val op: Op,
        val value: String,
    ) {
        @Serializable
        enum class Op { EQ, NEQ, CONTAINS, STARTS, ENDS, REGEX }

        /** 对节点求值;取值失败(null)视为不匹配 */
        fun matches(node: AccessibilityNodeInfo): Boolean {
            val actual: String = when (key) {
                "vid", "id" -> node.viewIdResourceName?.substringAfter('/') ?: return false
                "text" -> node.text?.toString() ?: return false
                "desc" -> node.contentDescription?.toString() ?: return false
                "cls", "className" -> node.className?.toString() ?: return false
                "clickable" -> node.isClickable.toString()
                "scrollable" -> node.isScrollable.toString()
                "checkable" -> node.isCheckable.toString()
                "checked" -> node.isChecked.toString()
                "selected" -> node.isSelected.toString()
                "childCount" -> node.childCount.toString()
                "visible" -> node.isVisibleToUser.toString()
                else -> return false
            }
            return when (op) {
                Op.EQ -> actual == value
                Op.NEQ -> actual != value
                Op.CONTAINS -> actual.contains(value, ignoreCase = true)
                Op.STARTS -> actual.startsWith(value, ignoreCase = true)
                Op.ENDS -> actual.endsWith(value, ignoreCase = true)
                Op.REGEX -> try {
                    Regex(value).containsMatchIn(actual)
                } catch (_: Throwable) {
                    false
                }
            }
        }

        /** 摘要:GKD 语法片段 */
        fun summary(): String {
            val op = when (this.op) {
                Op.EQ -> "="
                Op.NEQ -> "!="
                Op.CONTAINS -> "*="
                Op.STARTS -> "^="
                Op.ENDS -> "$="
                Op.REGEX -> "~="
            }
            return "[$key$op\"$value\"]"
        }
    }

    /** 本节点属性全中且每个子选择器都能在直接子节点中命中 */
    fun matches(node: AccessibilityNodeInfo): Boolean {
        // 带 index 的选择器:校验自身在父节点子列表中的序号(负数从末尾倒数,-1 = 最后一个)
        if (index != null) {
            val parent = node.parent ?: return false
            val count = parent.childCount
            val want = if (index < 0) count + index else index
            if (want !in 0 until count) return false
            var selfIdx = -1
            for (i in 0 until count) {
                if (parent.getChild(i) == node) { selfIdx = i; break }
            }
            if (selfIdx != want) return false
        }
        if (!props.all { it.matches(node) }) return false
        if (children.isEmpty()) return true
        // 每个子选择器需命中某个直接子节点(链式语义对齐 GKD 的 >)
        return children.all { child ->
            (0 until node.childCount).any { i ->
                node.getChild(i)?.let { child.matches(it) } == true
            }
        }
    }

    /** 编辑器/日志摘要 */
    fun summary(): String =
        props.joinToString("") { it.summary() } +
            (index?.let { "@$it" } ?: "") +
            children.joinToString(" > ") { it.summary() }

    companion object {
        /** GKD 语法解析:`[k="v"] > [k*=v] @2`,链式子级;供编辑器手写表达式 */
        fun parse(expr: String): GkdSelector {
            val chain = expr.split('>').map { part ->
                var src = part.trim()
                var idx: Int? = null
                // 尾部 @n = 兄弟索引
                val idxM = Regex("""@(-?\d+)\s*$""").find(src)
                if (idxM != null) {
                    idx = idxM.groupValues[1].toInt()
                    src = src.removeRange(idxM.range)
                }
                val props = Regex("""\[([a-zA-Z]+)\s*(~?=|\*=\^?|\$=|!=|\*=|\^=|~=)\s*(.*?)\s*]""")
                    .findAll(src).map { m ->
                        val op = when (m.groupValues[2]) {
                            "=" -> Prop.Op.EQ
                            "!=" -> Prop.Op.NEQ
                            "*=" -> Prop.Op.CONTAINS
                            "^=" -> Prop.Op.STARTS
                            "$=" -> Prop.Op.ENDS
                            "~=" -> Prop.Op.REGEX
                            else -> throw IllegalArgumentException("不支持的运算符: ${m.groupValues[2]}")
                        }
                        var v = m.groupValues[3]
                        if (v.length >= 2 && v.startsWith('"') && v.endsWith('"')) {
                            v = v.substring(1, v.length - 1)
                        }
                        Prop(m.groupValues[1], op, v)
                    }.toList()
                if (props.isEmpty()) throw IllegalArgumentException("无属性断言: $part")
                GkdSelector(props = props, index = idx)
            }
            // 链:最后一个为命中目标,前面的作为其祖先条件 → 反转为子级链
            return chain.reducedToTarget()
        }

        /** 把祖先链转换为子级链(根在前,目标在后) */
        private fun List<GkdSelector>.reducedToTarget(): GkdSelector = when (size) {
            1 -> this[0]
            else -> GkdSelector(children = this)
        }
    }
}
