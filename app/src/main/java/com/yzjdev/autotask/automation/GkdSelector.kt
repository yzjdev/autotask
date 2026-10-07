package com.yzjdev.autotask.automation

import kotlinx.serialization.Serializable

/**
 * GKD 选择器 —— 移植自 gkd-li/gkd 仓库 gkd-selector 模块(github.com/gkd-kit/gkd)。
 *
 * 解析器忠实照抄 gkd-selector/src/commonMain/kotlin/li/gkd/selector/syntax:
 *  - ParserCursor 直解文法(非 token 流):PropertySyntaxParser / RelationSyntaxParser / SelectorParser
 *  - 逻辑运算符 `(A + B) || (M > N) && !(C + D)`,&& 优先级 2 > || 优先级 1,
 *    逻辑运算符两侧操作数必须 () 包裹(GKD 硬约束)
 *  - 关系选择器:+ - > < << -> + 元组 (1,2,3) / 多项式 (an+b)(最多两个单项式,含 12-n / n+6 / -3n+10)
 *  - 属性选择器:@ 目标标记、名称简写 TextView ≡ [name='TextView'||name$='.TextView'] 由运行时
 *    matchName 实现(同 GKD PropertySelector.matchName:整名或 .name 后缀)
 *  - [] 内布尔表达式支持嵌套括号/取反 !(…);值表达式支持成员/调用
 *    (text.length、parent.childCount.minus(1)、equal/notEqual 全局函数等,照抄 BuiltinMembers)
 *
 * 序列化只存规范化表达式字符串;解析结果进程内 LRU 缓存。
 */
@Serializable
data class GkdSelector(val expr: String = "") {

    private val compiled: SelectorProgram? by lazy {
        runCatching { GkdSelector.compile(expr) }.getOrNull()
    }

    /** 表达式本身即摘要(GKD 语法) */
    fun summary(): String = expr

    override fun toString(): String = expr

    /** 子树内查找全部目标节点(先序) */
    fun find(root: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> {
        val program = compiled ?: return Traverser.preOrder(root)
        return program.querySelectorAll(root)
    }

    /** node 是否为该选择器的目标节点(@ 段) */
    fun matches(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        if (expr.isBlank()) return true
        val program = compiled ?: return true
        return program.matches(node)
    }

    /** 取第一个带引号的文本断言值(text/desc),供日志摘要显示 */
    fun firstPropValue(vararg keys: String): String? {
        // 匹配 [key op "value"](引号可为 '/"/`),值后跟 ] 或空格(|| 组合断言),返回引号内值;
        // text=null 等裸值不匹配,避免跨断言错误捕获
        val pattern = """\[(?:text|desc|${keys.joinToString("|") { Regex.escape(it) }})\s*(?:=|\*=|\^=|\$=|~=)\s*(['"`])((?:(?!\1).)*)\1(?:\]|\s)"""
        return Regex(pattern).find(expr)?.groupValues?.get(2)
    }

    companion object {
        private val cache = object : LinkedHashMap<String, SelectorProgram>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SelectorProgram>) = size > 128
        }

        /** 校验并构造;表达式非法抛 SelectorSyntaxException */
        fun parse(expr: String): GkdSelector {
            compile(expr)
            return GkdSelector(expr)
        }

        /** 编译(带缓存);非法抛异常 */
        internal fun compile(expr: String): SelectorProgram {
            val trimmed = expr.trim()
            if (trimmed.isEmpty()) return SelectorProgram.EMPTY
            synchronized(cache) { cache[trimmed] }?.let { return it }
            val program = SelectorParser(trimmed).readSelector()
            synchronized(cache) { cache[trimmed] = program }
            return program
        }
    }
}

// ==================== 字符集常量(照抄 ParserCursor.kt) ====================

