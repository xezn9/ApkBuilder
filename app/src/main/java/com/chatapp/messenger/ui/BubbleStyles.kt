package com.chatapp.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Blue = Color(0xFF1AA7FF)

/**
 * شكل فقاعة (مثل فقاعات تيك توك): شكل + ألوان + رسمة شخصية صغيرة (decor).
 * decor ترسم والشخصية على الجهة اليسرى من الفقاعة (الإحداثيات بالبكسل)، والنظام يعكسها حسب جهة الرسالة.
 * sidePad: مساحة محجوزة جوه الفقاعة للشخصية حتى ما تغطي النص.
 * topPad: مساحة فوق الفقاعة للأشياء الي تطلع من فوق (أذن، عنكبوت...).
 */
class BubbleStyle(
    val id: String,
    val name: String,
    val glass: Boolean = false,
    val shape: Shape = RoundedCornerShape(22.dp),
    val fill: List<Color> = emptyList(),
    val text: Color = Color.White,
    val border: Dp = 0.dp,
    val borderColor: Color = Color.Transparent,
    val sidePad: Dp = 0.dp,
    val topPad: Dp = 0.dp,
    val decor: ((DrawScope, Float, Float) -> Unit)? = null
)

// ---------------------------------------------------------------------------
// رسومات الشخصيات
// ---------------------------------------------------------------------------

private fun DrawScope.decorGhost(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cy = h / 2
    drawOval(Color.Black, Offset(9 * u, cy - 9 * u), Size(7 * u, 10 * u))
    drawOval(Color.Black, Offset(21 * u, cy - 9 * u), Size(7 * u, 10 * u))
    drawOval(Color.Black, Offset(14 * u, cy + 4 * u), Size(9 * u, 8 * u))
}

private fun DrawScope.decorPumpkin(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val rib = Color(0x33000000)
    val ink = Color(0xFF3B1A05)
    drawLine(rib, Offset(w * 0.3f, 5 * u), Offset(w * 0.3f, h - 5 * u), 2 * u, StrokeCap.Round)
    drawLine(rib, Offset(w * 0.7f, 5 * u), Offset(w * 0.7f, h - 5 * u), 2 * u, StrokeCap.Round)
    drawRoundRect(Color(0xFF3E8E41), Offset(w / 2 - 4 * u, -6 * u), Size(8 * u, 9 * u), CornerRadius(3 * u))
    drawRoundRect(ink, Offset(9 * u, h / 2 - 9 * u), Size(7 * u, 8 * u), CornerRadius(2 * u))
    drawRoundRect(ink, Offset(21 * u, h / 2 - 9 * u), Size(7 * u, 8 * u), CornerRadius(2 * u))
    drawArc(ink, 0f, 180f, true, Offset(11 * u, h / 2 + 1 * u), Size(14 * u, 9 * u))
}

private fun DrawScope.decorSpider(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val c = Color.White
    // شبكة عنكبوت بالزاوية العليا
    val o = Offset(w - 2 * u, 2 * u)
    fun pt(r: Float, deg: Double) = Offset(
        o.x + (r * cos(deg * PI / 180.0)).toFloat(),
        o.y + (r * sin(deg * PI / 180.0)).toFloat()
    )
    val rays = listOf(180.0, 150.0, 120.0, 90.0)
    rays.forEach { drawLine(c, o, pt(28 * u, it), 1.2f * u) }
    listOf(10 * u, 19 * u).forEach { r ->
        for (i in 0 until rays.size - 1) drawLine(c, pt(r, rays[i]), pt(r, rays[i + 1]), 1.2f * u)
    }
    // عنكبوت معلّق
    val x = 16 * u
    drawLine(c, Offset(x, -14 * u), Offset(x, 8 * u), 1.2f * u)
    for (k in 0 until 4) {
        val dy = (k - 1.5f) * 4 * u
        drawLine(c, Offset(x, 12 * u), Offset(x - 10 * u, 12 * u + dy), 1.2f * u)
        drawLine(c, Offset(x, 12 * u), Offset(x + 10 * u, 12 * u + dy), 1.2f * u)
    }
    drawCircle(c, 5 * u, Offset(x, 13 * u))
    drawCircle(c, 3 * u, Offset(x, 8 * u))
    drawCircle(Color.Black, 1f * u, Offset(x - 1.2f * u, 7.5f * u))
    drawCircle(Color.Black, 1f * u, Offset(x + 1.2f * u, 7.5f * u))
}

