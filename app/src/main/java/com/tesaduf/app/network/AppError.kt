package com.tesaduf.app.network

import androidx.annotation.StringRes
import com.tesaduf.app.R
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Everything that can go wrong, reduced to what the UI needs to say. Never shows raw exceptions. */
sealed class AppError(@param:StringRes val messageRes: Int) {
    data object NoInternet : AppError(R.string.error_no_internet)
    data object Timeout : AppError(R.string.error_timeout)
    data object Unauthorized : AppError(R.string.error_session)
    data object NotConfigured : AppError(R.string.error_not_configured)
    data object Suspended : AppError(R.string.error_suspended)
    data object MatchNotFound : AppError(R.string.error_match_not_found)
    data object MatchClosed : AppError(R.string.error_match_closed)
    data object RateLimited : AppError(R.string.error_rate_limited)
    data object Invalid : AppError(R.string.error_invalid)
    data object AccessoryLocked : AppError(R.string.error_accessory_locked)
    data class Server(val code: String) : AppError(R.string.error_server)

    val isConnectivity: Boolean get() = this is NoInternet || this is Timeout

    companion object {
        fun fromCode(code: String?): AppError = when (code) {
            "NOT_AUTHENTICATED", "TOKEN_INVALID", "PROFILE_NOT_FOUND" -> Unauthorized
            "ACCOUNT_SUSPENDED" -> Suspended
            "MATCH_NOT_FOUND", "INVALID_MATCH_ID" -> MatchNotFound
            "MATCH_NOT_OPEN", "MATCH_NOT_EXPIRED" -> MatchClosed
            "RATE_LIMITED" -> RateLimited
            "ACCESSORY_LOCKED" -> AccessoryLocked
            "INVALID_MESSAGE", "INVALID_CLIENT_ID", "INVALID_DECISION", "INVALID_REASON",
            "INVALID_DETAILS", "INVALID_JSON", "INVALID_ACTION", "INVALID_MODE", "UNSUPPORTED_MODE" -> Invalid
            "SERVER_MISCONFIGURED" -> NotConfigured
            else -> Server(code ?: "UNKNOWN")
        }

        fun fromThrowable(t: Throwable): AppError = when (t) {
            is AppException -> t.error
            is SocketTimeoutException -> Timeout
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> NoInternet
            // OkHttp call timeout surfaces as InterruptedIOException("timeout").
            is InterruptedIOException -> Timeout
            is SSLException -> NoInternet
            else -> Server(t.javaClass.simpleName)
        }
    }
}

class AppException(val error: AppError) : Exception(error.toString())

/** Result of a backend call. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) block(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(block: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(error)
    return this
}
