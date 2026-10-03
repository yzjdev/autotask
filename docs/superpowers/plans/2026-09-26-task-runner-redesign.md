# 自动化任务 v4（Task / TaskRunner）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用「NodeLocator + Step 密封类 + Task（Trigger/OnFailure 独立声明）」替代 RuleEngine/RuleStore/RuleStep，执行引擎改为协程顺序流，存储改为 kotlinx.serialization，旧数据不迁移。

**Architecture:** 模型（Task.kt）/ 引擎（TaskRunner.kt，协程）/ 存储（TaskStore.kt）三个新文件替代 RuleEngine.kt + RuleStore.kt；`DramaAccessibilityService` 只改字段与调用点；MainActivity 列表/编辑器换新类型。手势与节点查找底座复用不动。

**Tech Stack:** Kotlin 2.2.10、kotlinx-serialization 1.9.x、协程（lifecycle-runtime-ktx 已带）、AGP 9.1.1、Termux 本机构建。

**Spec:** `docs/superpowers/specs/2026-09-26-task-runner-redesign-design.md`

## Global Constraints

- 包名 `com.yzjdev.autotask`，新文件放 `app/src/main/java/com/yzjdev/autotask/automation/`。
- 本项目在 sdcard（FUSE）上，构建命令必须用 `sh gradlew assembleDebug`（或 `sh gradlew :app:compileDebugKotlin` 做快速校验）；`chmod` 无效。
- minSdk 26 / targetSdk 36 / compileSdk 36；JDK target 17。
- serialization 版本取 `1.9.0`，插件 id `org.jetbrains.kotlin.plugin.serialization`，版本引用现有 `kotlin = "2.2.10"`。
- 不引入 Room、不写 JVM 单测模块（本机 Termux 无测试基建）；每个任务用 `compileDebugKotlin` 编译验证。
- 注释风格沿现有中文 KDoc，密度与现文件一致。
- 旧 SharedPreferences key `automation_rules` 不读取、不删除。

## Review Focus

- **Wait 超时与取消**：`withTimeout` 抛的 `TimeoutCancellationException` 不能被当成「任务被用户取消」吞掉，也不能向外传播 crash——期望：超时→本轮失败走重试策略；用户取消→静默退出。Task 4 的实现步骤钉死这一点。
- **协程取消时机**：`service` 手势调用发生在主线程，`delay`/`withTimeout` 之外不得有阻塞调用；每步开始前检查前台，离开目标应用必须停（含循环轮间 delay 醒来后）。Task 4 钉死。
- **serialization 判别符**：所有 sealed 子类必须显式 `@SerialName`，否则升级/重命名类名会破坏已存 JSON。Task 2 钉死。
- **编辑器往返**：UI 编辑 → 保存 → 重新加载后步骤类型与参数不丢（尤其 `Swipe.up=false`、`Wait.timeoutMs`）。Task 6 的验证步骤钉死。
- **一次进入只触发一次**：`Trigger.OnPage(once=true)` 的去重 key 必须含 activity，离开包名后清空；循环任务不受 once 影响、同一任务不得并发两个执行。Task 4 钉死。

---

### Task 1: 构建配置接入 kotlinx.serialization

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `libs.kotlinx.serialization.json` 依赖与 `libs.plugins.kotlin.serialization` 插件，供 Task 2/3 使用。

- [ ] **Step 1: 修改 `gradle/libs.versions.toml`**

`[versions]` 加一行 `kotlinxSerialization = "1.9.0"`；`[libraries]` 加：
```toml
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```
`[plugins]` 加：
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: 修改 `app/build.gradle.kts`**

`plugins {}` 块加 `alias(libs.plugins.kotlin.serialization)`；`dependencies {}` 加 `implementation(libs.kotlinx.serialization.json)`。

- [ ] **Step 3: 编译验证**

Run: `sh gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（插件空载可用即通过）。

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add kotlinx-serialization plugin and dependency"
```

---

### Task 2: 数据模型 `Task.kt`

**Files:**
- Create: `app/src/main/java/com/yzjdev/autotask/automation/Task.kt`

