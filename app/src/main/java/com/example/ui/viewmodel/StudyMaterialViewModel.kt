package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.StudyMaterial
import com.example.data.repository.StudyMaterialRepository
import com.example.util.DocumentStorageUtil
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MaterialSlot(val label: String, val typeKey: String?) {
    ALL("All", null),
    LECTURE_NOTES("Lecture Notes", "LECTURE_NOTE"),
    QUESTION_PAPERS("Question Papers", "QUESTION_PAPER"),
    QUESTION_BANKS("Question Banks", "QUESTION_BANK"),
    MY_UPLOADS("My Uploads", null)
}

enum class UserPerspective(val label: String, val badge: String) {
    OWNER("Owner View", "Full Access (Edit/Delete)"),
    ANOTHER_STUDENT("Another Student", "Peer Read / Download Mode")
}

data class FilterState(
    val selectedSlot: MaterialSlot = MaterialSlot.ALL,
    val searchQuery: String = "",
    val selectedSemester: String = "All",
    val selectedCourseCode: String = "All",
    val selectedProfessor: String = "All",
    val selectedTag: String = "All"
)

class StudyMaterialViewModel(
    val repository: StudyMaterialRepository
) : ViewModel() {

    private val currentUserId: String?
        get() = Firebase.auth.currentUser?.uid

    private val _currentPerspective = MutableStateFlow(UserPerspective.OWNER)
    val currentPerspective: StateFlow<UserPerspective> = _currentPerspective.asStateFlow()

    private val _downloadingMaterialId = MutableStateFlow<String?>(null)
    val downloadingMaterialId: StateFlow<String?> = _downloadingMaterialId.asStateFlow()

    private val _downloadedMaterialIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedMaterialIds: StateFlow<Set<String>> = _downloadedMaterialIds.asStateFlow()

    private val _lastDownloadedMaterial = MutableStateFlow<StudyMaterial?>(null)
    val lastDownloadedMaterial: StateFlow<StudyMaterial?> = _lastDownloadedMaterial.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _isOperationLoading = MutableStateFlow(false)
    val isOperationLoading: StateFlow<Boolean> = _isOperationLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var statusDismissJob: Job? = null
    private var downloadBannerDismissJob: Job? = null

    private fun showStatusMessage(message: String, autoDismissMillis: Long? = 3000L) {
        statusDismissJob?.cancel()
        _statusMessage.value = message
        if (autoDismissMillis != null) {
            statusDismissJob = viewModelScope.launch {
                delay(autoDismissMillis)
                if (_statusMessage.value == message) {
                    _statusMessage.value = null
                }
            }
        }
    }

    private fun showDownloadedBanner(material: StudyMaterial) {
        downloadBannerDismissJob?.cancel()
        _lastDownloadedMaterial.value = material
        downloadBannerDismissJob = viewModelScope.launch {
            delay(3000L)
            if (_lastDownloadedMaterial.value?.id == material.id) {
                _lastDownloadedMaterial.value = null
            }
        }
    }

    fun togglePerspective() {
        _currentPerspective.value = if (_currentPerspective.value == UserPerspective.OWNER) {
            UserPerspective.ANOTHER_STUDENT
        } else {
            UserPerspective.OWNER
        }
    }

    fun clearLastDownloaded() {
        downloadBannerDismissJob?.cancel()
        _lastDownloadedMaterial.value = null
    }

    // Combined stream of user materials and shared materials
    private val myMaterials = repository.observeMyMaterials()
    private val sharedMaterials = repository.observeSharedMaterials()

    val allMaterials: StateFlow<List<StudyMaterial>> = combine(
        myMaterials,
        sharedMaterials
    ) { mine, shared ->
        // Distinct by document id
        val map = LinkedHashMap<String, StudyMaterial>()
        mine.forEach { map[it.id] = it }
        shared.forEach { if (!map.containsKey(it.id)) map[it.id] = it }
        map.values.toList().sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val filteredMaterials: StateFlow<List<StudyMaterial>> = combine(
        allMaterials,
        _filterState
    ) { materials, filter ->
        materials.filter { mat ->
            // 1. Slot filter
            val matchesSlot = when (filter.selectedSlot) {
                MaterialSlot.ALL -> true
                MaterialSlot.LECTURE_NOTES -> mat.type == "LECTURE_NOTE"
                MaterialSlot.QUESTION_PAPERS -> mat.type == "QUESTION_PAPER"
                MaterialSlot.QUESTION_BANKS -> mat.type == "QUESTION_BANK"
                MaterialSlot.MY_UPLOADS -> mat.userId == currentUserId
            }
            if (!matchesSlot) return@filter false

            // 2. Semester filter
            if (filter.selectedSemester != "All" && !mat.semester.equals(filter.selectedSemester, ignoreCase = true)) {
                return@filter false
            }

            // 3. Course Code filter
            if (filter.selectedCourseCode != "All" && !mat.courseCode.equals(filter.selectedCourseCode, ignoreCase = true)) {
                return@filter false
            }

            // 4. Professor filter
            if (filter.selectedProfessor != "All" && !mat.professor.equals(filter.selectedProfessor, ignoreCase = true)) {
                return@filter false
            }

            // 5. Tag filter
            if (filter.selectedTag != "All" && !mat.tags.any { it.equals(filter.selectedTag, ignoreCase = true) }) {
                return@filter false
            }

            // 6. Search Query filter
            if (filter.searchQuery.isNotBlank()) {
                val q = filter.searchQuery.trim().lowercase()
                val match = mat.title.lowercase().contains(q) ||
                        mat.subject.lowercase().contains(q) ||
                        mat.courseCode.lowercase().contains(q) ||
                        mat.professor.lowercase().contains(q) ||
                        mat.content.lowercase().contains(q) ||
                        mat.tags.any { it.lowercase().contains(q) }
                if (!match) return@filter false
            }

            true
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    fun setSlot(slot: MaterialSlot) {
        _filterState.value = _filterState.value.copy(selectedSlot = slot)
    }

    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun setSemesterFilter(sem: String) {
        _filterState.value = _filterState.value.copy(selectedSemester = sem)
    }

    fun setCourseCodeFilter(code: String) {
        _filterState.value = _filterState.value.copy(selectedCourseCode = code)
    }

    fun setProfessorFilter(prof: String) {
        _filterState.value = _filterState.value.copy(selectedProfessor = prof)
    }

    fun clearFilters() {
        _filterState.value = FilterState()
    }

    fun clearStatusMessage() {
        statusDismissJob?.cancel()
        _statusMessage.value = null
    }

    fun addStudyMaterial(
        title: String,
        subject: String,
        courseCode: String,
        semester: String,
        professor: String,
        type: String,
        tags: List<String>,
        content: String,
        driveUrl: String,
        isShared: Boolean,
        fileName: String = "",
        localPath: String = "",
        fileSize: Long = 0L,
        mimeType: String = "",
        fileBase64: String = "",
        chunks: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            try {
                val material = StudyMaterial(
                    title = title,
                    subject = subject,
                    courseCode = courseCode,
                    semester = semester,
                    professor = professor,
                    type = type,
                    tags = tags,
                    content = content,
                    driveUrl = driveUrl,
                    fileName = fileName,
                    localPath = localPath,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    fileBase64 = fileBase64,
                    totalChunks = chunks.size,
                    hasChunks = chunks.isNotEmpty(),
                    isShared = isShared
                )
                repository.addMaterial(material, chunks = chunks)
                showStatusMessage("Successfully added \"$title\" to UniVault!")
            } catch (e: Exception) {
                showStatusMessage("Failed to add material: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    fun openMaterialFile(context: Context, material: StudyMaterial) {
        viewModelScope.launch {
            val displayName = material.fileName.ifBlank { material.title }
            try {
                DocumentStorageUtil.openInExternalApp(context, material, repository)
                if (material.id.isNotBlank() && DocumentStorageUtil.isFileDownloaded(context, material)) {
                    _downloadedMaterialIds.value = _downloadedMaterialIds.value + material.id
                }
            } catch (e: Exception) {
                showStatusMessage("Failed to open \"$displayName\": ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun downloadMaterialFile(context: Context, material: StudyMaterial) {
        viewModelScope.launch {
            _downloadingMaterialId.value = material.id
            downloadBannerDismissJob?.cancel()
            _lastDownloadedMaterial.value = null
            val displayName = material.fileName.ifBlank { material.title }
            showStatusMessage("Downloading \"$displayName\"...", autoDismissMillis = null)
            try {
                val success = DocumentStorageUtil.downloadFileToDevice(context, material, repository)
                if (success) {
                    if (material.id.isNotBlank()) {
                        _downloadedMaterialIds.value = _downloadedMaterialIds.value + material.id
                    }
                    clearStatusMessage()
                    showDownloadedBanner(material)
                } else {
                    showStatusMessage("Could not download \"$displayName\". Please verify attachment or network.")
                }
            } catch (e: Exception) {
                showStatusMessage("Download failed: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                _downloadingMaterialId.value = null
            }
        }
    }

    fun renameMaterial(material: StudyMaterial, newTitle: String, newSubject: String) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            try {
                val updated = material.copy(
                    title = newTitle.trim(),
                    subject = newSubject.trim()
                )
                repository.updateMaterial(updated)
                showStatusMessage("Updated \"$newTitle\" successfully.")
            } catch (e: Exception) {
                showStatusMessage("Update failed: ${e.localizedMessage ?: "Error"}")
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    fun editMaterial(
        material: StudyMaterial,
        newTitle: String,
        newSubject: String,
        newCourseCode: String,
        newSemester: String,
        newProfessor: String,
        newType: String,
        newTags: List<String>,
        newContent: String,
        newDriveUrl: String,
        isShared: Boolean,
        newFileName: String = material.fileName,
        newLocalPath: String = material.localPath,
        newFileSize: Long = material.fileSize,
        newMimeType: String = material.mimeType,
        newFileBase64: String = material.fileBase64
    ) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            try {
                val updated = material.copy(
                    title = newTitle.trim(),
                    subject = newSubject.trim(),
                    courseCode = newCourseCode.trim(),
                    semester = newSemester.trim(),
                    professor = newProfessor.trim(),
                    type = newType,
                    tags = newTags,
                    content = newContent,
                    driveUrl = newDriveUrl.trim(),
                    isShared = isShared,
                    fileName = newFileName,
                    localPath = newLocalPath,
                    fileSize = newFileSize,
                    mimeType = newMimeType,
                    fileBase64 = newFileBase64
                )
                repository.updateMaterial(updated)
                showStatusMessage("Material \"$newTitle\" updated successfully.")
            } catch (e: Exception) {
                showStatusMessage("Edit failed: ${e.localizedMessage ?: "Error"}")
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    fun toggleShared(material: StudyMaterial) {
        viewModelScope.launch {
            try {
                val updated = material.copy(isShared = !material.isShared)
                repository.updateMaterial(updated)
                showStatusMessage(if (updated.isShared) "Shared with campus peers!" else "Made private.")
            } catch (e: Exception) {
                showStatusMessage("Failed to update sharing: ${e.localizedMessage ?: "Error"}")
            }
        }
    }

    fun deleteMaterial(material: StudyMaterial) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            try {
                repository.deleteMaterial(material.id)
                showStatusMessage("Deleted \"${material.title}\"")
            } catch (e: Exception) {
                showStatusMessage("Delete failed: ${e.localizedMessage ?: "Error"}")
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    fun generateExportSummary(materials: List<StudyMaterial>): String {
        val sb = StringBuilder()
        sb.append("=== UNIVault ACADEMIC STUDY PACK EXPORT ===\n")
        sb.append("Export Date: 2026 Academic Session\n")
        sb.append("Total Items: ${materials.size}\n\n")

        materials.forEachIndexed { i, m ->
            sb.append("${i + 1}. [${m.type}] ${m.title} (${m.courseCode})\n")
            sb.append("   Subject: ${m.subject} | Semester: ${m.semester}\n")
            sb.append("   Professor: ${m.professor.ifBlank { "N/A" }}\n")
            if (m.driveUrl.isNotBlank()) {
                sb.append("   Google Drive Link: ${m.driveUrl}\n")
            }
            if (m.tags.isNotEmpty()) {
                sb.append("   Tags: ${m.tags.joinToString(", ")}\n")
            }
            if (m.content.isNotBlank()) {
                sb.append("   Content Preview: ${m.content.take(150).replace("\n", " ")}...\n")
            }
            sb.append("\n")
        }
        sb.append("=== END OF EXPORT ===")
        return sb.toString()
    }
}
