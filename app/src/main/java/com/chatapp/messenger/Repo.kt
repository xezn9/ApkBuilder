package com.chatapp.messenger

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UserItem(val uid: String, val name: String, val email: String, val username: String = "")

/** عنصر بقائمة المحادثات: الطرف الثاني + آخر رسالة */
data class ChatItem(val uid: String, val lastText: String, val lastAt: Long, val unread: Int = 0)
data class Msg(
    val id: String,
    val from: String,
    val text: String,
    val createdAt: Long,
    /** الرسالة انحذفت (تظهر "رسالة مَحذوفة" بمكانها) */
    val deleted: Boolean = false,
    /** الرسالة انعدلت */
    val edited: Boolean = false,
    /** مثبتة بأعلى المحادثة (تظهر للطرفين) */
    val pinned: Boolean = false,
    val pinnedAt: Long = 0L,
    /** الرد: معرّف الرسالة الأصلية + نص مختصر + صاحبها */
    val replyId: String = "",
    val replyText: String = "",
    val replyFrom: String = "",
    /** رسالة صوتية: الصوت m4a بصيغة base64 (فارغ = رسالة نصية) + مدتها */
    val audio: String = "",
    val audioMs: Long = 0L
)

/**
 * معاينة بديلة بقائمة المحادثات عندي فقط: إذا حذفت (من عندي) آخر رسالة بالمحادثة.
 * تنطبق فقط إذا آخر نص بالمحادثة ما زال [forText] ولا وصلت رسالة جديدة بعد [at].
 */
data class HiddenPreview(val forText: String, val text: String, val at: Long)

/** حالة الاتصال كما هي مخزنة بالسيرفر (الحساب النهائي "متصل؟" يصير بالواجهة مع فحص التقادم) */
data class Presence(val online: Boolean = false, val lastSeen: Long = 0L)

/** حظر الحساب من الإدارة: until = وقت انتهاء الحظر بالميلي ثانية */
data class Ban(val until: Long = 0L, val days: Int = 0, val reason: String = "")

/** مدة القصة: 24 ساعة بالضبط من لحظة النشر */
const val STORY_MS = 24L * 60 * 60 * 1000

/** bg = رقم خلفية القصة (تدرج) من قائمة StoryBgs بالواجهة. القصص القديمة بدون الحقل تطلع 0 */
data class Story(val text: String, val color: Int, val image: String, val createdAt: Long, val bg: Int = 0) {
    val expiresAt: Long get() = createdAt + STORY_MS
}

data class Profile(
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val avatar: String = "",
    val banner: String = "",
    val story: Story? = null,
    /** شكل فقاعات الرسائل المختار (يطبق على كل محادثات المستخدم ويظهر للطرفين) */
    val bubbleStyle: String = "glass",
    /** true إذا هذا المستخدم حاظرني (ما أقدر أشوف ملفه) */
    val restricted: Boolean = false
)

