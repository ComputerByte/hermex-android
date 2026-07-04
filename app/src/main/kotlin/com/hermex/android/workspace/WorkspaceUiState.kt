package com.hermex.android.workspace

import com.hermex.android.core.network.dto.WorkspaceEntry

/** `"."` is the server's own root-path convention (matches the verified iOS `FileBrowserViewModel`
 * -- it always sends `path="."` for the top of a session's workspace, never an omitted/null path). */
const val WORKSPACE_ROOT_PATH = "."

data class WorkspaceUiState(
    val isLoading: Boolean = true,
    val currentPath: String = WORKSPACE_ROOT_PATH,
    val entries: List<WorkspaceEntry> = emptyList(),
    /** Directory-listing/navigation error only -- a failed file open lives in [selectedFile]
     * instead, so opening a file that fails to load never clobbers this. */
    val errorMessage: String? = null,
    /** Null while showing the directory listing; set while a file is open. Going "back" from the
     * file viewer is just clearing this back to null -- the directory underneath was never
     * touched, so there's nothing to reload. */
    val selectedFile: FileViewState? = null,
) {
    val isAtRoot: Boolean get() = currentPath == WORKSPACE_ROOT_PATH
}

data class FileViewState(
    val path: String,
    val name: String,
    val isLoading: Boolean = true,
    val content: WorkspaceFileContent? = null,
    /** Set only for an actual fetch failure (network/HTTP/server-reported error) -- never set for
     * an [WorkspaceFileContent.Unavailable] classification, which isn't a failure to recover from,
     * just a "won't show this as text" decision. [WorkspaceViewModel.retryOpenFile] uses this
     * distinction to know whether retrying even makes sense. */
    val errorMessage: String? = null,
)

sealed interface WorkspaceFileContent {
    data class Text(val text: String, val truncated: Boolean = false) : WorkspaceFileContent
    data class Unavailable(val reason: FileUnavailableReason, val message: String) : WorkspaceFileContent
}

enum class FileUnavailableReason {
    /** Known binary/image extension (client-side gate, never fetched) or the server's own
     * `binary: true` flag on an otherwise-fetched [com.hermex.android.core.network.dto.FileResponse]. */
    UNSUPPORTED_TYPE,
    /** [WorkspaceEntry.size] exceeded the client-side preview cap before any fetch was attempted --
     * see [WorkspaceViewModel]'s size constant and its "not a server contract" caveat. */
    TOO_LARGE,
    /** The server responded with neither `content` nor `error` -- decodes fine (tolerant decoding),
     * but there's nothing to show. */
    NO_CONTENT,
}
