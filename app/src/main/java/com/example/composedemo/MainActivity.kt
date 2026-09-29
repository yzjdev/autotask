package com.example.composedemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardBackspace
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.composedemo.automation.AutomationManager
import com.example.composedemo.automation.DramaAccessibilityService
import com.example.composedemo.automation.ShizukuShell
import com.example.composedemo.automation.LogStore
import com.example.composedemo.automation.NetPolicyStore
import com.example.composedemo.automation.NodeLocator
import com.example.composedemo.automation.NodeQuery
import com.example.composedemo.automation.Step
import com.example.composedemo.automation.StepCondition
import com.example.composedemo.automation.Task
import com.example.composedemo.automation.TaskStore
import com.example.composedemo.automation.label
import com.example.composedemo.automation.stepsSummary
import com.example.composedemo.ui.theme.ComposeDemoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyRecentsHidden(this)
        setContent {
            ComposeDemoTheme {
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

/** 按当前偏好应用排除最近任务(需在 Activity 存活时调用才对本任务生效) */
private fun applyRecentsHidden(activity: android.app.Activity) {
    val am = activity.getSystemService(android.app.ActivityManager::class.java) ?: return
    for (task in am.appTasks) {
        if (task.taskInfo.baseActivity?.packageName == activity.packageName) {
            task.setExcludeFromRecents(true)
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
    // 底部导航:0=首页(权限/悬浮窗) 1=应用(规则管理) 2=日志(执行记录)
    var tab by remember { mutableStateOf(0) }
    // 调试悬浮球开关:仅在无障碍服务已连接时可开;服务断开时自动复位
    var overlayOn by remember {
        mutableStateOf(DramaAccessibilityService.isRunning && DramaAccessibilityService.instance?.isDebugOverlayShowing == true)
    }
    // 订阅无障碍服务连接状态:服务断开时悬浮窗已被服务 hide(),同步复位开关状态
    DisposableEffect(Unit) {
        val listener = { running: Boolean -> if (!running) overlayOn = false }
        DramaAccessibilityService.addStateListener(listener)
        onDispose { DramaAccessibilityService.removeStateListener(listener) }
    }
    // 禁网恢复:进入界面时执行一次(VPN 授权为系统级持久授权,重启后直接重启服务即可)
    val scope = rememberCoroutineScope()
    var netPolicyRestored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!netPolicyRestored) {
            netPolicyRestored = true
            withContext(Dispatchers.IO) { restoreNetPolicies(context) }
        }
    }

    // ---- 应用 tab 共享数据:提升到顶层,切 tab 不销毁、不重新加载 ----
    var tasks by remember { mutableStateOf(TaskStore.loadAll(context.applicationContext)) }
    fun persistTasks(next: List<Task>) {
        tasks = next
        TaskStore.saveAll(context.applicationContext, next)
        DramaAccessibilityService.instance?.taskRunner?.setTasks(next)
        // 按当前前台页面重新评估触发,避免停留在目标页时保存的任务不响应
        DramaAccessibilityService.instance?.refreshTaskTriggers()
    }
    // 已安装应用:后台只加载一次(切回 tab 时 remember 仍在,不重复查询)
    var installedApps by remember { mutableStateOf<List<TaskStore.AppInfo>?>(null) }
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.Default) {
            TaskStore.loadInstalledApps(context.applicationContext, includeSystem = true)
        }
    }
    // 应用 tab UI 状态:同样提升,切 tab 保留搜索;rememberSaveable 兼顾进程重建
    var query by rememberSaveable { mutableStateOf("") }
    // 应用列表排序模式持久化:0=名称升序,1=名称降序,2=任务数多在前
    val sortModePrefs = context.getSharedPreferences("apps_ui", android.content.Context.MODE_PRIVATE)
    // 应用/系统应用分段:0 = 用户应用, 1 = 系统应用
    var appTab by rememberSaveable { mutableStateOf(0) }
    // 应用 tab → 任务页导航:null = 停留在应用列表;非空 = 该包名的独立任务页(全屏,隐藏底部导航)
    var taskPagePkg by rememberSaveable { mutableStateOf<String?>(null) }
    // 禁网应用清单:提升到顶层,应用信息抽屉改开关后即时同步到列表角标
    var netBlockedPkgs by remember { mutableStateOf(NetPolicyStore.loadBlocked(context)) }
    // 任务页 → 规则编辑页:null = 停留在任务页;非空 = 正在编辑该 id 的规则(新建时为临时标记)
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    // 任务页/编辑页独立显示时接管系统返回键:逐级返回而非退出应用
    BackHandler(enabled = taskPagePkg != null) {
        if (editingRuleId != null) editingRuleId = null else taskPagePkg = null
    }
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
    val allApps = remember(installedApps, tasks) {
        val infos = (installedApps ?: emptyList()).associateBy { it.packageName }
        val pkgs = linkedSetOf<String>()
        infos.keys.forEach { pkgs += it }
        tasks.forEach { pkgs += it.packageName }
        pkgs.map { pkg ->
            AppRow(
                packageName = pkg,
                label = labelByPkg[pkg] ?: pkg,
                taskCount = tasks.count { it.packageName == pkg },
                isSystem = infos[pkg]?.isSystem ?: false,
            )
        }.sortedBy { it.label.lowercase() }
    }
    // 应用图标缓存:后台逐包加载一次,切 tab 复用(新建 Bitmap + Canvas 绘制不能占主线程)
    var iconsByPkg by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(allApps) {
        val pending = allApps.map { it.packageName }.filterNot { iconsByPkg.containsKey(it) }
        if (pending.isEmpty()) return@LaunchedEffect
        val pm = context.packageManager
        // 绘制尺寸 = 显示尺寸 40dp × 屏幕密度,避免低分辨率位图被拉伸发虚
        val size = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val loaded = HashMap(iconsByPkg)
        withContext(Dispatchers.Default) {
            pending.forEach { pkg ->
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
        iconsByPkg = loaded
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
                        icon = { Icon(Icons.Filled.Article, contentDescription = null) },
                        label = { Text("日志") },
                        colors = navItemColors,
                    )
                }
            }
        },
    ) { innerPadding ->
        when (tab) {
            1 -> if (editingRuleId != null) {
                // 规则编辑页:全屏最顶层
                RuleEditorPage(
                    pkg = taskPagePkg ?: "",
                    existing = tasks.firstOrNull { it.id == editingRuleId },
                    onSave = { task ->
                        persistTasks(tasks.filter { it.id != task.id } + task)
                        editingRuleId = null
                    },
                    onBack = { editingRuleId = null },
                    modifier = Modifier.padding(innerPadding),
                )
            } else if (taskPagePkg != null) {
                TaskListPage(
                    pkg = taskPagePkg!!,
                    tasks = tasks,
                    onBack = { taskPagePkg = null },
                    onEditTask = { editingRuleId = it },
                    onNewTask = { editingRuleId = "new_${System.currentTimeMillis()}" },
                    onToggleTask = { id, enabled ->
                        persistTasks(tasks.map { if (it.id == id) it.copy(enabled = enabled) else it })
                    },
                    onPersistTasks = { persistTasks(it) },
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                AppsScreen(
                    allApps = allApps,
                    iconsByPkg = iconsByPkg,
                    query = query,
                    onQueryChange = { query = it },
                    onOpenApp = { taskPagePkg = it },
                    appTab = appTab,
                    onAppTabChange = { appTab = it },
                    sortMode = sortModePrefs.getInt("sort_mode", 0),
                    onSortModeChange = { sortModePrefs.edit().putInt("sort_mode", it).apply() },
                    netBlockedPkgs = netBlockedPkgs,
                    onNetworkBlocked = { pkg, blocked ->
                        val cur = netBlockedPkgs.toMutableSet()
                        if (blocked) cur += pkg else cur -= pkg
                        netBlockedPkgs = cur
                        NetPolicyStore.setBlocked(context, pkg, blocked)
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            2 -> LogsScreen(modifier = Modifier.padding(innerPadding))
            else -> HomeScreen(
                context = context,
                uiState = uiState,
                overlayOn = overlayOn,
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
    overlayOn: Boolean,
    onOverlayToggle: () -> Unit,
    onToggleAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val serviceConnected = uiState.isAccessibilityEnabled && DramaAccessibilityService.instance != null
    var recentsHidden by remember { mutableStateOf(isRecentsHidden(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleBar(serviceConnected = serviceConnected)

        // 中控面板:电源拨盘 + 状态文案
        ControlPanel(
            serviceConnected = serviceConnected,
            accessibilityEnabled = uiState.isAccessibilityEnabled,
            hasSecureSetting = uiState.hasSecureSetting,
            onToggle = onToggleAccessibility,
        )

        // 功能切换胶囊:横排
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TogglePill(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Adjust,
                accent = MaterialTheme.colorScheme.tertiary,
                title = "节点悬浮窗",
                checked = overlayOn,
                enabled = uiState.isAccessibilityEnabled,
                onToggle = onOverlayToggle,
            )
            TogglePill(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Layers,
                accent = MaterialTheme.colorScheme.secondary,
                title = "隐藏最近任务",
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
 * 中控面板:中央大号电源拨盘(状态光环 + 光晕,点击整盘启停),
 * 下方居中状态文案。
 */
@Composable
private fun ControlPanel(
    serviceConnected: Boolean,
    accessibilityEnabled: Boolean,
    hasSecureSetting: Boolean,
    onToggle: () -> Unit,
) {
    val accent = when {
        serviceConnected -> MaterialTheme.colorScheme.primary
        accessibilityEnabled -> MaterialTheme.colorScheme.tertiary
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
                modifier = Modifier.size(168.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 光晕:运行中才显示
                if (serviceConnected) {
                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(accent.copy(alpha = 0.25f), Color.Transparent),
                                ),
                            ),
                    )
                }
                // 状态光环
                CircularProgressIndicator(
                    progress = { if (serviceConnected) 1f else 0f },
                    modifier = Modifier.size(168.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    strokeWidth = 4.dp,
                )
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
                    modifier = Modifier.size(128.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = "启停无障碍服务",
                            modifier = Modifier.size(56.dp),
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
                    accessibilityEnabled -> "服务待连接"
                    else -> "服务未开启"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    serviceConnected -> "任务可自动执行"
                    accessibilityEnabled -> "请在无障碍设置中确认开关"
                    !hasSecureSetting -> "点击拨盘自动授权并开启"
                    else -> "点击拨盘开启无障碍服务"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 功能切换胶囊:整胶囊可点,强调色圆点 + 图标 + 标题,开启时染上强调色 */
@Composable
private fun TogglePill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: Color,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.large,
        color = if (checked) {
            accent.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
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
            Spacer(modifier = Modifier.weight(1f))
            // 胶囊右端状态圆点
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (checked) accent else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
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

/** 包名显示名:直接查已装应用标签,查不到回退包名 */
private fun appLabel(context: android.content.Context, pkg: String, sample: Task?): String {
    // 尝试从已装应用解析显示名(缓存成本低,chip 数量有限)
    val pm = context.packageManager
    return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
        .getOrDefault(pkg)
}

/** 应用条目:承载 packageName / label / taskCount / isSystem */
private data class AppRow(
    val packageName: String,
    val label: String,
    val taskCount: Int,
    val isSystem: Boolean,
)

/** 应用 tab:用户应用 / 系统应用分 tab,标题栏内搜索;点应用进入该应用的独立任务页,长按弹出应用信息抽屉 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppsScreen(
    allApps: List<AppRow>,
    iconsByPkg: Map<String, ImageBitmap>,
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
    // 列表 = 搜索过滤 + 排序 + 按 tab 严格过滤(用户/系统不混排)
    val visibleApps = remember(searchedApps, appTab, sortMode) {
        val filtered = searchedApps.filter { app ->
            (appTab == 1) == app.isSystem
        }
        when (sortMode) {
            1 -> filtered.sortedByDescending { it.label }
            2 -> filtered.sortedWith(compareByDescending<AppRow> { it.taskCount }.thenBy { it.label })
            else -> filtered.sortedBy { it.label }
        }
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
            }
        }
        }

        // 用户应用 / 系统应用分段
        TabRow(
            selectedTabIndex = appTab,
            modifier = Modifier.fillMaxWidth(),
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (visibleApps.isEmpty()) {
                item {
                    Text(
                        text = if (query.isBlank()) "未找到任何应用" else "无匹配应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(visibleApps.size, key = { visibleApps[it].packageName }) { i ->
                val app = visibleApps[i]
                AppListCard(
                    app = app,
                    icon = iconsByPkg[app.packageName],
                    blocked = netBlockedPkgs.contains(app.packageName),
                    onClick = { onOpenApp(app.packageName) },
                    onLongClick = { infoSheetPkg = app.packageName },
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

/** 应用列表卡片:强调色圆图标容器 + 名称/包名 + 任务数状态点;点击进入任务页,长按弹信息抽屉 */
@Composable
private fun AppListCard(
    app: AppRow,
    icon: ImageBitmap?,
    blocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            // 禁网态:错误色描边,与正常卡区分
            .then(
                if (blocked) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.large,
                ) else Modifier,
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                blocked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                app.taskCount > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
            // 应用图标:加载失败时用默认图标占位;禁网时叠加断开图标角标
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            // 禁网态:图标压暗,直观表示应用已离线
                            .then(if (blocked) Modifier.alpha(0.55f) else Modifier),
                    )
                } else {
                    AccentIcon(
                        imageVector = Icons.Filled.Apps,
                        accent = MaterialTheme.colorScheme.surfaceVariant,
                        size = 40.dp,
                        iconSize = 22.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (blocked) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = "已禁网",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = CircleShape,
                            )
                            .padding(2.dp)
                            .clip(CircleShape)
                            .align(Alignment.BottomEnd),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 禁网态:错误色小字提示
                if (blocked) {
                    Text(
                        text = "已断开网络",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 任务数状态点:有任务=强调色,无任务=灰
            StatusDot(active = app.taskCount > 0, accent = accent, size = 9.dp)
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

            // 功能开关区
            Text(
                text = "功能控制",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            AppToggleRow(
                title = "禁用网络",
                desc = "彻底阻断该应用联网(前台+后台,connectivity chain-3,无需 root/VPN,需 Shizuku);重启后自动恢复",
                checked = disableNetwork,
                onChange = { target ->
                    disableNetwork = target
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { setNetworkBlocked(pkg, target) }
                        if (ok) {
                            // 实际生效后才通知上层落盘并同步列表角标;失败回滚 UI
                            onBlockedChange(target)
                        } else {
                            disableNetwork = !target
                        }
                    }
                },
            )
        }
    }
}

/**
 * 彻底断网:connectivity chain-3(Android 11+ 平台防火墙,shell 身份可执行)。
 * 按 appId 在系统层拦截应用联网,前台后台都拦;无需 root、无 VPN 隧道。
 * 系统重启后规则清空,由 NetPolicyStore 持久化清单 + restoreNetPolicies 恢复。
 */

/** chain-3 开关:启用防火墙框架;失败返回 false */
private fun enableChain3(): Boolean =
    ShizukuShell.exec(arrayOf("cmd", "connectivity", "set-chain3-enabled", "true")).ok

/** 查询包名是否已被 chain-3 禁网 */
private fun queryNetworkBlocked(pkg: String): Boolean {
    if (!ShizukuShell.isReady()) return false
    val r = ShizukuShell.exec(arrayOf("cmd", "connectivity", "get-package-networking-enabled", pkg))
    return r.ok && r.stdout.contains(":deny")
}

/** 开关包名的联网(内部转 appId);返回是否成功 */
private fun setNetworkBlocked(pkg: String, blocked: Boolean): Boolean {
    if (!ShizukuShell.isReady()) return false
    if (!enableChain3()) return false
    val r = ShizukuShell.exec(
        arrayOf(
            "cmd", "connectivity", "set-package-networking-enabled",
            if (blocked) "false" else "true", pkg,
        )
    )
    return r.ok && queryNetworkBlocked(pkg) == blocked
}

/**
 * 恢复持久化的禁网清单:逐包重下 chain-3 规则(幂等,重复设置同值无副作用)。
 * 进入应用界面时于 IO 线程调用;Shizuku 未就绪时静默跳过,下次勾选/启动再恢复。
 */
private fun restoreNetPolicies(context: android.content.Context) {
    val blocked = NetPolicyStore.loadBlocked(context) ?: return
    if (blocked.isEmpty()) return
    blocked.forEach { pkg -> setNetworkBlocked(pkg, blocked = true) }
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

/** 独立任务页:单个应用的任务列表(开关/编辑)+ 新建入口;返回栏显示应用名,数据由上层持有 */
@Composable
private fun TaskListPage(
    pkg: String,
    tasks: List<Task>,
    onBack: () -> Unit,
    onEditTask: (String) -> Unit,
    onNewTask: () -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onPersistTasks: (List<Task>) -> Unit,
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
    // 该应用的任务(保持保存顺序)
    val appTasks = tasks.filter { it.packageName == pkg }

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
            Button(
                onClick = onNewTask,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("新建任务")
            }
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

            items(appTasks.size) { i ->
                val task = appTasks[i]
                val accent = if (task.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditTask(task.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (task.enabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
                            Text(
                                text = task.stepsSummary(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    task.activityPattern?.let { append("Activity≈$it · ") }
                                    when (val t = task.trigger) {
                                        is Task.Trigger.OnPage ->
                                            append(if (t.once) "仅一次" else "每次进入")
                                        is Task.Trigger.Loop -> {
                                            append("循环${t.intervalMs}ms")
                                            if (t.maxRounds > 0) append("×${t.maxRounds}轮")
                                        }
                                    }
                                    (task.onFailure as? Task.OnFailure.Retry)?.let {
                                        append(if (it.times < 0) " · 无限重试" else " · 重试${it.times}次")
                                    }
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
                        // 删除
                        TextButton(onClick = { onPersistTasks(tasks - task) }) {
                            Text(
                                "删除",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 日志 tab:自动化执行记录(触发/成功/失败),LogStore 驱动实时刷新 */
@Composable
private fun LogsScreen(modifier: Modifier = Modifier) {
    var logs by remember { mutableStateOf(LogStore.all()) }

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
                imageVector = Icons.Filled.Article,
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
            TextButton(onClick = { LogStore.clear() }) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
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
            items(logs.size) { i ->
                val e = logs[logs.size - 1 - i]
                val dotColor = when {
                    e.message.contains("失败") -> MaterialTheme.colorScheme.error
                    e.message.contains("成功") -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
}

/** 规则编辑页:页面匹配 + 步骤列表(条件 AND 组合 + 动作)+ 执行控制;新建与编辑共用 */
/** 动作种类元数据:图标 + 名称 + 说明(底部抽屉选择用) */
private data class ActionKind(
    val key: String,
    val label: String,
    val desc: String,
    val icon: ImageVector,
)

private val ACTION_KINDS = listOf(
    ActionKind("Click", "点击", "查询到指定节点时点击它", Icons.Filled.TouchApp),
    ActionKind("Delay", "延时", "固定等待一段时间", Icons.Filled.Timer),
    ActionKind("Back", "返回", "按一次系统返回键", Icons.Filled.KeyboardBackspace),
    ActionKind("Swipe", "滑动", "上滑或下滑一屏", Icons.Filled.SwipeVertical),
)

private fun Step.kindKey(): String = when (this) {
    is Step.Click -> "Click"
    is Step.Sleep -> "Delay"
    is Step.Back -> "Back"
    is Step.Swipe -> "Swipe"
}

/** 按动作种类生成默认步骤(添加后立即进入展开编辑) */
private fun defaultStep(key: String): Step = when (key) {
    "Delay" -> Step.Sleep()
    "Back" -> Step.Back()
    "Swipe" -> Step.Swipe()
    else -> Step.Click(NodeQuery(groups = listOf(listOf())))
}

/** 步骤参数摘要(卡片副标题);条件在前,动作参数在后,次数>1 追加 */
private fun stepParamSummary(step: Step): String {
    val base = when (step) {
        is Step.Click -> "查到「${step.query.summary()}」时点击"
        is Step.Sleep -> "${step.ms / 1000}s"
        is Step.Back -> "系统返回键"
        is Step.Swipe -> if (step.up) "向上" else "向下"
    }
    // 点击语义已含"查到才点",不再拼条件前缀
    val cond = if (step is Step.Click) "" else step.condition?.let { "${it.summary()} 时," } ?: ""
    val times = when {
        step.repeat > 1 -> " ×${step.repeat}"
        step.repeat == 0 -> " ×不限"
        else -> ""
    }
    return "$cond$base$times"
}

@Composable
private fun RuleEditorPage(
    pkg: String,
    existing: Task?,
    onSave: (Task) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var steps by remember { mutableStateOf(existing?.steps ?: emptyList<Step>()) }
    // 失败策略:整组重试 / 直接停止
    var onFailureStop by remember { mutableStateOf(existing?.onFailure is Task.OnFailure.Stop) }
    var retryTimes by remember {
        val t = (existing?.onFailure as? Task.OnFailure.Retry)?.times
        mutableStateOf(if (t == -1) "3" else (t ?: 3).toString())
    }
    // 无限重试:times = -1
    var retryInfinite by remember {
        mutableStateOf((existing?.onFailure as? Task.OnFailure.Retry)?.times == -1)
    }
    // 触发方式:0=仅一次 1=每次进入 2=循环
    var triggerMode by remember {
        mutableStateOf(
            when (val t = existing?.trigger) {
                null -> 0
                is Task.Trigger.Loop -> 2
                is Task.Trigger.OnPage -> if (t.once) 0 else 1
            }
        )
    }
    var loopIntervalSec by remember {
        val s = (existing?.trigger as? Task.Trigger.Loop)?.intervalMs
        mutableStateOf(((s ?: 3000) / 1000).toString())
    }
    var loopRounds by remember {
        mutableStateOf(((existing?.trigger as? Task.Trigger.Loop)?.maxRounds ?: 0).toString())
    }
    // 展开编辑中的步骤下标;null = 全部收起
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    // 底部抽屉:选择要添加的动作
    var showPicker by remember { mutableStateOf(false) }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in steps.indices) return
        val n = steps.toMutableList()
        val s = n.removeAt(i)
        n.add(j, s)
        steps = n
        if (expandedIndex == i) expandedIndex = j
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏:强调色返回圆钮 + 标题 + 保存
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
                    text = if (existing == null) "新建任务" else "编辑任务",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    val onFailure = if (onFailureStop) Task.OnFailure.Stop
                    else if (retryInfinite) Task.OnFailure.Retry(times = -1)
                    else Task.OnFailure.Retry(times = (retryTimes.toIntOrNull() ?: 3).coerceAtLeast(1))
                    val trigger = when (triggerMode) {
                        1 -> Task.Trigger.OnPage(once = false)
                        2 -> Task.Trigger.Loop(
                            intervalMs = (loopIntervalSec.toLongOrNull() ?: 3).coerceAtLeast(1) * 1000,
                            maxRounds = (loopRounds.toIntOrNull() ?: 0).coerceAtLeast(0),
                        )
                        else -> Task.Trigger.OnPage(once = true)
                    }
                    onSave(
                        Task(
                            id = existing?.id ?: "task_${System.currentTimeMillis()}",
                            name = existing?.name ?: "任务 ${steps.size} 步",
                            packageName = pkg,
                            activityPattern = existing?.activityPattern,
                            enabled = existing?.enabled ?: true,
                            trigger = trigger,
                            steps = steps,
                            onFailure = onFailure,
                        )
                    )
                    android.widget.Toast.makeText(
                        context, "任务已保存", android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
                enabled = steps.isNotEmpty(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("保存") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 步骤卡片流
            item {
                Text(
                    text = "执行步骤",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (steps.isEmpty()) {
                item {
                    Text(
                        text = "还没有步骤,点下方「添加步骤」开始。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(steps.size) { i ->
                StepCard(
                    index = i,
                    step = steps[i],
                    expanded = expandedIndex == i,
                    canMoveDown = i < steps.size - 1,
                    onToggle = { expandedIndex = if (expandedIndex == i) null else i },
                    onMove = { delta -> move(i, delta) },
                    onDelete = {
                        steps = steps.filterIndexed { idx, _ -> idx != i }
                        if (expandedIndex == i) expandedIndex = null
                        else if (expandedIndex != null && expandedIndex!! > i) expandedIndex = expandedIndex!! - 1
                    },
                    onSave = { s ->
                        steps = steps.toMutableList().also { it[i] = s }
                        expandedIndex = null
                    },
                )
            }
            item {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加步骤")
                }
            }

            // 触发方式 + 失败策略
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "触发方式",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = triggerMode == 0,
                                onClick = { triggerMode = 0 },
                                label = { Text("仅一次") },
                            )
                            FilterChip(
                                selected = triggerMode == 1,
                                onClick = { triggerMode = 1 },
                                label = { Text("每次进入") },
                            )
                            FilterChip(
                                selected = triggerMode == 2,
                                onClick = { triggerMode = 2 },
                                label = { Text("循环") },
                            )
                        }
                        if (triggerMode == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = loopIntervalSec,
                                    onValueChange = { loopIntervalSec = it.filter { c -> c.isDigit() } },
                                    label = { Text("间隔(秒)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = loopRounds,
                                    onValueChange = { loopRounds = it.filter { c -> c.isDigit() } },
                                    label = { Text("轮数(0=不限)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "失败时",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !onFailureStop && !retryInfinite,
                                onClick = { onFailureStop = false; retryInfinite = false },
                                label = { Text("整组重试") },
                            )
                            FilterChip(
                                selected = !onFailureStop && retryInfinite,
                                onClick = { onFailureStop = false; retryInfinite = true },
                                label = { Text("无限") },
                            )
                            FilterChip(
                                selected = onFailureStop,
                                onClick = { onFailureStop = true },
                                label = { Text("停止任务") },
                            )
                        }
                        // 仅「整组重试」模式下显示次数输入
                        if (!onFailureStop && !retryInfinite) {
                            OutlinedTextField(
                                value = retryTimes,
                                onValueChange = { retryTimes = it.filter { c -> c.isDigit() } },
                                label = { Text("重试次数") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    // 底部抽屉:选择要添加的动作
    if (showPicker) {
        ActionPickerSheet(
            onPick = { kind ->
                showPicker = false
                steps = steps + defaultStep(kind.key)
                expandedIndex = steps.size - 1
            },
            onDismiss = { showPicker = false },
        )
    }
}

private fun mode(contains: Boolean) =
    if (contains) NodeLocator.MatchMode.CONTAINS else NodeLocator.MatchMode.EQUALS

/** 条件输入行:值 + 匹配方式切换(等于/包含) */
@Composable
private fun CondRow(
    label: String,
    value: String,
    contains: Boolean,
    onChange: (String, Boolean) -> Unit,
    hint: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it, contains) },
            label = { Text("$label($hint)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(value, !contains) }) {
            Text(if (contains) "包含" else "等于")
        }
    }
}

/** 单个步骤卡片:收起时显示图标+摘要,展开后就地编辑参数 */
@Composable
private fun StepCard(
    index: Int,
    step: Step,
    expanded: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onSave: (Step) -> Unit,
) {
    val kind = ACTION_KINDS.first { it.key == step.kindKey() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 收起态:图标+标题+摘要+操作按钮,整行点击切换展开
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = kind.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${kind.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stepParamSummary(step),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Close, contentDescription = "删除")
                }
            }
            // 展开态:就地编辑表单
            if (expanded) {
                StepEditForm(initial = step, onSave = onSave)
            }
        }
    }
}

/** 步骤参数就地编辑表单(卡片内展开):条件 + 动作参数 + 次数,保存时保留原 id */
@Composable
private fun StepEditForm(
    initial: Step,
    onSave: (Step) -> Unit,
) {
    var delaySec by remember { mutableStateOf((((initial as? Step.Sleep)?.ms ?: 1000L) / 1000).toString()) }
    var swipeUp by remember { mutableStateOf((initial as? Step.Swipe)?.up ?: true) }
    // 条件标签序列(点击目标与执行条件共用)
    var terms by remember {
        mutableStateOf(
            when {
                initial is Step.Click -> initial.query.toTerms()
                initial.condition != null -> initial.condition!!.query.toTerms()
                else -> emptyList<Term>()
            }
        )
    }
    // 非点击动作的条件门槛:未启用 / 要求命中 / 要求不命中
    var condMode by remember {
        mutableStateOf(
            when {
                initial is Step.Click -> 1  // 点击恒为「查到才点」
                initial.condition == null -> 0
                initial.condition!!.expectPresent -> 1
                else -> 2
            }
        )
    }
    // 执行次数:0 = 不限,必须原样回显(coerceAtLeast(1) 会把「不限」破坏成 1)
    var repeatN by remember { mutableStateOf(initial.repeat.toString()) }

    val valid = true

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // 点击:查询到节点才点,条件即目标
        if (initial is Step.Click) {
            Text(
                text = "查询到以下节点时点击",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TermEditor(terms = terms, onChange = { terms = it })
        }
        when (initial) {
            is Step.Sleep -> OutlinedTextField(
                value = delaySec,
                onValueChange = { delaySec = it.filter { c -> c.isDigit() } },
                label = { Text("延时(秒)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            is Step.Swipe -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = swipeUp, onClick = { swipeUp = true }, label = { Text("上滑") })
                FilterChip(selected = !swipeUp, onClick = { swipeUp = false }, label = { Text("下滑") })
            }
            else -> {}
        }

        // 执行次数
        OutlinedTextField(
            value = repeatN,
            onValueChange = { repeatN = it.filter { c -> c.isDigit() } },
            label = { Text("执行次数(默认 1,0/留空=不限)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 条件门槛(点击已用上方标签,不再重复)
        if (initial !is Step.Click) {
            Text(
                text = "执行条件(可选)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(0 to "无条件", 1 to "节点存在时", 2 to "节点不存在时").forEach { (m, label) ->
                    FilterChip(
                        selected = condMode == m,
                        onClick = { condMode = m },
                        label = { Text(label) },
                    )
                }
            }
            if (condMode != 0) {
                TermEditor(terms = terms, onChange = { terms = it })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点击动作必须至少有一个有效条件,否则点击无从谈起;显式禁用并提示,避免"点了没反应"
            val canSave = initial !is Step.Click || terms.toQuery() != null
            if (!canSave) {
                Text(
                    text = "点击动作需至少设置一个查询条件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(
                enabled = canSave,
                onClick = {
                    // 同类型就地编辑,保留原 id;其余动作可无条件
                    val query = terms.toQuery()
                    val condition = if (condMode == 0 || query == null) null
                    else StepCondition(query, expectPresent = condMode == 1)
                    // 0 或空 = 不限次数
                    val repeat = (repeatN.toIntOrNull() ?: 0).coerceAtLeast(0)
                    onSave(
                        when (initial) {
                            is Step.Click -> Step.Click(query!!, condition, repeat, initial.id)
                            is Step.Sleep -> Step.Sleep((delaySec.toLongOrNull() ?: 1).coerceAtLeast(0) * 1000, condition, repeat, initial.id)
                            is Step.Swipe -> Step.Swipe(swipeUp, condition, repeat, initial.id)
                            is Step.Back -> Step.Back(condition, repeat, initial.id)
                        }
                    )
                },
            ) { Text("完成") }
        }
    }
}

/** 标签化条件编辑器的一个词条:断言或连接符 */
private sealed interface Term {
    /** 断言:字段 + 是否不等于 + 值 */
    data class Assert(
        val field: NodeQuery.Assertion.Field,
        val neq: Boolean,
        val value: String,
    ) : Term

    /** 连接符:true=且(&) false=或(|) */
    data class Link(val and: Boolean) : Term
}

private fun fieldName(f: NodeQuery.Assertion.Field): String = when (f) {
    NodeQuery.Assertion.Field.TEXT -> "文本"
    NodeQuery.Assertion.Field.VID -> "vid"
    NodeQuery.Assertion.Field.DESC -> "描述"
    NodeQuery.Assertion.Field.VISIBLE -> "可见性"
}

private fun fieldValueHint(f: NodeQuery.Assertion.Field): String = when (f) {
    NodeQuery.Assertion.Field.TEXT -> "节点文本"
    NodeQuery.Assertion.Field.VID -> "如 btn_play(不含包名)"
    NodeQuery.Assertion.Field.DESC -> "contentDescription"
    NodeQuery.Assertion.Field.VISIBLE -> "true / false"
}

/** NodeQuery → 标签序列:组间为「或」,组内为「且」 */
private fun NodeQuery.toTerms(): List<Term> {
    val out = mutableListOf<Term>()
    groups.forEachIndexed { gi, group ->
        if (gi > 0) out += Term.Link(false)
        group.forEachIndexed { ai, a ->
            if (ai > 0) out += Term.Link(true)
            out += Term.Assert(a.field, a.op == NodeQuery.Assertion.Op.NEQ, a.value)
        }
    }
    return out
}

/** 标签序列 → NodeQuery;无有效断言返回 null */
private fun List<Term>.toQuery(): NodeQuery? {
    val groups = mutableListOf<MutableList<NodeQuery.Assertion>>(mutableListOf())
    for (t in this) when (t) {
        is Term.Assert -> groups.last() += NodeQuery.Assertion(
            t.field,
            if (t.neq) NodeQuery.Assertion.Op.NEQ else NodeQuery.Assertion.Op.EQ,
            t.value.trim(),
        )
        is Term.Link -> if (!t.and) groups.add(mutableListOf())
    }
    val valid = groups.filter { it.isNotEmpty() }
    return if (valid.isEmpty()) null else NodeQuery(valid)
}

/**
 * 标签化条件编辑器:断言/连接符均以标签展示。
 * 点断言标签 → 下方展开编辑(值输入 + =/!= 切换 + 删除);点连接符标签 → 在 且/或 间切换;
 * 快捷 chips 追加新断言(已有条件时自动补「且」)。
 */
@Composable
private fun TermEditor(
    terms: List<Term>,
    onChange: (List<Term>) -> Unit,
) {
    var selected by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 标签行:断言 + 连接符
        if (terms.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                terms.forEachIndexed { i, t ->
                    when (t) {
                        is Term.Assert -> FilterChip(
                            selected = selected == i,
                            onClick = { selected = if (selected == i) null else i },
                            label = {
                                Text(
                                    fieldName(t.field) +
                                        (if (t.neq) "≠" else "=") +
                                        t.value.ifEmpty { "…" }
                                )
                            },
                        )
                        is Term.Link -> OutlinedButton(
                            onClick = {
                                onChange(terms.toMutableList().also { it[i] = t.copy(and = !t.and) })
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) { Text(if (t.and) "且" else "或") }
                    }
                }
            }
        }
        // 快捷选择:追加断言(字段 chip,选中即添加并展开编辑)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                NodeQuery.Assertion.Field.TEXT to "文本",
                NodeQuery.Assertion.Field.VID to "vid",
                NodeQuery.Assertion.Field.DESC to "描述",
                NodeQuery.Assertion.Field.VISIBLE to "可见性",
            ).forEach { (f, label) ->
                FilterChip(
                    selected = false,
                    onClick = {
                        val ns = terms.toMutableList()
                        if (ns.isNotEmpty()) ns += Term.Link(true)
                        ns += Term.Assert(f, neq = false, value = "")
                        onChange(ns)
                        selected = ns.size - 1
                    },
                    label = { Text(label) },
                )
            }
        }
        // 选中断言的编辑面板:值 + 运算符 + 删除
        selected?.let { i ->
            (terms.getOrNull(i) as? Term.Assert)?.let { a ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !a.neq,
                            onClick = { onChange(terms.toMutableList().also { it[i] = a.copy(neq = false) }) },
                            label = { Text("= 等于") },
                        )
                        FilterChip(
                            selected = a.neq,
                            onClick = { onChange(terms.toMutableList().also { it[i] = a.copy(neq = true) }) },
                            label = { Text("!= 不等于") },
                        )
                    }
                    OutlinedTextField(
                        value = a.value,
                        onValueChange = { v ->
                            onChange(terms.toMutableList().also { it[i] = a.copy(value = v) })
                        },
                        label = { Text(fieldName(a.field)) },
                        supportingText = { Text(fieldValueHint(a.field)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        onChange(terms.toMutableList().also { it.removeAt(i) })
                        selected = null
                    }) { Text("删除该条件") }
                }
            }
        }
    }
}

/** 底部抽屉:选择要添加的动作种类 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPickerSheet(
    onPick: (ActionKind) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "添加步骤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
            )
            ACTION_KINDS.forEach { kind ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(kind) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = kind.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(text = kind.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = kind.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 顶部标题 */
@Composable
private fun TitleBar(serviceConnected: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "自动化助手",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (serviceConnected) "服务运行中,任务可自动执行" else "无障碍自动化任务管理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 运行状态圆点:绿=服务已连接,灰=未连接
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    if (serviceConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                ),
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
 * 无独立「自启动」权限,各 ROM 的自启动开关最终都落到此 op;查询失败时返回 false(显示未授权)。
 */
private fun queryAutoStartAllowed(context: android.content.Context): Boolean {
    return runCatching {
        val am = context.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        val uid = context.packageManager.getApplicationInfo(context.packageName, 0).uid
        // 公开 API unsafeCheckOpNoThrow 用 op 名字符串查询
        am.unsafeCheckOpNoThrow(
            "RUN_ANY_IN_BACKGROUND", uid, context.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
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
        PermissionItem(
            icon = Icons.Filled.Search,
            title = "查询所有应用",
            desc = "允许读取已安装应用列表,用于自动化匹配",
            granted = true, // manifest 已声明 QUERY_ALL_PACKAGES,无需运行时授权
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
                                        onClick = { requestPermission(context, p.title, scope) { refreshTick++ } },
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
        }
        "自启动" -> {
            runCatching {
                context.startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ))
            }
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
    ComposeDemoTheme {
        AutomationScreen()
    }
}
