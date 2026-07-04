package com.hermex.android.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.auth.AuthRepository
import com.hermex.android.core.network.ApiError
import com.hermex.android.core.network.dto.CreateDirRequest
import com.hermex.android.core.network.dto.CreateFileRequest
import com.hermex.android.core.network.dto.DeleteFileRequest
import com.hermex.android.core.network.dto.FileSaveRequest
import com.hermex.android.core.network.dto.RenameFileRequest
import com.hermex.android.core.network.dto.WorkspaceEntry
import com.hermex.android.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Read-only workspace directory/file browser for one chat session -- `/api/list` and `/api/file`
 * are both session-scoped (there is no session-independent browse), matching the verified iOS
 * `FileBrowserViewModel`/`FilePreviewViewModel`. Directory navigation and the currently-open file
 * (if any) live in the same [WorkspaceUiState] rather than as separate screens/ViewModels, since
 * going back from the file to the directory only needs to clear [WorkspaceUiState.selectedFile] --
 * the underlying listing was never touched and doesn't need reloading.
 *
 * No create/rename/delete/upload -- this ViewModel has no mutation path at all.
 */
class WorkspaceViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        loadRoot()
    }

    fun loadRoot() {
        loadDirectory(WORKSPACE_ROOT_PATH)
    }

    /** Re-issues the request for whatever directory is currently showing (or failed to show). */
    fun retryDirectory() = loadDirectory(_uiState.value.currentPath)

    fun navigateInto(entry: WorkspaceEntry) {
        val path = entry.path ?: return
        if (!entry.isBrowsableDirectory) return
        loadDirectory(path)
    }

    /** No-ops at root -- "do not navigate above root" is satisfied by there being no parent path
     * to request in the first place. */
    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current == WORKSPACE_ROOT_PATH) return
        loadDirectory(parentPathOf(current))
    }

    /** Re-fetches the current directory listing without navigating. Useful after a failed load
     * or to pick up external changes. Preserves [searchQuery] so the filter is reapplied
     * after the fresh listing arrives. */
    fun refreshDirectory() {
        loadDirectory(_uiState.value.currentPath, preserveSearch = true)
    }

    /** Updates the client-side search/filter text. The underlying [entries] list is never
     * modified -- clearing the query restores the full listing immediately. */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ── Git (read-only) ──

    fun loadGitStatus(path: String? = null) {
        val api = authRepository.apiForActiveServer()
        if (api == null) return
        _uiState.update { it.copy(gitState = GitState(isLoading = true)) }
        viewModelScope.launch {
            try {
                val response = safeApiCall { api.gitStatus(sessionId, path) }
                val status = response.git ?: return@launch
                _uiState.update {
                    it.copy(gitState = GitState(
                        isGit = status.is_git == true,
                        branch = status.branch,
                        commit = status.commit,
                        changedFileCount = status.totals?.changed ?: 0,
                        additions = status.totals?.additions ?: 0,
                        deletions = status.totals?.deletions ?: 0,
                        files = status.files.orEmpty(),
                        isLoading = false,
                        errorMessage = response.error ?: status.error,
                    ))
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(gitState = GitState(isLoading = false, errorMessage = "Could not load git status.")) }
            }
        }
    }

    fun openGitDiff(file: com.hermex.android.core.network.dto.GitFileStatus) {
        val path = file.path ?: return
        _uiState.update { state ->
            state.copy(gitState = state.gitState?.copy(selectedDiff = DiffViewState(path = path, isLoading = true)))
        }
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { state -> state.copy(gitState = state.gitState?.copy(selectedDiff = DiffViewState(path = path, errorMessage = "Not signed in.", isLoading = false))) }
            return
        }
        viewModelScope.launch {
            try {
                val wrapped = safeApiCall { api.gitDiff(sessionId, path) }
                val response = wrapped.diff
                _uiState.update { state ->
                    when {
                        response?.error != null -> state.copy(gitState = state.gitState?.copy(selectedDiff = DiffViewState(path = path, errorMessage = response.error, isLoading = false)))
                        wrapped.error != null -> state.copy(gitState = state.gitState?.copy(selectedDiff = DiffViewState(path = path, errorMessage = wrapped.error, isLoading = false)))
                        response?.binary == true -> state.copy(gitState = state.gitState?.copy(selectedDiff = DiffViewState(path = path, diff = "", binary = true, isLoading = false)))
                        else -> state.copy(gitState = state.gitState?.copy(selectedDiff = DiffViewState(path = path, diff = response?.diff ?: "", isLoading = false)))
                    }
                }
            } catch (e: ApiError) {
                _uiState.update { state -> state.copy(gitState = state.gitState?.copy(selectedDiff = DiffViewState(path = path, errorMessage = e.message ?: "Could not load diff.", isLoading = false))) }
            }
        }
    }

    fun closeGitDiff() {
        _uiState.update { state -> state.copy(gitState = state.gitState?.copy(selectedDiff = null)) }
    }

    private fun loadDirectory(path: String, preserveSearch: Boolean = false) {
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Not signed in.") }
            return
        }
        viewModelScope.launch {
            // Also closes any open file view -- it belongs to whatever listing was showing
            // before this navigation and would otherwise be left stale over the new directory.
            _uiState.update { it.copy(isLoading = true, errorMessage = null, selectedFile = null) }
            try {
                val response = safeApiCall { api.directoryList(sessionId, path) }
                if (response.error != null) {
                    // Deliberately leaves currentPath/entries untouched -- a failed navigation
                    // should leave the user exactly where they were, not on a half-updated path.
                    _uiState.update { it.copy(isLoading = false, errorMessage = response.error) }
                } else {
                    val clearedSearch = if (preserveSearch) _uiState.value.searchQuery else ""
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentPath = response.path ?: path,
                            entries = response.entries.orEmpty(),
                            searchQuery = clearedSearch,
                        )
                    }
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Could not load this folder.") }
            }
        }
    }

    fun openFile(entry: WorkspaceEntry) {
        val path = entry.path ?: return
        if (entry.isBrowsableDirectory) return
        val name = entry.name ?: path.substringAfterLast('/')

        if (isUnsupportedExtension(path)) {
            showUnavailable(path, name, FileUnavailableReason.UNSUPPORTED_TYPE, "Preview is not available for this file type.")
            return
        }
        if ((entry.size ?: 0L) > MAX_PREVIEWABLE_FILE_BYTES) {
            showUnavailable(path, name, FileUnavailableReason.TOO_LARGE, "This file is too large to preview.")
            return
        }
        fetchFile(path, name)
    }

    /** Re-issues the request for whichever file is currently open. No-ops if nothing is open, or
     * if what's open is an [WorkspaceFileContent.Unavailable] classification rather than an actual
     * fetch failure -- retrying "this file type isn't supported" would mean re-fetching a file
     * this client deliberately chose never to request as text. */
    fun retryOpenFile() {
        val current = _uiState.value.selectedFile?.takeIf { it.errorMessage != null } ?: return
        fetchFile(current.path, current.name)
    }

    /** Back from the file viewer to the directory listing underneath -- no reload needed. */
    fun closeFile() {
        _uiState.update { it.copy(selectedFile = null) }
    }

    // ── Edit / Save (text files only) ──

    /** Enters edit mode for the currently open file. No-ops if the file isn't text content. */
    fun startEditing() {
        val file = _uiState.value.selectedFile ?: return
        val text = (file.content as? WorkspaceFileContent.Text)?.text ?: return
        _uiState.update {
            it.copy(selectedFile = file.copy(isEditing = true, editedContent = text, saveError = null))
        }
    }

    /** Updates the edited content as the user types. */
    fun updateEditedContent(content: String) {
        val file = _uiState.value.selectedFile ?: return
        if (!file.isEditing) return
        _uiState.update {
            it.copy(selectedFile = file.copy(editedContent = content, saveError = null))
        }
    }

    /** Discards unsaved changes and exits edit mode without saving. */
    fun cancelEditing() {
        val file = _uiState.value.selectedFile ?: return
        if (!file.isEditing) return
        _uiState.update {
            it.copy(selectedFile = file.copy(isEditing = false, editedContent = "", isSaving = false, saveError = null))
        }
    }

    /** Saves the current edited content via [HermexApi.saveFile]. On success, reloads the file
     * content. On failure, remains in edit mode with the edited text preserved. */
    fun saveFile() {
        val file = _uiState.value.selectedFile ?: return
        if (!file.isEditing || file.isSaving) return
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(selectedFile = file.copy(saveError = "Not signed in.")) }
            return
        }
        val path = file.path
        val content = file.editedContent
        _uiState.update { it.copy(selectedFile = file.copy(isSaving = true, saveError = null)) }
        viewModelScope.launch {
            try {
                val request = FileSaveRequest(session_id = sessionId, path = path, content = content)
                val response = safeApiCall { api.saveFile(request) }
                if (response.error != null) {
                    _uiState.update { state ->
                        val current = state.selectedFile
                        if (current?.path != path) return@update state
                        state.copy(selectedFile = current.copy(isSaving = false, saveError = response.error))
                    }
                    return@launch
                }
                // Save succeeded: exit edit mode and reload the file content
                val currentPath = _uiState.value.currentPath
                _uiState.update { state ->
                    val current = state.selectedFile
                    if (current?.path != path) return@update state
                    state.copy(
                        selectedFile = current.copy(
                            isEditing = false, editedContent = "", isSaving = false, saveError = null,
                            isLoading = true, content = null,
                        ),
                    )
                }
                fetchFile(path, file.name)
                loadDirectory(currentPath, preserveSearch = true)
            } catch (e: ApiError) {
                _uiState.update { state ->
                    val current = state.selectedFile
                    if (current?.path != path) return@update state
                    state.copy(selectedFile = current.copy(isSaving = false, saveError = e.message ?: "Could not save file."))
                }
            } catch (_: Exception) {
                _uiState.update { state ->
                    val current = state.selectedFile
                    if (current?.path != path) return@update state
                    state.copy(selectedFile = current.copy(isSaving = false, saveError = "Could not save file."))
                }
            }
        }
    }

    private fun showUnavailable(path: String, name: String, reason: FileUnavailableReason, message: String) {
        _uiState.update {
            it.copy(selectedFile = FileViewState(path = path, name = name, isLoading = false, content = WorkspaceFileContent.Unavailable(reason, message)))
        }
    }

    private fun fetchFile(path: String, name: String) {
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(selectedFile = FileViewState(path = path, name = name, isLoading = false, errorMessage = "Not signed in.")) }
            return
        }
        _uiState.update { it.copy(selectedFile = FileViewState(path = path, name = name, isLoading = true)) }
        viewModelScope.launch {
            try {
                val response = safeApiCall { api.workspaceFile(sessionId, path) }
                _uiState.update { state ->
                    // The user may have closed the viewer or opened a different file while this
                    // request was in flight -- a stale result must never clobber whatever (or
                    // nothing) is showing now.
                    val current = state.selectedFile
                    if (current == null || current.path != path) return@update state
                    val updated = when {
                        response.error != null -> current.copy(isLoading = false, errorMessage = response.error)
                        response.binary == true -> current.copy(
                            isLoading = false,
                            content = WorkspaceFileContent.Unavailable(FileUnavailableReason.UNSUPPORTED_TYPE, "This file can't be displayed as text."),
                        )
                        response.content == null -> current.copy(
                            isLoading = false,
                            content = WorkspaceFileContent.Unavailable(FileUnavailableReason.NO_CONTENT, "No content was returned for this file."),
                        )
                        else -> current.copy(
                            isLoading = false,
                            content = WorkspaceFileContent.Text(response.content, truncated = response.truncated == true),
                        )
                    }
                    state.copy(selectedFile = updated)
                }
            } catch (e: ApiError) {
                _uiState.update { state ->
                    val current = state.selectedFile
                    if (current == null || current.path != path) return@update state
                    state.copy(selectedFile = current.copy(isLoading = false, errorMessage = e.message ?: "Could not open this file."))
                }
            }
        }
    }

    fun showCreateFileDialog() {
        _uiState.update { it.copy(createDialog = CreateDialogState(mode = CreateMode.FILE)) }
    }

    fun showCreateFolderDialog() {
        _uiState.update { it.copy(createDialog = CreateDialogState(mode = CreateMode.FOLDER)) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(createDialog = null) }
    }

    fun updateCreateName(name: String) {
        val d = _uiState.value.createDialog ?: return
        _uiState.update { it.copy(createDialog = d.copy(name = name, errorMessage = null)) }
    }

    /** Opens a file by its full server-side path. Creates an ephemeral [WorkspaceEntry] since
     * the server may not return entry metadata from the create endpoint. */
    private fun openFileByName(path: String) {
        val name = path.substringAfterLast('/')
        val entry = WorkspaceEntry(name = name, path = path, type = "file")
        openFile(entry)
    }

    fun confirmCreate() {
        val d = _uiState.value.createDialog ?: return
        val name = d.name.trim()
        if (!d.isValid) {
            _uiState.update { it.copy(createDialog = d.copy(errorMessage = d.getValidationError())) }
            return
        }
        val path = if (_uiState.value.currentPath == WORKSPACE_ROOT_PATH) name else "${_uiState.value.currentPath}/$name"
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(createDialog = d.copy(errorMessage = "Not signed in.")) }
            return
        }
        _uiState.update { it.copy(createDialog = d.copy(isCreating = true, errorMessage = null)) }
        viewModelScope.launch {
            try {
                val response = when (d.mode) {
                    CreateMode.FILE -> safeApiCall {
                        api.createFile(CreateFileRequest(session_id = sessionId, path = path))
                    }
                    CreateMode.FOLDER -> safeApiCall {
                        api.createDir(CreateDirRequest(session_id = sessionId, path = path))
                    }
                }
                if (response.error != null) {
                    _uiState.update { it.copy(createDialog = d.copy(isCreating = false, errorMessage = response.error)) }
                    return@launch
                }
                // Success -- close dialog, refresh directory, open new file if applicable
                _uiState.update { it.copy(createDialog = null) }
                loadDirectory(_uiState.value.currentPath, preserveSearch = true)
                if (d.mode == CreateMode.FILE) {
                    openFileByName(path)
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(createDialog = d.copy(isCreating = false, errorMessage = e.message ?: "Could not create.")) }
            } catch (_: Exception) {
                _uiState.update { it.copy(createDialog = d.copy(isCreating = false, errorMessage = "Could not create.")) }
            }
        }
    }

    // ── Rename ──

    fun showRenameDialog(entry: WorkspaceEntry) {
        val path = entry.path ?: return
        val name = entry.name ?: path.substringAfterLast('/')
        _uiState.update {
            it.copy(renameDialog = RenameDialogState(targetPath = path, originalName = name, name = name))
        }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(renameDialog = null) }
    }

    fun updateRenameName(name: String) {
        val d = _uiState.value.renameDialog ?: return
        _uiState.update { it.copy(renameDialog = d.copy(name = name, errorMessage = null)) }
    }

    fun confirmRename() {
        val d = _uiState.value.renameDialog ?: return
        val newName = d.name.trim()
        if (!d.isValid) {
            _uiState.update { it.copy(renameDialog = d.copy(errorMessage = d.getValidationError())) }
            return
        }
        val currentPath = _uiState.value.currentPath
        val oldPath = d.targetPath
        val newPath = if (currentPath == WORKSPACE_ROOT_PATH) newName else "$currentPath/$newName"
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(renameDialog = d.copy(errorMessage = "Not signed in.")) }
            return
        }
        _uiState.update { it.copy(renameDialog = d.copy(isRenaming = true, errorMessage = null)) }
        viewModelScope.launch {
            try {
                val response = safeApiCall {
                    api.renameFile(RenameFileRequest(session_id = sessionId, path = oldPath, new_path = newPath))
                }
                if (response.error != null) {
                    _uiState.update { it.copy(renameDialog = d.copy(isRenaming = false, errorMessage = response.error)) }
                    return@launch
                }
                // Success
                _uiState.update { it.copy(renameDialog = null) }
                loadDirectory(currentPath, preserveSearch = true)
                // If the renamed item was the currently open file, close it
                val openFile = _uiState.value.selectedFile
                if (openFile?.path == oldPath) {
                    _uiState.update { it.copy(selectedFile = null) }
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(renameDialog = d.copy(isRenaming = false, errorMessage = e.message ?: "Could not rename.")) }
            } catch (_: Exception) {
                _uiState.update { it.copy(renameDialog = d.copy(isRenaming = false, errorMessage = "Could not rename.")) }
            }
        }
    }

    // ── Delete file only ──

    fun showDeleteFileDialog(entry: WorkspaceEntry) {
        if (entry.isBrowsableDirectory) return
        val path = entry.path ?: return
        val name = entry.name ?: path.substringAfterLast('/')
        // Block protected names
        if (name == ".git" || name == ".github") return
        _uiState.update {
            it.copy(deleteDialog = DeleteDialogState(targetPath = path, targetName = name))
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(deleteDialog = null) }
    }

    fun confirmDeleteFile() {
        val d = _uiState.value.deleteDialog ?: return
        val path = d.targetPath
        val api = authRepository.apiForActiveServer()
        if (api == null) {
            _uiState.update { it.copy(deleteDialog = d.copy(errorMessage = "Not signed in.")) }
            return
        }
        _uiState.update { it.copy(deleteDialog = d.copy(isDeleting = true, errorMessage = null)) }
        viewModelScope.launch {
            try {
                val response = safeApiCall {
                    api.deleteFile(DeleteFileRequest(session_id = sessionId, path = path))
                }
                if (response.error != null) {
                    _uiState.update { it.copy(deleteDialog = d.copy(isDeleting = false, errorMessage = response.error)) }
                    return@launch
                }
                // Success
                _uiState.update { it.copy(deleteDialog = null) }
                loadDirectory(_uiState.value.currentPath, preserveSearch = true)
                // If the deleted file was the currently open file, close viewer
                val openFile = _uiState.value.selectedFile
                if (openFile?.path == path) {
                    _uiState.update { it.copy(selectedFile = null) }
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(deleteDialog = d.copy(isDeleting = false, errorMessage = e.message ?: "Could not delete.")) }
            } catch (_: Exception) {
                _uiState.update { it.copy(deleteDialog = d.copy(isDeleting = false, errorMessage = "Could not delete.")) }
            }
        }
    }
}

