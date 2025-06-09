package com.example.box

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.example.box.logs.LogActivity
import com.example.box.logs.LogAdapter
import com.example.box.logs.LogDatabase
import com.example.box.notifications.MyNotificationListenerService
import com.example.box.notifications.NotificationHelper
import com.example.box.patterns.PatternRepository
import com.example.box.patterns.PatternUpdateWorker
//import com.google.zxing.client.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import com.example.box.BuildConfig
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_UNKNOWN_APP_SOURCES = 1234
    }

    private lateinit var adapter: LogAdapter
    private lateinit var pingIcon: ImageView
    private var pingError = false
    val currentVersionCode = BuildConfig.VERSION_CODE

    private val permissions = mutableListOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.entries.filter { !it.value }
            if (denied.isNotEmpty()) {
                Toast.makeText(this, "Некоторые разрешения были отклонены", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", null)

        requestPermissions()
        checkNotificationAccess()

        renderUIBasedOnToken()

        lifecycleScope.launch {
//            PatternRepository.loadFromApi(this@MainActivity) { updated ->
//                val message = if (updated) "Шаблоны обновлены" else "Шаблоны не изменились"
//                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
//            }
        }


        schedulePatternAutoUpdate()
        schedulePingWorker()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_UNKNOWN_APP_SOURCES) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    // Разрешение получено — запускай установку APK
                } else {
                    Toast.makeText(this, "Разрешение на установку приложений не предоставлено", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startBlinking() {
        if (pingError) return

        val blink = AlphaAnimation(1.0f, 0.2f).apply {
            duration = 500
            repeatMode = AlphaAnimation.REVERSE
            repeatCount = AlphaAnimation.INFINITE
        }
        pingIcon.startAnimation(blink)
    }

    suspend fun checkForUpdate(): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val url = URL("https://raw.githubusercontent.com/K1selev/testApkUpdate/main/version.json")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val latestVersionCode = json.getInt("versionCode")
            val apkUrl = json.getString("apkUrl")
            Pair(latestVersionCode > BuildConfig.VERSION_CODE, apkUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, null)
        } finally {
            connection.disconnect()
        }
    }


    fun downloadAndInstallApk(apkUrl: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
        request.setTitle("Загрузка обновления")
        request.setDescription("Пожалуйста, подождите...")
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "update.apk")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "update.apk")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                try {
                    context.startActivity(installIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка при открытии файла: ${e.message}", Toast.LENGTH_LONG).show()
                }

                unregisterReceiver(this)
            }
        }

        registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }


    private fun renderUIBasedOnToken() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", null)

        if (refreshToken.isNullOrEmpty()) {
            supportActionBar?.hide()
            setContentView(R.layout.activity_main_qr)
            findViewById<Button>(R.id.btnScanQr).setOnClickListener {
                startActivity(Intent(this, QrScannerActivity::class.java))
            }
        } else {
            supportActionBar?.hide()
            setContentView(R.layout.activity_main_logs)

            val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewLogs)
            adapter = LogAdapter()
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter

            lifecycleScope.launch {
                try {
                    LogDatabase.getInstance(applicationContext)
                        .logDao()
                        .getAllLogs()
                        .collectLatest { logs ->
                            adapter.submitList(logs)
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val btnUpdateApp = findViewById<Button>(R.id.btnUpdateApp)
            lifecycleScope.launch {
                val (isUpdateAvailable, apkUrl) = checkForUpdate()
                if (isUpdateAvailable && apkUrl != null) {
//                    btnUpdateApp.visibility = View.VISIBLE
                    btnUpdateApp.setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val canInstall = packageManager.canRequestPackageInstalls()
                            if (!canInstall) {
                                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                intent.data = Uri.parse("package:$packageName")
                                startActivityForResult(intent, REQUEST_CODE_UNKNOWN_APP_SOURCES)
                            } else {
                                downloadAndInstallApk(apkUrl)
                            }
                        } else {
                            downloadAndInstallApk(apkUrl)
                            // Для версий ниже Android 8 разрешение не нужно
                        }
                    }
                }
            }

            val btnPing = findViewById<LinearLayout>(R.id.btnPing)
            pingIcon = findViewById(R.id.pingIcon)
            startBlinking()

            btnPing.setOnClickListener {
                lifecycleScope.launch {
                    val success = PingHelper.ping(this@MainActivity) {
                        Toast.makeText(this@MainActivity, "Пинг успешен", Toast.LENGTH_SHORT).show()
                    }

                    if (success) {
                        pingError = false
                        pingIcon.setImageResource(R.drawable.ic_circle_red)
                        pingIcon.setColorFilter(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light)
                        )
                        startBlinking()
                    } else {
                        pingError = true
                        pingIcon.setImageResource(R.drawable.ic_circle_gray)
                        pingIcon.clearAnimation()
                        pingIcon.setColorFilter(
                            ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray)
                        )
                        Toast.makeText(this@MainActivity, "Ошибка пинга", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val btnPatternsUpdate = findViewById<ImageButton>(R.id.btnUpdatePatterns)
            btnPatternsUpdate?.setOnClickListener {
                lifecycleScope.launch {
                    PatternRepository.loadFromApi(this@MainActivity) { updated ->
                        val message = if (updated) {
                            "Шаблоны обновлены"
                        } else {
                            "Шаблоны не изменились"
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val btnLogout = findViewById<ImageButton>(R.id.btnLogout)
            btnLogout.setOnClickListener {
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply() // удаляет все prefs, включая токены

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

        }
    }


    private fun requestPermissions() {
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun schedulePatternAutoUpdate() {
        val workRequest = PeriodicWorkRequestBuilder<PatternUpdateWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PatternAutoUpdate",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun schedulePingWorker() {
        val workRequest = PeriodicWorkRequestBuilder<DevicePingWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DevicePingWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun checkNotificationAccess() {
        if (!isNotificationServiceEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("Доступ к уведомлениям")
                .setMessage("Для чтения уведомлений необходимо включить доступ вручную.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return !TextUtils.isEmpty(enabledListeners) &&
                enabledListeners.contains(ComponentName(pkgName, MyNotificationListenerService::class.java.name).flattenToString())
    }
}