private fun DrawScope.decorSketch(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val blue = Color(0xFF29B6F6)
    drawRoundRect(
        blue, Offset(2 * u, 2 * u), Size(w - 4 * u, h - 4 * u), CornerRadius(14 * u),
        style = Stroke(2.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9 * u, 6 * u)))
    )
    drawArc(blue, 180f, 260f, false, Offset(-6 * u, -6 * u), Size(14 * u, 14 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
    drawArc(blue, 0f, 250f, false, Offset(w - 9 * u, h - 8 * u), Size(14 * u, 14 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
}

private fun DrawScope.decorOtter(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val bub = Color(0xFF5BC8EA)
    drawCircle(bub, 6 * u, Offset(w - 6 * u, 5 * u))
    drawCircle(Color.White.copy(alpha = 0.6f), 2 * u, Offset(w - 8 * u, 3 * u))
    drawCircle(bub, 4 * u, Offset(w * 0.6f, 3 * u))
    val brown = Color(0xFFA9703F)
    drawOval(brown, Offset(2 * u, h - 20 * u), Size(30 * u, 18 * u))
    drawCircle(brown, 3 * u, Offset(7 * u, h - 30 * u))
    drawCircle(brown, 3 * u, Offset(21 * u, h - 30 * u))
    drawCircle(brown, 9 * u, Offset(14 * u, h - 23 * u))
    drawOval(Color(0xFFEBD0A8), Offset(8 * u, h - 22 * u), Size(12 * u, 8 * u))
    drawCircle(Color.Black, 1.6f * u, Offset(10.5f * u, h - 25 * u))
    drawCircle(Color.Black, 1.6f * u, Offset(17.5f * u, h - 25 * u))
    drawOval(Color(0xFF3B1A05), Offset(12 * u, h - 22 * u), Size(4 * u, 3 * u))
}

private fun DrawScope.flower(cx: Float, cy: Float, r: Float, petal: Color, center: Color) {
    for (i in 0 until 5) {
        val a = (i * 72.0 - 90.0) * PI / 180.0
        drawCircle(petal, r * 0.5f, Offset(cx + (r * 0.55f * cos(a)).toFloat(), cy + (r * 0.55f * sin(a)).toFloat()))
    }
    drawCircle(center, r * 0.3f, Offset(cx, cy))
}

private fun DrawScope.decorFlowers(w: Float, h: Float) {
    val u = 1.dp.toPx()
    flower(5 * u, 5 * u, 8 * u, Color(0xFFD6336C), Color(0xFFFFD54F))
    flower(w - 6 * u, h - 5 * u, 8 * u, Color(0xFFD6336C), Color(0xFFFFD54F))
    flower(w - 4 * u, 8 * u, 4 * u, Color(0xFFE85D8E), Color(0xFFFFD54F))
}

private fun DrawScope.decorCapybara(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val dark = Color(0xFFB77B45)
    drawOval(dark, Offset(-4 * u, h / 2 - 15 * u), Size(36 * u, 30 * u))
    drawCircle(dark, 4 * u, Offset(22 * u, h / 2 - 15 * u))
    drawCircle(Color(0xFFFF8F00), 4 * u, Offset(10 * u, h / 2 - 16 * u))
    drawCircle(Color.Black, 2 * u, Offset(15 * u, h / 2 - 4 * u))
    drawOval(Color(0xFF4A2A12), Offset(-1 * u, h / 2 + 2 * u), Size(9 * u, 7 * u))
}

private fun DrawScope.decorDuckFrog(w: Float, h: Float) {
    val u = 1.dp.toPx()
    // ضفدع
    drawCircle(Color(0xFF5DBB63), 8 * u, Offset(w - 34 * u, 2 * u))
    drawCircle(Color.White, 3 * u, Offset(w - 38 * u, -3 * u))
    drawCircle(Color.White, 3 * u, Offset(w - 30 * u, -3 * u))
    drawCircle(Color.Black, 1.4f * u, Offset(w - 38 * u, -3 * u))
    drawCircle(Color.Black, 1.4f * u, Offset(w - 30 * u, -3 * u))
    // بطة
    drawCircle(Color(0xFFFFB300), 8 * u, Offset(w - 14 * u, 2 * u))
    drawOval(Color(0xFFFF7043), Offset(w - 9 * u, 0f), Size(9 * u, 5 * u))
    drawCircle(Color.Black, 1.5f * u, Offset(w - 16 * u, -1 * u))
    // غيمة صغيرة
    val cloud = Color(0xFFA5D4F5)
    drawCircle(cloud, 4 * u, Offset(6 * u, h - 3 * u))
    drawCircle(cloud, 5 * u, Offset(12 * u, h - 5 * u))
    drawCircle(cloud, 4 * u, Offset(18 * u, h - 3 * u))
}

private fun DrawScope.decorCat(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val ink = Color(0xFF3B2A1A)
    val cream = Color(0xFFFFF4E0)
    listOf(5 * u, w - 20 * u).forEach { x ->
        val ear = Path().apply {
            moveTo(x, 3 * u)
            lineTo(x + 7 * u, -9 * u)
            lineTo(x + 15 * u, 3 * u)
            close()
        }
        drawPath(ear, cream)
        drawPath(ear, ink, style = Stroke(2 * u))
    }
    drawCircle(ink, 2 * u, Offset(10 * u, h / 2 - 4 * u))
    drawCircle(ink, 2 * u, Offset(22 * u, h / 2 - 4 * u))
    drawOval(Color(0xFFF48FB1), Offset(14 * u, h / 2 + 1 * u), Size(4 * u, 3 * u))
    drawLine(ink, Offset(2 * u, h / 2 + 2 * u), Offset(9 * u, h / 2 + 3 * u), 1.2f * u)
    drawLine(ink, Offset(23 * u, h / 2 + 3 * u), Offset(30 * u, h / 2 + 2 * u), 1.2f * u)
}


// ---------------------------------------------------------------------------
// ستة أشكال جديدة: نيون، فضاء، قلوب، غيمة، ثلج، روبوت
// ---------------------------------------------------------------------------

/** نجمة لامعة بأربع رؤوس */
private fun DrawScope.sparkle(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx, cy, cx + r, cy)
        quadraticTo(cx, cy, cx, cy + r)
        quadraticTo(cx, cy, cx - r, cy)
        quadraticTo(cx, cy, cx, cy - r)
        close()
    }
    drawPath(p, color)
}

/** قلب: (x,y) الزاوية العليا اليسرى، s الحجم */
private fun DrawScope.heart(x: Float, y: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(x + s * 0.5f, y + s * 0.9f)
        cubicTo(x + s * 0.1f, y + s * 0.6f, x + s * 0.0f, y + s * 0.3f, x + s * 0.2f, y + s * 0.15f)
        cubicTo(x + s * 0.35f, y + s * 0.03f, x + s * 0.5f, y + s * 0.15f, x + s * 0.5f, y + s * 0.28f)
        cubicTo(x + s * 0.5f, y + s * 0.15f, x + s * 0.65f, y + s * 0.03f, x + s * 0.8f, y + s * 0.15f)
        cubicTo(x + s * 1.0f, y + s * 0.3f, x + s * 0.9f, y + s * 0.6f, x + s * 0.5f, y + s * 0.9f)
        close()
    }
    drawPath(p, color)
}

