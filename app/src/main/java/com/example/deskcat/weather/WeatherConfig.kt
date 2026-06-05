package com.example.deskcat.weather

class WeatherNotConfiguredException : IllegalStateException(
    "天气服务未配置，请在 local.properties 中设置 QWEATHER_API_HOST 和 QWEATHER_API_KEY。",
)
