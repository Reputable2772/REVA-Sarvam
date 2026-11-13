package com.example.farmroutesaathi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
