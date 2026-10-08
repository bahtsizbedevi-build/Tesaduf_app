package com.tesaduf.app.ui.cinematic

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesaduf.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// Scene palette (night base never pure black).
private val Night0 = Color(0xFF02040B)
private val Night1 = Color(0xFF050816)
private val Night2 = Color(0xFF080617)
private val Cyan = Color(0xFF00E5FF)
private val Blue = Color(0xFF3B6BFF)
private val Purple = Color(0xFF8B5CF6)
private val Pink = Color(0xFFFF3EA5)
private val Gold = Color(0xFFFFC94A)

/**
 * The TESADÜF cinematic: one Canvas drives every layer from [CinematicDirector.params].
 * A frame loop calls [CinematicDirector.update]; drawing reads `director.frame`, so a
 * frame only invalidates the draw phase (no recomposition). Captions recompose only on
 * phase changes. The loop runs on the frame clock, so it pauses when not visible.
 */
@Composable
fun CinematicScene(
    director: CinematicDirector,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    showCaption: Boolean = true,
) {
    val context = LocalContext.current
    LaunchedEffect(director) {
        // Respect "remove animations": run the story quickly instead of hiding it.
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) director.speed = 4f
        var last = withFrameNanos { it }
        while (!director.finished) {
            withFrameNanos { now ->
                director.update((now - last) / 1_000_000_000f)
                last = now
            }
        }
    }
    val painter = remember { CinematicPainter() }

    BoxWithConstraints(modifier.fillMaxSize().background(Night0)) {
        val w = maxWidth
        val h = maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    director.frame // invalidate per frame
                    val p = director.params
                    alpha = p.fade
                    scaleX = p.zoom
                    scaleY = p.zoom
                }
                .drawBehind {
                    director.frame
                    painter.draw(this, director)
                },
        )

        // "Tesadüf" wordmark under the characters.
        val unit = min(w.value, h.value * 0.62f) * 0.14f
        Text(
            text = stringResource(R.string.cine_wordmark),
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = 46.sp,
                letterSpacing = 0.5.sp,
                color = Color.White,
                shadow = Shadow(Color(0xFF9D7BFF).copy(alpha = 0.55f), Offset.Zero, 22f),
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * CENTER_Y + (unit * 1.75f).dp)
                .graphicsLayer {
                    director.frame
                    val p = director.params.wordmark
                    alpha = p * director.params.fade
                    translationY = (1f - p) * 18.dp.toPx()
                },
        )

        if (showCaption) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = h * 0.74f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = director.phase.caption,
                    transitionSpec = {
                        (fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 2 }) togetherWith
                            (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 2 })
                    },
                    label = "caption",
                ) { res ->
                    if (res != null) {
                        Text(
                            stringResource(res),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White.copy(alpha = 0.92f),
                                shadow = Shadow(Cyan.copy(alpha = 0.55f), Offset.Zero, 18f),
                            ),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (showProgress) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = h * 0.80f)
                    .width(w * 0.56f)
                    .height(14.dp)
                    .graphicsLayer {
                        director.frame
                        alpha = director.params.fade
                    }
                    .drawBehind {
                        director.frame
                        drawProgress(director.params.progress, director.time)
                    },
            )
        }
    }
}

/** Vertical position of the characters' centre as a fraction of the height. */
private const val CENTER_Y = 0.40f

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.002f || radius <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceAtMost(1f)), Color.Transparent), center, radius), radius, center)
}

private fun DrawScope.drawProgress(progress: Float, time: Float) {
    val y = size.height / 2
    val th = 3.dp.toPx()
    drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(0f, y - th / 2), Size(size.width, th), CornerRadius(th))
    val end = size.width * progress.coerceIn(0f, 1f)
    if (end <= 0f) return
    val brush = Brush.horizontalGradient(listOf(Cyan, Purple, Pink), 0f, size.width)
    drawRoundRect(brush, Offset(0f, y - th / 2), Size(end, th), CornerRadius(th), alpha = 0.35f)
    drawRoundRect(brush, Offset(0f, y - th / 2), Size(end, th), CornerRadius(th))
    // Travelling light dot at the head.
    val pulse = 0.75f + 0.25f * sin(time * 6f)
    glow(Color.White, Offset(end, y), 10.dp.toPx(), 0.55f * pulse)
    drawCircle(Color.White, 2.5.dp.toPx(), Offset(end, y))
}

