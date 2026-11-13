package com.example.farmroutesaathi.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch

enum class RoleTab(val labelEn: String, val labelKn: String) {
    FARMER("Farmer", "ರೈತ"),
    BUYER("Buyer", "ಖರೀದಿದಾರ"),
    OPERATOR("Operator", "ಆಪರೇಟರ್"),
    DEMO("Demo Flow", "ಡೆಮೊ")
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isKannada by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(RoleTab.DEMO) }
    var backendStatus by remember { mutableStateOf("Checking backend...") }
    var lastApiResponse by remember { mutableStateOf("No checkpoint executed yet. Click 'Demo Reset' to initialize.") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val health = ApiClient.getHealth()
        backendStatus = if (health.contains("healthy")) "🟢 FastAPI Online (:8000)" else "🟡 Backend: $health"
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isKannada) "ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ" else "FarmRoute Saathi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (isKannada) "ರೈತ-ಮೊದಲ ವರ್ಚುವಲ್ ಎಫ್‌ಪಿಒ" else "Farmer-First Virtual FPO Intelligence",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    // Language Toggle Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { isKannada = !isKannada }
                    ) {
                        Text(
                            text = if (isKannada) "ಕನ್ನಡ (KN)" else "English (EN)",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = backendStatus,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Role Tab Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                RoleTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = if (isKannada) tab.labelKn else tab.labelEn,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    RoleTab.DEMO -> DemoControlView(
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
                    RoleTab.FARMER -> FarmerView(isKannada = isKannada)
                    RoleTab.BUYER -> BuyerView(isKannada = isKannada)
                    RoleTab.OPERATOR -> OperatorView(isKannada = isKannada)
                }
            }
        }
    }
}

@Composable
fun DemoControlView(
    isKannada: Boolean,
    isLoading: Boolean,
    lastApiResponse: String,
    onExecuteReset: () -> Unit,
    onExecuteCheckpoint: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isKannada) "🚀 3-ನಿಮಿಷದ ಹ್ಯಾಕಥಾನ್ ಡೆಮೊ ಹಂತಗಳು" else "🚀 3-Minute Hackathon Demo Workflow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isKannada) 
                    "ಪ್ರತಿ ಬಟನ್ ನೇರವಾಗಿ ಸ್ಥಳೀಯ FastAPI ಬ್ಯಾಕೆಂಡ್ ಜೊತೆ ಸಂಪರ್ಕ ಸಾಧಿಸಿ ಲೆಕ್ಕಾಚಾರಗಳನ್ನು ಪ್ರದರ್ಶಿಸುತ್ತದೆ."
                    else "Each button calls live FastAPI endpoints, performing exact deterministic calculations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onExecuteReset,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isKannada) "ಹಂತ 4: ಇನ್‌ಸ್ಟಂಟ್ ಪಾವತಿ" else "CP 4: Instant Settlement", fontSize = 11.sp)
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Divider()

            Text(
                text = "Live Backend Response Log:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lastApiResponse,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun FarmerView(isKannada: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isKannada) "👨‍🌾 ರೈತ ನೋಂದಣಿ & ನೆಟ್‌ಬ್ಯಾಕ್ ಹೋಲಿಕೆ" else "👨‍🌾 Harvest Registration & Netback Arbitrage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Lot Info Card
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Farmer: Manjunath Gowda (Mulbagal)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Declared Harvest: 100 kg Tomato | Lot ID: FR-KLR-00251", fontSize = 12.sp)
                    Text("Input Method: Sarvam AI Saaras Kannada Voice Input 🎙️", fontSize = 11.sp, color = Color(0xFF1B5E20))
                }
            }

            Text(
                text = if (isKannada) "ಮಾರುಕಟ್ಟೆ ಆದಾಯ ಹೋಲಿಕೆ (Net Returns):" else "Deterministic Netback Comparison:",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            // 3-way route comparison
            RouteCard(
                title = "1. Direct Virtual FPO (Recommended)",
                gross = "₹2,600 (₹26/kg)",
                deductions = "Shared Transport: -₹180 | Zero Mandi Cut",
                net = "Net Cash: ₹2,420 (+38% vs APMC)",
                isRecommended = true
            )

            RouteCard(
                title = "2. Local Mandi (Kolar APMC)",
                gross = "₹2,400 (₹24/kg)",
                deductions = "Transport: -₹250 | 8% Commission: -₹192",
                net = "Net Cash: ₹1,958",
                isRecommended = false
            )

            RouteCard(
                title = "3. Distant Mandi (Bangarpet APMC) ⚠️ TRAP",
                gross = "₹2,800 (₹28/kg Headline Rate)",
                deductions = "High Transport: -₹550 | 8% Commission: -₹224",
                net = "Net Cash: ₹1,786 (Lower cash despite high rate!)",
                isRecommended = false,
                isWarning = true
            )
        }
    }
}

