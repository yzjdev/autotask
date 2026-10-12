package com.yzjdev.autotask.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** 节点快照(调试悬浮窗展示用) */
internal class NodeSnapshot(
    val rect: Rect,
    val clickable: Boolean,
    val info: String,                     // 摘要(一行)
    val table: Map<String, String>,       // 详细信息(表格:属性名 → 值)
    val parent: Int,                      // 父节点下标,-1 为根
    val children: List<Int>,
    val node: AccessibilityNodeInfo? = null,  // 活节点引用,按索引定位动作目标用
    val visible: Boolean = true,          // 是否对用户可见;不可见节点也收录,供界面区分展示
)

/**
 * 节点快照收集:深度优先遍历「焦点且激活」的窗口。
 * 调试悬浮窗的 NODE 编号出自此处,顺序必须唯一。
 */
internal object NodeSnapshotCollector {

    private const val MAX_NODES = 300       // 快照最多收集的节点数

    /** 收集快照;window 非空时只遍历该窗口(窗口列表点选某窗口时用),否则取焦点激活窗口 */
    fun collect(
        service: AccessibilityService,
        window: AccessibilityWindowInfo? = null,
    ): List<NodeSnapshot> = collectInner(service, window)

    /**
     * 取目标窗口根节点的整棵子树:显式指定 window 时以其为准,否则取「焦点且激活」的窗口(优先应用窗口)。
     * 不合并其他窗口:悬浮窗/输入法等非焦点窗口不属于当前页面。
     * 不可见节点也收录并标记,但只有可见节点会展开子树。
     * 窗口列表不可用时退回活动窗口根节点。
     */
    private fun collectInner(
        service: AccessibilityService,
        window: AccessibilityWindowInfo? = null,
    ): List<NodeSnapshot> {
        val out = ArrayList<NodeSnapshot>()
        val win = window ?: (service as? AutoTaskAccessibilityService)?.focusedWindow()
        val root = win?.root ?: service.rootInActiveWindow ?: return out
        traverse(root, -1, out)
        return out
    }

    /**
     * 深度优先快照,收集边界/摘要/父子关系。
     * 不可见节点也收录(标记 visible=false,供界面区分),但不展开其子树 ——
     * 不可见容器(离屏页面、被回收的列表项)子树往往很大,展开会把节点预算吃光。
     */
    private fun traverse(
        node: AccessibilityNodeInfo,
        parent: Int,
        out: MutableList<NodeSnapshot>,
    ): Int {
        if (out.size >= MAX_NODES) return -1
        val visible = node.isVisibleToUser
        val index = out.size
        out.add(snapshotOf(node, visible))
        if (!visible) return index
        val children = ArrayList<Int>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val ci = traverse(child, index, out)
            if (ci >= 0) children.add(ci)
        }
        // 回填父子关系(snapshotOf 不感知树结构,parent/children 在此统一填入)
        val s = out[index]
        out[index] = NodeSnapshot(
            s.rect, s.clickable, s.info, s.table, parent, children, s.node, s.visible,
        )
        return index
    }

    private fun snapshotOf(node: AccessibilityNodeInfo, visible: Boolean): NodeSnapshot {
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
        val viewId = node.viewIdResourceName
        val table = linkedMapOf(
            "类名" to (node.className?.toString() ?: "?"),
            "viewId" to (viewId ?: "无"),
            // vid:只取资源名(去掉 pkg:id/ 前缀),如 com.tencent.mobileqq:id/title → title
            "vid" to (viewId?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: "无"),
            "文本" to (text ?: "无"),
            "描述" to (desc ?: "无"),
            "边界" to "${r.width()}x${r.height()} @(${r.left},${r.top})",
            "可点击" to if (node.isClickable) "是" else "否",
            "可滚动" to if (node.isScrollable) "是" else "否",
            "可勾选" to if (node.isCheckable) "是" else "否",
            "可见" to if (visible) "是" else "否",
            "子节点" to node.childCount.toString(),
        )
        return NodeSnapshot(r, node.isClickable, summary, table, -1, emptyList(), node, visible)
    }
}
