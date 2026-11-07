package com.apkbuilder

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apkbuilder.engine.BuildError
import com.apkbuilder.engine.ErrorParser
import com.apkbuilder.engine.GhRun
import com.apkbuilder.engine.GhWorkflow
import com.apkbuilder.engine.GitHubApi
import com.apkbuilder.engine.GitHubException
import com.apkbuilder.engine.LogLevel
import com.apkbuilder.engine.LogLine
import com.apkbuilder.engine.ProjectDetector
import com.apkbuilder.engine.ProjectInfo
import com.apkbuilder.engine.UnsupportedProjectException
import com.apkbuilder.engine.Phase
import com.apkbuilder.settings.AppSettings
import com.apkbuilder.settings.SaveMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

class BuildViewModel(private val app: Application) : AndroidViewModel(app) {

    val settings = AppSettings(app)

    // ---------------------------------------------------------------- حالة GitHub
    var login by mutableStateOf<String?>(null); private set
    var repos by mutableStateOf<List<String>>(emptyList()); private set
    var workflows by mutableStateOf<List<GhWorkflow>>(emptyList()); private set
    var githubBusy by mutableStateOf(false); private set
    var githubError by mutableStateOf<String?>(null); private set
    private var branch: String = ""

    // --------------------------------------------------------------- ملفات اختيارية
    var projectUri by mutableStateOf<Uri?>(null); private set
    var projectName by mutableStateOf<String?>(null); private set
    /** نتيجة التعرف على المشروع المختار (للعرض في الواجهة). */
    var detectedLabel by mutableStateOf<String?>(null); private set
    var detectError by mutableStateOf<String?>(null); private set
    var detecting by mutableStateOf(false); private set

    // ------------------------------------------------------------------- حالة البناء
    var phase by mutableStateOf(Phase.IDLE); private set
    var startedAt by mutableStateOf(0L); private set
    var statusText by mutableStateOf("جاهز"); private set
    var savedApks by mutableStateOf<List<String>>(emptyList()); private set
    var runUrl by mutableStateOf<String?>(null); private set

    val logs = mutableStateListOf<LogLine>()
    val errors = mutableStateListOf<BuildError>()

    /** ملفات APK بانتظار أن يختار المستخدم مكان حفظها (وضع «اسأل كل مرة»). */
    val askQueue = mutableStateListOf<File>()
    private val targetNames = HashMap<String, String>()

    private var job: Job? = null

    val isRunning: Boolean
        get() = phase == Phase.UPLOADING || phase == Phase.QUEUED ||
            phase == Phase.BUILDING || phase == Phase.DOWNLOADING

    val isConnected: Boolean get() = settings.token.isNotBlank() && login != null

    init {
        if (settings.token.isNotBlank()) refreshAccount()
    }

    // ============================================================ ربط حساب GitHub

