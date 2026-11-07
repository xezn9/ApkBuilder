package com.apkbuilder.engine

import android.content.Context

/** يحفظ حالة البناء الجاري على القرص (SharedPreferences) ليبقى قابلاً للاستئناف حتى لو أُغلق التطبيق. */
class SessionStore(context: Context) {
    private val p = context.getSharedPreferences("build_session", Context.MODE_PRIVATE)

    fun load(): BuildSession? = p.getString("session", null)?.let { BuildSession.fromJson(it) }

    fun save(s: BuildSession) { p.edit().putString("session", s.toJson()).commit() }

    fun clear() { p.edit().remove("session").commit() }
}
