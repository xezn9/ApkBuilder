package com.apkbuilder.engine

/**
 * يحلل مخرجات البناء سطراً بسطر ويستخرج: نص الخطأ، الملف، السطر، العمود.
 * يدعم: Kotlin، javac، AAPT2، سكربتات Gradle، فشل حل المكتبات، Stack traces، وأخطاء الشيل.
 */
class ErrorParser(private val onError: (BuildError) -> Unit) {

    private val seen = HashSet<String>()

    // e: file:///path/Foo.kt:12:5 message      |  e: /path/Foo.kt:12:5 message
    private val kotlinNew = Regex("""^e:\s+(?:file://)?(/?\S.*?\.kts?):(\d+):(\d+)\s+(.*)$""")
    // e: /path/Foo.kt: (12, 5): message
    private val kotlinOld = Regex("""^e:\s+(?:file://)?(.+?\.kts?):\s*\((\d+),\s*(\d+)\):\s*(.*)$""")
    // /path/Foo.java:12: error: message
    private val javac = Regex("""^(.+?\.java):(\d+):\s*error:\s*(.*)$""")
    // ERROR: /path/res/x.xml:12: AAPT: error: message   |  /path/x.xml:12: error: message
    private val aapt = Regex("""^(?:ERROR:\s*)?(.+?\.(?:xml|png|9\.png)):(\d+)(?::(\d+))?:\s*(?:AAPT:\s*)?error:\s*(.*)$""")
    private val aaptNoLine = Regex("""^(?:ERROR:\s*)?(.+?\.(?:xml|png)):\s*(?:AAPT:\s*)?error:\s*(.*)$""")
    // Build file '/x/build.gradle.kts' line: 12
    private val gradleWhere = Regex("""^(Build file|Settings file|Script|Initialization script)\s+'(.+?)'\s+line:\s*(\d+)""")
    private val gradleScriptErrKts = Regex("""^e:\s+(?:file://)?(.+?\.gradle\.kts):(\d+):(\d+)\s+(.*)$""")
    private val stackFrame = Regex("""^\s*at\s+[\w.$<>]+\(([\w$.\-]+\.(?:java|kt|groovy|kts)):(\d+)\)""")
    private val exceptionLine = Regex("""^(?:Caused by:\s*)?([\w.$]+(?:Exception|Error)):\s*(.*)$""")
    private val resolveFail = Regex("""^\s*>?\s*(Could not (?:resolve|find|get|download)\s.*)$""")
    private val shellFail = Regex("""^(?:sh:|/system/bin/sh:)?\s*(?:\d+:\s*)?(.*?):\s*(not found|No such file or directory|Permission denied|inaccessible or not found)\s*$""")
    private val genericError = Regex("""^(?:error|ERROR|Error|FAILURE)[:!]\s*(.*)$""")

    // حالة داخلية
    private var pendingJavac: BuildError? = null
    private var javacLinesLeft = 0
    private var whereFile: String? = null
    private var whereLine: Int? = null
    private var inWhatWentWrong = false
    private val wrongBuf = StringBuilder()
    private var pendingException: String? = null
    private var lastGradleFileHint: Pair<String, Int>? = null

    fun feed(raw: String) {
        val line = raw.trimEnd()

        // javac: السطر بعد الخطأ = الكود، والذي بعده = ^ (العمود)
        pendingJavac?.let { pj ->
            javacLinesLeft--
            if (line.trimStart().startsWith("^") && line.contains("^")) {
                emit(pj.copy(column = line.indexOf('^') + 1))
                pendingJavac = null
                return
            }
            if (javacLinesLeft <= 0) { emit(pj); pendingJavac = null }
            else return
        }

        // قسم "What went wrong"
        if (inWhatWentWrong) {
            val t = line.trim()
            if (t.startsWith("* Try:") || t.startsWith("* Get more help") || t.startsWith("BUILD FAILED") ||
                (t.isEmpty() && wrongBuf.isNotEmpty())
            ) {
                flushWrong()
            } else if (t.isNotEmpty()) {
                if (wrongBuf.isNotEmpty()) wrongBuf.append(' ')
                wrongBuf.append(t.removePrefix("> ").removePrefix("> "))
                return
            }
        }
        if (line.trim() == "* What went wrong:") { inWhatWentWrong = true; wrongBuf.setLength(0); return }
        if (line.trim() == "* Where:") { whereFile = null; whereLine = null; return }

        gradleWhere.find(line)?.let {
            whereFile = it.groupValues[2]; whereLine = it.groupValues[3].toIntOrNull()
            lastGradleFileHint = whereFile!! to (whereLine ?: 0)
            return
        }

        gradleScriptErrKts.find(line)?.let { m ->
            emit(BuildError(m.groupValues[4], m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt(), "Gradle script")); return
        }
        kotlinNew.find(line)?.let { m ->
            emit(BuildError(m.groupValues[4], m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt(), "Kotlin")); return
        }
        kotlinOld.find(line)?.let { m ->
            emit(BuildError(m.groupValues[4], m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt(), "Kotlin")); return
        }
        javac.find(line)?.let { m ->
            pendingJavac = BuildError(m.groupValues[3], m.groupValues[1], m.groupValues[2].toInt(), null, "javac")
            javacLinesLeft = 3
            return
        }
        aapt.find(line)?.let { m ->
            emit(BuildError(m.groupValues[4], m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull(), "AAPT2")); return
        }
        aaptNoLine.find(line)?.let { m ->
            emit(BuildError(m.groupValues[2], m.groupValues[1], null, null, "AAPT2")); return
        }
        resolveFail.find(line)?.let { m ->
            val hint = lastGradleFileHint
            emit(BuildError(m.groupValues[1], hint?.first, hint?.second?.takeIf { it > 0 }, null, "Dependency")); return
        }

        // Stack trace: نربط أول إطار بالاستثناء
        pendingException?.let { msg ->
            stackFrame.find(line)?.let { m ->
                emit(BuildError(msg, m.groupValues[1], m.groupValues[2].toInt(), null, "Exception"))
                pendingException = null
                return
            }
            if (!line.trimStart().startsWith("at ")) {
                emit(BuildError(msg, null, null, null, "Exception")); pendingException = null
            }
        }
        exceptionLine.find(line)?.let { m ->
            if (line.startsWith("Caused by") || line.startsWith(m.groupValues[1])) {
                pendingException = "${m.groupValues[1]}: ${m.groupValues[2]}".trim()
                return
            }
        }

        shellFail.find(line)?.let { m ->
            emit(BuildError("${m.groupValues[1]}: ${m.groupValues[2]}", null, null, null, "Shell")); return
        }
        genericError.find(line)?.let { m ->
            if (m.groupValues[1].isNotBlank()) emit(BuildError(m.groupValues[1], null, null, null, "Build")); return
        }
    }

    /** يُستدعى عند نهاية الخرج لتفريغ أي خطأ معلّق. */
    fun finish() {
        pendingJavac?.let { emit(it); pendingJavac = null }
        pendingException?.let { emit(BuildError(it, null, null, null, "Exception")); pendingException = null }
        flushWrong()
    }

    private fun flushWrong() {
        if (!inWhatWentWrong) return
        inWhatWentWrong = false
        if (wrongBuf.isNotEmpty()) {
            emit(BuildError(wrongBuf.toString(), whereFile, whereLine, null, "Gradle"))
        }
        wrongBuf.setLength(0)
        whereFile = null; whereLine = null
    }

    private fun emit(e: BuildError) {
        if (seen.add(e.key)) onError(e)
    }
}
