package com.example.box

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

object PingHelper {
    suspend fun ping(
        context: Context,
        showSuccessToast: (() -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
        var accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)

        if (deviceId == null || refreshToken == null) {
            Log.w("PingHelper", "Не хватает данных для ping")
            return@withContext false
        }

        suspend fun pingWithToken(token: String): Int {
            val measure = measurePing()
            val url = URL("https://api-box.pspware.dev/device-service/devices/$deviceId/ping?ping=$measure")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            return connection.responseCode
        }

//        suspend fun pingWithToken(context: Context, token: String): Int {
//            val measure = measurePing()
//            val url = URL("https://api-box.pspware.dev/device-service/devices/$deviceId/ping")
//            val connection = url.openConnection() as HttpURLConnection
//            connection.requestMethod = "POST"
//            connection.setRequestProperty("Authorization", "Bearer $token")
//            connection.setRequestProperty("Content-Type", "application/json")
//            connection.setRequestProperty("Accept", "application/json")
//            connection.doOutput = true
//
//            val json = JSONObject().apply {
//                put("ping", measurePing())
//            }
//
//            connection.outputStream.bufferedWriter().use {
//                it.write(json.toString())
//                it.flush()
//            }
//
//            return connection.responseCode
//        }

        try {
            var responseCode = pingWithToken(accessToken ?: "")
//            var responseCode = pingWithToken(context, accessToken ?: "")
            if (responseCode == 401) {
                Log.i("PingHelper", "Access token expired. Обновляем...")
                accessToken = refreshAccessToken(refreshToken)
                if (accessToken != null) {
                    prefs.edit().putString("access_token", accessToken).apply()
                    responseCode = pingWithToken(accessToken)
                }
            }
            val success = responseCode == 204
            if (success) {
                withContext(Dispatchers.Main) {
                    showSuccessToast?.invoke()
                }
            }
            return@withContext success
//            return@withContext responseCode == 204
        } catch (e: Exception) {
            Log.e("PingHelper", "Ошибка при ping", e)
            return@withContext false
        }
    }

    private fun measurePing(): Long {
        return try {
            val start = System.nanoTime()
            val address = InetAddress.getByName("api-box.pspware.dev")
            val reachable = address.isReachable(3000) // таймаут 3 секунды
            val end = System.nanoTime()
            if (reachable) (end - start) / 1_000_000 else -1
        } catch (e: Exception) {
            Log.e("PingHelper", "Ошибка при измерении ping", e)
            -1
        }
    }

    suspend fun refreshAccessToken(refreshToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api-box.pspware.dev/authentication-service/authentication/refresh")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $refreshToken")
            connection.doOutput = true

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                return@withContext json.getString("access_token")
            } else {
                Log.e("PingHelper", "Ошибка обновления токена: $code")
            }
        } catch (e: Exception) {
            Log.e("PingHelper", "Ошибка при обновлении токена", e)
        }
        return@withContext null
    }
}
