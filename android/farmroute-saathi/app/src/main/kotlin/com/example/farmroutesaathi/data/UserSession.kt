package com.example.farmroutesaathi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class FarmerProfile(
    val phone: String = "9845012345",
    val name: String = "Manjunatha Gowda",
    val state: String = "Karnataka",
    val district: String = "Kolar",
    val pincode: String = "563101",
    val token: String = "sim-token-001"
)

data class HarvestDeclaration(
    val harvestId: String = "FR-KLR-20261113-0001",
    val crop: String = "Tomato",
    val harvestDate: String = "2026-11-13",
    val harvestTime: String = "07:00 AM",
    val expectedDateTime: String = "2026-11-14 09:00 AM",
    val sellImmediately: Boolean = true,
    val quantityKg: Double = 200.0,
    val qualityGrade: String = "Grade A",
    val hasStorageAccess: Boolean = true,
    val storageRequirementNeeded: Boolean = false,
    val farmerLatitude: Double = 13.133,
    val farmerLongitude: Double = 78.133,
    val locationSource: String = "GPS (Verified)"
)

data class NotificationItem(
    val id: String,
    val titleEn: String,
    val titleKn: String,
    val messageEn: String,
    val messageKn: String,
    val category: String, // "WEATHER", "STORAGE", "SCHEME", "MANDI"
    val timestamp: String,
    val isUrgent: Boolean = false,
    var isRead: Boolean = false,
    val actionDestination: String? = null
)

data class SupportedLanguage(
    val code: String,        // "kn", "en", "hi", "te", "ta", "mr"
    val sarvamCode: String,  // "kn-IN", "en-IN", "hi-IN", "te-IN", "ta-IN", "mr-IN"
    val nativeName: String,  // "ಕನ್ನಡ", "English", "हिन्दी", "తెలుగు", "தமிழ்", "मराठी"
    val englishName: String  // "Kannada", "English", "Hindi", "Telugu", "Tamil", "Marathi"
)

object UserSession {
    var currentFarmer by mutableStateOf<FarmerProfile?>(
        FarmerProfile() // Pre-seeded with demo farmer, can be logged out
    )

    var activeHarvest by mutableStateOf<HarvestDeclaration?>(
        HarvestDeclaration() // Pre-seeded default harvest lot
    )

    val activeHarvestId: String
        get() = activeHarvest?.harvestId ?: "FR-KLR-20261113-0001"

    val isLoggedIn: Boolean
        get() = currentFarmer != null

    val supportedLanguages = listOf(
        SupportedLanguage("kn", "kn-IN", "ಕನ್ನಡ", "Kannada"),
        SupportedLanguage("en", "en-IN", "English", "English"),
        SupportedLanguage("hi", "hi-IN", "हिन्दी", "Hindi"),
        SupportedLanguage("te", "te-IN", "తెలుగు", "Telugu"),
        SupportedLanguage("ta", "ta-IN", "தமிழ்", "Tamil"),
        SupportedLanguage("mr", "mr-IN", "मराठी", "Marathi"),
        SupportedLanguage("bn", "bn-IN", "বাংলা", "Bengali"),
        SupportedLanguage("gu", "gu-IN", "ગુજરાતી", "Gujarati"),
        SupportedLanguage("ml", "ml-IN", "മലയാളം", "Malayalam"),
        SupportedLanguage("od", "od-IN", "ଓଡ଼ିଆ", "Odia"),
        SupportedLanguage("pa", "pa-IN", "ਪੰਜਾਬੀ", "Punjabi")
    )

    var selectedLanguageCode by mutableStateOf("kn")

