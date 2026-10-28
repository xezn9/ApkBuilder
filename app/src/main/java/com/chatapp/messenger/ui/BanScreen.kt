package com.chatapp.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.Ban
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.theme.appBackground

/** شاشة الحساب المحظور (بسبب بلاغ ثبت صحته). تختفي تلقائياً بعد انتهاء المدة */
@Composable
fun BanScreen(ban: Ban, now: Long) {
    val left = (ban.until - now).coerceAtLeast(0L)
    val mins = (left / 60_000L).toInt()
    val d = mins / (60 * 24)
    val h = (mins / 60) % 24
    val m = mins % 60
    val remaining = buildList {
        if (d > 0) add("$d يوم")
        if (h > 0) add("$h ساعة")
        if (d == 0 && m > 0) add("$m دقيقة")
        if (isEmpty()) add("أقل من دقيقة")
    }.joinToString(" و ")

    Column(
        Modifier.fillMaxSize().background(appBackground()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(AppIcons.Block, null, tint = Color(0xFFE5484D), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("حسابك محظور مؤقتاً", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "بسبب مخالفة شروط الاستخدام (إساءة أو عنف أو تهديد).\nمدة الحظر: ${ban.days} ${if (ban.days == 1) "يوم" else "أيام"}",
            textAlign = TextAlign.Center, fontSize = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        Text("المتبقي: $remaining", textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("تكرار المخالفة يزيد مدة الحظر.", textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { Repo.signOut() }) { Text("تسجيل خروج") }
    }
}