    fun connect(token: String) {
        val t = token.trim()
        if (t.isEmpty() || githubBusy) return
        githubBusy = true; githubError = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = GitHubApi(t)
                val (user, scopes) = api.userWithScopes()
                if (scopes != null) {
                    val missing = listOf("repo", "workflow").filter { it !in scopes }
                    if (missing.isNotEmpty()) {
                        githubError = "التوكن ينقصه صلاحية: ${missing.joinToString("، ")} — أنشئ توكن جديداً بالزر أعلاه"
                        return@launch
                    }
                }
                val list = api.repos()
                settings.updateToken(t)
                withContext(Dispatchers.Main) { login = user; repos = list }
                if (settings.repo.isNotBlank()) loadWorkflows(settings.repo)
            } catch (e: Exception) {
                githubError = e.message ?: "فشل الاتصال بـ GitHub"
            } finally {
                githubBusy = false
            }
        }
    }

    private fun refreshAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = GitHubApi(settings.token)
                val user = api.user()
                val list = api.repos()
                withContext(Dispatchers.Main) { login = user; repos = list }
                if (settings.repo.isNotBlank()) loadWorkflows(settings.repo)
            } catch (e: GitHubException) {
                if (e.code == 401) { githubError = e.message; settings.logout() }
            } catch (_: Exception) {
                // لا يوجد إنترنت الآن — نُبقي التوكن
            }
        }
    }

    fun disconnect() {
        if (isRunning) return
        settings.logout()
        login = null; repos = emptyList(); workflows = emptyList(); githubError = null
    }

    fun selectRepo(full: String) {
        settings.updateRepo(full)
        settings.setWorkflow(0L, "")
        workflows = emptyList()
        viewModelScope.launch(Dispatchers.IO) { loadWorkflows(full) }
    }

    fun selectWorkflow(w: GhWorkflow) = settings.setWorkflow(w.id, w.name)

    private fun loadWorkflows(repo: String) {
        try {
            githubBusy = true; githubError = null
            val api = GitHubApi(settings.token)
            branch = api.defaultBranch(repo)
            val list = api.workflows(repo)
            workflows = list
            if (list.none { it.id == settings.workflowId }) {
                val pick = list.firstOrNull { it.name.contains("build", true) || it.path.contains("build", true) }
                    ?: list.firstOrNull()
                if (pick != null) settings.setWorkflow(pick.id, pick.name) else settings.setWorkflow(0L, "")
            }
        } catch (e: Exception) {
            githubError = e.message ?: "تعذر قراءة الـ workflows"
        } finally {
            githubBusy = false
        }
    }

    // =============================================================== اختيار الملفات

    private fun displayName(uri: Uri): String {
        runCatching {
            app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    fun pickProject(uri: Uri?) {
        uri ?: return
        projectUri = uri; projectName = displayName(uri)
        detectedLabel = null; detectError = null; detecting = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = readProjectZip(uri)
                val info = ProjectDetector.detect(files.keys) { files[it]?.toString(Charsets.UTF_8) }
                if (info == null) detectError = "لم أتعرف على نوع المشروع — تأكد أن الـ zip يحتوي مشروعاً كاملاً"
                else detectedLabel = info.summary
            } catch (e: UnsupportedProjectException) {
                detectError = e.message
            } catch (e: Exception) {
                detectError = "تعذر قراءة الملف: ${e.message ?: "zip غير صالح"}"
            } finally {
                detecting = false
            }
        }
    }

    fun clearProject() {
        projectUri = null; projectName = null
        detectedLabel = null; detectError = null; detecting = false
    }

    private fun readProjectZip(uri: Uri): Map<String, ByteArray> =
        app.contentResolver.openInputStream(uri)?.use { ProjectDetector.readZip(it) } ?: error("تعذر قراءة الملف")

    // ================================================================= تشغيل البناء

    private fun log(level: LogLevel, text: String) { logs.add(LogLine(level, text)) }
    private fun status(text: String) { statusText = text }

    fun start() {
        val s = settings
        if (isRunning || detecting || s.token.isBlank() || s.repo.isBlank()) return
        logs.clear(); errors.clear(); askQueue.clear(); targetNames.clear()
        savedApks = emptyList(); runUrl = null
        startedAt = System.currentTimeMillis()
        val repo = s.repo
        var wfId = 0L
        var runId = 0L
        val api = GitHubApi(s.token)

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                phase = Phase.UPLOADING
                status("تجهيز المستودع…")
                val br = branch.ifBlank { api.defaultBranch(repo).also { branch = it } }
                fun runsSafe(): List<GhRun> = runCatching { api.runs(repo, wfId) }.getOrDefault(emptyList())

                // 1) التعرف على المشروع (من الـ zip المختار أو من المستودع نفسه)
                val upload = LinkedHashMap<String, ByteArray>()
                val info: ProjectInfo
                val prefix: String
                val uri = projectUri
                if (uri != null) {
                    status("قراءة ملف المشروع…")
                    val files = readProjectZip(uri)
                    info = ProjectDetector.detect(files.keys) { files[it]?.toString(Charsets.UTF_8) }
                        ?: error("لم أتعرف على نوع المشروع — تأكد أن الـ zip يحتوي مشروعاً كاملاً")
                    prefix = ProjectDetector.UPLOAD_DIR
                    files.forEach { (path, data) -> upload["$prefix/$path"] = data }
                } else {
                    status("فحص المشروع داخل المستودع…")
                    val paths = api.tree(repo, br)
                    info = ProjectDetector.detect(paths) { api.readText(repo, it, br) }
                        ?: error("لم أتعرف على مشروع داخل المستودع $repo — اختر ملف zip للمشروع")
                    prefix = ""
                }
                log(LogLevel.OK, "تم التعرف على المشروع: ${info.summary}")

                // 2) توليد ملف البناء ورفع كل شيء في commit واحد
                val appName = (projectName ?: repo.substringAfter('/')).removeSuffix(".zip")
                upload[ProjectDetector.WORKFLOW_PATH] = ProjectDetector.workflow(info, prefix, appName).toByteArray(Charsets.UTF_8)
                status("رفع ${upload.size} ملف إلى $repo…")
                log(LogLevel.INFO, "رفع ${upload.size} ملف (يشمل ملف البناء المولَّد تلقائياً)")
                val sha = api.commitFiles(
                    repo, br, upload, "ApkBuilder: build ${info.type.label}",
                    replaceDir = if (uri != null) prefix else null, onProgress = { status(it) }
                )
                log(LogLevel.OK, "تم الرفع — commit ${sha.take(7)}")

                // 3) انتظار ظهور الـ workflow الجديد في GitHub
                status("تفعيل ملف البناء…")
                var tries = 0
                while (wfId == 0L && tries < 10) {
                    tries++
                    val match = runCatching { api.workflows(repo) }.getOrDefault(emptyList())
                        .firstOrNull { it.path == ProjectDetector.WORKFLOW_PATH }
                    if (match != null) { wfId = match.id; settings.setWorkflow(match.id, match.name) } else delay(3000)
                }
                if (wfId == 0L) error("لم يظهر الـ workflow على GitHub — تأكد أن التوكن يملك صلاحية workflow وأن Actions مفعّل في المستودع")

                // 4) تشغيل البناء
                phase = Phase.QUEUED
                status("بانتظار بدء البناء على GitHub…")
                val before = runsSafe().maxOfOrNull { it.id } ?: 0L
                var dispatched = false
                var attempt = 0
                while (!dispatched) {
                    attempt++
                    try {
                        api.dispatch(repo, wfId, br); dispatched = true
                    } catch (e: GitHubException) {
                        // ربما وصل الطلب فعلاً رغم الخطأ (انقطاع) — نتأكد حتى لا يُشغَّل البناء مرتين
                        if (runsSafe().any { it.id > before }) { dispatched = true; continue }
                        if (attempt >= 6 || e.code == 401) throw e
                        delay(4000)
                    }
                }
                var run: GhRun? = null
                repeat(20) { if (run == null) { delay(3000); run = runsSafe().firstOrNull { it.id > before } } }
                val started = run ?: error("لم يبدأ أي تشغيل على GitHub — تحقق من تبويب Actions في المستودع")
                runId = started.id
                runUrl = started.htmlUrl
                log(LogLevel.OK, "بدأ التشغيل #${started.runNumber} — ${started.htmlUrl}")

                // 3) متابعة التنفيذ
                var current = started
                var pollFails = 0
                while (current.status != "completed") {
                    if (current.status == "in_progress") {
                        phase = Phase.BUILDING
                        status(runCatching { api.currentStep(repo, runId) }.getOrNull()?.let { "الخطوة: $it" } ?: "جارٍ البناء…")
                    } else {
                        phase = Phase.QUEUED
                        status("في الطابور…")
                    }
                    delay(5000)
                    try {
                        current = api.run(repo, runId); pollFails = 0
                    } catch (e: GitHubException) {
                        // انقطاع مؤقت أثناء المتابعة لا يُفشل البناء — نكمل المحاولة
                        if (e.code == 401 || ++pollFails >= 8) throw e
                        status("مشكلة اتصال مؤقتة — إعادة المحاولة…")
                    }
                }

                // 4) السجل
                phase = Phase.DOWNLOADING
                status("تنزيل السجل…")
                val success = current.conclusion == "success"
                downloadLogs(api, repo, runId, parseErrors = !success)

                if (success) {
                    status("تنزيل ملف APK…")
                    var apks = downloadApks(api, repo, runId)
                    var waits = 0
                    while (apks.isEmpty() && waits < 4) { waits++; delay(3000); apks = downloadApks(api, repo, runId) }
                    if (apks.isEmpty()) {
                        log(LogLevel.WARN, "اكتمل البناء لكن لا يوجد APK ضمن الـ artifacts. تأكد أن الـ workflow يستخدم actions/upload-artifact.")
                        finish(Phase.SUCCESS, "اكتمل البناء (لا يوجد APK ناتج)")
                    } else {
                        val saved = apks.mapIndexed { i, f -> save(f, i) }.filterNotNull()
                        withContext(Dispatchers.Main) { savedApks = saved }
                        finish(Phase.SUCCESS, "تم البناء بنجاح ✓")
                    }
                } else {
                    if (errors.isEmpty()) {
                        errors.add(BuildError("فشل البناء (${current.conclusion ?: "بدون نتيجة"}) — راجع السجل الكامل", null, null, null, "GitHub Actions"))
                    }
                    finish(Phase.FAILED, "فشل البناء")
                }
            } catch (e: CancellationException) {
                if (runId != 0L) withContext(NonCancellable) { api.cancel(repo, runId) }
                finish(Phase.CANCELLED, "أُلغي البناء")
                throw e
            } catch (e: Exception) {
                errors.add(BuildError(e.message ?: e.javaClass.simpleName, null, null, null, "ApkBuilder"))
                log(LogLevel.ERROR, e.message ?: e.javaClass.simpleName)
                finish(Phase.FAILED, "تعذر إكمال البناء")
            }
        }
    }

    fun cancel() { job?.cancel() }

    private fun finish(p: Phase, text: String) { phase = p; statusText = text }

    // ------------------------------------------------------------------- السجل

    private val timestamp = Regex("^\\uFEFF?\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z ")

    private fun downloadLogs(api: GitHubApi, repo: String, runId: Long, parseErrors: Boolean) {
        val zip = File(app.cacheDir, "run_logs.zip")
        try {
            api.downloadLogs(repo, runId, zip)
        } catch (e: Exception) {
            log(LogLevel.WARN, "تعذر تنزيل السجل: ${e.message}")
            return
        }
        val entries = ArrayList<Pair<String, String>>()
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) entries.add(e.name to z.readBytes().toString(Charsets.UTF_8))
                e = z.nextEntry
            }
        }
        zip.delete()
        // الملفات في الجذر = سجل كامل لكل job؛ الباقي سجل خطوات مكرر
        val chosen = entries.filter { !it.first.contains('/') }.ifEmpty { entries }.sortedBy { it.first }

        val ghErrors = ArrayList<String>()
        val parser = ErrorParser { err -> errors.add(err) }
        val batch = ArrayList<LogLine>()
        for ((_, text) in chosen) {
            for (raw in text.lineSequence()) {
                var line = timestamp.replace(raw, "")
                when {
                    line.startsWith("##[error]") -> {
                        line = line.removePrefix("##[error]"); ghErrors.add(line)
                        batch.add(LogLine(LogLevel.ERROR, line))
                    }
                    line.startsWith("##[warning]") -> batch.add(LogLine(LogLevel.WARN, line.removePrefix("##[warning]")))
                    line.startsWith("##[group]") -> batch.add(LogLine(LogLevel.CMD, "▶ " + line.removePrefix("##[group]")))
                    line.startsWith("##[endgroup]") -> {}
                    line.startsWith("[command]") -> batch.add(LogLine(LogLevel.CMD, line.removePrefix("[command]")))
                    else -> batch.add(LogLine(LogLevel.INFO, line))
                }
                if (parseErrors) parser.feed(line)
            }
        }
        if (parseErrors) {
            parser.finish()
            if (errors.isEmpty()) ghErrors.distinct().forEach { errors.add(BuildError(it, null, null, null, "GitHub Actions")) }
        }
        logs.addAll(if (batch.size > 6000) batch.takeLast(6000) else batch)
    }

    // -------------------------------------------------------------------- APK

    private fun downloadApks(api: GitHubApi, repo: String, runId: Long): List<File> {
        val outDir = File(app.cacheDir, "apk_out").apply { deleteRecursively(); mkdirs() }
        val result = ArrayList<File>()
        for (art in api.artifacts(repo, runId).filter { !it.expired }) {
            val zip = File(app.cacheDir, "artifact_${art.id}.zip")
            api.downloadArtifact(repo, art.id, zip)
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory && e.name.endsWith(".apk", true)) {
                        val f = File(outDir, e.name.substringAfterLast('/'))
                        f.outputStream().use { z.copyTo(it) }
                        result.add(f)
                    }
                    e = z.nextEntry
                }
            }
            zip.delete()
        }
        return result
    }

    // --------------------------------------------------------------------- الحفظ

    /** اسم الملف النهائي حسب إعداد «اسم التطبيق». */
    private fun finalName(original: String, index: Int): String {
        if (!settings.useCustomName || settings.customName.isBlank()) return original
        val base = settings.customName.trim().removeSuffix(".apk").replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (index == 0) "$base.apk" else "$base-${index + 1}.apk"
    }

    fun nameFor(file: File): String = targetNames[file.absolutePath] ?: file.name

    private fun save(file: File, index: Int): String? {
        val name = finalName(file.name, index)
        targetNames[file.absolutePath] = name
        return when (settings.saveMode) {
            SaveMode.ASK -> { askQueue.add(file); "$name — بانتظار اختيار المكان" }
            SaveMode.CUSTOM -> runCatching { saveToTree(file, name) }.getOrElse { saveToDownloads(file, name) }
            else -> saveToDownloads(file, name)
        }
    }

    private fun saveToTree(file: File, name: String): String {
        val tree = Uri.parse(settings.folderUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(app.contentResolver, parent, "application/vnd.android.package-archive", name)
            ?: error("تعذر إنشاء الملف")
        app.contentResolver.openOutputStream(doc)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        return "${folderLabel(settings.folderUri)}/$name"
    }

    private fun saveToDownloads(file: File, name: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/ApkBuilder")
                }
                val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert failed")
                app.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                "Download/ApkBuilder/$name"
            } else {
                val dir = File(app.getExternalFilesDir(null), "outputs").apply { mkdirs() }
                file.copyTo(File(dir, name), overwrite = true).absolutePath
            }
        } catch (e: Exception) {
            val dir = File(app.getExternalFilesDir(null), "outputs").apply { mkdirs() }
            file.copyTo(File(dir, name), overwrite = true).absolutePath
        }
    }

    /** يُستدعى بعد أن يختار المستخدم مكان الحفظ (وضع «اسأل كل مرة»). */
    fun onCreateDocResult(uri: Uri?) {
        val f = askQueue.firstOrNull() ?: return
        askQueue.removeAt(0)
        if (uri == null) return
        val name = nameFor(f)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            }.onSuccess {
                withContext(Dispatchers.Main) { savedApks = savedApks.filterNot { it.startsWith("$name —") } + "✓ $name" }
            }.onFailure {
                log(LogLevel.ERROR, "تعذر حفظ $name: ${it.message}")
            }
        }
    }

    fun onFolderPicked(uri: Uri?) {
        uri ?: return
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        settings.updateFolderUri(uri.toString())
    }

    fun folderLabel(uriString: String): String {
        if (uriString.isBlank()) return "لم يُختر مجلد"
        return runCatching {
            DocumentsContract.getTreeDocumentId(Uri.parse(uriString)).replace(':', '/')
        }.getOrDefault(uriString)
    }

    // ------------------------------------------------------------------- نسخ النص

    fun logAsText(): String = logs.joinToString("\n") { "[${it.level}] ${it.text}" }

    fun errorsAsText(): String = errors.joinToString("\n\n") {
        "الخطأ: ${it.message}\nالمكان: ${it.file ?: "—"}\nالسطر: ${it.line ?: "—"}  العمود: ${it.column ?: "—"}\nالمصدر: ${it.source}"
    }
}
