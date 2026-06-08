package com.example.deskcat.pet

import com.example.deskcat.R

data class FoodItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val hungerDelta: Int,
    val happinessDelta: Int,
    val energyDelta: Int,
    val iconRes: Int,
    val successSpeech: String,
)

data class MiniGameItem(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int,
    val enabled: Boolean,
)

object PetCatalog {
    const val COIN_CATCH_GAME_ID = "coin_catch"
    const val SLOT_MACHINE_GAME_ID = "slot_machine"

    const val CAT_TEASER_GAME_ID = "cat_teaser"
    val foods = listOf(
        FoodItem(
            id = "kibble",
            name = "普通猫粮",
            description = "免费，饱腹 +6",
            price = 0,
            hungerDelta = 6,
            happinessDelta = 0,
            energyDelta = 0,
            iconRes = R.drawable.food_kibble,
            successSpeech = "普通猫粮也很安心，先垫垫肚子。",
        ),
        FoodItem(
            id = "fish",
            name = "小鱼干",
            description = "饱腹 +10，开心 +2",
            price = 5,
            hungerDelta = 10,
            happinessDelta = 2,
            energyDelta = 0,
            iconRes = R.drawable.food_fish,
            successSpeech = "小鱼干真香，感觉肚子暖起来了。",
        ),
        FoodItem(
            id = "milk",
            name = "牛奶",
            description = "精力 +10，饱腹 +4",
            price = 8,
            hungerDelta = 4,
            happinessDelta = 0,
            energyDelta = 10,
            iconRes = R.drawable.food_milk,
            successSpeech = "喝完牛奶，精神恢复了一点。",
        ),
        FoodItem(
            id = "can",
            name = "猫罐头",
            description = "饱腹 +25，开心 +5",
            price = 15,
            hungerDelta = 25,
            happinessDelta = 5,
            energyDelta = 0,
            iconRes = R.drawable.food_can,
            successSpeech = "罐头满分，今天被照顾得很好。",
        ),
        FoodItem(
            id = "cookie",
            name = "甜点",
            description = "开心 +12，饱腹 +6，精力 -2",
            price = 12,
            hungerDelta = 6,
            happinessDelta = 12,
            energyDelta = -2,
            iconRes = R.drawable.food_cookie,
            successSpeech = "甜甜的，快乐值正在上涨。",
        ),
    )

    val miniGames = listOf(
        MiniGameItem(
            id = COIN_CATCH_GAME_ID,
            name = "接金币",
            description = "拖动小猫接住金币，20 秒内尽量多拿奖励。",
            iconRes = R.drawable.game_coin_catch,
            enabled = true,
        ),
        MiniGameItem(
            id = CAT_TEASER_GAME_ID,
            name = "逗猫棒",
            description = "拖动逗猫棒陪小猫玩耍",
            iconRes = R.drawable.game_teaser_wand,
            enabled = true
        ),
        MiniGameItem(
            id = SLOT_MACHINE_GAME_ID,
            name = "食物老虎机",
            description = "拉动摇杆，三个图案一致即可免费获得食物。",
            iconRes = R.drawable.game_card_match,
            enabled = true,
        ),
    )
}
