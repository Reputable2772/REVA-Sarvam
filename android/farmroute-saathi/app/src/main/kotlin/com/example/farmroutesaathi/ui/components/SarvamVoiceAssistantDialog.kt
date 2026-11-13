package com.example.farmroutesaathi.ui.components

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.farmroutesaathi.audio.VoiceRecorder
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.HarvestDeclaration
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SarvamVoiceAssistantDialog(
    isKannada: Boolean,
    onDismiss: () -> Unit,
    onNavigateAction: ((String) -> Unit)? = null
) {
    var isRecordingAudio by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var audioFileName by remember { mutableStateOf<String?>(null) }
    var transcript by remember { mutableStateOf("") }
    var spokenResponse by remember { mutableStateOf<String?>(null) }
    var detectedAction by remember { mutableStateOf<String?>(null) }
    var manualVoiceInput by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceRecorder = remember { VoiceRecorder(context) }

    fun processVoiceCommandText(speechText: String) {
        if (speechText.isBlank()) return
        transcript = speechText
        manualVoiceInput = speechText
        isUploading = true
        scope.launch {
            try {
                val res = ApiClient.processVoiceCommand(speechText, if (isKannada) "kn-IN" else "en-IN")
                val jsonObj = JSONObject(res)
                val act = jsonObj.optString("action", "UPDATE_HARVEST")
                detectedAction = act
                val feedback = jsonObj.optString("spoken_feedback", "")
                spokenResponse = feedback

                val entities = jsonObj.optJSONObject("entities")
                if (entities != null) {
                    val currentH = UserSession.activeHarvest ?: HarvestDeclaration()
                    var newCrop = currentH.crop
                    var newQty = currentH.quantityKg
                    var newDate = currentH.harvestDate
                    var newTime = currentH.harvestTime
                    var newImm = currentH.sellImmediately

                    if (entities.has("crop") && entities.getString("crop").isNotBlank()) {
                        val c = entities.getString("crop")
                        newCrop = when {
                            c.equals("Tomato", ignoreCase = true) || c.contains("ಟೊಮೆಟೊ") -> "Tomato"
                            c.equals("Onion", ignoreCase = true) || c.contains("ಈರುಳ್ಳಿ") -> "Onion"
                            c.equals("Potato", ignoreCase = true) || c.contains("ಆಲೂ") -> "Potato"
                            c.equals("Cabbage", ignoreCase = true) || c.contains("ಕೋಸು") -> "Cabbage"
                            c.equals("Carrot", ignoreCase = true) || c.contains("ಕ್ಯಾರೆಟ್") -> "Carrot"
                            c.equals("Chilli", ignoreCase = true) || c.contains("ಮೆಣಸಿನ") -> "Chilli"
                            else -> c
                        }
                    }
                    if (entities.has("quantity_kg")) {
                        val q = entities.optDouble("quantity_kg", 0.0)
                        if (q > 0) newQty = q
                    }
                    if (entities.has("harvest_date") && entities.getString("harvest_date").isNotBlank()) {
                        newDate = entities.getString("harvest_date")
                    }
                    if (entities.has("harvest_time") && entities.getString("harvest_time").isNotBlank()) {
                        newTime = entities.getString("harvest_time")
                    }
                    if (entities.has("sell_immediately")) {
                        newImm = entities.getBoolean("sell_immediately")
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

                if (act.startsWith("NAVIGATE_") && onNavigateAction != null) {
                    onNavigateAction(act)
                }
            } catch (e: Exception) {
                spokenResponse = "Error: ${e.localizedMessage}"
            } finally {
                isUploading = false
            }
        }
    }

    fun toggleAudioRecording() {
        if (!isRecordingAudio) {
            try {
                val file = voiceRecorder.startRecording()
                isRecordingAudio = true
                audioFileName = file.name
                spokenResponse = if (isKannada)
                    "🔴 ರೆಕಾರ್ಡ್ ಆಗುತ್ತಿದೆ... ಮಾತನಾಡಿ (ನಿಲ್ಲಿಸಲು ಮತ್ತೆ ಟ್ಯಾಪ್ ಮಾಡಿ)"
                else
                    "🔴 Recording from microphone... Speak now (tap mic again to stop & send to Sarvam)"
            } catch (e: Exception) {
                spokenResponse = "Recording error: ${e.localizedMessage}"
            }
        } else {
            val audioFile = voiceRecorder.stopRecording()
            isRecordingAudio = false

            if (audioFile != null && audioFile.exists() && audioFile.length() > 44) {
                audioFileName = audioFile.name
                isUploading = true
                spokenResponse = if (isKannada)
                    "⏳ ಸರ್ವಂ ಸಾರಸ್ v4 ಗೆ ಅಪ್‌ಲೋಡ್ ಮಾಡಲಾಗುತ್ತಿದೆ..."
                else
                    "⏳ Audio saved (${audioFile.length() / 1024} KB). Uploading to Sarvam Saaras AI..."

                scope.launch {
                    try {
                        val res = ApiClient.uploadVoiceAudio(audioFile, if (isKannada) "kn-IN" else "en-IN")
                        val jsonObj = JSONObject(res)
                        val rawTranscript = jsonObj.optString("raw_transcript", "")
                        if (rawTranscript.isNotBlank()) {
                            transcript = rawTranscript
                            manualVoiceInput = rawTranscript
                        }
                        val act = jsonObj.optString("action", "UPDATE_HARVEST")
                        detectedAction = act
                        val feedback = jsonObj.optString("spoken_feedback", "")
                        spokenResponse = feedback

                        val entities = jsonObj.optJSONObject("entities")
                        if (entities != null) {
                            val currentH = UserSession.activeHarvest ?: HarvestDeclaration()
                            var newCrop = currentH.crop
                            var newQty = currentH.quantityKg
                            var newDate = currentH.harvestDate
                            var newTime = currentH.harvestTime
                            var newImm = currentH.sellImmediately

                            if (entities.has("crop") && entities.getString("crop").isNotBlank()) {
                                val c = entities.getString("crop")
                                newCrop = when {
                                    c.equals("Tomato", ignoreCase = true) || c.contains("ಟೊಮೆಟೊ") -> "Tomato"
                                    c.equals("Onion", ignoreCase = true) || c.contains("ಈರುಳ್ಳಿ") -> "Onion"
                                    c.equals("Potato", ignoreCase = true) || c.contains("ಆಲೂ") -> "Potato"
                                    c.equals("Cabbage", ignoreCase = true) || c.contains("ಕೋಸು") -> "Cabbage"
                                    c.equals("Carrot", ignoreCase = true) || c.contains("ಕ್ಯಾರೆಟ್") -> "Carrot"
                                    c.equals("Chilli", ignoreCase = true) || c.contains("ಮೆಣಸಿನ") -> "Chilli"
                                    else -> c
                                }
                            }
                            if (entities.has("quantity_kg")) {
                                val q = entities.optDouble("quantity_kg", 0.0)
                                if (q > 0) newQty = q
                            }
                            if (entities.has("harvest_date") && entities.getString("harvest_date").isNotBlank()) {
                                newDate = entities.getString("harvest_date")
                            }
                            if (entities.has("harvest_time") && entities.getString("harvest_time").isNotBlank()) {
                                newTime = entities.getString("harvest_time")
                            }
                            if (entities.has("sell_immediately")) {
                                newImm = entities.getBoolean("sell_immediately")
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

                        if (act.startsWith("NAVIGATE_") && onNavigateAction != null) {
                            onNavigateAction(act)
                        }
                    } catch (e: Exception) {
                        spokenResponse = "Sarvam upload error: ${e.localizedMessage}"
                    } finally {
                        isUploading = false
                    }
                }
            } else {
                spokenResponse = if (isKannada) "ಆಡಿಯೋ ರೆಕಾರ್ಡ್ ಆಗಿಲ್ಲ" else "No audio recorded"
            }
        }
    }

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

                // Pulsing Mic Circle (Direct Hardware Recording)
                Surface(
                    shape = CircleShape,
                    color = when {
                        isRecordingAudio -> Color(0xFFEF4444)
                        isUploading -> HarvestGold
                        else -> ForestGreenDeep
                    },
                    shadowElevation = if (isRecordingAudio || isUploading) 10.dp else 4.dp,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable { toggleAudioRecording() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (isRecordingAudio) "🔴" else if (isUploading) "⏳" else "🎙️",
                            fontSize = 34.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when {
                        isRecordingAudio -> if (isKannada) "ರೆಕಾರ್ಡ್ ಆಗುತ್ತಿದೆ... ನಿಲ್ಲಿಸಲು ಟ್ಯಾಪ್ ಮಾಡಿ" else "Recording... Tap again to send to Sarvam"
                        isUploading -> if (isKannada) "ಸರ್ವಂ AI ವಿಶ್ಲೇಷಿಸುತ್ತಿದೆ..." else "Sarvam AI Analyzing Audio..."
                        else -> if (isKannada) "ರೆಕಾರ್ಡ್ ಮಾಡಲು ಮೈಕ್ ಟ್ಯಾಪ್ ಮಾಡಿ" else "Tap mic to record audio"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isRecordingAudio) Color(0xFFDC2626) else ForestGreenDeep
                )

                if (audioFileName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📁 Local WAV: $audioFileName",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                // Editable Spoken Transcript / Text input bar
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = manualVoiceInput,
                    onValueChange = { manualVoiceInput = it },
                    label = {
                        Text(
                            if (isKannada) "ಧ್ವನಿ ಪಠ್ಯ / ಆಜ್ಞೆ ನಮೂದಿಸಿ" else "Voice Speech / Command Input",
                            fontSize = 11.sp
                        )
                    },
                    placeholder = {
                        Text(
                            if (isKannada) "ಉದಾ: ಇಂದು 200 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ..."
                            else "e.g. Harvested 200kg tomato today...",
                            fontSize = 11.sp
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (manualVoiceInput.isNotBlank()) processVoiceCommandText(manualVoiceInput)
                        }) {
                            Text("⚡", fontSize = 18.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

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
                            val prompt = if (isKannada) "ನಾನು ಇಂದು 200 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ, ನಾಳೆ ಮಾರಾಟ ಮಾಡಬೇಕು" else "I harvested 200kg tomato today, planning to sell immediately"
                            processVoiceCommandText(prompt)
                        },
                        label = { Text("🍅 200kg", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            val prompt = if (isKannada) "ಕೋಲಾರದ ಮಾರುಕಟ್ಟೆ ದರ ತೋರಿಸಿ" else "Show Kolar market rates"
                            processVoiceCommandText(prompt)
                        },
                        label = { Text("📊 Rates", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            val prompt = if (isKannada) "ಹವಾಮಾನ ಮುನ್ಸೂಚನೆ ತೋರಿಸಿ" else "Show weather advisory"
                            processVoiceCommandText(prompt)
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
