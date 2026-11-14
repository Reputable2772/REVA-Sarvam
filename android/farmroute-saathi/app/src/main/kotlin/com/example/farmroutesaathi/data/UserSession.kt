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
) {
    val coordinates: Pair<Double, Double>
        get() = UserSession.getDistrictCoordinates(district, state)
}

data class HarvestDeclaration(
    val harvestId: String = "FR-LOT-20261113-0001",
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
        get() = activeHarvest?.harvestId ?: "FR-LOT-20261113-0001"

    val farmerLocationText: String
        get() {
            val dist = currentFarmer?.district ?: "Local"
            val st = currentFarmer?.state ?: ""
            return if (st.isNotBlank()) "$dist, $st" else dist
        }

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

    fun buildInitialNotifications(farmer: FarmerProfile? = currentFarmer): List<NotificationItem> {
        val dist = farmer?.district ?: "Local"
        val cropName = activeHarvest?.crop ?: "Produce"
        return listOf(
            NotificationItem(
                id = "NOTIF-001",
                titleEn = "⛈️ IMD Weather Alert: $dist Region",
                titleKn = "⛈️ ಐಎಂಡಿ ಹವಾಮಾನ ಎಚ್ಚರಿಕೆ: $dist ಪ್ರದೇಶ",
                messageEn = "Heavy rainfall predicted in 36-48 hours. Consider moving ripe $cropName to nearby verified cold storage.",
                messageKn = "ಮುಂದಿನ 36-48 ಗಂಟೆಗಳಲ್ಲಿ ಭಾರಿ ಮಳೆ ನಿರೀಕ್ಷಿಸಲಾಗಿದೆ. ಹಣ್ಣಾದ ಬೆಳೆಗಳನ್ನು ಸಮೀಪದ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್‌ಗೆ ಸಾಗಿಸಿ.",
                category = "WEATHER",
                timestamp = "Just now",
                isUrgent = true,
                actionDestination = "ColdStorageDiscovery"
            ),
            NotificationItem(
                id = "NOTIF-002",
                titleEn = "❄️ Cold Storage Space Verified",
                titleKn = "❄️ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್ ಜಾಗ ಪರಿಶೀಲಿಸಲಾಗಿದೆ",
                messageEn = "$dist Regional Cold Storage has available capacity for solanaceous vegetables at ₹1.80/qtl/day.",
                messageKn = "$dist ಪ್ರಾದೇಶಿಕ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್‌ನಲ್ಲಿ ಸಾಮರ್ಥ್ಯ ಲಭ್ಯವಿದೆ (ದಿನಕ್ಕೆ ₹1.80/ಕ್ವಿಂಟಾಲ್).",
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
                titleEn = "📈 Mandi Price Update: $cropName",
                titleKn = "📈 ಮಂಡಿ ದರ ನವೀಕರಣ: $cropName",
                messageEn = "Live modal prices updated. Check net returns after deducting transport charges.",
                messageKn = "ನೇರ ಮಾರುಕಟ್ಟೆ ದರಗಳು ಲಭ್ಯವಿದೆ. ನಿವ್ವಳ ಲಾಭ ಹೋಲಿಸಿ ನೋಡಿ.",
                category = "MANDI",
                timestamp = "2 days ago",
                isUrgent = false,
                actionDestination = "MandiAnalysis"
            )
        )
    }

    var notifications by mutableStateOf(buildInitialNotifications())

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

    fun getDistrictCoordinates(district: String, state: String = ""): Pair<Double, Double> {
        val d = district.trim().lowercase()
        val s = state.trim().lowercase()
        return when {
            // Tamil Nadu
            d.contains("chengalpet") || d.contains("chengalpattu") -> Pair(12.6937, 79.9757)
            d.contains("kanchipuram") || d.contains("kancheepuram") -> Pair(12.8342, 79.7036)
            d.contains("chennai") || d.contains("koyambedu") -> Pair(13.0827, 80.2707)
            d.contains("thiruvallur") || d.contains("tiruvallur") -> Pair(13.1432, 79.9074)
            d.contains("vellore") -> Pair(12.9165, 79.1325)
            d.contains("ranipet") -> Pair(12.9298, 79.3330)
            d.contains("dharmapuri") -> Pair(12.1211, 78.1582)
            d.contains("krishnagiri") -> Pair(12.5186, 78.2137)
            d.contains("salem") -> Pair(11.6643, 78.1460)
            d.contains("coimbatore") -> Pair(11.0168, 76.9558)
            d.contains("madurai") -> Pair(9.9252, 78.1198)
            d.contains("tiruchirappalli") || d.contains("trichy") -> Pair(10.7905, 78.7047)
            // Karnataka
            d.contains("kolar") -> Pair(13.1378, 78.1294)
            d.contains("bengaluru") || d.contains("bangalore") -> Pair(12.9716, 77.5946)
            d.contains("chikkaballapur") -> Pair(13.4333, 77.7333)
            d.contains("mysuru") || d.contains("mysore") -> Pair(12.2958, 76.6394)
            d.contains("tumkur") || d.contains("tumakuru") -> Pair(13.3409, 77.1006)
            d.contains("mandya") -> Pair(12.5218, 76.8951)
            d.contains("hassan") -> Pair(13.0072, 76.1032)
            d.contains("belagavi") || d.contains("belgaum") -> Pair(15.8497, 74.4977)
            // Maharashtra
            d.contains("nashik") -> Pair(19.9975, 73.7898)
            d.contains("pune") -> Pair(18.5204, 73.8567)
            d.contains("mumbai") -> Pair(19.0760, 72.8777)
            d.contains("nagpur") -> Pair(21.1458, 79.0882)
            // State centroid fallbacks
            s.contains("tamil") -> Pair(11.1271, 78.6569)
            s.contains("maharashtra") -> Pair(19.7515, 75.7139)
            s.contains("andhra") -> Pair(15.9129, 79.7400)
            s.contains("telangana") -> Pair(18.1124, 79.0193)
            else -> Pair(13.1378, 78.1294)
        }
    }

    fun login(farmer: FarmerProfile) {
        currentFarmer = farmer
        val coords = getDistrictCoordinates(farmer.district, farmer.state)
        val distPrefix = farmer.district.take(3).uppercase()
        activeHarvest = HarvestDeclaration(
            harvestId = "FR-$distPrefix-20261113-0001",
            farmerLatitude = coords.first,
            farmerLongitude = coords.second,
            locationSource = "PIN: ${farmer.pincode}"
        )
        notifications = buildInitialNotifications(farmer)
    }

    fun setHarvest(declaration: HarvestDeclaration) {
        activeHarvest = declaration
        notifications = buildInitialNotifications(currentFarmer)
    }

    fun logout() {
        currentFarmer = null
        activeHarvest = null
    }
}
