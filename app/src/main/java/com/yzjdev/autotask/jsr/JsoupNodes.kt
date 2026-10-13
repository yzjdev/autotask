package com.yzjdev.autotask.jsr

import android.view.accessibility.AccessibilityNodeInfo
import com.yzjdev.autotask.automation.AutoTaskAccessibilityService
import kotlinx.coroutines.delay

/**
 * CSS(jsoup 语法)选择器 → 定位无障碍节点 → 执行动作。
 *
 * 只有一个入口 [JsoupNodes]:
 * ```kotlin
 * val nodes = JsoupNodes(service)
 * nodes.select("Button#btn_ok[text*=登录]").first()?.let { nodes.click(it) }
 * nodes.clickByText("TextView#home_tab")
 * ```
 *
 * 支持的选择器(与 jsoup/CSS 对齐):
 *  - 标签:`Button` `TextView`(类名简单名,大小写敏感,写全名也认)
 *  - `#id`:viewId 资源名(`com.pkg:id/btn_ok` 只写 `#btn_ok` 即可)
 *  - `[attr]` `[attr=v]` `[attr*=v]` `[attr^=v]` `[attr$=v]`:
 *    attr ∈ text / desc / package / clickable / enabled / checked / scrollable / selected
 *  - 组合器:后代空格、`>` 直接子级、`+` 紧邻兄弟、`~` 之后兄弟
 *  - 伪类:`:first` `:last` `:eq(n)` `:lt(n)` `:gt(n)` `:checked` `:clickable` `:scrollable`
 *          `:contains(文本)` `:matches(正则)` `:not(选择器)`
 *  - 逗号并集:`A, B`
 *
 * 节点遍历独立于服务上会 recycle/去重的那套路径,命中节点不回收,可反复查找。
 */
class JsoupNodes(private val service: AutoTaskAccessibilityService) {

    companion object {
        /** 不自动替用户点掉的词:规则再怎么配也不放行 */
        private val GUARD_WORDS = listOf("允许", "同意", "安装", "支付", "授权", "卸载")
    }

    // ---- 选择:解析一次,匹配全部 ----

    /** 解析并匹配当前屏幕;未命中返回空列表 */
    fun select(css: String): List<AccessibilityNodeInfo> {
        val group = CssParser.parse(css) ?: return emptyList()
        val result = ArrayList<AccessibilityNodeInfo>()
        forEachNode { element ->
            if (group.matches(element, element)) result.add(element.node)
            false
        }
        return result
    }

