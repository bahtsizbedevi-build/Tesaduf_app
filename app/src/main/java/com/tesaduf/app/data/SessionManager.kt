package com.tesaduf.app.data

import android.content.Context
import androidx.core.content.edit
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.AppException
import com.tesaduf.app.network.HttpResult
import com.tesaduf.app.network.JsonMediaType
import com.tesaduf.app.network.TesadufJson
import com.tesaduf.app.network.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    /** Device epoch millis when the access token expires. */
    val expiresAtMs: Long,
)

interface SessionStorage {
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}

/** App-private SharedPreferences, excluded from backup (see data_extraction_rules.xml). */
class PrefsSessionStorage(context: Context) : SessionStorage {
    private val prefs = context.applicationContext.getSharedPreferences("tesaduf_session", Context.MODE_PRIVATE)

    override fun load(): StoredSession? = prefs.getString(KEY, null)?.let {
        runCatching { TesadufJson.decodeFromString(StoredSession.serializer(), it) }.getOrNull()
    }

    override fun save(session: StoredSession) {
        prefs.edit { putString(KEY, TesadufJson.encodeToString(StoredSession.serializer(), session)) }
    }

    override fun clear() {
        prefs.edit { remove(KEY) }
    }

    private companion object {
        const val KEY = "session_v1"
    }
}

@Serializable
private data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

/**
 * Supabase anonymous auth (GoTrue REST). Creates the anonymous user once and keeps it
 * alive with refresh tokens, so the anonymous id stays the same across app restarts.
 * All token work is serialized by a mutex: parallel requests never refresh twice.
 */
class SessionManager(
    private val http: OkHttpClient,
    private val config: SupabaseConfig,
    private val storage: SessionStorage,
    private val deviceNow: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: StoredSession? = null

    suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val current = cached ?: storage.load()?.also { cached = it }
        val session = when {
            current == null -> signInAnonymously()
            forceRefresh || current.expiresAtMs - REFRESH_MARGIN_MS <= deviceNow() -> refreshOrRecover(current)
            else -> current
        }
        session.accessToken
    }

    private suspend fun refreshOrRecover(current: StoredSession): StoredSession {
        val result = post(
            "${config.url}/auth/v1/token?grant_type=refresh_token",
            """{"refresh_token":${TesadufJson.encodeToString(String.serializer(), current.refreshToken)}}""",
        )
        return when {
            result.code in 200..299 -> persist(result)
            // Refresh token revoked/expired for good: the anonymous identity is gone,
            // start a fresh one rather than locking the user out.
            result.code == 400 || result.code == 401 || result.code == 403 -> {
                storage.clear()
                cached = null
                signInAnonymously()
            }
            else -> throw AppException(AppError.Server("AUTH_${result.code}"))
        }
    }

    private suspend fun signInAnonymously(): StoredSession {
        val result = post("${config.url}/auth/v1/signup", "{}")
        if (result.code !in 200..299) {
            // 422 => anonymous sign-ins are disabled in the Supabase project.
            throw AppException(if (result.code == 422) AppError.NotConfigured else AppError.Server("AUTH_${result.code}"))
        }
        return persist(result)
    }

    private fun persist(result: HttpResult): StoredSession {
        val auth = runCatching { TesadufJson.decodeFromString(AuthResponse.serializer(), result.body) }
            .getOrElse { throw AppException(AppError.Server("AUTH_PARSE")) }
        val session = StoredSession(
            accessToken = auth.accessToken,
            refreshToken = auth.refreshToken,
            expiresAtMs = deviceNow() + auth.expiresIn * 1000,
        )
        storage.save(session)
        cached = session
        return session
    }

    private suspend fun post(url: String, json: String): HttpResult {
        if (!config.isConfigured) throw AppException(AppError.NotConfigured)
        val request = Request.Builder()
            .url(url)
            .header("apikey", config.anonKey)
            .post(json.toRequestBody(JsonMediaType))
            .build()
        return http.newCall(request).await()
    }

    private companion object {
        const val REFRESH_MARGIN_MS = 60_000L
    }
}
