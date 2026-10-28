package com.chatapp.messenger

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** حالة التطبيق: هل هو مفتوح قدام المستخدم؟ (حتى ما نظهر إشعار وهو داخل التطبيق) */
object AppState {
    var foreground: Boolean by mutableStateOf(false)
}

class ChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_MESSAGES, "الرسائل", NotificationManager.IMPORTANCE_HIGH)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
            // قناة إشعار الخدمة الدائم (صامتة وبدون أيقونة بشريط الحالة)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LISTENER, "اتصال الرسائل", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_LISTENER = "listener"
    }
}
