package com.example.sleeptile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class SchoolDataSet(val schedule: JSONObject?, val bells: JSONObject?, val fromNetwork: Boolean)

/** Чтение расписания и звонков, парсинг текстового ввода, заготовки, смещение звонков. */
object SchoolData {
    const val FILE_SCHEDULE = "school_schedule.json"
    const val FILE_BELLS = "school_bells.json"

    private val DAY_NAMES = arrayOf(
        "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"
    )

    // ========== СТАНДАРТНОЕ РАСПИСАНИЕ ЗВОНКОВ ==========

    private val BELLS_MON: Map<Int, Pair<Int, Int>> = mapOf(
        1  to Pair(8*60+0,  8*60+30),
        2  to Pair(8*60+40, 9*60+20),
        3  to Pair(9*60+30, 10*60+10),
        4  to Pair(10*60+20, 11*60+0),
        5  to Pair(11*60+20, 12*60+0),
        6  to Pair(12*60+20, 13*60+0),
        7  to Pair(13*60+20, 14*60+0),
        8  to Pair(14*60+10, 14*60+50),
        9  to Pair(15*60+0,  15*60+40),
        10 to Pair(15*60+50, 16*60+30)
    )

    private val BELLS_TUE_FRI: Map<Int, Pair<Int, Int>> = mapOf(
        1  to Pair(8*60+30, 9*60+10),
        2  to Pair(9*60+20, 10*60+0),
        3  to Pair(10*60+10, 10*60+50),
        4  to Pair(11*60+10, 11*60+50),
        5  to Pair(12*60+10, 12*60+50),
        6  to Pair(13*60+10, 13*60+50),
        7  to Pair(14*60+0,  14*60+40),
        8  to Pair(14*60+50, 15*60+30),
        9  to Pair(15*60+40, 16*60+20),
        10 to Pair(16*60+30, 17*60+10)
    )

    fun bellsFor(dow: DayOfWeek): Map<Int, Pair<Int, Int>> {
        return when (dow) {
            DayOfWeek.MONDAY -> BELLS_MON
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> BELLS_TUE_FRI
            else -> emptyMap()
        }
    }

    /** Возвращает звонки на день с учетом временных смещений на конкретную дату. */
    fun bellsForDate(ctx: Context, date: LocalDate): Map<Int, Pair<Int, Int>> {
        val customDate = SchoolSettings.customBellsDate(ctx)
        if (customDate == date.toString()) {
            val json = SchoolSettings.customBellsJson(ctx)
            val custom = parseBellsJson(json)
            if (custom.isNotEmpty()) return custom
        }
        return bellsFor(date.dayOfWeek)
    }

