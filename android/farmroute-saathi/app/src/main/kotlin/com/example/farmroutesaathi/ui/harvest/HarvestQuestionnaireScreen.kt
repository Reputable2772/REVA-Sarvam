package com.example.farmroutesaathi.ui.harvest

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.HarvestDeclaration
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HarvestQuestionnaireScreen(
    onHarvestSubmitted: (String) -> Unit,
    onSkip: () -> Unit
) {
    var isKannada by remember { mutableStateOf(false) }

    // Form states
    var crop by remember { mutableStateOf("Tomato") }
    var harvestDate by remember { mutableStateOf("2026-11-13") }
    var harvestTime by remember { mutableStateOf("07:00 AM") }
    var expectedDateTime by remember { mutableStateOf("2026-11-14 09:00 AM") }
    var sellImmediately by remember { mutableStateOf(true) }
    var quantityKg by remember { mutableStateOf("200") }

    // Voice / STT State
    var isListening by remember { mutableStateOf(false) }
    var voiceFeedback by remember { mutableStateOf<String?>(null) }
    var recognizedFields by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val farmer = UserSession.currentFarmer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(
                        Brush.verticalGradient(listOf(ForestGreenDeep, FreshLeafGreen))
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = HarvestGold,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🌾", fontSize = 22.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ಕೊಯ್ಲು ವಿವರಗಳ ಪ್ರಶ್ನಾವಳಿ" else "Harvest Questionnaire",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isKannada) "ರೈತ: ${farmer?.name ?: "ಕಿಸಾನ್"}" else "Farmer: ${farmer?.name ?: "Kisan"}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        // Language Toggle Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = HarvestGold,
                            modifier = Modifier.clickable { isKannada = !isKannada }
                        ) {
                            Text(
                                text = if (isKannada) "🌐 ಕನ್ನಡ" else "🌐 English",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDeep,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isKannada)
                            "ನಿಮ್ಮ ಇಂದಿನ ಬೆಳೆ ಕೊಯ್ಲು ವಿವರಗಳನ್ನು ನಮೂದಿಸಿ ಅಥವಾ ಕೆಳಗಿನ ಧ್ವನಿ ಮೈಕ್ರೊಫೋನ್ ಬಳಸಿ ಮಾತನಾಡಿ."
                        else
                            "Declare your current crop harvest details below, or speak naturally using the Sarvam Voice AI mic.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.90f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // SARVAM SPEECH-TO-TEXT VOICE ASSISTANT CARD
            // ==========================================
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎙️", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKannada) "ಸರ್ವಂ ಧ್ವನಿ ಸಹಾಯಕ (STT)" else "Sarvam Voice STT Assistant",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ForestGreenDeep
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = FreshLeafGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "SARVAM SAARAS v4",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDeep,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Voice Listening Ripple Button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isListening) HarvestGold.copy(alpha = 0.25f) else ForestGreenDeep.copy(alpha = 0.06f),
                        border = BorderStroke(
                            1.5.dp,
                            if (isListening) HarvestGold else ForestGreenDeep.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isListening = !isListening
                                if (isListening) {
                                    scope.launch {
                                        // Trigger Sarvam STT understanding
                                        val sampleSpeech = if (isKannada)
                                            "ನಾನು ಇಂದು ಬೆಳಿಗ್ಗೆ 7 ಗಂಟೆಗೆ 250 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ, ನಾಳೆ ಬೆಳಿಗ್ಗೆ ಮಾರಾಟ ಮಾಡಬೇಕು"
                                        else
                                            "I harvested 250 kg tomato today at 7 AM, planning to sell immediately tomorrow morning"

                                        val res = ApiClient.processVoiceCommand(sampleSpeech, if (isKannada) "kn-IN" else "en-IN")
                                        isListening = false

                                        // Parse recognized fields and update form
                                        if (res.contains("\"crop\":")) {
                                            val c = res.substringAfter("\"crop\":\"").substringBefore("\"")
                                            if (c.isNotBlank()) crop = c
                                        }
                                        if (res.contains("\"quantity_kg\":")) {
                                            val q = res.substringAfter("\"quantity_kg\":").substringBefore(",")
                                                .substringBefore("}").trim()
                                            if (q.toDoubleOrNull() != null) quantityKg = q.toDouble().toInt().toString()
                                        }
                                        if (res.contains("\"harvest_date\":")) {
                                            val hd = res.substringAfter("\"harvest_date\":\"").substringBefore("\"")
                                            if (hd.isNotBlank()) harvestDate = hd
                                        }
                                        if (res.contains("\"harvest_time\":")) {
                                            val ht = res.substringAfter("\"harvest_time\":\"").substringBefore("\"")
                                            if (ht.isNotBlank()) harvestTime = ht
                                        }
                                        if (res.contains("\"expected_date_time\":")) {
                                            val ed = res.substringAfter("\"expected_date_time\":\"").substringBefore("\"")
                                            if (ed.isNotBlank()) expectedDateTime = ed
                                        }
                                        if (res.contains("\"sell_immediately\":")) {
                                            val imm = res.substringAfter("\"sell_immediately\":").substringBefore(",")
                                                .substringBefore("}").trim().toBoolean()
                                            sellImmediately = imm
                                        }

                                        voiceFeedback = if (isKannada)
                                            "✓ ಗುರುತಿಸಲಾಗಿದೆ: 250 ಕೆಜಿ $crop, ಕೊಯ್ಲು: $harvestDate $harvestTime"
                                        else
                                            "✓ Recognized: 250kg $crop, Harvest: $harvestDate $harvestTime"
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(if (isListening) "🔊" else "🎙️", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isListening)
                                    (if (isKannada) "ಆಲಿಸುತ್ತಿದೆ... ಮಾತನಾಡಿ" else "Listening with Sarvam STT...")
                                else
                                    (if (isKannada) "ಧ್ವನಿ ಮೂಲಕ ನಮೂದಿಸಲು ಟ್ಯಾಪ್ ಮಾಡಿ" else "Tap to Speak Harvest Details"),
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDeep,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Spoken Feedback Banner
                    if (voiceFeedback != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ForestGreenDeep.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = voiceFeedback ?: "",
                                color = ForestGreenDeep,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Quick Voice Test Phrases
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isKannada) "ಮಾದರಿ ಧ್ವನಿ ಪ್ರಾಂಪ್ಟ್‌ಗಳು (ಪರೀಕ್ಷಿಸಲು ಟ್ಯಾಪ್ ಮಾಡಿ):" else "Sample Voice Phrases (tap to simulate speech):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                crop = "Tomato"
                                quantityKg = "200"
                                harvestDate = "2026-11-13"
                                harvestTime = "07:00 AM"
                                sellImmediately = true
                                voiceFeedback = "✓ Applied: 200kg Tomato, Today 7AM"
                            },
                            label = { Text("🍅 200kg Tomato", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                crop = "Onion"
                                quantityKg = "300"
                                harvestDate = "2026-11-13"
                                harvestTime = "06:30 AM"
                                sellImmediately = true
                                voiceFeedback = "✓ Applied: 300kg Onion, Today 6:30AM"
                            },
                            label = { Text("🧅 300kg Onion", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {
                                crop = "Potato"
                                quantityKg = "500"
                                sellImmediately = false
                                voiceFeedback = "✓ Applied: 500kg Potato (Cold Storage)"
                            },
                            label = { Text("❄️ Cold Storage", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // HARVEST PARAMETERS QUESTIONNAIRE FORM
            // ==========================================
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {

                    // 1. CROP SELECTION
                    Text(
                        text = if (isKannada) "1. ಬೆಳೆ (Crop)" else "1. Select Crop",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val cropsList = listOf("Tomato", "Onion", "Potato", "Cabbage", "Carrot", "Chilli")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cropsList.take(3).forEach { c ->
                            FilterChip(
                                selected = crop == c,
                                onClick = { crop = c },
                                label = { Text(c, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cropsList.drop(3).forEach { c ->
                            FilterChip(
                                selected = crop == c,
                                onClick = { crop = c },
                                label = { Text(c, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. HARVEST DATE
                    Text(
                        text = if (isKannada) "2. ಕೊಯ್ಲು ದಿನಾಂಕ (Harvest Date)" else "2. Harvest Date",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = harvestDate,
                        onValueChange = { harvestDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("📅") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(
                            onClick = { harvestDate = "2026-11-13" },
                            label = { Text("Today (ಇಂದು)", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { harvestDate = "2026-11-12" },
                            label = { Text("Yesterday (ನಿನ್ನೆ)", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { harvestDate = "2026-11-14" },
                            label = { Text("Tomorrow (ನಾಳೆ)", fontSize = 10.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. HARVEST TIME
                    Text(
                        text = if (isKannada) "3. ಕೊಯ್ಲು ಸಮಯ (Harvest Time)" else "3. Harvest Time",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = harvestTime,
                        onValueChange = { harvestTime = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("⏰") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(onClick = { harvestTime = "06:00 AM" }, label = { Text("06:00 AM", fontSize = 10.sp) })
                        SuggestionChip(onClick = { harvestTime = "07:30 AM" }, label = { Text("07:30 AM", fontSize = 10.sp) })
                        SuggestionChip(onClick = { harvestTime = "11:00 AM" }, label = { Text("11:00 AM", fontSize = 10.sp) })
                        SuggestionChip(onClick = { harvestTime = "04:30 PM" }, label = { Text("04:30 PM", fontSize = 10.sp) })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. EXPECTED DATE / TIME
                    Text(
                        text = if (isKannada) "4. ನಿರೀಕ್ಷಿತ ಸಾಗಣೆ ಸಮಯ (Expected Pickup/Delivery)" else "4. Expected Date & Time",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = expectedDateTime,
                        onValueChange = { expectedDateTime = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("🚚") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(
                            onClick = { expectedDateTime = "2026-11-14 09:00 AM" },
                            label = { Text("+24h Morning", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { expectedDateTime = "2026-11-14 05:00 PM" },
                            label = { Text("+24h Evening", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { expectedDateTime = "2026-11-13 06:00 PM" },
                            label = { Text("Today Evening", fontSize = 10.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. PLANNING TO SELL IMMEDIATELY?
                    Text(
                        text = if (isKannada) "5. ತಕ್ಷಣ ಮಾರಾಟ ಮಾಡಲು ಯೋಜಿಸುತ್ತಿದ್ದೀರಾ?" else "5. Planning to Sell Immediately?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Yes Option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sellImmediately) ForestGreenDeep else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, if (sellImmediately) ForestGreenDeep else Color.LightGray),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { sellImmediately = true }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("⚡", fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isKannada) "ಹೌದು, ತಕ್ಷಣ ಮಾರಾಟ" else "Yes, Sell Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sellImmediately) Color.White else Color.Black
                                )
                                Text(
                                    text = if (isKannada) "ವರ್ಚುವಲ್ FPO ಪೂಲಿಂಗ್" else "Virtual FPO Pool",
                                    fontSize = 10.sp,
                                    color = if (sellImmediately) Color.White.copy(alpha = 0.8f) else Color.Gray
                                )
                            }
                        }

                        // No Option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (!sellImmediately) ForestGreenDeep else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, if (!sellImmediately) ForestGreenDeep else Color.LightGray),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { sellImmediately = false }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("❄️", fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isKannada) "ಇಲ್ಲ, ಶೀತಲಾಗಾರ" else "No, Cold Storage",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!sellImmediately) Color.White else Color.Black
                                )
                                Text(
                                    text = if (isKannada) "ಶೇಖರಿಸಿಡಿ" else "Hold in Facility",
                                    fontSize = 10.sp,
                                    color = if (!sellImmediately) Color.White.copy(alpha = 0.8f) else Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 6. QUANTITY (KG)
                    Text(
                        text = if (isKannada) "6. ಅಂದಾಜು ತೂಕ (Quantity in kg)" else "6. Declared Quantity (kg)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenDeep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = quantityKg,
                        onValueChange = { quantityKg = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Text("⚖️") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(onClick = { quantityKg = "100" }, label = { Text("100 kg", fontSize = 10.sp) })
                        SuggestionChip(onClick = { quantityKg = "200" }, label = { Text("200 kg", fontSize = 10.sp) })
                        SuggestionChip(onClick = { quantityKg = "300" }, label = { Text("300 kg", fontSize = 10.sp) })
                        SuggestionChip(onClick = { quantityKg = "500" }, label = { Text("500 kg", fontSize = 10.sp) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // ACTION BUTTONS: CONFIRM OR SKIP
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = {
                        isSubmitting = true
                        scope.launch {
                            val fId = farmer?.phone ?: "9845012345"
                            val qVal = quantityKg.toDoubleOrNull() ?: 200.0

                            val res = ApiClient.declareHarvest(
                                farmerId = fId,
                                crop = crop,
                                quantityKg = qVal,
                                harvestDate = harvestDate,
                                harvestTime = harvestTime,
                                expectedDateTime = expectedDateTime,
                                sellImmediately = sellImmediately
                            )
                            isSubmitting = false

                            // Extract Unique Harvest ID from response
                            val uniqueHarvestId = if (res.contains("\"harvest_id\":\"")) {
                                res.substringAfter("\"harvest_id\":\"").substringBefore("\"")
                            } else if (res.contains("\"lot_id\":\"")) {
                                res.substringAfter("\"lot_id\":\"").substringBefore("\"")
                            } else {
                                "FR-KLR-20261113-0001"
                            }

                            // Store in UserSession for the entire app!
                            UserSession.setHarvest(
                                HarvestDeclaration(
                                    harvestId = uniqueHarvestId,
                                    crop = crop,
                                    harvestDate = harvestDate,
                                    harvestTime = harvestTime,
                                    expectedDateTime = expectedDateTime,
                                    sellImmediately = sellImmediately,
                                    quantityKg = qVal
                                )
                            )

                            // Navigate to Main!
                            onHarvestSubmitted(uniqueHarvestId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = if (isKannada) "🌾 ಕೊಯ್ಲು ದೃಢೀಕರಿಸಿ (DECLARE HARVEST)" else "🌾 CONFIRM & DECLARE HARVEST",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = { onSkip() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isKannada) "ಈಗಲೇ ಬಿಟ್ಟುಬಿಡಿ (ಡೀಫಾಲ್ಟ್ ಲಾಟ್ ಬಳಸಿ)" else "Skip for now (Use default lot FR-KLR-20261113-0001)",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
