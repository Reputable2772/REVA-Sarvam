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
            "{\"exists\":$isKnown,\"phone\":\"$phone\",\"name\":\"$name\",\"district\":\"Kolar\",\"state\":\"Karnataka\"}"
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
                "{\"status\":\"authenticated\",\"token\":\"sim-token-$phone\",\"farmer\":{\"name\":\"$name\",\"phone\":\"$phone\",\"district\":\"Kolar\",\"state\":\"Karnataka\",\"pincode\":\"563101\"}}"
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
        val jsonBody = "{\"farmer_id\":\"$farmerId\",\"crop\":\"$crop\",\"variety\":\"Hybrid Red\",\"quantity_kg\":$quantityKg,\"quality_grade\":\"Grade A\",\"harvest_date\":\"$harvestDate\",\"harvest_time\":\"$harvestTime\",\"expected_date_time\":\"$expectedDateTime\",\"sell_immediately\":$sellImmediately}"
        val res = postJson("$BASE_URL/api/farmer/register", jsonBody)
        if (res.contains("\"simulated_ok\"") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            val dateStr = harvestDate.replace("-", "")
            "{\"success\":true,\"harvest_id\":\"FR-KLR-$dateStr-0001\",\"lot\":{\"lot_id\":\"FR-KLR-$dateStr-0001\",\"crop\":\"$crop\",\"quantity_kg\":$quantityKg,\"harvest_date\":\"$harvestDate\",\"harvest_time\":\"$harvestTime\",\"expected_date_time\":\"$expectedDateTime\",\"sell_immediately\":$sellImmediately}}"
        } else {
            res
        }
    }

    suspend fun uploadVoiceAudio(
        audioFile: java.io.File,
        languageCode: String = "kn-IN"
    ): String = withContext(Dispatchers.IO) {
        try {
            val boundary = "==FarmRouteBoundary${System.currentTimeMillis()}=="
            val url = URL("$BASE_URL/api/voice/process-audio")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            val outputStream = conn.outputStream
            val writer = java.io.PrintWriter(java.io.OutputStreamWriter(outputStream, "UTF-8"), true)

            // 1. Language code field
            writer.append("--$boundary").append("\r\n")
            writer.append("Content-Disposition: form-data; name=\"language_code\"").append("\r\n")
            writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n\r\n")
            writer.append(languageCode).append("\r\n")
            writer.flush()

            // 2. Audio file field
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
            writer.close()

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (e: Exception) {
            "{\"status\":\"error\",\"note\":\"${e.localizedMessage}\"}"
        }
    }

    suspend fun processVoiceCommand(
        text: String,
        languageCode: String = "kn-IN"
    ): String = withContext(Dispatchers.IO) {
        val escapedText = text.replace("\"", "\\\"").replace("\n", " ")
        val jsonBody = "{\"text\":\"$escapedText\",\"language_code\":\"$languageCode\"}"
        val res = postJson("$BASE_URL/api/voice/process-command", jsonBody)
        if (res.contains("\"simulated_ok\"") || res.contains("Failed to connect") || res.contains("Connection refused")) {
            // Intelligent fallback parsing in mock mode
            val isKn = "kn" in languageCode.lowercase()
            val crop = if (text.contains("ಈರುಳ್ಳಿ") || text.contains("onion", ignoreCase = true)) "Onion" else "Tomato"
            val qty = if (text.contains("100")) 100.0 else if (text.contains("300")) 300.0 else 200.0
            val imm = !text.contains("cold", ignoreCase = true) && !text.contains("ಶೀತಲ")
            val msgEn = "Updated: ${qty.toInt()}kg $crop, sell immediately: $imm."
            val msgKn = "ನವೀಕರಿಸಲಾಗಿದೆ: ${qty.toInt()} ಕೆಜಿ $crop."
            "{\"success\":true,\"transcript\":\"$escapedText\",\"action\":\"UPDATE_HARVEST\",\"entities\":{\"crop\":\"$crop\",\"quantity_kg\":$qty,\"harvest_date\":\"2026-11-13\",\"harvest_time\":\"07:00 AM\",\"expected_date_time\":\"2026-11-14 09:00 AM\",\"sell_immediately\":$imm},\"message_en\":\"$msgEn\",\"message_kn\":\"$msgKn\",\"spoken_feedback\":\"${if (isKn) msgKn else msgEn}\"}"
        } else {
            res
        }
    }

    suspend fun postCheckpoint(step: Int): String = withContext(Dispatchers.IO) {
        postJson("$BASE_URL/api/demo/checkpoint/$step", "{}")
    }

    suspend fun getFarmerHarvests(phone: String? = null): String = withContext(Dispatchers.IO) {
        val queryParam = if (phone != null) "?farmer_phone=$phone" else ""
        getJson("$BASE_URL/api/farmer/harvests$queryParam")
    }

    suspend fun getMandiAnalysis(harvestId: String, targetMandi: String? = null): String = withContext(Dispatchers.IO) {
        val queryParam = if (targetMandi != null) "?target_mandi=${URLEncoder.encode(targetMandi, "UTF-8")}" else ""
        getJson("$BASE_URL/api/farmer/harvests/$harvestId/mandi-analysis$queryParam")
    }

    suspend fun getMandiVoiceExplanation(harvestId: String, languageCode: String = "kn-IN"): String = withContext(Dispatchers.IO) {
        val jsonBody = "{\"harvest_id\":\"$harvestId\",\"language_code\":\"$languageCode\"}"
        postJson("$BASE_URL/api/voice/mandi-explanation", jsonBody)
    }

    suspend fun getSchemeRecommendations(identifier: String): String = withContext(Dispatchers.IO) {
        getJson("$BASE_URL/api/schemes/recommendations/$identifier")
    }

    suspend fun getSchemeNotifications(identifier: String): String = withContext(Dispatchers.IO) {
        getJson("$BASE_URL/api/schemes/notifications/$identifier")
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

    private fun getJson(urlString: String): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (e: Exception) {
            "{\"status\":\"simulated_ok\",\"note\":\"${e.localizedMessage}\"}"
        }
    }

    private fun postJson(urlString: String, jsonBody: String): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (e: Exception) {
            "{\"status\":\"simulated_ok\",\"note\":\"${e.localizedMessage}\"}"
        }
    }
}
