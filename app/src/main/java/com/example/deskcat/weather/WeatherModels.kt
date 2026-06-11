package com.example.deskcat.weather

import com.example.deskcat.PetMood
import com.example.deskcat.settings.DEFAULT_PET_NAME
import kotlin.math.abs

enum class WeatherKind {
    Sunny,
    Cloudy,
    Rainy,
    Snowy,
    Thunderstorm,
    Hazy,
    Other,
}

data class WeatherReport(
    val cityName: String,
    val text: String,
    val temp: String,
    val feelsLike: String,
    val humidity: String,
    val windDir: String,
    val windScale: String,
    val updatedAt: String,
) {
    fun toPetSpeech(petName: String = DEFAULT_PET_NAME): String {
        return "$cityName 现在$text，${temp}度，体感${feelsLike}度，$windDir $windScale 级。${careAdvice(petName)}"
    }

    fun petMood(): PetMood {
        return when {
            kind() == WeatherKind.Thunderstorm -> PetMood.Sleepy
            kind() == WeatherKind.Rainy || kind() == WeatherKind.Snowy -> PetMood.Sleepy
            isTooHot() || isTooCold() || isWindy() || kind() == WeatherKind.Hazy -> PetMood.Chill
            kind() == WeatherKind.Sunny && isComfortable() -> PetMood.Happy
            else -> PetMood.Chill
        }
    }

    fun careAdvice(petName: String = DEFAULT_PET_NAME): String {
        val subject = petName.ifBlank { DEFAULT_PET_NAME }
        return when {
            kind() == WeatherKind.Thunderstorm -> "外面有雷雨，先离窗边远一点，${subject}在桌面这边陪你等它过去。"
            kind() == WeatherKind.Snowy -> "路面可能会滑，出门放慢一点，${subject}在家给你留一小团暖意。"
            kind() == WeatherKind.Rainy -> "记得带伞，鞋子也别穿太容易湿的；回来的时候，${subject}还在这里等你。"
            kind() == WeatherKind.Hazy -> "空气不太清爽，出门可以戴个口罩，回来开窗前也先看看空气情况。"
            isWindy() -> "风有点大，外套和帽子要看紧一点，别让风把你的好心情吹跑。"
            isTooHot() -> "体感偏热，记得补水，别在太阳下待太久；${subject}会在阴凉处给你加油。"
            isTooCold() -> "外面偏冷，出门多加一层衣服，暖暖和和地回来。"
            isHumid() -> "湿度有点高，衣物可能不太容易干，今天就把节奏放轻一点。"
            kind() == WeatherKind.Sunny && isComfortable() -> "天气不错，适合开窗透透气，也适合让${subject}晒会儿太阳。"
            kind() == WeatherKind.Cloudy -> "天气比较温和，适合稳稳地完成今天的事；不用急，一点点来。"
            else -> "出门前再看一眼窗外，${subject}帮你盯着天气，也帮你留住一点安心。"
        }
    }

    fun specialSpeechComparedTo(previous: WeatherReport?, petName: String = DEFAULT_PET_NAME): String? {
        previous ?: return null
        val previousKind = previous.kind()
        val currentKind = kind()
        val subject = petName.ifBlank { DEFAULT_PET_NAME }
        if (previousKind != currentKind) {
            return when {
                currentKind == WeatherKind.Thunderstorm -> "天气变成雷雨了，先离窗边远一点，${subject}陪你等它过去。"
                currentKind == WeatherKind.Rainy -> "天气转雨了，伞要放到顺手的地方，别被突然的小雨偷袭。"
                currentKind == WeatherKind.Snowy -> "开始下雪了，路面可能会滑，出门慢一点，像轻轻踩在云上。"
                currentKind == WeatherKind.Hazy -> "空气变差了，出门记得戴口罩，${subject}把清爽空气先存在愿望清单里。"
                currentKind == WeatherKind.Sunny && previousKind == WeatherKind.Rainy -> "雨停转晴了，窗外应该会亮一些，${subject}心情也跟着好了。"
                currentKind == WeatherKind.Sunny -> "天气转晴了，适合开窗透透气，也适合把心情晾一晾。"
                currentKind == WeatherKind.Cloudy -> "天气转阴了，节奏放慢一点也没关系，${subject}会陪你稳稳过完今天。"
                else -> null
            }
        }

        val previousTemp = previous.temp.toIntOrNull()
        val currentTemp = temp.toIntOrNull()
        if (previousTemp != null && currentTemp != null && abs(currentTemp - previousTemp) >= 5) {
            return if (currentTemp > previousTemp) {
                "气温升得有点明显，记得补水，别让自己太热，${subject}会提醒你慢一点。"
            } else {
                "气温降了不少，外出多加一层会更稳妥，暖一点总没错。"
            }
        }

        if ((previous.windLevel() ?: 0) < 5 && (windLevel() ?: 0) >= 5) {
            return "风力变大了，外套和帽子要看紧一点，${subject}帮你守住桌面。"
        }
        return null
    }

    fun kind(): WeatherKind {
        return when {
            hasAny("雷", "thunder", "storm") -> WeatherKind.Thunderstorm
            hasAny("雪", "snow") -> WeatherKind.Snowy
            hasAny("雨", "rain", "shower", "drizzle") -> WeatherKind.Rainy
            hasAny("霾", "雾", "haze", "fog", "smog") -> WeatherKind.Hazy
            hasAny("晴", "sunny", "clear") -> WeatherKind.Sunny
            hasAny("云", "阴", "cloud", "overcast") -> WeatherKind.Cloudy
            else -> WeatherKind.Other
        }
    }

    fun windLevel(): Int? = windScale.filter { it.isDigit() }.toIntOrNull()

    private fun isWindy(): Boolean = (windLevel() ?: 0) >= 5

    private fun isTooHot(): Boolean {
        val temperature = temp.toIntOrNull()
        val feels = feelsLike.toIntOrNull()
        return (feels != null && feels >= 32) || (temperature != null && temperature >= 34)
    }

    private fun isTooCold(): Boolean {
        val temperature = temp.toIntOrNull()
        val feels = feelsLike.toIntOrNull()
        return (feels != null && feels <= 3) || (temperature != null && temperature <= 5)
    }

    private fun isHumid(): Boolean {
        return humidity.toIntOrNull()?.let { it >= 85 } == true
    }

    private fun isComfortable(): Boolean {
        val temperature = temp.toIntOrNull() ?: return false
        return temperature in 16..28 && !isWindy() && kind() != WeatherKind.Hazy
    }

    private fun hasAny(vararg keywords: String): Boolean {
        return keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
    }
}

data class WeatherLocation(
    val id: String,
    val name: String,
    val adm1: String,
    val adm2: String,
)

data class WeatherUiState(
    val cityInput: String = DEFAULT_CITY,
    val loading: Boolean = false,
    val usingDeviceLocation: Boolean = false,
    val serviceConfigured: Boolean = false,
    val report: WeatherReport? = null,
    val errorMessage: String? = null,
) {
    val hasConfiguredService: Boolean
        get() = serviceConfigured
}

const val DEFAULT_CITY = "北京"
