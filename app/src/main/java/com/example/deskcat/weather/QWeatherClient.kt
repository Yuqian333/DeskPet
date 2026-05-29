package com.example.deskcat.weather

import android.content.Context
import com.example.deskcat.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class QWeatherClient(
    context: Context,
) {
    private val apiHost: String = context.getString(R.string.qweather_api_host).trim()
    private val apiKey: String = context.getString(R.string.qweather_api_key).trim()

    val isConfigured: Boolean
        get() = apiHost.isNotBlank() && apiKey.isNotBlank()

    suspend fun getWeatherNow(location: String, cityName: String): Result<WeatherReport> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConfigured()
            val json = requestJson(
                path = "/v7/weather/now",
                query = mapOf("location" to location),
            )
            val code = json.optString("code")
            if (code != "200") {
                throw IllegalStateException("天气查询失败：$code")
            }
            val now = json.getJSONObject("now")
            WeatherReport(
                cityName = cityName,
                text = now.optString("text", "未知"),
                temp = now.optString("temp", "--"),
                feelsLike = now.optString("feelsLike", "--"),
                humidity = now.optString("humidity", "--"),
                windDir = now.optString("windDir", "未知风向"),
                windScale = now.optString("windScale", "--"),
                updatedAt = json.optString("updateTime"),
            )
        }
    }

    private fun ensureConfigured() {
        if (!isConfigured) {
            throw WeatherNotConfiguredException()
        }
    }

    private fun requestJson(path: String, query: Map<String, String>): JSONObject {
        val url = URL(buildUrl(path, query))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("X-QW-Api-Key", apiKey)
        }
        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val normalizedHost = apiHost
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val encodedQuery = query.entries.joinToString("&") { (key, value) ->
            "${key.encodeUrl()}=${value.encodeUrl()}"
        }
        return "https://$normalizedHost$path?$encodedQuery"
    }

    private fun String.encodeUrl(): String {
        return URLEncoder.encode(this, "UTF-8")
    }
}
