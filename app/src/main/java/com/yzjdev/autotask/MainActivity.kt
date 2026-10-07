package com.yzjdev.autotask

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import android.widget.Toast
import rikka.shizuku.Shizuku
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardBackspace
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.yzjdev.autotask.automation.AutomationManager
import com.yzjdev.autotask.automation.DramaAccessibilityService
import com.yzjdev.autotask.automation.ShizukuShell
import com.yzjdev.autotask.automation.LogStore
import com.yzjdev.autotask.automation.GkdSelector
import com.yzjdev.autotask.automation.Action
import com.yzjdev.autotask.automation.GkdTask
import com.yzjdev.autotask.automation.GkdSubscription
import com.yzjdev.autotask.automation.SubscriptionFetcher
import com.yzjdev.autotask.automation.SubscriptionStore
import com.yzjdev.autotask.automation.TaskStore
import com.yzjdev.autotask.automation.NodeSnapshot
import com.yzjdev.autotask.automation.NodeSnapshotCollector
import com.yzjdev.autotask.ui.theme.AutoTaskTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyRecentsHidden(this)
        // 日志打印开关:随偏好设置生效(引擎进程内生效)
        LogStore.enabled = getSharedPreferences("logs_ui", MODE_PRIVATE).getBoolean("log_enabled", true)
        setContent {
            AutoTaskTheme {
                AutomationScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyRecentsHidden(this)
        // 启动即自动检测(首次触发完整链路,之后仅刷新状态),无需用户手动开关;
        // 从系统设置返回后同样会刷新 Shizuku / 安全设置 / 无障碍状态
        AutomationManager.get(applicationContext).bootstrap()
    }
}

/** 最近任务卡片隐藏偏好:「隐藏最近任务卡片」开关持久化 */
private const val PREFS_RECENTS = "recents_ui"
private const val KEY_HIDE_RECENTS = "hide_recents"

fun isRecentsHidden(context: android.content.Context): Boolean =
    context.getSharedPreferences(PREFS_RECENTS, android.content.Context.MODE_PRIVATE)
        .getBoolean(KEY_HIDE_RECENTS, false)

/** 隐藏最近任务卡片开关:开启后把本应用任务从最近任务列表排除 */
fun setRecentsHidden(context: android.content.Context, hidden: Boolean) {
    context.getSharedPreferences(PREFS_RECENTS, android.content.Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_HIDE_RECENTS, hidden).apply()
    if (context is android.app.Activity) applyRecentsHidden(context)
}

// 禁网清单持久化:开关命令成功后落盘,启动直接恢复;实时查询仅作校正,不再作为状态来源
private const val PREFS_NET = "net_policy"
private const val KEY_NET_BLOCKED = "blocked_pkgs"

private fun loadNetBlocked(context: android.content.Context): Set<String> =
    context.getSharedPreferences(PREFS_NET, android.content.Context.MODE_PRIVATE)
        .getStringSet(KEY_NET_BLOCKED, emptySet()) ?: emptySet()

private fun saveNetBlocked(context: android.content.Context, pkgs: Set<String>) {
    context.getSharedPreferences(PREFS_NET, android.content.Context.MODE_PRIVATE)
        .edit().putStringSet(KEY_NET_BLOCKED, pkgs).apply()
}

/** 按当前偏好应用排除最近任务(需在 Activity 存活时调用才对本任务生效) */
private fun applyRecentsHidden(activity: android.app.Activity) {
    val hidden = isRecentsHidden(activity)
    val am = activity.getSystemService(android.app.ActivityManager::class.java) ?: return
    for (task in am.appTasks) {
        if (task.taskInfo.baseActivity?.packageName == activity.packageName) {
            task.setExcludeFromRecents(hidden)
        }
    }
}

/** 订阅导入完成通知:应用页监听 version 变化重载规则列表(跨 tab 同步) */
internal object RuleSync {
    var version by mutableStateOf(0)
}

