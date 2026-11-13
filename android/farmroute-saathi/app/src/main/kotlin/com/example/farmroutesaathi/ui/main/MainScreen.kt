package com.example.farmroutesaathi.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation3.runtime.NavKey
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.AppLocalization
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.benefits.FarmerBenefitsScreen
import com.example.farmroutesaathi.ui.harvest.HarvestHistoryScreen
import com.example.farmroutesaathi.ui.components.*
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch

// Reusable tactile press-scale animation modifier
@Composable
fun Modifier.pressScaleEffect(onClick: () -> Unit): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        awaitRelease()
                    } finally {
                        isPressed = false
                    }
                },
                onTap = { onClick() }
            )
        }
}

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
    onColdStorageClick: ((String, String, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentLangCode = UserSession.selectedLanguageCode
    val isKannada = (currentLangCode == "kn")
    val isDarkTheme = UserSession.isDarkTheme
    var selectedTab by remember { mutableStateOf(RoleTab.FARMER) }
    var backendStatus by remember { mutableStateOf("Checking backend...") }
    var voiceState by remember { mutableStateOf(SaathiVoiceState.IDLE) }
    var showVoiceAssistantDialog by remember { mutableStateOf(false) }
    var showFarmerPoolingDialog by remember { mutableStateOf(false) }
    var showWeatherDialog by remember { mutableStateOf(false) }
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
                    onLanguageToggle = {
                        // UserSession.selectedLanguageCode was updated by dialog; recomposes automatically
                    },
                    onThemeToggle = { UserSession.toggleTheme() },
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
                            "WEATHER" -> {
                                selectedTab = RoleTab.FARMER
                                showWeatherDialog = true
                            }
                            "COLD_STORAGE" -> {
                                selectedTab = RoleTab.FARMER
                                val harvest = UserSession.activeHarvest
                                onColdStorageClick?.invoke(UserSession.activeHarvestId, harvest?.crop ?: "Tomato", harvest?.quantityKg ?: 200.0)
                            }
                            "NAVIGATE_SCHEMES", "SCHEMES", "BENEFITS" -> selectedTab = RoleTab.BENEFITS
                            "HARVEST" -> onDeclareHarvestClick?.invoke()
                        }
                    }
                )
            }

            if (showFarmerPoolingDialog) {
                FarmerPoolingDialog(
                    isKannada = isKannada,
                    onDismiss = { showFarmerPoolingDialog = false }
                )
            }

            if (showWeatherDialog) {
                WeatherAdvisoryDialog(
                    isKannada = isKannada,
                    onDismiss = { showWeatherDialog = false },
                    onFindStorageClick = {
                        showWeatherDialog = false
                        val harvest = UserSession.activeHarvest
                        onColdStorageClick?.invoke(UserSession.activeHarvestId, harvest?.crop ?: "Tomato", harvest?.quantityKg ?: 200.0)
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
                    containerColor = if (isDarkTheme) SurfaceDarkCard else Color.White,
                    contentColor = SoilCropGreen,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = SoilCropGreen,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RoleTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        val tabTitle = when (tab) {
                            RoleTab.FARMER -> AppLocalization.t("tab_farmer")
                            RoleTab.HISTORY -> AppLocalization.t("tab_history")
                            RoleTab.BENEFITS -> AppLocalization.t("tab_benefits")
                        }
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = "${tab.icon} $tabTitle",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) SoilCropGreen else (if (isDarkTheme) SoilNaturalSky else SoilDeepEarth.copy(alpha = 0.7f))
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
                                onColdStorageClick = onColdStorageClick,
                                onViewBenefitsClick = { selectedTab = RoleTab.BENEFITS },
                                onViewHistoryClick = { selectedTab = RoleTab.HISTORY },
                                onViewPoolingClick = { showFarmerPoolingDialog = true },
                                onViewWeatherClick = { showWeatherDialog = true }
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
// 1. FARMER DASHBOARD VIEW (FROM SOIL TO MARKET)
// ============================================================================
@Composable
fun FarmerDashboardView(
    isKannada: Boolean,
    onDeclareHarvestClick: (() -> Unit)? = null,
    onMandiComparisonClick: ((String) -> Unit)? = null,
    onColdStorageClick: ((String, String, Double) -> Unit)? = null,
    onViewBenefitsClick: (() -> Unit)? = null,
    onViewHistoryClick: (() -> Unit)? = null,
    onViewPoolingClick: (() -> Unit)? = null,
    onViewWeatherClick: (() -> Unit)? = null
) {
    val isDark = UserSession.isDarkTheme
    val currentLot = UserSession.activeHarvestId
    val farmerProfile = UserSession.currentFarmer
    val farmerName = farmerProfile?.name ?: "Manjunatha Gowda"
    val farmerLocation = farmerProfile?.district ?: "Kolar"
    val farmerState = farmerProfile?.state ?: "Karnataka"
    val activeHarvest = UserSession.activeHarvest
    val activeCrop = activeHarvest?.crop ?: "Tomato"
    val activeQty = activeHarvest?.quantityKg ?: 200.0

    // Dashboard entrance animation state
    var isEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isEntered = true
    }

    val cardBg = if (isDark) SurfaceDarkCard else Color.White
    val cardBorder = if (isDark) BorderDarkSubtle else SoilDeepEarth.copy(alpha = 0.12f)
    val textPrimary = if (isDark) SoilFieldPaper else SoilDeepEarth
    val textSecondary = if (isDark) SoilFreshLeaf else SoilCropGreen
    val textMuted = if (isDark) SoilNaturalSky else SoilDeepEarth.copy(alpha = 0.75f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ====================================================================
        // SECTION 1: WELCOME & RURAL AGRICULTURAL LOCATION
        // ====================================================================
        AnimatedVisibility(
            visible = isEntered,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 }
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.2.dp, cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleEffect { }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) SoilHarvestGold.copy(alpha = 0.22f) else SoilHarvestGold.copy(alpha = 0.18f),
                            border = BorderStroke(1.5.dp, SoilHarvestGold),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("👨‍🌾", fontSize = 22.sp)
                            }
                        }
                        Column {
                            Text(
                                text = AppLocalization.t("welcome_farmer", "name" to farmerName),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "📍 $farmerLocation, $farmerState • Agri Cluster #12",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoilCropGreen.copy(alpha = if (isDark) 0.25f else 0.12f),
                        border = BorderStroke(1.dp, SoilCropGreen.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = if (isKannada) "ಕಿಸಾನ್ ಹಬ್" else "Farmer Hub",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) SoilFreshLeaf else SoilCropGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // ====================================================================
        // SECTION 2: ACTIVE HARVEST HERO FOCUS (MAJOR READABILITY OVERHAUL)
        // ====================================================================
        AnimatedVisibility(
            visible = isEntered,
            enter = fadeIn(tween(400, delayMillis = 100)) + slideInVertically(tween(400, delayMillis = 100)) { it / 8 }
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, if (isDark) BorderDarkSubtle else SoilCropGreen.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleEffect { onDeclareHarvestClick?.invoke() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row: Harvest Focus Badge + Lot ID Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🌱", fontSize = 15.sp)
                            Text(
                                text = AppLocalization.t("active_harvest_focus"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                color = if (isDark) SoilFreshLeaf else SoilCropGreen
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SoilHarvestGold.copy(alpha = if (isDark) 0.25f else 0.20f),
                            border = BorderStroke(1.2.dp, SoilHarvestGold),
                            shadowElevation = 1.dp
                        ) {
                            Text(
                                text = "Lot #$currentLot",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) SoilHarvestGold else SoilDeepEarth,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Crop Main Profile Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) SurfaceDarkCardHigh else SoilFieldPaper,
                            border = BorderStroke(1.2.dp, if (isDark) BorderDarkSubtle else SoilCropGreen.copy(alpha = 0.25f)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val cropEmoji = when {
                                    activeCrop.contains("Tomato", ignoreCase = true) || activeCrop.contains("ಟೊಮೆಟೊ") -> "🍅"
                                    activeCrop.contains("Onion", ignoreCase = true) || activeCrop.contains("ಈರುಳ್ಳಿ") -> "🧅"
                                    activeCrop.contains("Potato", ignoreCase = true) || activeCrop.contains("ಆಲೂ") -> "🥔"
                                    activeCrop.contains("Cabbage", ignoreCase = true) || activeCrop.contains("ಕೋಸು") -> "🥬"
                                    activeCrop.contains("Carrot", ignoreCase = true) || activeCrop.contains("ಕ್ಯಾರೆಟ್") -> "🥕"
                                    activeCrop.contains("Chilli", ignoreCase = true) || activeCrop.contains("ಮೆಣಸಿನ") -> "🌶️"
                                    else -> "🌾"
                                }
                                Text(cropEmoji, fontSize = 28.sp)
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$activeCrop (${if (isKannada) "ಹೈಬ್ರಿಡ್ ಪ್ರೀಮಿಯಂ" else "Hybrid Premium"})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${activeQty.toInt()} kg • ${activeHarvest?.qualityGrade ?: (if (isKannada) "ಗ್ರೇಡ್ A (ಉತ್ತಮ)" else "Grade A (Firm)")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) SoilFreshLeaf else SoilCropGreen
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isKannada)
                                    "📅 ಕೊಯ್ಲು: ${activeHarvest?.harvestDate ?: "2026-11-13"} (${activeHarvest?.harvestTime ?: "07:00 AM"})"
                                else
                                    "📅 Harvested: ${activeHarvest?.harvestDate ?: "2026-11-13"} at ${activeHarvest?.harvestTime ?: "07:00 AM"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = textMuted
                            )
                        }
                    }

                    HorizontalDivider(color = if (isDark) BorderDarkSubtle else SoilDeepEarth.copy(alpha = 0.08f))

                    // Status Pills - HIGH CONTRAST & READABLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SoilHarvestGold.copy(alpha = if (isDark) 0.22f else 0.18f),
                            border = BorderStroke(1.2.dp, SoilHarvestGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isKannada) "🏷️ ತಕ್ಷಣ ಮಾರಾಟಕ್ಕೆ ಸಿದ್ಧ" else "🏷️ Ready for Mandi Sale",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) SoilHarvestGold else SoilDeepEarth,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SoilNaturalSky.copy(alpha = if (isDark) 0.22f else 0.25f),
                            border = BorderStroke(1.2.dp, if (isDark) SoilNaturalSky else Color(0xFF6A99A2)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isKannada) "❄️ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್ ಲಭ್ಯ" else "❄️ Cold Storage Option",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) SoilNaturalSky else SoilDeepEarth,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // SECTION 3: MAIN 3 FARMER ACTION CTAs
        // 1. 🌱 Register Harvest
        // 2. 📊 Compare Markets
        // 3. ❄️ Find Cold Storage
        // ====================================================================
        Text(
            text = if (isKannada) "ಮುಖ್ಯ ಕೃಷಿ ನಿರ್ಧಾರಗಳು (Core Actions):" else "Core Farmer Decision Actions:",
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
            color = textPrimary
        )

        // CTA 1: Register Harvest
        FarmerActionTile(
            icon = "🌱",
            title = AppLocalization.t("register_cta"),
            subtitle = if (isKannada) "ಬೆಳೆ, ತೂಕ, ಗುಣಮಟ್ಟ ಮತ್ತು ಶೇಖರಣಾ ಅಗತ್ಯತೆ ನಮೂದಿಸಿ" else "Record crop, quantity, grade, date & storage needs",
            badgeText = AppLocalization.t("register_cta_sub"),
            containerColor = SoilCropGreen,
            contentColor = Color.White,
            badgeBgColor = Color.White,
            badgeTextColor = SoilCropGreen,
            onClick = { onDeclareHarvestClick?.invoke() }
        )

        // CTA 2: Compare Markets
        FarmerActionTile(
            icon = "📊",
            title = AppLocalization.t("compare_mandi_cta"),
            subtitle = if (isKannada) "ಕೋಲಾರ, ಬೆಂಗಳೂರು ಎಪಿಎಂಸಿ ನಿವ್ವಳ ಕೈಗೆ ಬರುವ ದರ ಪರಿಶೀಲಿಸಿ" else "Deterministic netback take-home after transport & commission",
            badgeText = AppLocalization.t("compare_mandi_cta_sub"),
            containerColor = SoilHarvestGold,
            contentColor = SoilDeepEarth,
            badgeBgColor = SoilDeepEarth,
            badgeTextColor = Color.White,
            onClick = { onMandiComparisonClick?.invoke(currentLot) }
        )

        // CTA 3: Find Cold Storage
        FarmerActionTile(
            icon = "❄️",
            title = AppLocalization.t("cold_storage_cta"),
            subtitle = if (isKannada) "ಎನ್‌ಎಚ್‌ಬಿ ನೋಂದಾಯಿತ ಸೌಲಭ್ಯಗಳು, ಸಾಮರ್ಥ್ಯ ಮತ್ತು ಸಾಗಾಟ ದೂರ" else "NHB verified storage, multi-leg routes & weather protection",
            badgeText = AppLocalization.t("cold_storage_cta_sub"),
            containerColor = SoilDeepEarth,
            contentColor = Color.White,
            badgeBgColor = SoilHarvestGold,
            badgeTextColor = SoilDeepEarth,
            onClick = { onColdStorageClick?.invoke(currentLot, activeCrop, activeQty) }
        )

        // ====================================================================
        // SECTION 4: SECONDARY AGRICULTURAL SHORTCUTS (2x2 GRID - HIGH CONTRAST)
        // ====================================================================
        Text(
            text = if (isKannada) "ತ್ವರಿತ ಸೇವೆಗಳು (Quick Services):" else "Secondary Services & Advisory:",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = textPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryServiceCard(
                icon = "📜",
                title = AppLocalization.t("harvest_history"),
                subtitle = if (isKannada) "ಹಿಂದಿನ ಲಾಟ್‌ಗಳು" else "Past lot records",
                iconBg = SoilHarvestGold.copy(alpha = if (isDark) 0.22f else 0.18f),
                iconBorder = SoilHarvestGold.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f),
                onClick = { onViewHistoryClick?.invoke() }
            )
            SecondaryServiceCard(
                icon = "🏛️",
                title = AppLocalization.t("schemes_cta"),
                subtitle = if (isKannada) "ಪಿಎಂ-ಕಿಸಾನ್, ವಿಮೆ" else "PM-KISAN, PMFBY",
                iconBg = SoilNaturalSky.copy(alpha = if (isDark) 0.22f else 0.25f),
                iconBorder = SoilNaturalSky.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
                onClick = { onViewBenefitsClick?.invoke() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryServiceCard(
                icon = "👥",
                title = AppLocalization.t("pooling_cta"),
                subtitle = if (isKannada) "ಸಾಗಾಟ ವೆಚ್ಚ ಕಡಿತ" else "Save 67% freight",
                iconBg = SoilCropGreen.copy(alpha = if (isDark) 0.22f else 0.14f),
                iconBorder = SoilCropGreen.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f),
                onClick = { onViewPoolingClick?.invoke() }
            )
            SecondaryServiceCard(
                icon = "⛅",
                title = AppLocalization.t("weather_cta"),
                subtitle = if (isKannada) "ಐಎಂಡಿ ಮಳೆ ಎಚ್ಚರಿಕೆ" else "IMD 48h rain alert",
                iconBg = SoilFreshLeaf.copy(alpha = if (isDark) 0.22f else 0.18f),
                iconBorder = SoilFreshLeaf.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f),
                onClick = { onViewWeatherClick?.invoke() }
            )
        }
    }
}

