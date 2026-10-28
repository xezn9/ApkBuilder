package com.chatapp.messenger.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.kyant.backdrop.effects.lens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import android.os.Build
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.CompositionLocalProvider
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.Story
import com.chatapp.messenger.STORY_MS
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val StoryBlue = Brush.sweepGradient(
    listOf(Color(0xFF0A84FF), Color(0xFF00E5FF), Color(0xFF0A84FF))
)
private val StoryColors = listOf(
    Color.White, Color.Black, Color(0xFFFFD60A), Color(0xFFFF453A),
    Color(0xFFFF2D92), Color(0xFF30D158), Color(0xFF00E5FF), Color(0xFFFF9F0A)
)

/** تبويب Account: ملف المستخدم (بانر، صورة، اسم، يوزر، نبذة) + القصة */
@Composable
fun AccountTab(onOverlayChange: (Boolean) -> Unit = {}) {
    val me = Repo.auth.currentUser
    val myUid = me?.uid ?: ""
    val profile by remember(myUid) { Repo.profileFlow(myUid) }.collectAsState(initial = Profile())
    val fallbackName = me?.displayName?.takeIf { it.isNotBlank() } ?: me?.email?.substringBefore("@") ?: ""
    val name = profile.name.ifBlank { fallbackName }
    val story = profile.story

    // القصة تنتهي وتنحذف بعد 24 ساعة بالضبط من وقت نشرها
    var storyActive by remember(story?.createdAt) {
        mutableStateOf(story != null && System.currentTimeMillis() < story.expiresAt)
    }
    LaunchedEffect(story?.createdAt) {
        if (story != null) {
            val wait = story.expiresAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            storyActive = false
            runCatching { Repo.deleteStory() }
        }
    }

    var showEdit by remember { mutableStateOf(false) }
    var showStoryEditor by remember { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }
    // نسخة ثابتة من القصة تبقى طول حركة الإغلاق (حتى لو انحذفت أو تغيّرت القصة الأصلية)
    var editorSeed by remember { mutableStateOf<Story?>(null) }
    var viewerSeed by remember { mutableStateOf<Story?>(null) }
    val scope = rememberCoroutineScope()

    // مواقع زر + والصورة الشخصية: منها تطلع الواجهات وإلها ترجع
    val rootView = LocalView.current
    var plusCenter by remember { mutableStateOf<Offset?>(null) }
    var avatarCenter by remember { mutableStateOf<Offset?>(null) }
    fun originOf(c: Offset?, fallback: TransformOrigin): TransformOrigin =
        if (c == null || rootView.width <= 0 || rootView.height <= 0) fallback
        else TransformOrigin(
            (c.x / rootView.width).coerceIn(0f, 1f),
            (c.y / rootView.height).coerceIn(0f, 1f)
        )

    // نخبر الشاشة الرئيسية حتى تخفي الشريط السفلي أثناء التعديل
    val overlay = showEdit || showStoryEditor || showViewer
    LaunchedEffect(overlay) { onOverlayChange(overlay) }
    DisposableEffect(Unit) { onDispose { onOverlayChange(false) } }

    val bannerImg = rememberB64Image(profile.banner)
    val avatarImg = rememberB64Image(profile.avatar)
    val gray = Color(0xFFB0B3B8)

    // الأحجام كلها نسبة من عرض الشاشة (مرجع التصميم: عرض 1440) حتى تطلع نفس الصورة بكل الأجهزة
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val u = maxWidth / 1440f
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // البانر مثل تيك توك: قصير، يبدأ من أعلى الشاشة (خلف شريط الحالة) ويذوب بالخلفية
            val bannerH = u * 620f + statusTop
            val glass = rememberLayerBackdrop()

            Box(Modifier.fillMaxSize().layerBackdrop(glass).background(appBackground())) {
                Box(
                    Modifier.fillMaxWidth().height(bannerH)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.6f to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    if (bannerImg != null) {
                        Image(bannerImg, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(listOf(Color(0xFF3B4A57), Color(0xFF14181D)))
                            )
                        )
                    }
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(0.30f),
                                0.5f to Color.Black.copy(0.05f),
                                1f to Color.Black.copy(0.25f)
                            )
                        )
                    )
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .heightIn(min = u * 900f)
                        .padding(bottom = 140.dp)
                        .navigationBarsPadding()
                ) {
                    // الأزرار العلوية: قائمة يسار، قلم يمين
                    Row(
                        Modifier.fillMaxWidth().padding(start = u * 22f, end = u * 20f, top = u * 20f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RoundIconButton(Icons.Filled.Menu, glass, u * 150f) { /* قائمة: لسا بدون وظيفة */ }
                        RoundIconButton(Icons.Filled.Edit, glass, u * 150f) { showEdit = true }
                    }
                    // الصورة الشخصية يسار
                    Box(Modifier.align(Alignment.TopStart).padding(start = u * 47f, top = u * 352f)) {
                        AvatarWithStory(
                            avatar = avatarImg,
                            hasStory = storyActive,
                            backdrop = glass,
                            u = u,
                            onAvatarClick = {
                                if (storyActive && story != null) {
                                    viewerSeed = story
                                    showViewer = true
                                }
                            },
                            onPlus = {
                                editorSeed = if (storyActive) story else null
                                showStoryEditor = true
                            },
                            onPlusPos = { plusCenter = it },
                            onAvatarPos = { avatarCenter = it }
                        )
                    }
                    // الاسم واليوزر يمين
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(end = u * 52f, top = u * 445f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            name, color = Color.White, fontSize = (u.value * 86f).sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.End
                        )
                        if (profile.username.isNotBlank()) {
                            Text(
                                "@${profile.username}", color = gray, fontSize = (u.value * 44f).sp,
                                fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = u * 8f)
                            )
                        }
                    }
                    // النبذة (بدون عنوان)
                    if (profile.bio.isNotBlank()) {
                        Text(
                            profile.bio, color = Color.White, fontSize = (u.value * 52f).sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .padding(top = u * 705f, end = u * 35f, start = u * 35f)
                        )
                    }
                }
            }
        }
    }

    // الشاشة تتفتح وتكبر من زر القلم (أعلى اليمين) وترجع تصغر إله لما تسكّرها
    val penOrigin = TransformOrigin(0.93f, 0.075f)
    AnimatedVisibility(
        visible = showEdit,
        enter = scaleIn(spring(dampingRatio = 0.84f, stiffness = 300f), initialScale = 0.03f, transformOrigin = penOrigin) +
            fadeIn(tween(200)),
        exit = scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.03f, transformOrigin = penOrigin) +
            fadeOut(tween(230, delayMillis = 40))
    ) {
        // الحواف تبدأ دائرية كبيرة (مثل الزر) وتتحول تدريجياً لشاشة كاملة
        val edge by transition.animateFloat(
            transitionSpec = { tween(320, easing = FastOutSlowInEasing) },
            label = "editEdge"
        ) { if (it == EnterExitState.Visible) 0f else 1f }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                shape = RoundedCornerShape((edge * 120f).dp)
                clip = edge > 0.001f
            }
        ) {
            EditProfileDialog(
                initName = name,
                initUsername = profile.username,
                initBio = profile.bio,
                initAvatar = profile.avatar,
                initBanner = profile.banner,
                onDismiss = { showEdit = false }
            )
        }
    }
    // شاشة نشر القصة: تطلع من زر + وترجع إله
    OriginOverlay(
        visible = showStoryEditor,
        origin = originOf(plusCenter, TransformOrigin(0.08f, 0.30f))
    ) {
        StoryEditorScreen(existing = editorSeed, onDismiss = { showStoryEditor = false })
    }
    // عرض القصة: يطلع من الصورة الشخصية ويرجع إلها
    OriginOverlay(
        visible = showViewer,
        origin = originOf(avatarCenter, TransformOrigin(0.18f, 0.27f)),
        startRadius = 200f
    ) {
        viewerSeed?.let { st ->
            StoryViewerScreen(
                story = st,
                onDismiss = { showViewer = false },
                onEdit = {
                    // نسكّر العارض أول، وبعدها يطلع المحرر من زر +
                    showViewer = false
                    scope.launch {
                        delay(330)
                        editorSeed = st
                        showStoryEditor = true
                    }
                }
            )
        }
    }
}

