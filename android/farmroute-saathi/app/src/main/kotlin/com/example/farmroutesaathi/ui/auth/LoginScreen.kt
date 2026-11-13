package com.example.farmroutesaathi.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.farmroutesaathi.ui.theme.ForestGreenDeep
import com.example.farmroutesaathi.ui.theme.FreshLeafGreen
import com.example.farmroutesaathi.ui.theme.FreshLimeAccent
import com.example.farmroutesaathi.ui.theme.HarvestGold
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

    var statesList by remember { mutableStateOf(listOf("Karnataka", "Andhra Pradesh", "Tamil Nadu")) }
    var districtsList by remember { mutableStateOf(listOf("Kolar", "Chikkaballapura", "Bangalore Rural")) }
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Card with Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ForestGreenDeep, FreshLeafGreen)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand Icon + Title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(HarvestGold),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🌱", fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isKannada) "ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ" else "FarmRoute Saathi",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isKannada) "ಕಿಸಾನ್ ದೃಢೀಕರಣ" else "Kisan Authentication",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        // Language Toggle Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = HarvestGold,
                            shadowElevation = 3.dp,
                            modifier = Modifier.clickable { isKannada = !isKannada }
                        ) {
                            Text(
                                text = if (isKannada) "🌐 ಕನ್ನಡ" else "🌐 English",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDeep,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Simulated Prototype Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isKannada)
                                "🛡️ ಸುರಕ್ಷಿತ ಮೊಬೈಲ್ + MPIN ಲಾಗಿನ್ [ಮಾದರಿ]"
                            else
                                "🛡️ SECURE MOBILE + MPIN AUTHENTICATION [PROTOTYPE]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Content Area based on Step
            Crossfade(
                targetState = currentStep,
                animationSpec = tween(300),
                label = "AuthStepTransition"
            ) { step ->
                when (step) {
                    AuthStep.PHONE_ENTRY -> {
                        PhoneEntryCard(
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
                                    errorMessage = if (isKannada) "ದಯವಿಟ್ಟು ಮಾನ್ಯವಾದ 10-ಅಂಕಿಯ ಮೊಬೈಲ್ ಸಂಖ್ಯೆಯನ್ನು ನಮೂದಿಸಿ" else "Please enter a valid 10-digit mobile number"
                                    return@PhoneEntryCard
                                }
                                focusManager.clearFocus()
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val checkRes = ApiClient.checkAccount(phoneNumber)
                                        isLoading = false
                                        if (checkRes.contains("\"exists\":true")) {
                                            // Extract name if available
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
                                            // New account -> onboarding
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
                        MpinEntryCard(
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
                                    errorMessage = if (isKannada) "ದಯವಿಟ್ಟು 4-ಅಂಕಿಯ MPIN ನಮೂದಿಸಿ" else "Please enter your 4-digit MPIN"
                                    return@MpinEntryCard
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
                                        errorMessage = if (isKannada) "ತಪ್ಪಾದ MPIN. (ಮಾದರಿ: 1234 ಬಳಸಿ)" else "Invalid MPIN. (Demo hint: Use 1234)"
                                    }
                                }
                            }
                        )
                    }

                    AuthStep.ONBOARDING -> {
                        OnboardingCard(
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
                                    return@OnboardingCard
                                }
                                if (regPincode.length != 6) {
                                    errorMessage = if (isKannada) "ದಯವಿಟ್ಟು ಮಾನ್ಯವಾದ 6-ಅಂಕಿಯ ಪಿನ್‌ಕೋಡ್ ನಮೂದಿಸಿ" else "Please enter a 6-digit pincode"
                                    return@OnboardingCard
                                }
                                if (regMpin.length != 4) {
                                    errorMessage = if (isKannada) "ದಯವಿಟ್ಟು 4-ಅಂಕಿಯ ಹೊಸ MPIN ನಮೂದಿಸಿ" else "Please set a 4-digit MPIN"
                                    return@OnboardingCard
                                }
                                if (regMpin != regConfirmMpin) {
                                    errorMessage = if (isKannada) "MPIN ಹೊಂದಿಕೆಯಾಗುತ್ತಿಲ್ಲ" else "MPIN confirmation does not match"
                                    return@OnboardingCard
                                }
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val regRes = ApiClient.register(
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PhoneEntryCard(
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
            .padding(horizontal = 20.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = if (isKannada) "ಕಿಸಾನ್ ಲಾಗಿನ್" else "Farmer Login",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isKannada)
                    "ನಿಮ್ಮ 10-ಅಂಕಿಯ ನೋಂದಾಯಿತ ಮೊಬೈಲ್ ಸಂಖ್ಯೆಯನ್ನು ನಮೂದಿಸಿ"
                else
                    "Enter your 10-digit registered mobile number",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Mobile Input Field
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isKannada) "ಮೊಬೈಲ್ ಸಂಖ್ಯೆ" else "Mobile Number") },
                prefix = {
                    Text(
                        text = "🇮🇳 +91 ",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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
                    focusedBorderColor = ForestGreenDeep,
                    focusedLabelColor = ForestGreenDeep
                )
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "⚠️ $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Continue Button
            Button(
                onClick = onContinue,
                enabled = !isLoading && phoneNumber.length == 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (isKannada) "ಮುಂದುವರಿಯಿರಿ ➔" else "Continue ➔",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Demo Selectors
            Text(
                text = if (isKannada) "ತ್ವರಿತ ಡೆಮೊ ಸಂಖ್ಯೆಗಳು:" else "Quick Demo Numbers:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DemoChip(
                    label = "9845012345\n(Manjunatha)",
                    onClick = { onDemoSelect("9845012345") },
                    modifier = Modifier.weight(1f)
                )
                DemoChip(
                    label = "9845012346\n(Venkatamma)",
                    onClick = { onDemoSelect("9845012346") },
                    modifier = Modifier.weight(1f)
                )
                DemoChip(
                    label = "9900011222\n(New Kisan)",
                    onClick = { onDemoSelect("9900011222") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DemoChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = FreshLeafGreen.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreshLeafGreen.copy(alpha = 0.3f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            color = ForestGreenDeep,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MpinEntryCard(
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
            .padding(horizontal = 20.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isKannada) "ಸ್ವಾಗತ, $farmerName" else "Welcome, $farmerName",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            ) {
                Text(
                    text = "🇮🇳 +91 $phoneNumber",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKannada) "(ಬದಲಾಯಿಸಿ)" else "(Change)",
                    fontSize = 12.sp,
                    color = HarvestGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onChangePhone() }
                )
            }

            Text(
                text = if (isKannada) "ನಿಮ್ಮ 4-ಅಂಕಿಯ MPIN ನಮೂದಿಸಿ" else "Enter your 4-digit MPIN",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4-Bullet MPIN Display
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isFilled = index < mpinInput.length
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) ForestGreenDeep else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.5.dp,
                                if (isFilled) HarvestGold else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "⚠️ $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Numeric Keypad
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Spacer(modifier = Modifier.size(54.dp))
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clickable {
                                            if (key == "⌫") onMpinBackspace() else onMpinDigit(key)
                                        },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = key,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onLogin,
                enabled = !isLoading && mpinInput.length == 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (isKannada) "ದೃಢೀಕರಿಸಿ ಮತ್ತು ಲಾಗಿನ್ ಆಗಿ" else "Verify & Login",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isKannada) "ಡೆಮೊ ಸುಳಿವು: MPIN '1234' ಬಳಸಿ" else "Demo Hint: Use MPIN '1234'",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingCard(
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
            .padding(horizontal = 20.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = if (isKannada) "ಹೊಸ ಕಿಸಾನ್ ನೋಂದಣಿ" else "New Kisan Registration",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isKannada)
                    "ಮೊಬೈಲ್: +91 $phoneNumber (ಖಾತೆ ಲಭ್ಯವಿಲ್ಲ)"
                else
                    "Mobile: +91 $phoneNumber (No account found)",
                fontSize = 12.sp,
                color = HarvestGold,
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
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // State Dropdown (AGMARKNET verified)
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

            Spacer(modifier = Modifier.height(12.dp))

            // District Dropdown (AGMARKNET verified)
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

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

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
                Text(
                    text = "⚠️ $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRegister,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenDeep)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (isKannada) "ನೋಂದಣಿ ಪೂರ್ಣಗೊಳಿಸಿ ➔" else "Complete Registration ➔",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onBackToPhone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isKannada) "← ಮೊಬೈಲ್ ಸಂಖ್ಯೆ ಬದಲಾಯಿಸಿ" else "← Change Mobile Number",
                    color = ForestGreenDeep,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