/** ندفة ثلج: ثلاث خطوط متقاطعة */
private fun DrawScope.snowflake(cx: Float, cy: Float, r: Float, color: Color, stroke: Float) {
    for (deg in listOf(0.0, 60.0, 120.0)) {
        val a = deg * PI / 180.0
        val dx = (r * cos(a)).toFloat()
        val dy = (r * sin(a)).toFloat()
        drawLine(color, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), stroke, StrokeCap.Round)
    }
    drawCircle(color, stroke * 0.9f, Offset(cx, cy))
}

private fun DrawScope.decorNeon(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val cyan = Color(0xFF00F0FF)
    val pink = Color(0xFFFF2BD6)
    val r = CornerRadius(20 * u)
    // هالة التوهج الخارجية ثم الإطار المتدرج الحاد
    drawRoundRect(cyan.copy(alpha = 0.16f), Offset(0f, 0f), Size(w, h), r, style = Stroke(9 * u))
    drawRoundRect(pink.copy(alpha = 0.18f), Offset(1 * u, 1 * u), Size(w - 2 * u, h - 2 * u), r, style = Stroke(5 * u))
    drawRoundRect(
        Brush.linearGradient(listOf(cyan, pink), Offset(0f, 0f), Offset(w, h)),
        Offset(1.2f * u, 1.2f * u), Size(w - 2.4f * u, h - 2.4f * u), r, style = Stroke(2.2f * u)
    )
    sparkle(w - 4 * u, 3 * u, 6 * u, Color.White)
    sparkle(6 * u, h - 2 * u, 4 * u, cyan)
}

