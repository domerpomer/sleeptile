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

/** Чтение расписания и звонков, подбор уроков для класса и подгруппы, построение событий. */
object SchoolData {
    const val FILE_SCHEDULE = "school_schedule.json"
    const val FILE_BELLS = "school_bells.json"

    private val DAY_NAMES = arrayOf(
        "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"
    )

    // ========== ЖЁСТКО ЗАШИТОЕ РАСПИСАНИЕ ЗВОНКОВ ==========

    /** Звонки: номер урока → (начало в минутах от полуночи, конец в минутах). */
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
            else -> emptyMap() // суббота и воскресенье — нет уроков
        }
    }

    // ========== ЗАГРУЗКА / ХРАНЕНИЕ ФАЙЛОВ (для совместимости) ==========

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
        return SchoolDataSet(schedule, null, s != null && parse(s) != null)
    }

    // ========== ЛОКАЛЬНОЕ РАСПИСАНИЕ УРОКОВ (ввод текстом) ==========

    private const val PREF_LESSONS = "local_lessons"

    /** Ключ для хранения: "dow_1" = понедельник, "date_2026-11-25" = конкретная дата. */
    private fun lessonPrefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_LESSONS, Context.MODE_PRIVATE)

    /** Сохраняет список уроков для дня недели (1=пн..7=вс). */
    fun saveLocalLessons(ctx: Context, dow: Int, lessons: List<Triple<Int, String, String>>) {
        val arr = JSONArray()
        for ((n, subj, room) in lessons) {
            arr.put(JSONObject().put("n", n).put("s", subj).put("r", room))
        }
        lessonPrefs(ctx).edit().putString("dow_$dow", arr.toString()).apply()
    }

    /** Сохраняет список уроков для конкретной даты (приоритет над днём недели). */
    fun saveLocalLessonsForDate(ctx: Context, date: LocalDate, lessons: List<Triple<Int, String, String>>) {
        val arr = JSONArray()
        for ((n, subj, room) in lessons) {
            arr.put(JSONObject().put("n", n).put("s", subj).put("r", room))
        }
        lessonPrefs(ctx).edit().putString("date_$date", arr.toString()).apply()
    }

    /** Загружает локальные уроки: сначала проверяет конкретную дату, потом день недели. */
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

    /** Загружает сохранённые уроки для дня недели (для показа в редакторе). */
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

    /** Парсит текст формата:
     *  1. Алгебра - 206
     *  2 Русский язык — 316
     *  3. Физика
     *  Поддерживает разные тире (-, –, —), с точкой и без, кабинет необязателен.
     */
    fun parseTextSchedule(text: String): List<Triple<Int, String, String>> {
        val result = ArrayList<Triple<Int, String, String>>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            // Пытаемся извлечь: номер [.)] предмет [-–—] кабинет
            val m = Regex("""^(\d+)\s*[.)]\s*(.+)""").find(trimmed) ?: continue
            val num = m.groupValues[1].toIntOrNull() ?: continue
            val rest = m.groupValues[2].trim()
            // Разделяем по тире
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

    // ========== ПОСТРОЕНИЕ СПИСКА УРОКОВ НА ДЕНЬ ==========

    private fun toMillis(date: LocalDate, minutes: Int, zone: ZoneId): Long =
        date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()

    fun lessonsForDay(ctx: Context, data: SchoolDataSet, date: LocalDate): List<ResolvedLesson> {
        val test = testLessons(ctx, date)
        if (test != null) return test

        // 1) Пытаемся из локального ввода
        val local = loadLocalLessons(ctx, date)
        if (local != null && local.isNotEmpty()) {
            return localToResolved(local, date)
        }

        // 2) Fallback на сетевое расписание
        return realLessonsForDay(ctx, data, date)
    }

    /** Преобразует список (номер, предмет, кабинет) в ResolvedLesson с временами из звонков. */
    private fun localToResolved(lessons: List<Triple<Int, String, String>>, date: LocalDate): List<ResolvedLesson> {
        val bells = bellsFor(date.dayOfWeek)
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

    /** Уроки по сетевому расписанию (без тестового режима). */
    fun realLessonsForDay(ctx: Context, data: SchoolDataSet, date: LocalDate): List<ResolvedLesson> {
        val klass = SchoolSettings.klass(ctx)
        val group = SchoolSettings.group(ctx)
        if (klass.isEmpty()) return emptyList()

        val day = data.schedule?.optJSONObject("days")?.optJSONObject(date.toString())
            ?: return emptyList()
        val arr = day.optJSONArray(klass) ?: return emptyList()
        val bells = bellsFor(date.dayOfWeek)
        val zone = ZoneId.systemDefault()

        val out = ArrayList<ResolvedLesson>()
        for (i in 0 until arr.length()) {
            val l = arr.getJSONObject(i)
            val n = l.getInt("n")
            val bell = bells[n] ?: continue
            val parts = l.optJSONArray("p") ?: continue
            var chosen: JSONObject? = null
            for (j in 0 until parts.length()) {
                val part = parts.getJSONObject(j)
                val g = part.optInt("g", 0)
                if (g == 0 || g == group) {
                    chosen = part
                    break
                }
            }
            if (chosen == null) continue
            out.add(
                ResolvedLesson(
                    n,
                    chosen.getString("s"),
                    chosen.optString("r", ""),
                    toMillis(date, bell.first, zone),
                    toMillis(date, bell.second, zone)
                )
            )
        }
        out.sortBy { it.n }
        return out
    }

    fun eventsForDay(ctx: Context, data: SchoolDataSet, date: LocalDate): List<SchoolEvent> {
        val ls = lessonsForDay(ctx, data, date)
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

    /** Ближайший день, для которого есть уроки (сегодня — только пока уроки не кончились). */
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

    /** Промежутки «тишины»: уроки, между которыми перемена не длиннее keepGapMin, объединяются. */
    fun windows(lessons: List<ResolvedLesson>, keepGapMin: Int): List<LongArray> {
        val out = ArrayList<LongArray>()
        for (l in lessons) {
            val last = if (out.isEmpty()) null else out[out.size - 1]
            if (last != null && l.start - last[1] <= keepGapMin * 60_000L) {
                last[1] = l.end
            } else {
                out.add(longArrayOf(l.start, l.end))
            }
        }
        return out
    }

    // ---------- проверка «за 5 минут» ----------

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

    // ---------- текст для экрана ----------

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
        if (shown == 0) return "Для выбранного класса пока нет расписания на ближайшие дни."
        return sb.toString().trim()
    }
}
