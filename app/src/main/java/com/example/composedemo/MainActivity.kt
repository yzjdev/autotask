package com.example.composedemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.composedemo.automation.AutomationManager
import com.example.composedemo.automation.DramaAccessibilityService
import com.example.composedemo.automation.LogStore
import com.example.composedemo.automation.NodeLocator
import com.example.composedemo.automation.Step
import com.example.composedemo.automation.Task
import com.example.composedemo.automation.TaskStore
import com.example.composedemo.automation.label
import com.example.composedemo.automation.stepsSummary
import com.example.composedemo.automation.SystemAlertWindow
import com.example.composedemo.ui.theme.ComposeDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeDemoTheme {
                AutomationScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 启动即自动检测(首次触发完整链路,之后仅刷新状态),无需用户手动开关;
        // 从系统设置返回后同样会刷新 Shizuku / 安全设置 / 无障碍状态
        AutomationManager.get(applicationContext).bootstrap()
    }
}

/** 自动化主界面 */
@Composable
fun AutomationScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val automation = remember { AutomationManager.get(context.applicationContext) }
    // 系统 Alert 悬浮窗开关状态:面板内点关闭时通过回调复位
    var alertOn by remember { mutableStateOf(false) }
    // 系统 Alert 悬浮窗面板:挂 app 级 context,不依赖无障碍服务;面板内点关闭时同步开关状态
    val systemAlert = remember {
        SystemAlertWindow(context.applicationContext) { alertOn = false }
    }
    var uiState by remember { mutableStateOf(automation.currentState()) }
    // 确认弹窗:跳转到系统设置必须由用户显式确认,不允许自动执行
    var showA11yConfirm by remember { mutableStateOf(false) }
    // 底部导航:0=首页(权限/悬浮窗) 1=应用(规则管理) 2=日志(执行记录)
    var tab by remember { mutableStateOf(0) }
    // 调试悬浮球开关:仅在无障碍服务已连接时可开;服务断开时自动复位
    var overlayOn by remember {
        mutableStateOf(DramaAccessibilityService.isRunning && DramaAccessibilityService.instance?.isDebugOverlayShowing == true)
    }

    // ---- 应用 tab 共享数据:提升到顶层,切 tab 不销毁、不重新加载 ----
    var tasks by remember { mutableStateOf(TaskStore.loadAll(context.applicationContext)) }
    fun persistTasks(next: List<Task>) {
        tasks = next
        TaskStore.saveAll(context.applicationContext, next)
        DramaAccessibilityService.instance?.taskRunner?.setTasks(next)
    }
    // 已安装应用:后台只加载一次(切回 tab 时 remember 仍在,不重复查询)
    var installedApps by remember { mutableStateOf<List<TaskStore.AppInfo>?>(null) }
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.Default) {
            TaskStore.loadInstalledApps(context.applicationContext)
        }
    }
    // 应用 tab UI 状态:同样提升,切 tab 保留搜索;rememberSaveable 兼顾进程重建
    var query by rememberSaveable { mutableStateOf("") }
    // 应用 tab → 任务页导航:null = 停留在应用列表;非空 = 该包名的独立任务页(全屏,隐藏底部导航)
    var taskPagePkg by rememberSaveable { mutableStateOf<String?>(null) }
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
    // 展示列表 = 全部已装应用 ∪ 有任务的应用,按显示名排序
    val allApps = remember(installedApps, tasks) {
        val pkgs = linkedSetOf<String>()
        (installedApps ?: emptyList()).forEach { pkgs += it.packageName }
        tasks.forEach { pkgs += it.packageName }
        pkgs.map { pkg ->
            Triple(pkg, labelByPkg[pkg] ?: pkg, tasks.count { it.packageName == pkg })
        }.sortedBy { it.second.lowercase() }
    }
    // 应用图标缓存:后台逐包加载一次,切 tab 复用(新建 Bitmap + Canvas 绘制不能占主线程)
    var iconsByPkg by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(allApps) {
        val pending = allApps.map { it.first }.filterNot { iconsByPkg.containsKey(it) }
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
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("首页") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Apps, contentDescription = null) },
                        label = { Text("应用") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Article, contentDescription = null) },
                        label = { Text("日志") },
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
                    modifier = Modifier.padding(innerPadding),
                )
            }
            2 -> LogsScreen(modifier = Modifier.padding(innerPadding))
            else -> HomeScreen(
                context = context,
                uiState = uiState,
                overlayOn = overlayOn,
                alertOn = alertOn,
                onOverlayToggle = {
                    val target = !overlayOn
                    val ok = DramaAccessibilityService.instance
                        ?.showDebugOverlay(target) == true
                    if (ok) overlayOn = target
                },
                onAlertToggle = {
                    if (!SystemAlertWindow.canDraw(context)) {
                        // 跳转本应用的「显示在其他应用上层」授权页
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        val target = !alertOn
                        if (target) systemAlert.show() else systemAlert.hide()
                        alertOn = target
                    }
                },
                onToggleAccessibility = {
                    // 缺「写入安全设置」权限时弹出确认框,由用户决定是否跳转无障碍设置
                    automation.toggleAccessibility { showA11yConfirm = true }
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

/** 主页:标题 + 权限/悬浮窗卡片 + 自动化任务入口 */
@Composable
private fun HomeScreen(
    context: android.content.Context,
    uiState: AutomationManager.State,
    overlayOn: Boolean,
    alertOn: Boolean,
    onOverlayToggle: () -> Unit,
    onAlertToggle: () -> Unit,
    onToggleAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题
        item { TitleBar() }

        // 三项能力状态
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusCard(
                    icon = Icons.Filled.Update,
                    title = context.getString(R.string.label_shizuku),
                    status = uiState.shizukuStatus.name,
                    enabled = uiState.isShizukuReady,
                    actionLabel = context.getString(R.string.action_request_shizuku),
                    onAction = { AutomationManager.get(context).requestShizuku() },
                    description = shizukuDescription(context, uiState),
                )
                // 「写入安全设置」纯状态展示:由 Shizuku 授权后自动代授,无操作按钮
                StatusCard(
                    icon = Icons.Filled.Shield,
                    title = context.getString(R.string.label_secure_settings),
                    status = if (uiState.hasSecureSetting) "已授权" else "未授权",
                    enabled = uiState.hasSecureSetting,
                    actionLabel = "",
                    onAction = {},
                    description = if (uiState.hasSecureSetting) {
                        context.getString(R.string.status_secure_granted)
                    } else {
                        context.getString(R.string.status_need_secure)
                    },
                    hideAction = true,
                )
                StatusCard(
                    icon = Icons.Filled.AccessibilityNew,
                    title = context.getString(R.string.label_accessibility),
                    status = if (uiState.isAccessibilityEnabled) "已开启" else "未开启",
                    enabled = uiState.isAccessibilityEnabled,
                    actionLabel = if (uiState.isAccessibilityEnabled) {
                        context.getString(R.string.action_disable_service)
                    } else {
                        context.getString(R.string.action_enable_service)
                    },
                    onAction = onToggleAccessibility,
                    // 真开关:由 serviceEnabled 驱动,开启/关闭都走 WRITE_SECURE_SETTINGS 直写
                    switchOn = uiState.isAccessibilityEnabled,
                    description = if (uiState.isAccessibilityEnabled) {
                        context.getString(R.string.status_service_running)
                    } else {
                        context.getString(R.string.status_service_disabled)
                    },
                )
                // 调试悬浮球开关:独立于无障碍服务,仅服务已连接时可用
                StatusCard(
                    icon = Icons.Filled.Adjust,
                    title = "节点悬浮窗",
                    status = if (overlayOn) "已开启" else "未开启",
                    enabled = uiState.isAccessibilityEnabled,
                    actionLabel = if (overlayOn) "关闭悬浮球" else "开启悬浮球",
                    onAction = onOverlayToggle,
                    switchOn = overlayOn,
                    description = if (uiState.isAccessibilityEnabled) {
                        "开启后屏幕上显示调试悬浮球,点击悬浮球抓取节点"
                    } else {
                        "需先开启无障碍服务"
                    },
                )
                // 系统 Alert 悬浮窗开关:未授权时点击跳转系统授权页,已授权时开关悬浮球
                StatusCard(
                    icon = Icons.Filled.Layers,
                    title = "系统悬浮窗",
                    status = if (alertOn) "已开启" else "未开启",
                    enabled = true,
                    actionLabel = when {
                        !SystemAlertWindow.canDraw(context) -> "去授权"
                        alertOn -> "关闭悬浮球"
                        else -> "开启悬浮球"
                    },
                    onAction = onAlertToggle,
                    switchOn = alertOn,
                    description = if (SystemAlertWindow.canDraw(context)) {
                        "开启后屏幕上显示系统悬浮球,无需无障碍服务"
                    } else {
                        "需先授予「显示在其他应用上层」权限"
                    },
                )
            }
        }
    }
}