**Interfaces:**
- Consumes: 无。
- Produces（Task 3/4/6 依赖的精确签名）:
  - `data class NodeLocator(val field: Field, val mode: MatchMode, val value: String)`，内嵌 `enum class Field { VIEW_ID, TEXT, DESC, CLASS_NAME }`、`enum class MatchMode { EQUALS, CONTAINS }`，均 `@Serializable`；方法 `fun matches(node: AccessibilityNodeInfo): Boolean`（沿用现 `NodeCondition.matches` 语义：TEXT 取 text 与 contentDescription 并集、DESC 取 contentDescription、VIEW_ID 取 viewIdResourceName、CLASS_NAME 取 className）与 `fun summary(): String`（如「文本=立即播放」「viewId 含 btn」）。
  - `sealed interface Step { val id: String }`，子类 `Click(locator)`、`Wait(locator, timeoutMs=5000)`、`Sleep(ms=1000)`、`Swipe(up=true)`、`data object Back`，全部 `@Serializable` + 显式 `@SerialName("click"/"wait"/"sleep"/"swipe"/"back")`；id 默认 `uuid()`（`java.util.UUID.randomUUID().toString()`，Back 固定 `"back"`）。
  - `Task` 顶层扩展函数 `fun Task.stepsSummary(): String` 与 `fun Step.label(): String`（沿用现 `RuleStep.actionLabel` 文案：点「x」/等待/延时Nms/返回/上滑·下滑，空步骤回退 name 由调用处处理）。

- [ ] **Step 1: 按 Interfaces 块写 `Task.kt`**

文件头 KDoc 标注「自动化任务 v4 模型」。结构照 spec §2：`NodeLocator`、`Step`（密封接口 + 5 个 `@Serializable` 子类）、`Task`（含内嵌 `sealed interface Trigger { OnPage(once) / Loop(intervalMs, maxRounds=0) }` 与 `sealed interface OnFailure { Retry(times=3, intervalMs=500) / Stop }`，同样显式 `@SerialName`）。`Task` 的展示字段：`name: String`。`stepsSummary()`/`label()` 为纯展示函数，实现照搬现有 `AutomationRule.stepsSummary`/`actionLabel`（见被删文件 `app/src/main/java/com/yzjdev/autotask/automation/RuleEngine.kt:100-113` 的文案），但 `Click` 的文本从 `step.locator` 取（field==TEXT 时显示 `点「value」`，否则「点击」）。

- [ ] **Step 2: 编译验证**

Run: `sh gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yzjdev/autotask/automation/Task.kt
git commit -m "feat(automation): add v4 Task/Step/NodeLocator model"
```

---

### Task 3: 存储 `TaskStore.kt`

**Files:**
- Create: `app/src/main/java/com/yzjdev/autotask/automation/TaskStore.kt`
- Modify: `app/src/main/java/com/yzjdev/autotask/automation/Task.kt`（如需给 `Step`/`Task` 补默认构造兼容，尽量不加）

**Interfaces:**
- Consumes: Task 2 的 `Task` / `Step` 模型。
- Produces: `object TaskStore`，签名与现 `RuleStore` 对齐：
  - `fun loadAll(context: Context): List<Task>`（key `"tasks"`，prefs 名 `"automation_tasks"`，`Json { ignoreUnknownKeys = true }`）
  - `fun saveAll(context: Context, tasks: List<Task>)`
  - `data class AppInfo(val packageName: String, val label: String)` + `fun loadInstalledApps(context: Context): List<AppInfo>`（实现整段照搬现 `RuleStore.loadInstalledApps`，`RuleStore.kt:47-63`）

- [ ] **Step 1: 写 `TaskStore.kt`**

`Json { ignoreUnknownKeys = true }` + `encodeDefaults = true`（Swipe.up=false、maxRounds=0 这类默认值必须显式落盘，Review Focus 第 4 条依赖它）。文件头 KDoc 注明「v4 存储；旧 key automation_rules 不读取不删除」。

- [ ] **Step 2: 编译验证**

Run: `sh gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yzjdev/autotask/automation/TaskStore.kt
git commit -m "feat(automation): add v4 TaskStore with kotlinx-serialization"
```

---

### Task 4: 执行引擎 `TaskRunner.kt`

**Files:**
- Create: `app/src/main/java/com/yzjdev/autotask/automation/TaskRunner.kt`

**Interfaces:**
- Consumes: Task 2 的 `Task`/`Step`/`NodeLocator.matches`；`DramaAccessibilityService` 的现有方法 `rootInActiveWindow`、`goBack()`、`swipeUp()`、`swipeDown()`、`clickAt(x,y)`、`readableText(node)`；`LogStore.log(String)`。
- Produces（Task 5/6 依赖）:
  - `class TaskRunner(private val service: DramaAccessibilityService)`
  - `fun setTasks(tasks: List<Task>)`（整体替换规则表并 cancelAll，对应现 `clearRules+addRule` 组合）
  - `fun onActivityChanged(packageName: String, activityName: String)`
  - `fun onLeftPackage(packageName: String)`
  - `fun cancelAll()`

- [ ] **Step 1: 写 `TaskRunner.kt`**

