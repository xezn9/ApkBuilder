package com.chatapp.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** بعد إعادة تشغيل الجهاز أو تحديث التطبيق: نرجع نشغل خدمة الاستماع إذا فيه حساب مفعّل */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Repo.auth.currentUser?.isEmailVerified == true) {
            MessageListenerService.start(context)
        }
    }
}
