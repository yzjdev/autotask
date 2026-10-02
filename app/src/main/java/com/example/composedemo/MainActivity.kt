package com.example.composedemo

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
import android.widget.Toast
import rikka.shizuku.Shizuku
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.composedemo.automation.AutomationManager
import com.example.composedemo.automation.DramaAccessibilityService
import com.example.composedemo.automation.ShizukuShell
import com.example.composedemo.automation.LogStore
import com.example.composedemo.automation.NetPolicyStore
import com.example.composedemo.automation.GkdSelector
import com.example.composedemo.automation.Action
import com.example.composedemo.automation.GkdTask
import com.example.composedemo.automation.GkdSubscription
import com.example.composedemo.automation.SubscriptionFetcher
import com.example.composedemo.automation.SubscriptionStore
import com.example.composedemo.automation.TaskStore
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
    // 订阅导入后同步:RuleSync.version 变化即从磁盘重载规则列表(引擎已由导入方直接换表)
    LaunchedEffect(Unit) {
        snapshotFlow { RuleSync.version }.drop(1).collect {
            tasks = TaskStore.loadAll(context.applicationContext)
        }
    }
    fun persistTasks(next: List<GkdTask>) {
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
    // 应用列表排序模式持久化:0=名称升序,1=名称降序,2=任务数多在前,3=安装时间新在前
    val sortModePrefs = context.getSharedPreferences("apps_ui", android.content.Context.MODE_PRIVATE)
    var sortMode by rememberSaveable { mutableStateOf(sortModePrefs.getInt("sort_mode", 0)) }
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
    // 安装时间单独后台批查:getPackageInfo 是逐包 binder IPC,几百个包在主线程会阻塞 UI 数秒;
    // 列表先以 installTime=0 渲染,查完由 installTimes 注入刷新(排序"最近安装"随后自动纠正)。
    var installTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val allApps = remember(installedApps, tasks, installTimes) {
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
                installTime = installTimes[pkg] ?: 0L,
                // 不在已装清单 = 订阅规则指向的未安装应用(灰色展示、排序置后)
                installed = infos.containsKey(pkg),
            )
        }.sortedBy { it.label.lowercase() }
    }
    LaunchedEffect(installedApps, tasks) {
        val pkgs = buildSet {
            (installedApps ?: emptyList()).forEach { add(it.packageName) }
            tasks.forEach { add(it.packageName) }
        }
        val pending = pkgs.filterNot { installTimes.containsKey(it) }
        if (pending.isEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            pending.associateWith { pkg ->
                runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
            }
        }
        installTimes = installTimes + loaded
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
                        onBack = { editingRuleId = null },
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
                    allApps = allApps,
                    iconsByPkg = iconsByPkg,
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
            .padding(horizontal = 16.dp)
            // 底部留白:权限管理卡片不贴屏幕底(兼顾导航条遮挡)
            .padding(bottom = 32.dp),
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

        // 功能切换胶囊:竖排,避免长标题在半宽胶囊里横向挤压
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TogglePill(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Adjust,
                accent = MaterialTheme.colorScheme.tertiary,
                title = "节点悬浮窗",
                checked = overlayOn,
                enabled = uiState.isAccessibilityEnabled,
                onToggle = onOverlayToggle,
            )
            TogglePill(
                modifier = Modifier.fillMaxWidth(),
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

        // 远程订阅入口:已订阅数量,点击打开管理(URL 添加/刷新/删除)
        var subDialogOpen by remember { mutableStateOf(false) }
        val subCount = remember(subDialogOpen) { SubscriptionStore.loadAll(context).size }
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
                        text = if (subCount == 0) "添加 GKD 订阅链接,自动导入规则" else "已订阅 $subCount 个,点击管理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (subDialogOpen) {
            SubscriptionManageDialog(onDismiss = { subDialogOpen = false })
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
    val accent = if (serviceConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
                text = if (serviceConnected) "服务运行中" else "服务未开启",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    serviceConnected -> "任务可自动执行"
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
                    onToggleNetwork = { target ->
                        onNetworkBlocked(app.packageName, target)
                    },
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
    onToggleNetwork: (Boolean) -> Unit,
) {
    val accent = when {
        blocked -> MaterialTheme.colorScheme.error
        app.installed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant // 未安装:灰色调
    }
    val context = LocalContext.current
    // 联网开关本地状态:实际下命令成功后才回调上层;失败回滚
    var netEnabled by remember(app.packageName) { mutableStateOf(!blocked) }
    LaunchedEffect(blocked) { netEnabled = !blocked }
    val scope = rememberCoroutineScope()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 先裁剪再挂点击:水波纹/长按反馈按卡片圆角绘制,而非矩形
            .clip(MaterialTheme.shapes.large)
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
                    // 未安装应用整卡置灰:文字/包名降为 outline 色
                    color = if (app.installed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
                Text(
                    // 副标题:包名 + 该应用的规则组数量(有规则时前置)
                    text = if (app.taskCount > 0) "${app.taskCount} 条规则 · ${app.packageName}" else app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (app.installed) 1f else 0.55f),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 联网开关:点击直接禁用/启用该应用联网(与长按抽屉同一命令通道)
            IconButton(
                onClick = {
                    // netEnabled = 期望的联网态;setNetworkBlocked/onToggleNetwork 传的是禁网态(取反)
                    val targetEnabled = !netEnabled
                    val targetBlocked = !targetEnabled
                    ensureShizukuForNetwork(
                        onReady = {
                            netEnabled = targetEnabled
                            scope.launch {
                                val err = withContext(Dispatchers.IO) {
                                    setNetworkBlocked(app.packageName, targetBlocked)
                                }
                                if (err == null) {
                                    onToggleNetwork(targetBlocked)
                                } else {
                                    netEnabled = !targetEnabled
                                    Toast.makeText(context, "禁网设置失败: $err", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onFail = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = if (netEnabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = if (netEnabled) "禁用网络" else "允许联网",
                    tint = if (netEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
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
                    ensureShizukuForNetwork(
                        onReady = {
                            disableNetwork = target
                            scope.launch {
                                val err = withContext(Dispatchers.IO) { setNetworkBlocked(pkg, target) }
                                if (err == null) {
                                    // 实际生效后才通知上层落盘并同步列表角标;失败回滚 UI
                                    onBlockedChange(target)
                                } else {
                                    disableNetwork = !target
                                    Toast.makeText(context, "禁网设置失败: $err", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onFail = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                    )
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

/** 开关包名的联网;返回 null = 成功,否则为失败原因 */
private fun setNetworkBlocked(pkg: String, blocked: Boolean): String? {
    if (!ShizukuShell.isReady()) return "Shizuku 未就绪或未授权"
    if (!enableChain3()) return "chain-3 防火墙启用失败"
    val r = ShizukuShell.exec(
        arrayOf(
            "cmd", "connectivity", "set-package-networking-enabled",
            if (blocked) "false" else "true", pkg,
        )
    )
    if (!r.ok) return "命令失败: ${r.stderr.ifBlank { "exit=${r.exitCode}" }}"
    return null
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
    // 该应用的任务(保持保存顺序)
    val appTasks = tasks.filter { it.packageName == pkg }
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
                                out.write(GkdSubscription.toJson(appTasks, pkg).toByteArray())
                            } != null
                        }.getOrDefault(false)
                    }
                    Toast.makeText(
                        context,
                        if (ok) "已导出 ${appTasks.size} 条规则" else "导出失败",
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
                        // 点按卡片进入编辑;长按删除
                        .combinedClickable(
                            onClick = { onEditTask(task.id) },
                            onLongClick = { pendingDeleteId = task.id },
                        ),
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
                                // 来源角标:远程订阅导入 / 本地自定义
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

@Composable
private fun RuleEditorPage(
    pkg: String,
    existing: GkdTask?,
    onSave: (GkdTask) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // GKD 规则字段:name / activityIds / matches / action / actionMaximum
    // 一条规则 = 一个动作(GKD 语义):matches 命中的节点即动作目标
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var activityIdsText by remember {
        mutableStateOf(existing?.activityIds?.joinToString(",").orEmpty())
    }
    var matchesExpr by remember { mutableStateOf(existing?.rules?.firstOrNull()?.matches?.firstOrNull()?.expr.orEmpty()) }
    // 高级字段:anyMatches / excludeMatches / 执行参数(规则级优先,空 = 用组级)
    var anyMatchesExpr by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.anyMatches?.firstOrNull()?.expr.orEmpty())
    }
    var excludeMatchesExpr by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.excludeMatches?.firstOrNull()?.expr.orEmpty())
    }
    var actionCdText by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.actionCd?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var actionDelayText by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.actionDelay?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var matchTimeText by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.matchTime?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var resetMatchKind by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.resetMatch ?: existing?.resetMatch ?: GkdTask.ResetMatch.Activity)
    }
    var fastQueryOn by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.fastQuery ?: existing?.fastQuery ?: false)
    }
    var matchRootOn by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.matchRoot ?: existing?.matchRoot ?: false)
    }
    var orderText by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.order?.takeIf { it != 0L }?.toString()
            ?: existing?.order?.takeIf { it != 0L }?.toString().orEmpty())
    }
    var showAdvanced by remember { mutableStateOf(false) }
    // GKD 动作名
    var actionKind by remember {
        mutableStateOf(existing?.rules?.firstOrNull()?.action?.gkd ?: "click")
    }
    var actionMax by remember {
        mutableStateOf((existing?.rules?.firstOrNull()?.actionMaximum?.takeIf { it > 0 } ?: 1L).toString())
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
                    // 解析 GKD 选择器;语法错误不允许保存
                    var parseFailed = false
                    fun parseOrNull(expr: String): GkdSelector? {
                        val t = expr.trim()
                        if (t.isEmpty()) return null
                        return runCatching { GkdSelector.parse(t) }.getOrElse {
                            Toast.makeText(context, "选择器语法错误: $t", Toast.LENGTH_SHORT).show()
                            parseFailed = true
                            null
                        }
                    }
                    val sel = parseOrNull(matchesExpr)
                    if (parseFailed) return@TextButton
                    val anySel = parseOrNull(anyMatchesExpr)
                    if (parseFailed) return@TextButton
                    val excludeSel = parseOrNull(excludeMatchesExpr)
                    if (parseFailed) return@TextButton
                    if (sel == null && anySel == null) {
                        Toast.makeText(context, "matches 与 anyMatches 至少填一个", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val action: Action = Action.entries.firstOrNull { it.gkd == actionKind } ?: Action.Click
                    val max = (actionMax.toLongOrNull() ?: 1L).coerceAtLeast(1L)
                    // swipe 方向编码:GKD 新版用 swipeArg;无绝对坐标时以 endY=-2 表示下滑,null=上滑
                    val rule = GkdTask.Rule(
                        key = existing?.rules?.firstOrNull()?.key ?: 0L,
                        name = name.trim(),
                        matches = listOfNotNull(sel),
                        anyMatches = listOfNotNull(anySel),
                        excludeMatches = listOfNotNull(excludeSel),
                        action = action,
                        // 本地扩展 swipeDir:2 = 下滑;GKD 标准形态用 swipeArg 表达式
                        swipeDir = if (actionKind == "scrollBackward") 2 else null,
                        actionMaximum = max,
                        actionCd = actionCdText.toLongOrNull() ?: 0L,
                        actionDelay = actionDelayText.toLongOrNull() ?: 0L,
                        fastQuery = fastQueryOn,
                        matchRoot = matchRootOn,
                        matchTime = matchTimeText.toLongOrNull() ?: 0L,
                        resetMatch = resetMatchKind,
                        order = orderText.toLongOrNull() ?: 0L,
                    )
                    val saved = GkdTask(
                        id = existing?.id ?: "task_${System.currentTimeMillis()}",
                        name = name.trim().ifEmpty { rule.name.ifEmpty { "规则${rule.key}" } },
                        packageName = pkg,
                        activityIds = activityIdsText.split(',', '，')
                            .map { it.trim() }.filter { it.isNotEmpty() },
                        enabled = existing?.enabled ?: true,
                        rules = listOf(rule),
                    )
                    onSave(saved)
                    Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
                },
                enabled = true,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("保存") }
        }

        // GKD 规则表单:一条规则 = 一个动作
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("规则名(可选)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = activityIdsText,
                onValueChange = { activityIdsText = it },
                label = { Text("activityIds(逗号分隔,留空 = 任意)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SelectorExprField(
                value = matchesExpr,
                onChange = { matchesExpr = it },
                label = "matches(GKD 选择器,全部命中)",
                hint = "例: [text*=\"跳过\"][clickable=true];back/scroll 类动作留空 = 页面就绪即执行",
            )
            SelectorExprField(
                value = anyMatchesExpr,
                onChange = { anyMatchesExpr = it },
                label = "anyMatches(任一命中,可选)",
                hint = "与 matches 二选一;都填时仍以 matches 为准",
            )
            Text(
                text = "action",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // GKD 全量动作
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Action.entries.forEach { a ->
                    FilterChip(
                        selected = actionKind == a.gkd,
                        onClick = { actionKind = a.gkd },
                        label = { Text(GKD_ACTION_LABELS[a.gkd] ?: a.gkd) },
                    )
                }
            }
            OutlinedTextField(
                value = actionMax,
                onValueChange = { actionMax = it.filter { c -> c.isDigit() } },
                label = { Text("actionMaximum(执行次数)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // 高级参数折叠区
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "收起高级参数" else "高级参数(冷却/延迟/重置等)")
            }
            if (showAdvanced) {
                OutlinedTextField(
                    value = actionCdText,
                    onValueChange = { actionCdText = it.filter { c -> c.isDigit() } },
                    label = { Text("actionCd(冷却 ms,0 = 默认 1000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = actionDelayText,
                    onValueChange = { actionDelayText = it.filter { c -> c.isDigit() } },
                    label = { Text("actionDelay(延迟执行 ms)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = matchTimeText,
                    onValueChange = { matchTimeText = it.filter { c -> c.isDigit() } },
                    label = { Text("matchTime(匹配时间窗 ms,0 = 不限)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { orderText = it.filter { c -> c.isDigit() } },
                    label = { Text("order(匹配顺序,越小越先)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("resetMatch(休眠重置策略)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GkdTask.ResetMatch.entries.forEach { rm ->
                        FilterChip(
                            selected = resetMatchKind == rm,
                            onClick = { resetMatchKind = rm },
                            label = { Text(rm.gkd) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = fastQueryOn, onCheckedChange = { fastQueryOn = it })
                        Spacer(Modifier.width(8.dp))
                        Text("fastQuery")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = matchRootOn, onCheckedChange = { matchRootOn = it })
                        Spacer(Modifier.width(8.dp))
                        Text("matchRoot")
                    }
                }
                SelectorExprField(
                    value = excludeMatchesExpr,
                    onChange = { excludeMatchesExpr = it },
                    label = "excludeMatches(命中即跳过本规则,可选)",
                    hint = "例: [vid=\"close_btn\"] 存在时不执行",
                )
            }
            Text(
                text = "GKD 语义:matches 命中的节点即动作目标;back / swipe 为全局动作,无需选择器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 远程订阅管理弹窗:URL 列表 / 添加 / 刷新 / 删除,拉取后解析合并进规则表(GKD 订阅同款) */
@Composable
private fun SubscriptionManageDialog(
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var subs by remember { mutableStateOf(SubscriptionStore.loadAll(context)) }
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
                SubscriptionStore.upsert(
                    context,
                    sub.copy(raw = raw, name = sub.name.ifEmpty { sub.url.substringAfterLast('/') }, lastUpdate = System.currentTimeMillis()),
                )
                subs = SubscriptionStore.loadAll(context)
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
            SubscriptionStore.upsert(context, updated)
            subs = SubscriptionStore.loadAll(context)
            // 规则表合并落盘同样在 IO(SharedPreferences 写入 + 序列化)
            val t2 = System.currentTimeMillis()
            val merged = withContext(kotlinx.coroutines.Dispatchers.IO) {
                SubscriptionStore.import(context, updated, parsed)
                TaskStore.loadAll(context)
            }
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
        text = {
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
                subs.forEach { sub ->
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
                                SubscriptionStore.remove(context, sub.id)
                                // 同步移除该订阅导入的规则
                                val cur = TaskStore.loadAll(context)
                                TaskStore.saveAll(context, cur.filterNot { it.id.startsWith("sub_${sub.id}_") })
                                subs = SubscriptionStore.loadAll(context)
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                        // 展开区:订阅内 app 清单,未安装的灰色展示
                        if (expandedId == sub.id) {
                            val apps = remember(sub.id, sub.raw) { GkdSubscription.appsOf(sub.raw) }
                            if (apps.isEmpty()) {
                                Text(
                                    "未解析到应用(订阅无 apps 或内容为空)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                )
                            } else {
                                apps.forEach { (appId, appName) ->
                                    // 未安装检测:PackageManager 查不到即置灰
                                    val installed = remember(appId) {
                                        runCatching { context.packageManager.getPackageInfo(appId, 0) }.isSuccess
                                    }
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

/** GKD 选择器表达式输入框:实时校验语法,错误时标红提示 */
@Composable
private fun SelectorExprField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String,
) {
    val error = value.trim().takeIf { it.isNotEmpty() }?.let { expr ->
        runCatching { GkdSelector.parse(expr) }.exceptionOrNull()?.message
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = { Text(error ?: hint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
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
@Suppress("DEPRECATION") // 平台无替代公开 API:公开的 unsafeCheckOpNoThrow(字符串 op 名)本身即弃用,仅此途径可查
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
