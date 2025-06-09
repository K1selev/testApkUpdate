package com.example.box.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.app.Notification
import com.example.box.patterns.PatternRepository

//
//class MyNotificationListenerService : NotificationListenerService() {
//
//    override fun onNotificationPosted(sbn: StatusBarNotification) {
//        val extras = sbn.notification.extras
//        val packageName = sbn.packageName
//
//        // Получаем заголовок уведомления
//        val title = extras.getCharSequence("android.title")?.toString() ?: "Нет заголовка"
//
//        // Получаем текст уведомления с учётом различных вариантов (bigText и summaryText)
//        val text = extras.getCharSequence("android.text")
//            ?: extras.getCharSequence("android.bigText")
//            ?: extras.getCharSequence("android.summaryText")
//            ?: "Нет текста"
//
//        val message = "[$packageName] $title: $text"
//        Log.d("NotifListener", message)
//
//        // Пример: сохраняем в базу данных (в фоне)
//        CoroutineScope(Dispatchers.IO).launch {
//            LogDatabase.getInstance(applicationContext).logDao().insert(
//                LogEntity(
//                    id = 0,
//                    message = message,
//                    timestamp = System.currentTimeMillis()
//                )
//            )
//        }
//    }
//
//    override fun onNotificationRemoved(sbn: StatusBarNotification) {
//        Log.d("NotifListener", "Уведомление удалено: ${sbn.packageName}")
//    }
//}

// Data class для паттернов из JSON
data class PatternItem(
    val id: Int,
    val name: String,
    val pattern: String,
    val packageName: String? = null,  // для push_patterns
    val phone: String? = null          // для sms_patterns
)


class MyNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        PatternRepository.loadPatterns(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val fullText = "$title $text"

        val matchedPushPattern = PatternRepository.pushPatterns.any { pattern ->
            pattern.`package` == packageName &&
                    Regex(pattern.pattern).containsMatchIn(fullText)
        }

        val matchedSmsPattern = PatternRepository.smsPatterns.any { pattern ->
            Regex(pattern.pattern).containsMatchIn(fullText)
        }

//        if (matchedPushPattern || matchedSmsPattern) {
//            // log or store notification
//            Log.d("FilteredNotification", "Accepted: [$packageName] $fullText")
//            // save to DB here (launch in coroutine!)
//        } else {
//            Log.d("FilteredNotification", "Ignored: [$packageName] $fullText")
//        }

        val isMatch = matchedPushPattern || matchedSmsPattern

        Log.d("FilteredNotification", "Notification from $packageName: \"$fullText\" matched? $isMatch")

        if (isMatch) {
            // Логируем, что уведомление принято
            Log.d("FilteredNotification", "Accepted: [$packageName] $fullText")
            // TODO: запуск корутины для записи в базу
        } else {
            // Логируем, что уведомление проигнорировано
            Log.d("FilteredNotification", "Ignored: [$packageName] $fullText")
        }
    }
}

