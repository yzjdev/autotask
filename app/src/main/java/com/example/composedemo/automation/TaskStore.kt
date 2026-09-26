package com.example.composedemo.automation

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * 任务持久化 v4:SharedPreferences + kotlinx.serialization。
 *
 *  - 密封类(Step/Trigger/OnFailure)由 @SerialName 判别,不再手写 JSON
 *  - encodeDefaults = true:默认值(Swipe.up=false、maxRounds=0 等)显式落盘,
 *    编辑器往返不丢参
 *  - 旧 key(automation_rules/rules)不读取、不删除
 */
object TaskStore {

    private const val PREFS = "automation_tasks"
    private const val KEY_TASKS = "tasks"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 读取全部任务(按保存顺序) */
    fun loadAll(context: Context): List<Task> {
        val raw = prefs(context).getString(KEY_TASKS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<Task>>(raw)
        }.getOrDefault(emptyList())
    }

    /** 全量替换保存(任务页删除/新建/编辑后整体写回) */
    fun saveAll(context: Context, tasks: List<Task>) {
        prefs(context).edit()
            .putString(KEY_TASKS, json.encodeToString(tasks))
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 查询已安装的用户应用(排除系统应用),按应用名排序。
     * 用于应用列表与新建任务时选择目标包名。
     */
    fun loadInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }  // 有入口的才可启动
            .filter { !it.packageName.startsWith("com.android.") && !it.packageName.startsWith("android") }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** 应用条目:包名 + 显示名 */
    data class AppInfo(val packageName: String, val label: String)
}