private const val WHITESPACE_CHARS = " \t\r\n"
private const val DIGIT_CHARS = "0123456789"
private const val POSITIVE_DIGIT_CHARS = "123456789"
private const val HEX_DIGIT_CHARS = "abcdefABCDEF0123456789"
private const val STRING_QUOTE_CHARS = "`'\""
private const val IDENTIFIER_START_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_"
private const val IDENTIFIER_PART_CHARS = IDENTIFIER_START_CHARS + DIGIT_CHARS
private const val VALUE_PRIMARY_START_CHARS = "-$DIGIT_CHARS$STRING_QUOTE_CHARS$IDENTIFIER_START_CHARS"
private const val VALUE_START_CHARS = "($VALUE_PRIMARY_START_CHARS"
private const val PROPERTY_START_CHARS = "@[*$IDENTIFIER_START_CHARS"
private const val CONNECT_START_CHARS = "+-<>"

private fun Char?.isOneOf(chars: String): Boolean = this != null && chars.contains(this)

// ==================== AST(照抄 engine / property / relation) ====================

/** 逻辑运算符:&& 优先级高于 ||(照抄 LogicalOperator) */
internal enum class LogicalOperator(val key: String, val precedence: Int) {
    AND("&&", 2),
    OR("||", 1);

    companion object {
        val parseOrder: List<LogicalOperator> = entries.sortedByDescending { it.key.length }
    }
}

/** 选择器表达式:单元(属性链) | 逻辑组合 | 取反 */
internal sealed interface SelectorExpression

internal class UnitSelectorExpression(
    val propertySelectors: List<PropSel>,
    val relations: List<RelationSel>,
) : SelectorExpression

internal class LogicalSelectorExpression(
    val left: SelectorExpression,
    val operator: LogicalOperator,
    val right: SelectorExpression,
) : SelectorExpression

internal class NotSelectorExpression(
    val expression: SelectorExpression,
) : SelectorExpression

/** 关系表达式:元组 | an+b 多项式(照抄 RelationExpression) */
internal sealed interface RelationExpression {
    val minOffset: Int
    val maxOffset: Int?
    fun checkOffset(offset: Int): Boolean
}

internal class TupleExpression(val numbers: List<Int>) : RelationExpression {
    private val indexes = numbers.map { it - 1 }
    override val minOffset: Int = (numbers.firstOrNull() ?: 1) - 1
    override val maxOffset: Int? = numbers.lastOrNull()?.minus(1)
    override fun checkOffset(offset: Int): Boolean = indexes.binarySearch(offset) >= 0
}

/** 序列 an+b;a=1,b=0 = 全部偏移(照抄 PolynomialExpression,含负多项式 min/max 推导) */
internal class PolynomialExpression(val a: Int = 0, val b: Int = 1) : RelationExpression {
    companion object {
        fun isValid(a: Int, b: Int): Boolean = when {
            a > 0 -> true
            a == 0 -> b > 0
            else -> b > 0 && b.toLong() > -a.toLong()
        }
    }

    val matchesAllOffsets: Boolean get() = a == 1 && b == 0

    override val minOffset: Int = (when {
        a > 0 && b > 0 -> a + b
        a > 0 && b == 0 -> a
        a > 0 -> a * (-b / a + 1) + b
        a == 0 -> b
        else -> {
            val maxN = -b / a - if (b % a == 0) 1 else 0
            a * maxN + b
        }
    }) - 1

    override val maxOffset: Int? = (when {
        a > 0 -> null
        a == 0 -> b
        else -> a + b
    })?.let { it - 1 }

    private val isConstant = minOffset == maxOffset

    override fun checkOffset(offset: Int): Boolean {
        if (isConstant) return offset == minOffset
        val y = offset + 1 - b
        return y % a == 0 && y / a >= 1
    }
}

/** 关系选择器 = 操作符 + 偏移表达式 */
internal class RelationSel(val op: RelOp, val expression: RelationExpression)

