package com.example.deskcat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DesktopActionPanel(
    onPet: () -> Unit,
    onFeed: () -> Unit,
    onPlay: () -> Unit,
    onRest: () -> Unit,
    onReset: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF)),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "互动操作",
                color = Color(0xFF111111),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DesktopActionChip(
                    text = "摸摸",
                    onClick = onPet,
                    containerColor = Color(0xFFF1E7D7),
                    modifier = Modifier.weight(1f),
                )
                DesktopActionChip(
                    text = "喂食",
                    onClick = onFeed,
                    containerColor = Color(0xFFE8E2D8),
                    modifier = Modifier.weight(1f),
                )
                DesktopActionChip(
                    text = "玩耍",
                    onClick = onPlay,
                    containerColor = Color(0xFFF7F4EE),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DesktopActionChip(
                    text = "休息",
                    onClick = onRest,
                    containerColor = Color(0xFFE6E1DA),
                    modifier = Modifier.weight(1f),
                )
                DesktopActionChip(
                    text = "归位",
                    onClick = onReset,
                    containerColor = Color(0xFFF0EEE9),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun DesktopActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = Color(0xFF111111),
        ),
    )
}
