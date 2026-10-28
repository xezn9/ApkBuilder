package com.chatapp.messenger.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.chatapp.messenger.HiddenPreview
import com.chatapp.messenger.Msg
import com.chatapp.messenger.Profile
import com.chatapp.messenger.AppState
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private val Blue = Color(0xFF1AA7FF)

/** إذا مر دقيقتين أو أكثر بين رسالتين من نفس الشخص تعتبر رسالة جديدة وتظهر صورته معها */
private const val AVATAR_GAP_MS = 2L * 60_000L

/** إذا مر 5 دقائق أو أكثر بين رسالتين يظهر وقت جديد */
private const val TIME_GAP_MS = 5L * 60_000L

private fun clock(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val h = c.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val m = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "$h:$m " + if (c.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
}

/** 12:56 ص لرسائل اليوم، وأمس/التاريخ لرسائل الأيام السابقة */
private fun timeLabel(ms: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(now, c)) return clock(ms)
    val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(y, c)) return "أمس " + clock(ms)
    return "${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.YEAR)} " + clock(ms)
}

/** شاشة المحادثة بالكامل Liquid Glass: شريط علوي + فقاعات + مربع كتابة + إعدادات */
@Composable
fun ChatScreen(other: UserItem, onBack: () -> Unit, onOpenProfile: () -> Unit = {}) {
    val me = Repo.auth.currentUser?.uid ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val rawMsgs by remember(other.uid) { Repo.messagesFlow(me, other.uid) }.collectAsState(initial = emptyList())
    val clearedMap by remember(me) { Repo.clearedFlow(me) }.collectAsState(initial = emptyMap())
    val clearedAt = clearedMap[other.uid] ?: 0L
    // رسائل حذفتها "من عندي فقط" (رسائلي أو رسائل الطرف الثاني): تنخفي عندي والطرف الثاني ما يتأثر
    val hiddenIds by remember(other.uid) { Repo.hiddenFlow(me, other.uid) }.collectAsState(initial = emptySet())
    // رسائل أقدم من "حذف المحادثة" + الرسائل المحذوفة من عندي تنخفي عندي فقط
    val msgs = remember(rawMsgs, clearedAt, hiddenIds) {
        rawMsgs.filter { it.createdAt > clearedAt && it.id !in hiddenIds }
    }
    // "مقروء": تحت آخر رسالة مني الطرف الثاني قرأها (إذا ما قرأها ما يظهر شي)
    val otherReadAt by remember(other.uid) { Repo.readAtFlow(me, other.uid) }.collectAsState(initial = 0L)
    val lastReadMineId = remember(msgs, otherReadAt) {
        if (otherReadAt <= 0L) null
        else msgs.firstOrNull { it.from == me && !it.deleted && it.createdAt <= otherReadAt }?.id
    }
    val profile by remember(other.uid) { Repo.profileFlow(other.uid) }.collectAsState(initial = Profile())
    val myProfile by remember(me) { Repo.profileFlow(me) }.collectAsState(initial = Profile())
    val pinnedSet by remember(me) { Repo.pinnedFlow(me) }.collectAsState(initial = emptySet())
    val blockedByMe by remember(other.uid) { Repo.blockedFlow(me, other.uid) }.collectAsState(initial = false)
    val blockedMe by remember(other.uid) { Repo.blockedFlow(other.uid, me) }.collectAsState(initial = false)

    val presence = rememberPresenceStatus(other.uid)
    val avatar = rememberB64Image(profile.avatar)
    val myAvatar = rememberB64Image(myProfile.avatar)
    val shownName = profile.name.ifBlank { other.name }.ifBlank { other.email }.ifBlank { "مستخدم" }
    val isPinned = other.uid in pinnedSet
    var text by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    // ---- الرسائل المثبتة / الرد / التعديل / قائمة الضغطة المطولة ----
    val pinnedRaw by remember(other.uid) { Repo.pinnedMessagesFlow(me, other.uid) }.collectAsState(initial = emptyList())
    val pinnedMsgs = remember(pinnedRaw, clearedAt, hiddenIds) {
        pinnedRaw.filter { it.createdAt > clearedAt && it.id !in hiddenIds }
    }
    var replyTo by remember(other.uid) { mutableStateOf<Msg?>(null) }
    var editing by remember(other.uid) { mutableStateOf<Msg?>(null) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var showPinned by remember { mutableStateOf(false) }
    var flashId by remember { mutableStateOf<String?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val openedAt = remember { System.currentTimeMillis() }
    val animatedIds = remember { mutableSetOf<String>() }
    val focusRequester = remember { FocusRequester() }
    val fly = remember { Animatable(0f) }

    // ---- خلفية هذي المحادثة (محلية وخاصة بي) ----
    val prefs = remember { ChatPrefs(ctx) }
    var bgCfg by remember(other.uid) { mutableStateOf(prefs.load(me, other.uid)) }
    val updateBg = { nb: ChatBg -> bgCfg = nb; prefs.save(me, other.uid, nb) }
    val bgFile = remember(other.uid) { prefs.imageFile(me, other.uid) }
    val hasImage = remember(bgCfg.version) { prefs.hasImage(me, other.uid) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bytes = ImageUtil.uriToJpeg(ctx, uri, 1600, 82)
            if (bytes == null) {
                Toast.makeText(ctx, "ما گدرت أفتح الصورة", Toast.LENGTH_SHORT).show()
            } else {
                withContext(Dispatchers.IO) { bgFile.writeBytes(bytes) }
                updateBg(bgCfg.copy(kind = "image", version = System.currentTimeMillis()))
            }
        }
    }

    // لما أفتح المحادثة أشيل إشعارها من شريط الإشعارات
    LaunchedEffect(other.uid) { NotificationManagerCompat.from(ctx).cancel(other.uid.hashCode()) }

    val custom = bgCfg.custom
    val dark = custom || isSystemInDarkTheme()
    val fg = if (custom) Color.White else MaterialTheme.colorScheme.onBackground
    val bgBackdrop = rememberLayerBackdrop()      // الخلفية فقط (الفقاعات تستخدمها)
    val contentBackdrop = rememberLayerBackdrop() // الخلفية + الرسائل (الأشرطة تستخدمها)

    val myStyle = bubbleStyleById(myProfile.bubbleStyle)
    val otherStyle = bubbleStyleById(profile.bubbleStyle)

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues().calculateBottomPadding()

    // ---- النزول التلقائي مع الرسائل ----
    val listState = rememberLazyListState()

    // ---- رسائل صوتية ----
    val recorder = remember { VoiceRecorder() }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableStateOf(0L) }
    fun finishRecording(send: Boolean) {
        recording = false
        if (!send) { recorder.cancel(); return }
        val r = recorder.stop()
        when {
            r == null -> Toast.makeText(ctx, "ما گدرت أسجل الصوت", Toast.LENGTH_SHORT).show()
            r.durationMs < VOICE_MIN_MS -> Toast.makeText(ctx, "التسجيل قصير", Toast.LENGTH_SHORT).show()
            else -> {
                Repo.sendVoice(me, other.uid, r.b64, r.durationMs)
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
    }
    fun beginRecording() {
        VoicePlayer.stop()
        if (recorder.start(ctx)) { recording = true; recMs = 0L }
        else Toast.makeText(ctx, "ما گدرت أشغّل المايك", Toast.LENGTH_SHORT).show()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) beginRecording()
        else Toast.makeText(ctx, "لازم تسمح باستخدام المايك", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(recording) {
        while (recording) {
            recMs = recorder.elapsedMs()
            if (recMs >= VOICE_MAX_MS) { finishRecording(true); break }
            delay(100)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { recorder.cancel(); VoicePlayer.stop() }
    }
    val density = LocalDensity.current
    // ارتفاع الشريط السفلي الفعلي (مربع الكتابة + الكيبورد + شريط النظام). نقيسه بدل ما نخمّن،
    // حتى المحادثة ما تنخفي خلف مربع الكتابة أبداً حتى لو كبر المربع لعدة أسطر.
    var barHeightPx by remember { mutableIntStateOf(0) }
    val listBottomPadding = if (barHeightPx > 0) with(density) { barHeightPx.toDp() } + 16.dp
    else bottomInset + 52.dp + 28.dp

    // رسالة جديدة (مني: دائماً، من الطرف الثاني: إذا كنت أقرأ آخر المحادثة) → انزل لها
    val newest = msgs.firstOrNull()
    val newestId = newest?.id
    val newestMine = newest?.from == me
    // أي رسالة تصل وأنا داخل المحادثة والتطبيق قدامي = مقروءة (أصفّر العداد وأسجل وقت القراءة).
    // إذا التطبيق بالخلفية ما تنحسب مقروءة، وترجع تنحسب أول ما أفتحه.
    LaunchedEffect(other.uid, newestId, AppState.foreground) {
        if (AppState.foreground) Repo.markRead(me, other.uid)
    }
    LaunchedEffect(newestId) {
        if (newestId != null && (newestMine || listState.firstVisibleItemIndex <= 1)) {
            listState.animateScrollToItem(0)
        }
    }
    // فتح الكيبورد: إذا كنت بآخر المحادثة ابقَ عليها
    val imeShown = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeShown) {
        if (imeShown && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    val onWriteFail: (Exception) -> Unit = { e ->
        val denied = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
        Toast.makeText(
            ctx,
            if (denied) "السيرفر رفض العملية: انشر firestore.rules (firebase deploy --only firestore:rules)"
            else "ما تمت العملية، تأكد من الإنترنت",
            Toast.LENGTH_LONG
        ).show()
    }

    fun nameOf(uid: String) = if (uid == me) "أنت" else shownName

    /** ينزل للرسالة الأصلية ويومّضها */
    fun jumpTo(id: String) {
        val idx = msgs.indexOfFirst { it.id == id }
        if (idx < 0) {
            Toast.makeText(ctx, "هذي الرسالة قديمة وما انحملت بالمحادثة", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val vh = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(idx, -(vh / 3))
            flashId = id
            delay(1000)
            if (flashId == id) flashId = null
        }
    }

    /** إرسال رسالة جديدة (مع رد إذا موجود) أو حفظ التعديل */
    fun submit() {
        val t = text.trim()
        if (t.isEmpty()) return
        val ed = editing
        if (ed != null) {
            if (t != ed.text) {
                Repo.editMessage(me, other.uid, ed.id, t, isLast = ed.id == rawMsgs.firstOrNull()?.id, onFail = onWriteFail)
            }
            editing = null
            text = ""
        } else {
            val r = replyTo
            Repo.send(
                me, other.uid, t,
                replyId = r?.id ?: "",
                replyText = r?.let { msgPreview(it, 100) } ?: "",
                replyFrom = r?.from ?: ""
            )
            replyTo = null
            text = ""
            scope.launch { listState.animateScrollToItem(0) }
        }
        // حركة طيران زر الإرسال
        scope.launch {
            fly.animateTo(1f, tween(170))
            fly.snapTo(-1f)
            fly.animateTo(0f, spring(0.6f, 500f))
        }
    }

    // عند الرد أو التعديل افتح الكيبورد على مربع الكتابة
    LaunchedEffect(replyTo?.id, editing?.id) {
        if (replyTo != null || editing != null) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
        }
    }

    BackHandler(enabled = menu != null || showPinned) {
        if (menu != null) menu = null else showPinned = false
    }
    LaunchedEffect(pinnedMsgs.isEmpty()) { if (pinnedMsgs.isEmpty()) showPinned = false }

    val pinnedPad by animateDpAsState(if (pinnedMsgs.isNotEmpty()) 58.dp else 0.dp, spring(0.85f, 400f), label = "pinnedPad")

    CompositionLocalProvider(LocalContentColor provides fg) {
        Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
            Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
                // تغيير الخلفية بتلاشي ناعم (blur يتغير فوراً بدون إعادة تلاشي)
                Box(Modifier.fillMaxSize().layerBackdrop(bgBackdrop)) {
                    Crossfade(
                        targetState = bgCfg.copy(blur = false),
                        animationSpec = tween(500),
                        label = "chatBg"
                    ) { c ->
                        ChatBackground(c.copy(blur = bgCfg.blur), bgFile, Modifier.fillMaxSize())
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // الرسائل الأحدث أولاً + reverseLayout = تظهر من تحت لفوق
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp,
                        top = statusTop + 76.dp + pinnedPad,
                        bottom = listBottomPadding
                    )
                ) {
                    itemsIndexed(msgs, key = { _, m -> m.id }) { i, m ->
                        val newer = msgs.getOrNull(i - 1)
                        val older = msgs.getOrNull(i + 1)
                        val mine = m.from == me
                        // صورة الملف الشخصي مرة وحدة: عند آخر رسالة من نفس الشخص ضمن نفس المجموعة
                        val showAvatar = newer == null || newer.from != m.from ||
                            newer.createdAt - m.createdAt >= AVATAR_GAP_MS
                        // الوقت: عند أول رسالة، أو بعد غياب 5 دقائق أو أكثر
                        val showTime = older == null || m.createdAt - older.createdAt >= TIME_GAP_MS
                        val tight = older != null && older.from == m.from && !showTime &&
                            m.createdAt - older.createdAt < AVATAR_GAP_MS
                        val fresh = remember(m.id) { m.createdAt >= openedAt - 1500L && animatedIds.add(m.id) }
                        val original = if (m.replyId.isNotEmpty()) msgs.firstOrNull { it.id == m.replyId } else null
                        MessageRow(
                            m = m, mine = mine,
                            showAvatar = showAvatar, showTime = showTime, tight = tight,
                            avatar = if (mine) myAvatar else avatar,
                            style = if (mine) myStyle else otherStyle,
                            dark = dark, fg = fg, backdrop = bgBackdrop,
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            animateIn = fresh,
                            readLabel = mine && m.id == lastReadMineId,
                            flash = flashId == m.id,
                            replyName = nameOf(m.replyFrom),
                            replyDeleted = original?.deleted == true,
                            onReply = { if (!m.deleted) { editing = null; replyTo = m } },
                            onJump = { jumpTo(it) },
                            onLongPress = { rect -> menu = MenuTarget(m, mine, rect) }
                        )
                    }
                }
            }

            // ----- شريط الرسائل المثبتة (يظهر للطرفين) -----
            PinnedBanner(
                backdrop = contentBackdrop, dark = dark, fg = fg, pinned = pinnedMsgs,
                onClick = { if (pinnedMsgs.size == 1) jumpTo(pinnedMsgs[0].id) else showPinned = true },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(top = statusTop + 64.dp, start = 12.dp, end = 12.dp)
            )

            // ----- الشريط العلوي: رجوع + بطاقة الشخص (صورة + اسم) + إعدادات -----
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(onClick = onBack, backdrop = contentBackdrop, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(24.dp))
                }
                GlassButton(
                    onClick = onOpenProfile, backdrop = contentBackdrop,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) {
                    Box(Modifier.size(32.dp)) {
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2B2F36)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatar != null) {
                                Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(18.dp))
                            }
                        }
                        OnlineDot(presence.online, size = 11.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(shownName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // متصل (أخضر) أو آخر ظهور بالضبط. إذا أحد حاظر الثاني ما يظهر شي
                        if (!blockedMe && !blockedByMe) {
                            if (presence.online) {
                                Text("متصل", fontSize = 11.sp, color = OnlineGreen, maxLines = 1)
                            } else if (presence.lastSeen > 0L) {
                                Text(lastSeenLabel(presence.lastSeen), fontSize = 11.sp, color = fg.copy(alpha = 0.6f), maxLines = 1)
                            }
                        }
                    }
                    if (isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    }
                }
                GlassButton(onClick = { showSettings = true }, backdrop = contentBackdrop, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Settings, null, modifier = Modifier.size(22.dp))
                }
            }

            // ----- الشريط السفلي: مربع الكتابة + زر الإرسال (أو إشعار الحظر) -----
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { barHeightPx = it.height }
                    .navigationBarsPadding().imePadding().padding(12.dp)
            ) {
                // شريط "رد على ..." أو "تعديل الرسالة" فوق مربع الكتابة
                val stripLast = remember { arrayOfNulls<Pair<Boolean, Msg>>(1) }
                val editNow = editing
                val replyNow = replyTo
                if (editNow != null) stripLast[0] = true to editNow
                else if (replyNow != null) stripLast[0] = false to replyNow
                val strip = stripLast[0]
                AnimatedVisibility(
                    visible = (editing != null || replyTo != null) && !(blockedByMe || blockedMe),
                    enter = expandVertically(spring(0.85f, 420f)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
                ) {
                    if (strip != null) {
                        ComposerStrip(
                            backdrop = contentBackdrop, dark = dark, fg = fg,
                            isEdit = strip.first,
                            title = if (strip.first) "تعديل الرسالة" else "رد على " + nameOf(strip.second.from),
                            preview = msgPreview(strip.second),
                            onClose = { if (editing != null) { editing = null; text = "" }; replyTo = null }
                        )
                    }
                }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (blockedByMe || blockedMe) {
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp)
                            .drawBackdrop(
                                backdrop = contentBackdrop,
                                shape = { Capsule() },
                                effects = { vibrancy(); blur(2f.dp.toPx()); lens(12f.dp.toPx(), 24f.dp.toPx()) },
                                onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.35f)) }
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (blockedByMe) "حظرت هذا المستخدم" else "ما تگدر تراسل هذا المستخدم",
                            color = fg.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center
                        )
                    }
                    if (blockedByMe) {
                        GlassButton(
                            onClick = { Repo.setBlocked(other.uid, false) },
                            backdrop = contentBackdrop, tint = Blue,
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Text("إلغاء الحظر", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp))
                        }
                    }
                } else {
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp)
                            .drawBackdrop(
                                backdrop = contentBackdrop,
                                shape = { Capsule() },
                                effects = {
                                    vibrancy()
                                    blur(2f.dp.toPx())
                                    lens(12f.dp.toPx(), 24f.dp.toPx())
                                },
                                onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.35f)) }
                            )
                            .border(
                                0.8.dp,
                                Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.06f))),
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (recording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                                Spacer(Modifier.width(10.dp))
                                Text(formatVoiceTime(recMs), color = fg, fontSize = 16.sp)
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).clickable { finishRecording(false) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.Close, null, tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
                            }
                        }
                        if (text.isEmpty() && !recording) Text("اكتب رسالة", color = fg.copy(alpha = 0.5f), fontSize = 16.sp)
                        if (!recording) BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            maxLines = 4,
                            textStyle = TextStyle(color = fg, fontSize = 16.sp),
                            cursorBrush = SolidColor(Blue),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                        )
                    }
                    val rtlNow = LocalLayoutDirection.current == LayoutDirection.Rtl
                    GlassButton(
                        onClick = {
                            if (recording) finishRecording(true)
                            else if (text.isBlank() && editing == null) {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        ctx, android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) beginRecording()
                                else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else submit()
                        },
                        backdrop = contentBackdrop,
                        tint = Blue,
                        modifier = Modifier.size(52.dp)
                    ) {
                      if (text.isBlank() && editing == null && !recording) {
                        MicGlyph(Color.White, Modifier.size(24.dp))
                      } else {
                        // أيقونة الإرسال تتحول لعلامة صح أثناء التعديل، وتطير عند الإرسال
                        AnimatedContent(
                            targetState = editing != null,
                            transitionSpec = {
                                (scaleIn(spring(0.6f, 500f), initialScale = 0.4f) + fadeIn(tween(150))) togetherWith
                                    (scaleOut(tween(100), targetScale = 0.4f) + fadeOut(tween(100)))
                            },
                            label = "sendIcon"
                        ) { isEdit ->
                            if (isEdit) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(26.dp))
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send, null, tint = Color.White,
                                    modifier = Modifier.size(24.dp).graphicsLayer {
                                        val dir = if (rtlNow) -1f else 1f
                                        translationX = fly.value * 22.dp.toPx() * dir
                                        translationY = -fly.value * 22.dp.toPx()
                                        alpha = (1f - abs(fly.value)).coerceIn(0f, 1f)
                                    }
                                )
                            }
                        }
                      }
                    }
                }
            }
            }
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(230))
        ) {
            ChatSettingsPanel(
                backdrop = contentBackdrop,
                dark = dark,
            otherName = shownName,
            bg = bgCfg,
            hasImage = hasImage,
            onBgChange = updateBg,
            onPickImage = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            styleId = myProfile.bubbleStyle,
            onStyleChange = { Repo.setBubbleStyle(it) },
            pinned = isPinned,
            onTogglePin = { Repo.setPinned(other.uid, !isPinned) },
            blockedByMe = blockedByMe,
            onToggleBlock = { Repo.setBlocked(other.uid, !blockedByMe) },
            onReport = { reason ->
                showSettings = false
                // أرسل آخر 15 رسالة للمبلّغ عنه كدليل، والسيرفر يتحقق منها بنفسه
                val ids = msgs.filter { it.from == other.uid }.take(15).map { it.id }
                scope.launch {
                    try {
                        Repo.report(other.uid, reason, ids)
                        Toast.makeText(ctx, "تم إرسال البلاغ، شكراً لك", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "فشل إرسال البلاغ، تأكد من الإنترنت", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showSettings = false }
        )
    }

        // ----- قائمة الضغطة المطولة على رسالة (Liquid Glass) -----
        val mt = menu
        if (mt != null) {
            val mm = mt.msg
            val actions = buildList {
                // الرسالة المحذوفة للجميع ("رسالة مَحذوفة") ما يصير عليها رد/تعديل/تثبيت، بس إخفاء من عندي
                if (!mm.deleted) {
                    add(MsgAction(AppIcons.Reply, "رد", Blue) { menu = null; editing = null; replyTo = mm })
                    if (mt.mine && mm.audio.isEmpty()) add(MsgAction(Icons.Filled.Edit, "تعديل", Color(0xFF8A5CFF)) {
                        menu = null; replyTo = null; editing = mm; text = mm.text
                    })
                    add(MsgAction(AppIcons.Pin, if (mm.pinned) "إلغاء التثبيت" else "تثبيت", Color(0xFFFFC107)) {
                        menu = null
                        Repo.setMessagePinned(me, other.uid, mm.id, !mm.pinned, onWriteFail)
                    })
                }
                // حذف من عندي فقط: لأي رسالة (رسالتي أو رسالة الطرف الثاني)
                add(MsgAction(Icons.Filled.Delete, "حذف من عندي", Color(0xFFFF8A3D)) {
                    menu = null
                    // إذا هي آخر رسالة ظاهرة عندي نحدّث معاينة قائمة المحادثات عندي (تصير الي قبلها)
                    val idx = msgs.indexOfFirst { it.id == mm.id }
                    val real = rawMsgs.firstOrNull()
                    val preview = if (idx == 0 && real != null) HiddenPreview(
                        forText = lastTextOf(real),
                        text = msgs.getOrNull(1)?.let { lastTextOf(it) } ?: "",
                        at = System.currentTimeMillis()
                    ) else null
                    Repo.hideMessage(me, other.uid, mm.id, preview, onWriteFail)
                })
                // حذف للجميع: رسائلي فقط (الي كان اسمه "حذف")
                if (mt.mine && !mm.deleted) add(MsgAction(Icons.Filled.Delete, "حذف للجميع", Color(0xFFE5484D)) {
                    menu = null
                    Repo.deleteMessage(me, other.uid, mm.id, isLast = mm.id == rawMsgs.firstOrNull()?.id, onFail = onWriteFail)
                })
            }
            GlassMessageMenu(
                backdrop = contentBackdrop, dark = dark,
                anchorTop = mt.bounds.top, anchorBottom = mt.bounds.bottom,
                anchorStart = mt.bounds.left, anchorEnd = mt.bounds.right,
                screenWidth = rootSize.width.toFloat(), screenHeight = rootSize.height.toFloat(),
                actions = actions,
                onDismiss = { menu = null }
            ) {
                StyledBubble(
                    style = if (mt.mine) myStyle else otherStyle, mine = mt.mine,
                    dark = dark, fg = fg, backdrop = bgBackdrop,
                    modifier = Modifier.widthIn(max = 260.dp)
                ) { textColor ->
                    BubbleBody(
                        m = mm, textColor = textColor, replyName = nameOf(mm.replyFrom),
                        replyDeleted = msgs.firstOrNull { it.id == mm.replyId }?.deleted == true,
                        onJump = {}, onLongPress = {}
                    )
                }
            }
        }

        // ----- قائمة الرسائل المثبتة -----
        if (showPinned) {
            PinnedMessagesSheet(
                backdrop = contentBackdrop, dark = dark, pinned = pinnedMsgs,
                nameOf = { nameOf(it.from) },
                onJump = { showPinned = false; jumpTo(it.id) },
                onUnpin = { Repo.setMessagePinned(me, other.uid, it.id, false, onWriteFail) },
                onDismiss = { showPinned = false }
            )
        }
        }
    }
}