要点（spec §3 全部钉死）：
- `scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)`；`jobs: MutableMap<String, Job>`（key = task.id）。
- `onActivityChanged`：遍历 tasks → enabled + pkg 相等 + activity 精确或 `startsWith` 匹配 → `Trigger.Loop` 时 `jobs` 已有该 task 则跳过（取代 activeLoops）；`Trigger.OnPage(once=true)` 用 `executed: HashSet<String>`（key `pkg|activity|taskId`）去重 → `launchTask(task)`。
- `launchTask`：`scope.launch { jobs[task.id] = coroutineContext[Job]!!; ... }`，内部按 Trigger 分派：OnPage 直接 `withRetry` 跑一轮；Loop 用 `while` 轮询（轮间 `delay(intervalMs)`，醒来后先查前台与 enabled，达 maxRounds 退出，`loopMaxRounds=0` 无限）。
- `withRetry(task, round: suspend () -> Boolean)`：`OnFailure.Retry` → 最多 `times+1` 次执行，失败间隔 `delay(intervalMs)`，每次失败 `LogStore.log`；超限放弃并 `executed.remove` 去重 key（去重 key 通过 `launchTask` 参数传入）；`OnFailure.Stop` → 跑一次。
- `runRound(task): Boolean`（suspend）：`task.steps.forEachIndexed`，每步先 `service.rootInActiveWindow?.packageName == task.packageName` 检查，不在前台 → `throw CancellationException("left foreground")`（取消当前 job）；动作分发：
  - `Click` → 找节点（复用下方 `findNode`/`dfs`，从 RuleEngine.kt:325-355 平移，入参改单个 `NodeLocator`）；找不到 return false；找到后 `isClickable && performAction(ACTION_CLICK)`，失败退化 `clickAt(bounds 中心)`，两者都失败 return false。
  - `Wait` → `withTimeout(step.timeoutMs) { while (findNode(...) == null) delay(200) }`；`withTimeout` 抛出的超时用 `runCatching` 区分：`TimeoutCancellationException` → return false，其他 `CancellationException` rethrow（Review Focus 第 1 条）。
  - `Sleep` → `delay(step.ms)`；`Swipe` → `swipeUp()/swipeDown()`；`Back` → `goBack()`。
- `onLeftPackage(pkg)`：`executed.removeAll { it.startsWith("$pkg|") }`；`jobs.filterKeys` 对应 task 的 packageName==pkg 的全部 cancel 并移除。
- `cancelAll()`：jobs 全 cancel，executed 清空。
- 日志文案沿用现有前缀（`▶ 触发「…」@ pkg/activity`、`✓ 成功…`、`✕ 失败…第N步 原因`），展示用 `task.stepsSummary()`。

- [ ] **Step 2: 编译验证**

Run: `sh gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yzjdev/autotask/automation/TaskRunner.kt
git commit -m "feat(automation): add coroutine TaskRunner engine"
```

---

### Task 5: 服务接入点切换

**Files:**
- Modify: `app/src/main/java/com/yzjdev/autotask/automation/DramaAccessibilityService.kt`

**Interfaces:**
- Consumes: Task 4 的 `TaskRunner` 全部公开方法。
- Produces: `DramaAccessibilityService.taskRunner` 字段（Task 6 依赖）。

- [ ] **Step 1: 替换字段与调用点**

`RuleEngine.kt` 暂不删（MainActivity 还引用着，Task 6 删）。改动：
- `val ruleEngine = RuleEngine(this)` → `val taskRunner = TaskRunner(this)`（RuleEngine.kt 此时仍可编译，但字段删了它就没入口——直接把 import 也切掉，未使用类不报错）。
- 全文件 grep `ruleEngine`，两处调用（`onWindowChanged` 内触发处、离开包回调处）改为 `taskRunner.onActivityChanged(...)` / `taskRunner.onLeftPackage(...)`。

- [ ] **Step 2: 编译验证（此时 MainActivity 仍引用 ruleEngine 字段会失败，预期内）**

