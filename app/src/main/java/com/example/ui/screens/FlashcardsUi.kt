package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FlashcardDeck
import com.example.data.model.FlashcardItem
import com.example.data.model.StudyMaterial
import com.example.ui.viewmodel.FlashcardStudySession
import com.example.ui.viewmodel.FlashcardViewModel

@Composable
fun FlashcardsTabContent(
    decks: List<FlashcardDeck>,
    availableNotes: List<StudyMaterial>,
    isGenerating: Boolean,
    statusMessage: String?,
    isStatusError: Boolean,
    onDismissStatus: () -> Unit,
    onGenerateDeck: (
        selectedText: String,
        deckTitle: String,
        subject: String,
        courseCode: String,
        sourceNoteId: String,
        sourceNoteTitle: String,
        cardCount: Int
    ) -> Unit,
    onOpenDeck: (FlashcardDeck, Boolean) -> Unit,
    onDeleteDeck: (FlashcardDeck) -> Unit
) {
    var showGeneratorDialog by remember { mutableStateOf(false) }
    var deckToDelete by remember { mutableStateOf<FlashcardDeck?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Note-to-Flashcard Studio",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select paragraphs from any Vault note or paste copied excerpts to auto-build interactive flip-card decks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showGeneratorDialog = true },
                    enabled = !isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("new_flashcard_deck_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating Flashcard Deck...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Flashcards from Notes")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        statusMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("flashcards_status_banner"),
                shape = RoundedCornerShape(14.dp),
                color = if (isStatusError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissStatus) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss notice",
                            tint = if (isStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Digital Flashcard Decks",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${decks.size} ${if (decks.size == 1) "deck" else "decks"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (decks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No flashcard decks yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select text from any lecture note in Vault or tap 'Create Flashcards from Notes' above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(decks, key = { it.id }) { deck ->
                    val progress = if (deck.cardsCount > 0) {
                        (deck.masteredCount.toFloat() / deck.cardsCount.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val remaining = (deck.cardsCount - deck.masteredCount).coerceAtLeast(0)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("flashcard_deck_card_${deck.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = deck.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${deck.subject} • ${deck.courseCode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { deckToDelete = deck },
                                    modifier = Modifier.testTag("delete_deck_btn_${deck.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete deck",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (deck.sourceNoteTitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "From note: ${deck.sourceNoteTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${deck.masteredCount}/${deck.cardsCount} Mastered",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (progress >= 1f) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onOpenDeck(deck, false) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("study_deck_btn_${deck.id}"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Study (${deck.cardsCount})")
                                }

                                if (remaining in 1 until deck.cardsCount) {
                                    OutlinedButton(
                                        onClick = { onOpenDeck(deck, true) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("study_unmastered_btn_${deck.id}"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Unmastered ($remaining)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGeneratorDialog) {
        NoteFlashcardGeneratorDialog(
            initialNote = null,
            availableNotes = availableNotes,
            isGenerating = isGenerating,
            onDismiss = { showGeneratorDialog = false },
            onGenerate = { selectedText, title, subject, code, noteId, noteTitle, count ->
                onGenerateDeck(selectedText, title, subject, code, noteId, noteTitle, count)
                showGeneratorDialog = false
            }
        )
    }

    deckToDelete?.let { deck ->
        AlertDialog(
            onDismissRequest = { deckToDelete = null },
            title = { Text("Delete Flashcard Deck") },
            text = { Text("Delete \"${deck.title}\" (${deck.cardsCount} cards)? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteDeck(deck)
                    deckToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deckToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NoteFlashcardGeneratorDialog(
    initialNote: StudyMaterial?,
    availableNotes: List<StudyMaterial>,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (
        selectedText: String,
        deckTitle: String,
        subject: String,
        courseCode: String,
        sourceNoteId: String,
        sourceNoteTitle: String,
        cardCount: Int
    ) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val notesWithText = remember(availableNotes, initialNote) {
        val merged = buildList {
            if (initialNote != null && initialNote.content.isNotBlank()) add(initialNote)
            addAll(availableNotes.filter { it.content.isNotBlank() && it.id != initialNote?.id })
        }
        merged
    }

    var selectedNote by remember(initialNote) {
        mutableStateOf(initialNote ?: notesWithText.firstOrNull())
    }
    var deckTitle by remember(initialNote) {
        mutableStateOf(initialNote?.title?.let { "$it • Flashcards" } ?: "")
    }
    var subject by remember(initialNote) {
        mutableStateOf(initialNote?.subject ?: selectedNote?.subject ?: "")
    }
    var courseCode by remember(initialNote) {
        mutableStateOf(initialNote?.courseCode ?: selectedNote?.courseCode ?: "")
    }
    var selectedText by remember(initialNote) {
        mutableStateOf(selectedNote?.content ?: "")
    }
    var cardCount by remember { mutableIntStateOf(8) }

    val noteSegments = remember(selectedNote?.id, selectedNote?.content) {
        selectedNote?.content?.let { FlashcardViewModel.splitNoteSegments(it) } ?: emptyList()
    }
    val selectedSegmentIndices = remember(selectedNote?.id) {
        mutableStateListOf<Int>().apply {
            noteSegments.indices.forEach { add(it) }
        }
    }

    fun applyNoteSelection(note: StudyMaterial?) {
        selectedNote = note
        if (note != null) {
            if (deckTitle.isBlank() || notesWithText.any { "${it.title} • Flashcards" == deckTitle }) {
                deckTitle = "${note.title} • Flashcards"
            }
            subject = note.subject
            courseCode = note.courseCode
            val segs = FlashcardViewModel.splitNoteSegments(note.content)
            selectedSegmentIndices.clear()
            segs.indices.forEach { selectedSegmentIndices.add(it) }
            selectedText = note.content
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notes to Flashcards", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pick paragraphs from a saved note, paste text you selected from Vault, or edit the excerpt directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (notesWithText.isNotEmpty()) {
                    Text(
                        text = "Source Vault Note:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        notesWithText.take(12).forEach { note ->
                            FilterChip(
                                selected = selectedNote?.id == note.id,
                                onClick = { applyNoteSelection(note) },
                                label = {
                                    Text(
                                        text = note.title.take(24),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }
                }

                if (noteSegments.size >= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap Sections to Include (${selectedSegmentIndices.size}/${noteSegments.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    selectedSegmentIndices.clear()
                                    noteSegments.indices.forEach { selectedSegmentIndices.add(it) }
                                    selectedText = noteSegments.joinToString("\n\n")
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("All", fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = {
                                    selectedSegmentIndices.clear()
                                    selectedText = ""
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        noteSegments.take(10).forEachIndexed { idx, segment ->
                            val isChecked = idx in selectedSegmentIndices
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            selectedSegmentIndices.remove(idx)
                                        } else {
                                            selectedSegmentIndices.add(idx)
                                        }
                                        selectedText = noteSegments
                                            .filterIndexed { i, _ -> i in selectedSegmentIndices }
                                            .joinToString("\n\n")
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = segment,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected Note Text *",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                            if (clip.isNotBlank()) {
                                selectedText = clip
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Clipboard", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = selectedText,
                    onValueChange = { selectedText = it },
                    placeholder = { Text("Paste highlighted study notes, definitions, formulas, or key concepts here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp, max = 180.dp)
                        .testTag("flashcard_source_text_input")
                )

                OutlinedTextField(
                    value = deckTitle,
                    onValueChange = { deckTitle = it },
                    label = { Text("Deck Title (Optional — AI names if blank)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = { courseCode = it },
                        label = { Text("Course") },
                        modifier = Modifier.weight(0.75f)
                    )
                }

                Text(
                    text = "Number of Flashcards",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 8, 12).forEach { count ->
                        FilterChip(
                            selected = cardCount == count,
                            onClick = { cardCount = count },
                            label = { Text("$count cards") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onGenerate(
                        selectedText,
                        deckTitle,
                        subject,
                        courseCode,
                        selectedNote?.id ?: "",
                        selectedNote?.title ?: "",
                        cardCount
                    )
                },
                enabled = selectedText.isNotBlank() && !isGenerating,
                modifier = Modifier.testTag("generate_flashcard_deck_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate Deck")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isGenerating) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FlashcardReviewDialog(
    session: FlashcardStudySession,
    onClose: () -> Unit,
    onFlip: () -> Unit,
    onToggleHint: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onToggleUnmasteredOnly: () -> Unit,
    onMarkMastered: (Boolean) -> Unit,
    onRestart: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onAddCard: (String, String, String) -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            FlashcardReviewView(
                session = session,
                onClose = onClose,
                onFlip = onFlip,
                onToggleHint = onToggleHint,
                onNext = onNext,
                onPrevious = onPrevious,
                onShuffle = onShuffle,
                onToggleUnmasteredOnly = onToggleUnmasteredOnly,
                onMarkMastered = onMarkMastered,
                onRestart = onRestart,
                onResetProgress = onResetProgress,
                onAddCard = onAddCard
            )
        }
    }
}

@Composable
fun FlashcardReviewView(
    session: FlashcardStudySession,
    onClose: () -> Unit,
    onFlip: () -> Unit,
    onToggleHint: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onToggleUnmasteredOnly: () -> Unit,
    onMarkMastered: (Boolean) -> Unit,
    onRestart: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onAddCard: (front: String, back: String, hint: String) -> Unit
) {
    BackHandler { onClose() }

    var showBrowseDialog by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("flashcard_close_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit flashcards")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.deck.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${session.masteredTotal}/${session.allCards.size} Mastered • ${session.deck.courseCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showBrowseDialog = true }) {
                        Icon(Icons.Default.Visibility, contentDescription = "Browse all cards")
                    }
                    IconButton(onClick = { showAddCardDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add card")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Controls bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = session.unmasteredOnly,
                    onClick = onToggleUnmasteredOnly,
                    leadingIcon = {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    label = { Text("Unmastered Only", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = onShuffle,
                    leadingIcon = {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    label = { Text("Shuffle", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = onResetProgress,
                    leadingIcon = {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    label = { Text("Reset Mastery", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val totalVisible = session.visibleCardIds.size.coerceAtLeast(1)
            val progressFraction = if (session.isCompleted) 1f else {
                (session.currentIndex + 1).toFloat() / totalVisible.toFloat()
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (session.isCompleted || session.currentCard == null) {
                // Completion Summary
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Deck Review Complete!",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${session.masteredTotal} of ${session.allCards.size} cards mastered • ${session.reviewedThisSession} reviewed this session",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        val remaining = session.allCards.count { !it.isMastered }
                        if (remaining > 0) {
                            Button(
                                onClick = { onRestart(true) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Practice Unmastered ($remaining)")
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        OutlinedButton(
                            onClick = { onRestart(false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Review Full Deck")
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(onClick = onClose) {
                            Text("Back to Decks")
                        }
                    }
                }
            } else {
                val card = session.currentCard!!
                val rotation by animateFloatAsState(
                    targetValue = if (session.isFlipped) 180f else 0f,
                    animationSpec = tween(durationMillis = 420),
                    label = "flashcard_flip"
                )
                val showingBack = rotation > 90f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card ${session.currentIndex + 1} of ${session.visibleCardIds.size}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (card.topic.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = card.topic,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3D Flip Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 12f * density
                        }
                        .clickable { onFlip() }
                        .testTag("flashcard_flip_surface"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (showingBack) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (showingBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (showingBack) rotationY = 180f
                            }
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (showingBack) "ANSWER / EXPLANATION" else "QUESTION / CONCEPT",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = if (showingBack) card.back else card.front,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = if (showingBack) FontWeight.Medium else FontWeight.Bold,
                                    lineHeight = 28.sp
                                ),
                                textAlign = TextAlign.Center,
                                color = if (showingBack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )

                            if (!showingBack && card.hint.isNotBlank()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                AnimatedVisibility(visible = session.showHint) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "Hint: ${card.hint}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                if (!session.showHint) {
                                    TextButton(onClick = onToggleHint) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Show Hint")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Tap card to flip",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Self-grading actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onMarkMastered(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("flashcard_still_learning_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Still Learning")
                    }

                    Button(
                        onClick = { onMarkMastered(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("flashcard_got_it_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Got It")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Previous / Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onPrevious,
                        enabled = session.currentIndex > 0
                    ) {
                        Text("Previous")
                    }
                    TextButton(onClick = onFlip) {
                        Text(if (session.isFlipped) "Show Question" else "Show Answer")
                    }
                    TextButton(onClick = onNext) {
                        Text(if (session.currentIndex < session.visibleCardIds.lastIndex) "Next" else "Finish")
                    }
                }
            }
        }
    }

    if (showBrowseDialog) {
        AlertDialog(
            onDismissRequest = { showBrowseDialog = false },
            title = { Text("All Cards (${session.allCards.size})") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    session.allCards.forEachIndexed { idx, item ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isMastered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (item.isMastered) "Mastered" else "Learning",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.isMastered) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.front, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(item.back, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrowseDialog = false }) { Text("Close") }
            }
        )
    }

    if (showAddCardDialog) {
        var front by remember { mutableStateOf("") }
        var back by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCardDialog = false },
            title = { Text("Add Flashcard") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = front,
                        onValueChange = { front = it },
                        label = { Text("Front (Question / Term) *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = back,
                        onValueChange = { back = it },
                        label = { Text("Back (Answer / Explanation) *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = hint,
                        onValueChange = { hint = it },
                        label = { Text("Hint (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCard(front, back, hint)
                        showAddCardDialog = false
                    },
                    enabled = front.isNotBlank() && back.isNotBlank()
                ) {
                    Text("Add Card")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCardDialog = false }) { Text("Cancel") }
            }
        )
    }
}
