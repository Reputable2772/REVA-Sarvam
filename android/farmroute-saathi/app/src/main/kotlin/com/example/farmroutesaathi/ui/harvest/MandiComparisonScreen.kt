package com.example.farmroutesaathi.ui.harvest

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.ApiClient
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
    var isKannada by remember { mutableStateOf(UserSession.isKannadaPreferred) }
    var isLoading by remember { mutableStateOf(true) }
    var crop by remember { mutableStateOf("Tomato") }
    var quantityKg by remember { mutableStateOf(200.0) }
    var farmerLocation by remember { mutableStateOf("Kolar, Karnataka") }
    var mandisList by remember { mutableStateOf<List<MandiCardData>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // Audio TTS playback state (Dual-Layer: Sarvam Neural Audio + Android Native TTS)
    var isPlayingAudio by remember { mutableStateOf(false) }
    var spokenSummaryText by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

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
        } catch (_: Exception) {
            try {
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = if (isKannada) Locale("kn", "IN") else Locale.ENGLISH
                    }
                }
                ttsEngine = tts
            } catch (_: Exception) {}
        }

        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (_: Exception) {}
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {}
        }
    }

    fun loadMandiData(targetQuery: String? = null) {
        isLoading = true
        scope.launch {
            try {
                val res = ApiClient.getMandiAnalysis(harvestId, targetQuery)
                val json = JSONObject(res)
                if (json.optBoolean("success", false)) {
                    crop = json.optString("crop", "Tomato")
                    quantityKg = json.optDouble("quantity_kg", 200.0)
                    farmerLocation = json.optString("farmer_location", "Kolar, Karnataka")

                    val arr = json.optJSONArray("mandis") ?: JSONArray()
                    val parsed = mutableListOf<MandiCardData>()
                    for (i in 0 until arr.length()) {
                        val m = arr.getJSONObject(i)
                        val ded = m.optJSONObject("itemized_deductions") ?: JSONObject()
                        parsed.add(
                            MandiCardData(
                                mandiName = m.optString("mandi_name", "Local Mandi"),
                                district = m.optString("district", ""),
                                state = m.optString("state", ""),
                                distanceKm = m.optDouble("distance_km", 25.0),
                                modalPricePerKg = m.optDouble("modal_price_per_kg", 24.0),
                                grossRevenue = m.optDouble("gross_revenue", 4800.0),
                                transportCost = ded.optDouble("transport_cost", 350.0),
                                commissionCost = ded.optDouble("commission_cost", 300.0),
                                commissionPct = ded.optDouble("commission_pct", 6.5),
                                unloadingCost = ded.optDouble("unloading_cost", 40.0),
                                weighingFee = ded.optDouble("weighing_fee", 30.0),
                                totalDeductions = ded.optDouble("total_deductions", 720.0),
                                netTakeHome = m.optDouble("net_take_home", 4080.0),
                                effectiveRatePerKg = m.optDouble("effective_rate_per_kg", 20.4),
                                isTrap = m.optBoolean("is_trap", false),
                                trapReason = m.optString("trap_reason", ""),
                                isRecommended = m.optBoolean("is_recommended", false)
                            )
                        )
                    }
                    if (parsed.isEmpty()) {
                        parsed.addAll(getDefaultMandiData(crop, quantityKg))
                    }
                    mandisList = parsed
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
            } catch (_: Exception) {}
            try {
                ttsEngine?.stop()
            } catch (_: Exception) {}
            isPlayingAudio = false
            return
        }

        isPlayingAudio = true
        scope.launch {
            try {
                val res = ApiClient.getMandiVoiceExplanation(harvestId, if (isKannada) "kn-IN" else "en-IN")
                val json = JSONObject(res)
                val audioB64 = json.optString("audio_base64", "")
                var text = json.optString("summary_text", "")
                if (text.isBlank()) {
                    text = if (isKannada) "ನಿಮ್ಮ ${quantityKg.toInt()} ಕೆಜಿ ${crop} ಬೆಳೆಗೆ ಸಮೀಪದ ಮಾರುಕಟ್ಟೆಗಳಲ್ಲಿ ಅತ್ಯುತ್ತಮ ನಿವ್ವಳ ಲಾಭ ಪಡೆಯಬಹುದು. ದೂರದ ಮಾರುಕಟ್ಟೆಗಳ ಸಾರಿಗೆ ವೆಚ್ಚ ಎಚ್ಚರಿಕೆಯಿಂದ ಪರಿಶೀಲಿಸಿ."
                           else "For your ${quantityKg.toInt()} kg of ${crop}, direct pooled markets offer the best net take-home cash. Beware of freight traps at distant mandis."
                }
                spokenSummaryText = text

                var playedViaMediaPlayer = false
                if (audioB64.isNotBlank()) {
                    val audioBytes = Base64.decode(audioB64, Base64.DEFAULT)
                    // Ensure it is not an empty header (< 100 bytes)
                    if (audioBytes.size > 100) {
                        try {
                            val tempFile = File.createTempFile("mandi_summary", ".wav", context.cacheDir)
                            FileOutputStream(tempFile).use { it.write(audioBytes) }

                            val player = MediaPlayer().apply {
                                setDataSource(tempFile.absolutePath)
                                prepare()
                                setOnCompletionListener {
                                    isPlayingAudio = false
                                    tempFile.delete()
                                }
                                setOnErrorListener { _, _, _ ->
                                    isPlayingAudio = false
                                    tempFile.delete()
                                    true
                                }
                                start()
                            }
                            mediaPlayer = player
                            playedViaMediaPlayer = true
                        } catch (e: Exception) {
                            playedViaMediaPlayer = false
                        }
                    }
                }

                // Fallback to Android Native TTS if Sarvam audio is missing or failed
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isKannada) "ಮಾರುಕಟ್ಟೆ ವೆಚ್ಚ & ನಿವ್ವಳ ಲಾಭ" else "Mandi Transparent Analysis",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Lot: $harvestId • ${quantityKg.toInt()}kg $crop",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    // Language Toggle Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = HarvestGold,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { isKannada = !isKannada }
                    ) {
                        Text(
                            text = if (isKannada) "EN" else "ಕನ್ನಡ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ForestGreenDeep,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreenDeep
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Audio Explanation Header Card
            Card(
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isKannada) "ಧ್ವನಿ ಮಾರ್ಗದರ್ಶಿ (ಸರ್ವಂ AI ಸಾಥಿ)" else "Spoken Analysis (Sarvam AI Saathi)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isKannada) "ಯಾವ ಮಾರುಕಟ್ಟೆ ಹೆಚ್ಚು ಲಾಭ ನೀಡುತ್ತದೆ ಎಂದು ಕೇಳಿ" else "Tap to hear transparent cost & trap comparison",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }

                        Button(
                            onClick = { playSpokenAudio() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingAudio) SemanticWarningAmber else ForestGreenDeep
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isPlayingAudio) (if (isKannada) "■ ನಿಲ್ಲಿಸಿ" else "■ Stop") else (if (isKannada) "▶ ಆಲಿಸಿ 🔊" else "▶ Listen 🔊"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isPlayingAudio && spokenSummaryText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ForestGreenDeep, RoundedCornerShape(8.dp))
                                .border(1.dp, FreshLimeAccent, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = FreshLimeAccent,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = if (isKannada) "ಸರ್ವಂ AI ಸಾಥಿ ಧ್ವನಿ ಸಿದ್ಧಪಡಿಸಲಾಗುತ್ತಿದೆ..." else "Sarvam AI is synthesizing spoken analysis...",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    } else if (!spokenSummaryText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = spokenSummaryText!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ForestGreenDeep, RoundedCornerShape(8.dp))
                                .border(1.dp, FreshLimeAccent, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        )
                    }
                }
            }

            // Pan-India Search Bar
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
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ForestGreenDeep)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMandis) { mandi ->
                        MandiCardItem(mandi = mandi, isKannada = isKannada, quantityKg = quantityKg)
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
    val isRecommended: Boolean
)

