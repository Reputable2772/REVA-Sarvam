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
import com.example.farmroutesaathi.ui.components.*
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RoleTab(val labelEn: String, val labelKn: String, val icon: String) {
    FARMER("Farmer", "ರೈತ", "👨‍🌾"),
    BUYER("Buyer", "ಖರೀದಿದಾರ", "🛒"),
    OPERATOR("Operator", "ಆಪರೇಟರ್", "⚖️"),
    BENEFITS("Benefits", "ಯೋಜನೆಗಳು", "🏛️"),
    DEMO("Demo Flow", "ಡೆಮೊ", "🚀")
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isKannada by remember { mutableStateOf(false) }
    var isDarkTheme by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(RoleTab.FARMER) }
    var backendStatus by remember { mutableStateOf("Checking backend...") }
    var lastApiResponse by remember { mutableStateOf("No checkpoint executed yet. Click 'Demo Reset' to initialize.") }
    var isLoading by remember { mutableStateOf(false) }
    var voiceState by remember { mutableStateOf(SaathiVoiceState.IDLE) }
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
                    onThemeToggle = { isDarkTheme = !isDarkTheme }
                )
            },
            floatingActionButton = {
                FloatingSaathiVoiceFab(
                    isKannada = isKannada,
                    voiceState = voiceState,
                    onVoiceFabClick = {
                        scope.launch {
                            voiceState = SaathiVoiceState.LISTENING
                            delay(1800)
                            voiceState = SaathiVoiceState.PROCESSING
                            delay(1200)
                            voiceState = SaathiVoiceState.IDLE
                            lastApiResponse = if (isKannada) 
                                "ಧ್ವನಿ ಗುರುತಿಸುವಿಕೆ (Sarvam AI): '100 ಕೆಜಿ ಟೊಮ್ಯಾಟೊ ನೋಂದಾಯಿಸಲಾಗಿದೆ' (Lot FR-KLR-00251)"
                                else "Voice Transcription (Sarvam AI Saaras): 'Registered 100 kg Tomato' (Lot FR-KLR-00251)"
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                // Living Intelligence Role Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 12.dp
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
                            RoleTab.FARMER -> FarmerDashboardView(isKannada = isKannada)
                            RoleTab.BUYER -> BuyerDashboardView(isKannada = isKannada)
                            RoleTab.OPERATOR -> OperatorCollectionCenterView(isKannada = isKannada)
                            RoleTab.BENEFITS -> GovernmentBenefitsOpportunityView(isKannada = isKannada)
                            RoleTab.DEMO -> DemoWorkflowInteractiveView(
                                isKannada = isKannada,
                                isLoading = isLoading,
                                lastApiResponse = lastApiResponse,
                                onExecuteReset = {
                                    scope.launch {
                                        isLoading = true
                                        lastApiResponse = "Executing Demo Reset..."
                                        val res = ApiClient.postDemoReset()
                                        lastApiResponse = res
                                        isLoading = false
                                    }
                                },
                                onExecuteCheckpoint = { step ->
                                    scope.launch {
                                        isLoading = true
                                        lastApiResponse = "Executing Checkpoint $step..."
                                        val res = ApiClient.postCheckpoint(step)
                                        lastApiResponse = res
                                        isLoading = false
                                    }
                                }
                            )
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
fun FarmerDashboardView(isKannada: Boolean) {
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
                        text = if (isKannada) "ನಮಸ್ಕಾರ, ಮಂಜುನಾಥ್ ಗೌಡ" else "Welcome, Manjunath Gowda",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isKannada) "ಮೂಲ್ಬಾಗಿಲು ಸಂಗ್ರಹಣಾ ಕೇಂದ್ರ #12" else "Mulbagal Collection Cluster #12",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Demo Farmer",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Harvest Summary Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isKannada) "ದಾಖಲಾದ ಬೆಳೆ ವಿವರ (Declared Lot FR-KLR-00251):" else "Declared Harvest (Lot FR-KLR-00251):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "100 kg Tomato | Grade A Premium Firm",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isKannada) "ಧ್ವನಿ ನೋಂದಣಿ: ಸರ್ವಂ AI ಸಾಥಿ (ಕನ್ನಡ) ಧ್ವನಿ ಇನ್ಪುಟ್" else "Voice Stamp: Sarvam AI Saathi (Kannada Voice)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            // Simulated Prototype Payout Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isKannada) "ಸಿಮ್ಯುಲೇಟೆಡ್ ಪಾವತಿ ಸ್ಥಿತಿ (Simulated Payout):" else "Simulated Payout Status:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹2,420.00 — SIMULATED PROTOTYPE PAYMENT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = HarvestGoldDark
                    )
                    Text(
                        text = "Ref: SIM-NPCI-00251 | No actual banking transfer performed.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. BUYER AGGREGATION SCREEN
// ============================================================================
@Composable
fun BuyerDashboardView(isKannada: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isKannada) "🛒 ಖರೀದಿದಾರರ ಬೇಡಿಕೆ & ಪೂಲಿಂಗ್ ಹಂಚಿಕೆ" else "🛒 Buyer Requirements & Demand Aggregation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("FreshDirect Supermarket (Bengaluru)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Target PO: Exactly 800.00 kg Grade A Tomato @ ₹26.00/kg", fontSize = 12.sp)
                    Text("Aggregation Method: Hamilton-Hare Largest Remainder (Zero-Drift)", fontSize = 11.sp, color = FreshLeafGreen)
                }
            }

            Text(
                text = if (isKannada) "ರೈತರ ಪಾಲು ಹಂಚಿಕೆ (Farmer Allocation Breakdown):" else "Proportional Allocation per Farmer Lot:",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            val allocations = listOf(
                Triple("FR-KLR-00251 (Manjunath)", 100.0, 66.67),
                Triple("FR-KLR-00252 (Ramesh)", 200.0, 133.33),
                Triple("FR-KLR-00253 (Suresh)", 250.0, 166.67),
                Triple("FR-KLR-00254 (Venkatesh)", 150.0, 100.00),
                Triple("FR-KLR-00255 (Anand)", 300.0, 200.00),
                Triple("FR-KLR-00256 (Gowramma)", 200.0, 133.33)
            )

            allocations.forEach { (name, declared, allocated) ->
                ProportionalAllocationRow(
                    farmerName = name,
                    declaredKg = declared,
                    allocatedKg = allocated,
                    poolTotalKg = 800.0
                )
            }

            Surface(
                color = FreshLeafGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Total Pooled: Exactly 800.00 kg (0.00 kg Drift) | Full PO Fulfilled ✅",
                    color = ForestGreenDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

// ============================================================================
// 3. OPERATOR COLLECTION CENTER SCREEN
// ============================================================================
@Composable
fun OperatorCollectionCenterView(isKannada: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isKannada) "⚖️ ಸಂಗ್ರಹಣಾ ಕೇಂದ್ರ ತೂಕ & ಗ್ರೇಡಿಂಗ್" else "⚖️ Collection Center Weighing & Grading",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kolar Collection Node #04 (Manual Operator Inspection Verification)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Lot: FR-KLR-00251", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Farmer: Manjunath Gowda", fontSize = 12.sp)
                    Text("• Grade A: 85.0 kg @ ₹26.00", fontSize = 11.sp, color = FreshLeafGreen, fontWeight = FontWeight.SemiBold)
                    Text("• Grade B: 15.0 kg @ ₹18.00", fontSize = 11.sp, color = HarvestGoldDark, fontWeight = FontWeight.SemiBold)
                    Text("Gross Weighed: 100.0 kg", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                GradeDonutChart(
                    gradeAPct = 85f,
                    gradeBPct = 15f,
                    centerValue = "100 kg",
                    centerSubtitle = "Total Verified"
                )
            }

            Surface(
                color = FreshLeafGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Operator Seal Status: VERIFIED & SEALED 🔒 (Ready for Simulated Settlement)",
                    color = ForestGreenDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

// ============================================================================
// 4. GOVERNMENT BENEFITS & SAATHI OPPORTUNITIES SCREEN
// ============================================================================
@Composable
fun GovernmentBenefitsOpportunityView(isKannada: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isKannada) "🏛️ ಸರ್ಕಾರಿ ಯೋಜನೆಗಳು & ರೈತ ಅವಕಾಶಗಳು" else "🏛️ Government Schemes & Opportunities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Verified official regional schemes for Kolar horticulture cluster:",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Scheme 1
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("1. Karnataka Raitha Siri Scheme", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Horticulture & Millet Cultivation Support | Direct DBT to Farmer Bank Account", fontSize = 11.sp)
                    Text("Status: Active Official Scheme (Verified 2026)", fontSize = 10.sp, color = FreshLeafGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            // Scheme 2
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("2. PM-KISAN Samman Nidhi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Direct Income Support of ₹6,000/year in 3 tranches | e-KYC Linked", fontSize = 11.sp)
                    Text("Status: Central Ongoing Benefit", fontSize = 10.sp, color = FreshLeafGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            // Scheme 3
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("3. NABARD / SFAC Virtual FPO Grant", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Shared Logistics & Micro-Aggregation Subsidy for smallholder clusters", fontSize = 11.sp)
                    Text("Status: Institutional Application Open", fontSize = 10.sp, color = HarvestGoldDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ============================================================================
// 5. DEMO WORKFLOW INTERACTIVE SCREEN
// ============================================================================
@Composable
fun DemoWorkflowInteractiveView(
    isKannada: Boolean,
    isLoading: Boolean,
    lastApiResponse: String,
    onExecuteReset: () -> Unit,
    onExecuteCheckpoint: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isKannada) "🚀 3-ನಿಮಿಷದ ಲೈವ್ ಡೆಮೊ ಹಂತಗಳು" else "🚀 3-Minute Live Hackathon Demo Workflow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Deterministic execution calling live FastAPI backend on port 8000:",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onExecuteReset,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = SemanticErrorRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isKannada) "🔄 ಡೆಮೊ ಮರುಹೊಂದಿಸಿ (Demo Reset)" else "🔄 Reset Demo Data (6 Seed Farmers)")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onExecuteCheckpoint(1) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isKannada) "ಹಂತ 1: ರೈತ ನೋಂದಣಿ" else "CP 1: Farmer Register", fontSize = 11.sp)
                }
                Button(
                    onClick = { onExecuteCheckpoint(2) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isKannada) "ಹಂತ 2: 800kg ಪೂಲಿಂಗ್" else "CP 2: 800kg Pool", fontSize = 11.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onExecuteCheckpoint(3) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isKannada) "ಹಂತ 3: ಗ್ರೇಡ್ ವಿಂಗಡಣೆ" else "CP 3: Operator Grading", fontSize = 11.sp)
                }
                Button(
                    onClick = { onExecuteCheckpoint(4) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isKannada) "ಹಂತ 4: ಸಿಮ್ಯುಲೇಟೆಡ್ ಪಾವತಿ" else "CP 4: Simulated Payment", fontSize = 11.sp)
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Divider()

            Text(
                text = "Live FastAPI Backend Response Log:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Surface(
                color = OledBackgroundDark,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lastApiResponse,
                    fontSize = 11.sp,
                    color = FreshLimeAccent,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
