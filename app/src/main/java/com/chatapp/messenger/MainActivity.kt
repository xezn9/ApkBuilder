package com.chatapp.messenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.chatapp.messenger.ui.App
import com.chatapp.messenger.ui.Nav
import com.chatapp.messenger.ui.theme.ChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            ChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
    }

    override fun onStop() {
        super.onStop()
        AppState.foreground = false
    }

    /** ضغطة على إشعار رسالة = افتح محادثة المرسل */
    private fun handleIntent(i: Intent?) {
        i?.getStringExtra(ChatMessagingService.EXTRA_OPEN_CHAT)?.let {
            Nav.openChatUid = it
            i.removeExtra(ChatMessagingService.EXTRA_OPEN_CHAT)
        }
    }
}
