package com.yzjdev.autotask.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍节点树的干净遍历,供规则引擎(AAR / AATR)共用。
 *
 * 刻意不走服务上的 `findFirst`/`traverseNodes`:那条路径命中即 `recycle()`,
 * 且去重集合 `processedNodes` 只在服务连接时清空 —— 规则要在一次运行里反复查找,
 * 复用它会第二次就查不到东西。这里不回收、不去重,并覆盖全部窗口(活动窗口优先)。
 */
object AccessibilityTreeWalker {

    /**
     * 当前所有可检索窗口的根节点,活动窗口排在最前。
     * 本应用自己的窗口(无障碍悬浮窗)会被跳过:规则不该点到引擎自己的界面上。
     */
    fun roots(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val selfPackage = runCatching { service.packageName }.getOrNull()

        val active = runCatching { service.rootInActiveWindow }.getOrNull()
        if (active != null && !isSelf(active, selfPackage)) out.add(active)

        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        for (window in windows) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (isSelf(root, selfPackage)) continue
            if (out.any { it === root }) continue
            out.add(root)
        }
        return out
    }

    /** 深度优先前序遍历全部窗口;[onNode] 返回 true 停止整轮遍历 */
    fun forEach(
        service: AccessibilityService,
        onNode: (node: AccessibilityNodeInfo, depth: Int) -> Boolean,
    ) {
        for (root in roots(service)) {
            if (walk(root, 0, onNode)) return
        }
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        onNode: (AccessibilityNodeInfo, Int) -> Boolean,
    ): Boolean {
        if (onNode(node, depth)) return true
        val count = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until count) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (walk(child, depth + 1, onNode)) return true
        }
        return false
    }

    private fun isSelf(node: AccessibilityNodeInfo, selfPackage: String?): Boolean {
        if (selfPackage == null) return false
        return runCatching { node.packageName?.toString() }.getOrNull() == selfPackage
    }
}
