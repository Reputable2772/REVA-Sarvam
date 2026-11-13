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
import com.example.farmroutesaathi.data.AppLocalization
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*

@Composable
fun KisanTopBar(
    isKannada: Boolean,
    isDarkTheme: Boolean,
    backendStatus: String = "",
    onLanguageToggle: () -> Unit,
    onThemeToggle: () -> Unit,
    onLogout: (() -> Unit)? = null,
    onHarvestClick: (() -> Unit)? = null,
    onVoiceAssistantClick: (() -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showInternalVoiceDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val gradientBrush = if (isDarkTheme) {
        Brush.horizontalGradient(listOf(SoilDeepEarth, Color(0xFF1F2F26), OledBackgroundDark))
    } else {
        Brush.horizontalGradient(listOf(SoilDeepEarth, SoilCropGreen, ForestGreenPrimary))
    }

    val currentFarmer = UserSession.currentFarmer
    val activeHarvestId = UserSession.activeHarvestId
    val unreadCount = UserSession.unreadNotificationCount

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            ),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)
        ) {
            // ================================================================
            // ROW 1: BRANDING (LEFT) & INTERACTIVE CONTROLS (RIGHT)
            // ================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Agricultural Brand & Tagline
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
                            Text(text = "🌾", fontSize = 20.sp)
                        }
                    }

                    Column {
                        Text(
                            text = AppLocalization.t("app_name"),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = AppLocalization.t("tagline"),
                            style = MaterialTheme.typography.bodySmall,
                            color = SoilHarvestGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right: Action Group (Notification + Voice + Theme + Language)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Notification Center Bell with Badge
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { showNotificationDialog = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🔔", fontSize = 15.sp)
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 2.dp, end = 2.dp)
                                        .size(14.dp)
                                        .background(Color(0xFFE53935), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // 2. Voice Assistant Shortcut
                    Surface(
                        shape = CircleShape,
                        color = SoilHarvestGold,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .size(36.dp)
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

                    // 3. Theme Toggle Button
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .size(36.dp)
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

                    // 4. Dynamic Language Selector Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showLanguageDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text("🌐 ", fontSize = 10.sp)
                            Text(
                                text = UserSession.selectedLanguage.code.uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ================================================================
            // ROW 2: FARMER IDENTITY, ACTIVE HARVEST ID & LOGOUT ACTION
            // ================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Farmer Name & Location + Harvest ID Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Farmer Name Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (currentFarmer != null)
                                "👤 ${currentFarmer.name} (${currentFarmer.district})"
                            else
                                "👤 Farmer Hub",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Active Harvest ID Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SoilHarvestGold,
                        border = BorderStroke(1.dp, Color(0xFFB45309)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onHarvestClick?.invoke() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🌾", fontSize = 10.sp)
                            Text(
                                text = activeHarvestId,
                                color = SoilDeepEarth,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Right: Logout Button
                if (onLogout != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.55f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onLogout() }
                    ) {
                        Text(
                            text = "🚪 " + AppLocalization.t("logout"),
                            fontSize = 10.5.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Dynamic Language Selection Dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { lang ->
                UserSession.setLanguage(lang)
                showLanguageDialog = false
                onLanguageToggle()
            }
        )
    }

    // Notification Center Dialog
    if (showNotificationDialog) {
        NotificationCenterDialog(
            isKannada = isKannada,
            onDismiss = { showNotificationDialog = false },
            onNavigate = onNavigate
        )
    }

    // App-Wide Voice Assistant Dialog
    if (showInternalVoiceDialog) {
        SarvamVoiceAssistantDialog(
            isKannada = isKannada,
            onDismiss = { showInternalVoiceDialog = false }
        )
    }
}
