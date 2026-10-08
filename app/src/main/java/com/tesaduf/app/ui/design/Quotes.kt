package com.tesaduf.app.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tesaduf.app.R
import com.tesaduf.app.ui.theme.TesadufColors
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * TESADÜF's rotating lines (home + matchmaking): random start, then a slow, soft
 * crossfade with a small vertical drift. Plain text, no decoration.
 */
@Composable
fun TesadufQuoteTicker(
    modifier: Modifier = Modifier,
    intervalMs: Long = 5_500L,
    style: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontStyle = FontStyle.Italic,
        color = TesadufColors.TextSecondary,
    ),
) {
    val quotes = stringArrayResource(R.array.home_quotes)
    var index by remember { mutableIntStateOf(Random.nextInt(quotes.size)) }
    LaunchedEffect(quotes.size, intervalMs) {
        while (true) {
            delay(intervalMs)
            index = (index + 1) % quotes.size
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (fadeIn(tween(700, easing = FastOutSlowInEasing)) + slideInVertically(tween(700, easing = FastOutSlowInEasing)) { it / 4 }) togetherWith
                    (fadeOut(tween(450)) + slideOutVertically(tween(450)) { -it / 4 })
            },
            label = "quote",
        ) { i ->
            Text(quotes[i], style = style, textAlign = TextAlign.Center)
        }
    }
}
