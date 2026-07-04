package com.hermex.android.workspace

import com.hermex.android.core.network.dto.WorkspaceEntry
import com.hermex.android.core.network.dto.GitFileStatus

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
    /** Client-side search/filter text. Narrowed server-side `entries` are displayed without
     * modifying the original list -- the full listing stays intact so clearing the filter
     * restores it immediately. */
    val searchQuery: String = "",
    /** Git status for the current workspace. Null until loaded; non-null even for
     * non-git workspaces (where [GitState.isGit] will be false). */
    val gitState: GitState? = null,
    /** Non-null when a create-file or create-folder dialog is active. */
    val createDialog: CreateDialogState? = null,
    /** Non-null when a rename dialog is active. */
    val renameDialog: RenameDialogState? = null,
    /** Non-null when a delete confirmation dialog is active. */
    val deleteDialog: DeleteDialogState? = null,
) {
    val isAtRoot: Boolean get() = currentPath == WORKSPACE_ROOT_PATH
}

/** Dialog state for creating a file or folder inside the workspace. */
data class CreateDialogState(
    val mode: CreateMode,
    val name: String = "",
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
) {
    val isValid: Boolean
        get() = getValidationError() == null

    fun getValidationError(): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Name cannot be empty."
        if (trimmed.contains("/") || trimmed.contains("\\")) return "Name cannot contain '/' or '\\'."
        if (trimmed == "." || trimmed == "..") return "Name cannot be '.' or '..'."
        if (trimmed.startsWith("..")) return "Name cannot start with '..'."
        if (trimmed == ".git") return "Name '.git' is reserved."
        return null
    }
}

enum class CreateMode { FILE, FOLDER }

/** Dialog state for renaming a file or folder inside the workspace. */
data class RenameDialogState(
    val targetPath: String,
    val originalName: String,
    val name: String = originalName,
    val isRenaming: Boolean = false,
    val errorMessage: String? = null,
) {
    val isUnchanged: Boolean get() = name.trim() == originalName

    val isValid: Boolean
        get() = getValidationError() == null

    fun getValidationError(): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Name cannot be empty."
        if (trimmed.contains("/") || trimmed.contains("\\")) return "Name cannot contain '/' or '\\'."
        if (trimmed == "." || trimmed == "..") return "Name cannot be '.' or '..'."
        if (trimmed.startsWith("..")) return "Name cannot start with '..'."
        if (trimmed == ".git") return "Name '.git' is reserved."
        if (trimmed == originalName && isUnchanged) return "Name is unchanged."
        return null
    }
}

/** Dialog state for deleting a file (two-step confirmation). */
data class DeleteDialogState(
    val targetPath: String,
    val targetName: String,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
)

data class GitState(
    val isGit: Boolean = false,
    val branch: String? = null,
    val commit: String? = null,
    val changedFileCount: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: List<GitFileStatus> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** A file currently being viewed as a diff. Non-null means the diff viewer is open. */
    val selectedDiff: DiffViewState? = null,
)

data class DiffViewState(
    val path: String,
    val diff: String = "",
    val binary: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

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
    /** True when the user is editing the file content. */
    val isEditing: Boolean = false,
    /** Current edited content while [isEditing] is true. Is sourced from the original text
     * when editing starts and updated as the user types. */
    val editedContent: String = "",
    /** True while a save request is in flight. */
    val isSaving: Boolean = false,
    /** Non-null when the most recent save attempt failed. */
    val saveError: String? = null,
) {
    val hasUnsavedChanges: Boolean
        get() = isEditing && content is WorkspaceFileContent.Text && editedContent != content.text
}

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
