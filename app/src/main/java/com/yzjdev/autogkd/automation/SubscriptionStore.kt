package com.yzjdev.autogkd.automation

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 远程订阅仓库:URL 列表 + 每次拉取到的订阅内容(GKD 订阅对齐)。
 *
 *  - 订阅条目含 updateUrl/checkUpdateUrl 语义:URL 即拉取地址
 *  - 内容原样落盘(解析在导入时进行,版本升级不需重新下载)
 *  - SharedPreferences 持久化,key gkd_subscriptions_v1
 */
object SubscriptionStore {

    @Serializable
    data class Subscription(
        val id: String,           // 本地唯一 id(时间戳)
        val url: String,          // 拉取地址
        val name: String,         // 订阅名(来自订阅内容 name 字段或 URL)
        val version: Long = 0L,   // 订阅版本号(内容 version 字段)
        val lastUpdate: Long = 0L,// 上次成功拉取时间
        val raw: String = "",     // 订阅原文(JSON5)
    )

    private const val PREFS = "gkd_subscriptions"
    private const val KEY_LIST = "gkd_subscriptions_v1"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun loadAll(context: Context): List<Subscription> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Subscription>>(raw) }.getOrDefault(emptyList())
    }

    fun saveAll(context: Context, subs: List<Subscription>) {
        prefs(context).edit().putString(KEY_LIST, json.encodeToString(subs)).apply()
    }

    /** upsert:按 id 替换,不存在则追加 */
    fun upsert(context: Context, sub: Subscription) {
        val cur = loadAll(context).filterNot { it.id == sub.id } + sub
        saveAll(context, cur)
    }

    fun remove(context: Context, id: String) {
        saveAll(context, loadAll(context).filterNot { it.id == id })
    }

    /** 把订阅内容解析为规则并合并进规则表(按订阅内规则 id 去重替换);parsed 可传预解析结果避免重复解析 */
    fun import(context: Context, sub: Subscription, parsed: GkdSubscription.ImportResult? = null): GkdSubscription.ImportResult {
        val result = parsed ?: GkdSubscription.parse(sub.raw)
        // 远程订阅规则打上来源标记:id 前缀 sub_<订阅id>_,重复导入时替换旧版本
        val remote = result.tasks.map { t ->
            t.copy(id = "sub_${sub.id}_${t.id.hashCode()}")
        }
        val cur = TaskStore.loadAll(context)
        val merged = cur.filterNot { existing -> remote.any { it.id == existing.id } } + remote
        TaskStore.saveAll(context, merged)
        return result
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
