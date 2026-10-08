package com.tesaduf.app

import android.app.Application
import com.tesaduf.app.data.AppPreferences
import com.tesaduf.app.data.NetworkMonitor
import com.tesaduf.app.data.PrefsSessionStorage
import com.tesaduf.app.data.RealtimeClient
import com.tesaduf.app.data.SessionManager
import com.tesaduf.app.data.SupabaseConfig
import com.tesaduf.app.data.TesadufApi
import com.tesaduf.app.network.ServerClock
import com.tesaduf.app.notifications.Reminders
import com.tesaduf.app.network.createHttpClient
import com.tesaduf.app.repository.TesadufRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI: one instance of each long-lived dependency for the whole process. */
class AppContainer(app: Application) {
    private val config = SupabaseConfig.fromBuildConfig()
    private val http = createHttpClient()
    private val clock = ServerClock()
    private val session = SessionManager(http, config, PrefsSessionStorage(app))
    val preferences = AppPreferences(app)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val networkMonitor = NetworkMonitor(app)
    val repository = TesadufRepository(
        api = TesadufApi(http, config, session, clock),
        realtime = RealtimeClient(http, config, session),
        sessionManager = session,
        preferences = preferences,
        clock = clock,
        appScope = appScope,
    )
}

class TesadufApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)
    }
}
