package com.yzjdev.autotask.automation.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 日志级别:决定着色与筛选 */
enum class LogLevel(val label: String) {
    Info("信息"),
    Success("成功"),
    Warn("警告"),
    Error("错误"),
}

/** 日志来源:固定枚举,筛选与统计都靠它(不再用自由文本当标签) */
enum class LogSource(val label: String) {
    Service("服务"),
    Overlay("悬浮窗"),
    Network("禁网"),
    Permission("权限"),
}

/** 一条日志:只存时间戳,展示时再格式化,避免存储层关心展示格式 */
data class LogRecord(
    /** 唯一 id,界面列表拿它当 key —— 不能用时间戳,同一毫秒的多条会撞 */
    val id: Long,
    val at: Long,
    val level: LogLevel,
    val source: LogSource,
    val message: String,
)

/**
 * 应用日志。
 *
 * 设计要点(与纯内存环形缓冲 + 手工监听器的做法不同):
 *  - **落盘**:写入 `filesDir/app.log`,进程被杀后重启仍能看到上次的日志;
 *  - **不阻塞调用方**:写入经 [Channel] 交给后台单协程顺序落盘,
 *    调用方可能是无障碍事件线程,不能被文件 IO 拖住;
 *  - **有界**:内存只保留最近 [MEMORY_CAPACITY] 条,启动时文件超限也按同一上限裁剪;
 *  - **响应式**:状态用 [StateFlow] 暴露,界面订阅即可,无需注册/反注册监听器。
 */
object AppLog {

    private const val MEMORY_CAPACITY = 500
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val FILE_NAME = "app.log"
    private const val FIELD_SEP = '|'

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = Channel<String>(Channel.UNLIMITED)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    // SimpleDateFormat 非线程安全:两个格式化器共用一把锁
    private val formatLock = Any()
    private val lock = Any()

    private val _records = MutableStateFlow<List<LogRecord>>(emptyList())

    /** 当前日志(旧 → 新);界面订阅它即可自动刷新 */
    val records: StateFlow<List<LogRecord>> = _records.asStateFlow()

    /** 记录开关:关闭时写入直接丢弃(不落盘、不入内存) */
    @Volatile
    var enabled: Boolean = true

    private var nextId = 0L
    private var file: File? = null
    private var writerStarted = false

    /** 应用启动时调用一次:定位日志文件、回填上次日志、启动后台写入 */
    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val target = File(context.filesDir, FILE_NAME)
            file = target
            val restored = runCatching { readTail(target) }.getOrDefault(emptyList())
            _records.value = restored
            nextId = (restored.maxOfOrNull { it.id } ?: -1L) + 1
        }
        if (!writerStarted) {
            writerStarted = true
            scope.launch {
                for (line in pending) {
                    val target = file ?: continue
                    runCatching { target.appendText(line + "\n") }
                }
            }
        }
    }

    fun info(source: LogSource, message: String) = record(LogLevel.Info, source, message)

    fun success(source: LogSource, message: String) = record(LogLevel.Success, source, message)

    fun warn(source: LogSource, message: String) = record(LogLevel.Warn, source, message)

    fun error(source: LogSource, message: String) = record(LogLevel.Error, source, message)

    /** 追加一条日志:先入内存(界面立即看到),再排队落盘 */
    fun record(level: LogLevel, source: LogSource, message: String) {
        if (!enabled) return
        val entry: LogRecord
        synchronized(lock) {
            entry = LogRecord(
                id = nextId++,
                at = System.currentTimeMillis(),
                level = level,
                source = source,
                message = message,
            )
            _records.value = (_records.value + entry).takeLast(MEMORY_CAPACITY)
        }
        // 未初始化(没有文件)时只在内存里留着,不影响调用方
        if (file != null) pending.trySend(encode(entry))
    }

    /** 清空内存与文件 */
    fun clear() {
        synchronized(lock) { _records.value = emptyList() }
        val target = file
        if (target != null) scope.launch { runCatching { target.writeText("") } }
    }

    /** 展示用时间(HH:mm:ss),由界面调用,避免存储层持有格式化结果 */
    fun formatTime(at: Long): String = synchronized(formatLock) { timeFormat.format(at) }

    /** 日志流的日期分隔:今天 / 昨天 / MM-dd */
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

    /** 两个时间戳是否同一天:日志流据此插日期分隔条 */
    fun isSameDay(atA: Long, atB: Long): Boolean = isSameDay(calendar(atA), calendar(atB))

    private fun calendar(at: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = at }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun encode(entry: LogRecord): String = buildString {
        append(entry.at).append(FIELD_SEP)
        append(entry.level.name).append(FIELD_SEP)
        append(entry.source.name).append(FIELD_SEP)
        append(entry.message.replace('\n', ' '))
    }

    /** 读文件尾部并解析;单行解析失败只跳过该行,不影响其余日志 */
    private fun readTail(target: File): List<LogRecord> {
        if (!target.isFile) return emptyList()
        val lines = target.readLines().takeLast(MEMORY_CAPACITY)
        return lines.mapNotNull { line ->
            val parts = line.split(FIELD_SEP, limit = 4)
            if (parts.size < 4) return@mapNotNull null
            val at = parts[0].toLongOrNull() ?: return@mapNotNull null
            val level = LogLevel.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            val source = LogSource.entries.firstOrNull { it.name == parts[2] } ?: return@mapNotNull null
            LogRecord(id = 0L, at = at, level = level, source = source, message = parts[3])
        }.takeLast(MEMORY_CAPACITY)
            // id 只是列表 key,必须唯一:时间戳会撞(同一毫秒的多条),按顺序重编号
            .mapIndexed { index, record -> record.copy(id = index.toLong()) }
    }

    /** 启动时裁剪超限文件,避免日志无限增长 */
    fun trimIfNeeded() {
        val target = file ?: return
        if (target.length() <= MAX_FILE_BYTES) return
        val tail = synchronized(lock) { _records.value }
        scope.launch {
            runCatching {
                target.writeText(tail.joinToString(separator = "\n", postfix = "\n") { encode(it) })
            }
        }
    }
}