/** 自动化主界面 */
@Composable
fun AutomationScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val automation = remember { AutomationManager.get(context.applicationContext) }
    var uiState by remember { mutableStateOf(automation.currentState()) }
    // 确认弹窗:跳转到系统设置必须由用户显式确认,不允许自动执行
    var showA11yConfirm by remember { mutableStateOf(false) }
    // 底部导航:0=首页(权限/悬浮窗) 1=应用(规则管理) 2=日志(执行记录)
    var tab by remember { mutableStateOf(0) }
    // 调试悬浮球开关:仅在无障碍服务已连接时可开;服务断开时自动复位
    var overlayOn by remember {
        mutableStateOf(DramaAccessibilityService.isRunning && DramaAccessibilityService.instance?.isDebugOverlayShowing == true)
    }
    // 服务实例绑定状态:可观察(连接/断开回调驱动),供「待连接」判定与 UI 自动刷新
    var serviceBound by remember { mutableStateOf(DramaAccessibilityService.isRunning) }
    // 订阅无障碍服务连接状态:连接/断开同步绑定状态;服务断开时悬浮窗已被服务 hide(),同步复位开关状态
    DisposableEffect(Unit) {
        val listener = { running: Boolean ->
            serviceBound = running
            if (!running) overlayOn = false
        }
        DramaAccessibilityService.addStateListener(listener)
        onDispose { DramaAccessibilityService.removeStateListener(listener) }
    }
    // 待连接轮询:设置已开但服务实例未绑定(中间态)时每秒刷新,直至 onServiceConnected 完成
    LaunchedEffect(uiState.isAccessibilityEnabled, serviceBound) {
        if (uiState.isAccessibilityEnabled && !serviceBound) {
            while (!DramaAccessibilityService.isRunning) {
                delay(1000)
                automation.refresh()
            }
            serviceBound = true
        }
    }
    // 禁网恢复:进入界面时执行一次(VPN 授权为系统级持久授权,重启后直接重启服务即可)
    val scope = rememberCoroutineScope()

    // ---- 应用 tab 共享数据:提升到顶层,切 tab 不销毁、不重新加载 ----
    // 规则多时 JSON 反序列化耗时会卡首帧,初始空表 + 后台加载
    var tasks by remember { mutableStateOf(emptyList<GkdTask>()) }
    // 订阅导入后同步:RuleSync.version 变化即从磁盘重载规则列表(引擎已由导入方直接换表)
    // 订阅数量同样提升到顶层:切 tab 回主页不再重新读盘导致描述文案跳变
    var subCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        tasks = withContext(Dispatchers.IO) { TaskStore.loadAll(context.applicationContext) }
        subCount = withContext(Dispatchers.IO) { SubscriptionStore.loadAll(context.applicationContext).size }
        snapshotFlow { RuleSync.version }.drop(1).collect {
            tasks = withContext(Dispatchers.IO) { TaskStore.loadAll(context.applicationContext) }
            subCount = withContext(Dispatchers.IO) { SubscriptionStore.loadAll(context.applicationContext).size }
        }
    }
    fun persistTasks(next: List<GkdTask>) {
        tasks = next
        // 全量序列化大规则表较慢,落盘放后台;引擎换表仍在主线程立即生效
        scope.launch(Dispatchers.IO) { TaskStore.saveAll(context.applicationContext, next) }
        DramaAccessibilityService.instance?.taskRunner?.setTasks(next)
        // 按当前前台页面重新评估触发,避免停留在目标页时保存的任务不响应
        DramaAccessibilityService.instance?.refreshTaskTriggers()
    }
    // 已安装应用:懒加载——首次切到「应用」tab 才查询(打开 app 不立即触发
    // 系统的「查询所有应用」权限弹窗,MIUI 等会在 getInstalledPackages 时弹)
    var installedApps by remember { mutableStateOf<List<TaskStore.AppInfo>?>(null) }
    var appsLoadedOnce by remember { mutableStateOf(false) }
    // 安装时间/版本号元数据:与列表同轮查询,供「最近安装」排序与版本号标签展示
    var installTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var versionsByPkg by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    fun loadInstalledApps() {
        // 加载中防重入;异常兜底(MIUI 等查询被拒抛异常)不能让状态悬空
        if (appsLoadedOnce && installedApps != null) return
        appsLoadedOnce = true
        scope.launch {
            val loaded = withContext(Dispatchers.Default) {
                // firstInstallTime/versionName 已随 getInstalledPackages 一次带回,无需逐包二次查询
                runCatching {
                    TaskStore.loadInstalledApps(context.applicationContext, includeSystem = true)
                }.getOrNull()
            } ?: return@launch
            installedApps = loaded.first
            installTimes = loaded.second.mapValues { it.value.first }
            versionsByPkg = loaded.second.mapValues { it.value.second }
        }
    }
    LaunchedEffect(tab) {
        if (tab == 1) loadInstalledApps()
    }
    // 应用 tab UI 状态:同样提升,切 tab 保留搜索;rememberSaveable 兼顾进程重建
    var query by rememberSaveable { mutableStateOf("") }
    // 应用列表排序模式持久化:0=名称升序,1=名称降序,2=任务数多在前,3=安装时间新在前
    val sortModePrefs = context.getSharedPreferences("apps_ui", android.content.Context.MODE_PRIVATE)
    var sortMode by rememberSaveable { mutableStateOf(sortModePrefs.getInt("sort_mode", 0)) }
    // 应用/系统应用分段:0 = 用户应用, 1 = 系统应用
    var appTab by rememberSaveable { mutableStateOf(0) }
    // 应用 tab → 任务页导航:null = 停留在应用列表;非空 = 该包名的独立任务页(全屏,隐藏底部导航)
    var taskPagePkg by rememberSaveable { mutableStateOf<String?>(null) }
    // 禁网应用清单:持久化为准,提升到顶层,应用信息抽屉改开关后即时同步到列表角标
    var netBlockedPkgs by remember { mutableStateOf(loadNetBlocked(context)) }
    // 任务页 → 规则编辑页:null = 停留在任务页;非空 = 正在编辑该 id 的规则(新建时为临时标记)
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    // 任务页/编辑页独立显示时接管系统返回键:
    // 规则编辑页返回 = 回到任务页规则列表;任务页返回 = 回应用列表
    BackHandler(enabled = taskPagePkg != null) {
        if (editingRuleId != null) editingRuleId = null else taskPagePkg = null
    }
    // 规则导航页的编辑态:系统返回键回到规则列表
    BackHandler(enabled = tab == 2 && editingRuleId != null) {
        editingRuleId = null
    }
    // 应用列表滚动状态:提升到顶层,进入任务页/规则页(AppsScreen 离开组合)返回后恢复位置
    val appsListState = rememberLazyListState()
    // 包名 → 显示名:优先已装应用标签,任务残留包回退 PackageManager 查询/原包名
    val labelByPkg = remember(installedApps, tasks) {
        val m = (installedApps ?: emptyList()).associate { it.packageName to it.label }.toMutableMap()
        tasks.forEach { r ->
            m.putIfAbsent(r.packageName, appLabel(context, r.packageName, null))
        }
        m
    }
    // 展示列表 = 已装应用(按系统/用户标记)∪ 有任务的应用,按显示名排序;
    // 有任务的应用始终保留,否则任务无法管理。isSystem 供 AppsScreen 按 tab 过滤。
    // 全局规则(packageName 空)不产生条目;未安装应用不显示(其规则仍在任务表中保留,
    // 应用装回后自动恢复显示)
    val allApps = remember(installedApps, tasks, installTimes, versionsByPkg) {
        val infos = (installedApps ?: emptyList()).associateBy { it.packageName }
        // 任务计数一次统计,避免逐包 count 的 O(n×m)
        val countByPkg = tasks.groupingBy { it.packageName }.eachCount()
        infos.keys.map { pkg ->
            AppRow(
                packageName = pkg,
                label = labelByPkg[pkg] ?: pkg,
                taskCount = countByPkg[pkg] ?: 0,
                isSystem = infos[pkg]?.isSystem ?: false,
                installTime = installTimes[pkg] ?: 0L,
                installed = true,
                version = versionsByPkg[pkg] ?: "",
            )
        }.sortedWith(compareBy<AppRow> { it.packageName.isNotEmpty() }.thenBy { it.label.lowercase() })
    }
    // 应用图标缓存:后台逐包加载,每 20 个回写一次增量刷新(列表图标逐步出现,不整批等完);
    // 仅在「应用」tab 才开始逐包取图标,首页不触发任何包管理器批量查询
    var iconsByPkg by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(allApps, tab) {
        if (tab != 1) return@LaunchedEffect
        val pending = allApps.map { it.packageName }.filterNot { iconsByPkg.containsKey(it) }
        if (pending.isEmpty()) return@LaunchedEffect
        val pm = context.packageManager
        // 绘制尺寸 = 显示尺寸 40dp × 屏幕密度,避免低分辨率位图被拉伸发虚
        val size = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val loaded = HashMap(iconsByPkg)
        pending.chunked(20).forEach { chunk ->
            withContext(Dispatchers.Default) {
                chunk.forEach { pkg ->
                    runCatching {
                        val d = pm.getApplicationIcon(pkg)
                        val b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(b)
                        d.setBounds(0, 0, size, size)
                        d.draw(canvas)
                        loaded[pkg] = b.asImageBitmap()
                    }  // 单包失败跳过,UI 显示占位图标
                }
            }
            iconsByPkg = loaded.toMap()
        }
    }

    // 禁网状态校正轮询:持久化清单为状态来源,查询仅补充确认(外部已禁网的包同步进 UI);
    // 查询不可靠(binder 抖动/输出缺失),故失败或"allow"都不删清单项,也不落盘
    LaunchedEffect(tab, appTab, installedApps) {
        if (tab != 1) return@LaunchedEffect
        val infos = installedApps ?: return@LaunchedEffect
        val wantSystem = appTab == 1
        val pkgs = infos.filter { it.isSystem == wantSystem }.map { it.packageName }
        if (pkgs.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(10_000)
            val confirmed = withContext(Dispatchers.IO) { queryNetworkBlockedBatch(pkgs) }
            val newlyBlocked = confirmed.filter { it.value }.keys - netBlockedPkgs
            if (newlyBlocked.isNotEmpty()) netBlockedPkgs = netBlockedPkgs + newlyBlocked
        }
    }

    automation.observe(lifecycleOwner) { state ->
        uiState = state
    }

    Scaffold(
        bottomBar = {
            // 任务页独立显示时隐藏底部导航,全屏沉浸
            if (taskPagePkg == null) {
                // 导航配色:surfaceContainer 平底 + primaryContainer 选中指示,与应用强调色统一
                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("首页") },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Apps, contentDescription = null) },
                        label = { Text("应用") },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("规则") },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                        label = { Text("日志") },
                        colors = navItemColors,
                    )
                }
            }
        },
    ) { innerPadding ->
        when (tab) {
            1 -> if (taskPagePkg != null) {
                val editId = editingRuleId
                if (editId != null) {
                    // 规则编辑页(新建时 existing 为 null)
                    RuleEditorPage(
                        pkg = taskPagePkg!!,
                        existing = tasks.firstOrNull { it.id == editId },
                        onSave = { saved ->
                            persistTasks(
                                if (tasks.any { it.id == saved.id }) {
                                    tasks.map { if (it.id == saved.id) saved else it }
                                } else {
                                    tasks + saved
                                },
                            )
                            editingRuleId = null
                        },
                        onBack = {
                            // 规则页返回 = 直接回应用列表(跳过任务页),列表滚动位置由提升的 state 恢复
                            editingRuleId = null
                            taskPagePkg = null
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    TaskListPage(
                        pkg = taskPagePkg!!,
                        tasks = tasks,
                        onBack = { taskPagePkg = null },
                        onToggleTask = { id, enabled ->
                            persistTasks(tasks.map { if (it.id == id) it.copy(enabled = enabled) else it })
                        },
                        onPersistTasks = { persistTasks(it) },
                        onNewTask = { editingRuleId = "new" },
                        onEditTask = { editingRuleId = it },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            } else {
                AppsScreen(
                    listState = appsListState,
                    allApps = allApps,
                    iconsByPkg = iconsByPkg,
                    loading = installedApps == null,
                    query = query,
                    onQueryChange = { query = it },
                    onOpenApp = { taskPagePkg = it },
                    appTab = appTab,
                    onAppTabChange = { appTab = it },
                    sortMode = sortMode,
                    onSortModeChange = {
                        sortMode = it
                        sortModePrefs.edit().putInt("sort_mode", it).apply()
                    },
                    netBlockedPkgs = netBlockedPkgs,
                    onNetworkBlocked = { pkg, blocked ->
                        val cur = netBlockedPkgs.toMutableSet()
                        if (blocked) cur += pkg else cur -= pkg
                        netBlockedPkgs = cur
                        saveNetBlocked(context, cur)
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            2 -> {
                val editId = editingRuleId
                if (editId != null) {
                    // 规则导航页 → 编辑页;返回回到规则列表
                    RuleEditorPage(
                        pkg = tasks.firstOrNull { it.id == editId }?.packageName.orEmpty(),
                        existing = tasks.firstOrNull { it.id == editId },
                        onSave = { saved ->
                            persistTasks(
                                if (tasks.any { it.id == saved.id }) {
                                    tasks.map { if (it.id == saved.id) saved else it }
                                } else {
                                    tasks + saved
                                },
                            )
                            editingRuleId = null
                        },
                        onBack = { editingRuleId = null },
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    AllRulesPage(
                        tasks = tasks,
                        installedApps = installedApps,
                        labelByPkg = labelByPkg,
                        onToggleTask = { id, enabled ->
                            persistTasks(tasks.map { if (it.id == id) it.copy(enabled = enabled) else it })
                        },
                        onEditTask = { editingRuleId = it },
                        onPersistTasks = { persistTasks(it) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
            3 -> LogsScreen(modifier = Modifier.padding(innerPadding))
            else -> HomeScreen(
                context = context,
                uiState = uiState,
                serviceBound = serviceBound,
                overlayOn = overlayOn,
                subCount = subCount,
                onOverlayToggle = {
                    val target = !overlayOn
                    val ok = DramaAccessibilityService.instance
                        ?.showDebugOverlay(target) == true
                    if (ok) overlayOn = target
                },
                onToggleAccessibility = {
                    // 缺「写入安全设置」权限时先走 Shizuku 授权并自动代授,成功后继续开无障碍;
                    // Shizuku 不可用才退回弹窗跳系统无障碍设置
                    if (uiState.hasSecureSetting) {
                        automation.toggleAccessibility()
                    } else {
                        automation.requestShizuku(
                            onReady = { automation.toggleAccessibility() },
                            onFail = { showA11yConfirm = true },
                        )
                    }
                },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // 确认框:缺权限时是否跳转系统无障碍设置页
    if (showA11yConfirm) {
        AlertDialog(
            onDismissRequest = { showA11yConfirm = false },
            title = { Text(context.getString(R.string.confirm_a11y_title)) },
            text = { Text(context.getString(R.string.confirm_a11y_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showA11yConfirm = false
                    automation.openAccessibilitySettings()
                }) {
                    Text(context.getString(R.string.confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showA11yConfirm = false }) {
                    Text(context.getString(R.string.confirm_cancel))
                }
            },
        )
    }
}

/** 主页:Bento 网格 —— 左侧服务大卡(跨高) + 右侧功能小卡 + 底部权限管理 */
@Composable
private fun HomeScreen(
    context: android.content.Context,
    uiState: AutomationManager.State,
    serviceBound: Boolean,
    overlayOn: Boolean,
    subCount: Int?,
    onOverlayToggle: () -> Unit,
    onToggleAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 已连接 = 设置已开 且 服务实例已绑定;待连接 = 设置已开但实例尚未绑定(中间态)
    val serviceConnected = uiState.isAccessibilityEnabled && serviceBound
    val servicePending = uiState.isAccessibilityEnabled && !serviceConnected
    var recentsHidden by remember { mutableStateOf(isRecentsHidden(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            // 底部留白:权限管理卡片不贴屏幕底(兼顾导航条遮挡)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleBar(serviceConnected = serviceConnected, servicePending = servicePending)

        // 中控面板:电源拨盘 + 状态文案
        ControlPanel(
            serviceConnected = serviceConnected,
            servicePending = servicePending,
            accessibilityEnabled = uiState.isAccessibilityEnabled,
            hasSecureSetting = uiState.hasSecureSetting,
            onToggle = onToggleAccessibility,
        )

        // 功能切换胶囊:竖排,避免长标题在半宽胶囊里横向挤压
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TogglePill(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Adjust,
                accent = MaterialTheme.colorScheme.tertiary,
                title = "节点悬浮窗",
                description = "查看屏幕节点,辅助编写选择器",
                checked = overlayOn,
                enabled = uiState.isAccessibilityEnabled,
                onToggle = onOverlayToggle,
            )
            TogglePill(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Layers,
                accent = MaterialTheme.colorScheme.secondary,
                title = "隐藏最近任务",
                description = "从最近任务列表中排除本应用",
                checked = recentsHidden,
                enabled = true,
                onToggle = {
                    val target = !recentsHidden
                    setRecentsHidden(context, target)
                    recentsHidden = target
                },
            )
        }

        // 远程订阅入口:已订阅数量,点击打开管理(URL 添加/刷新/删除)
        var subDialogOpen by remember { mutableStateOf(false) }
        Card(
            onClick = { subDialogOpen = true },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentIcon(
                    imageVector = Icons.Filled.CloudDownload,
                    accent = MaterialTheme.colorScheme.primary,
                    size = 40.dp,
                    iconSize = 22.dp,
                    shape = CircleShape,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "远程订阅",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (subCount != null && subCount > 0) "已订阅 $subCount 个,点击管理"
                               else "添加 GKD 订阅链接,自动导入规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (subDialogOpen) {
            SubscriptionManageDialog(onDismiss = { subDialogOpen = false })
        }

        // 规则文档入口:打开内置规则引擎文档(markdown 阅读)
        var showDocs by remember { mutableStateOf(false) }
        Card(
            onClick = { showDocs = true },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentIcon(
                    imageVector = Icons.AutoMirrored.Filled.Article,
                    accent = MaterialTheme.colorScheme.secondary,
                    size = 40.dp,
                    iconSize = 22.dp,
                    shape = CircleShape,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "规则文档",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "规则编写语法、调度参数与订阅格式说明",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showDocs) {
            // 全屏 Dialog 承载:文档页有独立 Scaffold,不能放进外层 verticalScroll Column
            //(无限高度约束会让 Scaffold 内容测量越界崩溃)
            Dialog(
                onDismissRequest = { showDocs = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                RulesDocPage(onClose = { showDocs = false })
            }
        }

        // 权限管理入口
        PermissionManageCard(context)
    }
}

/**
 * 中控面板:中央大号电源拨盘(状态光环 + 光晕,点击整盘启停),
 * 下方居中状态文案。
 */
@Composable
private fun ControlPanel(
    serviceConnected: Boolean,
    servicePending: Boolean,
    accessibilityEnabled: Boolean,
    hasSecureSetting: Boolean,
    onToggle: () -> Unit,
) {
    // 已连接 = 主色;待连接 = 三级色 + 旋转光环;未开启 = 灰
    val accent = when {
        serviceConnected -> MaterialTheme.colorScheme.primary
        servicePending -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 电源拨盘:光晕 + 光环 + 中央按钮
            Box(
                modifier = Modifier.size(128.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 光晕:运行中才显示
                if (serviceConnected) {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(accent.copy(alpha = 0.25f), Color.Transparent),
                                ),
                            ),
                    )
                }
                // 状态光环:已连接满环;待连接旋转(不确定进度);未开启空环
                CircularProgressIndicator(
                    progress = { if (serviceConnected) 1f else 0f },
                    modifier = Modifier.size(128.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    strokeWidth = 4.dp,
                )
                if (servicePending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(128.dp),
                        color = accent,
                        trackColor = Color.Transparent,
                        strokeWidth = 4.dp,
                    )
                }
                // 中央电源按钮
                Surface(
                    onClick = onToggle,
                    shape = CircleShape,
                    color = if (serviceConnected) {
                        accent
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(96.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = "启停无障碍服务",
                            modifier = Modifier.size(44.dp),
                            tint = if (serviceConnected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = when {
                    serviceConnected -> "服务运行中"
                    servicePending -> "服务待连接"
                    else -> "服务未开启"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 功能切换胶囊:整胶囊可点,强调色圆点 + 图标 + 标题(+ 可选描述),开启时染上强调色 */
@Composable
private fun TogglePill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (checked) {
            accent.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (description != null) 10.dp else 0.dp)
                .then(if (description == null) Modifier.height(64.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (description != null) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** 强调色图标容器:主页中控风格 —— 强调色圆角/圆底 + 白色图标,供其它页面统一 */
@Composable
private fun AccentIcon(
    imageVector: ImageVector,
    accent: Color,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = MaterialTheme.shapes.medium,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = tint,
        )
    }
}

/** 状态圆点:active=true 用强调色,否则灰,表达项级开关/授权状态 */
@Composable
private fun StatusDot(active: Boolean, accent: Color, size: Dp = 10.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                if (active) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
    )
}

/** 来源角标:远程订阅导入(SUB) / 本地自定义(自定义);区分规则来源 */
@Composable
private fun SourceBadge(isRemote: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isRemote) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = if (isRemote) "订阅" else "自定义",
            style = MaterialTheme.typography.labelSmall,
            color = if (isRemote) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 规则是否来自远程订阅导入(SubscriptionStore/gkd 导入均有 id 前缀;本地新建 = task_/gkd_ 无 sub 标记) */
private val GkdTask.isFromSubscription: Boolean
    get() = id.startsWith("sub_")

/** 包名显示名:直接查已装应用标签,查不到回退包名 */
private fun appLabel(context: android.content.Context, pkg: String, sample: GkdTask?): String {
    // 尝试从已装应用解析显示名(缓存成本低,chip 数量有限)
    val pm = context.packageManager
    return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
        .getOrDefault(pkg)
}

/** 应用条目:承载 packageName / label / taskCount / isSystem / installTime / installed(残留订阅规则指向未装应用时 false) */
private data class AppRow(
    val packageName: String,
    val label: String,
    val taskCount: Int,
    val isSystem: Boolean,
    val installTime: Long = 0L,
    val installed: Boolean = true,
    val version: String = "",
)

/** 场景流步骤编辑草稿:文本态字段,保存时解析为 GkdTask.Step */
private data class StepDraft(
    val name: String,
    val matchesText: String,
    val actionKind: String,
    val waitTimeoutText: String,
    val settleTimeText: String,
    val skipOnTimeout: Boolean,
    val triggerOnAbsent: Boolean = false,
)

/** 居中的占位内容(加载/空状态) */
@Composable
private fun CenteredPlaceholder(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/** 应用列表加载图标:2×2 应用宫格,四格波纹式渐亮渐灭 */
@Composable
private fun AppsLoadingIcon() {
    val accent = MaterialTheme.colorScheme.primary
    val anim = rememberInfiniteTransition()
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
            label = "wave",
        ).value
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0.05f)))),
        contentAlignment = Alignment.Center,
    ) {
        val gap = 6.dp
        val tile = 15.dp
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                tileWaveBox(accent, anim, 0, tile)
                tileWaveBox(accent, anim, 2, tile)
            }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                tileWaveBox(accent, anim, 1, tile)
                tileWaveBox(accent, anim, 3, tile)
            }
        }
    }
}

@Composable
private fun tileWaveBox(color: Color, wave: Float, index: Int, size: Dp) {
    // 四格相位错开,亮→暗循环,形成流动的波纹
    val a = (wave + index * 0.25f) % 1f
    val alpha = 0.25f + 0.75f * (0.5f + 0.5f * Math.cos(a * Math.PI * 2).toFloat())
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = alpha)),
    )
}

/** 应用列表空状态图标:应用宫格,搜索无结果时叠加放大镜角标 */
@Composable
private fun AppsEmptyIcon(searching: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0.05f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = null,
            tint = accent.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp),
        )
        if (searching) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 应用 tab:用户应用 / 系统应用分 tab,标题栏内搜索;点应用进入该应用的独立任务页,长按弹出应用信息抽屉 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppsScreen(
    listState: androidx.compose.foundation.lazy.LazyListState,
    allApps: List<AppRow>,
    iconsByPkg: Map<String, ImageBitmap>,
    loading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    appTab: Int,
    onAppTabChange: (Int) -> Unit,
    sortMode: Int,
    onSortModeChange: (Int) -> Unit,
    netBlockedPkgs: Set<String>,
    onNetworkBlocked: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 长按信息抽屉:非空 = 正在展示该包名的应用信息底部抽屉
    var infoSheetPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    // 仅按应用名 / 包名过滤(标题栏搜索框与列表共用)
    val searchedApps = remember(allApps, query) {
        if (query.isBlank()) allApps else allApps.filter { app ->
            app.label.contains(query, true) || app.packageName.contains(query, true)
        }
    }
    // 中文按拼音排序(Collator),否则默认码点序汉字不是字典序
    val collator = remember { java.text.Collator.getInstance(java.util.Locale.CHINA) }
    // 列表 = 搜索过滤 + 排序 + 按 tab 严格过滤(用户/系统不混排)
    val visibleApps = remember(searchedApps, appTab, sortMode) {
        val filtered = searchedApps.filter { app ->
            (appTab == 1) == app.isSystem
        }
        // 未安装应用统一排在已安装后面,组内仍按所选排序
        val sorted = when (sortMode) {
            1 -> filtered.sortedWith(compareByDescending(collator) { it.label })
            2 -> filtered.sortedWith(
                compareByDescending<AppRow> { it.taskCount }.thenBy(collator) { it.label },
            )
            3 -> filtered.sortedByDescending { it.installTime }
            else -> filtered.sortedWith(compareBy(collator) { it.label })
        }
        sorted.sortedBy { it.installed.not() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 紧凑顶栏:搜索框 + 排序菜单
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜索应用名 / 包名", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空搜索")
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        )
        // 排序菜单:名称升/降序、任务数多在前
        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "排序")
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("按名称升序") },
                    onClick = { onSortModeChange(0); sortMenuOpen = false },
                    trailingIcon = { if (sortMode == 0) Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) },
                )
                DropdownMenuItem(
                    text = { Text("按名称降序") },
                    onClick = { onSortModeChange(1); sortMenuOpen = false },
                    trailingIcon = { if (sortMode == 1) Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) },
                )
                DropdownMenuItem(
                    text = { Text("按任务数排序") },
                    onClick = { onSortModeChange(2); sortMenuOpen = false },
                    trailingIcon = { if (sortMode == 2) Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) },
                )
                DropdownMenuItem(
                    text = { Text("按安装时间(新→旧)") },
                    onClick = { onSortModeChange(3); sortMenuOpen = false },
                    trailingIcon = { if (sortMode == 3) Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) },
                )
            }
        }
        }

        // 用户应用 / 系统应用分段
        PrimaryTabRow(
            selectedTabIndex = appTab,
        ) {
            Tab(
                selected = appTab == 0,
                onClick = { onAppTabChange(0) },
                text = { Text("用户应用") },
            )
            Tab(
                selected = appTab == 1,
                onClick = { onAppTabChange(1) },
                text = { Text("系统应用") },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 滚动状态由上层传入:进入任务页/规则页返回后恢复上一次滚动位置
        when {
            loading -> CenteredPlaceholder {
                AppsLoadingIcon()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在加载应用列表…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            visibleApps.isEmpty() -> CenteredPlaceholder {
                AppsEmptyIcon(searching = query.isNotBlank())
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (query.isBlank()) "未找到任何应用" else "无匹配应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleApps.size, key = { visibleApps[it].packageName }, contentType = { "app" }) { i ->
                        val app = visibleApps[i]
                        AppListCard(
                            app = app,
                            icon = iconsByPkg[app.packageName],
                            blocked = netBlockedPkgs.contains(app.packageName),
                            onClick = { onOpenApp(app.packageName) },
                            onLongClick = { infoSheetPkg = app.packageName },
                            onToggleNetwork = { target ->
                                onNetworkBlocked(app.packageName, target)
                            },
                        )
                    }
                }
                // 快速滑动条:覆盖在列表右侧,拖动跳转
                FastScrollbar(
                    listState = listState,
                    itemCount = visibleApps.size,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }

    // 长按弹出的应用信息/功能开关抽屉
    infoSheetPkg?.let { pkg ->
        AppInfoSheet(
            pkg = pkg,
            initialBlocked = netBlockedPkgs.contains(pkg),
            onBlockedChange = { blocked -> onNetworkBlocked(pkg, blocked) },
            onDismiss = { infoSheetPkg = null },
        )
    }
}

/** 应用列表卡片:应用图标(禁网压暗+红色角标)+ 名称/包名 + 「联网/已禁网」胶囊开关;点击进入任务页,长按弹信息抽屉 */
@Composable
private fun AppListCard(
    app: AppRow,
    icon: ImageBitmap?,
    blocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleNetwork: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // 联网开关本地状态:实际下命令成功后才回调上层;失败回滚
    var netEnabled by remember(app.packageName) { mutableStateOf(!blocked) }
    LaunchedEffect(blocked) { netEnabled = !blocked }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    // 卡片背景:禁网 = 左侧红色渐隐(无描边,纯色块区分)
    val cardBrush = if (blocked) {
        Brush.horizontalGradient(
            listOf(scheme.error.copy(alpha = 0.16f), scheme.error.copy(alpha = 0.04f), scheme.surfaceVariant.copy(alpha = 0.3f))
        )
    } else {
        Brush.horizontalGradient(listOf(scheme.surfaceVariant.copy(alpha = 0.38f), scheme.surfaceVariant.copy(alpha = 0.25f)))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(cardBrush)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 应用图标:40dp 圆角,禁网时压暗 + 右下角红色断开角标
            Box {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .then(if (blocked) Modifier.alpha(0.5f) else Modifier),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(scheme.surfaceVariant.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Apps,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp).then(if (blocked) Modifier.alpha(0.5f) else Modifier),
                        )
                    }
                }
                if (blocked) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = "已禁网",
                        tint = scheme.onError,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(scheme.error)
                            .padding(1.dp)
                            .clip(CircleShape)
                            .align(Alignment.BottomEnd),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 名称 + 包名/规则数
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (app.installed) scheme.onSurface else scheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 版本号标签:紧跟应用名同行;空间不足时优先截断版本号尾部,应用名不截断
                    if (app.version.isNotEmpty()) {
                        Surface(
                            color = scheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = scheme.onSurfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                text = "v${app.version}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (app.taskCount > 0) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Rule,
                            contentDescription = null,
                            tint = if (blocked) scheme.error else scheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "${app.taskCount} 条规则",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (app.installed) scheme.onSurfaceVariant else scheme.outline,
                        )
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (app.installed) scheme.onSurfaceVariant.copy(alpha = 0.8f)
                                else scheme.outline.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            // 联网开关:图标+文字胶囊,联网=强调色浅底,禁网=错误色实底(无描边)
            Surface(
                color = if (netEnabled) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.error,
                contentColor = if (netEnabled) scheme.onPrimaryContainer else scheme.onError,
                shape = CircleShape,
                modifier = Modifier.clickable {
                    // netEnabled = 期望的联网态;setNetworkBlocked/onToggleNetwork 传的是禁网态(取反)
                    val targetEnabled = !netEnabled
                    val targetBlocked = !targetEnabled
                    ensureShizukuForNetwork(
                        onReady = {
                            netEnabled = targetEnabled
                            scope.launch {
                                val err = withContext(Dispatchers.IO) {
                                    setNetworkBlocked(app.packageName, targetBlocked, context.applicationContext)
                                }
                                // 禁网诊断日志:开关命令结果(成功/失败 + 原因)
                                LogStore.log(
                                    if (err == null) "⇅ 禁网设置:${app.packageName} → ${if (targetBlocked) "禁用" else "允许"}"
                                    else "⇅ 禁网设置失败:${app.packageName} → $err",
                                )
                                if (err == null) {
                                    onToggleNetwork(targetBlocked)
                                } else {
                                    netEnabled = !targetEnabled
                                    Toast.makeText(context, "禁网设置失败: $err", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onFail = { msg ->
                            LogStore.log("⇅ 禁网设置失败:${app.packageName} → $msg")
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = if (netEnabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = if (netEnabled) "禁用网络" else "已禁网",
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = if (netEnabled) "联网" else "已禁网",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** 长按应用弹出的底部抽屉:应用信息 + 功能开关;禁网状态由上层传入,变更回调同步到列表角标 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppInfoSheet(
    pkg: String,
    initialBlocked: Boolean,
    onBlockedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 应用信息:显示名 + 版本号,查不到回退包名
    val (label, version) = remember(pkg) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            val ver = context.packageManager.getPackageInfo(pkg, 0).versionName ?: "未知"
            context.packageManager.getApplicationLabel(info).toString() to ver
        }.getOrDefault(pkg to "未知")
    }

    // 功能开关状态:初始值来自上层清单,实际下命令成功后才回调上层同步
    var disableNetwork by remember(pkg) { mutableStateOf(initialBlocked) }
    val scope = rememberCoroutineScope()
    // 统一下发禁网命令:成功才回传上层,失败回滚 + Toast
    fun toggleBlocked(target: Boolean) {
        ensureShizukuForNetwork(
            onReady = {
                disableNetwork = target
                scope.launch {
                    val err = withContext(Dispatchers.IO) { setNetworkBlocked(pkg, target, context.applicationContext) }
                    LogStore.log(
                        if (err == null) "⇅ 禁网设置:$pkg → ${if (target) "禁用" else "允许"}"
                        else "⇅ 禁网设置失败:$pkg → $err",
                    )
                    if (err == null) {
                        onBlockedChange(target)
                    } else {
                        disableNetwork = !target
                        Toast.makeText(context, "禁网设置失败: $err", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onFail = { msg ->
                LogStore.log("⇅ 禁网设置失败:$pkg → $msg")
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 应用信息头
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "v$version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // 禁网状态卡:错误色容器 + 状态图标 + 说明 + 开关,整卡点按切换
            Surface(
                color = if (disableNetwork) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.large)
                    .combinedClickable(onClick = { toggleBlocked(!disableNetwork) }),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = if (disableNetwork) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (disableNetwork) MaterialTheme.colorScheme.onError
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (disableNetwork) Icons.Filled.WifiOff else Icons.Filled.Wifi,
                                contentDescription = "网络状态",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "网络状态",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Surface(
                                color = if (disableNetwork) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (disableNetwork) MaterialTheme.colorScheme.onError
                                        else MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = if (disableNetwork) "已禁网" else "联网中",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = "chain-3 平台防火墙,前台+后台全拦截,需 Shizuku",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = disableNetwork,
                        onCheckedChange = { target -> toggleBlocked(target) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.error,
                            checkedThumbColor = MaterialTheme.colorScheme.onError,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 彻底断网:connectivity chain-3(Android 11+ 平台防火墙,shell 身份可执行)。
 * 按 appId 在系统层拦截应用联网,前台后台都拦;无需 root、无 VPN 隧道。
 * 真实状态以系统为准,由界面经 get-package-networking-enabled 实时查询。
 */

/**
 * 确保禁网操作前 Shizuku 可用:已连接且已授权直接执行 onReady;
 * 未连接提示启动 Shizuku;未授权直接弹授权申请框,拒绝/失败才回调 onFail。
 */
private fun ensureShizukuForNetwork(
    onReady: () -> Unit,
    onFail: (String) -> Unit,
) {
    val binderReady = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }
    if (!binderReady) {
        onFail("请先打开 Shizuku 应用,点击「启动」后重试")
        return
    }
    val granted = try {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }
    if (granted) {
        onReady()
        return
    }
    // 未授权:发起授权申请,结果经监听器异步返回(一次性,收到即注销)
    try {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    onReady()
                } else {
                    onFail("Shizuku 授权被拒绝,请重新授权后重试")
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(0)
    } catch (e: Throwable) {
        onFail("发起 Shizuku 授权失败:${e.message}")
    }
}

/** chain-3 开关:启用防火墙框架;失败返回 false */
private fun enableChain3(): Boolean =
    ShizukuShell.exec(arrayOf("cmd", "connectivity", "set-chain3-enabled", "true")).ok

/** 开关包名的联网;返回 null = 成功,否则为失败原因。
 * 经 Shizuku 逐条 exec 偶发 exit=255/空 stderr(binder 抖动),故:
 * 合并为单条 shell 命令减少往返;失败重试;最终以 get 回读确认为准。
 * 全局互斥锁:连续对多个应用禁网时各处并发进入,多个 IRemoteProcess 同时跑会互相挤掉
 * (Shizuku binder 单通道,并发 transact 触发 DeadObject/255),在此串行化。 */
private val netOpMutex = kotlinx.coroutines.sync.Mutex()

private suspend fun setNetworkBlocked(pkg: String, blocked: Boolean, appContext: android.content.Context): String? =
    netOpMutex.withLock {
        setNetworkBlockedLocked(pkg, blocked, appContext)
    }

private fun setNetworkBlockedLocked(pkg: String, blocked: Boolean, appContext: android.content.Context): String? {
    if (!ShizukuShell.isReady()) return "Shizuku 未就绪或未授权"
    // 系统应用(appId 1000)平台拒绝设置(ShizuWall 同款预检),直接报错不重试
    val appId = runCatching {
        appContext.packageManager.getApplicationInfo(pkg, 0).uid % 100000
    }.getOrNull() ?: 0
    if (appId == 1000) return "系统应用(appId 1000)不支持禁网"
    val target = if (blocked) "false" else "true"
    var lastErr = ""
    repeat(3) { attempt ->
        if (attempt > 0) Thread.sleep(400L * attempt)  // 退避重试;调用方在 IO 线程,可阻塞
        val r = ShizukuShell.exec(
            arrayOf(
                "sh", "-c",
                "cmd connectivity set-chain3-enabled true; cmd connectivity set-package-networking-enabled $target $pkg",
            ),
        )
        if (r.ok) {
            // 回读确认:系统侧接受命令即认为成功
            val confirmed = queryNetworkBlocked(pkg)
            if (confirmed == blocked) return null
            // ShizuWall 同款:suidownermap 缺项视为已生效(应用从未联网过时系统无条目)
            val errText = (r.stderr + r.stdout).lowercase()
            if ("suidownermap does not have entry for uid" in errText) return null
            lastErr = "回读不符(实际${if (confirmed == true) "禁网" else "联网/未知"})"
        } else {
            val errText = (r.stderr + r.stdout).lowercase()
            if ("suidownermap does not have entry for uid" in errText) return null
            if ("can't set package firewall rule for system app" in errText) return "系统应用不支持禁网"
            lastErr = r.stderr.ifBlank { "exit=${r.exitCode}" }
        }
    }
    return "命令失败: $lastErr"
}

/**
 * 查询包名禁网状态:cmd connectivity get-package-networking-enabled
 * 设备实际输出为「<pkg>:allow」或「<pkg>:deny」(空输出/异常 = 未知态,返回 null)。
 */
private fun queryNetworkBlocked(pkg: String): Boolean? {
    if (!ShizukuShell.isReady()) return null
    val r = runCatching {
        ShizukuShell.exec(arrayOf("cmd", "connectivity", "get-package-networking-enabled", pkg))
    }.getOrNull() ?: return null
    if (!r.ok) return null
    val out = r.stdout.trim().lowercase()
    return when {
        out.endsWith(":deny") -> true
        out.endsWith(":allow") -> false
        else -> null
    }
}

/**
 * 批量查询禁网状态(ShizuWall 同款批处理思路):单条 shell for 循环一次 exec
 * 查完所有包,避免逐包开 IRemoteProcess 把 Shizuku binder 打挂(DeadObjectException)。
 * 返回 pkg → 禁网态;输出缺失/解析失败的包不出现在结果里(调用方保留旧值)。
 */
private fun queryNetworkBlockedBatch(pkgs: List<String>): Map<String, Boolean> {
    if (pkgs.isEmpty() || !ShizukuShell.isReady()) return emptyMap()
    val results = mutableMapOf<String, Boolean>()
    // 单条命令长度预算(照抄 ShizuWall MAX_BATCH_COMMAND_LENGTH 4096)
    val current = StringBuilder()
    fun flush() {
        if (current.isEmpty()) return
        val script = StringBuilder("true; for p in").append(current)
            .append("; do cmd connectivity get-package-networking-enabled \$p; done")
        val r = runCatching {
            ShizukuShell.exec(arrayOf("sh", "-c", script.toString()))
        }.getOrNull() ?: return
        // 单包 NameNotFound 只影响该行输出,for 循环继续;末尾 true 保证整体 exit=0
        r.stdout.lineSequence().forEach { line ->
            val t = line.trim().lowercase()
            val idx = t.lastIndexOf(':')
            if (idx <= 0) return@forEach
            when (t.substring(idx + 1)) {
                "deny" -> results[t.substring(0, idx)] = true
                "allow" -> results[t.substring(0, idx)] = false
            }
        }
        current.clear()
    }
    pkgs.forEach { pkg ->
        if (current.length + pkg.length + 1 > 3900) flush()
        current.append(' ').append(pkg)
    }
    flush()
    return results
}

/** 抽屉里的单行功能开关:强调色圆图标 + 标题/说明 + 开关,整行可点,开启时染上强调色 */
@Composable
private fun AppToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.tertiary
    Surface(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (checked) {
            accent.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentIcon(
                imageVector = if (checked) Icons.Filled.Block else Icons.Filled.Language,
                accent = if (checked) accent else MaterialTheme.colorScheme.surfaceVariant,
                size = 38.dp,
                iconSize = 20.dp,
                shape = CircleShape,
                tint = if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

/** 规则导航页:所有规则一屏展示,同名规则按标题合并(内容不合并)。
 * 全局规则(packageName 空)永远第一;合并卡展开显示所属应用,点击进入对应规则。
 */
@Composable
private fun AllRulesPage(
    tasks: List<GkdTask>,
    installedApps: List<TaskStore.AppInfo>?,
    labelByPkg: Map<String, String>,
    onToggleTask: (String, Boolean) -> Unit,
    onEditTask: (String) -> Unit,
    onPersistTasks: (List<GkdTask>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 长按删除确认:记录待删除任务 id
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    pendingDeleteId?.let { delId ->
        val del = tasks.firstOrNull { it.id == delId } ?: return@let
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除规则") },
            text = { Text("确定删除「${del.name.ifEmpty { del.actionsSummary }}」?") },
            confirmButton = {
                TextButton(onClick = {
                    onPersistTasks(tasks - del)
                    pendingDeleteId = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏:标题 + 规则总数
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "全部规则",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${tasks.count { it.packageName.isEmpty() || installedApps?.any { a -> a.packageName == it.packageName } != false }} 条",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 列表滚动状态:快速滑动条拖动定位用
        val listState = rememberLazyListState()
        // 三大分组:全局 / 订阅 / 自定义,全局第一、自定义最后;
        // 未安装应用的规则不展示(规则仍保留在任务表中,装回后自动恢复)
        val installedPkgs = (installedApps ?: emptyList()).map { it.packageName }.toSet()
        val visibleTasks = tasks.filter { it.packageName.isEmpty() || it.packageName in installedPkgs }
        // 分组:全局(packageName 空)/ 订阅(id 前缀 sub_)/ 自定义(其余),组内同名再合并
        val sections = listOf(
            RuleSection("全局规则", visibleTasks.filter { it.packageName.isEmpty() }, "global", Icons.Filled.Home),
            RuleSection("订阅规则", visibleTasks.filter { it.packageName.isNotEmpty() && it.isFromSubscription }, "sub", Icons.Filled.CloudDownload),
            RuleSection("自定义规则", visibleTasks.filter { it.packageName.isNotEmpty() && !it.isFromSubscription }, "custom", Icons.AutoMirrored.Filled.Rule),
        ).filter { it.tasks.isNotEmpty() }
        // 展开状态:默认全部收起;key = 分组 key,SharedPreferences 持久化(跨进程保留)
        val sectionPrefs = context.getSharedPreferences("rules_ui", android.content.Context.MODE_PRIVATE)
        var expandedSections by remember {
            mutableStateOf(sectionPrefs.getStringSet("expanded_sections_all", emptySet())!!.toSet())
        }
        LaunchedEffect(expandedSections) {
            sectionPrefs.edit().putStringSet("expanded_sections_all", expandedSections).apply()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            sections.forEach { sec ->
                val expanded = sec.key in expandedSections
                item(key = "sec_${sec.key}", contentType = "section") {
                    SectionHeader(
                        title = sec.title,
                        count = sec.tasks.size,
                        expanded = expanded,
                        icon = sec.icon,
                        onClick = {
                            expandedSections = if (expanded) expandedSections - sec.key
                            else expandedSections + sec.key
                        },
                    ) {
                        // 组内同名合并(与原逻辑一致:全局规则优先排前)
                        val merged = sec.tasks
                            .groupBy { it.name.ifEmpty { it.actionsSummary } }
                            .map { (title, list) ->
                                Triple(title, list.filter { it.packageName.isEmpty() } + list.filter { it.packageName.isNotEmpty() }, list.any { it.enabled })
                            }
                            .sortedWith(
                                compareByDescending<Triple<String, List<GkdTask>, Boolean>> { it.second.first().packageName.isEmpty() }
                                    .thenBy { it.first }
                            )
                        merged.forEach { (title, groupTasks, anyEnabled) ->
                            MergedRuleCard(
                                title = title,
                                groupTasks = groupTasks,
                                anyEnabled = anyEnabled,
                                labelByPkg = labelByPkg,
                                onToggleTask = onToggleTask,
                                onEditTask = onEditTask,
                                onLongPress = { pendingDeleteId = it },
                            )
                        }
                    }
                }
            }

            if (visibleTasks.isEmpty()) {
                item {
                    Text(
                        text = "暂无规则。在「应用」tab 选择应用后新建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            // 快速滑动条:覆盖在列表右侧,拖动跳转
            FastScrollbar(
                listState = listState,
                itemCount = listState.layoutInfo.totalItemsCount,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** 分区步骤项:分区 id + 标题 + 描述 + 图标(步骤头 / 底部步骤轨共用) */
private data class EditorSectionItem(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
)

/** 规则列表分组:标题 + 任务 + 分组 key + 图标(全局/订阅/自定义共用) */
private data class RuleSection(
    val title: String,
    val tasks: List<GkdTask>,
    val key: String,
    val icon: ImageVector,
)

/** 手风琴分组卡:可折叠分组(圆形图标 + 标题/描述 + 旋转箭头),展开显示正文 */
@Composable
private fun EditorAccordion(
    item: EditorSectionItem,
    expanded: Boolean,
    hasError: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasError -> cs.errorContainer.copy(alpha = 0.25f)
                expanded -> cs.primaryContainer.copy(alpha = 0.3f)
                else -> cs.surfaceVariant.copy(alpha = 0.25f)
            },
        ),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasError) cs.errorContainer
                            else if (expanded) cs.primary else cs.secondaryContainer,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (hasError) cs.onError
                        else if (expanded) cs.onPrimary else cs.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                if (hasError) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = cs.error,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .padding(end = 4.dp)
                        .rotate(arrowRotation),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(200)),
                exit = shrinkVertically(tween(200)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}
@Composable
private fun ExprField(
    value: String,
    onChange: (String) -> Unit,
    title: String,
    note: String? = null,
    singleLine: Boolean = false,
    numeric: Boolean = false,
    allowMinus: Boolean = false,
    maxLines: Int = 4,
    monospace: Boolean = false,
    error: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val isError = error != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) cs.error else cs.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isError) cs.errorContainer.copy(alpha = 0.35f)
                    else cs.surfaceVariant.copy(alpha = 0.4f),
                )
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = if (numeric) {
                    { onChange(it.filter { c -> c.isDigit() || (allowMinus && c == '-') }) }
                } else onChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = cs.onSurface,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                ),
                minLines = if (singleLine) 1 else maxLines,
                maxLines = if (singleLine) 1 else maxLines,
            )
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { onChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = "清空",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                // 占位保持高度一致(有/无清空按钮时输入框高度不变)
                Spacer(Modifier.size(26.dp))
            }
        }
        if (isError) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = cs.error,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                )
            }
        }
        if (note != null) InfoNote(note)
    }
}

/** 说明条:Info 图标 + 正文,承载所有原 hint/supporting 文案(Expressive:圆点标记,无填充) */
@Composable
private fun InfoNote(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier
                .size(15.dp)
                .padding(top = 1.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

/** 可点按选项卡:选中 = 柔和容器色填充 + 主色对勾,未选 = 描边(Expressive 标准 chip 形态) */
@Composable
private fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.45f),
        contentColor = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** 开关行:标签 + 说明 + 开关,整行可点(无背景填充,仅底部细分隔线) */
@Composable
private fun SwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    desc: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (desc != null) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
    }
}

/** 分区内小分组标题(调度与限频 / 时间窗 等):主色圆点 + 标题 + 尾部细线 */
@Composable
private fun SubSectionTitle(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(cs.primary),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(cs.outlineVariant.copy(alpha = 0.5f)),
        )
    }
}

/** 场景流步骤卡:折叠行 + 展开编辑区,可上移/下移/删除 */
@Composable
private fun StepCard(
    index: Int,
    totalSteps: Int,
    draft: StepDraft,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onChange: (StepDraft) -> Unit,
) {
    // 本步选择器逐行校验,错误显示在输入块下方
    val matchesError = draft.matchesText.lines()
        .mapIndexedNotNull { idx, line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("//")) null
            else runCatching { GkdSelector.parse(t) }.exceptionOrNull()?.let { "第 ${idx + 1} 行: ${it.message}" }
        }.firstOrNull()
    val cs = MaterialTheme.colorScheme
    // 展开箭头旋转动画(180°)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) cs.primaryContainer.copy(alpha = 0.35f)
            else cs.surfaceVariant.copy(alpha = 0.3f),
        ),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (expanded) cs.primary else cs.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (expanded) cs.onPrimary else cs.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.name.ifEmpty { "步骤${index + 1}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(GKD_ACTION_LABELS[draft.actionKind] ?: draft.actionKind)
                            val wt = draft.waitTimeoutText.toLongOrNull() ?: 0L
                            if (wt > 0) append(" · 等 ${wt / 1000}s")
                            if (draft.triggerOnAbsent) append(" · 反向")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (expanded) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(arrowRotation),
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                    ExprField(
                        value = draft.name,
                        onChange = { v -> onChange(draft.copy(name = v)) },
                        title = "步骤名(可选)",
                        singleLine = true,
                    )
                    ExprField(
                        value = draft.matchesText,
                        onChange = { v -> onChange(draft.copy(matchesText = v)) },
                        title = "选择器(每行一条,全部命中;留空 = 无条件步)",
                        note = "例: [text*=\"同意\"][clickable=true]",
                        monospace = true,
                        maxLines = 4,
                        error = matchesError,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "动作",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Action.entries.forEach { a ->
                                ChoiceChip(
                                    selected = draft.actionKind == a.gkd,
                                    onClick = { onChange(draft.copy(actionKind = a.gkd)) },
                                    label = GKD_ACTION_LABELS[a.gkd] ?: a.gkd,
                                )
                            }
                        }
                    }
                    ExprField(
                        value = draft.waitTimeoutText,
                        onChange = { v -> onChange(draft.copy(waitTimeoutText = v)) },
                        title = "等待目标出现超时 ms(0 = 只查当帧)",
                        singleLine = true,
                        numeric = true,
                    )
                    ExprField(
                        value = draft.settleTimeText,
                        onChange = { v -> onChange(draft.copy(settleTimeText = v)) },
                        title = "目标出现后稳定等待 ms(防动画中点空)",
                        singleLine = true,
                        numeric = true,
                    )
                    SwitchRow(
                        checked = draft.triggerOnAbsent,
                        onCheckedChange = { v -> onChange(draft.copy(triggerOnAbsent = v)) },
                        label = "反向触发",
                        desc = "节点消失后执行(仅返回/滑动/坐标类动作有效)",
                    )
                    SwitchRow(
                        checked = draft.skipOnTimeout,
                        onCheckedChange = { v -> onChange(draft.copy(skipOnTimeout = v)) },
                        label = "超时跳过",
                        desc = "关闭 = 超时终止整个流",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onMoveUp, enabled = index > 0) { Text("上移") }
                        OutlinedButton(onClick = onMoveDown, enabled = index < totalSteps - 1) { Text("下移") }
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = cs.error,
                            ),
                        ) { Text("删除") }
                    }
                }
            }
        }
    }
}

/** 模式选择大卡:单步规则 / 多步骤场景流二选一(Expressive:大圆角 + 主色圆形图标容器) */
@Composable
private fun ModeOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    desc: String,
    icon: ImageVector,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.35f),
        contentColor = if (selected) cs.onPrimaryContainer else cs.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) cs.primary else cs.surfaceVariant.copy(alpha = 0.6f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) cs.onPrimaryContainer.copy(alpha = 0.8f)
                    else cs.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) cs.primary else cs.outline,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 规则页分组标题行:与编辑器手风琴同款(圆底图标卡片 + 标题/条数 + 旋转箭头),点按切换折叠 */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) cs.primaryContainer.copy(alpha = 0.3f)
            else cs.surfaceVariant.copy(alpha = 0.25f),
        ),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (expanded) cs.primary else cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (expanded) cs.onPrimary else cs.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = cs.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 4.dp)
                    .rotate(arrowRotation),
            )
        }
        // 展开区:与头部同一卡片,展开时显示内容(编辑器手风琴同款动画)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)),
            exit = shrinkVertically(tween(200)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
        }
    }
}

/** 快速滑动条:右侧细轨道 + 滑块,拖动按比例跳转列表项 */
@Composable
private fun FastScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    if (itemCount < 8) return
    val scope = rememberCoroutineScope()
    // 拖动中:滑块高亮并直接定位;列表自身滚动时滑块同步位置
    var dragging by remember { mutableStateOf(false) }
    val canScroll = listState.canScrollForward || listState.canScrollBackward
    val thumbFraction = 0.15f

    fun jumpTo(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        val maxIndex = (itemCount - 1).coerceAtLeast(0)
        val target = (clamped * maxIndex).toInt().coerceIn(0, maxIndex)
        scope.launch { listState.scrollToItem(target) }
    }

    if (!canScroll) return
    BoxWithConstraints(modifier = modifier.width(28.dp).fillMaxHeightIfPossible()) {
        val density = LocalDensity.current
        val trackHeight = constraints.maxHeight.toFloat()
        val thumbHeight = (trackHeight * thumbFraction).coerceAtLeast(48f)
        val listFraction = if (itemCount > 1) {
            listState.firstVisibleItemIndex.toFloat() / (itemCount - 1)
        } else 0f
        val thumbY = (trackHeight - thumbHeight) * listFraction
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(with(density) { trackHeight.toDp() })
                .align(Alignment.Center)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        )
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(with(density) { thumbHeight.toDp() })
                .offset { IntOffset(0, thumbY.toInt()) }
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (dragging) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            // 按下位置直接映射到列表位置,开始拖动即跳转
                            jumpTo(offset.y / trackHeight)
                        },
                        onVerticalDrag = { change, _ ->
                            jumpTo(change.position.y / trackHeight)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
        )
    }
}