/** غلاف حركة: الشاشة تكبر من نقطة (زر) وترجع تصغر إلها، وحوافها تتحول من دائرية لشاشة كاملة */
@Composable
private fun OriginOverlay(
    visible: Boolean,
    origin: TransformOrigin,
    startRadius: Float = 120f,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.84f, stiffness = 300f), initialScale = 0.03f, transformOrigin = origin) +
            fadeIn(tween(200)),
        exit = scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.03f, transformOrigin = origin) +
            fadeOut(tween(230, delayMillis = 40))
    ) {
        val edge by transition.animateFloat(
            transitionSpec = { tween(320, easing = FastOutSlowInEasing) },
            label = "overlayEdge"
        ) { if (it == EnterExitState.Visible) 0f else 1f }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                shape = RoundedCornerShape((edge * startRadius).dp)
                clip = edge > 0.001f
            }
        ) { content() }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backdrop: Backdrop,
    size: Dp,
    onClick: () -> Unit
) {
    GlassButton(onClick = onClick, backdrop = backdrop, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

/** صورة الملف الشخصي: حلقة رمادية (زرقاء إذا فيه قصة) + زر + زجاجي لإضافة قصة */
@Composable
private fun AvatarWithStory(
    avatar: ImageBitmap?,
    hasStory: Boolean,
    backdrop: Backdrop,
    u: Dp,
    onAvatarClick: () -> Unit,
    onPlus: () -> Unit,
    onPlusPos: (Offset) -> Unit = {},
    onAvatarPos: (Offset) -> Unit = {}
) {
    Box(Modifier.size(u * 375f).onGloballyPositioned { onAvatarPos(it.boundsInRoot().center) }) {
        val ringW = u * 14f
        val ring = if (hasStory) Modifier.border(ringW, StoryBlue, CircleShape)
        else Modifier.border(ringW, Color(0xFF4F4F4F), CircleShape)
        Box(
            Modifier.fillMaxSize().then(ring).padding(ringW).background(Color.Black, CircleShape)
                .padding(u * 6f).clip(CircleShape)
                .background(Color(0xFF2B2F36)).clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(u * 160f))
            }
        }
        GlassButton(
            onClick = onPlus,
            backdrop = backdrop,
            tint = Color(0xFF1AA7FF),
            modifier = Modifier.align(Alignment.TopStart).offset(x = u * 15f, y = u * 271f)
                .size(u * 88f).border(u * 6f, Color.White, CircleShape)
                .onGloballyPositioned { onPlusPos(it.boundsInRoot().center) }
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(u * 52f))
        }
    }
}

