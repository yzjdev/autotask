package com.yzjdev.autotask.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.IdentityHashMap

/** 节点快照(悬浮窗展示与规则按索引执行共用同一结构) */
internal class NodeSnapshot(
    val rect: Rect,
    val clickable: Boolean,
    val info: String,                     // 摘要(一行)
    val table: Map<String, String>,       // 详细信息(表格:属性名 → 值)
    val parent: Int,                      // 父节点下标,-1 为根
    val children: List<Int>,
    val node: AccessibilityNodeInfo? = null,  // 活节点引用,按索引定位动作目标用
)

/**
 * 节点快照收集:深度优先遍历当前可交互窗口。
 * 节点悬浮窗的 NODE 编号与规则 targetIndex 均出自此处,顺序必须唯一。
 */
internal object NodeSnapshotCollector {

    private const val MAX_NODES = 300       // 快照最多收集的节点数

    /** 最近一次快照的 node → 下标(identity,供选择器 nodeIndex 属性查询),与悬浮窗 NODE 编号同源 */
    private var lastIndexOf: Map<AccessibilityNodeInfo, Int> = emptyMap()
    private var lastAt = 0L
    private var refreshing = false

    /** 选择器 nodeIndex 属性:节点在最近快照中的下标(1 基);过期 300ms 自动重抓,未收录返回 0 */
    fun snapshotIndexOf(node: AccessibilityNodeInfo): Int {
        if (!refreshing && System.currentTimeMillis() - lastAt > 300) refresh()
        return (lastIndexOf[node] ?: -1) + 1
    }

    /** 重新收集快照并刷新索引表 */
    fun refresh(service: AccessibilityService? = null) {
        if (refreshing) return
        refreshing = true
        try {
            val svc = service ?: DramaAccessibilityService.instance ?: return
            val list = collectInner(svc)
            val map = IdentityHashMap<AccessibilityNodeInfo, Int>()
            list.forEachIndexed { i, s -> s.node?.let { map[it] = i } }
            lastIndexOf = map
            lastAt = System.currentTimeMillis()
        } finally {
            refreshing = false
        }
    }

    fun collect(service: AccessibilityService): List<NodeSnapshot> {
        refresh(service)
        return collectInner(service).also { list ->
            val map = IdentityHashMap<AccessibilityNodeInfo, Int>()
            list.forEachIndexed { i, s -> s.node?.let { map[it] = i } }
            lastIndexOf = map
            lastAt = System.currentTimeMillis()
        }
    }

    private fun collectInner(service: AccessibilityService): List<NodeSnapshot> {
        val out = ArrayList<NodeSnapshot>()
        val root = service.rootInActiveWindow ?: return out
        traverse(service, root, -1, out)
        // 悬浮窗(TYPE_APPLICATION_OVERLAY 等)不在 rootInActiveWindow 内,
        // 遍历所有可交互窗口,把除活动窗口之外的根节点也并入快照;
        // 本应用自己的悬浮窗按包名跳过,避免把球和卡片自己框出来
        for (w in service.windows) {
            val wr = w.root ?: continue
            if (wr.packageName == service.packageName) continue
            if (wr.packageName == root.packageName && w.type ==
                android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
            traverse(service, wr, -1, out)
        }
        return out
    }

    /** 深度优先快照,收集边界/摘要/父子关系(忽略状态栏区域与不可见节点) */
    private fun traverse(
        service: AccessibilityService,
        node: AccessibilityNodeInfo,
        parent: Int,
        out: MutableList<NodeSnapshot>,
    ): Int {
        if (out.size >= MAX_NODES) return -1
        // 整体位于状态栏内的节点不收录
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.bottom <= statusBarHeight(service)) return -1
        // 不可见节点及其子树不收录
        if (!node.isVisibleToUser) return -1
        val index = out.size
        out.add(snapshotOf(node))
        val children = ArrayList<Int>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val ci = traverse(service, child, index, out)
            if (ci >= 0) children.add(ci)
        }
        // 回填父子关系(snapshotOf 不感知树结构,parent/children 在此统一填入)
        val s = out[index]
        out[index] = NodeSnapshot(s.rect, s.clickable, s.info, s.table, parent, children, s.node)
        return index
    }

    private fun snapshotOf(node: AccessibilityNodeInfo): NodeSnapshot {
        val r = Rect()
        node.getBoundsInScreen(r)
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val desc = node.contentDescription?.toString()
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val id = node.viewIdResourceName?.substringAfter('/')?.let { "#$it" } ?: ""
        val summary = buildString {
            append(cls)
            append(id)
            if (!text.isNullOrBlank()) append(" \"$text\"")
            if (!desc.isNullOrBlank()) append(" desc:\"$desc\"")
            if (node.isClickable) append(" [可点击]")
        }
        // 表格数据:属性名 → 值
        val table = linkedMapOf(
            "类名" to (node.className?.toString() ?: "?"),
            "viewId" to (node.viewIdResourceName ?: "无"),
            "文本" to (text ?: "无"),
            "描述" to (desc ?: "无"),
            "边界" to "${r.width()}x${r.height()} @(${r.left},${r.top})",
            "可点击" to if (node.isClickable) "是" else "否",
            "可滚动" to if (node.isScrollable) "是" else "否",
            "可勾选" to if (node.isCheckable) "是" else "否",
            "子节点" to node.childCount.toString(),
        )
        return NodeSnapshot(r, node.isClickable, summary, table, -1, emptyList(), node)
    }

    /** 状态栏高度(节点边界为屏幕坐标,以此过滤状态栏区域) */
    private fun statusBarHeight(service: AccessibilityService): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else service.dp(24)
    }

    private fun AccessibilityService.dp(v: Int): Int = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}
