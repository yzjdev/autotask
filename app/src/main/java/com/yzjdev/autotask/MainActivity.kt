package com.yzjdev.autotask

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import rikka.shizuku.Shizuku
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.yzjdev.autotask.automation.AppIconCache
import com.yzjdev.autotask.automation.AutomationManager
import com.yzjdev.autotask.automation.AutoTaskAccessibilityService
import com.yzjdev.autotask.automation.InstalledApps
import com.yzjdev.autotask.automation.ShizukuShell
import com.yzjdev.autotask.automation.log.AppLog
import com.yzjdev.autotask.automation.log.LogLevel
import com.yzjdev.autotask.automation.log.LogRecord
import com.yzjdev.autotask.automation.log.LogSource
import com.yzjdev.autotask.ui.liquid.LiquidBottomTab
import com.yzjdev.autotask.ui.liquid.LiquidBottomTabs
import com.yzjdev.autotask.ui.theme.AutoTaskTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyRecentsHidden(this)
        // 日志记录开关:随偏好设置生效(日志页可切换)
        AppLog.enabled = getSharedPreferences("logs_ui", MODE_PRIVATE).getBoolean("log_enabled", true)
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

/** 自动化主界面 */
@Composable
fun AutomationScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val automation = remember { AutomationManager.get(context.applicationContext) }
    var uiState by remember { mutableStateOf(automation.currentState()) }
    // 确认弹窗:跳转到系统设置必须由用户显式确认,不允许自动执行
    var showA11yConfirm by remember { mutableStateOf(false) }
    // 底部导航:0=首页(权限/悬浮窗) 1=应用(应用列表/禁网)
    var tab by remember { mutableStateOf(0) }
    // 调试悬浮球开关:仅在无障碍服务已连接时可开;服务断开时自动复位
    var overlayOn by remember {
        mutableStateOf(AutoTaskAccessibilityService.isRunning && AutoTaskAccessibilityService.instance?.isDebugOverlayShowing == true)
    }
    // 订阅无障碍服务连接状态:服务断开时悬浮窗已被服务 hide(),同步复位开关状态
    DisposableEffect(Unit) {
        val listener = { running: Boolean ->
            if (!running) {
                overlayOn = false
            }
        }
        AutoTaskAccessibilityService.addStateListener(listener)
        // 悬浮窗显隐同步:长按悬浮球关闭等非主页入口的变化也驱动开关状态
        val overlayListener = { showing: Boolean -> overlayOn = showing }
        AutoTaskAccessibilityService.addOverlayListener(overlayListener)
        onDispose {
            AutoTaskAccessibilityService.removeStateListener(listener)
            AutoTaskAccessibilityService.removeOverlayListener(overlayListener)
        }
    }
    val scope = rememberCoroutineScope()

    // ---- 应用 tab 共享数据:提升到顶层,切 tab 不销毁、不重新加载 ----
    // 已安装应用:懒加载——首次切到「应用」tab 才查询(打开 app 不立即触发
    // 系统的「查询所有应用」权限弹窗,MIUI 等会在 getInstalledPackages 时弹)。
    // 包列表与逐个显示名解析都在 InstalledApps 内部跑 IO,主线程只接收最终结果,
    // 单次状态写入,不再拆成三份状态反复触发列表重组。
    var installedApps by remember { mutableStateOf<List<InstalledApps.AppEntry>?>(null) }
    LaunchedEffect(tab) {
        if (tab != 1 || installedApps != null) return@LaunchedEffect
        installedApps = try {
            InstalledApps.load(context.applicationContext)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()  // MIUI 等查询被拒:给空表,不让状态悬空
        }
    }
    // 应用 tab UI 状态:同样提升,切 tab 保留搜索;rememberSaveable 兼顾进程重建
    var query by rememberSaveable { mutableStateOf("") }
    // 应用列表排序模式持久化:0=名称升序,1=名称降序,2=安装时间新在前
    val sortModePrefs = context.getSharedPreferences("apps_ui", android.content.Context.MODE_PRIVATE)
    var sortMode by rememberSaveable { mutableStateOf(sortModePrefs.getInt("sort_mode", 0)) }
    // 应用/系统应用分段:0 = 用户应用, 1 = 系统应用
    var appTab by rememberSaveable { mutableStateOf(0) }
    // 禁网应用清单:持久化为准,提升到顶层,应用信息抽屉改开关后即时同步到列表角标
    var netBlockedPkgs by remember { mutableStateOf(loadNetBlocked(context)) }
    // 应用列表滚动状态:提升到顶层,进应用信息抽屉返回后恢复位置
    val appsListState = rememberLazyListState()

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

    // 底部导航的内容取样源:内容层经 layerBackdrop 记录后,导航栏据此做毛玻璃与透镜折射
    val navBackdrop = rememberLayerBackdrop()

    Scaffold(
        bottomBar = {
            // 标签内容配色按官方 demo:浅色纯黑、深色纯白(可见标签不随按压变色,
            // 玻璃里那份蓝色放大拷贝由记录层染色提供)
            val contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { tab = it },
                backdrop = navBackdrop,
                tabsCount = NavTabs.size,
                // 官方 demo 给整条留 36dp 外边距;底部安全间距仍要自己加(组件里没有)
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 36.dp),
            ) {
                NavTabs.forEachIndexed { index, navTab ->
                    LiquidBottomTab({ tab = index }, contentSpacing = 0.dp) {
                        Icon(
                            imageVector = navTab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = contentColor,
                        )
                        Text(
                            text = navTab.label,
                            color = contentColor,
                            fontSize = 12.sp,
                            // 行高收到与字号一致:文字行盒的额外行距才是图标与文字之间那段空隙
                            lineHeight = 12.sp,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // 内容层记录进 backdrop:玻璃才有可折射的画面。
        // 背景必须放在 layerBackdrop 之后 —— 库的默认 onDraw 是 drawContent(),
        // 只重画该节点自己的内容;背景写在它前面就落在记录范围之外,采样层会半透明,
        // 胶囊盖不住原标签,原图标文字就会透出来发灰
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(navBackdrop)
                .background(MaterialTheme.colorScheme.background),
        ) {
            val contentModifier = Modifier.padding(top = innerPadding.calculateTopPadding())
            val bottomInset = innerPadding.calculateBottomPadding()
            when (tab) {
                1 -> AppsScreen(
                    listState = appsListState,
                    allApps = installedApps ?: emptyList(),
                    loading = installedApps == null,
                    query = query,
                    onQueryChange = { query = it },
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
                    bottomInset = bottomInset,
                    modifier = contentModifier,
                )
                2 -> RulesScreen(bottomInset = bottomInset, modifier = contentModifier)
                3 -> LogsScreen(bottomInset = bottomInset, modifier = contentModifier)
                else -> HomeScreen(
                    context = context,
                    uiState = uiState,
                    overlayOn = overlayOn,
                    onOverlayToggle = {
                        val target = !overlayOn
                        val ok = AutoTaskAccessibilityService.instance
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
                    bottomInset = bottomInset,
                    modifier = contentModifier,
                )
            }
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

/** 导航项:索引 + 标题 + 图标 */
private data class NavTab(val index: Int, val label: String, val icon: ImageVector)

/** 底部导航项:首页 / 应用 / 规则 / 日志 */
private val NavTabs = listOf(
    NavTab(0, "首页", Icons.Filled.Home),
    NavTab(1, "应用", Icons.Filled.Apps),
    NavTab(2, "规则", Icons.AutoMirrored.Filled.Rule),
    NavTab(3, "日志", Icons.AutoMirrored.Filled.Article),
)

/** 日志级别对应的强调色 */
@Composable
private fun logLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.Info -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.Success -> MaterialTheme.colorScheme.primary
    LogLevel.Warn -> MaterialTheme.colorScheme.tertiary
    LogLevel.Error -> MaterialTheme.colorScheme.error
}

/**
 * 日志页:两级导航 —— 列表层按来源聚合(服务 / 悬浮窗 / 禁网 / 权限),
 * 点按进入该来源的日志流(按天分隔、级别着色、长按复制)。
 * 顶部保留搜索与级别筛选、记录开关与清空。数据订阅 [AppLog.records]。
 */
@Composable
private fun LogsScreen(bottomInset: Dp = 0.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 记录开关持久化:关闭后不再写入新日志
    val prefs = remember {
        context.getSharedPreferences("logs_ui", android.content.Context.MODE_PRIVATE)
    }
    var enabled by remember { mutableStateOf(prefs.getBoolean("log_enabled", true)) }
    var query by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var levelMenuOpen by remember { mutableStateOf(false) }
    // 当前打开的来源:null = 停在列表层
    var openSource by remember { mutableStateOf<LogSource?>(null) }

    val all by AppLog.records.collectAsStateWithLifecycle()
    // 列表层:按来源聚合统计,有最新日志的来源在前
    val entries = remember(all, query, levelFilter) { groupLogSources(all, query, levelFilter) }
    // 打开来源后的日志流:现算,新日志进来立刻接上
    val stream = remember(openSource, all, query, levelFilter) {
        val source = openSource
        if (source == null) {
            emptyList()
        } else {
            all.filter { r ->
                r.source == source &&
                    (levelFilter == null || r.level == levelFilter) &&
                    (query.isBlank() ||
                        r.message.contains(query, true) ||
                        r.source.label.contains(query, true))
            }
        }
    }
    // 会话层拦截返回键,先退回列表层
    BackHandler(enabled = openSource != null) { openSource = null }

    if (openSource != null) {
        SourceLogStream(
            source = openSource!!,
            records = stream,
            bottomInset = bottomInset,
            onBack = { openSource = null },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 第一行:搜索 + 级别筛选 + 记录开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索日志",
                modifier = Modifier.weight(1f),
            )
            // 级别筛选:菜单里带各级别条数
            Box {
                TextButton(onClick = { levelMenuOpen = true }) {
                    Text(
                        text = levelFilter?.label ?: "级别",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                DropdownMenu(
                    expanded = levelMenuOpen,
                    onDismissRequest = { levelMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("全部级别") },
                        onClick = { levelFilter = null; levelMenuOpen = false },
                        trailingIcon = {
                            if (levelFilter == null) {
                                Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp))
                            }
                        },
                    )
                    LogLevel.entries.forEach { level ->
                        DropdownMenuItem(
                            text = { Text("${level.label}  ${all.count { it.level == level }}") },
                            onClick = { levelFilter = level; levelMenuOpen = false },
                            trailingIcon = {
                                if (levelFilter == level) {
                                    Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp))
                                }
                            },
                        )
                    }
                }
            }
            // 记录开关:暂停后新日志直接丢弃,列表与流都不再增长
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    AppLog.enabled = it
                    prefs.edit().putBoolean("log_enabled", it).apply()
                },
            )
        }

        // 第二行:计数 + 清空
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${all.size} 条日志  ${entries.size} 个来源",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { AppLog.clear() }, enabled = all.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = "清空日志",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (entries.isEmpty()) {
            CenteredPlaceholder {
                AccentIcon(
                    Icons.AutoMirrored.Filled.Article,
                    MaterialTheme.colorScheme.secondary,
                    size = 56.dp,
                    iconSize = 28.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when {
                        !enabled -> "记录已暂停\n开启「记录」后新的操作才会写入"
                        all.isEmpty() -> "暂无日志\n服务连接、悬浮窗开关、禁网设置会记录在此"
                        else -> "没有匹配的日志"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp + bottomInset),
            ) {
                items(entries, key = { it.source.name }) { entry ->
                    SourceCard(entry) { openSource = entry.source }
                }
            }
        }
    }
}