private fun DrawScope.decorSpace(w: Float, h: Float) {
    val u = 1.dp.toPx()
    // نجوم صغيرة
    listOf(
        Offset(w * 0.42f, 6 * u) to 1.4f, Offset(w * 0.58f, h - 6 * u) to 1.2f,
        Offset(w * 0.72f, 8 * u) to 1.6f, Offset(w - 8 * u, h * 0.55f) to 1.3f,
        Offset(w * 0.5f, h * 0.5f + 9 * u) to 1.0f
    ).forEach { (o, rr) -> drawCircle(Color.White.copy(alpha = 0.85f), rr * u, o) }
    sparkle(w - 8 * u, 8 * u, 5 * u, Color(0xFFFFF3B0))
    sparkle(w * 0.3f, h - 5 * u, 3.5f * u, Color.White)
    // كوكب بحلقة
    val c = Offset(19 * u, h / 2)
    val ring = Color(0xFFFFE0A3)
    drawOval(ring.copy(alpha = 0.55f), Offset(c.x - 16 * u, c.y - 4.5f * u), Size(32 * u, 9 * u), style = Stroke(1.8f * u))
    drawCircle(Brush.linearGradient(listOf(Color(0xFFFFB25B), Color(0xFFE5532D)), Offset(c.x - 9 * u, c.y - 9 * u), Offset(c.x + 9 * u, c.y + 9 * u)), 9.5f * u, c)
    drawArc(ring, 0f, 180f, false, Offset(c.x - 16 * u, c.y - 4.5f * u), Size(32 * u, 9 * u), style = Stroke(1.8f * u))
}

private fun DrawScope.decorHearts(w: Float, h: Float) {
    val u = 1.dp.toPx()
    heart(-6 * u, -9 * u, 20 * u, Color(0xFFE0245E))
    heart(w - 12 * u, h - 13 * u, 16 * u, Color(0xFFFFD1DF))
    heart(w - 18 * u, -6 * u, 11 * u, Color(0xFFFFE3EC))
    heart(14 * u, h - 8 * u, 9 * u, Color(0xFFFFC2D6))
    drawCircle(Color.White.copy(alpha = 0.55f), 1.6f * u, Offset(w * 0.55f, 5 * u))
}