internal enum class RelOp(val key: String) {
    BEFORE_SIBLING("+"), AFTER_SIBLING("-"), ANCESTOR(">"), CHILD("<"), DESCENDANT("<<"), PREVIOUS("->");

    companion object {
        // 按 key 长度降序匹配(先 << 后 <,先 -> 后 -;照抄 RelationOperator.parseOrder)
        val parseOrder: List<RelOp> = entries.sortedByDescending { it.key.length }
    }
}

/** 值表达式:字面量 | 变量(标识/成员/调用)(照抄 ValueExpression) */
internal sealed interface VExp {
    data class VLit(val value: Any?) : VExp // null / Boolean / Int / String
    data class VIdentifier(val name: String) : VExp {
        val role = when (name) {
            "prev" -> Role.PREVIOUS
            "current" -> Role.CURRENT
            "equal", "notEqual" -> Role.NULL_TOLERANT
            else -> Role.OTHER
        }
        enum class Role { OTHER, PREVIOUS, CURRENT, NULL_TOLERANT }
    }
    data class VMember(val obj: VExp, val property: String) : VExp
    data class VCall(val callee: VExp, val arguments: List<VExp>) : VExp
}

/** 属性选择器:@ 目标标记 + 名称 + [] 过滤表达式 */
internal class PropSel(val at: Boolean, val name: String, val filters: List<PropExpr>) {
    val matchAnyName: Boolean get() = name.isEmpty() || name == "*"
}

/** [] 内属性表达式:比较 | 逻辑 | 取反 */
internal sealed interface PropExpr

internal class ComparisonExpr(
    val left: VExp,
    val op: CmpOp,
    val right: VExp,
) : PropExpr {
    /** ~= / !~= 预编译正则(照抄 RegexComparison);null = 值比较 */
    val regex: Pair<Regex, Boolean>? = when (op) {
        CmpOp.MATCHES -> try { Regex((right as? VExp.VLit)?.value as? String ?: "") to true } catch (_: Throwable) { null }
        CmpOp.NOT_MATCHES -> try { Regex((right as? VExp.VLit)?.value as? String ?: "") to false } catch (_: Throwable) { null }
        else -> null
    }
}

internal class LogicalPropExpr(
    val left: PropExpr,
    val operator: LogicalOperator,
    val right: PropExpr,
) : PropExpr

internal class NotPropExpr(val expression: PropExpr) : PropExpr

/** 比较运算符(照抄 CompareOperator.parseOrder,按 key 长度降序) */
internal enum class CmpOp(val key: String) {
    EQ("="), NEQ("!="), STARTS("^="), NSTARTS("!^="), CONTAINS("*="), NCONTAINS("!*="),
    ENDS("$="), NENDS("!$="), LT("<"), LTE("<="), GT(">"), GTE(">="),
    MATCHES("~="), NOT_MATCHES("!~=");

    companion object {
        val parseOrder: List<CmpOp> = entries.sortedByDescending { it.key.length }
    }
}

/** 原始值相等比较(照抄 comparePrimitiveValue:CharSequence 逐字符,其余 ==) */
internal fun comparePrimitiveValue(left: Any?, right: Any?): Boolean {
    if (left !is CharSequence || right !is CharSequence) return left == right
    if (left === right) return true
    if (left.length != right.length) return false
    for (i in left.indices.reversed()) {
        if (left[i] != right[i]) return false
    }
    return true
}

// ==================== Cursor(照抄 ParserCursor.kt) ====================

private class SelectorSyntaxException(val expected: String, val actual: Char?) :
    Exception("期望 $expected,实际: ${actual ?: "EOF"}")

private class ParserCursor(val source: String) {
    var index = 0

    val current: Char? get() = source.getOrNull(index)

    fun readWhitespace() {
        while (current.isOneOf(WHITESPACE_CHARS)) index++
    }

    fun readChar(expected: Char) {
        expectChar(expected)
        index++
    }

