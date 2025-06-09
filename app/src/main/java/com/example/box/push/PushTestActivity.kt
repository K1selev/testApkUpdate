//package com.example.box.push
//
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.content.Context
//import android.content.Intent
//import android.os.Build
//import android.os.Bundle
//import android.widget.Button
//import android.widget.EditText
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.NotificationCompat
//import androidx.core.app.NotificationManagerCompat
//import com.example.box.R
//import com.example.box.notifications.NotificationReceiver
//
//class PushTestActivity : AppCompatActivity() {
//    private val channelId = "push_test_channel"
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_push_test)
//
//        createNotificationChannel()
//
//        val btnSend = findViewById<Button>(R.id.btnSendPush)
//        val edtMessage = findViewById<EditText>(R.id.edtPushMessage)
//
//        btnSend.setOnClickListener {
//            val message = edtMessage.text.toString()
//            val intent = Intent(this, NotificationReceiver::class.java).apply {
//                putExtra("message", message)
//            }
//            sendBroadcast(intent)
//
//            val notification = NotificationCompat.Builder(this, channelId)
//                .setSmallIcon(android.R.drawable.ic_dialog_info)
//                .setContentTitle("Test Push")
//                .setContentText(message)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .build()
//
//            NotificationManagerCompat.from(this).notify(1, notification)
//        }
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val name = "Push Test Channel"
//            val descriptionText = "Channel for testing push notifications"
//            val importance = NotificationManager.IMPORTANCE_HIGH
//            val channel = NotificationChannel(channelId, name, importance).apply {
//                description = descriptionText
//            }
//            val notificationManager: NotificationManager =
//                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            notificationManager.createNotificationChannel(channel)
//        }
//    }
//}