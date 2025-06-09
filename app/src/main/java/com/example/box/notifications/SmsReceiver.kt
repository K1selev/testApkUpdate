package com.example.box.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.box.logs.LogDatabase
import com.example.box.logs.LogEntity
import com.example.box.patterns.PatternRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages?.forEach { sms ->
            processSms(context, sms.originatingAddress ?: "Unknown", sms.messageBody)
        }
    }

    private fun processSms(context: Context, sender: String, messageBody: String) {
        val isMatched = PatternRepository.smsPatterns.any { pattern ->
            pattern.type == "SMS" && 
            (pattern.phone.isEmpty() || pattern.phone == sender) && 
            pattern.safeRegex?.containsMatchIn(messageBody) == true
        }

        val logMessage = "SMS [$sender]: $messageBody"
        
        if (isMatched) {
            Log.d(TAG, "ACCEPTED: $logMessage")
            saveToDatabase(context, logMessage)
        } else {
            Log.d(TAG, "IGNORED: $logMessage")
        }
    }

    private fun saveToDatabase(context: Context, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            LogDatabase.getInstance(context).logDao().insert(
                LogEntity(message = message)
            )
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
} 