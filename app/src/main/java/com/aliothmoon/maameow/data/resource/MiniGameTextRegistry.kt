package com.aliothmoon.maameow.data.resource

object MiniGameTextRegistry {
    const val EMPTY_TIP = "在上方选择小游戏以开始运行。"

    private val displayByKey = mapOf(
        "MiniGameNameSsStore" to "活动商店",
        "MiniGameNameGreenTicketStore" to "绿票商店",
        "MiniGameNameYellowTicketStore" to "黄票商店",
        "MiniGameNameRAStore" to "生息演算商店",
        "MiniGame@SecretFront" to "隐秘战线",
        "MiniGame@PV" to "PV-烟花筹委会",
        "MiniGame@SPA" to "卫戍协议：盟约",
        "MiniGame@OS" to "OS-喀兰贸易技术研发部",
        "MiniGame@RM-TR-1" to "次生预案 RM-TR-1",
        "MiniGame@RM-1" to "次生预案 RM-1",
        "MiniGame@AT@ConversationRoom" to "AT-相谈室",
        "MiniGame@ALL@GreenGrass" to "争锋频道：青草城",
        "MiniGame@ALL@HoneyFruit" to "争锋频道：蜜果城"
    )

    private val tipByKey = mapOf(
        "MiniGameNameSsStoreTip" to "请在活动商店页面开始。\n不买无限池。",
        "MiniGameNameGreenTicketStoreTip" to "1层全买。\n2层买寻访凭证和招聘许可。",
        "MiniGameNameYellowTicketStoreTip" to "请确保自己至少有258张黄票。",
        "MiniGameNameRAStoreTip" to "请在活动商店页面开始。",
        "MiniGame@SecretFrontTip" to "在选小队界面开始，如有存档须手动删除。\n第一次打自己看完把教程关了。\n推荐勾选游戏内 ｢继承上一支队伍发回的数据｣",
        "MiniGame@PVTip" to "从所选关卡开始并自动通关。若选择第一关，则从活动页最左侧开始；若选择其他关卡，请先进入上一关再返回以校准位置。",
        "MiniGame@SPATip" to "在活动主界面（有 ｢独立模拟｣ 处开始任务）\n手动通关 ｢标准模拟｣ 可以更快的刷分\n只能刷等级奖励，拿蚀刻章得打完所有的 ｢关键目标｣",
        "MiniGame@OSTip" to "在活动主界面（右下角有 ｢开始重建｣ 处）开始任务。",
        "MiniGame@RM-TR-1Tip" to "过完新手教程后进入前哨支点，滑动到界面最左侧。",
        "MiniGame@RM-1Tip" to "刷 RM-TR-1 大约 3 小时，营建策略中，石材开采 线点到底，\n特约任命指定维什戴尔，然后尽量精二专三，携带 1 技能，\n带满 12 个干员，其余 11 个的费用需要低于维什戴尔，\n生产模块各带 1 个，手动进入关卡并退出，\n让 RM-1 位于屏幕中央，运行 MAA。",
        "MiniGame@AT@ConversationRoomTip" to "在活动主界面（右下角有 ｢开始营业｣ 处）开始任务。",
        "MiniGame@ALL@GreenGrassTip" to "手动跳过教程对话，然后可以直接退出。\n在活动主界面（右下角有 ｢加入赛事｣ 处）开始任务。\n\n跟着鸭总喝口汤。",
        "MiniGame@ALL@HoneyFruitTip" to "手动跳过教程对话，然后可以直接退出。\n在活动主界面（右下角有 ｢加入赛事｣ 处）开始任务。\n\n跟着鸭总喝口汤。"
    )