    /** 读关键字字面量;后随不得是标识符字符(照抄 readLiteral) */
    fun readLiteral(value: String): Boolean {
        if (source.startsWith(value, index) &&
            !source.getOrNull(index + value.length).isOneOf(IDENTIFIER_PART_CHARS)
        ) {
            index += value.length
            return true
        }
        return false
    }

    fun readUnsignedInt(): Int {
        val start = index
        expectOneOf(DIGIT_CHARS, "digit")
        if (current == '0') {
            index++
            if (current.isOneOf(DIGIT_CHARS)) {
                index = start
                errorExpected("integer without leading zero")
            }
            return 0
        }
        while (current.isOneOf(DIGIT_CHARS)) index++
        return source.substring(start, index).toIntOrNull() ?: run {
            index = start
            errorExpected("32-bit integer")
        }
    }

    fun readInt(): Int {
        val start = index
        if (current == '-') index++
        expectOneOf(DIGIT_CHARS, "digit")
        if (current == '0') {
            index++
            if (current.isOneOf(DIGIT_CHARS)) {
                index = start
                errorExpected("integer without leading zero")
            }
            return 0
        }
        while (current.isOneOf(DIGIT_CHARS)) index++
        return source.substring(start, index).toIntOrNull() ?: run {
            index = start
            errorExpected("32-bit integer")
        }
    }

    /** 读字符串(照抄 scanString decode 分支:\xHH / \uHHHH / 控制字符必须转义) */
    fun readString(): String {
        expectOneOf(STRING_QUOTE_CHARS, "string quote")
        val quote = source[index]
        var i = index + 1
        val sb = StringBuilder()
        while (true) {
            val c = source.getOrNull(i) ?: errorExpected("$quote")
            when {
                c.code in 0x00..0x1F -> errorExpected("escaped control character")
                c == quote -> {
                    index = i + 1
                    return sb.toString()
                }
                c != '\\' -> { sb.append(c); i++ }
                else -> {
                    i++
                    when (val e = source.getOrNull(i) ?: errorExpected("escape character")) {
                        '\\', '\'', '"', '`' -> { sb.append(e); i++ }
                        'n' -> { sb.append('\n'); i++ }
                        'r' -> { sb.append('\r'); i++ }
                        't' -> { sb.append('\t'); i++ }
                        'b' -> { sb.append('\b'); i++ }
                        'x' -> {
                            val digits = source.substring(i + 1, (i + 3).coerceAtMost(source.length))
                            if (digits.length != 2 || digits.any { !HEX_DIGIT_CHARS.contains(it) }) {
                                errorExpected("hex digit")
                            }
                            sb.append(digits.toInt(16).toChar()); i += 3
                        }
                        'u' -> {
                            val digits = source.substring(i + 1, (i + 5).coerceAtMost(source.length))
                            if (digits.length != 4 || digits.any { !HEX_DIGIT_CHARS.contains(it) }) {
                                errorExpected("hex digit")
                            }
                            sb.append(digits.toInt(16).toChar()); i += 5
                        }
                        else -> errorExpected("escape character")
                    }
                }
            }
        }
    }

    fun expectChar(expected: Char): Char {
        if (current != expected) errorExpected("'$expected'")
        return expected
    }

    fun expectOneOf(chars: String, description: String): Char {
        val c = current ?: errorExpected(description)
        if (!chars.contains(c)) errorExpected(description)
        return c
    }

    fun errorExpected(expected: String): Nothing =
        throw SelectorSyntaxException(expected, current)
}

// ==================== 属性表达式解析(照抄 PropertySyntaxParser.kt) ====================

private class PropertySyntaxParser(private val cursor: ParserCursor) {

    fun readPropertySelector(): PropSel {
        val at = cursor.current == '@'
        if (at) cursor.readChar('@')
        val name = if (cursor.current == '[') "" else readPropertyName()
        if (name.isEmpty()) cursor.expectChar('[')
        val filters = ArrayList<PropExpr>()
        while (cursor.current == '[') filters.add(readFilter())
        return PropSel(at, name, filters)
    }