@Composable
fun MandiCardItem(
    mandi: MandiCardData,
    isKannada: Boolean,
    quantityKg: Double
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mandi.isRecommended) HarvestGold.copy(alpha = 0.12f)
            else if (mandi.isTrap) SemanticWarningAmber.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (mandi.isRecommended || mandi.isTrap) 1.5.dp else 0.5.dp,
                color = if (mandi.isRecommended) HarvestGoldDark
                else if (mandi.isTrap) SemanticWarningAmber
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mandi.isRecommended) {
                    Surface(
                        color = ForestGreenDeep,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isKannada) "⭐ ಶಿಫಾರಸು ಮಾಡಲಾಗಿದೆ (ಗರಿಷ್ಠ ಲಾಭ)" else "⭐ RECOMMENDED (Highest Net Profit)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (mandi.isTrap) {
                    Surface(
                        color = SemanticWarningAmber,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isKannada) "⚠️ ದರ ಮೋಸ (ಹೆಚ್ಚು ದರ, ಕಡಿಮೆ ಲಾಭ!)" else "⚠️ HEADLINE PRICE TRAP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "📍 ${mandi.distanceKm.toInt()} km",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mandi Name
            Text(
                text = mandi.mandiName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (mandi.district.isNotBlank()) {
                Text(
                    text = "${mandi.district}, ${mandi.state}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Comparison row: Headline Price vs Net Cash in Hand
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isKannada) "ಮಾರುಕಟ್ಟೆ ದರ (Gross):" else "Headline Price:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Text(
                        text = "₹${mandi.modalPricePerKg}/kg",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "₹${mandi.grossRevenue.toInt()} gross",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isKannada) "ಕೈಗೆ ಸಿಗುವ ನಿವ್ವಳ ಹಣ (Net):" else "Final Net Take-Home:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mandi.isTrap) SemanticWarningAmber else Color(0xFF4ADE80)
                    )
                    Text(
                        text = "₹${mandi.netTakeHome.toInt()}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (mandi.isTrap) SemanticWarningAmber else FreshLimeAccent
                    )
                    Text(
                        text = "Eff. ₹${mandi.effectiveRatePerKg}/kg",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4ADE80)
                    )
                }
            }

            // Trap warning explanation
            if (mandi.isTrap && mandi.trapReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SemanticWarningAmber.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 ${mandi.trapReason}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB74D)
                        )
                    }
                }
            }

            // Expandable Itemized Deductions
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = if (expanded) (if (isKannada) "▲ ವೆಚ್ಚದ ವಿವರಗಳನ್ನು ಮರೆಮಾಡಿ" else "▲ Hide Itemized Charges")
                    else (if (isKannada) "▼ ಎಲ್ಲಾ ವೆಚ್ಚಗಳ ಪಾರದರ್ಶಕ ವಿವರ ನೋಡಿ" else "▼ View Transparent Itemized Charges"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FreshLimeAccent,
                    modifier = Modifier.padding(vertical = 6.dp).align(Alignment.CenterHorizontally)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (isKannada) "ಪಾರದರ್ಶಕ ಕಡಿತಗಳ ವಿವರ (Deduction Breakdown):" else "Transparent Cost Itemization:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CostItemRow(label = if (isKannada) "ಸಾರಿಗೆ ವೆಚ್ಚ (Transport)" else "Distance Transport", amount = "₹${mandi.transportCost.toInt()}")
                    CostItemRow(label = if (isKannada) "ಎಪಿಎಂಸಿ ಕಮಿಷನ್ (${mandi.commissionPct}%)" else "APMC Commission (${mandi.commissionPct}%)", amount = "₹${mandi.commissionCost.toInt()}")
                    CostItemRow(label = if (isKannada) "ಕ್ರೇಟ್ ಇಳಿಸುವ ವೆಚ್ಚ (Unloading)" else "Crate Unloading Fee", amount = "₹${mandi.unloadingCost.toInt()}")
                    CostItemRow(label = if (isKannada) "ತೂಕದ ಶುಲ್ಕ (Weighing)" else "Weighing Slip Fee", amount = "₹${mandi.weighingFee.toInt()}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    CostItemRow(label = if (isKannada) "ಒಟ್ಟು ಕಡಿತಗಳು (Total Costs)" else "Total Deductions", amount = "₹${mandi.totalDeductions.toInt()}", isBold = true)
                }
            }
        }
    }
}

