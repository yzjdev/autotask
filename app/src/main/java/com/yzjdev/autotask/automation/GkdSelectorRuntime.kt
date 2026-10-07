package com.yzjdev.autotask.automation

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 选择器运行时 —— 移植自 gkd-li/gkd 仓库 gkd-selector 模块:
 *
 *  - [SelectorProgram] ≙ engine/SelectorProgram.kt(逻辑表达式短路求值;此处递归展开,
 *    语义与 VM 指令版一致:AND 左空即短路,OR 左非空即短路)
 *  - [MatchCtx] ≙ MatchContext.kt(prev 链供 ->/prev/getPrev 引用之前匹配段)
 *  - 单元匹配 ≙ engine/CompiledUnitSelector.matchPath:从最右段起反向回溯,
 *    每层先验证属性选择器,再按关系遍历产生左邻候选;targetIndex = 最右 @ 段
 *  - 遍历 ≙ NodeAdapter.traverse*:偏移从 0 计,受 RelationExpression.min/maxOffset 约束
 *  - 值求值 ≙ property/ValueEvaluator + BuiltinMembers:or/and/ifElse 短路,
 *    equal/notEqual 容忍 null 参数,其余调用任一参数为 null 即返回 null
 *  - 属性集 ≙ A11yContext.getCacheAttr:id/vid/name/text/desc/clickable/focusable/
 *    checkable/checked/editable/longClickable/visibleToUser/left/top/right/bottom/
 *    width/height/index/depth/childCount/parent;节点调用 getChild(i)
 *
 * 节点键 = AccessibilityNodeInfo 实例(其 equals 按属性比较,同 GKD 以节点为 key)。
 */
internal class SelectorProgram private constructor(private val expression: SelectorExpression) {

    /** node 作为目标候选是否匹配整个表达式(@ 锚定) */
    fun matches(node: AccessibilityNodeInfo): Boolean = eval(node) != null

    /** root 的全部后代(不含 root)中匹配的 @ 目标节点,按先序去重(GKD querySelectorAll) */
    fun querySelectorAll(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val seen = HashSet<AccessibilityNodeInfo>()
        for (desc in Traverser.descendants(root)) {
            val target = eval(desc) ?: continue
            if (seen.add(target)) out.add(target)
        }
        return out
    }

