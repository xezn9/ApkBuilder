package com.chatapp.messenger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.chatapp.messenger.ui.theme.appBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** إعدادات خلفية محادثة واحدة. kind: default | preset | image */
data class ChatBg(
    val kind: String = "default",
    val preset: String = "",
    val blur: Boolean = false,
    val version: Long = 0L
) {
    /** الخلفيات المخصصة غامقة دائماً، فنخلي النصوص بيضاء */
    val custom: Boolean get() = kind != "default"
}

class ChatBgPreset(
    val id: String,
    val name: String,
    val top: Color,
    val bottom: Color,
    /** دوائر زخرفية: (مركز x, مركز y, نصف القطر) كنسب من العرض + اللون */
    val circles: List<Triple<Offset, Float, Color>>
)

val ChatBgPresets: List<ChatBgPreset> = listOf(
    ChatBgPreset(
        "night", "ليل", Color(0xFF0B1026), Color(0xFF1B2A5A),
        listOf(
            Triple(Offset(0.85f, 0.15f), 0.45f, Color(0xFF4B6BFF).copy(alpha = 0.22f)),
            Triple(Offset(0.1f, 0.8f), 0.55f, Color(0xFF7A5CFF).copy(alpha = 0.18f)),
            Triple(Offset(0.6f, 0.5f), 0.2f, Color(0xFFFFFFFF).copy(alpha = 0.05f))
        )
    ),
    ChatBgPreset(
        "sunset", "غروب", Color(0xFF3B1240), Color(0xFFC2476A),
        listOf(
            Triple(Offset(0.8f, 0.2f), 0.5f, Color(0xFFFFB347).copy(alpha = 0.22f)),
            Triple(Offset(0.15f, 0.7f), 0.4f, Color(0xFFFF6A88).copy(alpha = 0.20f)),
            Triple(Offset(0.7f, 0.85f), 0.25f, Color(0xFFFFFFFF).copy(alpha = 0.06f))
        )
    ),
    ChatBgPreset(
        "forest", "غابة", Color(0xFF06241B), Color(0xFF14614A),
        listOf(
            Triple(Offset(0.2f, 0.2f), 0.5f, Color(0xFF3DDC97).copy(alpha = 0.16f)),
            Triple(Offset(0.9f, 0.6f), 0.45f, Color(0xFF1FA85A).copy(alpha = 0.18f)),
            Triple(Offset(0.4f, 0.9f), 0.25f, Color(0xFFFFFFFF).copy(alpha = 0.05f))
        )
    ),
    ChatBgPreset(
        "ocean", "محيط", Color(0xFF041C32), Color(0xFF0F6C8C),
        listOf(
            Triple(Offset(0.85f, 0.25f), 0.5f, Color(0xFF29B6F6).copy(alpha = 0.20f)),
            Triple(Offset(0.1f, 0.65f), 0.5f, Color(0xFF00E5FF).copy(alpha = 0.13f)),
            Triple(Offset(0.55f, 0.95f), 0.3f, Color(0xFFFFFFFF).copy(alpha = 0.05f))
        )
    ),
    ChatBgPreset(
        "violet", "ليلكي", Color(0xFF1E0B3A), Color(0xFF6A2FB0),
        listOf(
            Triple(Offset(0.15f, 0.2f), 0.5f, Color(0xFFD16BFF).copy(alpha = 0.20f)),
            Triple(Offset(0.85f, 0.7f), 0.5f, Color(0xFF8A5CFF).copy(alpha = 0.22f)),
            Triple(Offset(0.5f, 0.45f), 0.2f, Color(0xFFFFFFFF).copy(alpha = 0.05f))
        )
    )
)

fun bgPresetById(id: String): ChatBgPreset = ChatBgPresets.firstOrNull { it.id == id } ?: ChatBgPresets[0]

