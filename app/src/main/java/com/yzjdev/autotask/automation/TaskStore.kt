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
     */
    fun loadInstalledApps(context: Context, includeSystem: Boolean = true): List<AppInfo> {
        val pm = context.packageManager
        return pm.getInstalledPackages(0)
            .asSequence()
            .mapNotNull { it.applicationInfo }
            .filter { includeSystem || (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString(), (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** 应用条目:包名 + 显示名 + 是否系统应用 */
    data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean = false)
}
