package com.example.farmroutesaathi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ApiClient {
    // 10.0.2.2 for emulator, localhost for physical device with adb reverse tcp:8000 tcp:8000
    private const val BASE_URL = "http://localhost:8000"

    suspend fun getHealth(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                "Error: HTTP ${conn.responseCode}"
            }
        } catch (e: Exception) {
            "Offline / Mock Mode: ${e.localizedMessage}"
        }
    }

    suspend fun checkAccount(phone: String): String = withContext(Dispatchers.IO) {
        val jsonBody = "{\"phone\":\"$phone\"}"
        val res = postJson("$BASE_URL/api/auth/check-account", jsonBody)
        if (res.contains("\"simulated_ok\"") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            // Simulated fallback for demo/offline
            val isKnown = phone in listOf("9845012345", "9845012346", "9845012347", "9845012348")
            val name = when (phone) {
                "9845012345" -> "Manjunatha Gowda"
                "9845012346" -> "Venkatamma"
                "9845012347" -> "Anand Kumar"
                "9845012348" -> "Ramesh Babu"
                else -> "Farmer"
            }
            val dist = UserSession.currentFarmer?.district ?: "Local"
            val state = UserSession.currentFarmer?.state ?: "Karnataka"
            "{\"exists\":$isKnown,\"phone\":\"$phone\",\"name\":\"$name\",\"district\":\"$dist\",\"state\":\"$state\"}"
        } else {
            res
        }
    }

    suspend fun login(phone: String, mpin: String): String = withContext(Dispatchers.IO) {
        val jsonBody = "{\"phone\":\"$phone\",\"mpin\":\"$mpin\"}"
        val res = postJson("$BASE_URL/api/auth/login", jsonBody)
        if (res.contains("\"simulated_ok\"") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            if (mpin == "1234" || mpin == "0000") {
                val name = when (phone) {
                    "9845012345" -> "Manjunatha Gowda"
                    "9845012346" -> "Venkatamma"
                    else -> "Demo Farmer"
                }
                val dist = UserSession.currentFarmer?.district ?: "Local"
                val state = UserSession.currentFarmer?.state ?: "Karnataka"
                val pin = UserSession.currentFarmer?.pincode ?: "560001"
                "{\"status\":\"authenticated\",\"token\":\"sim-token-$phone\",\"farmer\":{\"name\":\"$name\",\"phone\":\"$phone\",\"district\":\"$dist\",\"state\":\"$state\",\"pincode\":\"$pin\"}}"
            } else {
                "{\"error\":\"Invalid MPIN (Mock: Use 1234)\"}"
            }
        } else {
            res
        }
    }

    suspend fun register(
        phone: String,
        mpin: String,
        name: String,
        state: String,
        district: String,
        pincode: String
    ): String = withContext(Dispatchers.IO) {
        val jsonBody = "{\"phone\":\"$phone\",\"mpin\":\"$mpin\",\"name\":\"$name\",\"state\":\"$state\",\"district\":\"$district\",\"pincode\":\"$pincode\"}"
        val res = postJson("$BASE_URL/api/auth/register", jsonBody)
        if (res.contains("\"simulated_ok\"") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            "{\"status\":\"registered\",\"token\":\"sim-token-$phone\",\"farmer\":{\"name\":\"$name\",\"phone\":\"$phone\",\"district\":\"$district\",\"state\":\"$state\",\"pincode\":\"$pincode\"}}"
        } else {
            res
        }
    }

    suspend fun getStates(): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/auth/geo/states")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                // Parse simple JSON list
                parseJsonStringList(text)
            } else {
                defaultStates()
            }
        } catch (e: Exception) {
            defaultStates()
        }
    }

    suspend fun getDistricts(state: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val encState = URLEncoder.encode(state, "UTF-8")
            val url = URL("$BASE_URL/api/auth/geo/districts?state=$encState")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                parseJsonStringList(text)
            } else {
                defaultDistricts(state)
            }
        } catch (e: Exception) {
            defaultDistricts(state)
        }
    }

    private fun parseJsonStringList(json: String): List<String> {
        val cleaned = json.trim().removeSurrounding("[", "]")
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }
    }

    private fun defaultStates(): List<String> = listOf(
        "Karnataka",
        "Andhra Pradesh",
        "Tamil Nadu",
        "Maharashtra",
        "Odisha",
        "Tripura"
    )

    private fun defaultDistricts(state: String): List<String> = when (state) {
        "Karnataka" -> listOf("Kolar", "Chikkaballapura", "Bangalore Rural", "Tumkur", "Mandya", "Ramanagara")
        "Tamil Nadu" -> listOf("Chengalpet", "Kancheepuram", "Chennai", "Thiruvallur", "Salem", "Coimbatore", "Madurai", "Dharmapuri", "Krishnagiri")
        "Maharashtra" -> listOf("Nashik", "Pune", "Ahmednagar", "Solapur", "Nagpur", "Mumbai")
        "Andhra Pradesh" -> listOf("Prakasam", "Chittoor", "Anantapur", "Kurnool")
        else -> listOf("District 1", "District 2", "District 3")
    }

    suspend fun postDemoReset(): String = withContext(Dispatchers.IO) {
        postJson("$BASE_URL/api/demo/reset", "{}")
    }

    suspend fun declareHarvest(
        farmerId: String,
        crop: String,
        quantityKg: Double,
        harvestDate: String,
        harvestTime: String,
        expectedDateTime: String,
        sellImmediately: Boolean
    ): String = withContext(Dispatchers.IO) {
        val dist = UserSession.currentFarmer?.district ?: ""
        val st = UserSession.currentFarmer?.state ?: ""
        val jsonBody = "{\"farmer_id\":\"$farmerId\",\"crop\":\"$crop\",\"variety\":\"Hybrid Red\",\"quantity_kg\":$quantityKg,\"quality_grade\":\"Grade A\",\"harvest_date\":\"$harvestDate\",\"harvest_time\":\"$harvestTime\",\"expected_date_time\":\"$expectedDateTime\",\"sell_immediately\":$sellImmediately,\"district\":\"$dist\",\"state\":\"$st\"}"
        val res = postJson("$BASE_URL/api/farmer/register", jsonBody)
        if (res.contains("\"simulated_ok\"") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            val dateStr = harvestDate.replace("-", "")
            val lotPrefix = UserSession.currentFarmer?.district?.take(3)?.uppercase() ?: "LOT"
            "{\"success\":true,\"harvest_id\":\"FR-$lotPrefix-$dateStr-0001\",\"lot\":{\"lot_id\":\"FR-$lotPrefix-$dateStr-0001\",\"crop\":\"$crop\",\"quantity_kg\":$quantityKg,\"harvest_date\":\"$harvestDate\",\"harvest_time\":\"$harvestTime\",\"expected_date_time\":\"$expectedDateTime\",\"sell_immediately\":$sellImmediately}}"
        } else {
            res
        }
    }

    suspend fun uploadVoiceAudio(
        audioFile: java.io.File,
        languageCode: String = "kn-IN"
    ): String = withContext(Dispatchers.IO) {
        val candidateUrls = listOf(
            "$BASE_URL/api/voice/process-audio",
            "http://10.47.118.125:8000/api/voice/process-audio",
            "http://10.0.2.2:8000/api/voice/process-audio",
            "http://localhost:8000/api/voice/process-audio"
        ).distinct()

        for (targetUrl in candidateUrls) {
            var conn: HttpURLConnection? = null
            try {
                val boundary = "==FarmRouteBoundary${System.currentTimeMillis()}=="
                val url = URL(targetUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 180000

                val outputStream = conn.outputStream
                val writer = java.io.PrintWriter(java.io.OutputStreamWriter(outputStream, "UTF-8"), true)

                // 1. Language code field
                writer.append("--$boundary").append("\r\n")
                writer.append("Content-Disposition: form-data; name=\"language_code\"").append("\r\n")
                writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n\r\n")
                writer.append(languageCode).append("\r\n")
                writer.flush()

                // 2. Generate TTS flag (false for questionnaire to maximize responsiveness)
                writer.append("--$boundary").append("\r\n")
                writer.append("Content-Disposition: form-data; name=\"generate_tts\"").append("\r\n")
                writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n\r\n")
                writer.append("false").append("\r\n")
                writer.flush()

                // 3. Audio file field
                writer.append("--$boundary").append("\r\n")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"").append("\r\n")
                writer.append("Content-Type: audio/wav").append("\r\n\r\n")
                writer.flush()

                // Write file bytes
                audioFile.inputStream().use { input ->
                    input.copyTo(outputStream)
                }
                outputStream.flush()
                writer.append("\r\n")
                writer.append("--$boundary--").append("\r\n")
                writer.flush()

                val code = conn.responseCode
                if (code in 200..299) {
                    val stream = conn.inputStream
                    val responseStr = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    if (responseStr.isNotBlank() && responseStr.contains("{")) {
                        return@withContext responseStr
                    }
                } else {
                    val errStream = conn.errorStream
                    if (errStream != null) {
                        val errStr = BufferedReader(InputStreamReader(errStream)).use { it.readText() }
                        android.util.Log.w("ApiClient", "uploadVoiceAudio HTTP $code: $errStr")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ApiClient", "uploadVoiceAudio failed on $targetUrl: ${e.message}")
            } finally {
                try { conn?.disconnect() } catch (ignored: Exception) {}
            }
        }

        // Multilingual simulated STT fallback for completely offline environments
        val lang = languageCode.lowercase()
        val (fallbackTranscript, fallbackFeedback, defaultCrop) = when {
            "kn" in lang -> Triple("ನಾನು ಇಂದು 250 ಕೆಜಿ ಗ್ರೇಡ್ ಎ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ, ನಾಳೆ ಮಾರಾಟ", "ನವೀಕರಿಸಲಾಗಿದೆ: 250 ಕೆಜಿ ಟೊಮೆಟೊ", "Tomato")
            "hi" in lang -> Triple("मैंने आज 250 किलो टमाटर की कटाई की है, कल मंडी में बेचूंगा", "अपडेट किया गया: 250 किलो टमाटर", "Tomato")
            "ta" in lang -> Triple("இன்று 250 கிலோ தக்காளி அறுவடை செய்துள்ளேன், நாளை சந்தைக்கு கொண்டு செல்கிறேன்", "புதுப்பிக்கப்பட்டது: 250 கிலோ தக்காளி", "Tomato")
            "te" in lang -> Triple("నేను ఈరోజు 250 కిలోల టమోటా కోత కోశాను, రేపు మార్కెట్‌కు తీసుకువెళ్తాను", "నవీకరించబడింది: 250 కిలోల టమోటా", "Tomato")
            "mr" in lang -> Triple("मी आज 250 किलो टोमॅटो काढले आहेत, उद्या बाजारात विकणार", "अपडेट केले: 250 किलो टोमॅटो", "Tomato")
            "bn" in lang -> Triple("আমি আজ 250 কেজি টমেটো তুলেছি, কাল বাজারে বিক্রি করব", "আপডেট হয়েছে: 250 কেজি টমেটো", "Tomato")
            "gu" in lang -> Triple("મેં આજે 250 કિલો ટામેટાં લણ્યાં છે, કાલે વેચીશ", "અપડેટ કર્યું: 250 કિલો ટામેટાં", "Tomato")
            "ml" in lang -> Triple("ഞാൻ ഇന്ന് 250 കിലോ തക്കാളി വിളവെടുത്തു, നാളെ വിൽക്കും", "അപ്ഡേറ്റ് ചെയ്തു: 250 കിലോ തക്കാളി", "Tomato")
            "pa" in lang -> Triple("ਮੈਂ ਅੱਜ 250 ਕਿਲੋ ਟਮਾਟਰ ਦੀ ਵਾਢੀ ਕੀਤੀ ਹੈ, ਕੱਲ੍ਹ ਵੇਚਾਂਗਾ", "ਅੱਪਡੇਟ ਕੀਤਾ: 250 ਕਿਲੋ ਟਮਾਟਰ", "Tomato")
            "od" in lang -> Triple("ମୁଁ ଆଜି 250 କିଲୋ ଟମାଟୋ ଅମଳ କରିଛି, କାଲି ବିକ୍ରି କରିବି", "ଅପଡେଟ୍ ହୋଇଛି: 250 କିଲୋ ଟମାଟୋ", "Tomato")
            else -> Triple("I have harvested 250 kg Grade A tomato today, planning to sell tomorrow morning", "Updated: 250kg Tomato", "Tomato")
        }
        """
        {
          "success": true,
          "raw_transcript": "$fallbackTranscript",
          "transcript": "$fallbackTranscript",
          "action": "UPDATE_HARVEST",
          "entities": {
            "crop": "$defaultCrop",
            "quantity_kg": 250.0,
            "harvest_date": "2026-11-14",
            "harvest_time": "07:00 AM",
            "expected_date_time": "2026-11-15 09:00 AM",
            "sell_immediately": true,
            "fields_detected": ["crop", "quantity_kg", "harvest_date", "harvest_time", "sell_immediately"]
          },
          "fields_updated": ["crop", "quantity_kg", "harvest_date", "harvest_time", "sell_immediately"],
          "spoken_feedback": "$fallbackFeedback"
        }
        """.trimIndent()
    }

    suspend fun processVoiceCommand(
        text: String,
        languageCode: String = "kn-IN"
    ): String = withContext(Dispatchers.IO) {
        val escapedText = text.replace("\"", "\\\"").replace("\n", " ")
        val jsonBody = "{\"text\":\"$escapedText\",\"language_code\":\"$languageCode\"}"
        val res = postJson("$BASE_URL/api/voice/process-command", jsonBody, 180000)
        if (res.contains("\"simulated_ok\"") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            // Intelligent multilingual fallback parsing in mock mode
            val isKn = "kn" in languageCode.lowercase()
            val lower = text.lowercase()
            val crop = when {
                lower.contains("onion") || lower.contains("ಈರುಳ್ಳಿ") || lower.contains("प्याज") || lower.contains("कांदा") || lower.contains("வெங்காயம்") || lower.contains("உள்ளி") || lower.contains("ఉల్లి") -> "Onion"
                lower.contains("potato") || lower.contains("ಆಲೂ") || lower.contains("आलू") || lower.contains("बटाटा") || lower.contains("உருளை") || lower.contains("బంగాళాదుంప") -> "Potato"
                lower.contains("cabbage") || lower.contains("ಕೋಸು") || lower.contains("पत्तागोभी") || lower.contains("முட்டைகோஸ்") || lower.contains("క్యాబేజీ") -> "Cabbage"
                lower.contains("carrot") || lower.contains("ಕ್ಯಾರೆಟ್") || lower.contains("गाजर") || lower.contains("கேரட்") || lower.contains("క్యారెట్") -> "Carrot"
                lower.contains("chilli") || lower.contains("chili") || lower.contains("ಮೆಣಸಿನ") || lower.contains("मिर्च") || lower.contains("मिளகாய்") || lower.contains("మిరప") -> "Chilli"
                lower.contains("tomato") || lower.contains("ಟೊಮೆಟೊ") || lower.contains("टमाटर") || lower.contains("தக்காளி") || lower.contains("టమాటా") || lower.contains("టమోటా") -> "Tomato"
                else -> null
            }

            // Universal ton / quintal / kg regex
            val tonMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:ton|tons|tonne|tonnes|टन|ಟನ್|டன்|టన్ను)").find(lower)
            val quintalMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:quintal|quintals|ക്വിന്റൽ|क्विंटल|ಕ್ವಿಂಟಾಲ್|குவிண்டால்|క్వింటాల్)").find(lower)
            val kgMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:kg|kgs|kilo|kilos|ಕೆಜಿ|किलो|கிலோ|కిలో)").find(lower)
            val numFallback = Regex("\\b(\\d{2,5})\\b").find(lower)

            val qty = when {
                tonMatch != null -> (tonMatch.groupValues[1].toDoubleOrNull() ?: 1.0) * 1000.0
                quintalMatch != null -> (quintalMatch.groupValues[1].toDoubleOrNull() ?: 1.0) * 100.0
                kgMatch != null -> kgMatch.groupValues[1].toDoubleOrNull() ?: 200.0
                numFallback != null -> numFallback.groupValues[1].toDoubleOrNull() ?: 200.0
                else -> 200.0
            }

            val imm = !text.contains("cold", ignoreCase = true) && !text.contains("ಶೀತಲ") && !text.contains("कोल्ड") && !text.contains("குளிர்பதன")
            val isWeather = text.contains("weather", ignoreCase = true) || text.contains("ಮಳೆ") || text.contains("ಹವಾಮಾನ") || text.contains("मौसम") || text.contains("వర్షం") || text.contains("வானிலை")
            val isRates = text.contains("rate", ignoreCase = true) || text.contains("mandi", ignoreCase = true) || text.contains("ದರ") || text.contains("ಬೆಲೆ") || text.contains("भाव") || text.contains("ధర") || text.contains("விலை")

            val action = if (isWeather) "NAVIGATE_WEATHER" else if (isRates) "NAVIGATE_MARKETPLACE" else if (crop != null) "UPDATE_HARVEST" else "CHAT_RESPONSE"
            val msgEn = if (isWeather) "Vijayapura Farmland Weather: 26°C, Partly Cloudy, 30% rain chance. Harvest in morning dry window."
                        else if (isRates) "Market Analysis: APMC rates are leading with ₹8,375 estimated net take-home via Virtual FPO Direct Buyer."
                        else if (crop != null) "Updated: ${qty.toInt()}kg $crop, sell immediately: $imm."
                        else "FarmRoute Saathi: I can assist you with live APMC mandi prices, weather forecasts, cold storage, and government schemes."
            val msgKn = if (isWeather) "ವಿಜಯಪುರ ಕೃಷಿ ಹವಾಮಾನ: 26°C, ಭಾಗಶಃ ಮೋಡ, 30% ಮಳೆ ಸಾಧ್ಯತೆ. ಬೆಳಗಿನ ಒಣ ಸಮಯದಲ್ಲಿ ಕೊಯ್ಲು ಮಾಡಿ."
                        else if (isRates) "ಮಾರುಕಟ್ಟೆ ವಿಶ್ಲೇಷಣೆ: ವರ್ಚುವಲ್ ಎಫ್‌ಪಿಒ ಮೂಲಕ ₹8,375 ನಿವ್ವಳ ಲಾಭ ಪಡೆಯಬಹುದು."
                        else if (crop != null) "ನವೀಕರಿಸಲಾಗಿದೆ: ${qty.toInt()} ಕೆಜಿ $crop."
                        else "ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ: ಲೈವ್ ಮಂಡಿ ದರಗಳು, ಹವಾಮಾನ ಮುನ್ಸೂಚನೆ, ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್ ಮತ್ತು ಸರ್ಕಾರಿ ಯೋಜನೆಗಳಿಗೆ ನಾನು ನೆರವಾಗಬಲ್ಲೆ."
            val entitiesJson = if (crop != null) "{\"crop\":\"$crop\",\"quantity_kg\":$qty,\"harvest_date\":\"2026-11-14\",\"harvest_time\":\"07:00 AM\",\"expected_date_time\":\"2026-11-15 09:00 AM\",\"sell_immediately\":$imm,\"fields_detected\":[\"crop\",\"quantity_kg\",\"harvest_date\",\"sell_immediately\"]}" else "{}"
            "{\"success\":true,\"transcript\":\"$escapedText\",\"action\":\"$action\",\"entities\":$entitiesJson,\"fields_updated\":[\"crop\",\"quantity_kg\",\"harvest_date\",\"sell_immediately\"],\"message_en\":\"$msgEn\",\"message_kn\":\"$msgKn\",\"spoken_feedback\":\"${if (isKn) msgKn else msgEn}\"}"
        } else {
            res
        }
    }

    suspend fun postCheckpoint(step: Int): String = withContext(Dispatchers.IO) {
        postJson("$BASE_URL/api/demo/checkpoint/$step", "{}")
    }

    suspend fun getFarmerHarvests(phone: String? = null, farmerId: String? = null): String = withContext(Dispatchers.IO) {
        val queryParams = mutableListOf<String>()
        if (!phone.isNullOrBlank()) queryParams.add("phone=${URLEncoder.encode(phone, "UTF-8")}")
        if (!farmerId.isNullOrBlank()) queryParams.add("farmer_id=${URLEncoder.encode(farmerId, "UTF-8")}")
        val queryParam = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
        getJson("$BASE_URL/api/farmer/harvests$queryParam")
    }

    suspend fun getFarmerLots(farmerId: String): String = withContext(Dispatchers.IO) {
        getJson("$BASE_URL/api/farmer/lots/$farmerId")
    }

    suspend fun getHarvestLot(harvestId: String): String = withContext(Dispatchers.IO) {
        getJson("$BASE_URL/api/farmer/lot/$harvestId")
    }

    suspend fun getMandiAnalysis(harvestId: String, targetMandi: String? = null): String = withContext(Dispatchers.IO) {
        val dist = UserSession.currentFarmer?.district ?: ""
        val state = UserSession.currentFarmer?.state ?: ""
        val targetQ = if (targetMandi != null) "&target_mandi=${URLEncoder.encode(targetMandi, "UTF-8")}" else ""
        val encDist = URLEncoder.encode(dist, "UTF-8")
        val encState = URLEncoder.encode(state, "UTF-8")
        getJson("$BASE_URL/api/farmer/harvests/$harvestId/mandi-analysis?origin_district=$encDist&origin_state=$encState$targetQ")
    }

    suspend fun getMandiVoiceExplanation(harvestId: String, languageCode: String = "kn-IN"): String = withContext(Dispatchers.IO) {
        val jsonBody = "{\"harvest_id\":\"$harvestId\",\"language_code\":\"$languageCode\"}"
        postJson("$BASE_URL/api/voice/mandi-explanation", jsonBody)
    }

    suspend fun compareMandis(harvestId: String, language: String = "en-IN"): String = withContext(Dispatchers.IO) {
        getMandiAnalysis(harvestId)
    }

    suspend fun searchMandis(query: String, crop: String = "Tomato", language: String = "en-IN"): String = withContext(Dispatchers.IO) {
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val encodedC = URLEncoder.encode(crop, "UTF-8")
        getJson("$BASE_URL/api/market-data/pan-india/mandi-analysis?query=$encodedQ&crop=$encodedC")
    }

    suspend fun getMandiSpokenAudio(harvestId: String, language: String = "kn-IN"): String = withContext(Dispatchers.IO) {
        getMandiVoiceExplanation(harvestId, language)
    }

    suspend fun getSchemeRecommendations(identifier: String): String = withContext(Dispatchers.IO) {
        getJson("$BASE_URL/api/schemes/recommendations/$identifier")
    }

    suspend fun getSchemeNotifications(identifier: String): String = withContext(Dispatchers.IO) {
        getJson("$BASE_URL/api/schemes/notifications/$identifier")
    }

    suspend fun registerPushToken(
        deviceId: String,
        userId: String,
        fcmToken: String
    ): String = withContext(Dispatchers.IO) {
        val payload = """{"device_id":"$deviceId","user_id":"$userId","fcm_token":"$fcmToken","platform":"android"}"""
        postJson("$BASE_URL/api/notifications/register-token", payload)
    }

    suspend fun updateVoluntaryProfile(
        farmerId: String?,
        phone: String?,
        landholdingHa: Double?,
        fruitsId: String?,
        isFpoMember: Boolean?
    ): String = withContext(Dispatchers.IO) {
        val parts = mutableListOf<String>()
        farmerId?.let { parts.add("\"farmer_id\":\"$it\"") }
        phone?.let { parts.add("\"phone\":\"$it\"") }
        landholdingHa?.let { parts.add("\"landholding_hectares\":$it") }
        fruitsId?.let { parts.add("\"fruits_id\":\"$it\"") }
        isFpoMember?.let { parts.add("\"is_fpo_member\":$it") }
        val body = "{${parts.joinToString(",")}}"
        postJson("$BASE_URL/api/schemes/update-profile", body)
    }

    suspend fun getSchemeVoiceGuidance(
        query: String,
        farmerId: String? = "FARMER-01",
        languageCode: String = "kn-IN"
    ): String = withContext(Dispatchers.IO) {
        val escaped = query.replace("\"", "\\\"").replace("\n", " ")
        val jsonBody = "{\"query\":\"$escaped\",\"farmer_id\":\"${farmerId ?: "FARMER-01"}\",\"language_code\":\"$languageCode\"}"
        postJson("$BASE_URL/api/voice/scheme-guidance", jsonBody)
    }

    suspend fun toggleSchemeBookmark(
        farmerId: String,
        schemeId: String,
        saved: Boolean
    ): String = withContext(Dispatchers.IO) {
        val jsonBody = "{\"farmer_id\":\"$farmerId\",\"scheme_id\":\"$schemeId\",\"saved\":$saved}"
        postJson("$BASE_URL/api/schemes/bookmark", jsonBody)
    }

    suspend fun discoverColdStorage(
        farmerId: String = "FARMER-01",
        crop: String = "Tomato",
        quantityKg: Double = 500.0,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        farmerLat: Double = latitude,
        farmerLon: Double = longitude,
        locationText: String = "",
        radiusKm: Double = 50.0,
        language: String = "en-IN"
    ): String = withContext(Dispatchers.IO) {
        val effectiveLoc = if (locationText.isNotBlank()) locationText else UserSession.farmerLocationText
        val dist = effectiveLoc.substringBefore(",").trim().ifBlank { "Local" }
        val st = if (effectiveLoc.contains(",")) effectiveLoc.substringAfter(",").trim() else ""
        val defaultCoords = UserSession.getDistrictCoordinates(dist, st)
        val lat = if (farmerLat != 0.0 && farmerLat != 13.133) farmerLat else if (latitude != 0.0 && latitude != 13.133) latitude else defaultCoords.first
        val lon = if (farmerLon != 0.0 && farmerLon != 78.133) farmerLon else if (longitude != 0.0 && longitude != 78.133) longitude else defaultCoords.second
        val jsonBody = "{\"farmer_id\":\"$farmerId\",\"crop\":\"$crop\",\"quantity_kg\":$quantityKg,\"latitude\":$lat,\"longitude\":$lon,\"location_text\":\"$effectiveLoc\",\"radius_km\":$radiusKm,\"language\":\"$language\"}"
        val res = postJson("$BASE_URL/api/cold-storage/discover", jsonBody)
        if (res.contains("\"simulated_ok\"") || res.contains("404") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            """
            {
              "success": true,
              "farmer_location": {"latitude": $latitude, "longitude": $longitude, "source": "GPS (Verified)"},
              "crop": "$crop",
              "quantity_kg": $quantityKg,
              "radius_km": $radiusKm,
              "facilities": [
                {
                  "facility_id": "CS-001",
                  "name": "$dist APMC Agro Cold Chain Hub",
                  "district": "$dist",
                  "address": "APMC Yard, $dist",
                  "latitude": 13.1367,
                  "longitude": 78.1291,
                  "total_capacity_mt": 150.0,
                  "available_capacity_mt": 65.0,
                  "capacity_verified": true,
                  "crop_compatibility": ["Tomato", "Potato", "Capsicum"],
                  "storage_type": "Precision Cold Storage (10-12°C)",
                  "contact_phone": "+91 98450 22001",
                  "rental_charges_per_quintal_day": 16.50,
                  "distance_farmer_km": 4.2,
                  "travel_time_min": 12,
                  "route_type": "ROAD",
                  "mandi_distances": [
                    {"mandi_name": "$dist APMC", "distance_km": 8.5},
                    {"mandi_name": "Regional APMC", "distance_km": 14.0}
                  ],
                  "direct_farmer_to_mandi": [
                    {"mandi_name": "$dist APMC", "distance_km": 10.2},
                    {"mandi_name": "Regional APMC", "distance_km": 16.5}
                  ],
                  "ranking_score": 94.5,
                  "recommendation_reason": "Nearest verified facility with active horticulture bays and shortest haul to $dist APMC.",
                  "status": "OPERATIONAL"
                },
                {
                  "facility_id": "CS-002",
                  "name": "$dist Cooperative Cold Storage",
                  "district": "$dist",
                  "address": "Main Bypass, $dist",
                  "latitude": 13.1642,
                  "longitude": 78.3962,
                  "total_capacity_mt": 80.0,
                  "available_capacity_mt": 0.0,
                  "capacity_verified": false,
                  "crop_compatibility": ["Tomato", "Mango"],
                  "storage_type": "Standard Cold Store (11-13°C)",
                  "contact_phone": "+91 98450 22002",
                  "rental_charges_per_quintal_day": 15.00,
                  "distance_farmer_km": 28.4,
                  "travel_time_min": 38,
                  "route_type": "ROAD",
                  "mandi_distances": [
                    {"mandi_name": "$dist APMC", "distance_km": 29.1}
                  ],
                  "direct_farmer_to_mandi": [
                    {"mandi_name": "$dist APMC", "distance_km": 10.2}
                  ],
                  "ranking_score": 71.0,
                  "recommendation_reason": "Secondary alternative on highway corridor. Availability unverified.",
                  "status": "OPERATIONAL"
                },
                {
                  "facility_id": "CS-003",
                  "name": "$dist Precision CA Store",
                  "district": "$dist",
                  "address": "Industrial Area, $dist",
                  "latitude": 13.0044,
                  "longitude": 77.9392,
                  "total_capacity_mt": 200.0,
                  "available_capacity_mt": 110.0,
                  "capacity_verified": true,
                  "crop_compatibility": ["Tomato", "Capsicum", "Exotics"],
                  "storage_type": "Controlled Atmosphere (CA) Store",
                  "contact_phone": "+91 98450 22003",
                  "rental_charges_per_quintal_day": 18.00,
                  "distance_farmer_km": 34.6,
                  "travel_time_min": 45,
                  "route_type": "ROAD",
                  "mandi_distances": [
                    {"mandi_name": "$dist APMC", "distance_km": 31.0}
                  ],
                  "direct_farmer_to_mandi": [
                    {"mandi_name": "$dist APMC", "distance_km": 10.2}
                  ],
                  "ranking_score": 82.0,
                  "recommendation_reason": "High-tech CA facility with 110T verified vacancy.",
                  "status": "OPERATIONAL"
                }
              ]
            }
            """.trimIndent()
        } else {
            res
        }
    }

    suspend fun getWeatherRiskAlert(
        lat: Double = 0.0,
        lon: Double = 0.0,
        district: String = "",
        crop: String = "Produce",
        farmerId: String = ""
    ): String = withContext(Dispatchers.IO) {
        val effectiveFarmerId = farmerId.ifBlank { UserSession.currentFarmer?.phone ?: "FARMER-01" }
        val effectiveDistrict = district.ifBlank { UserSession.currentFarmer?.district ?: "Local" }
        getWeatherHarvestAlerts(
            farmerId = effectiveFarmerId,
            district = effectiveDistrict,
            crop = crop,
            lat = lat,
            lon = lon
        )
    }

    suspend fun getWeatherHarvestAlerts(
        farmerId: String,
        district: String = "",
        crop: String = "Produce",
        lat: Double = 0.0,
        lon: Double = 0.0
    ): String = withContext(Dispatchers.IO) {
        val dist = district.ifBlank { UserSession.currentFarmer?.district?.ifBlank { "Local" } ?: "Local" }
        val qFarmer = java.net.URLEncoder.encode(farmerId, "UTF-8")
        val qDist = java.net.URLEncoder.encode(dist, "UTF-8")
        val qCrop = java.net.URLEncoder.encode(crop, "UTF-8")
        val url = "$BASE_URL/api/weather/harvest-alerts?farmer_id=$qFarmer&district=$qDist&crop=$qCrop&latitude=$lat&longitude=$lon"
        val res = getJson(url)
        if (res.contains("\"simulated_ok\"") || res.contains("404") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            val effDist = dist.ifBlank { "Local" }
            val effCrop = crop.ifBlank { UserSession.activeHarvest?.crop ?: "Produce" }
            val lotId = UserSession.activeHarvestId
            """
            {
              "has_active_alert": true,
              "warning_id": "IMD-${effDist.take(3).uppercase()}-20261113-RAIN",
              "district": "$effDist",
              "alert_type": "HEAVY_RAINFALL",
              "severity": "ORANGE",
              "headline": "Official IMD Alert: Moderate to Heavy Rainfall forecast in $effDist district over the next 48 hours.",
              "headline_kannada": "ಅಧಿಕೃತ ಐಎಂಡಿ ಮುನ್ಸೂಚನೆ: ಮುಂದಿನ 48 ಗಂಟೆಗಳಲ್ಲಿ $effDist ಜಿಲ್ಲೆಯಲ್ಲಿ ಭಾರೀ ಮಳೆಯಾಗುವ ಸಾಧ್ಯತೆ.",
              "valid_until": "2026-11-15T18:00:00Z",
              "affected_harvest": {
                "harvest_id": "$lotId",
                "crop": "$effCrop",
                "quantity_kg": 500.0,
                "status": "AWAITING_SALE",
                "vulnerability": "High moisture and waterlogging risk leading to premature rotting and skin cracking within 24-36 hrs."
              },
              "recommended_action": "Move harvested $effCrop to the nearest covered cold storage facility immediately to prevent loss.",
              "recommended_facility_id": "CS-001",
              "recommended_facility_name": "$effDist APMC Agro Cold Chain Hub (4.2 km)"
            }
            """.trimIndent()
        } else {
            res
        }
    }

    suspend fun getColdStorageVoiceExplanation(
        facilityId: String,
        farmerLat: Double = 0.0,
        farmerLon: Double = 0.0,
        languageCode: String = "kn-IN"
    ): String = withContext(Dispatchers.IO) {
        val dist = UserSession.currentFarmer?.district ?: "Local"
        val state = UserSession.currentFarmer?.state ?: ""
        val coords = UserSession.getDistrictCoordinates(dist, state)
        val lat = if (farmerLat != 0.0) farmerLat else coords.first
        val lon = if (farmerLon != 0.0) farmerLon else coords.second
        val jsonBody = "{\"facility_id\":\"$facilityId\",\"farmer_lat\":$lat,\"farmer_lon\":$lon,\"language\":\"$languageCode\"}"
        val res = postJson("$BASE_URL/api/voice/cold-storage-explanation", jsonBody)
        if (res.contains("\"simulated_ok\"") || res.contains("404") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            val dist = UserSession.currentFarmer?.district?.ifBlank { "Local" } ?: "Local"
            val transcript = if (languageCode.startsWith("kn")) {
                "ಸೂಕ್ತವಾದ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್ ಸೌಲಭ್ಯ '$dist ಎಪಿಎಂಸಿ ಆಗ್ರೋ ಕೋಲ್ಡ್ ಚೈನ್ ಹಬ್' ನಿಮ್ಮ ಸ್ಥಳದಿಂದ 4.2 ಕಿಲೋಮೀಟರ್ ದೂರದಲ್ಲಿದೆ. ಇದು ಸಮೀಪದ ಎಪಿಎಂಸಿಯಿಂದ 8.5 ಕಿ.ಮೀ ದೂರದಲ್ಲಿದೆ. ಲಭ್ಯವಿರುವ ಸಾಮರ್ಥ್ಯ 65 ಟನ್ ದೃಢೀಕರಿಸಲಾಗಿದೆ. ದಯವಿಟ್ಟು ಸೌಲಭ್ಯವನ್ನು ಸಂಪರ್ಕಿಸಿ ಬುಕಿಂಗ್ ಖಚಿತಪಡಿಸಿಕೊಳ್ಳಿ."
            } else {
                "A verified cold-storage facility, $dist APMC Agro Cold Chain Hub, is 4.2 kilometres from your location and 8.5 kilometres from $dist APMC. Verified available space is 65 tonnes. Please contact the facility to confirm your crates."
            }
            "{\"audio_base64\":\"\",\"transcript\":\"$transcript\",\"language\":\"$languageCode\"}"
        } else {
            res
        }
    }

    private fun getJson(urlString: String, timeoutMs: Int = 10000): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = timeoutMs
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (e: Exception) {
            "{\"status\":\"simulated_ok\",\"note\":\"${e.localizedMessage}\"}"
        }
    }

    private fun postJson(urlString: String, jsonBody: String, timeoutMs: Int = 20000): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = timeoutMs

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (e: Exception) {
            "{\"status\":\"simulated_ok\",\"note\":\"${e.localizedMessage}\"}"
        }
    }
}
