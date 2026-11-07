package com.ly.toolbox.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.ly.toolbox.data.ActionType
import com.ly.toolbox.data.ConfigStore
import com.ly.toolbox.data.Trigger

/**
 * 唯一需要特殊权限的组件：
 * - dispatchGesture 模拟滑动/点击（需要"无障碍服务"）
 * - performGlobalAction 返回/桌面/最近任务
 * - 过滤音量键，用于"音量键组合"触发
 */
class LyAccessibilityService : AccessibilityService() {

    private var lastVolUpAt = 0L
    private var lastVolDownAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ConfigStore.init(this)
        serviceInfo?.let { info ->
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * 音量键组合识别。返回 false 表示不拦截，单次按键仍然正常调节音量。
     * 注意：部分 ROM 会在系统层直接消费音量键，收不到事件；悬浮球是更稳的入口。
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false

        val now = SystemClock.uptimeMillis()
        when (code) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (lastVolDownAt > 0 && now - lastVolDownAt in 0L..260L) {
                    lastVolDownAt = 0
                    ActionRunner.run(this, Trigger.VOL_BOTH)
                    return true
                }
                if (lastVolUpAt > 0 && now - lastVolUpAt in 0L..450L) {
                    lastVolUpAt = 0
                    ActionRunner.run(this, Trigger.VOL_UP_DOUBLE)
                    return true
                }
                lastVolUpAt = now
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (lastVolUpAt > 0 && now - lastVolUpAt in 0L..260L) {
                    lastVolUpAt = 0
                    ActionRunner.run(this, Trigger.VOL_BOTH)
                    return true
                }
                if (lastVolDownAt > 0 && now - lastVolDownAt in 0L..450L) {
                    lastVolDownAt = 0
                    ActionRunner.run(this, Trigger.VOL_DOWN_DOUBLE)
                    return true
                }
                lastVolDownAt = now
            }
        }
        return false
    }

    fun swipe(type: ActionType): Boolean {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val points = when (type) {
            ActionType.SWIPE_UP -> floatArrayOf(w * 0.5f, h * 0.72f, w * 0.5f, h * 0.30f)
            ActionType.SWIPE_DOWN -> floatArrayOf(w * 0.5f, h * 0.30f, w * 0.5f, h * 0.72f)
            ActionType.SWIPE_LEFT -> floatArrayOf(w * 0.82f, h * 0.5f, w * 0.18f, h * 0.5f)
            ActionType.SWIPE_RIGHT -> floatArrayOf(w * 0.18f, h * 0.5f, w * 0.82f, h * 0.5f)
            else -> return false
        }
        val path = Path().apply {
            moveTo(points[0], points[1])
            lineTo(points[2], points[3])
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 260L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun tapCenter(): Boolean {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels * 0.5f, metrics.heightPixels * 0.5f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: LyAccessibilityService? = null

        fun isConnected(): Boolean = instance != null
    }
}