private fun DrawScope.decorCloud(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val white = Color.White
    // سحابة بارزة من أعلى الفقاعة
    val x = w * 0.42f
    drawCircle(white, 6 * u, Offset(x, 2 * u))
    drawCircle(white, 9 * u, Offset(x + 10 * u, -1 * u))
    drawCircle(white, 7 * u, Offset(x + 21 * u, 2 * u))
    drawRoundRect(white, Offset(x - 5 * u, 1 * u), Size(33 * u, 8 * u), CornerRadius(4 * u))
    // شمس صغيرة باليسار
    val c = Offset(17 * u, h / 2)
    for (i in 0 until 8) {
        val a = i * 45.0 * PI / 180.0
        drawLine(
            Color(0xFFFFC53D),
            Offset(c.x + (10 * u * cos(a)).toFloat(), c.y + (10 * u * sin(a)).toFloat()),
            Offset(c.x + (14 * u * cos(a)).toFloat(), c.y + (14 * u * sin(a)).toFloat()),
            1.8f * u, StrokeCap.Round
        )
    }
    drawCircle(Color(0xFFFFD54F), 7 * u, c)
    drawCircle(Color(0xFFFFF3B0), 3 * u, Offset(c.x - 2 * u, c.y - 2 * u))
    // سحابة صغيرة تحت اليمين
    drawCircle(white.copy(alpha = 0.9f), 4 * u, Offset(w - 16 * u, h - 2 * u))
    drawCircle(white.copy(alpha = 0.9f), 5 * u, Offset(w - 10 * u, h - 4 * u))
    drawCircle(white.copy(alpha = 0.9f), 4 * u, Offset(w - 4 * u, h - 2 * u))
}

private fun DrawScope.decorSnow(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val snow = Color.White
    val ice = Color(0xFF4FB0EE)
    // قبعة ثلج فوق الفقاعة مع قطرات
    drawRoundRect(snow, Offset(6 * u, -5 * u), Size(w * 0.55f, 10 * u), CornerRadius(5 * u))
    drawRoundRect(Color(0xFFBFE3FA), Offset(6 * u, -5 * u), Size(w * 0.55f, 10 * u), CornerRadius(5 * u), style = Stroke(1.2f * u))
    drawCircle(snow, 3.2f * u, Offset(14 * u, 6 * u))
    drawCircle(snow, 2.6f * u, Offset(w * 0.3f, 7 * u))
    drawCircle(snow, 3f * u, Offset(w * 0.5f, 6 * u))
    // ندف
    snowflake(17 * u, h / 2, 9.5f * u, ice, 1.7f * u)
    snowflake(w - 10 * u, h - 8 * u, 5.5f * u, ice.copy(alpha = 0.75f), 1.3f * u)
    drawCircle(ice.copy(alpha = 0.6f), 1.5f * u, Offset(w * 0.62f, h - 4 * u))
    drawCircle(ice.copy(alpha = 0.6f), 1.2f * u, Offset(w - 6 * u, 9 * u))
}

private fun DrawScope.decorRobot(w: Float, h: Float) {
    val u = 1.dp.toPx()
    val dark = Color(0xFF1E2A35)
    // هوائي
    drawLine(Color(0xFF4A5663), Offset(19 * u, 1 * u), Offset(19 * u, -8 * u), 2f * u, StrokeCap.Round)
    drawCircle(Color(0xFFFF4D4D), 3.6f * u, Offset(19 * u, -10 * u))
    drawCircle(Color.White.copy(alpha = 0.7f), 1.2f * u, Offset(18 * u, -11 * u))
    // شاشة العيون
    drawRoundRect(dark, Offset(6 * u, h / 2 - 10 * u), Size(26 * u, 20 * u), CornerRadius(6 * u))
    val glow = Color(0xFF3DF2FF)
    drawCircle(glow.copy(alpha = 0.28f), 6 * u, Offset(14 * u, h / 2 - 2 * u))
    drawCircle(glow.copy(alpha = 0.28f), 6 * u, Offset(24 * u, h / 2 - 2 * u))
    drawCircle(glow, 3.2f * u, Offset(14 * u, h / 2 - 2 * u))
    drawCircle(glow, 3.2f * u, Offset(24 * u, h / 2 - 2 * u))
    drawLine(glow, Offset(14 * u, h / 2 + 5 * u), Offset(24 * u, h / 2 + 5 * u), 1.6f * u, StrokeCap.Round)
    // براغي
    val screw = Color(0xFF5E6B79)
    listOf(Offset(w - 6 * u, 6 * u), Offset(w - 6 * u, h - 6 * u)).forEach {
        drawCircle(screw, 2.2f * u, it)
        drawLine(Color(0xFFD5DCE3), Offset(it.x - 1.2f * u, it.y), Offset(it.x + 1.2f * u, it.y), 0.9f * u)
    }
}

