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

    suspend fun postCheckpoint(step: Int): String = withContext(Dispatchers.IO) {
        postJson("$BASE_URL/api/demo/checkpoint/$step", "{}")
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
