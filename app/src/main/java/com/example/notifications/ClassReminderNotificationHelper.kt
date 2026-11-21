package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ScheduleItem
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class ScheduledClassReminder(
    val key: String,
    val collegeId: String,
    val collegeName: String,
    val itemId: String,
    val title: String,
    val courseCode: String,
    val type: String = "CLASS",
    val dayOfWeek: String = "",
    val dueDate: String = "",
    val timeSlot: String,
    val syllabusNotes: String,
    val requestCode: Int
)

object ClassReminderNotificationHelper {

    const val CHANNEL_ID = "univault_class_reminders"
    const val LEAD_MINUTES = 15
    const val ACTION_CLASS_REMINDER = "com.example.ACTION_CLASS_REMINDER"
    const val EXTRA_ENTRY_KEY = "extra_entry_key"

    private const val PREFS_NAME = "univault_class_reminders_prefs"
    private const val KEY_ENABLED = "reminders_enabled"
    private const val KEY_ENTRIES = "scheduled_entries_json"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRemindersEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasRuntimeNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_class_reminders_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_class_reminders_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    @Synchronized
    fun replaceCollegeReminders(
        context: Context,
        collegeId: String,
        collegeName: String,
        items: List<ScheduleItem>
    ): Pair<List<ScheduledClassReminder>, List<ScheduledClassReminder>> {
        val currentAll = getAllReminders(context)
        val previousForCollege = currentAll.filter { it.collegeId == collegeId }
        val remainingOtherColleges = currentAll.filterNot { it.collegeId == collegeId }

        val supportedTypes = setOf("CLASS", "EXAM", "MILESTONE")
        val updatedForCollege = items
            .filter { it.type in supportedTypes && it.id.isNotBlank() && it.title.isNotBlank() }
            .map { item ->
                val key = "$collegeId::${item.id}"
                ScheduledClassReminder(
                    key = key,
                    collegeId = collegeId,
                    collegeName = collegeName,
                    itemId = item.id,
                    title = item.title.trim(),
                    courseCode = item.courseCode.trim().uppercase(),
                    type = item.type,
                    dayOfWeek = item.dayOfWeek.trim(),
                    dueDate = item.dueDate.trim(),
                    timeSlot = item.timeSlot.trim(),
                    syllabusNotes = item.syllabusNotes.trim(),
                    requestCode = stableRequestCode(key)
                )
            }

        val activeKeys = updatedForCollege.mapTo(mutableSetOf()) { it.key }
        val removed = previousForCollege.filterNot { it.key in activeKeys }

        saveAllReminders(context, remainingOtherColleges + updatedForCollege)
        return removed to updatedForCollege
    }

    fun getAllReminders(context: Context): List<ScheduledClassReminder> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<ScheduledClassReminder>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val key = obj.optString("key")
                if (key.isBlank()) continue
                result.add(
                    ScheduledClassReminder(
                        key = key,
                        collegeId = obj.optString("collegeId"),
                        collegeName = obj.optString("collegeName"),
                        itemId = obj.optString("itemId"),
                        title = obj.optString("title"),
                        courseCode = obj.optString("courseCode"),
                        type = obj.optString("type", "CLASS").ifBlank { "CLASS" },
                        dayOfWeek = obj.optString("dayOfWeek"),
                        dueDate = obj.optString("dueDate"),
                        timeSlot = obj.optString("timeSlot"),
                        syllabusNotes = obj.optString("syllabusNotes"),
                        requestCode = obj.optInt("requestCode", stableRequestCode(key))
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findReminderByKey(context: Context, key: String): ScheduledClassReminder? =
        getAllReminders(context).firstOrNull { it.key == key }

    private fun saveAllReminders(context: Context, entries: List<ScheduledClassReminder>) {
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject().apply {
                    put("key", entry.key)
                    put("collegeId", entry.collegeId)
                    put("collegeName", entry.collegeName)
                    put("itemId", entry.itemId)
                    put("title", entry.title)
                    put("courseCode", entry.courseCode)
                    put("type", entry.type)
                    put("dayOfWeek", entry.dayOfWeek)
                    put("dueDate", entry.dueDate)
                    put("timeSlot", entry.timeSlot)
                    put("syllabusNotes", entry.syllabusNotes)
                    put("requestCode", entry.requestCode)
                }
            )
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    @SuppressLint("MissingPermission")
    fun showClassReminderNotification(context: Context, entry: ScheduledClassReminder) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            entry.requestCode,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val codePrefix = if (entry.courseCode.isNotBlank()) "${entry.courseCode} • " else ""
        val titleText = when (entry.type) {
            "EXAM" -> "${codePrefix}Exam starts in $LEAD_MINUTES min"
            "MILESTONE" -> "${codePrefix}Milestone due in $LEAD_MINUTES min"
            else -> "${codePrefix}Class starts in $LEAD_MINUTES min"
        }
        val whenText = if (entry.type == "CLASS") {
            "${entry.dayOfWeek}, ${entry.timeSlot}"
        } else {
            listOf(entry.dueDate, entry.timeSlot).filter { it.isNotBlank() }.joinToString(", ")
        }
        val summaryText = "${entry.title} (${entry.timeSlot})"
        val expandedText = buildString {
            append("${entry.title} • $whenText")
            if (entry.collegeName.isNotBlank()) {
                append("\n${entry.collegeName}")
            }
            if (entry.syllabusNotes.isNotBlank()) {
                append("\nDetails: ${entry.syllabusNotes}")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_class_reminder)
            .setContentTitle(titleText)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(entry.requestCode, notification)
    }

    internal fun stableRequestCode(key: String): Int {
        val hash = abs(key.hashCode())
        return if (hash == 0) 1001 else hash
    }
}
