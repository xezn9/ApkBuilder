package com.chatapp.messenger.ui

import androidx.compose.material.icons.filled.Star
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ChatItem
import com.chatapp.messenger.Profile
import com.chatapp.messenger.Repo
import com.chatapp.messenger.UserItem

/**
 * تبويب Home:
 * - بدون بحث: قائمة المحادثات (صورة + اسم الشخص + آخر رسالة)
 * - بالبحث (اسم أو يوزر): نتائج المستخدمين، ضغطة تفتح بروفايله وفيه زر مراسلة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    onOpenChat: (UserItem) -> Unit, onOpenProfile: (UserItem) -> Unit, onRequestLogout: () -> Unit,
    onRequestDeleteChat: (uid: String, name: String, lastAt: Long) -> Unit = { _, _, _ -> }
) {
    val myUid = Repo.auth.currentUser?.uid ?: ""
    val users by remember { Repo.usersFlow() }.collectAsState(initial = emptyList())
    val chats by remember(myUid) { Repo.chatsFlow(myUid) }.collectAsState(initial = emptyList())
    val pinned by remember(myUid) { Repo.pinnedFlow(myUid) }.collectAsState(initial = emptySet())
    val cleared by remember(myUid) { Repo.clearedFlow(myUid) }.collectAsState(initial = emptyMap())
    // معاينة بديلة إذا حذفت (من عندي) آخر رسالة بمحادثة
    val previewOverrides by remember(myUid) { Repo.previewOverridesFlow(myUid) }.collectAsState(initial = emptyMap())
    var query by remember { mutableStateOf("") }

    // المثبتين (الأشخاص المميزين) أولاً، حتى لو ما عندهم رسائل بعد
    val shownChats = remember(chats, pinned, cleared) {
        // المحادثات المحذوفة من عندي تنخفي (لين توصل رسالة جديدة)
        val visible = chats.filter { it.lastAt > (cleared[it.uid] ?: 0L) }
        val byUid = visible.associateBy { it.uid }
        val top = pinned.map { byUid[it] ?: ChatItem(it, "", 0L) }.sortedByDescending { it.lastAt }
        top + visible.filter { it.uid !in pinned }
    }

    val q = query.trim().removePrefix("@").lowercase()
    val results = if (q.isEmpty()) emptyList() else users.filter {
        it.uid != myUid && (it.name.lowercase().contains(q) || it.username.lowercase().contains(q))
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("المحادثات") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = { TextButton(onClick = onRequestLogout) { Text("تسجيل الخروج") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query) { query = it }

            when {
                q.isNotEmpty() -> {
                    if (results.isEmpty()) {
                        CenterText("ما لقيت أحد بهذا الاسم أو اليوزر")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 130.dp)) {
                            items(results, key = { it.uid }) { u ->
                                UserRow(
                                    uid = u.uid,
                                    fallbackName = u.name,
                                    secondary = { p -> "@" + p.username.ifBlank { u.username } },
                                    onClick = { onOpenProfile(u) },
                                    onAvatarClick = { onOpenProfile(u) }
                                )
                            }
                        }
                    }
                }
                shownChats.isEmpty() -> CenterText("ما عندك محادثات بعد.\nابحث عن شخص بالاسم أو اليوزر حتى تبدأ محادثة.")
                else -> {
                    LazyColumn(
                        // مسافة بالأسفل حتى ما يختفي آخر عنصر خلف الشريط الزجاجي
                        contentPadding = PaddingValues(bottom = 130.dp)
                    ) {
                        items(shownChats, key = { it.uid }) { c ->
                            val u = users.firstOrNull { it.uid == c.uid }
                            val toItem = { p: Profile ->
                                UserItem(
                                    c.uid,
                                    p.name.ifBlank { u?.name ?: "" },
                                    u?.email ?: "",
                                    p.username.ifBlank { u?.username ?: "" }
                                )
                            }
                            UserRow(
                                uid = c.uid,
                                fallbackName = u?.name ?: "",
                                secondary = {
                                    val o = previewOverrides[c.uid]
                                    // تنطبق فقط إذا آخر نص ما زال نفس الي حذفته ولا وصلت رسالة جديدة بعده
                                    if (o != null && c.lastText == o.forText && c.lastAt <= o.at) o.text else c.lastText
                                },
                                pinned = c.uid in pinned,
                                unread = c.unread,
                                showPresence = true,
                                onLongClick = { p ->
                                    onRequestDeleteChat(
                                        c.uid,
                                        p.name.ifBlank { u?.name ?: "" }.ifBlank { "مستخدم" },
                                        c.lastAt
                                    )
                                },
                                onClick = { p -> onOpenChat(toItem(p)) },
                                onAvatarClick = { p -> onOpenProfile(toItem(p)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

/** مربع بحث بستايل iOS */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape).background(fg.copy(alpha = 0.08f))
            .border(0.8.dp, fg.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = fg.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("ابحث بالاسم أو اليوزر", color = fg.copy(alpha = 0.5f), fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 16.sp),
                cursorBrush = SolidColor(Color(0xFF1AA7FF)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, null, tint = fg.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp).clickable { onChange("") }
            )
        }
    }
}

/** صف مستخدم: صورة الملف الشخصي + الاسم + سطر ثانوي (آخر رسالة أو @اليوزر) */
@Composable
private fun UserRow(
    uid: String,
    fallbackName: String,
    secondary: (Profile) -> String,
    onClick: (Profile) -> Unit,
    onAvatarClick: (Profile) -> Unit,
    pinned: Boolean = false,
    unread: Int = 0,
    showPresence: Boolean = false,
    onLongClick: ((Profile) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val prof by remember(uid) { Repo.profileFlow(uid) }.collectAsState(initial = Profile())
    val avatar = rememberB64Image(prof.avatar)
    val shown = prof.name.ifBlank { fallbackName }.ifBlank { "مستخدم" }
    // نقطة "متصل" الخضراء (فقط لصفوف المحادثات، حتى ما نفتح مستمع لكل نتيجة بحث)
    val online = showPresence && rememberPresenceStatus(uid).online

    val rowMod = if (onLongClick != null) {
        // ضغطة = فتح المحادثة، ضغطة مطولة = حذف المحادثة
        Modifier.fillMaxWidth().pointerInput(uid, prof) {
            detectTapGestures(
                onTap = { onClick(prof) },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(prof)
                }
            )
        }
    } else Modifier.fillMaxWidth().clickable { onClick(prof) }
    Row(
        rowMod.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp)) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2B2F36)).clickable { onAvatarClick(prof) },
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.Person, null, tint = Color(0xFF8A8F98), modifier = Modifier.size(28.dp))
                }
            }
            OnlineDot(online)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shown, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(15.dp))
                }
            }
            Text(
                secondary(prof), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (unread > 0) 0.9f else 0.6f)
            )
        }
        // شارة عدد الرسائل الجديدة (مثل تيك توك)
        AnimatedVisibility(
            visible = unread > 0,
            enter = scaleIn(spring(0.45f, 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                Modifier.padding(start = 8.dp).defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                    .clip(CircleShape).background(Color(0xFFFF3B5C)).padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (unread > 99) "99+" else unread.toString(),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
