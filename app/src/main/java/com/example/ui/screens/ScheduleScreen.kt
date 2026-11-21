package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ScheduleItem
import com.example.notifications.ClassReminderNotificationHelper
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.util.ArrangedClassItem
import com.example.util.TimetableScheduleHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val schedules by viewModel.schedules.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val isScanningTimetable by viewModel.isScanningTimetable.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isStatusError by viewModel.isStatusError.collectAsState()
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val scheduledReminderCount by viewModel.scheduledReminderCount.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Timetable Classes, 1: Exam Dates, 2: Syllabus Milestones
    var showAddDialog by remember { mutableStateOf(false) }
    var showAllClasses by remember { mutableStateOf(false) }
    var showAllExams by remember { mutableStateOf(false) }
    var showAllMilestones by remember { mutableStateOf(false) }
    var pendingPhotoTargetType by remember { mutableStateOf("CLASS") }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hasNotificationPermission by remember {
        mutableStateOf(ClassReminderNotificationHelper.hasNotificationPermission(context))
    }
    var promptedNotificationsOnce by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = ClassReminderNotificationHelper.hasNotificationPermission(context)
                if (hasNotificationPermission) {
                    viewModel.refreshClassReminders(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = ClassReminderNotificationHelper.hasNotificationPermission(context)
        if (isGranted && hasNotificationPermission) {
            viewModel.setClassRemindersEnabled(context, true)
        } else {
            viewModel.notifyNotificationPermissionDenied()
        }
    }

    fun requestOrToggleReminders() {
        val hasRuntime = ClassReminderNotificationHelper.hasRuntimeNotificationPermission(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasRuntime) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(settingsIntent)
            return
        }
        viewModel.setClassRemindersEnabled(context, !remindersEnabled)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val timetablePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val targetType = pendingPhotoTargetType
            val targetTab = when (targetType) {
                "EXAM" -> 1
                "MILESTONE" -> 2
                else -> 0
            }
            selectedTab = targetTab
            viewModel.importTimetableFromPhoto(context, uri, targetType = targetType) {
                selectedTab = targetTab
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !ClassReminderNotificationHelper.hasRuntimeNotificationPermission(context) &&
                    !promptedNotificationsOnce
                ) {
                    promptedNotificationsOnce = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    LaunchedEffect(statusMessage, isStatusError) {
        if (statusMessage != null && !isStatusError) {
            delay(4000L)
            viewModel.clearStatusMessage()
        }
    }

    val allClasses = remember(schedules) {
        schedules.filter { it.type == "CLASS" }
    }
    val allExams = remember(schedules) {
        schedules.filter { it.type == "EXAM" }
    }
    val allMilestones = remember(schedules) {
        schedules.filter { it.type == "MILESTONE" }
    }

    val arrangedClasses = remember(allClasses, showAllClasses, nowMillis) {
        TimetableScheduleHelper.arrangeClasses(
            classes = allClasses,
            showAll = showAllClasses,
            nowMillis = nowMillis
        )
    }
    val arrangedExams = remember(allExams, showAllExams, nowMillis) {
        TimetableScheduleHelper.arrangeDatedItems(
            items = allExams,
            showAll = showAllExams,
            nowMillis = nowMillis
        )
    }
    val arrangedMilestones = remember(allMilestones, showAllMilestones, nowMillis) {
        TimetableScheduleHelper.arrangeDatedItems(
            items = allMilestones,
            showAll = showAllMilestones,
            nowMillis = nowMillis
        )
    }

    val activeFullCount = when (selectedTab) {
        0 -> allClasses.size
        1 -> allExams.size
        else -> allMilestones.size
    }
    val activeArrangedList = when (selectedTab) {
        0 -> arrangedClasses
        1 -> arrangedExams
        else -> arrangedMilestones
    }
    val isShowingAllActiveTab = when (selectedTab) {
        0 -> showAllClasses
        1 -> showAllExams
        else -> showAllMilestones
    }
    val activeTypeKey = when (selectedTab) {
        1 -> "EXAM"
        2 -> "MILESTONE"
        else -> "CLASS"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header Summary Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Academic Schedule & Deadlines",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${allClasses.size} Classes • ${metrics.upcomingExams} Exams • ${metrics.totalMilestones} Milestones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Status / AI Scan Result Banner
            statusMessage?.let { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("schedule_status_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isStatusError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isStatusError) Icons.Default.ErrorOutline else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusMessage() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss message",
                                tint = if (isStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Tabs: Timetable Classes, Exam Dates, Syllabus Milestones
            val tabs = listOf("Timetable Classes", "Exam Dates", "Syllabus Milestones")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // Upcoming / All & 15m Reminder controls (shared across Timetable Classes, Exam Dates & Syllabus Milestones)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (activeFullCount > 0) {
                    val allLabel = when (selectedTab) {
                        0 -> "All Classes ($activeFullCount)"
                        1 -> "All Exams ($activeFullCount)"
                        else -> "All Milestones ($activeFullCount)"
                    }
                    FilterChip(
                        selected = !isShowingAllActiveTab,
                        onClick = {
                            when (selectedTab) {
                                0 -> showAllClasses = false
                                1 -> showAllExams = false
                                else -> showAllMilestones = false
                            }
                        },
                        label = {
                            Text(
                                text = "Upcoming",
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("timetable_upcoming_chip")
                    )
                    FilterChip(
                        selected = isShowingAllActiveTab,
                        onClick = {
                            when (selectedTab) {
                                0 -> showAllClasses = true
                                1 -> showAllExams = true
                                else -> showAllMilestones = true
                            }
                        },
                        label = {
                            Text(
                                text = allLabel,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("timetable_all_chip")
                    )
                }

                val alertsActive = hasNotificationPermission && remindersEnabled
                FilterChip(
                    selected = alertsActive,
                    onClick = { requestOrToggleReminders() },
                    label = {
                        val labelText = when {
                            !hasNotificationPermission -> "Enable 15m Alerts"
                            alertsActive && scheduledReminderCount > 0 -> "15m Alerts On ($scheduledReminderCount)"
                            alertsActive -> "15m Alerts On"
                            else -> "15m Alerts Off"
                        }
                        Text(
                            text = labelText,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (alertsActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("class_reminders_toggle_chip")
                )
            }

            if (activeArrangedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 128.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No ${tabs[selectedTab].lowercase()} scheduled yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Add Photo AI' at the bottom right to detect ${tabs[selectedTab].lowercase()} from a photo, or tap '+' to add manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("schedule_list"),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 156.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeArrangedList, key = { it.item.id }) { arranged ->
                        ScheduleItemCard(
                            item = arranged.item,
                            arrangedClass = arranged,
                            onDelete = { viewModel.deleteItem(arranged.item) }
                        )
                    }
                    if (!isShowingAllActiveTab && activeFullCount > activeArrangedList.size) {
                        val seeAllText = when (selectedTab) {
                            0 -> "See all $activeFullCount classes at once"
                            1 -> "See all $activeFullCount exams at once"
                            else -> "See all $activeFullCount milestones at once"
                        }
                        item(key = "see_all_items_footer_$selectedTab") {
                            OutlinedButton(
                                onClick = {
                                    when (selectedTab) {
                                        0 -> showAllClasses = true
                                        1 -> showAllExams = true
                                        else -> showAllMilestones = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(48.dp)
                                    .testTag("see_all_classes_button")
                            ) {
                                Text(seeAllText)
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isScanningTimetable) {
                        pendingPhotoTargetType = activeTypeKey
                        timetablePhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                },
                expanded = true,
                icon = {
                    if (isScanningTimetable) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Add schedule photo with AI"
                        )
                    }
                },
                text = {
                    Text(
                        text = if (isScanningTimetable) "Scanning..." else "Add Photo AI",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("scan_timetable_photo_button")
            )

            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_schedule_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Schedule Item")
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            initialType = activeTypeKey,
            isScanningTimetable = isScanningTimetable,
            onScanTimetablePhoto = { chosenType ->
                showAddDialog = false
                pendingPhotoTargetType = chosenType
                timetablePhotoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showAddDialog = false },
            onConfirm = { title, code, day, slot, type, due, notes ->
                viewModel.addScheduleItem(title, code, day, slot, type, due, notes)
                showAddDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !ClassReminderNotificationHelper.hasRuntimeNotificationPermission(context) &&
                    !promptedNotificationsOnce
                ) {
                    promptedNotificationsOnce = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }
}


@Composable
fun ScheduleItemCard(
    item: ScheduleItem,
    arrangedClass: ArrangedClassItem? = null,
    onDelete: () -> Unit
) {
    val isExam = item.type == "EXAM"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (typeColor, typeLabel) = when (item.type) {
                        "EXAM" -> Pair(Color(0xFFDC2626), "EXAM DEADLINE")
                        "MILESTONE" -> Pair(Color(0xFF0D9488), "SYLLABUS MILESTONE")
                        else -> Pair(Color(0xFF4F46E5), "TIMETABLE CLASS")
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(typeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(typeLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = typeColor)
                    }

                    if (arrangedClass != null) {
                        if (arrangedClass.isOngoing) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE NOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                            }
                        } else if (arrangedClass.isNextUp) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("NEXT UP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        item.courseCode,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                val datePrefix = arrangedClass?.dateLabel ?: if (item.type == "CLASS") item.dayOfWeek else item.dueDate.ifBlank { "Scheduled" }
                val scheduleText = if (item.timeSlot.isNotBlank()) "$datePrefix • ${item.timeSlot}" else datePrefix
                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExam && arrangedClass?.isPast != true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.syllabusNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Details: ${item.syllabusNotes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AddScheduleDialog(
    initialType: String,
    isScanningTimetable: Boolean = false,
    onScanTimetablePhoto: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        code: String,
        day: String,
        slot: String,
        type: String,
        dueDate: String,
        syllabusNotes: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(initialType) }
    var dayOfWeek by remember { mutableStateOf("Monday") }
    var timeSlot by remember { mutableStateOf("10:00 - 11:30 AM") }
    var dueDate by remember { mutableStateOf("") }
    var syllabusNotes by remember { mutableStateOf("") }

    val types = listOf("CLASS" to "Class", "EXAM" to "Exam", "MILESTONE" to "Milestone")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Academic Schedule") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { (typeKey, label) ->
                        FilterChip(
                            selected = selectedType == typeKey,
                            onClick = { selectedType = typeKey },
                            label = { Text(label) }
                        )
                    }
                }

                val aiImportButtonLabel = when (selectedType) {
                    "EXAM" -> "Import Exam Dates from Photo (AI)"
                    "MILESTONE" -> "Import Milestones from Photo (AI)"
                    else -> "Import Classes from Timetable Photo (AI)"
                }
                OutlinedButton(
                    onClick = { onScanTimetablePhoto(selectedType) },
                    enabled = !isScanningTimetable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_scan_timetable_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(aiImportButtonLabel)
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    placeholder = { Text("e.g. Operating Systems Lecture or Midterm Exam") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = courseCode,
                    onValueChange = { courseCode = it },
                    label = { Text("Course Code *") },
                    placeholder = { Text("CS201") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedType == "CLASS") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dayOfWeek,
                            onValueChange = { dayOfWeek = it },
                            label = { Text("Day of Week") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = timeSlot,
                            onValueChange = { timeSlot = it },
                            label = { Text("Time Slot") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dueDate,
                            onValueChange = { dueDate = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            placeholder = { Text("2026-11-25") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = timeSlot,
                            onValueChange = { timeSlot = it },
                            label = { Text("Time Slot") },
                            placeholder = { Text("02:00 - 05:00 PM") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = syllabusNotes,
                    onValueChange = { syllabusNotes = it },
                    label = { Text("Syllabus & Topics covered") },
                    placeholder = { Text("Chapters 1-4, CPU Scheduling, Virtual Memory") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && courseCode.isNotBlank()) {
                        onConfirm(title, courseCode, dayOfWeek, timeSlot, selectedType, dueDate, syllabusNotes)
                    }
                },
                enabled = title.isNotBlank() && courseCode.isNotBlank()
            ) {
                Text("Add to Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
