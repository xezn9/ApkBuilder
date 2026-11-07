package com.apkbuilder.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.apkbuilder.ui.AccentColors
import com.apkbuilder.ui.BarColors

object SaveMode {
    const val DEFAULT = 0   // Download/ApkBuilder
    const val CUSTOM = 1    // مجلد يختاره المستخدم
    const val ASK = 2       // سؤال في كل مرة
}

private const val DEFAULT_BLUR = 3f            // ضبابية خفيفة (dp)
private const val DEFAULT_REFRACTION = 26f     // انكسار واضح (dp)
private const val DEFAULT_TRANSPARENCY = 0.9f  // شبه شفاف

/** كل إعدادات التطبيق — حالة Compose + حفظ تلقائي في SharedPreferences. */
class AppSettings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // الإصدار 2: شريط زجاجي شفاف مع ضبابية خفيفة وانكسار — نطبّقه مرة واحدة على التثبيتات القديمة
        if (p.getInt("glassVersion", 0) < 2) {
            p.edit()
                .putFloat("blur", DEFAULT_BLUR).putFloat("refraction", DEFAULT_REFRACTION)
                .putFloat("transparency", DEFAULT_TRANSPARENCY).putInt("barColor", 0)
                .putInt("glassVersion", 2).apply()
        }
    }

    // المظهر
    var accentIndex by mutableIntStateOf(p.getInt("accent", 0).coerceIn(0, 19)); private set
    val accent: Color get() = AccentColors[accentIndex].color

    // Liquid Glass
    var blur by mutableFloatStateOf(p.getFloat("blur", DEFAULT_BLUR)); private set             // dp
    var refraction by mutableFloatStateOf(p.getFloat("refraction", DEFAULT_REFRACTION)); private set // dp
    var transparency by mutableFloatStateOf(p.getFloat("transparency", DEFAULT_TRANSPARENCY)); private set // 0..1
    var barColorIndex by mutableIntStateOf(p.getInt("barColor", 0).coerceIn(0, 19)); private set
    val barColor: Color get() = BarColors[barColorIndex].color

    // الحفظ
    var saveMode by mutableIntStateOf(p.getInt("saveMode", SaveMode.DEFAULT)); private set
    var folderUri by mutableStateOf(p.getString("folderUri", "") ?: ""); private set
    var useCustomName by mutableStateOf(p.getBoolean("useCustomName", false)); private set
    var customName by mutableStateOf(p.getString("customName", "") ?: ""); private set

    // GitHub
    var token by mutableStateOf(p.getString("token", "") ?: ""); private set
    var repo by mutableStateOf(p.getString("repo", "") ?: ""); private set
    var workflowId by mutableLongStateOf(p.getLong("workflowId", 0L)); private set
    var workflowName by mutableStateOf(p.getString("workflowName", "") ?: ""); private set

    fun setAccent(i: Int) { accentIndex = i; p.edit().putInt("accent", i).apply() }
    fun updateBlur(v: Float) { blur = v; p.edit().putFloat("blur", v).apply() }
    fun updateRefraction(v: Float) { refraction = v; p.edit().putFloat("refraction", v).apply() }
    fun updateTransparency(v: Float) { transparency = v; p.edit().putFloat("transparency", v).apply() }
    fun setBarColor(i: Int) { barColorIndex = i; p.edit().putInt("barColor", i).apply() }

    fun resetGlass() {
        updateBlur(DEFAULT_BLUR); updateRefraction(DEFAULT_REFRACTION); updateTransparency(DEFAULT_TRANSPARENCY); setBarColor(0)
    }

    fun updateSaveMode(m: Int) { saveMode = m; p.edit().putInt("saveMode", m).apply() }
    fun updateFolderUri(u: String) { folderUri = u; p.edit().putString("folderUri", u).apply() }
    fun updateUseCustomName(v: Boolean) { useCustomName = v; p.edit().putBoolean("useCustomName", v).apply() }
    fun updateCustomName(v: String) { customName = v; p.edit().putString("customName", v).apply() }

    fun updateToken(v: String) { token = v; p.edit().putString("token", v).apply() }
    fun updateRepo(v: String) { repo = v; p.edit().putString("repo", v).apply() }
    fun setWorkflow(id: Long, name: String) {
        workflowId = id; workflowName = name
        p.edit().putLong("workflowId", id).putString("workflowName", name).apply()
    }

    fun logout() {
        updateToken(""); updateRepo(""); setWorkflow(0L, "")
    }
}
