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
import com.example.farmroutesaathi.data.AppLocalization
import com.example.farmroutesaathi.data.FarmerProfile
import com.example.farmroutesaathi.data.UserSession
import com.example.farmroutesaathi.ui.components.LanguageSelectionDialog
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
    // Reactive language subscription for instant re-composition across all 11 languages
    val currentLangCode = UserSession.selectedLanguageCode
    val selectedLang = UserSession.selectedLanguage

    var showLanguageDialog by remember { mutableStateOf(false) }
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
                        color = SoilFieldPaper.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, SoilDeepEarth.copy(alpha = 0.2f)),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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

                    // Section C: Dynamic Language Selector
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SoilFieldPaper.copy(alpha = 0.95f),
                        border = BorderStroke(1.2.dp, SoilCropGreen),
                        shadowElevation = 3.dp,
                        modifier = Modifier.clickable { showLanguageDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌐 ", fontSize = 12.sp)
                            Text(
                                text = "${selectedLang.nativeName} (${selectedLang.code.uppercase()})",
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
                        text = AppLocalization.t("app_name"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SoilDeepEarth,
                        letterSpacing = 0.5.sp
                    )

                    // Primary Slogan: "Your Land. Your Harvest. Your Better Tomorrow."
                    Text(
                        text = AppLocalization.t("tagline_long"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SoilCropGreen,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // Secondary Tagline: "FROM SOIL TO MARKET"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoilHarvestGold.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, SoilHarvestGold.copy(alpha = 0.35f)),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = AppLocalization.t("tagline"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HarvestGoldDark,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ================================================================
            // SECTION B: AUTHENTICATION CONTAINER (STEP-BASED FLOW)
            // Step 1: Phone Entry | Step 2: MPIN Entry | Step 3: Onboarding
            // ================================================================
            Crossfade(
                targetState = currentStep,
                animationSpec = tween(350),
                label = "auth_step_crossfade"
            ) { step ->
                when (step) {
                    AuthStep.PHONE_ENTRY -> {
                        SoilPhoneEntryCard(
                            phoneNumber = phoneNumber,
                            onPhoneChange = { input ->
                                if (input.length <= 10 && input.all { c -> c.isDigit() }) {
                                    phoneNumber = input
                                    errorMessage = null
                                }
                            },
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onContinue = {
                                if (phoneNumber.length != 10) {
                                    errorMessage = "Please enter a valid 10-digit mobile number"
                                    return@SoilPhoneEntryCard
                                }
                                focusManager.clearFocus()
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val checkRes = ApiClient.checkAccount(phoneNumber)
                                        isLoading = false
                                        val isRegistered = checkRes.contains("\"exists\":true")
                                        if (isRegistered) {
                                            detectedFarmerName = if (checkRes.contains("\"name\":\"")) {
                                                checkRes.substringAfter("\"name\":\"").substringBefore("\"")
                                            } else "Manjunatha Gowda"
                                            detectedState = if (checkRes.contains("\"state\":\"")) {
                                                checkRes.substringAfter("\"state\":\"").substringBefore("\"")
                                            } else "Karnataka"
                                            detectedDistrict = if (checkRes.contains("\"district\":\"")) {
                                                checkRes.substringAfter("\"district\":\"").substringBefore("\"")
                                            } else "Kolar"
                                            currentStep = AuthStep.MPIN_ENTRY
                                        } else {
                                            regName = ""
                                            regPincode = "563101"
                                            regMpin = ""
                                            regConfirmMpin = ""
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
                                    errorMessage = AppLocalization.t("enter_4_mpin_err")
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
                                        errorMessage = AppLocalization.t("incorrect_mpin_err")
                                    }
                                }
                            }
                        )
                    }

                    AuthStep.ONBOARDING -> {
                        SoilOnboardingCard(
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
                                    errorMessage = AppLocalization.t("enter_full_name_err")
                                    return@SoilOnboardingCard
                                }
                                if (regPincode.length != 6) {
                                    errorMessage = AppLocalization.t("enter_pincode_err")
                                    return@SoilOnboardingCard
                                }
                                if (regMpin.length != 4) {
                                    errorMessage = AppLocalization.t("set_new_mpin_err")
                                    return@SoilOnboardingCard
                                }
                                if (regMpin != regConfirmMpin) {
                                    errorMessage = AppLocalization.t("mpin_mismatch_err")
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
                            text = AppLocalization.t("built_for_farmers"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SoilDeepEarth
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = AppLocalization.t("trust_footer"),
                    fontSize = 10.sp,
                    color = SoilDeepEarth.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showLanguageDialog) {
            LanguageSelectionDialog(
                onDismiss = { showLanguageDialog = false },
                onLanguageSelected = { lang ->
                    UserSession.setLanguage(lang)
                    showLanguageDialog = false
                }
            )
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

        // 1. Natural Sky (#A9C5C9 to soft morning ivory)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(SoilNaturalSky, Color(0xFFD6E4E2), SoilFieldPaper),
                startY = 0f,
                endY = height * 0.55f
            ),
            size = Size(width, height * 0.55f)
        )

        // 2. Golden Morning Sun (#D7A747) rising over hills
        val sunCenter = Offset(width * 0.74f, height * 0.28f)
        val sunRadius = 40.dp.toPx()
        // Sun halo / glow
        drawCircle(
            color = SoilHarvestGold.copy(alpha = 0.22f),
            radius = sunRadius * 1.55f,
            center = sunCenter
        )
        // Sun disk
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFE082), SoilHarvestGold),
                center = sunCenter,
                radius = sunRadius
            ),
            radius = sunRadius,
            center = sunCenter
        )

        // 3. Distant Rolling Agricultural Terraces - Light Green (#91A96D)
        val terrace1 = Path().apply {
            moveTo(0f, height * 0.40f)
            cubicTo(
                width * 0.25f, height * 0.36f,
                width * 0.55f, height * 0.44f,
                width, height * 0.38f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(path = terrace1, color = SoilFreshLeaf.copy(alpha = 0.85f))

        // 4. Middle Crop Fields - Deep Crop Green (#486B43)
        val terrace2 = Path().apply {
            moveTo(0f, height * 0.47f)
            cubicTo(
                width * 0.35f, height * 0.52f,
                width * 0.70f, height * 0.45f,
                width, height * 0.50f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(path = terrace2, color = SoilCropGreen)

        // 5. Rich Fertile Soil Bed (#493A2D)
        val soilBed = Path().apply {
            moveTo(0f, height * 0.56f)
            cubicTo(
                width * 0.40f, height * 0.54f,
                width * 0.75f, height * 0.59f,
                width, height * 0.55f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = soilBed,
            brush = Brush.verticalGradient(
                colors = listOf(SoilDeepEarth, Color(0xFF382A20)),
                startY = height * 0.55f,
                endY = height
            )
        )

        // 6. Natural Agricultural Furrow Lines in fertile soil
        val furrowColor = SoilHarvestGold.copy(alpha = 0.25f)
        for (i in 1..4) {
            val yOffset = height * (0.64f + i * 0.05f)
            drawLine(
                color = furrowColor,
                start = Offset(0f, yOffset),
                end = Offset(width, yOffset - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Soft bottom gradient blend into Field Paper
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, SoilFieldPaper),
                startY = height * 0.82f,
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
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            // Welcome Header
            Text(
                text = AppLocalization.t("welcome_title"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SoilDeepEarth
            )

            Text(
                text = AppLocalization.t("welcome_sub"),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SoilCropGreen,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            Text(
                text = AppLocalization.t("enter_mobile_desc"),
                fontSize = 12.sp,
                color = SoilDeepEarth.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 18.dp)
            )

            // Mobile Number Input Field with EXPLICIT high-contrast colors
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(
                    color = Color(0xFF1B2A1E),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                label = {
                    Text(
                        text = AppLocalization.t("farmer_mobile_label"),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                prefix = {
                    Text(
                        text = "🇮🇳 +91 ",
                        fontWeight = FontWeight.Bold,
                        color = SoilDeepEarth,
                        fontSize = 16.sp
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
                    focusedTextColor = Color(0xFF1B2A1E),
                    unfocusedTextColor = Color(0xFF1B2A1E),
                    disabledTextColor = Color(0xFF1B2A1E),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedBorderColor = SoilCropGreen,
                    unfocusedBorderColor = SoilDeepEarth.copy(alpha = 0.35f),
                    disabledBorderColor = SoilDeepEarth.copy(alpha = 0.35f),
                    focusedLabelColor = SoilCropGreen,
                    unfocusedLabelColor = SoilDeepEarth.copy(alpha = 0.75f),
                    disabledLabelColor = SoilDeepEarth.copy(alpha = 0.75f),
                    cursorColor = SoilCropGreen,
                    focusedPrefixColor = SoilDeepEarth,
                    unfocusedPrefixColor = SoilDeepEarth,
                    disabledPrefixColor = SoilDeepEarth
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
                            text = AppLocalization.t("continue_farmhub"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("➔", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ============================================================================
// STEP 2: MPIN ENTRY CARD
// ============================================================================
@Composable
private fun SoilMpinEntryCard(
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
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = AppLocalization.t("welcome_back", "name" to farmerName),
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
                    color = SoilDeepEarth.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppLocalization.t("change_mobile"),
                    fontSize = 12.sp,
                    color = SoilCropGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onChangePhone() }
                )
            }

            Text(
                text = AppLocalization.t("enter_mpin"),
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
                            text = AppLocalization.t("verify_enter"),
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
                text = AppLocalization.t("secure_login_hint"),
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
    // Common textfield colors with EXPLICIT high-contrast dark text
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFF1B2A1E),
        unfocusedTextColor = Color(0xFF1B2A1E),
        disabledTextColor = Color(0xFF1B2A1E),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        disabledContainerColor = Color.White,
        focusedBorderColor = SoilCropGreen,
        unfocusedBorderColor = SoilDeepEarth.copy(alpha = 0.35f),
        disabledBorderColor = SoilDeepEarth.copy(alpha = 0.35f),
        focusedLabelColor = SoilCropGreen,
        unfocusedLabelColor = SoilDeepEarth.copy(alpha = 0.75f),
        disabledLabelColor = SoilDeepEarth.copy(alpha = 0.75f),
        cursorColor = SoilCropGreen
    )
    val fieldTextStyle = androidx.compose.ui.text.TextStyle(
        color = Color(0xFF1B2A1E),
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.2.dp, SoilDeepEarth.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Text(
                text = AppLocalization.t("register_title"),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = SoilDeepEarth
            )

            Text(
                text = AppLocalization.t("mobile_new_farmer", "phone" to phoneNumber),
                fontSize = 12.sp,
                color = SoilCropGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            // Full Name Input
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = fieldTextStyle,
                label = { Text(AppLocalization.t("farmer_name_label")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
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
                    textStyle = fieldTextStyle,
                    label = { Text(AppLocalization.t("state_label")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
                ExposedDropdownMenu(
                    expanded = stateExpanded,
                    onDismissRequest = { onStateExpandedChange(false) },
                    modifier = Modifier.background(Color.White)
                ) {
                    statesList.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s, color = Color(0xFF1B2A1E), fontWeight = FontWeight.Medium) },
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
                    textStyle = fieldTextStyle,
                    label = { Text(AppLocalization.t("district_label")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
                ExposedDropdownMenu(
                    expanded = districtExpanded,
                    onDismissRequest = { onDistrictExpandedChange(false) },
                    modifier = Modifier.background(Color.White)
                ) {
                    districtsList.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d, color = Color(0xFF1B2A1E), fontWeight = FontWeight.Medium) },
                            onClick = {
                                onDistrictChange(d)
                                onDistrictExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pincode Input
            OutlinedTextField(
                value = pincode,
                onValueChange = onPincodeChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = fieldTextStyle,
                label = { Text(AppLocalization.t("pincode_label")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
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
                    textStyle = fieldTextStyle,
                    label = { Text(AppLocalization.t("set_mpin_label")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )

                OutlinedTextField(
                    value = confirmMpin,
                    onValueChange = onConfirmMpinChange,
                    modifier = Modifier.weight(1f),
                    textStyle = fieldTextStyle,
                    label = { Text(AppLocalization.t("confirm_mpin_label")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
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
                            text = AppLocalization.t("complete_reg_btn") + " ➔",
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
                    text = AppLocalization.t("change_number_btn"),
                    color = SoilDeepEarth,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
