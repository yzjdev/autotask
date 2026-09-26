package com.example.composedemo.automation

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 无障碍节点检查悬浮窗(选中模式):
 *  - 服务连接后只显示一个可拖动的小面板,「节点」按钮开关抓取
 *  - 点击「节点」:快照当前窗口节点树,绘制全部节点边界框,并选中根节点
 *  - 选中节点:屏幕上高亮其边框,信息卡片居中显示该节点(而非全部)
 *  - 方向按钮移动选中:上=父节点,下=第一个子节点,左=前一兄弟,右=后一兄弟
 *  - 除控制面板外均为 FLAG_NOT_TOUCHABLE,事件穿透
 *  - TYPE_ACCESSIBILITY_OVERLAY:无需悬浮窗权限
 */
class OverlayDebugWindow(private val service: AccessibilityService) {

    companion object {
        private const val MAX_NODES = 300       // 快照最多收集的节点数
        private const val MAX_INFO_LINES = 24   // 信息卡片最多行数
    }

    // 服务构造时 context 尚未 attach,所有 WindowManager/Resources 相关一律懒初始化
    private val wm by lazy { service.getSystemService(WindowManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    // 开关悬浮球:独立 UI,点击回调切换抓取状态
    private val toggleWindow = OverlayToggleWindow(service) { toggleCapture() }

    private var cardView: LinearLayout? = null
    private var titleView: TextView? = null
    private var infoView: LinearLayout? = null
    private var dirRow: LinearLayout? = null
    private var boundsView: BoundsOverlayView? = null

    /** 信息卡片:标题栏(应用名+关闭)+ 详情 + 方向按钮,可交互(中层层级) */
    private val cardLp by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
    }

    /** 边框绘制层:显式用整块显示器尺寸(含状态栏),可点击命中(底层层级) */
    private val boundsLp by lazy {
        // MATCH_PARENT 会被解析为「应用可用区域」(排除状态栏),导致绘制范围够不到状态栏;
        // 显式设为整个 display 的像素尺寸 + NO_LIMITS 才能真正铺满全屏
        val dm = service.resources.displayMetrics
        WindowManager.LayoutParams(
            dm.widthPixels,
            dm.heightPixels,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN   // 原点=屏幕(0,0),坐标含状态栏
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,  // 允许超出常规窗口边界
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    /** 节点信息抓取开关状态 */
    @Volatile
    var captureEnabled = false
        private set

    /** 抓取时的节点快照(节点树会失效,必须立即转为数据) */
    private class NodeSnapshot(
        val rect: Rect,
        val clickable: Boolean,
        val info: String,                     // 摘要(一行)
        val table: Map<String, String>,       // 详细信息(表格:属性名 → 值)
        val parent: Int,                      // 父节点下标,-1 为根
        val children: List<Int>,
    )

    private var snapshots: List<NodeSnapshot> = emptyList()
    private var selectedIndex = -1

    /** 最近一次抓取的应用名(卡片被 ✕ 关闭后重建标题栏用) */
    private var lastAppName = ""

    // ---- 控制面板(开关悬浮球,独立 UI)----

    /**
     * 开启开关:一次性依次添加三个窗口(绘制层 → 信息卡片 → 悬浮球),
     * 同层级窗口按添加顺序叠放,后加的在上:绘制层在底,悬浮球在最上,
     * 天态层级即正确,无需 bringToFront 补救。
     * 卡片与绘制层初始 GONE,后续只切 visibility,不再动态 addView/removeView,
     * 避免抓取/选中时窗口反复重建导致的闪烁与层级跳动。
     */
    fun show() {
        mainHandler.post {
            ensureBoundsView()
            boundsView?.visibility = View.GONE
            ensureInfoCard(lastAppName)
            cardView?.visibility = View.GONE
            // 悬浮球最后创建,天然位于最上层
            toggleWindow.show()
        }
    }

    fun hide() {
        mainHandler.post {
            removeCaptureViews()
            toggleWindow.hide()
            captureEnabled = false
            toggleWindow.setActive(false)
        }
    }

    val isShowing: Boolean
        get() = toggleWindow.isShowing

    // ---- 抓取 ----

    /**
     * 点击悬浮球:切换节点边界绘制层的显隐。
     * 开启 → 抓取节点树并显示绘制层(信息卡片保持隐藏,点击节点才显示);
     * 关闭 → 隐藏绘制层与信息卡片。窗口常驻,只切 visibility,不重建窗口。
     */
    private fun toggleCapture() {
        captureEnabled = !captureEnabled
        toggleWindow.setActive(captureEnabled)
        if (captureEnabled) {
            captureAndShow()
        } else {
            hideCaptureLayers()
        }
    }

    /** 隐藏绘制层与信息卡片(窗口保留,置 GONE + NOT_TOUCHABLE,事件穿透) */
    private fun hideCaptureLayers() {
        setLayerVisible(cardLp, cardView, false)
        setLayerVisible(boundsLp, boundsView, false)
        snapshots = emptyList()
        selectedIndex = -1
    }

    /**
     * 切换常驻窗口的显隐:置 visibility 的同时同步窗口 FLAG_NOT_TOUCHABLE——
     * 隐藏的全屏窗口即使内容 GONE,窗口本身仍会拦截触摸,必须加 NOT_TOUCHABLE 才能穿透。
     */
    private fun setLayerVisible(lp: WindowManager.LayoutParams, view: View?, visible: Boolean) {
        view ?: return
        view.visibility = if (visible) View.VISIBLE else View.GONE
        lp.flags = if (visible) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
    }

    /** 仅隐藏节点信息卡片;边框绘制保留,可继续点击边框查看节点 */
    private fun closeInfoCard() {
        setLayerVisible(cardLp, cardView, false)
        boundsView?.setSelected(-1)
    }

    /** 抓取当前窗口节点树:绘制边界框 + 选中根节点 */
    private fun captureAndShow() {
        try {
            val list = ArrayList<NodeSnapshot>()
            val root = service.rootInActiveWindow ?: run {
                snapshots = emptyList()
                selectedIndex = -1
                return
            }
            // 标题:立即显示(不等待查询)——先取事件缓存的 Activity,没有则用窗口包名,
            // 永不出现 Unknown/空白;随后后台线程反射查询真实前台 Activity,查到即回填替换
            captureGeneration++
            val cached = (service as? com.example.composedemo.automation.DramaAccessibilityService)?.lastActivity
            val title = cached?.let { "${it.packageName}/${it.shortClassName}" }
                ?: root.packageName?.toString()
                ?: "Unknown"
            lastAppName = title
            traverseSnapshot(root, -1, list)
            // 悬浮窗(TYPE_APPLICATION_OVERLAY 等)不在 rootInActiveWindow 内,
            // 遍历所有可交互窗口,把除活动窗口之外的根节点也并入快照
            for (w in service.windows) {
                val wr = w.root ?: continue
                if (wr.packageName == root.packageName && w.type ==
                    android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
                traverseSnapshot(wr, -1, list)
            }
            snapshots = list
            ensureBoundsView()          // 绘制层窗口已在 show() 时创建,这里仅兜底
            boundsView?.setSnapshots(list)
            setLayerVisible(boundsLp, boundsView, true)   // 显示绘制层
            setLayerVisible(cardLp, cardView, false)      // 信息卡片保持隐藏
            selectedIndex = 0           // 根节点仅在内部选中,不弹卡片

            // 后台查询(反射 binder 调用不能在主线程),完成后主线程替换标题
            val generation = captureGeneration
            Thread {
                val top = runCatching { queryTopActivityViaReflection() }.getOrNull() ?: return@Thread
                mainHandler.post {
                    // 只替换本次抓取的标题;期间用户重新抓取过(generation 变化)则丢弃
                    if (generation == captureGeneration && captureEnabled) {
                        val newTitle = "${top.packageName}/${top.shortClassName}"
                        if (newTitle != lastAppName) {
                            titleView?.text = newTitle
                            lastAppName = newTitle
                        }
                    }
                }
            }.start()
        } catch (_: Exception) {
            // 窗口可能已被系统回收,静默忽略
        }
    }

    /** 抓取代数:每次 captureAndShow 递增,用于丢弃过期的后台查询结果 */
    @Volatile
    private var captureGeneration = 0

    /** 已应用的 hidden API 豁免("L" 前缀覆盖全部类),进程级一次即可 */
    private val hiddenApiReady: Boolean by lazy {
        runCatching {
            org.lsposed.hiddenapibypass.HiddenApiBypass.setHiddenApiExemptions("L")
        }.isSuccess
    }

    /**
     * 反射系统 ActivityTaskManager binder 获取前台 Activity,不依赖无障碍事件与 Shizuku。
     * 链路:ServiceManager.getService("activity_task") → IActivityTaskManager.Stub.asInterface
     * → getTasks(1) → RunningTaskInfo.topActivity。
     * 先用 HiddenApiBypass 豁免 hidden API 限制(logcat 显示 asInterface 被 blocked);
     * 需在非主线程调用(binder 调用);任何 ROM 差异/受限场景返回 null,由调用方兜底。
     */
    private fun queryTopActivityViaReflection(): android.content.ComponentName? {
        if (!hiddenApiReady) return null
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "activity_task") ?: return null

        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
        val taskManager = asInterface.invoke(null, binder)

        val getTasks = taskManager.javaClass.methods.firstOrNull {
            it.name == "getTasks" && it.parameterTypes.size == 1
        } ?: return null
        @Suppress("UNCHECKED_CAST")
        val tasks = getTasks.invoke(taskManager, 1) as? List<*>
        return (tasks?.firstOrNull() as? android.app.ActivityManager.RunningTaskInfo)
            ?.topActivity
    }

    /** 深度优先快照,收集边界/摘要/父子关系(忽略状态栏区域) */
    private fun traverseSnapshot(
        node: AccessibilityNodeInfo,
        parent: Int,
        out: MutableList<NodeSnapshot>,
    ): Int {
        if (out.size >= MAX_NODES) return -1
        // 整体位于状态栏内的节点不收录(不绘制、不可命中)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.bottom <= statusBarHeight()) return -1
        // 不可见节点及其子树不收录(不绘制、不可命中)
        if (!node.isVisibleToUser) return -1
        val index = out.size
        out.add(snapshotOf(node))
        val children = ArrayList<Int>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val ci = traverseSnapshot(child, index, out)
            if (ci >= 0) children.add(ci)
        }
        // 回填父子关系(snapshotOf 不感知树结构,parent/children 在此统一填入)
        val s = out[index]
        out[index] = NodeSnapshot(s.rect, s.clickable, s.info, s.table, parent, children)
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
        return NodeSnapshot(r, node.isClickable, summary, table, -1, emptyList())
    }

    /**
     * 创建绘制层(边界框)。三个窗口在 show() 时已按 球→卡片→绘制层 顺序创建,
     * 层级固定:绘制层在底层,信息卡片、悬浮球依次叠加其上;这里仅兜底创建。
     */
    private fun ensureBoundsView() {
        if (boundsView != null) return
        val bv = BoundsOverlayView(service)
        bv.onNodeHit = { index ->
            ensureInfoCard(lastAppName)  // 卡片被 ✕ 隐藏后点边框重新显示
            select(index)
        }
        boundsView = bv
        wm.addView(bv, boundsLp)
        // 悬浮球永远置顶:同层级窗口按添加顺序叠放,重加悬浮球保证最上层
        toggleWindow.bringToFront()
    }

    /** 创建节点信息卡片(标题栏 + 属性表格 + 方向按钮),置于绘制层之上 */
    private fun ensureInfoCard(appName: String) {
        if (cardView != null) return
        val title = TextView(service).apply {
            text = appName
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        titleView = title

        val close = circleButton("✕") { closeInfoCard() }  // 只关闭节点信息卡片,绘制保留

        val titleBar = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC333333.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            addView(title, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(close)
        }

        // 属性表格容器:每行 = 属性名列 + 值列,选中节点时重建
        val info = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        infoView = info

        // 四个方向按钮:固定尺寸 + 固定间距,整行居中,不铺满卡片宽度
        val dirs = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC333333.toInt())
            setPadding(dp(6), dp(4), dp(6), dp(4))
            gravity = Gravity.CENTER
        }
        dirRow = dirs
        listOf("↑" to { moveUp() }, "↓" to { moveDown() },
            "←" to { moveLeft() }, "→" to { moveRight() }).forEachIndexed { i, (label, action) ->
            if (i > 0) dirs.addView(View(service), LinearLayout.LayoutParams(dp(10), 1))
            dirs.addView(circleButton(label) { action() })
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC1E1E1E.toInt())
            addView(titleBar)
            addView(info)
            addView(dirs)
        }
        cardView = card
        wm.addView(card, cardLp)
        // 悬浮球永远置顶:信息卡片创建后把悬浮球重新加回最上层
        toggleWindow.bringToFront()
    }

