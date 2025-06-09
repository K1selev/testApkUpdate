package com.example.box.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.box.logs.LogDatabase
import com.example.box.logs.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: return
        CoroutineScope(Dispatchers.IO).launch {
            LogDatabase.getDatabase(context).logDao().insert(LogEntity(message = "PUSH: $message"))
        }
    }
}
