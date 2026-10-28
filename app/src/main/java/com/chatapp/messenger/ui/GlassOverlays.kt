package com.chatapp.messenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

private val DangerRed = Color(0xFFE5484D)

/** حركة دخول مشتركة للنوافذ الزجاجية: تعتيم الخلفية + تكبير نابضي */
@Composable
private fun rememberEnter(): Animatable<Float, AnimationVector1D> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
    return a
}

/** تأكيد (مثل تسجيل الخروج) بنافذة Liquid Glass في منتصف الشاشة */
@Composable
fun GlassConfirmDialog(
    backdrop: Backdrop,
    dark: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    confirmColor: Color = DangerRed,
    cancelText: String = "إلغاء"
) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    val enter = rememberEnter()
    val shape = RoundedCornerShape(32.dp)

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.40f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(28.dp).widthIn(max = 360.dp).fillMaxWidth()
                .graphicsLayer {
                    val s = 0.78f + 0.22f * enter.value
                    scaleX = s; scaleY = s
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.38f) else Color.White.copy(0.50f)) }
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
                    shape
                )
                // يمنع الضغط داخل النافذة من إغلاقها
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(confirmColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = confirmColor, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(14.dp))
            }
            Text(title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, color = fg.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    onClick = onDismiss, backdrop = backdrop, pressScale = 1.05f,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text(cancelText, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                GlassButton(
                    onClick = onConfirm, backdrop = backdrop, tint = confirmColor, pressScale = 1.05f,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text(confirmText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** خيار واحد بقائمة الرسالة */
class MsgAction(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)

/**
 * قائمة الضغطة المطولة على الرسالة (Liquid Glass).
 * [anchorTop]/[anchorBottom] حدود الفقاعة على الشاشة (px) و[screenHeight] ارتفاع الشاشة (px)
 * حتى نحط القائمة تحت الفقاعة أو فوقها حسب المساحة. [bubble] نسخة الفقاعة تُرسم فوق التعتيم.
 */
@Composable
fun GlassMessageMenu(
    backdrop: Backdrop,
    dark: Boolean,
    anchorTop: Float,
    anchorBottom: Float,
    anchorStart: Float,
    anchorEnd: Float,
    screenWidth: Float,
    screenHeight: Float,
    actions: List<MsgAction>,
    onDismiss: () -> Unit,
    bubble: @Composable () -> Unit
) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    val enter = rememberEnter()
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shape = RoundedCornerShape(26.dp)

    val menuW = with(density) { 230.dp.toPx() }
    val menuH = with(density) { (actions.size * 52 + 16).dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val margin = with(density) { 12.dp.toPx() }
    val below = anchorBottom + gap + menuH <= screenHeight - margin
    // إذا ما يكفي تحت ولا فوق (رسالة طويلة جداً) نحط القائمة بمنتصف الشاشة
    val above = anchorTop - gap - menuH >= margin * 4
    val y = when {
        below -> anchorBottom + gap
        above -> anchorTop - gap - menuH
        else -> (screenHeight - menuH) / 2f
    }
    val centerX = (anchorStart + anchorEnd) / 2f
    val x = (centerX - menuW / 2f).coerceIn(margin, (screenWidth - menuW - margin).coerceAtLeast(margin))
    val originY = if (below) 0f else 1f
    val realDir = LocalLayoutDirection.current

    // التموضع بالبكسل المطلق يحتاج LTR، ثم نرجّع اتجاه التطبيق للمحتوى نفسه
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        // نسخة الفقاعة "مرفوعة" فوق التعتيم بنفس مكانها
        Box(
            Modifier.graphicsLayer {
                translationX = anchorStart
                translationY = anchorTop
                val s = 1f + 0.04f * enter.value
                scaleX = s; scaleY = s
            }
        ) { CompositionLocalProvider(LocalLayoutDirection provides realDir) { bubble() } }

        Column(
            Modifier
                .graphicsLayer {
                    translationX = x
                    translationY = y
                    transformOrigin = TransformOrigin(0.5f, originY)
                    val s = 0.6f + 0.4f * enter.value
                    scaleX = s; scaleY = s
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .width(230.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.40f) else Color.White.copy(0.55f)) }
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
                    shape
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(8.dp)
        ) {
          CompositionLocalProvider(LocalLayoutDirection provides realDir) {
            actions.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            // نخلي حركة صغيرة قبل الإغلاق ثم ننفذ الخيار
                            scope.launch {
                                enter.animateTo(0f, tween(120))
                                a.onClick()
                            }
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(a.icon, null, tint = a.color, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(a.label, color = if (a.color == DangerRed) DangerRed else fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
          }
        }
    }
    }
}
