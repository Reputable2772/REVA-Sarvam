package com.example.farmroutesaathi.ui.harvest

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.AppLocalization
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.components.LanguageSelectionDialog
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandiComparisonScreen(
    harvestId: String,
    onBackClick: () -> Unit
) {
    var isKannada: Boolean by remember { mutableStateOf(UserSession.isKannadaPreferred) }
    var isLoading by remember { mutableStateOf(true) }
    var crop by remember { mutableStateOf("Tomato") }
    var quantityKg by remember { mutableStateOf(200.0) }
    var farmerLocation by remember { mutableStateOf("Kolar, Karnataka") }
    var mandisList by remember { mutableStateOf<List<MandiCardData>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Audio TTS playback state (Dual-Layer: Sarvam Neural Audio + Android Native TTS)
    var isPlayingAudio by remember { mutableStateOf(false) }
    var spokenSummaryText by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = UserSession.isDarkTheme

    // Initialize Android native TTS engine targeting Google TTS with generic fallback
    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        try {
            tts = TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val targetLocale = if (isKannada) Locale("kn", "IN") else Locale.ENGLISH
                    val avail = tts?.isLanguageAvailable(targetLocale)
                    if (avail != null && avail >= TextToSpeech.LANG_AVAILABLE) {
                        tts?.language = targetLocale
                    } else {
                        tts?.language = Locale.ENGLISH
                    }
                }
            }, "com.google.android.tts")
            ttsEngine = tts
        } catch (e: Exception) {
            try {
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = if (isKannada) Locale("kn", "IN") else Locale.ENGLISH
                    }
                }
                ttsEngine = tts
            } catch (ignored: Exception) {}
        }

        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (ignored: Exception) {}
            try {
                tts?.stop()
                tts?.shutdown()
                ttsEngine = null
            } catch (ignored: Exception) {}
        }
    }

    fun loadMandiData(targetQuery: String? = null) {
        isLoading = true
        scope.launch {
            try {
                val lotRes = ApiClient.getHarvestLot(harvestId)
                val lotJson = JSONObject(lotRes)
                if (lotJson.optBoolean("success", false)) {
                    val lotObj = lotJson.optJSONObject("lot")
                    if (lotObj != null) {
                        crop = lotObj.optString("crop", "Tomato")
                        quantityKg = lotObj.optDouble("quantity_kg", 200.0)
                    }
                }
            } catch (e: Exception) {}

            try {
                val langCode = if (isKannada) "kn-IN" else "en-IN"
                val responseStr = if (!targetQuery.isNullOrBlank()) {
                    ApiClient.searchMandis(query = targetQuery, crop = crop, language = langCode)
                } else {
                    ApiClient.compareMandis(harvestId = harvestId, language = langCode)
                }

                val json = JSONObject(responseStr)
                if (json.optBoolean("success", false) || json.has("mandis")) {
                    farmerLocation = json.optString("farmer_location", "Kolar, Karnataka")
                    val arr = json.optJSONArray("mandis") ?: JSONArray()
                    val list = mutableListOf<MandiCardData>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            MandiCardData(
                                mandiName = obj.optString("mandi_name", "APMC Mandi"),
                                district = obj.optString("district", ""),
                                state = obj.optString("state", "Karnataka"),
                                distanceKm = obj.optDouble("distance_km", 15.0),
                                modalPricePerKg = obj.optDouble("modal_price_per_kg", 25.0),
                                grossRevenue = obj.optDouble("gross_revenue", 5000.0),
                                transportCost = obj.optDouble("transport_cost", 300.0),
                                commissionCost = obj.optDouble("commission_cost", 350.0),
                                commissionPct = obj.optDouble("commission_pct", 7.0),
                                unloadingCost = obj.optDouble("unloading_cost", 50.0),
                                weighingFee = obj.optDouble("weighing_fee", 30.0),
                                totalDeductions = obj.optDouble("total_deductions", 730.0),
                                netTakeHome = obj.optDouble("net_take_home", 4270.0),
                                effectiveRatePerKg = obj.optDouble("effective_rate_per_kg", 21.35),
                                isTrap = obj.optBoolean("is_trap", false),
                                trapReason = obj.optString("trap_reason", ""),
                                isRecommended = obj.optBoolean("is_recommended", false),
                                transitLossCost = obj.optDouble("transit_loss_cost", obj.optJSONObject("itemized_deductions")?.optDouble("transit_loss_cost", 0.0) ?: 0.0),
                                transitLossPct = obj.optDouble("transit_loss_pct", obj.optJSONObject("itemized_deductions")?.optDouble("transit_loss_pct", 0.0) ?: 0.0)
                            )
                        )
                    }
                    mandisList = list.sortedWith(
                        compareByDescending<MandiCardData> { it.isRecommended }
                            .thenByDescending { it.netTakeHome }
                    )
                } else {
                    mandisList = getDefaultMandiData(crop, quantityKg)
                }
            } catch (e: Exception) {
                mandisList = getDefaultMandiData(crop, quantityKg)
            } finally {
                isLoading = false
            }
        }
    }

    fun playSpokenAudio() {
        if (isPlayingAudio) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (ignored: Exception) {}
            try {
                ttsEngine?.stop()
            } catch (ignored: Exception) {}
            isPlayingAudio = false
            return
        }

        isPlayingAudio = true
        scope.launch {
            try {
                val langCode = UserSession.selectedLanguage.sarvamCode
                val audioRes = ApiClient.getMandiSpokenAudio(harvestId = harvestId, language = langCode)
                val json = JSONObject(audioRes)
                val text = json.optString("summary_text", "")
                val audioBase64 = json.optString("audio_base64", "")
                spokenSummaryText = text

                var playedViaMediaPlayer = false

                if (audioBase64.isNotBlank()) {
                    try {
                        val decodedBytes: ByteArray? = Base64.decode(audioBase64, Base64.DEFAULT)
                        if (decodedBytes != null && decodedBytes.isNotEmpty()) {
                            val tempFile = File.createTempFile("mandi_tts_", ".wav", context.cacheDir)
                            FileOutputStream(tempFile).use { it.write(decodedBytes) }

                            val player = MediaPlayer().apply {
                                setDataSource(tempFile.absolutePath)
                                prepare()
                                setOnCompletionListener {
                                    isPlayingAudio = false
                                    tempFile.delete()
                                }
                                start()
                            }
                            mediaPlayer = player
                            playedViaMediaPlayer = true
                        }
                    } catch (e: Exception) {
                        playedViaMediaPlayer = false
                    }
                }

                if (!playedViaMediaPlayer && text.isNotBlank() && ttsEngine != null) {
                    val loc = if (isKannada) Locale("kn", "IN") else Locale.ENGLISH
                    ttsEngine?.language = loc
                    ttsEngine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isPlayingAudio = true
                        }
                        override fun onDone(utteranceId: String?) {
                            isPlayingAudio = false
                        }
                        override fun onError(utteranceId: String?) {
                            isPlayingAudio = false
                        }
                    })
                    ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mandi_summary_tts")
                } else if (!playedViaMediaPlayer) {
                    isPlayingAudio = false
                }
            } catch (e: Exception) {
                isPlayingAudio = false
            }
        }
    }

    LaunchedEffect(harvestId) {
        loadMandiData()
    }

    val filteredMandis = remember(mandisList, searchQuery) {
        if (searchQuery.isBlank()) mandisList
        else mandisList.filter {
            it.mandiName.contains(searchQuery, ignoreCase = true) ||
            it.district.contains(searchQuery, ignoreCase = true) ||
            it.state.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { lang ->
                UserSession.setLanguage(lang)
                isKannada = lang.code == "kn"
                showLanguageDialog = false
                loadMandiData(if (searchQuery.isNotBlank()) searchQuery else null)
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = if (isDark) SurfaceDarkCard else Color.White,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBackClick) {
                            Text(
                                text = "←",
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isKannada) "ಮಾರುಕಟ್ಟೆ ವೆಚ್ಚ & ನಿವ್ವಳ ಲಾಭ" else "Mandi Transparent Analysis",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Lot: $harvestId • ${quantityKg.toInt()}kg $crop",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Theme toggle pill
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { UserSession.toggleTheme() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isDark) "🌙" else "☀️",
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Language selector pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.2.dp, SoilCropGreen),
                            modifier = Modifier.clickable { showLanguageDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🌐 ", fontSize = 11.sp)
                                Text(
                                    text = UserSession.selectedLanguage.code.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isDark) Color(0xFF689F60) else SoilCropGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ================================================================
            // SECTION A: SPOKEN ANALYSIS CARD (SARVAM AI SAATHI)
            // ================================================================
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) SurfaceDarkCard else Color.White,
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎙️", fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = AppLocalization.t("spoken_analysis_title"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = AppLocalization.t("tap_to_listen"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Button(
                            onClick = { playSpokenAudio() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingAudio) SemanticWarningAmber else (if (isDark) Color(0xFF5E9457) else SoilCropGreen),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isPlayingAudio) AppLocalization.t("stop_audio") else "▶ " + AppLocalization.t("listen_btn"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isPlayingAudio && spokenSummaryText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, SoilCropGreen.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = if (isDark) Color(0xFF689F60) else SoilCropGreen,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = if (isKannada) "ಸರ್ವಂ AI ಸಾಥಿ ಧ್ವನಿ ಸಿದ್ಧಪಡಿಸಲಾಗುತ್ತಿದೆ..." else "Sarvam Saaras AI is synthesizing spoken analysis...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (!spokenSummaryText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = spokenSummaryText!!,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // ================================================================
            // SECTION B: PAN-INDIA MANDI SEARCH
            // ================================================================
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.length >= 3) {
                        loadMandiData(it)
                    }
                },
                placeholder = {
                    Text(
                        if (isKannada) "ಭಾರತದ ಯಾವುದೇ ಮಾರುಕಟ್ಟೆ ಹುಡುಕಿ (Search Mandi / State)..."
                        else "Search any Mandi across India (e.g. Kolar, Azadpur, Nashik)...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Text("🔍", fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = if (isDark) SurfaceDarkCard else Color.White,
                    unfocusedContainerColor = if (isDark) SurfaceDarkCard else Color.White,
                    focusedBorderColor = SoilCropGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    cursorColor = SoilCropGreen
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )

            // ================================================================
            // SECTION C: MANDI COMPARISON LIST
            // ================================================================
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = if (isDark) Color(0xFF689F60) else SoilCropGreen)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isKannada) "ಮಂಡಿ ದರಗಳನ್ನು ಹೋಲಿಕೆ ಮಾಡಲಾಗುತ್ತಿದೆ..." else "Comparing APMC Mandi rates & transport netbacks...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMandis) { mandi ->
                        MandiCardItem(
                            mandi = mandi,
                            isKannada = isKannada,
                            isDark = isDark,
                            quantityKg = quantityKg
                        )
                    }
                }
            }
        }
    }
}

