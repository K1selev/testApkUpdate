//package com.example.box.logs
//
////import android.os.Bundle
////import androidx.appcompat.app.AppCompatActivity
////import androidx.recyclerview.widget.LinearLayoutManager
////import androidx.recyclerview.widget.RecyclerView
//
////class LogActivity : AppCompatActivity() {
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        setContentView(R.layout.activity_log)
////
////        val logs = mutableListOf<String>()
////        logs += FileHelper.readJsonArray(this, "sms_log.json").let {
////            (0 until it.length()).map { i -> "SMS: " + it.getJSONObject(i).getString("message") }
////        }
////        logs += FileHelper.readJsonArray(this, "push_log.json").let {
////            (0 until it.length()).map { i -> "PUSH: " + it.getJSONObject(i).getString("message") }
////        }
////
////        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
////        recyclerView.layoutManager = LinearLayoutManager(this)
////        recyclerView.adapter = LogAdapter(logs)
////    }
////}
//
//
//
////import android.os.Bundle
////import androidx.appcompat.app.AppCompatActivity
////import androidx.lifecycle.lifecycleScope
////import androidx.recyclerview.widget.LinearLayoutManager
////import androidx.recyclerview.widget.RecyclerView
////import kotlinx.coroutines.Dispatchers
////import kotlinx.coroutines.launch
////import kotlinx.coroutines.withContext
////
////class LogActivity : AppCompatActivity() {
////
////    private lateinit var recyclerView: RecyclerView
////    private lateinit var adapter: LogAdapter
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        setContentView(R.layout.activity_log)
////
////        recyclerView = findViewById(R.id.recyclerView)
////        adapter = LogAdapter()
////        recyclerView.layoutManager = LinearLayoutManager(this)
////        recyclerView.adapter = adapter
////
////        lifecycleScope.launch {
////            val logs = withContext(Dispatchers.IO) {
////                LogDatabase.getInstance(this@LogActivity).logDao().getAllLogs()
////            }
////            adapter.submitList(logs)
////        }
////    }
////}
//
//

package com.example.box.logs

import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.box.PingHelper
import com.example.box.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogActivity : AppCompatActivity() {
    private lateinit var adapter: LogAdapter
    private lateinit var pingIcon: ImageView
    private var pingError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        // RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewLogs)
        adapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Обновление логов
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

        // Кнопка Ping
        val btnPing = findViewById<LinearLayout>(R.id.btnPing)
        pingIcon = findViewById(R.id.pingIcon)

        // Запуск анимации моргания
        startBlinking()


        btnPing.setOnClickListener {
            lifecycleScope.launch {
                val success = PingHelper.ping(this@LogActivity) {
                    Toast.makeText(this@LogActivity, "Пинг успешен", Toast.LENGTH_SHORT).show()
                }

                if (success) {
                    pingError = false
                    pingIcon.setImageResource(R.drawable.ic_circle_red)
                    pingIcon.setColorFilter(ContextCompat.getColor(this@LogActivity, android.R.color.holo_red_light))
                    startBlinking()
                } else {
                    pingError = true
                    pingIcon.setImageResource(R.drawable.ic_circle_gray)
                    pingIcon.clearAnimation()
                    pingIcon.setColorFilter(ContextCompat.getColor(this@LogActivity, android.R.color.darker_gray))
                    Toast.makeText(this@LogActivity, "Ошибка пинга", Toast.LENGTH_SHORT).show()
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
}
