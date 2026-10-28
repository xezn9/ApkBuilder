package com.chatapp.messenger.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** أقصى مدة للرسالة الصوتية (حتى يبقى حجمها صغير بوثيقة Firestore) */
const val VOICE_MAX_MS = 120_000L

/** أقل مدة مقبولة (أقصر من هذا يعتبر ضغطة بالغلط) */
const val VOICE_MIN_MS = 700L

class VoiceResult(val b64: String, val durationMs: Long, val wave: String)

/** عدد أعمدة الموجة بالفقاعة */
const val WAVE_BARS = 30

/** تسجيل صوت AAC/m4a أحادي + حفظ شكل الموجة (مستوى الصوت) لعرضه بالفقاعة */
class VoiceRecorder {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val amps = ArrayList<Int>()

    val isRecording get() = rec != null
    fun elapsedMs(): Long = if (rec == null) 0L else System.currentTimeMillis() - startedAt

    /** تُستدعى كل ~100ms أثناء التسجيل لجمع مستوى الصوت */
    fun sample() {
        val r = rec ?: return
        try { amps.add(r.maxAmplitude) } catch (_: Exception) {}
    }

    fun start(ctx: Context): Boolean {
        if (rec != null) return true
        amps.clear()
        val sources = intArrayOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        for (src in sources) {
            val f = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
            try {
                val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
                r.setAudioSource(src)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioChannels(1)
                r.setAudioSamplingRate(44100)
                r.setAudioEncodingBitRate(64000)
                r.setMaxDuration(VOICE_MAX_MS.toInt())
                r.setOutputFile(f.absolutePath)
                r.prepare()
                r.start()
                rec = r; file = f; startedAt = System.currentTimeMillis()
                return true
            } catch (e: Exception) {
                release(); f.delete()
            }
        }
        return false
    }

    /** يوقف التسجيل ويرجع الصوت كنص base64 (null إذا فشل) */
    fun stop(): VoiceResult? {
        val r = rec ?: return null
        val f = file
        val dur = System.currentTimeMillis() - startedAt
        val ok = try { r.stop(); true } catch (e: Exception) { false }
        release()
        if (!ok || f == null || !f.exists()) { f?.delete(); return null }
        val bytes = try { f.readBytes() } catch (e: Exception) { null }
        f.delete()
        if (bytes == null || bytes.isEmpty()) return null
        return VoiceResult(
            Base64.encodeToString(bytes, Base64.NO_WRAP),
            dur.coerceAtMost(VOICE_MAX_MS),
            buildWave()
        )
    }

    /** يحوّل عينات المستوى إلى WAVE_BARS حرف (a..z) كل حرف = ارتفاع عمود */
    private fun buildWave(): String {
        if (amps.isEmpty()) return ""
        val peak = (amps.maxOrNull() ?: 0).coerceAtLeast(1)
        val sb = StringBuilder()
        for (i in 0 until WAVE_BARS) {
            val from = i * amps.size / WAVE_BARS
            val to = ((i + 1) * amps.size / WAVE_BARS).coerceAtLeast(from + 1).coerceAtMost(amps.size)
            var m = 0
            for (k in from until to) if (amps[k] > m) m = amps[k]
            val lvl = Math.sqrt(m.toDouble() / peak).times(25).toInt().coerceIn(0, 25)
            sb.append('a' + lvl)
        }
        return sb.toString()
    }

    fun cancel() {
        val f = file
        try { rec?.stop() } catch (_: Exception) {}
        release()
        f?.delete()
    }

    private fun release() {
        try { rec?.release() } catch (_: Exception) {}
        rec = null; file = null
    }
}

/** مشغّل واحد للتطبيق: تشغيل رسالة يوقف الي قبلها */
object VoicePlayer {
    var playingId by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    private var mp: MediaPlayer? = null

    suspend fun toggle(ctx: Context, id: String, b64: String) {
        if (playingId == id) { stop(); return }
        stop()
        try {
            val f = File(ctx.cacheDir, "voice_$id.m4a")
            withContext(Dispatchers.IO) {
                if (!f.exists()) f.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
            }
            val p = MediaPlayer()
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepare()
            p.start()
            mp = p; playingId = id; progress = 0f
        } catch (e: Exception) {
            stop()
        }
    }

