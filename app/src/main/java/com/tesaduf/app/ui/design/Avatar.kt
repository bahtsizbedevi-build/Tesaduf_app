package com.tesaduf.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tesaduf.app.R
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.coroutineScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class Accessory(val index: Int, val labelRes: Int, val requirementRes: Int? = null) {
    None(1, R.string.accessory_none),
    Beanie(2, R.string.accessory_beanie),
    Cap(3, R.string.accessory_cap),
    PartyHat(4, R.string.accessory_party, R.string.unlock_party),
    Crown(5, R.string.accessory_crown, R.string.unlock_crown),
    Headphones(6, R.string.accessory_headphones, R.string.unlock_headphones),
    Glasses(7, R.string.accessory_glasses),
    CatEars(8, R.string.accessory_cat, R.string.unlock_cat),
    Halo(9, R.string.accessory_halo, R.string.unlock_halo);

    companion object {
        fun of(index: Int): Accessory = entries.firstOrNull { it.index == index } ?: None
    }
}

/**
 * Avatar = colour (1–8, assigned at random by the server, never chosen) × accessory,
 * stored as "av_<color>_<accessory>". Legacy "orb_<n>" means colour n, no accessory.
 */
data class AvatarStyle(val color: Int, val accessory: Accessory) {
    val key: String get() = "av_${color}_${accessory.index}"

    companion object {
        fun parse(key: String?): AvatarStyle {
            val av = key?.let { Regex("^av_([1-8])_([1-9])$").find(it) }
            if (av != null) return AvatarStyle(av.groupValues[1].toInt(), Accessory.of(av.groupValues[2].toInt()))
            val legacy = key?.removePrefix("orb_")?.toIntOrNull()?.coerceIn(1, 8)
            return AvatarStyle(legacy ?: 1, Accessory.None)
        }
    }
}

// Order is fixed forever: avatar keys store the index (legacy "orb_<n>" used the same order).
private val AvatarPalette: List<Pair<Color, Color>> = listOf(
    Color(0xFF00E5FF) to Color(0xFF3B82F6), // cyan → blue
    Color(0xFF3B82F6) to Color(0xFF7C3AED), // blue → purple
    Color(0xFFB45CFF) to Color(0xFFF43F9E), // purple → pink
    Color(0xFFF43F9E) to Color(0xFFFF7A59), // pink → coral
    Color(0xFF3DF5A8) to Color(0xFF00E5FF), // mint → cyan
    Color(0xFF6366F1) to Color(0xFFA855F7), // indigo → violet
    Color(0xFFFF5CA8) to Color(0xFF7C3AED), // rose → purple
    Color(0xFF00E5FF) to Color(0xFFF43F9E), // cyan → pink
)

/**
 * The living anonymous avatar: a glossy orb with eyes and a tiny mouth.
 *
 * Idle life (when [alive]): breathing squash/stretch, a slow sway, random blinks and the
 * occasional wink, glances around, now and then a happy wiggle. A non-null [gaze]
 * (x, y in -1..1) makes it look at that point (typing, finger, phone tilt).
 * [mood] sets the expression from outside; with [onTap] (or [interactive]) a tap makes it
 * squish and react on its own (giggle, wink, surprise or a burst of hearts).
 * All motion is read in the draw phase only, so animation never recomposes.
 */
