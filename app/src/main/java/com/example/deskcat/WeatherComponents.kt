package com.example.deskcat

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "小猫天气播报",
                        color = Color(0xFF2A2118),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "天气会同步影响宠物心情和对白",
                        color = Color(0xFF7A6652),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onAskWeather,
                        enabled = !weatherState.loading,
                        label = { OneLineText(if (weatherState.loading) "查询中" else "刷新") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFFFE0B8),
                            labelColor = Color(0xFF2A2118),
                        ),
                    )
                    AssistChip(
                        onClick = onUseDeviceLocation,
                        enabled = !weatherState.loading,
                        label = { OneLineText("定位") },
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
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "${report.cityName} · ${report.text} · ${report.temp}°C",
                                color = Color(0xFF2A2118),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "体感 ${report.feelsLike}°C，湿度 ${report.humidity}%，${report.windDir} ${report.windScale} 级",
                                color = Color(0xFF5F5144),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "小猫提醒：${report.careAdvice()}",
                                color = Color(0xFF7A4A20),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                !weatherState.hasConfiguredService -> Text(
                    text = "天气服务未配置，请在 local.properties 中设置 QWEATHER_API_HOST 和 QWEATHER_API_KEY。",
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                else -> Text(
                    text = "点击刷新，或使用定位，让小猫按当前天气给你一句提醒。",
                    color = Color(0xFF7A6652),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
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
    Surface(
        color = Color(0xFFFFF6EA),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "关注城市",
                color = Color(0xFF2A2118),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    OneLineText(if (weatherState.loading) "查询中" else "保存")
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
                        "当前使用你附近的天气"
                    } else {
                        "支持常见城市名，也可以输入和风天气 LocationID"
                    },
                    color = Color(0xFF7A6652),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = onUseDeviceLocation,
                    enabled = !weatherState.loading,
                ) {
                    OneLineText("定位")
                }
            }
        }
    }
}

@Composable
private fun OneLineText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
