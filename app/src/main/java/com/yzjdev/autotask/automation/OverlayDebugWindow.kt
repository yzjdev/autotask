package com.yzjdev.autotask.automation

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
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
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
        private const val MAX_INFO_LINES = 24   // 信息卡片最多行数
    }

    // 服务构造时 context 尚未 attach,所有 WindowManager/Resources 相关一律懒初始化
    private val wm by lazy { service.getSystemService(WindowManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    // 开关悬浮球:独立 UI,点击回调切换抓取状态,长按关闭整个悬浮窗
    private val toggleWindow = OverlayToggleWindow(service, { toggleCapture() }, { hide() })

    private var cardView: LinearLayout? = null
    private var titleView: TextView? = null
    private var activityView: TextView? = null
    private var countView: TextView? = null
    private var infoView: LinearLayout? = null
    private var dirRow: LinearLayout? = null
    private var dirButtons: MutableList<TextView>? = null
    private var scrollRegion: ScrollView? = null
    private var boundsView: BoundsOverlayView? = null
    private var treeDrawer: LinearLayout? = null
    private var treeListView: LinearLayout? = null
    private var treeScroll: ScrollView? = null
    private var treeShown = false
    private val maxScrollHeight: Int get() =
        (screenH * 0.35f).toInt().coerceAtLeast(dp(80))  // 表格区高度封顶(约 1/3 屏)

    /** 信息卡片:标题栏 + 详情 + 方向按钮,可交互(中层层级)。固定宽高,不随内容伸缩 */
    private val cardLp by lazy {
        WindowManager.LayoutParams(
            dp(320),
            dp(380),
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

    /** 节点树抽屉:底部弹出的独立窗口,树形列表 + 点击定位(与信息卡片同级层级)。全屏宽 */
    private val treeLp by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (screenH * 0.6f).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
    }

    /** 节点信息抓取开关状态 */
    @Volatile
    var captureEnabled = false
        private set

    /** 抓取时的节点快照(节点树会失效,必须立即转为数据) */
    private var snapshots: List<NodeSnapshot> = emptyList()
    private var selectedIndex = -1

    /** 最近一次抓取的应用名(卡片被 ✕ 关闭后重建标题栏用) */
    private var lastAppName = ""

    /** 本次抓取的窗口根包名:标题只接受与之一致的 Activity 来源,防桌面/系统窗口污染 */
    @Volatile
    private var capturePkg: String? = null

    /**
     * 订阅服务层的 lastActivity:每次前台 Activity 切换(经 getActivityInfo 反查确认)
     * 即同步更新悬浮窗标题,无需用户重新抓取节点。
     */
    private val activityListener: (android.content.ComponentName) -> Unit = { cn ->
        mainHandler.post {
            // 只跟随与当前抓取窗口同包名的 Activity;桌面/Launcher 是合法 Activity,
            // 但若当前抓的不是桌面,不能让标题跳回桌面
            if (capturePkg == null || cn.packageName == capturePkg) applyHeader(cn)
        }
    }

    /** 标题格式:应用名(包名/Activity 短类名) */
    private fun formatTitle(cn: android.content.ComponentName): String =
        "${appLabel(cn.packageName ?: "")}(${cn.packageName}/${cn.shortClassName})"

    /** 头部两行:第一行应用名,第二行单独显示 Activity 全名 */
    private fun applyHeader(cn: android.content.ComponentName) {
        lastAppName = formatTitle(cn)
        titleView?.text = appLabel(cn.packageName ?: "")
        activityView?.text = "${cn.packageName}/${cn.shortClassName}"
    }

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
            // 订阅前台 Activity 切换,标题实时跟随(悬浮窗显示期间)
            (service as? com.yzjdev.autotask.automation.DramaAccessibilityService)
                ?.addActivityListener(activityListener)
            // 悬浮球最后创建,天然位于最上层
            toggleWindow.show()
        }
    }

    fun hide() {
        mainHandler.post {
            (service as? com.yzjdev.autotask.automation.DramaAccessibilityService)
                ?.removeActivityListener(activityListener)
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
        hideTreeDrawer()
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
        hideTreeDrawer()
        boundsView?.setSelected(-1)
    }

    // ---- 节点树抽屉 ----

    /** 抽屉显隐切换:展开时隐藏节点信息卡片(互不遮挡),收起时恢复 */
    private fun toggleTreeDrawer() {
        if (treeShown) {
            hideTreeDrawer()
            // 收起抽屉后恢复信息卡片(仅当仍有选中节点)
            if (selectedIndex >= 0) setLayerVisible(cardLp, cardView, true)
        } else {
            ensureTreeDrawer()
            renderTreeView()
            setLayerVisible(treeLp, treeDrawer, true)
            setLayerVisible(cardLp, cardView, false)  // 抽屉展开时隐藏节点信息卡片
            treeShown = true
            toggleWindow.bringToFront()
        }
    }

    private fun hideTreeDrawer() {
        setLayerVisible(treeLp, treeDrawer, false)
        treeShown = false
    }

    /** 创建抽屉窗口:标题栏(节点数 + 收起钮) + 树形列表 ScrollView */
    private fun ensureTreeDrawer() {
        if (treeDrawer != null) return
        val title = TextView(service).apply {
            text = "节点树"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val collapse = TextView(service).apply {
            text = "▾ 收起"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xCCF59E0B.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { hideTreeDrawer() }
        }
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(collapse)
        }
        val list = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }
        treeListView = list
        // 横向滚动:深层级行向右延展,左右滑动查看
        val hScroll = HorizontalScrollView(service).apply {
            isHorizontalScrollBarEnabled = true
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        treeScroll = ScrollView(service).apply {
            isVerticalScrollBarEnabled = true
            addView(hScroll)
        }
        val drawer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            // 背景色不变;顶部 1dp 琥珀线用 FrameLayout 叠加实现(避免描边在贴边三侧画线)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dp(14).toFloat(), dp(14).toFloat(),  // 左上
                    dp(14).toFloat(), dp(14).toFloat(),  // 右上
                    0f, 0f,                               // 右下
                    0f, 0f,                               // 左下
                )
                setColor(0xF51A1206.toInt())
            }
            background = bg
            addView(header)
            addView(treeScroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            // 顶部琥珀线:2dp 高的圆角矩形,叠在 header 下沿
            val topLine = View(service).apply {
                background = android.graphics.drawable.ColorDrawable(0x40F59E0B.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            }
            addView(topLine, 1)  // 插在 header 之后、列表之前
        }
        treeDrawer = drawer
        wm.addView(drawer, treeLp)
        toggleWindow.bringToFront()
    }

    /** 渲染节点树:DFS 缩进列表,每行 = 缩进 + 类名 + 摘要,点击定位该节点。
     * 行内容完整不省略(深层级向右延展),整列套双向滚动(纵向 + 横向)。 */
    private fun renderTreeView() {
        val container = treeListView ?: return
        container.removeAllViews()
        if (snapshots.isEmpty()) return
        // 找根节点们(parent = -1),逐个 DFS 输出
        fun dfs(idx: Int, depth: Int) {
            if (depth > 20) return  // 防环
            val s = snapshots.getOrNull(idx) ?: return
            container.addView(treeRow(idx, s, depth))
            s.children.forEach { dfs(it, depth + 1) }
        }
        snapshots.indices.filter { snapshots[it].parent == -1 }.forEach { dfs(it, 0) }
    }

    /** 树行双击检测(抽屉级共享一个 GestureDetector,记录当前触控行,避免每行各建一个) */
    private var treeTapIndex = -1
    private val treeTapDetector by lazy {
        android.view.GestureDetector(service,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (treeTapIndex >= 0) select(treeTapIndex)
                    return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (treeTapIndex >= 0) {
                        select(treeTapIndex)
                        toggleTreeDrawer()
                    }
                    return true
                }
            })
    }

    /** 节点树单行:缩进(空格) + 类名(#id/"文本") + 可点击标记,选中行高亮。
     * 行不省略、不限定宽度,超宽由外层横向 ScrollView 滑动查看。 */
    private fun treeRow(idx: Int, s: NodeSnapshot, depth: Int): View {
        val selected = idx == selectedIndex
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(26)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            isClickable = true
            setOnTouchListener { _, ev ->
                treeTapIndex = idx
                treeTapDetector.onTouchEvent(ev)
            }
            background = if (selected) android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(0x33F59E0B.toInt())
            } else null
        }
        row.addView(TextView(service).apply {
            text = if (s.children.isNotEmpty()) "▾" else "·"
            setTextColor(if (s.children.isNotEmpty()) 0x80F59E0B.toInt() else 0x40FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            layoutParams = LinearLayout.LayoutParams(dp(10),
                LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(service).apply {
            // 缩进用等宽空格,行宽随深度自然延展,不参与省略
            val indent = "  ".repeat(depth)
            text = buildString {
                append(indent)
                append(s.info)
                if (s.clickable) append(" ⬑")
            }
            setTextColor(if (selected) 0xFFF59E0B.toInt()
                else if (s.clickable) 0xCCFFFFFF.toInt() else 0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = android.graphics.Typeface.MONOSPACE
            maxLines = 1
            // 不 ellipsize:完整内容,由横向滚动承接
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(4)
            }
        })
        return row
    }

    /** 抓取当前窗口节点树:绘制边界框 + 选中根节点 */
    private fun captureAndShow() {
        try {
            val list = NodeSnapshotCollector.collect(service)
            // GKD 用 rootInActiveWindow.packageName 做 appId,但抓取瞬间该值可能已指向
            // 桌面/系统窗口;无 Shizuku 校验,改用「焦点应用窗口」做基准(更接近真实前台)
            val anchor = service.windows.firstOrNull {
                it.isFocused && it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
            }?.root ?: service.rootInActiveWindow
            val root = anchor ?: run {
                snapshots = emptyList()
                selectedIndex = -1
                return
            }
            val appId = root.packageName?.toString() ?: "Unknown"
            // GKD resolveActivityId 语义:appId 以无障碍根节点包名为准,
            // 标题先显示,后台轮询到「系统侧前台包名 == 根节点包名」才更新 Activity 名
            captureGeneration++
            capturePkg = appId
            val svc = service as? com.yzjdev.autotask.automation.DramaAccessibilityService
            val title = svc?.getValidActivity(appId)?.let { formatTitle(it) }
                ?: "$appId(未知 Activity)"
            lastAppName = title
            snapshots = list
            ensureBoundsView()          // 绘制层窗口已在 show() 时创建,这里仅兜底
            boundsView?.setSnapshots(list)
            setLayerVisible(boundsLp, boundsView, true)   // 显示绘制层
            setLayerVisible(cardLp, cardView, false)      // 信息卡片保持隐藏
            selectedIndex = 0           // 根节点仅在内部选中,不弹卡片

            // 后台轮询(GKD resolveActivityId):反射 getTasks 查询,
            // 直到返回的包名等于本次抓取的 appId(未对齐说明任务栈未就绪,继续等),上限 2 秒
            val generation = captureGeneration
            Thread {
                var top: android.content.ComponentName? = null
                var waited = 0L
                while (waited < 2000) {
                    // 反射 getTasks 以应用身份可能看不到前台任务,退回事件缓存的同包名 Activity
                    top = runCatching { queryTopActivityViaReflection() }.getOrNull()
                        ?: (service as? com.yzjdev.autotask.automation.DramaAccessibilityService)
                            ?.getValidActivity(appId)
                    if (top != null && top.packageName == appId) break
                    Thread.sleep(100)
                    waited += 100
                }
                val result = top?.takeIf { it.packageName == appId } ?: return@Thread
                mainHandler.post {
                    // 只替换本次抓取的标题;期间用户重新抓取过(generation 变化)则丢弃
                    if (generation == captureGeneration && captureEnabled) {
                        applyHeader(result)
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

        // 头部:应用名(左,加粗) + 节点计数徽章(胶囊) + 关闭钮(右)
        val title = TextView(service).apply {
            text = appName.substringBefore('(').ifEmpty { appName }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleView = title

        // 第二行:Activity 全名(包名/短类名),完整显示(不截断),长按复制
        val activity = TextView(service).apply {
            text = appName.substringAfter('(', "").removeSuffix(")")
                .ifEmpty { "未知 Activity" }
            setTextColor(0x80FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = android.graphics.Typeface.MONOSPACE
            // 长按复制完整 Activity 名
            setOnLongClickListener {
                copyToClipboard(text.toString())
                it.animate().alpha(0.4f).setDuration(80)
                    .withEndAction { it.animate().alpha(1f).setDuration(120) }
                true
            }
        }
        activityView = activity

        // 节点计数徽章:浅琥珀底胶囊,显示「n/m」,选中时由 renderInfoTable 更新
        val count = TextView(service).apply {
            text = "--"
            setTextColor(0xFF92600A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(0x40F59E0B.toInt())
            }
        }
        countView = count

        // 关闭按钮:琥珀圆底 + ✕
        val close = TextView(service).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setTextColor(0xFFF59E0B.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x1FF59E0B.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                leftMargin = dp(8)
            }
            setOnClickListener { closeInfoCard() }  // 只关闭节点信息卡片,绘制保留
        }

        // 第一行:应用名(左) + 节点计数徽章 + 关闭钮(右)
        val headerRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(10), dp(6))
            addView(title, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(count)
            addView(close)
        }
        // 第二行:Activity 全名单独一行,完整显示(超宽卡片内换行),长按复制
        val activityRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(10), dp(8))
            addView(activity, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        // 分隔线:同一 View 实例不能放两处,各建一条
        val dividerTop = View(service).apply {
            setBackgroundColor(0x26F59E0B.toInt())
        }
        val dividerBottom = View(service).apply {
            setBackgroundColor(0x26F59E0B.toInt())
        }

        // 属性表格容器:每行 = 属性名列 + 值列,选中节点时重建
        val info = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        infoView = info

        // 表格套 ScrollView:高度随内容自适应,封顶 maxScrollHeight(约半屏),超出内部滚动。
        // ScrollView 无 maxHeight 属性,只能给精确高度;renderInfoTable 里每次重算
        val scroll = ScrollView(service).apply {
            isVerticalScrollBarEnabled = true
            addView(info)
        }
        scrollRegion = scroll

        // 方向按钮:单行排布(上 / 下 / 左 / 右),等分四格,不可移动时置禁用态
        val dirs = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(6), dp(12), dp(8))
        }
        dirRow = dirs
        val buttons = mutableListOf<TextView>()
        listOf("↑" to { moveUp() }, "↓" to { moveDown() },
            "←" to { moveLeft() }, "→" to { moveRight() }).forEach { (label, action) ->
            val cell = FrameLayout(service).apply {
                addView(dirButton(label) { action() }, FrameLayout.LayoutParams(
                    dp(30), dp(30), Gravity.CENTER))
            }
            buttons.add(cell.getChildAt(0) as TextView)
            dirs.addView(cell, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        dirButtons = buttons

        // 底部抽屉把手:点开/收起节点树抽屉
        val treeHandle = TextView(service).apply {
            text = "▼ 节点树"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setTextColor(0xCCF59E0B.toInt())
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { toggleTreeDrawer() }
        }

        // 外层 wrapper 承担圆角背景:子 View 的直角不会溢出圆角
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(0xF51A1206.toInt())  // 琥珀夜底
                setStroke(dp(1), 0x40F59E0B.toInt())  // 琥珀描边
            }
            addView(headerRow)
            addView(activityRow)
            addView(dividerTop, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
            // 表格区占满剩余高度(卡片固定高,内容超出时内部滚动)
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(dividerBottom, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
            addView(dirs)
            addView(treeHandle)
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
        activityView = null
        countView = null
        infoView = null
        dirRow = null
        dirButtons = null
        scrollRegion = null
        treeDrawer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        treeDrawer = null
        treeListView = null
        treeScroll = null
        treeShown = false
        snapshots = emptyList()
        selectedIndex = -1
    }

    // ---- 选中与方向移动 ----

    private fun select(index: Int) {
        if (index < 0 || index >= snapshots.size) return
        val prevSelected = selectedIndex
        selectedIndex = index
        val s = snapshots[index]
        boundsView?.setSelected(index)
        ensureInfoCard(lastAppName)
        setLayerVisible(cardLp, cardView, true && !treeShown)   // 选中节点即显示卡片;树抽屉展开时保持隐藏
        updateDirButtons(index, s)
        renderInfoTable(index, s)
        // 树抽屉打开时只刷新新旧两行高亮,不整棵重建(全量重建数百行会卡顿)
        if (treeShown) refreshTreeHighlight(prevSelected, index)
    }

    /** 只更新两行的高亮背景与文字颜色:遍历可见子 View 匹配,避免整棵树重建 */
    private fun refreshTreeHighlight(prev: Int, cur: Int) {
        val list = treeListView ?: return
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as? LinearLayout ?: continue
            // 树行按 DFS 顺序添加,与 snapshots 下标一致:列表第 i 行 = 节点 i
            when (i) {
                prev, cur -> {
                    val isSel = i == cur
                    row.background = if (isSel) android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(6).toFloat()
                        setColor(0x33F59E0B.toInt())
                    } else null
                    // 第二个子 View 是文本行,更新颜色
                    (row.getChildAt(1) as? TextView)?.setTextColor(
                        if (isSel) 0xFFF59E0B.toInt()
                        else if (snapshots[i].clickable) 0xCCFFFFFF.toInt() else 0x99FFFFFF.toInt()
                    )
                }
            }
        }
    }

    /** 四个方向按钮:对应方向可移动才可用,否则禁用(变暗 + 不可点) */
    private fun updateDirButtons(index: Int, s: NodeSnapshot) {
        val btns = dirButtons ?: return
        val siblings = if (s.parent >= 0) snapshots[s.parent].children else emptyList()
        val pos = siblings.indexOf(index)
        val states = listOf(
            s.parent >= 0,                          // 上:有父节点
            s.children.isNotEmpty(),                // 下:有子节点
            pos > 0,                                // 左:有前一兄弟
            pos in siblings.indices && pos < siblings.size - 1,  // 右:有后一兄弟
        )
        states.forEachIndexed { i, enabled ->
            val b = btns[i]
            b.isEnabled = enabled
            b.isClickable = enabled
            b.alpha = if (enabled) 1f else 0.3f
            b.setTextColor(if (enabled) Color.WHITE else 0x664ADE80.toInt())
            (b.background as? android.graphics.drawable.GradientDrawable)?.let { bg ->
                bg.setColor(if (enabled) 0x334ADE80.toInt() else 0x0D4ADE80.toInt())
                bg.setStroke(dp(1),
                    if (enabled) 0x804ADE80.toInt() else 0x264ADE80.toInt())
            }
        }
    }

    /** 选中节点信息:节点块(大索引 + 祖先面包屑 + 摘要) + 按组属性表 */
    private fun renderInfoTable(index: Int, s: NodeSnapshot) {
        val container = infoView ?: return
        container.removeAllViews()
        countView?.text = "${index + 1}/${snapshots.size}"

        // ---- 节点块:大索引 + 摘要(第一行) + 祖先面包屑(第二行) ----
        val block = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0x14FFFFFF.toInt())
            }
        }
        val top = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(service).apply {
            text = "NODE ${index + 1}"
            setTextColor(0xFFF59E0B.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(0x33F59E0B.toInt())
            }
        })
        top.addView(TextView(service).apply {
            text = s.info.take(60)
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(8)
            }
        })
        block.addView(top)
        // 祖先面包屑:横向可滑动 + 每级可点击跳转(root > … > 父节点,最多 12 级防环)
        val crumbs = ArrayList<Pair<String, Int>>()  // 类名 → 祖先节点下标
        var p = s.parent
        var guard = 0
        while (p >= 0 && guard < 12) {
            guard++
            val ps = snapshots.getOrNull(p) ?: break
            crumbs.add(0, (ps.table["类名"] ?: "?").substringAfterLast('.') to p)
            p = ps.parent
        }
        if (crumbs.isNotEmpty()) {
            // 面包屑行:超出卡片宽度时横向滑动;点任一级直接选中该祖先
            val crumbRow = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            crumbs.forEachIndexed { ci, (name, idx) ->
                if (ci > 0) {
                    crumbRow.addView(TextView(service).apply {
                        text = " > "
                        setTextColor(0x50FFFFFF.toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        typeface = android.graphics.Typeface.MONOSPACE
                    })
                }
                crumbRow.addView(TextView(service).apply {
                    text = name
                    setTextColor(0x99FFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                    typeface = android.graphics.Typeface.MONOSPACE
                    // 点击面包屑某一级 = 选中该祖先节点
                    setOnClickListener { select(idx) }
                })
            }
            val crumbScroll = HorizontalScrollView(service).apply {
                isHorizontalScrollBarEnabled = false
                addView(crumbRow)
            }
            block.addView(crumbScroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })
            // 选中后重渲染,默认让最右(最近祖先)可见
            crumbScroll.post { crumbScroll.fullScroll(View.FOCUS_RIGHT) }
        }
        container.addView(block, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        })

        // ---- 属性表:按 内容/定位/状态/布局 分组,组标题行 + 键值行 ----
        val groups = listOf(
            "内容" to listOf("类名", "viewId", "文本", "描述"),
            "定位" to listOf("边界"),
            "状态" to listOf("可点击", "可滚动", "可勾选"),
            "布局" to listOf("子节点"),
        )
        for ((title, keys) in groups) {
            val rows = keys.mapNotNull { k -> s.table[k]?.let { k to it } }
            if (rows.isEmpty()) continue
            container.addView(TextView(service).apply {
                text = title
                setTextColor(0xB392600A.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(6), 0, dp(3))
            })
            for ((name, value) in rows) {
                val row = LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(22)
                    isClickable = true
                    isLongClickable = true
                    // 长按复制属性值
                    setOnLongClickListener {
                        copyToClipboard(value)
                        it.animate().alpha(0.4f).setDuration(80)
                            .withEndAction { it.animate().alpha(1f).setDuration(120) }
                        true
                    }
                }
                row.addView(TextView(service).apply {
                    text = name
                    setTextColor(0xCCF59E0B.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    layoutParams = LinearLayout.LayoutParams(dp(56),
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                row.addView(TextView(service).apply {
                    text = value
                    setTextColor(0xE8FFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = android.graphics.Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                container.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }

        // 滚动区高度随内容自适应(封顶 maxScrollHeight),避免内容少时底部大片留白
        val region = scrollRegion
        region?.post {
            container.measure(
                View.MeasureSpec.makeMeasureSpec(region.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val h = container.measuredHeight.coerceAtMost(maxScrollHeight)
            region.layoutParams = region.layoutParams.also { it.height = h }
        }
    }

    /** 复制文本到剪贴板并提示 */
    private fun copyToClipboard(text: String) {
        val cm = service.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("node", text))
        android.widget.Toast.makeText(service, text, android.widget.Toast.LENGTH_SHORT).show()
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

    /** 方向按钮:圆角方形(半透明墨绿底 + 1dp 墨绿描边),初始为可用态 */
    private fun dirButton(icon: String, onClick: () -> Unit): TextView =
        TextView(service).apply {
            text = icon
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(0x334ADE80.toInt())  // 墨绿半透底
                setStroke(dp(1), 0x804ADE80.toInt())  // 墨绿描边
            }
            // 固定 36dp 圆角方形,居中在等分格内
            layoutParams = FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER)
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics
    ).toInt()

    private val screenW: Int get() = service.resources.displayMetrics.widthPixels
    private val screenH: Int get() = service.resources.displayMetrics.heightPixels

    private fun clamp(v: Int, min: Int, max: Int): Int = v.coerceIn(min, max.coerceAtLeast(min))

    /** 应用名:PackageManager 按 applicationInfo.labelRes/label 解析,失败回退包名 */
    private fun appLabel(pkg: String): String = runCatching {
        val pm = service.packageManager
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai).toString()
    }.getOrDefault(pkg)
}
