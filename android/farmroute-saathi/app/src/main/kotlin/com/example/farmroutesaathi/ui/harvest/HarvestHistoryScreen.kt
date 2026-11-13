package com.example.farmroutesaathi.ui.harvest

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class HarvestHistoryItem(
    val harvestId: String,
    val lotId: String,
    val farmerId: String,
    val crop: String,
    val variety: String,
    val quantityKg: Double,
    val qualityGrade: String,
    val harvestDate: String,
    val harvestTime: String,
    val expectedDateTime: String,
    val sellImmediately: Boolean,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarvestHistoryScreen(
    isKannada: Boolean,
    onMandiComparisonClick: ((String) -> Unit)? = null,
    onDeclareHarvestClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var harvests by remember { mutableStateOf<List<HarvestHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    val scope = rememberCoroutineScope()

    val currentFarmer = UserSession.currentFarmer
    val farmerPhone = currentFarmer?.phone ?: "9845012345"
    val farmerName = currentFarmer?.name ?: "Manjunatha Gowda"

    fun loadHarvestHistory() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                // Fetch from backend
                val res = ApiClient.getFarmerHarvests(phone = farmerPhone)
                val json = JSONObject(res)
                if (json.optBoolean("success", false)) {
                    val arr = json.optJSONArray("harvests") ?: JSONArray()
                    val list = mutableListOf<HarvestHistoryItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            HarvestHistoryItem(
                                harvestId = obj.optString("harvest_id", obj.optString("lot_id")),
                                lotId = obj.optString("lot_id", "FR-KLR-001"),
                                farmerId = obj.optString("farmer_id", "FARMER-01"),
                                crop = obj.optString("crop", "Tomato"),
                                variety = obj.optString("variety", "Hybrid"),
                                quantityKg = obj.optDouble("quantity_kg", 200.0),
                                qualityGrade = obj.optString("quality_grade", "Grade A"),
                                harvestDate = obj.optString("harvest_date", "2026-11-13"),
                                harvestTime = obj.optString("harvest_time", "07:00 AM"),
                                expectedDateTime = obj.optString("expected_date_time", ""),
                                sellImmediately = obj.optBoolean("sell_immediately", true),
                                status = obj.optString("status", "READY FOR SALE")
                            )
                        )
                    }

                    // If backend list is empty, prepend the active session harvest if present
                    if (list.isEmpty() && UserSession.activeHarvest != null) {
                        val ah = UserSession.activeHarvest!!
                        list.add(
                            HarvestHistoryItem(
                                harvestId = ah.harvestId,
                                lotId = ah.harvestId,
                                farmerId = currentFarmer?.name ?: "FARMER-01",
                                crop = ah.crop,
                                variety = "Hybrid",
                                quantityKg = ah.quantityKg,
                                qualityGrade = ah.qualityGrade,
                                harvestDate = ah.harvestDate,
                                harvestTime = ah.harvestTime,
                                expectedDateTime = ah.expectedDateTime,
                                sellImmediately = ah.sellImmediately,
                                status = "READY FOR SALE"
                            )
                        )
                    }
                    harvests = list
                } else {
                    errorMessage = "Failed to load harvest history"
                    // Fallback to active harvest
                    UserSession.activeHarvest?.let { ah ->
                        harvests = listOf(
                            HarvestHistoryItem(
                                harvestId = ah.harvestId,
                                lotId = ah.harvestId,
                                farmerId = currentFarmer?.name ?: "FARMER-01",
                                crop = ah.crop,
                                variety = "Hybrid",
                                quantityKg = ah.quantityKg,
                                qualityGrade = ah.qualityGrade,
                                harvestDate = ah.harvestDate,
                                harvestTime = ah.harvestTime,
                                expectedDateTime = ah.expectedDateTime,
                                sellImmediately = ah.sellImmediately,
                                status = "READY FOR SALE"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
                UserSession.activeHarvest?.let { ah ->
                    harvests = listOf(
                        HarvestHistoryItem(
                            harvestId = ah.harvestId,
                            lotId = ah.harvestId,
                            farmerId = currentFarmer?.name ?: "FARMER-01",
                            crop = ah.crop,
                            variety = "Hybrid",
                            quantityKg = ah.quantityKg,
                            qualityGrade = ah.qualityGrade,
                            harvestDate = ah.harvestDate,
                            harvestTime = ah.harvestTime,
                            expectedDateTime = ah.expectedDateTime,
                            sellImmediately = ah.sellImmediately,
                            status = "READY FOR SALE"
                        )
                    )
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(farmerPhone) {
        loadHarvestHistory()
    }

    val filteredHarvests = remember(harvests, selectedFilter) {
        when (selectedFilter) {
            "TODAY" -> harvests.filter { it.sellImmediately }
            "LATER" -> harvests.filter { !it.sellImmediately }
            "TOMATO" -> harvests.filter { it.crop.equals("Tomato", ignoreCase = true) }
            else -> harvests
        }
    }

    val isDark = UserSession.isDarkTheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Card with Farmer Profile and Summary
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceDarkCard else Color.White),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, if (isDark) BorderDarkSubtle else BorderPaperLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isKannada) "📜 ಕೊಯ್ಲು ಇತಿಹಾಸ & ಹಿಂದಿನ ಲಾಟ್‌ಗಳು" else "📜 Harvest History & Past Lots",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$farmerName • $farmerPhone",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Count Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SoilCropGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${harvests.size} " + (if (isKannada) "ದಾಖಲೆಗಳು" else "Lots"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = SoilCropGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Filter Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text(if (isKannada) "ಎಲ್ಲವೂ" else "All (${harvests.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SoilCropGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "TODAY",
                        onClick = { selectedFilter = "TODAY" },
                        label = { Text(if (isKannada) "⚡ ಇಂದೇ ಮಾರಾಟ" else "⚡ Sell Today", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SoilCropGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "LATER",
                        onClick = { selectedFilter = "LATER" },
                        label = { Text(if (isKannada) "📅 ನಿಗದಿತ" else "📅 Scheduled", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SoilCropGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "TOMATO",
                        onClick = { selectedFilter = "TOMATO" },
                        label = { Text("🍅 Tomato", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SoilCropGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Action Row: Add New Harvest & Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onDeclareHarvestClick?.invoke() },
                colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isKannada) "➕ ಹೊಸ ಕೊಯ್ಲು ನೋಂದಾಯಿಸಿ" else "➕ Declare New Harvest",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            IconButton(onClick = { loadHarvestHistory() }) {
                Text("🔄", fontSize = 16.sp)
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SoilCropGreen)
            }
        } else if (filteredHarvests.isEmpty()) {
            // Empty State
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceDarkCard else Color.White),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, if (isDark) BorderDarkSubtle else BorderPaperLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🌾", fontSize = 42.sp)
                    Text(
                        text = if (isKannada) "ಯಾವುದೇ ಕೊಯ್ಲು ದಾಖಲೆಗಳಿಲ್ಲ" else "No harvests found for this filter",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isKannada) "ನಿಮ್ಮ ಬೆಳೆ ಕೊಯ್ಲನ್ನು ನೋಂದಾಯಿಸಲು ಕೆಳಗಿನ ಬಟನ್ ಒತ್ತಿ"
                        else "Declare your crop harvest via Kannada or English voice or typing",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onDeclareHarvestClick?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isKannada) "➕ ಈಗಲೇ ಕೊಯ್ಲು ನೋಂದಾಯಿಸಿ" else "➕ Declare Harvest Now", color = Color.White)
                    }
                }
            }
        } else {
            // List of Harvest Cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredHarvests.forEach { item ->
                    HarvestHistoryCard(
                        item = item,
                        isKannada = isKannada,
                        onViewMandiRates = { onMandiComparisonClick?.invoke(item.lotId) }
                    )
                }
            }
        }
                        isKannada = isKannada,
                        onViewMandiRates = { onMandiComparisonClick?.invoke(item.lotId) }
                    )
                }
            }
        }
    }
}

