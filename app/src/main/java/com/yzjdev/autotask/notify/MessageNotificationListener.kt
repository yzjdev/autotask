package com.yzjdev.autotask.notify

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * 通知栏消息监听:只记 QQ / 微信 的通知正文,落进 [MessageStore]。
 *
 * 服务由系统按「通知使用权」绑定与解绑,应用不能自行开启 —— 主页的「消息监听」开关
 * 只负责跳系统设置页与解绑。回调跑在 binder 线程,落库走 [MessageStore] 的同步块,
 * 不在这里做文件 IO。
 */
class MessageNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName !in MessageSources.packages) return
        val notification = posted.notification ?: return
        val extras = notification.extras ?: return
        val text = extractText(extras) ?: return
        // 分组摘要("5条新消息")不是具体消息,正文在各条通知里,记它只会产生噪音
        if (isGroupSummary(text)) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        // 同一条通知会因新消息或状态变化被反复重发,短时间内完全相同的跳过
        if (!accept("${posted.packageName}\u0000$title\u0000$text")) return
        MessageStore.add(posted.packageName, title, text)
    }

    /** 正文:EXTRA_TEXT 优先,其次多行正文,最后副标题;都没有就不记 */
    private fun extractText(extras: Bundle): String? {
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** 「N条新消息」这类摘要:短且只描述条数,不是消息内容 */
    private fun isGroupSummary(text: String): Boolean =
        text.length <= 16 && text.contains("条新消息")

    /** 连续重复的同一条消息只记一次(窗口 [DEDUPE_WINDOW_MS]) */
    private fun accept(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < DEDUPE_WINDOW_MS) return false
        lastKey = key
        lastAt = now
        return true
    }

    private var lastKey: String? = null
    private var lastAt = 0L

    companion object {

        private const val DEDUPE_WINDOW_MS = 3_000L

        /** 当前绑定的服务实例;未在监听时为 null */
        @Volatile
        var instance: MessageNotificationListener? = null
            private set

        /**
         * 系统「通知使用权」里是否已勾选本应用。
         *
         * 走 [NotificationManagerCompat.getEnabledListenerPackages]:它只比对**包名**。
         * 若自己解析 `enabled_notification_listeners` 再拿类名做全等比较,系统写入的组件名
         * 写法各 ROM 并不统一(相对类名、包名与类名不同源等),会出现「已授权仍判为未授权」。
         */
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

        /** 解绑本服务(等价于在设置里取消勾选);实例不在时返回 false,由调用方退回设置页 */
        fun disable(): Boolean {
            val service = instance ?: return false
            return runCatching { service.requestUnbind() }.isSuccess
        }
    }
}
