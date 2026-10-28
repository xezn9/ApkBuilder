package com.chatapp.messenger

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * خدمة بالخلفية (Foreground Service) تسمع لمحادثات المستخدم بـ Firestore
 * وتطلع إشعار لما توصل رسالة جديدة والتطبيق مو مفتوح. بدون سيرفر وبدون Cloud Functions.
 *
 * - تشتغل فقط وانت مسجل دخول (تتوقف تلقائياً عند الخروج).
 * - لكل محادثة نحفظ "مؤشر" (وقت آخر رسالة معالجة) حتى ما تتكرر الإشعارات،
 *   والرسائل الي وصلت والخدمة واقفة تطلع لها إشعار لما ترجع.
 */
class MessageListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var prefs: SharedPreferences
    private val locks = ConcurrentHashMap<String, Mutex>()

    private var reg: ListenerRegistration? = null
    private var myUid: String? = null
    private var initialDone = false

    private val authListener = FirebaseAuth.AuthStateListener { a ->
        val u = a.currentUser
        if (u == null) {
            reg?.remove()
            reg = null
            myUid = null
            stopSelf()
        } else if (u.uid != myUid) {
            startListening()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("notif_cursor", Context.MODE_PRIVATE)
        Repo.auth.addAuthStateListener(authListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (Repo.auth.currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Repo.auth.removeAuthStateListener(authListener)
        reg?.remove()
        reg = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ إشعار الخدمة الدائم
    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, ChatApp.CHANNEL_LISTENER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("متصل لاستقبال الرسائل")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ------------------------------------------------------------ الاستماع
    private fun startListening() {
        val me = Repo.auth.currentUser?.uid ?: return
        if (reg != null && myUid == me) return
        reg?.remove()
        myUid = me
        initialDone = false

        reg = db.collection("chats").whereArrayContains("participants", me)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    // خطأ نهائي (مثلاً صلاحيات): نعيد المحاولة بعد دقيقة
                    reg?.remove()
                    reg = null
                    scope.launch {
                        delay(60_000)
                        if (Repo.auth.currentUser?.uid == me) startListening()
                    }
                    return@addSnapshotListener
                }
                val first = !initialDone
                initialDone = true
                for (ch in snap.documentChanges) {
                    if (ch.type == DocumentChange.Type.REMOVED) continue
                    val d = ch.document
                    // الكتابة الي توها انرسلت وقتها لسا null: ننتظر التأكيد من السيرفر
                    val lastAt = d.getTimestamp("lastAt")?.nanos() ?: continue
                    val has = prefs.contains(d.id)
                    if (!has && first) {
                        // أول مرة نشوف المحادثة على هذا الجهاز: ما نطلع إشعار للتاريخ القديم
                        prefs.edit().putLong(d.id, lastAt).apply()
                        continue
                    }
                    if (lastAt <= prefs.getLong(d.id, 0L)) continue   // مثلاً تصفير عداد القراءة
                    trigger(d.id, lastAt, me, if (first) 0L else 1200L)
                }
            }
    }

    /** وثيقة المحادثة تنكتب قبل الرسالة بلحظات، فننتظر شوي قبل ما نجلب الرسائل */
    private fun trigger(chatId: String, lastAt: Long, me: String, delayMs: Long) {
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            locks.getOrPut(chatId) { Mutex() }.withLock {
                try {
                    process(chatId, lastAt, me, 0)
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun process(chatId: String, lastAt: Long, me: String, attempt: Int) {
        var cursor = prefs.getLong(chatId, 0L)
        if (lastAt <= cursor) return

        val snap = db.collection("chats").document(chatId).collection("messages")
            .whereGreaterThan("createdAt", fromNanos(cursor))
            .orderBy("createdAt")
            .limit(10)
            .get().await()

        if (snap.isEmpty) {
            if (attempt < 3) {
                delay(1500L * (attempt + 1))
                process(chatId, lastAt, me, attempt + 1)
            }
            return
        }
        for (doc in snap.documents) {
            val t = doc.getTimestamp("createdAt")?.nanos() ?: continue
            cursor = t
            prefs.edit().putLong(chatId, t).apply()

            val from = doc.getString("from") ?: continue
            if (from == me || doc.getBoolean("deleted") == true) continue
            val text = doc.getString("text") ?: continue
            if (AppState.foreground) continue            // التطبيق مفتوح: الرسالة تبين بالمحادثة
            ChatNotifier.show(applicationContext, from, text)
        }
    }

    // الوقت بالنانو ثانية (Firestore يخزن بدقة أعلى من الميلي ثانية، فلو قصيناه تتكرر الرسائل)
    private fun Timestamp.nanos() = seconds * 1_000_000_000L + nanoseconds
    private fun fromNanos(n: Long) = Timestamp(n / 1_000_000_000L, (n % 1_000_000_000L).toInt())

    companion object {
        private const val NOTIF_ID = 7001

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, MessageListenerService::class.java))
            } catch (_: Exception) {
            }
        }

        /** مرة وحدة: يطلب من أندرويد استثناء التطبيق من توفير البطارية (حتى ما تنقتل الخدمة) */
        fun askBatteryExemptionOnce(ctx: Context) {
            try {
                val p = ctx.getSharedPreferences("notif_cursor", Context.MODE_PRIVATE)
                if (p.getBoolean("battery_asked", false)) return
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) return
                p.edit().putBoolean("battery_asked", true).apply()
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                )
            } catch (_: Exception) {
            }
        }
    }
}
