package com.chatapp.messenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.InstallResult
import com.chatapp.messenger.UpdateInfo
import com.chatapp.messenger.Updater
import com.chatapp.messenger.ui.glass.GlassButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * شاشة "تحديث جديد" الزجاجية. تظهر مرة وحدة عند فتح التطبيق إذا versionCode المنشور
 * بـ Firestore (app_config/android) أكبر من المثبّت. التنزيل والتثبيت من داخل التطبيق،
 * والنظام يحدّث فوق النسخة القديمة بدون حذفها.
 */
@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }
    var apk by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) { info = Updater.check(ctx) }

    val i = info ?: return
    if (dismissed && !i.force) return

    BackHandler(enabled = !i.force) {
        job?.cancel()
        dismissed = true
    }

    fun startInstall(file: File) {
        when (Updater.install(ctx, file)) {
            InstallResult.STARTED -> message = null
            InstallResult.NEED_PERMISSION ->
                message = "فعّل خيار «السماح بالتثبيت من هذا المصدر» ثم ارجع واضغط تثبيت"
            InstallResult.FAILED -> message = "ما گدرت أفتح المثبّت، جرّب مرة ثانية"
        }
    }

    fun startDownload() {
        if (downloading) return
        downloading = true
        progress = 0f
        message = null
        job = scope.launch {
            try {
                val f = Updater.download(ctx, i) { progress = it }
                apk = f
                downloading = false
                startInstall(f)
            } catch (e: CancellationException) {
                downloading = false
                throw e
            } catch (e: Exception) {
                downloading = false
                message = e.localizedMessage ?: "فشل التحديث، جرّب مرة ثانية"
            }
        }
    }

    GlassAuthScaffold { backdrop, dark ->
        val fg = glassFg(dark)

        Box(
            Modifier.size(88.dp).glassCircle(backdrop, dark),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Download, null, tint = GlassAccent, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("تحديث جديد متوفر", color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        val now = Updater.installedVersionName(ctx)
        Text(
            buildString {
                append("الإصدار ")
                append(i.versionName.ifBlank { i.versionCode.toString() })
                if (now.isNotBlank()) append("  (عندك $now)")
            },
            color = fg.copy(alpha = 0.65f), fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))

        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().glassPanel(backdrop, dark).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (i.notes.isNotBlank()) {
                Text(i.notes, color = fg, fontSize = 15.sp, textAlign = TextAlign.Center)
            } else {
                Text(
                    "نسخة محسّنة من التطبيق. التحديث يتثبت فوق نسختك الحالية وما تفقد شي.",
                    color = fg, fontSize = 15.sp, textAlign = TextAlign.Center
                )
            }

            if (downloading) {
                // شريط تقدم زجاجي بسيط
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f))
                ) {
                    val frac = if (progress < 0f) 0.15f else progress.coerceIn(0.02f, 1f)
                    Box(
                        Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(GlassAccent)
                    )
                }
                Text(
                    if (progress < 0f) "جاري التنزيل..." else "جاري التنزيل ${(progress * 100).toInt()}%",
                    color = fg.copy(alpha = 0.7f), fontSize = 13.sp
                )
            }

            message?.let {
                Text(
                    it,
                    color = if (dark) Color(0xFFFF8A8A) else Color(0xFFC62828),
                    fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }

            GlassButton(
                onClick = {
                    val f = apk
                    if (f != null && f.exists()) startInstall(f) else startDownload()
                },
                backdrop = backdrop,
                enabled = !downloading,
                tint = GlassAccent,
                pressScale = 1.03f,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    if (apk != null) "تثبيت" else if (message != null && !downloading) "أعد المحاولة" else "تحديث الآن",
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            }

            if (!i.force) {
                GlassButton(
                    onClick = { job?.cancel(); dismissed = true },
                    backdrop = backdrop,
                    pressScale = 1.03f,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("لاحقاً", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
