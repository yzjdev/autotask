package com.yzjdev.autotask.automation

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * 规则持久化 v5(GKD 对齐):SharedPreferences + kotlinx.serialization。
 *
 *  - 密封类(Action/OnFailure)由 @SerialName 判别,GkdSelector 结构化落盘
 *  - encodeDefaults = true:默认值显式落盘,编辑器往返不丢参
 *  - 新 key(gkd_rules_v5);旧 v4 数据(automation_tasks/tasks)不读取、不迁移,直接废弃
 */
object TaskStore {

    private const val PREFS = "automation_tasks"
    private const val KEY_TASKS = "gkd_rules_v5"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 读取全部规则(按保存顺序);解码失败(含旧格式数据)返回空表 */
    fun loadAll(context: Context): List<GkdTask> {
        val raw = prefs(context).getString(KEY_TASKS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<GkdTask>>(raw)
        }.getOrDefault(emptyList())
    }

    /** 全量替换保存(规则页删除/新建/编辑后整体写回) */
    fun saveAll(context: Context, tasks: List<GkdTask>) {
        prefs(context).edit()
            .putString(KEY_TASKS, json.encodeToString(tasks))
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 查询全部已安装应用(用户 + 系统按 [includeSystem] 过滤),按应用名排序。
     * 不按 launcher 入口过滤:纯 widget/服务类应用同样要出现在列表中
     * (自动化任务可能只需匹配前台包名,不一定要能拉起)。
     * 依赖 QUERY_ALL_PACKAGES(Android 11+ package visibility)。
     * 同轮返回包名 → (firstInstallTime, versionName),均来自同一批 PackageInfo,无逐包二次查询。
     */
    fun loadInstalledApps(context: Context, includeSystem: Boolean = true): Pair<List<AppInfo>, Map<String, Pair<Long, String>>> {
        val pm = context.packageManager
        val infos = pm.getInstalledPackages(0)
        val candidates = infos.asSequence()
            .mapNotNull { it.applicationInfo }
            .filter { includeSystem || (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .toList()
        // label 解析需加载各 APK 资源,是主要耗时:并行执行
        val labels = candidates.parallelStream()
            .collect(
                { HashMap<String, String>() },
                { acc, ai -> acc[ai.packageName] = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName) },
                { a, b -> a.putAll(b) }
            )
        val apps = candidates.map { AppInfo(it.packageName, labels[it.packageName] ?: it.packageName, (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) }
            .sortedBy { it.label.lowercase() }
        val meta = infos.associate {
            it.packageName to Pair(it.firstInstallTime, it.versionName ?: "")
        }
        return apps to meta
    }

    /** 应用条目:包名 + 显示名 + 是否系统应用 */
    data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean = false)
}
