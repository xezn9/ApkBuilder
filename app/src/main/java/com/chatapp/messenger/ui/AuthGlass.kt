package com.chatapp.messenger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/** اللون الأزرق الرئيسي للتطبيق (نفس لون زر الإرسال بالمحادثة) */
val GlassAccent = Color(0xFF1AA7FF)

/** لون النص الأساسي فوق الزجاج */
fun glassFg(dark: Boolean): Color = if (dark) Color.White else Color(0xFF0D1B2A)

/**
 * هيكل الشاشات الزجاجية (تسجيل الدخول / إنشاء حساب / التفعيل):
 * خلفية فيها بقع ملونة تتحرك ببطء حتى يبين تأثير الزجاج، والمحتوى فوقها بالنص.
 * [content] يستلم الـ backdrop (الي يرسمه الزجاج) وهل الوضع غامق.
 */
@Composable
fun GlassAuthScaffold(content: @Composable ColumnScope.(backdrop: Backdrop, dark: Boolean) -> Unit) {
    val dark = isSystemInDarkTheme()
    val backdrop = rememberLayerBackdrop()
    val transition = rememberInfiniteTransition(label = "blobs")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase"
    )

    // pointerInput: حتى لو الشاشة فوق شاشة ثانية (مثل شاشة التحديث) ما تمر الضغطات للي تحتها
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        // طبقة الخلفية (هذي الي يشوفها الزجاج ويكسرها)
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(appBackground())
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val a = if (dark) 0.55f else 0.60f

                    fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color, Color.Transparent),
                                center = Offset(cx, cy),
                                radius = r
                            ),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                    }
                    blob(Color(0xFF1AA7FF).copy(alpha = a), w * (0.15f + 0.30f * phase), h * (0.12f + 0.06f * phase), w * 0.70f)
                    blob(Color(0xFF7A5CFF).copy(alpha = a), w * (0.95f - 0.30f * phase), h * (0.42f + 0.08f * phase), w * 0.65f)
                    blob(Color(0xFF2EE6A8).copy(alpha = a), w * (0.25f + 0.25f * phase), h * (0.95f - 0.10f * phase), w * 0.75f)
                }
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content(backdrop, dark)
        }
    }
}

/** لوحة زجاجية كبيرة (تحتوي الحقول والأزرار) */
fun Modifier.glassPanel(backdrop: Backdrop, dark: Boolean, radius: Dp = 32.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(16f.dp.toPx())
                lens(12f.dp.toPx(), 24f.dp.toPx())
            },
            onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.42f)) }
        )
        .border(
            0.8.dp,
            Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
            shape
        )
}

/** دائرة زجاجية بسيطة (للشعار) */
fun Modifier.glassCircle(backdrop: Backdrop, dark: Boolean): Modifier = this
    .drawBackdrop(
        backdrop = backdrop,
        shape = { Capsule() },
        effects = {
            vibrancy()
            blur(2f.dp.toPx())
            lens(12f.dp.toPx(), 24f.dp.toPx())
        },
        onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.35f)) }
    )
    .border(
        0.8.dp,
        Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.06f))),
        RoundedCornerShape(50)
    )

/** حقل إدخال زجاجي: أيقونة + نص. للباسوورد فيه زر إظهار/إخفاء */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    backdrop: Backdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
    onDone: () -> Unit = {}
) {
    val fg = glassFg(dark)
    val focus = LocalFocusManager.current
    var show by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.30f) else Color.White.copy(0.35f)) }
            )
            .border(
                0.8.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.06f))),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = fg.copy(alpha = 0.45f), fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 16.sp),
                cursorBrush = SolidColor(GlassAccent),
                visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focus.moveFocus(FocusDirection.Down) },
                    onDone = { focus.clearFocus(); onDone() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (password) {
            Spacer(Modifier.width(8.dp))
            Icon(
                AppIcons.Eye, null,
                tint = if (show) GlassAccent else fg.copy(alpha = 0.45f),
                modifier = Modifier.size(24.dp).clickable { show = !show }
            )
        }
    }
}
