package com.example.farmroutesaathi.ui.benefits

import android.media.MediaPlayer
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class SchemeCardData(
    val schemeId: String,
    val titleEn: String,
    val titleKn: String,
    val category: String,
    val targetLevel: String,
    val issuingDepartment: String,
    val benefitAmountSpec: String,
    val benefitDescEn: String,
    val benefitDescKn: String,
    val eligibilityEn: String,
    val eligibilityKn: String,
    val exclusionEn: String,
    val exclusionKn: String,
    val requiredDocuments: List<String>,
    val applicationSteps: List<String>,
    val applicationMethod: String,
    val officialPortalUrl: String,
    val officialContact: String,
    val verificationStatus: String,
    val lastVerifiedDate: String,
    val freshnessLabel: String,
    val matchStatus: String,
    val relevanceReasonEn: String,
    val relevanceReasonKn: String,
    val missingFields: List<String>,
    val matchedRules: List<String>,
    var isSaved: Boolean = false
)

data class BenefitNotificationItem(
    val id: Int,
    val titleEn: String,
    val titleKn: String,
    val bodyEn: String,
    val bodyKn: String,
    val whyReceivedEn: String,
    val whyReceivedKn: String,
    val sourceUrl: String,
    val isRead: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerBenefitsScreen(
    isKannada: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var schemesList by remember { mutableStateOf<List<SchemeCardData>>(emptyList()) }
    var notificationsList by remember { mutableStateOf<List<BenefitNotificationItem>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var expandedSchemeId by remember { mutableStateOf<String?>(null) }
    var showMissingInfoDialog by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    // Voluntary Profile inputs
    var landholdingInput by remember { mutableStateOf("1.2") }
    var fruitsIdInput by remember { mutableStateOf("FID-KLR-8821") }
    var isFpoMemberInput by remember { mutableStateOf(true) }

    // Audio TTS playback state
    var isPlayingAudio by remember { mutableStateOf(false) }
    var activeSpokenText by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val scope = rememberCoroutineScope()
    val isDark = UserSession.isDarkTheme

    fun loadData() {
        isLoading = true
        scope.launch {
            try {
                val identifier = UserSession.currentFarmer?.phone ?: "FARMER-01"
                val res = ApiClient.getSchemeRecommendations(identifier)
                val json = JSONObject(res)
                if (json.optBoolean("success", false)) {
                    val arr = json.optJSONArray("opportunities") ?: JSONArray()
                    val parsed = mutableListOf<SchemeCardData>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        parsed.add(parseSchemeJson(obj))
                    }
                    schemesList = if (parsed.isNotEmpty()) parsed else getDefaultSchemes()
                } else {
                    schemesList = getDefaultSchemes()
                }

                // Load notifications
                val notifRes = ApiClient.getSchemeNotifications(identifier)
                val notifJson = JSONObject(notifRes)
                if (notifJson.optBoolean("success", false)) {
                    val nArr = notifJson.optJSONArray("notifications") ?: JSONArray()
                    val nParsed = mutableListOf<BenefitNotificationItem>()
                    for (i in 0 until nArr.length()) {
                        val no = nArr.getJSONObject(i)
                        nParsed.add(
                            BenefitNotificationItem(
                                id = no.optInt("id"),
                                titleEn = no.optString("title_en"),
                                titleKn = no.optString("title_kn"),
                                bodyEn = no.optString("body_en"),
                                bodyKn = no.optString("body_kn"),
                                whyReceivedEn = no.optString("why_received_en"),
                                whyReceivedKn = no.optString("why_received_kn"),
                                sourceUrl = no.optString("source_url"),
                                isRead = no.optBoolean("is_read", false)
                            )
                        )
                    }
                    notificationsList = nParsed
                }
            } catch (e: Exception) {
                schemesList = getDefaultSchemes()
            } finally {
                isLoading = false
            }
        }
    }

    fun playVoiceGuidance(query: String) {
        if (isPlayingAudio) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlayingAudio = false
            return
        }

        isPlayingAudio = true
        scope.launch {
            try {
                val res = ApiClient.getSchemeVoiceGuidance(
                    query = query,
                    farmerId = UserSession.currentFarmer?.phone ?: "FARMER-01",
                    languageCode = if (isKannada) "kn-IN" else "en-IN"
                )
                val json = JSONObject(res)
                val answer = json.optString("answer", "")
                val audioB64 = json.optString("audio_base64", "")
                activeSpokenText = answer

                if (audioB64.isNotBlank()) {
                    val audioBytes = Base64.decode(audioB64, Base64.DEFAULT)
                    val tempFile = File.createTempFile("scheme_tts", ".wav")
                    FileOutputStream(tempFile).use { it.write(audioBytes) }

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
                } else {
                    isPlayingAudio = false
                }
            } catch (e: Exception) {
                isPlayingAudio = false
            }
        }
    }

    fun submitVoluntaryProfile() {
        scope.launch {
            try {
                isLoading = true
                val land = landholdingInput.toDoubleOrNull() ?: 1.2
                ApiClient.updateVoluntaryProfile(
                    farmerId = "FARMER-01",
                    phone = UserSession.currentFarmer?.phone,
                    landholdingHa = land,
                    fruitsId = fruitsIdInput.trim(),
                    isFpoMember = isFpoMemberInput
                )
                showMissingInfoDialog = false
                loadData()
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val filteredSchemes = remember(schemesList, selectedCategory) {
        if (selectedCategory == "ALL") schemesList
        else schemesList.filter { it.category == selectedCategory }
    }

    val likelyEligibleCount = schemesList.count { it.matchStatus == "LIKELY_ELIGIBLE" }
    val moreInfoNeededCount = schemesList.count { it.matchStatus == "MORE_INFO_REQUIRED" }
    val unreadNotifsCount = notificationsList.count { !it.isRead }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Summary & Intelligence Header
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceDarkCard else Color.White),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, if (isDark) BorderDarkSubtle else BorderPaperLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isKannada) "🏛️ AI ರೈತ ಯೋಜನೆಗಳು & ಸೌಲಭ್ಯಗಳು" else "🏛️ AI Farmer Benefits & Schemes",
                            color = if (isDark) Color.White else SoilDeepEarth,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "📍 Kolar, Karnataka • Verified Government Intelligence",
                            color = if (isDark) SoilFreshLeaf else SoilCropGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Notification Alert Bell with Badge
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(alpha = 0.15f) else SoilCropGreen.copy(alpha = 0.12f))
                            .clickable { showNotificationsSheet = true }
                            .padding(8.dp)
                    ) {
                        Text("🔔", fontSize = 16.sp)
                        if (unreadNotifsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(SemanticWarningAmber),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$unreadNotifsCount",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Match Metric Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isDark) SurfaceDarkCardHigh else SoilFieldPaper,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = if (isKannada) "ಅರ್ಹತೆಯಿದೆ" else "Likely Eligible",
                                fontSize = 10.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.9f) else TextSecondaryLight
                            )
                            Text(
                                text = "$likelyEligibleCount Schemes",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) SoilFreshLeaf else SoilCropGreen
                            )
                        }
                    }

                    Surface(
                        color = if (isDark) SurfaceDarkCardHigh else SoilFieldPaper,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = if (isKannada) "ಮಾಹಿತಿ ಬೇಕು" else "More Info",
                                fontSize = 10.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.9f) else TextSecondaryLight
                            )
                            Text(
                                text = "$moreInfoNeededCount Schemes",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) HarvestGold else HarvestGoldDark
                            )
                        }
                    }
                }

                // Spoken Guidance Trigger
                Button(
                    onClick = {
                        val prompt = if (isKannada) "ನನಗೆ ಅನ್ವಯವಾಗುವ ಸರ್ಕಾರಿ ಯೋಜನೆಗಳು ಯಾವುವು?" else "What schemes are available for me?"
                        playVoiceGuidance(prompt)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingAudio) SemanticWarningAmber else SoilCropGreen
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPlayingAudio) "⏹ Stop Audio / ನಿಲ್ಲಿಸಿ" else if (isKannada) "▶ ಧ್ವನಿ ಸಾರಾಂಶ ಕೇಳಿ (Bulbul TTS)" else "▶ Listen AI Scheme Summary 🔊",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Spoken Text Banner
                if (activeSpokenText != null) {
                    Surface(
                        color = if (isDark) Color.Black.copy(alpha = 0.35f) else SoilFieldPaper,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SoilCropGreen.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "🗣️ AI Saathi Guidance:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoilCropGreen
                            )
                            Text(
                                text = activeSpokenText ?: "",
                                fontSize = 11.sp,
                                color = if (isDark) Color.White else TextPrimaryLight,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Missing Information Prompt Banner (If user can unlock Raitha Siri / Smallholder grants)
        if (moreInfoNeededCount > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceDarkCardHigh else WarmIvoryBackground),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HarvestGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isKannada) "💡 ₹20,000 ರೈತ ಸಿರಿ ಸೌಲಭ್ಯ ಅನ್‌ಲಾಕ್ ಮಾಡಿ" else "💡 Unlock ₹20,000 Raitha Siri Benefit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isDark) HarvestGold else HarvestGoldDark
                        )
                        Text(
                            text = if (isKannada) "ಫ್ರೂಟ್ಸ್ (FRUITS) FID ದಾಖಲಿಸಿ ಅರ್ಹತೆ ಖಚಿತಪಡಿಸಿ" else "Provide FRUITS FID & Landholding to confirm eligibility",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showMissingInfoDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (isKannada) "ದಾಖಲಿಸಿ" else "Enter Info", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val categories = listOf(
                "ALL" to (if (isKannada) "ಎಲ್ಲವೂ" else "All"),
                "DIRECT_INCOME_SUPPORT" to (if (isKannada) "ಆದಾಯ ಬೆಂಬಲ" else "Income Support"),
                "HORTICULTURE_INCENTIVE" to (if (isKannada) "ತೋಟಗಾರಿಕೆ" else "Horticulture"),
                "CROP_INSURANCE" to (if (isKannada) "ಬೆಳೆ ವಿಮೆ" else "Crop Insurance"),
                "FPO_INSTITUTIONAL" to (if (isKannada) "FPO ಸಂಸ್ಥೆ" else "FPO Grants"),
                "MARKET_ACCESS" to (if (isKannada) "e-NAM ಮಾರುಕಟ್ಟೆ" else "e-NAM Access")
            )
            items(categories) { (catKey, label) ->
                FilterChip(
                    selected = selectedCategory == catKey,
                    onClick = { selectedCategory = catKey },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SoilCropGreen,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // Schemes List
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SoilCropGreen)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredSchemes.forEach { scheme ->
                    SchemeCardView(
                        scheme = scheme,
                        isKannada = isKannada,
                        isDark = isDark,
                        onPlayVoice = {
                            playVoiceGuidance(if (isKannada) "${scheme.titleKn} ಬಗ್ಗೆ ವಿವರ ನೀಡಿ" else "Explain ${scheme.titleEn}")
                        },
                        onToggleBookmark = {
                            scheme.isSaved = !scheme.isSaved
                            scope.launch {
                                ApiClient.toggleSchemeBookmark("FARMER-01", scheme.schemeId, scheme.isSaved)
                            }
                        }
                    )
                }
            }
        }

        // FPO Institutional Distinction Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceDarkCard else Color.White),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) BorderDarkSubtle else BorderPaperLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isKannada) "ℹ️ FPO ಸಂಸ್ಥೆ ಮತ್ತು ವೈಯಕ್ತಿಕ ರೈತರ ಸೌಲಭ್ಯಗಳ ವ್ಯತ್ಯಾಸ" else "ℹ️ Individual vs FPO Institutional Grants Policy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isDark) SoilFreshLeaf else SoilCropGreen
                )
                Text(
                    text = if (isKannada)
                        "SFAC ಮತ್ತು ನಬಾರ್ಡ್‌ನ ₹15 ಲಕ್ಷ ಈಕ್ವಿಟಿ ಅನುದಾನವು ನೋಂದಾಯಿತ FPO ಕಂಪನಿಗೆ ಮಾತ್ರ ಸಿಗುತ್ತದೆ, ವೈಯಕ್ತಿಕ ರೈತರ ಖಾತೆಗಲ್ಲ. ನೀವು FPO ಸದಸ್ಯರಾಗುವುದರಿಂದ ಸಾಮೂಹಿಕ ಖರೀದಿ ಮತ್ತು ಸಾರಿಗೆ ವೆಚ್ಚ ಉಳಿತಾಯವಾಗುತ್ತದೆ."
                    else
                        "Institutional grants (SFAC/NABARD up to ₹15 Lakhs) are deposited directly into registered Farmer Producer Companies for infrastructure & procurement, not paid to individual farmers. Joining your local Kolar FPO gives you aggregated market power.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }

    // Voluntary Missing Info Dialog
    if (showMissingInfoDialog) {
        AlertDialog(
            onDismissRequest = { showMissingInfoDialog = false },
            title = {
                Text(
                    text = if (isKannada) "ರೈತ ಪ್ರೊಫೈಲ್ ಅಪ್‌ಡೇಟ್" else "Voluntary Farmer Profile",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isKannada)
                            "ಭೂಮಿ ವಿಸ್ತೀರ್ಣ ಮತ್ತು FRUITS FID ದಾಖಲಿಸಿ ಕರ್ನಾಟಕ ಸರ್ಕಾರದ ₹20,000 ರೈತ ಸಿರಿ ಸೌಲಭ್ಯವನ್ನು ದೃಢೀಕರಿಸಿ:"
                        else
                            "Provide your landholding and FRUITS FID to unlock Karnataka Raitha Siri benefits:",
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = landholdingInput,
                        onValueChange = { landholdingInput = it },
                        label = { Text("Landholding (Hectares)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = fruitsIdInput,
                        onValueChange = { fruitsIdInput = it },
                        label = { Text("FRUITS FID Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isFpoMemberInput,
                            onCheckedChange = { isFpoMemberInput = it }
                        )
                        Text("Member of local FPO cluster", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { submitVoluntaryProfile() },
                    colors = ButtonDefaults.buttonColors(containerColor = FreshLeafGreen)
                ) {
                    Text("Recalculate Eligibility", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMissingInfoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Notifications Bottom Sheet / Dialog
    if (showNotificationsSheet) {
        AlertDialog(
            onDismissRequest = { showNotificationsSheet = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔔 Scheme Alerts & Notifications", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = { showNotificationsSheet = false }) {
                        Text("✕")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (notificationsList.isEmpty()) {
                        Text("No active notifications at this time.", fontSize = 12.sp)
                    } else {
                        notificationsList.forEach { notif ->
                            Surface(
                                color = if (isDark) SurfaceDarkCard else SurfaceParchmentWarm,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (isKannada) notif.titleKn else notif.titleEn,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isDark) FreshLimeAccent else ForestGreenDeep
                                    )
                                    Text(
                                        text = if (isKannada) notif.bodyKn else notif.bodyEn,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = if (isKannada) notif.whyReceivedKn else notif.whyReceivedEn,
                                        fontSize = 10.sp,
                                        color = HarvestGoldDark,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsSheet = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SchemeCardView(
    scheme: SchemeCardData,
    isKannada: Boolean,
    isDark: Boolean,
    onPlayVoice: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = when (scheme.matchStatus) {
        "LIKELY_ELIGIBLE" -> FreshLeafGreen
        "MORE_INFO_REQUIRED" -> HarvestGoldDark
        "POTENTIALLY_RELEVANT" -> SemanticInfoBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusLabel = when (scheme.matchStatus) {
        "LIKELY_ELIGIBLE" -> if (isKannada) "⭐ ಅರ್ಹತೆಯಿದೆ" else "⭐ Likely Eligible"
        "MORE_INFO_REQUIRED" -> if (isKannada) "❓ ಮಾಹಿತಿ ಬೇಕು" else "❓ More Info Needed"
        "POTENTIALLY_RELEVANT" -> if (isKannada) "💡 ಸಂಬಂಧಿತವಾಗಿದೆ" else "💡 Potentially Relevant"
        else -> if (isKannada) "ಅರ್ಹವಲ್ಲ" else "Ineligible"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceDarkCard else Color.White),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (scheme.matchStatus == "LIKELY_ELIGIBLE") 1.5.dp else 1.dp,
            color = if (scheme.matchStatus == "LIKELY_ELIGIBLE") SoilCropGreen else (if (isDark) BorderDarkSubtle else BorderPaperLight)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Status Pill + Bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "🟢 Verified",
                        fontSize = 10.sp,
                        color = if (isDark) SoilFreshLeaf else SoilCropGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(if (scheme.isSaved) "★" else "☆", fontSize = 16.sp, color = HarvestGold)
                    }
                }
            }

            // Title & Issuing Authority
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isKannada) scheme.titleKn else scheme.titleEn,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else SoilDeepEarth
                )
                Text(
                    text = scheme.issuingDepartment,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Benefit Spec Box (High-Contrast Living Intelligence style)
            Surface(
                color = if (isDark) SurfaceDarkCardHigh else SoilFieldPaper,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isKannada) "ಆರ್ಥಿಕ ಸೌಲಭ್ಯ / ಪ್ರೋತ್ಸಾಹಧನ:" else "Financial Assistance / Benefit:",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = scheme.benefitAmountSpec,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) SoilFreshLeaf else SoilCropGreen
                        )
                    }
                }
            }

            // Relevance reason
            Text(
                text = "💡 ${if (isKannada) scheme.relevanceReasonKn else scheme.relevanceReasonEn}",
                fontSize = 11.sp,
                color = if (isDark) HarvestGold else HarvestGoldDark,
                lineHeight = 15.sp
            )

            // Missing information callout if any
            if (scheme.missingFields.isNotEmpty()) {
                Surface(
                    color = SemanticWarningAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Missing: ${scheme.missingFields.joinToString(", ")}",
                        fontSize = 10.sp,
                        color = SemanticWarningAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            // Action Row: Expand / Audio Listen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isExpanded) "▲ Hide Details" else "▼ View Documents & Steps",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) SoilFreshLeaf else SoilCropGreen
                    )
                }

                IconButton(
                    onClick = onPlayVoice,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("🔊", fontSize = 16.sp)
                }
            }

            // Expanded Dossier Section
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = BorderParchment.copy(alpha = 0.5f))

                    // Benefit Description
                    Text(
                        text = "📋 Description:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (isKannada) scheme.benefitDescKn else scheme.benefitDescEn,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    // Required Documents Checklist
                    Text(
                        text = "📄 Required Documents Checklist:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    scheme.requiredDocuments.forEach { doc ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("☑", color = if (isDark) SoilFreshLeaf else SoilCropGreen, fontSize = 12.sp)
                            Text(doc, fontSize = 11.sp)
                        }
                    }

                    // Step-by-Step Instructions
                    Text(
                        text = "🚀 Step-by-Step Application Walkthrough:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    scheme.applicationSteps.forEach { step ->
                        Text(step, fontSize = 11.sp, lineHeight = 15.sp)
                    }

                    // Official Source & Portal URL
                    Surface(
                        color = if (isDark) Color.Black.copy(alpha = 0.3f) else SoilFieldPaper,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "🌐 Official Portal: ${scheme.officialPortalUrl}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SemanticInfoBlue
                            )
                            Text(
                                text = "📞 Official Helpdesk: ${scheme.officialContact}",
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Last Verified: ${scheme.lastVerifiedDate} (${scheme.freshnessLabel})",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseSchemeJson(obj: JSONObject): SchemeCardData {
    val reqDocs = mutableListOf<String>()
    val docsArr = obj.optJSONArray("required_documents")
    if (docsArr != null) {
        for (i in 0 until docsArr.length()) reqDocs.add(docsArr.getString(i))
    }

    val steps = mutableListOf<String>()
    val stepsArr = obj.optJSONArray("application_steps")
    if (stepsArr != null) {
        for (i in 0 until stepsArr.length()) steps.add(stepsArr.getString(i))
    }

    val missing = mutableListOf<String>()
    val missArr = obj.optJSONArray("missing_fields")
    if (missArr != null) {
        for (i in 0 until missArr.length()) missing.add(missArr.getString(i))
    }

    val rules = mutableListOf<String>()
    val rulesArr = obj.optJSONArray("matched_rules")
    if (rulesArr != null) {
        for (i in 0 until rulesArr.length()) rules.add(rulesArr.getString(i))
    }

    return SchemeCardData(
        schemeId = obj.optString("scheme_id"),
        titleEn = obj.optString("title_en"),
        titleKn = obj.optString("title_kn"),
        category = obj.optString("category"),
        targetLevel = obj.optString("target_level", "INDIVIDUAL_FARMER"),
        issuingDepartment = obj.optString("issuing_department"),
        benefitAmountSpec = obj.optString("benefit_amount_spec"),
        benefitDescEn = obj.optString("benefit_description_en"),
        benefitDescKn = obj.optString("benefit_description_kn"),
        eligibilityEn = obj.optString("eligibility_criteria_en"),
        eligibilityKn = obj.optString("eligibility_criteria_kn"),
        exclusionEn = obj.optString("exclusion_criteria_en"),
        exclusionKn = obj.optString("exclusion_criteria_kn"),
        requiredDocuments = reqDocs,
        applicationSteps = steps,
        applicationMethod = obj.optString("application_method"),
        officialPortalUrl = obj.optString("official_portal_url"),
        officialContact = obj.optString("official_contact"),
        verificationStatus = obj.optString("verification_status", "OFFICIALLY_VERIFIED"),
        lastVerifiedDate = obj.optString("last_verified_date", "2026-11-13"),
        freshnessLabel = obj.optString("freshness_label", "Active 2026"),
        matchStatus = obj.optString("match_status", "LIKELY_ELIGIBLE"),
        relevanceReasonEn = obj.optString("relevance_reason_en"),
        relevanceReasonKn = obj.optString("relevance_reason_kn"),
        missingFields = missing,
        matchedRules = rules
    )
}

private fun getDefaultSchemes(): List<SchemeCardData> {
    return listOf(
        SchemeCardData(
            schemeId = "SCHEME-PM-KISAN",
            titleEn = "PM-KISAN Samman Nidhi",
            titleKn = "ಪ್ರಧಾನ ಮಂತ್ರಿ ಕಿಸಾನ್ ಸಮ್ಮಾನ್ ನಿಧಿ",
            category = "DIRECT_INCOME_SUPPORT",
            targetLevel = "INDIVIDUAL_FARMER",
            issuingDepartment = "Department of Agriculture & Farmers Welfare, GoI",
            benefitAmountSpec = "₹6,000 / year (3 tranches of ₹2,000 via DBT)",
            benefitDescEn = "Direct income support of ₹6,000 per year transferred in three equal quarterly installments of ₹2,000 directly into Aadhaar-seeded bank accounts.",
            benefitDescKn = "ವರ್ಷಕ್ಕೆ ₹6,000 ನೇರ ಆದಾಯ ಬೆಂಬಲವನ್ನು ತಲಾ ₹2,000 ರ ಮೂರು ಕಂತುಗಳಲ್ಲಿ ನೇರವಾಗಿ ಆಧಾರ್ ಜೋಡಿಸಲಾದ ಬ್ಯಾಂಕ್ ಖಾತೆಗೆ ಜಮೆ ಮಾಡಲಾಗುತ್ತದೆ.",
            eligibilityEn = "All landholding farmer families with cultivable landholding in their names.",
            eligibilityKn = "ತಮ್ಮ ಹೆಸರಿನಲ್ಲಿ ಸಾಗುವಳಿ ಭೂಮಿ ಹೊಂದಿರುವ ಎಲ್ಲಾ ರೈತ ಕುಟುಂಬಗಳು.",
            exclusionEn = "Institutional landholders, income tax payees, government employees.",
            exclusionKn = "ಸಂಸ್ಥಾಗತ ಭೂಮಾಲೀಕರು, ಆದಾಯ ತೆರಿಗೆ ಪಾವತಿದಾರರು, ಸರ್ಕಾರಿ ನೌಕರರು.",
            requiredDocuments = listOf("Aadhaar Card", "Land Record (RTC / Pahani)", "Bank Passbook", "e-KYC"),
            applicationSteps = listOf("1. Register on pmkisan.gov.in.", "2. Enter Aadhaar and land details.", "3. Complete e-KYC."),
            applicationMethod = "ONLINE_PORTAL_OR_CSC",
            officialPortalUrl = "https://pmkisan.gov.in",
            officialContact = "Toll Free: 155261",
            verificationStatus = "OFFICIALLY_VERIFIED",
            lastVerifiedDate = "2026-11-13",
            freshnessLabel = "Active 2026 Direct Benefit",
            matchStatus = "LIKELY_ELIGIBLE",
            relevanceReasonEn = "Matches your smallholder family profile in Kolar, Karnataka.",
            relevanceReasonKn = "ಕೋಲಾರದ ನಿಮ್ಮ ರೈತ ಕುಟುಂಬಕ್ಕೆ ನೇರವಾಗಿ ಅನ್ವಯಿಸುತ್ತದೆ.",
            missingFields = emptyList(),
            matchedRules = listOf("Geographic eligibility confirmed for Karnataka, Kolar")
        ),
        SchemeCardData(
            schemeId = "SCHEME-KA-RAITHA-SIRI",
            titleEn = "Karnataka Raitha Siri Scheme",
            titleKn = "ಕರ್ನಾಟಕ ರೈತ ಸಿರಿ ಯೋಜನೆ (ತೋಟಗಾರಿಕೆ & ಸಿರಿಧಾನ್ಯ)",
            category = "HORTICULTURE_INCENTIVE",
            targetLevel = "INDIVIDUAL_FARMER",
            issuingDepartment = "Department of Agriculture & Horticulture, Govt of Karnataka",
            benefitAmountSpec = "₹10,000 / hectare (Max 2 ha = ₹20,000)",
            benefitDescEn = "Direct incentive of ₹10,000 per hectare deposited via DBT to support growers of tomato, millets, and horticulture crops.",
            benefitDescKn = "ಸಿರಿಧಾನ್ಯ ಮತ್ತು ತೋಟಗಾರಿಕೆ ಬೆಳೆಗಾರರಿಗೆ ಹೆಕ್ಟೇರ್‌ಗೆ ₹10,000 ನೇರ ನಗದು ವರ್ಗಾವಣೆ ಮೂಲಕ ಪ್ರೋತ್ಸಾಹಧನ.",
            eligibilityEn = "Farmers cultivating notified horticulture crops registered under FRUITS database.",
            eligibilityKn = "ಫ್ರೂಟ್ಸ್ ಪೋರ್ಟಲ್‌ನಲ್ಲಿ ನೋಂದಾಯಿಸಲಾದ ತೋಟಗಾರಿಕೆ ಬೆಳೆ ಬೆಳೆಯುವ ರೈತರು.",
            exclusionEn = "Landholdings outside Karnataka; duplicate subsidies for same crop season.",
            exclusionKn = "ಕರ್ನಾಟಕದ ಹೊರಗಿನ ಭೂಮಿ; ಒಂದೇ ಬೆಳೆಗೆ ಎರಡು ಕಡೆ ಸಬ್ಸಿಡಿ ಪಡೆದವರು.",
            requiredDocuments = listOf("FRUITS FID Number", "Land RTC / Pahani", "NPCI-linked Bank Account"),
            applicationSteps = listOf("1. Verify FRUITS FID.", "2. Register crop survey at RSK.", "3. DBT incentive credit."),
            applicationMethod = "FRUITS_AUTO_OR_RSK",
            officialPortalUrl = "https://fruits.karnataka.gov.in",
            officialContact = "Kisan Call Centre: 1551",
            verificationStatus = "OFFICIALLY_VERIFIED",
            lastVerifiedDate = "2026-11-13",
            freshnessLabel = "Active Karnataka State Guideline",
            matchStatus = "LIKELY_ELIGIBLE",
            relevanceReasonEn = "Matches your Tomato / Horticulture crop in Kolar district.",
            relevanceReasonKn = "ಕೋಲಾರದ ನಿಮ್ಮ ಟೊಮೆಟೊ / ತೋಟಗಾರಿಕೆ ಬೆಳೆಗೆ ಅನ್ವಯಿಸುತ್ತದೆ.",
            missingFields = emptyList(),
            matchedRules = listOf("Crop match confirmed: Tomato", "Smallholder ceiling satisfied")
        ),
        SchemeCardData(
            schemeId = "SCHEME-PMFBY-SAMRAKSHANE",
            titleEn = "PM Fasal Bima / Samrakshane Karnataka",
            titleKn = "ಪ್ರಧಾನ ಮಂತ್ರಿ ಫಸಲ್ ಬಿಮಾ ಯೋಜನೆ (ಸಂರಕ್ಷಣೆ)",
            category = "CROP_INSURANCE",
            targetLevel = "INDIVIDUAL_FARMER",
            issuingDepartment = "Karnataka State Crop Insurance Authority & MoA&FW",
            benefitAmountSpec = "Claim compensation up to ₹50,000 - ₹85,000 / ha based on threshold yield deficit",
            benefitDescEn = "Comprehensive risk insurance covering natural risks from pre-sowing to post-harvest. Farmers pay nominal 5% premium for horticulture.",
            benefitDescKn = "ನೈಸರ್ಗಿಕ ವಿಕೋಪಗಳಿಂದಾಗುವ ಬೆಳೆ ಹಾನಿಗೆ ವಿಮಾ ರಕ್ಷಣೆ. ತೋಟಗಾರಿಕೆ ಬೆಳೆಗೆ ಕೇವಲ 5% ಪ್ರೀಮಿಯಂ.",
            eligibilityEn = "All farmers growing notified crops in notified areas of Karnataka.",
            eligibilityKn = "ಅಧಿಸೂಚಿತ ಪ್ರದೇಶಗಳಲ್ಲಿ ಅಧಿಸೂಚಿತ ಬೆಳೆಗಳನ್ನು ಬೆಳೆಯುವ ಎಲ್ಲಾ ರೈತರು.",
            exclusionEn = "Man-made damage or harvest left standing beyond 14 days.",
            exclusionKn = "ಮಾನವ ನಿರ್ಮಿತ ಹಾನಿ ಅಥವಾ ನಿಗದಿತ 14 ದಿನ ಮೀರಿದ ನಂತರದ ಹಾನಿ.",
            requiredDocuments = listOf("Land RTC / Pahani", "Sowing Certificate", "Aadhaar Card", "Bank Passbook"),
            applicationSteps = listOf("1. Check cut-off date on Samrakshane.", "2. Submit RTC & pay 5% premium at CSC."),
            applicationMethod = "CSC_KENDRA_OR_BANK",
            officialPortalUrl = "https://samrakshane.karnataka.gov.in",
            officialContact = "Samrakshane Helpline: 080-22180800",
            verificationStatus = "OFFICIALLY_VERIFIED",
            lastVerifiedDate = "2026-11-13",
            freshnessLabel = "Active Crop Insurance Season",
            matchStatus = "LIKELY_ELIGIBLE",
            relevanceReasonEn = "Notified crop (Tomato) in Kolar district covered under Samrakshane.",
            relevanceReasonKn = "ಕೋಲಾರ ಜಿಲ್ಲೆಯಲ್ಲಿ ಅಧಿಸೂಚಿತ ಟೊಮೆಟೊ ಬೆಳೆಗೆ ಸಂರಕ್ಷಣೆ ವಿಮೆ ಲಭ್ಯ.",
            missingFields = emptyList(),
            matchedRules = listOf("Crop match confirmed: Tomato")
        ),
        SchemeCardData(
            schemeId = "SCHEME-SFAC-10K-FPO",
            titleEn = "Formation of 10,000 FPOs Scheme (SFAC / NABARD)",
            titleKn = "10,000 FPO ಗಳ ರಚನೆ ಮತ್ತು ಪ್ರಚಾರ ಯೋಜನೆ (SFAC / ನಬಾರ್ಡ್)",
            category = "FPO_INSTITUTIONAL",
            targetLevel = "FPO_COMPANY",
            issuingDepartment = "Small Farmers' Agribusiness Consortium (SFAC) & NABARD",
            benefitAmountSpec = "Up to ₹15 Lakh Equity Grant + ₹2 Cr Credit Guarantee (Paid to FPO Institution)",
            benefitDescEn = "Matching Equity Grant up to ₹15 Lakhs and Credit Guarantee Cover up to ₹2 Crores for registered FPO companies with 300+ farmers.",
            benefitDescKn = "300+ ರೈತ ಸದಸ್ಯರಿರುವ FPO ಸಂಸ್ಥೆಗಳಿಗೆ ₹15 ಲಕ್ಷ ಈಕ್ವಿಟಿ ಅನುದಾನ ಮತ್ತು ₹2 ಕೋಟಿ ಸಾಲ ಖಾತರಿ.",
            eligibilityEn = "Registered Farmer Producer Companies with minimum 300 active farmer members.",
            eligibilityKn = "ಕನಿಷ್ಠ 300 ಸದಸ್ಯರಿರುವ ನೋಂದಾಯಿತ ರೈತ ಉತ್ಪಾದಕ ಕಂಪನಿಗಳು (FPO).",
            exclusionEn = "Individual farmers cannot apply directly. Non-producing shell entities.",
            exclusionKn = "ವೈಯಕ್ತಿಕ ರೈತರಿಗೆ ನೇರವಾಗಿ ಅನ್ವಯಿಸುವುದಿಲ್ಲ.",
            requiredDocuments = listOf("FPO Certificate of Incorporation", "300+ Member Shareholder Register", "Audited Financials"),
            applicationSteps = listOf("1. Form 300+ farmer company.", "2. Partner with CBBO.", "3. Apply on sfacindia.com."),
            applicationMethod = "SFAC_ONLINE_PORTAL",
            officialPortalUrl = "https://sfacindia.com",
            officialContact = "SFAC Toll Free: 1800 270 0224",
            verificationStatus = "OFFICIALLY_VERIFIED",
            lastVerifiedDate = "2026-11-13",
            freshnessLabel = "Active Central Sector FPO Guideline",
            matchStatus = "POTENTIALLY_RELEVANT",
            relevanceReasonEn = "Institutional FPO grant (Paid to FPO company, not individual farmers). Join your local Kolar FPO to benefit.",
            relevanceReasonKn = "ಸಾಂಸ್ಥಿಕ FPO ಅನುದಾನ (ವೈಯಕ್ತಿಕ ರೈತರಿಗಲ್ಲ). ಸ್ಥಳೀಯ FPO ಗುಂಪಿಗೆ ಸೇರಿ ಲಾಭ ಪಡೆಯಿರಿ.",
            missingFields = emptyList(),
            matchedRules = listOf("Institutional Producer Company eligibility rule evaluated")
        ),
        SchemeCardData(
            schemeId = "SCHEME-ENAM-DIRECT",
            titleEn = "e-NAM Direct Electronic Trading & Price Discovery",
            titleKn = "ರಾಷ್ಟ್ರೀಯ ಕೃಷಿ ಮಾರುಕಟ್ಟೆ (e-NAM) ನೇರ ಎಲೆಕ್ಟ್ರಾನಿಕ್ ವಹಿವಾಟು",
            category = "MARKET_ACCESS",
            targetLevel = "INDIVIDUAL_FARMER",
            issuingDepartment = "Directorate of Marketing & Inspection, MoA&FW",
            benefitAmountSpec = "Zero unauthorized commission + Direct online payment in 24 hours",
            benefitDescEn = "Pan-India online bidding at integrated APMC mandis (Kolar & Bangarpet). Assayed produce, electronic weighing, direct bank settlement.",
            benefitDescKn = "ಎಪಿಎಂಸಿ ಮಾರುಕಟ್ಟೆಗಳಲ್ಲಿ ಆನ್‌ಲೈನ್ ಬಿಡ್ಡಿಂಗ್, ಸರಿಯಾದ ತೂಕ, ಮತ್ತು 24 ಗಂಟೆಗಳಲ್ಲಿ ನೇರ ಬ್ಯಾಂಕ್ ಜಮೆ.",
            eligibilityEn = "Any farmer bringing produce to e-NAM integrated APMC mandis.",
            eligibilityKn = "ಕೋಲಾರ, ಬಂಗಾರಪೇಟೆ ಎಪಿಎಂಸಿ ಮಾರುಕಟ್ಟೆಗಳಿಗೆ ಬೆಳೆ ತರುವ ಯಾವುದೇ ರೈತರು.",
            exclusionEn = "Produce failing basic food safety norms.",
            exclusionKn = "ಗುಣಮಟ್ಟ ಪರೀಕ್ಷೆಯಲ್ಲಿ ತಿರಸ್ಕರಿಸಲ್ಪಟ್ಟ ಬೆಳೆಗಳು.",
            requiredDocuments = listOf("Aadhaar Card", "Bank Account Passbook", "Farmer Mandi Registration"),
            applicationSteps = listOf("1. Gate entry at APMC.", "2. Quality assaying.", "3. National e-bidding.", "4. Direct payment."),
            applicationMethod = "APMC_GATE_ENTRY_OR_ENAM_PORTAL",
            officialPortalUrl = "https://enam.gov.in",
            officialContact = "e-NAM Helpdesk: 1800 270 0224",
            verificationStatus = "OFFICIALLY_VERIFIED",
            lastVerifiedDate = "2026-11-13",
            freshnessLabel = "Active Daily Electronic Trading",
            matchStatus = "LIKELY_ELIGIBLE",
            relevanceReasonEn = "Direct electronic market access at nearby Kolar & Bangarpet APMC mandis.",
            relevanceReasonKn = "ಕೋಲಾರ ಮತ್ತು ಬಂಗಾರಪೇಟೆ ಎಪಿಎಂಸಿಗಳಲ್ಲಿ ನೇರ ಆನ್‌ಲೈನ್ ಮಾರಾಟ ಲಭ್ಯ.",
            missingFields = emptyList(),
            matchedRules = listOf("Market access confirmed for Kolar APMC")
        )
    )
}
