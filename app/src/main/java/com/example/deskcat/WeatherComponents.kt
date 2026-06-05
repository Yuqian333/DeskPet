package com.example.deskcat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.deskcat.weather.WeatherUiState

@Composable
fun WeatherCard(
    weatherState: WeatherUiState,
    onAskWeather: () -> Unit,
    onUseDeviceLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F0)),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "小猫天气播报",
                        color = Color(0xFF2A2118),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "让我看看今天适合怎么照顾你",
                        color = Color(0xFF7A6652),
                        fontSize = 12.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onAskWeather,
                        enabled = !weatherState.loading,
                        label = { Text(if (weatherState.loading) "闻天气" else "问天气") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFFFE0B8),
                            labelColor = Color(0xFF2A2118),
                        ),
                    )
                    AssistChip(
                        onClick = onUseDeviceLocation,
                        enabled = !weatherState.loading,
                        label = { Text("定位") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFFFF1DE),
                            labelColor = Color(0xFF2A2118),
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            val report = weatherState.report
            when {
                weatherState.errorMessage != null -> Text(
                    text = weatherState.errorMessage,
                    color = Color(0xFF8A3B2E),
                    style = MaterialTheme.typography.bodyMedium,
                )
                report != null -> {
                    Surface(
                        color = Color(0xFFFFFFFF),
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "${report.cityName} · ${report.text} · ${report.temp}°C",
                                color = Color(0xFF2A2118),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "体感 ${report.feelsLike}°C，湿度 ${report.humidity}%，${report.windDir} ${report.windScale} 级",
                                color = Color(0xFF5F5144),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "小猫提醒：${report.careAdvice()}",
                                color = Color(0xFF7A4A20),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                !weatherState.hasConfiguredService -> Text(
                    text = "天气服务未配置，请在 local.properties 中设置 QWEATHER_API_HOST 和 QWEATHER_API_KEY。",
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> Text(
                    text = "点击问天气，小猫会按当前城市给你一句贴心提醒。",
                    color = Color(0xFF7A6652),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun WeatherSettingsSection(
    weatherState: WeatherUiState,
    onCityChange: (String) -> Unit,
    onSaveCity: () -> Unit,
    onUseDeviceLocation: () -> Unit,
) {
    Spacer(modifier = Modifier.height(14.dp))
    Surface(
        color = Color(0xFFFFF6EA),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "小猫要关注哪里",
                color = Color(0xFF2A2118),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = weatherState.cityInput,
                    onValueChange = onCityChange,
                    placeholder = { Text("例如 北京 / 101010100") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFFD99B5D),
                        unfocusedBorderColor = Color(0xFFE6CBAA),
                        cursorColor = Color(0xFFD1843B),
                        focusedLabelColor = Color(0xFF7A4A20),
                        unfocusedLabelColor = Color(0xFF7A6652),
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF2A2118),
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onSaveCity,
                    enabled = !weatherState.loading,
                ) {
                    Text(if (weatherState.loading) "查询中" else "保存并查询")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (weatherState.usingDeviceLocation) {
                        "小猫正在看你附近的天气"
                    } else {
                        "支持常见城市名，也可以输入和风 LocationID"
                    },
                    color = Color(0xFF7A6652),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onUseDeviceLocation,
                    enabled = !weatherState.loading,
                ) {
                    Text("使用定位")
                }
            }
        }
    }
}
