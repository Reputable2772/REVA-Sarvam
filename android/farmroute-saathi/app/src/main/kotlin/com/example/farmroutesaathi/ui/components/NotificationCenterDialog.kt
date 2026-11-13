package com.example.farmroutesaathi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.farmroutesaathi.data.NotificationItem
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*

@Composable
fun NotificationCenterDialog(
    isKannada: Boolean,
    onDismiss: () -> Unit,
    onNavigate: ((String) -> Unit)? = null
) {
    val notifications = UserSession.notifications
    val isDark = UserSession.isDarkTheme

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceDarkCard else Color.White),
            border = BorderStroke(1.5.dp, if (isDark) BorderDarkSubtle else SoilDeepEarth.copy(alpha = 0.15f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Unread Count & Mark All Read
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = SoilHarvestGold.copy(alpha = 0.18f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🔔", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isKannada) "ಸೂಚನಾ ಕೇಂದ್ರ" else "Notification Center",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) TextPrimaryDark else SoilDeepEarth
                            )
                            Text(
                                text = if (isKannada)
                                    "${UserSession.unreadNotificationCount} ಓದದಿರುವ ಎಚ್ಚರಿಕೆಗಳು"
                                else
                                    "${UserSession.unreadNotificationCount} unread agricultural updates",
                                fontSize = 11.sp,
                                color = if (isDark) SoilFreshLeaf else SoilCropGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("✕", fontSize = 18.sp, color = if (isDark) TextSecondaryDark else SoilDeepEarth.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Action: Mark all as read
                if (UserSession.unreadNotificationCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = if (isKannada) "ಎಲ್ಲವನ್ನೂ ಓದಿದೆ ಎಂದು ಗುರುತಿಸಿ ✓" else "Mark all as read ✓",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoilCropGreen,
                            modifier = Modifier
                                .clickable { UserSession.markAllNotificationsAsRead() }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        )
                    }
                }

                HorizontalDivider(color = SoilDeepEarth.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))

                // Notification Items List
                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isKannada) "ಯಾವುದೇ ಹೊಸ ಸೂಚನೆಗಳಿಲ್ಲ" else "No active notifications",
                            fontSize = 13.sp,
                            color = SoilDeepEarth.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notifications) { item ->
                            NotificationCardItem(
                                item = item,
                                isKannada = isKannada,
                                onRead = { UserSession.markNotificationAsRead(item.id) },
                                onClick = {
                                    UserSession.markNotificationAsRead(item.id)
                                    if (item.actionDestination != null && onNavigate != null) {
                                        onDismiss()
                                        onNavigate(item.actionDestination)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen)
                ) {
                    Text(
                        text = if (isKannada) "ಮುಚ್ಚಿ" else "Close",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCardItem(
    item: NotificationItem,
    isKannada: Boolean,
    onRead: () -> Unit,
    onClick: () -> Unit
) {
    val isDark = UserSession.isDarkTheme

    val bgColor = when {
        item.isUrgent -> if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0)
        !item.isRead -> if (isDark) Color(0xFF1B2E20) else Color(0xFFF1F8E9)
        else -> if (isDark) SurfaceDarkCardHigh else SoilFieldPaper.copy(alpha = 0.5f)
    }

    val borderColor = when {
        item.isUrgent -> Color(0xFFFB8C00)
        !item.isRead -> if (isDark) SoilFreshLeaf else SoilCropGreen
        else -> if (isDark) BorderDarkSubtle else SoilDeepEarth.copy(alpha = 0.12f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.isUrgent) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE65100)
                        ) {
                            Text(
                                text = if (isKannada) "ತುರ್ತು" else "URGENT",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isKannada) item.titleKn else item.titleEn,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextPrimaryDark else SoilDeepEarth
                    )
                }

                if (!item.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isDark) SoilFreshLeaf else SoilCropGreen, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isKannada) item.messageKn else item.messageEn,
                fontSize = 11.5.sp,
                color = if (isDark) TextSecondaryDark else SoilDeepEarth.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.timestamp,
                    fontSize = 10.sp,
                    color = if (isDark) TextSecondaryDark.copy(alpha = 0.7f) else SoilDeepEarth.copy(alpha = 0.5f)
                )

                if (item.actionDestination != null) {
                    Text(
                        text = if (isKannada) "ವಿವರ ನೋಡಿ ➔" else "View Details ➔",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) SoilFreshLeaf else SoilCropGreen
                    )
                }
            }
        }
    }
}
