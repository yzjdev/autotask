package com.yzjdev.autotask.automation

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
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
import com.yzjdev.autotask.automation.log.AppLog
import com.yzjdev.autotask.automation.log.LogSource

/** 悬浮窗整体不透明度:调试卡片与节点树抽屉共用同一值(越大越不透明) */
internal const val OVERLAY_ALPHA = 0.92f

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
        private const val CARD_DRAG_SLOP_PX = 12  // 卡片拖动 vs 点击的位移阈值
    }

    // 服务构造时 context 尚未 attach,所有 WindowManager/Resources 相关一律懒初始化
    private val wm by lazy { service.getSystemService(WindowManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    // 开关悬浮球:独立 UI,单击绘制/收起焦点激活窗口的节点边界,长按关闭悬浮窗
    private val toggleWindow = OverlayToggleWindow(
        service,
        { togglePanel() },
        { (service as? AutoTaskAccessibilityService)?.showDebugOverlay(false) },
    )

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
        ).apply {
            gravity = Gravity.CENTER
            alpha = OVERLAY_ALPHA
        }
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
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            alpha = OVERLAY_ALPHA
        }
    }

    /** 抓取时的节点快照(节点树会失效,必须立即转为数据) */
    private var snapshots: List<NodeSnapshot> = emptyList()
    private var selectedIndex = -1

    /** 已折叠的节点下标:折叠后其子孙不在树中展开;每次重新抓取/清空快照时重置 */
    private val collapsedNodes = HashSet<Int>()

    /** 卡片头部两行:应用名与 Activity 全名。同源取用,不再靠拼接字符串再拆解 */
    private var headerTitle = ""
    private var headerActivity = ""

    /** 头部当前窗口的包名:标题里那份是「应用名(包名)」,长按要复制的是纯包名 */
    private var headerPkg = ""

    /** 头部第一行的名字:通知栏/导航栏/桌面用类别名,其余用应用名 */
    private fun headerName(kind: WindowKind?, pkg: String): String = when (kind) {
        WindowKind.Launcher, WindowKind.StatusBar, WindowKind.NavigationBar -> kind.label
        else -> appLabel(pkg)
    }

    /**
     * 写入头部两行:第一行「应用名(包名)」,第二行完整 Activity 类名(不再带包名前缀)。
     * 卡片已存在时即时改写。
     */
    private fun applyHeader(name: String, pkg: String, activity: String) {
        val title = if (pkg.isEmpty()) name else "$name($pkg)"
        headerTitle = title
        headerPkg = pkg
        headerActivity = activity
        titleView?.text = title
        // 没有 Activity 就整行收起,不写占位文字
        activityView?.apply {
            text = activity
            visibility = if (activity.isBlank()) View.GONE else View.VISIBLE
        }
    }

    // ---- 控制面板(开关悬浮球,独立 UI)----

    /**
     * 开启开关:一次性按序创建四个窗口(绘制层 → 信息卡片 → 窗口列表 → 悬浮球),
     * 同层级窗口按添加顺序叠放,后加的在上:绘制层在底,悬浮球在最上。
     * 各窗口初始 GONE/隐藏态,后续只切 visibility,不再动态 addView/removeView,
     * 避免抓取/选中时窗口反复重建导致的闪烁与层级跳动。
     *
     * 四窗口的显隐不由各自入口零散控制,统一收口到 [PanelState] 状态机(见 [applyState]):
     * 三块面板同一时刻只显示一种组合,悬浮球全程常显。
     */
    fun show() {
        mainHandler.post {
            ensureBoundsView()
            ensureInfoCard(headerTitle, headerActivity)
            // 窗口列表悬浮窗预加入在信息卡之后、悬浮球之前,保证层级:绘制层 < 卡片 < 列表 < 悬浮球
            (service as? AutoTaskAccessibilityService)?.prepareWindowListOverlay()
            // 初始态:只有悬浮球,三块面板全隐(显隐与触摸穿透标志一并由状态机置好)
            panelState = PanelState.None
            applyState()
            // 悬浮球最后创建并置顶,天然位于最上层
            toggleWindow.show()
            toggleWindow.bringToFront()
            AutoTaskAccessibilityService.notifyOverlayShowing(true)
        }
    }

    fun hide() {
        mainHandler.post {
            AutoTaskAccessibilityService.notifyOverlayShowing(false)
            panelState = PanelState.None
            removeCaptureViews()
            toggleWindow.hide()
            toggleWindow.setActive(false)
        }
    }

    val isShowing: Boolean
        get() = toggleWindow.isShowing

    // ---- 面板状态机 ----

    /**
     * 三块面板的显隐状态:同一时刻只呈现一种组合。
     * 悬浮球不在状态内 —— 调试悬浮窗开启期间它全程常显,是唯一常驻的入口。
     *
     * | 状态    | 窗口列表 | 绘制层 | 信息卡片                 |
     * |---------|----------|--------|--------------------------|
     * | `None`  | 隐       | 隐     | 隐                       |
     * | `List`  | 显       | 隐     | 隐                       |
     * | `Bounds`| 隐       | 显     | 选中节点时显(节点树展开时隐) |
     *
     * 转移表(所有入口只改状态再调 [applyState],不各自 setLayerVisible):
     *  - 点悬浮球:`None` → `List`;`List`/`Bounds` → `None`
     *  - 列表点某行窗口:`List` → `Bounds`(画该窗口,列表让位)
     *  - 列表 ✕:`List` → `None`
     *  - 点绘制层:`Bounds` 内实时重抓刷新(列表点选的窗口,不落回激活窗口)
     *  - 点中节点:`Bounds` 内显示信息卡片;卡片 ✕ 只收卡片,绘制层保留
     */
    private enum class PanelState { None, List, Bounds }

    private var panelState = PanelState.None

    /** 窗口列表点选的来源窗口:点击绘制层实时刷新时重抓它,而不是落回焦点激活窗口 */
    private var boundsWindow: android.view.accessibility.AccessibilityWindowInfo? = null

    /**
     * 唯一显隐收口:按 [panelState] 与选中状态推导四块面板的显隐。
     * 入口只改状态再调这里 —— 否则状态与画面容易不一致(残留上次的绘制/卡片)。
     */
    private fun applyState() {
        val bounds = panelState == PanelState.Bounds
        if (!bounds) {
            // 离开 Bounds:清空绘制数据,下次进来从干净状态开始
            boundsView?.setSelected(-1)
            snapshots = emptyList()
            collapsedNodes.clear()
            selectedIndex = -1
            boundsWindow = null
            treeShown = false
        }
        val card = bounds && selectedIndex >= 0 && !treeShown
        setLayerVisible(boundsLp, boundsView, bounds)
        setLayerVisible(cardLp, cardView, card)
        setLayerVisible(treeLp, treeDrawer, bounds && treeShown)
        // 窗口列表是服务侧持有的独立窗口,同样由这里统一驱动
        (service as? AutoTaskAccessibilityService)?.setWindowListVisible(panelState == PanelState.List)
    }

    /** 点悬浮球:未展开 → 打开窗口列表;已展开 → 全部收起 */
    private fun togglePanel() {
        panelState = if (panelState == PanelState.None) PanelState.List else PanelState.None
        applyState()
    }

    /** 列表 ✕:收起列表,回到只剩悬浮球 */
    fun closeWindowList() {
        panelState = PanelState.None
        applyState()
    }

    /**
     * 窗口列表点选某窗口:进入 `Bounds` 状态,画该窗口的节点边界,头部取该窗口自己的 Activity。
     * 这是 `Bounds` 的唯一入口 —— 抓取的窗口在此确定,后续点击绘制层重抓的都是它。
     */
    fun showBoundsFor(window: android.view.accessibility.AccessibilityWindowInfo): Boolean {
        val list = NodeSnapshotCollector.collect(service, window)
        if (list.isEmpty()) {
            // 诊断:root 取不到 = 窗口引用已失效(系统重建了该窗口);root 可取但为空 = 子树不可读
            val rootOk = runCatching { window.root }.getOrNull() != null
            AppLog.warn(
                LogSource.Overlay,
                "窗口 ${window.id} 节点读取失败(root=${if (rootOk) "可取但子树为空" else "null"})",
            )
            return false
        }
        val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull().orEmpty()
        val svc = service as? AutoTaskAccessibilityService
        val cn = svc?.activityOfWindow(window)
        if (pkg.isEmpty()) {
            applyHeader("窗口 ${window.id}", "", "")
        } else {
            applyHeader(
                headerName(svc?.windowKind(window), pkg),
                pkg,
                cn?.className.orEmpty(),
            )
        }
        boundsWindow = window
        snapshots = list
        collapsedNodes.clear()
        // 刚进来不预选节点:只画框,点中哪个才弹哪张卡片
        selectedIndex = -1
        treeShown = false
        ensureBoundsView()
        boundsView?.setSnapshots(list)   // 内含清空上次命中的高亮
        panelState = PanelState.Bounds
        applyState()
        return true
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

    /** 信息卡 ✕:只收信息卡(取消选中),绘制层保留,可继续点边框查看节点 */
    private fun closeInfoCard() {
        boundsView?.setSelected(-1)
        selectedIndex = -1
        treeShown = false
        applyState()
    }

    // ---- 节点树抽屉 ----

    /** 抽屉显隐切换:展开时隐藏信息卡(互不遮挡),收起时恢复 */
    private fun toggleTreeDrawer() {
        treeShown = !treeShown
        if (treeShown) {
            ensureTreeDrawer()
            renderTreeView()
            toggleWindow.bringToFront()
        }
        applyState()               // 信息卡与抽屉的互斥关系在 applyState 里统一处理
        if (treeShown) scrollTreeToSelected()
    }

    /**
     * 把当前选中节点滚进可视区:已经完整可见就不动(点树里那一行不该跳),
     * 否则居中显示。行上挂了快照下标(tag),按它找行。
     */
    private fun scrollTreeToSelected() {
        val scroll = treeScroll ?: return
        val list = treeListView ?: return
        val target = selectedIndex
        if (target < 0) return
        // 等这一帧布局完成再算偏移:此时行高与视口高度才有值
        scroll.post {
            val row = (0 until list.childCount)
                .mapNotNull { list.getChildAt(it) }
                .firstOrNull { (it.tag as? Int) == target }
                ?: return@post
            val viewTop = scroll.scrollY
            val viewBottom = viewTop + scroll.height
            val rowTop = row.top
            val rowBottom = rowTop + row.height
            if (rowTop >= viewTop && rowBottom <= viewBottom) return@post  // 已完整可见
            val centered = rowTop - (scroll.height - row.height) / 2
            val maxY = (list.height - scroll.height).coerceAtLeast(0)
            scroll.smoothScrollTo(0, centered.coerceIn(0, maxY))
            // 深层节点的缩进在右侧,横向回到最左,缩进层级才看得见
            (scroll.getChildAt(0) as? HorizontalScrollView)?.smoothScrollTo(0, 0)
        }
    }

    /** 收起抽屉:信息卡是否随之恢复由 [applyState] 按「是否仍有选中节点」决定 */
    private fun hideTreeDrawer() {
        treeShown = false
        applyState()
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
            if (idx in collapsedNodes) return  // 已折叠:不展开子孙
            s.children.forEach { dfs(it, depth + 1) }
        }
        snapshots.indices.filter { snapshots[it].parent == -1 }.forEach { dfs(it, 0) }
    }

    /** 折叠/展开某节点的子树:只影响树的显示,不改变当前选中 */
    private fun toggleCollapse(idx: Int) {
        if (!collapsedNodes.add(idx)) collapsedNodes.remove(idx)
        renderTreeView()
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

    /** 树行背景:选中 = 琥珀高亮(整行,横向撑满);可见性交给文字颜色,不占背景 */
    private fun treeRowBackground(selected: Boolean): android.graphics.drawable.Drawable? =
        if (selected) {
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0x33F59E0B.toInt())
            }
        } else {
            null
        }

    /** 树行文字色:选中 = 琥珀;不可见 = 压暗;可见再按是否可点击分两档亮度 */
    private fun treeRowTextColor(selected: Boolean, visible: Boolean, clickable: Boolean): Int = when {
        selected -> 0xFFF59E0B.toInt()
        !visible -> 0x59FFFFFF.toInt()
        clickable -> 0xCCFFFFFF.toInt()
        else -> 0x99FFFFFF.toInt()
    }

    /** 节点树单行:缩进(空格) + 类名(#id/"文本") + 可点击标记,选中行高亮。
     * 行内容不省略,超宽由外层横向 ScrollView 滑动查看;
     * 选中底带挂在整行上:行最小宽度 = 抽屉可见宽度,横向撑满;行高给足,底带有厚度。 */
    private fun treeRow(idx: Int, s: NodeSnapshot, depth: Int): View {
        val selected = idx == selectedIndex
        val row = LinearLayout(service).apply {
            // 行号会因折叠/略过而与快照下标错位,把下标挂在 tag 上,高亮时按它匹配
            tag = idx
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 行高比文字高出一圈,选中底带才不会紧贴文字
            minimumHeight = dp(34)
            // 抽屉为全屏宽,列表左右各 8dp 内边距,故可见内容宽度 = 屏宽 - 16dp
            minimumWidth = screenW - dp(16)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = treeRowBackground(selected)
            // 不可见节点不可点:选中它们也不会在屏幕上画出任何东西
            if (s.visible) {
                isClickable = true
                setOnTouchListener { _, ev ->
                    treeTapIndex = idx
                    treeTapDetector.onTouchEvent(ev)
                }
            }
        }
        // 折叠箭头:有子节点时可点(▾ 展开 / ▸ 折叠),只切折叠不改选中;叶子节点留空占位
        val collapsed = idx in collapsedNodes
        row.addView(TextView(service).apply {
            text = when {
                s.children.isEmpty() -> ""
                collapsed -> "▸"
                else -> "▾"
            }
            setTextColor(if (s.children.isNotEmpty()) 0x80F59E0B.toInt() else 0x40FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            if (s.children.isNotEmpty()) {
                isClickable = true
                setPadding(dp(2), dp(7), dp(2), dp(7))
                setOnClickListener { toggleCollapse(idx) }
            }
            layoutParams = LinearLayout.LayoutParams(dp(16),
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
            setTextColor(treeRowTextColor(selected, s.visible, s.clickable))
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

    /**
     * 创建绘制层(边界框)。三个窗口在 show() 时已按 球→卡片→绘制层 顺序创建,
     * 层级固定:绘制层在底层,信息卡片、悬浮球依次叠加其上;这里仅兜底创建。
     */
    private fun ensureBoundsView() {
        if (boundsView != null) return
        val bv = BoundsOverlayView(service)
        bv.onTap = { _, _ ->
            // 点击绘制层:重抓当前正在画的这个窗口(服务端会按 id/类型匹配最新实例)。
            // 不落回焦点激活窗口 —— 否则点输入法等非焦点窗口会被激活窗口覆盖。
            val svc = service as? AutoTaskAccessibilityService
            boundsWindow?.let { svc?.showWindowBounds(it) }
        }
        bv.onNodeHit = { index ->
            ensureInfoCard(headerTitle, headerActivity)  // 卡片被 ✕ 隐藏后点边框重新显示
            select(index)
        }
        boundsView = bv
        wm.addView(bv, boundsLp)
        // 悬浮球永远置顶:同层级窗口按添加顺序叠放,重加悬浮球保证最上层
        toggleWindow.bringToFront()
    }

    /** 创建节点信息卡片(标题栏 + 属性表格 + 方向按钮),置于绘制层之上 */
    private fun ensureInfoCard(appName: String, activityName: String) {
        if (cardView != null) return

        // 头部:应用名(左,加粗) + 节点计数徽章(胶囊) + 关闭钮(右)
        val title = TextView(service).apply {
            text = appName
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            // 长按复制包名(标题是「应用名(包名)」,复制的是纯包名)
            setOnLongClickListener {
                val pkg = headerPkg
                if (pkg.isEmpty()) {
                    false
                } else {
                    copyToClipboard(pkg)
                    it.animate().alpha(0.4f).setDuration(80)
                        .withEndAction { it.animate().alpha(1f).setDuration(120) }
                    true
                }
            }
        }
        titleView = title

        // 第二行:完整 Activity 类名,完整显示(不截断),长按复制;没有就收起
        val activity = TextView(service).apply {
            text = activityName
            visibility = if (activityName.isBlank()) View.GONE else View.VISIBLE
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
        attachCardDrag(card)
        wm.addView(card, cardLp)
        // 悬浮球永远置顶:信息卡片创建后把悬浮球重新加回最上层
        toggleWindow.bringToFront()
    }

    /**
     * 信息卡片整卡拖动:监听器挂在卡片根布局上。
     * 子 View(标题、关闭钮、方向键、节点树把手、表格滚动区)会消费自己的 DOWN,
     * 事件不会冒泡到这里,因此拖动只在头部行内边距/分隔线/空白处生效,不干扰内部交互。
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachCardDrag(card: LinearLayout) {
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragging = false
        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = cardLp.x; startY = cardLp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragging || dx * dx + dy * dy > CARD_DRAG_SLOP_PX * CARD_DRAG_SLOP_PX) {
                        dragging = true
                        // gravity=CENTER 时 x/y 为相对屏幕中心的偏移,clamp 到半屏范围
                        cardLp.x = clamp(startX + dx.toInt(),
                            -(screenW - v.width) / 2, (screenW - v.width) / 2)
                        cardLp.y = clamp(startY + dy.toInt(),
                            -(screenH - v.height) / 2, (screenH - v.height) / 2)
                        wm.updateViewLayout(v, cardLp)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragging
                    dragging = false
                    wasDragging
                }
                else -> dragging
            }
        }
    }

    private fun removeCaptureViews() {
        boundsView?.let {
            it.onNodeHit = null
            it.onTap = null
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
        collapsedNodes.clear()
        selectedIndex = -1
    }

    // ---- 选中与方向移动 ----

    private fun select(index: Int) {
        if (index < 0 || index >= snapshots.size) return
        val prevSelected = selectedIndex
        selectedIndex = index
        val s = snapshots[index]
        boundsView?.setSelected(index)
        ensureInfoCard(headerTitle, headerActivity)
        applyState()   // 选中节点即显示卡片;树抽屉展开时保持隐藏,由状态机统一决定
        updateDirButtons(index, s)
        renderInfoTable(index, s)
        // 树抽屉打开时只刷新新旧两行高亮,不整棵重建(全量重建数百行会卡顿)
        if (treeShown) {
            refreshTreeHighlight(prevSelected, index)
            // 在屏幕上点中节点后,树里也跟到那一行
            scrollTreeToSelected()
        }
    }

    /** 只更新两行的高亮背景与文字颜色:遍历可见子 View 匹配,避免整棵树重建 */
    private fun refreshTreeHighlight(prev: Int, cur: Int) {
        val list = treeListView ?: return
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as? LinearLayout ?: continue
            // 按行上挂的快照下标匹配:折叠或略过节点时行数少于节点数,行号不再等于下标
            val idx = row.tag as? Int ?: continue
            if (idx != prev && idx != cur) continue
            val isSel = idx == cur
            val snap = snapshots.getOrNull(idx)
            val visible = snap?.visible ?: true
            // 底带在整行上;可见性走文字颜色,与 treeRow 保持一致
            row.background = treeRowBackground(isSel)
            (row.getChildAt(1) as? TextView)?.setTextColor(
                treeRowTextColor(isSel, visible, snap?.clickable == true)
            )
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
            "内容" to listOf("类名", "viewId", "vid", "文本", "描述"),
            "定位" to listOf("边界"),
            "状态" to listOf("可点击", "可滚动", "可勾选", "可见性"),
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

        /** 点击绘制层时回调(触点坐标):宿主先实时抓取最新快照,再做命中 */
        var onTap: ((Int, Int) -> Unit)? = null

        private var snapshots: List<NodeSnapshot>? = null
        private var selected = -1

        /** 边框按 dp 加粗:原来写死的 2px 在高密度屏上几乎看不见,但也不宜过粗 */
        private val density = context.resources.displayMetrics.density

        private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = 0x99FF4081.toInt()
        }
        private val clickablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = 0x8800E5FF.toInt()
        }
        private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            color = 0xFFFFEB3B.toInt()
        }
        private val selectedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x44FFEB3B.toInt()
        }

        fun setSnapshots(list: List<NodeSnapshot>) {
            snapshots = list
            selected = -1   // 新快照:清空上次命中的高亮,避免旧下标串到新列表
            invalidate()
        }

        fun setSelected(index: Int) {
            selected = index
            invalidate()
        }

        /**
         * 点击命中:选包含触点、面积最小的节点(最小命中 = 树中最深/最内层),
         * 同面积取 DFS 序靠后的(更深的那层)。
         * 返回 true 表示消费了事件。
         */
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
            val x = event.x.toInt()
            val y = event.y.toInt()
            // 点击时实时抓取:快照不跟随事件回调刷新,落点之前先拿最新节点树
            onTap?.invoke(x, y)
            val list = snapshots ?: return false
            var best = -1
            var bestArea = Int.MAX_VALUE
            for ((i, s) in list.withIndex()) {
                // 不可见节点不参与命中:点到空白处不该选中屏幕上没有的节点
                if (!s.visible) continue
                val r = s.rect
                if (r.width() <= 0 || r.height() <= 0) continue
                if (!r.contains(x, y)) continue
                val area = r.width() * r.height()
                if (area <= bestArea) {
                    bestArea = area
                    best = i
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
                // 不可见节点不画框:屏幕上没有对应画面,画出来只会干扰
                if (!s.visible) continue
                val r = s.rect
                if (r.width() <= 0 || r.height() <= 0) continue
                if (i == selected) continue  // 选中框最后画,压在最上层
                canvas.drawRect(r, if (s.clickable) clickablePaint else normalPaint)
            }
            if (selected in list.indices && list[selected].visible) {
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
