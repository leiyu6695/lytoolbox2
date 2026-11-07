package com.ly.toolbox.service

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * 通过"媒体按键"控制第三方音乐/视频 App。
 * 原理与耳机线控一致：向系统派发 MEDIA_PLAY_PAUSE / MEDIA_STOP，由当前活跃的 MediaSession 处理。
 * 不需要任何额外权限；极少数 App 未注册 MediaSession 时无效。
 */
object MediaController {

    fun pause(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PAUSE)

    /**
     * "停止播放"统一发 PAUSE：兼容性最好，绝大多数播放器都响应；
     * 少数只认 MEDIA_STOP 的 App 会退化为暂停，体验上等价。
     */
    fun stop(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PAUSE)

    fun playPause(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun next(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun send(context: Context, keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }
}
