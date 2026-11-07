package com.ly.toolbox.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.ly.toolbox.data.ActionStep
import com.ly.toolbox.data.ActionType
import com.ly.toolbox.data.ConfigStore
import com.ly.toolbox.data.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 动作执行引擎：把一条"动作序列"跑完。
 * 所有入口（悬浮球、音量键、桌面快捷键、小组件）最终都汇聚到这里。
 */
object ActionRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running: Job? = null
    private var lastToastAt = 0L

    fun run(context: Context, trigger: Trigger) {
        val app = context.applicationContext
        ConfigStore.init(app)
        val binding = ConfigStore.bindingFor(trigger.id)
        if (binding == null) {
            toast(app, "「${trigger.label}」还没有绑定动作")
            return
        }
        if (binding.steps.isEmpty()) {
            toast(app, "这条快捷键还没有设置动作")
            return
        }
        running?.cancel()
        running = scope.launch { execute(app, binding.steps) }
    }

    private suspend fun execute(context: Context, steps: List<ActionStep>) {
        for (step in steps) {
            if (!perform(context, step)) return
            if (step.delayAfterMs > 0) delay(step.delayAfterMs.toLong())
        }
    }

    private suspend fun perform(context: Context, step: ActionStep): Boolean {
        val service = LyAccessibilityService.instance
        return when (step.type) {
            ActionType.SWIPE_UP,
            ActionType.SWIPE_DOWN,
            ActionType.SWIPE_LEFT,
            ActionType.SWIPE_RIGHT -> {
                if (service == null) {
                    needAccessibility(context)
                    false
                } else {
                    val dispatched = service.swipe(step.type)
                    if (dispatched) delay(280L)
                    dispatched
                }
            }

            ActionType.TAP_CENTER -> {
                if (service == null) {
                    needAccessibility(context)
                    false
                } else {
                    val dispatched = service.tapCenter()
                    if (dispatched) delay(80L)
                    dispatched
                }
            }

            ActionType.MEDIA_PLAY_PAUSE -> {
                MediaController.playPause(context)
                true
            }

            ActionType.MEDIA_STOP -> {
                MediaController.stop(context)
                true
            }

            ActionType.MEDIA_NEXT -> {
                MediaController.next(context)
                true
            }

            ActionType.MEDIA_PREV -> {
                MediaController.previous(context)
                true
            }

            ActionType.VOLUME_UP -> {
                adjustVolume(context, AudioManager.ADJUST_RAISE)
                true
            }

            ActionType.VOLUME_DOWN -> {
                adjustVolume(context, AudioManager.ADJUST_LOWER)
                true
            }

            ActionType.BACK -> globalAction(context, service, AccessibilityService.GLOBAL_ACTION_BACK)
            ActionType.HOME -> globalAction(context, service, AccessibilityService.GLOBAL_ACTION_HOME)
            ActionType.RECENTS -> globalAction(context, service, AccessibilityService.GLOBAL_ACTION_RECENTS)

            ActionType.OPEN_APP -> openApp(context, step.arg)
            ActionType.OPEN_URL -> openUrl(context, step.arg)
            ActionType.DELAY -> {
                delay((step.arg.toLongOrNull() ?: 300L).coerceIn(0L, 60_000L))
                true
            }
        }
    }

    private fun globalAction(
        context: Context,
        service: LyAccessibilityService?,
        action: Int
    ): Boolean {
        if (service == null) {
            needAccessibility(context)
            return false
        }
        return service.performGlobalAction(action)
    }

    private fun adjustVolume(context: Context, direction: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun openApp(context: Context, arg: String): Boolean {
        val pkg = arg.trim()
        if (pkg.isBlank()) {
            toast(context, "请先填写应用包名")
            return false
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast(context, "没有找到应用：$pkg")
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun openUrl(context: Context, arg: String): Boolean {
        val url = arg.trim()
        if (!url.startsWith("http", ignoreCase = true)) {
            toast(context, "链接需要以 http/https 开头")
            return false
        }
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    private fun needAccessibility(context: Context) {
        toast(context, "请先开启「LY工具箱」的无障碍服务")
    }

    private fun toast(context: Context, message: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastToastAt < 1200) return
        lastToastAt = now
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
