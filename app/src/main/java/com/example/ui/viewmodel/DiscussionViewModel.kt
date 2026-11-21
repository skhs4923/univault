package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DiscussionMessage
import com.example.data.repository.DiscussionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionViewModel(
    private val repository: DiscussionRepository
) : ViewModel() {

    private val _selectedChannel = MutableStateFlow("General")
    val selectedChannel: StateFlow<String> = _selectedChannel.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    val availableChannels = listOf("General", "CS201", "MATH101", "PHY101", "CHEM101", "EE102")

    val messages: StateFlow<List<DiscussionMessage>> = _selectedChannel
        .flatMapLatest { channel -> repository.observeMessages(channel) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val recentCollegeMessages: StateFlow<List<DiscussionMessage>> = repository.observeRecentMessages(limit = 50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun selectChannel(channel: String) {
        _selectedChannel.value = channel
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            try {
                repository.sendMessage(_selectedChannel.value, text)
            } catch (e: Exception) {
                // error handled in repository
            } finally {
                _isSending.value = false
            }
        }
    }
}
