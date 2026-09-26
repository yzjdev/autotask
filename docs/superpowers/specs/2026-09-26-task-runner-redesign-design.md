# 自动化任务步骤重新设计（v4：Task / TaskRunner）

日期：2026-09-26
状态：已与用户确认设计方向，本文档为实施依据。

## 1. 背景与目标

现有自动化实现（`RuleEngine` / `RuleStore` / `RuleStep`，模型版本 v3）存在结构性问题：

1. **执行器是回调链**：`runSteps` 用 `postDelayed` 递归推进步骤，等待/重试/循环/去重四种状态散落在 `waitStart` / `executed` / `activeLoops` 三个 HashMap，靠字符串 key 互相清理，取消靠 `removeCallbacksAndMessages(null)` 一把梭。
2. **步骤模型有「专用字段」**：`RuleStep` 把 5 种动作塞进一个 data class，`timeoutMs` / `delayMs` / `swipeUp` 各自只对一个动作有意义，全靠注释约定。
3. **控制流混进步骤**：失败整组重试的 `attempt` / `dedupKey` 参数横穿整个执行链。
4. **手写 JSON 序列化**：`RuleStore.toJson` / `ruleFromJson` 手写，还留着 `legacyText` 兼容代码。

用户决定：**抛弃现有结构，从零重新设计**，且旧规则数据**完全不做迁移**。

### 已确认的设计决策（用户选择）

| 决策点 | 选择 |
|---|---|
| 步骤定义模型 | 节点定位器 + 动作的扁平密封类列表 |
| 执行引擎 | Kotlin 协程顺序流 |
| 步骤来源 | 手动编辑（表单），不做录制 |
| 旧数据兼容 | 完全不兼容，全新存储 |

## 2. 数据模型（新文件 `automation/Task.kt`）

### 2.1 节点定位器 —— 「找到谁」与「做什么」分离

```kotlin
@Serializable
data class NodeLocator(
    val field: Field,      // VIEW_ID / TEXT / DESC / CLASS_NAME
    val mode: MatchMode,   // EQUALS / CONTAINS
    val value: String,
) {
    @Serializable enum class Field { VIEW_ID, TEXT, DESC, CLASS_NAME }
    @Serializable enum class MatchMode { EQUALS, CONTAINS }
}
```

继承现有 `NodeCondition` 的语义（AND 匹配退化为单一定位器；多条件 AND 场景中，实测主要用法是「字段+值」单条件，复杂 AND 用定位器组合即可覆盖，不再单列条件列表）。

### 2.2 步骤 —— 密封接口，参数自包含

```kotlin
@Serializable
sealed interface Step {
    val id: String

    @Serializable @SerialName("click")
    data class Click(val locator: NodeLocator, val id: String = uuid()) : Step

    @Serializable @SerialName("wait")
    data class Wait(val locator: NodeLocator, val timeoutMs: Long = 5000, val id: String = uuid()) : Step

    @Serializable @SerialName("sleep")
    data class Sleep(val ms: Long = 1000, val id: String = uuid()) : Step

    @Serializable @SerialName("swipe")
    data class Swipe(val up: Boolean = true, val id: String = uuid()) : Step

    @Serializable @SerialName("back")
    data object Back : Step { override val id get() = "back" }
}
```

要点：
- 每种动作只带自己的参数，无「专用字段」。
- `Click` 保留现有退化策略：节点 `isClickable` 时 `ACTION_CLICK`，否则取屏幕边界中心点走 `clickAt` 坐标点击。

### 2.3 任务 —— 触发与失败策略独立声明

```kotlin
@Serializable
data class Task(
    val id: String,
    val name: String,                     // 人起的名字，列表/日志展示用
    val packageName: String,
    val activityPattern: String? = null,  // 精确名或前缀，null = 任意
    val enabled: Boolean = true,
    val trigger: Trigger,
    val steps: List<Step>,
    val onFailure: OnFailure = OnFailure.Retry(times = 3, intervalMs = 500),
) {
    @Serializable
    sealed interface Trigger {
        /** 进入页面时触发；once = 同一次进入该页面只触发一次 */
        @Serializable @SerialName("onPage")
        data class OnPage(val once: Boolean = true) : Trigger

        /** 前台停留期间循环执行；maxRounds = 0 表示无限（离开目标应用自动停） */
        @Serializable @SerialName("loop")
        data class Loop(val intervalMs: Long, val maxRounds: Int = 0) : Trigger
    }

    @Serializable
    sealed interface OnFailure {
        /** 整组重试：失败后从第 0 步重来，超过 times 次放弃 */
        @Serializable @SerialName("retry")
        data class Retry(val times: Int = 3, val intervalMs: Long = 500) : OnFailure

        /** 失败即放弃 */
        @Serializable @SerialName("stop")
        data object Stop : OnFailure
    }
}
```

