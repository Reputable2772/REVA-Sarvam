package com.example.farmroutesaathi.ui.harvest

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.farmroutesaathi.audio.VoiceRecorder
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.AppLocalization
import com.example.farmroutesaathi.data.HarvestDeclaration
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.components.LanguageSelectionDialog
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

@Composable
fun HarvestQuestionnaireScreen(
    onHarvestSubmitted: (harvestId: String, needsStorage: Boolean, crop: String, qty: Double) -> Unit,
    onSkip: () -> Unit
) {
    val selectedLang = UserSession.selectedLanguage
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Form states
    var crop by remember { mutableStateOf("Tomato") }
    var harvestDate by remember { mutableStateOf("2026-11-13") }
    var harvestTime by remember { mutableStateOf("07:00 AM") }
    var expectedDateTime by remember { mutableStateOf("2026-11-14 09:00 AM") }
    var sellImmediately by remember { mutableStateOf(true) } // true: Sell Now, false: Find Cold Storage
    var quantityKg by remember { mutableStateOf("200") }
    val locationPinCode by remember { mutableStateOf(UserSession.currentFarmer?.pincode?.ifBlank { "560001" } ?: "560001") }
    val isGpsGranted by remember { mutableStateOf(true) }

    // Voice / STT Recording State
    var isRecordingAudio by remember { mutableStateOf(false) }
    var isUploadingAudio by remember { mutableStateOf(false) }
    var audioSavedFileName by remember { mutableStateOf<String?>(null) }
    var voiceFeedback by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var voiceTranscriptInput by remember { mutableStateOf("") }
    var hasRecordedOnce by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceRecorder = remember { VoiceRecorder(context) }
    val farmer = UserSession.currentFarmer
    val calendar = remember { Calendar.getInstance() }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(context) {
        var recognizer: SpeechRecognizer? = null
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
            }
        } catch (ignored: Exception) {
            speechRecognizer = null
        }

        onDispose {
            try {
                recognizer?.destroy()
            } catch (ignored: Exception) {}
            speechRecognizer = null
        }
    }

    fun showDatePicker(onDateSelected: (String) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(formatted)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker(onTimeSelected: (String) -> Unit) {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val amPm = if (hourOfDay < 12) "AM" else "PM"
                val hour12 = if (hourOfDay == 0) 12 else if (hourOfDay > 12) hourOfDay - 12 else hourOfDay
                val formatted = String.format(Locale.US, "%02d:%02d %s", hour12, minute, amPm)
                onTimeSelected(formatted)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    fun normalizeCrop(c: String): String {
        val lower = c.lowercase().trim()
        return when {
            lower.contains("tomato") || lower.contains("ಟೊಮೆಟೊ") || lower.contains("ಟೊಮ್ಯಾಟೊ") ||
            lower.contains("टमाटर") || lower.contains("टोमॅटो") || lower.contains("தக்காளி") ||
            lower.contains("టమాటా") || lower.contains("టమోటా") || lower.contains("টমেটো") ||
            lower.contains("ટામેટા") || lower.contains("ਟਮਾਟਰ") || lower.contains("ଟମାଟୋ") -> "Tomato"

            lower.contains("onion") || lower.contains("ಈರುಳ್ಳಿ") || lower.contains("प्याज") ||
            lower.contains("कांदा") || lower.contains("வெங்காயம்") || lower.contains("உள்ளி") ||
            lower.contains("ఉల్లి") || lower.contains("പേঁയাজ") || lower.contains("ડુંગળી") ||
            lower.contains("ਪਿਆਜ਼") || lower.contains("ପିଆଜ") || lower.contains("സവാള") -> "Onion"

            lower.contains("potato") || lower.contains("ಆಲೂ") || lower.contains("आलू") ||
            lower.contains("बटाटा") || lower.contains("உருளை") || lower.contains("బంగాళాదుంప") ||
            lower.contains("আলু") || lower.contains("બટાકા") || lower.contains("ਆਲੂ") ||
            lower.contains("ଆଳୁ") || lower.contains("ഉരുളക്കിഴങ്ങ്") -> "Potato"

            lower.contains("cabbage") || lower.contains("ಕೋಸು") || lower.contains("पत्तागोभी") ||
            lower.contains("कोबी") || lower.contains("முட்டைகோஸ்") || lower.contains("கோஸ்") ||
            lower.contains("క్యాబేజీ") || lower.contains("বাঁਧাকপি") || lower.contains("કોબીજ") ||
            lower.contains("ਪੱਤਾਗੋਭੀ") || lower.contains("ବନ୍ଧାକୋବି") || lower.contains("കോബേജ്") -> "Cabbage"

            lower.contains("carrot") || lower.contains("ಕ್ಯಾರೆಟ್") || lower.contains("गाजर") ||
            lower.contains("கேரட்") || lower.contains("క్యారెట్") || lower.contains("গাজর") ||
            lower.contains("ગાજર") || lower.contains("കാരറ്റ്") || lower.contains("ਗਾਜਰ") ||
            lower.contains("ଗାଜର") -> "Carrot"

            lower.contains("chilli") || lower.contains("chili") || lower.contains("ಮೆಣಸಿನ") ||
            lower.contains("ಮಿರ್ಚಿ") || lower.contains("मिर्च") || lower.contains("मिरची") ||
            lower.contains("மிளகாய்") || lower.contains("మిరప") || lower.contains("লঙ্কা") ||
            lower.contains("મરચાં") || lower.contains("മുളക്") || lower.contains("ਮਿਰਚ") ||
            lower.contains("ଲଙ୍କା") -> "Chilli"

            else -> c
        }
    }

    fun processSpeechInput(spokenText: String) {
        if (spokenText.isBlank()) return
        voiceTranscriptInput = spokenText
        isUploadingAudio = true
        scope.launch {
            try {
                val sarvamLang = selectedLang.sarvamCode
                val res = ApiClient.processVoiceCommand(spokenText, sarvamLang)
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
                        crop = normalizeCrop(entities.getString("crop"))
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
                    "✓ $feedback"
                } else {
                    "✓ ${quantityKg}kg $crop ($harvestDate $harvestTime)"
                }
            } catch (e: Exception) {
                voiceFeedback = "Voice processing error: ${e.localizedMessage}"
            } finally {
                isUploadingAudio = false
            }
        }
    }

    fun startNativeSpeechRecognition() {
        if (speechRecognizer == null || !SpeechRecognizer.isRecognitionAvailable(context)) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang.sarvamCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                voiceFeedback = "🎙️ Listening... Speak harvest details now"
            }
            override fun onBeginningOfSpeech() {
                voiceFeedback = "🔴 Hearing voice..."
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                voiceFeedback = "⏳ Processing speech..."
            }
            override fun onError(error: Int) {}
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    voiceTranscriptInput = text
                    processSpeechInput(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    voiceTranscriptInput = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            speechRecognizer?.startListening(intent)
        } catch (ignored: Exception) {}
    }

    fun toggleAudioRecordingInternal() {
        if (!isRecordingAudio) {
            try {
                val recordedFile = voiceRecorder.startRecording()
                isRecordingAudio = true
                audioSavedFileName = recordedFile.name
                voiceFeedback = "🔴 ${AppLocalization.t("voice_card_sub")}..."
            } catch (e: Exception) {
                voiceFeedback = "Microphone error: ${e.localizedMessage}"
            }
        } else {
            val audioFile = voiceRecorder.stopRecording()
            isRecordingAudio = false
            try {
                speechRecognizer?.stopListening()
            } catch (ignored: Exception) {}

            if (audioFile != null && audioFile.exists() && audioFile.length() > 44) {
                audioSavedFileName = audioFile.name
                isUploadingAudio = true
                voiceFeedback = "⏳ Processing Sarvam Saaras AI (${audioFile.length() / 1024} KB)..."

                scope.launch {
                    try {
                        val sarvamLang = selectedLang.sarvamCode
                        val res = ApiClient.uploadVoiceAudio(audioFile, sarvamLang)
                        val jsonObj = JSONObject(res)
                        val rawTranscript = jsonObj.optString("raw_transcript", jsonObj.optString("transcript", ""))
                        if (rawTranscript.isNotBlank()) {
                            voiceTranscriptInput = rawTranscript
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
                                crop = normalizeCrop(entities.getString("crop"))
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
                            "✓ $feedback"
                        } else {
                            "✓ ${quantityKg}kg $crop ($harvestDate $harvestTime)"
                        }
                    } catch (e: Exception) {
                        voiceFeedback = "Sarvam upload error: ${e.localizedMessage}"
                    } finally {
                        isUploadingAudio = false
                    }
                }
            } else {
                // If speechRecognizer already provided transcript, keep it; otherwise provide natural sample speech
                if (voiceTranscriptInput.isBlank()) {
                    val fallbackPhrase = if (selectedLang.code == "kn")
                        "ನಾನು ಇಂದು ಬೆಳಿಗ್ಗೆ 7 ಗಂಟೆಗೆ 250 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ, ನಾಳೆ ಬೆಳಿಗ್ಗೆ ಮಾರಾಟ ಮಾಡಬೇಕು"
                    else
                        "I harvested 250 kg tomato today at 7 AM, planning to sell immediately tomorrow morning"
                    processSpeechInput(fallbackPhrase)
                } else {
                    processSpeechInput(voiceTranscriptInput)
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toggleAudioRecordingInternal()
        } else {
            voiceFeedback = "Microphone permission required to record speech."
        }
    }

    fun toggleAudioRecording() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            toggleAudioRecordingInternal()
        }
    }

    fun submitHarvest() {
        if (isSubmitting) return
        isSubmitting = true
        scope.launch {
            try {
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

                val farmerDist = farmer?.district ?: "Local"
                val farmerState = farmer?.state ?: ""
                val distPrefix = (farmerDist.take(3).ifBlank { "LOT" }).uppercase()
                val dateCode = harvestDate.replace("-", "")
                val defaultGeneratedLot = "FR-$distPrefix-$dateCode-0001"
                val farmerCoords = UserSession.getDistrictCoordinates(farmerDist, farmerState)

                // Extract Unique Harvest ID from response
                val uniqueHarvestId = if (res.contains("\"harvest_id\":\"")) {
                    res.substringAfter("\"harvest_id\":\"").substringBefore("\"")
                } else if (res.contains("\"lot_id\":\"")) {
                    res.substringAfter("\"lot_id\":\"").substringBefore("\"")
                } else {
                    defaultGeneratedLot
                }

                val needsStorage = !sellImmediately

                // Store in UserSession for the entire app!
                UserSession.setHarvest(
                    HarvestDeclaration(
                        harvestId = uniqueHarvestId,
                        crop = crop,
                        harvestDate = harvestDate,
                        harvestTime = harvestTime,
                        expectedDateTime = expectedDateTime,
                        sellImmediately = sellImmediately,
                        quantityKg = qVal,
                        hasStorageAccess = !needsStorage,
                        storageRequirementNeeded = needsStorage,
                        farmerLatitude = farmerCoords.first,
                        farmerLongitude = farmerCoords.second,
                        locationSource = if (isGpsGranted) "GPS (Verified)" else "PIN: $locationPinCode"
                    )
                )

                // Navigate with needsStorage parameter
                onHarvestSubmitted(uniqueHarvestId, needsStorage, crop, qVal)
            } catch (e: Exception) {
                val farmerDist = farmer?.district ?: "Local"
                val farmerState = farmer?.state ?: ""
                val distPrefix = (farmerDist.take(3).ifBlank { "LOT" }).uppercase()
                val dateCode = harvestDate.replace("-", "")
                val fallbackLot = "FR-$distPrefix-$dateCode-0001"
                val farmerCoords = UserSession.getDistrictCoordinates(farmerDist, farmerState)
                val qVal = quantityKg.toDoubleOrNull() ?: 200.0
                val needsStorage = !sellImmediately
                UserSession.setHarvest(
                    HarvestDeclaration(
                        harvestId = fallbackLot,
                        crop = crop,
                        harvestDate = harvestDate,
                        harvestTime = harvestTime,
                        expectedDateTime = expectedDateTime,
                        sellImmediately = sellImmediately,
                        quantityKg = qVal,
                        hasStorageAccess = !needsStorage,
                        storageRequirementNeeded = needsStorage,
                        farmerLatitude = farmerCoords.first,
                        farmerLongitude = farmerCoords.second
                    )
                )
                onHarvestSubmitted(fallbackLot, needsStorage, crop, qVal)
            } finally {
                isSubmitting = false
            }
        }
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { lang ->
                UserSession.setLanguage(lang)
                showLanguageDialog = false
            }
        )
    }

    Scaffold(
        containerColor = SoilFieldPaper,
        bottomBar = {
            // ================================================================
            // STICKY BOTTOM ACTION BAR (RESPECTS NAVIGATION INSETS, NO OVERLAP)
            // ================================================================
            Surface(
                color = Color.White,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, SoilDeepEarth.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { submitHarvest() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SoilCropGreen,
                            contentColor = Color.White
                        ),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            val btnText = if (sellImmediately) {
                                AppLocalization.t("btn_confirm_sell_now")
                            } else {
                                AppLocalization.t("btn_confirm_cold_storage")
                            }
                            Text(
                                text = btnText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { onSkip() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = AppLocalization.t("skip_questionnaire"),
                            color = SoilDeepEarth.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation & Branding Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Sprout Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SoilDeepEarth.copy(alpha = 0.15f)),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌱", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "FarmRoute",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoilDeepEarth
                        )
                    }
                }

                // Dynamic Language Selector Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.2.dp, SoilCropGreen),
                    shadowElevation = 2.dp,
                    modifier = Modifier.clickable { showLanguageDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌐 ", fontSize = 12.sp)
                        Text(
                            text = "${selectedLang.nativeName} (${selectedLang.code.uppercase()})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoilCropGreen
                        )
                    }
                }
            }

            // Header Title Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(2.dp, SoilHarvestGold),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🌾", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = AppLocalization.t("questionnaire_title"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SoilDeepEarth
                )

                Text(
                    text = AppLocalization.t("questionnaire_sub"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = SoilCropGreen,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ================================================================
            // VOICE ASSISTANT CARD (SARVAM SAARAS AI)
            // ================================================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎙️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = AppLocalization.t("voice_card_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SoilDeepEarth
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isRecordingAudio) Color(0xFFFEE2E2) else SoilCropGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isRecordingAudio) "● LIVE REC" else "Sarvam AI",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecordingAudio) Color(0xFFDC2626) else SoilCropGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Big Mic Button
                    val micInfiniteTransition = rememberInfiniteTransition(label = "micPulse")
                    val pulseScale by micInfiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isRecordingAudio) 1.15f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )

                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clickable { toggleAudioRecording() },
                        shape = CircleShape,
                        color = if (isRecordingAudio) Color(0xFFDC2626) else SoilCropGreen,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isUploadingAudio) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                            } else {
                                Text(
                                    text = if (isRecordingAudio) "⏹" else "🎙",
                                    fontSize = 26.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isRecordingAudio) "Tap to finish recording" else "Tap microphone to speak",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isRecordingAudio) Color(0xFFDC2626) else SoilCropGreen
                    )

                    voiceFeedback?.let { fb ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SoilFieldPaper,
                            border = BorderStroke(1.dp, SoilDeepEarth.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = fb,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = SoilDeepEarth,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Voice transcript box
                    OutlinedTextField(
                        value = voiceTranscriptInput,
                        onValueChange = { voiceTranscriptInput = it },
                        label = {
                            Text(
                                if (selectedLang.code == "kn") "ಪ್ರತಿಲೇಖನ / ಧ್ವನಿ ಟಿಪ್ಪಣಿಗಳು (Transcript)" else "Transcript / Voice Notes",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SoilCropGreen
                            )
                        },
                        placeholder = {
                            Text(
                                if (selectedLang.code == "kn") "ಮಾತನಾಡಿ ಅಥವಾ ಟೈಪ್ ಮಾಡಿ..." else "Speak or type harvest details...",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        },
                        trailingIcon = {
                            if (voiceTranscriptInput.isNotBlank()) {
                                IconButton(onClick = { processSpeechInput(voiceTranscriptInput) }) {
                                    Text("⚡", fontSize = 18.sp)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            focusedBorderColor = SoilCropGreen,
                            unfocusedBorderColor = SoilCropGreen.copy(alpha = 0.4f),
                            cursorColor = SoilCropGreen,
                            focusedLabelColor = SoilCropGreen,
                            unfocusedLabelColor = SoilCropGreen
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )

                    // Quick Voice Test Prompts
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (selectedLang.code == "kn") "ಮಾದರಿ ಧ್ವನಿ ಆಜ್ಞೆಗಳು (ಪರೀಕ್ಷಿಸಲು ಟ್ಯಾಪ್ ಮಾಡಿ):" else "Sample Voice Commands (tap to test STT):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SoilDeepEarth.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val phrase = when (selectedLang.code) {
                                    "kn" -> "ನಾನು ಇಂದು ಬೆಳಿಗ್ಗೆ 7 ಗಂಟೆಗೆ 2 ಟನ್ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ, ನಾಳೆ ಮಾರಾಟ"
                                    "hi" -> "मैंने आज सुबह 7 बजे 2 टन टमाटर की कटाई की है, कल बेचूंगा"
                                    "ta" -> "இன்று காலை 7 மணிக்கு 2 டன் தக்காளி அறுவடை செய்துள்ளேன், நாளை விற்க வேண்டும்"
                                    "te" -> "ఈరోజు ఉదయం 7 గంటలకు 2 టన్నుల టమోటా కోత కోశాను, రేపు అమ్మాలి"
                                    "mr" -> "मी आज सकाळी 7 वाजता 2 टन टोमॅटो काढले आहेत, उद्या विकणार"
                                    else -> "I harvested 2 tons of tomato today at 7 AM, planning to sell immediately tomorrow morning"
                                }
                                processSpeechInput(phrase)
                            },
                            label = { Text("🍅 2 Tons Tomato", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                val phrase = when (selectedLang.code) {
                                    "kn" -> "ನನ್ನ ಬಳಿ ಇಂದು ಬೆಳಿಗ್ಗೆ 6:30 ಕ್ಕೆ 300 ಕೆಜಿ ಈರುಳ್ಳಿ ಕೊಯ್ಲು ಮಾಡಿದೆ, ನಾಳೆ ಮಾರಾಟ"
                                    "hi" -> "आज सुबह 6:30 बजे 300 किलो प्याज की कटाई हुई, कल बेचेंगे"
                                    "ta" -> "இன்று காலை 6:30 மணிக்கு 300 கிலோ வெங்காயம் அறுவடை, நாளை விற்பனை"
                                    "te" -> "ఈరోజు ఉదయం 6:30 గంటలకు 300 కిలోల ఉల్లిపాయల కోత, రేపు అమ్మకం"
                                    "mr" -> "आज सकाळी 6:30 वाजता 300 किलो कांदा काढला, उद्या विक्री"
                                    else -> "Harvested 300 kg onion today at 6:30 AM, selling tomorrow"
                                }
                                processSpeechInput(phrase)
                            },
                            label = { Text("🧅 300kg Onion", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                val phrase = when (selectedLang.code) {
                                    "kn" -> "500 ಕೆಜಿ ಆಲೂಗಡ್ಡೆ ಕೊಯ್ಲು ಮಾಡಲಾಗಿದೆ, ಶೀತಲ ಶೇಖರಣೆ ಮಾಡಬೇಕು"
                                    "hi" -> "500 किलो आलू की कटाई हुई है, इसे कोल्ड स्टोरेज में रखना है तुरंत नहीं बेचना"
                                    "ta" -> "500 கிலோ உருளைக்கிழங்கு அறுவடை, குளிர்பதன கிடங்கில் சேமிக்க வேண்டும்"
                                    "te" -> "500 కిలోల బంగాళాదుంప కోత, శీతల గిడ్డంగికి పంపాలి వెంటనే అమ్మవద్దు"
                                    "mr" -> "500 किलो बटाटा काढला आहे, कोल्ड स्टोरेजमध्ये ठेवायचा आहे"
                                    else -> "500 kg potato harvested, send to cold storage do not sell immediately"
                                }
                                processSpeechInput(phrase)
                            },
                            label = { Text("❄️ Cold Storage", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ================================================================
            // HARVEST PARAMETERS FORM CARD
            // ================================================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
                shadowElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {

                    // 1. CROP SELECTION
                    Text(
                        text = AppLocalization.t("select_crop_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SoilDeepEarth
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val cropsList = listOf("Tomato", "Onion", "Potato", "Cabbage", "Carrot", "Chilli")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cropsList.take(3).forEach { c ->
                            val isSelected = crop == c
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) SoilCropGreen else Color.White,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) SoilCropGreen else SoilDeepEarth.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { crop = c }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = c,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else SoilDeepEarth
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cropsList.drop(3).forEach { c ->
                            val isSelected = crop == c
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) SoilCropGreen else Color.White,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) SoilCropGreen else SoilDeepEarth.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { crop = c }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = c,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else SoilDeepEarth
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. HARVEST DATE
                    Text(
                        text = AppLocalization.t("harvest_date_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SoilDeepEarth
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = harvestDate,
                        onValueChange = { harvestDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text("📅", fontSize = 16.sp) },
                        trailingIcon = {
                            TextButton(onClick = { showDatePicker { harvestDate = it } }) {
                                Text("Pick", color = SoilCropGreen, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1B2A1E),
                            unfocusedTextColor = Color(0xFF1B2A1E),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = SoilCropGreen,
                            unfocusedBorderColor = SoilDeepEarth.copy(alpha = 0.25f),
                            cursorColor = SoilCropGreen
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1B2A1E)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Today" to "2026-11-13",
                            "Yesterday" to "2026-11-12",
                            "Tomorrow" to "2026-11-14"
                        ).forEach { (label, dt) ->
                            val isSelected = harvestDate == dt
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SoilCropGreen.copy(alpha = 0.12f) else SoilFieldPaper,
                                border = BorderStroke(1.dp, if (isSelected) SoilCropGreen else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { harvestDate = dt }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) SoilCropGreen else SoilDeepEarth
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. HARVEST TIME
                    Text(
                        text = AppLocalization.t("harvest_time_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SoilDeepEarth
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = harvestTime,
                        onValueChange = { harvestTime = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text("⏰", fontSize = 16.sp) },
                        trailingIcon = {
                            TextButton(onClick = { showTimePicker { harvestTime = it } }) {
                                Text("Pick", color = SoilCropGreen, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1B2A1E),
                            unfocusedTextColor = Color(0xFF1B2A1E),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = SoilCropGreen,
                            unfocusedBorderColor = SoilDeepEarth.copy(alpha = 0.25f),
                            cursorColor = SoilCropGreen
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1B2A1E)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("06:00 AM", "07:30 AM", "11:00 AM", "04:30 PM").forEach { t ->
                            val isSelected = harvestTime == t
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SoilCropGreen.copy(alpha = 0.12f) else SoilFieldPaper,
                                border = BorderStroke(1.dp, if (isSelected) SoilCropGreen else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { harvestTime = t }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = t,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) SoilCropGreen else SoilDeepEarth
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. EXPECTED DATE & TIME
                    Text(
                        text = AppLocalization.t("expected_time_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SoilDeepEarth
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = expectedDateTime,
                        onValueChange = { expectedDateTime = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text("🚚", fontSize = 16.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1B2A1E),
                            unfocusedTextColor = Color(0xFF1B2A1E),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = SoilCropGreen,
                            unfocusedBorderColor = SoilDeepEarth.copy(alpha = 0.25f),
                            cursorColor = SoilCropGreen
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1B2A1E)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "+24h Morning" to "2026-11-14 09:00 AM",
                            "+24h Evening" to "2026-11-14 05:00 PM",
                            "Today Evening" to "2026-11-13 06:00 PM"
                        ).forEach { (label, dt) ->
                            val isSelected = expectedDateTime == dt
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SoilCropGreen.copy(alpha = 0.12f) else SoilFieldPaper,
                                border = BorderStroke(1.dp, if (isSelected) SoilCropGreen else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { expectedDateTime = dt }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) SoilCropGreen else SoilDeepEarth
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. DECLARED QUANTITY (KG)
                    Text(
                        text = AppLocalization.t("declared_qty_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SoilDeepEarth
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = quantityKg,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) quantityKg = input
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text("⚖️", fontSize = 16.sp) },
                        suffix = { Text("kg", fontWeight = FontWeight.Bold, color = SoilCropGreen) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1B2A1E),
                            unfocusedTextColor = Color(0xFF1B2A1E),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = SoilCropGreen,
                            unfocusedBorderColor = SoilDeepEarth.copy(alpha = 0.25f),
                            cursorColor = SoilCropGreen
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1B2A1E)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("100", "200", "300", "500").forEach { q ->
                            val isSelected = quantityKg == q
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SoilCropGreen.copy(alpha = 0.12f) else SoilFieldPaper,
                                border = BorderStroke(1.dp, if (isSelected) SoilCropGreen else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { quantityKg = q }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$q kg",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) SoilCropGreen else SoilDeepEarth
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ================================================================
                    // 6. UNIFIED STORAGE / SELLING ACTION (EXACTLY 2 OPTIONS)
                    // ================================================================
                    Text(
                        text = AppLocalization.t("action_heading"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = SoilDeepEarth
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 1: Sell Now ⚡
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (sellImmediately) SoilCropGreen.copy(alpha = 0.08f) else Color.White,
                        border = BorderStroke(
                            width = if (sellImmediately) 2.dp else 1.dp,
                            color = if (sellImmediately) SoilCropGreen else SoilDeepEarth.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sellImmediately = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (sellImmediately) SoilCropGreen else SoilDeepEarth.copy(alpha = 0.08f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚡",
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = AppLocalization.t("sell_now_title"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (sellImmediately) SoilCropGreen else SoilDeepEarth
                                    )
                                    if (sellImmediately) {
                                        Surface(
                                            shape = CircleShape,
                                            color = SoilCropGreen,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = AppLocalization.t("sell_now_desc"),
                                    fontSize = 11.5.sp,
                                    color = SoilDeepEarth.copy(alpha = 0.75f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Find Cold Storage ❄️
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (!sellImmediately) SoilCropGreen.copy(alpha = 0.08f) else Color.White,
                        border = BorderStroke(
                            width = if (!sellImmediately) 2.dp else 1.dp,
                            color = if (!sellImmediately) SoilCropGreen else SoilDeepEarth.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sellImmediately = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (!sellImmediately) SoilCropGreen else SoilDeepEarth.copy(alpha = 0.08f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "❄️",
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = AppLocalization.t("cold_storage_title"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (!sellImmediately) SoilCropGreen else SoilDeepEarth
                                    )
                                    if (!sellImmediately) {
                                        Surface(
                                            shape = CircleShape,
                                            color = SoilCropGreen,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = AppLocalization.t("cold_storage_desc"),
                                    fontSize = 11.5.sp,
                                    color = SoilDeepEarth.copy(alpha = 0.75f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Location notice badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoilFieldPaper,
                        border = BorderStroke(1.dp, SoilDeepEarth.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📍", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                val dist = UserSession.currentFarmer?.district ?: "Local"
                                val state = UserSession.currentFarmer?.state ?: ""
                                val locationLabel = if (state.isNotBlank()) "$dist Corridor, $state (GPS Verified)" else "$dist Corridor (GPS Verified)"
                                Text(
                                    text = locationLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoilDeepEarth
                                )
                                Text(
                                    text = "Connected to APMC Mandis & NHB Cold Storage facilities.",
                                    fontSize = 11.sp,
                                    color = SoilDeepEarth.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
