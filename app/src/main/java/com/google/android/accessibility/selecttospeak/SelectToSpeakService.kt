package com.google.android.accessibility.selecttospeak

/**
 * 空壳无障碍服务:继承自业务服务 [com.yzjdev.autotask.automation.DramaAccessibilityService]。
 *
 * 使用 com.google.android.accessibility.selecttospeak 包名与类名:
 * 部分应用(如微信)会校验无障碍服务的包名/类名,仅放行系统级服务
 * (如 TalkBack / Select to Speak),manifest 中注册此类可绕过这类检测。
 * 业务逻辑全部在父类中,这里只负责以系统级服务名出现在无障碍设置列表里。
 */
open class SelectToSpeakService : com.yzjdev.autotask.automation.DramaAccessibilityService()
