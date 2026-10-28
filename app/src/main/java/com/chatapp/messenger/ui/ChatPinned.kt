package com.chatapp.messenger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Msg
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

private val PinGold = Color(0xFFFFC107)

/** نص مختصر للرسالة بسطر واحد */
fun msgPreview(m: Msg, max: Int = 70): String =
    if (m.deleted) "رسالة مَحذوفة" else m.text.replace('\n', ' ').take(max)

/**
 * شريط الرسائل المثبتة أعلى المحادثة (يظهر للطرفين):
 * - رسالة وحدة: يعرض نصها، والضغط ينقلك لها
 * - أكثر من رسالة: "الرسائل المثبتة (N)"، والضغط يفتح قائمة بكل المثبتة
 */
@Composable
fun PinnedBanner(
    backdrop: Backdrop,
    dark: Boolean,
    fg: Color,
    pinned: List<Msg>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // نتذكر آخر قائمة غير فارغة حتى تكمل حركة الاختفاء بنفس المحتوى
    val last = remember { arrayOfNulls<List<Msg>>(1) }
    if (pinned.isNotEmpty()) last[0] = pinned
    val shown = last[0] ?: pinned
    val shape = RoundedCornerShape(22.dp)

    AnimatedVisibility(
        visible = pinned.isNotEmpty(),
        modifier = modifier,
        enter = slideInVertically(spring(0.8f, 420f)) { -it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(160))
    ) {
        Row(
            Modifier.fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(10f.dp.toPx(), 20f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.34f) else Color.White.copy(0.45f)) }
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.06f))),
                    shape
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(PinGold.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) { Icon(AppIcons.Pin, null, tint = PinGold, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            // عند تغيّر العدد أو الرسالة الأحدث ينزلق النص بحركة
            AnimatedContent(
                targetState = shown.size to (shown.firstOrNull()?.id ?: ""),
                transitionSpec = {
                    (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(140)))
                },
                modifier = Modifier.weight(1f),
                label = "pinnedBannerText"
            ) { (count, _) ->
                Column {
                    Text(
                        if (count > 1) "الرسائل المثبتة ($count)" else "رسالة مثبتة",
                        color = PinGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        shown.firstOrNull()?.let { msgPreview(it) } ?: "",
                        color = fg.copy(alpha = 0.85f), fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** قائمة الرسائل المثبتة (تفتح عند الضغط على الشريط إذا فيه أكثر من رسالة) */
@Composable
fun PinnedMessagesSheet(
    backdrop: Backdrop,
    dark: Boolean,
    pinned: List<Msg>,
    nameOf: (Msg) -> String,
    onJump: (Msg) -> Unit,
    onUnpin: (Msg) -> Unit,
    onDismiss: () -> Unit
) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) }
    val shape = RoundedCornerShape(32.dp)

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.40f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(10.dp)
                .graphicsLayer { translationY = (1f - enter.value) * 160.dp.toPx() }
                .heightIn(max = 460.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.40f) else Color.White.copy(0.52f)) }
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
                    shape
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Pin, null, tint = PinGold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "الرسائل المثبتة (${pinned.size})", color = fg,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(fg.copy(alpha = 0.10f)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, null, tint = fg, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.size(10.dp))
            LazyColumn {
                items(pinned, key = { it.id }) { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(fg.copy(alpha = 0.08f))
                            .clickable { onJump(m) }
                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(nameOf(m), color = PinGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                m.text, color = fg, fontSize = 15.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { onUnpin(m) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Close, null, tint = fg.copy(alpha = 0.65f), modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}
