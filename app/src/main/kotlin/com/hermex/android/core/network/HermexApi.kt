package com.hermex.android.core.network

import com.hermex.android.core.network.dto.AuthStatusResponse
import com.hermex.android.core.network.dto.ChatCancelResponse
import com.hermex.android.core.network.dto.ChatStartRequest
import com.hermex.android.core.network.dto.ChatStartResponse
import com.hermex.android.core.network.dto.EmptyRequestBody
import com.hermex.android.core.network.dto.HealthResponse
import com.hermex.android.core.network.dto.LoginRequest
import com.hermex.android.core.network.dto.LoginResponse
import com.hermex.android.core.network.dto.NewSessionRequest
import com.hermex.android.core.network.dto.SessionResponse
import com.hermex.android.core.network.dto.SessionsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * All REST endpoints for the MVP. The chat *stream* itself (`GET /api/chat/stream`) is
 * deliberately NOT here -- SSE bypasses Retrofit entirely; see [SseClient] and [chatStreamUrl].
 * Every call should be wrapped in [safeApiCall] at the call site.
 */
interface HermexApi {
    @GET("/health")
    suspend fun health(): HealthResponse

    @GET("/api/auth/status")
    suspend fun authStatus(): AuthStatusResponse

    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("/api/auth/logout")
    suspend fun logout(@Body body: EmptyRequestBody = EmptyRequestBody): LoginResponse

    @GET("/api/sessions")
    suspend fun sessions(): SessionsResponse

    @GET("/api/session")
    suspend fun session(
        @Query("session_id") sessionId: String,
        @Query("messages") messages: Int = 1,
        @Query("msg_limit") msgLimit: Int? = 50,
    ): SessionResponse

    @POST("/api/session/new")
    suspend fun newSession(@Body body: NewSessionRequest): SessionResponse

    @POST("/api/chat/start")
    suspend fun chatStart(@Body body: ChatStartRequest): ChatStartResponse

    @GET("/api/chat/cancel")
    suspend fun chatCancel(@Query("stream_id") streamId: String): ChatCancelResponse
}
