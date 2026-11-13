package com.example.farmroutesaathi.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*

@Composable
fun KisanTopBar(
    isKannada: Boolean,
    isDarkTheme: Boolean,
    backendStatus: String,
    onLanguageToggle: () -> Unit,
    onThemeToggle: () -> Unit,
    onLogout: (() -> Unit)? = null,
    onHarvestClick: (() -> Unit)? = null,
    onVoiceAssistantClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showInternalVoiceDialog by remember { mutableStateOf(false) }

    val gradientBrush = if (isDarkTheme) {
        Brush.horizontalGradient(listOf(ForestGreenDeep, Color(0xFF163824), OledBackgroundDark))
    } else {
        Brush.horizontalGradient(listOf(ForestGreenDeep, ForestGreenPrimary, FreshLeafGreen))
    }

    val currentFarmer = UserSession.currentFarmer
    val activeHarvest = UserSession.activeHarvest
    val activeHarvestId = UserSession.activeHarvestId

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
            ),
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)
        ) {
            // Main Branding & Top-Right Interactive Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Logo Badge & Titles
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.20f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "🌱", fontSize = 18.sp)
                        }
                    }

                    Column {
                        AnimatedContent(
                            targetState = isKannada,
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                            label = "TitleAnimation"
                        ) { kn ->
                            Text(
                                text = if (kn) "ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ" else "FarmRoute Saathi",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                        }
                        Text(
                            text = if (isKannada) "ರೈತ-ಮೊದಲ ಲಿವಿಂಗ್ ಇಂಟೆಲಿಜೆನ್ಸ್" else "Living Intelligence Virtual FPO",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        )
                    }
                }

                // ==========================================
                // TOP RIGHT ACTION GROUP (HARVEST ID + VOICE + LANG + THEME)
                // ==========================================
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // UNIQUE HARVEST ID BADGE (PROMINENT TOP-RIGHT)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = HarvestGold,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, Color(0xFFB45309)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onHarvestClick?.invoke() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("🌾", fontSize = 11.sp)
                            Text(
                                text = activeHarvestId,
                                color = ForestGreenDeep,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // UNIVERSAL SARVAM STT MIC BUTTON
                    Surface(
                        shape = CircleShape,
                        color = HarvestGold,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (onVoiceAssistantClick != null) {
                                    onVoiceAssistantClick()
                                } else {
                                    showInternalVoiceDialog = true
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🎙️", fontSize = 16.sp)
                        }
                    }

                    // Theme Switch Pill
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.20f),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onThemeToggle() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isDarkTheme) "🌙" else "☀️",
                                fontSize = 14.sp
                            )
                        }
                    }

                    // High-Visibility Language Switcher Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLanguageToggle() }
                    ) {
                        Text(
                            text = if (isKannada) "KN" else "EN",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-bar: Server Status Pill & Active Harvest Lot Brief
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live backend connectivity badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.28f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF4ADE80), CircleShape)
                        )
                        Text(
                            text = backendStatus,
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Active Harvest Declaration Summary Chip
                if (activeHarvest != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                        modifier = Modifier.clickable { onHarvestClick?.invoke() }
                    ) {
                        Text(
                            text = "${activeHarvest.crop} (${activeHarvest.quantityKg.toInt()}kg) • ${if (activeHarvest.sellImmediately) "⚡ Immediate" else "❄️ Cold Storage"}",
                            color = HarvestGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "SIMULATED PROTOTYPE",
                            color = HarvestGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Farmer Profile & Logout Row (if logged in)
            if (currentFarmer != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "👤 ${currentFarmer.name} (${currentFarmer.district})",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    if (onLogout != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { onLogout() }
                        ) {
                            Text(
                                text = if (isKannada) "🚪 ನಿರ್ಗಮಿಸಿ" else "🚪 Logout",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // App-Wide Voice Assistant Dialog
    if (showInternalVoiceDialog) {
        SarvamVoiceAssistantDialog(
            isKannada = isKannada,
            onDismiss = { showInternalVoiceDialog = false }
        )
    }
}
