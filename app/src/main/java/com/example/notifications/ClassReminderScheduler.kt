package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.ScheduleItem
import com.example.util.TimetableScheduleHelper

object ClassReminderScheduler {

    fun syncAndScheduleForCollege(
        context: Context,
        collegeId: String,
        collegeName: String,
        classes: List<ScheduleItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val appContext = context.applicationContext
        val (removedEntries, activeForCollege) = ClassReminderNotificationHelper.replaceCollegeReminders(
            context = appContext,
            collegeId = collegeId,
            collegeName = collegeName,
            items = classes
        )

        removedEntries.forEach { cancelEntryAlarm(appContext, it) }

        if (!ClassReminderNotificationHelper.isRemindersEnabled(appContext) ||
            !ClassReminderNotificationHelper.hasNotificationPermission(appContext)
        ) {
            activeForCollege.forEach { cancelEntryAlarm(appContext, it) }
            return 0
        }

        ClassReminderNotificationHelper.ensureNotificationChannel(appContext)
        var scheduledCount = 0
        activeForCollege.forEach { entry ->
            if (scheduleEntryAlarm(appContext, entry, nowMillis) > 0L) {
                scheduledCount++
            }
        }
        return scheduledCount
    }

    fun rescheduleAllStoredReminders(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val appContext = context.applicationContext
        val allEntries = ClassReminderNotificationHelper.getAllReminders(appContext)
        if (!ClassReminderNotificationHelper.isRemindersEnabled(appContext) ||
            !ClassReminderNotificationHelper.hasNotificationPermission(appContext)
        ) {
            allEntries.forEach { cancelEntryAlarm(appContext, it) }
            return 0
        }

        ClassReminderNotificationHelper.ensureNotificationChannel(appContext)
        var scheduledCount = 0
        allEntries.forEach { entry ->
            if (scheduleEntryAlarm(appContext, entry, nowMillis) > 0L) {
                scheduledCount++
            }
        }
        return scheduledCount
    }

    fun scheduleEntryAlarm(
        context: Context,
        entry: ScheduledClassReminder,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return 0L

        val triggerAtMillis = if (entry.type == "CLASS") {
            TimetableScheduleHelper.computeNextReminderTriggerMillis(
                dayOfWeek = entry.dayOfWeek,
                timeSlot = entry.timeSlot,
                leadMinutes = ClassReminderNotificationHelper.LEAD_MINUTES,
                nowMillis = nowMillis
            )
        } else {
            val datedTrigger = TimetableScheduleHelper.computeDatedReminderTriggerMillis(
                dueDate = entry.dueDate,
                timeSlot = entry.timeSlot,
                leadMinutes = ClassReminderNotificationHelper.LEAD_MINUTES,
                nowMillis = nowMillis
            )
            if (datedTrigger == null || datedTrigger <= nowMillis) {
                cancelEntryAlarm(appContext, entry)
                return 0L
            }
            datedTrigger
        }

        val pendingIntent = buildAlarmPendingIntent(
            context = appContext,
            entry = entry,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return 0L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        return triggerAtMillis
    }

    fun cancelEntryAlarm(context: Context, entry: ScheduledClassReminder) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildAlarmPendingIntent(
            context = appContext,
            entry = entry,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildAlarmPendingIntent(
        context: Context,
        entry: ScheduledClassReminder,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ClassReminderNotificationHelper.ACTION_CLASS_REMINDER
            putExtra(ClassReminderNotificationHelper.EXTRA_ENTRY_KEY, entry.key)
        }
        return PendingIntent.getBroadcast(context, entry.requestCode, intent, flags)
    }
}
