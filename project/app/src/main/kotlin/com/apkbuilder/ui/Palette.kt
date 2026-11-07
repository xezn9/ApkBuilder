package com.apkbuilder.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class NamedColor(val name: String, val color: Color)

/** لون التطبيق الحالي — يُقرأ من أي شاشة. */
val LocalAccent = compositionLocalOf { Color(0xFF4F7BFF) }

/** 20 لوناً لتطبيق (الواجهة + الخلفية). */
val AccentColors = listOf(
    NamedColor("أزرق", Color(0xFF4F7BFF)),
    NamedColor("أزرق ملكي", Color(0xFF2563EB)),
    NamedColor("أزرق سماوي", Color(0xFF0EA5E9)),
    NamedColor("سماوي", Color(0xFF22D3EE)),
    NamedColor("تركوازي", Color(0xFF14B8A6)),
    NamedColor("نعناعي", Color(0xFF34D399)),
    NamedColor("أخضر", Color(0xFF22C55E)),
    NamedColor("ليموني", Color(0xFF84CC16)),
    NamedColor("أصفر", Color(0xFFFACC15)),
    NamedColor("ذهبي", Color(0xFFD4A017)),
    NamedColor("كهرماني", Color(0xFFF59E0B)),
    NamedColor("برتقالي", Color(0xFFFB923C)),
    NamedColor("مرجاني", Color(0xFFFF6B57)),
    NamedColor("أحمر", Color(0xFFEF4444)),
    NamedColor("عنابي", Color(0xFFE11D48)),
    NamedColor("وردي", Color(0xFFEC4899)),
    NamedColor("فوشيا", Color(0xFFD946EF)),
    NamedColor("بنفسجي", Color(0xFFA855F7)),
    NamedColor("نيلي", Color(0xFF6366F1)),
    NamedColor("رمادي فولاذي", Color(0xFF94A3B8)),
)

/** 20 لوناً للشريط السفلي (أول لونين: داكن وفاتح مثل Backdrop Catalog). */
val BarColors = listOf(
    NamedColor("داكن", Color(0xFF121212)),
    NamedColor("فاتح", Color(0xFFFAFAFA)),
    NamedColor("أزرق", Color(0xFF3B6BFF)),
    NamedColor("أزرق سماوي", Color(0xFF0EA5E9)),
    NamedColor("سماوي", Color(0xFF22D3EE)),
    NamedColor("تركوازي", Color(0xFF14B8A6)),
    NamedColor("نعناعي", Color(0xFF34D399)),
    NamedColor("أخضر", Color(0xFF22C55E)),
    NamedColor("ليموني", Color(0xFF84CC16)),
    NamedColor("أصفر", Color(0xFFFACC15)),
    NamedColor("كهرماني", Color(0xFFF59E0B)),
    NamedColor("برتقالي", Color(0xFFFB923C)),
    NamedColor("مرجاني", Color(0xFFFF6B57)),
    NamedColor("أحمر", Color(0xFFEF4444)),
    NamedColor("عنابي", Color(0xFFE11D48)),
    NamedColor("وردي", Color(0xFFEC4899)),
    NamedColor("فوشيا", Color(0xFFD946EF)),
    NamedColor("بنفسجي", Color(0xFFA855F7)),
    NamedColor("نيلي", Color(0xFF6366F1)),
    NamedColor("رمادي", Color(0xFF64748B)),
)