    private fun readPropertyName(): String {
        val start = cursor.index
        if (cursor.current == '*') {
            cursor.index++
            return "*"
        }
        cursor.expectOneOf(IDENTIFIER_START_CHARS, "property name")
        cursor.index++
        while (true) {
            when (cursor.current) {
                '.' -> {
                    cursor.index++
                    cursor.expectOneOf(IDENTIFIER_START_CHARS, "property name segment")
                    cursor.index++
                }
                else -> if (cursor.current.isOneOf(IDENTIFIER_PART_CHARS)) {
                    cursor.index++
                } else {
                    return cursor.source.substring(start, cursor.index)
                }
            }
        }
    }

    private fun readFilter(): PropExpr {
        cursor.readChar('[')
        cursor.readWhitespace()
        val expression = readExpression()
        cursor.readWhitespace()
        cursor.readChar(']')
        return expression
    }

    // ---- 布尔表达式:帧式递归下降(照抄 readExpression) ----

    private class ExpressionFrame(val negated: Boolean) {
        val values = ArrayList<PropExpr>()
        val operators = ArrayList<LogicalOperator>()
    }

    private fun readExpression(): PropExpr {
        val frames = ArrayList<ExpressionFrame>()
        frames.add(ExpressionFrame(negated = false))

        while (true) {
            val frame = frames.last()
            if (frame.values.size == frame.operators.size) {
                when (cursor.current) {
                    '(' -> {
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        frames.add(ExpressionFrame(negated = false))
                    }
                    '!' -> {
                        cursor.readChar('!')
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        frames.add(ExpressionFrame(negated = true))
                    }
                    else -> frame.values.add(readBinaryExpression())
                }
                continue
            }

            cursor.readWhitespace()
            val operator = peekLogicalOperator()
            if (operator != null) {
                cursor.index += operator.key.length
                frame.operators.add(operator)
                cursor.readWhitespace()
                continue
            }

            if (frames.size > 1 && cursor.current == ')') {
                ensureComplete(frame)
                val term = finish(frame)
                cursor.readChar(')')
                frames.removeAt(frames.lastIndex)
                frames.last().values.add(if (frame.negated) NotPropExpr(term) else term)
                continue
            }

            if (frames.size > 1) cursor.errorExpected("')'")
            ensureComplete(frame)
            return finish(frame)
        }
    }

    private fun ensureComplete(frame: ExpressionFrame) {
        if (frame.values.isEmpty() || frame.values.size != frame.operators.size + 1) {
            cursor.errorExpected("property expression")
        }
    }

    private fun finish(frame: ExpressionFrame): PropExpr {
        while (frame.operators.isNotEmpty()) {
            // && 优先级在帧式解析里天然成立:运算符按序两两归约
            val right = frame.values.removeAt(frame.values.lastIndex)
            val left = frame.values.removeAt(frame.values.lastIndex)
            val operator = frame.operators.removeAt(frame.operators.lastIndex)
            frame.values.add(LogicalPropExpr(left, operator, right))
        }
        return frame.values.single()
    }

    private fun peekLogicalOperator(): LogicalOperator? =
        LogicalOperator.parseOrder.firstOrNull { cursor.source.startsWith(it.key, cursor.index) }

    private fun readBinaryExpression(): ComparisonExpr {
        val left = readValueExpression()
        cursor.readWhitespace()
        val op = readCompareOperator()
        cursor.readWhitespace()
        val right = readValueExpression()
        return ComparisonExpr(left, op, right)
    }

    private fun readCompareOperator(): CmpOp {
        val op = CmpOp.parseOrder.firstOrNull { cursor.source.startsWith(it.key, cursor.index) }
            ?: cursor.errorExpected("comparison operator")
        cursor.index += op.key.length
        return op
    }

    // ---- 值表达式(照抄 readValueExpression 的帧式调用解析) ----

