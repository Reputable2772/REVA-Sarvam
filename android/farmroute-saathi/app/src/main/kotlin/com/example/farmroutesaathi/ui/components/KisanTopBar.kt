package com.example.farmroutesaathi.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.ui.theme.*

@Composable
fun KisanTopBar(
    isKannada: Boolean,
    isDarkTheme: Boolean,
    backendStatus: String,
    onLanguageToggle: () -> Unit,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientBrush = if (isDarkTheme) {
        Brush.horizontalGradient(listOf(ForestGreenDeep, Color(0xFF1B3D2B), OledBackgroundDark))
    } else {
        Brush.horizontalGradient(listOf(ForestGreenDeep, ForestGreenPrimary, FreshLeafGreen))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(gradientBrush)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = isKannada,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                    label = "TitleAnimation"
                ) { kn ->
                    Text(
                        text = if (kn) "ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ" else "FarmRoute Saathi",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isKannada) "ರೈತ-ಮೊದಲ ಲಿವಿಂಗ್ ಇಂಟೆಲಿಜೆನ್ಸ್" else "Living Intelligence Virtual FPO",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Theme Toggle Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.clickable { onThemeToggle() }
                ) {
                    Text(
                        text = if (isDarkTheme) "🌙 Dark" else "☀️ Light",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Language Toggle Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HarvestGold,
                    modifier = Modifier.clickable { onLanguageToggle() }
                ) {
                    Text(
                        text = if (isKannada) "ಕನ್ನಡ (KN)" else "English (EN)",
                        color = TextPrimaryLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Backend Connectivity & Simulated Engine Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = backendStatus,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isDarkTheme) SimulatedBadgeBgDark else SimulatedBadgeBgLight
            ) {
                Text(
                    text = "SIMULATED PROTOTYPE",
                    color = if (isDarkTheme) SimulatedBadgeFgDark else SimulatedBadgeFgLight,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