    private fun removeCaptureViews() {
        boundsView?.let {
            it.onNodeHit = null
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        boundsView = null
        cardView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        cardView = null
        titleView = null
        infoView = null
        dirRow = null
        snapshots = emptyList()
        selectedIndex = -1
    }

    // ---- 选中与方向移动 ----

    private fun select(index: Int) {
        if (index < 0 || index >= snapshots.size) return
        selectedIndex = index
        val s = snapshots[index]
        boundsView?.setSelected(index)
        ensureInfoCard(lastAppName)
        setLayerVisible(cardLp, cardView, true)   // 选中节点即显示卡片
        renderInfoTable(index, s)
    }

    /** 把节点属性渲染为两列表格:每行 = 属性名(灰) + 值(白) */
    private fun renderInfoTable(index: Int, s: NodeSnapshot) {
        val container = infoView ?: return
        container.removeAllViews()

        // 表头:序号 + 一行摘要
        container.addView(TextView(service).apply {
            text = "[$index/${snapshots.size - 1}] ${s.info.take(80)}"
            setTextColor(0xFF00E5FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, 0, 0, dp(4))
        })

        for ((name, value) in s.table) {
            val row = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(20)
                isClickable = true
                isLongClickable = true
                // 长按任意一行复制「属性名: 值」到剪贴板
                setOnLongClickListener {
                    copyToClipboard("$name: $value")
                    true
                }
            }
            row.addView(TextView(service).apply {
                text = name
                setTextColor(0xFF9E9E9E.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                layoutParams = LinearLayout.LayoutParams(dp(52),
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(service).apply {
                text = value
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            container.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(1), 0, dp(1))
            })
        }
    }

    /** 复制文本到剪贴板并提示 */
    private fun copyToClipboard(text: String) {
        val cm = service.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("node", text))
        android.widget.Toast.makeText(service, "已复制: $text", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 上:父节点 */
    private fun moveUp() {
        snapshots.getOrNull(selectedIndex)?.parent?.let { select(it) }
    }

    /** 下:第一个子节点 */
    private fun moveDown() {
        snapshots.getOrNull(selectedIndex)?.children?.firstOrNull()?.let { select(it) }
    }

    /** 左:前一兄弟 */
    private fun moveLeft() {
        val cur = snapshots.getOrNull(selectedIndex) ?: return
        val siblings = cur.parent.takeIf { it >= 0 }
            ?.let { snapshots[it].children } ?: return
        val pos = siblings.indexOf(selectedIndex)
        if (pos > 0) select(siblings[pos - 1])
    }

    /** 右:后一兄弟 */
    private fun moveRight() {
        val cur = snapshots.getOrNull(selectedIndex) ?: return
        val siblings = cur.parent.takeIf { it >= 0 }
            ?.let { snapshots[it].children } ?: return
        val pos = siblings.indexOf(selectedIndex)
        if (pos < siblings.size - 1) select(siblings[pos + 1])
    }

    // ---- 边框绘制层(全屏,抓取开启时可点击命中节点)----

    @SuppressLint("ClickableViewAccessibility")
    private class BoundsOverlayView(context: android.content.Context) : View(context) {

        /** 命中节点时回调(由外部注入,避免持有外部引用导致泄漏) */
        var onNodeHit: ((Int) -> Unit)? = null

        private var snapshots: List<NodeSnapshot>? = null
        private var selected = -1

        private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x66FF4081.toInt()
        }
        private val clickablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x4400E5FF.toInt()
        }
        private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = 0xFFFFEB3B.toInt()
        }
        private val selectedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x33FFEB3B.toInt()
        }