@Composable
fun RouteCard(
    title: String,
    gross: String,
    deductions: String,
    net: String,
    isRecommended: Boolean,
    isWarning: Boolean = false
) {
    val borderColor = when {
        isRecommended -> Color(0xFF2E7D32)
        isWarning -> MaterialTheme.colorScheme.error
        else -> Color.LightGray
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isRecommended -> Color(0xFFE8F5E9)
            isWarning -> Color(0xFFFFEBEE)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(text = "Gross: $gross", fontSize = 11.sp)
            Text(text = deductions, fontSize = 11.sp, color = Color.Gray)
            Text(text = net, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (isRecommended) Color(0xFF1B5E20) else Color.Unspecified)
        }
    }
}

@Composable
fun BuyerView(isKannada: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isKannada) "🛒 ಖರೀದಿದಾರರ ಬೇಡಿಕೆ & ಪೂಲಿಂಗ್ ಸ್ಥಿತಿ" else "🛒 Buyer Requirements & Demand Aggregation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Buyer: FreshDirect Supermarket (Bengaluru)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Required: 800 kg Grade A Tomato @ ₹26/kg", fontSize = 12.sp)

            Divider()

            Text("Largest-Remainder Allocation (6 Farmers Pooled):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            val allocations = listOf(
                "FR-KLR-00251 (Manjunath) - Declared: 100 kg -> Allocated: 66.67 kg",
                "FR-KLR-00252 (Ramesh) - Declared: 200 kg -> Allocated: 133.33 kg",
                "FR-KLR-00253 (Suresh) - Declared: 250 kg -> Allocated: 166.67 kg",
                "FR-KLR-00254 (Venkatesh) - Declared: 150 kg -> Allocated: 100.00 kg",
                "FR-KLR-00255 (Anand) - Declared: 300 kg -> Allocated: 200.00 kg",
                "FR-KLR-00256 (Gowramma) - Declared: 200 kg -> Allocated: 133.33 kg"
            )
            allocations.forEach {
                Text("• $it", fontSize = 11.sp)
            }
            Text("Total Pooled: Exactly 800.00 kg (0.00 kg drift)", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), fontSize = 12.sp)
        }
    }
}

@Composable
fun OperatorView(isKannada: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isKannada) "⚖️ ಆಪರೇಟರ್ ತೂಕ & ಗ್ರೇಡಿಂಗ್ ದೃಢೀಕರಣ" else "⚖️ Operator Weighing & Grade Verification",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Lot: FR-KLR-00251 (Farmer: Manjunath Gowda)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Weighment Scale: Digital Certified (Kolar Village Collection Point)", fontSize = 12.sp)

            Divider()

            Text("Grade Breakdown:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("• Grade A (Premium Firm): 85 kg @ ₹26.00/kg -> ₹2,210.00", fontSize = 12.sp)
            Text("• Grade B (Slightly Soft): 15 kg @ ₹18.00/kg -> ₹270.00", fontSize = 12.sp)
            Text("• Total Weighed: 100.00 kg (Gross: ₹2,480.00)", fontWeight = FontWeight.Bold, fontSize = 12.sp)

            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Operator Status: VERIFIED & SEALED 🔒\nProceed to Escrow Payout",
                    color = Color(0xFF1B5E20),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
