package com.yzjdev.autotask.notify

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 监听的消息来源:只认这两个包名 */
object MessageSources {

    const val QQ = "com.tencent.mobileqq"
    const val WECHAT = "com.tencent.mm"

    val packages = setOf(QQ, WECHAT)

    /** 展示用应用名 */
    fun label(pkg: String): String = when (pkg) {
        QQ -> "QQ"
        WECHAT -> "微信"
        else -> pkg
    }
}

/** 一条通知栏消息:只存时间戳,展示时再格式化 */
data class MessageRecord(
    /** 唯一 id,界面列表拿它当 key —— 不能用时间戳,同一毫秒的多条会撞 */
    val id: Long,
    val at: Long,
    /** 来源包名 */
    val pkg: String,
    /** 会话标题(联系人 / 群名) */
    val title: String,
    /** 消息正文 */
    val text: String,
)

/**
 * 消息记录:通知栏监听到的 QQ / 微信 消息。
 *
 * 与 [com.yzjdev.autotask.automation.log.AppLog] 一致 —— 内存有界 + 响应式状态([records]),
 * 界面订阅即可,不用注册/反注册监听器。存储沿用普通 SharedPreferences,内容经 org.json
 * 序列化(平台自带,不引入序列化库),按 [CAPACITY] 截断,避免无限增长。
 */
object MessageStore {

    private const val PREFS = "message_log"
    private const val KEY_RECORDS = "records"
    private const val CAPACITY = 300

    private val lock = Any()
    // SimpleDateFormat 非线程安全:三个格式化器共用一把锁
    private val formatLock = Any()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

    private val _records = MutableStateFlow<List<MessageRecord>>(emptyList())

    /** 当前消息(旧 → 新);界面订阅它即可自动刷新 */
    val records: StateFlow<List<MessageRecord>> = _records.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var nextId = 0L

    /** 应用启动时调用一次:回填上次记录 */
    fun init(context: Context) {
        synchronized(lock) {
            if (prefs != null) return
            val target = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = target
            val restored = runCatching { decode(target.getString(KEY_RECORDS, null)) }
                .getOrDefault(emptyList())
            _records.value = restored
            nextId = (restored.maxOfOrNull { it.id } ?: -1L) + 1
        }
    }

    /** 追加一条消息:先入内存(界面立即看到),再落盘 */
    fun add(pkg: String, title: String, text: String) {
        synchronized(lock) {
            val entry = MessageRecord(
                id = nextId++,
                at = System.currentTimeMillis(),
                pkg = pkg,
                title = title,
                text = text,
            )
            val updated = (_records.value + entry).takeLast(CAPACITY)
            _records.value = updated
            prefs?.edit()?.putString(KEY_RECORDS, encode(updated))?.apply()
        }
    }

    /** 清空内存与落盘内容 */
    fun clear() {
        synchronized(lock) {
            _records.value = emptyList()
            prefs?.edit()?.remove(KEY_RECORDS)?.apply()
        }
    }

    /** 展示用时间(MM-dd HH:mm),由界面调用,存储层不持有格式化结果 */
    fun formatTime(at: Long): String = synchronized(formatLock) { timeFormat.format(at) }

    /** 气泡内时间(HH:mm) */
    fun formatClock(at: Long): String = synchronized(formatLock) { clockFormat.format(at) }

    /** 列表用短时间:今天显示 HH:mm,更早显示 MM-dd */
    fun formatShort(at: Long): String = synchronized(formatLock) {
        val then = calendar(at)
        if (isSameDay(then, calendar(System.currentTimeMillis()))) {
            clockFormat.format(then.time)
        } else {
            dayFormat.format(then.time)
        }
    }

    /** 气泡流的日期分隔:今天 / 昨天 / MM-dd */
    fun formatDay(at: Long): String = synchronized(formatLock) {
        val target = calendar(at)
        val today = calendar(System.currentTimeMillis())
        val yesterday = calendar(System.currentTimeMillis()).apply { add(Calendar.DAY_OF_YEAR, -1) }
        when {
            isSameDay(target, today) -> "今天"
            isSameDay(target, yesterday) -> "昨天"
            else -> dayFormat.format(target.time)
        }
    }

    /** 两个时间戳是否同一天:会话流据此插日期分隔条 */
    fun isSameDay(atA: Long, atB: Long): Boolean =
        isSameDay(calendar(atA), calendar(atB))

    private fun calendar(at: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = at }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun encode(list: List<MessageRecord>): String {
        val array = JSONArray()
        list.forEach { record ->
            array.put(
                JSONObject()
                    .put("at", record.at)
                    .put("pkg", record.pkg)
                    .put("title", record.title)
                    .put("text", record.text),
            )
        }
        return array.toString()
    }

    /** 单条解析失败只跳过该条,不影响其余记录;id 只是列表 key,按顺序重编号 */
    private fun decode(raw: String?): List<MessageRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length())
            .mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                MessageRecord(
                    id = 0L,
                    at = obj.optLong("at"),
                    pkg = obj.optString("pkg"),
                    title = obj.optString("title"),
                    text = obj.optString("text"),
                )
            }
            .takeLast(CAPACITY)
            .mapIndexed { index, record -> record.copy(id = index.toLong()) }
    }
}