data class MandiCardData(
    val mandiName: String,
    val district: String,
    val state: String,
    val distanceKm: Double,
    val modalPricePerKg: Double,
    val grossRevenue: Double,
    val transportCost: Double,
    val commissionCost: Double,
    val commissionPct: Double,
    val unloadingCost: Double,
    val weighingFee: Double,
    val totalDeductions: Double,
    val netTakeHome: Double,
    val effectiveRatePerKg: Double,
    val isTrap: Boolean,
    val trapReason: String,
    val isRecommended: Boolean,
    val transitLossCost: Double = 0.0,
    val transitLossPct: Double = 0.0
)

@Composable
fun MandiCardItem(
    mandi: MandiCardData,
    isKannada: Boolean,
    isDark: Boolean,
    quantityKg: Double
) {
    var expanded by remember { mutableStateOf(false) }

    val cardBg = if (isDark) {
        if (mandi.isRecommended) Color(0xFF1E2A20)
        else if (mandi.isTrap) Color(0xFF261D17)
        else SurfaceDarkCard
    } else {
        if (mandi.isRecommended) Color(0xFFF7FBF6)
        else if (mandi.isTrap) Color(0xFFFDFBF7)
        else Color.White
    }

    val cardBorder = if (isDark) {
        if (mandi.isRecommended) BorderStroke(1.8.dp, Color(0xFF5E9457))
        else if (mandi.isTrap) BorderStroke(1.5.dp, Color(0xFFD97706).copy(alpha = 0.6f))
        else BorderStroke(1.dp, BorderDarkSubtle)
    } else {
        if (mandi.isRecommended) BorderStroke(1.8.dp, SoilCropGreen)
        else if (mandi.isTrap) BorderStroke(1.5.dp, HarvestGoldDark.copy(alpha = 0.5f))
        else BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = cardBorder,
        shadowElevation = if (mandi.isRecommended) 4.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mandi.isRecommended) {
                    Surface(
                        color = if (isDark) Color(0xFF5E9457) else SoilCropGreen,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isKannada) "⭐ ಶಿಫಾರಸು (ಗರಿಷ್ಠ ಲಾಭ)" else "⭐ RECOMMENDED (Highest Net Profit)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else if (mandi.isTrap) {
                    Surface(
                        color = HarvestGoldDark,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isKannada) "⚠️ ದರ ಮೋಸ (ಹೆಚ್ಚು ದರ, ಕಡಿಮೆ ಲಾಭ!)" else "⚠️ HEADLINE PRICE TRAP",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "📍 ${mandi.distanceKm.toInt()} km",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Mandi / Buyer Name
            Text(
                text = mandi.mandiName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.2.sp
            )

            // 2. Location
            if (mandi.district.isNotBlank()) {
                Text(
                    text = "${mandi.district}, ${mandi.state}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3 & 4. Headline Price vs Net Take-Home (Information Hierarchy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Headline Market Price
                Column {
                    Text(
                        text = if (isKannada) "ಮಾರುಕಟ್ಟೆ ದರ (Gross):" else "Headline Price:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${mandi.modalPricePerKg}/kg",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "₹${mandi.grossRevenue.toInt()} gross",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Right Column: Final Net Cash in Hand
                Column(horizontalAlignment = Alignment.End) {
                    val highlightColor = if (mandi.isTrap) {
                        HarvestGoldDark
                    } else {
                        if (isDark) Color(0xFF689F60) else SoilCropGreen
                    }

                    Text(
                        text = if (isKannada) "ಕೈಗೆ ಸಿಗುವ ನಿವ್ವಳ ಹಣ (Net):" else "Final Net Take-Home:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = highlightColor
                    )
                    Text(
                        text = "₹${mandi.netTakeHome.toInt()}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = highlightColor
                    )
                    Text(
                        text = "Eff. ₹${mandi.effectiveRatePerKg}/kg",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = highlightColor
                    )
                }
            }

            // Trap warning explanation box
            if (mandi.isTrap && mandi.trapReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF2E1C06) else Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, HarvestGoldDark.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 ${mandi.trapReason}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFFDE68A) else HarvestGoldDark,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // 5. Expandable Transparent Deductions Toggle
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = (if (isDark) Color(0xFF5E9457) else SoilCropGreen).copy(alpha = 0.10f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = if (expanded) (if (isKannada) "▲ ವೆಚ್ಚದ ವಿವರಗಳನ್ನು ಮರೆಮಾಡಿ" else "▲ Hide Itemized Charges")
                        else (if (isKannada) "▼ ಎಲ್ಲಾ ವೆಚ್ಚಗಳ ಪಾರದರ್ಶಕ ವಿವರ ನೋಡಿ" else "▼ View Transparent Itemized Charges"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF689F60) else SoilCropGreen
                    )
                }
            }

            // 6. Itemized Deductions Breakdown
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (isKannada) "ಪಾರದರ್ಶಕ ಕಡಿತಗಳ ವಿವರ (Deduction Breakdown):" else "Transparent Cost Itemization:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CostItemRow(
                        label = if (isKannada) "ಸಾರಿಗೆ ವೆಚ್ಚ (Transport)" else "Distance Transport",
                        amount = "₹${mandi.transportCost.toInt()}"
                    )
                    CostItemRow(
                        label = if (isKannada) "ಎಪಿಎಂಸಿ ಕಮಿಷನ್ (${mandi.commissionPct}%)" else "APMC Commission (${mandi.commissionPct}%)",
                        amount = "₹${mandi.commissionCost.toInt()}"
                    )
                    CostItemRow(
                        label = if (isKannada) "ಕ್ರೇಟ್ ಇಳಿಸುವ ವೆಚ್ಚ (Unloading)" else "Crate Unloading Fee",
                        amount = "₹${mandi.unloadingCost.toInt()}"
                    )
                    CostItemRow(
                        label = if (isKannada) "ತೂಕದ ಶುಲ್ಕ (Weighing)" else "Weighing Slip Fee",
                        amount = "₹${mandi.weighingFee.toInt()}"
                    )
                    if (mandi.transitLossCost > 0.0) {
                        CostItemRow(
                            label = if (isKannada) "ಸಾಗಣೆ ನಷ್ಟ / Transit Loss (${mandi.transitLossPct}%)" else "Transit Spoilage Loss (${mandi.transitLossPct}%)",
                            amount = "₹${mandi.transitLossCost.toInt()}"
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 5.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    CostItemRow(
                        label = if (isKannada) "ಒಟ್ಟು ಕಡಿತಗಳು (Total Costs)" else "Total Deductions",
                        amount = "₹${mandi.totalDeductions.toInt()}",
                        isBold = true
                    )
                }
            }
        }
    }
}