/** يفتح معرض الصور، يضغط الصورة ويرجعها كنص base64 */
@Composable
private fun rememberImagePicker(maxSide: Int, quality: Int, onPicked: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cb by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b64 = ImageUtil.uriToB64(ctx, uri, maxSide, quality)
            if (b64 != null) cb(b64) else Toast.makeText(ctx, "تعذّر قراءة الصورة", Toast.LENGTH_SHORT).show()
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** خلفية الديالوجات: تدرج التطبيق + باكدروب تستخدمه الأزرار الزجاجية */
@Composable
private fun GlassDialogRoot(content: @Composable (Backdrop) -> Unit) {
    val bd = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(bd).background(appBackground()))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content(bd)
        }
    }
}

/** باج/حقل بستايل iOS 16 (بطاقة مدورة، عنوان صغير فوق القيمة) + زجاج سائل */
@Composable
private fun GlassField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(20.dp)
    val fg = MaterialTheme.colorScheme.onBackground
    Column(
        modifier.fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(if (dark) Color.White.copy(0.08f) else Color.White.copy(0.45f))
                }
            )
            .border(
                0.8.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.06f))),
                shape
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefix != null) Text(prefix, fontSize = 17.sp, color = fg.copy(alpha = 0.6f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = TextStyle(color = fg, fontSize = 17.sp),
                cursorBrush = SolidColor(Color(0xFF1AA7FF)),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ======================= تعديل الحساب (زر القلم) =======================

private val AccentBlue = Color(0xFF1AA7FF)
private val AccentViolet = Color(0xFF8A5CFF)
private val AccentMint = Color(0xFF00D4AA)

/** بطاقة زجاج سائل: تمويه + عدسة (انكسار الحواف) + حد لامع */
private fun Modifier.liquidCard(
    backdrop: Backdrop,
    shape: Shape,
    dark: Boolean,
    borderWidth: Dp = 0.9.dp,
    borderColor: Color? = null
): Modifier {
    val brush: Brush = if (borderColor != null) SolidColor(borderColor)
    else Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.08f)))
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(14f.dp.toPx())
                lens(10f.dp.toPx(), 20f.dp.toPx())
            },
            onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.26f) else Color.White.copy(0.42f)) }
        )
        .border(borderWidth, brush, shape)
}