        fun setSnapshots(list: List<NodeSnapshot>) {
            snapshots = list
            invalidate()
        }

        fun setSelected(index: Int) {
            selected = index
            invalidate()
        }

        /**
         * 点击命中:选包含触点、面积最小(即树中最深/最内层)的节点。
         * 返回 true 表示消费了事件。
         */
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val list = snapshots ?: return false
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
            val x = event.x.toInt()
            val y = event.y.toInt()
            var best = -1
            var bestArea = Int.MAX_VALUE
            for ((i, s) in list.withIndex()) {
                val r = s.rect
                if (r.width() <= 0 || r.height() <= 0) continue
                if (r.contains(x, y)) {
                    val area = r.width() * r.height()
                    if (area < bestArea) {   // 面积最小 = 树中最深
                        bestArea = area
                        best = i
                    }
                }
            }
            if (best >= 0) {
                onNodeHit?.invoke(best)
                return true
            }
            return false  // 点到空白处不消费,事件落到下层 App
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val list = snapshots ?: return
            for ((i, s) in list.withIndex()) {
                val r = s.rect
                if (r.width() <= 0 || r.height() <= 0) continue
                if (i == selected) continue  // 选中框最后画,压在最上层
                canvas.drawRect(r, if (s.clickable) clickablePaint else normalPaint)
            }
            if (selected in list.indices) {
                val r = list[selected].rect
                if (r.width() > 0 && r.height() > 0) {
                    canvas.drawRect(r, selectedFill)
                    canvas.drawRect(r, selectedPaint)
                }
            }
        }
    }

    /** 圆形图标按钮(与悬浮球同风格):固定宽高防挤压变形,半透明圆底 + 白色图标 */
    private fun circleButton(icon: String, onClick: () -> Unit): TextView =
        TextView(service).apply {
            text = icon
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x66FFFFFF.toInt())  // 半透明白圆底,叠在深色卡片上
            }
            // 固定 28dp 圆,不随布局伸缩
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener { onClick() }
        }

    /** 状态栏高度(节点边界为屏幕坐标,以此过滤状态栏区域) */
    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics
    ).toInt()

    private val screenW: Int get() = service.resources.displayMetrics.widthPixels
    private val screenH: Int get() = service.resources.displayMetrics.heightPixels

    private fun clamp(v: Int, min: Int, max: Int): Int = v.coerceIn(min, max.coerceAtLeast(min))
}