@Composable
fun CostItemRow(label: String, amount: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = amount,
            fontSize = 10.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

    val mulbagalGross = q * 21.0
    val mulbagalComm = mulbagalGross * 0.06
    val mulbagalDed = 120.0 + mulbagalComm + 32.0 + 20.0

    val fpoGross = q * 28.50
    val fpoDed = 196.0

    return listOf(
        MandiCardData(
            mandiName = "Virtual FPO Direct Buyer (Aggregated Pool)",
            district = "Kolar",
            state = "Karnataka",
            distanceKm = 10.0,
            modalPricePerKg = 28.50,
            grossRevenue = fpoGross,
            transportCost = 180.0,
            commissionCost = 0.0,
            commissionPct = 0.0,
            unloadingCost = 16.0,
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
            distanceKm = 30.0,
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
            mandiName = "Bangarpet APMC (Lower Net Return)",
            district = "Kolar",
            state = "Karnataka",
            distanceKm = 42.0,
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
            isTrap = true,
            trapReason = "Headline Rate is ₹3.50/kg higher, but ₹620 rural transport and 8% APMC commission results in lower net take-home!",
            isRecommended = false
        ),
        MandiCardData(
            mandiName = "Mulbagal Sub-Market",
            district = "Kolar",
            state = "Karnataka",
            distanceKm = 5.0,
            modalPricePerKg = 21.0,
            grossRevenue = mulbagalGross,
            transportCost = 120.0,
            commissionCost = mulbagalComm,
            commissionPct = 6.0,
            unloadingCost = 32.0,
            weighingFee = 20.0,
            totalDeductions = mulbagalDed,
            netTakeHome = mulbagalGross - mulbagalDed,
            effectiveRatePerKg = (mulbagalGross - mulbagalDed) / q,
            isTrap = false,
            trapReason = "",
            isRecommended = false
        )
    )
}