    private val displayByValue = mapOf(
        "SS@Store@Begin" to "活动商店",
        "GreenTicket@Store@Begin" to "绿票商店",
        "YellowTicket@Store@Begin" to "黄票商店",
        "RA@Store@Begin" to "生息演算商店",
        "MiniGame@SecretFront" to "隐秘战线",
        "MiniGame@PV" to "PV-烟花筹委会",
        "MiniGame@SPA" to "卫戍协议：盟约",
        "MiniGame@OS" to "OS-喀兰贸易技术研发部",
        "MiniGame@RM-TR-1" to "次生预案 RM-TR-1",
        "MiniGame@RM-1" to "次生预案 RM-1",
        "MiniGame@AT@ConversationRoom" to "AT-相谈室",
        "MiniGame@ALL@GreenGrass" to "争锋频道：青草城",
        "MiniGame@ALL@HoneyFruit" to "争锋频道：蜜果城"
    )

    private val tipByValue = mapOf(
        "SS@Store@Begin" to "请在活动商店页面开始。\n不买无限池。",
        "GreenTicket@Store@Begin" to "1层全买。\n2层买寻访凭证和招聘许可。",
        "YellowTicket@Store@Begin" to "请确保自己至少有258张黄票。",
        "RA@Store@Begin" to "请在活动商店页面开始。",
        "MiniGame@SecretFront" to "在选小队界面开始，如有存档须手动删除。\n第一次打自己看完把教程关了。\n推荐勾选游戏内 ｢继承上一支队伍发回的数据｣",
        "MiniGame@PV" to "从所选关卡开始并自动通关。若选择第一关，则从活动页最左侧开始；若选择其他关卡，请先进入上一关再返回以校准位置。",
        "MiniGame@SPA" to "在活动主界面（有 ｢独立模拟｣ 处开始任务）\n手动通关 ｢标准模拟｣ 可以更快的刷分\n只能刷等级奖励，拿蚀刻章得打完所有的 ｢关键目标｣",
        "MiniGame@OS" to "在活动主界面（右下角有 ｢开始重建｣ 处）开始任务。",
        "MiniGame@RM-TR-1" to "过完新手教程后进入前哨支点，滑动到界面最左侧。",
        "MiniGame@RM-1" to "刷 RM-TR-1 大约 3 小时，营建策略中，石材开采 线点到底，\n特约任命指定维什戴尔，然后尽量精二专三，携带 1 技能，\n带满 12 个干员，其余 11 个的费用需要低于维什戴尔，\n生产模块各带 1 个，手动进入关卡并退出，\n让 RM-1 位于屏幕中央，运行 MAA。",
        "MiniGame@AT@ConversationRoom" to "在活动主界面（右下角有 ｢开始营业｣ 处）开始任务。",
        "MiniGame@ALL@GreenGrass" to "手动跳过教程对话，然后可以直接退出。\n在活动主界面（右下角有 ｢加入赛事｣ 处）开始任务。\n\n跟着鸭总喝口汤。",
        "MiniGame@ALL@HoneyFruit" to "手动跳过教程对话，然后可以直接退出。\n在活动主界面（右下角有 ｢加入赛事｣ 处）开始任务。\n\n跟着鸭总喝口汤。"
    )

    fun resolveDisplay(display: String?, displayKey: String?, value: String?): String {
        return when {
            !display.isNullOrBlank() -> display
            !displayKey.isNullOrBlank() -> displayByKey[displayKey] ?: value ?: displayKey
            !value.isNullOrBlank() -> displayByValue[value] ?: value
            else -> ""
        }
    }

    fun resolveTip(
        tip: String?,
        tipKey: String?,
        display: String?,
        displayKey: String?,
        value: String?
    ): String {
        val localizedTip = when {
            !tip.isNullOrBlank() -> tip
            !tipKey.isNullOrBlank() -> tipByKey[tipKey]
            !displayKey.isNullOrBlank() -> tipByKey["${displayKey}Tip"]
            !value.isNullOrBlank() -> tipByValue[value]
            else -> null
        }

        return when {
            !localizedTip.isNullOrBlank() -> localizedTip
            !display.isNullOrBlank() -> display
            else -> EMPTY_TIP
        }
    }

    fun tipForValue(value: String): String? = tipByValue[value]
}
