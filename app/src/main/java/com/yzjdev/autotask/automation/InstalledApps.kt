package com.yzjdev.autotask.automation

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 已安装应用查询:应用列表页的数据来源。
 *
 * 显示名(label)要逐个加载 APK 资源,是整条链路的主要耗时,因此:
 *  - 整段查询跑在 [Dispatchers.IO],不占用 UI 线程;
 *  - 显示名按 [LABEL_CONCURRENCY] 上限并发解析,既压缩总耗时,又不把 binder 与
 *    资源加载打满(此前用 parallelStream 走 ForkJoinPool.commonPool,会和渲染抢 CPU);
 *  - 安装时间/版本号与包列表同轮带回,不做逐包二次查询。
 *
 * 不按 launcher 入口过滤:纯 widget/服务类应用同样要出现在列表中。
 * 依赖 QUERY_ALL_PACKAGES(Android 11+ package visibility)。
 */
object InstalledApps {

    /** 应用条目:列表展示所需的全部字段,一次查询带回 */
    data class AppEntry(
        val packageName: String,
        val label: String,
        val isSystem: Boolean,
        val installTime: Long,
        val version: String,
    )

    /** 显示名并发上限:过高会让资源加载与 binder 互相挤占,反而拖慢首屏 */
    private const val LABEL_CONCURRENCY = 8

    /** 中文按拼音排序(Collator):默认码点序汉字不是字典序 */
    private val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)

    /**
     * 查询全部已安装应用(用户 + 系统),按显示名拼音排序。
     * 排序放在这里(整段都在 IO 线程),UI 侧直接复用顺序 —— 否则切到应用 tab 时
     * 要在主线程用 Collator 重排几百个应用,会卡住列表首次出现的那一帧。
     */
    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        val labels = resolveLabels(pm, packages.mapNotNull { it.applicationInfo })
        packages.mapNotNull { info ->
            val ai = info.applicationInfo ?: return@mapNotNull null
            AppEntry(
                packageName = info.packageName,
                label = labels[info.packageName] ?: info.packageName,
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                installTime = info.firstInstallTime,
                version = info.versionName ?: "",
            )
        }.sortedWith(compareBy(collator) { it.label })
    }

    /** 并发解析显示名;单个失败回退包名,不因一个应用解析失败而中断整批 */
    private suspend fun resolveLabels(
        pm: PackageManager,
        apps: List<ApplicationInfo>,
    ): Map<String, String> = coroutineScope {
        val gate = Semaphore(LABEL_CONCURRENCY)
        apps.map { ai ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    ai.packageName to runCatching { pm.getApplicationLabel(ai).toString() }
                        .getOrDefault(ai.packageName)
                }
            }
        }.awaitAll().toMap()
    }
}
