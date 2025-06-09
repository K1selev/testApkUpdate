package com.example.box

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.box.logs.LogActivity
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.provider.Settings


class QrScannerActivity : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private val CAMERA_REQUEST_CODE = 101
    private var alreadyHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        val btnClose = findViewById<ImageButton>(R.id.btnCloseScanner)

        btnClose.setOnClickListener {
            finish()
        }

        barcodeView = findViewById(R.id.barcode_scanner)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        } else {
            startScanning()
        }
    }

    private fun startScanning() {
        barcodeView.decodeContinuous(callback)
        barcodeView.resume()
        barcodeView.statusView.visibility = View.GONE
    }

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            if (alreadyHandled) return
            result?.let {
                val qrUrl = it.text
                if (qrUrl.startsWith("https://boxexchange/open")) {
                    alreadyHandled = true
                    barcodeView.pause()
                    handleQr(qrUrl)
                } else {
                    Toast.makeText(this@QrScannerActivity, "Неверный QR-код", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
    }

    private fun handleQr(qrUrl: String) {
        val uri = Uri.parse(qrUrl)

        val userId = uri.getQueryParameter("user_id")
        val teamId = uri.getQueryParameter("team_id")
        val refreshToken = uri.getQueryParameter("refresh_token")

        if (userId != null && teamId != null && refreshToken != null) {
            Toast.makeText(this, "Обработка...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch(Dispatchers.IO) {
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val existingDeviceId = prefs.getString("device_id", null)

                if (existingDeviceId != null) {
                    Log.i("QR", "Устройство уже зарегистрировано")
                    goToLogs()
                    return@launch
                }

                val accessToken = getAccessToken(refreshToken)
                if (accessToken != null) {
                    val success = registerDevice(accessToken, userId, teamId)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(this@QrScannerActivity, "Устройство зарегистрировано", Toast.LENGTH_LONG).show()
                            goToLogs()
                        } else {
                            alreadyHandled = false
                            barcodeView.resume()
                            Toast.makeText(this@QrScannerActivity, "Ошибка регистрации", Toast.LENGTH_LONG).show()
                        }
                    }
//                    val response = apiService.registerDevice("Bearer $accessToken", userId, teamId)
//                    if (response.isSuccessful) {
//                        true
//                    } else {
//                        Log.e("DeviceRegister", "Ошибка регистрации: ${response.code()} ${response.message()}")
//                        Log.e("DeviceRegister", "Тело ошибки: ${response.errorBody()?.string()}")
//                        false
//                    }

                } else {
                    withContext(Dispatchers.Main) {
                        alreadyHandled = false
                        barcodeView.resume()
                        Toast.makeText(this@QrScannerActivity, "Не удалось получить токен", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(this@QrScannerActivity, "Неверный QR-код", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToLogs() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private suspend fun getAccessToken(refreshToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api-box.pspware.dev/authentication-service/authentication/refresh")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $refreshToken")
            connection.doOutput = true

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val accessToken = json.getString("access_token")

                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putString("access_token", accessToken)
                    .putString("refresh_token", refreshToken)
                    .apply()

                return@withContext accessToken
            } else {
                Log.e("AccessToken", "Ошибка получения access_token: $code")
            }
        } catch (e: Exception) {
            Log.e("AccessToken", "Ошибка при получении токена", e)
        }
        return@withContext null
    }

    private suspend fun registerDevice(accessToken: String, userId: String, teamId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api-box.pspware.dev/device-service/devices"
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

        val deviceModel = Build.MODEL ?: "Unknown"
        val deviceName = Build.DEVICE ?: deviceModel
        val osVersion = "Android ${Build.VERSION.RELEASE}"

        val body = """
        {
            "owner_user_id": "$userId",
            "user_id": "$userId",
            "team_id": "$teamId",
            "platform": "android",
            "device_name": "$deviceName",
            "name": "$deviceName",
            "model": "$deviceModel",
            "os": "$osVersion",
            "device_id": "$androidId"
        }
        """.trimIndent()

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("accept", "application/json")
            connection.doOutput = true

            connection.outputStream.use { it.write(body.toByteArray()) }

            val code = connection.responseCode
            if (code in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val deviceId = json.getString("id")

                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putString("device_id", deviceId)
                    .apply()

                return@withContext true
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("RegisterDevice", "Ошибка: код $code, тело: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("RegisterDevice", "Исключение при регистрации", e)
        }
        return@withContext false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startScanning()
        } else {
            Toast.makeText(this, "Камера не разрешена", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }
}
