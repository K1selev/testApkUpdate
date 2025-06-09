package com.example.box.patterns

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.box.notifications.NotificationHelper

class PatternUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {


    override fun doWork(): Result {
        return try {
            val updated = PatternRepository.fetchAndUpdatePatterns(
                applicationContext,
                "https://example.com/patterns.json"
            )

            Handler(Looper.getMainLooper()).post {
                if (updated) {
//                    NotificationHelper.showNotification(
//                        applicationContext,
//                        "Обновление шаблонов",
//                        "Шаблоны успешно обновлены"
//                    )
                    Log.d("PatternUpdateWorker", "Шаблоны обновлены")
//                    Toast.makeText(applicationContext, "Шаблоны автоматически обновлены", Toast.LENGTH_SHORT).show()
                } else {
//                    NotificationHelper.showNotification(
//                        applicationContext,
//                        "Обновление шаблонов",
//                        "Шаблоны не изменились"
//                    )
                    Log.d("PatternUpdateWorker", "Обновление шаблонов не требуется")
//                    Toast.makeText(applicationContext, "Обновление шаблонов не требуется", Toast.LENGTH_SHORT).show()
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

}
