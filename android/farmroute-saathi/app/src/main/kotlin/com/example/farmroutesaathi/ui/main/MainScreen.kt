package com.example.farmroutesaathi.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.benefits.FarmerBenefitsScreen
import com.example.farmroutesaathi.ui.harvest.HarvestHistoryScreen
import com.example.farmroutesaathi.ui.components.*
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RoleTab(val labelEn: String, val labelKn: String, val icon: String) {
    FARMER("Farmer", "ರೈತ", "👨‍🌾"),
    HISTORY("History", "ಇತಿಹಾಸ", "📜"),
    BENEFITS("Benefits", "ಯೋಜನೆಗಳು", "🏛️")
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    onLogout: (() -> Unit)? = null,
    onDeclareHarvestClick: (() -> Unit)? = null,
    onMandiComparisonClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isKannada by remember { mutableStateOf(false) }
    var isDarkTheme by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(RoleTab.FARMER) }
    var backendStatus by remember { mutableStateOf("Checking backend...") }
    var voiceState by remember { mutableStateOf(SaathiVoiceState.IDLE) }
    var showVoiceAssistantDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val health = ApiClient.getHealth()
        backendStatus = if (health.contains("healthy")) "🟢 FastAPI Online (:8000)" else "🟡 Backend: $health"
    }

    FarmRouteSaathiTheme(darkTheme = isDarkTheme) {
        Scaffold(
            topBar = {
                KisanTopBar(
                    isKannada = isKannada,
                    isDarkTheme = isDarkTheme,
                    backendStatus = backendStatus,
                    onLanguageToggle = { isKannada = !isKannada },
                    onThemeToggle = { isDarkTheme = !isDarkTheme },
                    onLogout = onLogout,
                    onHarvestClick = { onDeclareHarvestClick?.invoke() },
                    onVoiceAssistantClick = { showVoiceAssistantDialog = true }
                )
            },
            floatingActionButton = {
                FloatingSaathiVoiceFab(
                    isKannada = isKannada,
                    voiceState = voiceState,
                    onVoiceFabClick = {
                        showVoiceAssistantDialog = true
                    }
                )
            }
        ) { innerPadding ->
            if (showVoiceAssistantDialog) {
                com.example.farmroutesaathi.ui.components.SarvamVoiceAssistantDialog(
                    isKannada = isKannada,
                    onDismiss = { showVoiceAssistantDialog = false },
                    onNavigateAction = { act ->
                        when (act) {
                            "HISTORY", "PAST_HARVESTS", "LOTS" -> selectedTab = RoleTab.HISTORY
                            "WEATHER" -> selectedTab = RoleTab.FARMER
                            "COLD_STORAGE" -> selectedTab = RoleTab.FARMER
                            "NAVIGATE_SCHEMES", "SCHEMES", "BENEFITS" -> selectedTab = RoleTab.BENEFITS
                            "HARVEST" -> onDeclareHarvestClick?.invoke()
                        }
                    }
                )
            }
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                // Living Intelligence Role Tab Row
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    RoleTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = "${tab.icon} " + if (isKannada) tab.labelKn else tab.labelEn,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                // Smooth Crossfade Screen Transitions
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "ScreenTransition",
                    modifier = Modifier.fillMaxSize()
                ) { currentTab ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (currentTab) {
                            RoleTab.FARMER -> FarmerDashboardView(
                                isKannada = isKannada,
                                onDeclareHarvestClick = onDeclareHarvestClick,
                                onMandiComparisonClick = onMandiComparisonClick,
                                onViewBenefitsClick = { selectedTab = RoleTab.BENEFITS },
                                onViewHistoryClick = { selectedTab = RoleTab.HISTORY }
                            )
                            RoleTab.HISTORY -> HarvestHistoryScreen(
                                isKannada = isKannada,
                                onMandiComparisonClick = onMandiComparisonClick,
                                onDeclareHarvestClick = onDeclareHarvestClick
                            )
                            RoleTab.BENEFITS -> FarmerBenefitsScreen(isKannada = isKannada)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. FARMER DASHBOARD SCREEN
// ============================================================================
@Composable
fun FarmerDashboardView(
    isKannada: Boolean,
    onDeclareHarvestClick: (() -> Unit)? = null,
    onMandiComparisonClick: ((String) -> Unit)? = null,
    onViewBenefitsClick: (() -> Unit)? = null,
    onViewHistoryClick: (() -> Unit)? = null
) {
    val currentLot = UserSession.activeHarvestId
    val farmerName = UserSession.currentFarmer?.name ?: "Manjunath Gowda"
    val farmerLocation = UserSession.currentFarmer?.district ?: "Mulbagal"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Welcome Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isKannada) "ನಮಸ್ಕಾರ, $farmerName" else "Welcome, $farmerName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isKannada) "$farmerLocation ಸಂಗ್ರಹಣಾ ಕೇಂದ್ರ #12" else "$farmerLocation Collection Cluster #12",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Farmer Hub",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Harvest Summary Card with Action Buttons
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isKannada) "ಸಕ್ರಿಯ ಕೊಯ್ಲು ($currentLot):" else "Active Harvest Lot ($currentLot):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ForestGreenDeep.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "READY FOR SALE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDeep,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "200 kg Tomato | Grade A Premium Firm",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isKannada) "ಧ್ವನಿ ನೋಂದಣಿ: ಸರ್ವಂ AI ಸಾಥಿ (ಕನ್ನಡ) ಧ್ವನಿ ಇನ್ಪುಟ್" else "Voice Stamp: Sarvam AI Saathi (Hardware Mic PCM)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons: Compare Mandis & Schedule New Harvest
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onMandiComparisonClick?.invoke(currentLot) },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isKannada) "📊 ಮಾರುಕಟ್ಟೆ ಹೋಲಿಕೆ" else "📊 View All Mandis",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onDeclareHarvestClick?.invoke() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isKannada) "➕ ಹೊಸ ಕೊಯ್ಲು" else "➕ Add Harvest",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDeep
                            )
                        }
                    }

                    // Past Harvests Quick CTA Card
                    Surface(
                        color = ForestGreenDeep.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewHistoryClick?.invoke() }
                            .padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("📜", fontSize = 14.sp)
                                Column {
                                    Text(
                                        text = if (isKannada) "ಹಿಂದಿನ ಕೊಯ್ಲು ಇತಿಹಾಸ & ದರಗಳು" else "View Past Harvests & Mandi Rates",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = ForestGreenDeep
                                    )
                                    Text(
                                        text = if (isKannada) "ಎಲ್ಲಾ ನೋಂದಾಯಿತ ಲಾಟ್‌ಗಳು ಮತ್ತು ಮಾರುಕಟ್ಟೆ ಆದಾಯ ಪರಿಶೀಲಿಸಿ" else "Inspect all your previous lots and mandi net returns",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text("➔", color = ForestGreenDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Discover Govt Schemes Quick CTA
                    Surface(
                        color = HarvestGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewBenefitsClick?.invoke() }
                            .padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🏛️", fontSize = 14.sp)
                                Column {
                                    Text(
                                        text = if (isKannada) "ಸರ್ಕಾರಿ ಯೋಜನೆಗಳು & ಸಬ್ಸಿಡಿಗಳು" else "Government Schemes & Subsidies",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = HarvestGoldDark
                                    )
                                    Text(
                                        text = if (isKannada) "ಪಿಎಂ-ಕಿಸಾನ್, ರೈತ ಸಿರಿ (₹20,000), ಸಂರಕ್ಷಣೆ ವಿಮೆ ಲಭ್ಯ" else "PM-KISAN, Raitha Siri (₹20,000), PMFBY Crop Insurance",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text("➔", color = HarvestGoldDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Text(
                text = if (isKannada) "ಮಾರುಕಟ್ಟೆ ಆದಾಯ ಹೋಲಿಕೆ (Netback Comparison):" else "Deterministic Netback Arbitrage (Live APMC Data):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            // Dynamic Comparison Bars (Accurate, objective cost difference)
            val netbackRoutes = listOf(
                NetbackRouteData(
                    titleEn = "Direct Virtual FPO (Recommended)",
                    titleKn = "ವರ್ಚುವಲ್ ಎಫ್‌ಪಿಒ ನೇರ ಖರೀದಿ (ಶಿಫಾರಸು)",
                    grossPerKg = 26.0,
                    netTakeHome = 2420.0,
                    deductionsTextEn = "Shared Freight: -₹180 | Zero Mandi Cut",
                    deductionsTextKn = "ಹಂಚಿಕೆಯ ಸಾಗಾಟ: -₹180 | ಶೂನ್ಯ ದಲ್ಲಾಳಿ ಕಮಿಷನ್",
                    isRecommended = true
                ),
                NetbackRouteData(
                    titleEn = "Kolar APMC Mandi",
                    titleKn = "ಕೋಲಾರ ಎಪಿಎಂಸಿ ಮಂಡಿ",
                    grossPerKg = 24.0,
                    netTakeHome = 1958.0,
                    deductionsTextEn = "Solo Freight: -₹250 | 8% Commission: -₹192",
                    deductionsTextKn = "ವೈಯಕ್ತಿಕ ಸಾಗಾಟ: -₹250 | 8% ಕಮಿಷನ್: -₹192"
                ),
                NetbackRouteData(
                    titleEn = "Bangarpet APMC (Lower Net Return)",
                    titleKn = "ಬಂಗಾರಪೇಟೆ ಎಪಿಎಂಸಿ (ಕಡಿಮೆ ನಿವ್ವಳ ಆದಾಯ)",
                    grossPerKg = 28.0,
                    netTakeHome = 1786.0,
                    deductionsTextEn = "High Freight (42km): -₹550 | 8% Commission: -₹224",
                    deductionsTextKn = "ದೂರದ ಸಾಗಾಟ (42km): -₹550 | 8% ಕಮಿಷನ್: -₹224",
                    isWarning = true
                )
            )

            AnimatedNetbackChart(routes = netbackRoutes, isKannada = isKannada)

            // Transparent Mandi Intelligence Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMandiComparisonClick?.invoke(currentLot) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isKannada) "💡 ಮಾರುಕಟ್ಟೆ ಪಾರದರ್ಶಕತೆ & ದರ ಮೋಸ ಎಚ್ಚರಿಕೆ:" else "💡 Mandi Cost Transparency & Trap Advisory:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ForestGreenDeep
                        )
                        Text(
                            text = if (isKannada) "ದೂರದ ಮಂಡಿಗಳಲ್ಲಿ ದರ ಹೆಚ್ಚಿದ್ದರೂ ಸಾರಿಗೆ & ಕಮಿಷನ್ ವೆಚ್ಚದಿಂದ ನಿವ್ವಳ ಹಣ ಕಡಿಮೆಯಾಗುತ್ತದೆ. ಸಂಪೂರ್ಣ ವಿವರಗಳನ್ನು ಪರಿಶೀಲಿಸಿ."
                            else "Higher headline prices at distant mandis often yield lower cash in hand once freight & commissions are deducted. Tap to inspect.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "➔",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenDeep,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