/** خلفية الشاشة: تدرج التطبيق + صورة البانر الذايبة + فقاعات ألوان تتحرك ببطء، حتى يبين تأثير الزجاج */
@Composable
private fun LiquidScene(banner: ImageBitmap?, content: @Composable (Backdrop) -> Unit) {
    val bd = rememberLayerBackdrop()
    val t = rememberInfiniteTransition(label = "blobs")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blobsP"
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(bd).background(appBackground())) {
            if (banner != null) {
                Image(
                    banner, null,
                    Modifier.fillMaxWidth().height(360.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 0.85f }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    contentScale = ContentScale.Crop
                )
            }
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r),
                        r, Offset(cx, cy)
                    )
                }
                blob(AccentBlue, w * (0.15f + 0.5f * p), h * (0.30f + 0.10f * p), w * 0.65f)
                blob(AccentViolet, w * (0.85f - 0.45f * p), h * (0.58f - 0.10f * p), w * 0.60f)
                blob(AccentMint, w * (0.30f + 0.30f * p), h * (0.88f - 0.15f * p), w * 0.65f)
            }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content(bd)
        }
    }
}

/** حقل بزجاج سائل: أيقونة ملونة، عنوان صغير، عداد أحرف، وتوهج لما تضغط عليه */
@Composable
private fun LiquidField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop,
    tint: Color,
    maxLen: Int,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1
) {
    val dark = isSystemInDarkTheme()
    val fg = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(26.dp)
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val glow by animateFloatAsState(if (focused) 1f else 0f, spring(0.8f, 400f), label = "fieldGlow")
    val pop by animateFloatAsState(if (focused) 1.015f else 1f, spring(0.6f, 450f), label = "fieldPop")
    val labelColor by animateColorAsState(if (focused) tint else fg.copy(alpha = 0.6f), tween(200), label = "fieldLabel")

    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .liquidCard(
                backdrop, shape, dark,
                borderWidth = (0.9f + 1.1f * glow).dp,
                borderColor = if (glow > 0.01f) lerp(Color.White.copy(alpha = 0.30f), tint, glow) else null
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fr.requestFocus() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            else Text(iconText ?: "", color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = labelColor)
            Spacer(Modifier.height(3.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                textStyle = TextStyle(color = fg, fontSize = 17.sp),
                cursorBrush = SolidColor(tint),
                modifier = Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { focused = it.isFocused }
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${value.length}/$maxLen", fontSize = 11.sp,
            color = fg.copy(alpha = if (focused) 0.7f else 0.4f)
        )
    }
}

@Composable
private fun EditProfileDialog(
    initName: String,
    initUsername: String,
    initBio: String,
    initAvatar: String,
    initBanner: String,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val fg = MaterialTheme.colorScheme.onBackground
    var name by remember { mutableStateOf(initName) }
    var username by remember { mutableStateOf(initUsername) }
    var bio by remember { mutableStateOf(initBio) }
    var avatarB64 by remember { mutableStateOf(initAvatar) }
    var bannerB64 by remember { mutableStateOf(initBanner) }
    var saving by remember { mutableStateOf(false) }

    val pickBanner = rememberImagePicker(maxSide = 1000, quality = 70) { bannerB64 = it }
    var cropSrc by remember { mutableStateOf<String?>(null) }
    // نختار الصورة بدقة أعلى ثم المستخدم يحرّكها ويقص الجزء المطلوب
    val pickAvatar = rememberImagePicker(maxSide = 1000, quality = 85) { cropSrc = it }
    val banner = rememberB64Image(bannerB64)
    val avatar = rememberB64Image(avatarB64)

    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LiquidScene(banner) { bd ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // ---- الشريط العلوي ----
                StaggerIn(0, Modifier.fillMaxWidth(), startDelay = 120L) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("تعديل الحساب", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("خصّص ملفك الشخصي", fontSize = 12.sp, color = fg.copy(alpha = 0.6f))
                        }
                        GlassButton(
                            backdrop = bd, tint = AccentBlue, modifier = Modifier.height(48.dp),
                            enabled = !saving && name.isNotBlank(),
                            onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        Repo.saveProfileInfo(name.trim(), username.trim(), bio.trim(), avatarB64, bannerB64)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        saving = false
                                        Toast.makeText(ctx, "فشل الحفظ: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            if (!saving) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (saving) "..." else "حفظ", color = Color.White, fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = if (saving) 26.dp else 0.dp, end = 22.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- البانر + الصورة الشخصية ----
                StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 120L) {
                    Box(Modifier.fillMaxWidth().height(226.dp)) {
                        val bannerShape = RoundedCornerShape(32.dp)
                        Box(
                            Modifier.fillMaxWidth().height(172.dp)
                                .liquidCard(bd, bannerShape, dark)
                                .clip(bannerShape)
                                .clickable(onClick = pickBanner),
                            contentAlignment = Alignment.Center
                        ) {
                            if (banner != null) {
                                Image(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(
                                    Modifier.matchParentSize().background(
                                        Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(0.35f))
                                    )
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(AppIcons.Image, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text("اضغط لاختيار صورة البانر", fontSize = 14.sp, color = fg.copy(alpha = 0.75f))
                                }
                            }
                            Row(
                                Modifier.align(Alignment.TopEnd).padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (bannerB64.isNotEmpty()) {
                                    GlassButton(
                                        onClick = { bannerB64 = "" }, backdrop = bd, tint = Color(0xFFE5484D),
                                        modifier = Modifier.size(40.dp)
                                    ) { Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                                }
                                GlassButton(onClick = pickBanner, backdrop = bd, modifier = Modifier.height(40.dp)) {
                                    Icon(AppIcons.Image, null, tint = Color.White, modifier = Modifier.padding(start = 14.dp).size(18.dp))
                                    Text(
                                        if (bannerB64.isEmpty()) "إضافة" else "تغيير", color = Color.White,
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 6.dp, end = 16.dp)
                                    )
                                }
                            }
                        }

                        // الصورة الشخصية تتداخل مع أسفل البانر
                        Box(Modifier.align(Alignment.BottomStart).padding(start = 22.dp)) {
                            Box(
                                Modifier.size(108.dp)
                                    .border(
                                        3.dp,
                                        Brush.sweepGradient(listOf(AccentBlue, Color(0xFF00E5FF), AccentViolet, AccentBlue)),
                                        CircleShape
                                    )
                                    .padding(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B2F36))
                                    .clickable(onClick = pickAvatar),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(48.dp))
                            }
                            GlassButton(
                                onClick = pickAvatar, backdrop = bd, tint = AccentBlue,
                                modifier = Modifier.align(Alignment.BottomEnd).size(36.dp).border(2.5.dp, Color.White, CircleShape)
                            ) { Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                        }

                        if (avatarB64.isNotEmpty()) {
                            Row(
                                Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassButton(onClick = { cropSrc = avatarB64 }, backdrop = bd, modifier = Modifier.height(40.dp)) {
                                    Text("تحريك", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                GlassButton(
                                    onClick = { avatarB64 = "" }, backdrop = bd, tint = Color(0xFFE5484D),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("إزالة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                StaggerIn(2, Modifier.fillMaxWidth(), startDelay = 120L) {
                    LiquidField(
                        label = "اسم الحساب", value = name, backdrop = bd, tint = AccentBlue, maxLen = 30,
                        icon = Icons.Filled.Person,
                        onValueChange = { name = it.take(30) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                StaggerIn(3, Modifier.fillMaxWidth(), startDelay = 120L) {
                    LiquidField(
                        label = "اسم المستخدم (اليوزر)", value = username, backdrop = bd, tint = AccentViolet, maxLen = 24,
                        iconText = "@",
                        onValueChange = { v ->
                            username = v.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }.take(24)
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                StaggerIn(4, Modifier.fillMaxWidth(), startDelay = 120L) {
                    LiquidField(
                        label = "النبذة", value = bio, backdrop = bd, tint = AccentMint, maxLen = 150,
                        icon = AppIcons.ChatBubble, singleLine = false, maxLines = 4, minLines = 2,
                        onValueChange = { bio = it.take(150) }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        val src = cropSrc
        if (src != null) {
            AvatarCropOverlay(srcB64 = src, onCancel = { cropSrc = null }, onDone = { avatarB64 = it; cropSrc = null })
        }
    }
}

/** تحريك/تقريب الصورة داخل دائرة واختيار الجزء المطلوب (ضمن حدود الصورة) */
@Composable
private fun AvatarCropOverlay(srcB64: String, onCancel: () -> Unit, onDone: (String) -> Unit) {
    BackHandler(onBack = onCancel)
    val bmp = rememberB64Image(srcB64)
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var viewPx by remember { mutableFloatStateOf(0f) }

    // يمنع خروج الصورة عن حدود الدائرة
    fun clampOff(o: Offset, z: Float): Offset {
        val b = bmp ?: return Offset.Zero
        if (viewPx <= 0f) return o
        val s = kotlin.math.max(viewPx / b.width, viewPx / b.height) * z
        val mx = kotlin.math.max(0f, (b.width * s - viewPx) / 2f)
        val my = kotlin.math.max(0f, (b.height * s - viewPx) / 2f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        GlassDialogRoot { bd ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(onClick = onCancel, backdrop = bd, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, null)
                    }
                    Text("حرّك الصورة", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    GlassButton(
                        backdrop = bd, tint = Color(0xFF1AA7FF), modifier = Modifier.height(40.dp),
                        enabled = bmp != null,
                        onClick = {
                            val b = bmp
                            if (b != null && viewPx > 0f) {
                                val out = 400
                                val k = out / viewPx
                                val s = kotlin.math.max(viewPx / b.width, viewPx / b.height) * zoom
                                val left = (viewPx - b.width * s) / 2f + off.x
                                val top = (viewPx - b.height * s) / 2f + off.y
                                val result = android.graphics.Bitmap.createBitmap(out, out, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(result)
                                canvas.drawColor(android.graphics.Color.BLACK)
                                val m = android.graphics.Matrix()
                                m.postScale(s * k, s * k)
                                m.postTranslate(left * k, top * k)
                                canvas.drawBitmap(b.asAndroidBitmap(), m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                val os = java.io.ByteArrayOutputStream()
                                result.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, os)
                                onDone(android.util.Base64.encodeToString(os.toByteArray(), android.util.Base64.NO_WRAP))
                            }
                        }
                    ) { Text("تم", color = Color.White, modifier = Modifier.padding(horizontal = 22.dp)) }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth - 16.dp, maxHeight, 340.dp)
                    Box(
                        Modifier.size(side)
                            .onSizeChanged { viewPx = it.width.toFloat() }
                            .pointerInput(bmp) {
                                detectTransformGestures { _, pan, zm, _ ->
                                    val nz = (zoom * zm).coerceIn(1f, 4f)
                                    zoom = nz
                                    off = clampOff(off + pan, nz)
                                }
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2B2F36))) {
                            val b = bmp
                            if (b != null) {
                                val sz = size.width
                                val s = kotlin.math.max(sz / b.width, sz / b.height) * zoom
                                val dw = b.width * s
                                val dh = b.height * s
                                val left = (sz - dw) / 2f + off.x
                                val top = (sz - dh) / 2f + off.y
                                drawImage(
                                    b,
                                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
                                )
                            }
                        }
                        Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape))
                    }
                }
                Text(
                    "اسحب لتحريك الصورة، وقرّب بإصبعين للتكبير",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

// ======================= إضافة / تعديل / حذف القصة =======================

/** خلفيات القصة النصية (تدرجات). رقم الخلفية يتخزن بالقصة بحقل bg */
private val StoryBgs = listOf(
    listOf(Color(0xFF3B4A57), Color(0xFF14181D)),
    listOf(Color(0xFF8A5CFF), Color(0xFFFF2D92)),
    listOf(Color(0xFF0A84FF), Color(0xFF00E5FF)),
    listOf(Color(0xFFFF9F0A), Color(0xFFFF453A)),
    listOf(Color(0xFF30D158), Color(0xFF0B6E4F)),
    listOf(Color(0xFF1B1464), Color(0xFF6A11CB)),
    listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
    listOf(Color(0xFF0F2027), Color(0xFF2C5364))
)

private fun storyBgBrush(i: Int): Brush =
    Brush.linearGradient(StoryBgs[i.coerceIn(0, StoryBgs.lastIndex)])

/** حجم الخط يصغر تدريجياً كل ما طال النص (مثل تيك توك) */
private fun storyFontSize(len: Int) = when {
    len <= 30 -> 36.sp
    len <= 80 -> 30.sp
    len <= 140 -> 24.sp
    else -> 20.sp
}

private val StoryTextShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(0.45f), blurRadius = 10f)

/** دائرة اختيار (لون نص أو خلفية) */
@Composable
private fun StorySwatch(selected: Boolean, brush: Brush, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (selected) 1.14f else 1f, spring(0.6f, 400f), label = "swatch")
    Box(
        Modifier.size(38.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(CircleShape).background(brush)
            .border(
                if (selected) BorderStroke(3.dp, AccentBlue) else BorderStroke(1.dp, Color.White.copy(0.35f)),
                CircleShape
            )
            .clickable(onClick = onClick)
    )
}

/** شاشة نشر القصة: نص مباشرة على البطاقة مثل تيك توك + صورة اختيارية، بزجاج سائل */
@Composable
private fun StoryEditorScreen(existing: Story?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var color by remember { mutableStateOf(existing?.let { Color(it.color) } ?: Color.White) }
    var bg by remember { mutableStateOf(existing?.bg ?: 0) }
    var imageB64 by remember { mutableStateOf(existing?.image ?: "") }
    var busy by remember { mutableStateOf(false) }

    val pickImage = rememberImagePicker(maxSide = 1000, quality = 70) { imageB64 = it }
    val image = rememberB64Image(imageB64)
    val focus = remember { FocusRequester() }
    val canPublish = !busy && (text.isNotBlank() || imageB64.isNotEmpty())

    // القصة الجديدة: الكيبورد يطلع تلقائياً بعد ما تخلص حركة الفتح
    LaunchedEffect(Unit) {
        if (existing == null) {
            delay(450)
            runCatching { focus.requestFocus() }
        }
    }

    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LiquidScene(banner = null) { bd ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .padding(horizontal = 16.dp)
            ) {
                // الشريط العلوي: إغلاق + عنوان + نشر
                StaggerIn(0, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            if (existing == null) "إضافة قصة" else "تعديل القصة",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                        )
                        GlassButton(
                            backdrop = bd, tint = Color(0xFF1AA7FF),
                            modifier = Modifier.height(48.dp),
                            enabled = canPublish,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        // كل نشر (جديد أو تعديل) يبدأ مدة 24 ساعة من الآن
                                        Repo.saveStory(
                                            text.trim(), color.toArgb(), imageB64,
                                            System.currentTimeMillis(), bg
                                        )
                                        onDismiss()
                                    } catch (e: Exception) {
                                        busy = false
                                        Toast.makeText(ctx, "فشل النشر: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (busy) "..." else if (existing == null) "نشر" else "نشر من جديد",
                                color = Color.White, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 26.dp)
                            )
                        }
                    }
                }

                // بطاقة القصة (9:16): الكتابة تصير مباشرة عليها
                StaggerIn(1, Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val cardShape = RoundedCornerShape(34.dp)
                        Box(
                            Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                                .clip(cardShape)
                                .background(storyBgBrush(bg))
                                .border(
                                    0.9.dp,
                                    Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.08f))),
                                    cardShape
                                )
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    runCatching { focus.requestFocus() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it.take(200) },
                                textStyle = TextStyle(
                                    color = color, fontSize = storyFontSize(text.length),
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                    shadow = StoryTextShadow
                                ),
                                cursorBrush = SolidColor(color),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).focusRequester(focus),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        if (text.isEmpty()) {
                                            Text(
                                                "اكتب قصتك...", color = Color.White.copy(0.45f),
                                                fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                            Text(
                                "${text.length}/200", fontSize = 12.sp, color = Color.White.copy(0.7f),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                            )
                        }
                    }
                }

                // لوحة الأدوات (زجاج سائل)
                StaggerIn(2, Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    val fg = MaterialTheme.colorScheme.onBackground
                    Column(
                        Modifier.fillMaxWidth().liquidCard(bd, RoundedCornerShape(28.dp), dark).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("لون النص", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg.copy(0.65f))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 3.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StoryColors.forEach { c ->
                                StorySwatch(selected = c == color, brush = SolidColor(c)) { color = c }
                            }
                        }
                        Text("الخلفية", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg.copy(0.65f))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 3.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StoryBgs.indices.forEach { i ->
                                StorySwatch(selected = i == bg, brush = storyBgBrush(i)) { bg = i }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(onClick = pickImage, backdrop = bd, modifier = Modifier.height(44.dp)) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                                Text(
                                    if (imageB64.isEmpty()) "إضافة صورة" else "تغيير الصورة",
                                    modifier = Modifier.padding(start = 6.dp, end = 16.dp)
                                )
                            }
                            if (imageB64.isNotEmpty()) {
                                GlassButton(onClick = { imageB64 = "" }, backdrop = bd, modifier = Modifier.height(44.dp)) {
                                    Text("إزالة الصورة", modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** عنصر بقائمة الثلاث نقاط */
@Composable
private fun StoryMenuItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** عرض القصة بملء الشاشة: شريط الوقت المتبقي + زر ثلاث نقاط (تعديل ونشر من جديد / حذف) */
@Composable
private fun StoryViewerScreen(story: Story, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val image = rememberB64Image(story.image)
    val bd = rememberLayerBackdrop()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val remainMs = (story.expiresAt - now).coerceAtLeast(0L)
    val remainMin = remainMs / 60_000
    val frac = (remainMs.toFloat() / STORY_MS).coerceIn(0f, 1f)

    BackHandler {
        when {
            confirmDelete -> if (!deleting) confirmDelete = false
            menuOpen -> menuOpen = false
            else -> onDismiss()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // محتوى القصة (هذا اللي تتموّه خلفه الأزرار الزجاجية)
        Box(
            Modifier.fillMaxSize().layerBackdrop(bd).background(storyBgBrush(story.bg))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (menuOpen) menuOpen = false else onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (story.text.isNotBlank()) {
                Text(
                    story.text, color = Color(story.color), fontSize = storyFontSize(story.text.length),
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(28.dp),
                    style = TextStyle(shadow = StoryTextShadow)
                )
            }
        }
        // ظل خفيف أعلى الشاشة حتى تبين الأزرار
        Box(
            Modifier.fillMaxWidth().height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.45f), Color.Transparent)))
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(0.28f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(CircleShape).background(Color.White))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(onClick = onDismiss, backdrop = bd, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Text(
                    "تنتهي بعد ${remainMin / 60} س و ${remainMin % 60} د",
                    color = Color.White.copy(0.9f), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(onClick = { menuOpen = !menuOpen }, backdrop = bd, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }

        // قائمة الثلاث نقاط: تنفتح من الزر بحركة نابضية
        val menuOrigin = TransformOrigin(if (rtl) 0f else 1f, 0f)
        val menuShape = RoundedCornerShape(26.dp)
        AnimatedVisibility(
            visible = menuOpen,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 78.dp, start = 16.dp, end = 16.dp),
            enter = scaleIn(spring(0.78f, 420f), initialScale = 0.3f, transformOrigin = menuOrigin) + fadeIn(tween(150)),
            exit = scaleOut(tween(160), targetScale = 0.3f, transformOrigin = menuOrigin) + fadeOut(tween(120))
        ) {
            Column(
                Modifier.width(270.dp)
                    .liquidCard(bd, menuShape, dark = true)
                    .background(Color.Black.copy(0.22f), menuShape)
                    .padding(8.dp)
            ) {
                StoryMenuItem(Icons.Filled.Edit, "تعديل القصة ونشرها من جديد", Color.White) {
                    menuOpen = false
                    onEdit()
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(0.6.dp).background(Color.White.copy(0.18f)))
                StoryMenuItem(Icons.Filled.Delete, "حذف القصة", Color(0xFFFF6B61)) {
                    menuOpen = false
                    confirmDelete = true
                }
            }
        }

        if (confirmDelete) {
            GlassConfirmDialog(
                backdrop = bd,
                dark = true,
                title = "حذف القصة؟",
                message = "راح تنحذف قصتك نهائياً وما تگدر ترجعها.",
                confirmText = if (deleting) "..." else "حذف",
                icon = Icons.Filled.Delete,
                onConfirm = {
                    if (!deleting) {
                        deleting = true
                        scope.launch {
                            try {
                                Repo.deleteStory()
                                onDismiss()
                            } catch (e: Exception) {
                                deleting = false
                                confirmDelete = false
                                Toast.makeText(ctx, "فشل الحذف: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onDismiss = { if (!deleting) confirmDelete = false }
            )
        }
    }
}
