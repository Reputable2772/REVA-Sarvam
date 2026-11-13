package com.example.farmroutesaathi.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.data.ApiClient
import com.example.farmroutesaathi.data.FarmerProfile
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.theme.*
import kotlinx.coroutines.launch

enum class AuthStep {
    PHONE_ENTRY,
    MPIN_ENTRY,
    ONBOARDING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    var isKannada by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(AuthStep.PHONE_ENTRY) }
    var phoneNumber by remember { mutableStateOf("") }
    var mpinInput by remember { mutableStateOf("") }
    var detectedFarmerName by remember { mutableStateOf("") }
    var detectedDistrict by remember { mutableStateOf("Kolar") }
    var detectedState by remember { mutableStateOf("Karnataka") }

    // Onboarding form state
    var regName by remember { mutableStateOf("") }
    var regState by remember { mutableStateOf("Karnataka") }
    var regDistrict by remember { mutableStateOf("Kolar") }
    var regPincode by remember { mutableStateOf("563101") }
    var regMpin by remember { mutableStateOf("") }
    var regConfirmMpin by remember { mutableStateOf("") }

    var statesList by remember { mutableStateOf(listOf("Karnataka", "Andhra Pradesh", "Tamil Nadu", "Maharashtra")) }
    var districtsList by remember { mutableStateOf(listOf("Kolar", "Chikkaballapura", "Bangalore Rural", "Hassan", "Mandya")) }
    var stateDropdownExpanded by remember { mutableStateOf(false) }
    var districtDropdownExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                statesList = ApiClient.getStates()
                districtsList = ApiClient.getDistricts(regState)
            } catch (e: Exception) {
                // Keep default lists
            }
        }
    }

    LaunchedEffect(regState) {
        scope.launch {
            try {
                districtsList = ApiClient.getDistricts(regState)
                if (districtsList.isNotEmpty() && regDistrict !in districtsList) {
                    regDistrict = districtsList.first()
                }
            } catch (e: Exception) {
                // Keep default
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoilFieldPaper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ================================================================
            // SECTION A: AGRICULTURAL VISUAL IDENTITY (HERO SECTION)
            // Visual: Indian farmland landscape, morning sun, rich soil, crops
            // ================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                // Canvas: Indian Farmland Landscape under Golden Morning Sun
                FarmlandHeroLandscape(
                    modifier = Modifier.fillMaxSize()
                )

                // Top Floating Bar: Brand & Language Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small Brand Sprout Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SoilFieldPaper.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, SoilDeepEarth.copy(alpha = 0.2f)),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌱", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "FarmRoute",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoilDeepEarth
                            )
                        }
                    }

                    // Section C: Language Selector
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SoilFieldPaper.copy(alpha = 0.95f),
                        border = BorderStroke(1.2.dp, SoilCropGreen),
                        shadowElevation = 3.dp,
                        modifier = Modifier.clickable { isKannada = !isKannada }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌐 ", fontSize = 12.sp)
                            Text(
                                text = if (isKannada) "ಕನ್ನಡ (KN)" else "English (EN)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoilCropGreen
                            )
                        }
                    }
                }

                // Brand Emblem, Logo, Tagline Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Golden Sun & Sprout Circular Emblem
                    Surface(
                        shape = CircleShape,
                        color = SoilFieldPaper,
                        border = BorderStroke(2.dp, SoilHarvestGold),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🌾", fontSize = 28.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isKannada) "ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ" else "FarmRoute Saathi",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SoilDeepEarth,
                        letterSpacing = 0.5.sp
                    )

                    // Tagline: "From Soil to Market"
                    Text(
                        text = if (isKannada) "ಮಣ್ಣಿನಿಂದ ಮಾರುಕಟ್ಟೆಗೆ • FROM SOIL TO MARKET" else "FROM SOIL TO MARKET",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SoilCropGreen,
                        letterSpacing = 1.2.sp
                    )

                    Text(
                        text = if (isKannada)
                            "ಫಲವತ್ತಾದ ಮಣ್ಣಿನಿಂದ ಪಾರದರ್ಶಕ ಮಾರುಕಟ್ಟೆಗಳಿಗೆ ಭಾರತೀಯ ರೈತರ ಪ್ರಯಾಣ"
                        else
                            "Connecting Indian farmers from fertile soil to transparent markets",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SoilDeepEarth.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ================================================================
            // SECTION B: WELCOME AND LOGIN
            // "Welcome to FarmRoute"
            // "Your harvest. Your choices. Better returns."
            // ================================================================
            Crossfade(
                targetState = currentStep,
                animationSpec = tween(280),
                label = "AuthStepTransition"
            ) { step ->
                when (step) {
                    AuthStep.PHONE_ENTRY -> {
                        SoilPhoneEntryCard(
                            isKannada = isKannada,
                            phoneNumber = phoneNumber,
                            onPhoneChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) phoneNumber = it },
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onDemoSelect = { demoPhone ->
                                phoneNumber = demoPhone
                                errorMessage = null
                            },
                            onContinue = {
                                if (phoneNumber.length != 10) {
                                    errorMessage = if (isKannada)
                                        "ದಯವಿಟ್ಟು ಮಾನ್ಯವಾದ 10-ಅಂಕಿಯ ಮೊಬೈಲ್ ಸಂಖ್ಯೆಯನ್ನು ನಮೂದಿಸಿ"
                                    else
                                        "Please enter a valid 10-digit mobile number"
                                    return@SoilPhoneEntryCard
                                }
                                focusManager.clearFocus()
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val checkRes = ApiClient.checkAccount(phoneNumber)
                                        isLoading = false
                                        if (checkRes.contains("\"exists\":true")) {
                                            detectedFarmerName = if (checkRes.contains("\"name\":\"")) {
                                                checkRes.substringAfter("\"name\":\"").substringBefore("\"")
                                            } else {
                                                "Manjunatha Gowda"
                                            }
                                            detectedDistrict = if (checkRes.contains("\"district\":\"")) {
                                                checkRes.substringAfter("\"district\":\"").substringBefore("\"")
                                            } else {
                                                "Kolar"
                                            }
                                            detectedState = if (checkRes.contains("\"state\":\"")) {
                                                checkRes.substringAfter("\"state\":\"").substringBefore("\"")
                                            } else {
                                                "Karnataka"
                                            }
                                            currentStep = AuthStep.MPIN_ENTRY
                                        } else {
                                            regName = ""
                                            regState = "Karnataka"
                                            regDistrict = "Kolar"
                                            currentStep = AuthStep.ONBOARDING
                                        }
                                    } catch (e: Exception) {
                                        isLoading = false
                                        errorMessage = "Error checking account: ${e.localizedMessage}"
                                    }
                                }
                            }
                        )
                    }

                    AuthStep.MPIN_ENTRY -> {
                        SoilMpinEntryCard(
                            isKannada = isKannada,
                            phoneNumber = phoneNumber,
                            farmerName = detectedFarmerName,
                            mpinInput = mpinInput,
                            onMpinDigit = { digit ->
                                if (mpinInput.length < 4) {
                                    mpinInput += digit
                                    errorMessage = null
                                }
                            },
                            onMpinBackspace = {
                                if (mpinInput.isNotEmpty()) {
                                    mpinInput = mpinInput.dropLast(1)
                                    errorMessage = null
                                }
                            },
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onChangePhone = {
                                currentStep = AuthStep.PHONE_ENTRY
                                mpinInput = ""
                                errorMessage = null
                            },
                            onLogin = {
                                if (mpinInput.length != 4) {
                                    errorMessage = if (isKannada)
                                        "ದಯವಿಟ್ಟು 4-ಅಂಕಿಯ MPIN ನಮೂದಿಸಿ"
                                    else
                                        "Please enter your 4-digit MPIN"
                                    return@SoilMpinEntryCard
                                }
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val loginRes = ApiClient.login(phoneNumber, mpinInput)
                                    isLoading = false
                                    if (loginRes.contains("\"status\":\"authenticated\"") || loginRes.contains("\"token\"")) {
                                        UserSession.login(
                                            FarmerProfile(
                                                phone = phoneNumber,
                                                name = detectedFarmerName.ifBlank { "Manjunatha Gowda" },
                                                state = detectedState,
                                                district = detectedDistrict,
                                                pincode = "563101",
                                                token = "token-$phoneNumber"
                                            )
                                        )
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = if (isKannada)
                                            "ತಪ್ಪಾದ MPIN. (ಮಾದರಿ: 1234 ಬಳಸಿ)"
                                        else
                                            "Invalid MPIN. (Demo hint: Use 1234)"
                                    }
                                }
                            }
                        )
                    }

                    AuthStep.ONBOARDING -> {
                        SoilOnboardingCard(
                            isKannada = isKannada,
                            phoneNumber = phoneNumber,
                            name = regName,
                            onNameChange = { regName = it },
                            state = regState,
                            onStateChange = { regState = it },
                            district = regDistrict,
                            onDistrictChange = { regDistrict = it },
                            pincode = regPincode,
                            onPincodeChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) regPincode = it },
                            mpin = regMpin,
                            onMpinChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) regMpin = it },
                            confirmMpin = regConfirmMpin,
                            onConfirmMpinChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) regConfirmMpin = it },
                            statesList = statesList,
                            districtsList = districtsList,
                            stateExpanded = stateDropdownExpanded,
                            onStateExpandedChange = { stateDropdownExpanded = it },
                            districtExpanded = districtDropdownExpanded,
                            onDistrictExpandedChange = { districtDropdownExpanded = it },
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onBackToPhone = {
                                currentStep = AuthStep.PHONE_ENTRY
                                errorMessage = null
                            },
                            onRegister = {
                                if (regName.isBlank()) {
                                    errorMessage = if (isKannada) "ದಯವಿಟ್ಟು ನಿಮ್ಮ ಹೆಸರನ್ನು ನಮೂದಿಸಿ" else "Please enter your full name"
                                    return@SoilOnboardingCard
                                }
                                if (regPincode.length != 6) {
                                    errorMessage = if (isKannada) "ದಯವಿಟ್ಟು ಮಾನ್ಯವಾದ 6-ಅಂಕಿಯ ಪಿನ್‌ಕೋಡ್ ನಮೂದಿಸಿ" else "Please enter a 6-digit pincode"
                                    return@SoilOnboardingCard
                                }
                                if (regMpin.length != 4) {
                                    errorMessage = if (isKannada) "ದಯವಿಟ್ಟು 4-ಅಂಕಿಯ ಹೊಸ MPIN ನಮೂದಿಸಿ" else "Please set a 4-digit MPIN"
                                    return@SoilOnboardingCard
                                }
                                if (regMpin != regConfirmMpin) {
                                    errorMessage = if (isKannada) "MPIN ಹೊಂದಿಕೆಯಾಗುತ್ತಿಲ್ಲ" else "MPIN confirmation does not match"
                                    return@SoilOnboardingCard
                                }
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    ApiClient.register(
                                        phone = phoneNumber,
                                        mpin = regMpin,
                                        name = regName,
                                        state = regState,
                                        district = regDistrict,
                                        pincode = regPincode
                                    )
                                    isLoading = false
                                    UserSession.login(
                                        FarmerProfile(
                                            phone = phoneNumber,
                                            name = regName,
                                            state = regState,
                                            district = regDistrict,
                                            pincode = regPincode,
                                            token = "token-$phoneNumber"
                                        )
                                    )
                                    onLoginSuccess()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ================================================================
            // SECTION E: TRUST AND AGRICULTURAL IDENTITY
            // "Built for farmers. Connected to markets."
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoilCropGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, SoilCropGreen.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌾", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isKannada)
                                "ರೈತರಿಗಾಗಿ ನಿರ್ಮಿಸಲಾಗಿದೆ • ಮಾರುಕಟ್ಟೆಗಳೊಂದಿಗೆ ಸಂಪರ್ಕಿಸಲಾಗಿದೆ"
                            else
                                "Built for farmers. Connected to markets.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SoilDeepEarth
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isKannada)
                        "ಕೃಷಿ ತಂತ್ರಜ್ಞಾನ ದೃಢೀಕರಣ • ರಾಷ್ಟ್ರೀಯ ತೋಟಗಾರಿಕಾ ಮಂಡಳಿ & AGMARKNET ಬೆಂಬಲಿತ"
                    else
                        "Authentic Indian AgriTech • Backed by NHB & AGMARKNET market intelligence",
                    fontSize = 10.sp,
                    color = SoilDeepEarth.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Custom Compose Canvas painting an Indian Farmland Landscape:
 * - Natural Sky (#A9C5C9) with gentle morning clouds
 * - Golden morning sun (#D7A747) rising over distant hills
 * - Rolling green farmland terraces (#91A96D, #486B43)
 * - Rich fertile soil furrows (#493A2D) at the base
 */
@Composable
private fun FarmlandHeroLandscape(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // 1. Natural Sky Gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    SoilNaturalSky,
                    SoilNaturalSky.copy(alpha = 0.6f),
                    SoilFieldPaper
                ),
                startY = 0f,
                endY = height * 0.75f
            ),
            size = Size(width, height)
        )

        // 2. Golden Morning Sun (#D7A747)
        val sunCenter = Offset(width * 0.75f, height * 0.32f)
        drawCircle(
            color = SoilHarvestGold.copy(alpha = 0.35f),
            radius = 52.dp.toPx(),
            center = sunCenter
        )
        drawCircle(
            color = SoilHarvestGold.copy(alpha = 0.85f),
            radius = 34.dp.toPx(),
            center = sunCenter
        )

        // 3. Distant Mountain/Hill Ridge (Fresh Leaf tint)
        val hillPath1 = Path().apply {
            moveTo(0f, height * 0.50f)
            cubicTo(
                width * 0.25f, height * 0.42f,
                width * 0.50f, height * 0.54f,
                width, height * 0.44f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = hillPath1,
            color = SoilFreshLeaf.copy(alpha = 0.5f)
        )

        // 4. Middle Ground: Rolling Crop Terraces (#486B43)
        val hillPath2 = Path().apply {
            moveTo(0f, height * 0.60f)
            cubicTo(
                width * 0.35f, height * 0.54f,
                width * 0.70f, height * 0.68f,
                width, height * 0.58f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = hillPath2,
            color = SoilCropGreen.copy(alpha = 0.75f)
        )

        // 5. Foreground: Fertile Soil Field Furrows (#493A2D)
        val soilPath = Path().apply {
            moveTo(0f, height * 0.74f)
            cubicTo(
                width * 0.30f, height * 0.70f,
                width * 0.65f, height * 0.76f,
                width, height * 0.72f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = soilPath,
            color = SoilDeepEarth
        )

        // 6. Natural Agricultural Furrow Lines in fertile soil
        val furrowColor = SoilHarvestGold.copy(alpha = 0.25f)
        for (i in 1..4) {
            val yOffset = height * (0.76f + i * 0.05f)
            drawLine(
                color = furrowColor,
                start = Offset(0f, yOffset),
                end = Offset(width, yOffset - 6.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Soft bottom gradient blend into Field Paper
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, SoilFieldPaper),
                startY = height * 0.85f,
                endY = height
            ),
            size = Size(width, height)
        )
    }
}

// ============================================================================
// STEP 1: PHONE ENTRY CARD
// ============================================================================
@Composable
private fun SoilPhoneEntryCard(
    isKannada: Boolean,
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onDemoSelect: (String) -> Unit,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            // Welcome Header
            Text(
                text = if (isKannada) "ಫಾರ್ಮ್‌ರೂಟ್‌ಗೆ ಸುಸ್ವಾಗತ" else "Welcome to FarmRoute",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SoilDeepEarth
            )

            Text(
                text = if (isKannada)
                    "ನಿಮ್ಮ ಕೊಯ್ಲು • ನಿಮ್ಮ ಆಯ್ಕೆ • ಉತ್ತಮ ಆದಾಯ"
                else
                    "Your harvest. Your choices. Better returns.",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SoilCropGreen,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            Text(
                text = if (isKannada)
                    "ಮುಂದುವರಿಯಲು ನಿಮ್ಮ 10-ಅಂಕಿಯ ಮೊಬೈಲ್ ಸಂಖ್ಯೆಯನ್ನು ನಮೂದಿಸಿ"
                else
                    "Enter your 10-digit mobile number to access your farmland hub",
                fontSize = 12.sp,
                color = SoilDeepEarth.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 18.dp)
            )

            // Mobile Number Input Field
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isKannada) "ರೈತರ ಮೊಬೈಲ್ ಸಂಖ್ಯೆ" else "Farmer Mobile Number") },
                prefix = {
                    Text(
                        text = "🇮🇳 +91 ",
                        fontWeight = FontWeight.Bold,
                        color = SoilDeepEarth
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onContinue() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SoilCropGreen,
                    focusedLabelColor = SoilCropGreen,
                    unfocusedBorderColor = SoilDeepEarth.copy(alpha = 0.25f),
                    cursorColor = SoilCropGreen
                )
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFEBEE),
                    border = BorderStroke(1.dp, Color(0xFFEF5350))
                ) {
                    Text(
                        text = "⚠️ $errorMessage",
                        color = Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION D: PRIMARY ACTION BUTTON (Crop Green #486B43)
            Button(
                onClick = onContinue,
                enabled = !isLoading && phoneNumber.length == 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoilCropGreen,
                    disabledContainerColor = SoilCropGreen.copy(alpha = 0.45f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isKannada) "ಮುಂದುವರಿಯಿರಿ" else "Continue to FarmHub",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("➔", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Quick Demo Selectors
            HorizontalDivider(color = SoilDeepEarth.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isKannada) "ಪರಿಶೀಲನೆಗಾಗಿ ಡೆಮೊ ಸಂಖ್ಯೆಗಳು:" else "Quick Tap Demo Farmers:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SoilDeepEarth.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoilDemoChip(
                    label = "9845012345\n(Manjunatha)",
                    onClick = { onDemoSelect("9845012345") },
                    modifier = Modifier.weight(1f)
                )
                SoilDemoChip(
                    label = "9845012346\n(Venkatamma)",
                    onClick = { onDemoSelect("9845012346") },
                    modifier = Modifier.weight(1f)
                )
                SoilDemoChip(
                    label = "9900011222\n(New Farmer)",
                    onClick = { onDemoSelect("9900011222") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SoilDemoChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SoilFreshLeaf.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SoilCropGreen.copy(alpha = 0.35f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            color = SoilDeepEarth,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}

// ============================================================================
// STEP 2: MPIN ENTRY CARD
// ============================================================================
@Composable
private fun SoilMpinEntryCard(
    isKannada: Boolean,
    phoneNumber: String,
    farmerName: String,
    mpinInput: String,
    onMpinDigit: (String) -> Unit,
    onMpinBackspace: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onChangePhone: () -> Unit,
    onLogin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isKannada) "ಸ್ವಾಗತ, $farmerName" else "Welcome back, $farmerName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SoilDeepEarth,
                textAlign = TextAlign.Center
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            ) {
                Text(
                    text = "🇮🇳 +91 $phoneNumber",
                    fontSize = 13.sp,
                    color = SoilDeepEarth.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKannada) "(ಬದಲಾಯಿಸಿ)" else "(Change)",
                    fontSize = 12.sp,
                    color = SoilCropGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onChangePhone() }
                )
            }

            Text(
                text = if (isKannada) "ನಿಮ್ಮ 4-ಅಂಕಿಯ MPIN ನಮೂದಿಸಿ" else "Enter your 4-digit security MPIN",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = SoilDeepEarth
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Bullet MPIN Display
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isFilled = index < mpinInput.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) SoilCropGreen else SoilFieldPaper)
                            .border(
                                1.5.dp,
                                if (isFilled) SoilHarvestGold else SoilDeepEarth.copy(alpha = 0.25f),
                                CircleShape
                            )
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFEBEE),
                    border = BorderStroke(1.dp, Color(0xFFEF5350))
                ) {
                    Text(
                        text = "⚠️ $errorMessage",
                        color = Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Keypad
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                ).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(50.dp))
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clickable {
                                            if (key == "⌫") onMpinBackspace() else onMpinDigit(key)
                                        },
                                    shape = CircleShape,
                                    color = SoilFieldPaper,
                                    border = BorderStroke(1.dp, SoilDeepEarth.copy(alpha = 0.15f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = key,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SoilDeepEarth
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION D: PRIMARY ACTION BUTTON (Crop Green #486B43)
            Button(
                onClick = onLogin,
                enabled = !isLoading && mpinInput.length == 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoilCropGreen,
                    disabledContainerColor = SoilCropGreen.copy(alpha = 0.45f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isKannada) "ದೃಢೀಕರಿಸಿ ಮತ್ತು ಲಾಗಿನ್ ಆಗಿ" else "Verify & Enter FarmHub",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🌾", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isKannada) "ಡೆಮೊ ಸುಳಿವು: MPIN '1234' ಬಳಸಿ" else "Demo Hint: Use MPIN '1234'",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = SoilDeepEarth.copy(alpha = 0.6f)
            )
        }
    }
}