    /** 等待第一个匹配出现;超时返回 null */
    suspend fun await(css: String, timeoutMs: Long = 5_000L): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        while (true) {
            select(css).firstOrNull()?.let { return it }
            if (System.currentTimeMillis() >= deadline) return null
            delay(100)
        }
    }

    fun exists(css: String): Boolean = select(css).isNotEmpty()

    // ---- 动作 ----

    /** 点击:节点不可点时向上找可点击祖先;仍点不动退回坐标手势 */
    fun click(node: AccessibilityNodeInfo): Boolean {
        guard(node)
        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && !target.isClickable && depth++ < 8) {
            target = runCatching { target.parent }.getOrNull()
        }
        target = target ?: node
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return clickCenter(node)
    }

    fun longClick(node: AccessibilityNodeInfo): Boolean {
        guard(node)
        val rect = bounds(node) ?: return false
        service.longClickAt(rect.exactCenterX(), rect.exactCenterY())
        return true
    }

    /** 输入文本;变量插值由调用方完成,这里只写入 */
    fun input(node: AccessibilityNodeInfo, text: String): Boolean {
        guard(node)
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun clear(node: AccessibilityNodeInfo): Boolean = input(node, "")

    fun scroll(node: AccessibilityNodeInfo?, forward: Boolean = true): Boolean {
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val target = node ?: runCatching { service.rootInActiveWindow }.getOrNull()
        if (target != null && target.performAction(action)) return true
        return if (forward) service.swipeUp().let { true } else service.swipeDown().let { true }
    }

    fun back(): Boolean = service.goBack()
    fun home(): Boolean = service.goToHome()
    fun recents(): Boolean = service.openRecentApps()
    fun notifications(): Boolean = service.openNotificationPanel()

    // ---- 内部 ----

    /** 敏感词拦截:命中即抛,不重试不降级 */
    private fun guard(node: AccessibilityNodeInfo) {
        val text = listOfNotNull(
            runCatching { node.text?.toString() }.getOrNull(),
            runCatching { node.contentDescription?.toString() }.getOrNull(),
        ).joinToString(" ")
        GUARD_WORDS.firstOrNull { text.contains(it) }?.let {
            throw SecurityException("目标文本命中敏感词「$it」,已拦截")
        }
    }

    private fun bounds(node: AccessibilityNodeInfo): android.graphics.Rect? = runCatching {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) null else rect
    }.getOrNull()

    private fun clickCenter(node: AccessibilityNodeInfo): Boolean {
        val rect = bounds(node) ?: return false
        service.clickAt(rect.exactCenterX(), rect.exactCenterY())
        return true
    }

    /** 干净的前序 DFS:不回收、不去重,跳过本应用自己的窗口 */
    private fun forEachNode(onNode: (Element) -> Boolean) {
        val self = runCatching { service.packageName }.getOrNull()
        val roots = ArrayList<AccessibilityNodeInfo>()
        runCatching { service.rootInActiveWindow }.getOrNull()?.let { roots.add(it) }
        runCatching { service.windows }.getOrNull().orEmpty().forEach { window ->
            runCatching { window.root }.getOrNull()?.let { root ->
                if (roots.none { it === root }) roots.add(root)
            }
        }
        for (root in roots) {
            val rootPackage = runCatching { root.packageName?.toString() }.getOrNull()
            if (self != null && rootPackage == self) continue
            if (walk(Element(root), onNode)) return
        }
    }

    private fun walk(element: Element, onNode: (Element) -> Boolean): Boolean {
        if (onNode(element)) return true
        val count = runCatching { element.node.childCount }.getOrDefault(0)
        for (index in 0 until count) {
            val child = runCatching { element.node.getChild(index) }.getOrNull() ?: continue
            if (walk(Element(child), onNode)) return true
        }
        return false
    }

    /** 轻量包装:选择器匹配需要「我在父节点的第几位」这类树信息 */
    class Element(val node: AccessibilityNodeInfo) {
        fun tag(): String = runCatching {
            node.className?.toString()?.substringAfterLast('.')
        }.getOrNull() ?: "unknown"

        fun id(): String? = runCatching {
            node.viewIdResourceName?.substringAfterLast('/')
        }.getOrNull()

        fun text(): String? = runCatching { node.text?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
        fun desc(): String? = runCatching { node.contentDescription?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
        fun pkg(): String? = runCatching { node.packageName?.toString() }.getOrNull()

        fun attr(name: String): String? = when (name.lowercase()) {
            "text" -> text()
            "desc" -> desc()
            "id" -> id()
            "package" -> pkg()
            "clickable" -> runCatching { node.isClickable }.getOrDefault(false).toString()
            "enabled" -> runCatching { node.isEnabled }.getOrDefault(false).toString()
            "checked" -> runCatching { node.isChecked }.getOrDefault(false).toString()
            "scrollable" -> runCatching { node.isScrollable }.getOrDefault(false).toString()
            "selected" -> runCatching { node.isSelected }.getOrDefault(false).toString()
            else -> null
        }

        fun parent(): Element? = runCatching { node.parent }.getOrNull()?.let { Element(it) }

        fun children(): List<Element> {
            val count = runCatching { node.childCount }.getOrDefault(0)
            val out = ArrayList<Element>(count)
            for (index in 0 until count) {
                runCatching { node.getChild(index) }.getOrNull()?.let { out.add(Element(it)) }
            }
            return out
        }

        /** 同级序号;无父节点时 -1 */
        fun index(): Int {
            val parent = parent() ?: return -1
            parent.children().forEachIndexed { position, child ->
                if (child.node == node) return position
            }
            return -1
        }

        fun prevSibling(): Element? {
            val position = index()
            if (position <= 0) return null
            return parent()?.children()?.getOrNull(position - 1)
        }

        fun nextSiblings(): List<Element> {
            val position = index()
            if (position < 0) return emptyList()
            return parent()?.children()?.drop(position + 1).orEmpty()
        }
    }

    // ---- 选择器模型与匹配 ----

    /** 逗号分隔的一组选择器,任一命中即算命中 */
    private class Group(val parts: List<Chain>)

    /** 一条组合器链:`A > B C` 存成 [A, child(B), descendant(C)] */
    private class Chain(val steps: List<Step>)

    private enum class Combinator { Descendant, Child, Adjacent, Sibling }

    /** 链上的一步:简单选择器 + 与上一步的连接方式 */
    private class Step(val combinator: Combinator, val simple: Simple)

    /** 简单选择器:标签 + #id + [属性...] + 伪类 */
    private class Simple(
        val tag: String?,
        val id: String?,
        val attrs: List<AttrMatch>,
        val pseudos: List<Pseudo>,
    )

    private class AttrMatch(val name: String, val op: String?, val value: String)

    private class Pseudo(val name: String, val arg: String?)

    /** 匹配入口:[element] 是候选,[root] 是链的起点评估基准(这里始终是同一棵树) */
    private fun Group.matches(element: Element, root: Element): Boolean =
        parts.any { chain -> chain.matchesFromEnd(element) }

    /** 从链尾往前回溯:候选节点先命中最后一步,再按组合器找前几步的宿主 */
    private fun Chain.matchesFromEnd(element: Element): Boolean {
        if (!simpleMatches(steps.last().simple, element)) return false
        return matchRest(element, steps.size - 2)
    }

    private fun Chain.matchRest(element: Element, stepIndex: Int): Boolean {
        if (stepIndex < 0) return true
        val step = steps[stepIndex]
        val need = step.simple
        return when (step.combinator) {
            Combinator.Child -> element.parent()?.let { parent ->
                simpleMatches(need, parent) && matchRest(parent, stepIndex - 1)
            } ?: false

            Combinator.Descendant -> {
                var current = element.parent()
                while (current != null) {
                    if (simpleMatches(need, current) && matchRest(current, stepIndex - 1)) return true
                    current = current.parent()
                }
                false
            }

            Combinator.Adjacent -> element.prevSibling()?.let { previous ->
                simpleMatches(need, previous) && matchRest(previous, stepIndex - 1)
            } ?: false

            Combinator.Sibling -> {
                var current = element.prevSibling()
                while (current != null) {
                    if (simpleMatches(need, current) && matchRest(current, stepIndex - 1)) return true
                    current = current.prevSibling()
                }
                false
            }
        }
    }

    private fun simpleMatches(simple: Simple, element: Element): Boolean {
        simple.tag?.let { tag ->
            val actual = element.tag()
            if (actual != tag && element.node.className?.toString() != tag) return false
        }
        simple.id?.let { id -> if (element.id() != id) return false }
        simple.attrs.forEach { attr ->
            val actual = element.attr(attr.name)
            if (actual == null) return false
            val ok = when (attr.op) {
                null -> true
                "=" -> actual == attr.value
                "*=" -> actual.contains(attr.value)
                "^=" -> actual.startsWith(attr.value)
                "$=" -> actual.endsWith(attr.value)
                else -> false
            }
            if (!ok) return false
        }
        simple.pseudos.forEach { pseudo ->
            if (!pseudoMatches(pseudo, element)) return false
        }
        return true
    }

    private fun pseudoMatches(pseudo: Pseudo, element: Element): Boolean = when (pseudo.name) {
        "first" -> element.index() == 0
        "last" -> {
            val parent = element.parent() ?: return false
            element.index() == parent.children().size - 1
        }

        "eq" -> element.index() == pseudo.arg?.toIntOrNull()
        "lt" -> {
            val index = element.index()
            index >= 0 && index < (pseudo.arg?.toIntOrNull() ?: 0)
        }

        "gt" -> {
            val index = element.index()
            index >= 0 && index > (pseudo.arg?.toIntOrNull() ?: 0)
        }

        "checked" -> runCatching { element.node.isChecked }.getOrDefault(false)
        "clickable" -> runCatching { element.node.isClickable }.getOrDefault(false)
        "scrollable" -> runCatching { element.node.isScrollable }.getOrDefault(false)
        "contains" -> {
            val needle = pseudo.arg.orEmpty()
            element.text()?.contains(needle) == true || element.desc()?.contains(needle) == true
        }

        "matches" -> {
            val regex = runCatching { Regex(pseudo.arg.orEmpty()) }.getOrNull() ?: return false
            element.text()?.let { regex.containsMatchIn(it) } == true ||
                element.desc()?.let { regex.containsMatchIn(it) } == true
        }

        "not" -> {
            val inner = pseudo.arg?.let { CssParser.parseSimple(it) } ?: return false
            !simpleMatches(inner, element)
        }

        else -> false
    }

    // ---- 选择器解析 ----

    private object CssParser {

        fun parse(css: String): Group? {
            val parts = splitTopLevel(css, ',')
            if (parts.isEmpty()) return null
            val chains = parts.mapNotNull { parseChain(it.trim()) }
            if (chains.isEmpty()) return null
            return Group(chains)
        }

        /** 只解析一个简单选择器(:not 的参数用) */
        fun parseSimple(text: String): Simple? = parseChain(text)?.steps?.singleOrNull()?.simple

        private fun parseChain(text: String): Chain? {
            if (text.isBlank()) return null
            val steps = ArrayList<Step>()
            var combinator = Combinator.Descendant
            var index = 0
            while (index < text.length) {
                if (text[index].isWhitespace()) {
                    index++
                    continue
                }
                val nextCombinator = when (text[index]) {
                    '>' -> {
                        index++
                        Combinator.Child
                    }

                    '+' -> {
                        index++
                        Combinator.Adjacent
                    }

                    '~' -> {
                        index++
                        Combinator.Sibling
                    }

                    else -> Combinator.Descendant
                }
                val simple = parseSimpleAt(text, index) ?: return null
                index = simple.second
                steps.add(Step(if (steps.isEmpty()) Combinator.Descendant else nextCombinator, simple.first))
            }
            if (steps.isEmpty()) return null
            return Chain(steps)
        }

        /** 从 [start] 解析一个简单选择器,返回 (结果, 下一个位置) */
        private fun parseSimpleAt(text: String, start: Int): Pair<Simple, Int>? {
            var index = start
            var tag: String? = null
            var id: String? = null
            val attrs = ArrayList<AttrMatch>()
            val pseudos = ArrayList<Pseudo>()

            if (index < text.length && (text[index].isLetter() || text[index] == '*')) {
                val begin = index
                while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_' || text[index] == '*')) index++
                if (text.substring(begin, index) != "*") tag = text.substring(begin, index)
            }
            if (index >= text.length || (!isSelectorChar(text[index]) && text[index] != '[' && text[index] != ':')) {
                return Simple(tag, id, attrs, pseudos) to index
            }

            while (index < text.length) {
                when (text[index]) {
                    '#' -> {
                        index++
                        val begin = index
                        while (index < text.length && isNameChar(text[index])) index++
                        if (index == begin) return null
                        id = text.substring(begin, index)
                    }

                    '[' -> {
                        val close = text.indexOf(']', index)
                        if (close < 0) return null
                        val body = text.substring(index + 1, close).trim()
                        parseAttr(body)?.let { attrs.add(it) }
                        index = close + 1
                    }

                    ':' -> {
                        index++
                        val begin = index
                        while (index < text.length && text[index].isLetter()) index++
                        val name = text.substring(begin, index).lowercase()
                        var arg: String? = null
                        if (index < text.length && text[index] == '(') {
                            val close = matchParen(text, index)
                            if (close < 0) return null
                            arg = text.substring(index + 1, close).trim().trim('"', '\'')
                            index = close + 1
                        }
                        if (name.isEmpty()) return null
                        pseudos.add(Pseudo(name, arg))
                    }

                    else -> return Simple(tag, id, attrs, pseudos) to index
                }
            }
            return Simple(tag, id, attrs, pseudos) to index
        }

        private fun parseAttr(body: String): AttrMatch? {
            val operators = listOf("*=", "^=", "$=", "=")
            for (op in operators) {
                val at = body.indexOf(op)
                if (at > 0) {
                    val name = body.substring(0, at).trim()
                    val value = body.substring(at + op.length).trim().trim('"', '\'')
                    if (name.isNotEmpty()) return AttrMatch(name.lowercase(), op, value)
                }
            }
            val name = body.trim()
            return if (name.isNotEmpty()) AttrMatch(name.lowercase(), null, "") else null
        }

        /** 括号配对(:contains(a(b)) 这类嵌套也能吃下) */
        private fun matchParen(text: String, open: Int): Int {
            var depth = 0
            var index = open
            while (index < text.length) {
                when (text[index]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
                index++
            }
            return -1
        }

        /** 逗号按顶层切分(括号里的逗号不算) */
        private fun splitTopLevel(text: String, separator: Char): List<String> {
            val out = ArrayList<String>()
            var depth = 0
            val current = StringBuilder()
            text.forEach { char ->
                when {
                    char == '(' -> {
                        depth++
                        current.append(char)
                    }

                    char == ')' -> {
                        depth--
                        current.append(char)
                    }

                    char == separator && depth == 0 -> {
                        if (current.isNotBlank()) out.add(current.toString())
                        current.clear()
                    }

                    else -> current.append(char)
                }
            }
            if (current.isNotBlank()) out.add(current.toString())
            return out
        }

        private fun isSelectorChar(char: Char) = char.isLetterOrDigit() || char == '_' || char == '-'
        private fun isNameChar(char: Char) = char.isLetterOrDigit() || char == '_' || char == '-'
    }
}
