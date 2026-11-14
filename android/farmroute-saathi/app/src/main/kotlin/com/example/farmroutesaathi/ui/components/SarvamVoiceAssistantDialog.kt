package com.example.farmroutesaathi.ui.components

import android.media.MediaPlayer
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.farmroutesaathi.audio.VoiceRecorder
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.AppLocalization
import com.example.farmroutesaathi.data.HarvestDeclaration
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val audioBase64: String? = null,
    val timestamp: String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SarvamVoiceAssistantDialog(
    isKannada: Boolean = false,
    onDismiss: () -> Unit,
    onNavigateAction: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val voiceRecorder = remember { VoiceRecorder(context) }

    val selectedLang = UserSession.selectedLanguage
    val sarvamLangCode = selectedLang.sarvamCode
    val activeHarvest = UserSession.activeHarvest
    val harvestCrop = activeHarvest?.crop
    val harvestQty = (activeHarvest?.quantityKg ?: 200.0).toInt()
    val harvestId = UserSession.activeHarvestId

    var isRecordingAudio by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var manualInput by remember { mutableStateOf("") }
    var lastSpokenAudioBase64 by remember { mutableStateOf<String?>(null) }

    val messages = remember {
        mutableStateListOf(
            AssistantChatMessage(
                text = AppLocalization.t("assistant_greeting"),
                isUser = false
            )
        )
    }

    // Mic Pulsing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecordingAudio) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    fun stopAudioPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlayingAudio = false
        } catch (_: Exception) {}
    }

    fun playAudioBase64(b64: String) {
        if (b64.isBlank()) return
        stopAudioPlayback()
        scope.launch(Dispatchers.IO) {
            try {
                val audioBytes = Base64.decode(b64, Base64.DEFAULT)
                val tempFile = File.createTempFile("sarvam_voice_", ".wav", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(audioBytes) }

                withContext(Dispatchers.Main) {
                    val player = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            isPlayingAudio = false
                            try { release() } catch (_: Exception) {}
                            mediaPlayer = null
                        }
                        setOnErrorListener { _, _, _ ->
                            isPlayingAudio = false
                            try { release() } catch (_: Exception) {}
                            mediaPlayer = null
                            false
                        }
                    }
                    mediaPlayer = player
                    isPlayingAudio = true
                    lastSpokenAudioBase64 = b64
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isPlayingAudio = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudioPlayback()
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun handleAssistantResponse(responseText: String, audioB64: String?, action: String? = null) {
        messages.add(
            AssistantChatMessage(
                text = responseText,
                isUser = false,
                audioBase64 = audioB64
            )
        )
        if (!audioB64.isNullOrBlank()) {
            playAudioBase64(audioB64)
        }
        if (!action.isNullOrBlank() && action.startsWith("NAVIGATE_") && onNavigateAction != null) {
            onNavigateAction(action)
        }
    }

    fun executeVoiceQuery(queryText: String) {
        if (queryText.isBlank() || isProcessing) return
        stopAudioPlayback()
        messages.add(AssistantChatMessage(text = queryText, isUser = true))
        manualInput = ""
        isProcessing = true

        scope.launch {
            try {
                val res = ApiClient.processVoiceCommand(queryText, sarvamLangCode)
                val jsonObj = JSONObject(res)
                val feedback = jsonObj.optString("spoken_feedback", jsonObj.optString("message_en", "Information retrieved."))
                val audioB64 = jsonObj.optString("audio_base64", "")
                val action = jsonObj.optString("action", "")

                // Extract any harvest updates
                val entities = jsonObj.optJSONObject("entities")
                if (entities != null) {
                    val currentH = UserSession.activeHarvest ?: HarvestDeclaration()
                    var newCrop = currentH.crop
                    var newQty = currentH.quantityKg
                    if (entities.has("crop") && entities.getString("crop").isNotBlank()) {
                        newCrop = entities.getString("crop")
                    }
                    if (entities.has("quantity_kg")) {
                        val q = entities.optDouble("quantity_kg", 0.0)
                        if (q > 0) newQty = q
                    }
                    UserSession.setHarvest(currentH.copy(crop = newCrop, quantityKg = newQty))
                }

                handleAssistantResponse(feedback, audioB64, action)
            } catch (e: Exception) {
                handleAssistantResponse("Error: ${e.localizedMessage ?: "Could not connect to Sarvam AI"}", null)
            } finally {
                isProcessing = false
            }
        }
    }

    fun executeQuickWeather() {
        if (isProcessing) return
        stopAudioPlayback()
        val userPrompt = AppLocalization.t("quick_weather")
        messages.add(AssistantChatMessage(text = userPrompt, isUser = true))
        isProcessing = true

        scope.launch {
            try {
                val dist = UserSession.currentFarmer?.district ?: "Local"
                val state = UserSession.currentFarmer?.state ?: ""
                val cropSuffix = if (!harvestCrop.isNullOrBlank()) " for $harvestCrop" else ""
                val promptText = "Current weather forecast and farm advisory for $dist $state$cropSuffix"
                val res = ApiClient.processVoiceCommand(promptText, sarvamLangCode)
                val jsonObj = JSONObject(res)
                val feedback = jsonObj.optString("spoken_feedback", jsonObj.optString("message_en", "Weather advisory retrieved."))
                val audioB64 = jsonObj.optString("audio_base64", "")
                handleAssistantResponse(feedback, audioB64, "NAVIGATE_WEATHER")
            } catch (e: Exception) {
                handleAssistantResponse("Unable to fetch weather intelligence right now.", null)
            } finally {
                isProcessing = false
            }
        }
    }

    fun executeQuickRates() {
        if (isProcessing) return
        stopAudioPlayback()
        val userPrompt = AppLocalization.t("quick_rates")
        messages.add(AssistantChatMessage(text = userPrompt, isUser = true))
        isProcessing = true

        scope.launch {
            try {
                val cropText = if (!harvestCrop.isNullOrBlank()) " for $harvestCrop" else ""
                val promptText = "Compare APMC mandi rates and net take-home$cropText"
                val res = if (!harvestCrop.isNullOrBlank() && !harvestId.isNullOrBlank()) {
                    try {
                        ApiClient.getMandiVoiceExplanation(harvestId, sarvamLangCode)
                    } catch (_: Exception) {
                        ApiClient.processVoiceCommand(promptText, sarvamLangCode)
                    }
                } else {
                    ApiClient.processVoiceCommand(promptText, sarvamLangCode)
                }
                val jsonObj = JSONObject(res)
                val summary = jsonObj.optString("spoken_feedback", jsonObj.optString("summary_text", jsonObj.optString("message_en", "Comparing live mandi rates.")))
                val audioB64 = jsonObj.optString("audio_base64", "")
                handleAssistantResponse(summary, audioB64, "NAVIGATE_MARKETPLACE")
            } catch (e: Exception) {
                handleAssistantResponse("Unable to retrieve mandi comparison right now.", null)
            } finally {
                isProcessing = false
            }
        }
    }

    fun executeQuickHarvest() {
        if (isProcessing) return
        stopAudioPlayback()
        val userPrompt = if (!harvestCrop.isNullOrBlank()) {
            AppLocalization.t("quick_harvest", "qty" to "$harvestQty", "crop" to harvestCrop)
        } else {
            "🌾 ${AppLocalization.t("quick_my_crops")}"
        }
        messages.add(AssistantChatMessage(text = userPrompt, isUser = true))
        isProcessing = true

        scope.launch {
            try {
                val promptText = if (!harvestCrop.isNullOrBlank()) {
                    "What is the status and next step for active harvest $harvestCrop $harvestQty kg"
                } else {
                    "Check my registered harvest lots and available crops"
                }
                val res = ApiClient.processVoiceCommand(promptText, sarvamLangCode)
                val jsonObj = JSONObject(res)
                val feedback = jsonObj.optString("spoken_feedback", "Active harvest details retrieved.")
                val audioB64 = jsonObj.optString("audio_base64", "")
                handleAssistantResponse(feedback, audioB64)
            } catch (e: Exception) {
                val fallbackMsg = if (!harvestCrop.isNullOrBlank()) {
                    "Active harvest lot: $harvestQty kg Grade-A $harvestCrop. Ready for Mandi sale or cold storage."
                } else {
                    "No active harvest declared yet. Tap 'Register Harvest Lot' to record your produce."
                }
                handleAssistantResponse(fallbackMsg, null)
            } finally {
                isProcessing = false
            }
        }
    }

    fun toggleAudioRecording() {
        if (!isRecordingAudio) {
            stopAudioPlayback()
            try {
                voiceRecorder.startRecording()
                isRecordingAudio = true
            } catch (e: Exception) {
                Toast.makeText(context, "Microphone error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            val audioFile = voiceRecorder.stopRecording()
            isRecordingAudio = false

            if (audioFile != null && audioFile.exists() && audioFile.length() > 44) {
                isProcessing = true
                scope.launch {
                    try {
                        val res = ApiClient.uploadVoiceAudio(audioFile, sarvamLangCode)
                        val jsonObj = JSONObject(res)
                        val rawTranscript = jsonObj.optString("raw_transcript", "")
                        if (rawTranscript.isNotBlank()) {
                            messages.add(AssistantChatMessage(text = rawTranscript, isUser = true))
                        }
                        val feedback = jsonObj.optString("spoken_feedback", jsonObj.optString("message_en", "Voice command processed."))
                        val audioB64 = jsonObj.optString("audio_base64", "")
                        val action = jsonObj.optString("action", "")
                        handleAssistantResponse(feedback, audioB64, action)
                    } catch (e: Exception) {
                        handleAssistantResponse("Audio recognition failed. Please try again or type below.", null)
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = {
            stopAudioPlayback()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // ================================================================
                // HEADER
                // ================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SoilCropGreen,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎙️", fontSize = 20.sp)
                            }
                        }
                        Column {
                            Text(
                                text = AppLocalization.t("voice_assistant_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = SoilDeepEarth
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${selectedLang.nativeName} (${selectedLang.englishName})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SoilCropGreen
                                )
                                Text(
                                    text = "• Bulbul TTS",
                                    fontSize = 10.5.sp,
                                    color = SoilDeepEarth.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                stopAudioPlayback()
                                messages.clear()
                                messages.add(
                                    AssistantChatMessage(
                                        text = AppLocalization.t("assistant_greeting"),
                                        isUser = false
                                    )
                                )
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("🔄", fontSize = 16.sp)
                        }
                        IconButton(
                            onClick = {
                                stopAudioPlayback()
                                onDismiss()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SoilDeepEarth)
                        }
                    }
                }

                HorizontalDivider(
                    color = SoilDeepEarth.copy(alpha = 0.10f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // ================================================================
                // ACTIVE AUDIO STATUS / PLAYBACK BANNER
                // ================================================================
                AnimatedVisibility(visible = isPlayingAudio || isProcessing || isRecordingAudio) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isRecordingAudio -> Color(0xFFFEE2E2)
                            isProcessing -> SoilHarvestGold.copy(alpha = 0.15f)
                            else -> SoilCropGreen.copy(alpha = 0.12f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isRecordingAudio -> Color(0xFFEF4444)
                                isProcessing -> SoilHarvestGold
                                else -> SoilCropGreen
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = when {
                                        isRecordingAudio -> "🔴"
                                        isProcessing -> "⏳"
                                        else -> "🔊"
                                    },
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = when {
                                        isRecordingAudio -> AppLocalization.t("recording_audio")
                                        isProcessing -> AppLocalization.t("analyzing_audio")
                                        else -> AppLocalization.t("sarvam_speaking")
                                    },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isRecordingAudio -> Color(0xFFDC2626)
                                        isProcessing -> HarvestGoldDark
                                        else -> SoilCropGreen
                                    }
                                )
                            }

                            if (isPlayingAudio) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { stopAudioPlayback() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(AppLocalization.t("stop_audio"), fontSize = 10.5.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // ================================================================
                // CONVERSATION HISTORY (BUBBLES)
                // ================================================================
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (msg.isUser) 16.dp else 2.dp,
                                    bottomEnd = if (msg.isUser) 2.dp else 16.dp
                                ),
                                color = if (msg.isUser) SoilCropGreen else SoilFieldPaper,
                                border = if (msg.isUser) null else BorderStroke(1.dp, SoilDeepEarth.copy(alpha = 0.10f)),
                                shadowElevation = 1.dp,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.text,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = if (msg.isUser) Color.White else SoilDeepEarth,
                                        fontWeight = if (msg.isUser) FontWeight.Medium else FontWeight.Normal
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = msg.timestamp,
                                            fontSize = 9.5.sp,
                                            color = if (msg.isUser) Color.White.copy(alpha = 0.7f) else SoilDeepEarth.copy(alpha = 0.55f)
                                        )
                                        if (!msg.isUser && !msg.audioBase64.isNullOrBlank()) {
                                            Text(
                                                text = "🔊 " + AppLocalization.t("replay_audio"),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SoilCropGreen,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable { playAudioBase64(msg.audioBase64) }
                                                    .padding(2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = SoilDeepEarth.copy(alpha = 0.10f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // ================================================================
                // QUICK COMMANDS (3 CONTEXTUAL PILLS)
                // ================================================================
                Text(
                    text = AppLocalization.t("quick_commands_label"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SoilDeepEarth.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { executeQuickWeather() },
                        label = {
                            Text(
                                text = AppLocalization.t("quick_weather"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SoilFieldPaper,
                            labelColor = SoilDeepEarth
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = SoilDeepEarth.copy(alpha = 0.18f),
                            enabled = true
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    AssistChip(
                        onClick = { executeQuickRates() },
                        label = {
                            Text(
                                text = AppLocalization.t("quick_rates"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SoilFieldPaper,
                            labelColor = SoilDeepEarth
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = SoilDeepEarth.copy(alpha = 0.18f),
                            enabled = true
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    AssistChip(
                        onClick = { executeQuickHarvest() },
                        label = {
                            Text(
                                text = if (!harvestCrop.isNullOrBlank()) "🌾 ${harvestQty}kg $harvestCrop" else "🌾 ${AppLocalization.t("quick_my_crops")}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SoilFieldPaper,
                            labelColor = SoilDeepEarth
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = SoilDeepEarth.copy(alpha = 0.18f),
                            enabled = true
                        ),
                        modifier = Modifier.weight(1.15f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ================================================================
                // HARDWARE MIC RECORDING + TYPED INPUT ROW
                // ================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pulsing Hardware Mic Button
                    Surface(
                        shape = CircleShape,
                        color = if (isRecordingAudio) Color(0xFFEF4444) else SoilCropGreen,
                        shadowElevation = if (isRecordingAudio) 8.dp else 3.dp,
                        modifier = Modifier
                            .size(54.dp)
                            .scale(if (isRecordingAudio) micScale else 1f)
                            .clip(CircleShape)
                            .clickable { toggleAudioRecording() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isRecordingAudio) "🔴" else "🎙️",
                                fontSize = 24.sp
                            )
                        }
                    }

                    // Typed Question Field with Send Button
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = AppLocalization.t("voice_input_placeholder"),
                                fontSize = 11.5.sp,
                                color = SoilDeepEarth.copy(alpha = 0.5f)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (manualInput.isNotBlank()) executeVoiceQuery(manualInput)
                                },
                                enabled = manualInput.isNotBlank() && !isProcessing
                            ) {
                                Text(
                                    text = "➔",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (manualInput.isNotBlank()) SoilCropGreen else Color.Gray
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (manualInput.isNotBlank()) executeVoiceQuery(manualInput)
                        }),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SoilDeepEarth,
                            unfocusedTextColor = SoilDeepEarth,
                            focusedBorderColor = SoilCropGreen,
                            unfocusedBorderColor = SoilDeepEarth.copy(alpha = 0.25f),
                            focusedContainerColor = SoilFieldPaper,
                            unfocusedContainerColor = SoilFieldPaper
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Done Button
                Button(
                    onClick = {
                        stopAudioPlayback()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = AppLocalization.t("done_btn"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
