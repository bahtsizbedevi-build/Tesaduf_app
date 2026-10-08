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

enum class Accessory(val index: Int, val labelRes: Int) {
    None(1, R.string.accessory_none),
    Beanie(2, R.string.accessory_beanie),
    Cap(3, R.string.accessory_cap),
    PartyHat(4, R.string.accessory_party),
    Crown(5, R.string.accessory_crown),
    Headphones(6, R.string.accessory_headphones);

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
            val av = key?.let { Regex("^av_([1-8])_([1-6])$").find(it) }
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
 * The living anonymous avatar: a glossy orb with eyes. When [alive], it blinks at random
 * intervals and glances around; a non-null [gaze] (x, y in -1..1) makes it look at that
 * point instead (e.g. the text being typed). All motion is read in the draw phase only,
 * so it never triggers recomposition, and frame-clock animations pause off-screen.
 */
@Composable
fun TesadufAvatar(
    avatarKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
    alive: Boolean = true,
    gaze: Offset? = null,
    contentDescription: String? = null,
) {
    val style = AvatarStyle.parse(avatarKey)
    val (from, to) = AvatarPalette[style.color - 1]
    val description = contentDescription ?: stringResource(R.string.avatar_content_description)

    val eyeOpen = remember { Animatable(1f) }
    val lookX = remember { Animatable(0f) }
    val lookY = remember { Animatable(0f) }
    val target = rememberUpdatedState(gaze)

    if (alive) {
        LaunchedEffect(Unit) {
            // Blinking: occasionally a double blink.
            while (true) {
                delay(Random.nextLong(2_200, 5_600))
                repeat(if (Random.nextFloat() < 0.2f) 2 else 1) {
                    eyeOpen.animateTo(0.08f, tween(80))
                    eyeOpen.animateTo(1f, tween(130))
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
    }
    LaunchedEffect(gaze) {
        // Released: ease back to centre, then idle glances take over.
        val g = gaze ?: Offset.Zero
        launch { lookX.animateTo(g.x.coerceIn(-1f, 1f), spring(stiffness = Spring.StiffnessMediumLow)) }
        lookY.animateTo(g.y.coerceIn(-1f, 1f), spring(stiffness = Spring.StiffnessMediumLow))
    }

    Box(
        modifier
            .size(size)
            .semantics { this.contentDescription = description }
            .drawBehind {
                drawOrb(from, to, eyeOpen.value, Offset(lookX.value, lookY.value))
                drawAccessory(style.accessory)
            },
    )
}

// Leaves headroom so every accessory (party hat is the tallest) stays inside the bounds.
private fun DrawScope.orbCenter() = Offset(size.width / 2, size.height * 0.62f)
private fun DrawScope.orbRadius() = size.minDimension * 0.33f

private fun DrawScope.drawOrb(from: Color, to: Color, eyeOpen: Float, look: Offset) {
    val c = orbCenter()
    val r = orbRadius()
    softGlow(from, c, r * 1.45f, 0.28f)
    drawCircle(Brush.linearGradient(listOf(from, to), c - Offset(r, r), c + Offset(r, r)), r, c)
    // Gloss highlight.
    softGlow(Color.White, c - Offset(r * 0.38f, r * 0.45f), r * 0.55f, 0.38f)
    // Eyes follow the look direction; blinking squashes their height.
    val eyeW = r * 0.17f
    val eyeH = r * 0.30f * eyeOpen.coerceIn(0.05f, 1f)
    val shift = Offset(look.x * r * 0.22f, look.y * r * 0.18f)
    val eyeY = c.y - r * 0.08f + shift.y - eyeH / 2
    for (dx in listOf(-0.24f, 0.24f)) {
        val x = c.x + r * dx + shift.x - eyeW / 2
        drawOval(Color.White.copy(alpha = 0.96f), Offset(x, eyeY), Size(eyeW, eyeH))
    }
}

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
    }
}

/** Accessory choices rendered on the user's own (random) colour. */
@Composable
fun AccessoryPicker(
    color: Int,
    selected: Accessory,
    onSelect: (Accessory) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Accessory.entries.forEach { accessory ->
            val isSelected = accessory == selected
            val name = stringResource(accessory.labelRes)
            Column(
                Modifier
                    .widthIn(min = 88.dp)
                    .clip(Shapes.field)
                    .selectable(isSelected, onClick = { onSelect(accessory) }, role = Role.RadioButton)
                    .background(if (isSelected) TesadufColors.Cyan.copy(alpha = 0.10f) else TesadufColors.Glass)
                    .border(1.dp, if (isSelected) TesadufColors.Cyan.copy(alpha = 0.6f) else TesadufColors.StrokeSoft, Shapes.field)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TesadufAvatar(AvatarStyle(color, accessory).key, 56.dp, alive = false, contentDescription = name)
                Spacer(Modifier.height(4.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) TesadufColors.Cyan else TesadufColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
