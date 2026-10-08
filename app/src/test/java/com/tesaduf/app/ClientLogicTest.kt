package com.tesaduf.app

import com.tesaduf.app.data.SessionManager
import com.tesaduf.app.data.SessionStorage
import com.tesaduf.app.data.StoredSession
import com.tesaduf.app.data.SupabaseConfig
import com.tesaduf.app.data.TesadufApi
import com.tesaduf.app.model.MatchStatus
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.network.ServerClock
import com.tesaduf.app.network.TesadufJson
import com.tesaduf.app.network.parseInstantMillis
import com.tesaduf.app.util.formatCountdown
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ClientLogicTest {

    @Test
    fun countdownFormatting() {
        assertEquals("15:00", formatCountdown(15 * 60_000L))
        assertEquals("12:43", formatCountdown((12 * 60 + 43) * 1000L))
        assertEquals("00:01", formatCountdown(400))
        assertEquals("00:00", formatCountdown(-5_000))
    }

    @Test
    fun parsesPostgresAndIsoTimestamps() {
        val pg = parseInstantMillis("2026-10-08T12:00:00.123456+00:00")
        val iso = parseInstantMillis("2026-10-08T12:00:00.123Z")
        assertEquals(pg, iso)
        assertEquals(null, parseInstantMillis("garbage"))
    }

    @Test
    fun serverClockUsesServerTimeNotDeviceTime() {
        var device = 1_000_000L
        val clock = ServerClock { device }
        // Device is 10 minutes behind the server; request took 200 ms.
        val serverNow = device + 10 * 60_000L + 100
        clock.onServerTime(java.time.Instant.ofEpochMilli(serverNow).toString(), device, device + 200)
        device += 200
        assertTrue(kotlin.math.abs(clock.now() - (serverNow + 100)) < 5)
    }

    @Test
    fun errorCodesMapToFriendlyErrors() {
        assertEquals(AppError.MatchNotFound, AppError.fromCode("MATCH_NOT_FOUND"))
        assertEquals(AppError.RateLimited, AppError.fromCode("RATE_LIMITED"))
        assertEquals(AppError.Timeout, AppError.fromThrowable(java.net.SocketTimeoutException()))
        assertEquals(AppError.NoInternet, AppError.fromThrowable(java.net.UnknownHostException()))
        assertEquals(MatchStatus.DESTINY, MatchStatus.from("destiny"))
    }
}

class TesadufApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TesadufApi
    private val storage = InMemoryStorage()

    private class InMemoryStorage : SessionStorage {
        var session: StoredSession? = null
        override fun load() = session
        override fun save(session: StoredSession) { this.session = session }
        override fun clear() { session = null }
    }

    private val authJson = """{"access_token":"tok-1","refresh_token":"ref-1","expires_in":3600}"""
    private val matchJson = """{"id":"11111111-1111-1111-1111-111111111111","status":"active",
        "started_at":"2026-10-08T12:00:00+00:00","expires_at":"2026-10-08T12:15:00+00:00",
        "decision_deadline":"2026-10-08T12:17:00+00:00","partner":{"anonymous_id":"A7K92X4F","avatar":"orb_3","online":true}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val config = SupabaseConfig(server.url("/").toString().trimEnd('/').replace("http://", "https://"), "anon")
        // MockWebServer is plain HTTP; keep the https-only config check but point calls at the mock.
        val http = OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(req.newBuilder().url(req.url.newBuilder().scheme("http").build()).build())
            }
            .build()
        val session = SessionManager(http, config, storage)
        api = TesadufApi(http, config, session, ServerClock())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun signsInAnonymouslyThenCallsFunctionWithBearer() = runTest {
        server.enqueue(MockResponse().setBody(authJson))
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":$matchJson,"server_time":"2026-10-08T12:01:00Z"}"""))

        val result = api.matchStatus("11111111-1111-1111-1111-111111111111")

        assertTrue(result is Outcome.Success)
        assertEquals("A7K92X4F", (result as Outcome.Success).value.partner.anonymousId)
        assertEquals("/auth/v1/signup", server.takeRequest().path)
        val call = server.takeRequest()
        assertEquals("/functions/v1/match-status", call.path)
        assertEquals("Bearer tok-1", call.getHeader("Authorization"))
        assertEquals("tok-1", storage.session?.accessToken)
    }

    @Test
    fun refreshesTokenOnceOn401AndRetries() = runTest {
        storage.session = StoredSession("old", "ref-0", Long.MAX_VALUE)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"ok":false,"error":{"code":"TOKEN_INVALID"}}"""))
        server.enqueue(MockResponse().setBody(authJson))
        server.enqueue(MockResponse().setBody("""{"ok":true,"data":$matchJson}"""))

        val result = api.matchStatus("11111111-1111-1111-1111-111111111111")

        assertTrue(result is Outcome.Success)
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
        assertTrue(server.takeRequest().path!!.startsWith("/auth/v1/token?grant_type=refresh_token"))
        assertEquals("Bearer tok-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun serverErrorCodeBecomesFriendlyError() = runTest {
        storage.session = StoredSession("t", "r", Long.MAX_VALUE)
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"ok":false,"error":{"code":"MATCH_NOT_OPEN"}}"""))
        val result = api.sendMessage("11111111-1111-1111-1111-111111111111", "selam", "22222222-2222-2222-2222-222222222222")
        assertEquals(Outcome.Failure(AppError.MatchClosed), result)
    }

    @Test
    fun timeoutNeverCrashesAndMapsToTimeout() = runTest {
        storage.session = StoredSession("t", "r", Long.MAX_VALUE)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = api.heartbeat(null)
        assertEquals(Outcome.Failure(AppError.Timeout), result)
    }

    @Test
    fun malformedResponseDoesNotCrash() = runTest {
        storage.session = StoredSession("t", "r", Long.MAX_VALUE)
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        val result = api.myChats()
        assertTrue(result is Outcome.Failure && result.error is AppError.Server)
    }

    @Test
    fun envelopeParsesHistory() {
        val json = """[{"id":"x","status":"destiny","started_at":"2026-10-08T12:00:00+00:00","is_destiny":true,
            "partner":{"anonymous_id":"QWERTY23","avatar":"orb_1"},"last_message":{"body":"hey","mine":true,"created_at":"2026-10-08T12:00:00+00:00"}}]"""
        val list = TesadufJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(com.tesaduf.app.model.ChatSummary.serializer()), json,
        )
        assertTrue(list.single().canOpen && list.single().isDestiny)
    }
}
