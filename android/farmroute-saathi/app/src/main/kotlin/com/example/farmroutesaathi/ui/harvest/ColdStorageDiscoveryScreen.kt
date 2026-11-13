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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.UserSession
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

    var isKannada by remember { mutableStateOf(UserSession.isKannadaPreferred) }
    var selectedRadiusKm by remember { mutableStateOf(50.0) }
    var isLoading by remember { mutableStateOf(true) }
    var facilities by remember { mutableStateOf<List<ColdStorageItem>>(emptyList()) }
    var weatherAlert by remember { mutableStateOf<WeatherRiskAlert?>(null) }
    var voiceTranscript by remember { mutableStateOf<String?>(null) }
    var isSpeakingAudio by remember { mutableStateOf(false) }
    var useGpsLocation by remember { mutableStateOf(true) }
    var manualLocationText by remember { mutableStateOf(locationText) }

    fun loadData() {
        isLoading = true
        scope.launch {
            try {
                val farmerId = UserSession.currentFarmer?.phone ?: "9845012345"
                val res = ApiClient.discoverColdStorage(
                    farmerId = farmerId,
                    crop = crop,
                    quantityKg = quantityKg,
                    latitude = if (useGpsLocation) farmerLat else 13.136,
                    longitude = if (useGpsLocation) farmerLon else 78.129,
                    locationText = manualLocationText,
                    radiusKm = selectedRadiusKm
                )
                val json = JSONObject(res)
                val facList = mutableListOf<ColdStorageItem>()
                val arr = json.optJSONArray("facilities") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val mArr = obj.optJSONArray("mandi_distances") ?: JSONArray()
                    val mList = mutableListOf<MandiDistanceItem>()
                    for (j in 0 until mArr.length()) {
                        val mObj = mArr.getJSONObject(j)
                        mList.add(
                            MandiDistanceItem(
                                mandiName = mObj.optString("mandi_name", "APMC"),
                                distanceKm = mObj.optDouble("distance_km", 0.0)
                            )
                        )
                    }

                    val dArr = obj.optJSONArray("direct_farmer_to_mandi") ?: JSONArray()
                    val dList = mutableListOf<MandiDistanceItem>()
                    for (j in 0 until dArr.length()) {
                        val dObj = dArr.getJSONObject(j)
                        dList.add(
                            MandiDistanceItem(
                                mandiName = dObj.optString("mandi_name", "APMC"),
                                distanceKm = dObj.optDouble("distance_km", 0.0)
                            )
                        )
                    }

                    val cropsArr = obj.optJSONArray("crop_compatibility") ?: JSONArray()
                    val cropList = mutableListOf<String>()
                    for (k in 0 until cropsArr.length()) {
                        cropList.add(cropsArr.getString(k))
                    }

                    facList.add(
                        ColdStorageItem(
                            facilityId = obj.optString("facility_id", "CS-$i"),
                            name = obj.optString("name", "Cold Storage"),
                            district = obj.optString("district", "Kolar"),
                            address = obj.optString("address", ""),
                            latitude = obj.optDouble("latitude", 13.13),
                            longitude = obj.optDouble("longitude", 78.13),
                            totalCapacityMt = obj.optDouble("total_capacity_mt", 100.0),
                            availableCapacityMt = obj.optDouble("available_capacity_mt", 0.0),
                            capacityVerified = obj.optBoolean("capacity_verified", false),
                            cropCompatibility = cropList,
                            storageType = obj.optString("storage_type", "Cold Storage"),
                            contactPhone = obj.optString("contact_phone", "+91 98450 22001"),
                            rentalChargesPerQuintalDay = obj.optDouble("rental_charges_per_quintal_day", 16.0),
                            distanceFarmerKm = obj.optDouble("distance_farmer_km", 5.0),
                            travelTimeMin = obj.optInt("travel_time_min", 15),
                            routeType = obj.optString("route_type", "ROAD"),
                            mandiDistances = mList,
                            directFarmerToMandi = dList,
                            rankingScore = obj.optDouble("ranking_score", 80.0),
                            recommendationReason = obj.optString("recommendation_reason", "Nearby facility"),
                            status = obj.optString("status", "OPERATIONAL")
                        )
                    )
                }
                facilities = facList

                // Fetch Weather harvest alerts
                val wRes = ApiClient.getWeatherHarvestAlerts(farmerId)
                val wJson = JSONObject(wRes)
                if (wJson.optBoolean("has_active_alert", false)) {
                    val aff = wJson.optJSONObject("affected_harvest")
                    weatherAlert = WeatherRiskAlert(
                        hasActiveAlert = true,
                        warningId = wJson.optString("warning_id", "IMD-001"),
                        district = wJson.optString("district", "Kolar"),
                        severity = wJson.optString("severity", "ORANGE"),
                        headline = wJson.optString("headline", "Weather warning"),
                        headlineKannada = wJson.optString("headline_kannada", "ಹವಾಮಾನ ಎಚ್ಚರಿಕೆ"),
                        vulnerability = aff?.optString("vulnerability", "") ?: "Perishable produce at risk.",
                        recommendedAction = wJson.optString("recommended_action", "Move to cold storage."),
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

    Scaffold(
        topBar = {
            Surface(
                color = ForestGreenDeep,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBackClick) {
                                Text("←", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ಸ್ಮಾರ್ಟ್ ಶೀತಲಾಗಾರ ಶೋಧನೆ" else "Smart Cold Storage Discovery",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isKannada) "ರಾಷ್ಟ್ರೀಯ ತೋಟಗಾರಿಕಾ ಮಂಡಳಿ (NHB) ದತ್ತಾಂಶ" else "National Horticulture Board (NHB) Registry",
                                    color = HarvestGoldLight,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Language Switcher
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { isKannada = !isKannada }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🌐 ", fontSize = 12.sp)
                                Text(
                                    text = if (isKannada) "ಕನ್ನಡ" else "English",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
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
                .background(CreamBackgroundLight)
                .verticalScroll(rememberScrollState())
        ) {

            // ==========================================
            // FARMER LOCATION & SEARCH RADIUS BAR
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📍", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ರೈತರ ಸ್ಥಳ (Farmer Location)" else "Farmer Location",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ForestGreenDeep
                                )
                                Text(
                                    text = if (useGpsLocation) "$manualLocationText (13.133° N, 78.133° E)" else "$manualLocationText (PIN: 563101)",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (useGpsLocation) ForestGreenLight.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (useGpsLocation) "✓ GPS Active" else "Fallback PIN",
                                color = if (useGpsLocation) ForestGreenDeep else AmberWarningDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
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
                            color = Color.Gray
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15.0, 30.0, 50.0, 75.0).forEach { r ->
                                FilterChip(
                                    selected = selectedRadiusKm == r,
                                    onClick = { selectedRadiusKm = r },
                                    label = { Text("${r.toInt()} km", fontSize = 10.sp) },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // WEATHER RISK HARVEST PROTECTION ALERT
            // ==========================================
            weatherAlert?.let { alert ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AmberWarning.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.5.dp, AmberWarning),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                        color = AmberWarningDark
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AmberWarning
                                    ) {
                                        Text(
                                            text = alert.severity,
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isKannada) alert.headlineKannada else alert.headline,
                                    fontSize = 12.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ " + (if (isKannada) "ಕೊಯ್ಲು ಮಾಡಿದ ಟೊಮೆಟೊ ನೀರು ನಿಲ್ಲುವ ಅಪಾಯ: 24-36 ಗಂಟೆಗಳಲ್ಲಿ ಕೊಳೆಯುವ ಸಾಧ್ಯತೆ. ತಕ್ಷಣ ಶೀತಲಾಗಾರಕ್ಕೆ ಸ್ಥಳಾಂತರಿಸಿ." else alert.vulnerability),
                            fontSize = 11.sp,
                            color = Color(0xFF6B3A00)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ForestGreenDeep.copy(alpha = 0.08f),
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
                                    color = ForestGreenDeep
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // HARVEST DETAILS PILL
            // ==========================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ForestGreenDeep.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                            color = ForestGreenDeep
                        )
                        Text(
                            text = " • Lot: $harvestId",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    Text(
                        text = if (facilities.isNotEmpty()) "${facilities.size} Facilities Found" else "Searching...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HarvestGoldDark
                    )
                }
            }

            // ==========================================
            // VOICE RECOMMENDATION BANNER
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎙️", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ಸರ್ವಂ ಸಾರಥಿ ಧ್ವನಿ ವಿವರಣೆ" else "Sarvam Saathi Voice Guidance",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isKannada) "ಬುಲ್ ಬುಲ್ ಧ್ವನಿ ಮುಖಾಂತರ ಆಲಿಸಿ" else "Listen via Sarvam Bulbul TTS",
                                    color = HarvestGoldLight,
                                    fontSize = 10.sp
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
                            colors = ButtonDefaults.buttonColors(containerColor = HarvestGold),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isSpeakingAudio) "⏳ Loading..." else if (isKannada) "▶ ಆಲಿಸಿ" else "▶ Listen",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    voiceTranscript?.let { transcript ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "\"$transcript\"",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // RANKED COLD STORAGE FACILITY CARDS
            // ==========================================
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ForestGreenDeep)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isKannada) "ಹತ್ತಿರದ ಶೀತಲಾಗಾರಗಳನ್ನು ಹುಡುಕಲಾಗುತ್ತಿದೆ..." else "Searching nearby cold storage facilities...",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else if (facilities.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
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
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isKannada) "ದಯವಿಟ್ಟು ಶೋಧನಾ ತ್ರಿಜ್ಯವನ್ನು ಹೆಚ್ಚಿಸಿ." else "Please increase the search radius above.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                facilities.forEachIndexed { index, facility ->
                    ColdStorageCard(
                        facility = facility,
                        rank = index + 1,
                        isKannada = isKannada,
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
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun ColdStorageCard(
    facility: ColdStorageItem,
    rank: Int,
    isKannada: Boolean,
    onCallFacility: (String) -> Unit,
    onViewDirections: (Double, Double, String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
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
                        color = if (rank == 1) HarvestGold else ForestGreenDeep.copy(alpha = 0.1f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#$rank",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (rank == 1) Color.Black else ForestGreenDeep
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = facility.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ForestGreenDeep
                        )
                        Text(
                            text = "${facility.address}, ${facility.district}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ForestGreenLight.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${facility.rankingScore.toInt()}% Match",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenDeep,
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
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50))
                    ) {
                        Text(
                            text = "✓ ${facility.availableCapacityMt.toInt()}T Space Verified",
                            color = Color(0xFF2E7D32),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AmberWarning.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AmberWarning)
                    ) {
                        Text(
                            text = "⚠️ Contact to confirm space",
                            color = AmberWarningDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEDE7F6)
                ) {
                    Text(
                        text = "🍅 Tomato Suitable",
                        color = Color(0xFF512DA8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFF3E0)
                ) {
                    Text(
                        text = "₹${facility.rentalChargesPerQuintalDay.toInt()}/Q/day",
                        color = Color(0xFFE65100),
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
                color = CreamBackgroundLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isKannada) "ರಸ್ತೆ ಮಾರ್ಗ ಮತ್ತು ಮಾರುಕಟ್ಟೆ ದೂರ (Routing & Mandi Logistics)" else "ROAD DISTANCES & MANDI LOGISTICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenDeep
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${facility.distanceFarmerKm} km (~${facility.travelTimeMin} min)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenDeep
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // Legs 2-4: Storage to Mandis
                    Text(
                        text = if (isKannada) "ಶೀತಲಾಗಾರದಿಂದ ಮಾರುಕಟ್ಟೆಗಳಿಗೆ:" else "From Storage ➔ Downstream Mandis:",
                        fontSize = 10.sp,
                        color = Color.Gray,
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
                                color = Color.DarkGray
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${m.distanceKm} km",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                directM?.let { dm ->
                                    Text(
                                        text = " (vs direct ${dm.distanceKm} km)",
                                        fontSize = 10.sp,
                                        color = Color.Gray
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
                color = Color.DarkGray,
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
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ForestGreenDeep)
                ) {
                    Text("📞", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isKannada) "ಕರೆ ಮಾಡಿ" else "Call Facility",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenDeep
                    )
                }

                Button(
                    onClick = { onViewDirections(facility.latitude, facility.longitude, facility.name) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep)
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
