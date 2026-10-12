package com.yzjdev.autotask.automation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用图标按需加载与缓存。
 *
 * 列表只渲染可见项,图标也只在卡片进入组合时才解码:此前是切到「应用」tab 就把
 * 全部应用的图标逐包解码并常驻内存,几百个应用会同时占用 CPU 与十几 MB 位图,
 * 是列表卡顿的主因。LRU 上限 [MAX_CACHE] 条,来回滚动不会重复解码。
 */
object AppIconCache {

    private const val MAX_CACHE = 160

    /** android.util.LruCache 自身方法已同步,可跨线程读写 */
    private val cache = object : android.util.LruCache<String, ImageBitmap>(MAX_CACHE) {}

    /** 取过但失败(应用已卸载 / 资源异常)的包名:避免每次进入组合都重试一次 IPC */
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /** 命中直接返回,未命中返回 null(不阻塞);供 remember 初始值使用 */
    fun peek(pkg: String): ImageBitmap? = cache.get(pkg)

    /**
     * 取应用图标:未命中时在 [Dispatchers.IO] 取 drawable 并按 [sizePx] 绘制成位图。
     * 失败记入 [failed] 并返回 null,由 UI 显示占位图标。
     */
    suspend fun load(context: Context, pkg: String, sizePx: Int): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        if (pkg in failed) return null
        val icon = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }.getOrNull()
        }
        if (icon == null) failed += pkg else cache.put(pkg, icon)
        return icon
    }
}
