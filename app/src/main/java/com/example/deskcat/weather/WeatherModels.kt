package com.example.deskcat.weather

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
    fun toPetSpeech(): String {
        return "$cityName 现在$text，${temp} 度，体感 ${feelsLike} 度，$windDir $windScale 级。${careAdvice()}"
    }

    fun careAdvice(): String {
        val temperature = temp.toIntOrNull()
        val feels = feelsLike.toIntOrNull()
        val humidityValue = humidity.toIntOrNull()
        val windLevel = windScale.filter { it.isDigit() }.toIntOrNull()
        val weatherText = text.lowercase()

        return when {
            text.contains("雨") -> "出门记得带伞，鞋子也别穿太容易湿的。"
            text.contains("雪") -> "路上可能会滑，慢一点走，小猫在家等你。"
            text.contains("雷") -> "有雷电天气，尽量少在户外久待。"
            text.contains("雾") || text.contains("霾") -> "空气不太清爽，出门可以戴个口罩。"
            windLevel != null && windLevel >= 5 -> "风有点大，外套和帽子要看紧一点。"
            feels != null && feels >= 32 -> "体感偏热，记得补水，别晒太久。"
            temperature != null && temperature <= 5 -> "外面很冷，出门多加一层衣服。"
            temperature != null && temperature in 6..14 -> "天气偏凉，薄外套会比较安心。"
            humidityValue != null && humidityValue >= 85 -> "湿度有点高，衣物可能不太容易干。"
            weatherText.contains("sunny") || text.contains("晴") -> "天气不错，适合开窗透透气。"
            text.contains("阴") || text.contains("云") -> "天气比较温和，适合稳稳地完成今天的事。"
            else -> "小猫建议你出门前再看一眼窗外。"
        }
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
