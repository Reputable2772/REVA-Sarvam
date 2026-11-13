package com.example.farmroutesaathi.ui.harvest

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.audio.VoiceRecorder
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.HarvestDeclaration
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun HarvestQuestionnaireScreen(
    onHarvestSubmitted: (String) -> Unit,
    onSkip: () -> Unit
) {
    var isKannada by remember { mutableStateOf(false) }

    // Form states
    var crop by remember { mutableStateOf("Tomato") }
    var harvestDate by remember { mutableStateOf("2026-11-13") }
    var harvestTime by remember { mutableStateOf("07:00 AM") }
    var expectedDateTime by remember { mutableStateOf("2026-11-14 09:00 AM") }
    var sellImmediately by remember { mutableStateOf(true) }
    var quantityKg by remember { mutableStateOf("200") }

    // Voice / STT Recording State
    var isRecordingAudio by remember { mutableStateOf(false) }
    var isUploadingAudio by remember { mutableStateOf(false) }
    var audioSavedFileName by remember { mutableStateOf<String?>(null) }
    var voiceFeedback by remember { mutableStateOf<String?>(null) }
    var recognizedFields by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var voiceTranscriptInput by remember { mutableStateOf("") }
    var hasRecordedOnce by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceRecorder = remember { VoiceRecorder(context) }
    val farmer = UserSession.currentFarmer

    fun processSpeechInput(spokenText: String) {
        if (spokenText.isBlank()) return
        voiceTranscriptInput = if (voiceTranscriptInput.isBlank() || !hasRecordedOnce) spokenText else "$voiceTranscriptInput | $spokenText"
        isUploadingAudio = true
        scope.launch {
            try {
                val res = ApiClient.processVoiceCommand(spokenText, if (isKannada) "kn-IN" else "en-IN")
                val jsonObj = JSONObject(res)
                val fieldsUpdated = jsonObj.optJSONArray("fields_updated")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                } ?: emptySet()
                val entities = jsonObj.optJSONObject("entities")
                if (entities != null) {
                    val isFirst = !hasRecordedOnce
                    val updateCrop = isFirst || fieldsUpdated.contains("crop")
                    val updateQty = isFirst || fieldsUpdated.contains("quantity_kg")
                    val updateDate = isFirst || fieldsUpdated.contains("harvest_date")
                    val updateTime = isFirst || fieldsUpdated.contains("harvest_time")
                    val updateExpected = isFirst || fieldsUpdated.contains("expected_date_time")
                    val updateSell = isFirst || fieldsUpdated.contains("sell_immediately")

                    if (updateCrop && entities.has("crop") && entities.getString("crop").isNotBlank()) {
                        val c = entities.getString("crop")
                        crop = when {
                            c.equals("Tomato", ignoreCase = true) || c.contains("ಟೊಮೆಟೊ") -> "Tomato"
                            c.equals("Onion", ignoreCase = true) || c.contains("ಈರುಳ್ಳಿ") -> "Onion"
                            c.equals("Potato", ignoreCase = true) || c.contains("ಆಲೂ") -> "Potato"
                            c.equals("Cabbage", ignoreCase = true) || c.contains("ಕೋಸು") -> "Cabbage"
                            c.equals("Carrot", ignoreCase = true) || c.contains("ಕ್ಯಾರೆಟ್") -> "Carrot"
                            c.equals("Chilli", ignoreCase = true) || c.contains("ಮೆಣಸಿನ") -> "Chilli"
                            else -> c
                        }
                    }
                    if (updateQty && entities.has("quantity_kg")) {
                        val q = entities.optDouble("quantity_kg", 0.0)
                        if (q > 0) quantityKg = q.toInt().toString()
                    }
                    if (updateDate && entities.has("harvest_date") && entities.getString("harvest_date").isNotBlank()) {
                        harvestDate = entities.getString("harvest_date")
                    }
                    if (updateTime && entities.has("harvest_time") && entities.getString("harvest_time").isNotBlank()) {
                        harvestTime = entities.getString("harvest_time")
                    }
                    if (updateExpected && entities.has("expected_date_time") && entities.getString("expected_date_time").isNotBlank()) {
                        expectedDateTime = entities.getString("expected_date_time")
                    }
                    if (updateSell && entities.has("sell_immediately")) {
                        sellImmediately = entities.getBoolean("sell_immediately")
                    }
                    hasRecordedOnce = true
                }
                val feedback = jsonObj.optString("spoken_feedback", "")
                voiceFeedback = if (feedback.isNotBlank()) {
                    "✓ $feedback (Fields merged)"
                } else {
                    if (isKannada) "✓ ಗುರುತಿಸಲಾಗಿದೆ: $quantityKg ಕೆಜಿ $crop ($harvestDate $harvestTime)"
                    else "✓ Recognized: ${quantityKg}kg $crop ($harvestDate $harvestTime)"
                }
            } catch (e: Exception) {
                voiceFeedback = "Voice processing error: ${e.localizedMessage}"
            } finally {
                isUploadingAudio = false
            }
        }
    }

    fun toggleAudioRecording() {
        if (!isRecordingAudio) {
            try {
                val recordedFile = voiceRecorder.startRecording()
                isRecordingAudio = true
                audioSavedFileName = recordedFile.name
                voiceFeedback = if (isKannada)
                    "🔴 ಮೈಕ್ರೊಫೋನ್ ರೆಕಾರ್ಡಿಂಗ್ ಪ್ರಾರಂಭವಾಗಿದೆ... ಮಾತನಾಡಿ (ನಿಲ್ಲಿಸಲು ಮತ್ತೆ ಟ್ಯಾಪ್ ಮಾಡಿ)"
                else
                    "🔴 Recording from microphone... Speak now (tap again to stop & send to Sarvam)"
            } catch (e: Exception) {
                voiceFeedback = "Microphone error: ${e.localizedMessage}"
            }
        } else {
            val audioFile = voiceRecorder.stopRecording()
            isRecordingAudio = false

            if (audioFile != null && audioFile.exists() && audioFile.length() > 44) {
                audioSavedFileName = audioFile.name
                isUploadingAudio = true
                voiceFeedback = if (isKannada)
                    "⏳ ಧ್ವನಿ ರೆಕಾರ್ಡಿಂಗ್ ಉಳಿಸಲಾಗಿದೆ (${audioFile.length() / 1024} KB). ಸರ್ವಂ ಸಾರಸ್ v4 ಗೆ ಅಪ್‌ಲೋಡ್ ಮಾಡಲಾಗುತ್ತಿದೆ..."
                else
                    "⏳ Audio saved (${audioFile.length() / 1024} KB). Uploading to Sarvam Saaras v4 backend..."

                scope.launch {
                    try {
                        val res = ApiClient.uploadVoiceAudio(audioFile, if (isKannada) "kn-IN" else "en-IN")
                        val jsonObj = JSONObject(res)
                        val rawTranscript = jsonObj.optString("raw_transcript", "")
                        if (rawTranscript.isNotBlank()) {
                            voiceTranscriptInput = if (voiceTranscriptInput.isBlank() || !hasRecordedOnce) rawTranscript else "$voiceTranscriptInput | $rawTranscript"
                        }
                        val fieldsUpdated = jsonObj.optJSONArray("fields_updated")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }.toSet()
                        } ?: emptySet()
                        val entities = jsonObj.optJSONObject("entities")
                        if (entities != null) {
                            val isFirst = !hasRecordedOnce
                            val updateCrop = isFirst || fieldsUpdated.contains("crop")
                            val updateQty = isFirst || fieldsUpdated.contains("quantity_kg")
                            val updateDate = isFirst || fieldsUpdated.contains("harvest_date")
                            val updateTime = isFirst || fieldsUpdated.contains("harvest_time")
                            val updateExpected = isFirst || fieldsUpdated.contains("expected_date_time")
                            val updateSell = isFirst || fieldsUpdated.contains("sell_immediately")

                            if (updateCrop && entities.has("crop") && entities.getString("crop").isNotBlank()) {
                                val c = entities.getString("crop")
                                crop = when {
                                    c.equals("Tomato", ignoreCase = true) || c.contains("ಟೊಮೆಟೊ") -> "Tomato"
                                    c.equals("Onion", ignoreCase = true) || c.contains("ಈರುಳ್ಳಿ") -> "Onion"
                                    c.equals("Potato", ignoreCase = true) || c.contains("ಆಲೂ") -> "Potato"
                                    c.equals("Cabbage", ignoreCase = true) || c.contains("ಕೋಸು") -> "Cabbage"
                                    c.equals("Carrot", ignoreCase = true) || c.contains("ಕ್ಯಾರೆಟ್") -> "Carrot"
                                    c.equals("Chilli", ignoreCase = true) || c.contains("ಮೆಣಸಿನ") -> "Chilli"
                                    else -> c
                                }
                            }
                            if (updateQty && entities.has("quantity_kg")) {
                                val q = entities.optDouble("quantity_kg", 0.0)
                                if (q > 0) quantityKg = q.toInt().toString()
                            }
                            if (updateDate && entities.has("harvest_date") && entities.getString("harvest_date").isNotBlank()) {
                                harvestDate = entities.getString("harvest_date")
                            }
                            if (updateTime && entities.has("harvest_time") && entities.getString("harvest_time").isNotBlank()) {
                                harvestTime = entities.getString("harvest_time")
                            }
                            if (updateExpected && entities.has("expected_date_time") && entities.getString("expected_date_time").isNotBlank()) {
                                expectedDateTime = entities.getString("expected_date_time")
                            }
                            if (updateSell && entities.has("sell_immediately")) {
                                sellImmediately = entities.getBoolean("sell_immediately")
                            }
                            hasRecordedOnce = true
                        }
                        val feedback = jsonObj.optString("spoken_feedback", "")
                        voiceFeedback = if (feedback.isNotBlank()) {
                            "✓ $feedback (Fields merged)"
                        } else {
                            if (isKannada) "✓ ಗುರುತಿಸಲಾಗಿದೆ: $quantityKg ಕೆಜಿ $crop ($harvestDate $harvestTime)"
                            else "✓ Recognized: ${quantityKg}kg $crop ($harvestDate $harvestTime)"
                        }
                    } catch (e: Exception) {
                        voiceFeedback = "Sarvam upload error: ${e.localizedMessage}"
                    } finally {
                        isUploadingAudio = false
                    }
                }
            } else {
                voiceFeedback = if (isKannada) "ರೆಕಾರ್ಡ್ ಮಾಡಿದ ಆಡಿಯೋ ಫೈಲ್ ಖಾಲಿಯಾಗಿದೆ" else "Recording stopped: no audio recorded"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(
                        Brush.verticalGradient(listOf(ForestGreenDeep, FreshLeafGreen))
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = HarvestGold,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🌾", fontSize = 22.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ಕೊಯ್ಲು ವಿವರಗಳ ಪ್ರಶ್ನಾವಳಿ" else "Harvest Questionnaire",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isKannada) "ರೈತ: ${farmer?.name ?: "ಕಿಸಾನ್"}" else "Farmer: ${farmer?.name ?: "Kisan"}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        // Language Toggle Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = HarvestGold,
                            modifier = Modifier.clickable { isKannada = !isKannada }
                        ) {
                            Text(
                                text = if (isKannada) "🌐 ಕನ್ನಡ" else "🌐 English",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDeep,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isKannada)
                            "ನಿಮ್ಮ ಇಂದಿನ ಬೆಳೆ ಕೊಯ್ಲು ವಿವರಗಳನ್ನು ನಮೂದಿಸಿ ಅಥವಾ ಕೆಳಗಿನ ಧ್ವನಿ ಮೈಕ್ರೊಫೋನ್ ಬಳಸಿ ಮಾತನಾಡಿ."
                        else
                            "Declare your current crop harvest details below, or speak naturally using the Sarvam Voice AI mic.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.90f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // SARVAM SPEECH-TO-TEXT VOICE ASSISTANT CARD
            // ==========================================
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎙️", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKannada) "ಸರ್ವಂ ಧ್ವನಿ ಸಹಾಯಕ (STT)" else "Sarvam Voice STT Assistant",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ForestGreenDeep
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = FreshLeafGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "SARVAM SAARAS v4",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDeep,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Voice Recording Hardware Button (Records audio file and sends to Sarvam STT)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            isRecordingAudio -> Color(0xFFEF4444).copy(alpha = 0.20f)
                            isUploadingAudio -> HarvestGold.copy(alpha = 0.25f)
                            else -> ForestGreenDeep.copy(alpha = 0.06f)
                        },
                        border = BorderStroke(
                            1.5.dp,
                            when {
                                isRecordingAudio -> Color(0xFFEF4444)
                                isUploadingAudio -> HarvestGold
                                else -> ForestGreenDeep.copy(alpha = 0.3f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleAudioRecording() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                if (isRecordingAudio) "🔴" else if (isUploadingAudio) "⏳" else "🎙️",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    isRecordingAudio -> if (isKannada) "ರೆಕಾರ್ಡ್ ಆಗುತ್ತಿದೆ... ನಿಲ್ಲಿಸಲು ಟ್ಯಾಪ್ ಮಾಡಿ" else "Recording Audio... Tap to Stop & Send"
                                    isUploadingAudio -> if (isKannada) "ಸರ್ವಂ AI ವಿಶ್ಲೇಷಿಸುತ್ತಿದೆ..." else "Sarvam AI Analyzing Audio..."
                                    else -> if (isKannada) "ಧ್ವನಿ ರೆಕಾರ್ಡ್ ಮಾಡಲು ಟ್ಯಾಪ್ ಮಾಡಿ" else "Tap to Record & Send Audio to Sarvam"
                                },
                                fontWeight = FontWeight.Bold,
                                color = if (isRecordingAudio) Color(0xFFDC2626) else ForestGreenDeep,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Recording File info banner
                    if (audioSavedFileName != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📁 Local Recording: $audioSavedFileName",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }

                    // Spoken Feedback Banner
                    if (voiceFeedback != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ForestGreenDeep.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = voiceFeedback ?: "",
                                color = ForestGreenDeep,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Editable Spoken Transcript / Text input bar
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = voiceTranscriptInput,
                        onValueChange = { voiceTranscriptInput = it },
                        label = {
                            Text(
                                if (isKannada) "ಧ್ವನಿ ಪಠ್ಯ (ಸಂಪಾದಿಸಿ ಅಥವಾ ಬರೆಯಿರಿ)" else "Voice Transcript / Custom Input",
                                fontSize = 11.sp
                            )
                        },
                        placeholder = {
                            Text(
                                if (isKannada) "ಉದಾ: ಇಂದು ಬೆಳಿಗ್ಗೆ 7 ಗಂಟೆಗೆ 250 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ..."
                                else "e.g. Harvested 250 kg tomato today at 7 AM...",
                                fontSize = 11.sp
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (voiceTranscriptInput.isNotBlank()) processSpeechInput(voiceTranscriptInput)
                            }) {
                                Text("⚡", fontSize = 18.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Quick Voice Test Phrases
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isKannada) "ಮಾದರಿ ಧ್ವನಿ ಆಜ್ಞೆಗಳು (ಪರೀಕ್ಷಿಸಲು ಟ್ಯಾಪ್ ಮಾಡಿ):" else "Sample Voice Commands (tap to test Sarvam STT):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val phrase = if (isKannada)
                                    "ನಾನು ಇಂದು ಬೆಳಿಗ್ಗೆ 7 ಗಂಟೆಗೆ 250 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ, ನಾಳೆ ಬೆಳಿಗ್ಗೆ ಮಾರಾಟ ಮಾಡಬೇಕು"
                                else
                                    "I harvested 250 kg tomato today at 7 AM, planning to sell immediately tomorrow morning"
                                processSpeechInput(phrase)
                            },
                            label = { Text("🍅 250kg Tomato", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                val phrase = if (isKannada)
                                    "ನನ್ನ ಬಳಿ ಇಂದು ಬೆಳಿಗ್ಗೆ 6:30 ಕ್ಕೆ 300 ಕೆಜಿ ಈರುಳ್ಳಿ ಕೊಯ್ಲು ಮಾಡಿದೆ, ನಾಳೆ ಮಾರಾಟ"
                                else
                                    "Harvested 300 kg onion today at 6:30 AM, selling tomorrow"
                                processSpeechInput(phrase)
                            },
                            label = { Text("🧅 300kg Onion", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                val phrase = if (isKannada)
                                    "500 ಕೆಜಿ ಆಲೂಗಡ್ಡೆ ಕೊಯ್ಲು ಮಾಡಲಾಗಿದೆ, ಶೀತಲ ಶೇಖರಣೆ ಮಾಡಬೇಕು"
                                else
                                    "500 kg potato harvested, send to cold storage do not sell immediately"
                                processSpeechInput(phrase)
                            },
                            label = { Text("❄️ Cold Storage", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // HARVEST PARAMETERS QUESTIONNAIRE FORM
            // ==========================================
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {

                    // 1. CROP SELECTION
                    Text(
                        text = if (isKannada) "1. ಬೆಳೆ (Crop)" else "1. Select Crop",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val cropsList = listOf("Tomato", "Onion", "Potato", "Cabbage", "Carrot", "Chilli")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cropsList.take(3).forEach { c ->
                            FilterChip(
                                selected = crop == c,
                                onClick = { crop = c },
                                label = { Text(c, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cropsList.drop(3).forEach { c ->
                            FilterChip(
                                selected = crop == c,
                                onClick = { crop = c },
                                label = { Text(c, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. HARVEST DATE
                    Text(
                        text = if (isKannada) "2. ಕೊಯ್ಲು ದಿನಾಂಕ (Harvest Date)" else "2. Harvest Date",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = harvestDate,
                        onValueChange = { harvestDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("📅") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(
                            onClick = { harvestDate = "2026-11-13" },
                            label = { Text("Today (ಇಂದು)", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { harvestDate = "2026-11-12" },
                            label = { Text("Yesterday (ನಿನ್ನೆ)", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { harvestDate = "2026-11-14" },
                            label = { Text("Tomorrow (ನಾಳೆ)", fontSize = 10.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. HARVEST TIME
                    Text(
                        text = if (isKannada) "3. ಕೊಯ್ಲು ಸಮಯ (Harvest Time)" else "3. Harvest Time",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = harvestTime,
                        onValueChange = { harvestTime = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("⏰") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(onClick = { harvestTime = "06:00 AM" }, label = { Text("06:00 AM", fontSize = 10.sp) })
                        SuggestionChip(onClick = { harvestTime = "07:30 AM" }, label = { Text("07:30 AM", fontSize = 10.sp) })
                        SuggestionChip(onClick = { harvestTime = "11:00 AM" }, label = { Text("11:00 AM", fontSize = 10.sp) })
                        SuggestionChip(onClick = { harvestTime = "04:30 PM" }, label = { Text("04:30 PM", fontSize = 10.sp) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. EXPECTED DATE / TIME
                    Text(
                        text = if (isKannada) "4. ನಿರೀಕ್ಷಿತ ಸಾಗಣೆ ಸಮಯ (Expected Pickup/Delivery)" else "4. Expected Date & Time",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = expectedDateTime,
                        onValueChange = { expectedDateTime = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("🚚") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(
                            onClick = { expectedDateTime = "2026-11-14 09:00 AM" },
                            label = { Text("+24h Morning", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { expectedDateTime = "2026-11-14 05:00 PM" },
                            label = { Text("+24h Evening", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { expectedDateTime = "2026-11-13 06:00 PM" },
                            label = { Text("Today Evening", fontSize = 10.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. PLANNING TO SELL IMMEDIATELY?
                    Text(
                        text = if (isKannada) "5. ತಕ್ಷಣ ಮಾರಾಟ ಮಾಡಲು ಯೋಜಿಸುತ್ತಿದ್ದೀರಾ?" else "5. Planning to Sell Immediately?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Yes Option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sellImmediately) ForestGreenDeep else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, if (sellImmediately) ForestGreenDeep else Color.LightGray),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { sellImmediately = true }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("⚡", fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isKannada) "ಹೌದು, ತಕ್ಷಣ ಮಾರಾಟ" else "Yes, Sell Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sellImmediately) Color.White else Color.Black
                                )
                                Text(
                                    text = if (isKannada) "ವರ್ಚುವಲ್ FPO ಪೂಲಿಂಗ್" else "Virtual FPO Pool",
                                    fontSize = 10.sp,
                                    color = if (sellImmediately) Color.White.copy(alpha = 0.8f) else Color.Gray
                                )
                            }
                        }

                        // No Option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (!sellImmediately) ForestGreenDeep else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, if (!sellImmediately) ForestGreenDeep else Color.LightGray),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { sellImmediately = false }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("❄️", fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isKannada) "ಇಲ್ಲ, ಶೀತಲಾಗಾರ" else "No, Cold Storage",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!sellImmediately) Color.White else Color.Black
                                )
                                Text(
                                    text = if (isKannada) "ಶೇಖರಿಸಿಡಿ" else "Hold in Facility",
                                    fontSize = 10.sp,
                                    color = if (!sellImmediately) Color.White.copy(alpha = 0.8f) else Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 6. QUANTITY (KG)
                    Text(
                        text = if (isKannada) "6. ಅಂದಾಜು ತೂಕ (Quantity in kg)" else "6. Declared Quantity (kg)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = quantityKg,
                        onValueChange = { quantityKg = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("⚖️") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(onClick = { quantityKg = "100" }, label = { Text("100 kg", fontSize = 10.sp) })
                        SuggestionChip(onClick = { quantityKg = "200" }, label = { Text("200 kg", fontSize = 10.sp) })
                        SuggestionChip(onClick = { quantityKg = "300" }, label = { Text("300 kg", fontSize = 10.sp) })
                        SuggestionChip(onClick = { quantityKg = "500" }, label = { Text("500 kg", fontSize = 10.sp) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // ACTION BUTTONS: CONFIRM OR SKIP
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = {
                        isSubmitting = true
                        scope.launch {
                            val fId = farmer?.phone ?: "9845012345"
                            val qVal = quantityKg.toDoubleOrNull() ?: 200.0

                            val res = ApiClient.declareHarvest(
                                farmerId = fId,
                                crop = crop,
                                quantityKg = qVal,
                                harvestDate = harvestDate,
                                harvestTime = harvestTime,
                                expectedDateTime = expectedDateTime,
                                sellImmediately = sellImmediately
                            )
                            isSubmitting = false

                            // Extract Unique Harvest ID from response
                            val uniqueHarvestId = if (res.contains("\"harvest_id\":\"")) {
                                res.substringAfter("\"harvest_id\":\"").substringBefore("\"")
                            } else if (res.contains("\"lot_id\":\"")) {
                                res.substringAfter("\"lot_id\":\"").substringBefore("\"")
                            } else {
                                "FR-KLR-20261113-0001"
                            }

                            // Store in UserSession for the entire app!
                            UserSession.setHarvest(
                                HarvestDeclaration(
                                    harvestId = uniqueHarvestId,
                                    crop = crop,
                                    harvestDate = harvestDate,
                                    harvestTime = harvestTime,
                                    expectedDateTime = expectedDateTime,
                                    sellImmediately = sellImmediately,
                                    quantityKg = qVal
                                )
                            )

                            // Navigate to Main!
                            onHarvestSubmitted(uniqueHarvestId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = if (isKannada) "🌾 ಕೊಯ್ಲು ದೃಢೀಕರಿಸಿ (DECLARE HARVEST)" else "🌾 CONFIRM & DECLARE HARVEST",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = { onSkip() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isKannada) "ಈಗಲೇ ಬಿಟ್ಟುಬಿಡಿ (ಡೀಫಾಲ್ಟ್ ಲಾಟ್ ಬಳಸಿ)" else "Skip for now (Use default lot FR-KLR-20261113-0001)",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