/** Holds reusable paths so drawing allocates as little as possible per frame. */
private class CinematicPainter {
    private val bodyPath = Path()
    private val starPath = Path()

    private class Particle(val angle: Float, val radius: Float, val speed: Float, val size: Float, val color: Color, val seed: Float, val tilt: Int)

    private val particles: List<Particle> = List(PARTICLES) { i ->
        val seed = ((i * 7919) % 1000) / 1000f
        Particle(
            angle = seed * 2f * PI.toFloat() * 3f,
            radius = 0.55f + ((i * 37) % 100) / 100f * 0.8f,
            speed = (0.04f + ((i * 13) % 10) / 10f * 0.12f) * if (i % 2 == 0) 1f else 0.8f,
            size = 1f + (i % 4) * 0.6f,
            color = when (i % 4) { 0 -> Cyan; 1 -> Purple; 2 -> Pink; else -> Color.White },
            seed = seed,
            tilt = i % 2,
        )
    }
    private val stars: List<Offset> = List(STARS) { i ->
        Offset(((i * 173) % 1000) / 1000f, ((i * 397 + 211) % 1000) / 1000f)
    }

    fun draw(scope: DrawScope, d: CinematicDirector) = with(scope) {
        val p = d.params
        val t = d.time
        val w = size.width
        val c = Offset(w / 2, size.height * CENTER_Y)
        val r = min(w, size.height * 0.62f) * 0.14f

        background(t, p)
        orbits(c, w, t, p)
        particles(c, w, t, p, behind = true)
        if (p.avatars > 0.01f) avatars(c, w, size.height, t, p)

        // Crossing: characters arrive from the sides with light trails, then flash.
        val crossP = if (d.phase == CinematicPhase.Crossing) (d.phaseTime / d.phase.durationSec).coerceIn(0f, 1f) else 1f
        val arrive = easeOutCubic((crossP / 0.62f).coerceIn(0f, 1f))
        val shift = (1f - arrive) * w * 0.42f * p.trails
        val sep = p.separation * w
        val bluePos = Offset(c.x - sep - shift, c.y)
        val pinkPos = Offset(c.x + sep + shift, c.y)
        if (p.trails > 0.01f) trails(bluePos, pinkPos, w, r, p.trails * (1f - crossP * 0.6f))

        val pair = min(p.blue, p.pink)
        logoRing(c, sep, r, t, pair, front = false)
        character(bluePos, r, t, p.blue, isBlue = true)
        character(pinkPos, r, t, p.pink, isBlue = false)
        logoRing(c, sep, r, t, pair, front = true)
        sparkle(Offset(c.x, c.y - r * 1.15f), r, t, p.sparkle)
        particles(c, w, t, p, behind = false)

        if (p.bigRing > 0.01f) bigRing(c, sep, r, t, p.bigRing)
        val burst = d.burst
        if (burst > 0f) burstAt(c, w, burst)
        if (p.bloom > 0.01f) {
            glow(Color(0xFFB9A6FF), c, w * (0.35f + 0.5f * p.bloom), 0.22f * p.bloom)
            // Soft light falling from above in the final beats.
            glow(Purple, Offset(c.x, 0f), size.height * 0.45f * p.bloom, 0.25f * p.bloom)
        }
    }

    private fun DrawScope.background(t: Float, p: SceneParams) {
        drawRect(Brush.verticalGradient(listOf(Night1, Night0, Night2)))
        val w = size.width
        val h = size.height
        glow(Blue, Offset(w * 0.15f, h * 0.2f), w * 0.8f, 0.10f)
        glow(Purple, Offset(w * 0.9f, h * 0.75f), w * 0.8f, 0.10f)
        glow(Pink, Offset(w * 0.5f, h * 0.45f), w * 0.5f, 0.05f + 0.04f * p.bloom)
        for ((i, s) in stars.withIndex()) {
            val tw = 0.15f + 0.35f * (0.5f + 0.5f * sin(t * (0.8f + (i % 5) * 0.3f) + i))
            drawCircle(Color.White.copy(alpha = tw), (0.6f + (i % 3) * 0.35f).dp.toPx(), Offset(s.x * w, s.y * h))
        }
    }