private fun parentPathOf(path: String): String {
    val parts = path.split("/").filter { it.isNotEmpty() }
    return if (parts.size <= 1) WORKSPACE_ROOT_PATH else parts.dropLast(1).joinToString("/")
}

/** Mirrors the verified iOS `WorkspaceEntry.isBrowsableDirectory` -- some servers only send
 * `type: "dir"`, others only `is_directory`/`is_dir`; treat either as authoritative. */
private val WorkspaceEntry.isBrowsableDirectory: Boolean
    get() = isDirectory == true || type == "dir"

/** Client-side gate so a known-binary (or image) file is never requested as text in the first
 * place -- mirrors the verified iOS `FilePreviewViewModel.isKnownUnsupportedBinaryPath` /
 * `isRasterImagePath` extension lists, merged into one set since this MVP has no image-preview UI
 * either. */
private val UNSUPPORTED_EXTENSIONS = setOf(
    "7z", "a", "aiff", "avi", "bin", "bmp", "bz2", "class", "db", "dmg", "doc",
    "docx", "dylib", "exe", "flac", "gif", "gz", "ico", "jar", "jpeg", "jpg",
    "m4a", "mov", "mp3", "mp4", "o", "pdf", "pkg", "png", "ppt", "pptx", "pyc",
    "rar", "sqlite", "svg", "tar", "tgz", "wav", "webp", "xls", "xlsx", "xz", "zip",
)

private fun isUnsupportedExtension(path: String): Boolean {
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension.isNotEmpty() && extension in UNSUPPORTED_EXTENSIONS
}

/** Client-side-only policy, NOT a server-documented limit -- no upstream contract specifies a max
 * file size for `/api/file` (see the Phase 1 recon/report). Chosen only to keep a single
 * in-memory text blob from becoming unreasonably large; revisit once real server behavior for
 * oversized files is confirmed. */
private const val MAX_PREVIEWABLE_FILE_BYTES = 1_000_000L
