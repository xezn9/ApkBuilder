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
import com.apkbuilder.engine.BuildSession
import com.apkbuilder.engine.NetworkException
import com.apkbuilder.engine.NetworkMonitor
import com.apkbuilder.engine.SessionStore
import com.apkbuilder.engine.Stage
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
import java.io.IOException
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

    // ----------------------------------------------------------- الاستئناف بعد الانقطاع
    private val store = SessionStore(app)
    private val net = NetworkMonitor(app)
    private var session: BuildSession? = null
    private var userCancelled = false

    /** المرحلة التي كان البناء فيها قبل التوقف المؤقت (لعرضها في المؤشر). */
    var pausedFrom by mutableStateOf(Phase.UPLOADING); private set

    /** بناء غير مكتمل محفوظ يمكن استئنافه (يظهر زر «استئناف» عندما لا يكون هناك بناء جارٍ). */
    var resumable by mutableStateOf<BuildSession?>(null); private set

    val isRunning: Boolean
        get() = phase == Phase.UPLOADING || phase == Phase.QUEUED ||
            phase == Phase.BUILDING || phase == Phase.DOWNLOADING || phase == Phase.PAUSED

    val isConnected: Boolean get() = settings.token.isNotBlank() && login != null

    init {
        if (settings.token.isNotBlank()) refreshAccount()
        // بناء كان جارياً عند إغلاق التطبيق/انقطاع النت → نكمله تلقائياً من آخر مرحلة
        val saved = store.load()
        if (saved != null && settings.token.isNotBlank() && saved.repo == settings.repo) {
            resumable = saved
            resume()
        } else if (saved != null) {
            discardSession(saved)
        }
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

    private fun phaseFor(st: Stage) = when (st) {
        Stage.UPLOAD, Stage.WAIT_WORKFLOW -> Phase.UPLOADING
        Stage.DISPATCH, Stage.FIND_RUN -> Phase.QUEUED
        Stage.POLL -> Phase.BUILDING
        else -> Phase.DOWNLOADING
    }

    private val sessionZip: File get() = File(app.filesDir, "session/project.zip")

    private fun discardSession(s: BuildSession? = session) {
        store.clear(); session = null; resumable = null
        runCatching { sessionZip.delete() }
        s?.runId?.takeIf { it != 0L }?.let { id ->
            runCatching { File(app.cacheDir, "run_logs_$id.zip.part").delete(); File(app.cacheDir, "run_logs_$id.zip").delete() }
        }
    }

    /** يحفظ تعديلاً على الجلسة على القرص فوراً. */
    private fun patch(block: BuildSession.() -> BuildSession) {
        val ns = session!!.block(); session = ns; store.save(ns)
    }

    private fun setStage(st: Stage) {
        patch { copy(stage = st) }
        phase = phaseFor(st)
    }

    private fun isTransient(e: Throwable) =
        e is IOException || (e is GitHubException && e.isTransient)

    /**
     * الانتظار بعد خطأ مؤقت: إن كان السبب انقطاع الإنترنت ننتظر عودته، وإن كان ضغطاً على GitHub
     * ننتظر مدة متزايدة. يعود بعدها المستدعي ليكمل من نفس النقطة.
     */
    private suspend fun pauseAndWait(e: Exception, fails: Int) {
        val offline = e is IOException
        val from = if (phase == Phase.PAUSED) pausedFrom else phase
        val where = session?.stage?.label ?: ""
        pausedFrom = from; phase = Phase.PAUSED
        if (fails == 1) {
            log(LogLevel.WARN, if (offline) "انقطع الاتصال أثناء «$where» — سأنتظر عودة الإنترنت وأكمل من نفس النقطة" else "GitHub مشغول مؤقتاً أثناء «$where» — إعادة المحاولة تلقائياً")
        }
        status(if (offline) "انقطع الإنترنت — سيُستأنف تلقائياً من «$where»" else "GitHub مشغول — إعادة المحاولة تلقائياً…")
        if (offline) net.await()
        delay(if (offline) minOf(2000L * fails, 15_000L) else minOf(5000L * fails, 60_000L))
        phase = from
        status("استئناف «$where»…")
    }

    /** ينفّذ [block] ويعيد المحاولة عند الانقطاع/الأعطال المؤقتة بدل إفشال البناء. */
    private suspend fun <T> resilient(block: suspend () -> T): T {
        var fails = 0
        while (true) {
            try {
                return block().also { if (fails > 0) log(LogLevel.OK, "عاد الاتصال — تم الاستئناف") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isTransient(e)) throw e
                fails++
                // الاتصال سليم لكن الفشل يتكرر → ليس انقطاعاً، نكفّ عن الانتظار
                if (net.isOnline() && fails >= (if (e is IOException) 12 else 30)) throw e
                pauseAndWait(e, fails)
            }
        }
    }

    /** بناء جديد من البداية. */
    fun start() {
        val s = settings
        if (isRunning || detecting || s.token.isBlank() || s.repo.isBlank()) return
        discardSession()
        logs.clear(); errors.clear(); askQueue.clear(); targetNames.clear()
        savedApks = emptyList(); runUrl = null
        startedAt = System.currentTimeMillis()
        userCancelled = false
        phase = Phase.UPLOADING
        status("تجهيز المستودع…")
        val repo = s.repo
        val api = GitHubApi(s.token)

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val br = branch.ifBlank { resilient { api.defaultBranch(repo) }.also { branch = it } }
                // نسخة خاصة من الـ zip: تبقى صالحة للاستئناف حتى لو أُغلق التطبيق أو سُحب إذن الملف
                val uri = projectUri
                var zipPath: String? = null
                if (uri != null) {
                    status("نسخ ملف المشروع…")
                    sessionZip.parentFile?.mkdirs()
                    app.contentResolver.openInputStream(uri)?.use { i -> sessionZip.outputStream().use { o -> i.copyTo(o) } }
                        ?: error("تعذر قراءة الملف")
                    zipPath = sessionZip.absolutePath
                }
                val appName = (projectName ?: repo.substringAfter('/')).removeSuffix(".zip")
                session = BuildSession(repo, br, Stage.UPLOAD, appName, startedAt, projectZip = zipPath)
                store.save(session!!)
                runStages(api)
            } catch (e: CancellationException) {
                onCancelled(api)
                throw e
            } catch (e: Exception) {
                onFailed(e)
            }
        }
    }

    /** يكمل بناءً محفوظاً من آخر مرحلة وصل لها (يُستدعى تلقائياً أو بزر «استئناف»). */
    fun resume() {
        val saved = resumable ?: store.load() ?: return
        if (isRunning || settings.token.isBlank()) return
        session = saved
        resumable = null
        userCancelled = false
        errors.clear(); askQueue.clear(); targetNames.clear(); savedApks = emptyList()
        startedAt = saved.startedAt
        runUrl = saved.runUrl
        branch = saved.branch
        pausedFrom = phaseFor(saved.stage)
        phase = Phase.PAUSED
        log(LogLevel.INFO, "استئناف البناء من مرحلة «${saved.stage.label}»" + if (saved.runId != 0L) " (التشغيل على GitHub لم يتوقف)" else "")
        status("استئناف من «${saved.stage.label}»…")
        val api = GitHubApi(settings.token)

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                net.await()
                phase = pausedFrom
                runStages(api)
            } catch (e: CancellationException) {
                onCancelled(api)
                throw e
            } catch (e: Exception) {
                onFailed(e)
            }
        }
    }

    fun cancel() { userCancelled = true; job?.cancel() }

    private suspend fun onCancelled(api: GitHubApi) {
        val s = session
        if (userCancelled) {
            // المستخدم ضغط «إيقاف»: نلغي التشغيل على GitHub وننهي الجلسة
            if (s != null && s.runId != 0L) withContext(NonCancellable) { api.cancel(s.repo, s.runId) }
            withContext(NonCancellable) { discardSession(s) }
            finish(Phase.CANCELLED, "أُلغي البناء")
        } else {
            // أُغلق التطبيق/الشاشة: نبقي الجلسة محفوظة ليكمل البناء عند الفتح
            resumable = s
        }
    }

    private fun onFailed(e: Exception) {
        errors.add(BuildError(e.message ?: e.javaClass.simpleName, null, null, null, "ApkBuilder"))
        log(LogLevel.ERROR, e.message ?: e.javaClass.simpleName)
        val s = session
        if (s != null && s.stage >= Stage.DISPATCH) {
            // فشل بعد أن بدأ البناء على GitHub: نحتفظ بالتقدم ليمكن استئنافه بدل البدء من الصفر
            resumable = s
            finish(Phase.FAILED, "توقف عند «${s.stage.label}» — يمكنك الاستئناف")
        } else {
            discardSession(s)
            finish(Phase.FAILED, "تعذر إكمال البناء")
        }
    }

    /** يمر على المراحل بالترتيب؛ كل مرحلة تحفظ تقدمها فيستأنف من حيث توقف. تنتهي عندما تُمسح الجلسة. */
    private suspend fun runStages(api: GitHubApi) {
        while (true) {
            val s = session ?: return
            when (s.stage) {
                Stage.UPLOAD -> stageUpload(api)
                Stage.WAIT_WORKFLOW -> stageWaitWorkflow(api)
                Stage.DISPATCH -> stageDispatch(api)
                Stage.FIND_RUN -> stageFindRun(api)
                Stage.POLL -> stagePoll(api)
                Stage.LOGS -> stageLogs(api)
                Stage.APK -> stageApk(api)
                Stage.DONE -> return
            }
        }
    }

    private suspend fun stageUpload(api: GitHubApi) {
        val s = session!!
        phase = Phase.UPLOADING
        status("فحص المشروع…")
        val upload = LinkedHashMap<String, ByteArray>()
        val info: ProjectInfo
        val prefix: String
        if (s.projectZip != null) {
            val files = File(s.projectZip).inputStream().use { ProjectDetector.readZip(it) }
            info = ProjectDetector.detect(files.keys) { files[it]?.toString(Charsets.UTF_8) }
                ?: error("لم أتعرف على نوع المشروع — تأكد أن الـ zip يحتوي مشروعاً كاملاً")
            prefix = ProjectDetector.UPLOAD_DIR
            files.forEach { (path, data) -> upload["$prefix/$path"] = data }
        } else {
            val paths = resilient { api.tree(s.repo, s.branch) }
            info = resilient { ProjectDetector.detect(paths) { api.readText(s.repo, it, s.branch) } }
                ?: error("لم أتعرف على مشروع داخل المستودع ${s.repo} — اختر ملف zip للمشروع")
            prefix = ""
        }
        if (s.infoLabel.isBlank()) log(LogLevel.OK, "تم التعرف على المشروع: ${info.summary}")
        patch { copy(infoLabel = info.summary) }

        upload[ProjectDetector.WORKFLOW_PATH] = ProjectDetector.workflow(info, prefix, s.appName).toByteArray(Charsets.UTF_8)
        status("رفع ${upload.size} ملف إلى ${s.repo}…")
        if (s.upload == null) log(LogLevel.INFO, "رفع ${upload.size} ملف (يشمل ملف البناء المولَّد تلقائياً)")
        val sha = resilient {
            api.commitFiles(
                s.repo, s.branch, upload, "ApkBuilder: build ${info.type.label}",
                replaceDir = if (s.projectZip != null) prefix else null,
                onProgress = { status(it) },
                resume = session?.upload,
                onCheckpoint = { cp -> patch { copy(upload = cp) } }
            )
        }
        patch { copy(commitSha = sha, upload = null) }
        runCatching { sessionZip.delete() }
        log(LogLevel.OK, "تم الرفع — commit ${sha.take(7)}")
        setStage(Stage.WAIT_WORKFLOW)
    }

    private suspend fun stageWaitWorkflow(api: GitHubApi) {
        val s = session!!
        phase = Phase.UPLOADING
        status("تفعيل ملف البناء…")
        var wfId = 0L
        var tries = 0
        while (wfId == 0L && tries < 10) {
            tries++
            val match = resilient { api.workflows(s.repo) }.firstOrNull { it.path == ProjectDetector.WORKFLOW_PATH }
            if (match != null) { wfId = match.id; settings.setWorkflow(match.id, match.name) } else delay(3000)
        }
        if (wfId == 0L) error("لم يظهر الـ workflow على GitHub — تأكد أن التوكن يملك صلاحية workflow وأن Actions مفعّل في المستودع")
        patch { copy(wfId = wfId) }
        setStage(Stage.DISPATCH)
    }

    private suspend fun stageDispatch(api: GitHubApi) {
        val s = session!!
        phase = Phase.QUEUED
        status("بانتظار بدء البناء على GitHub…")
        if (s.runsBefore < 0) {
            val before = resilient { api.runs(s.repo, s.wfId) }.maxOfOrNull { it.id } ?: 0L
            patch { copy(runsBefore = before) }
        }
        val before = session!!.runsBefore
        var attempt = 0
        var maybeSent = false
        var pauses = 0
        while (true) {
            // ربما وصل طلب التشغيل فعلاً (قبل الانقطاع) — نتأكد حتى لا يُشغَّل البناء مرتين
            if (maybeSent) delay(6000)
            if (resilient { api.runs(s.repo, s.wfId) }.any { it.id > before }) break
            try {
                api.dispatch(s.repo, s.wfId, s.branch)
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is GitHubException && e.code == 422 && ++attempt < 6) { delay(4000); continue }
                if (e is GitHubException && e.code == 401) throw e
                if (!isTransient(e)) throw e
                maybeSent = true
                pauseAndWait(e, ++pauses)
            }
        }
        setStage(Stage.FIND_RUN)
    }

    private suspend fun stageFindRun(api: GitHubApi) {
        val s = session!!
        phase = Phase.QUEUED
        var run: GhRun? = null
        repeat(20) { if (run == null) { delay(3000); run = resilient { api.runs(s.repo, s.wfId) }.firstOrNull { it.id > s.runsBefore } } }
        val started = run ?: error("لم يبدأ أي تشغيل على GitHub — تحقق من تبويب Actions في المستودع")
        patch { copy(runId = started.id, runUrl = started.htmlUrl) }
        runUrl = started.htmlUrl
        log(LogLevel.OK, "بدأ التشغيل #${started.runNumber} — ${started.htmlUrl}")
        setStage(Stage.POLL)
    }

    private suspend fun stagePoll(api: GitHubApi) {
        val s = session!!
        var current = resilient { api.run(s.repo, s.runId) }
        while (current.status != "completed") {
            if (current.status == "in_progress") {
                phase = Phase.BUILDING
                status(runCatching { api.currentStep(s.repo, s.runId) }.getOrNull()?.let { "الخطوة: $it" } ?: "جارٍ البناء…")
            } else {
                phase = Phase.QUEUED
                status("في الطابور…")
            }
            delay(5000)
            current = resilient { api.run(s.repo, s.runId) }
        }
        patch { copy(conclusion = current.conclusion ?: "none") }
        setStage(Stage.LOGS)
    }

    private suspend fun stageLogs(api: GitHubApi) {
        val s = session!!
        phase = Phase.DOWNLOADING
        status("تنزيل السجل…")
        val success = s.conclusion == "success"
        resilient { downloadLogs(api, s.repo, s.runId, parseErrors = !success) }
        if (success) {
            setStage(Stage.APK)
        } else {
            if (errors.isEmpty()) {
                errors.add(BuildError("فشل البناء (${s.conclusion?.takeIf { it != "none" } ?: "بدون نتيجة"}) — راجع السجل الكامل", null, null, null, "GitHub Actions"))
            }
            discardSession(s)
            finish(Phase.FAILED, "فشل البناء")
        }
    }

    private suspend fun stageApk(api: GitHubApi) {
        val s = session!!
        phase = Phase.DOWNLOADING
        status("تنزيل ملف APK…")
        var apks = resilient { downloadApks(api, s.repo, s.runId) }
        var waits = 0
        while (apks.isEmpty() && waits < 4) { waits++; delay(3000); apks = resilient { downloadApks(api, s.repo, s.runId) } }
        if (apks.isEmpty()) {
            log(LogLevel.WARN, "اكتمل البناء لكن لا يوجد APK ضمن الـ artifacts. تأكد أن الـ workflow يستخدم actions/upload-artifact.")
            discardSession(s)
            finish(Phase.SUCCESS, "اكتمل البناء (لا يوجد APK ناتج)")
        } else {
            val saved = apks.mapIndexed { i, f -> save(f, i) }.filterNotNull()
            withContext(Dispatchers.Main) { savedApks = saved }
            discardSession(s)
            finish(Phase.SUCCESS, "تم البناء بنجاح ✓")
        }
    }

    private fun finish(p: Phase, text: String) { phase = p; statusText = text }

    // ------------------------------------------------------------------- السجل

    private val timestamp = Regex("^\\uFEFF?\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z ")

    private fun downloadLogs(api: GitHubApi, repo: String, runId: Long, parseErrors: Boolean) {
        val zip = File(app.cacheDir, "run_logs_$runId.zip")
        try {
            api.downloadLogs(repo, runId, zip)
        } catch (e: Exception) {
            if (isTransient(e)) throw e   // انقطاع: نُبقي الجزء المنزَّل ونكمل بعد عودة الإنترنت
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
            // ما اكتمل تنزيله سابقاً يُستخدم كما هو، والجزئي يُكمَل من حيث توقف (Range)
            if (!zip.exists()) api.downloadArtifact(repo, art.id, zip)
            try {
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
            } catch (e: IOException) {
                zip.delete()   // ملف تالف → التنزيل التالي يبدأ نظيفاً
                throw e
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
