package com.tesaduf.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tesaduf.app.R
import com.tesaduf.app.ui.theme.TesadufColors

/** All badges, in showcase order. Earned ones glow; the rest stay as dim silhouettes. */
enum class Badge(val key: String, val title: Int, val hint: Int, val color: Color) {
    FirstTesaduf("first_tesaduf", R.string.badge_first_tesaduf, R.string.badge_first_tesaduf_hint, TesadufColors.Cyan),
    FirstKader("first_kader", R.string.badge_first_kader, R.string.badge_first_kader_hint, TesadufColors.Pink),
    TenTesaduf("ten_tesaduf", R.string.badge_ten, R.string.badge_ten_hint, TesadufColors.Blue),
    Streak3("streak_3", R.string.badge_streak, R.string.badge_streak_hint, Color(0xFFFF7A45)),
    NightOwl("night_owl", R.string.badge_night, R.string.badge_night_hint, TesadufColors.Purple),
    Chatty("chatty", R.string.badge_chatty, R.string.badge_chatty_hint, TesadufColors.Success),
    Kader5("kader_5", R.string.badge_kader5, R.string.badge_kader5_hint, Color(0xFFFBBF24)),
}

@Composable
private fun badgeIcon(badge: Badge): ImageVector = when (badge) {
    Badge.FirstTesaduf -> TIcons.Sparkles
    Badge.FirstKader -> TIcons.Heart
    Badge.TenTesaduf -> TIcons.Medal
    Badge.Streak3 -> TIcons.Flame
    Badge.NightOwl -> TIcons.Moon
    Badge.Chatty -> TIcons.Chats
    Badge.Kader5 -> TIcons.Trophy
}

/** Badge showcase grid: earned badges pop in; locked ones show how to earn them. */
@Composable
fun BadgeShowcase(earned: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        maxItemsInEachRow = 4,
    ) {
        Badge.entries.forEach { badge ->
            val has = badge.key in earned
            val title = stringResource(badge.title)
            val hint = stringResource(badge.hint)
            val pop = remember { Animatable(if (has) 0.6f else 1f) }
            LaunchedEffect(has) { if (has) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
            Column(
                Modifier
                    .width(72.dp)
                    .semantics(mergeDescendants = true) { contentDescription = if (has) title else "$title. $hint" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TesadufGlow(
                    Modifier.padding(2.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = if (has) 1f else 0.35f },
                    color = if (has) badge.color else TesadufColors.TextMuted,
                    secondary = if (has) badge.color else TesadufColors.TextMuted,
                    intensity = if (has) 0.9f else 0f,
                ) {
                    TesadufIconBadge(
                        if (has) badgeIcon(badge) else TIcons.LockKeyhole,
                        size = 52.dp,
                        tint = if (has) badge.color else TesadufColors.TextMuted,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (has) TesadufColors.TextPrimary else TesadufColors.TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
