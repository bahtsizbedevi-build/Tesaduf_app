package com.tesaduf.app.ui.onboarding

import com.tesaduf.app.ui.design.TIcons
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesaduf.app.R
import com.tesaduf.app.ui.design.ButtonTone
import com.tesaduf.app.ui.design.HeartsIllustration
import com.tesaduf.app.ui.design.HourglassIllustration
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufButton
import com.tesaduf.app.ui.design.TesadufGlow
import com.tesaduf.app.ui.design.TesadufIconBadge
import com.tesaduf.app.ui.design.TesadufLogo
import com.tesaduf.app.ui.design.TesadufTextButton
import com.tesaduf.app.ui.design.TesadufWordmark
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors
import kotlinx.coroutines.launch

private const val PAGES = 4

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1

    TesadufBackground(particles = true) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    TesadufLogo(size = 44.dp, glow = 0.6f)
                    TesadufWordmark(fontSize = 15.sp, letterSpacing = 5.sp)
                }
                TesadufTextButton(stringResource(R.string.onb_skip), onFinished, Modifier.align(Alignment.TopEnd))
            }

            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (page) {
                        0 -> IntroPage()
                        1 -> IllustratedPage(R.string.onb2_title, R.string.onb2_body) {
                            TesadufGlow(Modifier.size(150.dp)) { TesadufIconBadge(TIcons.Incognito, size = 96.dp) }
                        }
                        2 -> IllustratedPage(R.string.onb3_title, R.string.onb3_body) { HourglassIllustration(Modifier.size(150.dp)) }
                        else -> IllustratedPage(R.string.onb4_title, R.string.onb4_body) { HeartsIllustration(Modifier.size(170.dp)) }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PagerDots(pager.currentPage, PAGES)
                Spacer(Modifier.height(20.dp))
                TesadufButton(
                    text = stringResource(if (last) R.string.onb_start else R.string.onb_next),
                    onClick = { if (last) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    tone = if (last) ButtonTone.Signature else ButtonTone.Cool,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                )
            }
        }
    }
}

@Composable
private fun IntroPage() {
    Text(
        stringResource(R.string.onb1_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(28.dp))
    val features: List<Pair<ImageVector, Int>> = listOf(
        TIcons.Shield to R.string.onb1_f1,
        TIcons.Timer to R.string.onb1_f2,
        TIcons.Users to R.string.onb1_f3,
        TIcons.Heart to R.string.onb1_f4,
    )
    features.forEachIndexed { i, (icon, label) ->
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TesadufIconBadge(icon, tint = if (i == 3) TesadufColors.Pink else TesadufColors.Cyan)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun IllustratedPage(title: Int, body: Int, illustration: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        illustration()
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge.copy(color = TesadufColors.TextSecondary), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PagerDots(current: Int, count: Int) {
    val description = stringResource(R.string.onb_page, current + 1, count)
    Row(
        Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 7.dp, label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 7.dp)
                    .clip(Shapes.pill)
                    .background(if (i == current) TesadufColors.Cyan else TesadufColors.Stroke),
            )
        }
    }
}