    private fun readValueExpression(): VExp {
        // 简化栈式解析:GKD 的 CallFrame/GroupFrame 语义
        val primary = readValuePrimary()
        var term: VExp = when (primary) {
            is VExp.VLit -> return primary // 字面量后不允许 . 和 (
            else -> primary
        }
        while (true) {
            val variable = term
            val whitespaceStart = cursor.index
            cursor.readWhitespace()
            when (cursor.current) {
                '.' -> {
                    cursor.readChar('.')
                    cursor.readWhitespace()
                    val property = readIdentifierName()
                    term = VExp.VMember(variable, property)
                }
                '(' -> {
                    if (variable is VExp.VCall) cursor.errorExpected("non-call-expression callable")
                    cursor.readChar('(')
                    cursor.readWhitespace()
                    val args = ArrayList<VExp>()
                    if (cursor.current == ')') {
                        cursor.readChar(')')
                        term = VExp.VCall(variable, args)
                        continue
                    }
                    if (!cursor.current.isOneOf(VALUE_START_CHARS)) cursor.errorExpected("call argument")
                    do {
                        args.add(readValueExpression())
                        cursor.readWhitespace()
                    } while (cursor.eatComma())
                    cursor.readChar(')')
                    term = VExp.VCall(variable, args)
                }
                else -> {
                    cursor.index = whitespaceStart
                    return term
                }
            }
        }
    }

    private fun ParserCursor.eatComma(): Boolean {
        if (current == ',') { index++; readWhitespace(); return true }
        return false
    }

    private fun readValuePrimary(): VExp {
        cursor.expectOneOf(VALUE_PRIMARY_START_CHARS, "value")
        val value = when {
            cursor.readLiteral("null") -> VExp.VLit(null)
            cursor.readLiteral("false") -> VExp.VLit(false)
            cursor.readLiteral("true") -> VExp.VLit(true)
            cursor.current.isOneOf("-$DIGIT_CHARS") -> VExp.VLit(cursor.readInt())
            cursor.current.isOneOf(STRING_QUOTE_CHARS) -> VExp.VLit(cursor.readString())
            else -> VExp.VIdentifier(readIdentifierName())
        }
        return value
    }

    private fun readIdentifierName(): String {
        val start = cursor.index
        cursor.expectOneOf(IDENTIFIER_START_CHARS, "identifier")
        cursor.index++
        while (cursor.current.isOneOf(IDENTIFIER_PART_CHARS)) cursor.index++
        val value = cursor.source.substring(start, cursor.index)
        if (value == "null" || value == "false" || value == "true") {
            cursor.index = start
            cursor.errorExpected("non-keyword identifier")
        }
        return value
    }
}

// ==================== 关系表达式解析(照抄 RelationSyntaxParser.kt) ====================

private class RelationSyntaxParser(private val cursor: ParserCursor) {

    private class Monomial(val coefficient: Int, val power: Int)

    fun readRelationSelector(): RelationSel {
        val operator = readOperator()
        val expression: RelationExpression = if (cursor.current.isOneOf("(n$DIGIT_CHARS")) {
            readExpression()
        } else {
            PolynomialExpression() // 无参数 = 默认 (0,1)
        }
        return RelationSel(operator, expression)
    }

    private fun readOperator(): RelOp {
        val op = RelOp.parseOrder.firstOrNull { cursor.source.startsWith(it.key, cursor.index) }
            ?: cursor.errorExpected("relation operator")
        cursor.index += op.key.length
        return op
    }

    private fun readExpression(): RelationExpression =
        if (isTupleExpression()) readTupleExpression() else readPolynomialExpression()

    private fun isTupleExpression(): Boolean {
        val start = cursor.index
        try {
            if (cursor.current != '(') return false
            cursor.index++
            cursor.readWhitespace()
            if (!cursor.current.isOneOf(DIGIT_CHARS)) return false
            while (cursor.current.isOneOf(DIGIT_CHARS)) cursor.index++
            cursor.readWhitespace()
            return cursor.current == ','
        } finally {
            cursor.index = start
        }
    }

