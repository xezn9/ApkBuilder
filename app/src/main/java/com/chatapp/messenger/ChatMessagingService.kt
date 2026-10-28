package com.chatapp.messenger

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

/**
 * يستقبل إشعارات الرسائل من Cloud Function (functions/index.js).
 * - بدون حساب مسجل دخول: ما يظهر شي.
 * - التطبيق مفتوح: ما يظهر شي (الرسالة تبين بالمحادثة).
 * - الإشعار فيه اسم المرسل + صورته + نص الرسالة.
 */
class ChatMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // إذا ما فيه حساب مسجل ما يحفظ شي
        Repo.saveFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        if (d["type"] != "msg") return
        val me = Repo.auth.currentUser?.uid ?: return       // بدون حساب = بدون إشعارات
        if (d["to"] != me) return                           // الإشعار لحساب ثاني
        if (AppState.foreground) return                     // التطبيق مفتوح
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val from = d["from"] ?: return
        val text = d["text"] ?: return
        var name = d["fromName"].orEmpty()

        // صورة المرسل من ملفه الشخصي (تنجلب هنا لأن حجمها أكبر من حد إشعارات FCM)
        var avatar: Bitmap? = null
        try {
            val doc = Tasks.await(
                FirebaseFirestore.getInstance().collection("profiles").document(from).get(),
                8, TimeUnit.SECONDS
            )
            doc.getString("name")?.takeIf { it.isNotBlank() }?.let { name = it }
            val b64 = doc.getString("avatar").orEmpty()
            if (b64.isNotBlank()) {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                avatar = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { circle(it, 160) }
            }
        } catch (_: Exception) {
        }
        if (name.isBlank()) name = "مستخدم"

        val sender = Person.Builder().setName(name).setKey(from)
            .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val id = from.hashCode()
        val nm = getSystemService(NotificationManager::class.java)
        // إذا فيه إشعار قديم من نفس الشخص نضيف عليه (رسائل متعددة بإشعار واحد)
        val style = nm.activeNotifications.firstOrNull { it.id == id }?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName("أنا").build())
        style.addMessage(text, System.currentTimeMillis(), sender)

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT, from)
        }
        val pi = PendingIntent.getActivity(
            this, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, ChatApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(text)
            .setLargeIcon(avatar)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(this).notify(id, n)
    }

    /** قص الصورة على شكل دائرة */
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

    companion object {
        const val EXTRA_OPEN_CHAT = "openChat"
    }
}