@Composable
fun TesadufAvatar(
    avatarKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
    alive: Boolean = true,
    gaze: Offset? = null,
    mood: AvatarMood = AvatarMood.Idle,
    interactive: Boolean = false,
    onTap: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val style = AvatarStyle.parse(avatarKey)
    val (from, to) = AvatarPalette[style.color - 1]
    val description = contentDescription ?: stringResource(R.string.avatar_content_description)
    val scope = rememberCoroutineScope()

    val leftOpen = remember { Animatable(1f) }
    val rightOpen = remember { Animatable(1f) }
    val lookX = remember { Animatable(0f) }
    val lookY = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val squish = remember { Animatable(0f) } // >0 squashed, <0 stretched
    val wiggle = remember { Animatable(0f) }
    val hearts = remember { Animatable(0f) } // 0..1 burst progress (0 = none)
    val moodBlend = remember { Animatable(1f) }
    var reaction by remember { mutableStateOf<AvatarMood?>(null) }
    val target = rememberUpdatedState(gaze)

    val time: State<Float> = if (alive) {
        rememberInfiniteTransition(label = "life").animateFloat(
            0f, 1_000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing)), label = "t",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    if (alive) {
        LaunchedEffect(Unit) {
            // Blinks; sometimes a double blink, sometimes a cheeky wink.
            while (true) {
                delay(Random.nextLong(2_200, 5_600))
                if (Random.nextFloat() < 0.12f) {
                    val eye = if (Random.nextBoolean()) leftOpen else rightOpen
                    eye.animateTo(0.05f, tween(110))
                    delay(260)
                    eye.animateTo(1f, tween(160))
                } else {
                    repeat(if (Random.nextFloat() < 0.2f) 2 else 1) {
                        launch { leftOpen.animateTo(0.08f, tween(80)); leftOpen.animateTo(1f, tween(130)) }
                        rightOpen.animateTo(0.08f, tween(80))
                        rightOpen.animateTo(1f, tween(130))
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            // Idle glances, only while nobody tells it where to look.
            val spots = listOf(Offset.Zero, Offset.Zero, Offset(-0.85f, 0f), Offset(0.85f, 0f), Offset(0.5f, -0.4f), Offset(-0.5f, 0.35f))
            while (true) {
                delay(Random.nextLong(1_600, 4_200))
                if (target.value == null) {
                    val spot = spots.random()
                    launch { lookX.animateTo(spot.x, tween(320, easing = FastOutSlowInEasing)) }
                    lookY.animateTo(spot.y, tween(320, easing = FastOutSlowInEasing))
                }
            }
        }
        LaunchedEffect(Unit) {
            // Every so often a little happy wiggle.
            while (true) {
                delay(Random.nextLong(9_000, 16_000))
                wiggle.animateTo(1f, tween(140))
                wiggle.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessLow))
            }
        }
    }

    suspend fun jump() = coroutineScope {
        squish.animateTo(0.18f, tween(90))
        launch {
            squish.animateTo(-0.12f, tween(160))
            squish.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
        hop.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
        hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }

    suspend fun burstHearts() {
        hearts.snapTo(0.001f)
        hearts.animateTo(1f, tween(1_400, easing = LinearEasing))
        hearts.snapTo(0f)
    }

    LaunchedEffect(mood) {
        moodBlend.snapTo(0f)
        launch { moodBlend.animateTo(1f, tween(260)) }
        if (mood == AvatarMood.Love) launch { burstHearts() }
        if (mood == AvatarMood.Joy || mood == AvatarMood.Love) jump()
    }

    LaunchedEffect(gaze) {
        // Released: ease back to centre, then idle glances take over.
        val g = gaze ?: Offset.Zero
        launch { lookX.animateTo(g.x.coerceIn(-1f, 1f), spring(stiffness = Spring.StiffnessMediumLow)) }
        lookY.animateTo(g.y.coerceIn(-1f, 1f), spring(stiffness = Spring.StiffnessMediumLow))
    }

    val tappable = interactive || onTap != null
    val tapLabel = stringResource(R.string.avatar_poke)
    val react: () -> Unit = {
        onTap?.invoke()
        scope.launch {
            val pick = listOf(AvatarMood.Joy, AvatarMood.Love, AvatarMood.Surprised, AvatarMood.Wink).random()
            reaction = pick
            moodBlend.snapTo(0f)
            launch { moodBlend.animateTo(1f, tween(200)) }
            launch { jump() }
            if (pick == AvatarMood.Love) launch { burstHearts() }
            if (pick == AvatarMood.Wink) {
                rightOpen.animateTo(0.05f, tween(110))
                delay(450)
                rightOpen.animateTo(1f, tween(160))
            } else {
                delay(1_300)
            }
            reaction = null
        }
    }
    val shownMood = rememberUpdatedState(reaction ?: mood)

    Box(
        modifier
            .size(size)
            .semantics { this.contentDescription = description }
            .then(
                if (tappable) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = tapLabel,
                        onClick = react,
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                val t = time.value
                val breathe = sin(t * 2.2f) * 0.018f
                val sq = squish.value
                translationY = -hop.value * size.toPx() * 0.14f + sin(t * 1.3f) * size.toPx() * 0.012f
                scaleX = 1f + breathe + sq * 0.9f
                scaleY = 1f - breathe - sq
                rotationZ = sin(t * 0.9f) * 2.2f + sin(t * 18f) * 7f * wiggle.value
                transformOrigin = TransformOrigin(0.5f, 0.95f)
            }
            .drawBehind {
                val m = shownMood.value
                drawOrb(from, to, leftOpen.value, rightOpen.value, Offset(lookX.value, lookY.value), m, moodBlend.value, time.value)
                // Accessories lag behind a hop a little (wobble).
                rotate(-hop.value * 9f + wiggle.value * sin(time.value * 18f) * 6f, orbCenter()) {
                    drawAccessory(style.accessory)
                }
                if (hearts.value > 0f) drawHeartBurst(hearts.value)
            },
    )
}

// Leaves headroom so every accessory (party hat is the tallest) stays inside the bounds.
private fun DrawScope.orbCenter() = Offset(size.width / 2, size.height * 0.62f)
private fun DrawScope.orbRadius() = size.minDimension * 0.33f

private fun heartPathAt(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy + s * 0.45f)
    cubicTo(cx - s * 0.75f, cy - s * 0.05f, cx - s * 0.45f, cy - s * 0.6f, cx, cy - s * 0.25f)
    cubicTo(cx + s * 0.45f, cy - s * 0.6f, cx + s * 0.75f, cy - s * 0.05f, cx, cy + s * 0.45f)
    close()
}

private fun DrawScope.drawHeartBurst(p: Float) {
    val c = orbCenter()
    val r = orbRadius()
    for (i in 0 until 6) {
        val a = (-150f + i * 24f) * (PI.toFloat() / 180f)
        val dist = r * (0.9f + 1.1f * p)
        val x = c.x + cos(a) * dist
        val y = c.y + sin(a) * dist - r * 0.6f * p
        val alpha = (if (p < 0.2f) p / 0.2f else 1f) * (1f - p)
        val col = if (i % 2 == 0) TesadufColors.Pink else Color(0xFFFF8FD0)
        drawPath(heartPathAt(x, y, r * (0.22f + 0.08f * (i % 3))), col.copy(alpha = alpha))
    }
}

private val MouthDark = Color(0xFF3B1240)

private fun DrawScope.drawOrb(
    from: Color,
    to: Color,
    leftOpen: Float,
    rightOpen: Float,
    look: Offset,
    mood: AvatarMood,
    blend: Float,
    t: Float,
) {
    val c = orbCenter()
    val r = orbRadius()
    softGlow(from, c, r * 1.45f, 0.28f)
    drawCircle(Brush.linearGradient(listOf(from, to), c - Offset(r, r), c + Offset(r, r)), r, c)
    // Gloss highlight.
    softGlow(Color.White, c - Offset(r * 0.38f, r * 0.45f), r * 0.55f, 0.38f)
    // Blush on happy moods.
    if (mood == AvatarMood.Love || mood == AvatarMood.Joy || mood == AvatarMood.Wink) {
        for (dx in listOf(-0.5f, 0.5f)) softGlow(Color(0xFFFF6FB5), Offset(c.x + r * dx, c.y + r * 0.22f), r * 0.24f, 0.55f * blend)
    }
    // Eyes follow the look direction; blinking squashes their height. Moods reshape them.
    val eyeW = r * 0.17f
    val shift = Offset(look.x * r * 0.22f, look.y * r * 0.18f)
    val white = Color.White.copy(alpha = 0.96f * blend.coerceIn(0.3f, 1f))
    listOf(-0.24f, 0.24f).forEachIndexed { idx, dx ->
        val ex = c.x + r * dx + shift.x
        val ey = c.y - r * 0.08f + shift.y
        val open = if (idx == 0) leftOpen else rightOpen
        when (mood) {
            AvatarMood.Love -> drawPath(heartPathAt(ex, ey, r * 0.26f * (0.6f + 0.4f * blend)), Color(0xFFFFE3F3))
            AvatarMood.Joy -> drawArc(
                white, 200f, 140f, false,
                Offset(ex - eyeW * 0.9f, ey - eyeW * 0.5f), Size(eyeW * 1.8f, eyeW * 1.6f),
                style = Stroke(r * 0.07f, cap = StrokeCap.Round),
            )
            AvatarMood.Sleepy -> {
                val h = r * 0.07f
                drawOval(white, Offset(ex - eyeW / 2, ey + r * 0.06f - h / 2), Size(eyeW * 1.1f, h))
            }
            AvatarMood.Surprised -> {
                val d = eyeW * 1.35f
                drawOval(white, Offset(ex - d / 2, ey - d * 0.65f), Size(d, d * 1.3f))
            }
            AvatarMood.Wink, AvatarMood.Idle, AvatarMood.Wave, AvatarMood.Talking -> {
                if (open < 0.2f) {
                    // Closed eye: a soft curved line.
                    drawArc(
                        white, 20f, 140f, false,
                        Offset(ex - eyeW * 0.8f, ey - eyeW * 0.6f), Size(eyeW * 1.6f, eyeW * 1.2f),
                        style = Stroke(r * 0.06f, cap = StrokeCap.Round),
                    )
                } else {
                    val eyeH = r * 0.30f * open
                    drawOval(white, Offset(ex - eyeW / 2, ey - eyeH / 2), Size(eyeW, eyeH))
                }
            }
        }
    }
    // Mouth.
    val mx = c.x + shift.x * 0.6f
    val my = c.y + r * 0.3f + shift.y * 0.5f
    val mouth = Color.White.copy(alpha = 0.85f * blend.coerceIn(0.3f, 1f))
    when (mood) {
        AvatarMood.Joy -> {
            val w = r * 0.42f
            val path = Path().apply {
                moveTo(mx - w / 2, my - r * 0.04f)
                quadraticTo(mx, my + r * 0.32f, mx + w / 2, my - r * 0.04f)
                close()
            }
            drawPath(path, MouthDark.copy(alpha = 0.85f))
            drawPath(path, mouth, style = Stroke(r * 0.045f, join = StrokeJoin.Round))
        }
        AvatarMood.Talking -> {
            // Chatter: the mouth opens and closes as if speaking.
            val open = 0.25f + 0.75f * abs(sin(t * 11f) * sin(t * 4.3f + 1f))
            val w = r * 0.2f
            val h = r * 0.2f * open
            drawOval(MouthDark.copy(alpha = 0.85f), Offset(mx - w / 2, my - h * 0.3f), Size(w, h))
        }
        AvatarMood.Surprised, AvatarMood.Sleepy -> {
            val d = r * (if (mood == AvatarMood.Surprised) 0.16f else 0.1f)
            drawOval(MouthDark.copy(alpha = 0.85f), Offset(mx - d / 2, my - d * 0.3f), Size(d, d * 1.2f))
        }
        else -> drawArc(
            mouth, 25f, 130f, false,
            Offset(mx - r * 0.13f, my - r * 0.12f), Size(r * 0.26f, r * 0.18f),
            style = Stroke(r * 0.045f, cap = StrokeCap.Round),
        )
    }
    if (mood == AvatarMood.Wave) {
        // A little glove waving beside the body.
        val pivot = Offset(c.x + r * 0.95f, c.y + r * 0.25f)
        rotate(-25f + 40f * sin(t * 9f), pivot) {
            val hand = Offset(pivot.x + r * 0.05f, pivot.y - r * 0.55f)
            drawLine(to, pivot, hand, r * 0.16f, StrokeCap.Round)
            drawCircle(Brush.linearGradient(listOf(from, to)), r * 0.2f, hand)
            softGlow(Color.White, hand - Offset(r * 0.06f, r * 0.06f), r * 0.12f, 0.4f)
        }
    }
    if (mood == AvatarMood.Sleepy) {
        val z = Offset(c.x + r * 0.7f, c.y - r * 0.85f)
        val zs = r * 0.18f
        val zPath = Path().apply {
            moveTo(z.x, z.y)
            lineTo(z.x + zs, z.y)
            lineTo(z.x, z.y + zs)
            lineTo(z.x + zs, z.y + zs)
        }
        drawPath(zPath, Color.White.copy(alpha = 0.7f * blend), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
    }
}

/** Facial expression of the living avatar. */
enum class AvatarMood { Idle, Love, Joy, Sleepy, Surprised, Wink, Wave, Talking }

private fun DrawScope.drawAccessory(accessory: Accessory) {
    val c = orbCenter()
    val r = orbRadius()
    when (accessory) {
        Accessory.None -> Unit
        Accessory.Beanie -> {
            val body = Color(0xFF22305C)
            drawArc(body, 180f, 180f, true, Offset(c.x - r * 0.98f, c.y - r * 1.12f), Size(r * 1.96f, r * 1.5f))
            drawRoundRect(
                Color.White.copy(alpha = 0.9f),
                Offset(c.x - r * 1.0f, c.y - r * 0.5f), Size(r * 2.0f, r * 0.26f), CornerRadius(r * 0.13f),
            )
            drawCircle(Color.White, r * 0.16f, Offset(c.x, c.y - r * 1.12f))
        }
        Accessory.Cap -> {
            val cap = Color(0xFF3B82F6)
            drawArc(cap, 180f, 180f, true, Offset(c.x - r * 0.9f, c.y - r * 1.05f), Size(r * 1.8f, r * 1.25f))
            drawRoundRect(cap, Offset(c.x - r * 0.1f, c.y - r * 0.5f), Size(r * 1.35f, r * 0.2f), CornerRadius(r * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.08f, Offset(c.x, c.y - r * 1.02f))
        }
        Accessory.PartyHat -> {
            val cone = Path().apply {
                moveTo(c.x - r * 0.45f, c.y - r * 0.82f)
                lineTo(c.x + r * 0.42f, c.y - r * 0.9f)
                lineTo(c.x + r * 0.12f, c.y - r * 1.8f)
                close()
            }
            drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFBBF24), Color(0xFFF43F9E)), c.y - r * 1.8f, c.y - r * 0.8f))
            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.06f, Offset(c.x - r * 0.05f, c.y - r * 1.1f))
            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.05f, Offset(c.x + r * 0.15f, c.y - r * 1.38f))
            drawCircle(Color(0xFF00E5FF), r * 0.14f, Offset(c.x + r * 0.12f, c.y - r * 1.82f))
        }
        Accessory.Crown -> {
            val gold = Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)), c.y - r * 1.5f, c.y - r * 0.75f)
            val crown = Path().apply {
                moveTo(c.x - r * 0.55f, c.y - r * 0.78f)
                lineTo(c.x - r * 0.6f, c.y - r * 1.4f)
                lineTo(c.x - r * 0.28f, c.y - r * 1.08f)
                lineTo(c.x, c.y - r * 1.5f)
                lineTo(c.x + r * 0.28f, c.y - r * 1.08f)
                lineTo(c.x + r * 0.6f, c.y - r * 1.4f)
                lineTo(c.x + r * 0.55f, c.y - r * 0.78f)
                close()
            }
            drawPath(crown, gold)
            drawCircle(Color(0xFFF43F9E), r * 0.09f, Offset(c.x, c.y - r * 0.98f))
        }
        Accessory.Headphones -> {
            val band = Color(0xFFE2E8F0)
            drawArc(
                band, 195f, 150f, false,
                Offset(c.x - r * 1.08f, c.y - r * 1.12f), Size(r * 2.16f, r * 2.0f),
                style = Stroke(r * 0.13f, cap = StrokeCap.Round),
            )
            for (side in listOf(-1f, 1f)) {
                val x = if (side < 0) c.x - r * 1.18f else c.x + r * 0.86f
                drawRoundRect(Color(0xFF00E5FF), Offset(x, c.y - r * 0.32f), Size(r * 0.32f, r * 0.58f), CornerRadius(r * 0.14f))
            }
        }
        Accessory.Glasses -> {
            val frame = Color(0xFF0B0F1F)
            val y = c.y - r * 0.08f
            for (dx in listOf(-0.27f, 0.27f)) {
                drawCircle(Color.White.copy(alpha = 0.12f), r * 0.24f, Offset(c.x + r * dx, y))
                drawCircle(frame, r * 0.24f, Offset(c.x + r * dx, y), style = Stroke(r * 0.07f))
            }
            drawLine(frame, Offset(c.x - r * 0.05f, y - r * 0.02f), Offset(c.x + r * 0.05f, y - r * 0.02f), r * 0.06f, StrokeCap.Round)
            softGlow(Color.White, Offset(c.x - r * 0.33f, y - r * 0.1f), r * 0.07f, 0.6f)
        }
        Accessory.CatEars -> {
            for (side in listOf(-1f, 1f)) {
                val base = c.x + side * r * 0.55f
                val outer = Path().apply {
                    moveTo(base - r * 0.32f, c.y - r * 0.72f)
                    lineTo(base + side * r * 0.12f, c.y - r * 1.35f)
                    lineTo(base + r * 0.32f, c.y - r * 0.72f)
                    close()
                }
                drawPath(outer, Color(0xFF2A1F4A))
                val inner = Path().apply {
                    moveTo(base - r * 0.16f, c.y - r * 0.8f)
                    lineTo(base + side * r * 0.08f, c.y - r * 1.18f)
                    lineTo(base + r * 0.16f, c.y - r * 0.8f)
                    close()
                }
                drawPath(inner, Color(0xFFFF8FD0))
            }
        }
        Accessory.Halo -> {
            val center = Offset(c.x, c.y - r * 1.25f)
            softGlow(Color(0xFFFFE08A), center, r * 0.75f, 0.5f)
            drawOval(
                Color(0xFFFFD45C), Offset(center.x - r * 0.55f, center.y - r * 0.14f), Size(r * 1.1f, r * 0.28f),
                style = Stroke(r * 0.09f),
            )
        }
    }
}

