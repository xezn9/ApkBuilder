package com.chatapp.messenger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chatapp.messenger.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** يتأكد إذا المستخدم ضغط رابط التفعيل بإيميله */
private suspend fun isNowVerified(): Boolean {
    val u = Repo.auth.currentUser ?: return false
    u.reload().await()
    if (u.isEmailVerified) {
        u.getIdToken(true).await() // يحدّث التوكن حتى تقبله قواعد Firestore
        return true
    }
    return false
}

@Composable
fun VerifyScreen(onVerified: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }

    // يفحص كل 3 ثواني تلقائياً
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            try {
                if (isNowVerified()) {
                    onVerified()
                    return@LaunchedEffect
                }
            } catch (_: Exception) {
            }
        }
    }

    GlassAuthScaffold { backdrop, dark ->
        val fg = glassFg(dark)

        Box(
            Modifier.size(88.dp).glassCircle(backdrop, dark),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Email, null, tint = GlassAccent, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("فعّل حسابك", color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))

        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().glassPanel(backdrop, dark).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "أرسلنا رابط التفعيل إلى:\n${Repo.auth.currentUser?.email ?: ""}\nاضغط الرابط بإيميلك (شوف مجلد Spam أيضاً) ثم ارجع هنا.",
                color = fg, fontSize = 15.sp, textAlign = TextAlign.Center
            )
            msg?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = GlassAccent, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))

            GlassButton(
                onClick = {
                    scope.launch {
                        try {
                            if (isNowVerified()) onVerified() else msg = "لسه ما انفعل. اضغط الرابط بالإيميل."
                        } catch (e: Exception) {
                            msg = e.localizedMessage
                        }
                    }
                },
                backdrop = backdrop, tint = GlassAccent, pressScale = 1.03f,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("تحقّقت، تابع", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(10.dp))
            GlassButton(
                onClick = {
                    scope.launch {
                        try {
                            Repo.auth.currentUser?.sendEmailVerification()?.await()
                            msg = "تم إرسال الرابط من جديد."
                        } catch (e: Exception) {
                            msg = e.localizedMessage
                        }
                    }
                },
                backdrop = backdrop, pressScale = 1.03f,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("إعادة إرسال الرابط", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(10.dp))
            GlassButton(
                onClick = { Repo.signOut() },
                backdrop = backdrop, pressScale = 1.03f,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("تسجيل خروج", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
