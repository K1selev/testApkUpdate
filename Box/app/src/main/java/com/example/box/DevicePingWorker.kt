package com.example.box

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DevicePingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d("DevicePingWorker", "doWork запущен")

        return if (PingHelper.ping(applicationContext)) {
            Log.d("DevicePingWorker", "Ping успешен")
            Result.success()
        } else {
            Log.e("DevicePingWorker", "Ping не удался")
            Result.retry()
        }
    }
}
