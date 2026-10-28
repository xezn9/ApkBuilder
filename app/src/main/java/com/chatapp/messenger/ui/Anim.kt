package com.chatapp.messenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * ظهور متتابع: كل عنصر يتأخر شوية عن اللي قبله ويطلع من تحت بنعومة (نبضة نابضية خفيفة).
 * [index] ترتيب العنصر، [step] الفرق بين عنصر وعنصر بالملي ثانية.
 */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    step: Long = 45L,
    startDelay: Long = 60L,
    content: @Composable () -> Unit
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(startDelay + index * step)
        a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 380f))
    }
    Box(
        modifier.graphicsLayer {
            alpha = a.value.coerceIn(0f, 1f)
            translationY = (1f - a.value) * 26.dp.toPx()
            val s = 0.94f + 0.06f * a.value
            scaleX = s; scaleY = s
        }
    ) { content() }
}
