package com.example.util

import com.example.data.model.ScheduleItem
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ArrangedClassItem(
    val item: ScheduleItem,
    val occurrenceMillis: Long,
    val dayDelta: Int,
    val startMinutes: Int,
    val dateLabel: String,
    val isOngoing: Boolean,
    val isNextUp: Boolean = false,
    val isPast: Boolean = false
)

object TimetableScheduleHelper {

    private val TIME_TOKEN_REGEX = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
    private val RANGE_SPLIT_REGEX = Regex("""\s*(?:-|–|—|\bto\b)\s*""", RegexOption.IGNORE_CASE)

    private val FULL_DATE_PATTERNS = listOf(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "dd-MM-yyyy",
        "dd/MM/yyyy",
        "MM/dd/yyyy",
        "MMM d, yyyy",
        "MMM d yyyy",
        "d MMM yyyy",
        "MMMM d, yyyy",
        "d MMMM yyyy"
    )

    private val PARTIAL_DATE_PATTERNS = listOf(
        "MMM d",
        "d MMM",
        "MMMM d",
        "d MMMM",
        "dd/MM",
        "dd-MM"
    )

    fun arrangeClasses(
        classes: List<ScheduleItem>,
        showAll: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ArrangedClassItem> {
        if (classes.isEmpty()) return emptyList()

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val todayStart = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayIso = calendarDayToIso(now.get(Calendar.DAY_OF_WEEK))
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val shortDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        val sorted = classes.map { item ->
            val targetIso = parseWeekdayIso(item.dayOfWeek)
            val (startMin, endMin) = parseTimeRangeMinutes(item.timeSlot)
            var dayDelta = (targetIso - todayIso + 7) % 7
            val effectiveEnd = endMin ?: (startMin + 60)

            val isOngoing = dayDelta == 0 && nowMinutes >= startMin && nowMinutes < effectiveEnd
            if (dayDelta == 0 && nowMinutes >= effectiveEnd) {
                dayDelta = 7
            }

            val occurrenceCal = (todayStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayDelta)
                set(Calendar.HOUR_OF_DAY, startMin / 60)
                set(Calendar.MINUTE, startMin % 60)
            }

            val dateLabel = when (dayDelta) {
                0 -> "Today, ${shortDateFormat.format(occurrenceCal.time)}"
                1 -> "Tomorrow, ${shortDateFormat.format(occurrenceCal.time)}"
                else -> dateFormat.format(occurrenceCal.time)
            }

            ArrangedClassItem(
                item = item,
                occurrenceMillis = occurrenceCal.timeInMillis,
                dayDelta = dayDelta,
                startMinutes = startMin,
                dateLabel = dateLabel,
                isOngoing = isOngoing
            )
        }.sortedWith(
            compareBy<ArrangedClassItem>(
                { it.occurrenceMillis },
                { it.item.title.lowercase() }
            )
        ).mapIndexed { index, entry ->
            if (index == 0 && !entry.isOngoing) entry.copy(isNextUp = true) else entry
        }

        if (showAll) return sorted

        val nearTerm = sorted.filter { it.dayDelta <= 1 }
        if (nearTerm.isNotEmpty()) return nearTerm

        val nextAvailableDay = sorted.first().dayDelta
        return sorted.filter { it.dayDelta == nextAvailableDay }
    }

    fun arrangeDatedItems(
        items: List<ScheduleItem>,
        showAll: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ArrangedClassItem> {
        if (items.isEmpty()) return emptyList()

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val todayStart = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val shortDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        val mapped = items.map { item ->
            val (startMin, endMin) = parseTimeRangeMinutes(item.timeSlot)
            val parsedDay = parseDueDateCalendar(item.dueDate, nowMillis)

            if (parsedDay != null) {
                val startCal = (parsedDay.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, startMin / 60)
                    set(Calendar.MINUTE, startMin % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val durationMin = ((endMin ?: (startMin + 60)) - startMin).coerceAtLeast(15)
                val startMillis = startCal.timeInMillis
                val endMillis = startMillis + durationMin * 60_000L

                val dayDelta = ((parsedDay.timeInMillis - todayStart.timeInMillis) / 86_400_000L).toInt()
                val isOngoing = nowMillis in startMillis until endMillis
                val isPast = nowMillis >= endMillis

                val dateLabel = when {
                    dayDelta == 0 -> "Today, ${shortDateFormat.format(startCal.time)}"
                    dayDelta == 1 -> "Tomorrow, ${shortDateFormat.format(startCal.time)}"
                    dayDelta == -1 -> "Yesterday, ${shortDateFormat.format(startCal.time)}"
                    isPast -> "Past • ${dateFormat.format(startCal.time)}"
                    else -> dateFormat.format(startCal.time)
                }

                ArrangedClassItem(
                    item = item,
                    occurrenceMillis = startMillis,
                    dayDelta = dayDelta,
                    startMinutes = startMin,
                    dateLabel = dateLabel,
                    isOngoing = isOngoing,
                    isPast = isPast
                )
            } else {
                ArrangedClassItem(
                    item = item,
                    occurrenceMillis = Long.MAX_VALUE - 1_000_000L + startMin,
                    dayDelta = Int.MAX_VALUE,
                    startMinutes = startMin,
                    dateLabel = item.dueDate.trim().ifBlank { "Scheduled" },
                    isOngoing = false,
                    isPast = false
                )
            }
        }

        val sorted = mapped.sortedWith(
            compareBy<ArrangedClassItem>(
                { it.isPast },
                { if (!it.isPast) it.occurrenceMillis else -it.occurrenceMillis },
                { it.item.title.lowercase() }
            )
        ).let { list ->
            val firstUpcomingIndex = list.indexOfFirst { !it.isPast && !it.isOngoing && it.dayDelta != Int.MAX_VALUE }
            list.mapIndexed { index, entry ->
                if (index == firstUpcomingIndex) entry.copy(isNextUp = true) else entry
            }
        }

        if (showAll) return sorted

        val upcoming = sorted.filter { !it.isPast }
        return if (upcoming.isNotEmpty()) upcoming else sorted
    }

    fun computeNextReminderTriggerMillis(
        dayOfWeek: String,
        timeSlot: String,
        leadMinutes: Int = 15,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val todayIso = calendarDayToIso(now.get(Calendar.DAY_OF_WEEK))
        val targetIso = parseWeekdayIso(dayOfWeek)
        val (startMinutes, _) = parseTimeRangeMinutes(timeSlot)
        val dayDelta = (targetIso - todayIso + 7) % 7

        val reminderCal = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayDelta)
            set(Calendar.HOUR_OF_DAY, startMinutes / 60)
            set(Calendar.MINUTE, startMinutes % 60)
            add(Calendar.MINUTE, -leadMinutes)
        }

        if (reminderCal.timeInMillis <= nowMillis) {
            reminderCal.add(Calendar.DAY_OF_YEAR, 7)
        }
        return reminderCal.timeInMillis
    }