@Composable
fun CostItemRow(label: String, amount: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = amount,
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isBold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun getDefaultMandiData(crop: String, quantityKg: Double): List<MandiCardData> {
    val q = if (quantityKg > 0) quantityKg else 200.0
    val kolarGross = q * 24.0
    val kolarComm = kolarGross * 0.065
    val kolarDed = 350.0 + kolarComm + 40.0 + 30.0

    val bangarGross = q * 27.50
    val bangarComm = bangarGross * 0.08
    val bangarDed = 620.0 + bangarComm + 64.0 + 50.0

    val chintGross = q * 22.0
    val chintComm = chintGross * 0.05
    val chintDed = 480.0 + chintComm + 40.0 + 30.0

    val azadGross = q * 42.0
    val azadDed = 5800.0 + (azadGross * 0.08) + 200.0 + 100.0

    val fpoGross = q * 25.0
    val fpoDed = 120.0 + 0.0 + 20.0 + 0.0

    return listOf(
        MandiCardData(
            mandiName = "Virtual FPO Direct Buyer (Aggregated Pool)",
            district = "Kolar",
            state = "Karnataka",
            distanceKm = 5.0,
            modalPricePerKg = 25.0,
            grossRevenue = fpoGross,
            transportCost = 120.0,
            commissionCost = 0.0,
            commissionPct = 0.0,
            unloadingCost = 20.0,
            weighingFee = 0.0,
            totalDeductions = fpoDed,
            netTakeHome = fpoGross - fpoDed,
            effectiveRatePerKg = (fpoGross - fpoDed) / q,
            isTrap = false,
            trapReason = "",
            isRecommended = true
        ),
        MandiCardData(
            mandiName = "Kolar APMC Mandi",
            district = "Kolar",
            state = "Karnataka",
            distanceKm = 12.0,
            modalPricePerKg = 24.0,
            grossRevenue = kolarGross,
            transportCost = 350.0,
            commissionCost = kolarComm,
            commissionPct = 6.5,
            unloadingCost = 40.0,
            weighingFee = 30.0,
            totalDeductions = kolarDed,
            netTakeHome = kolarGross - kolarDed,
            effectiveRatePerKg = (kolarGross - kolarDed) / q,
            isTrap = false,
            trapReason = "",
            isRecommended = false
        ),
        MandiCardData(
            mandiName = "Bangarapet APMC Mandi",
            district = "Kolar",
            state = "Karnataka",
            distanceKm = 24.0,
            modalPricePerKg = 27.50,
            grossRevenue = bangarGross,
            transportCost = 620.0,
            commissionCost = bangarComm,
            commissionPct = 8.0,
            unloadingCost = 64.0,
            weighingFee = 50.0,
            totalDeductions = bangarDed,
            netTakeHome = bangarGross - bangarDed,
            effectiveRatePerKg = (bangarGross - bangarDed) / q,
            isTrap = false,
            trapReason = "",
            isRecommended = false
        ),
        MandiCardData(
            mandiName = "Chintamani APMC Mandi",
            district = "Chikkaballapura",
            state = "Karnataka",
            distanceKm = 42.0,
            modalPricePerKg = 22.0,
            grossRevenue = chintGross,
            transportCost = 480.0,
            commissionCost = chintComm,
            commissionPct = 5.0,
            unloadingCost = 40.0,
            weighingFee = 30.0,
            totalDeductions = chintDed,
            netTakeHome = chintGross - chintDed,
            effectiveRatePerKg = (chintGross - chintDed) / q,
            isTrap = false,
            trapReason = "",
            isRecommended = false
        ),
        MandiCardData(
            mandiName = "Azadpur APMC Mandi (Delhi)",
            district = "North Delhi",
            state = "Delhi",
            distanceKm = 2100.0,
            modalPricePerKg = 42.0,
            grossRevenue = azadGross,
            transportCost = 5800.0,
            commissionCost = azadGross * 0.08,
            commissionPct = 8.0,
            unloadingCost = 200.0,
            weighingFee = 100.0,
            totalDeductions = azadDed,
            netTakeHome = azadGross - azadDed,
            effectiveRatePerKg = (azadGross - azadDed) / q,
            isTrap = true,
            trapReason = if (q <= 500) "₹42/kg headline price is attractive, but ₹5800 distance transport causes a negative net loss of ₹${(azadDed - azadGross).toInt()}!" else "High transport cost wipes out price gain.",
            isRecommended = false
        )
    )
}