语义沿用 v3：触发条件（应用 + Activity 前缀）、`once` 去重（pkg|activity|taskId）、循环模式在前台停留期间按 `intervalMs` 重跑全部步骤直到离开应用或达 `maxRounds`、失败整组重试后放弃并释放去重。

## 3. 执行引擎（新文件 `automation/TaskRunner.kt`）

协程顺序流，替代 `RuleEngine` 的全部职责：

```kotlin
class TaskRunner(private val service: DramaAccessibilityService) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<String, Job>()   // taskId → 当前执行 Job

    // 由 DramaAccessibilityService.onWindowChanged 调用
    fun onActivityChanged(pkg: String, activity: String)
    // 离开目标包：清 once 去重 + 取消该包的活跃任务（含循环）
    fun onLeftPackage(pkg: String)
    fun cancel(taskId: String)
    fun cancelAll()
}
```

### 3.1 核心执行流

```kotlin
private suspend fun runRound(task: Task): Boolean = coroutineScope {
    task.steps.forEachIndexed { i, step ->
        ensureStillForeground(task)          // 不在前台 → CancellationException
        when (step) {
            is Step.Click -> click(step.locator) ?: return@coroutineScope false
            is Step.Wait  -> waitNode(step.locator, step.timeoutMs) ?: return@coroutineScope false
            is Step.Sleep -> delay(step.ms)
            is Step.Swipe -> if (step.up) service.swipeUp() else service.swipeDown()
            Step.Back     -> service.goBack()
        }
    }
    true
}
```

- **取消** = `job.cancel()`，无需清回调表和状态 map。
- **等待** = `withTimeout(step.timeoutMs) { while (!found) delay(200) }`，超时/取消由协程结构化处理，删除 `waitStart` 表和 `waitKey`。
- **重试** = 独立包装函数（约 5 行），不再横穿执行链：

```kotlin
private suspend fun withRetry(task: Task, round: () -> Boolean) {
    val policy = task.onFailure
    if (policy is OnFailure.Retry) {
        repeat(policy.times) { attempt ->
            if (round()) { logSuccess(attempt); return }
            delay(policy.intervalMs)
        }
        logGiveUp(policy.times)
    } else {
        round()
    }
}
```

- **循环**（`Trigger.Loop`）= 外层 `while` + `delay(intervalMs)` + 轮数上限检查；`activeLoops` 去重简化为「jobs 里已有该 taskId 就不重复启动」。
- **once 去重** = 保留 `executed: HashSet<String>`（key: pkg|activity|taskId），`onLeftPackage` 时按包前缀清理，语义与 v3 一致。

### 3.2 节点查找

沿用 `RuleEngine.findNode` / `dfs` 的实现（快路径：TEXT+EQUALS 走 `findAccessibilityNodeInfosByText` 预筛，否则 DFS），只是入参从 `List<NodeCondition>` 改为单个 `NodeLocator`。该实现是纯函数式的（`AccessibilityNodeInfo` 树遍历），可随 `DramaAccessibilityService` 复用。

### 3.3 可测试性

节点查找和「步骤列表 → 执行动作序列」的决策逻辑不依赖 Android 类（依赖 `service` 的方法签名），为后续 JVM 单测预留接口；本次重构不强制新增测试文件。

## 4. 存储（新文件 `automation/TaskStore.kt`）

