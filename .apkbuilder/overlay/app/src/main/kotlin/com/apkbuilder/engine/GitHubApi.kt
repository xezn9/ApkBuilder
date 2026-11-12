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
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * @param code رمز HTTP (0 = مشكلة اتصال).
 * @param retryAfterMs إن كان > 0 فالخطأ مؤقت (ضغط على الخادم أو حد المعدل) ويمكن إعادة المحاولة بعد هذه المدة.
 */
class GitHubException(val code: Int, message: String, val retryAfterMs: Long = 0L) : Exception(message) {
    /** خطأ مؤقت (ضغط/عطل في GitHub أو حد المعدل) — يستحق الانتظار وإعادة المحاولة بدل الفشل. */
    val isTransient: Boolean get() = code >= 500 || code == 429 || retryAfterMs != 0L
}

/** انقطاع الاتصال بالإنترنت (وليس خطأً من GitHub). */
class NetworkException(message: String) : IOException(message)

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
                if (attempt >= retries) throw NetworkException("تعذر الاتصال بـ GitHub — تحقق من الإنترنت")
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
                if (attempt >= 3) throw NetworkException("تعذر الاتصال بـ GitHub — تحقق من الإنترنت")
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
        if (res.optBoolean("truncated")) throw GitHubException(-1, "المستودع كبير جداً للفحص التلقائي — اختر ملف zip للمشروع")
        val arr = res.getJSONArray("tree")
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("type") == "blob") out.add(e.getString("path"))
        }
        return out
    }

    /**
     * كل ملفات الفرع مع sha كل ملف (يُستخدم لتخطي رفع ما لم يتغير).
     * يعيد خريطة فارغة لمستودع فارغ، و null إن كان المستودع ضخماً لدرجة أن GitHub اقتطع القائمة.
     */
    fun treeShas(repo: String, branch: String): Map<String, String>? {
        val res = try {
            JSONObject(call("GET", "/repos/$repo/git/trees/${encRef(branch)}?recursive=1"))
        } catch (e: GitHubException) {
            if (e.code == 404 || e.code == 409) return emptyMap()   // مستودع فارغ لا فرع فيه بعد
            throw e
        }
        if (res.optBoolean("truncated")) return null
        val arr = res.getJSONArray("tree")
        val out = HashMap<String, String>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("type") == "blob") out[e.getString("path")] = e.getString("sha")
        }
        return out
    }

    /** يقرأ ملفاً نصياً صغيراً من المستودع، أو null إن لم يوجد. */
    fun readText(repo: String, path: String, ref: String): String? = try {
        val o = JSONObject(call("GET", "/repos/$repo/contents/${path.split('/').joinToString("/") { enc(it) }}?ref=${enc(ref)}"))
        String(Base64.decode(o.getString("content"), Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        if (e is IOException || (e is GitHubException && e.isTransient)) throw e
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

    private fun fingerprint(files: Map<String, ByteArray>, replaceDirs: List<String>): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(replaceDirs.joinToString("|").toByteArray())
        for ((path, data) in files) {
            md.update(path.toByteArray()); md.update(0.toByte())
            md.update(data.size.toString().toByteArray()); md.update(10.toByte())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * يرفع الملفات في commit واحد (Git Data API) ويعيد sha الخاص به.
     * الملفات النصية تُرسل داخل الـ tree مباشرة (طلب واحد لكل دفعة)، والثنائية فقط تحتاج blob مستقل.
     *
     * الاستئناف: بعد كل دفعة يُستدعى [onCheckpoint] بنقطة تحتوي آخر tree أُنشئ على GitHub وفهرس الملف التالي.
     * إن انقطع الاتصال ثم استُدعيت الدالة من جديد بنفس [resume] (والملفات نفسها) تكمل من تلك النقطة
     * ولا تعيد رفع ما تم رفعه.
     * @param replaceDirs مجلدات تُحذف منها أي ملفات قديمة غير موجودة في [files].
     * @param remoteShas ملفات المستودع الحالية (مسار ← sha): أي ملف محتواه مطابق لما هناك لا يُرسل (مثل git).
     */
    fun commitFiles(
        repo: String, branch: String, files: Map<String, ByteArray>, message: String,
        replaceDirs: List<String> = emptyList(), remoteShas: Map<String, String>? = null,
        onProgress: (String) -> Unit = {},
        resume: UploadCheckpoint? = null, onCheckpoint: (UploadCheckpoint?) -> Unit = {}
    ): String {
        val fp = fingerprint(files, replaceDirs)
        var cp = resume?.takeIf { it.fingerprint == fp }
        var attempt = 0
        while (true) {
            attempt++
            try {
                return commitOnce(repo, branch, files, message, replaceDirs, remoteShas, onProgress, fp, cp, onCheckpoint)
            } catch (e: GitHubException) {
                if (cp != null && (e.code == 404 || e.code == 409 || e.code == 422)) {
                    // الكائنات المحفوظة لم تعد صالحة (حُذفت أو تغيّر الفرع) → نبدأ الرفع من جديد
                    cp = null; onCheckpoint(null)
                    onProgress("تعذر الإكمال من نقطة التوقف — إعادة الرفع من البداية…")
                    continue
                }
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
        replaceDirs: List<String>, remoteShas: Map<String, String>?, onProgress: (String) -> Unit,
        fp: String, resume: UploadCheckpoint?, onCheckpoint: (UploadCheckpoint?) -> Unit
    ): String {
        val head = headSha(repo, branch)
        val refPath = "/repos/$repo/git/refs/heads/${encRef(branch)}"
        val entries = files.entries.toList()

        // الـ commit أُنشئ في محاولة سابقة: نتأكد أن الفرع وصل إليه أو نحرّكه الآن
        if (resume?.commitSha != null) {
            if (head == resume.commitSha) return resume.commitSha
            if (head == resume.parentSha) {
                call("PATCH", refPath, JSONObject().put("sha", resume.commitSha))
                return resume.commitSha
            }
        }

        val parentSha = head
        var current: String
        var startIndex = 0
        if (resume != null && resume.parentSha == head && resume.commitSha == null) {
            current = resume.currentTree; startIndex = resume.nextIndex.coerceIn(0, entries.size)
            onProgress("استئناف الرفع من الملف $startIndex / ${entries.size}…")
        } else {
            val baseTree = JSONObject(call("GET", "/repos/$repo/git/commits/$parentSha"))
                .getJSONObject("tree").getString("sha")
            current = baseTree
            // حذف الملفات القديمة داخل المجلدات المستبدَلة
            if (replaceDirs.isNotEmpty()) {
                val old = JSONObject(call("GET", "/repos/$repo/git/trees/$baseTree?recursive=1")).getJSONArray("tree")
                val deletions = JSONArray()
                for (i in 0 until old.length()) {
                    val e = old.getJSONObject(i)
                    val path = e.getString("path")
                    if (e.optString("type") == "blob" && replaceDirs.any { path.startsWith("$it/") } && !files.containsKey(path)) {
                        deletions.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL))
                    }
                }
                if (deletions.length() > 0) current = createTree(repo, current, deletions)
            }
            onCheckpoint(UploadCheckpoint(fp, parentSha, current, 0))
        }

        var batch = JSONArray()
        var batchBytes = 0

        fun flush(nextIndex: Int) {
            if (batch.length() == 0) return
            current = createTree(repo, current, batch)
            batch = JSONArray(); batchBytes = 0
            onCheckpoint(UploadCheckpoint(fp, parentSha, current, nextIndex))
        }

        for (i in startIndex until entries.size) {
            val (path, data) = entries[i]
            // نفس المحتوى موجود في المستودع أصلاً → لا نرفعه (الـ base_tree يبقيه كما هو)
            if (remoteShas != null && remoteShas[path] == ZipSync.gitSha(data)) continue
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
            if (batchBytes > 2_000_000 || batch.length() >= 400) {
                flush(i + 1); onProgress("رفع الملفات… ${i + 1} / ${entries.size}")
            }
        }
        flush(entries.size)

        val commit = JSONObject(
            call(
                "POST", "/repos/$repo/git/commits",
                JSONObject().put("message", message).put("tree", current).put("parents", JSONArray().put(parentSha))
            )
        ).getString("sha")
        // نحفظ الـ commit قبل تحريك الفرع: لو انقطع الاتصال بعد وصول الطلب نتحقق ولا نكرر العمل
        onCheckpoint(UploadCheckpoint(fp, parentSha, current, entries.size, commit))
        call("PATCH", refPath, JSONObject().put("sha", commit))
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

    fun downloadArtifact(repo: String, artifactId: Long, dest: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        download("$base/repos/$repo/actions/artifacts/$artifactId/zip", dest, onProgress)

    fun downloadLogs(repo: String, runId: Long, dest: File) =
        download("$base/repos/$repo/actions/runs/$runId/logs", dest)

    /**
     * GitHub يرد بتحويل (302) إلى رابط مؤقت — لا نرسل التوكن إلى الرابط الثاني.
     * التنزيل قابل للاستئناف: ما نُزّل يُحفظ في `<dest>.part` ويُكمَل بطلب Range عند إعادة المحاولة،
     * حتى بعد انقطاع طويل أو إعادة تشغيل التطبيق.
     */
    private fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val part = File(dest.path + ".part")
        var attempt = 0
        while (true) {
            attempt++
            try {
                downloadOnce(url, dest, part, onProgress)
                return
            } catch (e: GitHubException) {
                if (attempt >= 6 || (e.code < 500 && e.retryAfterMs <= 0L)) {
                    if (!e.isTransient) part.delete()
                    throw e
                }
                Thread.sleep(2000L * attempt)
            } catch (e: IOException) {
                // نُبقي الملف الجزئي ليُستأنف
                if (attempt >= 6) throw NetworkException("انقطع التنزيل — تحقق من الإنترنت")
                Thread.sleep(1000L * attempt)
            }
        }
    }

    private fun downloadOnce(url: String, dest: File, part: File, onProgress: (Long, Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val first = open(url, "GET", false)
        first.instanceFollowRedirects = false
        try {
            val code = first.responseCode
            if (code in 300..399) {
                val loc = first.getHeaderField("Location") ?: throw GitHubException(code, "تحويل بدون عنوان")
                // تنزيل متوازي: عدة اتصالات بنفس الوقت (كل اتصال يجلب جزءاً من الملف)
                if (parallelDownload(loc, part, onProgress)) {
                    dest.delete()
                    if (!part.renameTo(dest)) { part.copyTo(dest, overwrite = true); part.delete() }
                    return
                }
                val second = URL(loc).openConnection() as HttpURLConnection
                second.connectTimeout = 20_000
                second.readTimeout = 20_000   // مهلة قصيرة: التوقف يُكتشف بسرعة ويُستأنف بـ Range بدل الانتظار دقيقتين
                second.setRequestProperty("Accept-Encoding", "identity")
                val have = if (part.exists()) part.length() else 0L
                if (have > 0) second.setRequestProperty("Range", "bytes=$have-")
                try {
                    val c2 = second.responseCode
                    when (c2) {
                        206 -> saveBody(second, part, append = true, already = have, onProgress = onProgress)
                        in 200..299 -> saveBody(second, part, append = false, already = 0L, onProgress = onProgress)
                        416 -> { part.delete(); throw IOException("الملف الجزئي غير صالح — إعادة التنزيل") }
                        else -> throw GitHubException(c2, "فشل التنزيل ($c2)")
                    }
                } finally {
                    second.disconnect()
                }
            } else if (code in 200..299) {
                saveBody(first, part, append = false, already = 0L, onProgress = onProgress)
            } else {
                val text = first.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GitHubException(code, friendly(code, text))
            }
        } finally {
            first.disconnect()
        }
        dest.delete()
        if (!part.renameTo(dest)) { part.copyTo(dest, overwrite = true); part.delete() }
    }


    private val parallelConnections = 6

    /**
     * يجلب الملف بعدة اتصالات متوازية (Range). يرجع false إذا السيرفر لا يدعم Range
     * أو الملف صغير، فيُستخدم التنزيل العادي كاحتياط.
     */
    private fun parallelDownload(loc: String, part: File, onProgress: (Long, Long) -> Unit): Boolean {
        val probe = URL(loc).openConnection() as HttpURLConnection
        probe.connectTimeout = 20_000
        probe.readTimeout = 20_000
        probe.setRequestProperty("Accept-Encoding", "identity")
        probe.setRequestProperty("Range", "bytes=0-0")
        val total: Long
        try {
            if (probe.responseCode != 206) return false
            total = probe.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: return false
        } finally {
            probe.disconnect()
        }
        if (total < 4L * 1024 * 1024) return false

        part.delete()
        java.io.RandomAccessFile(part, "rw").use { it.setLength(total) }

        val n = parallelConnections
        val chunk = (total + n - 1) / n
        val downloaded = java.util.concurrent.atomic.AtomicLong(0)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(n)
        try {
            val futures = (0 until n).map { idx ->
                val start = idx * chunk
                val end = minOf(total - 1, start + chunk - 1)
                pool.submit(Runnable {
                    var pos = start
                    var tries = 0
                    while (pos <= end && failure.get() == null) {
                        val c = URL(loc).openConnection() as HttpURLConnection
                        try {
                            c.connectTimeout = 20_000
                            c.readTimeout = 20_000
                            c.setRequestProperty("Accept-Encoding", "identity")
                            c.setRequestProperty("Range", "bytes=$pos-$end")
                            if (c.responseCode != 206) throw IOException("Range ${c.responseCode}")
                            java.io.RandomAccessFile(part, "rw").use { raf ->
                                raf.seek(pos)
                                c.inputStream.use { i ->
                                    val buf = ByteArray(128 * 1024)
                                    while (pos <= end && failure.get() == null) {
                                        val r = i.read(buf, 0, minOf(buf.size.toLong(), end - pos + 1).toInt())
                                        if (r < 0) break
                                        raf.write(buf, 0, r)
                                        pos += r
                                        downloaded.addAndGet(r.toLong())
                                        tries = 0
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            if (++tries > 8) { failure.compareAndSet(null, e); break }
                            try { Thread.sleep(1000L) } catch (_: InterruptedException) { break }
                        } finally {
                            c.disconnect()
                        }
                    }
                })
            }
            // التقدّم يُبلَّغ من الخيط الرئيسي فقط
            while (futures.any { !it.isDone }) {
                Thread.sleep(300)
                onProgress(downloaded.get(), total)
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        failure.get()?.let { throw (it as? IOException ?: IOException(it)) }
        if (part.length() != total) throw IOException("انقطع التنزيل قبل اكتماله")
        onProgress(total, total)
        return true
    }

    private fun saveBody(c: HttpURLConnection, part: File, append: Boolean, already: Long, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val expected = c.contentLengthLong.takeIf { it >= 0 }?.let { it + already }
        java.io.FileOutputStream(part, append).use { o ->
            c.inputStream.use { i ->
                val buf = ByteArray(64 * 1024)
                var done = already
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n); done += n
                    onProgress(done, expected ?: 0L)
                }
            }
        }
        if (expected != null && part.length() != expected) throw IOException("انقطع التنزيل قبل اكتماله")
    }
}
