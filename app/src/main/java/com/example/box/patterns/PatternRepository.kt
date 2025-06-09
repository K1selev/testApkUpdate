package com.example.box.patterns

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object PatternRepository {

    data class Pattern(
        val id: String,
        val name: String,
        val pattern: String,
        val `package`: String,
        val phone: String = "",
        val type: String = "PUSH",  // Добавляем поле type с значением по умолчанию "PUSH"
        val safeRegex: Regex? = null
    ) {
        fun matches(sourcePackage: String, text: String): Boolean {
            val packageMatches = this.`package`.isEmpty() || sourcePackage == this.`package`
            val textMatches = safeRegex?.containsMatchIn(text) == true
            return packageMatches && textMatches
        }
    }

    var pushPatterns: List<Pattern> = emptyList()
    var smsPatterns: List<Pattern> = emptyList()

    fun loadPatterns(context: Context) {
        try {
            val rawJson = loadRawPatternsJson(context) ?: return
            val json = JSONObject(rawJson)

            pushPatterns = parsePatterns(json.optJSONArray("push_patterns"))
            smsPatterns = parsePatterns(json.optJSONArray("sms_patterns"))

            Log.d("PatternRepository", "Шаблоны загружены из локального файла")
        } catch (e: Exception) {
            Log.e("PatternRepository", "Ошибка загрузки шаблонов: ${e.message}", e)
        }
    }

    fun loadFromUrl(context: Context, url: String, onComplete: (updated: Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }

                    val previousJson = loadRawPatternsJson(context)
                    if (previousJson == json) {
                        Log.d("PatternRepository", "Шаблоны не изменились")
                        withContext(Dispatchers.Main) {
                            onComplete(false)
                        }
                    } else {
                        saveRawPatternsJson(context, json)
                        loadPatterns(context)
                        Log.d("PatternRepository", "Шаблоны обновлены")
                        withContext(Dispatchers.Main) {
                            onComplete(true)
                        }
                    }
                } else {
                    Log.e("PatternRepository", "Ошибка загрузки: код ${connection.responseCode}")
                    withContext(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("PatternRepository", "Ошибка загрузки шаблонов: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    suspend fun loadFromApi(context: Context, onComplete: (Boolean) -> Unit) {
        withContext(Dispatchers.IO) {
            val url = URL("https://api-box.pspware.dev/device-service/patterns/")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("accept", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }

            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val rawArray = JSONArray(responseText)

                    val push = JSONArray()
                    val sms = JSONArray()

                    for (i in 0 until rawArray.length()) {
                        val obj = rawArray.getJSONObject(i)
                        when (obj.optString("type").uppercase()) {
                            "PUSH" -> push.put(obj)
                            "SMS" -> sms.put(obj)
                        }
                    }

                    val unifiedJson = JSONObject().apply {
                        put("push_patterns", push)
                        put("sms_patterns", sms)
                    }

                    val newJson = unifiedJson.toString()
                    val previousJson = loadRawPatternsJson(context)

                    if (previousJson != newJson) {
                        saveRawPatternsJson(context, newJson)
                        loadPatterns(context)
                        withContext(Dispatchers.Main) { onComplete(true) }
                    } else {
                        withContext(Dispatchers.Main) { onComplete(false) }
                    }
                } else {
                    Log.e("PatternRepository", "Ошибка запроса: код ${connection.responseCode}")
                    withContext(Dispatchers.Main) { onComplete(false) }
                }
            } catch (e: Exception) {
                Log.e("PatternRepository", "Ошибка загрузки шаблонов: ${e.message}", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parsePatterns(array: JSONArray?): List<Pattern> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let {
                val patternStr = it.optString("pattern")

                val safeRegex = try {
                    Regex(patternStr)
                } catch (e: Exception) {
                    Log.e("PatternRepository", "Ошибка компиляции Regex для pattern='$patternStr': ${e.message}")
                    null
                }

                Pattern(
                    id = it.optString("id"),
                    name = it.optString("name"),
                    pattern = patternStr,
                    `package` = it.optString("source", ""),
                    phone = it.optString("phone", ""),
                    type = it.optString("type", "PUSH"),  // Парсим тип шаблона
                    safeRegex = safeRegex
                )
            }
        }.sortedBy { it.id }
    }

    private fun loadRawPatternsJson(context: Context): String? {
        return try {
            val file = File(context.filesDir, "patterns.json")
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveRawPatternsJson(context: Context, json: String) {
        try {
            val file = File(context.filesDir, "patterns.json")
            file.writeText(json)
        } catch (e: Exception) {
            Log.e("PatternRepository", "Ошибка сохранения шаблонов: ${e.message}", e)
        }
    }

    fun fetchAndUpdatePatterns(context: Context, url: String): Boolean {
        return runBlocking {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@runBlocking false
                }

                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val previousJson = loadRawPatternsJson(context)

                if (json == previousJson) {
                    false
                } else {
                    saveRawPatternsJson(context, json)
                    loadPatterns(context)
                    true
                }
            } catch (e: Exception) {
                Log.e("PatternRepository", "Ошибка обновления шаблонов: ${e.message}", e)
                false
            }
        }
    }
}
