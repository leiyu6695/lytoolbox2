package com.ly.toolbox.data

/**
 * 触发方式。id 会持久化在配置里，改名时不要改 id。
 */
enum class Trigger(val id: String, val label: String, val group: String) {
    BALL_TAP("ball_tap", "悬浮球 · 单击", "悬浮球"),
    BALL_DOUBLE_TAP("ball_double_tap", "悬浮球 · 双击", "悬浮球"),
    BALL_LONG_PRESS("ball_long_press", "悬浮球 · 长按", "悬浮球"),
    BALL_SWIPE_UP("ball_swipe_up", "悬浮球 · 上滑", "悬浮球"),
    BALL_SWIPE_DOWN("ball_swipe_down", "悬浮球 · 下滑", "悬浮球"),
    BALL_SWIPE_LEFT("ball_swipe_left", "悬浮球 · 左滑", "悬浮球"),
    BALL_SWIPE_RIGHT("ball_swipe_right", "悬浮球 · 右滑", "悬浮球"),

    VOL_UP_DOUBLE("vol_up_double", "音量 + 双击", "音量键"),
    VOL_DOWN_DOUBLE("vol_down_double", "音量 − 双击", "音量键"),
    VOL_BOTH("vol_both", "音量 + 与 音量 − 同时按", "音量键"),

    SHORTCUT_1("shortcut_1", "桌面快捷方式 1", "桌面"),
    SHORTCUT_2("shortcut_2", "桌面快捷方式 2", "桌面"),
    SHORTCUT_3("shortcut_3", "桌面快捷方式 3", "桌面"),
    WIDGET("widget", "桌面小组件", "桌面");

    companion object {
        fun fromId(id: String?): Trigger = values().firstOrNull { it.id == id } ?: BALL_TAP
    }
}