/** 一个来源的聚合统计:列表层用 */
private data class LogSourceEntry(
    val source: LogSource,
    val total: Int,
    val latest: LogRecord?,
    /** 各级别条数,用于卡片上的迷你分布条 */
    val byLevel: Map<LogLevel, Int>,
)

/** 来源对应的图标 */
private fun sourceIcon(source: LogSource): ImageVector = when (source) {
    LogSource.Service -> Icons.Filled.PowerSettingsNew
    LogSource.Overlay -> Icons.Filled.Layers
    LogSource.Network -> Icons.Filled.Wifi
    LogSource.Permission -> Icons.Filled.Security
}

/** 来源对应的强调色:沿用日志级别之外的四个主题色槽 */
@Composable
private fun sourceColor(source: LogSource): Color = when (source) {
    LogSource.Service -> MaterialTheme.colorScheme.primary
    LogSource.Overlay -> MaterialTheme.colorScheme.secondary
    LogSource.Network -> MaterialTheme.colorScheme.tertiary
    LogSource.Permission -> MaterialTheme.colorScheme.error
}

/** 按来源聚合:先套搜索与级别筛选,再分组统计,最新一条有日志的来源排最前 */
private fun groupLogSources(
    records: List<LogRecord>,
    query: String = "",
    levelFilter: LogLevel? = null,
): List<LogSourceEntry> {
    val matched = records.filter { r ->
        (levelFilter == null || r.level == levelFilter) &&
            (query.isBlank() ||
                r.message.contains(query, true) ||
                r.source.label.contains(query, true))
    }
    val grouped = linkedMapOf<LogSource, MutableList<LogRecord>>()
    matched.forEach { grouped.getOrPut(it.source) { mutableListOf() } += it }
    return grouped.map { (source, list) ->
        LogSourceEntry(
            source = source,
            total = list.size,
            latest = list.lastOrNull(),
            byLevel = list.groupingBy { it.level }.eachCount(),
        )
    }.sortedByDescending { it.latest?.at ?: 0L }
}

