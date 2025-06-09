package com.example.box

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FileHelper {
    fun appendJson(context: Context, fileName: String, data: JSONObject) {
        val file = File(context.filesDir, fileName)
        val jsonArray = if (file.exists()) JSONArray(file.readText()) else JSONArray()
        jsonArray.put(data)
        file.writeText(jsonArray.toString())
    }

    fun readJsonArray(context: Context, fileName: String): JSONArray {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) JSONArray(file.readText()) else JSONArray()
    }
}
