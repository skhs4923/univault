package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FlashcardDeck
import com.example.data.model.FlashcardItem
import com.example.data.repository.AiTutorRepository
import com.example.data.repository.FlashcardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class FlashcardStudySession(
    val deck: FlashcardDeck,
    val allCards: List<FlashcardItem>,
    val visibleCardIds: List<String>,
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val showHint: Boolean = false,
    val unmasteredOnly: Boolean = false,
    val reviewedThisSession: Int = 0,
    val masteredThisSession: Int = 0,
    val isCompleted: Boolean = false
) {
    val currentCard: FlashcardItem?
        get() {
            val targetId = visibleCardIds.getOrNull(currentIndex) ?: return null
            return allCards.firstOrNull { it.id == targetId }
        }

    val masteredTotal: Int
        get() = allCards.count { it.isMastered }
}

class FlashcardViewModel(
    private val repository: FlashcardRepository,
    private val aiRepository: AiTutorRepository
) : ViewModel() {

    val decks: StateFlow<List<FlashcardDeck>> = repository.observeDecks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _activeSession = MutableStateFlow<FlashcardStudySession?>(null)
    val activeSession: StateFlow<FlashcardStudySession?> = _activeSession.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isStatusError = MutableStateFlow(false)
    val isStatusError: StateFlow<Boolean> = _isStatusError.asStateFlow()

    fun generateDeckFromNotes(
        selectedText: String,
        deckTitle: String = "",
        subject: String = "General Studies",
        courseCode: String = "GEN",
        sourceNoteId: String = "",
        sourceNoteTitle: String = "",
        cardCount: Int = 8,
        openImmediately: Boolean = true
    ) {
        val cleanedSelection = selectedText.trim()
        if (cleanedSelection.isBlank()) {
            _isStatusError.value = true
            _statusMessage.value = "Please highlight/select or paste note text first."
            return
        }

        viewModelScope.launch {
            _isGenerating.value = true
            _isStatusError.value = false
            _statusMessage.value = "Generating digital flashcards from your selected notes..."
            try {
                val titleHint = deckTitle.trim().ifBlank { sourceNoteTitle.trim() }
                val safeSubject = subject.trim().ifBlank { "General Studies" }
                val safeCode = courseCode.trim().uppercase().ifBlank { "GEN" }

                val (aiTitle, cards) = aiRepository.generateFlashcardsFromNotes(
                    selectedText = cleanedSelection,
                    titleHint = titleHint,
                    subject = safeSubject,
                    cardCount = cardCount
                )

                val finalTitle = deckTitle.trim().ifBlank { aiTitle }
                val newDeck = FlashcardDeck(
                    title = finalTitle,
                    subject = safeSubject,
                    courseCode = safeCode,
                    sourceNoteId = sourceNoteId,
                    sourceNoteTitle = sourceNoteTitle.trim(),
                    sourceExcerpt = cleanedSelection.take(280),
                    cardsCount = cards.size,
                    masteredCount = 0,
                    cardsJson = FlashcardItem.serializeList(cards)
                )

                val createdId = repository.createDeck(newDeck)
                val persistedDeck = newDeck.copy(id = createdId)

                _isStatusError.value = false
                _statusMessage.value = "Created \"$finalTitle\" with ${cards.size} flashcards!"
                if (openImmediately) {
                    startSession(persistedDeck, cards, unmasteredOnly = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isStatusError.value = true
                _statusMessage.value = "Flashcard generation failed: ${e.localizedMessage ?: "Please try again."}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun openDeck(deck: FlashcardDeck, unmasteredOnly: Boolean = false) {
        val cards = FlashcardItem.parseList(deck.cardsJson)
        if (cards.isEmpty()) {
            _isStatusError.value = true
            _statusMessage.value = "This deck currently has no flashcards."
            return
        }
        startSession(deck, cards, unmasteredOnly = unmasteredOnly)
    }

    private fun startSession(
        deck: FlashcardDeck,
        cards: List<FlashcardItem>,
        unmasteredOnly: Boolean
    ) {
        val visibleIds = if (unmasteredOnly) {
            cards.filter { !it.isMastered }.map { it.id }.ifEmpty { cards.map { it.id } }
        } else {
            cards.map { it.id }
        }
        val effectiveFilter = unmasteredOnly && cards.any { !it.isMastered }
        _activeSession.value = FlashcardStudySession(
            deck = deck,
            allCards = cards,
            visibleCardIds = visibleIds,
            currentIndex = 0,
            isFlipped = false,
            showHint = false,
            unmasteredOnly = effectiveFilter
        )
    }

    fun flipCard() {
        val session = _activeSession.value ?: return
        _activeSession.value = session.copy(isFlipped = !session.isFlipped)
    }

    fun toggleHint() {
        val session = _activeSession.value ?: return
        _activeSession.value = session.copy(showHint = !session.showHint)
    }

    fun nextCard() {
        val session = _activeSession.value ?: return
        if (session.visibleCardIds.isEmpty()) return
        if (session.currentIndex < session.visibleCardIds.lastIndex) {
            _activeSession.value = session.copy(
                currentIndex = session.currentIndex + 1,
                isFlipped = false,
                showHint = false
            )
        } else {
            _activeSession.value = session.copy(isCompleted = true, isFlipped = false, showHint = false)
        }
    }

    fun previousCard() {
        val session = _activeSession.value ?: return
        if (session.currentIndex > 0) {
            _activeSession.value = session.copy(
                currentIndex = session.currentIndex - 1,
                isFlipped = false,
                showHint = false,
                isCompleted = false
            )
        }
    }

    fun shuffleSession() {
        val session = _activeSession.value ?: return
        if (session.visibleCardIds.size <= 1) return
        _activeSession.value = session.copy(
            visibleCardIds = session.visibleCardIds.shuffled(),
            currentIndex = 0,
            isFlipped = false,
            showHint = false,
            isCompleted = false
        )
    }

    fun toggleUnmasteredFilter() {
        val session = _activeSession.value ?: return
        val nextFilter = !session.unmasteredOnly
        val filtered = if (nextFilter) {
            session.allCards.filter { !it.isMastered }.map { it.id }
        } else {
            session.allCards.map { it.id }
        }
        if (filtered.isEmpty()) {
            _isStatusError.value = false
            _statusMessage.value = "All cards in this deck are mastered!"
            return
        }
        _activeSession.value = session.copy(
            visibleCardIds = filtered,
            currentIndex = 0,
            isFlipped = false,
            showHint = false,
            unmasteredOnly = nextFilter,
            isCompleted = false
        )
    }

    fun markCardMastered(mastered: Boolean) {
        val session = _activeSession.value ?: return
        val current = session.currentCard ?: return

        val updatedCards = session.allCards.map { card ->
            if (card.id == current.id) {
                card.copy(
                    isMastered = mastered,
                    reviewCount = card.reviewCount + 1
                )
            } else {
                card
            }
        }

        val updatedDeck = session.deck.copy(
            cardsCount = updatedCards.size,
            masteredCount = updatedCards.count { it.isMastered },
            cardsJson = FlashcardItem.serializeList(updatedCards)
        )

        val nextVisible = if (session.unmasteredOnly && mastered) {
            session.visibleCardIds.filterNot { it == current.id }
        } else {
            session.visibleCardIds
        }

        val nextCompleted = if (session.unmasteredOnly && mastered) {
            nextVisible.isEmpty() || session.currentIndex >= nextVisible.size
        } else {
            session.currentIndex >= nextVisible.lastIndex
        }

        val nextIndex = if (nextVisible.isEmpty()) {
            0
        } else if (session.unmasteredOnly && mastered) {
            session.currentIndex.coerceAtMost(nextVisible.lastIndex)
        } else {
            (session.currentIndex + 1).coerceAtMost(nextVisible.lastIndex)
        }

        _activeSession.value = session.copy(
            deck = updatedDeck,
            allCards = updatedCards,
            visibleCardIds = nextVisible,
            currentIndex = nextIndex,
            isFlipped = false,
            showHint = false,
            reviewedThisSession = session.reviewedThisSession + 1,
            masteredThisSession = session.masteredThisSession + if (mastered && !current.isMastered) 1 else 0,
            isCompleted = nextCompleted
        )

        viewModelScope.launch {
            try {
                repository.updateDeck(updatedDeck)
            } catch (e: Exception) {
                _isStatusError.value = true
                _statusMessage.value = "Could not sync progress: ${e.localizedMessage ?: "Network error"}"
            }
        }
    }

    fun restartSession(unmasteredOnly: Boolean = false) {
        val session = _activeSession.value ?: return
        startSession(session.deck, session.allCards, unmasteredOnly = unmasteredOnly)
    }

    fun resetDeckProgress() {
        val session = _activeSession.value ?: return
        val resetCards = session.allCards.map { it.copy(isMastered = false, reviewCount = 0) }
        val updatedDeck = session.deck.copy(
            cardsCount = resetCards.size,
            masteredCount = 0,
            cardsJson = FlashcardItem.serializeList(resetCards)
        )
        startSession(updatedDeck, resetCards, unmasteredOnly = false)
        viewModelScope.launch {
            try {
                repository.updateDeck(updatedDeck)
                _isStatusError.value = false
                _statusMessage.value = "Reset progress for \"${updatedDeck.title}\"."
            } catch (e: Exception) {
                _isStatusError.value = true
                _statusMessage.value = "Failed to reset progress: ${e.localizedMessage ?: "Error"}"
            }
        }
    }

    fun addCardToSessionDeck(front: String, back: String, hint: String = "", topic: String = "") {
        val session = _activeSession.value ?: return
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        if (cleanFront.isBlank() || cleanBack.isBlank()) return

        val newCard = FlashcardItem(
            id = "fc_${UUID.randomUUID().toString().take(10)}",
            front = cleanFront,
            back = cleanBack,
            hint = hint.trim(),
            topic = topic.trim().ifBlank { session.deck.subject }
        )
        val updatedCards = session.allCards + newCard
        val updatedDeck = session.deck.copy(
            cardsCount = updatedCards.size,
            masteredCount = updatedCards.count { it.isMastered },
            cardsJson = FlashcardItem.serializeList(updatedCards)
        )
        _activeSession.value = session.copy(
            deck = updatedDeck,
            allCards = updatedCards,
            visibleCardIds = session.visibleCardIds + newCard.id,
            isCompleted = false
        )
        viewModelScope.launch {
            try {
                repository.updateDeck(updatedDeck)
            } catch (e: Exception) {
                _isStatusError.value = true
                _statusMessage.value = "Failed to save new card: ${e.localizedMessage ?: "Error"}"
            }
        }
    }

    fun deleteDeck(deck: FlashcardDeck) {
        viewModelScope.launch {
            try {
                repository.deleteDeck(deck.id)
                if (_activeSession.value?.deck?.id == deck.id) {
                    _activeSession.value = null
                }
                _isStatusError.value = false
                _statusMessage.value = "Deleted deck \"${deck.title}\"."
            } catch (e: Exception) {
                _isStatusError.value = true
                _statusMessage.value = "Failed to delete deck: ${e.localizedMessage ?: "Error"}"
            }
        }
    }

    fun closeSession() {
        _activeSession.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
        _isStatusError.value = false
    }

    companion object {
        /**
         * Splits raw study note text into clean, selectable paragraphs/sections so users
         * can cherry-pick exact paragraphs or bullets without manual copy-paste if preferred.
         */
        fun splitNoteSegments(noteText: String): List<String> {
            val normalized = noteText.replace("\r\n", "\n").trim()
            if (normalized.isBlank()) return emptyList()

            val paragraphs = normalized
                .split(Regex("\n{2,}"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (paragraphs.size >= 2) {
                return paragraphs
            }

            val lines = normalized
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }

            return if (lines.size >= 2) lines else listOf(normalized)
        }
    }
}
