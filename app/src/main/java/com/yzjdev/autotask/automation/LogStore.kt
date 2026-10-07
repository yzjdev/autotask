package com.yzjdev.autotask.automation

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 自动化执行日志:内存环形缓冲,线程安全。
 *
 *  - TaskRunner 在任务触发/成功/失败时调用 [log] 写入
 *  - 日志页 observe 消费,UI 刷新
 *  - 不落盘:进程被杀即清空,仅用于实时观察
 */
object LogStore {

    private const val MAX_ENTRIES = 200

    /** 单条日志:时间戳 + 内容 */
    data class Entry(val timeMs: Long, val message: String) {
        private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time: String get() = fmt.format(Date(timeMs))
    }

    private val lock = Any()
    private val entries = ArrayDeque<Entry>(MAX_ENTRIES)
    private var seq = 0L
    private val listeners = ArrayList<(List<Entry>) -> Unit>()

    /** 打印开关:关闭时 log() 直接丢弃,不写入不通知 */
    @Volatile
    var enabled: Boolean = true

    /** 追加一条日志;超出容量丢弃最旧的 */
    fun log(message: String) {
        if (!enabled) return
        val snapshot: List<Entry>
        synchronized(lock) {
            entries.addLast(Entry(System.currentTimeMillis(), message))
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            seq++
            snapshot = entries.toList()
        }
        // 回调在锁外派发,避免 UI 线程重入死锁
        synchronized(listeners) { listeners.toList() }.forEach { it(snapshot) }
    }

    /** 当前全部日志(旧 → 新) */
    fun all(): List<Entry> = synchronized(lock) { entries.toList() }

    /** 清空日志 */
    fun clear() {
        val snapshot: List<Entry>
        synchronized(lock) {
            entries.clear()
            snapshot = emptyList()
        }
        synchronized(listeners) { listeners.toList() }.forEach { it(snapshot) }
    }

    /** UI 订阅:注册即回放当前内容,之后每次变更推送完整快照;返回反注册函数 */
    fun observe(listener: (List<Entry>) -> Unit): () -> Unit {
        synchronized(listeners) { listeners += listener }
        listener(all())
        return { synchronized(listeners) { listeners -= listener } }
    }

    /** 变更代数:UI 可用于 diff 判断是否需要重组 */
    fun generation(): Long = synchronized(lock) { seq }
}
