package com.hermex.android.core.network

import com.hermex.android.core.network.dto.AuthStatusResponse
import com.hermex.android.core.network.dto.ChatCancelResponse
import com.hermex.android.core.network.dto.ChatStartRequest
import com.hermex.android.core.network.dto.ChatStartResponse
import com.hermex.android.core.network.dto.CronJobIdRequest
import com.hermex.android.core.network.dto.CronJobsResponse
import com.hermex.android.core.network.dto.CronMutationResponse
import com.hermex.android.core.network.dto.CronOutputResponse
import com.hermex.android.core.network.dto.CronStatusResponse
import com.hermex.android.core.network.dto.EmptyRequestBody
import com.hermex.android.core.network.dto.HealthResponse
import com.hermex.android.core.network.dto.InsightsResponse
import com.hermex.android.core.network.dto.LoginRequest
import com.hermex.android.core.network.dto.LoginResponse
import com.hermex.android.core.network.dto.CreateProjectRequest
import com.hermex.android.core.network.dto.DefaultModelRequest
import com.hermex.android.core.network.dto.DefaultModelResponse
import com.hermex.android.core.network.dto.MemoryResponse
import com.hermex.android.core.network.dto.ModelsLiveResponse
import com.hermex.android.core.network.dto.ModelsResponse
import com.hermex.android.core.network.dto.NewSessionRequest
import com.hermex.android.core.network.dto.ProfileSwitchRequest
import com.hermex.android.core.network.dto.ProfileSwitchResponse
import com.hermex.android.core.network.dto.ProfilesResponse
import com.hermex.android.core.network.dto.ProjectIdRequest
import com.hermex.android.core.network.dto.ProjectMutationResponse
import com.hermex.android.core.network.dto.ProjectsResponse
import com.hermex.android.core.network.dto.RenameProjectRequest
import com.hermex.android.core.network.dto.ServerSettingsResponse
import com.hermex.android.core.network.dto.SessionResponse
import com.hermex.android.core.network.dto.SessionsResponse
import com.hermex.android.core.network.dto.SkillDetailResponse
import com.hermex.android.core.network.dto.SkillsResponse
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

    @GET("/api/skills")
    suspend fun skills(): SkillsResponse

    @GET("/api/skills/content")
    suspend fun skillContent(
        @Query("name") name: String,
        @Query("file") file: String? = null,
    ): SkillDetailResponse

    @GET("/api/memory")
    suspend fun memory(): MemoryResponse

    @GET("/api/crons")
    suspend fun crons(): CronJobsResponse

    @GET("/api/crons/status")
    suspend fun cronStatus(@Query("job_id") jobId: String): CronStatusResponse

    @GET("/api/crons/output")
    suspend fun cronOutput(
        @Query("job_id") jobId: String,
        @Query("limit") limit: Int? = 5,
    ): CronOutputResponse

    @POST("/api/crons/run")
    suspend fun cronRun(@Body body: CronJobIdRequest): CronMutationResponse

    @POST("/api/crons/pause")
    suspend fun cronPause(@Body body: CronJobIdRequest): CronMutationResponse

    @POST("/api/crons/resume")
    suspend fun cronResume(@Body body: CronJobIdRequest): CronMutationResponse

    @POST("/api/crons/delete")
    suspend fun cronDelete(@Body body: CronJobIdRequest): CronMutationResponse

    @GET("/api/profiles")
    suspend fun profiles(): ProfilesResponse

    @POST("/api/profile/switch")
    suspend fun switchProfile(@Body body: ProfileSwitchRequest): ProfileSwitchResponse

    @GET("/api/projects")
    suspend fun projects(): ProjectsResponse

    @POST("/api/projects/create")
    suspend fun createProject(@Body body: CreateProjectRequest): ProjectMutationResponse

    @POST("/api/projects/rename")
    suspend fun renameProject(@Body body: RenameProjectRequest): ProjectMutationResponse

    @POST("/api/projects/delete")
    suspend fun deleteProject(@Body body: ProjectIdRequest): ProjectMutationResponse

    @GET("/api/insights")
    suspend fun insights(@Query("days") days: Int): InsightsResponse

    @GET("/api/settings")
    suspend fun serverSettings(): ServerSettingsResponse

    @GET("/api/models")
    suspend fun models(): ModelsResponse

    @GET("/api/models/live")
    suspend fun modelsLive(): ModelsLiveResponse

    @POST("/api/default-model")
    suspend fun setDefaultModel(@Body body: DefaultModelRequest): DefaultModelResponse
}
