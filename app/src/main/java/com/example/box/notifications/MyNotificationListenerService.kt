package com.example.box.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.box.logs.LogDatabase
import com.example.box.logs.LogEntity
import com.example.box.patterns.PatternRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.PatternSyntaxException

class MyNotificationListenerService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        PatternRepository.loadPatterns(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        val extras = sbn.notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullText = "$title $text"

        Log.d("MyNotificationListener", "Получено уведомление от $packageName: $fullText")
        Log.d("MyNotificationListener", "Доступные push шаблоны: ${PatternRepository.pushPatterns.map { "${it.name} (${it.`package`})" }}")

        // Проверка по push_patterns.json с обработкой исключений
        val matchedPushPattern = PatternRepository.pushPatterns.any { pattern ->
            Log.d("MyNotificationListener", "Проверяем шаблон: name=${pattern.name}, type=${pattern.type}, package=${pattern.`package`}, pattern=${pattern.pattern}")
            
            if (pattern.type != "PUSH") {
                Log.d("MyNotificationListener", "Неверный тип шаблона: ${pattern.type}")
                return@any false
            }
            
            if (pattern.`package` != packageName) {
                Log.d("MyNotificationListener", "Пакет не совпадает: ${pattern.`package`} != $packageName")
                return@any false
            }

            try {
                val matches = pattern.safeRegex?.containsMatchIn(fullText) == true
                Log.d("MyNotificationListener", "Проверка шаблона ${pattern.name}: $matches (текст: '$fullText', regex: '${pattern.pattern}')")
                matches
            } catch (e: PatternSyntaxException) {
                Log.e("MyNotificationListener", "Некорректный Regex в pushPatterns: ${pattern.pattern}. Ошибка: ${e.message}")
                false
            }
        }

        // Проверка по sms_patterns.json с обработкой исключений
        val matchedSmsPattern = PatternRepository.smsPatterns.any { pattern ->
            Log.d("MyNotificationListener", "Проверяем SMS шаблон: name=${pattern.name}, type=${pattern.type}, package=${pattern.`package`}, pattern=${pattern.pattern}")
            
            if (pattern.type != "SMS") {
                Log.d("MyNotificationListener", "Неверный тип шаблона: ${pattern.type}")
                return@any false
            }

            if (pattern.`package` != packageName) {
                Log.d("MyNotificationListener", "Пакет не совпадает: ${pattern.`package`} != $packageName")
                return@any false
            }

            try {
                val matches = pattern.safeRegex?.containsMatchIn(fullText) == true
                Log.d("MyNotificationListener", "Проверка SMS шаблона ${pattern.name}: $matches (текст: '$fullText', regex: '${pattern.pattern}')")
                matches
            } catch (e: PatternSyntaxException) {
                Log.e("MyNotificationListener", "Некорректный Regex в smsPatterns: ${pattern.pattern}. Ошибка: ${e.message}")
                false
            }
        }

        if (matchedPushPattern || matchedSmsPattern) {
            val logMessage = "[$packageName] $title: $text"
            Log.d("FilteredNotification", "ACCEPTED: $logMessage")

//            CoroutineScope(Dispatchers.IO).launch {
//                LogDatabase.getInstance(applicationContext).logDao().insert(
//                    LogEntity(message = logMessage)
//                )
//            }

            val matchedPattern = (PatternRepository.pushPatterns + PatternRepository.smsPatterns).find {
                it.matches(packageName, fullText)
            }

            CoroutineScope(Dispatchers.IO).launch {
                val dao = LogDatabase.getInstance(applicationContext).logDao()
                dao.insert(LogEntity(message = logMessage))

                matchedPattern?.let {
                    NotificationSender.sendNotificationToServer(
                        applicationContext,
                        patternId = it.id.toString(),
                        title = title,
                        text = text
                    )
                }
            }
        } else {
            Log.d("FilteredNotification", "IGNORED: [$packageName] $title: $text")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d("NotifListener", "Уведомление удалено: ${sbn.packageName}")
    }
}