    private fun readTupleExpression(): TupleExpression {
        cursor.readChar('(')
        cursor.readWhitespace()
        val numbers = ArrayList<Int>()
        while (true) {
            cursor.expectOneOf(POSITIVE_DIGIT_CHARS, "positive integer")
            val value = cursor.readUnsignedInt()
            if (numbers.lastOrNull()?.let { it >= value } == true) {
                cursor.errorExpected("increasing integer")
            }
            numbers.add(value)
            cursor.readWhitespace()
            if (cursor.current != ',') break
            cursor.readChar(',')
            cursor.readWhitespace()
        }
        cursor.readChar(')')
        return TupleExpression(numbers)
    }

    private fun readMonomial(): Monomial {
        cursor.expectOneOf("+-n$DIGIT_CHARS", "monomial")
        val sign = when (cursor.current) {
            '+' -> { cursor.index++; 1 }
            '-' -> { cursor.index++; -1 }
            else -> 1
        }
        cursor.readWhitespace()
        cursor.expectOneOf("n$DIGIT_CHARS", "monomial value")
        val coefficient = sign * if (cursor.current.isOneOf(DIGIT_CHARS)) {
            cursor.readUnsignedInt()
        } else {
            1
        }
        val power = if (cursor.current == 'n') {
            cursor.index++
            1
        } else {
            0
        }
        return Monomial(coefficient, power)
    }

    private fun readPolynomialExpression(): PolynomialExpression {
        cursor.expectOneOf("(n$DIGIT_CHARS", "relation expression")
        val monomials = ArrayList<Monomial>()
        if (cursor.current == '(') {
            cursor.readChar('(')
            cursor.readWhitespace()
            while (true) {
                if (monomials.isNotEmpty()) cursor.expectOneOf("+-", "'+' or '-'")
                if (monomials.size >= 2) cursor.errorExpected("at most two monomials")
                val monomial = readMonomial()
                if (monomials.any { it.power == monomial.power }) {
                    cursor.errorExpected("distinct monomial powers")
                }
                monomials.add(monomial)
                cursor.readWhitespace()
                if (!cursor.current.isOneOf("+-")) break
            }
            cursor.readChar(')')
        } else {
            monomials.add(readMonomial())
        }

        val a = monomials.firstOrNull { it.power == 1 }?.coefficient ?: 0
        val b = monomials.firstOrNull { it.power == 0 }?.coefficient ?: 0
        if (!PolynomialExpression.isValid(a, b)) {
            cursor.errorExpected("valid an+b polynomial")
        }
        return PolynomialExpression(a, b)
    }
}

// ==================== 选择器解析(照抄 SelectorParser.kt) ====================

private class SelectorParser(source: String) {
    private val cursor = ParserCursor(source)
    private val propertyParser = PropertySyntaxParser(cursor)
    private val relationParser = RelationSyntaxParser(cursor)

    private class SelectorTerm(
        val value: SelectorExpression,
        val grouped: Boolean,
    )

    private class ExpressionFrame(val negated: Boolean) {
        val values = ArrayList<SelectorTerm>()
        val operators = ArrayList<LogicalOperator>()
    }

    fun readSelector(): SelectorProgram {
        cursor.readWhitespace()
        val expression = readExpression()
        cursor.readWhitespace()
        if (cursor.current != null) cursor.errorExpected("end of selector")
        return SelectorProgram.compile(expression)
    }

