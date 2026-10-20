package au.howesthat.whirl.core.scraper

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Whirlpool's human-readable times. Pages without a web login show Sydney local time with no zone, in several forms:
 * `2025-Jun-23, 11:11 am`, `2017-Dec-1`, `Today at 4:47 pm`, `Yesterday at 9:39 pm`, `Monday at 2:33 pm`,
 * `33 minutes ago`, `22h`, `2d`, and on archive pages `2011-Jul-6, 3:51 pm AEST`.
 */
object WhirlpoolTime {
    val SYDNEY: ZoneId = ZoneId.of("Australia/Sydney")

    private val DATE: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("yyyy-MMM-d")
        .toFormatter(Locale.ENGLISH)

    private val TIME: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("h:mm a")
        .toFormatter(Locale.ENGLISH)

    private val ABSOLUTE = Regex("""^(\d{4}-[A-Za-z]{3}-\d{1,2})(?:,\s*(\d{1,2}:\d{2}\s*[ap]m))?(?:\s+[A-Z]{3,5})?$""", RegexOption.IGNORE_CASE)
    private val DAY_AT = Regex("""^(Today|Yesterday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday) at (\d{1,2}:\d{2}\s*[ap]m)$""", RegexOption.IGNORE_CASE)
    private val AGO = Regex("""^(\d+)\s*(second|minute|min|hour|day|week|month|year)s?\s+ago$""", RegexOption.IGNORE_CASE)
    private val SHORT = Regex("""^(\d+)\s*([smhdwy])$""")

    /** Parses the absolute forms (`2025-Jun-23, 11:11 am`, `2017-Dec-1`, optional trailing zone name). */
    fun parseAbsolute(text: String): LocalDateTime? {
        val m = ABSOLUTE.matchEntire(text.squash()) ?: return null
        return try {
            val date = LocalDate.parse(m.groupValues[1], DATE)
            val time = m.groupValues[2].takeIf(String::isNotEmpty)?.let { LocalTime.parse(normaliseAmPm(it), TIME) }
            date.atTime(time ?: LocalTime.MIDNIGHT)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Parses any form relative to [now] (which should be in [SYDNEY]). Relative forms ("22h", "3 weeks ago") are
     * approximate; that is all the page offers.
     */
    fun parse(text: String, now: ZonedDateTime): ZonedDateTime? {
        val t = text.squash()
        parseAbsolute(t)?.let { return it.atZone(SYDNEY) }
        DAY_AT.matchEntire(t)?.let { m ->
            val time = LocalTime.parse(normaliseAmPm(m.groupValues[2]), TIME)
            val today = now.withZoneSameInstant(SYDNEY).toLocalDate()
            val date = when (val day = m.groupValues[1].lowercase()) {
                "today" -> today
                "yesterday" -> today.minusDays(1)
                // The page uses weekday names for the last week, excluding today and yesterday.
                else -> today.minusDays(1).with(TemporalAdjusters.previous(DayOfWeek.valueOf(day.uppercase())))
            }
            return date.atTime(time).atZone(SYDNEY)
        }
        val ago = AGO.matchEntire(t)?.let { it.groupValues[1].toLong() to it.groupValues[2].lowercase().first() }
            ?: SHORT.matchEntire(t)?.let { it.groupValues[1].toLong() to it.groupValues[2].first() }
        if (ago != null) {
            val (n, unit) = ago
            return when (unit) {
                's' -> now.minusSeconds(n)
                'm' -> if (t.contains("month", ignoreCase = true)) now.minusMonths(n) else now.minusMinutes(n)
                'h' -> now.minusHours(n)
                'd' -> now.minusDays(n)
                'w' -> now.minusWeeks(n)
                'y' -> now.minusYears(n)
                else -> null
            }?.truncatedTo(ChronoUnit.MINUTES)
        }
        return null
    }

    // "9:39 pm" → "9:39 PM"; tolerate a missing space.
    private fun normaliseAmPm(s: String): String = s.replace(Regex("""\s*([ap]m)$""", RegexOption.IGNORE_CASE), " $1")
}