Run: `sh gradlew :app:compileDebugKotlin`
Expected: FAIL，且错误只应出现在 `MainActivity.kt`（引用 `svc.ruleEngine`）。若 `DramaAccessibilityService.kt` 或 automation 包内报错，回到 Step 1 修正。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yzjdev/autotask/automation/DramaAccessibilityService.kt
git commit -m "refactor(automation): wire service to TaskRunner"
```

（中间态编译失败允许提交：Task 6 完成后整体恢复可编译。）

---

### Task 6: MainActivity 切换到 Task 模型

**Files:**
- Modify: `app/src/main/java/com/yzjdev/autotask/MainActivity.kt`

**Interfaces:**
- Consumes: Task 2 `Task`/`Step`/`stepsSummary()`/`label()`；Task 3 `TaskStore`（含 `AppInfo`）；Task 5 `taskRunner.setTasks(...)`。
- Produces: 无（终端任务）。

- [ ] **Step 1: 数据层替换（MainActivity.kt:77-171 区域）**

- import：`AutomationRule`→`Task`、`RuleStep`→`Step`、`RuleStore`→`TaskStore`。
- `rules: List<AutomationRule>` → `tasks: List<Task>`；`persistRules` 内 `svc.ruleEngine.clearRules()/addRule` → `svc.taskRunner.setTasks(next)`；`RuleStore.loadAll/saveAll/loadInstalledApps` → `TaskStore.*`。
- 变量名/导航状态 `editingRuleId` 等保持不变（只是类型语义换掉），列表过滤 `tasks.filter { it.packageName == pkg }`、计数 `tasks.count { it.packageName == pkg }`。

- [ ] **Step 2: 列表页 `TaskListPage`（MainActivity.kt:544-656）**

- 参数类型 `List<AutomationRule>` → `List<Task>`；卡片标题 `rule.stepsSummary()` → `task.stepsSummary()`（空步骤回退 `task.name`）。
- 副标题行（MainActivity.kt:632-639）改按新模型拼：`task.activityPattern?.let { "Activity≈$it · " }` + Trigger 描述（OnPage→`仅一次/每次进入`；Loop→`循环${intervalMs}ms` + `×N轮`/无限）+ OnFailure.Retry→` · 重试N次`。

- [ ] **Step 3: 编辑器 `RuleEditorPage`（MainActivity.kt:720-947）**

- 状态：`steps: List<Step>`；触发区——`once` Switch 仅在 `Trigger.OnPage` 下显示；`loopOn` Switch 切换 `Trigger.OnPage ↔ Trigger.Loop`（间隔秒/轮数字段逻辑不变）。
- 保存按钮组装 `Task(id = existing?.id ?: "task_${System.currentTimeMillis()}", name = existing?.name ?: "任务 ${steps.size} 步", packageName = pkg, activityPattern = …, enabled = existing?.enabled ?: true, trigger = …, steps = steps, onFailure = existing?.onFailure ?: OnFailure.Retry(3, 500))`。
- 步骤行摘要 `step.summary()` → `step.label()`；`defaultStep(action)` 改为 5 个工厂：`Step.Click(NodeLocator(TEXT, EQUALS, ""))`、`Step.Wait(NodeLocator(TEXT, EQUALS, ""), 5000)`、`Step.Sleep(1000)`、`Step.Back`、`Step.Swipe(true)`；按钮文案不变（+点文本/+等待/+延时/+返回/+滑动）。

- [ ] **Step 4: 步骤弹窗 `StepEditorDialog`（MainActivity.kt:959-1142）**

按「每个 Step 只编辑自己的字段」重写：
- `initial: Step`，先 `when(initial)` 初始化各自状态；动作切换（点击/等待/延时/返回/滑动按钮行保留）改用 `var actionName by remember { mutableStateOf(stepKind(initial)) }`，切换时重置参数为对应工厂默认。
- Click/Wait 共用定位器输入（复用现有 `CondRow` 组件，但只保留一个定位器：字段选择 4 选 1——viewId/文本/描述/类名——加等于/包含切换；不再多字段 AND）。字段选择用现有按钮选中态样式（参考 swipeUp 按钮组 MainActivity.kt:1069-1082）。
- Wait 显示超时秒输入、Sleep 显示延时秒输入、Swipe 显示上/下滑按钮；保存时按 `actionName` 构造对应 `Step` 子类（保留编辑中 Step 的 `id`：`initial` 是同类型时带 `initial.id`，换类型则新 id）。

- [ ] **Step 5: 编译验证**

Run: `sh gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（此时 RuleEngine.kt/RuleStore.kt 已无人引用，但还没删）。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/yzjdev/autotask/MainActivity.kt
git commit -m "refactor(ui): adapt rule list/editor to v4 Task model"
```

---

### Task 7: 删除旧实现 + 全量验收

**Files:**
- Delete: `app/src/main/java/com/yzjdev/autotask/automation/RuleEngine.kt`
- Delete: `app/src/main/java/com/yzjdev/autotask/automation/RuleStore.kt`

- [ ] **Step 1: 删除两个旧文件**

```bash
git rm app/src/main/java/com/yzjdev/autotask/automation/RuleEngine.kt app/src/main/java/com/yzjdev/autotask/automation/RuleStore.kt
```

- [ ] **Step 2: 无残留引用检查**

Run: `grep -rn "RuleEngine\|RuleStore\|RuleStep\|NodeCondition\|AutomationRule" app/src/main/java --include=*.kt`
Expected: 无输出。

- [ ] **Step 3: 全量构建**

Run: `sh gradlew assembleDebug`
Expected: BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(automation): remove v3 RuleEngine/RuleStore"
```

- [ ] **Step 5: 真机行为验收（需用户配合，装 APK 手动验证 spec §7 第 2-5 条）**

1. 新建任务（点文本 + 等待 + 延时），进入目标应用看日志推进；
2. 循环任务前台重跑、离开即停；
3. Wait 超时走重试、Stop 策略直接放弃；
4. 杀进程重启后任务列表恢复。
