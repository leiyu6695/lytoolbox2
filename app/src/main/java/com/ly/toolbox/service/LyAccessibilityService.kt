package com.ly.toolbox.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ly.toolbox.data.ActionType
import com.ly.toolbox.data.ConfigStore
import com.ly.toolbox.data.Trigger

/**
 * 唯一需要特殊权限的组件：
 * - dispatchGesture 模拟滑动/点击（需要"无障碍服务"）
 * - performGlobalAction 返回/桌面/最近任务
 * - 过滤音量键，用于"音量键组合"触发
 * - 读取窗口内容，识别并点击广告的"跳过"按钮
 */
class LyAccessibilityService : AccessibilityService() {

    private var lastVolUpAt = 0L
    private var lastVolDownAt = 0L

    // 广告跳过状态
    private val adHandler = Handler(Looper.getMainLooper())
    private var adScanRunnable: Runnable? = null
    private var currentPackage: String? = null
    private var packageEnteredAt = 0L
    private var adSkippedForPackage = false
    private var lastAdClickAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ConfigStore.init(this)
        serviceInfo?.let { info ->
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        maybeSkipAd(event)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        adHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------- 广告跳过 ----------------

    /**
     * 只在"刚进入某个应用"的 12 秒内尝试，覆盖开屏广告的典型时长；
     * 避免在应用正常使用中误点正文里的"跳过"字样。
     */
    private fun maybeSkipAd(event: AccessibilityEvent?) {
        val e = event ?: return
        if (!ConfigStore.adSkipEnabled()) return

        val pkg = e.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == packageName) return

        if (pkg != currentPackage) {
            currentPackage = pkg
            packageEnteredAt = SystemClock.uptimeMillis()
            adSkippedForPackage = false
        }
        if (adSkippedForPackage) return
        if (!ConfigStore.isAdSkipTarget(pkg)) return
        if (SystemClock.uptimeMillis() - packageEnteredAt > AD_WINDOW_MS) return

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            scheduleAdScan()
        }
    }

    private fun scheduleAdScan() {
        adScanRunnable?.let { adHandler.removeCallbacks(it) }
        val runnable = Runnable { trySkipAd() }
        adScanRunnable = runnable
        adHandler.postDelayed(runnable, AD_SCAN_DELAY)
    }

    private fun trySkipAd() {
        val now = SystemClock.uptimeMillis()
        if (now - lastAdClickAt < AD_CLICK_COOLDOWN) return

        val root = rootInActiveWindow ?: return
        var best: AccessibilityNodeInfo? = null
        var bestLength = Int.MAX_VALUE

        for (probe in AD_PROBES) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByText(probe) }
                .getOrNull() ?: continue
            for (node in nodes) {
                val text = nodeText(node)
                if (text.isEmpty() || !AdSkipRules.matches(text)) continue
                if (!node.isVisibleToUser) continue
                if (text.length < bestLength) {
                    best = node
                    bestLength = text.length
                }
            }
        }

        val target = best ?: return
        if (clickNode(target)) {
            lastAdClickAt = now
            adSkippedForPackage = true
            ConfigStore.bumpAdSkipCount()
        }
    }

    private fun nodeText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString().orEmpty().trim()
        if (text.isNotEmpty()) return text
        return node.contentDescription?.toString().orEmpty().trim()
    }

    /**
     * 优先点击最近的 clickable 祖先；没有可点击节点时，按文字中心坐标模拟点击兜底。
     */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cursor: AccessibilityNodeInfo? = node
        var depth = 0
        while (cursor != null && depth < 6) {
            val current = cursor // 固定为 val，避免在 lambda 中捕获可变的 cursor 导致智能转换失效
            if (current.isClickable && current.isEnabled) {
                val clicked = runCatching {
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                if (clicked) return true
                break
            }
            cursor = current.parent
            depth++
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        return tapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    // ---------------- 音量键组合 ----------------

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

    // ---------------- 手势 ----------------

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
        return tapAt(metrics.widthPixels * 0.5f, metrics.heightPixels * 0.5f)
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val AD_WINDOW_MS = 12_000L
        private const val AD_SCAN_DELAY = 300L
        private const val AD_CLICK_COOLDOWN = 1_200L
        private val AD_PROBES = listOf("跳过", "skip", "Skip", "Skip Ad", "关闭广告")

        @Volatile
        var instance: LyAccessibilityService? = null

        fun isConnected(): Boolean = instance != null
    }
}