@Composable
private fun FarmerActionTile(
    icon: String,
    title: String,
    subtitle: String,
    badgeText: String,
    containerColor: Color,
    contentColor: Color,
    badgeBgColor: Color,
    badgeTextColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleEffect { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = contentColor.copy(alpha = 0.18f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 24.sp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.90f),
                    lineHeight = 16.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeBgColor,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun SecondaryServiceCard(
    icon: String,
    title: String,
    subtitle: String,
    iconBg: Color,
    iconBorder: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = UserSession.isDarkTheme
    Card(
        modifier = modifier.pressScaleEffect { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) SurfaceDarkCard else Color.White
        ),
        border = BorderStroke(1.2.dp, if (isDark) BorderDarkSubtle else SoilDeepEarth.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBg,
                border = BorderStroke(1.dp, iconBorder),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 20.sp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) SoilFieldPaper else SoilDeepEarth
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) SoilFreshLeaf else SoilCropGreen
                )
            }
        }
    }
}

@Composable
private fun FarmerPoolingDialog(
    isKannada: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (UserSession.isDarkTheme) SurfaceDarkCard else Color.White
            ),
            border = BorderStroke(1.2.dp, SoilCropGreen),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("👥", fontSize = 22.sp)
                        Text(
                            text = if (isKannada) "ರೈತ ಸಮೂಹ ವಾಹನ ಹಂಚಿಕೆ" else "Farmer Freight Pooling",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "✕",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SoilCropGreen.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, SoilCropGreen.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isKannada) "ಮುಳುಬಾಗಿಲು ➔ ಕೋಲಾರ ಎಪಿಎಂಸಿ ಕಾರಿಡಾರ್ (32 ಕಿ.ಮೀ)" else "Route: Mulbagal ➔ Kolar APMC Corridor (32 km)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoilCropGreen
                        )
                        Text(
                            text = if (isKannada) "ಹಂಚಿಕೆಯ ವಾಹನ: ಮಹೀಂದ್ರಾ ಬೊಲೆರೊ ಮ್ಯಾಕ್ಸಿ ಟ್ರಕ್ (1.5 ಟನ್)" else "Pooled Vehicle: Mahindra Bolero Maxi Truck (1.5 Ton)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = if (isKannada) "ಭಾಗವಹಿಸುವ ಸ್ಥಳೀಯ ರೈತರು:" else "Participating Cluster Farmers:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PoolingFarmerRow("1. Manjunatha Gowda (You)", "200 kg Tomato", "₹180 (Shared)")
                    PoolingFarmerRow("2. Venkatamma R.", "350 kg Tomato", "₹220 (Shared)")
                    PoolingFarmerRow("3. K. Narayanaswamy", "450 kg Potato", "₹250 (Shared)")
                }

                HorizontalDivider(color = SoilDeepEarth.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isKannada) "ವೈಯಕ್ತಿಕ ಸಾಗಾಟ ವೆಚ್ಚ: ₹550" else "Solo Freight Cost: ₹550",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isKannada) "ಹಂಚಿಕೆಯ ವೆಚ್ಚ: ₹180 (₹370 ಉಳಿತಾಯ!)" else "Pooled Cost: ₹180 (Save ₹370 / 67%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoilCropGreen
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen)
                ) {
                    Text(if (isKannada) "ಸರಿ, ಅರ್ಥವಾಯಿತು" else "Got It, Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PoolingFarmerRow(name: String, produce: String, freight: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(produce, fontSize = 10.sp, color = Color.Gray)
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = SoilFreshLeaf.copy(alpha = 0.15f)
        ) {
            Text(
                freight,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = SoilCropGreen,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun WeatherAdvisoryDialog(
    isKannada: Boolean,
    onDismiss: () -> Unit,
    onFindStorageClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (UserSession.isDarkTheme) SurfaceDarkCard else Color.White
            ),
            border = BorderStroke(1.2.dp, SoilHarvestGold),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⛈️", fontSize = 22.sp)
                        Text(
                            text = if (isKannada) "ಐಎಂಡಿ ಹವಾಮಾನ ರಕ್ಷಣೆ" else "IMD Weather Advisory",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "✕",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF3E0),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isKannada) "ಎಚ್ಚರಿಕೆ: ಮುಂದಿನ 36-48 ಗಂಟೆಗಳಲ್ಲಿ ಭಾರಿ ಮಳೆ (38mm)" else "Alert: Heavy Rainfall (38mm) in next 36-48h",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isKannada) "ಕೋಲಾರ, ಮುಳುಬಾಗಿಲು ಮತ್ತು ಮಾಲೂರು ತಾಲೂಕುಗಳಲ್ಲಿ ತೇವಾಂಶ ಹಾನಿ ಸಂಭವ." else "Moisture and skin spotting risk for harvested tomatoes across Kolar & Mulbagal.",
                            fontSize = 11.sp,
                            color = Color(0xFF5D4037)
                        )
                    }
                }

                Text(
                    text = if (isKannada) "ಶಿಫಾರಸು ಮಾಡಿದ ಕ್ರಮಗಳು:" else "Recommended Protective Steps:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (isKannada)
                        "1. ತಕ್ಷಣ ಸಮೀಪದ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್‌ಗೆ (4.2 ಕಿ.ಮೀ) ಸಾಗಿಸಿ ಅಥವಾ ಇಂದೇ ಮಾರುಕಟ್ಟೆಗೆ ರವಾನಿಸಿ.\n2. ಬಯಲು ಹೊಲದಲ್ಲಿ ಹಣ್ಣುಗಳನ್ನು ತೆರೆದಿಡಬೇಡಿ."
                    else
                        "1. Move ripe tomatoes to verified cold storage (4.2 km) or dispatch to APMC mandi immediately.\n2. Avoid open field storage under plastic sheets.",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isKannada) "ಮುಚ್ಚಿ" else "Dismiss")
                    }

                    Button(
                        onClick = onFindStorageClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text(if (isKannada) "❄️ ಶೀತಲಾಗಾರ ಶೋಧಿಸಿ" else "❄️ Find Cold Storage", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

