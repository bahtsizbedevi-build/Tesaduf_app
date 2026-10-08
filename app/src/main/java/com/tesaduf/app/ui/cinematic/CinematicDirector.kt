package com.tesaduf.app.ui.cinematic

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tesaduf.app.R
import kotlin.math.exp

/**
 * The story beats. Each phase only declares *targets* for the scene; [SceneParams]
 * eases towards them every frame, so one phase flows into the next without cuts —
 * the same particles, orbits and lights are re-shaped rather than replaced.
 */
enum class CinematicPhase(val durationSec: Float, @param:StringRes val caption: Int?) {
    Intro(1.5f, null),
    Logo(1.3f, null),
    Connecting(1.5f, R.string.cine_connecting),
    NewChats(1.5f, R.string.cine_new_chats),
    Preparing(1.5f, R.string.cine_preparing),
    Searching(1.8f, R.string.cine_searching),
    Crossing(1.5f, R.string.cine_crossing),
    Moment(1.3f, R.string.cine_moment),
    Outro(0.7f, null),
}

enum class CinematicMode { Splash, Matchmaking }

/** Hooks for optional sound / haptics at story beats. */
enum class CinematicCue { Sparkle, Whoosh, Connect, Chime }

/** Smoothed, frame-by-frame scene values the painter draws from. */
class SceneParams {
    var blue = 0f          // blue character presence 0..1
    var pink = 0f          // pink character presence 0..1
    var separation = 0.24f // half distance between characters, fraction of width
    var sparkle = 0f       // golden star intensity (can exceed 1 for a flare)
    var orbitDraw = 0f     // how much of the orbit paths is drawn 0..1
    var orbitScale = 1f
    var particles = 0f     // particle density 0..1
    var outward = 0f       // particles streaming outwards
    var attract = 0f       // particles pulled towards the characters
    var avatars = 0f       // surrounding people nodes
    var trails = 0f        // motion trails / converging from the sides
    var bigRing = 0f       // the single big ring of the final moment
    var wordmark = 0f      // "Tesadüf" text
    var bloom = 0f         // central light bloom
    var zoom = 1f
    var progress = 0f      // loading bar
    var fade = 1f          // whole-scene opacity (0 at the very end)
}

private data class Targets(
    val blue: Float = 1f, val pink: Float = 1f, val separation: Float = 0.12f, val sparkle: Float = 1f,
    val orbitDraw: Float = 1f, val orbitScale: Float = 1f, val particles: Float = 0.35f, val outward: Float = 0f,
    val attract: Float = 0f, val avatars: Float = 0f, val trails: Float = 0f, val bigRing: Float = 0f,
    val wordmark: Float = 0f, val bloom: Float = 0f, val zoom: Float = 1f, val progress: Float = 0f, val fade: Float = 1f,
)

/**
 * Drives the cinematic timeline. Not a Compose object: [update] is called once per
 * frame from the scene's frame loop. [phase] is observable state (captions recompose
 * only when it changes); everything else is read during drawing.
 */