/** كل التعامل مع Firebase هنا. الواجهات فقط تستدعي هذي الدوال. */
object Repo {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** نطاق يعيش بعمر التطبيق (حتى ما ينلغي الإرسال لو تغيرت الشاشة) */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun chatId(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    /** يحفظ بيانات المستخدم بعد تفعيل الإيميل */
    suspend fun saveProfile() {
        val u = auth.currentUser ?: return
        val data = mapOf(
            "uid" to u.uid,
            "email" to (u.email ?: ""),
            "name" to (u.displayName?.takeIf { it.isNotBlank() } ?: u.email?.substringBefore("@") ?: "")
        )
        db.collection("users").document(u.uid).set(data, SetOptions.merge()).await()
    }

    fun usersFlow(): Flow<List<UserItem>> = callbackFlow {
        val reg = db.collection("users").addSnapshotListener { snap, _ ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { d ->
                    val uid = d.getString("uid") ?: return@mapNotNull null
                    UserItem(uid, d.getString("name") ?: "", d.getString("email") ?: "", d.getString("username") ?: "")
                })
            }
        }
        awaitClose { reg.remove() }
    }

    /** آخر 50 رسالة، الأحدث أولاً */
    fun messagesFlow(me: String, other: String): Flow<List<Msg>> = callbackFlow {
        val reg = db.collection("chats").document(chatId(me, other)).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    trySend(snap.documents.map { it.toMsg() })
                }
            }
        awaitClose { reg.remove() }
    }


    private fun DocumentSnapshot.toMsg() = Msg(
        id = id,
        from = getString("from") ?: "",
        text = getString("text") ?: "",
        // الرسالة الي توها انرسلت (الوقت لسا null) نستخدم الوقت التقديري
        createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: System.currentTimeMillis(),
        deleted = getBoolean("deleted") == true,
        edited = getBoolean("edited") == true,
        pinned = getBoolean("pinned") == true,
        pinnedAt = getTimestamp("pinnedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: 0L,
        replyId = getString("replyId") ?: "",
        replyText = getString("replyText") ?: "",
        replyFrom = getString("replyFrom") ?: "",
        audio = getString("audio") ?: "",
        audioMs = getLong("audioMs") ?: 0L
    )

    /** الرسائل المثبتة بالمحادثة (حتى لو أقدم من آخر 50 رسالة)، الأحدث تثبيتاً أولاً */
    fun pinnedMessagesFlow(me: String, other: String): Flow<List<Msg>> = callbackFlow {
        val reg = db.collection("chats").document(chatId(me, other)).collection("messages")
            .whereEqualTo("pinned", true)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    trySend(snap.documents.map { it.toMsg() }.filter { !it.deleted }.sortedByDescending { it.pinnedAt })
                }
            }
        awaitClose { reg.remove() }
    }

    private fun msgRef(me: String, other: String, id: String) =
        db.collection("chats").document(chatId(me, other)).collection("messages").document(id)

    /** تحديث "آخر رسالة" بقائمة المحادثات (يُستدعى إذا الرسالة المعدّلة/المحذوفة هي الأخيرة) */
    private fun updateLastText(me: String, other: String, text: String) {
        db.collection("chats").document(chatId(me, other)).set(
            mapOf("participants" to listOf(me, other), "lastText" to text.take(100)),
            SetOptions.merge()
        )
    }

    /** حذف ناعم: الرسالة تبقى بمكانها وتظهر "رسالة مَحذوفة"، ونص الرسالة الأصلي يتمسح */
    fun deleteMessage(me: String, other: String, id: String, isLast: Boolean, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).update(
            mapOf("deleted" to true, "text" to "", "pinned" to false, "edited" to false, "audio" to "", "audioMs" to 0L)
        ).addOnFailureListener(onFail)
        if (isLast) updateLastText(me, other, "رسالة مَحذوفة")
    }

    fun editMessage(me: String, other: String, id: String, newText: String, isLast: Boolean, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).update(mapOf("text" to newText, "edited" to true)).addOnFailureListener(onFail)
        if (isLast) updateLastText(me, other, newText)
    }

    fun setMessagePinned(me: String, other: String, id: String, pinned: Boolean, onFail: (Exception) -> Unit = {}) {
        msgRef(me, other, id).update(
            if (pinned) mapOf("pinned" to true, "pinnedAt" to FieldValue.serverTimestamp(), "pinnedBy" to me)
            else mapOf("pinned" to false)
        ).addOnFailureListener(onFail)
    }

    /** المحادثات الي أنا طرف فيها (تظهر بعد أول رسالة)، الأحدث أولاً */
    fun chatsFlow(me: String): Flow<List<ChatItem>> = callbackFlow {
        val reg = db.collection("chats").whereArrayContains("participants", me)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    trySend(
                        snap.documents.mapNotNull { d ->
                            val parts = d.get("participants") as? List<*> ?: return@mapNotNull null
                            val other = parts.firstOrNull { it != me } as? String ?: return@mapNotNull null
                            ChatItem(
                                other,
                                d.getString("lastText") ?: "",
                                // الرسالة الي توها انرسلت (الوقت لسا null) تنحسب الأحدث
                                d.getTimestamp("lastAt")?.toDate()?.time ?: Long.MAX_VALUE,
                                ((d.get("unread") as? Map<*, *>)?.get(me) as? Number)?.toInt() ?: 0
                            )
                        }.sortedByDescending { it.lastAt }
                    )
                }
            }
        awaitClose { reg.remove() }
    }

    fun send(
        me: String, other: String, text: String,
        replyId: String = "", replyText: String = "", replyFrom: String = ""
    ) {
        db.collection("chats").document(chatId(me, other)).set(
            mapOf(
                "participants" to listOf(me, other),
                "lastText" to text.take(100),
                "lastAt" to FieldValue.serverTimestamp(),
                // عداد الرسائل غير المقروءة عند الطرف الثاني
                "unread" to mapOf(other to FieldValue.increment(1))
            ),
            SetOptions.merge()
        )
        val data = mutableMapOf<String, Any>(
            "from" to me,
            "to" to other,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (replyId.isNotEmpty()) {
            data["replyId"] = replyId
            data["replyText"] = replyText.take(100)
            data["replyFrom"] = replyFrom
        }
        db.collection("chats").document(chatId(me, other)).collection("messages").add(data)
    }


    /** رسالة صوتية: الصوت base64 داخل الوثيقة (أقل من 1MB بحدود Firestore) */
    fun sendVoice(me: String, other: String, b64: String, durationMs: Long) {
        val label = "🎤 رسالة صوتية"
        db.collection("chats").document(chatId(me, other)).set(
            mapOf(
                "participants" to listOf(me, other),
                "lastText" to label,
                "lastAt" to FieldValue.serverTimestamp(),
                "unread" to mapOf(other to FieldValue.increment(1))
            ),
            SetOptions.merge()
        )
        db.collection("chats").document(chatId(me, other)).collection("messages").add(
            mapOf(
                "from" to me,
                "to" to other,
                "text" to label,
                "audio" to b64,
                "audioMs" to durationMs,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }

    // ---------- غير المقروء + حذف المحادثة من عندي فقط ----------

    /** أصفّر عداد الرسائل غير المقروءة لهذي المحادثة (update فقط حتى ما تنخلق وثيقة فارغة) */
    fun markRead(me: String, other: String) {
        db.collection("chats").document(chatId(me, other)).update(
            mapOf(
                "unread.$me" to 0,
                // وقت آخر قراءة مني: الطرف الثاني يشوف منه "مقروء" تحت رسائله
                "readAt.$me" to FieldValue.serverTimestamp()
            )
        )
    }

    /** وقت آخر مرة الطرف الثاني قرأ المحادثة (0 = ما قرأ بعد) */
    fun readAtFlow(me: String, other: String): Flow<Long> = callbackFlow {
        val reg = db.collection("chats").document(chatId(me, other))
            .addSnapshotListener { d, _ ->
                val t = (d?.get("readAt") as? Map<*, *>)?.get(other) as? com.google.firebase.Timestamp
                trySend(t?.toDate()?.time ?: 0L)
            }
        awaitClose { reg.remove() }
    }

    /** لكل شخص: وقت آخر "حذف محادثة" عندي. الرسائل الأقدم منه تنخفي عندي فقط */
    fun clearedFlow(me: String): Flow<Map<String, Long>> = callbackFlow {
        val reg = db.collection("settings").document(me).addSnapshotListener { d, _ ->
            if (d != null) {
                val m = (d.get("cleared") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    (k as? String)?.let { it to ((v as? Number)?.toLong() ?: 0L) }
                }?.toMap() ?: emptyMap()
                trySend(m)
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- حذف رسالة من عندي فقط ----------
    // يتخزن بوثيقة settings/<uid> (قواعدها تسمح لصاحبها فقط) فما تحتاج تغيير للقواعد:
    //   hidden.<otherUid>   = قائمة معرّفات الرسائل المخفية عندي
    //   previews.<otherUid> = معاينة بديلة بقائمة المحادثات إذا الرسالة المخفية كانت الأخيرة

    /** معرّفات الرسائل الي حذفتها من عندي بهذي المحادثة */
    fun hiddenFlow(me: String, other: String): Flow<Set<String>> = callbackFlow {
        val reg = db.collection("settings").document(me).addSnapshotListener { d, _ ->
            if (d != null) {
                val list = ((d.get("hidden") as? Map<*, *>)?.get(other) as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
                trySend(list.toSet())
            }
        }
        awaitClose { reg.remove() }
    }

    /** المعاينات البديلة لقائمة المحادثات (لكل شخص) */
    fun previewOverridesFlow(me: String): Flow<Map<String, HiddenPreview>> = callbackFlow {
        val reg = db.collection("settings").document(me).addSnapshotListener { d, _ ->
            if (d != null) {
                val m = (d.get("previews") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val uid = k as? String ?: return@mapNotNull null
                    val o = v as? Map<*, *> ?: return@mapNotNull null
                    uid to HiddenPreview(
                        forText = o["forText"] as? String ?: "",
                        text = o["text"] as? String ?: "",
                        at = (o["at"] as? Number)?.toLong() ?: 0L
                    )
                }?.toMap() ?: emptyMap()
                trySend(m)
            }
        }
        awaitClose { reg.remove() }
    }

    /** حذف رسالة من عندي فقط (رسالتي أو رسالة الطرف الثاني). الطرف الثاني ما يتأثر أبداً */
    fun hideMessage(
        me: String, other: String, id: String,
        preview: HiddenPreview? = null,
        onFail: (Exception) -> Unit = {}
    ) {
        val data = mutableMapOf<String, Any>(
            "hidden" to mapOf(other to FieldValue.arrayUnion(id))
        )
        if (preview != null) {
            data["previews"] = mapOf(
                other to mapOf("forText" to preview.forText, "text" to preview.text, "at" to preview.at)
            )
        }
        db.collection("settings").document(me).set(data, SetOptions.merge())
            .addOnFailureListener(onFail)
    }

    /** حذف المحادثة من عندي فقط: الطرف الثاني تبقى عنده كاملة */
    fun clearChat(me: String, other: String, lastAt: Long) {
        val at = if (lastAt <= 0L || lastAt == Long.MAX_VALUE) System.currentTimeMillis() else lastAt
        db.collection("settings").document(me).set(
            mapOf(
                "cleared" to mapOf(other to at),
                // المحادثة تختفي كلياً من القائمة: نشيل التثبيت (المثبتين يظهرون حتى بدون رسائل)
                "pinned" to FieldValue.arrayRemove(other),
                // تنظيف سجل "حذف من عندي" لهذي المحادثة (ما عاد نحتاجه)
                "hidden" to mapOf(other to FieldValue.delete()),
                "previews" to mapOf(other to FieldValue.delete())
            ), SetOptions.merge()
        )
        markRead(me, other)
    }

    // ---------- متصل / آخر ظهور ----------
    // مجموعة presence/<uid> منفصلة عن profiles (حتى ما تنرسل صور base64 مع كل نبضة)

    /** أكتب حالتي: online=true مع وقت السيرفر، أو online=false = آخر ظهور الحين */
    fun setPresence(online: Boolean) {
        val u = auth.currentUser ?: return
        db.collection("presence").document(u.uid).set(
            mapOf("online" to online, "lastSeen" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        )
    }

    fun presenceFlow(uid: String): Flow<Presence> = callbackFlow {
        val reg = db.collection("presence").document(uid).addSnapshotListener { d, err ->
            if (err != null) {
                // الصلاحيات ترفض القراءة إذا هذا المستخدم حاظرني
                trySend(Presence())
            } else if (d != null) {
                trySend(
                    Presence(
                        online = d.getBoolean("online") == true,
                        lastSeen = d.getTimestamp("lastSeen", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.toDate()?.time ?: 0L
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- الملف الشخصي والقصة (مجموعة profiles منفصلة حتى ما تثقل قائمة المستخدمين) ----------

    fun profileFlow(uid: String): Flow<Profile> = callbackFlow {
        val reg = db.collection("profiles").document(uid).addSnapshotListener { d, err ->
            if (err != null) {
                // الصلاحيات ترفض القراءة إذا هذا المستخدم حاظرني
                trySend(Profile(restricted = true))
            } else if (d != null) {
                val s = d.get("story") as? Map<*, *>
                val story = s?.let {
                    Story(
                        text = it["text"] as? String ?: "",
                        color = (it["color"] as? Number)?.toInt() ?: -1,
                        image = it["image"] as? String ?: "",
                        createdAt = (it["createdAt"] as? Number)?.toLong() ?: 0L,
                        bg = (it["bg"] as? Number)?.toInt() ?: 0
                    )
                }
                trySend(
                    Profile(
                        name = d.getString("name") ?: "",
                        username = d.getString("username") ?: "",
                        bio = d.getString("bio") ?: "",
                        avatar = d.getString("avatar") ?: "",
                        banner = d.getString("banner") ?: "",
                        story = story,
                        bubbleStyle = d.getString("bubbleStyle") ?: "glass"
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- اليوزر: فريد لكل مستخدم (مجموعة usernames تحجز الاسم) ----------

    private fun genUsername(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return "user_" + (1..7).map { chars.random() }.joinToString("")
    }

    /** يحجز اليوزر للمستخدم. يرجع false إذا محجوز لشخص ثاني. يفك حجز اليوزر القديم */
    private suspend fun claimUsername(uid: String, username: String, old: String): Boolean {
        val ref = db.collection("usernames").document(username)
        val oldRef = if (old.isNotBlank() && old != username) db.collection("usernames").document(old) else null
        return db.runTransaction { tx ->
            val snap = tx.get(ref)
            val oldSnap = oldRef?.let { tx.get(it) }
            if (snap.exists() && snap.getString("uid") != uid) {
                false
            } else {
                tx.set(ref, mapOf("uid" to uid))
                if (oldRef != null && oldSnap != null && oldSnap.exists() && oldSnap.getString("uid") == uid) {
                    tx.delete(oldRef)
                }
                true
            }
        }.await()
    }

    /** إذا المستخدم ما عنده يوزر يعطيه يوزر افتراضي عشوائي فريد، وإذا عنده يتأكد انحجز */
    suspend fun ensureUsername() {
        val u = auth.currentUser ?: return
        val profRef = db.collection("profiles").document(u.uid)
        val current = profRef.get().await().getString("username") ?: ""
        if (current.isNotBlank()) {
            claimUsername(u.uid, current, "")
            db.collection("users").document(u.uid).set(mapOf("username" to current), SetOptions.merge()).await()
            return
        }
        repeat(8) {
            val cand = genUsername()
            if (claimUsername(u.uid, cand, "")) {
                profRef.set(mapOf("username" to cand), SetOptions.merge()).await()
                db.collection("users").document(u.uid).set(mapOf("username" to cand), SetOptions.merge()).await()
                return
            }
        }
    }

    /** حفظ الاسم/اليوزر/النبذة/الصور. الصور نص base64 مضغوط */
    suspend fun saveProfileInfo(name: String, username: String, bio: String, avatar: String, banner: String) {
        val u = auth.currentUser ?: return
        val old = db.collection("profiles").document(u.uid).get().await().getString("username") ?: ""
        val finalUser = username.ifBlank { old }
        if (finalUser != old) {
            if (finalUser.length < 3) throw Exception("اليوزر لازم يكون 3 حروف على الأقل")
            if (!claimUsername(u.uid, finalUser, old)) throw Exception("اليوزر محجوز، اختر غيره")
        }
        db.collection("profiles").document(u.uid).set(
            mapOf("name" to name, "username" to finalUser, "bio" to bio, "avatar" to avatar, "banner" to banner),
            SetOptions.merge()
        ).await()
        db.collection("users").document(u.uid).set(mapOf("name" to name, "username" to finalUser), SetOptions.merge()).await()
        // حتى saveProfile() ما يرجّع الاسم القديم عند كل فتح للتطبيق
        u.updateProfile(
            com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name).build()
        ).await()
    }

    suspend fun saveStory(text: String, color: Int, image: String, createdAt: Long, bg: Int = 0) {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid).set(
            mapOf("story" to mapOf("text" to text, "color" to color, "image" to image, "createdAt" to createdAt, "bg" to bg)),
            SetOptions.mergeFields("story")
        ).await()
    }

    suspend fun deleteStory() {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid).update("story", FieldValue.delete()).await()
    }

    // ---------- شكل الفقاعات (يتخزن بملفي الشخصي حتى يشوفه الطرف الثاني) ----------

    fun setBubbleStyle(id: String) {
        val u = auth.currentUser ?: return
        db.collection("profiles").document(u.uid).set(mapOf("bubbleStyle" to id), SetOptions.merge())
    }

    // ---------- الحظر ----------

    /** هل [owner] حاظر [target]؟ */
    fun blockedFlow(owner: String, target: String): Flow<Boolean> = callbackFlow {
        val reg = db.collection("blocks").document(owner).collection("users").document(target)
            .addSnapshotListener { d, err ->
                if (err != null) trySend(false) else if (d != null) trySend(d.exists())
            }
        awaitClose { reg.remove() }
    }

    fun setBlocked(other: String, blocked: Boolean) {
        val u = auth.currentUser ?: return
        val ref = db.collection("blocks").document(u.uid).collection("users").document(other)
        if (blocked) ref.set(mapOf("at" to FieldValue.serverTimestamp())) else ref.delete()
    }

    // ---------- التثبيت (شخص مميز) ----------

    fun pinnedFlow(me: String): Flow<Set<String>> = callbackFlow {
        val reg = db.collection("settings").document(me).addSnapshotListener { d, _ ->
            if (d != null) {
                val list = (d.get("pinned") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                trySend(list.toSet())
            }
        }
        awaitClose { reg.remove() }
    }

    fun setPinned(other: String, pinned: Boolean) {
        val u = auth.currentUser ?: return
        db.collection("settings").document(u.uid).set(
            mapOf("pinned" to if (pinned) FieldValue.arrayUnion(other) else FieldValue.arrayRemove(other)),
            SetOptions.merge()
        )
    }

    // ---------- البلاغات والحظر من الإدارة ----------

    /** يرسل بلاغ. السيرفر (Cloud Function) هو الي يتحقق من الرسائل ويعاقب إذا ثبت الخطأ */
    suspend fun report(reported: String, reason: String, messageIds: List<String>) {
        val me = auth.currentUser?.uid ?: return
        db.collection("reports").add(
            mapOf(
                "reporter" to me,
                "reported" to reported,
                "chatId" to chatId(me, reported),
                "reason" to reason,
                "messageIds" to messageIds.take(15),
                "status" to "new",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun banFlow(me: String): Flow<Ban> = callbackFlow {
        val reg = db.collection("bans").document(me).addSnapshotListener { d, err ->
            if (err != null) {
                trySend(Ban())
            } else if (d != null) {
                trySend(
                    if (d.exists()) Ban(
                        until = (d.get("until") as? Number)?.toLong() ?: 0L,
                        days = (d.get("days") as? Number)?.toInt() ?: 0,
                        reason = d.getString("reason") ?: ""
                    ) else Ban()
                )
            }
        }
        awaitClose { reg.remove() }
    }

    // ---------- الإشعارات (FCM) ----------

    /** يسجل توكن هذا الجهاز لحساب المستخدم الحالي، حتى يوصله إشعار. بدون حساب = بدون توكن = ما توصل إشعارات */
    fun saveFcmToken(token: String) {
        val u = auth.currentUser ?: return
        db.collection("tokens").document(u.uid).collection("devices").document(token)
            .set(mapOf("at" to FieldValue.serverTimestamp()))
    }

    fun registerFcmToken() {
        if (auth.currentUser == null) return
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { saveFcmToken(it) }
    }

    /** تسجيل الخروج: يحذف توكن الجهاز أولاً حتى ما توصل إشعارات الحساب بعد الخروج */
    fun signOut() {
        val u = auth.currentUser
        if (u == null) {
            auth.signOut()
            return
        }
        appScope.launch {
            // قبل الخروج: أصير "غير متصل" (القواعد تحتاج الحساب لسا مسجل دخول)
            try {
                withTimeoutOrNull(3000) {
                    db.collection("presence").document(u.uid).set(
                        mapOf("online" to false, "lastSeen" to FieldValue.serverTimestamp()),
                        SetOptions.merge()
                    ).await()
                }
            } catch (_: Exception) {
            }
            try {
                val fm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                val token = withTimeoutOrNull(3000) { fm.token.await() }
                if (token != null) {
                    withTimeoutOrNull(3000) {
                        db.collection("tokens").document(u.uid).collection("devices").document(token).delete().await()
                    }
                }
                withTimeoutOrNull(3000) { fm.deleteToken().await() }
            } catch (_: Exception) {
            }
            auth.signOut()
        }
    }
}