// ============================================================================
// STEP 3: ONBOARDING CARD
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoilOnboardingCard(
    isKannada: Boolean,
    phoneNumber: String,
    name: String,
    onNameChange: (String) -> Unit,
    state: String,
    onStateChange: (String) -> Unit,
    district: String,
    onDistrictChange: (String) -> Unit,
    pincode: String,
    onPincodeChange: (String) -> Unit,
    mpin: String,
    onMpinChange: (String) -> Unit,
    confirmMpin: String,
    onConfirmMpinChange: (String) -> Unit,
    statesList: List<String>,
    districtsList: List<String>,
    stateExpanded: Boolean,
    onStateExpandedChange: (Boolean) -> Unit,
    districtExpanded: Boolean,
    onDistrictExpandedChange: (Boolean) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onBackToPhone: () -> Unit,
    onRegister: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Text(
                text = if (isKannada) "ಹೊಸ ಕಿಸಾನ್ ನೋಂದಣಿ" else "Register Farmland Account",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = SoilDeepEarth
            )

            Text(
                text = if (isKannada)
                    "ಮೊಬೈಲ್: +91 $phoneNumber (ಹೊಸ ರೈತ)"
                else
                    "Mobile: +91 $phoneNumber (New Farmland Account)",
                fontSize = 12.sp,
                color = SoilCropGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            // Full Name
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isKannada) "ರೈತರ ಪೂರ್ಣ ಹೆಸರು" else "Farmer Full Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SoilCropGreen,
                    focusedLabelColor = SoilCropGreen
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // State Dropdown
            ExposedDropdownMenuBox(
                expanded = stateExpanded,
                onExpandedChange = onStateExpandedChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = state,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isKannada) "ರಾಜ್ಯ (State - AGMARKNET)" else "State (AGMARKNET)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = stateExpanded,
                    onDismissRequest = { onStateExpandedChange(false) }
                ) {
                    statesList.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            onClick = {
                                onStateChange(s)
                                onStateExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // District Dropdown
            ExposedDropdownMenuBox(
                expanded = districtExpanded,
                onExpandedChange = onDistrictExpandedChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = district,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isKannada) "ಜಿಲ್ಲೆ (District)" else "District") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = districtExpanded,
                    onDismissRequest = { onDistrictExpandedChange(false) }
                ) {
                    districtsList.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d) },
                            onClick = {
                                onDistrictChange(d)
                                onDistrictExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pincode
            OutlinedTextField(
                value = pincode,
                onValueChange = onPincodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isKannada) "ಪಿನ್‌ಕೋಡ್ (6 ಅಂಕಿ)" else "Pincode (6-digit)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Set MPIN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = mpin,
                    onValueChange = onMpinChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(if (isKannada) "ಹೊಸ MPIN" else "Set MPIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = confirmMpin,
                    onValueChange = onConfirmMpinChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(if (isKannada) "ದೃಢೀಕರಿಸಿ" else "Confirm") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFEBEE),
                    border = BorderStroke(1.dp, Color(0xFFEF5350))
                ) {
                    Text(
                        text = "⚠️ $errorMessage",
                        color = Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION D: PRIMARY ACTION BUTTON (Crop Green #486B43)
            Button(
                onClick = onRegister,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoilCropGreen)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isKannada) "ನೋಂದಣಿ ಪೂರ್ಣಗೊಳಿಸಿ ➔" else "Complete Registration & Enter ➔",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onBackToPhone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isKannada) "← ಮೊಬೈಲ್ ಸಂಖ್ಯೆ ಬದಲಾಯಿಸಿ" else "← Change Mobile Number",
                    color = SoilDeepEarth,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
