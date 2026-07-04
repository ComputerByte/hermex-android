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
import com.hermex.android.core.network.dto.CreateDirRequest
import com.hermex.android.core.network.dto.CreateFileRequest
import com.hermex.android.core.network.dto.DirectoryListResponse
import com.hermex.android.core.network.dto.EmptyRequestBody
import com.hermex.android.core.network.dto.FileSaveRequest
import com.hermex.android.core.network.dto.FileSaveResponse
import com.hermex.android.core.network.dto.FileResponse
import com.hermex.android.core.network.dto.RenameFileRequest
import com.hermex.android.core.network.dto.GitBranchesResponse
import com.hermex.android.core.network.dto.GitBranchesWrapper
import com.hermex.android.core.network.dto.GitDiffResponse
import com.hermex.android.core.network.dto.GitDiffWrapper
import com.hermex.android.core.network.dto.GitStatusResponse
import com.hermex.android.core.network.dto.GitStatusWrapper
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
import com.hermex.android.core.network.dto.UpdateSessionRequest
import com.hermex.android.core.network.dto.UploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Streaming

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

    @POST("/api/session/update")
    suspend fun updateSession(@Body body: UpdateSessionRequest): SessionResponse

    @POST("/api/chat/start")
    suspend fun chatStart(@Body body: ChatStartRequest): ChatStartResponse

    @GET("/api/chat/cancel")
    suspend fun chatCancel(@Query("stream_id") streamId: String): ChatCancelResponse

    // Workspace/file browser (read-only; no create/rename/delete/upload endpoints in this MVP).
    // Both are session-scoped -- there is no session-independent directory browse.
    @GET("/api/list")
    suspend fun directoryList(
        @Query("session_id") sessionId: String,
        @Query("path") path: String? = null,
    ): DirectoryListResponse

    @GET("/api/file")
    suspend fun workspaceFile(
        @Query("session_id") sessionId: String,
        @Query("path") path: String,
    ): FileResponse

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

    /** Multipart fields verified against `hermes-webui`'s `handle_upload` (V5 Phase 5 recon):
     * `session_id` (text) and `file` (binary, filename required). Not yet called from any send
     * flow -- see [MessageAttachment]. */
    @Multipart
    @POST("/api/upload")
    suspend fun uploadAttachment(
        @Part("session_id") sessionId: RequestBody,
        @Part file: MultipartBody.Part,
    ): UploadResponse

    /** Raw bytes for a workspace file or a session's uploaded attachment (the server tries the
     * workspace path first, then falls back to the session's attachment inbox -- V5 Phase 5
     * recon). `@Streaming` avoids buffering the whole body into memory before Retrofit returns.
     * Not yet called from any preview UI. */
    @Streaming
    @GET("/api/file/raw")
    suspend fun fileRaw(
        @Query("session_id") sessionId: String,
        @Query("path") path: String,
        @Query("download") download: Int? = null,
        @Query("inline") inline: Int? = null,
    ): ResponseBody

    @POST("/api/file/save")
    suspend fun saveFile(@Body body: FileSaveRequest): FileSaveResponse

    @POST("/api/file/create")
    suspend fun createFile(@Body body: CreateFileRequest): FileSaveResponse

    @POST("/api/file/create-dir")
    suspend fun createDir(@Body body: CreateDirRequest): FileSaveResponse

    @POST("/api/file/rename")
    suspend fun renameFile(@Body body: RenameFileRequest): FileSaveResponse

    // Git endpoints (read-only -- no commit/pull/discard/checkout in v0.7.3).
    @GET("/api/git/status")
    suspend fun gitStatus(
        @Query("session_id") sessionId: String,
        @Query("path") path: String? = null,
    ): GitStatusWrapper

    @GET("/api/git/diff")
    suspend fun gitDiff(
        @Query("session_id") sessionId: String,
        @Query("path") path: String,
        @Query("kind") kind: String = "unstaged",
    ): GitDiffWrapper

    @GET("/api/git/branches")
    suspend fun gitBranches(
        @Query("session_id") sessionId: String,
    ): GitBranchesWrapper
}
