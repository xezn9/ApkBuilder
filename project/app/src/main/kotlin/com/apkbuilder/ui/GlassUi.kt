package com.apkbuilder.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow

/**
 * تأثير Liquid Glass الأصلي من مكتبة backdrop (io.github.kyant0:backdrop).
 * كل عنصر زجاجي في التطبيق يمر من هنا.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    radius: Dp = 32.dp,
    tint: Color = Color.White.copy(alpha = 0.10f),
    blurRadius: Dp = 8.dp,
    refraction: Boolean = false,
    vibrant: Boolean = true
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedCornerShape(radius) },
    effects = {
        if (vibrant) vibrancy()
        blur(blurRadius.toPx())
        if (refraction) lens(14.dp.toPx(), 28.dp.toPx())
    },
    // ظل داخلي خفيف يعطي سماكة الزجاج
    innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.22f) },
    // لمعة متدرجة: أفتح من الأعلى وأخف من الأسفل
    onDrawSurface = {
        drawRect(
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = (tint.alpha * 1.6f).coerceAtMost(1f)),
                    tint.copy(alpha = tint.alpha * 0.6f)
                )
            )
        )
    }
)

/** يدوّر درجة اللون (Hue) للحصول على ألوان متناسقة مع لون التطبيق. */
fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** خلفية ملوّنة متحركة — هي المصدر الذي تنكسر عليه الطبقة الزجاجية. */
@Composable
fun GlassScene(
    accent: Color,
    content: @Composable BoxScope.(backdrop: LayerBackdrop) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    // البقع الملونة تتحرك ببطء — الزجاج يعكسها فتبدو الواجهة حيّة
    val t = rememberInfiniteTransition(label = "scene")
    val a by t.animateFloat(
        0.1f, 0.9f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        0.9f, 0.1f,
        infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )

    Box(Modifier.fillMaxSize().background(Color(0xFF070B18))) {
        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1230), Color(0xFF0A0F22), Color(0xFF150A2B))))
            fun blob(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(
                brush = Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r, center = Offset(cx, cy)
            )
            val w = size.width; val h = size.height
            blob(w * (0.15f + 0.6f * a), h * (0.12f + 0.15f * b), w * 0.75f, accent.copy(alpha = 0.85f))
            blob(w * (0.85f - 0.5f * b), h * (0.42f + 0.1f * a), w * 0.65f, accent.shiftHue(70f).copy(alpha = 0.75f))
            blob(w * (0.2f + 0.4f * b), h * (0.78f - 0.12f * a), w * 0.7f, accent.shiftHue(-70f).copy(alpha = 0.55f))
            blob(w * (0.9f - 0.3f * a), h * 0.95f, w * 0.5f, accent.shiftHue(150f).copy(alpha = 0.45f))
        }
        content(backdrop)
    }
}

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    radius: Dp = 32.dp,
    tint: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .liquidGlass(backdrop, radius, tint, refraction = true)
            .padding(20.dp),
        content = content
    )
}

@Composable
fun GlassButton(
    backdrop: Backdrop,
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    enabled: Boolean = true,
    radius: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val tint = (accent ?: Color.White).copy(alpha = if (accent != null) 0.34f else 0.12f)

    Box(
        modifier = modifier
            .scale(scale)
            .liquidGlass(backdrop, radius, tint, refraction = true)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(22.dp))
            Text(
                text,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun GlassChip(@Suppress("UNUSED_PARAMETER") backdrop: Backdrop, text: String, tint: Color = Color.White.copy(alpha = 0.12f)) {
    Box(
        Modifier
            .background(tint, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** بطاقة شبه شفافة بدون معالجة backdrop — للعناصر الكثيرة أو المتحركة حتى لا يحدث لاق. */
@Composable
fun PlainCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    tint: Color = Color.Black.copy(alpha = 0.30f),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(tint, shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(16.dp),
        content = content
    )
}
