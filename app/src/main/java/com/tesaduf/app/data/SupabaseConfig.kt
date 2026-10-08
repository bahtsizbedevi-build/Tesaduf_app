package com.tesaduf.app.data

import com.tesaduf.app.BuildConfig

/** Public client configuration only. The service_role key never ships in the app. */
data class SupabaseConfig(val url: String, val anonKey: String) {
    val isConfigured: Boolean get() = url.startsWith("https://") && anonKey.isNotBlank()

    val functionsUrl: String get() = "$url/functions/v1"

    val realtimeUrl: String
        get() = url.replaceFirst("https://", "wss://") + "/realtime/v1/websocket"

    companion object {
        fun fromBuildConfig() = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    }
}