- **kotlinx.serialization** 替代手写 JSON：sealed class 用 `@Serializable` + `@SerialName` 判别，删除全部 `toJson` / `fromXxx` / `legacyText`。
- 存储位置沿用 SharedPreferences 单 key JSON 数组（`prefs = "automation_tasks"`，key = `"tasks"`），数据量小，不引入 Room。
- 保留 `loadInstalledApps()` / `AppInfo`（编辑器选应用用，与任务模型无关，直接搬过来）。
- **不读取旧 key**：旧 `rules` key 留在 SharedPreferences 里不管（不删除也不读取，避免启动时多做一次解析；用户已确认不迁移）。

## 5. 接入点改动

### 5.1 `DramaAccessibilityService`

- `val ruleEngine = RuleEngine(this)` → `val taskRunner = TaskRunner(this)`
- `onWindowChanged` 里 `ruleEngine.onActivityChanged(...)` → `taskRunner.onActivityChanged(...)`；离开包的回调同理（现有 `onLeftPackage` 调用点改为 `taskRunner.onLeftPackage`）。
- 其余手势/查找方法（`clickAt` / `swipeUp` / `swipeDown` / `goBack` / `gesture` / `readableText` 等）全部复用不动。

### 5.2 `MainActivity`（UI 适配）

列表/编辑器骨架保留，数据类型换新模型：

- `rules` → `tasks`，`RuleStore.loadAll/saveAll` → `TaskStore.loadAll/saveAll`。
- 编辑器：步骤类型直接是 `Step` 密封类子类，「添加步骤」按钮组从 5 个动作生成对应子类；编辑某一步时只编辑它自己的字段（定位器 + 该动作参数），不再有「条件列表 + 动作」两块。
- 触发设置 UI：原来 `loopIntervalMs > 0` 的隐式开关变成显式的 Trigger 选择（进入页面时 / 循环），失败策略同理（重试 N 次 / 直接停止）。
- 展示摘要 `stepsSummary`：改为 `Task` 的扩展函数，格式不变（`点「x」→等待→延时1s`）。

### 5.3 构建配置

`gradle/libs.versions.toml` 增加：

```toml
kotlinSerialization = "1.9.0"        # libraries: kotlinx-serialization-json
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`app/build.gradle.kts` 增加 `alias(libs.plugins.kotlin.serialization)` + `implementation(libs.kotlinx.serialization.json)`。版本与内置 Kotlin 2.2.10 兼容（serialization 1.9.x 支持 Kotlin 2.2）。

## 6. 文件改动清单

| 文件 | 动作 |
|---|---|
| `automation/Task.kt` | 新建：NodeLocator / Step / Task 模型 |
| `automation/TaskRunner.kt` | 新建：协程执行引擎（替代 RuleEngine） |
| `automation/TaskStore.kt` | 新建：kotlinx.serialization 存储（替代 RuleStore） |
| `automation/RuleEngine.kt` | 删除（模型部分并入 Task.kt，引擎并入 TaskRunner.kt） |
| `automation/RuleStore.kt` | 删除（AppInfo/loadInstalledApps 搬入 TaskStore） |
| `automation/DramaAccessibilityService.kt` | 改：ruleEngine 字段与调用点 → taskRunner |
| `MainActivity.kt` | 改：列表/编辑器适配 Task 模型 |
| `build.gradle.kts` + `libs.versions.toml` | 改：加 serialization 插件与依赖 |

不改：`LogStore`、`OverlayDebugWindow` / `OverlayToggleWindow` / `SystemAlertWindow`、`crash/`、`ui/theme/`、`AndroidManifest.xml`（服务声明不变）。

## 7. 验收标准

1. `sh gradlew assembleDebug` 编译通过。
2. 新建一条任务（点击某文本按钮 + 等待 + 延时），进入目标应用触发执行，日志可见步骤推进。
3. 循环任务在前台按间隔重跑，离开应用自动停止。
4. 失败重试：目标节点不存在时按 Retry 策略整组重试后放弃，日志记录；Stop 策略直接放弃。
5. 杀进程重启后任务列表从 SharedPreferences 正确加载（新旧 key 不互通）。
6. 代码中不再存在 `RuleEngine` / `RuleStore` / `RuleStep` / `NodeCondition` 引用。

## 8. 明确不做

- 步骤录制、分支/子流程、子步骤独立重试（本次范围外，模型上留了扩展空间：`Step` 密封接口可加新子类，`Trigger` / `OnFailure` 可加新策略）。
- Room 数据库、WorkManager 调度。
- 旧规则迁移。
