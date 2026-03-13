package com.aliothmoon.maameow.data.resource

/**
 * 默认小游戏列表
 */
object DefaultMiniGames {
    data class DefaultMiniGameEntry(
        val display: String,
        val value: String,
        val tip: String? = null
    )

    val ENTRIES = listOf(
        DefaultMiniGameEntry(
            "活动商店",
            "SS@Store@Begin",
            "请在活动商店页面开始。\n不买无限池。"
        ),
        DefaultMiniGameEntry(
            "绿票商店",
            "GreenTicket@Store@Begin",
            "1层全买。\n2层买寻访凭证和招聘许可。"
        ),
        DefaultMiniGameEntry(
            "黄票商店",
            "YellowTicket@Store@Begin",
            "请确保自己至少有258张黄票。"
        ),
        DefaultMiniGameEntry(
            "生息演算商店",
            "RA@Store@Begin",
            "请在活动商店页面开始。"
        ),
        DefaultMiniGameEntry(
            "隐秘战线",
            "MiniGame@SecretFront",
            "在选小队界面开始，如有存档须手动删除。\n第一次打自己看完把教程关了。\n推荐勾选游戏内 ｢继承上一支队伍发回的数据｣"
        )
    )
}
