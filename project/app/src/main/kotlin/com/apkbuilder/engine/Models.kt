package com.apkbuilder.engine

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

enum class Phase { IDLE, UPLOADING, QUEUED, BUILDING, DOWNLOADING, SUCCESS, FAILED, CANCELLED }

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
