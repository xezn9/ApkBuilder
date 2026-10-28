package com.chatapp.messenger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chatapp.messenger.Ban
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.AppState
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem
import com.chatapp.messenger.ui.glass.LiquidBottomTab
import com.chatapp.messenger.ui.glass.LiquidBottomTabs
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** إذا الحساب محظور (بعد بلاغ ثبت صحته) تظهر شاشة الحظر، وإلا الشاشة الرئيسية */
@Composable
fun MainScreen() {
    val myUid = Repo.auth.currentUser?.uid ?: ""
    val ban by remember(myUid) { Repo.banFlow(myUid) }.collectAsState(initial = Ban())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ban.until) {
        now = System.currentTimeMillis()
        while (now < ban.until) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    if (ban.until > now) BanScreen(ban, now) else MainContent()
}

/** الشاشة الرئيسية: المحتوى + الشريط السفلي الزجاجي (Home / BDF / Account) */
@Composable
private fun MainContent() {
    val myUid = Repo.auth.currentUser?.uid ?: ""
    val ctx = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var tab by remember { mutableIntStateOf(0) }
    var chatWith by remember { mutableStateOf<UserItem?>(null) }
    var viewProfile by remember { mutableStateOf<UserItem?>(null) }
    var overlayOpen by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    // حذف محادثة من عندي فقط: (uid, الاسم, وقت آخر رسالة)
    var deleteChat by remember { mutableStateOf<Triple<String, String, Long>?>(null) }
    // نتذكر آخر محادثة/بروفايل حتى تشتغل حركة الإغلاق قبل ما تختفي الشاشة
    var lastChat by remember { mutableStateOf<UserItem?>(null) }
    var lastProfile by remember { mutableStateOf<UserItem?>(null) }
    val backdrop = rememberLayerBackdrop()
    val contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    val iconColorFilter = ColorFilter.tint(contentColor)
    val tabs = listOf(
        rememberVectorPainter(Icons.Filled.Home) to "Home",
        rememberVectorPainter(Icons.Filled.Star) to "BDF",
        rememberVectorPainter(Icons.Filled.Person) to "Account"
    )

    LaunchedEffect(myUid) {
        try {
            Repo.saveProfile()
        } catch (_: Exception) {
        }
        // يوزر افتراضي فريد لكل مستخدم جديد
        try {
            Repo.ensureUsername()
        } catch (_: Exception) {
        }
    }
    // إشعارات الرسائل: صلاحية أندرويد 13+ وتسجيل توكن الجهاز لهذا الحساب
    LaunchedEffect(myUid) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Repo.registerFcmToken()
        // استقبال الرسائل بالخلفية بدون سيرفر: خدمة تسمع لـ Firestore وتطلع الإشعار
        com.chatapp.messenger.MessageListenerService.start(ctx)
        com.chatapp.messenger.MessageListenerService.askBatteryExemptionOnce(ctx)
    }
    // متصل / آخر ظهور: أنا "متصل" والتطبيق مفتوح قدامي (نبضة كل دقيقتين)، وأول ما يروح للخلفية أصير "غير متصل"
    LaunchedEffect(myUid, AppState.foreground) {
        if (myUid.isEmpty()) return@LaunchedEffect
        if (AppState.foreground) {
            while (true) {
                Repo.setPresence(true)
                delay(PRESENCE_HEARTBEAT_MS)
            }
        } else {
            Repo.setPresence(false)
        }
    }
    // ضغطة على إشعار = افتح محادثة المرسل
    val pendingChat = Nav.openChatUid
    LaunchedEffect(pendingChat) {
        if (pendingChat != null) {
            viewProfile = null
            chatWith = UserItem(pendingChat, "", "", "")
            Nav.openChatUid = null
        }
    }
    BackHandler(enabled = confirmLogout || deleteChat != null) { confirmLogout = false; deleteChat = null }
    BackHandler(enabled = (chatWith != null || viewProfile != null) && !confirmLogout && deleteChat == null) {
        if (viewProfile != null) viewProfile = null else chatWith = null
    }

    Box(Modifier.fillMaxSize()) {
        // المحتوى: هذا الي يظهر خلف الزجاج
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(appBackground())
        ) {
            // لما تفتح محادثة/بروفايل، الشاشة الرئيسية تصغر وتخفت شوية خلفها، ولما ترجع تتكبر بحركة نابضية
            val coverOpen = chatWith != null || viewProfile != null
            val backScale by animateFloatAsState(
                if (coverOpen) 0.93f else 1f,
                spring(dampingRatio = 0.72f, stiffness = 260f), label = "backScale"
            )
            val backAlpha by animateFloatAsState(
                if (coverOpen) 0.55f else 1f, tween(320), label = "backAlpha"
            )
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = backScale; scaleY = backScale; alpha = backAlpha
                }
            ) {
            // خلفية متحركة بألوان مختلفة لكل تبويب
            AnimatedAppBackground(tab, Modifier.fillMaxSize())
            // انيميشن التنقل بين التبويبات: انزلاق أفقي + تلاشي حسب اتجاه التبويب
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 4 * dir } +
                        fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { -it / 4 * dir } +
                            fadeOut(tween(160)))
                },
                label = "tabs"
            ) { t ->
                when (t) {
                    0 -> HomeTab(
                        onOpenChat = { chatWith = it },
                        onOpenProfile = { viewProfile = it },
                        onRequestLogout = { confirmLogout = true },
                        onRequestDeleteChat = { uid, name, lastAt -> deleteChat = Triple(uid, name, lastAt) }
                    )
                    1 -> BdfTab()
                    else -> AccountTab(onOverlayChange = { overlayOpen = it })
                }
            }
            }
        }

        val open = chatWith
        val profileOpen = viewProfile
        if (open != null) lastChat = open
        if (profileOpen != null) lastProfile = profileOpen

        // الشريط السفلي يختفي بحركة نزول عند فتح محادثة/بروفايل ويرجع بحركة طلوع
        AnimatedVisibility(
            visible = open == null && profileOpen == null && !overlayOpen,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = slideInVertically(spring(0.8f, 380f)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160))
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { tab = it },
                backdrop = backdrop,
                tabsCount = 3,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 36f.dp, vertical = 24f.dp)
            ) {
                // نفس تركيبة الكتالوج: Box + paint بدل Icon، والنص 12sp
                tabs.forEachIndexed { index, (painter, label) ->
                    LiquidBottomTab({ tab = index }) {
                        Box(
                            Modifier
                                .size(28f.dp)
                                .paint(painter, colorFilter = iconColorFilter)
                        )
                        BasicText(label, style = TextStyle(contentColor, 12f.sp))
                    }
                }
            }
        }

        // انيميشن دخول المحادثة: تنزلق من الجانب مع تلاشي وتكبير خفيف
        AnimatedVisibility(
            visible = open != null,
            enter = slideInHorizontally(spring(0.86f, 420f)) { it } + fadeIn(tween(180)) +
                scaleIn(spring(0.9f, 420f), initialScale = 0.94f),
            // خروج: المحادثة تصغر وتتدور حوافها وتنزلق للجنب وتتلاشى، والرئيسية ترجع من ورا
            exit = slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { (it * 0.55f).toInt() } +
                scaleOut(tween(340, easing = FastOutSlowInEasing), targetScale = 0.82f, transformOrigin = TransformOrigin(1f, 0.5f)) +
                fadeOut(tween(260, delayMillis = 60))
        ) {
            // 0 = ظاهرة بالكامل، 1 = داخلة/طالعة: نستعملها لتدوير الحواف بالتدريج
            val p by transition.animateFloat(
                transitionSpec = { tween(340, easing = FastOutSlowInEasing) },
                label = "chatEdge"
            ) { if (it == EnterExitState.Visible) 0f else 1f }
            lastChat?.let { c ->
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        shape = RoundedCornerShape((p * 44f).dp)
                        clip = p > 0.001f
                    }
                ) {
                    ChatScreen(other = c, onBack = { chatWith = null }, onOpenProfile = { viewProfile = c })
                }
            }
        }
        AnimatedVisibility(
            visible = profileOpen != null,
            enter = slideInHorizontally(spring(0.86f, 420f)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))
        ) {
            lastProfile?.let { p ->
                UserProfileScreen(
                    user = p,
                    onBack = { viewProfile = null },
                    onMessage = { viewProfile = null; chatWith = p }
                )
            }
        }

        // تأكيد حذف المحادثة (من عندي فقط)
        deleteChat?.let { (uid, name, lastAt) ->
            GlassConfirmDialog(
                backdrop = backdrop,
                dark = isSystemInDarkTheme(),
                title = "حذف المحادثة",
                message = "راح تنحذف محادثتك مع $name من عندك فقط. الطرف الثاني تبقى عنده المحادثة كاملة.",
                confirmText = "حذف",
                icon = Icons.Filled.Delete,
                onConfirm = { deleteChat = null; Repo.clearChat(myUid, uid, lastAt) },
                onDismiss = { deleteChat = null }
            )
        }

        // تأكيد تسجيل الخروج (Liquid Glass)
        if (confirmLogout) {
            GlassConfirmDialog(
                backdrop = backdrop,
                dark = isSystemInDarkTheme(),
                title = "تسجيل الخروج",
                message = "متأكد تريد تسجّل الخروج من حسابك؟ ما راح توصلك إشعارات الرسائل لين ترجع تسجّل دخول.",
                confirmText = "تسجيل الخروج",
                icon = AppIcons.Logout,
                onConfirm = { confirmLogout = false; Repo.signOut() },
                onDismiss = { confirmLogout = false }
            )
        }
    }
}

/** تبويب BDF: فارغ مؤقتاً */
@Composable
fun BdfTab() {
    Box(Modifier.fillMaxSize())
}