/** رسم خلفية preset (يستخدم بالمحادثة وبمعاينة الاختيار) */
fun Modifier.presetBackground(p: ChatBgPreset): Modifier =
    this.background(Brush.verticalGradient(listOf(p.top, p.bottom))).drawBehind {
        p.circles.forEach { (c, r, color) ->
            drawCircle(color, radius = size.width * r, center = Offset(size.width * c.x, size.height * c.y))
        }
    }

/** تخزين محلي لخلفية كل محادثة (خاصة بالمستخدم نفسه). الصورة ملف JPEG داخل التطبيق */
class ChatPrefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("chat_bg", Context.MODE_PRIVATE)
    private fun key(me: String, other: String) = "${me}_$other"

    fun load(me: String, other: String): ChatBg {
        val k = key(me, other)
        return ChatBg(
            kind = sp.getString("$k.kind", "default") ?: "default",
            preset = sp.getString("$k.preset", "") ?: "",
            blur = sp.getBoolean("$k.blur", false),
            version = sp.getLong("$k.ver", 0L)
        )
    }

    fun save(me: String, other: String, bg: ChatBg) {
        val k = key(me, other)
        sp.edit()
            .putString("$k.kind", bg.kind)
            .putString("$k.preset", bg.preset)
            .putBoolean("$k.blur", bg.blur)
            .putLong("$k.ver", bg.version)
            .apply()
    }

    fun imageFile(me: String, other: String): File = File(ctx.filesDir, "chatbg_${key(me, other)}.jpg")
    fun hasImage(me: String, other: String): Boolean = imageFile(me, other).exists()
}

/** يحمّل صورة الخلفية من الملف. softBlur: تمويه تقريبي للأجهزة الأقدم من أندرويد 12 */
@Composable
private fun rememberFileImage(path: String, version: Long, softBlur: Boolean): ImageBitmap? {
    val img by produceState<ImageBitmap?>(null, path, version, softBlur) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                var bmp = BitmapFactory.decodeFile(path) ?: return@runCatching null
                if (softBlur) {
                    // تصغير شديد ثم تكبير بفلتر ناعم = ضباب خفيف
                    bmp = Bitmap.createScaledBitmap(
                        bmp, max(1, bmp.width / 14), max(1, bmp.height / 14), true
                    )
                }
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    return img
}

/** خلفية المحادثة كاملة (تُوضع داخل layerBackdrop حتى يشتغل الزجاج فوقها) */
@Composable
fun ChatBackground(bg: ChatBg, imageFile: File, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // ضباب معتدل وليس قوي
    val blurMod = if (bg.blur && canBlur) Modifier.blur(10.dp, BlurredEdgeTreatment.Unbounded) else Modifier

    Box(modifier.clipToBounds()) {
        when (bg.kind) {
            "image" -> {
                val img = rememberFileImage(imageFile.path, bg.version, softBlur = bg.blur && !canBlur)
                if (img != null) {
                    Image(
                        img, null,
                        modifier = Modifier.fillMaxSize().then(blurMod),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Medium
                    )
                    // تعتيم خفيف حتى تبقى الرسائل مقروءة
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                } else {
                    Box(Modifier.fillMaxSize().background(appBackground()))
                }
            }
            "preset" -> Box(Modifier.fillMaxSize().then(blurMod).presetBackground(bgPresetById(bg.preset)))
            else -> Box(
                Modifier.fillMaxSize().then(blurMod).background(appBackground()).drawBehind {
                    val r = size.width * 0.75f
                    val blue = Color(0xFF1AA7FF)
                    val c1 = Offset(size.width * 0.9f, size.height * 0.15f)
                    val c2 = Offset(size.width * 0.1f, size.height * 0.75f)
                    drawCircle(
                        Brush.radialGradient(listOf(blue.copy(alpha = if (dark) 0.35f else 0.25f), Color.Transparent), c1, r),
                        radius = r, center = c1
                    )
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xFF8A5CFF).copy(alpha = if (dark) 0.30f else 0.20f), Color.Transparent), c2, r),
                        radius = r, center = c2
                    )
                }
            )
        }
    }
}