    private fun parseBellsJson(json: String): Map<Int, Pair<Int, Int>> {
        val out = LinkedHashMap<Int, Pair<Int, Int>>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out[o.getInt("n")] = Pair(o.getInt("s"), o.getInt("e"))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun bellsToJson(map: Map<Int, Pair<Int, Int>>): String {
        val arr = JSONArray()
        for ((n, pair) in map) {
            arr.put(JSONObject().put("n", n).put("s", pair.first).put("e", pair.second))
        }
        return arr.toString()
    }

    // ========== ПАРСИНГ ТАБЛИЦ ЗВОНКОВ И ГЕНЕРАТОР ==========

    /** Парсит вставку из Word / Telegram вида: «1 9.30 10.05 10» или «9:30 - 10:05». */
    fun parseCustomBellsTable(text: String): Map<Int, Pair<Int, Int>> {
        val result = LinkedHashMap<Int, Pair<Int, Int>>()
        val timeRegex = Regex("""\b(\d{1,2})[.:](\d{2})\b""")
        var autoNum = 1
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val times = timeRegex.findAll(trimmed).toList()
            if (times.size >= 2) {
                val h1 = times[0].groupValues[1].toInt()
                val m1 = times[0].groupValues[2].toInt()
                val h2 = times[1].groupValues[1].toInt()
                val m2 = times[1].groupValues[2].toInt()
                if (h1 in 5..23 && m1 in 0..59 && h2 in 5..23 && m2 in 0..59) {
                    val s = h1 * 60 + m1
                    val e = h2 * 60 + m2
                    if (e > s) {
                        val leadNum = Regex("""^(\d{1,2})\b""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
                        val num = leadNum ?: autoNum
                        result[num] = Pair(s, e)
                        autoNum = maxOf(autoNum + 1, num + 1)
                    }
                }
            }
        }
        return result
    }

    /** Быстрый генератор звонков по началу 1-го урока, длине урока и переменам. */
    fun generateBells(startHhMm: String, durationMin: Int, breaks: List<Int>, lessonCount: Int = 8): Map<Int, Pair<Int, Int>> {
        val result = LinkedHashMap<Int, Pair<Int, Int>>()
        val parts = startHhMm.trim().replace('.', ':').split(':')
        if (parts.size < 2) return emptyMap()
        val h = parts[0].toIntOrNull() ?: return emptyMap()
        val m = parts[1].toIntOrNull() ?: return emptyMap()
        var current = h * 60 + m

        for (i in 1..lessonCount) {
            val end = current + durationMin
            result[i] = Pair(current, end)
            val br = if (i - 1 < breaks.size) breaks[i - 1] else 10
            current = end + br
        }
        return result
    }

    // ========== ОПРЕДЕЛЕНИЕ «ОКОН» И РЕАЛЬНЫХ УРОКОВ ==========

    /** Проверяет, является ли урок настоящим, а не окном/прочерком («1. -», «нет», «окно»). */
    fun isRealSubject(subject: String): Boolean {
        val clean = subject.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return false
        if (clean == "-" || clean == "—" || clean == "–" || clean == "нет" || clean == "окно" || clean == "прочерк" || clean == "отмена") return false
        if (clean.all { it == '-' || it == '—' || it == '–' || it.isWhitespace() }) return false
        return true
    }

    // ========== ЗАГРУЗКА И ХРАНЕНИЕ ==========

    private fun readLocal(ctx: Context, name: String): String? {
        return try {
            val f = File(ctx.filesDir, name)
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun readAsset(ctx: Context, name: String): String? {
        return try {
            ctx.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(text: String?): JSONObject? {
        if (text == null) return null
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }

    fun load(ctx: Context): SchoolDataSet {
        val s = readLocal(ctx, FILE_SCHEDULE)
        val schedule = parse(s) ?: parse(readAsset(ctx, "schedule.json"))
        return SchoolDataSet(schedule, null, false)
    }

    private const val PREF_LESSONS = "local_lessons"
    private const val PREF_TEMPLATES = "schedule_templates"

    private fun lessonPrefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_LESSONS, Context.MODE_PRIVATE)

    private fun templatePrefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_TEMPLATES, Context.MODE_PRIVATE)

    fun hasAnyLocalLessons(ctx: Context): Boolean {
        val p = lessonPrefs(ctx)
        for (dow in 1..7) {
            val json = p.getString("dow_$dow", null) ?: continue
            try {
                if (JSONArray(json).length() > 0) return true
            } catch (e: Exception) { }
        }
        return false
    }

    fun saveLocalLessons(ctx: Context, dow: Int, lessons: List<Triple<Int, String, String>>) {
        val arr = JSONArray()
        for ((n, subj, room) in lessons) {
            arr.put(JSONObject().put("n", n).put("s", subj).put("r", room))
        }
        lessonPrefs(ctx).edit().putString("dow_$dow", arr.toString()).apply()
    }

    fun loadLocalLessons(ctx: Context, date: LocalDate): List<Triple<Int, String, String>>? {
        val p = lessonPrefs(ctx)
        val dateKey = "date_$date"
        val dowKey = "dow_${date.dayOfWeek.value}"
        val json = p.getString(dateKey, null) ?: p.getString(dowKey, null) ?: return null
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<Triple<Int, String, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Triple(o.getInt("n"), o.getString("s"), o.optString("r", "")))
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    fun loadLocalLessonsForDow(ctx: Context, dow: Int): List<Triple<Int, String, String>>? {
        val json = lessonPrefs(ctx).getString("dow_$dow", null) ?: return null
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<Triple<Int, String, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Triple(o.getInt("n"), o.getString("s"), o.optString("r", "")))
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    // ========== ЗАГОТОВКИ (ШАБЛОНЫ) ==========

    fun saveTemplate(ctx: Context, dow: Int, text: String) {
        templatePrefs(ctx).edit().putString("template_$dow", text.trim()).apply()
    }

    fun loadTemplate(ctx: Context, dow: Int): String? {
        val s = templatePrefs(ctx).getString("template_$dow", null)
        return if (s.isNullOrBlank()) null else s
    }

    fun hasTemplate(ctx: Context, dow: Int): Boolean =
        !templatePrefs(ctx).getString("template_$dow", "").isNullOrBlank()

    // ========== ПАРСИНГ ТЕКСТОВОГО ВВОДА РАСПИСАНИЯ ==========

    fun parseTextSchedule(text: String): List<Triple<Int, String, String>> {
        val result = ArrayList<Triple<Int, String, String>>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val m = Regex("""^(\d+)\s*[.)]\s*(.+)""").find(trimmed) ?: continue
            val num = m.groupValues[1].toIntOrNull() ?: continue
            val rest = m.groupValues[2].trim()
            val dashPattern = Regex("""\s+[-–—]\s+""")
            val parts = dashPattern.split(rest, 2)
            val subject = parts[0].trim()
            val room = if (parts.size > 1) parts[1].trim() else ""
            if (subject.isNotEmpty()) {
                result.add(Triple(num, subject, room))
            }
        }
        return result
    }

    fun importFullSchedule(ctx: Context, fullText: String): Int {
        val lines = fullText.lines()
        var currentDow = 0
        val dayBuffers = HashMap<Int, StringBuilder>()

        val dayRegex = Regex("""(?i)^\s*(понедельник|вторник|среда|четверг|пятница|суббота|пн|вт|ср|чт|пт|сб)\b[:\-–—\s]*""")

        fun matchDow(str: String): Int {
            val lower = str.lowercase(Locale.ROOT)
            return when {
                lower.startsWith("пон") || lower.startsWith("пн") -> 1
                lower.startsWith("вто") || lower.startsWith("вт") -> 2
                lower.startsWith("сре") || lower.startsWith("ср") -> 3
                lower.startsWith("чет") || lower.startsWith("чт") -> 4
                lower.startsWith("пят") || lower.startsWith("пт") -> 5
                lower.startsWith("суб") || lower.startsWith("сб") -> 6
                else -> 0
            }
        }

        for (line in lines) {
            val m = dayRegex.find(line)
            if (m != null) {
                val dow = matchDow(m.groupValues[1])
                if (dow in 1..6) {
                    currentDow = dow
                    dayBuffers.getOrPut(dow) { StringBuilder() }
                    continue
                }
            }
            if (currentDow in 1..6) {
                dayBuffers.getOrPut(currentDow) { StringBuilder() }.append(line).append("\n")
            }
        }

        var updatedDays = 0
        for ((dow, sb) in dayBuffers) {
            val parsed = parseTextSchedule(sb.toString())
            if (parsed.isNotEmpty()) {
                saveLocalLessons(ctx, dow, parsed)
                updatedDays++
            }
        }
        return updatedDays
    }

    // ========== ПОСТРОЕНИЕ СПИСКА УРОКОВ НА ДЕНЬ ==========

    private fun toMillis(date: LocalDate, minutes: Int, zone: ZoneId): Long =
        date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()

    fun lessonsForDay(ctx: Context, data: SchoolDataSet, date: LocalDate): List<ResolvedLesson> {
        // Если на этот день объявлен «КОД КРАСНЫЙ / отмена уроков» — уроков нет!
        if (SchoolSettings.canceledDate(ctx) == date.toString()) return emptyList()

        val test = testLessons(ctx, date)
        if (test != null) return test

        val local = loadLocalLessons(ctx, date)
        if (local != null && local.isNotEmpty()) {
            return localToResolved(ctx, local, date)
        }
        return emptyList()
    }

    private fun localToResolved(ctx: Context, lessons: List<Triple<Int, String, String>>, date: LocalDate): List<ResolvedLesson> {
        val bells = bellsForDate(ctx, date)
        if (bells.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val out = ArrayList<ResolvedLesson>()
        for ((n, subject, room) in lessons) {
            val bell = bells[n] ?: continue
            out.add(ResolvedLesson(n, subject, room, toMillis(date, bell.first, zone), toMillis(date, bell.second, zone)))
        }
        out.sortBy { it.n }
        return out
    }

    /** События дня только для РЕАЛЬНЫХ уроков (окна/прочерки не будят и не глушат звук). */
    fun eventsForDay(ctx: Context, data: SchoolDataSet, date: LocalDate): List<SchoolEvent> {
        val all = lessonsForDay(ctx, data, date)
        // Фильтруем только настоящие уроки
        val ls = all.filter { isRealSubject(it.subject) }
        val out = ArrayList<SchoolEvent>()
        for (i in ls.indices) {
            val l = ls[i]
            val next = if (i + 1 < ls.size) ls[i + 1] else null
            if (i == 0) out.add(SchoolEvent(l.start - SchoolSettings.preMinutes(ctx) * 60_000L, EvType.PRE, l, null))
            out.add(SchoolEvent(l.start, EvType.START, l, null))
            out.add(SchoolEvent(l.end, EvType.END, l, next))
        }
        return out
    }

    fun relevantDay(ctx: Context, data: SchoolDataSet, now: Long): Pair<LocalDate, List<ResolvedLesson>>? {
        val today = LocalDate.now()
        for (d in 0..7) {
            val date = today.plusDays(d.toLong())
            val ls = lessonsForDay(ctx, data, date)
            if (ls.isEmpty()) continue
            if (d == 0 && now >= ls[ls.size - 1].end) continue
            return Pair(date, ls)
        }
        return null
    }

    fun anyRelevantDay(ctx: Context, data: SchoolDataSet): Pair<LocalDate, List<ResolvedLesson>>? {
        val rel = relevantDay(ctx, data, System.currentTimeMillis())
        if (rel != null) return rel

        val today = LocalDate.now()
        val todayLessons = lessonsForDay(ctx, data, today)
        if (todayLessons.isNotEmpty()) {
            return Pair(today, todayLessons)
        }

        for (dow in 1..7) {
            val local = loadLocalLessonsForDow(ctx, dow)
            if (local != null && local.isNotEmpty()) {
                for (offset in 0..7) {
                    val candidate = today.plusDays(offset.toLong())
                    if (candidate.dayOfWeek.value == dow) {
                        val resolved = localToResolved(ctx, local, candidate)
                        if (resolved.isNotEmpty()) return Pair(candidate, resolved)
                    }
                }
            }
        }
        return null
    }

    /** Промежутки «тишины»: звук выключается только на реальных уроках, в окнах — включается. */
    fun windows(lessons: List<ResolvedLesson>, keepGapMin: Int): List<LongArray> {
        val realLessons = lessons.filter { isRealSubject(it.subject) }
        val out = ArrayList<LongArray>()
        for (l in realLessons) {
            val last = if (out.isEmpty()) null else out[out.size - 1]
            if (last != null && l.start - last[1] <= keepGapMin * 60_000L) {
                last[1] = l.end
            } else {
                out.add(longArrayOf(l.start, l.end))
            }
        }
        return out
    }

    private fun testUntil(ctx: Context): Long = SchoolSettings.prefs(ctx).getLong("test_until", 0L)

    fun testActive(ctx: Context): Boolean = testUntil(ctx) > System.currentTimeMillis()

    fun startTest(ctx: Context) {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        arr.put(JSONArray().put(1).put("Тестовый урок 1").put("101").put(now + 60_000L).put(now + 150_000L))
        arr.put(JSONArray().put(2).put("Тестовый урок 2").put("102").put(now + 210_000L).put(now + 300_000L))
        SchoolSettings.prefs(ctx).edit()
            .putString("test", arr.toString())
            .putLong("test_until", now + 360_000L)
            .apply()
    }

    fun stopTest(ctx: Context) {
        SchoolSettings.prefs(ctx).edit().remove("test").remove("test_until").apply()
    }

    private fun testLessons(ctx: Context, date: LocalDate): List<ResolvedLesson>? {
        if (!testActive(ctx) || date != LocalDate.now()) return null
        val text = SchoolSettings.prefs(ctx).getString("test", null) ?: return null
        return try {
            val arr = JSONArray(text)
            val out = ArrayList<ResolvedLesson>()
            for (i in 0 until arr.length()) {
                val a = arr.getJSONArray(i)
                out.add(ResolvedLesson(a.getInt(0), a.getString(1), a.getString(2), a.getLong(3), a.getLong(4)))
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    fun describeLesson(l: ResolvedLesson): String =
        if (l.room.isEmpty()) l.subject else "${l.subject}, каб. ${l.room}"

    fun dayTitle(date: LocalDate): String =
        DAY_NAMES[date.dayOfWeek.value - 1] + ", " +
            String.format(Locale.ROOT, "%02d.%02d", date.dayOfMonth, date.monthValue)

    fun describeUpcoming(ctx: Context): String {
        val data = load(ctx)
        val sb = StringBuilder()
        var shown = 0
        val today = LocalDate.now()
        for (d in 0..7) {
            val date = today.plusDays(d.toLong())
            val ls = lessonsForDay(ctx, data, date)
            if (ls.isEmpty()) continue
            sb.append(dayTitle(date)).append("\n")
            for (l in ls) {
                sb.append("${l.n}) ${hm(l.start)}–${hm(l.end)}  ${describeLesson(l)}\n")
            }
            sb.append("\n")
            shown++
            if (shown >= 3) break
        }
        if (shown == 0) return "Пока нет уроков на ближайшие дни.\nВведите уроки в расписание выше."
        return sb.toString().trim()
    }
}
