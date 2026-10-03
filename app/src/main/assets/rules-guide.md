# 规则引擎完整文档(GKD 订阅对齐)

本应用的自动化引擎与 [GKD](https://github.com/gkd-kit/gkd)(gkd.li)订阅格式完全对齐:GKD 生态里的订阅 JSON5 可以直接导入使用,自己编写的规则也可以导出为 GKD 订阅格式分享。本文档从零开始讲解:**引擎怎么工作 → 怎么写一条规则 → 每个字段什么含义 → 选择器语法 → 调度参数如何配合 → 订阅格式 → 界面操作**。

> 阅读建议:新手只需读 §1 快速上手 + §9 界面操作就能开始用;写复杂规则前再读 §4-§7 的详解。

---

## 目录

1. [快速上手:五分钟写一条规则](#1-快速上手)
2. [引擎工作原理](#2-引擎工作原理)
3. [三层模型总览](#3-三层模型总览)
4. [规则组(GkdTask)字段详解](#4-规则组字段)
5. [规则(Rule)字段详解](#5-规则字段)
6. [调度与执行机制详解](#6-调度与执行)
7. [选择器语法详解](#7-选择器语法)
8. [动作与坐标详解](#8-动作与坐标)
9. [界面操作说明](#9-界面操作)
10. [订阅格式规范](#10-订阅格式)
11. [调试与常见问题](#11-调试与常见问题)
12. [与 GKD 官方的差异](#12-与-gkd-的差异)

---

<a id="1-快速上手"></a>
## 1. 快速上手

### 1.1 场景:自动关闭某应用的"跳过"按钮

1. 打开应用 → 底部导航「**应用**」→ 找到目标应用(可用搜索)→ 点进去进入**任务页**。
2. 点右上角「**+**」新建规则,填入:
   - **规则名**:`关闭跳过按钮`(可选,只用于展示)
   - **matches**(核心!一行选择器):

     ```
     [text*="跳过"][clickable=true][visibleToUser=true]
     ```

   - **action**:保持默认 `点击`
3. 点「**保存**」。切到目标应用,出现"跳过"按钮时引擎会自动点掉它,日志页可看到执行记录。

### 1.2 这条规则的读法

`[text*="跳过"]` = 找 **text 属性包含**"跳过"二字的控件;`[clickable=true]` = 还必须**可点击**;`[visibleToUser=true]` = 必须**对用户可见**(在屏幕内,不是被藏起来的空节点)。三个条件全部满足(方括号相邻 = AND)的节点就是动作目标,引擎对它执行一次点击。

### 1.3 场景:先关弹窗、再点返回(顺序链)

有些界面关掉弹窗后还需要再按一次返回。用 **preKeys** 表达先后顺序:

- 规则 A(先执行):`matches=[弹窗上的关闭按钮]`,action = `仅标记`(none)
- 规则 B(后执行):`matches=[text="确定"]`,`preKeys = A 的规则 key`

B 只会在 A 执行后的 **10 秒内**、且晚于 B 自己上次执行时才触发——这是 GKD 的"前置 key 链"语义,详见 §6.5。

---

<a id="2-引擎工作原理"></a>
## 2. 引擎工作原理

```
无障碍服务(DramaAccessibilityService)
   │  监听系统事件:窗口切换 / Activity 变化 / 界面内容变化
   ▼
调度器(TaskRunner)
   │  ① 按前台包名筛出候选规则组(应用组只在自己包内触发,全局组任意包触发)
   │  ② 按组遍历规则:调度准入(冷却/次数/时间窗)→ preKeys → 选择器匹配
   │  ③ forcedTime 规则额外走 250ms 主动轮询(flutter/webview 界面不发事件也能匹配)
   ▼
执行器
   │  actionDelay 延迟后二次确认选择器仍匹配 → 对目标节点执行动作
   │  (节点事件 click 优先,失败/坐标类动作用无障碍手势 dispatchGesture)
   ▼
日志(LogStore,环形 200 条,不落盘)
      「▶ 触发」「↳ 点击/返回/滑动」「未找到目标节点」全程记录
```

三个关键结论:

- **规则只在"界面变化"的时机评估**。普通原生应用切换 Activity 时触发;flutter/webview 类界面需要配 `forcedTime` 让引擎主动轮询(§6.4)。
- **选择器匹配的是"当前屏幕的控件树"**。写规则就是描述"目标控件长什么样、它在哪个位置"。
- **动作目标 = matches 最后一条选择器查到的节点**。多段选择器(§7.3)从最右段往左回溯,最左段负责"定位环境",带 `@` 的段才是被点的东西。

---

<a id="3-三层模型总览"></a>
## 3. 三层模型总览

| 层 | 本项目模型 | GKD 对应 | 一句话解释 |
|---|---|---|---|
| 订阅 | `SubscriptionStore.Subscription` | RawSubscription | 一个远程 JSON5 文件,含很多应用的一堆规则 |
| 规则组 | `GkdTask` | RawAppGroup / RawGlobalGroup | **一个应用的一批规则**,组级参数是组内所有规则的默认值 |
| 规则 | `GkdTask.Rule` | RawAppRule / RawGlobalRule | **一次触发**:什么时候匹配(选择器)+ 匹配后做什么(动作) |

规则组的两种类型:

- **应用组**:`packageName` 指向某个应用,只在该应用前台时评估。982 个订阅组的绝大多数属于此类。
- **全局组**:`packageName` 为空(订阅里写在 `globalGroups` 下),**任何应用**前台都评估,用于"开屏广告""更新弹窗"这类跨应用场景。全局组可通过 `disableIfAppGroupMatch` 让位给应用自己的同名规则,避免双重触发。

id 标记约定(用于合并去重):订阅导入 = `sub_<订阅id>_<hash>`,重复导入即更新;本地新建 = `task_<时间戳>`。

### 持久化位置

| 数据 | SharedPreferences 文件 | key |
|---|---|---|
| 规则表(全量 JSON) | `automation_tasks` | `gkd_rules_v5` |
| 订阅列表(URL+原文+版本) | `gkd_subscriptions` | `gkd_subscriptions_v1` |
| 日志 | 不落盘,进程内环形 200 条 | — |

---

<a id="4-规则组字段"></a>
## 4. 规则组(GkdTask)字段

组级字段是**组内所有规则的默认值**:规则没写这个参数时,用组级的;规则自己写了,用规则级的(取值规则:规则级 ≠ 0/未写 → 用规则级,否则组级,再否则引擎默认)。

### 4.1 基础字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `key` | Long | ✔(订阅) | 组的唯一编号。**只在本应用内需要唯一**,通常从 0 或 1 递增。规则的 `preKeys` 引用的就是"同组内某条规则的 key" |
| `name` | String | ✔ | 组名。惯例格式:`分类-具体描述`,如 `开屏广告-全局`、`局部广告-首页右下角浮窗`。**全局组的 `disableIfAppGroupMatch` 按组名匹配,取名要规范** |
| `enable` | Boolean | | 默认 true;订阅可用它预置开关 |
| `desc` | String | | 描述,展示用 |
| `rules` | List | ✔ | 组内规则列表;允许空组(GKD 保留壳组) |
| `order` | Long | | 组的评估顺序,**越小越先**。订阅对全局组用负数(-10/-9/-8)让它们先于应用组执行 |

### 4.2 应用组专用

| 字段 | 说明 |
|---|---|
| `activityIds` | Activity 白名单(字符串或数组)。支持**全限定名**(`com.x.y.MainActivity`)与**短名**(`.MainActivity`,相对应用包名);引擎按前缀匹配。留空 = 任意界面 |
| `excludeActivityIds` | Activity 排除。典型用法:QQ 开屏规则排除聊天页 `['.activity.ChatActivity','.search.activity.UniteSearchActivity']` |
| `versionCode` | 应用版本限定。matcher 形式:`{minimum: 100191030}` / `{maximum:…}` / `{include:[…]}` / `{exclude:[…]}` |
| `versionName` | 版本名限定:`{pattern:"regex"}` / `{include:[…]}` / `{exclude:[…]}` |

> **为什么要限定版本**:广告 SDK 改版后控件的 vid/结构会变,限定 versionCode 能避免在新版本上误点。这是订阅维护者的良好习惯。

### 4.3 全局组专用

| 字段 | 说明 |
|---|---|
| `disableIfAppGroupMatch` | **让位机制**。目标应用已存在**组名包含此字符串**的应用组时,本全局组在该应用禁用。例:全局组"开屏广告-全局"设 `disableIfAppGroupMatch:'开屏广告'`,某应用自己写了名为"开屏广告"的组,则该应用内只跑应用自己的规则,不跑全局的 |
| `matchAnyApp` / `matchSystemApp` / `matchLauncher` | 声明性保留(本引擎 packageName 为空即匹配任意前台) |
| `globalApps` / `scopeKeys` | 解析时保留;scopeKeys 在组级出现时为规则作用域标记 |

### 4.4 调度参数(组级默认,规则可覆盖)

各参数的精确语义见 §6;这里是清单:

| 字段 | 引擎默认 | 干什么用 |
|---|---|---|
| `fastQuery` | false | 声明只用 vid/text 快查,性能提示(订阅里 2516 处,几乎每条都写) |
| `matchTime` | 0(不限) | 匹配时间窗(ms):组首次触发匹配后,窗内才允许执行 |
| `actionCd` | 1000ms | 冷却:同一规则两次执行的最小间隔 |
| `actionCdKey` | 0 | 冷却共享 key:不同规则填同一个 key 即共享冷却(订阅中极少用) |
| `actionDelay` | 0ms | 触发后延迟再执行,执行前**重新校验**选择器 |
| `actionMaximum` | 0(不限) | 执行次数上限 |
| `actionMaximumKey` | 0 | 次数共享 key |
| `resetMatch` | `activity` | 计数/时间窗的重置策略(§6.3) |
| `priorityTime` | 0(关) | 服务启动后此窗内,组内规则为优先级规则 |
| `priorityActionMaximum` | 1 | 优先级窗内最多执行次数 |
| `forcedTime` | 0(关) | 主动轮询窗口(§6.4) |
| `matchDelay` / `matchRoot` | 0 / false | 声明性保留 |

**订阅作者的经验值**(来自 id667 订阅 1139 处 resetMatch 统计):应用广告组典型配置是

```
matchTime:10000, actionMaximum:1, resetMatch:'app', fastQuery:true
```

含义:界面出现后 10 秒窗内最多执行 1 次,离开应用即重置——"每次进应用只关一次弹窗",防止误循环。

---


<a id="5-规则字段"></a>
## 5. 规则(Rule)字段详解

一条规则回答两个问题:**什么时候触发**(选择器)和**触发后做什么**(动作),外加一组调度微调。

### 5.1 匹配相关字段

| 字段 | 语义 | 典型用途 |
|---|---|---|
| `matches` | 字符串**或数组**;多条时**全部命中**才触发 | 主选择器。多数规则就一条 |
| `anyMatches` | 数组;**任一条命中**即触发 | 多种形态的同类弹窗(广告关闭按钮有好几种布局)。与 matches **独立判定**;都填时以 matches 为准 |
| `excludeMatches` | 字符串或数组;**存在一个命中就放弃触发** | 排除误伤:如全局跳过规则排除 `[text*="阅读并同意"]`(那是隐私协议页,不能点) |
| `excludeAllMatches` | 数组;**全部命中才放弃触发** | AND 语义的排除,订阅中暂无使用,引擎支持 |
| `preKeys` | 数组 `[0]` / `[0,1]`;前置规则 key | 顺序链:所列 key 的规则**刚刚执行过**(晚于本规则上次执行,且 10 秒内)才允许触发 |
| `activityIds` / `excludeActivityIds` | 规则级 Activity 限定 | 同组名下多个界面不同做法时使用 |

**匹配判定顺序**(引擎 `ruleMatches`):excludeMatches 任一命中 → 不触发;excludeAllMatches 全命中 → 不触发;matches 非空 → 全命中才触发;否则 anyMatches 任一命中 → 触发;全空 → 不触发。

> **matches 数组 vs anyMatches 的区别**(容易混):`matches:['A','B']` 要求 A **和** B 同时在屏幕上(用于多步骤定位);`anyMatches:['A','B']` 要求 A **或** B 出现任意一个(用于兼容多种布局)。id667 订阅中 matches 数组 307 处、anyMatches 68 处。

### 5.2 动作相关字段

| 字段 | 说明 |
|---|---|
| `action` | 动作名,缺省 = `click`。全集见 §8.1 |
| `position` | 自定义坐标(相对目标节点边界的表达式),见 §8.2 |
| `swipeArg` | swipe 动作的滑动参数,见 §8.3 |

### 5.3 其他字段

`key`(规则编号,preKeys 引用)、`name`(规则名,日志显示)、`fastQuery` / `matchRoot` / `matchDelay`(声明性)、`versionCode` / `versionName`(同组级)、`order`(组内评估顺序)。

---

<a id="6-调度与执行"></a>
## 6. 调度与执行机制详解

### 6.1 触发入口

**Activity 切换**(主路径):窗口事件 → 拿到前台包名 + Activity → 逐组逐规则评估。组按 `order` 升序;组内规则优先级规则在前、其余按 order。

**forcedTime 轮询**:只要任何启用的规则带 `forcedTime > 0`,引擎启动一个 250ms 间隔的轮询协程,在"服务启动后 forcedTime 毫秒"窗口内主动抓当前屏幕匹配。**何时需要**:flutter / webview / 游戏内嵌页的界面变化不产生无障碍窗口事件,普通触发永远不会命中;给这类界面配 `forcedTime:10000` 表示"启动后 10 秒内每 250ms 查一次屏"。订阅里 48 处,全局开屏组全都有。

**手动执行**:任务页每条规则可手动触发(不走包名与调度校验),用于测试选择器写没写对。

### 6.2 一条规则从触发到执行的完整判定

```
① 组归属:packageName == 前台包名(应用组)或为空(全局组)
② 全局组让位:目标应用有组名含 disableIfAppGroupMatch 的应用组 → 跳过
③ Activity:activityIds 命中且 excludeActivityIds 未命中(组级+规则级)
④ 调度准入 schedulable:
     count < actionMaximum            (执行次数未用完)
     now - matchSince ≤ matchTime     (匹配窗未过)
     now - lastAt ≥ actionCd          (冷却已过,默认 1000ms)
⑤ preKeys:每个前置 key 的规则 lastAt 晚于本规则 lastAt 且在 10s 内
⑥ 选择器匹配:exclude → matches/anyMatches(见 §5.1 判定顺序)
⑦ 执行:actionDelay > 0 时先延迟,然后重新校验⑥,仍匹配才真正执行
⑧ 成功后:count++,lastAt=now;若在优先级窗内,消耗一次 priorityLeft
```

任何一步不过,本轮静默跳过(不报错,因为"不匹配"是常态)。

### 6.3 resetMatch:计数什么时候清零

| 值 | 清零时机 | 适用场景 |
|---|---|---|
| `activity` | Activity 切换 | 同一界面允许多次触发(如列表页反复出现卡片广告)。引擎默认 |
| `app` | 离开目标应用 | "每次进应用只处理一次"(开屏弹窗)。**订阅里 1124/1139 都用它** |
| `match` | 由"失配→重新匹配"检测重置 | 屏幕上目标消失又出现算新一轮 |

### 6.4 priorityTime 与 forcedTime 的窗口起点

两者都以**服务启动时刻**(`bootAt`)为窗口起点:`now - bootAt ≤ 时间值` 时生效。

- `priorityTime`:窗内该规则是**优先级规则**——排序时排在普通规则之前,可打断普通规则的评估;最多执行 `priorityActionMaximum` 次(默认 1)。用途:开屏广告必须"抢在用户点到内容之前"关掉,所以开屏组几乎都配 `priorityTime:10000`。
- `forcedTime`:窗内开启主动轮询。窗口过后轮询自动停止(下次换表时重新评估),不常驻耗电。

### 6.5 preKeys 顺序链详解

状态键 = `taskId|ruleKey`,每条规则记录 `lastAt`(上次执行时刻)。规则 B(带 `preKeys:[A]`)的判定:

```
A 执行过(A.lastAt > 0)
且 A.lastAt > B.lastAt          (A 比 B 最近一次执行更晚 → 是"下一轮"的事)
且 now - A.lastAt < 10000ms     (A 刚发生,10 秒内)
```

订阅实战(某阅读应用"更新提示"组):

```
规则 key=0: matches=[勾选框"暂不更新"未勾选], action=click   ← 先勾选
规则 key=1: preKeys=[0], matches=[text="暂不更新"], action=click ← 再点按钮
```

多前置 `preKeys:[0,1]`:两个都必须满足(AND)。

### 6.6 状态清理

离开某个包名时:该应用的应用组清状态、在途执行取消;全局组只在 `resetMatch='app'` 时随离开重置。规则保存/订阅导入后整体换表,状态全部清空。

---

<a id="7-选择器语法"></a>
## 7. 选择器语法详解

选择器是规则的灵魂,语法与 GKD 完全一致(逐文件移植自 gkd-selector)。一个选择器 = **若干"段"用关系符连接**,每段 = **若干个属性断言**;带 `@` 的段是动作目标。

### 7.1 属性断言(方括号)

```
[text*="跳过"]            text 包含"跳过"
[text="确定"]             text 完全等于"确定"
[vid="iv_close"]          resource-id 等于(省前缀匹配)
[id$="_btn"]              完整 id 以 _btn 结尾
[name="TextView"]         控件类名
[desc*="关闭"]            contentDescription 包含
[clickable=true]          布尔属性
[text.length>3]           属性 + 内建成员运算
```

**比较运算符**:

| 运算符 | 含义 | 示例 |
|---|---|---|
| `=` / `!=` | 等于 / 不等 | `[checked=false]` |
| `^=` / `!^=` | 前缀 / 非前缀 | `[text^="选"]` |
| `$=` / `!$=` | 后缀 / 非后缀 | `[vid$="_close"]` |
| `*=` / `!*=` | 包含 / 不包含 | `[text*="跳过"]` |
| `~=` / `!~=` | 正则 / 非正则 | `[text~="(?is).*skip.*"]` |
| `<` `<=` `>` `>=` | 数值比较 | `[width<500]` `[childCount>=3]` |

**支持的全部属性**:`id` `vid`(短 id) `name`(类名) `text` `desc` `clickable` `focusable` `checkable` `checked` `editable` `longClickable` `visibleToUser` `left` `top` `right` `bottom` `width` `height` `index` `depth` `childCount`(以及 `parent` 导航)。

> **vid 与 id**:`vid` 匹配短 id(`iv_close`),`id` 匹配完整 id(`com.x.y:id/iv_close`)。订阅里 vid 1964 处、id 777 处——**优先用 vid**,它不随包名变。

**相邻方括号 = AND**:`[text*="跳过"][clickable=true][visibleToUser=true]` 三个条件都要满足。`visibleToUser=true` 强烈建议写上:不可见节点点不着,还容易误匹配隐藏布局里的同款控件(订阅里 1783 处)。

### 7.2 布尔逻辑(重要:硬性语法约束)

```
([text*="广告"] && [clickable=true]) || [vid="ad_close"]
!([text="取消"])
```

- `&&` 优先级高于 `||`
- **逻辑运算符两侧的操作数必须用 `()` 包裹**——这是 GKD 的硬约束,不写括号直接语法错误
- 求值短路:AND 左侧为空即停,OR 左侧非空即停

### 7.3 关系选择器(段间连接)

一个选择器可以由多段组成,段与段之间用**关系符**连接,描述"目标控件周围长什么样":

| 关系符 | 含义 | 示例读法 |
|---|---|---|
| `+` / `+n` | 右边第 n 个兄弟 | `@[vid="close"] + [text="广告"]`:关闭按钮,它右边是"广告"文字 |
| `-` / `-n` | 左边第 n 个兄弟 | `[text="广告"] -2 @View[clickable=true]`:左侧第 2 个可点 View |
| `>` / `>n` | 第 n 层父节点 | `@Button >2 [vid="container"]` |
| `<` / `<n` | 子节点 | `@* < [text="确定"]` |
| `<<` / `<<n` | 任意后代(n = 层深) | `<< [text="开屏广告"]` |
| `->` | 引用之前匹配段 | 用于跨段取 `getChild(0).text` 等值比较 |

**偏移表达式**:`+2` 固定偏移;`(1,2,3)` 元组(任一);`(n+3)` `(an+b)` 多项式(**最多两个单项式**,支持 `n+6`、`12-n`、`-3n+10`、`(-n+18)` 负向)。`*` 通配(任意控件)。

**匹配方向**:从**最右段**开始,每命中一层就按关系符向左找候选,逐段回溯(帧式栈);`@` 标记的段决定"点谁"——**没写 `@` 时,最右段就是目标**。

实战示例(id667 订阅,含 `@` 与关系回溯):

```
@[vid="iv_close"] - [vid="rl_ad"][visibleToUser=true]
```
读法:找 vid 为 iv_close 的控件(`@`=点它),它的**左边一个兄弟**是 rl_ad 容器且可见——比单纯 `[vid="iv_close"]` 更精确,避免点到别的同名关闭按钮。

```
@View[clickable=true][childCount=0] +(1,2) TextView[index=parent.childCount.minus(1)] <n FrameLayout[childCount>2] >(n+6) [text*="第三方应用"]
```
读法:一个小可点 View,右边 1~2 格是某 FrameLayout 的最后一个 TextView,该 FrameLayout 的 (n+6) 层祖先是"第三方应用"文字——开屏互动广告的典型结构。`index=parent.childCount.minus(1)` 用到了属性值表达式。

### 7.4 属性值表达式(断言里做运算)

方括号里的值可以用成员访问与内建函数:

- 算术:`parent.childCount.minus(1)`(订阅 138 处 minus;plus 亦支持)
- 索引/子节点:`getChild(0).text*="Plus"`、`getChild(0).desc="关闭"`
- 字符串:`text.substring(3,5).toInt()>45`(订阅实战:解析"03:52"格式的倒计时!`text.get(1).toInt()<3` 取第 2 个字符)
- `equal` / `notEqual` 容忍 null;其余函数任一参数为 null 整体返回 null(不匹配)
- `or` / `and` / `ifElse` 短路组合

### 7.5 缩写与捷径

- **类名简写**:`TextView` 单写 ≡ `[name="TextView"||name$=".TextView"]`(整名或短类名后缀)
- `@*`:目标 = 当前段任意控件,只靠关系定位(订阅里常见 `@* ->3 [text="…"]`)
- 空 `[]` 或空白表达式恒匹配(慎用)

### 7.6 编辑器里的写法

规则编辑器的选择器输入框**每行一条**(matches 多行 = 多条 AND),支持 `//` 注释行;输入时逐行实时校验,写错标红并提示行号;上方"选择器片段"一键插入常用模板。语法错误**不允许保存**。

---

<a id="8-动作与坐标"></a>
## 8. 动作与坐标

### 8.1 动作全集

| action | 名称 | 行为 |
|---|---|---|
| `click` | 点击(默认) | **混合语义**:优先发节点 ACTION_CLICK;节点不可点时改用坐标手势点目标中心。有 position 时直接走坐标 |
| `clickNode` | 点节点 | 仅节点事件,不发手势 |
| `clickCenter` | 点中心 | 仅坐标手势(目标节点中心,或 position 计算点) |
| `longClick` / `longClickNode` / `longClickCenter` | 长按三兄弟 | 同上,500ms 长按手势 |
| `back` | 返回 | 全局返回键。**无需选择器**(留空 = 页面就绪即执行,配 preKeys 做链) |
| `swipe` | 滑动 | 有 swipeArg 按参数;无参数时整屏上滑(swipeDir 本地扩展 1 上/2 下) |
| `none` | 仅标记 | 什么都不做。**preKeys 链的占位/条件节点** |
| `inputText` | 输入 | 聚焦目标(文本注入扩展中) |
| `launchApp` | 启动应用 | 待实现 |
| `check` / `uncheck` | 勾选/取消 | ACTION_SELECT / CLEAR_SELECTION |

订阅中实际分布:clickCenter 68、back 66、longClick 12、none 3、swipe 2——**back 与坐标点击占了非默认动作的大头**。

### 8.2 position:相对目标的坐标

六字段表达式(字符串):`left` `top` `right` `bottom` `x` `y`。四则运算 `+ - * / %` 与括号均支持。

可用变量:`left` `top` `right` `bottom`(目标节点边界) `width` `height`(节点尺寸) `random`(0~1 随机) `screenWidth` `screenHeight`(屏幕尺寸)。

**求值规则**(照抄 GKD):x 坐标 = `left`(节点左界 + 值)/ `right`(右界 − 值)/ `x`(绝对);y 同理;取值优先级 left>right>x、top>bottom>y。x、y 两个方向**各至少一个字段**才有效。

示例(id667 订阅实战):

```
position:{left:'width * 0.7972', top:'width * 0.5347'}   ← 目标节点内比例定位(编辑按钮)
position:{right:'width * 0.1', top:'height/2'}           ← 右侧向内 10% 高度居中
```

**有 position 时,click/longClick 自动切换为坐标手势**(不再尝试节点事件)——适合"按钮本身不可点、但它的某个区域可以点"的怪异布局。

### 8.3 swipeArg:精确滑动

```
swipeArg:{ start:{left:'width * 0.15', top:'width * 0.08'},
           end:{left:'width * 0.64', top:'width * 0.08'},
           duration:114 }
```

- `start` 必填;`end` 缺省 = start(即变成点击);`duration` 缺省 300ms
- 坐标同样支持六字段表达式,变量以**目标节点**边界为基准;想按屏幕算就用 `screenWidth/screenHeight`
- 用途:列表条目"右滑删除"(订阅实战)、滑块验证、翻页

编辑器中选 `swipe` 动作后会出现 swipeArg 输入框,格式:`start(x=…,y=…),end(x=…,y=…),duration=300`。

---

<a id="9-界面操作"></a>
## 9. 界面操作说明

### 9.1 底部导航

| 页 | 功能 |
|---|---|
| **首页** | 中控电源盘(一键开/关无障碍服务)、节点悬浮窗开关、隐藏最近任务、远程订阅入口、**规则文档**(内置本手册)、权限管理(安全设置/Shizuku/电池/自启动) |
| **应用** | 应用列表(用户应用/系统应用分段;按名称/任务数/安装时间排序;搜索)。订阅规则指向的未安装应用灰显置后 |
| **日志** | 执行记录(环形 200 条):`▶ 触发`、`↳ 点击/返回/滑动`、`未找到目标节点`;点条目看详情并复制 |

### 9.2 任务页(点某个应用进入)

- 该应用全部规则组:开关 / 编辑 / 删除
- 右上角**导入**:选一个 GKD 订阅 JSON/JSON5 文件,只合并属于该应用的规则
- 右上角**导出**:把该应用规则写成 GKD 订阅 JSON 分享
- 手动执行按钮:不走前台校验立即测试一遍(验证选择器用)
- 「+」新建规则 → 进入规则编辑页

### 9.3 规则编辑页字段对照

| 表单项 | 对应字段 | 说明 |
|---|---|---|
| 规则名 | `name` / 规则 `name` | 可选 |
| activityIds | 组 `activityIds` | 每行一个;留空 = 任意界面 |
| matches | 规则 `matches` | 每行一条,全部命中才触发;动作目标 = 最后一行 |
| anyMatches | 规则 `anyMatches` | 每行一条,任一命中 |
| 选择器片段 | — | 点按插入 matches |
| action | 规则 `action` | chips 单选 |
| actionMaximum | 规则 `actionMaximum` | 执行次数下限 1 |
| 高级参数 | 见下 | 冷却/延迟/时间窗/preKeys/exclude/position/swipeArg/resetMatch/fastQuery/matchRoot |

### 9.4 主页「远程订阅」管理

- 粘贴订阅 URL(如 `https://gkd667.vv.ax/gkd.json5`)→ 添加 → 自动拉取解析导入
- **刷新**:重新拉取该 URL,按 id 替换旧规则(订阅升级)
- **删除**:移除订阅 + 该订阅导入的全部规则
- 点订阅行**展开**:查看订阅内应用清单,未安装的灰显
- 1MB+ 大订阅的解析/落盘在后台线程,不卡界面

---

<a id="10-订阅格式"></a>
## 10. 订阅格式规范

### 10.1 顶层结构

```json5
{
  id: 667,                      // 订阅 id(数字或字符串)
  name: 'id667的GKD订阅🚀',
  version: 601,                 // 版本号,数字递增便于检查更新
  author: '👻',
  checkUpdateUrl: './gkd.version.json5',
  supportUri: 'https://github.com/…/issues/new/choose',
  categories: [                 // 分类(11 个,管理用)
    {key:0, name:'开屏广告', enable:true},
    {key:1, name:'青少年模式', enable:false}, …
  ],
  globalGroups: [ … ],          // 全局规则组(任意前台触发)
  apps: [ … ],                  // 应用规则 + 停用清单
}
```

分类惯例(key 为订阅内约定):0 开屏广告 / 1 青少年模式 / 2 更新提示 / 3 评价提示 / 4 通知提示 / 5 权限提示 / 6 局部广告 / 7 全屏广告 / 8 分段广告 / 9 功能类 / 10 其他。**组名以分类名开头**(`开屏广告-全局`、`局部广告-首页卡片`),`disableIfAppGroupMatch` 与展示都依赖这个约定。

### 10.2 apps 数组的两种条目

```json5
apps: [
  // ① 规则应用:含 groups
  {id:'com.tencent.mm', name:'微信', groups:[
    {key:0, name:'开屏广告', fastQuery:true, matchTime:10000,
     actionMaximum:1, scopeKeys:[13], actionMaximumKey:0,
     resetMatch:'app', priorityTime:10000,
     rules:[{key:0, excludeActivityIds:['.activity.ChatActivity'],
             excludeMatches:'[vid="root"]',
             matches:'TextView[text^="跳过"][text.length<=10][!(vid="title")]'}],
     order:-10},
  ]},
  // ② 停用清单:只有 id + enable(声明"该应用不跑全局规则",无规则)
  {id:'li.songe.gkd', enable:false}, …
]
```

id667 订阅实际规模:982 个规则应用、约 2100 个停用清单条目、2910 个组、3763 条规则。

### 10.3 规则元素的三种形态

```json5
rules: [
  'TextView[text="跳过"]',        // ① 裸字符串 ≡ {matches:'…'}
  ['[a]','[b]'],                  // ② 裸数组 ≡ {matches:['…','…']}
  {key:0, matches:'…', action:'click'}  // ③ 完整对象
]
```

### 10.4 三个全局组(id667 实例)

| 组 | 关键参数 | 让位 |
|---|---|---|
| 开屏广告-全局 | order:-10, fastQuery, matchTime:10000, actionMaximum:2, resetMatch:'app', forcedTime:10000, priorityTime:10000 | `disableIfAppGroupMatch:'开屏广告'` |
| 更新提示-全局 | order:-9, 同上, actionMaximum:1 | `'更新提示'` |
| 青少年模式-全局 | order:-8, 同上 | `'青少年模式'` |

全局组的 `apps` 字段是**例外清单**:`{id:'com.tencent.mm', enable:false}` 表示微信不跑此全局组(微信自己有专门规则);`enable:true` 则是额外启用的应用(11 个厂商应用商店/浏览器)。

### 10.5 JSON5 兼容性

解析器内置 JSON5 清洗:单引号、裸键、尾逗号、注释(字符串感知,不破坏内容)。所以 `https://gkd667.vv.ax/gkd.json5` 这类"纯 JSON5"订阅可直接导入。

### 10.6 合并规则

导入时远端规则 id 置为 `sub_<订阅id>_<hash>`;与现有规则表按 id 替换合并。**重复导入 = 更新**,不会重复堆积。删除订阅时其导入的规则一并删除(按 id 前缀识别)。

---

<a id="11-调试与常见问题"></a>
## 11. 调试与常见问题

### 11.1 规则没触发,怎么排查

按链条逐级查(日志页会显示走到哪一步):

1. **服务没连**:首页电源盘确认无障碍服务运行中。
2. **包名/Activity 不对**:任务页手动执行一次——手动执行跳过前台校验,能跑说明选择器没问题,是触发条件问题;检查 activityIds 是否写全(有的应用首页 Activity 是短名 `.MainActivity`)。
3. **调度参数卡住**:actionMaximum 用完没重置(resetMatch 不合适)?matchTime 窗口太短?冷却没过?→ 日志里"触发"之后没有"↳ 执行"多半是这个。
4. **选择器没命中**:检查 visibleToUser、检查 text 是否有空格差异、vid 是否带错(用 vid 而不是 id)。
5. **flutter/webview 界面**:加 `forcedTime:10000`。

### 11.2 规则触发了但没效果

- 目标不可点:改 `clickCenter`(坐标手势),或加 position。
- 弹窗是 WebView 绘制:无障碍树可能拿不到节点,加 forcedTime + 文本匹配。
- 点了又被弹回来:广告有防连点,配 `actionMaximum:2` + `actionDelay:500`。

### 11.3 误点/误触发

- 加 `excludeMatches` 排除危险界面(隐私协议、支付确认)。
- 加 `[visibleToUser=true]`、`[clickable=true]` 收紧条件。
- 用更长的关系链定位(§7.3)而不是单属性。
- 全局规则务必配 `disableIfAppGroupMatch` 让位给应用专规则。

### 11.4 常见语法错误

| 错误 | 正确 |
|---|---|
| `[a] && [b]`(没括号) | `([a] && [b])` |
| `[text="跳过"`(缺右括号) | 配对方括号 |
| `vid=com.x:id/btn`(值没引号) | `[vid="…"]` 值必须引号(单双反引号均可) |
| 关系符两侧紧贴属性(可读性差) | 段间留空格:`@A - [b]` |

---

<a id="12-与-gkd-的差异"></a>
## 12. 与 GKD 官方的差异

**兼容的部分**:订阅 JSON5 解析(JSON5 清洗/三形态/旧别名 cd、delay/matcher 两形态/key 去重)、选择器语法(全部属性/关系/逻辑/值表达式/多项式)、组与规则的全部调度字段、position/swipeArg。

**实现差异**:

| 项 | 说明 |
|---|---|
| `inputText` | 仅聚焦目标,文本注入待扩展 |
| `launchApp` | 未实现(仅日志) |
| `fastQuery` / `matchRoot` / `matchDelay` / `matchAnyApp` 等 | 声明性保留,不影响执行 |
| `swipeDir`(1/2) | 本地扩展编码,不导出 |
| 选择器失败缓存 | 省略(仅性能优化,语义一致) |
| `->` 关系 | 只回溯同一表达式内的 prev 链 |
| snapshotUrls / exampleUrls | 解析保留原文字段,不参与执行 |

---

*文档基于引擎源码(GkdTask / GkdSelector / GkdSelectorRuntime / TaskRunner / GkdSubscription)与 id667 订阅 v601(1.2MB,982 应用组)的真实写法分析编写。*
