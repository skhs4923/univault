package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudyMaterial
import com.example.ui.viewmodel.FlashcardViewModel
import com.example.ui.viewmodel.MaterialSlot
import com.example.ui.viewmodel.StudyMaterialViewModel
import com.example.ui.viewmodel.UserPerspective
import com.example.util.DocumentMetadata
import com.example.util.DocumentStorageUtil
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: StudyMaterialViewModel,
    currentUserId: String,
    flashcardViewModel: FlashcardViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val filterState by viewModel.filterState.collectAsState()
    val materials by viewModel.filteredMaterials.collectAsState()
    val allMaterials by viewModel.allMaterials.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val currentPerspective by viewModel.currentPerspective.collectAsState()
    val downloadingMaterialId by viewModel.downloadingMaterialId.collectAsState()
    val downloadedMaterialIds by viewModel.downloadedMaterialIds.collectAsState()
    val lastDownloadedMaterial by viewModel.lastDownloadedMaterial.collectAsState()

    val isGeneratingFlashcards = flashcardViewModel?.isGenerating?.collectAsState()?.value ?: false
    val activeFlashcardSession = flashcardViewModel?.activeSession?.collectAsState()?.value

    var showAddDialog by remember { mutableStateOf(false) }
    var materialToEdit by remember { mutableStateOf<StudyMaterial?>(null) }
    var materialToView by remember { mutableStateOf<StudyMaterial?>(null) }
    var materialToDelete by remember { mutableStateOf<StudyMaterial?>(null) }
    var materialForFlashcards by remember { mutableStateOf<StudyMaterial?>(null) }

    // Distinct filter candidates
    val availableSemesters = remember(allMaterials) {
        listOf("All") + allMaterials.map { it.semester }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val availableCourseCodes = remember(allMaterials) {
        listOf("All") + allMaterials.map { it.courseCode }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val availableProfessors = remember(allMaterials) {
        listOf("All") + allMaterials.map { it.professor }.filter { it.isNotBlank() }.distinct().sorted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Slot Tabs: All, Lecture Notes, Question Papers, Question Banks, My Uploads
            val slotTabs = MaterialSlot.values()
            ScrollableTabRow(
                selectedTabIndex = slotTabs.indexOf(filterState.selectedSlot),
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                slotTabs.forEach { slot ->
                    val isSelected = filterState.selectedSlot == slot
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.setSlot(slot) },
                        text = {
                            Text(
                                text = slot.label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag("tab_${slot.name.lowercase()}")
                    )
                }
            }

            // Filter row for Semester, Course Code, Professor, and Perspective Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Perspective toggle: Experience as Owner (uploader) vs Another Person (peer student)
                FilterChip(
                    selected = currentPerspective == UserPerspective.OWNER,
                    onClick = { viewModel.togglePerspective() },
                    label = {
                        Text(
                            text = if (currentPerspective == UserPerspective.OWNER) "Mode: Owner (You)" else "Mode: Another Person (Peer)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (currentPerspective == UserPerspective.OWNER) Icons.Default.AccountCircle else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("perspective_toggle_chip")
                )

                FilterDropdownChip(
                    label = "Sem: ${filterState.selectedSemester}",
                    options = availableSemesters,
                    selectedOption = filterState.selectedSemester,
                    onOptionSelected = { viewModel.setSemesterFilter(it) }
                )

                FilterDropdownChip(
                    label = "Course: ${filterState.selectedCourseCode}",
                    options = availableCourseCodes,
                    selectedOption = filterState.selectedCourseCode,
                    onOptionSelected = { viewModel.setCourseCodeFilter(it) }
                )

                FilterDropdownChip(
                    label = "Prof: ${filterState.selectedProfessor}",
                    options = availableProfessors,
                    selectedOption = filterState.selectedProfessor,
                    onOptionSelected = { viewModel.setProfessorFilter(it) }
                )

                if (filterState.selectedSemester != "All" || filterState.selectedCourseCode != "All" || filterState.selectedProfessor != "All" || filterState.searchQuery.isNotBlank()) {
                    TextButton(onClick = { viewModel.clearFilters() }) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Status notice (auto-hides after 3 seconds)
            AnimatedVisibility(
                visible = statusMessage != null && lastDownloadedMaterial == null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                statusMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearStatusMessage() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (isGeneratingFlashcards) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Generating digital flashcards from selected notes...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            flashcardViewModel?.statusMessage?.collectAsState()?.value?.let { fcMsg ->
                if (!isGeneratingFlashcards) {
                    val isFcErr = flashcardViewModel.isStatusError.collectAsState().value
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFcErr) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                tint = if (isFcErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = fcMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFcErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { flashcardViewModel.clearStatusMessage() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Download Success Top Notification (visible for 3 seconds, with Open button)
            AnimatedVisibility(
                visible = lastDownloadedMaterial != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                lastDownloadedMaterial?.let { downloaded ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Downloaded: ${downloaded.fileName.ifBlank { downloaded.title }}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Saved to Downloads • Ready to open",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    viewModel.openMaterialFile(context, downloaded)
                                    viewModel.clearLastDownloaded()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open", fontSize = 11.sp)
                            }
                            IconButton(
                                onClick = { viewModel.clearLastDownloaded() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Dismiss notification",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Materials list
            if (materials.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No files uploaded in this college yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Files uploaded here are stored inside this college card only. Tap '+' below to upload PDFs, PPT slides, notes, images, or videos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("materials_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(materials, key = { it.id }) { item ->
                        val effectiveIsOwner = (item.userId == currentUserId) && (currentPerspective == UserPerspective.OWNER)
                        val isDownloaded = remember(item.id, downloadedMaterialIds, item.fileName) {
                            item.id in downloadedMaterialIds || DocumentStorageUtil.isFileDownloaded(context, item)
                        }
                        MaterialItemCard(
                            material = item,
                            isOwner = effectiveIsOwner,
                            isDownloading = downloadingMaterialId == item.id,
                            isDownloaded = isDownloaded,
                            onView = { materialToView = item },
                            onDownload = {
                                viewModel.downloadMaterialFile(context, item)
                            },
                            onOpenExternal = {
                                viewModel.openMaterialFile(context, item)
                            },
                            onEdit = { materialToEdit = item },
                            onToggleShared = { viewModel.toggleShared(item) },
                            onDelete = { materialToDelete = item },
                            onOpenDrive = {
                                if (item.driveUrl.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.driveUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open URL: ${item.driveUrl}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onCreateFlashcards = if (flashcardViewModel != null && item.content.isNotBlank()) {
                                { materialForFlashcards = item }
                            } else null
                        )
                    }
                }
            }
        }

        // Floating Action Button to Add Material
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_material_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Upload Study Material")
        }
    }

    // Modal: Add Material (with PDF, PPT, image, video upload up to 3MB)
    if (showAddDialog) {
        AddMaterialDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, subject, code, sem, prof, type, tags, content, drive, isShared, fileName, localPath, fileSize, mimeType, fileBase64, chunks ->
                viewModel.addStudyMaterial(
                    title = title,
                    subject = subject,
                    courseCode = code,
                    semester = sem,
                    professor = prof,
                    type = type,
                    tags = tags,
                    content = content,
                    driveUrl = drive,
                    isShared = isShared,
                    fileName = fileName,
                    localPath = localPath,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    fileBase64 = fileBase64,
                    chunks = chunks
                )
                showAddDialog = false
            }
        )
    }

    // Modal: Edit Material & Naming (Accessible by the uploader/owner)
    materialToEdit?.let { material ->
        EditMaterialDialog(
            material = material,
            onDismiss = { materialToEdit = null },
            onConfirm = { title, subject, code, sem, prof, type, tags, content, drive, isShared ->
                viewModel.editMaterial(
                    material = material,
                    newTitle = title,
                    newSubject = subject,
                    newCourseCode = code,
                    newSemester = sem,
                    newProfessor = prof,
                    newType = type,
                    newTags = tags,
                    newContent = content,
                    newDriveUrl = drive,
                    isShared = isShared
                )
                materialToEdit = null
            }
        )
    }

    // Modal: Full Document & Material Details
    materialToView?.let { material ->
        val isDialogMaterialDownloaded = remember(material.id, downloadedMaterialIds, material.fileName) {
            material.id in downloadedMaterialIds || DocumentStorageUtil.isFileDownloaded(context, material)
        }
        val effectiveIsOwner = (material.userId == currentUserId) && (currentPerspective == UserPerspective.OWNER)
        ViewMaterialDialog(
            material = material,
            isOwner = effectiveIsOwner,
            isDownloading = downloadingMaterialId == material.id,
            isDownloaded = isDialogMaterialDownloaded,
            onDismiss = { materialToView = null },
            onDownload = {
                viewModel.downloadMaterialFile(context, material)
            },
            onOpenExternal = {
                viewModel.openMaterialFile(context, material)
            },
            onCreateFlashcards = if (flashcardViewModel != null && material.content.isNotBlank()) {
                {
                    materialToView = null
                    materialForFlashcards = material
                }
            } else null
        )
    }

    materialForFlashcards?.let { targetMaterial ->
        flashcardViewModel?.let { fcVm ->
            NoteFlashcardGeneratorDialog(
                initialNote = targetMaterial,
                availableNotes = allMaterials,
                isGenerating = isGeneratingFlashcards,
                onDismiss = { materialForFlashcards = null },
                onGenerate = { text, title, subj, code, noteId, noteTitle, count ->
                    fcVm.generateDeckFromNotes(
                        selectedText = text,
                        deckTitle = title,
                        subject = subj,
                        courseCode = code,
                        sourceNoteId = noteId,
                        sourceNoteTitle = noteTitle,
                        cardCount = count,
                        openImmediately = true
                    )
                    materialForFlashcards = null
                }
            )
        }
    }

    if (activeFlashcardSession != null && flashcardViewModel != null) {
        FlashcardReviewDialog(
            session = activeFlashcardSession,
            onClose = { flashcardViewModel.closeSession() },
            onFlip = { flashcardViewModel.flipCard() },
            onToggleHint = { flashcardViewModel.toggleHint() },
            onNext = { flashcardViewModel.nextCard() },
            onPrevious = { flashcardViewModel.previousCard() },
            onShuffle = { flashcardViewModel.shuffleSession() },
            onToggleUnmasteredOnly = { flashcardViewModel.toggleUnmasteredFilter() },
            onMarkMastered = { mastered -> flashcardViewModel.markCardMastered(mastered) },
            onRestart = { unmasteredOnly -> flashcardViewModel.restartSession(unmasteredOnly) },
            onResetProgress = { flashcardViewModel.resetDeckProgress() },
            onAddCard = { front, back, hint -> flashcardViewModel.addCardToSessionDeck(front, back, hint) }
        )
    }

    // Modal: Confirm Delete
    materialToDelete?.let { material ->
        AlertDialog(
            onDismissRequest = { materialToDelete = null },
            title = { Text("Delete Material") },
            text = { Text("Are you sure you want to permanently delete \"${material.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMaterial(material)
                    materialToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { materialToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Helper to determine icon, badge label and color for various media types
data class MediaTypeInfo(val label: String, val icon: ImageVector, val color: Color)

fun getMediaTypeInfo(fileName: String, mimeType: String): MediaTypeInfo {
    val lower = fileName.lowercase()
    val mime = mimeType.lowercase()

    return when {
        mime.contains("pdf") || lower.endsWith(".pdf") ->
            MediaTypeInfo("PDF Document", Icons.Default.PictureAsPdf, Color(0xFFDC2626))
        mime.contains("presentation") || mime.contains("powerpoint") || lower.endsWith(".ppt") || lower.endsWith(".pptx") ->
            MediaTypeInfo("PPT Presentation", Icons.Default.Slideshow, Color(0xFFEA580C))
        mime.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") ->
            MediaTypeInfo("Image Attachment", Icons.Default.Image, Color(0xFF16A34A))
        mime.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".avi") ->
            MediaTypeInfo("Video Lecture", Icons.Default.VideoLibrary, Color(0xFF9333EA))
        else ->
            MediaTypeInfo("Study File", Icons.Default.Description, Color(0xFF2563EB))
    }
}

@Composable
fun MaterialItemCard(
    material: StudyMaterial,
    isOwner: Boolean,
    isDownloading: Boolean = false,
    isDownloaded: Boolean = false,
    onView: () -> Unit,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit,
    onEdit: () -> Unit,
    onToggleShared: () -> Unit,
    onDelete: () -> Unit,
    onOpenDrive: () -> Unit,
    onCreateFlashcards: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val hasLocalDocument = remember(material.localPath) {
        material.localPath.isNotBlank() && File(material.localPath).exists()
    }
    val hasCloudFile = material.fileBase64.isNotBlank() || material.hasChunks || material.totalChunks > 0
    val hasDriveUrl = material.driveUrl.isNotBlank()
    val hasFileAttached = hasLocalDocument || hasCloudFile || material.fileName.isNotBlank() || hasDriveUrl
    val canOpenOrDownload = hasLocalDocument || hasCloudFile || hasDriveUrl
    val mediaInfo = remember(material.fileName, material.mimeType) {
        getMediaTypeInfo(material.fileName, material.mimeType)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge
                val (badgeColor, badgeText) = when (material.type) {
                    "QUESTION_PAPER" -> Pair(Color(0xFFEA580C), "QUESTION PAPER")
                    "QUESTION_BANK" -> Pair(Color(0xFF7C3AED), "QUESTION BANK")
                    else -> Pair(Color(0xFF2563EB), "LECTURE NOTE")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(badgeColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = badgeColor
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = material.courseCode.ifBlank { "GEN" },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (material.professor.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Prof. ${material.professor}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (material.isShared && isOwner) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AssistChip(
                        onClick = onToggleShared,
                        label = { Text("Shared", fontSize = 9.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(10.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                        modifier = Modifier.height(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Document Title (Can be renamed/edited by uploader)
            Text(
                text = material.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Subject, Semester & Uploader Name (Visible for everyone)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${material.subject} • ${material.semester}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Uploaded by",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Uploaded by ${material.userName.ifBlank { if (isOwner) "You (Owner)" else "Peer Student" }}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Document / Media attachment highlight box
            if (hasFileAttached) {
                val sizeFormatted = remember(material.fileSize) {
                    if (material.fileSize > 0) Formatter.formatFileSize(context, material.fileSize) else ""
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(mediaInfo.color.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            mediaInfo.color.copy(alpha = 0.22f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (isDownloaded) onOpenExternal() else if (!isDownloading) onDownload()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = mediaInfo.icon,
                        contentDescription = null,
                        tint = mediaInfo.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = material.fileName.ifBlank { "Attached ${mediaInfo.label}" },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isDownloading -> "Downloading file... $sizeFormatted"
                                isDownloaded -> "Downloaded • $sizeFormatted (Tap to Open)"
                                hasLocalDocument -> "Ready to Download • $sizeFormatted"
                                hasCloudFile -> "Cloud Synced • $sizeFormatted • Tap to Download"
                                hasDriveUrl -> "Available via Google Drive • $sizeFormatted"
                                else -> "Uploaded by ${material.userName.ifBlank { "peer" }} ($sizeFormatted)"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = mediaInfo.color
                        )
                    }

                    // Action icon on attachment card: Download before download, Open after download
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp,
                            color = mediaInfo.color
                        )
                    } else if (isDownloaded) {
                        IconButton(
                            onClick = onOpenExternal,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open File",
                                tint = mediaInfo.color,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download File",
                                tint = mediaInfo.color,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (canOpenOrDownload) {
                    if (isDownloaded) {
                        // Replace Download button with Open button once downloaded
                        Button(
                            onClick = onOpenExternal,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("open_file_button_${material.id}"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", fontSize = 11.5.sp)
                        }
                    } else {
                        // Show Download button before the file is downloaded
                        Button(
                            onClick = onDownload,
                            enabled = !isDownloading,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("download_file_button_${material.id}"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Downloading...", fontSize = 11.5.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", fontSize = 11.5.sp)
                            }
                        }
                    }

                    // View Note / Details button
                    OutlinedButton(
                        onClick = onView,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("view_note_button_${material.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Note", fontSize = 11.5.sp)
                    }
                } else {
                    // Text-only note without attached document
                    Button(
                        onClick = onView,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("view_note_button_${material.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Note", fontSize = 11.5.sp)
                    }
                }

                // Google Drive Link
                if (material.driveUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = onOpenDrive,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Drive", fontSize = 11.5.sp)
                    }
                }

                if (onCreateFlashcards != null && material.content.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCreateFlashcards,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("make_flashcards_card_btn_${material.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cards", fontSize = 11.5.sp)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Owner actions: Edit all details, Rename & Delete (ONLY accessible by the uploader)
                if (isOwner) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Material", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Material", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FilterDropdownChip(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selectedOption != "All",
            onClick = { expanded = true },
            label = { Text(label, maxLines = 1, softWrap = false) },
            trailingIcon = {
                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AddMaterialDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        subject: String,
        code: String,
        sem: String,
        prof: String,
        type: String,
        tags: List<String>,
        content: String,
        driveUrl: String,
        isShared: Boolean,
        fileName: String,
        localPath: String,
        fileSize: Long,
        mimeType: String,
        fileBase64: String,
        chunks: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("Semester 1") }
    var professor by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("LECTURE_NOTE") }
    var tagsInput by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var driveUrl by remember { mutableStateOf("") }
    var isShared by remember { mutableStateOf(true) }

    // Uploaded document metadata
    var attachedDoc by remember { mutableStateOf<DocumentMetadata?>(null) }
    var isSavingFile by remember { mutableStateOf(false) }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isSavingFile = true
                try {
                    val meta = DocumentStorageUtil.saveDocumentFromUri(context, uri)
                    attachedDoc = meta

                    // Auto-fill title if empty
                    if (title.isBlank()) {
                        title = meta.fileName.substringBeforeLast(".")
                    }
                    Toast.makeText(context, "Attached: ${meta.fileName}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load document: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isSavingFile = false
                }
            }
        }
    }

    val typeOptions = listOf(
        Pair("LECTURE_NOTE", "Lecture Note"),
        Pair("QUESTION_PAPER", "Question Paper"),
        Pair("QUESTION_BANK", "Question Bank")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Study Material") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // PDF, PPT, Image, Video File Attachment section
                val attachedMedia = attachedDoc?.let { getMediaTypeInfo(it.fileName, it.mimeType) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (attachedDoc != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = attachedMedia?.icon ?: Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = attachedMedia?.color ?: MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PDF, PPT, Image or Video File",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Upload PDF notes, PPT slides, lecture photos or tutorial videos. You will have full edit and download access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isSavingFile) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Importing file into study vault...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (attachedDoc != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = attachedDoc!!.fileName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (attachedDoc!!.fileSize <= DocumentStorageUtil.MAX_CLOUD_SYNC_SIZE_BYTES)
                                            "${attachedMedia?.label ?: "File"} • ${Formatter.formatFileSize(context, attachedDoc!!.fileSize)} • Cloud Synced"
                                        else
                                            "${attachedMedia?.label ?: "File"} • ${Formatter.formatFileSize(context, attachedDoc!!.fileSize)} (Please add Drive link)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (attachedDoc!!.fileSize <= DocumentStorageUtil.MAX_CLOUD_SYNC_SIZE_BYTES) Color(0xFF16A34A) else MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                IconButton(onClick = { attachedDoc = null }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (attachedDoc!!.fileSize > DocumentStorageUtil.MAX_CLOUD_SYNC_SIZE_BYTES) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "💡 Large file (>3MB): Please also paste a Google Drive link below so classmates can open it on their devices.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    docPickerLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.ms-powerpoint",
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "image/*",
                                            "video/*",
                                            "text/*",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select PDF, PPT, Image, or Video")
                            }
                        }
                    }
                }

                // Type selector
                Text("Select Material Category:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    typeOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document / Note Title *") },
                    placeholder = { Text("e.g. Tree Data Structures & Traversal") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject *") },
                    placeholder = { Text("Computer Science") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = { courseCode = it },
                        label = { Text("Course Code *") },
                        placeholder = { Text("CS201") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = professor,
                        onValueChange = { professor = it },
                        label = { Text("Professor") },
                        placeholder = { Text("Dr. Turing") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    label = { Text("Semester") },
                    placeholder = { Text("Semester 3") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Topic Tags (comma-separated)") },
                    placeholder = { Text("trees, bst, algorithms, midterm") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = driveUrl,
                    onValueChange = { driveUrl = it },
                    label = { Text("Google Drive / Cloud URL (Optional)") },
                    placeholder = { Text("https://drive.google.com/...") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Notes / Question Bank Text Content (Optional)") },
                    placeholder = { Text("Type or paste study materials, definitions, formulas, or practice questions here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isShared = !isShared }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isShared,
                        onCheckedChange = { isShared = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share with university peers in open campus vault",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && subject.isNotBlank() && courseCode.isNotBlank()) {
                        val tags = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        onConfirm(
                            title,
                            subject,
                            courseCode,
                            semester,
                            professor,
                            selectedType,
                            tags,
                            content,
                            driveUrl,
                            isShared,
                            attachedDoc?.fileName ?: "",
                            attachedDoc?.localPath ?: "",
                            attachedDoc?.fileSize ?: 0L,
                            attachedDoc?.mimeType ?: "",
                            attachedDoc?.fileBase64 ?: "",
                            attachedDoc?.chunks ?: emptyList()
                        )
                    }
                },
                enabled = title.isNotBlank() && subject.isNotBlank() && courseCode.isNotBlank() && !isSavingFile
            ) {
                Text("Save & Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Complete Edit Material Dialog: allows the uploader to rename the document, change subject,
 * course code, semester, professor, category type, tags, notes content, and Drive link.
 */
@Composable
fun EditMaterialDialog(
    material: StudyMaterial,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        subject: String,
        courseCode: String,
        semester: String,
        professor: String,
        type: String,
        tags: List<String>,
        content: String,
        driveUrl: String,
        isShared: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(material.title) }
    var subject by remember { mutableStateOf(material.subject) }
    var courseCode by remember { mutableStateOf(material.courseCode) }
    var semester by remember { mutableStateOf(material.semester) }
    var professor by remember { mutableStateOf(material.professor) }
    var selectedType by remember { mutableStateOf(material.type) }
    var tagsInput by remember { mutableStateOf(material.tags.joinToString(", ")) }
    var content by remember { mutableStateOf(material.content) }
    var driveUrl by remember { mutableStateOf(material.driveUrl) }
    var isShared by remember { mutableStateOf(material.isShared) }

    val typeOptions = listOf(
        Pair("LECTURE_NOTE", "Lecture Note"),
        Pair("QUESTION_PAPER", "Question Paper"),
        Pair("QUESTION_BANK", "Question Bank")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Study Material", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "As the author, you have full access to rename and update all material parameters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category type selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    typeOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document / Material Title *") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject Name *") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = { courseCode = it },
                        label = { Text("Course Code *") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = professor,
                        onValueChange = { professor = it },
                        label = { Text("Professor") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    label = { Text("Semester") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = driveUrl,
                    onValueChange = { driveUrl = it },
                    label = { Text("Google Drive / Cloud Link") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content Notes / Text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isShared = !isShared }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isShared,
                        onCheckedChange = { isShared = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share with peers in open campus vault",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && subject.isNotBlank() && courseCode.isNotBlank()) {
                        val tags = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        onConfirm(title, subject, courseCode, semester, professor, selectedType, tags, content, driveUrl, isShared)
                    }
                },
                enabled = title.isNotBlank() && subject.isNotBlank() && courseCode.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ViewMaterialDialog(
    material: StudyMaterial,
    isOwner: Boolean = false,
    isDownloading: Boolean = false,
    isDownloaded: Boolean = false,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit,
    onCreateFlashcards: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val hasLocalDocument = remember(material.localPath) {
        material.localPath.isNotBlank() && File(material.localPath).exists()
    }
    val hasCloudFile = material.fileBase64.isNotBlank() || material.hasChunks || material.totalChunks > 0
    val hasDriveUrl = material.driveUrl.isNotBlank()
    val hasFileAttached = hasLocalDocument || hasCloudFile || material.fileName.isNotBlank() || hasDriveUrl
    val canOpenOrDownload = hasLocalDocument || hasCloudFile || hasDriveUrl
    val mediaInfo = remember(material.fileName, material.mimeType) {
        getMediaTypeInfo(material.fileName, material.mimeType)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(material.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "${material.subject} • ${material.courseCode} • ${material.semester}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Access badge & File Uploader Name visible inside View Note dialog
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isOwner) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isOwner) "Full Access" else "Public Read/Download",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Uploaded by ${material.userName.ifBlank { "University Student" }}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (material.professor.isNotBlank()) {
                    Text("Instructor: Prof. ${material.professor}", style = MaterialTheme.typography.bodyMedium)
                }

                if (material.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        material.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // External App Open & Download Card
                if (hasFileAttached) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isDownloaded) onOpenExternal() else if (!isDownloading) onDownload()
                            },
                        colors = CardDefaults.cardColors(containerColor = mediaInfo.color.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = mediaInfo.icon,
                                contentDescription = null,
                                tint = mediaInfo.color,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = material.fileName.ifBlank { "Attached File" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = when {
                                        isDownloading -> "Downloading file..."
                                        isDownloaded -> "Downloaded • Tap to open in external app"
                                        hasLocalDocument -> "Ready to Download • Tap Download"
                                        hasCloudFile -> "Cloud Synced • Tap to download"
                                        hasDriveUrl -> "Available via Google Drive"
                                        else -> "Uploaded file by ${material.userName.ifBlank { "student" }}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = mediaInfo.color
                                )
                            } else if (isDownloaded) {
                                IconButton(onClick = onOpenExternal) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = mediaInfo.color)
                                }
                            } else {
                                IconButton(onClick = onDownload) {
                                    Icon(Icons.Default.Download, contentDescription = "Download File", tint = mediaInfo.color)
                                }
                            }
                        }
                    }
                }

                if (material.driveUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(material.driveUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Open in Google Drive / Cloud Link",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (material.content.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Academic Notes (Long-press text to select):", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            SelectionContainer {
                                Text(
                                    text = material.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onCreateFlashcards != null && material.content.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCreateFlashcards,
                        modifier = Modifier.testTag("view_note_make_flashcards_btn")
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flashcards")
                    }
                }
                if (canOpenOrDownload) {
                    if (isDownloaded) {
                        Button(onClick = onOpenExternal) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open File")
                        }
                    } else {
                        Button(onClick = onDownload, enabled = !isDownloading) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Downloading...")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download")
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
