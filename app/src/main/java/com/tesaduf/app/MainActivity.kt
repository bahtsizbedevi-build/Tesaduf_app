package com.tesaduf.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tesaduf.app.navigation.TesadufNavHost
import com.tesaduf.app.notifications.Reminders
import com.tesaduf.app.ui.theme.TesadufTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash is a plain night frame; the branded animation is in Compose.
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as TesadufApplication).container
        setContent {
            TesadufTheme {
                TesadufNavHost(container)
            }
        }
        // Debug-only hook for manual testing:  adb shell am start -n com.tesaduf.app/.MainActivity --ez debug_reminder true
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DEBUG_REMINDER, false) == true) {
            Reminders.fireNowForDebug(this)
        }
    }

    override fun onResume() {
        super.onResume()
        (application as TesadufApplication).container.preferences.markOpened()
    }

    private companion object {
        const val EXTRA_DEBUG_REMINDER = "debug_reminder"
    }
}
