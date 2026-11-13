package com.example.farmroutesaathi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.ui.theme.*

data class NetbackRouteData(
    val titleEn: String,
    val titleKn: String,
    val grossPerKg: Double,
    val netTakeHome: Double,
    val deductionsTextEn: String,
    val deductionsTextKn: String,
    val isRecommended: Boolean = false,
    val isWarning: Boolean = false
)

@Composable
fun AnimatedNetbackChart(
    routes: List<NetbackRouteData>,
    isKannada: Boolean,
    modifier: Modifier = Modifier
) {
    val maxNet = routes.maxOfOrNull { it.netTakeHome } ?: 3000.0

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        routes.forEach { route ->
            val progress = remember { Animatable(0f) }
            val targetRatio = (route.netTakeHome / maxNet).toFloat().coerceIn(0.1f, 1f)

            LaunchedEffect(route.netTakeHome) {
                progress.animateTo(
                    targetValue = targetRatio,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            }

            val cardBorder = when {
                route.isRecommended -> BorderStroke(1.5.dp, HarvestGold)
                route.isWarning -> BorderStroke(1.dp, SemanticWarningAmber.copy(alpha = 0.6f))
                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            }

            val barColor = when {
                route.isRecommended -> HarvestGold
                route.isWarning -> SemanticWarningAmber
                else -> FreshLeafGreen
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = cardBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = if (route.isRecommended) 3.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isKannada) route.titleKn else route.titleEn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (route.isRecommended) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = HarvestGold.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isKannada) "⭐ ಶಿಫಾರಸು" else "⭐ Recommended",
                                    color = HarvestGoldDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Animated Progress Bar showing Net Return
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.value)
                                .clip(RoundedCornerShape(5.dp))
                                .background(barColor)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isKannada) route.deductionsTextKn else route.deductionsTextEn,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Net: ₹${route.netTakeHome.toInt()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (route.isRecommended) HarvestGoldDark else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