class CinematicDirector(
    val mode: CinematicMode,
    private val onCue: (CinematicCue) -> Unit = {},
) {
    var phase by mutableStateOf(if (mode == CinematicMode.Splash) CinematicPhase.Intro else CinematicPhase.Connecting)
        private set

    /** Bumped every frame; reading it in a draw lambda invalidates only drawing. */
    var frame by mutableLongStateOf(0L)
        private set

    var finished by mutableStateOf(false)
        private set

    val params = SceneParams().apply {
        if (mode == CinematicMode.Matchmaking) {
            // Matchmaking starts from the settled brand pose instead of darkness.
            blue = 1f; pink = 1f; separation = 0.12f; sparkle = 1f; orbitDraw = 0.5f; particles = 0.2f
        }
    }

    /** Seconds since start (drives continuous motion: floating, rotation, particles). */
    var time = 0f
        private set
    var phaseTime = 0f
        private set

    /** Splash: the app has what it needs (session ready or failed) — allowed to finish. */
    var ready = false

    /** Matchmaking: a partner was found. */
    private var matched = false

    /** Speed multiplier (e.g. when system animations are reduced). */
    var speed = 1f

    fun onMatched() {
        if (matched) return
        matched = true
        if (phase.ordinal < CinematicPhase.Crossing.ordinal) enter(CinematicPhase.Crossing)
    }

    fun update(dtRaw: Float) {
        if (finished) return
        val dt = (dtRaw * speed).coerceIn(0f, 0.05f)
        time += dt
        phaseTime += dt
        advanceIfDue()
        ease(dt)
        frame++
    }

    private fun advanceIfDue() {
        if (phaseTime < phase.durationSec) return
        val next: CinematicPhase? = when (mode) {
            CinematicMode.Splash -> when (phase) {
                CinematicPhase.Intro -> CinematicPhase.Logo
                // Hold on "Bağlantılar kuruluyor..." only while the app is not ready yet.
                CinematicPhase.Logo, CinematicPhase.Connecting -> if (ready) CinematicPhase.Outro else CinematicPhase.Connecting
                CinematicPhase.Outro -> null
                else -> CinematicPhase.Outro
            }
            CinematicMode.Matchmaking -> when (phase) {
                CinematicPhase.Connecting -> CinematicPhase.NewChats
                CinematicPhase.NewChats -> CinematicPhase.Preparing
                CinematicPhase.Preparing -> CinematicPhase.Searching
                // Keep cycling the "searching" beats until someone is found.
                CinematicPhase.Searching -> if (matched) CinematicPhase.Crossing else CinematicPhase.NewChats
                CinematicPhase.Crossing -> CinematicPhase.Moment
                CinematicPhase.Moment -> CinematicPhase.Outro
                CinematicPhase.Outro -> null
                else -> CinematicPhase.Connecting
            }
        }
        if (next == null) {
            finished = true
        } else if (next == phase) {
            phaseTime = 0f
        } else {
            enter(next)
        }
    }

    private fun enter(next: CinematicPhase) {
        phase = next
        phaseTime = 0f
        when (next) {
            CinematicPhase.Crossing -> onCue(CinematicCue.Whoosh)
            CinematicPhase.Moment -> onCue(CinematicCue.Chime)
            CinematicPhase.Searching -> onCue(CinematicCue.Connect)
            else -> Unit
        }
    }

    /** Burst of light at the crossing point (0..1, peaks right where the trails meet). */
    val burst: Float
        get() {
            if (phase != CinematicPhase.Crossing) return 0f
            val p = phaseTime / phase.durationSec
            return if (p < 0.62f) 0f else ((1f - (p - 0.62f) / 0.38f)).coerceIn(0f, 1f)
        }

    private var sparkleCueSent = false

    private fun targets(): Targets {
        val pt = phaseTime
        val loopProgress = when (phase) {
            CinematicPhase.NewChats -> 0.55f
            CinematicPhase.Preparing -> 0.68f
            CinematicPhase.Searching -> 0.8f
            else -> 0f
        }
        return when (phase) {
            CinematicPhase.Intro -> {
                if (pt > 1.0f && !sparkleCueSent) { sparkleCueSent = true; onCue(CinematicCue.Sparkle) }
                Targets(
                    blue = if (pt > 0.25f) 1f else 0f,
                    pink = if (pt > 0.65f) 1f else 0f,
                    separation = if (pt > 0.85f) 0.12f else 0.2f,
                    sparkle = if (pt > 1.0f) 1f else 0f,
                    orbitDraw = if (pt > 1.1f) 0.3f else 0f,
                    particles = 0.15f, progress = 0.12f,
                )
            }
            CinematicPhase.Logo -> Targets(wordmark = 1f, orbitDraw = 0.55f, particles = 0.25f, progress = 0.3f, bloom = 0.1f)
            CinematicPhase.Connecting -> Targets(
                orbitDraw = 1f, particles = 0.45f, zoom = 1.04f, bloom = 0.1f,
                wordmark = if (mode == CinematicMode.Splash) 0.35f else 0f,
                progress = if (mode == CinematicMode.Splash) 0.5f else 0.3f,
            )
            CinematicPhase.NewChats -> Targets(orbitScale = 1.22f, particles = 0.85f, outward = 1f, zoom = 1.04f, progress = loopProgress)
            CinematicPhase.Preparing -> Targets(
                separation = 0.105f, sparkle = 1.45f, orbitScale = 1.1f, particles = 0.8f, attract = 1f,
                bloom = 0.35f, zoom = 1.06f, progress = loopProgress,
            )
            CinematicPhase.Searching -> Targets(orbitScale = 1.05f, particles = 0.5f, avatars = 1f, progress = loopProgress, bloom = 0.15f)
            CinematicPhase.Crossing -> Targets(
                separation = 0.11f, trails = 1f, orbitDraw = 0.6f, particles = 0.4f, bloom = 0.2f, zoom = 1.02f, progress = 0.92f,
            )
            CinematicPhase.Moment -> Targets(separation = 0.12f, sparkle = 1.3f, bigRing = 1f, orbitDraw = 0.3f, particles = 0.5f, bloom = 0.35f, progress = 1f)
            CinematicPhase.Outro -> Targets(
                wordmark = if (mode == CinematicMode.Splash) 1f else 0f,
                bigRing = if (mode == CinematicMode.Matchmaking) 1f else 0f,
                bloom = 1f, particles = 0.3f, progress = 1f, fade = 0f, orbitDraw = 0.6f,
            )
        }
    }

    private fun ease(dt: Float) {
        val t = targets()
        val p = params
        fun k(rate: Float) = 1f - exp(-rate * dt)
        val soft = k(3.2f)
        val med = k(5f)
        p.blue += (t.blue - p.blue) * med
        p.pink += (t.pink - p.pink) * med
        p.separation += (t.separation - p.separation) * k(2.4f)
        p.sparkle += (t.sparkle - p.sparkle) * med
        p.orbitDraw += (t.orbitDraw - p.orbitDraw) * k(1.6f)
        p.orbitScale += (t.orbitScale - p.orbitScale) * soft
        p.particles += (t.particles - p.particles) * soft
        p.outward += (t.outward - p.outward) * soft
        p.attract += (t.attract - p.attract) * soft
        p.avatars += (t.avatars - p.avatars) * soft
        p.trails += (t.trails - p.trails) * k(4f)
        p.bigRing += (t.bigRing - p.bigRing) * k(2.6f)
        p.wordmark += (t.wordmark - p.wordmark) * k(4f)
        p.bloom += (t.bloom - p.bloom) * k(3f)
        p.zoom += (t.zoom - p.zoom) * k(1.2f)
        p.progress += (t.progress - p.progress) * k(2.2f)
        p.fade += (t.fade - p.fade) * (if (t.fade < p.fade) k(6f) else med)
        if (phase == CinematicPhase.Outro && phaseTime >= phase.durationSec * 0.95f) p.fade = 0f
    }
}