    private fun readExpression(): SelectorExpression {
        val frames = ArrayList<ExpressionFrame>()
        frames.add(ExpressionFrame(negated = false))

        while (true) {
            val frame = frames.last()
            if (frame.values.size == frame.operators.size) {
                when {
                    cursor.current == '(' -> {
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        frames.add(ExpressionFrame(negated = false))
                    }
                    cursor.current == '!' -> {
                        cursor.readChar('!')
                        cursor.readChar('(')
                        cursor.readWhitespace()
                        frames.add(ExpressionFrame(negated = true))
                    }
                    cursor.current.isOneOf(PROPERTY_START_CHARS) -> {
                        frame.values.add(SelectorTerm(readUnitExpression(), grouped = false))
                    }
                    else -> cursor.errorExpected("selector expression")
                }
                continue
            }

            val whitespaceStart = cursor.index
            cursor.readWhitespace()
            val operator = peekLogicalOperator()
            if (operator != null) {
                if (!frame.values.last().grouped) {
                    cursor.errorExpected("parenthesized selector before logical operator")
                }
                cursor.index += operator.key.length
                frame.operators.add(operator)
                cursor.readWhitespace()
                continue
            }

            if (frames.size > 1 && cursor.current == ')') {
                ensureComplete(frame)
                val term = finish(frame)
                cursor.readChar(')')
                frames.removeAt(frames.lastIndex)
                val groupedTerm = if (frame.negated) {
                    SelectorTerm(NotSelectorExpression(term.value), grouped = true)
                } else {
                    SelectorTerm(term.value, grouped = true)
                }
                frames.last().values.add(groupedTerm)
                continue
            }

            if (frames.size > 1) cursor.errorExpected("')'")
            cursor.index = whitespaceStart
            ensureComplete(frame)
            return finish(frame).value
        }
    }

    private fun ensureComplete(frame: ExpressionFrame) {
        if (frame.values.isEmpty() || frame.values.size != frame.operators.size + 1) {
            cursor.errorExpected("selector expression")
        }
        if (frame.operators.isNotEmpty() && !frame.values.last().grouped) {
            cursor.errorExpected("parenthesized selector after logical operator")
        }
    }

    private fun finish(frame: ExpressionFrame): SelectorTerm {
        while (frame.operators.isNotEmpty()) {
            val right = frame.values.removeAt(frame.values.lastIndex)
            val left = frame.values.removeAt(frame.values.lastIndex)
            if (!right.grouped) {
                cursor.errorExpected("parenthesized selector after logical operator")
            }
            val operator = frame.operators.removeAt(frame.operators.lastIndex)
            frame.values.add(SelectorTerm(LogicalSelectorExpression(left.value, operator, right.value), grouped = true))
        }
        return frame.values.single()
    }

    private fun peekLogicalOperator(): LogicalOperator? =
        LogicalOperator.parseOrder.firstOrNull { cursor.source.startsWith(it.key, cursor.index) }

    /** 单元表达式:属性选择器 + (关系 属性选择器)*;空白连接 = 任意祖先(照抄 readUnitExpression) */
    private fun readUnitExpression(): UnitSelectorExpression {
        val propertySelectors = ArrayList<PropSel>()
        val relations = ArrayList<RelationSel>()
        propertySelectors.add(propertyParser.readPropertySelector())
        while (cursor.current.isOneOf(WHITESPACE_CHARS)) {
            val whitespaceStart = cursor.index
            cursor.readWhitespace()
            when {
                cursor.current.isOneOf(CONNECT_START_CHARS) -> {
                    relations.add(relationParser.readRelationSelector())
                    cursor.expectOneOf(WHITESPACE_CHARS, "whitespace after relation expression")
                    cursor.readWhitespace()
                    propertySelectors.add(propertyParser.readPropertySelector())
                }
                cursor.current.isOneOf(PROPERTY_START_CHARS) -> {
                    relations.add(RelationSel(RelOp.ANCESTOR, PolynomialExpression(a = 1, b = 0)))
                    propertySelectors.add(propertyParser.readPropertySelector())
                }
                else -> {
                    cursor.index = whitespaceStart
                    break
                }
            }
        }
        return UnitSelectorExpression(propertySelectors, relations)
    }
}
