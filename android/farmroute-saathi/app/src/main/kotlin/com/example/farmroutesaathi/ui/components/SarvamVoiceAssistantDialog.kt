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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.HarvestDeclaration
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SarvamVoiceAssistantDialog(
    isKannada: Boolean,
    onDismiss: () -> Unit,
    onNavigateAction: ((String) -> Unit)? = null
) {
    var isListening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var spokenResponse by remember { mutableStateOf<String?>(null) }
    var detectedAction by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = ForestGreenDeep,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎙️", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isKannada) "ಸರ್ವಂ ಧ್ವನಿ ಸಹಾಯಕ" else "Sarvam Voice AI Assistant",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ForestGreenDeep
                            )
                            Text(
                                text = "STT (Saaras) + TTS (Bulbul)",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Pulsing Mic Circle
                Surface(
                    shape = CircleShape,
                    color = if (isListening) HarvestGold else ForestGreenDeep,
                    shadowElevation = if (isListening) 10.dp else 4.dp,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable {
                            isListening = !isListening
                            if (isListening) {
                                isProcessing = true
                                scope.launch {
                                    val prompt = if (isKannada)
                                        "ನನ್ನ ಬಳಿ ಇಂದು ಬೆಳಿಗ್ಗೆ 7 ಗಂಟೆಗೆ 200 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ, ನಾಳೆ ಬೆಳಿಗ್ಗೆ ಮಾರಾಟ ಮಾಡಬೇಕು"
                                    else
                                        "I have 200kg tomato harvested today, planning to sell immediately"
                                    transcript = prompt

                                    val res = ApiClient.processVoiceCommand(prompt, if (isKannada) "kn-IN" else "en-IN")
                                    isProcessing = false
                                    isListening = false

                                    // Extract fields and update app state
                                    if (res.contains("\"action\":")) {
                                        val act = res.substringAfter("\"action\":\"").substringBefore("\"")
                                        detectedAction = act
                                    }
                                    if (res.contains("\"spoken_feedback\":")) {
                                        spokenResponse = res.substringAfter("\"spoken_feedback\":\"").substringBefore("\"")
                                    }

                                    // Update harvest lot parameters in UserSession
                                    val currentH = UserSession.activeHarvest ?: HarvestDeclaration()
                                    var newCrop = currentH.crop
                                    var newQty = currentH.quantityKg
                                    var newDate = currentH.harvestDate
                                    var newTime = currentH.harvestTime
                                    var newImm = currentH.sellImmediately

                                    if (res.contains("\"crop\":")) {
                                        val c = res.substringAfter("\"crop\":\"").substringBefore("\"")
                                        if (c.isNotBlank()) newCrop = c
                                    }
                                    if (res.contains("\"quantity_kg\":")) {
                                        val q = res.substringAfter("\"quantity_kg\":").substringBefore(",").substringBefore("}").trim()
                                        if (q.toDoubleOrNull() != null) newQty = q.toDouble()
                                    }
                                    if (res.contains("\"harvest_date\":")) {
                                        val hd = res.substringAfter("\"harvest_date\":\"").substringBefore("\"")
                                        if (hd.isNotBlank()) newDate = hd
                                    }
                                    if (res.contains("\"harvest_time\":")) {
                                        val ht = res.substringAfter("\"harvest_time\":\"").substringBefore("\"")
                                        if (ht.isNotBlank()) newTime = ht
                                    }
                                    if (res.contains("\"sell_immediately\":")) {
                                        val imm = res.substringAfter("\"sell_immediately\":").substringBefore(",").substringBefore("}").trim().toBoolean()
                                        newImm = imm
                                    }

                                    UserSession.setHarvest(
                                        currentH.copy(
                                            crop = newCrop,
                                            quantityKg = newQty,
                                            harvestDate = newDate,
                                            harvestTime = newTime,
                                            sellImmediately = newImm
                                        )
                                    )
                                }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isListening) "🔊" else "🎙️", fontSize = 34.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isListening)
                        (if (isKannada) "ಆಲಿಸುತ್ತಿದೆ... ಮಾತನಾಡಿ" else "Listening with Sarvam STT...")
                    else if (isProcessing)
                        (if (isKannada) "ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲಾಗುತ್ತಿದೆ..." else "Processing voice command...")
                    else
                        (if (isKannada) "ಮಾತನಾಡಲು ಮೈಕ್ ಟ್ಯಾಪ್ ಮಾಡಿ" else "Tap microphone to speak"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ForestGreenDeep
                )

                // Live Transcript Box
                if (transcript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isKannada) "🗣️ ನಿಮ್ಮ ಧ್ವನಿ:" else "🗣️ Your Speech:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"$transcript\"",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // AI Response & Spoken Confirmation
                if (spokenResponse != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ForestGreenDeep.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, ForestGreenDeep.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🤖", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ಸರ್ವಂ ಧ್ವನಿ ಉತ್ತರ:" else "Sarvam Response:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenDeep
                                )
                                Text(
                                    text = spokenResponse ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ForestGreenDeep
                                )
                            }
                        }
                    }
                }

                // Quick Voice Test Chips
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (isKannada) "ತ್ವರಿತ ಧ್ವನಿ ಪರೀಕ್ಷೆಗಳು:" else "Quick Voice Commands:",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            val prompt = if (isKannada) "ಇಂದು 200 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ" else "Harvested 200kg tomato today"
                            transcript = prompt
                            spokenResponse = if (isKannada) "✓ ಟೊಮೆಟೊ 200 ಕೆಜಿ ಕೊಯ್ಲು ನವೀಕರಿಸಲಾಗಿದೆ" else "✓ Updated 200kg Tomato harvest"
                            val cur = UserSession.activeHarvest ?: HarvestDeclaration()
                            UserSession.setHarvest(cur.copy(crop = "Tomato", quantityKg = 200.0, harvestDate = "2026-11-13"))
                        },
                        label = { Text("🍅 200kg", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            val prompt = if (isKannada) "ಕೋಲಾರದ ಮಾರುಕಟ್ಟೆ ದರ ತೋರಿಸಿ" else "Show Kolar market rates"
                            transcript = prompt
                            spokenResponse = if (isKannada) "✓ ಮಾರುಕಟ್ಟೆ ದರಗಳಿಗೆ ನ್ಯಾವಿಗೇಟ್ ಮಾಡಲಾಗುತ್ತಿದೆ" else "✓ Opening Marketplace rates"
                            onNavigateAction?.invoke("MARKETPLACE")
                        },
                        label = { Text("📊 Rates", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            val prompt = if (isKannada) "ಹವಾಮಾನ ಮುನ್ಸೂಚನೆ ತೋರಿಸಿ" else "Show weather advisory"
                            transcript = prompt
                            spokenResponse = if (isKannada) "✓ ಹವಾಮಾನ ಮುನ್ಸೂಚನೆ ತೆರೆಯಲಾಗುತ್ತಿದೆ" else "✓ Opening Weather Advisory"
                            onNavigateAction?.invoke("WEATHER")
                        },
                        label = { Text("🌦️ Weather", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isKannada) "ಮುಗಿದಿದೆ (Done)" else "Done",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
