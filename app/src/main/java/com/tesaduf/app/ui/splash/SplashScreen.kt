package com.tesaduf.app.ui.splash

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.tesaduf.app.ui.cinematic.CinematicDirector
import com.tesaduf.app.ui.cinematic.CinematicMode
import com.tesaduf.app.ui.cinematic.CinematicScene
import com.tesaduf.app.ui.cinematic.rememberCinematicFx

/**
 * Cold-start splash: darkness → the two characters → "Tesadüf". As soon as the
 * anonymous session is resolved ([ready]) the scene blooms into the app (~3.5 s);
 * if it is not ready yet, it lingers on "Bağlantılar kuruluyor…" instead of cutting.
 */
@Composable
fun SplashScreen(
    ready: Boolean,
    hapticsEnabled: Boolean,
    onStart: () -> Unit,
    onFinished: () -> Unit,
) {
    val fx = rememberCinematicFx(hapticsEnabled)
    val director = remember { CinematicDirector(CinematicMode.Splash, fx) }
    val finished = rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) { onStart() }
    LaunchedEffect(ready) { director.ready = ready }
    LaunchedEffect(director.finished) { if (director.finished) finished.value() }

    CinematicScene(director, Modifier.fillMaxSize(), showCaption = true, showProgress = true)
}
