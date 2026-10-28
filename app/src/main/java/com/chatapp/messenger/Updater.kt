package com.chatapp.messenger

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** معلومات تحديث متوفر. تُقرأ من Firestore: app_config/android */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    /** true = المستخدم ما يگدر يتجاوز التحديث */
    val force: Boolean
)

/** نتيجة محاولة فتح المثبّت */
enum class InstallResult { STARTED, NEED_PERMISSION, FAILED }

/**
 * التحديث من داخل التطبيق:
 * 1) check: يقارن versionCode المثبّت مع المنشور بـ Firestore
 * 2) download: ينزل الـ APK لمجلد الكاش ويتأكد أنه نفس التطبيق ونسخة أحدث ونفس التوقيع
 * 3) install: يفتح مثبّت النظام، والنظام يحدّث فوق النسخة القديمة (بدون حذفها)
 */
object Updater {

    @Suppress("DEPRECATION")
    fun installedVersionCode(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    @Suppress("DEPRECATION")
    fun installedVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

    /** يرجع null إذا ما فيه تحديث أو فشل الفحص (بدون إنترنت، قواعد غير منشورة...) */
    suspend fun check(ctx: Context): UpdateInfo? {
        return try {
            val snap = withTimeoutOrNull(10_000) {
                FirebaseFirestore.getInstance().collection("app_config").document("android").get().await()
            } ?: return null
            if (!snap.exists()) return null
            val code = (snap.get("versionCode") as? Number)?.toLong() ?: return null
            val url = snap.getString("apkUrl")?.trim().orEmpty()
            if (code <= installedVersionCode(ctx)) return null
            if (!url.startsWith("https://")) return null
            UpdateInfo(
                versionCode = code,
                versionName = snap.getString("versionName").orEmpty(),
                apkUrl = url,
                notes = snap.getString("notes").orEmpty(),
                force = snap.getBoolean("force") == true
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ينزل ملف التحديث. [onProgress] من 0 إلى 1 (أو -1 إذا حجم الملف مجهول).
     * يرمي استثناء برسالة عربية جاهزة للعرض إذا فشل أو الملف غير صالح.
     */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val part = File(dir, "update.apk.part")
            val out = File(dir, "update.apk")

            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    throw IOException("السيرفر رجّع خطأ ${conn.responseCode}، تأكد من رابط التحديث")
                }
                val total = conn.contentLengthLong
                var done = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var lastReport = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 100) {
                                lastReport = now
                                onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                            }
                        }
                    }
                }
                onProgress(1f)
                if (total > 0 && done != total) throw IOException("انقطع التنزيل قبل ما يكتمل")
            } catch (e: kotlinx.coroutines.CancellationException) {
                part.delete()
                throw e
            } catch (e: IOException) {
                part.delete()
                throw IOException(e.message?.takeIf { it.contains("،") } ?: "فشل التنزيل، تأكد من الإنترنت وجرّب مرة ثانية")
            } finally {
                conn.disconnect()
            }

            if (!part.renameTo(out)) throw IOException("ما گدرت أحفظ ملف التحديث")
            validate(ctx, out, info)
            out
        }

    /** يتأكد أن الملف فعلاً تحديث لهذا التطبيق، ونفس توقيعه، حتى ما يفشل التثبيت بعد التنزيل */
    @Suppress("DEPRECATION")
    private fun validate(ctx: Context, apk: File, info: UpdateInfo) {
        val pm = ctx.packageManager
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, 0)
        if (archive == null) {
            apk.delete()
            throw IOException("الملف المنزّل مو APK صالح، تأكد من الرابط")
        }
        if (archive.packageName != ctx.packageName) {
            apk.delete()
            throw IOException("الملف يخص تطبيق ثاني (${archive.packageName})")
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= installedVersionCode(ctx)) {
            apk.delete()
            throw IOException("الملف مو أحدث من النسخة الحالية (لازم versionCode أكبر)")
        }
        if (sameSigner(ctx, apk) == false) {
            apk.delete()
            throw IOException(
                "التحديث موقّع بمفتاح غير مفتاح النسخة المثبّتة، فالنظام ما راح يقبله كتحديث. " +
                    "احذف التطبيق مرة وحدة ونزّل النسخة الجديدة، وبعدها كل التحديثات تجي تلقائي."
            )
        }
    }

    /** true = نفس التوقيع، false = مختلف، null = ما گدرنا نتحقق (أندرويد أقدم من 9) */
    @Suppress("DEPRECATION")
    private fun sameSigner(ctx: Context, apk: File): Boolean? {
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            val pm = ctx.packageManager
            val a = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?.signingInfo?.apkContentsSigners ?: return null
            val b = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners ?: return null
            a.map { it.toCharsString() }.toSet() == b.map { it.toCharsString() }.toSet()
        } catch (_: Exception) {
            null
        }
    }

    /** يفتح مثبّت النظام. إذا ما عنده صلاحية "تثبيت التطبيقات" يفتح له صفحة الإعداد */
    fun install(ctx: Context, apk: File): InstallResult {
        return try {
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return InstallResult.NEED_PERMISSION
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            InstallResult.STARTED
        } catch (_: Exception) {
            InstallResult.FAILED
        }
    }
}
