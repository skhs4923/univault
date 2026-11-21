package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext

        when (action) {
            ClassReminderNotificationHelper.ACTION_CLASS_REMINDER -> {
                val entryKey = intent.getStringExtra(ClassReminderNotificationHelper.EXTRA_ENTRY_KEY)
                    ?: return
                val entry = ClassReminderNotificationHelper.findReminderByKey(appContext, entryKey)
                    ?: return

                if (ClassReminderNotificationHelper.isRemindersEnabled(appContext)) {
                    ClassReminderNotificationHelper.showClassReminderNotification(appContext, entry)
                    if (entry.type == "CLASS") {
                        // Schedule next week's recurring reminder, advancing 1 minute past trigger to avoid double firing
                        ClassReminderScheduler.scheduleEntryAlarm(
                            context = appContext,
                            entry = entry,
                            nowMillis = System.currentTimeMillis() + 60_000L
                        )
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                ClassReminderScheduler.rescheduleAllStoredReminders(appContext)
            }
        }
    }
}
