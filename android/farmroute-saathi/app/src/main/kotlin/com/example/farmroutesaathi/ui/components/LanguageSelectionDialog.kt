package com.example.farmroutesaathi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.farmroutesaathi.data.SupportedLanguage
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*

@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit,
    onLanguageSelected: (SupportedLanguage) -> Unit
) {
    val languages = UserSession.supportedLanguages
    var currentSelectedCode by remember { mutableStateOf(UserSession.selectedLanguageCode) }
    val isKn = UserSession.isKannadaPreferred

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, SoilDeepEarth.copy(alpha = 0.15f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SoilCropGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🌐", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isKn) "ಭಾಷೆಯನ್ನು ಆಯ್ಕೆಮಾಡಿ" else "Select Language",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoilDeepEarth
                            )
                            Text(
                                text = if (isKn) "ಸರ್ವಂ ಧ್ವನಿ ಮತ್ತು ಇಂಟರ್ಫೇಸ್" else "Sarvam Voice & Interface",
                                fontSize = 12.sp,
                                color = SoilCropGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("✕", fontSize = 18.sp, color = SoilDeepEarth.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SoilDeepEarth.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                // Language List with Radio Selection
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(languages) { lang ->
                        val isSelected = lang.code == currentSelectedCode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) SoilCropGreen.copy(alpha = 0.10f) else SoilFieldPaper.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = if (isSelected) 1.8.dp else 1.dp,
                                color = if (isSelected) SoilCropGreen else SoilDeepEarth.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelectedCode = lang.code
                                    UserSession.selectedLanguageCode = lang.code
                                    onLanguageSelected(lang)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = lang.nativeName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) SoilCropGreen else SoilDeepEarth
                                    )
                                    Text(
                                        text = "${lang.englishName} (${lang.sarvamCode})",
                                        fontSize = 12.sp,
                                        color = SoilDeepEarth.copy(alpha = 0.65f)
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        currentSelectedCode = lang.code
                                        UserSession.selectedLanguageCode = lang.code
                                        onLanguageSelected(lang)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = SoilCropGreen,
                                        unselectedColor = SoilDeepEarth.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Confirmation Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen)
                ) {
                    Text(
                        text = if (isKn) "ದೃಢೀಕರಿಸಿ" else "Apply Language",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