/** Accessory choices rendered on the user's own (random) colour. */
@Composable
fun AccessoryPicker(
    color: Int,
    selected: Accessory,
    onSelect: (Accessory) -> Unit,
    modifier: Modifier = Modifier,
    unlocked: List<Int> = Accessory.entries.map { it.index },
    current: Accessory = selected,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Accessory.entries.forEach { accessory ->
            val isSelected = accessory == selected
            val locked = accessory.index !in unlocked && accessory != current
            val name = stringResource(accessory.labelRes)
            val requirement = accessory.requirementRes?.let { stringResource(it) }
            Column(
                Modifier
                    .widthIn(min = 88.dp)
                    .clip(Shapes.field)
                    .selectable(isSelected, enabled = !locked, onClick = { onSelect(accessory) }, role = Role.RadioButton)
                    .background(if (isSelected) TesadufColors.Cyan.copy(alpha = 0.10f) else TesadufColors.Glass)
                    .border(1.dp, if (isSelected) TesadufColors.Cyan.copy(alpha = 0.6f) else TesadufColors.StrokeSoft, Shapes.field)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    TesadufAvatar(
                        AvatarStyle(color, accessory).key, 56.dp, alive = false, contentDescription = name,
                        modifier = Modifier.graphicsLayer { alpha = if (locked) 0.35f else 1f },
                    )
                    if (locked) {
                        Icon(TIcons.LockKeyhole, contentDescription = null, tint = TesadufColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) TesadufColors.Cyan else TesadufColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                if (locked && requirement != null) {
                    Text(requirement, style = MaterialTheme.typography.labelSmall, color = TesadufColors.TextMuted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