/** نص الرسالة بنفس صيغة "آخر رسالة" المخزنة بوثيقة المحادثة (تُقارن معها حرفياً) */
private fun lastTextOf(m: Msg): String = if (m.deleted) "رسالة مَحذوفة" else m.text.take(100)

private class MenuTarget(val msg: Msg, val mine: Boolean, val bounds: Rect)

/** شريط فوق مربع الكتابة: رد على رسالة أو تعديل رسالة */
@Composable
private fun ComposerStrip(
    backdrop: Backdrop,
    dark: Boolean,
    fg: Color,
    isEdit: Boolean,
    title: String,
    preview: String,
    onClose: () -> Unit
) {
    val accent = if (isEdit) Color(0xFF8A5CFF) else Blue
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx())
                        lens(10f.dp.toPx(), 20f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.40f)) }
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.06f))),
                    shape
                )
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(10.dp))
            Icon(if (isEdit) Icons.Filled.Edit else AppIcons.Reply, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(preview, color = fg.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Close, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
        }
    }
}

/** داخل الفقاعة: اقتباس الرد (إن وجد) + النص أو "رسالة مَحذوفة" + علامات (مثبتة/معدّلة) */
@Composable
private fun BubbleBody(
    m: Msg,
    textColor: Color,
    replyName: String,
    replyDeleted: Boolean,
    onJump: (String) -> Unit,
    onLongPress: () -> Unit
) {
    Column(Modifier.animateContentSize(spring(0.85f, 400f))) {
        if (m.replyId.isNotEmpty() && !m.deleted) {
            Row(
                Modifier.padding(bottom = 6.dp)
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(10.dp))
                    .background(textColor.copy(alpha = 0.12f))
                    .pointerInput(m.id) {
                        detectTapGestures(onTap = { onJump(m.replyId) }, onLongPress = { onLongPress() })
                    }
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(textColor.copy(alpha = 0.7f)))
                Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(replyName, color = textColor.copy(alpha = 0.95f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        if (replyDeleted) "رسالة مَحذوفة" else m.replyText,
                        color = textColor.copy(alpha = 0.8f), fontSize = 13.sp,
                        fontStyle = if (replyDeleted) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // تعديل/حذف: النص يتبدل بتلاشي ناعم
        AnimatedContent(
            targetState = if (m.deleted) null else m.text,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            label = "bubbleText"
        ) { txt ->
            if (txt == null) {
                Text(
                    "رسالة مَحذوفة", color = textColor.copy(alpha = 0.6f),
                    fontSize = 15.sp, fontStyle = FontStyle.Italic
                )
            } else if (m.audio.isNotEmpty()) {
                VoiceBubbleContent(m.id, m.audio, m.audioMs, textColor)
            } else {
                Text(txt, color = textColor, fontSize = 16.sp)
            }
        }

        AnimatedVisibility(
            visible = !m.deleted && (m.pinned || m.edited),
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = m.pinned,
                    enter = scaleIn(spring(0.4f, 500f)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Icon(AppIcons.Pin, null, tint = textColor.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                }
                if (m.pinned && m.edited) Spacer(Modifier.width(5.dp))
                if (m.edited) Text("معدّلة", color = textColor.copy(alpha = 0.65f), fontSize = 11.sp)
            }
        }
    }
}

/**
 * سطر رسالة: [وقت] + (صورة + فقاعة). الصورة تظهر بآخر رسالة بالمجموعة فقط.
 * - ضغطة مطولة = قائمة الخيارات (تُرجع حدود الفقاعة)
 * - سحب بسيط أفقي = رد على الرسالة
 */
@Composable
private fun MessageRow(
    m: Msg,
    mine: Boolean,
    showAvatar: Boolean,
    showTime: Boolean,
    tight: Boolean,
    avatar: ImageBitmap?,
    style: BubbleStyle,
    dark: Boolean,
    fg: Color,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    animateIn: Boolean = false,
    readLabel: Boolean = false,
    flash: Boolean = false,
    replyName: String = "",
    replyDeleted: Boolean = false,
    onReply: () -> Unit = {},
    onJump: (String) -> Unit = {},
    onLongPress: (Rect) -> Unit = {}
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val canAct = !m.deleted

    // انيميشن إرسال/وصول رسالة جديدة: تطلع من تحت بتكبير نابضي
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { if (animateIn) appear.animateTo(1f, spring(0.72f, 380f)) }

    // نبضة الفقاعة: عند تعديل/حذف/تثبيت الرسالة، وعند القفز لها من رد أو تثبيت
    val pulse = remember { Animatable(1f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(m.text, m.edited, m.deleted, m.pinned) {
        if (firstRun) { firstRun = false; return@LaunchedEffect }
        pulse.animateTo(if (m.deleted) 0.9f else 1.07f, tween(130))
        pulse.animateTo(1f, spring(0.45f, 450f))
    }
    LaunchedEffect(flash) {
        if (flash) {
            pulse.animateTo(1.08f, tween(160))
            pulse.animateTo(1f, spring(0.4f, 400f))
        }
    }

    // سحب للرد
    val maxSwipe = with(density) { 64.dp.toPx() }
    var swipeX by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val onReplyState = rememberUpdatedState(onReply)
    val onLongState = rememberUpdatedState(onLongPress)
    val boundsHolder = remember { arrayOf(Rect.Zero) }

    fun springBack() {
        scope.launch { animate(swipeX, 0f, animationSpec = spring(0.6f, 500f)) { v, _ -> swipeX = v } }
    }

    val swipeMod = if (canAct) {
        Modifier.pointerInput(m.id) {
            detectHorizontalDragGestures(
                onDragStart = { armed = false },
                onDragEnd = {
                    val fire = abs(swipeX) >= maxSwipe * 0.9f
                    armed = false
                    springBack()
                    if (fire) onReplyState.value()
                },
                onDragCancel = { armed = false; springBack() },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    val next = (swipeX + dx * 0.7f).coerceIn(-maxSwipe, maxSwipe)
                    val nowArmed = abs(next) >= maxSwipe * 0.9f
                    if (nowArmed && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    armed = nowArmed
                    swipeX = next
                }
            )
        }
    } else Modifier

    fun fireLongPress() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongState.value(boundsHolder[0])
    }

    val avatarSlot = @Composable {
        // نحجز مكان الصورة دائماً حتى تبقى الفقاعات بنفس المحاذاة
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .background(if (showAvatar) Color(0xFF2B2F36) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (showAvatar) {
                if (avatar != null) {
                    Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    val bubble = @Composable {
        StyledBubble(
            style = style, mine = mine, dark = dark, fg = fg, backdrop = backdrop,
            modifier = Modifier.widthIn(max = 260.dp)
                .onGloballyPositioned { boundsHolder[0] = it.boundsInRoot() }
                .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                // الضغطة المطولة تشتغل حتى على "رسالة مَحذوفة" (فيها خيار "حذف من عندي" فقط)
                .pointerInput(m.id) {
                    detectTapGestures(onLongPress = { fireLongPress() })
                }
        ) { textColor ->
            BubbleBody(
                m = m, textColor = textColor, replyName = replyName, replyDeleted = replyDeleted,
                onJump = onJump, onLongPress = { fireLongPress() }
            )
        }
    }

    Column(
        modifier.fillMaxWidth()
            .padding(top = if (tight) 2.dp else 10.dp)
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 36.dp.toPx()
                val sc = 0.88f + 0.12f * appear.value
                scaleX = sc; scaleY = sc
            }
    ) {
        if (showTime) {
            Text(
                timeLabel(m.createdAt),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = fg.copy(alpha = 0.6f)
            )
        }
        Box(Modifier.fillMaxWidth()) {
            // أيقونة الرد تنكشف خلف الرسالة أثناء السحب
            if (swipeX != 0f) {
                val p = (abs(swipeX) / maxSwipe).coerceIn(0f, 1f)
                val onStart = (swipeX > 0f) != rtl
                Box(
                    Modifier.align(if (onStart) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 8.dp)
                        .size(34.dp)
                        .graphicsLayer {
                            alpha = p
                            val sc = 0.5f + 0.5f * p
                            scaleX = sc; scaleY = sc
                        }
                        .clip(CircleShape)
                        .background(fg.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Reply, null, tint = if (armed) Blue else fg.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .then(swipeMod)
                    .graphicsLayer { translationX = swipeX },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End
            ) {
                if (mine) {
                    avatarSlot(); Spacer(Modifier.width(6.dp)); bubble()
                } else {
                    bubble(); Spacer(Modifier.width(6.dp)); avatarSlot()
                }
            }
        }
        // "مقروء" تحت الفقاعة (محاذاة بداية الفقاعة: عرض الصورة 32 + المسافة 6)
        if (readLabel) {
            Text(
                "مقروء",
                modifier = Modifier.fillMaxWidth().padding(start = 38.dp, top = 2.dp),
                textAlign = TextAlign.Start,
                fontSize = 11.sp,
                color = fg.copy(alpha = 0.6f)
            )
        }
    }
}
