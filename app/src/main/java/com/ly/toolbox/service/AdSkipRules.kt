package com.ly.toolbox.service

/**
 * 广告跳过按钮的文字规则。
 *
 * 只认"像按钮"的短文本，避免把文章标题（如"跳过广告的5种方法"）误判成按钮：
 * - 跳过 / 跳过广告 / 跳过 5 / 跳过5s / 跳过 5 秒
 * - Skip / Skip Ad / Skip 5
 * - 关闭广告
 */
object AdSkipRules {

    private val skipPattern = Regex(
        "^(跳过|skip)\\s*(广告|ad)?[\\s\\dSs秒>·.、|｜]*$",
        RegexOption.IGNORE_CASE
    )

    private val closePattern = Regex("^关闭广告$")

    fun matches(rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty() || text.length > 10) return false
        return skipPattern.matches(text) || closePattern.matches(text)
    }
}
