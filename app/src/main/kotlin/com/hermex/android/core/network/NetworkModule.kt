package com.hermex.android.core.network

import com.hermex.android.core.storage.CookieStore
import com.hermex.android.core.util.HermexLog
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit

/**
 * Wires the shared cookie jar and OkHttp/Retrofit clients used by [HermexApi].
 *
 * The base URL isn't known until the user enters one, so this does NOT own a single Retrofit
 * instance -- [createApi] builds a fresh one per server URL, mirroring the iOS client's
 * `clientFactory: (URL) -> AuthAPIClient` pattern (onboarding's "test connection" probes a
 * candidate URL before it's ever persisted). All instances share the same cookie jar and
 * interceptor, since cookies are already scoped by request URL.
 *
 * CSRF note: the server validates `Origin`/`Referer` against `Host` on POSTs, and treats their
 * *absence* as a trusted non-browser client (see API_CONTRACT.md). OkHttp/Retrofit never send
 * these headers unless an interceptor explicitly adds them -- do not add one here. This is
 * load-bearing for login/session calls working at all.
 */
class NetworkModule(
    cookieStore: CookieStore,
    /** Fired when an authenticated (non-login) call gets a 401 -- the session cookie is stale. */
    private val onUnauthorized: () -> Unit,
) {
    val cookieJar = PersistentCookieJar(cookieStore, HermexJson)

    private val unauthorizedInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        // A wrong password on /api/auth/login is just a failed login attempt, not a stale
        // session -- routing it through onUnauthorized() would incorrectly clear valid auth
        // state (e.g. mid-login on an already-configured server).
        if (response.code == 401 && !request.url.encodedPath.endsWith("/api/auth/login")) {
            HermexLog.w("Auth", "401 on ${request.url.encodedPath} -- treating session as expired")
            onUnauthorized()
        }
        response
    }

    /**
     * BASIC level only -- method, URL, response code, and duration. Never HEADERS or BODY: the
     * login request body carries the password, and every request's headers/cookie carry the
     * session token. Logging either would put a secret in logcat, which is readable by other
     * apps on a rooted device and easy to paste into a bug report by accident.
     */
    private val loggingInterceptor = HttpLoggingInterceptor { message -> HermexLog.d("Http", message) }
        .apply { level = HttpLoggingInterceptor.Level.BASIC }

    private fun baseClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(unauthorizedInterceptor)

    /** REST calls: normal bounded timeouts. */
    val restClient: OkHttpClient = baseClientBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * SSE stream client. `readTimeout` MUST be 0 (no timeout): the server holds the connection
     * open for the whole turn and sends `:`-heartbeat comments every ~30s, but a turn can run
     * far longer between visible tokens. The default ~10s read timeout would kill the stream
     * long before `done`/`stream_end`. Built here so the Phase 4 SseClient can reuse it.
     */
    val sseClient: OkHttpClient = baseClientBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val retrofitConverterFactory = HermexJson.asConverterFactory("application/json".toMediaType())

    /** Builds a [HermexApi] bound to [serverBaseUrl] (e.g. "https://hermes.example.com"). */
    fun createApi(serverBaseUrl: String): HermexApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(serverBaseUrl))
            .client(restClient)
            .addConverterFactory(retrofitConverterFactory)
            .build()
        return retrofit.create(HermexApi::class.java)
    }

    private fun normalizeBaseUrl(url: String): String = if (url.endsWith("/")) url else "$url/"
}
