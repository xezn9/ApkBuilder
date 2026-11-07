package com.apkbuilder.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkbuilder.engine.Phase
import kotlinx.coroutines.delay

/** مؤشر انتظار أثناء البناء: حلقتان متدرجتان + نواة تنبض + مراحل + مؤقت. */
@Composable
fun BuildLoader(phase: Phase, status: String, startedAt: Long, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val t = rememberInfiniteTransition(label = "loader")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "r1")
    val rot2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "r2")
    val pulse by t.animateFloat(
        0.8f, 1.05f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
    )

    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsed = (System.currentTimeMillis() - startedAt) / 1000
            delay(1000)
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(128.dp)) {
            val c = center
            val r = size.minDimension / 2
            val core = r * 0.55f * pulse
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), accent.copy(alpha = 0.55f), Color.Transparent), c, core * 1.6f),
                radius = core * 1.6f, center = c
            )
            val w1 = 6.dp.toPx()
            rotate(rot, c) {
                drawArc(
                    brush = Brush.sweepGradient(0f to Color.Transparent, 0.55f to accent, 0.75f to Color.White, center = c),
                    startAngle = 0f, sweepAngle = 270f, useCenter = false,
                    topLeft = Offset(w1, w1), size = Size(size.width - 2 * w1, size.height - 2 * w1),
                    style = Stroke(w1, cap = StrokeCap.Round)
                )
            }
            val inset = r * 0.28f
            val w2 = 4.dp.toPx()
            rotate(rot2, c) {
                drawArc(
                    brush = Brush.sweepGradient(0f to Color.Transparent, 0.6f to Color(0xFFB04BFF), 0.8f to Color(0xFF16D6B0), center = c),
                    startAngle = 0f, sweepAngle = 220f, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    style = Stroke(w2, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            "%02d:%02d".format(elapsed / 60, elapsed % 60),
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(status, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)

        val index = when (phase) { Phase.UPLOADING -> 0; Phase.QUEUED -> 1; Phase.BUILDING -> 2; else -> 3 }
        Row(
            Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("الرفع", "الانتظار", "البناء", "التنزيل").forEachIndexed { i, label ->
                val state = when { i < index -> 2; i == index -> 1; else -> 0 }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .alpha(if (state == 1) pulse else if (state == 2) 1f else 0.3f)
                            .background(if (state == 2) Color(0xFF3DDC97) else Color.White, CircleShape)
                    )
                    Text(
                        label, fontSize = 12.sp,
                        color = Color.White.copy(alpha = if (state == 0) 0.45f else 1f),
                        fontWeight = if (state == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Text(
            "البناء على GitHub قد يستغرق عدة دقائق — أبقِ التطبيق مفتوحاً",
            color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp
        )
    }
}
