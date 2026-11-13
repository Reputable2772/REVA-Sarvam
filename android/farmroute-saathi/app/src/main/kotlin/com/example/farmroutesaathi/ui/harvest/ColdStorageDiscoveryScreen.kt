package com.example.farmroutesaathi.ui.harvest

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.components.LanguageSelectionDialog
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class MandiDistanceItem(
    val mandiName: String,
    val distanceKm: Double
)

data class ColdStorageItem(
    val facilityId: String,
    val name: String,
    val district: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val totalCapacityMt: Double,
    val availableCapacityMt: Double,
    val capacityVerified: Boolean,
    val cropCompatibility: List<String>,
    val storageType: String,
    val contactPhone: String,
    val rentalChargesPerQuintalDay: Double,
    val distanceFarmerKm: Double,
    val travelTimeMin: Int,
    val routeType: String,
    val mandiDistances: List<MandiDistanceItem>,
    val directFarmerToMandi: List<MandiDistanceItem>,
    val rankingScore: Double,
    val recommendationReason: String,
    val status: String
)

data class WeatherRiskAlert(
    val hasActiveAlert: Boolean,
    val warningId: String,
    val district: String,
    val severity: String,
    val headline: String,
    val headlineKannada: String,
    val vulnerability: String,
    val recommendedAction: String,
    val recommendedFacilityName: String
)

