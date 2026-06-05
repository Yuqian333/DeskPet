package com.example.deskcat.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deskcat.R
import com.example.deskcat.pet.FoodItem
import com.example.deskcat.pet.PetCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FOOD_ICONS = listOf(
    R.drawable.food_kibble,
    R.drawable.food_fish,
    R.drawable.food_milk,
    R.drawable.food_can,
    R.drawable.food_cookie,
)

@Composable
fun SlotMachineGame(
    onWinFood: (FoodItem) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var slots by remember { mutableStateOf(listOf(0, 1, 2)) }
    var spinning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("拉动摇杆，三张一致即可获得食物！") }

    val shake = remember { Animatable(0f) }

    fun spin() {
        if (spinning) return
        scope.launch {
            spinning = true
            resultMessage = "摇动中..."

            // 快速滚动动画：每个 slot 随机切换多次，逐步停止
            val rounds = listOf(12, 16, 20)
            val finalSlots = List(3) { (0 until FOOD_ICONS.size).random() }
            val jobs = List(3) { idx ->
                launch {
                    repeat(rounds[idx]) { step ->
                        slots = slots.toMutableList().also { it[idx] = (0 until FOOD_ICONS.size).random() }
                        delay(60L + step * 8L)
                    }
                    // 停在最终结果
                    slots = slots.toMutableList().also { it[idx] = finalSlots[idx] }
                }
            }
            jobs.forEach { it.join() }

            spinning = false

            if (finalSlots[0] == finalSlots[1] && finalSlots[1] == finalSlots[2]) {
                val won = PetCatalog.foods[finalSlots[0]]
                resultMessage = "三连 ${won.name}！获得食物！"
                // 摇杆震动效果
                launch {
                    repeat(4) {
                        shake.animateTo(8f, tween(60))
                        shake.animateTo(-8f, tween(60))
                    }
                    shake.animateTo(0f, tween(60))
                }
                onWinFood(won)
            } else {
                resultMessage = "差一点点，再试一次！"
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF)),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "食物老虎机",
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onExit) { Text("退出") }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = resultMessage,
                color = Color(0xFF555555),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 三个食物格子
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slots.forEach { iconIdx ->
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(Color(0xFFF7F4EE), RoundedCornerShape(18.dp))
                            .border(2.dp, Color(0x33111111), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = FOOD_ICONS[iconIdx]),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(62.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 摇杆按钮
            Box(
                modifier = Modifier.graphicsLayer { translationX = shake.value },
            ) {
                Button(
                    onClick = { spin() },
                    enabled = !spinning,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                    modifier = Modifier
                        .width(160.dp)
                        .height(52.dp),
                ) {
                    Text(
                        text = if (spinning) "滚动中..." else "拉动摇杆",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "三个图案一致，即可免费获得该食物",
                color = Color(0xFF999999),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
