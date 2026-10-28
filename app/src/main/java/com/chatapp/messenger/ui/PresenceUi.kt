package com.chatapp.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chatapp.messenger.Presence
import com.chatapp.messenger.Repo
import kotlinx.coroutines.delay
import java.util.Calendar

/** نبضة الحضور كل دقيقتين، وإذا انقطعت النبضات أكثر من 5 دقائق (كراش/ما في نت) نعتبره غير متصل */
const val PRESENCE_HEARTBEAT_MS = 2L * 60 * 1000
const val PRESENCE_STALE_MS = 5L * 60 * 1000

val OnlineGreen = Color(0xFF34C759)

/** الحالة النهائية للعرض: متصل؟ وإذا لا، وقت آخر ظهور (0 = غير معروف) */
class PresenceStatus(val online: Boolean, val lastSeen: Long)

/** يتابع حالة شخص ويعيد الحساب كل 30 ثانية حتى يتحول لـ"غير متصل" إذا انقطعت نبضاته */
@Composable
fun rememberPresenceStatus(uid: String): PresenceStatus {
    val p by remember(uid) { Repo.presenceFlow(uid) }.collectAsState(initial = Presence())
    val now by tick30s()
    return PresenceStatus(
        online = p.online && p.lastSeen > 0L && now - p.lastSeen < PRESENCE_STALE_MS,
        lastSeen = p.lastSeen
    )
}

@Composable
private fun tick30s(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(30_000)
        value = System.currentTimeMillis()
    }
}

/** نقطة خضراء صغيرة بزاوية الصورة (تنحط داخل Box يحتوي الصورة، خارج الـ clip) */
@Composable
fun BoxScope.OnlineDot(visible: Boolean, size: Dp = 14.dp, ring: Color = Color(0xFF101418)) {
    if (!visible) return
    Box(
        Modifier.align(Alignment.BottomEnd)
            .size(size)
            .background(OnlineGreen, CircleShape)
            .border(2.dp, ring, CircleShape)
    )
}

private fun clock(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val h = c.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val m = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "$h:$m " + if (c.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
}

/** "آخر ظهور اليوم 1:30 ص" / "أمس 9:05 م" / "آخر ظهور 12/10/2026 3:40 م" */
fun lastSeenLabel(ms: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(now, c)) return "آخر ظهور اليوم " + clock(ms)
    val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(y, c)) return "آخر ظهور أمس " + clock(ms)
    return "آخر ظهور ${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.YEAR)} " + clock(ms)
}
