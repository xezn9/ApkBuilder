package com.chatapp.messenger

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** يبني إشعار الرسالة (اسم المرسل + صورته + النص). تستخدمه خدمة الاستماع بالخلفية. */
object ChatNotifier {

    private class Info(val name: String, val avatar: Bitmap?, val at: Long)

    private val cache = ConcurrentHashMap<String, Info>()
    private const val TTL = 10 * 60 * 1000L

    private suspend fun info(from: String): Info {
        cache[from]?.let { if (System.currentTimeMillis() - it.at < TTL) return it }
        var name = ""
        var avatar: Bitmap? = null
        var got = false
        try {
            val doc = withTimeoutOrNull(8000) {
                FirebaseFirestore.getInstance().collection("profiles").document(from).get().await()
            }
            if (doc != null) {
                got = true
                name = doc.getString("name").orEmpty()
                val b64 = doc.getString("avatar").orEmpty()
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    avatar = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { circle(it, 160) }
                }
            }
            if (name.isBlank()) {
                val u = withTimeoutOrNull(5000) {
                    FirebaseFirestore.getInstance().collection("users").document(from).get().await()
                }
                name = u?.getString("name").orEmpty()
            }
        } catch (_: Exception) {
        }
        val i = Info(name.ifBlank { "مستخدم" }, avatar, System.currentTimeMillis())
        if (got) cache[from] = i          // ما نخزن نتيجة فاشلة
        return i
    }

    suspend fun show(ctx: Context, from: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val info = info(from)
        val sender = Person.Builder().setName(info.name).setKey(from)
            .apply { info.avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val id = from.hashCode()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // إذا فيه إشعار قديم من نفس الشخص نضيف عليه (رسائل متعددة بإشعار واحد)
        val style = nm.activeNotifications.firstOrNull { it.id == id }?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName("أنا").build())
        style.addMessage(text, System.currentTimeMillis(), sender)

        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChatMessagingService.EXTRA_OPEN_CHAT, from)
        }
        val pi = PendingIntent.getActivity(
            ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, ChatApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(info.name)
            .setContentText(text)
            .setLargeIcon(info.avatar)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    private fun circle(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val side = minOf(src.width, src.height)
        val r = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        c.drawBitmap(src, r, Rect(0, 0, size, size), p)
        return out
    }
}