@Composable
fun HarvestHistoryCard(
    item: HarvestHistoryItem,
    isKannada: Boolean,
    onViewMandiRates: () -> Unit
) {
    val cropEmoji = when (item.crop.lowercase()) {
        "tomato", "ಟೊಮೆಟೊ" -> "🍅"
        "onion", "ಈರುಳ್ಳಿ" -> "🧅"
        "potato", "ಆಲೂಗಡ್ಡೆ" -> "🥔"
        "chilli", "ಮೆಣಸಿನಕಾಯಿ" -> "🌶️"
        "cabbage", "ಕೋಸು" -> "🥬"
        "carrot", "ಕ್ಯಾರೆಟ್" -> "🥕"
        else -> "🌾"
    }

    val isDark = UserSession.isDarkTheme

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) SurfaceDarkCard else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, if (isDark) BorderDarkSubtle else BorderPaperLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Lot ID + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDark) SurfaceDarkCardHigh else SoilFieldPaper
                ) {
                    Text(
                        text = item.lotId,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (isDark) SoilFreshLeaf else SoilCropGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.sellImmediately) SoilCropGreen.copy(alpha = 0.12f) else SoilHarvestGold.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = if (item.sellImmediately)
                            (if (isKannada) "⚡ ಇಂದೇ ಮಾರಾಟ" else "⚡ SELL TODAY")
                        else
                            (if (isKannada) "📅 ನಿಗದಿತ ಕೊಯ್ಲು" else "📅 SCHEDULED"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.sellImmediately) (if (isDark) SoilFreshLeaf else SoilCropGreen) else HarvestGoldDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Crop Info + Quantity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$cropEmoji ${item.crop} (${item.variety})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.qualityGrade} • ${item.harvestDate} ${item.harvestTime}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!item.sellImmediately && item.expectedDateTime.isNotBlank()) {
                        Text(
                            text = if (isKannada) "ಮಾರಾಟ ನಿಗದಿ: ${item.expectedDateTime}" else "Expected Sale: ${item.expectedDateTime}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HarvestGoldDark
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${item.quantityKg.toInt()} kg",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = if (isDark) SoilFreshLeaf else SoilCropGreen
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SoilCropGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = item.status,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) SoilFreshLeaf else SoilCropGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Direct Mandi Comparison CTA Button
            Button(
                onClick = onViewMandiRates,
                colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📊", fontSize = 13.sp)
                    Text(
                        text = if (isKannada) "ಮಾರುಕಟ್ಟೆ ದರಗಳು & ಆದಾಯ ಪರಿಶೀಲಿಸಿ (${item.crop})"
                        else "View Mandi Rates & Net Profit (${item.crop})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
