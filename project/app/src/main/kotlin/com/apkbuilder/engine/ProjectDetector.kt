package com.apkbuilder.engine

import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/** أنواع المشاريع التي يتعرف عليها التطبيق ويبنيها. */
enum class ProjectType(val label: String) {
    ANDROID("Android (Gradle)"),
    FLUTTER("Flutter"),
    REACT_NATIVE("React Native"),
    EXPO("Expo"),
    CAPACITOR("Capacitor / Ionic"),
    CORDOVA("Cordova"),
    KIVY("Python (Kivy / Buildozer)"),
    WEB("موقع ويب (يُغلَّف كتطبيق)")
}

/** نتيجة التعرف على المشروع. */
data class ProjectInfo(
    val type: ProjectType,
    /** مسار مجلد المشروع داخل الأرشيف أو المستودع ("" = الجذر). */
    val root: String,
    val jdk: String = "17",
    val node: String = "20",
    /** إصدار Gradle المطلوب تثبيته — فقط عند غياب gradlew أو gradle-wrapper.jar. */
    val gradleVersion: String? = null,
    val pm: String = "npm",
    val hasAndroidDir: Boolean = false,
    val hasBuildScript: Boolean = false,
    val flutterName: String = "app"
) {
    val summary: String
        get() = if (root.isEmpty()) type.label else "${type.label} — $root"
}

class UnsupportedProjectException(message: String) : Exception(message)

/**
 * يتعرف على نوع المشروع من قائمة مساراته (سواء من zip أو من المستودع)،
 * ثم يولّد ملف GitHub Actions المناسب له. المستخدم لا يحتاج لكتابة build.yml أبداً.
 */
object ProjectDetector {

    const val WORKFLOW_PATH = ".github/workflows/apkbuilder.yml"
    const val UPLOAD_DIR = "project"

    // ------------------------------------------------------------------ تنظيف المسارات

    private val JUNK_SEGMENTS = setOf(".git", ".gradle", "node_modules", "__MACOSX", ".idea", ".dart_tool", "Pods", ".github")
    private val SOURCE_SEGMENTS = setOf("src", "lib", "java", "kotlin", "sources", "include")

    /** ملفات ناتجة عن البناء أو لا قيمة لها — لا تُرفع ولا تُحلَّل. */
    fun isJunk(path: String): Boolean {
        val segs = path.split('/')
        for ((i, s) in segs.withIndex()) {
            if (s in JUNK_SEGMENTS) return true
            // مجلد build الناتج عن Gradle/Flutter (وليس حزمة برمجية اسمها build داخل src)
            if (s == "build" && i < segs.size - 1 && segs.take(i).none { it in SOURCE_SEGMENTS }) return true
        }
        val name = segs.last()
        return name == ".DS_Store" || name == "local.properties" ||
            name.endsWith(".apk", true) || name.endsWith(".aab", true)
    }