    /** 表达式求值:返回 @ 目标节点,不匹配返回 null(照抄 SelectorProgram.match 语义) */
    private fun eval(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        evalExpr(expression, node)

    private fun evalExpr(expr: SelectorExpression, node: AccessibilityNodeInfo): AccessibilityNodeInfo? = when (expr) {
        is UnitSelectorExpression -> matchUnit(expr, node)
        is NotSelectorExpression -> if (evalExpr(expr.expression, node) == null) node else null
        is LogicalSelectorExpression -> when (expr.operator) {
            LogicalOperator.AND -> evalExpr(expr.left, node)?.let { evalExpr(expr.right, node) }
            LogicalOperator.OR -> evalExpr(expr.left, node) ?: evalExpr(expr.right, node)
        }
    }

    /** 单元匹配:最右段起回溯(照抄 matchPath,省略失败缓存——仅性能优化) */
    private fun matchUnit(expression: UnitSelectorExpression, initial: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val pss = expression.propertySelectors
        val rels = expression.relations
        if (pss.size == 1) {
            return initial.takeIf { psMatch(pss[0], it) }
        }
        val targetIndex = pss.indexOfLast { it.at }.takeIf { it >= 0 } ?: pss.lastIndex
        var psIdx = pss.lastIndex
        var ctx = MatchCtx(initial, prev = null)
        class Frame(val psIdx: Int, val ctx: MatchCtx, val candidates: Iterator<AccessibilityNodeInfo>)
        val stack = ArrayList<Frame>()

        while (true) {
            if (psMatch(pss[psIdx], ctx.node)) {
                if (psIdx == 0) {
                    // ctx.get(targetIndex):从最左段向 prev 走 targetIndex 步
                    var c = ctx
                    repeat(targetIndex) { c = c.prev ?: return null }
                    return c.node
                }
                stack.add(Frame(psIdx, ctx, candidates(rels[psIdx - 1], ctx).iterator()))
            }
            var advanced = false
            while (stack.isNotEmpty()) {
                val frame = stack.last()
                if (frame.candidates.hasNext()) {
                    ctx = MatchCtx(frame.candidates.next(), prev = frame.ctx)
                    psIdx = frame.psIdx - 1
                    advanced = true
                    break
                }
                stack.removeAt(stack.lastIndex)
            }
            if (!advanced) return null
        }
    }

    /** 关系遍历产生左邻候选(照抄 RelationOperator.traversal + NodeAdapter.traverse*) */
    private fun candidates(rel: RelationSel, ctx: MatchCtx): Sequence<AccessibilityNodeInfo> = sequence {
        val re = rel.expression
        when (rel.op) {
            RelOp.PREVIOUS -> { // 之前匹配段:沿 ctx.prev 链(照抄 Previous.traversal)
                var prev = ctx.getPrev(re.minOffset)
                var offset = re.minOffset
                while (prev != null) {
                    if (re.checkOffset(offset)) yield(prev.node)
                    prev = prev.prev
                    offset++
                    if (re.maxOffset?.let { offset > it } == true) break
                }
            }
            RelOp.BEFORE_SIBLING -> { // 左段是前置兄弟,offset = 距离-1
                val parent = getParent(ctx.node) ?: return@sequence
                val nodeIndex = indexOfNode(parent, ctx.node)
                if (nodeIndex <= 0) return@sequence
                for (offset in 0 until nodeIndex) {
                    if (re.maxOffset?.let { offset > it } == true) break
                    if (re.checkOffset(offset)) {
                        getChild(parent, nodeIndex - offset - 1)?.let { yield(it) }
                    }
                }
            }
            RelOp.AFTER_SIBLING -> { // 左段是后置兄弟
                val parent = getParent(ctx.node) ?: return@sequence
                val nodeIndex = indexOfNode(parent, ctx.node)
                val count = getChildCount(parent)
                for (index in nodeIndex + 1 until count) {
                    val offset = index - nodeIndex - 1
                    if (re.maxOffset?.let { offset > it } == true) break
                    if (re.checkOffset(offset)) {
                        getChild(parent, index)?.let { yield(it) }
                    }
                }
            }
            RelOp.ANCESTOR -> { // 左段是第 offset 代祖先(0 = 直接父)
                var offset = 0
                var parent = getParent(ctx.node)
                while (parent != null) {
                    if (re.checkOffset(offset)) yield(parent)
                    offset++
                    if (re.maxOffset?.let { offset > it } == true) return@sequence
                    parent = getParent(parent)
                }
            }
            RelOp.CHILD -> { // 左段是子节点,offset = 子索引(注意 A < B 无参 = 仅第一个子节点)
                val count = getChildCount(ctx.node)
                for (offset in 0 until count) {
                    if (re.maxOffset?.let { offset > it } == true) return@sequence
                    if (re.checkOffset(offset)) {
                        getChild(ctx.node, offset)?.let { yield(it) }
                    }
                }
            }
            RelOp.DESCENDANT -> { // 左段是子孙,offset = 先序序(不含自身)
                var offset = 0
                for (desc in Traverser.descendants(ctx.node)) {
                    if (re.checkOffset(offset)) yield(desc)
                    offset++
                    if (re.maxOffset?.let { offset > it } == true) return@sequence
                }
            }
        }
    }

    // ---- 属性选择器匹配(照抄 PropertySelector.match/matchName) ----

    private fun psMatch(ps: PropSel, node: AccessibilityNodeInfo): Boolean {
        if (!matchName(ps, node)) return false
        return ps.filters.all { evalFilter(it, node) }
    }

    /** 整名相等或 .name 后缀(照抄 matchName) */
    private fun matchName(ps: PropSel, node: AccessibilityNodeInfo): Boolean {
        if (ps.matchAnyName) return true
        val str = getAttr(node, "name") as? CharSequence ?: return false
        val name = ps.name
        return when {
            str.length == name.length -> str == name
            str.length > name.length ->
                str[str.length - name.length - 1] == '.' && str.endsWith(name)
            else -> false
        }
    }

    private fun evalFilter(e: PropExpr, node: AccessibilityNodeInfo): Boolean = when (e) {
        is ComparisonExpr -> cmpMatch(e, node)
        is NotPropExpr -> !evalFilter(e.expression, node)
        is LogicalPropExpr -> when (e.operator) {
            LogicalOperator.AND -> evalFilter(e.left, node) && evalFilter(e.right, node)
            LogicalOperator.OR -> evalFilter(e.left, node) || evalFilter(e.right, node)
        }
    }

    /** 比较求值(照抄 ComparisonExpression.match:~= 全匹配,非 EQ 运算符对 null 恒 false) */
    private fun cmpMatch(e: ComparisonExpr, node: AccessibilityNodeInfo): Boolean {
        val left = evalV(e.left, node)
        e.regex?.let { (regex, expected) ->
            return left is CharSequence && regex.matches(left) == expected
        }
        val right = evalV(e.right, node)
        return when (e.op) {
            CmpOp.EQ -> comparePrimitiveValue(left, right)
            CmpOp.NEQ -> !comparePrimitiveValue(left, right)
            CmpOp.STARTS -> left is CharSequence && right is CharSequence && left.startsWith(right)
            CmpOp.NSTARTS -> left is CharSequence && right is CharSequence && !left.startsWith(right)
            CmpOp.CONTAINS -> left is CharSequence && right is CharSequence && left.contains(right)
            CmpOp.NCONTAINS -> left is CharSequence && right is CharSequence && !left.contains(right)
            CmpOp.ENDS -> left is CharSequence && right is CharSequence && left.endsWith(right)
            CmpOp.NENDS -> left is CharSequence && right is CharSequence && !left.endsWith(right)
            CmpOp.LT -> left is Int && right is Int && left < right
            CmpOp.LTE -> left is Int && right is Int && left <= right
            CmpOp.GT -> left is Int && right is Int && left > right
            CmpOp.GTE -> left is Int && right is Int && left >= right
            CmpOp.MATCHES, CmpOp.NOT_MATCHES -> false // 已由 regex 分支处理
        }
    }

    // ---- 值求值(照抄 ValueEvaluator + BuiltinMembers) ----

    private fun evalV(v: VExp, node: AccessibilityNodeInfo): Any? = evalV(v, MatchCtx(node, prev = null))

    private fun evalV(v: VExp, ctx: MatchCtx): Any? {
        return when (v) {
            is VExp.VLit -> v.value
            is VExp.VIdentifier -> when (v.role) {
                VExp.VIdentifier.Role.PREVIOUS -> ctx.prev
                VExp.VIdentifier.Role.CURRENT -> ctx.node
                else -> getAttr(ctx.node, v.name) // null 容忍函数 equal/notEqual 也走属性兜底
            }
            is VExp.VMember -> {
                val receiver = evalV(v.obj, ctx) ?: return null
                readProperty(receiver, v.property)
            }
            is VExp.VCall -> {
                val callee = v.callee
                if (callee is VExp.VIdentifier) {
                    // 全局调用:receiver = 上下文;equal/notEqual 容忍 null 参数
                    when (callee.name) {
                        "equal" -> if (v.arguments.size == 2) {
                            comparePrimitiveValue(evalV(v.arguments[0], ctx), evalV(v.arguments[1], ctx))
                        } else null
                        "notEqual" -> if (v.arguments.size == 2) {
                            !comparePrimitiveValue(evalV(v.arguments[0], ctx), evalV(v.arguments[1], ctx))
                        } else null
                        else -> {
                            // 其余全局调用落在当前节点上(getChild 等);任一参数 null 即 null
                            val args = v.arguments.map { evalV(it, ctx) }
                            if (args.any { it == null }) null
                            else invokeNode(ctx.node, callee.name, args.filterNotNull())
                        }
                    }
                } else {
                    val calleeMember = callee as? VExp.VMember ?: return null
                    val receiver = evalV(calleeMember.obj, ctx) ?: return null
                    val name = calleeMember.property
                    // 短路(照抄 ValueEvaluator EXPECT_RECEIVER 分支)
                    when {
                        name == "or" && receiver == true -> true
                        name == "and" && receiver == false -> false
                        name == "ifElse" && receiver is Boolean && v.arguments.size == 2 ->
                            evalV(v.arguments[if (receiver) 0 else 1], ctx)
                        else -> {
                            val args = v.arguments.map { evalV(it, ctx) }
                            invokeBuiltin(receiver, name, args)
                        }
                    }
                }
            }
        }
    }

    /** 内建方法分发(照抄 BuiltinMembers.invoke/evaluate;类型不符返回 null) */
    private fun invokeBuiltin(receiver: Any, name: String, args: List<Any?>): Any? = when {
        receiver is MatchCtx -> when (name) {
            "getPrev" -> (args.getOrNull(0) as? Int)?.let { receiver.getPrev(it) }
            else -> readProperty(receiver.node, name)?.let { invokeBuiltin(it, name, args) }
                ?: invokeNode(receiver.node, name, args.filterNotNull())
        }
        receiver is Boolean -> when (name) {
            "toInt" -> if (args.isEmpty()) if (receiver) 1 else 0 else null
            "or" -> if (args.size == 1 && args[0] is Boolean) receiver || (args[0] as Boolean) else null
            "and" -> if (args.size == 1 && args[0] is Boolean) receiver && (args[0] as Boolean) else null
            "not" -> if (args.isEmpty()) !receiver else null
            "ifElse" -> if (args.size == 2) if (receiver) args[0] else args[1] else null
            else -> null
        }
        receiver is Int -> when (name) {
            "toString" -> when (args.size) {
                0 -> receiver.toString()
                1 -> (args[0] as? Int)?.takeIf { it in 2..36 }?.let { receiver.toString(it) }
                else -> null
            }
            "plus" -> int1(args) { receiver + it }
            "minus" -> int1(args) { receiver - it }
            "times" -> int1(args) { receiver * it }
            "div" -> int1(args) { if (it != 0) receiver / it else null }
            "rem" -> int1(args) { if (it != 0) receiver % it else null }
            "more" -> int1(args) { receiver > it }
            "moreEqual" -> int1(args) { receiver >= it }
            "less" -> int1(args) { receiver < it }
            "lessEqual" -> int1(args) { receiver <= it }
            else -> null
        }
        receiver is CharSequence -> when (name) {
            "get" -> int1(args) { receiver.getOrNull(it)?.toString() }
            "at" -> int1(args) {
                val i = if (it < 0) receiver.length + it else it
                receiver.getOrNull(i)?.toString()
            }
            "substring" -> when (args.size) {
                1 -> (args[0] as? Int)?.takeIf { it >= 0 }?.let { start ->
                    if (start >= receiver.length) "" else receiver.substring(start)
                }
                2 -> {
                    val start = args[0] as? Int ?: return null
                    val end = args[1] as? Int ?: return null
                    if (start < 0 || end < start) null
                    else receiver.substring(start, end.coerceAtMost(receiver.length))
                }
                else -> null
            }
            "toInt" -> when (args.size) {
                0 -> receiver.toString().toIntOrNull()
                1 -> (args[0] as? Int)?.takeIf { it in 2..36 }?.let { receiver.toString().toIntOrNull(it) }
                else -> null
            }
            "indexOf" -> when (args.size) {
                1 -> (args[0] as? CharSequence)?.let { receiver.indexOf(it.toString()) }
                2 -> {
                    val s = args[0] as? CharSequence ?: return null
                    val from = args[1] as? Int ?: return null
                    receiver.indexOf(s.toString(), from)
                }
                else -> null
            }
            else -> null
        }
        else -> null
    }

    private inline fun <T> int1(args: List<Any?>, f: (Int) -> T): T? {
        val v = args.getOrNull(0) as? Int ?: return null
        return f(v)
    }

    /** 成员属性读取(照抄 readProperty:字符串 length;上下文 prev/current;其余落到节点属性) */
    private fun readProperty(receiver: Any, property: String): Any? = when {
        receiver is MatchCtx -> when (property) {
            "prev" -> receiver.prev
            "current" -> receiver.node
            else -> getAttr(receiver.node, property)
        }
        receiver is CharSequence && property == "length" -> receiver.length
        receiver is AccessibilityNodeInfo -> getAttr(receiver, property)
        else -> null
    }

    /** 节点调用(照抄 adapter.getInvoke:getChild) */
    private fun invokeNode(node: AccessibilityNodeInfo, name: String, args: List<Any>): Any? = when (name) {
        "getChild" -> (args.getOrNull(0) as? Int)?.let { getChild(node, it) }
        else -> null
    }

    // ---- 节点适配(A11yContext.getCacheAttr 的公开 API 等价实现) ----

    private fun getParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? = node.parent

    private fun getChildCount(node: AccessibilityNodeInfo): Int = node.childCount.coerceAtMost(MAX_CHILD_SIZE)

    private fun getChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? =
        if (index in 0 until node.childCount) node.getChild(index) else null

    /** 兄弟序号:父节点子列表中按节点相等判定(照抄 getCacheIndex,找不到回退 0) */
    private fun indexOfNode(parent: AccessibilityNodeInfo, node: AccessibilityNodeInfo): Int {
        for (i in 0 until parent.childCount) {
            val c = parent.getChild(i) ?: continue
            if (c == node) return i
        }
        return 0
    }

    private fun getAttr(node: AccessibilityNodeInfo, name: String): Any? = when (name) {
        "id" -> node.viewIdResourceName
        "vid" -> node.viewIdResourceName?.substringAfter("id/")
        "name" -> node.className
        "text" -> node.text
        "desc" -> node.contentDescription
        "clickable" -> node.isClickable
        "focusable" -> node.isFocusable
        "checkable" -> node.isCheckable
        "checked" -> @Suppress("DEPRECATION") node.isChecked
        "editable" -> node.isEditable
        "longClickable" -> node.isLongClickable
        "visibleToUser" -> node.isVisibleToUser
        "left" -> boundsOf(node).left
        "top" -> boundsOf(node).top
        "right" -> boundsOf(node).right
        "bottom" -> boundsOf(node).bottom
        "width" -> boundsOf(node).width()
        "height" -> boundsOf(node).height()
        "index" -> getParent(node)?.let { indexOfNode(it, node) } ?: 0
        "depth" -> generateSequence(node.parent) { it.parent }.count()
        "childCount" -> node.childCount
        "parent" -> getParent(node)
        // 本地扩展:节点在悬浮窗快照中的 NODE 编号(1 基,与悬浮窗显示一致);未收录 = 0
        "nodeIndex" -> NodeSnapshotCollector.snapshotIndexOf(node)
        else -> null
    }

    private fun boundsOf(node: AccessibilityNodeInfo): android.graphics.Rect =
        android.graphics.Rect().also { node.getBoundsInScreen(it) }

    companion object {
        private const val MAX_CHILD_SIZE = 512

        /** 空表达式:匹配任意节点(空 name + 无过滤 = 全通过,同 GKD matchAnyName) */
        val EMPTY = SelectorProgram(
            UnitSelectorExpression(listOf(PropSel(at = false, name = "", filters = emptyList())), emptyList()),
        )

        fun compile(expression: SelectorExpression): SelectorProgram = SelectorProgram(expression)
    }
}

/** 匹配上下文:prev 链指向已匹配的右侧段(照抄 MatchContext) */
internal class MatchCtx internal constructor(
    val node: AccessibilityNodeInfo,
    val prev: MatchCtx?,
) {
    /** index 0 = 紧邻右段(照抄 getPrev) */
    fun getPrev(index: Int): MatchCtx? {
        if (index < 0) return null
        var c = prev ?: return null
        repeat(index) { c = c.prev ?: return null }
        return c
    }
}

internal object Traverser {
    /** 先序遍历含根(空表达式回退用) */
    fun preOrder(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            out.add(n)
            for (i in n.childCount - 1 downTo 0) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return out
    }

    /** 先序后代,不含自身(照抄 NodeAdapter.getDescendants) */
    fun descendants(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        for (i in root.childCount - 1 downTo 0) {
            root.getChild(i)?.let { stack.addLast(it) }
        }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            yield(n)
            for (i in n.childCount - 1 downTo 0) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
    }
}
