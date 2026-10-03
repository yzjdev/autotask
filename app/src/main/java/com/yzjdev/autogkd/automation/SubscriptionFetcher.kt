package com.yzjdev.autogkd.automation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * 订阅下载器:HTTP(S) GET 拉取订阅文本(对齐 GKD 的 updateUrl 行为)。
 *
 *  - java.net 标准库实现,不引第三方网络库(与项目零依赖风格一致)
 *  - 手动跟随重定向(最多 5 次):HttpURLConnection 默认不跨协议跟随
 *    (http→https / https→http),GKD 订阅短链常跨协议,必须手动处理
 *  - 非 2xx 抛 IOException,由调用方提示
 */
object SubscriptionFetcher {

    private const val MAX_REDIRECTS = 5

    /** 拉取订阅原文;失败抛异常(调用方 Toast 提示) */
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        var current = url
        repeat(MAX_REDIRECTS) {
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "gkd-subscription-client/1.0")
                conn.instanceFollowRedirects = false // 手动跟随,支持跨协议
                val code = conn.responseCode
                when {
                    code in 200..299 ->
                        return@withContext conn.inputStream.bufferedReader().use { it.readText() }
                    code == 301 || code == 302 || code == 303 || code == 307 || code == 308 -> {
                        val loc = conn.getHeaderField("Location")
                            ?: throw IOException("重定向缺少 Location")
                        current = URI(current).resolve(loc).toString()
                    }
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("重定向次数过多")
    }
}