    fun computeDatedReminderTriggerMillis(
        dueDate: String,
        timeSlot: String,
        leadMinutes: Int = 15,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        val dateCal = parseDueDateCalendar(dueDate, nowMillis) ?: return null
        val (startMinutes, _) = parseTimeRangeMinutes(timeSlot)
        dateCal.set(Calendar.HOUR_OF_DAY, startMinutes / 60)
        dateCal.set(Calendar.MINUTE, startMinutes % 60)
        dateCal.set(Calendar.SECOND, 0)
        dateCal.set(Calendar.MILLISECOND, 0)
        dateCal.add(Calendar.MINUTE, -leadMinutes)
        return dateCal.timeInMillis
    }

    fun parseDueDateCalendar(rawDate: String, referenceMillis: Long = System.currentTimeMillis()): Calendar? {
        val cleaned = rawDate.trim()
        if (cleaned.isBlank()) return null

        for (pattern in FULL_DATE_PATTERNS) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val pos = ParsePosition(0)
            val parsed = fmt.parse(cleaned, pos)
            if (parsed != null && pos.index == cleaned.length) {
                return Calendar.getInstance().apply {
                    time = parsed
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
        }

        val refCal = Calendar.getInstance().apply { timeInMillis = referenceMillis }
        val currentYear = refCal.get(Calendar.YEAR)
        for (pattern in PARTIAL_DATE_PATTERNS) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val pos = ParsePosition(0)
            val parsed = fmt.parse(cleaned, pos)
            if (parsed != null && pos.index == cleaned.length) {
                return Calendar.getInstance().apply {
                    time = parsed
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
        }

        return null
    }

    fun parseWeekdayIso(raw: String): Int {
        val cleaned = raw.trim().lowercase()
        return when {
            cleaned.startsWith("mon") -> 1
            cleaned.startsWith("tue") -> 2
            cleaned.startsWith("wed") -> 3
            cleaned.startsWith("thu") -> 4
            cleaned.startsWith("fri") -> 5
            cleaned.startsWith("sat") -> 6
            cleaned.startsWith("sun") -> 7
            else -> 1
        }
    }

    fun parseTimeRangeMinutes(timeSlot: String): Pair<Int, Int?> {
        val parts = timeSlot.trim().split(RANGE_SPLIT_REGEX).filter { it.isNotBlank() }
        if (parts.isEmpty()) return 9 * 60 to null

        val startMatch = TIME_TOKEN_REGEX.find(parts[0]) ?: return 9 * 60 to null
        val endMatch = if (parts.size > 1) TIME_TOKEN_REGEX.find(parts[1]) else null

        val endMinutes = endMatch?.let { matchToMinutes(it, fallbackAmPm = null) }
        val endAmPm = endMatch?.groupValues?.getOrNull(3)?.lowercase()?.ifBlank { null }
        val startAmPm = startMatch.groupValues.getOrNull(3)?.lowercase()?.ifBlank { null }

        val startMinutes = if (startAmPm != null) {
            matchToMinutes(startMatch, fallbackAmPm = null)
        } else if (endAmPm == "pm" && endMinutes != null) {
            val rawHour = startMatch.groupValues[1].toIntOrNull() ?: 9
            val rawMinute = (startMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 59)
            if (rawHour in 1..12) {
                val amCandidate = ((if (rawHour == 12) 0 else rawHour) * 60) + rawMinute
                val pmCandidate = ((if (rawHour == 12) 12 else rawHour + 12) * 60) + rawMinute
                when {
                    pmCandidate <= endMinutes -> pmCandidate
                    amCandidate <= endMinutes -> amCandidate
                    else -> pmCandidate
                }
            } else {
                matchToMinutes(startMatch, fallbackAmPm = null)
            }
        } else {
            matchToMinutes(startMatch, fallbackAmPm = endAmPm)
        }

        return startMinutes to endMinutes
    }

    private fun matchToMinutes(match: MatchResult, fallbackAmPm: String?): Int {
        var hour = match.groupValues[1].toIntOrNull() ?: 9
        val minute = (match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 59)
        val suffix = match.groupValues.getOrNull(3)?.lowercase()?.ifBlank { null } ?: fallbackAmPm

        hour = when (suffix) {
            "am" -> if (hour == 12) 0 else hour.coerceIn(0, 23)
            "pm" -> if (hour in 1..11) hour + 12 else hour.coerceIn(0, 23)
            else -> hour.coerceIn(0, 23)
        }
        return hour * 60 + minute
    }

    private fun calendarDayToIso(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}
