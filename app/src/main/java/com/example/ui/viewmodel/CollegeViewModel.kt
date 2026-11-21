package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UnlockedCollege
import com.example.data.repository.CollegeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollegeViewModel(
    private val repository: CollegeRepository
) : ViewModel() {

    val unlockedColleges: StateFlow<List<UnlockedCollege>> = repository.observeUnlockedColleges()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _selectedCollege = MutableStateFlow<UnlockedCollege?>(null)
    val selectedCollege: StateFlow<UnlockedCollege?> = _selectedCollege.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun selectCollege(college: UnlockedCollege) {
        _selectedCollege.value = college
        _errorMessage.value = null
        _statusMessage.value = null
    }

    fun exitCollege() {
        _selectedCollege.value = null
    }

    fun clearMessages() {
        _errorMessage.value = null
        _statusMessage.value = null
    }

    fun unlockByCode(rawCode: String, onUnlocked: (UnlockedCollege) -> Unit = {}) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val college = repository.unlockCollegeByCode(rawCode)
                _statusMessage.value = "Unlocked \"${college.collegeName}\"!"
                onUnlocked(college)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not unlock college with that code."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createCollege(
        collegeName: String,
        rawCode: String,
        description: String,
        onCreated: (UnlockedCollege) -> Unit = {}
    ) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val created = repository.createCollege(
                    collegeName = collegeName,
                    rawCode = rawCode,
                    description = description
                )
                _statusMessage.value = "Created \"${created.collegeName}\" (Code: ${created.accessCode})."
                onCreated(created)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not register college."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun lockCollege(college: UnlockedCollege) {
        viewModelScope.launch {
            try {
                repository.lockCollegeForUser(college.collegeId)
                if (_selectedCollege.value?.collegeId == college.collegeId) {
                    _selectedCollege.value = null
                }
                _statusMessage.value = "Locked \"${college.collegeName}\". Enter its special code anytime to access it again."
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to lock college."
            }
        }
    }
}
