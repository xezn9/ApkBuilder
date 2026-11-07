package com.apkbuilder.engine

import org.json.JSONObject

enum class LogLevel { INFO, OK, WARN, ERROR, CMD }

data class LogLine(
    val level: LogLevel,
    val text: String,
    val time: Long = System.currentTimeMillis()
)

/** خطأ بناء مُحلَّل: نص الخطأ + مكانه (الملف) + السطر + العمود إن وُجد. */
data class BuildError(
    val message: String,
    val file: String?,
    val line: Int?,
    val column: Int?,
    val source: String
) {
    val key: String get() = "$file:$line:$column:$message"

    val location: String
        get() = buildString {
            append(file ?: "—")
            if (line != null) append(":").append(line)
            if (column != null) append(":").append(column)
        }
}

enum class Phase { IDLE, UPLOADING, QUEUED, BUILDING, DOWNLOADING, PAUSED, SUCCESS, FAILED, CANCELLED }

data class GhWorkflow(val id: Long, val name: String, val path: String)

data class GhRun(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val headSha: String,
    val htmlUrl: String,
    val runNumber: Int
)

data class GhArtifact(val id: Long, val name: String, val sizeBytes: Long, val expired: Boolean)

// ============================================================ الاستئناف بعد الانقطاع

/** مراحل البناء بالترتيب — تُحفظ على القرص ليكمل التطبيق من آخر مرحلة وصل لها. */
enum class Stage(val label: String) {
    UPLOAD("رفع المشروع"),
    WAIT_WORKFLOW("تفعيل ملف البناء"),
    DISPATCH("تشغيل البناء"),
    FIND_RUN("البحث عن التشغيل"),
    POLL("متابعة البناء"),
    LOGS("تنزيل السجل"),
    APK("تنزيل APK"),
    DONE("اكتمل")
}

/** نقطة استئناف للرفع: ما أُنشئ على GitHub يبقى محفوظاً هناك، فنكمل منه بدل البدء من الصفر. */
data class UploadCheckpoint(
    val fingerprint: String,
    val parentSha: String,
    val currentTree: String,
    /** فهرس أول ملف لم يُرفع بعد. */
    val nextIndex: Int,
    /** يُحدَّد بعد إنشاء الـ commit (قبل تحريك الفرع إليه). */
    val commitSha: String? = null
)

/** حالة بناء جارٍ، تُحفظ بعد كل مرحلة. */
data class BuildSession(
    val repo: String,
    val branch: String,
    val stage: Stage,
    val appName: String,
    val startedAt: Long,
    /** نسخة خاصة من zip المشروع داخل التطبيق (null = البناء من محتوى المستودع). */
    val projectZip: String? = null,
    val infoLabel: String = "",
    val upload: UploadCheckpoint? = null,
    val commitSha: String? = null,
    val wfId: Long = 0L,
    /** أكبر رقم تشغيل قبل الإرسال؛ -1 = لم يُسجَّل بعد. */
    val runsBefore: Long = -1L,
    val runId: Long = 0L,
    val runUrl: String? = null,
    val conclusion: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("repo", repo); put("branch", branch); put("stage", stage.name); put("appName", appName)
        put("startedAt", startedAt); put("projectZip", projectZip ?: JSONObject.NULL)
        put("infoLabel", infoLabel); put("commitSha", commitSha ?: JSONObject.NULL)
        put("wfId", wfId); put("runsBefore", runsBefore); put("runId", runId)
        put("runUrl", runUrl ?: JSONObject.NULL); put("conclusion", conclusion ?: JSONObject.NULL)
        upload?.let {
            put("upload", JSONObject().put("fp", it.fingerprint).put("parent", it.parentSha)
                .put("tree", it.currentTree).put("next", it.nextIndex).put("commit", it.commitSha ?: JSONObject.NULL))
        }
    }.toString()

    companion object {
        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }

        fun fromJson(text: String): BuildSession? = runCatching {
            val o = JSONObject(text)
            BuildSession(
                repo = o.getString("repo"), branch = o.getString("branch"),
                stage = Stage.valueOf(o.getString("stage")), appName = o.getString("appName"),
                startedAt = o.optLong("startedAt", System.currentTimeMillis()),
                projectZip = o.str("projectZip"), infoLabel = o.optString("infoLabel"),
                upload = o.optJSONObject("upload")?.let {
                    UploadCheckpoint(it.getString("fp"), it.getString("parent"), it.getString("tree"), it.getInt("next"), it.str("commit"))
                },
                commitSha = o.str("commitSha"), wfId = o.optLong("wfId"),
                runsBefore = o.optLong("runsBefore", -1L), runId = o.optLong("runId"),
                runUrl = o.str("runUrl"), conclusion = o.str("conclusion")
            )
        }.getOrNull()
    }
}
