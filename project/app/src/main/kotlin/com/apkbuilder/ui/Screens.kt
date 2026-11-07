package com.apkbuilder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkbuilder.BuildViewModel
import com.apkbuilder.engine.LogLevel
import com.apkbuilder.engine.Phase
import com.apkbuilder.settings.AppSettings
import com.apkbuilder.settings.SaveMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

private enum class LogView { ERRORS, FULL }

private val Ok = Color(0xFF3DDC97)
private val Bad = Color(0xFFFF5C6C)
private val Warn = Color(0xFFFFC857)

private val TabIcons = listOf(Icons.Filled.Build, Icons.Filled.BugReport, Icons.Filled.Settings)

@Composable
fun AppRoot(
    vm: BuildViewModel,
    pickProject: () -> Unit,
    pickFolder: () -> Unit,
    createDoc: (String) -> Unit
) {
    val st = vm.settings
    var tab by remember { mutableIntStateOf(0) }

    // عند الفشل ننتقل تلقائياً لصفحة السجل
    LaunchedEffect(vm.phase) { if (vm.phase == Phase.FAILED) tab = 1 }

    // وضع «اسأل كل مرة»: نفتح نافذة اختيار المكان لكل APK بالتتابع
    LaunchedEffect(vm.askQueue.firstOrNull()) {
        vm.askQueue.firstOrNull()?.let { createDoc(vm.nameFor(it)) }
    }

    val accent = st.accent
    val barColor = st.barColor
    val contentColor = if (barColor.luminanceApprox() > 0.6f) Color(0xFF1A1A1A) else Color.White
    val labels = listOf("البناء", if (vm.errors.isNotEmpty()) "السجل (${vm.errors.size})" else "السجل", "الإعدادات")

    CompositionLocalProvider(LocalAccent provides accent) {
        GlassScene(accent) { backdrop ->
            // طبقة المحتوى (البطاقات والنصوص) تُسجَّل ليراها الشريط السفلي من خلف الزجاج
            val contentBackdrop = rememberLayerBackdrop()
            val barBackdrop = rememberCombinedBackdrop(backdrop, contentBackdrop)
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
                    when (tab) {
                        0 -> BuildScreen(vm, backdrop, pickProject)
                        1 -> LogScreen(vm, backdrop)
                        else -> SettingsScreen(vm, st, backdrop, pickFolder)
                    }
                }

                LiquidBottomTabs(
                    selectedTabIndex = { tab },
                    onTabSelected = { tab = it },
                    backdrop = barBackdrop,
                    tabsCount = 3,
                    containerColor = barColor.copy(alpha = (1f - st.transparency).coerceIn(0.02f, 1f)),
                    accentColor = accent,
                    blurRadius = st.blur.dp,
                    refraction = st.refraction.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 36.dp, vertical = 14.dp)
                        .fillMaxWidth()
                ) {
                    repeat(3) { i ->
                        LiquidBottomTab({ tab = i }) {
                            Icon(TabIcons[i], null, tint = contentColor, modifier = Modifier.size(28.dp))
                            Text(labels[i], color = contentColor, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

// =============================================================== صفحة البناء

@Composable
private fun BuildScreen(
    vm: BuildViewModel,
    backdrop: Backdrop,
    pickProject: () -> Unit
) {
    val accent = LocalAccent.current
    val st = vm.settings
    val canStart = vm.isConnected && st.repo.isNotBlank() && !vm.detecting

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("ApkBuilder", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("البناء يتم على GitHub Actions ويرجع لك الـ APK", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }

        GitHubCard(vm, backdrop)

        if (vm.isConnected) {
            GlassCard(backdrop, Modifier.fillMaxWidth()) {
                Text("المشروع", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "اختر ملف zip للمشروع، أو اتركه فارغاً ليبني التطبيق المشروع الموجود في المستودع. " +
                        "يتعرف على نوع المشروع ويجهّز ملف البناء بنفسه.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                PickRow("ملف المشروع (zip)", vm.projectName, Icons.Filled.FolderZip, backdrop, !vm.isRunning, pickProject, vm::clearProject)
                when {
                    vm.detecting -> Text("جارٍ التعرف على نوع المشروع…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    vm.detectError != null -> Text(vm.detectError!!, color = Bad, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    vm.detectedLabel != null -> Text("✓ تم التعرف: ${vm.detectedLabel}", color = Ok, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        if (vm.isRunning) {
            GlassButton(backdrop, "إيقاف البناء", Icons.Filled.Stop, vm::cancel, Modifier.fillMaxWidth(), accent = Bad)
        } else {
            GlassButton(
                backdrop, "ابدأ البناء", Icons.Filled.PlayArrow, vm::start, Modifier.fillMaxWidth(),
                accent = accent, enabled = canStart
            )
        }

        StatusCard(vm, backdrop)

        if (vm.savedApks.isNotEmpty()) {
            GlassCard(backdrop, Modifier.fillMaxWidth(), tint = Ok.copy(alpha = 0.16f)) {
                Text("ملفات APK الناتجة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                vm.savedApks.forEach {
                    Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun PickRow(
    title: String, picked: String?, icon: ImageVector, backdrop: Backdrop,
    enabled: Boolean, onPick: () -> Unit, onClear: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            GlassButton(
                backdrop, title, icon, onPick, Modifier.fillMaxWidth(), enabled = enabled, radius = 20.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            )
            Text(
                picked?.let { "✓ $it" } ?: "لم يُختر",
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (picked != null && enabled) {
            Icon(
                Icons.Filled.Close, "إزالة", tint = Color.White,
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClear).padding(6.dp)
            )
        }
    }
}

@Composable
private fun GitHubCard(vm: BuildViewModel, backdrop: Backdrop) {
    val st = vm.settings
    var tokenInput by remember { mutableStateOf("") }
    var showRepos by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current

    GlassCard(backdrop, Modifier.fillMaxWidth()) {
        Text("حساب GitHub", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

        if (!vm.isConnected) {
            Text(
                "التوكن هو «مفتاح» يسمح للتطبيق بالعمل على حسابك في GitHub. طريقة الحصول عليه:",
                color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "١) اضغط «إنشاء التوكن» بالأسفل — تُفتح صفحة GitHub وصلاحيتا repo و workflow محددتان مسبقاً.\n" +
                    "٢) سجّل دخولك ثم انزل واضغط الزر الأخضر Generate token.\n" +
                    "٣) انسخ التوكن (يظهر مرة واحدة فقط) وارجع هنا واضغط أيقونة اللصق داخل الحقل.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.5.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
            GlassButton(
                backdrop, "إنشاء التوكن من GitHub", Icons.AutoMirrored.Filled.OpenInNew,
                { uriHandler.openUri("https://github.com/settings/tokens/new?scopes=repo,workflow&description=ApkBuilder") },
                Modifier.fillMaxWidth(), radius = 24.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            )
            Box(Modifier.height(12.dp))
            GlassTextField(
                value = tokenInput, onValueChange = { tokenInput = it.trim() },
                label = "الصق التوكن هنا (ghp_… أو github_pat_…)",
                password = true,
                modifier = Modifier.fillMaxWidth(),
                trailing = {
                    Icon(
                        Icons.Filled.ContentPaste, "لصق", tint = Color.White,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()?.let { tokenInput = it }
                        }.padding(9.dp)
                    )
                }
            )
            vm.githubError?.let {
                Text(it, color = Bad, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Box(Modifier.height(10.dp))
            GlassButton(
                backdrop, if (vm.githubBusy) "جارٍ الاتصال…" else "ربط الحساب", Icons.Filled.Link,
                { vm.connect(tokenInput) }, Modifier.fillMaxWidth(),
                accent = LocalAccent.current, enabled = tokenInput.isNotBlank() && !vm.githubBusy, radius = 20.dp
            )
        } else {
            Text("✓ متصل: @${vm.login}", color = Ok, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.height(10.dp))
            SelectRow("المستودع", st.repo.ifBlank { "اختر مستودعاً" }, !vm.isRunning) { showRepos = true }
            vm.githubError?.let {
                Text(it, color = Bad, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showRepos) PickDialog("اختر المستودع", vm.repos, { it }, { vm.selectRepo(it); showRepos = false }) { showRepos = false }
}

@Composable
private fun SelectRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Text(
            value, color = Color.White.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
    }
}

@Composable
private fun <T> PickDialog(title: String, items: List<T>, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.height(320.dp)) {
                items(items) { item ->
                    Text(
                        label(item), fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun StatusCard(vm: BuildViewModel, backdrop: Backdrop) {
    val accent = LocalAccent.current
    if (vm.isRunning) {
        // أثناء العمل: بطاقة بدون backdrop حتى لا تُعاد معالجة الزجاج مع كل إطار من الأنيميشن
        PlainCard(Modifier.fillMaxWidth(), radius = 28.dp, tint = accent.copy(alpha = 0.16f)) {
            BuildLoader(vm.phase, vm.statusText, vm.startedAt)
            if (vm.errors.isNotEmpty()) {
                Text(
                    "${vm.errors.size} خطأ حتى الآن — تبويب «السجل»",
                    color = Bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        return
    }
    val (tint, title) = when (vm.phase) {
        Phase.IDLE -> Color.White.copy(alpha = 0.08f) to "الحالة"
        Phase.SUCCESS -> Ok.copy(alpha = 0.22f) to "نجح البناء"
        Phase.FAILED -> Bad.copy(alpha = 0.22f) to "فشل البناء"
        Phase.CANCELLED -> Warn.copy(alpha = 0.18f) to "أُلغي"
        else -> accent.copy(alpha = 0.18f) to "الحالة"
    }
    GlassCard(backdrop, Modifier.fillMaxWidth(), tint = tint) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(vm.statusText, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        if (vm.errors.isNotEmpty()) {
            Text(
                "${vm.errors.size} خطأ — افتح تبويب «السجل» للتفاصيل",
                color = Bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

// ============================================================== صفحة السجل

@Composable
private fun LogScreen(vm: BuildViewModel, backdrop: Backdrop) {
    val accent = LocalAccent.current
    var view by remember { mutableStateOf(LogView.ERRORS) }
    val ctx = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
    ) {
        Text("السجل", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)

        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                backdrop, "الأخطاء (${vm.errors.size})", null, { view = LogView.ERRORS },
                Modifier.weight(1f), accent = if (view == LogView.ERRORS) Bad else null, radius = 22.dp,
                contentPadding = PaddingValues(12.dp)
            )
            GlassButton(
                backdrop, "السجل الكامل", null, { view = LogView.FULL },
                Modifier.weight(1f), accent = if (view == LogView.FULL) accent else null, radius = 22.dp,
                contentPadding = PaddingValues(12.dp)
            )
            GlassButton(
                backdrop, "نسخ", Icons.Filled.ContentCopy,
                { copy(ctx, if (view == LogView.ERRORS) vm.errorsAsText() else vm.logAsText()) },
                radius = 22.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            )
        }

        Box(Modifier.weight(1f)) {
            when (view) {
                LogView.ERRORS -> ErrorsList(vm, backdrop)
                LogView.FULL -> FullLog(vm)
            }
        }
        Box(Modifier.height(110.dp))
    }
}

@Composable
private fun ErrorsList(vm: BuildViewModel, backdrop: Backdrop) {
    val accent = LocalAccent.current
    if (vm.errors.isEmpty()) {
        GlassCard(backdrop, Modifier.fillMaxWidth()) {
            Text(
                if (vm.phase == Phase.SUCCESS) "لا توجد أخطاء ✓" else "لا توجد أخطاء مسجلة حتى الآن.",
                color = Color.White, fontSize = 15.sp
            )
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(vm.errors) { e ->
            PlainCard(Modifier.fillMaxWidth(), radius = 24.dp, tint = Bad.copy(alpha = 0.16f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(backdrop, e.source, accent.copy(alpha = 0.35f))
                    e.line?.let { GlassChip(backdrop, "سطر $it") }
                    e.column?.let { GlassChip(backdrop, "عمود $it") }
                }
                Text(
                    e.message, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "📄 ${e.location}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FullLog(vm: BuildViewModel) {
    val state = rememberLazyListState()
    LaunchedEffect(vm.logs.size) { if (vm.logs.isNotEmpty()) state.scrollToItem(vm.logs.size - 1) }
    PlainCard(Modifier.fillMaxSize(), radius = 24.dp, tint = Color.Black.copy(alpha = 0.40f)) {
        if (vm.logs.isEmpty()) {
            Text("لا يوجد سجل بعد.", color = Color.White.copy(alpha = 0.7f))
        } else {
            LazyColumn(state = state) {
                items(vm.logs) { l ->
                    val c = when (l.level) {
                        LogLevel.ERROR -> Bad
                        LogLevel.WARN -> Warn
                        LogLevel.OK -> Ok
                        LogLevel.CMD -> Color(0xFF8FB0FF)
                        LogLevel.INFO -> Color.White.copy(alpha = 0.85f)
                    }
                    Text(l.text, color = c, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                }
            }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ApkBuilder", text))
}

// ============================================================ صفحة الإعدادات

@Composable
private fun SettingsScreen(vm: BuildViewModel, st: AppSettings, backdrop: Backdrop, pickFolder: () -> Unit) {
    val accent = LocalAccent.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("الإعدادات", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)

        // ---- حساب GitHub
        if (vm.isConnected) {
            GlassCard(backdrop, Modifier.fillMaxWidth()) {
                Text("حساب GitHub", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("متصل: @${vm.login}", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                GlassButton(
                    backdrop, "فصل الحساب", Icons.Filled.LinkOff, vm::disconnect, Modifier.fillMaxWidth(),
                    accent = Bad, enabled = !vm.isRunning, radius = 20.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        // ---- لون التطبيق
        GlassCard(backdrop, Modifier.fillMaxWidth()) {
            Text("لون التطبيق", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(AccentColors[st.accentIndex].name, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            ColorGrid(AccentColors, st.accentIndex) { st.setAccent(it) }
        }

        // ---- Liquid Glass
        GlassCard(backdrop, Modifier.fillMaxWidth()) {
            Text("تخصيص Liquid Glass (الشريط السفلي)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Box(Modifier.height(8.dp))
            SliderRow("شدة الضبابية", st.blur, 0f..24f, "%.0f".format(st.blur)) { st.updateBlur(it) }
            SliderRow("شدة الانكسار", st.refraction, 0f..48f, "%.0f".format(st.refraction)) { st.updateRefraction(it) }
            SliderRow("شدة الشفافية", st.transparency, 0.05f..1f, "${(st.transparency * 100).toInt()}%") { st.updateTransparency(it) }
            Text("لون الشريط", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text(BarColors[st.barColorIndex].name, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            ColorGrid(BarColors, st.barColorIndex) { st.setBarColor(it) }
            Box(Modifier.height(12.dp))
            GlassButton(
                backdrop, "إعادة الضبط", Icons.Filled.RestartAlt, st::resetGlass, Modifier.fillMaxWidth(), radius = 20.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // ---- مكان الحفظ
        GlassCard(backdrop, Modifier.fillMaxWidth()) {
            Text("مكان حفظ التطبيق", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Box(Modifier.height(4.dp))
            ChoiceRow("افتراضي (Download/ApkBuilder)", st.saveMode == SaveMode.DEFAULT, accent) { st.updateSaveMode(SaveMode.DEFAULT) }
            ChoiceRow("مجلد مخصص", st.saveMode == SaveMode.CUSTOM, accent) {
                st.updateSaveMode(SaveMode.CUSTOM)
                if (st.folderUri.isBlank()) pickFolder()
            }
            if (st.saveMode == SaveMode.CUSTOM) {
                Text(
                    vm.folderLabel(st.folderUri), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
                GlassButton(
                    backdrop, "اختيار المجلد", Icons.Filled.Folder, pickFolder, Modifier.fillMaxWidth(), radius = 20.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            ChoiceRow("اسأل في كل مرة", st.saveMode == SaveMode.ASK, accent) { st.updateSaveMode(SaveMode.ASK) }
        }

        // ---- اسم التطبيق
        GlassCard(backdrop, Modifier.fillMaxWidth()) {
            Text("اسم التطبيق (اسم ملف الـ APK)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Box(Modifier.height(4.dp))
            ChoiceRow("افتراضي (الاسم الناتج من البناء)", !st.useCustomName, accent) { st.updateUseCustomName(false) }
            ChoiceRow("مخصص", st.useCustomName, accent) { st.updateUseCustomName(true) }
            if (st.useCustomName) {
                GlassTextField(
                    value = st.customName, onValueChange = st::updateCustomName,
                    label = "الاسم بدون .apk",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorGrid(colors: List<NamedColor>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.indices.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { i ->
                    val c = colors[i].color
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (i == selected) 3.dp else 1.dp, if (i == selected) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (i == selected) {
                            Icon(Icons.Filled.Check, null, tint = if (c.luminanceApprox() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    val accent = LocalAccent.current
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(display, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = Color.White.copy(alpha = 0.6f))
        )
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

/** حقل نص بحواف دائرية وخلفية زجاجية خفيفة. */
@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val accent = LocalAccent.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(26.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = accent, unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
            focusedContainerColor = Color.White.copy(alpha = 0.10f), unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
            cursorColor = accent,
            focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = 0.65f)
        ),
        modifier = modifier
    )
}