// ---------------------------------------------------------------------------
// قائمة الأشكال (16)
// ---------------------------------------------------------------------------

val BubbleStyles: List<BubbleStyle> = listOf(
    BubbleStyle(id = "glass", name = "زجاجي", glass = true),
    BubbleStyle(
        id = "ghost", name = "شبح", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color.White), text = Color(0xFF111111),
        border = 2.5.dp, borderColor = Color.Black, sidePad = 34.dp, decor = DrawScope::decorGhost
    ),
    BubbleStyle(
        id = "pumpkin", name = "يقطينة", shape = RoundedCornerShape(28.dp),
        fill = listOf(Color(0xFFFF9A2E), Color(0xFFE0620F)), text = Color.White,
        border = 2.dp, borderColor = Color(0xFFB84A0A), sidePad = 34.dp, topPad = 6.dp,
        decor = DrawScope::decorPumpkin
    ),
    BubbleStyle(
        id = "spider", name = "عنكبوت", shape = RoundedCornerShape(22.dp),
        fill = listOf(Color.Black), text = Color.White,
        border = 1.5.dp, borderColor = Color.White, topPad = 14.dp, decor = DrawScope::decorSpider
    ),
    BubbleStyle(
        id = "sketch", name = "رسم تخطيطي", shape = RoundedCornerShape(16.dp),
        fill = listOf(Color(0xFFD8F4FF)), text = Color(0xFF0B4A66), decor = DrawScope::decorSketch
    ),
    BubbleStyle(
        id = "otter", name = "ثعلب الماء", shape = RoundedCornerShape(10.dp),
        fill = listOf(Color(0xFF8FE0F2)), text = Color(0xFF0B3F4F), sidePad = 36.dp, decor = DrawScope::decorOtter
    ),
    BubbleStyle(
        id = "flowers", name = "وردي", shape = RoundedCornerShape(16.dp),
        fill = listOf(Color(0xFFF4B6C8)), text = Color(0xFF5A1730), decor = DrawScope::decorFlowers
    ),
    BubbleStyle(
        id = "capybara", name = "كابيبارا", shape = RoundedCornerShape(28.dp),
        fill = listOf(Color(0xFFD9A066)), text = Color(0xFF3B2410), sidePad = 34.dp, decor = DrawScope::decorCapybara
    ),
    BubbleStyle(
        id = "duckfrog", name = "ضفدع وبطة", shape = RoundedCornerShape(12.dp),
        fill = listOf(Color(0xFFFFF59D)), text = Color(0xFF4A3F00), topPad = 10.dp, decor = DrawScope::decorDuckFrog
    ),
    BubbleStyle(
        id = "cat", name = "قطة", shape = RoundedCornerShape(18.dp),
        fill = listOf(Color(0xFFFFF4E0)), text = Color(0xFF3B2A1A),
        border = 2.dp, borderColor = Color(0xFF3B2A1A), sidePad = 34.dp, topPad = 10.dp,
        decor = DrawScope::decorCat
    ),
    BubbleStyle(
        id = "neon", name = "نيون", shape = RoundedCornerShape(20.dp),
        fill = listOf(Color(0xFF0E0B24), Color(0xFF1A1145)), text = Color(0xFFE9FEFF),
        topPad = 4.dp, decor = DrawScope::decorNeon
    ),
    BubbleStyle(
        id = "space", name = "فضاء", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color(0xFF3A2480), Color(0xFF0C1B52)), text = Color.White,
        sidePad = 40.dp, decor = DrawScope::decorSpace
    ),
    BubbleStyle(
        id = "hearts", name = "قلوب", shape = RoundedCornerShape(24.dp),
        fill = listOf(Color(0xFFFF9DBA), Color(0xFFFF5C8A)), text = Color.White,
        topPad = 8.dp, decor = DrawScope::decorHearts
    ),
    BubbleStyle(
        id = "cloud", name = "غيمة", shape = RoundedCornerShape(26.dp),
        fill = listOf(Color(0xFFD3EEFF), Color(0xFF9FD3FA)), text = Color(0xFF0D3B66),
        sidePad = 34.dp, topPad = 10.dp, decor = DrawScope::decorCloud
    ),
    BubbleStyle(
        id = "snow", name = "ثلج", shape = RoundedCornerShape(18.dp),
        fill = listOf(Color(0xFFEFF9FF), Color(0xFFCDEBFF)), text = Color(0xFF0E3A5C),
        border = 1.5.dp, borderColor = Color(0xFFA6D6F5), sidePad = 34.dp, topPad = 6.dp,
        decor = DrawScope::decorSnow
    ),
    BubbleStyle(
        id = "robot", name = "روبوت", shape = RoundedCornerShape(10.dp),
        fill = listOf(Color(0xFFC3CEDA), Color(0xFF93A1B0)), text = Color(0xFF14202B),
        border = 2.dp, borderColor = Color(0xFF4A5663), sidePad = 38.dp, topPad = 12.dp,
        decor = DrawScope::decorRobot
    )
)

