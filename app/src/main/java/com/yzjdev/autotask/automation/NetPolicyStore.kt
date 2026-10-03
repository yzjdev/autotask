package com.yzjdev.autotask.automation

import android.content.Context

/**
 * 禁网策略持久化:SharedPreferences 记录 pkg → 是否禁网。
 *
 * connectivity chain-3 规则在系统重启后清空,因此以包名持久化;
 * 规则恢复由进入应用界面时的 restoreNetPolicies 执行(见 MainActivity)。
 */
object NetPolicyStore {

    private const val PREFS = "net_policy"
    private const val KEY_BLOCKED = "blocked_pkgs"

    /** 读取全部禁网包名集合 */
    fun loadBlocked(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()

    /** 更新单个包名的禁网状态(target=true 记入,false 移除) */
    fun setBlocked(context: Context, pkg: String, blocked: Boolean) {
        val cur = loadBlocked(context).toMutableSet()
        if (blocked) cur += pkg else cur -= pkg
        prefs(context).edit().putStringSet(KEY_BLOCKED, cur).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
