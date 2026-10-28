package com.chatapp.messenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

private val Blue = Color(0xFF1AA7FF)
private val Red = Color(0xFFE5484D)
private val Gold = Color(0xFFFFC107)

/** أسباب البلاغ. الـ id لازم يطابق firestore.rules و functions/index.js */
private val ReportReasons = listOf(
    "abuse" to "كلمات مسيئة",
    "violence" to "عنف ودموية",
    "suicide_kill" to "أفكار انتحار وقتل"
)

/**
 * لوحة إعدادات المحادثة بتصميم Liquid Glass (نفس زجاج التطبيق).
 * تُوضع فوق شاشة المحادثة، و[backdrop] هو نفس backdrop الأشرطة حتى يظهر الزجاج على المحادثة الحقيقية.
 */
@Composable
fun AnimatedVisibilityScope.ChatSettingsPanel(
    backdrop: Backdrop,
    dark: Boolean,
    otherName: String,
    bg: ChatBg,
    hasImage: Boolean,
    onBgChange: (ChatBg) -> Unit,
    onPickImage: () -> Unit,
    styleId: String,
    onStyleChange: (String) -> Unit,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    blockedByMe: Boolean,
    onToggleBlock: () -> Unit,
    onReport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    val defaultBg = appBackground()
    var page by remember { mutableStateOf("main") }

    BackHandler { if (page != "main") page = "main" else onDismiss() }

    // طبقة تعتيم: الضغط عليها يغلق اللوحة
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter)
                .animateEnterExit(
                    enter = slideInVertically(spring(dampingRatio = 0.74f, stiffness = 330f)) { it / 2 } +
                        scaleIn(spring(dampingRatio = 0.8f, stiffness = 360f), initialScale = 0.82f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                        fadeIn(tween(180)),
                    exit = slideOutVertically(tween(230)) { it / 2 } +
                        scaleOut(tween(230), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                        fadeOut(tween(170))
                )
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(10.dp)
                .heightIn(max = 600.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(32.dp) },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.38f) else Color.White.copy(0.50f)) }
                )
                .border(
                    0.8.dp,
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
                    RoundedCornerShape(32.dp)
                )
                // يمنع الضغط داخل اللوحة من إغلاقها
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                when (page) {
                    "main" -> {
                        StaggerIn(0, Modifier.fillMaxWidth(), startDelay = 40L) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("إعدادات المحادثة", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                GlassButton(onClick = onDismiss, backdrop = backdrop, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 40L) {
                            PanelRow(AppIcons.Image, "خلفية المحادثة", Blue, fg) { page = "bg" }
                        }
                        StaggerIn(2, Modifier.fillMaxWidth(), startDelay = 40L) {
                            PanelRow(AppIcons.ChatBubble, "فقاعات الرسائل", Color(0xFF8A5CFF), fg) { page = "bubbles" }
                        }
                        StaggerIn(3, Modifier.fillMaxWidth(), startDelay = 40L) {
                            PanelRow(Icons.Filled.Star, if (pinned) "إلغاء التثبيت" else "تثبيت كشخص مميز", Gold, fg) {
                                onTogglePin(); onDismiss()
                            }
                        }
                        StaggerIn(4, Modifier.fillMaxWidth(), startDelay = 40L) {
                            PanelRow(AppIcons.Block, if (blockedByMe) "إلغاء حظر $otherName" else "حظر $otherName", Red, fg) {
                                if (blockedByMe) { onToggleBlock(); onDismiss() } else page = "block"
                            }
                        }
                        StaggerIn(5, Modifier.fillMaxWidth(), startDelay = 40L) {
                            PanelRow(Icons.Filled.Warning, "إبلاغ عن $otherName", Color(0xFFFF9800), fg) { page = "report" }
                        }
                    }

                    "bg" -> {
                        SubHeader("خلفية المحادثة", backdrop) { page = "main" }
                        Text("الخلفية تنطبق على هذي المحادثة فقط", fontSize = 13.sp, color = fg.copy(alpha = 0.65f))
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BgTile("افتراضي", bg.kind == "default", fg, { Modifier.background(defaultBg) }) {
                                onBgChange(bg.copy(kind = "default"))
                            }
                            ChatBgPresets.forEach { p ->
                                BgTile(p.name, bg.kind == "preset" && bg.preset == p.id, fg, { Modifier.presetBackground(p) }) {
                                    onBgChange(bg.copy(kind = "preset", preset = p.id))
                                }
                            }
                            if (hasImage) {
                                BgTile("صورتي", bg.kind == "image", fg, { Modifier.background(Color(0xFF3A4048)) }, showImageIcon = true) {
                                    onBgChange(bg.copy(kind = "image"))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        GlassButton(
                            onClick = onPickImage, backdrop = backdrop, tint = Blue,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(AppIcons.Image, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار من المعرض", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("خلفية ضبابية", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("تمويه خفيف للخلفية", fontSize = 13.sp, color = fg.copy(alpha = 0.65f))
                            }
                            Switch(checked = bg.blur, onCheckedChange = { onBgChange(bg.copy(blur = it)) })
                        }
                    }

                    "bubbles" -> {
                        SubHeader("فقاعات الرسائل", backdrop) { page = "main" }
                        Text("الشكل ينطبق على كل محادثاتك ويظهر عند الطرفين", fontSize = 13.sp, color = fg.copy(alpha = 0.65f))
                        Spacer(Modifier.height(12.dp))
                        BubbleStyles.chunked(2).forEachIndexed { rowIdx, row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEachIndexed { colIdx, st ->
                                    StaggerIn(rowIdx * 2 + colIdx, Modifier.weight(1f), step = 30L, startDelay = 30L) {
                                        BubbleTile(st, st.id == styleId, fg) { onStyleChange(st.id) }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    "block" -> {
                        SubHeader("حظر $otherName؟", backdrop) { page = "main" }
                        Text(
                            "ما راح يقدر يراسلك ولا يشوف ملفك الشخصي، إلى أن تلغي الحظر.",
                            fontSize = 15.sp, color = fg.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                onClick = { page = "main" }, backdrop = backdrop,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("إلغاء", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                            GlassButton(
                                onClick = { onToggleBlock(); onDismiss() }, backdrop = backdrop, tint = Red,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(AppIcons.Block, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("حظر", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    "report" -> {
                        var selected by remember { mutableStateOf<String?>(null) }
                        SubHeader("إبلاغ عن $otherName", backdrop) { page = "main" }
                        Text(
                            "اختر سبب البلاغ. سيتم فحص الرسائل ومعاقبة المستخدم إذا ثبت الخطأ.",
                            fontSize = 13.sp, color = fg.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.height(10.dp))
                        ReportReasons.forEach { (id, label) ->
                            val sel = selected == id
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (sel) Blue.copy(alpha = 0.22f) else fg.copy(alpha = 0.08f))
                                    .border(1.dp, if (sel) Blue else Color.Transparent, RoundedCornerShape(18.dp))
                                    .clickable { selected = id }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                if (sel) Icon(Icons.Filled.Check, null, tint = Blue, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassButton(
                            onClick = { selected?.let { onReport(it) } },
                            backdrop = backdrop, tint = Red, enabled = selected != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("إرسال البلاغ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(icon: ImageVector, title: String, tint: Color, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fg.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SubHeader(title: String, backdrop: Backdrop, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassButton(onClick = onBack, backdrop = backdrop, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}

@Composable
private fun BgTile(
    label: String,
    selected: Boolean,
    fg: Color,
    background: () -> Modifier,
    showImageIcon: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 64.dp, height = 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(background())
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Blue else Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (showImageIcon) Icon(AppIcons.Image, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = if (selected) Blue else fg.copy(alpha = 0.75f))
    }
}

@Composable
private fun BubbleTile(style: BubbleStyle, selected: Boolean, fg: Color, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1C2733).copy(alpha = 0.75f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Blue else Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StyledBubble(style, mine = false, dark = true, fg = Color.White, backdrop = null) {
            BubbleSampleText("مرحبا", it)
        }
        Spacer(Modifier.height(8.dp))
        Text(style.name, fontSize = 13.sp, color = if (selected) Blue else Color.White.copy(alpha = 0.85f))
    }
}