    var isDarkTheme by mutableStateOf(false)

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
    }

    val selectedLanguage: SupportedLanguage
        get() = supportedLanguages.find { it.code == selectedLanguageCode } ?: supportedLanguages.first()

    fun setLanguage(lang: SupportedLanguage) {
        selectedLanguageCode = lang.code
    }

    var isKannadaPreferred: Boolean
        get() = selectedLanguageCode == "kn"
        set(value) {
            selectedLanguageCode = if (value) "kn" else "en"
        }

    var notifications by mutableStateOf(
        listOf(
            NotificationItem(
                id = "NOTIF-001",
                titleEn = "⛈️ IMD Weather Alert: Kolar Corridor",
                titleKn = "⛈️ ಐಎಂಡಿ ಹವಾಮಾನ ಎಚ್ಚರಿಕೆ: ಕೋಲಾರ ಕಾರಿಡಾರ್",
                messageEn = "Heavy rainfall predicted in 36-48 hours. Consider moving ripe tomatoes to nearby verified cold storage.",
                messageKn = "ಮುಂದಿನ 36-48 ಗಂಟೆಗಳಲ್ಲಿ ಭಾರಿ ಮಳೆ ನಿರೀಕ್ಷಿಸಲಾಗಿದೆ. ಹಣ್ಣಾದ ಟೊಮೆಟೊಗಳನ್ನು ಸಮೀಪದ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್‌ಗೆ ಸಾಗಿಸಿ.",
                category = "WEATHER",
                timestamp = "Just now",
                isUrgent = true,
                actionDestination = "ColdStorageDiscovery"
            ),
            NotificationItem(
                id = "NOTIF-002",
                titleEn = "❄️ Cold Storage Space Verified",
                titleKn = "❄️ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್ ಜಾಗ ಪರಿಶೀಲಿಸಲಾಗಿದೆ",
                messageEn = "Mulbagal Agro Cold Storage has 120 MT available capacity for solanaceous vegetables at ₹1.80/qtl/day.",
                messageKn = "ಮುಳುಬಾಗಿಲು ಅಗ್ರೋ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್‌ನಲ್ಲಿ 120 ಮೆಟ್ರಿಕ್ ಟನ್ ಲಭ್ಯವಿದೆ (ದಿನಕ್ಕೆ ₹1.80/ಕ್ವಿಂಟಾಲ್).",
                category = "STORAGE",
                timestamp = "2 hours ago",
                isUrgent = false,
                actionDestination = "ColdStorageDiscovery"
            ),
            NotificationItem(
                id = "NOTIF-003",
                titleEn = "📜 PM-KUSUM Solar Pump Subsidy",
                titleKn = "📜 ಪಿಎಂ-ಕುಸುಮ್ ಸೌರ ಪಂಪ್ ಸಬ್ಸಿಡಿ",
                messageEn = "State subsidy application window open until Nov 30. Get up to 60% capital grant for 5HP solar water pumps.",
                messageKn = "ರಾಜ್ಯ ಸಬ್ಸಿಡಿ ಅರ್ಜಿ ನವೆಂಬರ್ 30 ರವರೆಗೆ ಲಭ್ಯವಿದೆ. 5 ಹೆಚ್‌ಪಿ ಸೌರ ಪಂಪ್‌ಗಳಿಗೆ 60% ವರೆಗೆ ಅನುದಾನ ಪಡೆಯಿರಿ.",
                category = "SCHEME",
                timestamp = "1 day ago",
                isUrgent = false,
                actionDestination = "GovernmentBenefits"
            ),
            NotificationItem(
                id = "NOTIF-004",
                titleEn = "📈 Mandi Price Update: Tomato",
                titleKn = "📈 ಮಂಡಿ ದರ ನವೀಕರಣ: ಟೊಮೆಟೊ",
                messageEn = "Bangalore APMC modal rate increased to ₹2,850/qtl. Check net returns after deducting transport charges.",
                messageKn = "ಬೆಂಗಳೂರು ಎಪಿಎಂಸಿಯಲ್ಲಿ ಟೊಮೆಟೊ ಮಾದರಿ ದರ ₹2,850 ಕ್ವಿಂಟಾಲ್‌ಗೆ ಏರಿಕೆಯಾಗಿದೆ. ನಿವ್ವಳ ಲಾಭ ಹೋಲಿಸಿ.",
                category = "MANDI",
                timestamp = "2 days ago",
                isUrgent = false,
                actionDestination = "MandiAnalysis"
            )
        )
    )

    val unreadNotificationCount: Int
        get() = notifications.count { !it.isRead }

    fun markNotificationAsRead(id: String) {
        notifications = notifications.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    fun markAllNotificationsAsRead() {
        notifications = notifications.map { it.copy(isRead = true) }
    }

    fun login(farmer: FarmerProfile) {
        currentFarmer = farmer
    }

    fun setHarvest(declaration: HarvestDeclaration) {
        activeHarvest = declaration
    }

    fun logout() {
        currentFarmer = null
        activeHarvest = null
    }
}
