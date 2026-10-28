package com.chatapp.messenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// غيّر الألوان من هنا
private val Light = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    surfaceVariant = Color(0xFFE8EAED)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    surfaceVariant = Color(0xFF303134)
)

@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}

/** خلفية التطبيق (الزجاج يستخدمها ليعمل التأثير) */
@Composable
fun appBackground(): Brush =
    Brush.verticalGradient(
        if (isSystemInDarkTheme()) listOf(Color(0xFF0A2420), Color(0xFF0A1B3A))
        else listOf(Color(0xFFD9F7EE), Color(0xFFCFE4FF))
    )
