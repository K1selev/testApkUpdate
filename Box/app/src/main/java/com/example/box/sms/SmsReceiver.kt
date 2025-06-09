package com.example.box.sms

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
import java.util.regex.PatternSyntaxException

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.messageBody
                val sender = sms.originatingAddress ?: ""

                // Проверяем соответствие шаблонам
                val matchedPattern = PatternRepository.smsPatterns.any { pattern ->
                    try {
                        // Проверяем номер телефона, если он указан в шаблоне
                        val phoneMatches = pattern.phone.isEmpty() || pattern.phone == sender
                        // Проверяем текст сообщения
                        val textMatches = pattern.safeRegex?.containsMatchIn(messageBody) == true
                        
                        phoneMatches && textMatches
                    } catch (e: PatternSyntaxException) {
                        Log.e("SmsReceiver", "Некорректный Regex в smsPatterns: ${pattern.pattern}. Ошибка: ${e.message}")
                        false
                    }
                }

                if (matchedPattern) {
                    CoroutineScope(Dispatchers.IO).launch {
                        LogDatabase.getDatabase(context).logDao().insert(
                            LogEntity(message = "SMS [$sender]: $messageBody")
                        )
                    }
                    Log.d("SmsReceiver", "ACCEPTED: SMS from $sender: $messageBody")
                } else {
                    Log.d("SmsReceiver", "IGNORED: SMS from $sender: $messageBody")
                }
            }
        }
    }
}