/** 包名显示名:直接查已装应用标签,查不到回退包名 */
private fun appLabel(context: android.content.Context, pkg: String, sample: Task?): String {
    // 尝试从已装应用解析显示名(缓存成本低,chip 数量有限)
    val pm = context.packageManager
    return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
        .getOrDefault(pkg)
}

/** 应用 tab:全部应用列表,搜索过滤;点应用进入该应用的独立任务页(导航由上层状态驱动) */
@Composable
private fun AppsScreen(
    allApps: List<Triple<String, String, Int>>,
    iconsByPkg: Map<String, ImageBitmap>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 搜索过滤:纯内存操作,不触发任何重新加载
    val visibleApps = allApps.filter { (pkg, label, _) ->
        query.isBlank() || label.contains(query, true) || pkg.contains(query, true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏:标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "应用",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 搜索框:按应用名 / 包名过滤
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("搜索应用名 / 包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (visibleApps.isEmpty()) {
                item {
                    Text(
                        text = if (query.isBlank()) "未找到任何应用" else "无匹配应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(visibleApps.size) { i ->
                val (pkg, label, count) = visibleApps[i]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenApp(pkg) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 应用图标:加载失败时用默认图标占位
                        val icon = iconsByPkg[pkg]
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (count > 0) "$count 项" else "无任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
        // 顶栏:返回 + 应用名 + 新建
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onNewTask) {
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditTask(task.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.stepsSummary(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
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
                                        append(" · 重试${it.times}次")
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
                        TextButton(onClick = { onPersistTasks(tasks - task) }) { Text("删除") }
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
        // 顶栏:标题 + 清空
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "日志",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { LogStore.clear() }) { Text("清空") }
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 新日志在顶部:倒序渲染
            items(logs.size) { i ->
                val e = logs[logs.size - 1 - i]
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = e.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = e.message,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** 规则编辑页:页面匹配 + 步骤列表(条件 AND 组合 + 动作)+ 执行控制;新建与编辑共用 */
@Composable
private fun RuleEditorPage(
    pkg: String,
    existing: Task?,
    onSave: (Task) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activity by remember { mutableStateOf(existing?.activityPattern ?: "") }
    var steps by remember { mutableStateOf(existing?.steps ?: emptyList<Step>()) }
    // 触发策略:进入页面(once) / 循环
    var isLoop by remember { mutableStateOf(existing?.trigger is Task.Trigger.Loop) }
    var once by remember {
        mutableStateOf((existing?.trigger as? Task.Trigger.OnPage)?.once ?: true)
    }
    var loopIntervalSec by remember {
        mutableStateOf((((existing?.trigger as? Task.Trigger.Loop)?.intervalMs ?: 3000L) / 1000).toString())
    }
    var loopRounds by remember {
        mutableStateOf(((existing?.trigger as? Task.Trigger.Loop)?.maxRounds ?: 0).toString())
    }
    // 正在编辑的步骤:index to draft;index<0 = 追加新步骤
    var editing by remember { mutableStateOf<Pair<Int, Step>?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏:返回 + 标题 + 保存
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (existing == null) "新建任务" else "编辑任务",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    val trigger = if (isLoop) {
                        Task.Trigger.Loop(
                            intervalMs = (loopIntervalSec.toLongOrNull() ?: 3).coerceAtLeast(1) * 1000,
                            maxRounds = (loopRounds.toIntOrNull() ?: 0).coerceAtLeast(0),
                        )
                    } else {
                        Task.Trigger.OnPage(once = once)
                    }
                    onSave(
                        Task(
                            id = existing?.id ?: "task_${System.currentTimeMillis()}",
                            name = existing?.name ?: "任务 ${steps.size} 步",
                            packageName = pkg,
                            activityPattern = activity.trim().takeIf { it.isNotBlank() },
                            enabled = existing?.enabled ?: true,
                            trigger = trigger,
                            steps = steps,
                            onFailure = existing?.onFailure ?: Task.OnFailure.Retry(),
                        )
                    )
                },
                enabled = steps.isNotEmpty(),
            ) { Text("保存") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 触发条件
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "触发条件",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = activity,
                            onValueChange = { activity = it },
                            label = { Text("Activity 匹配(可选,留空=任意页面)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // 循环模式下无 once 概念,仅进入页面触发时显示
                        if (!isLoop) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "进入页面后只执行一次",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Switch(checked = once, onCheckedChange = { once = it })
                            }
                        }
                    }
                }
            }

            // 执行步骤
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "执行步骤(自上而下,失败整组重试)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (steps.isEmpty()) {
                            Text(
                                text = "暂无步骤,从下方添加。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        steps.forEachIndexed { i, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${i + 1}. ${step.label()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { editing = i to step },  // 点摘要进入编辑
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(
                                    onClick = {
                                        val n = steps.toMutableList()
                                        n[i - 1] = step.also { n[i] = n[i - 1] }
                                        steps = n
                                    },
                                    enabled = i > 0,
                                ) { Text("↑") }
                                TextButton(
                                    onClick = {
                                        val n = steps.toMutableList()
                                        n[i + 1] = step.also { n[i] = n[i + 1] }
                                        steps = n
                                    },
                                    enabled = i < steps.size - 1,
                                ) { Text("↓") }
                                TextButton(onClick = { steps = steps - step }) { Text("删") }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OutlinedButton(onClick = {
                                editing = -1 to Step.Click(NodeLocator(NodeLocator.Field.TEXT, NodeLocator.MatchMode.EQUALS, ""))
                            }) { Text("+点文本") }
                            OutlinedButton(onClick = {
                                editing = -1 to Step.Wait(NodeLocator(NodeLocator.Field.TEXT, NodeLocator.MatchMode.EQUALS, ""))
                            }) { Text("+等待") }
                            OutlinedButton(onClick = {
                                editing = -1 to Step.Sleep()
                            }) { Text("+延时") }
                            OutlinedButton(onClick = {
                                editing = -1 to Step.Back
                            }) { Text("+返回") }
                            OutlinedButton(onClick = {
                                editing = -1 to Step.Swipe()
                            }) { Text("+滑动") }
                        }
                    }
                }
            }

            // 执行控制:循环模式
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "循环执行(前台停留期间重复)",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Switch(checked = isLoop, onCheckedChange = { isLoop = it })
                        }
                        if (isLoop) {
                            OutlinedTextField(
                                value = loopIntervalSec,
                                onValueChange = { loopIntervalSec = it.filter { c -> c.isDigit() } },
                                label = { Text("每轮间隔(秒)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = loopRounds,
                                onValueChange = { loopRounds = it.filter { c -> c.isDigit() } },
                                label = { Text("最大轮数(0=无限,离开应用自动停)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    // 步骤编辑弹窗
    editing?.let { (idx, draft) ->
        StepEditorDialog(
            initial = draft,
            onDismiss = { editing = null },
            onSave = { s ->
                steps = if (idx < 0) steps + s else steps.toMutableList().also { it[idx] = s }
                editing = null
            },
        )
    }
}

/** 单步骤编辑弹窗:单定位器(字段 4 选 1 + 等于/包含)+ 动作专属参数 */
@Composable
private fun StepEditorDialog(
    initial: Step,
    onDismiss: () -> Unit,
    onSave: (Step) -> Unit,
) {
    // 动作种类:key 与保存时的分支一一对应
    fun kindOf(s: Step) = when (s) {
        is Step.Click -> "Click"
        is Step.Wait -> "Wait"
        is Step.Sleep -> "Delay"
        is Step.Back -> "Back"
        is Step.Swipe -> "Swipe"
    }
    fun locatorOf(s: Step): NodeLocator? = when (s) {
        is Step.Click -> s.locator
        is Step.Wait -> s.locator
        else -> null
    }

    var actionName by remember { mutableStateOf(kindOf(initial)) }
    val initialLocator = locatorOf(initial)
    var field by remember {
        mutableStateOf(initialLocator?.field ?: NodeLocator.Field.TEXT)
    }
    var contains by remember {
        mutableStateOf(initialLocator?.mode == NodeLocator.MatchMode.CONTAINS)
    }
    var value by remember { mutableStateOf(initialLocator?.value ?: "") }
    var timeoutSec by remember {
        mutableStateOf((((initial as? Step.Wait)?.timeoutMs ?: 5000L) / 1000).toString())
    }
    var delaySec by remember { mutableStateOf(((initial as? Step.Sleep)?.ms ?: 1000L).toString()) }
    var swipeUp by remember { mutableStateOf((initial as? Step.Swipe)?.up ?: true) }

    val needLocator = actionName == "Click" || actionName == "Wait"
    val valid = !needLocator || value.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑步骤") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "动作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Click" to "点击", "Wait" to "等待", "Delay" to "延时",
                        "Back" to "返回", "Swipe" to "滑动").forEach { (key, label) ->
                        OutlinedButton(
                            onClick = { actionName = key },
                            colors = if (actionName == key) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                        ) { Text(label) }
                    }
                }
                if (needLocator) {
                    Text(
                        text = "定位节点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 字段 4 选 1(单定位器,不再多字段 AND)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            NodeLocator.Field.TEXT to "文本",
                            NodeLocator.Field.VIEW_ID to "viewId",
                            NodeLocator.Field.DESC to "描述",
                            NodeLocator.Field.CLASS_NAME to "类名",
                        ).forEach { (f, label) ->
                            OutlinedButton(
                                onClick = { field = f },
                                colors = if (field == f) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors(),
                            ) { Text(label) }
                        }
                    }
                    CondRow(
                        label = when (field) {
                            NodeLocator.Field.TEXT -> "文本"
                            NodeLocator.Field.VIEW_ID -> "viewId"
                            NodeLocator.Field.DESC -> "描述"
                            NodeLocator.Field.CLASS_NAME -> "类名"
                        },
                        value = value,
                        contains = contains,
                        onChange = { v, c -> value = v; contains = c },
                        hint = when (field) {
                            NodeLocator.Field.TEXT -> "节点文本"
                            NodeLocator.Field.VIEW_ID -> "如 btn_play(不含包名)"
                            NodeLocator.Field.DESC -> "contentDescription"
                            NodeLocator.Field.CLASS_NAME -> "如 TextView"
                        },
                    )
                }
                when (actionName) {
                    "Wait" -> OutlinedTextField(
                        value = timeoutSec,
                        onValueChange = { timeoutSec = it.filter { c -> c.isDigit() } },
                        label = { Text("等待超时(秒)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "Delay" -> OutlinedTextField(
                        value = delaySec,
                        onValueChange = { delaySec = it.filter { c -> c.isDigit() } },
                        label = { Text("延时(秒)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "Swipe" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { swipeUp = true },
                            colors = if (swipeUp) ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) else ButtonDefaults.outlinedButtonColors(),
                        ) { Text("上滑") }
                        OutlinedButton(
                            onClick = { swipeUp = false },
                            colors = if (!swipeUp) ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) else ButtonDefaults.outlinedButtonColors(),
                        ) { Text("下滑") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    // 同类型编辑保留原 id,换类型则生成新 id
                    fun keepId(s: Step): String =
                        if (kindOf(s) == kindOf(initial)) initial.id else java.util.UUID.randomUUID().toString()
                    val locator = NodeLocator(field, mode(contains), value.trim())
                    onSave(
                        when (actionName) {
                            "Wait" -> Step.Wait(locator, (timeoutSec.toLongOrNull() ?: 5).coerceAtLeast(1) * 1000, keepId(Step.Wait(locator)))
                            "Delay" -> Step.Sleep((delaySec.toLongOrNull() ?: 1).coerceAtLeast(0) * 1000, keepId(Step.Sleep()))
                            "Back" -> Step.Back
                            "Swipe" -> Step.Swipe(swipeUp, keepId(Step.Swipe()))
                            else -> Step.Click(locator, keepId(Step.Click(locator)))
                        }
                    )
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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

/** 顶部标题 */
@Composable
private fun TitleBar() {
    Column {
        Text(
            text = "自动化权限管理",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Shizuku · 写入安全设置 · 无障碍服务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 能力状态卡 */
@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    status: String,
    enabled: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    description: String,
    // 无障碍卡用真开关而不是按钮:开启/关闭都在应用内完成,不需要跳转
    switchOn: Boolean? = null,
    // 纯状态展示卡:不渲染任何操作控件
    hideAction: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        // 标题宽度弹性收缩:空间不足时省略,而不是被压成逐字竖排
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status,
                        // 状态词固定宽度且不换行,避免被挤压成竖排
                        modifier = Modifier.wrapContentWidth(),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                hideAction -> { /* 纯状态卡,无操作控件 */ }
                switchOn != null -> {
                    // 无障碍卡用真开关:开启/关闭都在应用内完成,不跳转系统设置
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(checked = switchOn, onCheckedChange = { onAction() })
                    }
                }
                else -> {
                    OutlinedButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
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
