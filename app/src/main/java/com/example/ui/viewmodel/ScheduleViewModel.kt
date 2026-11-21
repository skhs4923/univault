package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ScheduleItem
import com.example.data.repository.AiTutorRepository
import com.example.data.repository.ScheduleRepository
import com.example.notifications.ClassReminderNotificationHelper
import com.example.notifications.ClassReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ScheduleViewModel"
private const val MAX_TIMETABLE_IMAGE_DIM = 1200

data class ScheduleMetrics(
    val totalMilestones: Int = 0,
    val completedMilestones: Int = 0,
    val upcomingExams: Int = 0,
    val completionPercentage: Float = 0f
)

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val aiRepository: AiTutorRepository = AiTutorRepository(),
    private val appContext: Context? = null,
    private val collegeId: String = "",
    private val collegeName: String = ""
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isStatusError = MutableStateFlow(false)
    val isStatusError: StateFlow<Boolean> = _isStatusError.asStateFlow()

    private val _isScanningTimetable = MutableStateFlow(false)
    val isScanningTimetable: StateFlow<Boolean> = _isScanningTimetable.asStateFlow()

    private val _remindersEnabled = MutableStateFlow(
        appContext?.let { ClassReminderNotificationHelper.isRemindersEnabled(it) } ?: true
    )
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    private val _scheduledReminderCount = MutableStateFlow(0)
    val scheduledReminderCount: StateFlow<Int> = _scheduledReminderCount.asStateFlow()

    val schedules: StateFlow<List<ScheduleItem>> = repository.observeSchedules()
        .onEach { items -> syncClassRemindersInternal(items) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val metrics: StateFlow<ScheduleMetrics> = schedules.map { items ->
        val milestones = items.filter { it.type == "MILESTONE" }
        val exams = items.filter { it.type == "EXAM" && !it.isCompleted }
        val completed = milestones.count { it.isCompleted }
        val pct = if (milestones.isNotEmpty()) (completed.toFloat() / milestones.size.toFloat()) * 100f else 0f
        ScheduleMetrics(
            totalMilestones = milestones.size,
            completedMilestones = completed,
            upcomingExams = exams.size,
            completionPercentage = pct
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ScheduleMetrics()
    )

    private fun syncClassRemindersInternal(items: List<ScheduleItem>) {
        val ctx = appContext ?: return
        if (collegeId.isBlank()) return
        val count = ClassReminderScheduler.syncAndScheduleForCollege(
            context = ctx,
            collegeId = collegeId,
            collegeName = collegeName,
            classes = items
        )
        _scheduledReminderCount.value = count
    }

    fun refreshClassReminders(context: Context) {
        val safeContext = context.applicationContext
        if (collegeId.isBlank()) return
        val count = ClassReminderScheduler.syncAndScheduleForCollege(
            context = safeContext,
            collegeId = collegeId,
            collegeName = collegeName,
            classes = schedules.value
        )
        _scheduledReminderCount.value = count
    }

    fun setClassRemindersEnabled(context: Context, enabled: Boolean) {
        val safeContext = context.applicationContext
        ClassReminderNotificationHelper.setRemindersEnabled(safeContext, enabled)
        _remindersEnabled.value = enabled
        val count = if (collegeId.isNotBlank()) {
            ClassReminderScheduler.syncAndScheduleForCollege(
                context = safeContext,
                collegeId = collegeId,
                collegeName = collegeName,
                classes = schedules.value
            )
        } else {
            ClassReminderScheduler.rescheduleAllStoredReminders(safeContext)
        }
        _scheduledReminderCount.value = count
        _isStatusError.value = false
        _statusMessage.value = if (enabled) {
            if (count > 0) {
                "15-minute reminders active for $count upcoming ${if (count == 1) "item" else "items"}."
            } else {
                "15-minute schedule reminders enabled."
            }
        } else {
            "15-minute schedule reminders paused."
        }
    }

    fun notifyNotificationPermissionDenied() {
        _isStatusError.value = true
        _statusMessage.value = "Allow notifications to receive 15-minute reminders before scheduled events."
    }

    fun addScheduleItem(
        title: String,
        courseCode: String,
        dayOfWeek: String,
        timeSlot: String,
        type: String,
        dueDate: String,
        syllabusNotes: String
    ) {
        viewModelScope.launch {
            try {
                val item = ScheduleItem(
                    title = title.trim().take(200),
                    courseCode = courseCode.trim().uppercase().ifBlank { "CLASS" }.take(50),
                    dayOfWeek = dayOfWeek.trim().ifBlank { "Monday" }.take(50),
                    timeSlot = timeSlot.trim().ifBlank { "09:00 - 10:00 AM" }.take(100),
                    type = type,
                    dueDate = dueDate.trim().take(50),
                    isCompleted = false,
                    syllabusNotes = syllabusNotes.trim().take(5000)
                )
                repository.addSchedule(item)
                _isStatusError.value = false
                _statusMessage.value = "Added $type: \"${item.title}\" to schedule."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isStatusError.value = true
                _statusMessage.value = "Failed to add schedule: ${e.localizedMessage ?: "Error"}"
            }
        }
    }

    fun importTimetableFromPhoto(
        context: Context,
        imageUri: Uri,
        targetType: String = "CLASS",
        onImported: () -> Unit = {}
    ) {
        if (_isScanningTimetable.value) return
        val appContext = context.applicationContext

        viewModelScope.launch {
            _isScanningTimetable.value = true
            _statusMessage.value = null
            _isStatusError.value = false

            val labelPlural = when (targetType) {
                "EXAM" -> "exam dates"
                "MILESTONE" -> "milestones"
                else -> "classes"
            }
            val labelSingular = when (targetType) {
                "EXAM" -> "exam date"
                "MILESTONE" -> "milestone"
                else -> "class"
            }
            val destinationTabName = when (targetType) {
                "EXAM" -> "Exam Dates"
                "MILESTONE" -> "Syllabus Milestones"
                else -> "Timetable Classes"
            }

            var decodedBitmap: Bitmap? = null
            try {
                decodedBitmap = withContext(Dispatchers.IO) {
                    decodeScaledTimetableBitmap(appContext, imageUri)
                }

                val extractedEntries = aiRepository.extractScheduleItemsFromImage(decodedBitmap, targetType)
                if (extractedEntries.isEmpty()) {
                    _isStatusError.value = true
                    _statusMessage.value = "No $labelPlural were detected in that photo. Try a clearer, well-lit image."
                    return@launch
                }

                var addedCount = 0
                for (entry in extractedEntries) {
                    val safeTitle = entry.title.trim().take(200)
                    if (safeTitle.isBlank()) continue
                    val safeCode = entry.courseCode.trim().uppercase().ifBlank { targetType }.take(50)
                    val safeDay = entry.dayOfWeek.trim().ifBlank { "Monday" }.take(50)
                    val safeDueDate = if (targetType == "CLASS") "" else entry.dueDate.trim().take(50)
                    val safeSlot = entry.timeSlot.trim().ifBlank { "09:00 - 10:00 AM" }.take(100)
                    val safeNotes = entry.notes.trim().take(5000)

                    repository.addSchedule(
                        ScheduleItem(
                            title = safeTitle,
                            courseCode = safeCode,
                            dayOfWeek = safeDay,
                            timeSlot = safeSlot,
                            type = targetType,
                            dueDate = safeDueDate,
                            isCompleted = false,
                            syllabusNotes = safeNotes
                        )
                    )
                    addedCount++
                }

                if (addedCount > 0) {
                    _isStatusError.value = false
                    _statusMessage.value = "AI detected and added $addedCount ${if (addedCount == 1) labelSingular else labelPlural} to $destinationTabName."
                    onImported()
                } else {
                    _isStatusError.value = true
                    _statusMessage.value = "Could not parse any valid $labelPlural from the selected photo."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Schedule photo scan timed out", e)
                _isStatusError.value = true
                _statusMessage.value = "Photo scan timed out. Please retry with a clearer, closer photo."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan schedule photo", e)
                _isStatusError.value = true
                _statusMessage.value = e.localizedMessage ?: "Failed to scan photo with AI."
            } finally {
                decodedBitmap?.recycle()
                _isScanningTimetable.value = false
            }
        }
    }

    fun toggleComplete(item: ScheduleItem) {
        viewModelScope.launch {
            try {
                repository.toggleCompleted(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isStatusError.value = true
                _statusMessage.value = "Could not update status: ${e.localizedMessage}"
            }
        }
    }

    fun deleteItem(item: ScheduleItem) {
        viewModelScope.launch {
            try {
                repository.deleteSchedule(item.id)
                _isStatusError.value = false
                _statusMessage.value = "Removed \"${item.title}\""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isStatusError.value = true
                _statusMessage.value = "Delete failed: ${e.localizedMessage}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
        _isStatusError.value = false
    }

    companion object {
        internal fun decodeScaledTimetableBitmap(context: Context, uri: Uri): Bitmap {
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val source = ImageDecoder.createSource(resolver, uri)
                    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val width = info.size.width
                        val height = info.size.height
                        val maxSide = maxOf(width, height)
                        if (maxSide > MAX_TIMETABLE_IMAGE_DIM && maxSide > 0) {
                            val scale = MAX_TIMETABLE_IMAGE_DIM.toFloat() / maxSide.toFloat()
                            decoder.setTargetSize(
                                (width * scale).toInt().coerceAtLeast(1),
                                (height * scale).toInt().coerceAtLeast(1)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ImageDecoder failed, falling back to BitmapFactory", e)
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val opened = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
                true
            } ?: false
            if (!opened) {
                throw IllegalArgumentException("Unable to open the selected timetable photo.")
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("Selected file is not a valid image.")
            }

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > MAX_TIMETABLE_IMAGE_DIM * 2 ||
                (bounds.outHeight / sampleSize) > MAX_TIMETABLE_IMAGE_DIM * 2
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw IllegalStateException("Could not decode the selected timetable image.")

            val orientation = try {
                resolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val rotated = rotateBitmapIfNeeded(sampledBitmap, orientation)
            if (rotated !== sampledBitmap) {
                sampledBitmap.recycle()
            }

            val maxSide = maxOf(rotated.width, rotated.height)
            if (maxSide <= MAX_TIMETABLE_IMAGE_DIM) {
                return rotated
            }

            val scale = MAX_TIMETABLE_IMAGE_DIM.toFloat() / maxSide.toFloat()
            val targetWidth = (rotated.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (rotated.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(rotated, targetWidth, targetHeight, true)
            if (scaled !== rotated) {
                rotated.recycle()
            }
            return scaled
        }

        private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
