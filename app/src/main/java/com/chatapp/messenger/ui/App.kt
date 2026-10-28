package com.chatapp.messenger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chatapp.messenger.Repo
import com.google.firebase.auth.FirebaseAuth

/** يقرر أي شاشة تظهر: تسجيل / تفعيل / الرئيسية */
@Composable
fun App() {
    var user by remember { mutableStateOf(Repo.auth.currentUser) }
    var verified by remember { mutableStateOf(user?.isEmailVerified == true) }

    DisposableEffect(Unit) {
        val l = FirebaseAuth.AuthStateListener {
            user = it.currentUser
            verified = it.currentUser?.isEmailVerified == true
        }
        Repo.auth.addAuthStateListener(l)
        onDispose { Repo.auth.removeAuthStateListener(l) }
    }

    Box(Modifier.fillMaxSize()) {
        // انيميشن تسجيل الدخول/الخروج: الشاشة القديمة تتلاشى وتصغر، والجديدة تظهر وتكبر بنعومة
        val stage = when {
            user == null -> 0
            !verified -> 1
            else -> 2
        }
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                if (targetState > initialState) {
                    // دخول: تكبير نابضي للشاشة الجديدة
                    (fadeIn(tween(380, delayMillis = 90)) + scaleIn(spring(0.82f, 300f), initialScale = 0.9f)) togetherWith
                        (fadeOut(tween(220)) + scaleOut(tween(260), targetScale = 1.08f))
                } else {
                    // خروج: الشاشة الجديدة تطلع من الخلف
                    (fadeIn(tween(380, delayMillis = 90)) + scaleIn(spring(0.82f, 300f), initialScale = 1.08f)) togetherWith
                        (fadeOut(tween(220)) + scaleOut(tween(260), targetScale = 0.9f))
                }
            },
            label = "appStage"
        ) { st ->
            when (st) {
                0 -> AuthScreen()
                1 -> VerifyScreen(onVerified = { verified = true })
                else -> MainScreen()
            }
        }
        // شاشة التحديث: تظهر فوق كل شي إذا فيه نسخة أحدث
        UpdatePrompt()
    }
}