fun bubbleStyleById(id: String): BubbleStyle = BubbleStyles.firstOrNull { it.id == id } ?: BubbleStyles[0]

private fun List<Color>.toBrush(): Brush =
    if (size == 1) SolidColor(first()) else Brush.linearGradient(this)

/**
 * فقاعة بشكل مختار. [backdrop] = null يعني معاينة (بدون زجاج حقيقي).
 * [content] تستلم لون النص المناسب.
 */
@Composable
fun StyledBubble(
    style: BubbleStyle,
    mine: Boolean,
    dark: Boolean,
    fg: Color,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable (textColor: Color) -> Unit
) {
    val shape = style.shape
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // الشخصية تكون على الجهة البعيدة عن صورة صاحب الرسالة
    val charLeft = (mine && rtl) || (!mine && !rtl)
    val sp = style.sidePad
    val padLeft = if (charLeft) sp else 0.dp
    val padRight = if (!charLeft) sp else 0.dp
    val startPad = 14.dp + (if (rtl) padRight else padLeft)
    val endPad = 14.dp + (if (rtl) padLeft else padRight)

    val textColor = if (style.glass) (if (mine) Color.White else fg) else style.text

    var m: Modifier = modifier.padding(top = style.topPad)

    m = if (style.glass) {
        if (backdrop != null) {
            m.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                },
                onDrawSurface = {
                    if (mine) {
                        drawRect(Blue, blendMode = BlendMode.Hue)
                        drawRect(Blue.copy(alpha = 0.65f))
                    } else {
                        drawRect(if (dark) Color.White.copy(0.10f) else Color.White.copy(0.50f))
                    }
                }
            )
        } else {
            m.background(if (mine) Blue.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.18f), shape)
        }
    } else {
        if (style.fill.isNotEmpty()) m.background(style.fill.toBrush(), shape) else m
    }

    m = if (style.glass) {
        m.border(
            0.8.dp,
            Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
            shape
        )
    } else if (style.border > 0.dp) {
        m.border(style.border, style.borderColor, shape)
    } else m

    // رسمة الشخصية فوق الفقاعة (تنعكس إذا الفقاعة على الجهة الثانية)
    val d = style.decor
    if (d != null) {
        m = m.drawWithContent {
            drawContent()
            if (charLeft) {
                d(this, size.width, size.height)
            } else {
                withTransform({ scale(-1f, 1f, Offset(size.width / 2f, size.height / 2f)) }) {
                    d(this, size.width, size.height)
                }
            }
        }
    }

    Box(m.padding(start = startPad, end = endPad, top = 9.dp, bottom = 9.dp)) {
        content(textColor)
    }
}

/** نص عينة للمعاينة داخل قائمة الأشكال */
@Composable
fun BubbleSampleText(text: String, color: Color) {
    Text(text, color = color, fontSize = 14.sp)
}
