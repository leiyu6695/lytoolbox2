package com.ly.toolbox.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ly.toolbox.LyApp
import com.ly.toolbox.R
import com.ly.toolbox.data.ConfigStore
import com.ly.toolbox.data.Trigger
import com.ly.toolbox.ui.MainActivity
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 悬浮球常驻服务。
 *
 * 手势冲突处理（重点）：
 * - 手指按下后 180ms 内移动 → 判定为"滑动"，只触发绑定动作，不移动球；
 * - 按住超过 180ms 再移动 → 判定为"拖动"，球跟随手指，松手保存位置；
 * - 不动直接抬起 → 单击 / 双击；按住超过 480ms → 长按。
 * 之前任何移动都会进入拖动，导致上/下滑动作永远触发不了。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var ballView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private var singleTapRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private var dragArmRunnable: Runnable? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var gestureMode = false
    private var dragArmed = false
    private var longPressFired = false
    private var lastTapAt = 0L

    override fun onCreate() {
        super.onCreate()
        ConfigStore.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        addBall()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            applyAppearance()
            return START_STICKY
        }
        if (ballView == null) addBall()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        ballView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        ballView = null
        super.onDestroy()
    }

    private fun addBall() {
        if (ballView != null) return
        val size = dp(56f)

        val view = TextView(this).apply {
            text = "LY"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF7C4DFF.toInt(), 0xFF22D3EE.toInt())
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2f), 0x55FFFFFF)
            }
            elevation = dp(6f).toFloat()
            alpha = alphaOf(ConfigStore.ballAlpha())
        }

        params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ConfigStore.ballX()
            y = ConfigStore.ballY()
        }

        view.setOnTouchListener { v, event -> handleTouch(v, event) }
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) {
            stopSelf()
            return
        }
        ballView = view
    }

    private fun applyAppearance() {
        ballView?.alpha = alphaOf(ConfigStore.ballAlpha())
    }

    private fun alphaOf(percent: Int): Float = percent.coerceIn(20, 100) / 100f

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                gestureMode = false
                dragArmed = false
                longPressFired = false
                // 新一次按下先取消上一次尚未执行的单击，避免 300ms 边界重复触发
                singleTapRunnable?.let { handler.removeCallbacks(it) }
                scheduleLongPress()
                scheduleDragArm()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && !gestureMode && hypot(dx, dy) > slop) {
                    if (dragArmed) {
                        // 已按住足够久：开始拖动
                        dragging = true
                        cancelLongPress()
                    } else {
                        // 快速移动：判定为滑动动作，球保持不动
                        gestureMode = true
                        cancelLongPress()
                        cancelDragArm(resetScale = true)
                    }
                }
                if (dragging) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                cancelDragArm(resetScale = true)
                when {
                    dragging -> ConfigStore.setBallPos(params.x, params.y)
                    longPressFired -> Unit // 长按动作已执行，抬手不再触发
                    gestureMode -> handleSwipe(
                        event.rawX - downRawX,
                        event.rawY - downRawY
                    )

                    else -> onTap()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                cancelDragArm(resetScale = true)
                return true
            }
        }
        return false
    }

    private fun handleSwipe(dx: Float, dy: Float) {
        val threshold = dp(36f).toFloat()
        if (abs(dx) < threshold && abs(dy) < threshold) return
        if (abs(dx) > abs(dy)) {
            fire(if (dx > 0) Trigger.BALL_SWIPE_RIGHT else Trigger.BALL_SWIPE_LEFT)
        } else {
            fire(if (dy > 0) Trigger.BALL_SWIPE_DOWN else Trigger.BALL_SWIPE_UP)
        }
    }

    private fun onTap() {
        val now = SystemClock.uptimeMillis()
        if (lastTapAt > 0 && now - lastTapAt in 1L..280L) {
            lastTapAt = 0
            singleTapRunnable?.let { handler.removeCallbacks(it) }
            fire(Trigger.BALL_DOUBLE_TAP)
            return
        }
        lastTapAt = now
        val runnable = Runnable { fire(Trigger.BALL_TAP) }
        singleTapRunnable = runnable
        handler.postDelayed(runnable, 300L)
    }

    private fun scheduleLongPress() {
        val runnable = Runnable {
            longPressFired = true
            fire(Trigger.BALL_LONG_PRESS)
        }
        longPressRunnable = runnable
        handler.postDelayed(runnable, LONG_PRESS_DELAY)
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun scheduleDragArm() {
        val runnable = Runnable {
            dragArmed = true
            ballView?.animate()?.scaleX(1.12f)?.scaleY(1.12f)?.setDuration(120L)?.start()
        }
        dragArmRunnable = runnable
        handler.postDelayed(runnable, DRAG_ARM_DELAY)
    }

    private fun cancelDragArm(resetScale: Boolean) {
        dragArmRunnable?.let { handler.removeCallbacks(it) }
        dragArmRunnable = null
        dragArmed = false
        if (resetScale) {
            ballView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120L)?.start()
        }
    }

    private fun fire(trigger: Trigger) {
        haptic()
        ActionRunner.run(this, trigger)
    }

    private fun haptic() {
        runCatching {
            val vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18L)
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, LyApp.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_ly)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val DRAG_ARM_DELAY = 180L
        private const val LONG_PRESS_DELAY = 480L
        private const val ACTION_REFRESH = "com.ly.toolbox.REFRESH_OVERLAY"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, OverlayService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        /** 透明度等外观变化后，通知运行中的悬浮球立即刷新（球未开启时忽略）。 */
        fun refresh(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, OverlayService::class.java).setAction(ACTION_REFRESH)
                )
            }
        }
    }
}
