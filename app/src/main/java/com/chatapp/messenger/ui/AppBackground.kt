package com.chatapp.messenger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.chatapp.messenger.ui.theme.appBackground

/** ألوان كل تبويب: Home / BDF / Account. تتبدل بسلاسة عند التنقل */
private val TabBlobs = listOf(
    listOf(Color(0xFF1AA7FF), Color(0xFF7A5CFF), Color(0xFF2EE6A8)), // Home
    listOf(Color(0xFFFF5C8A), Color(0xFFFFB347), Color(0xFF8A5CFF)), // BDF
    listOf(Color(0xFF2EE6A8), Color(0xFF1AA7FF), Color(0xFFFFD166))  // Account
)

/**
 * خلفية الواجهات الرئيسية: بقع ضوء ملونة تتحرك ببطء (نفس روح شاشة الدخول)
 * ولكل تبويب لون مختلف. توضع داخل layerBackdrop حتى يكسرها الزجاج.
 */
@Composable
fun AnimatedAppBackground(tab: Int, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val set = TabBlobs[tab.coerceIn(0, TabBlobs.lastIndex)]
    val c1 by animateColorAsState(set[0], tween(900), label = "b1")
    val c2 by animateColorAsState(set[1], tween(900), label = "b2")
    val c3 by animateColorAsState(set[2], tween(900), label = "b3")
    val t = rememberInfiniteTransition(label = "appBg")
    val phase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase"
    )
    val a = if (dark) 0.42f else 0.50f

    Box(
        modifier.background(appBackground()).drawBehind {
            fun blob(color: Color, cx: Float, cy: Float, r: Float) = drawBlob(color, cx, cy, r)
            val w = size.width
            val h = size.height
            blob(c1.copy(alpha = a), w * (0.10f + 0.35f * phase), h * (0.10f + 0.08f * phase), w * 0.75f)
            blob(c2.copy(alpha = a), w * (0.95f - 0.35f * phase), h * (0.45f + 0.10f * phase), w * 0.70f)
            blob(c3.copy(alpha = a), w * (0.20f + 0.30f * phase), h * (0.98f - 0.12f * phase), w * 0.80f)
        }
    )
}

private fun DrawScope.drawBlob(color: Color, cx: Float, cy: Float, r: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
        radius = r,
        center = Offset(cx, cy)
    )
}
