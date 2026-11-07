package com.ly.toolbox.data

/**
 * 单个可执行动作。序列按顺序执行，[delayAfterMs] 表示执行完本动作后停顿多久。
 */
enum class ActionType(
    val label: String,
    val needsArg: Boolean = false,
    val argHint: String = "",
    val defaultArg: String = ""
) {
    SWIPE_UP("上滑 · 下一个视频（抖音/快手/Reels）"),
    SWIPE_DOWN("下滑 · 上一个视频"),
    SWIPE_LEFT("左滑"),
    SWIPE_RIGHT("右滑"),
    TAP_CENTER("点一下屏幕中心（点赞/播放）"),

    MEDIA_PLAY_PAUSE("播放 / 暂停音乐"),
    MEDIA_STOP("停止播放音乐"),
    MEDIA_NEXT("下一首"),
    MEDIA_PREV("上一首"),

    VOLUME_UP("音量 +"),
    VOLUME_DOWN("音量 −"),

    BACK("返回"),
    HOME("回到桌面"),
    RECENTS("最近任务"),

    OPEN_APP("打开指定应用（包名）", needsArg = true, argHint = "com.tencent.mm", defaultArg = ""),
    OPEN_URL("打开网页", needsArg = true, argHint = "https://", defaultArg = "https://"),
    DELAY("等待（毫秒）", needsArg = true, argHint = "300", defaultArg = "300")
}

data class ActionStep(
    var type: ActionType = ActionType.SWIPE_UP,
    var arg: String = "",
    var delayAfterMs: Int = 0
) {
    fun summary(): String {
        val base = if (type.needsArg && arg.isNotBlank()) "${type.label.split("（")[0]}：$arg" else type.label
        return if (delayAfterMs > 0) "$base（延迟 ${delayAfterMs}ms）" else base
    }
}