    /** يحدّث نسبة التقدم (تُستدعى من الواجهة كل ~100ms وقت التشغيل) */
    fun tick() {
        val p = mp ?: return
        try {
            val d = p.duration
            if (d > 0) progress = (p.currentPosition.toFloat() / d).coerceIn(0f, 1f)
        } catch (_: Exception) {}
    }

    fun stop() {
        try { mp?.stop() } catch (_: Exception) {}
        try { mp?.release() } catch (_: Exception) {}
        mp = null; playingId = null; progress = 0f
    }
}

fun formatVoiceTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** أيقونة المايك (مرسومة بالكود لأن icons-core ما فيها مايك) */
@Composable
fun MicGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val bodyW = w * 0.34f
        drawRoundRect(
            tint, Offset((w - bodyW) / 2, h * 0.06f), Size(bodyW, h * 0.52f),
            CornerRadius(bodyW / 2, bodyW / 2)
        )
        val sw = w * 0.085f
        drawArc(
            tint, 0f, 180f, false, Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.46f),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawLine(tint, Offset(w / 2, h * 0.68f), Offset(w / 2, h * 0.88f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.36f, h * 0.9f), Offset(w * 0.64f, h * 0.9f), sw, StrokeCap.Round)
    }
}

@Composable
private fun PauseGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bw = size.width * 0.24f
        val top = size.height * 0.14f
        val bh = size.height * 0.72f
        drawRoundRect(tint, Offset(size.width * 0.16f, top), Size(bw, bh), CornerRadius(3f, 3f))
        drawRoundRect(tint, Offset(size.width * 0.60f, top), Size(bw, bh), CornerRadius(3f, 3f))
    }
}

/** أعمدة الموجة: من الحقل المخزن، أو شكل ثابت مشتق من المعرّف للرسائل القديمة */
private fun waveLevels(id: String, wave: String): List<Float> {
    if (wave.length >= WAVE_BARS) return wave.take(WAVE_BARS).map { ((it - 'a').coerceIn(0, 25)) / 25f }
    var h = id.hashCode()
    return List(WAVE_BARS) {
        h = h * 1103515245 + 12345
        0.25f + ((h ushr 16) and 0xFF) / 255f * 0.75f
    }
}

/** داخل الفقاعة: زر تشغيل/إيقاف + موجة الصوت (تتلوّن مع التشغيل) + المدة */
@Composable
fun VoiceBubbleContent(id: String, b64: String, durationMs: Long, wave: String, textColor: Color) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val playing = VoicePlayer.playingId == id
    LaunchedEffect(playing) {
        while (playing) { VoicePlayer.tick(); delay(80) }
    }
    val frac = if (playing) VoicePlayer.progress else 0f
    val levels = androidx.compose.runtime.remember(id, wave) { waveLevels(id, wave) }
    Row(
        Modifier.width(210.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(textColor.copy(alpha = 0.92f))
                .clickable { scope.launch { VoicePlayer.toggle(ctx, id, b64) } },
            contentAlignment = Alignment.Center
        ) {
            // الأيقونة بلون معاكس للفقاعة حتى تبين دايماً
            val inv = if (textColor.luminance() > 0.5f) Color(0xFF222222) else Color.White
            if (playing) PauseGlyph(inv, Modifier.size(20.dp))
            else Icon(Icons.Filled.PlayArrow, null, tint = inv, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                val n = levels.size
                val gap = 2.dp.toPx()
                val bw = (size.width - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val bh = (4.dp.toPx() + levels[i] * (size.height - 4.dp.toPx()))
                    val x = i * (bw + gap)
                    val done = (i + 0.5f) / n <= frac
                    drawRoundRect(
                        textColor.copy(alpha = if (done) 1f else 0.4f),
                        Offset(x, (size.height - bh) / 2), Size(bw, bh),
                        CornerRadius(bw / 2, bw / 2)
                    )
                }
            }
            Text(
                if (playing) formatVoiceTime((durationMs * frac).toLong()) else formatVoiceTime(durationMs),
                color = textColor.copy(alpha = 0.8f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
