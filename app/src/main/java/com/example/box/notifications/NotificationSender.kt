package com.example.box.notifications

import android.content.Context
import android.util.Log
import com.example.box.PingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NotificationSender {

    suspend fun sendNotificationToServer(
        context: Context,
        patternId: String,
        title: String,
        text: String
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
        var accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)

        if (deviceId == null || accessToken == null || refreshToken == null) {
            Log.w("NotificationSender", "Нет device_id или токенов")
            return@withContext false
        }

        fun buildJson(): String {
            val json = JSONObject()
            json.put("pattern_id", patternId)
            json.put("text", title + "\n" + text)
            return json.toString()
        }

//        fun postNotification(token: String): Int {
//            val url = URL("https://api-box.pspware.dev/device-service/notification/$deviceId")
//            val connection = url.openConnection() as HttpURLConnection
//            connection.requestMethod = "POST"
//            connection.setRequestProperty("Authorization", "Bearer $token")
//            connection.setRequestProperty("Content-Type", "application/json")
//            connection.setRequestProperty("Accept", "application/json")
//            connection.doOutput = true
//            Log.d("NotificationSender", "$url")
//
//            val requestBody = buildJson()
//            Log.d("NotificationSender", "JSON к отправке: $requestBody")
//
//
//            connection.outputStream.bufferedWriter().use {
//                it.write(requestBody)
//                it.flush()
//            }
//
//            return connection.responseCode
//        }
//
//        try {
//            var responseCode = postNotification(accessToken)
//            if (responseCode == 401) {
//                Log.d("NotificationSender", "Access token expired, обновляем...")
//                val newAccessToken = PingHelper.refreshAccessToken(refreshToken)
//                if (newAccessToken != null) {
//                    prefs.edit().putString("access_token", newAccessToken).apply()
//                    responseCode = postNotification(newAccessToken)
//                }
//            }
//            Log.d("NotificationSender", "Результат отправки: $responseCode")
//            return@withContext responseCode in 200..299
//        } catch (e: Exception) {
//            Log.e("NotificationSender", "Ошибка отправки уведомления", e)
//            return@withContext false
//        }

        fun postNotification(token: String): Pair<Int, String?> {
            val url = URL("https://api-box.pspware.dev/device-service/notification/$deviceId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            Log.d("NotificationSender", "$url")

            val requestBody = buildJson()
            Log.d("NotificationSender", "JSON к отправке: $requestBody")

            connection.outputStream.bufferedWriter().use {
                it.write(requestBody)
                it.flush()
            }

            val responseCode = connection.responseCode
            val errorBody = if (responseCode !in 200..299) {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
            } else null

            return Pair(responseCode, errorBody)
        }

        try {
            var (responseCode, errorBody) = postNotification(accessToken)

            if (responseCode == 401) {
                Log.d("NotificationSender", "Access token expired, обновляем...")
                val newAccessToken = PingHelper.refreshAccessToken(refreshToken)
                if (newAccessToken != null) {
                    prefs.edit().putString("access_token", newAccessToken).apply()
                    val result = postNotification(newAccessToken)
                    responseCode = result.first
                    errorBody = result.second
                }
            }

            if (responseCode !in 200..299) {
                Log.e("NotificationSender", "Ошибка $responseCode: $errorBody")

                try {
                    val errorJson = JSONObject(errorBody ?: "")
                    val detailArray = errorJson.getJSONArray("detail")
                    if (detailArray.length() > 0) {
                        val firstError = detailArray.getJSONObject(0)
                        val msg = firstError.getString("msg")
                        val type = firstError.getString("type")
                        val loc = firstError.getJSONArray("loc").join(", ")

                        Log.e("NotificationSender", "Ошибка: $msg (type: $type, loc: $loc)")
                    }
                } catch (e: Exception) {
                    Log.e("NotificationSender", "Не удалось распарсить тело ошибки", e)
                }
            }

            Log.d("NotificationSender", "Результат отправки: $responseCode")
            return@withContext responseCode in 200..299

        } catch (e: Exception) {
            Log.e("NotificationSender", "Ошибка отправки уведомления", e)
            return@withContext false
        }
    }
}