@Composable
fun ColdStorageDiscoveryScreen(
    harvestId: String = "FR-KLR-20261113-0001",
    crop: String = "Tomato",
    quantityKg: Double = 500.0,
    farmerLat: Double = 13.133,
    farmerLon: Double = 78.133,
    locationText: String = "Kolar, Karnataka",
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = UserSession.isDarkTheme

    var isKannada by remember { mutableStateOf(UserSession.isKannadaPreferred) }
    var selectedRadiusKm by remember { mutableStateOf(50.0) }
    var isLoading by remember { mutableStateOf(true) }
    var facilities by remember { mutableStateOf<List<ColdStorageItem>>(emptyList()) }
    var weatherAlert by remember { mutableStateOf<WeatherRiskAlert?>(null) }
    var voiceTranscript by remember { mutableStateOf<String?>(null) }
    var isSpeakingAudio by remember { mutableStateOf(false) }
    var useGpsLocation by remember { mutableStateOf(true) }
    var manualLocationText by remember { mutableStateOf(locationText) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    fun loadData() {
        isLoading = true
        scope.launch {
            try {
                val lat = if (useGpsLocation) 13.133 else 13.133
                val lon = if (useGpsLocation) 78.133 else 78.133
                val langCode = if (isKannada) "kn-IN" else "en-IN"

                val res = ApiClient.discoverColdStorage(
                    farmerId = UserSession.currentFarmer?.phone ?: "FARMER-01",
                    farmerLat = lat,
                    farmerLon = lon,
                    crop = crop,
                    quantityKg = quantityKg,
                    radiusKm = selectedRadiusKm,
                    language = langCode
                )
                val json = JSONObject(res)
                val arr = json.optJSONArray("facilities") ?: JSONArray()
                val list = mutableListOf<ColdStorageItem>()

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val mArr = obj.optJSONArray("mandi_distances") ?: JSONArray()
                    val mList = mutableListOf<MandiDistanceItem>()
                    for (j in 0 until mArr.length()) {
                        val mObj = mArr.getJSONObject(j)
                        mList.add(MandiDistanceItem(mObj.optString("mandi_name"), mObj.optDouble("distance_km")))
                    }

                    val dArr = obj.optJSONArray("direct_farmer_to_mandi") ?: JSONArray()
                    val dList = mutableListOf<MandiDistanceItem>()
                    for (k in 0 until dArr.length()) {
                        val dObj = dArr.getJSONObject(k)
                        dList.add(MandiDistanceItem(dObj.optString("mandi_name"), dObj.optDouble("distance_km")))
                    }

                    list.add(
                        ColdStorageItem(
                            facilityId = obj.optString("facility_id", "FAC-$i"),
                            name = obj.optString("name", "Cold Storage"),
                            district = obj.optString("district", "Kolar"),
                            address = obj.optString("address", "NHB Registered Facility"),
                            latitude = obj.optDouble("latitude", 13.14),
                            longitude = obj.optDouble("longitude", 78.14),
                            totalCapacityMt = obj.optDouble("total_capacity_mt", 5000.0),
                            availableCapacityMt = obj.optDouble("available_capacity_mt", 120.0),
                            capacityVerified = obj.optBoolean("capacity_verified", true),
                            cropCompatibility = listOf("Tomato", "Vegetables"),
                            storageType = obj.optString("storage_type", "Chilled Atmosphere"),
                            contactPhone = obj.optString("contact_phone", "9845012345"),
                            rentalChargesPerQuintalDay = obj.optDouble("rental_charges_per_quintal_day", 1.80),
                            distanceFarmerKm = obj.optDouble("distance_farmer_km", 14.5),
                            travelTimeMin = obj.optInt("travel_time_min", 25),
                            routeType = obj.optString("route_type", "State Highway 96"),
                            mandiDistances = mList,
                            directFarmerToMandi = dList,
                            rankingScore = obj.optDouble("ranking_score", 92.0),
                            recommendationReason = obj.optString("recommendation_reason", "Nearest verified facility on direct APMC route"),
                            status = obj.optString("status", "AVAILABLE")
                        )
                    )
                }
                facilities = list

                val wRes = ApiClient.getWeatherRiskAlert(lat = lat, lon = lon, crop = crop)
                val wJson = JSONObject(wRes)
                if (wJson.optBoolean("has_active_alert", false)) {
                    weatherAlert = WeatherRiskAlert(
                        hasActiveAlert = true,
                        warningId = wJson.optString("warning_id", "IMD-001"),
                        district = wJson.optString("district", "Kolar"),
                        severity = wJson.optString("severity", "ORANGE WARNING"),
                        headline = wJson.optString("headline", "Heavy Unseasonal Rainfall Warning"),
                        headlineKannada = wJson.optString("headline_kn", "ಭಾರಿ ಅಕಾಲಿಕ ಮಳೆ ಎಚ್ಚರಿಕೆ: ಕೊಯ್ಲು ತಕ್ಷಣ ರಕ್ಷಿಸಿ"),
                        vulnerability = wJson.optString("vulnerability", "Harvested Solanaceous crop susceptible to damp rot"),
                        recommendedAction = wJson.optString("recommended_action", "Move harvest to nearest cold storage within 24 hours"),
                        recommendedFacilityName = wJson.optString("recommended_facility_name", "Kolar APMC Cold Chain")
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedRadiusKm, useGpsLocation) {
        loadData()
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { lang ->
                UserSession.setLanguage(lang)
                isKannada = lang.code == "kn"
                showLanguageDialog = false
                loadData()
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
                                text = AppLocalization.t("cold_storage_screen_title"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isKannada) "ರಾಷ್ಟ್ರೀಯ ತೋಟಗಾರಿಕಾ ಮಂಡಳಿ (NHB) ದತ್ತಾಂಶ" else "National Horticulture Board (NHB) Registry",
                                fontSize = 11.sp,
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
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .verticalScroll(rememberScrollState())
        ) {
            // ==========================================
            // 1. FARMER LOCATION & SEARCH RADIUS BAR
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) SurfaceDarkCard else Color.White,
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📍", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ರೈತರ ಸ್ಥಳ (Farmer Location)" else "Farmer Location",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (useGpsLocation) "$manualLocationText (13.133° N, 78.133° E)" else "$manualLocationText (PIN: 563101)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = (if (isDark) Color(0xFF5E9457) else SoilCropGreen).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, (if (isDark) Color(0xFF5E9457) else SoilCropGreen).copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = if (useGpsLocation) "✓ GPS Active" else "Fallback PIN",
                                color = if (isDark) Color(0xFF689F60) else SoilCropGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isKannada) "ಶೋಧನೆಯ ತ್ರಿಜ್ಯ (Radius):" else "Search Radius:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15.0, 30.0, 50.0, 75.0).forEach { r ->
                                val isSelected = selectedRadiusKm == r
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) (if (isDark) Color(0xFF5E9457) else SoilCropGreen) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) (if (isDark) Color(0xFF5E9457) else SoilCropGreen) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.clickable { selectedRadiusKm = r }
                                ) {
                                    Text(
                                        text = "${r.toInt()} km",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. WEATHER RISK HARVEST PROTECTION ALERT
            // ==========================================
            weatherAlert?.let { alert ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isDark) Color(0xFF2E1C06) else Color(0xFFFEF3C7),
                    border = BorderStroke(1.5.dp, HarvestGoldDark.copy(alpha = 0.5f)),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⛈️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isKannada) "ಭಾರತೀಯ ಹವಾಮಾನ ಇಲಾಖೆ (IMD) ಎಚ್ಚರಿಕೆ" else "IMD WEATHER PROTECTION ALERT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isDark) Color(0xFFFDE68A) else HarvestGoldDark
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = HarvestGoldDark
                                    ) {
                                        Text(
                                            text = alert.severity,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isKannada) alert.headlineKannada else alert.headline,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ " + (if (isKannada) "ಕೊಯ್ಲು ಮಾಡಿದ ಟೊಮೆಟೊ ನೀರು ನಿಲ್ಲುವ ಅಪಾಯ: 24-36 ಗಂಟೆಗಳಲ್ಲಿ ಕೊಳೆಯುವ ಸಾಧ್ಯತೆ. ತಕ್ಷಣ ಶೀತಲಾಗಾರಕ್ಕೆ ಸ್ಥಳಾಂತರಿಸಿ." else alert.vulnerability),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = (if (isDark) Color(0xFF5E9457) else SoilCropGreen).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, (if (isDark) Color(0xFF5E9457) else SoilCropGreen).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💡", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isKannada) "ಶಿಫಾರಸು: ${alert.recommendedFacilityName} ನಲ್ಲಿ ಜಾಗ ಕಾದಿರಿಸಿ." else "Recommended: Safe storage at ${alert.recommendedFacilityName}.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF689F60) else SoilCropGreen
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. HARVEST DETAILS PILL
            // ==========================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌾", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${quantityKg.toInt()} kg $crop",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " • Lot: $harvestId",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = if (facilities.isNotEmpty()) "${facilities.size} Facilities Found" else "Searching...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF689F60) else SoilCropGreen
                    )
                }
            }

            // ==========================================
            // 4. VOICE RECOMMENDATION BANNER
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) SurfaceDarkCard else Color.White,
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🎙️", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ಸರ್ವಂ ಸಾಥಿ ಧ್ವನಿ ವಿವರಣೆ" else "Sarvam Saathi Voice Guidance",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isKannada) "ಬುಲ್ ಬುಲ್ ಧ್ವನಿ ಮುಖಾಂತರ ಆಲಿಸಿ" else "Listen via Sarvam Saaras TTS",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (facilities.isNotEmpty()) {
                                    val topFac = facilities.first()
                                    isSpeakingAudio = true
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getColdStorageVoiceExplanation(
                                                facilityId = topFac.facilityId,
                                                farmerLat = farmerLat,
                                                farmerLon = farmerLon,
                                                languageCode = if (isKannada) "kn-IN" else "en-IN"
                                            )
                                            val obj = JSONObject(res)
                                            voiceTranscript = obj.optString("transcript", "")
                                            Toast.makeText(context, if (isKannada) "ಧ್ವನಿ ಮಾರ್ಗದರ್ಶನ ಪ್ರಾರಂಭವಾಗಿದೆ" else "Playing Voice Guidance...", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Voice error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSpeakingAudio = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF5E9457) else SoilCropGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isSpeakingAudio) "⏳ Loading..." else if (isKannada) "▶ ಆಲಿಸಿ 🔊" else "▶ Listen 🔊",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    voiceTranscript?.let { transcript ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "\"$transcript\"",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // 5. RANKED COLD STORAGE FACILITY CARDS
            // ==========================================
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = if (isDark) Color(0xFF689F60) else SoilCropGreen)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isKannada) "ಹತ್ತಿರದ ಶೀತಲಾಗಾರಗಳನ್ನು ಹುಡುಕಲಾಗುತ್ತಿದೆ..." else "Searching nearby cold storage facilities...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (facilities.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isDark) SurfaceDarkCard else Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isKannada) "ಈ ತ್ರಿಜ್ಯದಲ್ಲಿ ಯಾವುದೇ ಶೀತಲಾಗಾರ ಕಂಡುಬಂದಿಲ್ಲ" else "No cold storage found within ${selectedRadiusKm.toInt()} km",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isKannada) "ದಯವಿಟ್ಟು ಶೋಧನಾ ತ್ರಿಜ್ಯವನ್ನು ಹೆಚ್ಚಿಸಿ." else "Please increase the search radius above.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                facilities.forEachIndexed { index, facility ->
                    ColdStorageCard(
                        facility = facility,
                        rank = index + 1,
                        isKannada = isKannada,
                        isDark = isDark,
                        onCallFacility = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        },
                        onViewDirections = { lat, lon, name ->
                            val uri = Uri.parse("geo:$lat,$lon?q=${Uri.encode(name)}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(mapIntent)
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun ColdStorageCard(
    facility: ColdStorageItem,
    rank: Int,
    isKannada: Boolean,
    isDark: Boolean,
    onCallFacility: (String) -> Unit,
    onViewDirections: (Double, Double, String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isDark) SurfaceDarkCard else Color.White,
        border = BorderStroke(
            1.2.dp,
            if (rank == 1) (if (isDark) Color(0xFF5E9457) else SoilCropGreen) else MaterialTheme.colorScheme.outline
        ),
        shadowElevation = if (rank == 1) 4.dp else 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: Rank Badge & Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (rank == 1) (if (isDark) Color(0xFF5E9457) else SoilCropGreen) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#$rank",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (rank == 1) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = facility.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${facility.address}, ${facility.district}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isDark) Color(0xFF5E9457) else SoilCropGreen).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${facility.rankingScore.toInt()}% Match",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF689F60) else SoilCropGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Capacity & Verification Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (facility.capacityVerified) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (isDark) Color(0xFF5E9457) else SoilCropGreen).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, (if (isDark) Color(0xFF5E9457) else SoilCropGreen).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "✓ ${facility.availableCapacityMt.toInt()}T Verified Space",
                            color = if (isDark) Color(0xFF689F60) else SoilCropGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AmberWarning.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "⚠️ Contact to confirm",
                            color = HarvestGoldDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "🍅 Tomato Ready",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "₹${facility.rentalChargesPerQuintalDay.toInt()}/Q/day",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // MULTI-LEG ROUTE & DISTANCE BREAKDOWN
            // ==========================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isKannada) "ರಸ್ತೆ ಮಾರ್ಗ ಮತ್ತು ಮಾರುಕಟ್ಟೆ ದೂರ (Routing & Mandi Logistics)" else "ROAD DISTANCES & MANDI LOGISTICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Leg 1: Farmer to Facility
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚜", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isKannada) "ರೈತರ ಸ್ಥಳ ➔ ಶೀತಲಾಗಾರ:" else "Farmer ➔ Storage:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${facility.distanceFarmerKm} km (~${facility.travelTimeMin} min)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF689F60) else SoilCropGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Legs 2-4: Storage to Mandis
                    Text(
                        text = if (isKannada) "ಶೀತಲಾಗಾರದಿಂದ ಮಾರುಕಟ್ಟೆಗಳಿಗೆ:" else "From Storage ➔ Downstream Mandis:",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    facility.mandiDistances.forEach { m ->
                        val directM = facility.directFarmerToMandi.find { it.mandiName == m.mandiName }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "  • ${m.mandiName}:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${m.distanceKm} km",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                directM?.let { dm ->
                                    Text(
                                        text = " (vs direct ${dm.distanceKm} km)",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recommendation Reason
            Text(
                text = "💡 ${facility.recommendationReason}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Call & Directions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onCallFacility(facility.contactPhone) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.2.dp, if (isDark) Color(0xFF5E9457) else SoilCropGreen)
                ) {
                    Text("📞", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isKannada) "ಕರೆ ಮಾಡಿ" else "Call Facility",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF689F60) else SoilCropGreen
                    )
                }

                Button(
                    onClick = { onViewDirections(facility.latitude, facility.longitude, facility.name) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF5E9457) else SoilCropGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text("🗺️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isKannada) "ಮಾರ್ಗ ನೋಡಿ" else "Directions",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
