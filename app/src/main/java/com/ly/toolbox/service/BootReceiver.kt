package com.ly.toolbox.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ly.toolbox.data.ConfigStore

/**
 * 开机后按用户选择恢复悬浮球。Android 12+ 对后台启动前台服务有限制，
 * BOOT_COMPLETED 属于系统豁免场景；失败时忽略即可，用户手动打开 App 也能恢复。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        ConfigStore.init(context)
        if (!ConfigStore.ballEnabled()) return
        if (!Settings.canDrawOverlays(context)) return
        OverlayService.start(context)
    }
}