/** 同名合并的规则卡片:收起时一条摘要,展开后逐条显示所属应用,点击进入对应规则 */
@Composable
private fun MergedRuleCard(
    title: String,
    groupTasks: List<GkdTask>,
    anyEnabled: Boolean,
    labelByPkg: Map<String, String>,
    onToggleTask: (String, Boolean) -> Unit,
    onEditTask: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = if (anyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (anyEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            // 头部:点按展开/收起,长按删除组内全部规则
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = { groupTasks.forEach { onLongPress(it.id) } },
                    )
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = if (expanded) 6.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(active = anyEnabled, accent = accent, size = 9.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 组内含全局规则时标注
                if (groupTasks.any { it.packageName.isEmpty() }) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = "全局",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                // 合并条数 + 订阅角标(组内任一为订阅导入即标)
                SourceBadge(isRemote = groupTasks.any { it.isFromSubscription })
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = "${groupTasks.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 展开区:逐条列出,行内开关 + 点行进入编辑,长按删除该条
            if (expanded) {
                groupTasks.forEach { task ->
                    val rowAccent = if (task.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    val isGlobal = task.packageName.isEmpty()
                    val rowLabel = if (isGlobal) "全局规则" else (labelByPkg[task.packageName] ?: task.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onEditTask(task.id) },
                                onLongClick = { onLongPress(task.id) },
                            )
                            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(active = task.enabled, accent = rowAccent, size = 7.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rowLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    // 场景流显示步数,单步显示触发选择器
                                    val steps = task.steps
                                    if (steps?.isNotEmpty() == true) {
                                        append("${steps.size} 步场景流")
                                    } else {
                                        if (task.activityIds.isNotEmpty()) append("Activity≈${task.activityIds.first()} · ")
                                        val trigger = task.rules.firstOrNull()?.matches?.firstOrNull()
                                        append(if (trigger != null) "触发:${trigger.expr}" else "页面就绪即触发")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = task.enabled,
                            onCheckedChange = { onToggleTask(task.id, it) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** 独立任务页:单个应用的任务列表(开关/编辑)+ 新建入口;返回栏显示应用名,数据由上层持有 */
@Composable
private fun TaskListPage(
    pkg: String,
    tasks: List<GkdTask>,
    onBack: () -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onPersistTasks: (List<GkdTask>) -> Unit,
    onNewTask: () -> Unit,
    onEditTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appName = remember(pkg) {
        runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)
    }
    // 该应用的任务 + 全局规则(packageName 空),全局规则永远排在最前;
    // 全局规则仅在列表中只读入口,删除/持久化仍走原任务表
    val appTasks = tasks.filter { it.packageName.isEmpty() } + tasks.filter { it.packageName == pkg }
    // 长按删除确认:记录待删除任务 id
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    pendingDeleteId?.let { delId ->
        val del = appTasks.firstOrNull { it.id == delId } ?: return@let
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除任务") },
            text = { Text("确定删除「${del.actionsSummary}」?") },
            confirmButton = {
                TextButton(onClick = {
                    onPersistTasks(tasks - del)
                    pendingDeleteId = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏:强调色返回圆钮 + 应用名/包名 + 新建按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // GKD 订阅导入:选择 JSON 文件,规则解析后合并进当前应用的任务列表
            val scope = rememberCoroutineScope()
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)
                                ?.bufferedReader()?.use { it.readText() }
                        }.getOrNull()
                    }
                    if (text == null) {
                        Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val result = runCatching { GkdSubscription.parse(text) }.getOrElse {
                        Toast.makeText(context, "解析失败:${it.message}", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    if (result.tasks.none { it.packageName == pkg }) {
                        Toast.makeText(
                            context,
                            "文件中没有 ${appName} 的规则" +
                                result.skipped.takeIf { it.isNotEmpty() }?.joinToString(";") { it }
                                ?.let { "\n$it" }.orEmpty(),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }
                    // 只合并该应用的规则,id 重新生成避免冲突
                    val merged = tasks + result.tasks
                        .filter { it.packageName == pkg }
                        .map { t -> t.copy(id = "task_${System.currentTimeMillis()}_${t.id.hashCode()}") }
                    onPersistTasks(merged)
                    Toast.makeText(
                        context,
                        "已导入 ${result.tasks.size} 条规则" +
                            result.skipped.takeIf { it.isNotEmpty() }
                                ?.joinToString(";") { it }?.let { "\n跳过:$it" }.orEmpty(),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            // GKD 订阅导出:把当前应用的规则写成 GKD 订阅 JSON
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(GkdSubscription.toJson(
                                    appTasks.filter { it.packageName == pkg }, pkg,
                                ).toByteArray())
                            } != null
                        }.getOrDefault(false)
                    }
                    Toast.makeText(
                        context,
                        if (ok) "已导出 ${appTasks.count { it.packageName == pkg }} 条规则" else "导出失败",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                Icon(Icons.Filled.GetApp, contentDescription = "导入 GKD 订阅")
            }
            IconButton(onClick = {
                if (appTasks.isEmpty()) {
                    Toast.makeText(context, "当前应用没有可导出的规则", Toast.LENGTH_SHORT).show()
                } else {
                    exportLauncher.launch("gkd-rules-$pkg.json")
                }
            }) {
                Icon(Icons.Filled.Update, contentDescription = "导出 GKD 订阅")
            }
        }

        // 分组展开状态:默认全部收起,SharedPreferences 持久化(按包名区分,跨进程保留)
        val sectionPrefs = context.getSharedPreferences("rules_ui", android.content.Context.MODE_PRIVATE)
        var expandedSections by remember {
            mutableStateOf(sectionPrefs.getStringSet("expanded_sections_$pkg", emptySet())!!.toSet())
        }
        LaunchedEffect(expandedSections) {
            sectionPrefs.edit().putStringSet("expanded_sections_$pkg", expandedSections).apply()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (appTasks.isEmpty()) {
                item {
                    Text(
                        text = "该应用暂无任务。新建任务:进入此 App 界面时,按步骤自动执行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 三大分组:全局 / 订阅 / 自定义,默认收起,点标题展开
            val sections = listOf(
                RuleSection("全局规则", appTasks.filter { it.packageName.isEmpty() }, "global", Icons.Filled.Home),
                RuleSection("订阅规则", appTasks.filter { it.packageName == pkg && it.isFromSubscription }, "sub", Icons.Filled.CloudDownload),
                RuleSection("自定义规则", appTasks.filter { it.packageName == pkg && !it.isFromSubscription }, "custom", Icons.AutoMirrored.Filled.Rule),
            ).filter { it.tasks.isNotEmpty() }
            sections.forEach { sec ->
                val expanded = sec.key in expandedSections
                item(key = "sec_${sec.key}", contentType = "section") {
                    SectionHeader(
                        title = sec.title,
                        count = sec.tasks.size,
                        expanded = expanded,
                        icon = sec.icon,
                        onClick = {
                            expandedSections = if (expanded) expandedSections - sec.key
                            else expandedSections + sec.key
                        },
                    ) {
                        sec.tasks.forEach { task ->
                        val accent = if (task.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        // 全局规则用次要色容器底与普通应用规则区分
                        val isGlobal = task.packageName.isEmpty()
                        Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 点按卡片进入编辑;长按删除
                        .combinedClickable(
                            onClick = { onEditTask(task.id) },
                            onLongClick = { pendingDeleteId = task.id },
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isGlobal -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            task.enabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 启用状态点
                        StatusDot(active = task.enabled, accent = accent, size = 9.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    // 标题:规则名(name)优先,无名称回退动作摘要
                                    text = task.name.ifEmpty { task.actionsSummary },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // 全局标签:packageName 空 = 全局规则(匹配任意应用)
                                if (isGlobal) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                                    ) {
                                        Text(
                                            text = "全局",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                // 来源角标:远程订阅导入 / 本地自定义;场景流显示「N 步」角标
                                if (task.steps?.isNotEmpty() == true) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = "${task.steps.size}步",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                SourceBadge(isRemote = task.isFromSubscription)
                            }
                            Text(
                                text = buildString {
                                    if (task.activityIds.isNotEmpty()) append("Activity≈${task.activityIds.first()} · ")
                                    val trigger = task.rules.firstOrNull()?.matches?.firstOrNull()
                                    append(if (trigger != null) "触发:${trigger.expr} · " else "页面就绪即触发 · ")
                                    val max = task.actionMaximum
                                    if (max > 0) append("最多执行${max}次")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 启用/停用开关:停用的任务不参与匹配
                        Switch(
                            checked = task.enabled,
                            onCheckedChange = { onToggleTask(task.id, it) },
                        )
                    }
                }
                    }
                }
            }
        }

            // 新建规则入口
            item {
                Surface(
                    onClick = onNewTask,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "新建规则",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** 日志 tab:自动化执行记录(触发/成功/失败),LogStore 驱动实时刷新;点按条目查看详情并复制 */
@Composable
private fun LogsScreen(modifier: Modifier = Modifier) {
    var logs by remember { mutableStateOf(LogStore.all()) }
    // 详情弹窗:展示完整日志文本,一键复制
    var detailText by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // 打印开关持久化:关闭后引擎不再写入日志
    val prefs = remember { context.getSharedPreferences("logs_ui", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("log_enabled", true)) }

    DisposableEffect(Unit) {
        val unsubscribe = LogStore.observe { logs = it }
        onDispose { unsubscribe() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏:强调色图标 + 标题 + 清空
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentIcon(
                imageVector = Icons.AutoMirrored.Filled.Article,
                accent = MaterialTheme.colorScheme.primary,
                size = 40.dp,
                iconSize = 22.dp,
                shape = CircleShape,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "日志",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "自动化执行记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 打印开关:关闭后不再记录新日志
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(MaterialTheme.shapes.small),
            ) {
                Text(
                    text = "记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        LogStore.enabled = it
                        prefs.edit().putBoolean("log_enabled", it).apply()
                    },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            // 清空:浅错误色圆盘 + 细叉,柔和不刺眼
            IconButton(onClick = { LogStore.clear() }) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空日志",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (!enabled) {
            Text(
                text = "记录已关闭,新的执行不会写入日志。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (logs.isEmpty()) {
            Text(
                text = "暂无日志。规则触发/成功/失败时记录在此。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 新日志在顶部:倒序渲染
            items(logs.size, key = { logs.size - 1 - it }) { i ->
                val e = logs[logs.size - 1 - i]
                val dotColor = when {
                    e.message.contains("失败") -> MaterialTheme.colorScheme.error
                    e.message.contains("成功") -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 点按查看完整详情
                        .clickable { detailText = "${e.time} ${e.message}" },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(active = true, accent = dotColor, size = 8.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = e.time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = e.message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    // 日志详情弹窗:完整内容 + 复制
    detailText?.let { text ->
        AlertDialog(
            onDismissRequest = { detailText = null },
            title = { Text("日志详情") },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("log", text))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { detailText = null }) { Text("关闭") }
            },
        )
    }
}

/** 规则编辑页:GKD 规则表单(name / activityIds / matches / action / 执行参数);新建与编辑共用 */

/** GKD 动作名 → 中文标签(编辑器 chips 展示) */
private val GKD_ACTION_LABELS = mapOf(
    "click" to "点击",
    "clickNode" to "点节点",
    "clickCenter" to "点中心",
    "longClick" to "长按",
    "longClickNode" to "长按节点",
    "longClickCenter" to "长按中心",
    "back" to "返回",
    "swipe" to "滑动",
    "inputText" to "输入文本",
    "launchApp" to "启动应用",
    "check" to "勾选",
    "uncheck" to "取消勾选",
    "none" to "仅标记",
)

/** 规则编辑器实时校验:分区(sectionId)→ 错误消息列表 */
private data class EditorValidation(
    val ok: Boolean,
    val errors: Map<String, List<String>>,
)

/** 多行 GKD 选择器列表解析(跳过空行与 // 注释,坏行静默丢弃) */
private fun parseSelectorList(text: String): List<GkdSelector> =
    text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") }
        .mapNotNull { runCatching { GkdSelector.parse(it) }.getOrNull() }

/** 位置表达式解析: left=…,top=…,x=…;返回 (位置, 错误消息) */
private fun parsePositionExpr(text: String): Pair<GkdTask.Position?, String?> {
    val t = text.trim()
    if (t.isEmpty()) return null to null
    var p = GkdTask.Position()
    t.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.forEach { kv ->
        val (k, v) = kv.split('=', limit = 2).map { it.trim() }
        p = when (k) {
            "left" -> p.copy(left = v); "top" -> p.copy(top = v)
            "right" -> p.copy(right = v); "bottom" -> p.copy(bottom = v)
            "x" -> p.copy(x = v); "y" -> p.copy(y = v)
            else -> p
        }
    }
    if (!p.isValid) return null to "position 无有效坐标字段,示例: left=width/2,top=height/2"
    return p to null
}

/** swipeArg 表达式解析: start(…),end(…),duration=…;返回 (滑动参数, 错误消息) */
private fun parseSwipeArgExpr(text: String): Pair<GkdTask.SwipeArg?, String?> {
    val t = text.trim()
    if (t.isEmpty()) return null to null
    var start = GkdTask.Position()
    var end: GkdTask.Position? = null
    var duration = 300L
    Regex("start\\(([^)]*)\\)").find(t)?.let { m ->
        m.groupValues[1].split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { kv ->
                val (k, v) = kv.split('=', limit = 2).map { it.trim() }
                start = when (k) {
                    "left" -> start.copy(left = v); "top" -> start.copy(top = v)
                    "x" -> start.copy(x = v); "y" -> start.copy(y = v)
                    else -> start
                }
            }
    }
    Regex("end\\(([^)]*)\\)").find(t)?.let { m ->
        var e = GkdTask.Position()
        m.groupValues[1].split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { kv ->
                val (k, v) = kv.split('=', limit = 2).map { it.trim() }
                e = when (k) {
                    "left" -> e.copy(left = v); "top" -> e.copy(top = v)
                    "x" -> e.copy(x = v); "y" -> e.copy(y = v)
                    else -> e
                }
            }
        end = e
    }
    Regex("duration=(\\d+)").find(t)?.let { duration = it.groupValues[1].toLong() }
    if (!start.isValid) return null to "swipeArg 错误: start 缺少 x/y 定位字段(示例: start(x=screenWidth/2,y=screenHeight*0.8))"
    return GkdTask.SwipeArg(start = start, end = end?.takeIf { it.isValid }, duration = duration) to null
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RuleEditorPage(
    pkg: String,
    existing: GkdTask?,
    onSave: (GkdTask) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rule0 = existing?.rules?.firstOrNull()
    // GKD 规则字段:一条规则 = 一个动作(GKD 语义):matches 命中的节点即动作目标
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var activityIdsText by remember {
        mutableStateOf(existing?.activityIds?.joinToString("\n").orEmpty())
    }
    // matches / anyMatches / excludeMatches 支持多条:一行一条
    var matchesText by remember {
        mutableStateOf(rule0?.matches?.joinToString("\n") { it.expr }.orEmpty())
    }
    var anyMatchesText by remember {
        mutableStateOf(rule0?.anyMatches?.joinToString("\n") { it.expr }.orEmpty())
    }
    var excludeMatchesText by remember {
        mutableStateOf(rule0?.excludeMatches?.joinToString("\n") { it.expr }.orEmpty())
    }
    var excludeAllMatchesText by remember {
        mutableStateOf(rule0?.excludeAllMatches?.joinToString("\n") { it.expr }.orEmpty())
    }
    // 调度参数(规则级优先,空 = 用组级默认)
    var actionCdText by remember { mutableStateOf(rule0?.actionCd?.takeIf { it > 0 }?.toString().orEmpty()) }
    var actionDelayText by remember { mutableStateOf(rule0?.actionDelay?.takeIf { it > 0 }?.toString().orEmpty()) }
    var matchTimeText by remember { mutableStateOf(rule0?.matchTime?.takeIf { it > 0 }?.toString().orEmpty()) }
    var priorityTimeText by remember { mutableStateOf(rule0?.priorityTime?.takeIf { it > 0 }?.toString().orEmpty()) }
    var forcedTimeText by remember { mutableStateOf(rule0?.forcedTime?.takeIf { it > 0 }?.toString().orEmpty()) }
    var preKeysText by remember { mutableStateOf(rule0?.preKeys?.joinToString(",").orEmpty()) }
    var resetMatchKind by remember {
        mutableStateOf(rule0?.resetMatch ?: existing?.resetMatch ?: GkdTask.ResetMatch.Activity)
    }
    var fastQueryOn by remember { mutableStateOf(rule0?.fastQuery ?: existing?.fastQuery ?: false) }
    var matchRootOn by remember { mutableStateOf(rule0?.matchRoot ?: existing?.matchRoot ?: false) }
    // 本地扩展:反向触发(节点不存在时执行动作),不导出 GKD 订阅
    var triggerOnAbsentOn by remember { mutableStateOf(rule0?.triggerOnAbsent ?: false) }
    // 触发事件类型(手动选哪些 AccessibilityEvent 触发评估);空 = 仅窗口状态变化
    var selectedEventTypes by remember { mutableStateOf(rule0?.eventTypes?.toSet() ?: emptySet()) }
    var orderText by remember {
        mutableStateOf(rule0?.order?.takeIf { it != 0L }?.toString()
            ?: existing?.order?.takeIf { it != 0L }?.toString().orEmpty())
    }
    // 自定义坐标 / 滑动:swipe 动作的补充参数
    var positionText by remember {
        mutableStateOf(rule0?.position?.let { p ->
            listOfNotNull(
                p.left?.let { "left=$it" }, p.top?.let { "top=$it" },
                p.right?.let { "right=$it" }, p.bottom?.let { "bottom=$it" },
                p.x?.let { "x=$it" }, p.y?.let { "y=$it" },
            ).joinToString(",")
        }.orEmpty())
    }
    var swipeArgText by remember {
        mutableStateOf(rule0?.swipeArg?.let { a ->
            fun fmt(tag: String, p: GkdTask.Position): String = buildString {
                append(tag).append("(")
                listOfNotNull(
                    p.left?.let { "left=$it" }, p.top?.let { "top=$it" },
                    p.x?.let { "x=$it" }, p.y?.let { "y=$it" },
                ).joinTo(this, ",")
                append(")")
            }
            listOfNotNull(
                fmt("start", a.start),
                a.end?.let { fmt("end", it) },
                "duration=${a.duration}",
            ).joinToString(",")
        }.orEmpty())
    }
    // GKD 动作名
    var actionKind by remember { mutableStateOf(rule0?.action?.gkd ?: "click") }
    var actionMax by remember {
        // 0 = 不限(GKD 默认);仅编辑已有规则时回填其原值
        mutableStateOf(rule0?.actionMaximum?.toString() ?: "0")
    }
    // 步骤模式:开启后本组为多步骤场景流(steps),单步表单收起
    var stepMode by remember { mutableStateOf(existing?.steps?.isNotEmpty() == true) }
    // 步骤编辑草稿:文本态字段,保存时解析为 GkdTask.Step
    var stepDrafts: List<StepDraft> by remember {
        mutableStateOf<List<StepDraft>>(
            existing?.steps?.map { s ->
                StepDraft(
                    name = s.name,
                    matchesText = (s.matches + s.anyMatches).joinToString("\n") { it.expr },
                    actionKind = s.action?.gkd ?: "click",
                    waitTimeoutText = s.waitTimeout.toString(),
                    settleTimeText = s.settleTime.toString(),
                    skipOnTimeout = s.onTimeout == GkdTask.Step.TimeoutPolicy.Continue,
                    triggerOnAbsent = s.triggerOnAbsent,
                )
            } ?: emptyList(),
        )
    }
    var editingStepIdx by remember { mutableStateOf<Int?>(null) }
    // 单页手风琴:当前展开的分组 id(默认全部折叠,用户逐组展开)
    var expandedSections by remember { mutableStateOf(setOf<String>()) }
    // 分区列表:场景流/单步规则两种模式分组集不同
    val sections = remember(stepMode) {
        if (stepMode) listOf(
            EditorSectionItem("mode", "模式", "单步规则 / 场景流", Icons.Filled.ExpandMore),
            EditorSectionItem("basic", "基本信息", "任务名与生效范围", Icons.AutoMirrored.Filled.ListAlt),
            EditorSectionItem("steps", "场景流步骤", "按序执行", Icons.Filled.PlayCircle),
        ) else listOf(
            EditorSectionItem("mode", "模式", "单步规则 / 场景流", Icons.Filled.ExpandMore),
            EditorSectionItem("basic", "基本信息", "任务名与生效范围", Icons.AutoMirrored.Filled.ListAlt),
            EditorSectionItem("trigger", "触发条件", "何时评估 + 命中节点", Icons.AutoMirrored.Filled.Rule),
            EditorSectionItem("action", "动作", "命中后执行什么", Icons.Filled.TouchApp),
            EditorSectionItem("advanced", "高级参数", "冷却 / 时间窗 / 重置", Icons.Filled.Tune),
        )
    }
    // 摘要文本(顶部标题栏副标题)
    val summaryAction = GKD_ACTION_LABELS[actionKind] ?: actionKind
    val summaryTrigger = when {
        stepMode -> "场景流 ${stepDrafts.size} 步"
        actionKind == "back" -> "无选择器 · 立即执行"
        else -> {
            val n = parseSelectorList(matchesText).size + parseSelectorList(anyMatchesText).size
            if (n > 0) "$n 条选择器" else "未填选择器"
        }
    }
    // 实时校验:与 doSave 同规则,结果驱动概要卡状态与导航红点
    fun validate(): EditorValidation {
        val errors = LinkedHashMap<String, MutableList<String>>()
        fun err(sec: String, msg: String) { errors.getOrPut(sec) { mutableListOf() } += msg }
        if (stepMode) {
            if (stepDrafts.isEmpty()) err("steps", "请至少添加一个步骤")
            stepDrafts.forEachIndexed { idx, d ->
                d.matchesText.lines().map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("//") }
                    .forEach { expr ->
                        runCatching { GkdSelector.parse(expr) }
                            .exceptionOrNull()?.let { err("steps", "步骤${idx + 1} 选择器错误: ${it.message}") }
                    }
            }
        } else {
            listOf("matches" to matchesText, "anyMatches" to anyMatchesText,
                "excludeMatches" to excludeMatchesText, "excludeAllMatches" to excludeAllMatchesText
            ).forEach { (field, text) ->
                text.lines().map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("//") }
                    .forEach { expr ->
                        runCatching { GkdSelector.parse(expr) }
                            .exceptionOrNull()?.let { err("trigger", "$field 错误: ${it.message}") }
                    }
            }
            val selCount = parseSelectorList(matchesText).size
            val anyCount = parseSelectorList(anyMatchesText).size
            if (selCount == 0 && anyCount == 0) err("trigger", "matches 与 anyMatches 至少填一个")
            if (positionText.isNotBlank()) {
                parsePositionExpr(positionText).second?.let { err("action", it) }
            }
            if (actionKind == "swipe" && swipeArgText.isNotBlank()) {
                parseSwipeArgExpr(swipeArgText).second?.let { err("action", it) }
            }
        }
        return EditorValidation(ok = errors.isEmpty(), errors = errors)
    }
    // 保存逻辑:校验通过才落盘并退出
    fun doSave() {
        val v = validate()
        if (!v.ok) {
            val firstSec = v.errors.keys.firstOrNull()
            if (firstSec != null) {
                Toast.makeText(context, v.errors[firstSec]!!.first(), Toast.LENGTH_LONG).show()
            }
            return
        }
        // 场景流:解析各步选择器为 GkdTask.Step,rules 留空由场景流驱动
        if (stepMode) {
            val parsed = stepDrafts.map { d ->
                GkdTask.Step(
                    name = d.name.trim(),
                    matches = parseSelectorList(d.matchesText),
                    action = Action.entries.firstOrNull { it.gkd == d.actionKind } ?: Action.Click,
                    waitTimeout = d.waitTimeoutText.toLongOrNull() ?: 0L,
                    settleTime = d.settleTimeText.toLongOrNull() ?: 0L,
                    onTimeout = if (d.skipOnTimeout) GkdTask.Step.TimeoutPolicy.Continue
                    else GkdTask.Step.TimeoutPolicy.Abort,
                    triggerOnAbsent = d.triggerOnAbsent,
                )
            }
            val saved = GkdTask(
                id = existing?.id ?: "task_${System.currentTimeMillis()}",
                name = name.trim().ifEmpty { "场景流" },
                packageName = pkg,
                activityIds = activityIdsText.lines()
                    .map { it.trim() }.filter { it.isNotEmpty() },
                enabled = existing?.enabled ?: false,
                rules = emptyList(),
                steps = parsed,
            )
            onSave(saved)
            Toast.makeText(context, "场景流已保存(${parsed.size} 步)", Toast.LENGTH_SHORT).show()
            return
        }
        // 单步规则:共享解析函数已保证表达式合法
        val sel = parseSelectorList(matchesText)
        val anySel = parseSelectorList(anyMatchesText)
        val excludeSel = parseSelectorList(excludeMatchesText)
        val excludeAllSel = parseSelectorList(excludeAllMatchesText)
        val action: Action = Action.entries.firstOrNull { it.gkd == actionKind } ?: Action.Click
        val max = (actionMax.toLongOrNull() ?: 0L).coerceAtLeast(0L)
        val preKeys = preKeysText.split(',', '，', ' ')
            .mapNotNull { it.trim().toLongOrNull() }
        val position = parsePositionExpr(positionText).first
        val swipeArg = if (actionKind == "swipe") parseSwipeArgExpr(swipeArgText).first else null
        // swipe 方向编码:GKD 新版用 swipeArg;无绝对坐标时以 endY=-2 表示下滑,null=上滑
        val rule = GkdTask.Rule(
            key = rule0?.key ?: 0L,
            name = name.trim(),
            matches = sel,
            anyMatches = anySel,
            excludeMatches = excludeSel,
            excludeAllMatches = excludeAllSel,
            preKeys = preKeys,
            action = action,
            // 本地扩展 swipeDir:2 = 下滑;GKD 标准形态用 swipeArg 表达式
            swipeDir = if (actionKind == "scrollBackward") 2 else null,
            position = position,
            swipeArg = swipeArg,
            actionMaximum = max,
            actionCd = actionCdText.toLongOrNull() ?: 0L,
            actionDelay = actionDelayText.toLongOrNull() ?: 0L,
            matchTime = matchTimeText.toLongOrNull() ?: 0L,
            priorityTime = priorityTimeText.toLongOrNull() ?: 0L,
            forcedTime = forcedTimeText.toLongOrNull() ?: 0L,
            fastQuery = fastQueryOn,
            matchRoot = matchRootOn,
            triggerOnAbsent = triggerOnAbsentOn,
            eventTypes = selectedEventTypes.toList(),
            resetMatch = resetMatchKind,
            order = orderText.toLongOrNull() ?: 0L,
        )
        val saved = GkdTask(
            id = existing?.id ?: "task_${System.currentTimeMillis()}",
            name = name.trim().ifEmpty { rule.name.ifEmpty { "规则${rule.key}" } },
            packageName = pkg,
            activityIds = activityIdsText.lines()
                .map { it.trim() }.filter { it.isNotEmpty() },
            // 本地新建规则也默认关闭,用户在任务页手动开启
            enabled = existing?.enabled ?: false,
            rules = listOf(rule),
        )
        onSave(saved)
        Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
    }

    val validation = validate()

    // 各分组正文内容(手风琴卡片引用)
    @Composable
    fun sectionBody(id: String) {
        when (id) {
            "mode" -> {
                ModeOptionCard(
                    selected = !stepMode,
                    onClick = { stepMode = false },
                    title = "单步规则",
                    desc = "一个条件 + 一个动作",
                    icon = Icons.AutoMirrored.Filled.Rule,
                )
                ModeOptionCard(
                    selected = stepMode,
                    onClick = { stepMode = true },
                    title = "多步骤场景流",
                    desc = "按序执行,适合多步操作场景",
                    icon = Icons.Filled.PlayCircle,
                )
            }
                "basic" -> {
                        ExprField(
                            value = name,
                            onChange = { name = it },
                            title = "任务名(可选)",
                            note = "留空自动按规则内容命名",
                            singleLine = true,
                        )
                        ExprField(
                            value = activityIdsText,
                            onChange = { activityIdsText = it },
                            title = "activityIds(每行一个)",
                            note = "留空 = 任意 Activity;限制规则只在指定 Activity 生效",
                            maxLines = 4,
                        )
                    }
                    "trigger" -> {
                        // 触发选择器二选一切换:matches(全部命中)/ anyMatches(任一命中)
                        var matchMode by remember(existing?.id) {
                            mutableStateOf(if (rule0?.anyMatches?.isNotEmpty() == true && rule0.matches.isEmpty()) 1 else 0)
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                "matches(全部命中)" to 0,
                                "anyMatches(任一命中)" to 1,
                            ).forEach { (label, mode) ->
                                ChoiceChip(
                                    selected = matchMode == mode,
                                    onClick = { matchMode = mode },
                                    label = label,
                                )
                            }
                        }
                        if (matchMode == 0) {
                            ExprField(
                                value = matchesText,
                                onChange = { matchesText = it },
                                title = "matches(GKD 选择器,全部命中;每行一条)",
                                note = "例: [text*=\"跳过\"][clickable=true];动作目标 = 最后一行",
                                monospace = true,
                                maxLines = 4,
                                error = validation.errors["trigger"]?.firstOrNull { it.startsWith("matches") },
                            )
                        } else {
                            ExprField(
                                value = anyMatchesText,
                                onChange = { anyMatchesText = it },
                                title = "anyMatches(GKD 选择器,任一命中即可;每行一条)",
                                note = "例: [text*=\"跳过\"][clickable=true];动作目标 = 最后一行",
                                monospace = true,
                                maxLines = 4,
                                error = validation.errors["trigger"]?.firstOrNull { it.startsWith("anyMatches") },
                            )
                        }
                        // 常用选择器片段:点按插入到 matches 末尾
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "选择器片段(点按插入 matches)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SELECTOR_SNIPPETS.forEach { (label, snippet) ->
                                    ChoiceChip(
                                        selected = false,
                                        onClick = {
                                            matchesText = matchesText.trimEnd().let {
                                                if (it.isEmpty()) snippet else "$it\n$snippet"
                                            }
                                        },
                                        label = label,
                                    )
                                }
                            }
                        }
                        // 排除选择器二选一切换:excludeMatches(存在一个即跳过)/ excludeAllMatches(全部命中才跳过)
                        var excludeMode by remember(existing?.id) {
                            mutableStateOf(if (rule0?.excludeAllMatches?.isNotEmpty() == true && rule0.excludeMatches.isEmpty()) 1 else 0)
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                "excludeMatches(存在一个即跳过)" to 0,
                                "excludeAllMatches(全部命中才跳过)" to 1,
                            ).forEach { (label, mode) ->
                                ChoiceChip(
                                    selected = excludeMode == mode,
                                    onClick = { excludeMode = mode },
                                    label = label,
                                )
                            }
                        }
                        if (excludeMode == 0) {
                            ExprField(
                                value = excludeMatchesText,
                                onChange = { excludeMatchesText = it },
                                title = "excludeMatches(GKD 选择器,存在一个命中即跳过本规则;每行一条)",
                                note = "例: [vid=\"close_btn\"] 存在时不执行",
                                monospace = true,
                                maxLines = 4,
                                error = validation.errors["trigger"]?.firstOrNull { it.startsWith("excludeMatches") },
                            )
                        } else {
                            ExprField(
                                value = excludeAllMatchesText,
                                onChange = { excludeAllMatchesText = it },
                                title = "excludeAllMatches(GKD 选择器,全部命中才跳过本规则;每行一条)",
                                note = "与 excludeMatches 的区别:AND 语义,全部存在才拦截",
                                monospace = true,
                                maxLines = 4,
                                error = validation.errors["trigger"]?.firstOrNull { it.startsWith("excludeAllMatches") },
                            )
                        }
                        RuleTriggerSection(
                            selectedTypes = selectedEventTypes,
                            onTypesChange = { selectedEventTypes = it },
                            onAbsent = triggerOnAbsentOn,
                            onAbsentChange = { triggerOnAbsentOn = it },
                        )
                    }
                    "action" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "动作",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // GKD 全量动作:单行横向滚动 chip 组
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Action.entries.forEach { a ->
                                    ChoiceChip(
                                        selected = actionKind == a.gkd,
                                        onClick = { actionKind = a.gkd },
                                        label = GKD_ACTION_LABELS[a.gkd] ?: a.gkd,
                                    )
                                }
                            }
                        }
                        // 该动作对选择器/参数的要求
                        ACTION_HINTS[actionKind]?.let { hint -> InfoNote(hint) }
                        ExprField(
                            value = actionMax,
                            onChange = { actionMax = it },
                            title = "actionMaximum(执行次数)",
                            note = "0 = 不限次数(GKD 默认);填 1 = 只执行一次,配合 resetMatch 决定何时重置",
                            singleLine = true,
                            numeric = true,
                        )
                        ExprField(
                            value = positionText,
                            onChange = { positionText = it },
                            title = "position(自定义点击坐标,可选)",
                            note = "例: left=width/2,top=height/2(节点中心);变量 left/top/right/bottom/width/height/random/screenWidth/screenHeight;有 position 时 click/longClick 走坐标手势",
                            maxLines = 4,
                            error = validation.errors["action"]?.firstOrNull(),
                        )
                        if (actionKind == "swipe") {
                            ExprField(
                                value = swipeArgText,
                                onChange = { swipeArgText = it },
                                title = "swipeArg(滑动参数)",
                                note = "例: start(x=screenWidth/2,y=screenHeight*0.8),end(…),duration=300;start 必填,end 缺省 = start,留空 = 整屏上滑",
                                maxLines = 4,
                                error = validation.errors["action"]?.getOrNull(1),
                            )
                        }
                    }
                    "advanced" -> {
                        SubSectionTitle("调度与限频")
                        ExprField(
                            value = actionCdText,
                            onChange = { actionCdText = it },
                            title = "actionCd(冷却 ms)",
                            note = "0 = 默认 1000",
                            singleLine = true,
                            numeric = true,
                        )
                        ExprField(
                            value = actionDelayText,
                            onChange = { actionDelayText = it },
                            title = "actionDelay(延迟执行 ms)",
                            note = "延迟后重新校验选择器",
                            singleLine = true,
                            numeric = true,
                        )
                        ExprField(
                            value = orderText,
                            onChange = { orderText = it },
                            title = "order(匹配顺序)",
                            note = "越小越先,可为负",
                            singleLine = true,
                            numeric = true,
                            allowMinus = true,
                        )
                        ExprField(
                            value = preKeysText,
                            onChange = { preKeysText = it },
                            title = "preKeys(前置规则 key,逗号分隔)",
                            note = "须在 10s 内刚执行过",
                            singleLine = true,
                        )
                        SubSectionTitle("时间窗")
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("3000" to "3秒", "10000" to "10秒(推荐)", "0" to "不限").forEach { (v, label) ->
                                ChoiceChip(
                                    selected = matchTimeText == v,
                                    onClick = { matchTimeText = v },
                                    label = label,
                                )
                            }
                        }
                        ExprField(
                            value = matchTimeText,
                            onChange = { matchTimeText = it },
                            title = "matchTime(匹配时间窗 ms)",
                            note = "0 = 不限",
                            singleLine = true,
                            numeric = true,
                        )
                        ExprField(
                            value = priorityTimeText,
                            onChange = { priorityTimeText = it },
                            title = "priorityTime(优先级窗 ms)",
                            note = "窗内优先匹配并可打断普通规则",
                            singleLine = true,
                            numeric = true,
                        )
                        ExprField(
                            value = forcedTimeText,
                            onChange = { forcedTimeText = it },
                            title = "forcedTime(主动轮询窗 ms)",
                            note = "flutter/webview 不发界面事件时用",
                            singleLine = true,
                            numeric = true,
                        )
                        SubSectionTitle("其他")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "resetMatch(休眠重置策略)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GkdTask.ResetMatch.entries.forEach { rm ->
                                    ChoiceChip(
                                        selected = resetMatchKind == rm,
                                        onClick = { resetMatchKind = rm },
                                        label = rm.gkd,
                                    )
                                }
                            }
                            InfoNote(
                                when (resetMatchKind) {
                                    GkdTask.ResetMatch.App ->
                                        "离开本应用即清除执行次数与匹配窗,下次进入重新计"
                                    GkdTask.ResetMatch.Activity ->
                                        "每次 Activity 切换即清执行次数与匹配窗(同页 1 秒去抖);注意:同 Activity 内界面变化不重置,限次规则在此页内点满即休眠"
                                    GkdTask.ResetMatch.Match ->
                                        "按 activityIds 判断:从「不在匹配页」切到「在匹配页」时清执行次数;activityIds 留空 = 任意 Activity 变化都清"
                                }
                            )
                        }
                        SwitchRow(
                            checked = fastQueryOn,
                            onCheckedChange = { fastQueryOn = it },
                            label = "fastQuery",
                            desc = "跳过无障碍缓存直接查询",
                        )
                        SwitchRow(
                            checked = matchRootOn,
                            onCheckedChange = { matchRootOn = it },
                            label = "matchRoot",
                            desc = "选择器从根节点开始匹配",
                        )
                        InfoNote("本地扩展 nodeIndex:选择器属性 [nodeIndex=5] = 点悬浮窗 NODE 5 节点,可与 text/vid 等属性同段使用。")
                    }
                    "steps" -> {
                        val updateDraft: (Int, (StepDraft) -> StepDraft) -> Unit = { i, transform ->
                            stepDrafts = stepDrafts.mapIndexed { j, d -> if (j == i) transform(d) else d }
                        }
                        stepDrafts.forEachIndexed { idx, d ->
                            StepCard(
                                index = idx,
                                totalSteps = stepDrafts.size,
                                draft = d,
                                expanded = editingStepIdx == idx,
                                onToggle = { editingStepIdx = if (editingStepIdx == idx) null else idx },
                                onMoveUp = {
                                    stepDrafts = stepDrafts.toMutableList().apply { add(idx - 1, removeAt(idx)) }
                                    editingStepIdx = idx - 1
                                },
                                onMoveDown = {
                                    stepDrafts = stepDrafts.toMutableList().apply { add(idx + 1, removeAt(idx)) }
                                    editingStepIdx = idx + 1
                                },
                                onDelete = {
                                    stepDrafts = stepDrafts.filterIndexed { j, _ -> j != idx }
                                    editingStepIdx = null
                                },
                                onChange = { updated -> updateDraft(idx) { updated } },
                            )
                        }
                        // 添加步骤
                        OutlinedButton(
                            onClick = {
                                stepDrafts = stepDrafts + StepDraft("", "", "click", "3000", "0", false, false)
                                editingStepIdx = stepDrafts.size - 1
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("添加步骤")
                        }
                        InfoNote("场景流由第一步的选择器触发;组内 rules 不参与独立调度。")
                    }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏:返回 + 标题/副标题 + 属性查看 + 保存状态徽标
        val cs = MaterialTheme.colorScheme
        val topTitle = if (existing == null) "新建任务" else "编辑任务"
        var showNodeProps by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = cs.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$pkg · ${summaryAction} · $summaryTrigger",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 属性查看:列出当前屏幕节点快照(NODE 编号 + 摘要),点行复制 [nodeIndex=n]
            Surface(
                onClick = { showNodeProps = true },
                shape = CircleShape,
                color = cs.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Layers, contentDescription = "属性查看")
                }
            }
            Surface(
                color = if (validation.ok) cs.primaryContainer else cs.errorContainer,
                contentColor = if (validation.ok) cs.onPrimaryContainer else cs.onErrorContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (validation.ok) Icons.Filled.Check else Icons.Filled.Error,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (validation.ok) "可保存" else "待修正",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))

        if (showNodeProps) {
            NodePropsDialog(onDismiss = { showNodeProps = false })
        }

        // 手风琴正文:滚动区
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sections, key = { it.id }) { sec ->
                EditorAccordion(
                    item = sec,
                    expanded = expandedSections.contains(sec.id),
                    hasError = validation.errors.containsKey(sec.id),
                    onToggle = {
                        expandedSections = if (sec.id in expandedSections)
                            expandedSections - sec.id else expandedSections + sec.id
                    },
                ) {
                    sectionBody(sec.id)
                }
            }
        }

        // 底部常驻保存栏:错误提示 + 保存按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surface)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val firstError = validation.errors.values.flatten().firstOrNull()
            if (firstError != null) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = firstError,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = { doSave() },
                enabled = validation.ok,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .weight(if (firstError != null) 0.6f else 1f)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存")
            }
        }
    }
}

/**
 * 选择器属性参考弹窗:列出规则选择器支持的全部属性及说明。
 */
@Composable
private fun NodePropsDialog(onDismiss: () -> Unit) {
    // 名称 → (类型, 说明)
    val props = listOf(
        "name" to ("文本" to "完整类名,如 android.widget.TextView"),
        "text" to ("文本" to "节点文本"),
        "desc" to ("文本" to "contentDescription"),
        "vid" to ("文本" to "viewId 资源名,如 close_btn"),
        "id" to ("文本" to "完整 viewId,如 com.xx:id/close"),
        "nodeIndex" to ("数值" to "本地扩展:悬浮窗节点编号(1 基),如 [nodeIndex=5]"),
        "clickable" to ("布尔" to "可点击"),
        "longClickable" to ("布尔" to "可长按"),
        "checkable" to ("布尔" to "可勾选"),
        "checked" to ("布尔" to "已勾选"),
        "editable" to ("布尔" to "可编辑"),
        "focusable" to ("布尔" to "可聚焦"),
        "visibleToUser" to ("布尔" to "用户可见"),
        "left" to ("数值" to "屏幕边界左"),
        "top" to ("数值" to "屏幕边界上"),
        "right" to ("数值" to "屏幕边界右"),
        "bottom" to ("数值" to "屏幕边界下"),
        "width" to ("数值" to "宽度"),
        "height" to ("数值" to "高度"),
        "index" to ("数值" to "兄弟节点序号(0 基)"),
        "depth" to ("数值" to "距根深度"),
        "childCount" to ("数值" to "子节点数"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("支持的属性") },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(props.size) { i ->
                    val (name, doc) = props[i]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(96.dp),
                        )
                        Column {
                            Text(
                                text = doc.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = doc.second,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = "用法: [text*=\"跳过\"][clickable=true];比较符 = != > < ^= $= *= ~=(正则)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "matches(全部命中才触发)与 anyMatches(任一命中即触发)独立判定;excludeMatches(存在即不触发)与 excludeAllMatches(全部存在才不触发)为排除条件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 远程订阅管理弹窗:URL 列表 / 添加 / 刷新 / 删除,拉取后解析合并进规则表(GKD 订阅同款) */
@Composable
private fun SubscriptionManageDialog(
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var subs by remember { mutableStateOf(emptyList<SubscriptionStore.Subscription>()) }
    LaunchedEffect(Unit) {
        subs = withContext(Dispatchers.IO) { SubscriptionStore.loadAll(context) }
    }
    var urlInput by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    // 展开查看订阅内 app 清单的订阅 id
    var expandedId by remember { mutableStateOf<String?>(null) }

    fun fetchAndImport(sub: SubscriptionStore.Subscription) {
        loading = true
        LogStore.log("⬇ 订阅拉取开始: ${sub.url.ifEmpty { sub.name }}")
        scope.launch {
            val t0 = System.currentTimeMillis()
            val raw = try { SubscriptionFetcher.fetch(sub.url) } catch (e: Exception) {
                loading = false
                LogStore.log("✕ 订阅拉取失败(${System.currentTimeMillis() - t0}ms): ${e.message} @ ${sub.url}")
                Toast.makeText(context, "拉取失败: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            LogStore.log("⬇ 订阅已拉取: ${raw.length / 1024}KB,耗时 ${System.currentTimeMillis() - t0}ms")
            // 解析 1MB+ 的订阅很耗时,放 Default 线程;失败不崩溃:保存原文供排查,提示具体错误
            val t1 = System.currentTimeMillis()
            val parsed = withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { GkdSubscription.parse(raw) }
            }.getOrElse { e ->
                loading = false
                LogStore.log("✕ 订阅解析失败(${System.currentTimeMillis() - t1}ms): ${e.message}")
                withContext(Dispatchers.IO) {
                    SubscriptionStore.upsert(
                        context,
                        sub.copy(raw = raw, name = sub.name.ifEmpty { sub.url.substringAfterLast('/') }, lastUpdate = System.currentTimeMillis()),
                    )
                    SubscriptionStore.loadAll(context)
                }.also { subs = it }
                Toast.makeText(context, "订阅已保存,但解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            LogStore.log(
                "✓ 订阅解析完成(${System.currentTimeMillis() - t1}ms): " +
                    "${parsed.tasks.size} 条规则组,跳过 ${parsed.skipped.size} 项" +
                    parsed.skipped.take(3).joinToString(";") { "「$it」" }.let { if (it.isNotEmpty()) " — $it" else "" },
            )
            val name = Regex("""["']name["']\s*:\s*["']([^"']+)["']""").find(raw)
                ?.groupValues?.get(1) ?: sub.url.substringAfterLast('/')
            val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(raw)
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val updated = sub.copy(raw = raw, name = name, version = version, lastUpdate = System.currentTimeMillis())
            // 订阅 upsert + 规则表合并落盘都在 IO(SharedPreferences 写入 + 大 JSON 序列化)
            val t2 = System.currentTimeMillis()
            val merged = withContext(kotlinx.coroutines.Dispatchers.IO) {
                SubscriptionStore.upsert(context, updated)
                SubscriptionStore.import(context, updated, parsed)
                TaskStore.loadAll(context)
            }
            subs = withContext(Dispatchers.IO) { SubscriptionStore.loadAll(context) }
            LogStore.log("✓ 规则已合并落盘: 「$name」v$version,共 ${parsed.tasks.size} 条,落盘 ${System.currentTimeMillis() - t2}ms")
            // 同步:引擎立即换表 + 通知应用页重载(此协程在 Main 上,可直接调服务)
            DramaAccessibilityService.instance?.taskRunner?.setTasks(merged)
            DramaAccessibilityService.instance?.refreshTaskTriggers()
            RuleSync.version++
            loading = false
            Toast.makeText(
                context,
                "已导入 ${parsed.tasks.size} 条规则" +
                    parsed.skipped.takeIf { it.isNotEmpty() }?.joinToString(";") { it }?.let { "\n跳过:$it" }.orEmpty(),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("远程订阅") },
        // 完成按钮移入内容列:既消除 text 与按钮区的固定大间距,也保持 text 槽为 Composable lambda
        confirmButton = {},
        text = {
            // 输入行固定在顶部,订阅清单用 LazyColumn 列表(限高滚动),不整列滚动、底部不留大片空白
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("订阅 URL(JSON5)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val url = urlInput.trim()
                            if (url.isEmpty()) return@TextButton
                            fetchAndImport(SubscriptionStore.Subscription(id = "s${System.currentTimeMillis()}", url = url, name = ""))
                            urlInput = ""
                        },
                        enabled = !loading && urlInput.isNotBlank(),
                    ) { Text("添加") }
                }
                if (loading) Text("拉取中…", style = MaterialTheme.typography.bodySmall)
                if (subs.isEmpty()) {
                    Text("暂无订阅。粘贴 GKD 订阅链接(如 .json5/raw 地址)添加。", style = MaterialTheme.typography.bodySmall)
                }
                if (subs.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(subs.size, key = { subs[it].id }) { i ->
                            val sub = subs[i]
                            Column {
                                // 订阅行:点按展开/收起订阅内 app 清单
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedId = if (expandedId == sub.id) null else sub.id },
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sub.name.ifEmpty { sub.url }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("v${sub.version} · ${sub.url}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(onClick = { fetchAndImport(sub) }, enabled = !loading) { Text("刷新") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            // 订阅/规则表都是大 JSON,读写放后台,避免卡 UI
                                            withContext(Dispatchers.IO) {
                                                SubscriptionStore.remove(context, sub.id)
                                                val cur = TaskStore.loadAll(context)
                                                TaskStore.saveAll(context, cur.filterNot { it.id.startsWith("sub_${sub.id}_") })
                                            }
                                            subs = withContext(Dispatchers.IO) { SubscriptionStore.loadAll(context) }
                                        }
                                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                                }
                                // 展开区:订阅内 app 清单,未安装的灰色展示;
                                // LazyColumn 惰性组合(大订阅几百行不卡展开),安装状态后台批查
                                if (expandedId == sub.id) {
                                    var installedSet by remember(sub.id, sub.raw) { mutableStateOf<Set<String>?>(null) }
                                    LaunchedEffect(sub.id, sub.raw) {
                                        val apps = GkdSubscription.appsOf(sub.raw)
                                        if (apps.isEmpty()) return@LaunchedEffect
                                        installedSet = withContext(Dispatchers.IO) {
                                            apps.map { it.first }
                                                .filter { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
                                                .toSet()
                                        }
                                    }
                                    val apps = remember(sub.id, sub.raw) { GkdSubscription.appsOf(sub.raw) }
                                    if (apps.isEmpty()) {
                                        Text(
                                            "未解析到应用(订阅无 apps 或内容为空)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                        )
                                    } else {
                                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                            items(apps.size, key = { apps[it].first }) { idx ->
                                                val (appId, appName) = apps[idx]
                                                // installedSet 为 null 时先按已安装展示,批查完成后再修正置灰
                                                val installed = installedSet?.contains(appId) ?: true
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                                                ) {
                                                    StatusDot(active = installed, accent = MaterialTheme.colorScheme.primary, size = 7.dp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = appName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (installed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (!installed) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            "未安装",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.outline,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // 完成按钮放内容列尾部:M3 AlertDialog text 与按钮区固定间距偏大,内联消除
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("完成") }
            }
        },
    )
}

/** 触发事件类型选择区:多选 chips(哪些事件触发评估)+ 反向触发开关(本地扩展,不导出 GKD 订阅) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleTriggerSection(
    selectedTypes: Set<String>,
    onTypesChange: (Set<String>) -> Unit,
    onAbsent: Boolean,
    onAbsentChange: (Boolean) -> Unit,
) {
    // 顺序即展示顺序,与 TaskRunner.knownEventTypes 保持一致
    val labels = linkedMapOf(
        "windowStateChanged" to "窗口状态变化",
        "windowContentChanged" to "窗口内容变化",
        "viewFocused" to "焦点",
        "viewTextChanged" to "文本输入",
        "viewClicked" to "点击",
        "viewLongClicked" to "长按",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "触发事件(多选;不选 = 状态+内容变化)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEach { (type, label) ->
                ChoiceChip(
                    selected = type in selectedTypes,
                    onClick = {
                        onTypesChange(
                            if (type in selectedTypes) selectedTypes - type else selectedTypes + type,
                        )
                    },
                    label = label,
                )
            }
        }
        SwitchRow(
            checked = onAbsent,
            onCheckedChange = onAbsentChange,
            label = "反向触发",
            desc = "节点不存在时执行(仅返回/滑动/坐标类动作有效)",
        )
    }
}

/** 编辑器常用选择器片段(label to gkd 表达式) */
private val SELECTOR_SNIPPETS = listOf(
    "文本包含" to """[text*="跳过"][visibleToUser=true]""",
    "文本等于" to """[text="确定"][clickable=true]""",
    "resource-id" to """[vid="close_btn"]""",
    "控件类名" to """Button[clickable=true]""",
    "正则匹配" to """[text~="(?is).*skip.*"][visibleToUser=true]""",
    "desc 包含" to """[desc*="关闭"]""",
    "多条件组合" to """([text*="广告"] && [clickable=true]) || [vid="ad_close"]""",
    "父级锚定" to """@TextView[clickable=true] < [childCount=2]""",
)

/** 动作专属提示:选择器是否必填 / 注意事项 */
private val ACTION_HINTS = mapOf(
    "back" to "back 是全局动作,无需选择器(matches 可留空,页面就绪即执行);常与 preKeys 组成顺序链",
    "none" to "仅标记不执行操作:作 preKeys 链的占位/条件节点,建议配 matches 与 actionMaximum:1",
    "swipe" to "swipe 留空选择器 = 整屏上滑;精确滑动请在高级参数填 swipeArg",
    "click" to "click 混合语义:优先节点点击,不可点时点中心坐标;怪异布局可加 position",
    "clickNode" to "仅发节点 ACTION_CLICK,不发手势;控件不可点时无效果",
    "clickCenter" to "仅坐标手势点中心;必填选择器(目标节点决定点哪里)",
    "inputText" to "当前仅聚焦输入框;需系统输入法配合输入",
    "launchApp" to "尚未实现,保存后不会执行",
)

/** 顶部标题 */
@Composable
private fun TitleBar(serviceConnected: Boolean = false, servicePending: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "自动化助手",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 权限项:弹窗里逐条展示的权限(含授权状态与跳转) */
private data class PermissionItem(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val granted: Boolean,
)

/**
 * 自启动/后台运行检测:AppOpsManager 的 OP_RUN_ANY_IN_BACKGROUND(op 编号 43,API 28+)。
 * 无独立「自启动」权限,各 ROM 的自启动开关最终都落到此 op。
 * MODE_ALLOWED / MODE_DEFAULT 都视为可用(DEFAULT 表示未显式限制,系统默认允许后台);
 * 仅 MODE_IGNORED / MODE_ERRORED(被用户/ROM 显式禁止)才算未授权——
 * 之前把 DEFAULT 当未授权,导致「系统里开了自启动,弹窗却显示未授权」。
 */
@Suppress("DEPRECATION") // 平台无替代公开 API:公开的 unsafeCheckOpNoThrow(字符串 op 名)本身即弃用,仅此途径可查
private fun queryAutoStartAllowed(context: android.content.Context): Boolean {
    return runCatching {
        val am = context.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        val uid = context.packageManager.getApplicationInfo(context.packageName, 0).uid
        val mode = am.unsafeCheckOpNoThrow(
            "RUN_ANY_IN_BACKGROUND", uid, context.packageName
        )
        mode == android.app.AppOpsManager.MODE_ALLOWED ||
            mode == android.app.AppOpsManager.MODE_DEFAULT
    }.getOrDefault(false)
}

/** 权限管理卡片:点击弹出权限清单(忽略电池优化 / 自启动),逐项显示授权状态并可跳转 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionManageCard(context: android.content.Context) {
    var dialogOpen by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }  // 从设置页返回后刷新授权状态
    val scope = rememberCoroutineScope()

    // 忽略电池优化:PowerManager 实时查询
    val batteryGranted = remember(refreshTick) {
        runCatching {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }.getOrDefault(false)
    }
    // 自启动:appops RUN_ANY_IN_BACKGROUND(需 Shizuku);shell 命令阻塞,必须 IO 线程异步查询
    var autoStartGranted by remember { mutableStateOf(false) }
    LaunchedEffect(refreshTick) {
        autoStartGranted = withContext(Dispatchers.IO) { queryAutoStartAllowed(context) }
    }
    // 弹窗中的权限清单,逐条点击触发各自的授权动作
    val items = listOf(
        PermissionItem(
            icon = Icons.Filled.BatterySaver,
            title = "忽略电池优化",
            desc = "防止后台被杀,保证任务持续运行",
            granted = batteryGranted,
        ),
        PermissionItem(
            icon = Icons.Filled.PlayCircleOutline,
            title = "自启动",
            desc = "允许开机自动拉起本应用",
            granted = autoStartGranted,
        ),
    )

    Card(
        onClick = { dialogOpen = true },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentIcon(
                imageVector = Icons.Filled.Security,
                accent = MaterialTheme.colorScheme.secondary,
                size = 40.dp,
                iconSize = 22.dp,
                shape = CircleShape,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "权限管理",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "后台保活相关权限管理入口",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 授权页是独立 Activity:打开弹窗时查一次,从设置页返回(ON_RESUME)时再刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && dialogOpen) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(dialogOpen) {
        if (dialogOpen) refreshTick++
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("需要的权限") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 强调色圆图标:已授权=主色,未授权=灰
                            AccentIcon(
                                imageVector = p.icon,
                                accent = if (p.granted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                size = 38.dp,
                                iconSize = 20.dp,
                                shape = CircleShape,
                                tint = if (p.granted) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = p.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // 状态徽标与授权按钮共用固定宽度容器,两行对齐不跳动
                            Box(
                                modifier = Modifier.width(64.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (p.granted) {
                                    // 授权状态:强调色圆点
                                    StatusDot(active = true, accent = MaterialTheme.colorScheme.primary)
                                } else {
                                    // 未授权:行内按钮逐项授权
                                    TextButton(
                                        onClick = {
                                            requestPermission(
                                                context, p.title, scope,
                                                onDone = { refreshTick++ },
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp),
                                    ) {
                                        Text(
                                            text = "去授权",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) { Text("关闭") }
            },
        )
    }
}

/**
 * 单条权限的授权动作:
 * - 忽略电池优化:拉起系统授权弹窗(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
 * - 自启动:跳应用详情页由用户按 ROM 开启(自启动无统一系统入口);
 * - 显示在其他应用上层:跳系统悬浮窗授权页。
 */
private fun requestPermission(
    context: android.content.Context,
    title: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit,
) {
    when (title) {
        "忽略电池优化" -> runCatching {
            context.startActivity(Intent(
                ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ))
            // 系统授权弹窗不离开本 Activity,ON_RESUME 不触发;延迟等用户操作后主动刷新
            scope.launch {
                kotlinx.coroutines.delay(3000)
                onDone()
            }
        }
        "自启动" -> {
            runCatching {
                context.startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ))
            }
        }
        "显示在其他应用上层" -> {
            // 悬浮窗已改用 TYPE_ACCESSIBILITY_OVERLAY(无障碍层),无需此权限;忽略
        }
    }
}

/** Shizuku 状态文案 */
private fun shizukuDescription(context: android.content.Context, uiState: AutomationManager.State): String =
    when (uiState.shizukuStatus) {
        AutomationManager.ShizukuStatus.Ready -> context.getString(R.string.status_shizuku_ready)
        AutomationManager.ShizukuStatus.Unready -> context.getString(R.string.status_shizuku_unready)
        else -> "等待 Shizuku 授权…"
    }

/**
 * 规则文档阅读页:assets/rules-guide.md 全屏覆盖显示。
 * 轻量 markdown 渲染:标题分级、表格行转对齐文本、代码块等宽、列表缩进;返回键关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesDocPage(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var markdown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        markdown = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("rules-guide.md").bufferedReader().use { it.readText() }
            }.getOrNull() ?: "文档缺失:assets/rules-guide.md"
        }
    }
    // 返回键由 Dialog 的 onDismissRequest 处理(关闭 = onClose)
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    // Dialog 设了 decorFitsSystemWindows=false,不自动避让,手动让出状态栏
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 与任务页/编辑页顶栏一致的强调色返回圆钮
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "规则文档",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { padding ->
        val text = markdown
        if (text == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // 解析一次:块列表 + 目录锚点表(slug → 标题块索引),目录链接点击滚动到标题
            val blocks = remember(text) { RulesDocBlocks(text) }
            val anchorIndex = remember(blocks) {
                buildMap {
                    blocks.forEachIndexed { idx, block ->
                        // 锚点行挂在其后的标题块上,目录跳转直接定位该块
                        block.anchors.forEach { put(it, idx) }
                    }
                }
            }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                blocks.forEachIndexed { idx, block ->
                    // key 用索引:文档中存在重复内容的块(空行/相同行),hashCode 会撞 key
                    item(key = idx) {
                        RulesDocBlock(block) { target ->
                            val anchor = target.removePrefix("#").trim()
                            val destIdx = anchorIndex[anchor]
                            if (destIdx != null) {
                                // scrollToItem 带 offset:滚到标题块并给它留出顶部空隙,不贴顶
                                scope.launch { listState.scrollToItem(destIdx, -12) }
                            } else if (target.startsWith("http")) {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 文档行块:解析产物(类型 + 原始行);anchors = 挂在本块上的目录锚点(<a id="…">);indent = 缩进列(dp,列表内嵌套块继承父项缩进) */
private data class DocBlock(val kind: DocKind, val lines: List<String>, val anchors: List<String> = emptyList(), val indent: Int = 0)
private enum class DocKind { H1, H2, H3, Quote, Divider, Text, Code, ListItem, Table, Blank }

/**
 * markdown → 块序列(本项目的受控子集):
 * 标题 #/##/###、``` 代码块、> 引用、--- 分隔线、| 表格行、- 列表项、普通文本。
 * <a id="…"></a> 锚点行不渲染,挂到其后的第一个块(目录跳转定位用)。
 */
private fun RulesDocBlocks(src: String): List<DocBlock> {
    val out = ArrayList<DocBlock>()
    var i = 0
    val pendingAnchors = ArrayList<String>()
    val anchorRe = Regex("""^<a id="([^"]+)"></a>\s*$""")
    val lines = src.lines()
    fun add(block: DocBlock) {
        out.add(if (pendingAnchors.isEmpty()) block else block.copy(anchors = ArrayList(pendingAnchors)))
        pendingAnchors.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        when {
            anchorRe.containsMatchIn(line) -> { pendingAnchors += anchorRe.find(line)!!.groupValues[1]; i++ }
            line.trimStart().startsWith("```") -> {
                // 缩进的 ``` = 列表项内嵌套代码块,保留缩进渲染,不与列表层级断裂
                val indentCol = line.indexOf('`') / 2
                val code = ArrayList<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code += lines[i]; i++ }
                i++ // 跳过结束 ```
                // 代码行统一去掉与围栏相同的缩进列
                val strip = " ".repeat(line.indexOf('`'))
                add(DocBlock(DocKind.Code, code.map { if (it.startsWith(strip)) it.removePrefix(strip) else it.trimStart() }, indent = indentCol * 16))
            }
            line.startsWith("### ") -> { add(DocBlock(DocKind.H3, listOf(line.removePrefix("### ")))); i++ }
            line.startsWith("## ") -> { add(DocBlock(DocKind.H2, listOf(line.removePrefix("## ")))); i++ }
            line.startsWith("# ") -> { add(DocBlock(DocKind.H1, listOf(line.removePrefix("# ")))); i++ }
            line.startsWith(">") -> {
                // 引用块:连续 > 行合并
                val quote = ArrayList<String>()
                while (i < lines.size && lines[i].startsWith(">")) {
                    quote += lines[i].removePrefix(">").removePrefix(" ")
                    i++
                }
                add(DocBlock(DocKind.Quote, quote))
            }
            Regex("^---+$").containsMatchIn(line.trim()) && line.trim().startsWith("-") -> {
                out.add(DocBlock(DocKind.Divider, emptyList())); i++
            }
            line.startsWith("|") -> {
                val rows = ArrayList<String>()
                while (i < lines.size && lines[i].startsWith("|")) { rows += lines[i]; i++ }
                add(DocBlock(DocKind.Table, rows))
            }
            Regex("^\\s*- ").containsMatchIn(line) -> { add(DocBlock(DocKind.ListItem, listOf(line))); i++ }
            Regex("^\\s*\\d+\\. ").containsMatchIn(line) -> { add(DocBlock(DocKind.ListItem, listOf(line))); i++ }
            line.isBlank() -> { add(DocBlock(DocKind.Blank, emptyList())); i++ }
            else -> { add(DocBlock(DocKind.Text, listOf(line))); i++ }
        }
    }
    return out
}

/**
 * 行内 markdown → AnnotatedString:**加粗**、`代码`、[文本](#锚点或url)。
 * 链接加 "doc_link" 注解(文本 = 目标),由渲染层拦截点击实现目录跳转/外链。
 */
private fun inlineDoc(text: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    var i = 0
    val bold = androidx.compose.ui.text.font.FontWeight.SemiBold
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = bold))
                    append(text.substring(i + 2, end)); pop()
                    i = end + 2
                } else { append("**"); i += 2 }
            }
            rest.startsWith("`") -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontFamily = mono))
                    append(text.substring(i + 1, end)); pop()
                    i = end + 1
                } else { append("`"); i += 1 }
            }
            rest.startsWith("[") && rest.contains("](") -> {
                val close = text.indexOf("](", i)
                val end = text.indexOf(')', close + 2)
                if (close > 0 && end > 0) {
                    val label = text.substring(i + 1, close)
                    val target = text.substring(close + 2, end)
                    pushStringAnnotation("doc_link", target)
                    pushStyle(androidx.compose.ui.text.SpanStyle(
                        color = androidx.compose.ui.graphics.Color(0xFF2A6DD8),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    ))
                    append(label); pop(); pop()
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

/** 表格渲染:列宽按内容对齐(等宽字体),表头加分隔线;超宽横向滚动 */
@Composable
private fun DocTable(rows: List<String>) {
    // 去掉分隔行 |---|---|,每行切单元格
    val cells = rows
        .filterNot { it.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isEmpty() }
        .map { row -> row.trim('|').split('|').map { it.trim() } }
    if (cells.isEmpty()) return
    val colCount = cells.maxOf { it.size }
    // 每列宽度 = 该列最大字符宽(CJK 记 2)
    fun visualLen(s: String): Int = s.sumOf { if (it.code > 0x2E80) 2 else 1 }
    val widths = IntArray(colCount)
    cells.forEach { row -> row.forEachIndexed { c, cell -> widths[c] = maxOf(widths[c], visualLen(cell).coerceAtMost(40)) } }
    fun pad(s: String, w: Int): String {
        val len = visualLen(s)
        return if (len >= w) s else s + " ".repeat(w - len)
    }
    val rendered = cells.mapIndexed { rIdx, row ->
        (0 until colCount).joinToString("  ") { c ->
            pad(row.getOrElse(c) { "" }, widths[c])
        }.trimEnd()
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            rendered.forEachIndexed { rIdx, line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    fontWeight = if (rIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (rIdx == 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.text.TextStyle.Default.color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                )
                if (rIdx == 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}

/** 块渲染:对应样式输出;链接点击回调(目录跳转/外链) */
@Composable
private fun RulesDocBlock(block: DocBlock, onLink: (String) -> Unit = {}) {
    // 链接文本:点击命中 doc_link 注解区域时回调目标
    @Composable
    fun LinkText(text: androidx.compose.ui.text.AnnotatedString, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
        // ClickableText 已弃用:改用 Text + linkAnnotation 拦截点击(doc_link 注解区域)
        androidx.compose.foundation.text.BasicText(
            text = androidx.compose.ui.text.buildAnnotatedString {
                append(text)
                text.getStringAnnotations("doc_link", 0, text.length).forEach { ann ->
                    addLink(
                        androidx.compose.ui.text.LinkAnnotation.Clickable(
                            tag = "doc_link",
                            styles = androidx.compose.ui.text.TextLinkStyles(
                                style = androidx.compose.ui.text.SpanStyle(color = style.color),
                            ),
                        ) { onLink(ann.item) },
                        ann.start, ann.end,
                    )
                }
            },
            style = style,
            modifier = modifier,
        )
    }
    when (block.kind) {
        DocKind.H1 -> LinkText(inlineDoc(block.lines.first()), MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
            Modifier.padding(top = 8.dp, bottom = 4.dp))
        DocKind.H2 -> LinkText(inlineDoc(block.lines.first()), MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary),
            Modifier.padding(top = 12.dp, bottom = 4.dp))
        DocKind.H3 -> LinkText(inlineDoc(block.lines.first()), MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            Modifier.padding(top = 8.dp, bottom = 2.dp))
        DocKind.Quote -> Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Row {
                Box(
                    modifier = Modifier.width(3.dp).fillMaxHeightIfPossible(),
                ) {}
                Text(
                    block.lines.joinToString("\n") { inlineDoc(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        DocKind.Divider -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 8.dp),
        )
        DocKind.Code -> Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .let { if (block.indent > 0) it.padding(start = block.indent.dp) else it }
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                block.lines.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                modifier = Modifier.padding(10.dp).horizontalScroll(rememberScrollState()),
            )
        }
        DocKind.Table -> DocTable(block.lines)
        DocKind.ListItem -> {
            val raw = block.lines.first()
            val indent = when {
                raw.startsWith("    ") -> 28.dp
                raw.startsWith("  ") -> 16.dp
                else -> 4.dp
            }
            val text = raw.trimStart()
            // 数字列表保留序号,- 列表换成圆点
            val bullet = if (Regex("^\\d+\\. ").containsMatchIn(text)) "" else "• "
            LinkText(
                inlineDoc(if (bullet.isEmpty()) text else bullet + text.removePrefix("- ")),
                MaterialTheme.typography.bodyMedium,
                Modifier.padding(start = indent, bottom = 2.dp),
            )
        }
        DocKind.Text -> LinkText(
            inlineDoc(block.lines.first()),
            MaterialTheme.typography.bodyMedium,
            Modifier.padding(bottom = 2.dp),
        )
        DocKind.Blank -> Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun Modifier.fillMaxHeightIfPossible(): Modifier = this

@Preview(showBackground = true)
@Composable
private fun AutomationScreenPreview() {
    AutoTaskTheme {
        AutomationScreen()
    }
}
