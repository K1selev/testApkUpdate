package com.example.box

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class LinkHandlerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri?.path == "/open") {
            val userId = uri.getQueryParameter("user_id")
            val teamId = uri.getQueryParameter("team_id")
            val refreshToken = uri.getQueryParameter("refresh_token")

            Log.d("DeepLink", "userId=$userId, teamId=$teamId, token=$refreshToken")
            // Перенаправление или логика
        }

        finish() // Закрой, если UI не нужен
    }
}
