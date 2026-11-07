package com.ly.toolbox.data

data class Binding(
    var id: String = "",
    var name: String = "",
    var trigger: String = Trigger.BALL_TAP.id,
    var enabled: Boolean = true,
    var steps: MutableList<ActionStep> = mutableListOf()
)