    /** Two neon elliptical orbits that draw themselves (trim) and keep rotating. */
    private fun DrawScope.orbits(c: Offset, w: Float, t: Float, p: SceneParams) {
        if (p.orbitDraw < 0.01f) return
        val rx = w * 0.42f * p.orbitScale
        val ry = rx * 0.36f
        val stroke = 1.6.dp.toPx()
        for (k in 0..1) {
            val tilt = if (k == 0) -14f else 16f
            val start = -90f + t * (if (k == 0) 22f else -18f)
            val sweep = 360f * p.orbitDraw.coerceIn(0f, 1f)
            val brush = Brush.sweepGradient(listOf(Cyan, Purple, Pink, Cyan), c)
            rotate(tilt, c) {
                val tl = Offset(c.x - rx, c.y - ry)
                val sz = Size(rx * 2, ry * 2)
                drawArc(brush, start, sweep, false, tl, sz, alpha = 0.22f, style = Stroke(stroke * 4, cap = StrokeCap.Round))
                drawArc(brush, start, sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                // Bright spark riding at the drawing head.
                val a = Math.toRadians((start + sweep).toDouble())
                val head = Offset(c.x + (rx * cos(a)).toFloat(), c.y + (ry * sin(a)).toFloat())
                glow(Color.White, head, 9.dp.toPx(), 0.7f)
                drawCircle(Color.White, 1.8.dp.toPx(), head)
            }
        }
    }

    private fun DrawScope.particles(c: Offset, w: Float, t: Float, p: SceneParams, behind: Boolean) {
        val visible = (p.particles * PARTICLES).toInt()
        if (visible == 0) return
        val rx = w * 0.42f * p.orbitScale
        val ry = rx * 0.36f
        val pull = 1f - 0.55f * p.attract
        for (i in 0 until visible) {
            val pt = particles[i]
            val ang = pt.angle + t * pt.speed * 2f * PI.toFloat()
            val sinA = sin(ang)
            // Particles on the far side of the orbit are drawn behind the characters.
            if ((sinA < 0f) != behind) continue
            val stream = if (p.outward > 0.01f) frac(t * 0.22f + pt.seed) else 0f
            val radial = 1f + stream * 0.9f * p.outward
            var x = cos(ang) * rx * pt.radius * pull * radial
            var y = sinA * ry * pt.radius * pull * radial
            val tilt = if (pt.tilt == 0) -0.245f else 0.28f
            val rxT = x * cos(tilt) - y * sin(tilt)
            y = x * sin(tilt) + y * cos(tilt)
            x = rxT
            val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * 3f + pt.seed * 20f))
            val alpha = twinkle * (1f - stream * p.outward * 0.9f) * p.particles.coerceAtMost(1f)
            drawCircle(pt.color.copy(alpha = alpha), pt.size.dp.toPx(), Offset(c.x + x, c.y + y))
        }
    }

    /** Ring wrapped around the two characters, like in the logo (back half / front half). */
    private fun DrawScope.logoRing(c: Offset, sep: Float, r: Float, t: Float, presence: Float, front: Boolean) {
        if (presence < 0.01f) return
        val rx = sep + r * 1.55f
        val ry = r * 0.42f
        val stroke = r * 0.11f
        val breath = 0.85f + 0.15f * sin(t * 1.6f)
        val brush = Brush.horizontalGradient(listOf(Cyan, Purple, Pink), c.x - rx, c.x + rx)
        rotate(-8f, c) {
            val tl = Offset(c.x - rx, c.y + r * 0.15f - ry)
            val sz = Size(rx * 2, ry * 2)
            val start = if (front) 0f else 180f
            drawArc(brush, start, 180f, false, tl, sz, alpha = 0.3f * presence * breath, style = Stroke(stroke * 3f, cap = StrokeCap.Round))
            drawArc(brush, start, 180f, false, tl, sz, alpha = presence, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }

    /**
     * Speech-bubble character from the logo: glossy sphere, tail at the bottom (blue: left,
     * pink: right), two oval eyes looking at the other. Idle life: float, breathe, blink.
     */
    private fun DrawScope.character(pos: Offset, r: Float, t: Float, presence: Float, isBlue: Boolean) {
        if (presence < 0.01f) return
        val phaseOff = if (isBlue) 0f else 1.7f
        val bob = sin(t * 1.3f + phaseOff) * r * 0.05f
        val breathe = 1f + 0.015f * sin(t * 1.1f + phaseOff)
        val appear = 0.6f + 0.4f * easeOutBack(presence.coerceIn(0f, 1f))
        val center = Offset(pos.x, pos.y + bob)
        val dir = if (isBlue) -1f else 1f // tail side
        val glowColor = if (isBlue) Cyan else Pink
        glow(glowColor, center, r * 2.2f, 0.32f * presence)

        rotate(sin(t * 0.9f + phaseOff) * 2f, center) {
            scale(appear * breathe, center) {
                // Tail.
                bodyPath.reset()
                bodyPath.moveTo(center.x + dir * r * 0.55f, center.y + r * 0.6f)
                bodyPath.quadraticTo(center.x + dir * r * 0.95f, center.y + r * 1.15f, center.x + dir * r * 0.98f, center.y + r * 1.32f)
                bodyPath.quadraticTo(center.x + dir * r * 0.45f, center.y + r * 1.1f, center.x + dir * r * 0.02f, center.y + r * 0.92f)
                bodyPath.close()
                val fill = if (isBlue) {
                    Brush.radialGradient(
                        listOf(Color(0xFF8FE3FF), Color(0xFF2F7BFF), Color(0xFF2A1FD0)),
                        center = center + Offset(-r * 0.35f, -r * 0.45f), radius = r * 1.6f,
                    )
                } else {
                    Brush.radialGradient(
                        listOf(Color(0xFFFFC07A), Color(0xFFFF4FA8), Color(0xFFB81382)),
                        center = center + Offset(r * 0.35f, -r * 0.5f), radius = r * 1.6f,
                    )
                }
                drawPath(bodyPath, fill, alpha = presence)
                drawCircle(fill, r, center, alpha = presence)
                // Rim light + gloss.
                drawCircle(glowColor.copy(alpha = 0.55f * presence), r, center, style = Stroke(r * 0.05f))
                glow(Color.White, center + Offset(-r * 0.38f, -r * 0.48f), r * 0.55f, 0.5f * presence)
                // Eyes look towards the other character; blink every few seconds.
                val blinkT = frac((t + phaseOff) / 3.7f)
                val open = if (blinkT > 0.965f) 0.12f else 1f
                val eyeW = r * 0.17f
                val eyeH = r * 0.34f * open
                val look = -dir * r * 0.1f
                for (ex in listOf(-0.2f, 0.2f)) {
                    drawOval(
                        Color.White.copy(alpha = presence),
                        Offset(center.x + ex * r + look - eyeW / 2, center.y - r * 0.12f - eyeH / 2),
                        Size(eyeW, eyeH),
                    )
                }
            }
        }
    }

    private fun DrawScope.sparkle(at: Offset, r: Float, t: Float, intensity: Float) {
        if (intensity < 0.01f) return
        val twinkle = 0.9f + 0.1f * sin(t * 4f)
        val s = r * 0.42f * intensity.coerceAtMost(1.6f) * twinkle
        glow(Gold, at, s * 3.2f, 0.45f * intensity.coerceAtMost(1f))
        starPath.reset()
        starPath.moveTo(at.x, at.y - s)
        starPath.quadraticTo(at.x, at.y, at.x + s * 0.72f, at.y)
        starPath.quadraticTo(at.x, at.y, at.x, at.y + s)
        starPath.quadraticTo(at.x, at.y, at.x - s * 0.72f, at.y)
        starPath.quadraticTo(at.x, at.y, at.x, at.y - s)
        starPath.close()
        drawPath(starPath, Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Gold, Color(0xFFFF9F1C)), at.y - s, at.y + s))
    }

    /** People around the pair: glass circles with a person glyph, flickering links, one stronger. */
    private fun DrawScope.avatars(c: Offset, w: Float, h: Float, t: Float, p: SceneParams) {
        val chosen = floor(t / 1.8f).toInt() % AVATARS
        for (i in 0 until AVATARS) {
            val appear = (p.avatars * AVATARS - i * 0.6f).coerceIn(0f, 1f)
            if (appear <= 0f) continue
            val base = (i / AVATARS.toFloat()) * 2f * PI.toFloat() + 0.4f
            val ang = base + sin(t * 0.35f + i) * 0.12f
            val pos = Offset(c.x + cos(ang) * w * 0.4f, c.y + sin(ang) * h * 0.2f)
            val nr = w * 0.055f
            val strong = i == chosen
            val linkAlpha = appear * if (strong) 0.9f else (0.12f + 0.4f * maxOf(0f, sin(t * 1.7f + i * 1.3f)))
            val linkBrush = Brush.linearGradient(listOf(if (i % 2 == 0) Cyan else Pink, Purple), pos, c)
            drawLine(linkBrush, pos, c, (if (strong) 2.2f else 1f).dp.toPx(), alpha = linkAlpha * 0.8f)
            glow(if (strong) Pink else Purple, pos, nr * 2f, (if (strong) 0.45f else 0.18f) * appear)
            drawCircle(Color.White.copy(alpha = 0.06f * appear), nr, pos)
            drawCircle(
                Brush.sweepGradient(listOf(Cyan, Purple, Pink, Cyan), pos), nr, pos,
                alpha = appear * (if (strong) 1f else 0.7f), style = Stroke(1.5.dp.toPx()),
            )
            val glyph = Color.White.copy(alpha = 0.85f * appear)
            drawCircle(glyph, nr * 0.22f, Offset(pos.x, pos.y - nr * 0.2f))
            drawArc(glyph, 200f, 140f, true, Offset(pos.x - nr * 0.42f, pos.y + nr * 0.08f), Size(nr * 0.84f, nr * 0.7f))
        }
    }

    private fun DrawScope.trails(blue: Offset, pink: Offset, w: Float, r: Float, strength: Float) {
        if (strength <= 0.01f) return
        for (k in 0..3) {
            val dy = (k - 1.5f) * r * 0.32f
            val len = w * (0.35f + 0.1f * k)
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, Cyan.copy(alpha = 0.8f)), blue.x - len, blue.x),
                Offset(blue.x - len, blue.y + dy), Offset(blue.x, blue.y + dy),
                (2.5f - k * 0.4f).dp.toPx(), cap = StrokeCap.Round, alpha = strength,
            )
            drawLine(
                Brush.horizontalGradient(listOf(Pink.copy(alpha = 0.8f), Color.Transparent), pink.x, pink.x + len),
                Offset(pink.x, pink.y + dy), Offset(pink.x + len, pink.y + dy),
                (2.5f - k * 0.4f).dp.toPx(), cap = StrokeCap.Round, alpha = strength,
            )
        }
    }

    private fun DrawScope.burstAt(c: Offset, w: Float, burst: Float) {
        glow(Color.White, c, w * (0.15f + 0.45f * (1f - burst)), 0.85f * burst)
        glow(Purple, c, w * 0.7f, 0.4f * burst)
        for (i in 0 until 10) {
            val a = i / 10f * 2f * PI.toFloat()
            val len = w * 0.35f * (1.2f - burst)
            drawLine(
                Color.White.copy(alpha = 0.5f * burst),
                c + Offset(cos(a) * len * 0.3f, sin(a) * len * 0.3f),
                c + Offset(cos(a) * len, sin(a) * len),
                1.5.dp.toPx(), cap = StrokeCap.Round,
            )
        }
    }

    private fun DrawScope.bigRing(c: Offset, sep: Float, r: Float, t: Float, k: Float) {
        val radius = (sep + r * 1.9f) * (0.85f + 0.15f * k)
        val stroke = 2.5.dp.toPx()
        rotate(t * 35f, c) {
            val brush = Brush.sweepGradient(listOf(Cyan, Blue, Purple, Pink, Cyan), c)
            drawCircle(brush, radius, c, alpha = 0.25f * k, style = Stroke(stroke * 5))
            drawCircle(brush, radius, c, alpha = k, style = Stroke(stroke))
            val spark = Offset(c.x + radius, c.y)
            glow(Color.White, spark, 10.dp.toPx(), 0.8f * k)
        }
        glow(Purple, c, radius * 1.4f, 0.14f * k)
    }

    private fun frac(x: Float) = x - floor(x)
    private fun easeOutCubic(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
    private fun easeOutBack(x: Float): Float {
        val c1 = 1.4f
        val c3 = c1 + 1f
        val y = x - 1f
        return 1f + c3 * y * y * y + c1 * y * y
    }

    companion object {
        const val PARTICLES = 72
        const val STARS = 46
        const val AVATARS = 6
    }
}
