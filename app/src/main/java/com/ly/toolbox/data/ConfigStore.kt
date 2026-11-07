package com.ly.toolbox.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 极简配置存储：SharedPreferences + Gson。
 * 绑定列表整体序列化为 JSON，数据量很小（几十条以内），无需数据库。
 */
object ConfigStore {

    private const val PREFS = "ly_toolbox"
    private const val KEY_BINDINGS = "bindings"
    private const val KEY_BALL_ENABLED = "ball_enabled"
    private const val KEY_BALL_X = "ball_x"
    private const val KEY_BALL_Y = "ball_y"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun bindings(): MutableList<Binding> {
        val json = prefs.getString(KEY_BINDINGS, null) ?: return defaultBindings()
        return runCatching {
            val type = object : TypeToken<MutableList<Binding>>() {}.type
            gson.fromJson<MutableList<Binding>>(json, type) ?: defaultBindings()
        }.getOrElse { defaultBindings() }
    }

    fun saveBindings(list: List<Binding>) {
        prefs.edit().putString(KEY_BINDINGS, gson.toJson(list)).apply()
    }

    fun bindingFor(triggerId: String): Binding? =
        bindings().firstOrNull { it.enabled && it.trigger == triggerId }

    fun bindingById(id: String): Binding? = bindings().firstOrNull { it.id == id }

    fun ballEnabled(): Boolean = prefs.getBoolean(KEY_BALL_ENABLED, false)

    fun setBallEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BALL_ENABLED, enabled).apply()
    }

    fun ballX(): Int = prefs.getInt(KEY_BALL_X, 0)

    fun ballY(): Int = prefs.getInt(KEY_BALL_Y, 320)

    fun setBallPos(x: Int, y: Int) {
        prefs.edit().putInt(KEY_BALL_X, x).putInt(KEY_BALL_Y, y).apply()
    }

    /** 首次使用时给出的默认绑定，让 App 装上就能用。 */
    fun defaultBindings(): MutableList<Binding> = mutableListOf(
        Binding(
            id = "default_ball_up",
            name = "下一个视频",
            trigger = Trigger.BALL_SWIPE_UP.id,
            steps = mutableListOf(ActionStep(ActionType.SWIPE_UP))
        ),
        Binding(
            id = "default_ball_down",
            name = "上一个视频",
            trigger = Trigger.BALL_SWIPE_DOWN.id,
            steps = mutableListOf(ActionStep(ActionType.SWIPE_DOWN))
        ),
        Binding(
            id = "default_ball_tap",
            name = "播放 / 暂停",
            trigger = Trigger.BALL_TAP.id,
            steps = mutableListOf(ActionStep(ActionType.MEDIA_PLAY_PAUSE))
        ),
        Binding(
            id = "default_ball_double",
            name = "停止播放音乐",
            trigger = Trigger.BALL_DOUBLE_TAP.id,
            steps = mutableListOf(ActionStep(ActionType.MEDIA_STOP))
        ),
        Binding(
            id = "default_vol_up",
            name = "音量+双击 → 下一个视频",
            trigger = Trigger.VOL_UP_DOUBLE.id,
            steps = mutableListOf(ActionStep(ActionType.SWIPE_UP))
        ),
        Binding(
            id = "default_vol_down",
            name = "音量−双击 → 停止播放",
            trigger = Trigger.VOL_DOWN_DOUBLE.id,
            steps = mutableListOf(ActionStep(ActionType.MEDIA_STOP))
        ),
        Binding(
            id = "default_shortcut_1",
            name = "快捷方式 1 · 下一个视频",
            trigger = Trigger.SHORTCUT_1.id,
            steps = mutableListOf(ActionStep(ActionType.SWIPE_UP))
        ),
        Binding(
            id = "default_shortcut_2",
            name = "快捷方式 2 · 停止播放",
            trigger = Trigger.SHORTCUT_2.id,
            steps = mutableListOf(ActionStep(ActionType.MEDIA_STOP))
        ),
        Binding(
            id = "default_widget",
            name = "小组件 · 停止播放",
            trigger = Trigger.WIDGET.id,
            steps = mutableListOf(ActionStep(ActionType.MEDIA_STOP))
        )
    )
}