    /** يقرأ zip إلى خريطة (مسار ← بايتات) ويزيل المجلد الجذري الوحيد إن وُجد. */
    fun readZip(input: InputStream): Map<String, ByteArray> {
        val raw = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input.buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val name = e.name.replace('\\', '/').trimStart('/')
                if (!e.isDirectory && name.isNotEmpty() && !name.split('/').contains("..") && !isJunk(name)) {
                    raw[name] = z.readBytes()
                }
                e = z.nextEntry
            }
        }
        if (raw.isEmpty()) return raw
        val single = raw.keys.map { it.substringBefore('/') }.toSet().size == 1
        return if (single && raw.keys.all { it.contains('/') }) raw.mapKeys { it.key.substringAfter('/') } else raw
    }

    // ------------------------------------------------------------------ التعرف

    private fun depth(d: String) = if (d.isEmpty()) 0 else d.count { it == '/' } + 1
    private fun join(a: String, b: String) = if (a.isEmpty()) b else "$a/$b"

    /**
     * @param read يقرأ محتوى ملف نصي (أو null إن لم يوجد) — يُستدعى للملفات الصغيرة فقط.
     * @return null إن لم يُعرف النوع، ويرمي [UnsupportedProjectException] للأنواع المعروفة غير المدعومة.
     */
    fun detect(allPaths: Collection<String>, read: (String) -> String?): ProjectInfo? {
        val paths = allPaths.filter { !isJunk(it) }
        val set = paths.toHashSet()

        fun has(dir: String, name: String) = join(dir, name) in set
        fun hasDir(dir: String, name: String): Boolean {
            val p = join(dir, name) + "/"
            return paths.any { it.startsWith(p) }
        }
        fun dirsOf(vararg names: String): List<String> =
            paths.filter { p -> names.any { n -> p == n || p.endsWith("/$n") } }
                .map { it.substringBeforeLast('/', "") }
                .distinct()
                .filter { depth(it) <= 3 }
                .sortedBy { depth(it) }

        fun pmOf(d: String) = when {
            has(d, "pnpm-lock.yaml") -> "pnpm"
            has(d, "yarn.lock") -> "yarn"
            else -> "npm"
        }

        // 1) Flutter
        for (d in dirsOf("pubspec.yaml")) {
            val txt = read(join(d, "pubspec.yaml")) ?: continue
            if (!Regex("sdk:\\s*flutter").containsMatchIn(txt)) continue
            val name = Regex("(?m)^name:\\s*([A-Za-z0-9_]+)").find(txt)?.groupValues?.get(1) ?: "app"
            return ProjectInfo(ProjectType.FLUTTER, d, hasAndroidDir = hasDir(d, "android"), flutterName = name.lowercase())
        }

        // 2) Kivy / Buildozer
        dirsOf("buildozer.spec").firstOrNull()?.let { return ProjectInfo(ProjectType.KIVY, it) }

        // 3) Capacitor / Ionic
        dirsOf("capacitor.config.ts", "capacitor.config.js", "capacitor.config.json").firstOrNull()?.let { d ->
            val txt = read(join(d, "package.json")).orEmpty()
            val major = Regex("\"@capacitor/(?:core|android)\"\\s*:\\s*\"[^0-9\"]*([0-9]+)").find(txt)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 7
            return ProjectInfo(
                ProjectType.CAPACITOR, d,
                jdk = if (major >= 7) "21" else "17",
                node = if (major >= 7) "22" else "20",
                pm = pmOf(d), hasAndroidDir = hasDir(d, "android")
            )
        }

        // 4) React Native / Expo — وفي نفس الحلقة نلتقط مشاريع الويب العاملة بـ npm
        var webNodeDir: String? = null
        for (d in dirsOf("package.json")) {
            val txt = read(join(d, "package.json")) ?: continue
            val pj = try { JSONObject(txt) } catch (_: Exception) { continue }
            val deps = HashSet<String>()
            for (k in listOf("dependencies", "devDependencies")) {
                pj.optJSONObject(k)?.keys()?.forEach { deps.add(it) }
            }
            val type = when {
                "expo" in deps -> ProjectType.EXPO
                "react-native" in deps -> ProjectType.REACT_NATIVE
                else -> null
            }
            if (type != null) {
                return ProjectInfo(type, d, pm = pmOf(d), hasAndroidDir = hasDir(d, "android"))
            }
            if (webNodeDir == null && pj.optJSONObject("scripts")?.has("build") == true &&
                !deps.contains("cordova-android")
            ) webNodeDir = d
        }

        // 5) Cordova
        for (d in dirsOf("config.xml")) {
            if (read(join(d, "config.xml"))?.contains("<widget") == true) {
                return ProjectInfo(ProjectType.CORDOVA, d, hasAndroidDir = hasDir(d, "platforms/android"))
            }
        }

        // 6) Android Gradle عادي (Kotlin / Java)
        for (d in dirsOf("settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts")) {
            val p = if (d.isEmpty()) "" else "$d/"
            if (paths.none { it.startsWith(p) && it.endsWith("AndroidManifest.xml") }) continue

            val text = listOf(
                "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle/libs.versions.toml"
            ).mapNotNull { read(join(d, it)) }.joinToString("\n")
            val agp = listOf(
                Regex("com\\.android\\.(?:application|library)[\"')\\s]+version\\s*\\(?\\s*[\"']([0-9]+)\\.([0-9]+)"),
                Regex("com\\.android\\.tools\\.build:gradle:([0-9]+)\\.([0-9]+)"),
                Regex("(?m)^\\s*(?:agp|androidGradlePlugin|android-gradle-plugin|androidGradle)\\s*=\\s*\"([0-9]+)\\.([0-9]+)")
            ).firstNotNullOfOrNull { it.find(text) }
                ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

            val jdk = if (agp != null && (agp.first < 7 || (agp.first == 7 && agp.second < 3))) "11" else "17"
            val hasWrapper = has(d, "gradlew") && has(d, "gradle/wrapper/gradle-wrapper.jar")
            val wrapperVer = read(join(d, "gradle/wrapper/gradle-wrapper.properties"))?.let {
                Regex("gradle-([0-9][0-9A-Za-z.\\-]*?)-(?:bin|all)\\.zip").find(it)?.groupValues?.get(1)
            }
            val gradleVer = wrapperVer ?: when {
                agp == null -> "8.14.3"
                agp.first >= 9 -> "current"
                agp.first == 8 -> "8.14.3"
                agp.first == 7 -> "7.6.4"
                else -> "6.9.4"
            }
            return ProjectInfo(ProjectType.ANDROID, d, jdk = jdk, gradleVersion = if (hasWrapper) null else gradleVer)
        }

        // 7) موقع ويب: مشروع npm له سكربت build، أو index.html بسيط
        webNodeDir?.let { return ProjectInfo(ProjectType.WEB, it, jdk = "21", node = "22", pm = pmOf(it), hasBuildScript = true) }
        dirsOf("index.html").firstOrNull { depth(it) <= 1 }?.let {
            return ProjectInfo(ProjectType.WEB, it, jdk = "21", node = "22")
        }

        // أنواع معروفة لكن غير مدعومة
        if (has("", "ProjectSettings/ProjectVersion.txt") || dirsOf("ProjectVersion.txt").isNotEmpty())
            throw UnsupportedProjectException("مشروع Unity يحتاج تفعيل ترخيص Unity ولا يمكن بناؤه تلقائياً على GitHub Actions.")
        if (dirsOf("project.godot").isNotEmpty())
            throw UnsupportedProjectException("مشاريع Godot غير مدعومة حالياً (تحتاج قوالب التصدير وإعداد توقيع).")
        if (paths.any { it.endsWith(".csproj") })
            throw UnsupportedProjectException("مشاريع .NET / MAUI / Xamarin غير مدعومة حالياً.")
        return null
    }

    // ------------------------------------------------------------------ توليد الـ workflow

    private fun q(s: String) = "'" + s.replace("'", "''") + "'"

    private fun gradleCmd(task: String) =
        "if [ -f gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then\n" +
            "  chmod +x gradlew\n" +
            "  ./gradlew $task --stacktrace\n" +
            "else\n" +
            "  gradle $task --stacktrace\n" +
            "fi"

    private fun installCmd(pm: String) = when (pm) {
        "yarn" -> "yarn install"
        "pnpm" -> "corepack enable\npnpm install"
        else -> "npm install --legacy-peer-deps --no-audit --no-fund"
    }

    /**
     * @param prefix المجلد الذي رُفع فيه المشروع في المستودع ("" إن كان المشروع أصلاً في جذر المستودع).
     * @param appName اسم التطبيق (يُستخدم لمشاريع الويب فقط).
     */
    fun workflow(info: ProjectInfo, prefix: String, appName: String): String {
        val dir = join(prefix, info.root)
        val sb = StringBuilder()

        fun step(
            name: String,
            uses: String? = null,
            params: List<Pair<String, String>> = emptyList(),
            workDir: String? = null,
            run: String? = null
        ) {
            sb.appendLine("      - name: ${q(name)}")
            if (uses != null) sb.appendLine("        uses: $uses")
            if (!workDir.isNullOrEmpty()) sb.appendLine("        working-directory: ${q(workDir)}")
            if (params.isNotEmpty()) {
                sb.appendLine("        with:")
                params.forEach { (k, v) -> sb.appendLine("          $k: ${q(v)}") }
            }
            if (run != null) {
                sb.appendLine("        run: |")
                run.lines().forEach { sb.appendLine("          $it") }
            }
        }

        fun javaStep() = step("إعداد Java ${info.jdk}", "actions/setup-java@v4",
            listOf("distribution" to "temurin", "java-version" to info.jdk))
        // نكتب تراخيص الـ SDK يدوياً بدل android-actions/setup-android (كان يفشل بـ sdkmanager exit code 1)
        fun sdkStep() = step("تجهيز Android SDK", run = listOf(
            "SDK=\"\${ANDROID_HOME:-\${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}\"",
            "LIC='\\n24333f8a63b6825ea9c5514f83c2829b004d1fee\\n8933bad161af4178b1185d1a37fbf41ea5269c55\\nd56f5187479451eabf01fb78af6dfcb131a6481e\\n'",
            "sudo mkdir -p \"\$SDK/licenses\"",
            "printf \"\$LIC\" | sudo tee \"\$SDK/licenses/android-sdk-license\" > /dev/null",
            "echo \"ANDROID_HOME=\$SDK\" >> \"\$GITHUB_ENV\"",
            "echo \"ANDROID_SDK_ROOT=\$SDK\" >> \"\$GITHUB_ENV\""
        ).joinToString("\n"))
        fun nodeStep() = step("إعداد Node ${info.node}", "actions/setup-node@v4", listOf("node-version" to info.node))
        fun uploadStep(path: String) = step("رفع ملف APK", "actions/upload-artifact@v4",
            listOf("name" to "apk", "path" to path, "if-no-files-found" to "error"))

        sb.appendLine("# ملف مُولَّد تلقائياً بواسطة ApkBuilder (${info.type.label}) — يُستبدل عند كل بناء، لا تعدّله يدوياً.")
        sb.appendLine("name: ApkBuilder")
        sb.appendLine("on:")
        sb.appendLine("  workflow_dispatch:")
        sb.appendLine("jobs:")
        sb.appendLine("  build:")
        sb.appendLine("    runs-on: ubuntu-latest")
        sb.appendLine("    timeout-minutes: 60")
        sb.appendLine("    steps:")
        step("تنزيل الكود", "actions/checkout@v4")

        when (info.type) {
            ProjectType.ANDROID -> {
                javaStep(); sdkStep()
                step("إعداد Gradle", "gradle/actions/setup-gradle@v4",
                    info.gradleVersion?.let { listOf("gradle-version" to it) } ?: emptyList())
                step("بناء APK", workDir = dir, run = gradleCmd("assembleDebug"))
                uploadStep(join(dir, "**/build/outputs/apk/**/*.apk"))
            }

            ProjectType.FLUTTER -> {
                javaStep()
                step("إعداد Flutter", "subosito/flutter-action@v2", listOf("channel" to "stable", "cache" to "true"))
                if (!info.hasAndroidDir) {
                    step("إنشاء منصة أندرويد", workDir = dir,
                        run = "flutter create --platforms=android --project-name ${info.flutterName} .")
                }
                step("تنزيل الحزم", workDir = dir, run = "flutter pub get")
                step("بناء APK", workDir = dir, run = "flutter build apk --release")
                uploadStep(join(dir, "build/app/outputs/flutter-apk/*.apk"))
            }

            ProjectType.REACT_NATIVE, ProjectType.EXPO -> {
                javaStep(); sdkStep(); nodeStep()
                step("تثبيت الحزم", workDir = dir, run = installCmd(info.pm))
                if (!info.hasAndroidDir) {
                    step("توليد مجلد android (Expo)", workDir = dir, run = "npx expo prebuild --platform android --no-install")
                }
                step("بناء APK", workDir = join(dir, "android"), run = gradleCmd("assembleRelease"))
                uploadStep(join(dir, "android/app/build/outputs/apk/**/*.apk"))
            }

            ProjectType.CAPACITOR -> {
                javaStep(); sdkStep(); nodeStep()
                step("تثبيت الحزم", workDir = dir, run = installCmd(info.pm))
                step("بناء الواجهة", workDir = dir, run = "npm run build --if-present")
                if (!info.hasAndroidDir) step("إضافة منصة أندرويد", workDir = dir, run = "npx cap add android")
                step("مزامنة Capacitor", workDir = dir, run = "npx cap sync android")
                step("بناء APK", workDir = join(dir, "android"), run = gradleCmd("assembleDebug"))
                uploadStep(join(dir, "android/app/build/outputs/apk/**/*.apk"))
            }

            ProjectType.CORDOVA -> {
                javaStep(); sdkStep(); nodeStep()
                step("تثبيت Cordova", run = "npm install -g cordova")
                step("تثبيت الحزم", workDir = dir, run = "if [ -f package.json ]; then npm install --legacy-peer-deps --no-audit --no-fund; fi")
                if (!info.hasAndroidDir) step("إضافة منصة أندرويد", workDir = dir, run = "cordova platform add android")
                step("بناء APK", workDir = dir, run = "cordova build android")
                uploadStep(join(dir, "platforms/android/app/build/outputs/apk/**/*.apk"))
            }

            ProjectType.KIVY -> {
                step("بناء APK (Buildozer)", "ArtemSBulgakov/buildozer-action@v1",
                    listOf("workdir" to dir.ifEmpty { "." }, "buildozer_version" to "stable"))
                uploadStep(join(dir, "bin/*.apk"))
            }

            ProjectType.WEB -> {
                javaStep(); sdkStep(); nodeStep()
                val safe = appName.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "WebApp" }
                val script = buildString {
                    appendLine("set -e")
                    appendLine("SRC=\"\$PWD\"")
                    if (info.hasBuildScript) {
                        appendLine(installCmd(info.pm))
                        appendLine("npm run build")
                        appendLine("OUT=\"\"")
                        appendLine("for d in dist build out public www; do")
                        appendLine("  if [ -f \"\$d/index.html\" ]; then OUT=\"\$d\"; break; fi")
                        appendLine("done")
                        appendLine("[ -n \"\$OUT\" ] || { echo \"لم أجد index.html في مجلد الإخراج بعد البناء\"; exit 1; }")
                        appendLine("WEB=\"\$SRC/\$OUT\"")
                    } else {
                        appendLine("WEB=\"\$SRC\"")
                    }
                    appendLine("APP=\"\$RUNNER_TEMP/webapp\"")
                    appendLine("mkdir -p \"\$APP/www\"")
                    appendLine("cp -r \"\$WEB\"/. \"\$APP/www/\"")
                    appendLine("rm -rf \"\$APP/www/.git\" \"\$APP/www/node_modules\"")
                    appendLine("cd \"\$APP\"")
                    appendLine("npm init -y")
                    appendLine("npm install @capacitor/core @capacitor/cli @capacitor/android")
                    appendLine("npx cap init '$safe' com.apkbuilder.webapp --web-dir www")
                    appendLine("npx cap add android")
                    appendLine("npx cap sync android")
                    appendLine("cd android")
                    appendLine("chmod +x gradlew")
                    append("./gradlew assembleDebug --stacktrace")
                }
                step("تغليف الموقع وبناء APK", workDir = dir, run = script)
                uploadStep("\${{ runner.temp }}/webapp/android/app/build/outputs/apk/**/*.apk")
            }
        }
        return sb.toString()
    }
}
