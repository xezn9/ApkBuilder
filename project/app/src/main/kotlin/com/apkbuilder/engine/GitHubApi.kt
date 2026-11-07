package com.apkbuilder.engine

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * @param code رمز HTTP (0 = مشكلة اتصال).
 * @param retryAfterMs إن كان > 0 فالخطأ مؤقت (ضغط على الخادم أو حد المعدل) ويمكن إعادة المحاولة بعد هذه المدة.
 */
class GitHubException(val code: Int, message: String, val retryAfterMs: Long = 0L) : Exception(message)

/** عميل GitHub REST API بدون أي مكتبات خارجية (HttpURLConnection + org.json). */
class GitHubApi(private val token: String) {

    private val base = "https://api.github.com"

    private fun open(url: String, method: String, hasBody: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
        } catch (e: ProtocolException) {
            // بعض إصدارات HttpURLConnection لا تدعم PATCH
            c.requestMethod = "POST"
            c.setRequestProperty("X-HTTP-Method-Override", method)
        }
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.setRequestProperty("User-Agent", "ApkBuilder-Android")
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        if (hasBody) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
        }
        return c
    }

    private fun friendly(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
        return when {
            code == 401 -> "التوكن غير صالح أو منتهي — أنشئ توكن جديداً"
            msg.contains("protected branch", true) -> "الفرع محمي ولا يسمح بالرفع المباشر — استخدم مستودعاً/فرعاً غير محمي"
            msg.contains("rate limit", true) || code == 429 -> "تجاوز حد الطلبات في GitHub — انتظر قليلاً ثم أعد المحاولة"
            code == 403 -> if (msg.isNotBlank()) "ممنوع: $msg" else "ممنوع — تحقق من صلاحيات التوكن (repo + workflow)"
            code == 404 -> "غير موجود — تحقق من اسم المستودع وصلاحيات التوكن"
            code == 409 -> "تعارض في المستودع (قد يكون فارغاً أو تغيّر أثناء الرفع)"
            code >= 500 -> "خطأ مؤقت في خوادم GitHub ($code)"
            else -> if (msg.isNotBlank()) msg else "خطأ HTTP $code"
        }
    }

    /** يحدد إن كان الرد خطأً مؤقتاً، وكم ننتظر قبل المحاولة التالية (0 = لا تُعد المحاولة). */
    private fun waitBeforeRetry(c: HttpURLConnection, code: Int, body: String, attempt: Int): Long {
        if (code >= 500) return 2000L * attempt
        val limited = code == 429 ||
            (code == 403 && (c.getHeaderField("X-RateLimit-Remaining") == "0" ||
                body.contains("rate limit", true) || body.contains("abuse", true)))
        if (!limited) return 0L
        val retryAfter = c.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000)
        val reset = c.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
            ?.let { it * 1000 - System.currentTimeMillis() }
        return (retryAfter ?: reset ?: 20_000L * attempt).coerceIn(2000L, 90_000L)
            .let { if ((retryAfter ?: reset ?: 0L) > 90_000L) -1L else it }
    }

    private fun callOnce(method: String, path: String, body: JSONObject?, attempt: Int): String {
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8)
        val c = open(if (path.startsWith("http")) path else base + path, method, bytes != null)
        try {
            if (bytes != null) {
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val wait = waitBeforeRetry(c, code, text, attempt)
                throw GitHubException(code, friendly(code, text), wait)
            }
            return text
        } finally {
            c.disconnect()
        }
    }

    /**
     * طلب مع إعادة محاولة تلقائية: انقطاع الشبكة، أخطاء 5xx، وحد المعدل (ننتظر المدة التي يحددها GitHub).
     * @param retries أقصى عدد محاولات (استخدم 1 للطلبات التي لا يجوز تكرارها كالتشغيل).
     */
    private fun call(method: String, path: String, body: JSONObject? = null, retries: Int = 4): String {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return callOnce(method, path, body, attempt)
            } catch (e: GitHubException) {
                if (e.retryAfterMs <= 0L || attempt >= retries) throw e
                Thread.sleep(e.retryAfterMs)
            } catch (e: IOException) {
                if (attempt >= retries) throw GitHubException(0, "تعذر الاتصال بـ GitHub — تحقق من الإنترنت")
                Thread.sleep(1500L * attempt)
            }
        }
    }

    // ------------------------------------------------------------- الحساب والمستودعات

    fun user(): String = JSONObject(call("GET", "/user")).getString("login")

    /** اسم المستخدم + صلاحيات التوكن (null للتوكنات الدقيقة fine-grained التي لا تُرجع الصلاحيات). */
    fun userWithScopes(): Pair<String, List<String>?> {
        var attempt = 0
        while (true) {
            attempt++
            val c = open("$base/user", "GET", false)
            try {
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw GitHubException(code, friendly(code, text))
                val scopes = c.getHeaderField("X-OAuth-Scopes")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                return JSONObject(text).getString("login") to scopes
            } catch (e: IOException) {
                if (attempt >= 3) throw GitHubException(0, "تعذر الاتصال بـ GitHub — تحقق من الإنترنت")
                Thread.sleep(1500L * attempt)
            } finally {
                c.disconnect()
            }
        }
    }

    /** المستودعات (الأحدث دفعاً أولاً) حتى 300 مستودع. */
    fun repos(): List<String> {
        val out = ArrayList<String>()
        for (page in 1..3) {
            val arr = JSONArray(
                call("GET", "/user/repos?per_page=100&sort=pushed&affiliation=owner,collaborator,organization_member&page=$page")
            )
            for (i in 0 until arr.length()) out.add(arr.getJSONObject(i).getString("full_name"))
            if (arr.length() < 100) break
        }
        return out
    }

    fun defaultBranch(repo: String): String =
        JSONObject(call("GET", "/repos/$repo")).getString("default_branch")

    fun workflows(repo: String): List<GhWorkflow> {
        val arr = JSONObject(call("GET", "/repos/$repo/actions/workflows?per_page=100")).getJSONArray("workflows")
        val out = ArrayList<GhWorkflow>()
        for (i in 0 until arr.length()) {
            val w = arr.getJSONObject(i)
            if (w.optString("state") == "active") out.add(GhWorkflow(w.getLong("id"), w.getString("name"), w.getString("path")))
        }
        return out
    }

    // ------------------------------------------------------------------ قراءة المستودع

    /** مسارات كل الملفات في الفرع (قد تُقتطع في المستودعات الضخمة جداً). */
    fun tree(repo: String, branch: String): List<String> {
        val res = JSONObject(call("GET", "/repos/$repo/git/trees/${encRef(branch)}?recursive=1"))
        if (res.optBoolean("truncated")) throw GitHubException(0, "المستودع كبير جداً للفحص التلقائي — اختر ملف zip للمشروع")
        val arr = res.getJSONArray("tree")
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("type") == "blob") out.add(e.getString("path"))
        }
        return out
    }

    /** يقرأ ملفاً نصياً صغيراً من المستودع، أو null إن لم يوجد. */
    fun readText(repo: String, path: String, ref: String): String? = try {
        val o = JSONObject(call("GET", "/repos/$repo/contents/${path.split('/').joinToString("/") { enc(it) }}?ref=${enc(ref)}"))
        String(Base64.decode(o.getString("content"), Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun encRef(s: String) = s.split('/').joinToString("/") { enc(it) }

    // ------------------------------------------------------------------ رفع الملفات

    private fun asText(b: ByteArray): String? {
        if (b.isEmpty() || b.size > 1_000_000 || b.any { it == 0.toByte() }) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun createTree(repo: String, base: String, entries: JSONArray): String =
        JSONObject(call("POST", "/repos/$repo/git/trees", JSONObject().put("base_tree", base).put("tree", entries)))
            .getString("sha")

    /**
     * يرفع الملفات في commit واحد (Git Data API) ويعيد sha الخاص به.
     * الملفات النصية تُرسل داخل الـ tree مباشرة (طلب واحد لكل دفعة)، والثنائية فقط تحتاج blob مستقل.
     * @param replaceDir إن حُدد، تُحذف منه أي ملفات قديمة غير موجودة في [files].
     */
    fun commitFiles(
        repo: String, branch: String, files: Map<String, ByteArray>, message: String,
        replaceDir: String? = null, onProgress: (String) -> Unit = {}
    ): String {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return commitOnce(repo, branch, files, message, replaceDir, onProgress)
            } catch (e: GitHubException) {
                // شخص/عملية دفعت للفرع أثناء رفعنا → نعيد من أحدث commit
                val conflict = e.code == 409 || (e.code == 422 && attempt < 3)
                if (!conflict || attempt >= 3) throw e
                onProgress("تعارض أثناء الرفع — إعادة المحاولة…")
                Thread.sleep(1500)
            }
        }
    }

    /** مستودع فارغ تماماً: ننشئ أول commit عبر Contents API (يُنشئ الفرع الافتراضي). */
    private fun initEmptyRepo(repo: String) {
        call(
            "PUT", "/repos/$repo/contents/README.md",
            JSONObject().put("message", "Initial commit")
                .put("content", Base64.encodeToString("# ${repo.substringAfter('/')}\n".toByteArray(), Base64.NO_WRAP))
        )
    }

    private fun headSha(repo: String, branch: String): String {
        fun get() = JSONObject(call("GET", "/repos/$repo/git/ref/heads/${encRef(branch)}"))
            .getJSONObject("object").getString("sha")
        return try {
            get()
        } catch (e: GitHubException) {
            if (e.code != 404 && e.code != 409) throw e
            initEmptyRepo(repo)
            Thread.sleep(1500)
            get()
        }
    }

    private fun commitOnce(
        repo: String, branch: String, files: Map<String, ByteArray>, message: String,
        replaceDir: String?, onProgress: (String) -> Unit
    ): String {
        val parentSha = headSha(repo, branch)
        val baseTree = JSONObject(call("GET", "/repos/$repo/git/commits/$parentSha"))
            .getJSONObject("tree").getString("sha")

        var current = baseTree
        var batch = JSONArray()
        var batchBytes = 0
        var done = 0

        fun flush() {
            if (batch.length() == 0) return
            current = createTree(repo, current, batch)
            batch = JSONArray(); batchBytes = 0
        }

        // حذف الملفات القديمة داخل المجلد المستبدَل
        if (replaceDir != null) {
            val old = JSONObject(call("GET", "/repos/$repo/git/trees/$baseTree?recursive=1")).getJSONArray("tree")
            for (i in 0 until old.length()) {
                val e = old.getJSONObject(i)
                val path = e.getString("path")
                if (e.optString("type") == "blob" && path.startsWith("$replaceDir/") && !files.containsKey(path)) {
                    batch.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL))
                }
            }
        }

        for ((path, data) in files) {
            val mode = if (path.endsWith("gradlew") || path.endsWith(".sh")) "100755" else "100644"
            val entry = JSONObject().put("path", path).put("mode", mode).put("type", "blob")
            val text = asText(data)
            if (text != null) {
                entry.put("content", text)
            } else {
                val blob = JSONObject().put("content", Base64.encodeToString(data, Base64.NO_WRAP)).put("encoding", "base64")
                entry.put("sha", JSONObject(call("POST", "/repos/$repo/git/blobs", blob)).getString("sha"))
            }
            batch.put(entry)
            batchBytes += data.size
            done++
            if (batchBytes > 2_000_000 || batch.length() >= 400) {
                flush(); onProgress("رفع الملفات… $done / ${files.size}")
            }
        }
        flush()

        val commit = JSONObject(
            call(
                "POST", "/repos/$repo/git/commits",
                JSONObject().put("message", message).put("tree", current).put("parents", JSONArray().put(parentSha))
            )
        ).getString("sha")
        call("PATCH", "/repos/$repo/git/refs/heads/${encRef(branch)}", JSONObject().put("sha", commit))
        return commit
    }

    // ------------------------------------------------------------------ تشغيل البناء

    fun dispatch(repo: String, workflowId: Long, ref: String) {
        try {
            call("POST", "/repos/$repo/actions/workflows/$workflowId/dispatches", JSONObject().put("ref", ref), retries = 1)
        } catch (e: GitHubException) {
            if (e.code == 422) throw GitHubException(
                422,
                "GitHub لم يفعّل الـ workflow الجديد بعد — حاول مرة ثانية بعد ثوانٍ"
            )
            throw e
        }
    }

    private fun parseRun(o: JSONObject) = GhRun(
        id = o.getLong("id"),
        status = o.optString("status"),
        conclusion = o.optString("conclusion").takeIf { it.isNotBlank() && it != "null" },
        headSha = o.optString("head_sha"),
        htmlUrl = o.optString("html_url"),
        runNumber = o.optInt("run_number")
    )

    fun runs(repo: String, workflowId: Long): List<GhRun> {
        val arr = JSONObject(call("GET", "/repos/$repo/actions/workflows/$workflowId/runs?per_page=10"))
            .getJSONArray("workflow_runs")
        return (0 until arr.length()).map { parseRun(arr.getJSONObject(it)) }
    }

    fun run(repo: String, runId: Long): GhRun =
        parseRun(JSONObject(call("GET", "/repos/$repo/actions/runs/$runId")))

    /** وصف الخطوة الجارية، مثل: «Build debug APK (3/7)». */
    fun currentStep(repo: String, runId: Long): String? {
        val jobs = JSONObject(call("GET", "/repos/$repo/actions/runs/$runId/jobs")).getJSONArray("jobs")
        for (i in 0 until jobs.length()) {
            val j = jobs.getJSONObject(i)
            if (j.optString("status") != "in_progress") continue
            val steps = j.optJSONArray("steps") ?: continue
            for (k in 0 until steps.length()) {
                val s = steps.getJSONObject(k)
                if (s.optString("status") == "in_progress") return "${s.optString("name")} (${k + 1}/${steps.length()})"
            }
            return j.optString("name")
        }
        return null
    }

    fun cancel(repo: String, runId: Long) {
        runCatching { call("POST", "/repos/$repo/actions/runs/$runId/cancel") }
    }

    fun artifacts(repo: String, runId: Long): List<GhArtifact> {
        val arr = JSONObject(call("GET", "/repos/$repo/actions/runs/$runId/artifacts?per_page=100"))
            .getJSONArray("artifacts")
        return (0 until arr.length()).map {
            val a = arr.getJSONObject(it)
            GhArtifact(a.getLong("id"), a.getString("name"), a.optLong("size_in_bytes"), a.optBoolean("expired"))
        }
    }

    fun downloadArtifact(repo: String, artifactId: Long, dest: File) =
        download("$base/repos/$repo/actions/artifacts/$artifactId/zip", dest)

    fun downloadLogs(repo: String, runId: Long, dest: File) =
        download("$base/repos/$repo/actions/runs/$runId/logs", dest)

    /** GitHub يرد بتحويل (302) إلى رابط مؤقت — لا نرسل التوكن إلى الرابط الثاني. */
    private fun download(url: String, dest: File) {
        var attempt = 0
        while (true) {
            attempt++
            try {
                downloadOnce(url, dest)
                return
            } catch (e: GitHubException) {
                dest.delete()
                if (attempt >= 3 || (e.code < 500 && e.retryAfterMs <= 0L)) throw e
                Thread.sleep(2000L * attempt)
            } catch (e: IOException) {
                dest.delete()
                if (attempt >= 3) throw GitHubException(0, "انقطع التنزيل — تحقق من الإنترنت")
                Thread.sleep(2000L * attempt)
            }
        }
    }

    private fun downloadOnce(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        val first = open(url, "GET", false)
        first.instanceFollowRedirects = false
        try {
            val code = first.responseCode
            if (code in 300..399) {
                val loc = first.getHeaderField("Location") ?: throw GitHubException(code, "تحويل بدون عنوان")
                val second = URL(loc).openConnection() as HttpURLConnection
                second.connectTimeout = 20_000
                second.readTimeout = 120_000
                try {
                    if (second.responseCode !in 200..299) throw GitHubException(second.responseCode, "فشل التنزيل (${second.responseCode})")
                    second.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                } finally {
                    second.disconnect()
                }
            } else if (code in 200..299) {
                first.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            } else {
                val text = first.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GitHubException(code, friendly(code, text))
            }
        } finally {
            first.disconnect()
        }
    }
}