/** 来源卡:图标 + 来源名 + 最新一条 + 条数 + 级别分布条 */
@Composable
private fun SourceCard(entry: LogSourceEntry, onClick: () -> Unit) {
    val accent = sourceColor(entry.source)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentIcon(sourceIcon(entry.source), accent)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.source.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    entry.latest?.let { latest ->
                        Text(
                            text = AppLog.formatTime(latest.at),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                // 最新一条正文:没有就留一行提示
                Text(
                    text = entry.latest?.message ?: "暂无记录",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 级别分布条:各级别按比例占宽,一眼看出错误占比
                if (entry.total > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                    ) {
                        LogLevel.entries.forEach { level ->
                            val count = entry.byLevel[level] ?: 0
                            if (count == 0) return@forEach
                            Box(
                                modifier = Modifier
                                    .weight(count.toFloat())
                                    .fillMaxHeight()
                                    .background(logLevelColor(level)),
                            )
                        }
                        // 未命中的级别不占宽;剩余空间留空,不做拉伸
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${entry.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }
}

/** 来源日志流:顶栏返回 + 来源名,下面是按天分隔的日志行,长按复制 */
@Composable
private fun SourceLogStream(
    source: LogSource,
    records: List<LogRecord>,
    bottomInset: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // 渲染行:日期分隔条与日志混在同一列表里
    val rows = remember(records) {
        buildList<Any> {
            records.forEachIndexed { index, r ->
                val prev = records.getOrNull(index - 1)
                if (prev == null || !AppLog.isSameDay(prev.at, r.at)) {
                    add(AppLog.formatDay(r.at))
                }
                add(r)
            }
        }
    }
    // 打开与来新日志都贴到底部(最新一条在最下)
    LaunchedEffect(source, rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${records.size} 条日志",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (rows.isEmpty()) {
            CenteredPlaceholder {
                Text(
                    text = "该来源暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(rows.size, key = { i ->
                    val row = rows[i]
                    if (row is LogRecord) row.id else "day-$i"
                }) { i ->
                    when (val row = rows[i]) {
                        is String -> DayDivider(row)
                        is LogRecord -> LogRow(row) {
                            copyText(
                                context,
                                "${AppLog.formatTime(row.at)} [${row.source.label}] ${row.message}",
                            )
                        }
                    }
                    if (i == rows.size - 1) Spacer(modifier = Modifier.height(bottomInset + 8.dp))
                }
            }
        }
    }
}

/** 日志行:等宽时间 + 级别色条 + 级别 + 正文,长按复制 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(record: LogRecord, onCopy: () -> Unit) {
    val accent = logLevelColor(record.level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onCopy)
            .padding(vertical = 4.dp),
    ) {
        // 级别色条:左侧竖条,一眼区分级别
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(accent, CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = AppLog.formatTime(record.at),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = record.level.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = record.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 规则页:规则列表。
 *
 * 规则数据源尚未接入,列表恒为空 ——
 * 这里先把页面骨架与空态搭好,接入数据后把占位换成列表项渲染即可。
 */
@Composable
private fun RulesScreen(bottomInset: Dp = 0.dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomInset),
    ) {
        // 计数行:与日志页、消息页同一套版式
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "共 0 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        CenteredPlaceholder {
            Text(
                text = "暂无规则\n规则数据源尚未接入",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}



/** 日期分隔条:小号灰字居中 */
@Composable
private fun DayDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 复制文本到剪贴板并提示 */
private fun copyText(context: android.content.Context, text: String) {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

/** 主页:Bento 网格 —— 左侧服务大卡(跨高) + 右侧功能小卡 + 底部权限管理 */
@Composable
private fun HomeScreen(
    context: android.content.Context,
    uiState: AutomationManager.State,
    overlayOn: Boolean,
    onOverlayToggle: () -> Unit,
    onToggleAccessibility: () -> Unit,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // 只认系统里的无障碍开关:开就是开,关就是关
    val serviceConnected = uiState.isAccessibilityEnabled
    var recentsHidden by remember { mutableStateOf(isRecentsHidden(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            // 底部留白:权限管理卡片不贴屏幕底(兼顾导航条遮挡与玻璃导航栏高度)
            .padding(bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleBar()

        // 中控面板:电源拨盘 + 状态文案
        ControlPanel(
            serviceConnected = serviceConnected,
            accessibilityEnabled = uiState.isAccessibilityEnabled,
            hasSecureSetting = uiState.hasSecureSetting,
            onToggle = onToggleAccessibility,
        )

        // 功能开关卡:与权限管理卡片同一套样式,竖排
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleCard(
                icon = Icons.Filled.Adjust,
                accent = MaterialTheme.colorScheme.tertiary,
                title = "节点悬浮窗",
                description = "查看屏幕节点,辅助定位控件",
                checked = overlayOn,
                enabled = uiState.isAccessibilityEnabled,
                onToggle = onOverlayToggle,
            )
            ToggleCard(
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

        // 权限管理入口
        PermissionManageCard(context)
    }
}

/**
 * 服务控制卡:左侧电源状态指示,中间状态与说明,右侧开关;整卡可点启停。
 * 横向排布比原来的居中大拨盘省纵向空间,状态与操作落在同一视线内。
 */
@Composable
private fun ControlPanel(
    serviceConnected: Boolean,
    accessibilityEnabled: Boolean,
    hasSecureSetting: Boolean,
    onToggle: () -> Unit,
) {
    // 开 = 主色;关 = 灰
    val accent = if (serviceConnected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val title = if (serviceConnected) "服务运行中" else "服务未开启"
    // 副文案只讲无障碍服务本身:禁网、悬浮窗各自有开关,不在这里提
    val subtitle = when {
        serviceConnected -> "无障碍服务已开启"
        hasSecureSetting -> "点击开启无障碍服务"
        else -> "缺「写入安全设置」权限"
    }

    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PowerStateIndicator(
                connected = serviceConnected,
                accent = accent,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = accessibilityEnabled,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

/** 电源状态指示:光晕(已开启)+ 状态环 + 中央电源图标 */
@Composable
private fun PowerStateIndicator(
    connected: Boolean,
    accent: Color,
) {
    val size = 56.dp
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        // 光晕:运行中才显示
        if (connected) {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                        ),
                    ),
            )
        }
        // 状态环:已开启满环;未开启为空环
        CircularProgressIndicator(
            progress = { if (connected) 1f else 0f },
            modifier = Modifier.size(size),
            color = accent,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            strokeWidth = 3.dp,
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (connected) accent else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (connected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * 设置开关卡:与权限管理卡片同一套样式 —— 强调色圆图标 + 标题/说明,整卡可点。
 * 不放开关键:开关态只用卡片背景色表达(开启时染上该卡自己的强调色浅底)。
 */
@Composable
private fun ToggleCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        onClick = { if (enabled) onToggle() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                accent.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
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
                imageVector = icon,
                accent = accent,
                size = 40.dp,
                iconSize = 22.dp,
                shape = CircleShape,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
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

/**
 * 胶囊搜索框:浅底无描边,前置搜索图标,有内容时右侧出现清空。
 * 相比 OutlinedTextField 少了外描边与浮动标签的占位,高度与内边距完全可控,
 * 放进工具条里不会显得臃肿。
 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        color = scheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            // 占位与输入框叠放:BasicTextField 没有 placeholder 参数
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onValueChange("") },
                )
            }
        }
    }
}

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

/** 应用 tab:用户应用 / 系统应用分 tab,标题栏内搜索;点按或长按弹出应用信息抽屉 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppsScreen(
    listState: androidx.compose.foundation.lazy.LazyListState,
    allApps: List<InstalledApps.AppEntry>,
    loading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    appTab: Int,
    onAppTabChange: (Int) -> Unit,
    sortMode: Int,
    onSortModeChange: (Int) -> Unit,
    netBlockedPkgs: Set<String>,
    onNetworkBlocked: (String, Boolean) -> Unit,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    // 仅按应用名 / 包名过滤(标题栏搜索框与列表共用)
    val searchedApps = remember(allApps, query) {
        if (query.isBlank()) allApps else allApps.filter { app ->
            app.label.contains(query, true) || app.packageName.contains(query, true)
        }
    }
    // 中文顺序已由 InstalledApps.load 在 IO 线程排好,这里只做 O(N) 的过滤与复用:
    // 在主线程用 Collator 重排几百个应用会卡住列表首次出现的那一帧
    // 列表 = 搜索过滤 + 排序 + 按 tab 严格过滤(用户/系统不混排)
    val visibleApps = remember(searchedApps, appTab, sortMode) {
        val filtered = searchedApps.filter { app ->
            (appTab == 1) == app.isSystem
        }
        // 组内按所选排序
        when (sortMode) {
            1 -> filtered.asReversed()                      // 名称降序 = 升序反向
            2 -> filtered.sortedByDescending { it.installTime }
            else -> filtered                                // 名称升序 = 查询给出的顺序
        }
    }
    // 切换排序后整个列表顺序都变了,原来的滚动位置失去意义:回到第一项。
    // 首次组合不滚动,保留从其它 tab 返回时的滚动位置。
    var sortSettled by remember { mutableStateOf(false) }
    LaunchedEffect(sortMode) {
        if (sortSettled) listState.scrollToItem(0) else sortSettled = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 紧凑顶栏:搜索框 + 排序菜单
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        ) {
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索应用名 / 包名",
            modifier = Modifier.weight(1f),
        )
        // 排序菜单:名称升/降序、安装时间
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
                    text = { Text("按安装时间(新→旧)") },
                    onClick = { onSortModeChange(2); sortMenuOpen = false },
                    trailingIcon = { if (sortMode == 2) Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) },
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

        // 滚动状态由上层传入:返回应用 tab 后恢复上一次滚动位置
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
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleApps.size, key = { visibleApps[it].packageName }, contentType = { "app" }) { i ->
                    val app = visibleApps[i]
                    AppListCard(
                        app = app,
                        blocked = netBlockedPkgs.contains(app.packageName),
                        onToggleNetwork = { target ->
                            onNetworkBlocked(app.packageName, target)
                        },
                    )
                }
            }
        }
    }
}

/**
 * 按需加载应用图标:卡片进入组合时才解码,配合 [AppIconCache] 的 LRU,
 * 滚动过程中只有可见项产生加载,不再一次性解码整个应用列表。
 */
@Composable
private fun rememberAppIcon(pkg: String): ImageBitmap? {
    val context = LocalContext.current
    // 绘制尺寸 = 显示尺寸 40dp × 屏幕密度,避免低分辨率位图被拉伸发虚
    val sizePx = remember(context) {
        (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    }
    var icon by remember(pkg) { mutableStateOf(AppIconCache.peek(pkg)) }
    LaunchedEffect(pkg) {
        if (icon == null) icon = AppIconCache.load(context.applicationContext, pkg, sizePx)
    }
    return icon
}

/** 应用列表卡片:应用图标(禁网压暗+红色角标)+ 名称/包名 + 「联网/已禁网」胶囊开关 */
@Composable
private fun AppListCard(
    app: InstalledApps.AppEntry,
    blocked: Boolean,
    onToggleNetwork: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // 图标按需加载:卡片进入组合时才解码,配合 AppIconCache 的 LRU
    val icon = rememberAppIcon(app.packageName)
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
            // 名称 + 包名
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
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
                // 点包名复制到剪贴板
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .offset(x = (-4).dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { copyText(context, app.packageName) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            // 联网开关:图标+文字胶囊,联网=强调色浅底,禁网=错误色实底(无描边)
            Surface(
                color = if (netEnabled) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.error,
                contentColor = if (netEnabled) scheme.onPrimaryContainer else scheme.onError,
                shape = CircleShape,
                // 先裁形状再接管点击:否则水波纹节点落在 Surface 的裁剪之外,反馈是矩形
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
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
                                if (err == null) {
                                    AppLog.success(
                                        LogSource.Network,
                                        "${app.packageName} → ${if (targetBlocked) "禁用" else "允许"}",
                                    )
                                    onToggleNetwork(targetBlocked)
                                } else {
                                    netEnabled = !targetEnabled
                                    AppLog.error(LogSource.Network, "${app.packageName} → $err")
                                    Toast.makeText(context, "禁网设置失败: $err", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onFail = { msg ->
                            AppLog.error(LogSource.Network, "${app.packageName} → $msg")
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
            ) {
                // 只留图标:去掉文字后改成等边内边距,CircleShape 才是个正圆
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (netEnabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = if (netEnabled) "禁用网络" else "已禁网",
                        modifier = Modifier.size(15.dp),
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

/** 开关包名的联网;返回 null = 成功,否则为失败原因。
 * 经 Shizuku 逐条 exec 偶发 exit=255/空 stderr(binder 抖动),故:
 * 合并为单条 shell 命令减少往返;失败重试;最终以 get 回读确认为准。
 * 全局互斥锁:连续对多个应用禁网时各处并发进入,多个 IRemoteProcess 同时跑会互相挤掉
 * (Shizuku binder 单通道,并发 transact 触发 DeadObject/255),在此串行化。 */
private val netOpMutex = Mutex()

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

/** 顶部标题 */
@Composable
private fun TitleBar() {
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
            desc = "防止后台被杀,保证服务持续运行",
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

@Preview(showBackground = true)
@Composable
private fun AutomationScreenPreview() {
    AutoTaskTheme {
        AutomationScreen()
    }
}
