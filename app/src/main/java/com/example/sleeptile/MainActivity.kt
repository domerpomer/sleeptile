package com.example.sleeptile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val REQ_PICK_TXT = 2001
    private var tab = 0
    private var scrollView: ScrollView? = null
    private var renderedTab = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        tab = getSharedPreferences("ui", MODE_PRIVATE).getInt("tab", 0).coerceIn(0, 3)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_TXT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (text.isNotBlank()) {
                    val count = SchoolData.importFullSchedule(this, text)
                    if (count > 0) {
                        toast("Импортировано дней: $count")
                        onSchoolChanged()
                    } else {
                        toast("Не удалось распознать дни недели.\nПример формата в файле:\nПонедельник:\n1. Алгебра - 206")
                    }
                }
            } catch (e: Exception) {
                toast("Ошибка чтения файла: ${e.message}")
            }
        }
    }

    // ================= помощники для интерфейса =================

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float = 16f, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun hint(s: String): TextView {
        val t = tv(s, 13f)
        t.setTextColor(0xFF888888.toInt())
        return t
    }

    private fun button(s: String, enabled: Boolean = true, onClick: () -> Unit): Button =
        Button(this).apply {
            text = s
            isAllCaps = false
            isEnabled = enabled
            alpha = if (enabled) 1.0f else 0.4f
            if (enabled) setOnClickListener { onClick() }
        }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    private fun selectableBg(): Drawable? {
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val d = ta.getDrawable(0)
        ta.recycle()
        return d
    }

    /** Карточка-раздел с полупрозрачным фоном (хорошо выглядит и в тёмной, и в светлой теме). */
    private fun card(title: String?, subtitle: String? = null): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(12), dp(16), dp(12))
        val bg = GradientDrawable()
        bg.setColor(0x1F888888)
        bg.cornerRadius = dp(16).toFloat()
        c.background = bg
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, dp(6), 0, dp(6))
        c.layoutParams = lp
        if (title != null) c.addView(tv(title, 18f, true))
        if (subtitle != null) c.addView(hint(subtitle))
        return c
    }

    private fun toggleRow(
        parent: LinearLayout, title: String, hintText: String?, value: Boolean,
        enabled: Boolean = true, onChange: (Boolean) -> Unit
    ): Switch {
        val sw = Switch(this)
        sw.text = title
        sw.textSize = 16f
        sw.isChecked = value
        sw.isEnabled = enabled
        sw.alpha = if (enabled) 1.0f else 0.4f
        sw.setPadding(0, dp(10), 0, dp(2))
        sw.setOnCheckedChangeListener { _, on -> onChange(on) }
        parent.addView(sw)
        if (hintText != null) {
            val h = hint(hintText)
            h.alpha = if (enabled) 1.0f else 0.4f
            parent.addView(h)
        }
        return sw
    }

    private fun choiceRow(
        parent: LinearLayout, title: String, options: List<String>, current: Int,
        valueText: String, enabled: Boolean = true, onPick: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        row.isEnabled = enabled
        row.alpha = if (enabled) 1.0f else 0.4f
        if (enabled) row.background = selectableBg()

        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val v = TextView(this)
        v.text = "$valueText  ›"
        v.textSize = 15f
        v.setTextColor(if (enabled) 0xFF7986CB.toInt() else 0xFF888888.toInt())
        row.addView(t)
        row.addView(v)

        if (enabled) {
            row.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setSingleChoiceItems(options.toTypedArray(), current) { dialog, which ->
                        dialog.dismiss()
                        onPick(which)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }
        parent.addView(row)
        return row
    }

    private fun permissionStatusRow(
        parent: LinearLayout, ok: Boolean, title: String, hintText: String, actionLabel: String = "Выдать", action: () -> Unit
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(10), 0, dp(10))

        val textCol = LinearLayout(this)
        textCol.orientation = LinearLayout.VERTICAL
        textCol.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val t = tv(title, 16f, true)
        val h = hint(hintText)
        textCol.addView(t)
        textCol.addView(h)
        row.addView(textCol)

        if (ok) {
            val status = TextView(this)
            status.text = "✓ Выдано"
            status.textSize = 14f
            status.setTypeface(status.typeface, Typeface.BOLD)
            status.setTextColor(Color.parseColor("#4CAF50"))
            status.setPadding(dp(8), dp(4), dp(8), dp(4))
            row.addView(status)
        } else {
            val b = Button(this)
            b.text = actionLabel
            b.isAllCaps = false
            b.setOnClickListener { action() }
            row.addView(b)
        }
        parent.addView(row)
    }

    private fun fmt(min: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", min / 60, min % 60)

    private fun daysText(d: Int): String =
        if (d == ALL_DAYS) "каждый день"
        else (0..6).filter { ((d shr it) and 1) == 1 }.joinToString(" ") { DAY_SHORT[it] }

    private fun describe(r: Rule): String {
        val t = if (r.endMin >= 0) "${fmt(r.startMin)}–${fmt(r.endMin)}" else fmt(r.startMin)
        return "$t · ${daysText(r.days)}"
    }

    private fun sb(key: String, def: Boolean): Boolean = SchoolSettings.b(this, key, def)
    private fun si(key: String, def: Int): Int = SchoolSettings.i(this, key, def)

    // ================= каркас: вкладки =================

    private fun render() {
        val keep = if (renderedTab == tab) (scrollView?.scrollY ?: 0) else 0

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        // Щедрый нижний отступ dp(100), чтобы нижние кнопки никогда не обрезались панелью навигации:
        col.setPadding(dp(14), dp(16), dp(14), dp(100))

        val scroll = ScrollView(this)
        scroll.addView(col)
        root.addView(
            scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        when (tab) {
            0 -> buildSchoolTab(col)
            1 -> buildSleepTab(col)
            2 -> buildPermissionsTab(col)
            else -> buildLogTab(col)
        }
        root.addView(bottomBar())

        setContentView(root)
        scrollView = scroll
        renderedTab = tab
        if (keep > 0) scroll.post { scroll.scrollTo(0, keep) }
    }

    private fun bottomBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(0x22888888)
        bar.addView(tabButton(0, R.drawable.ic_book, "Школа"), tabParams())
        bar.addView(tabButton(1, R.drawable.ic_tile, "Сон"), tabParams())
        bar.addView(tabButton(2, R.drawable.ic_shield, "Разрешения"), tabParams())
        bar.addView(tabButton(3, R.drawable.ic_list, "Журнал"), tabParams())
        return bar
    }

    private fun tabParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun tabButton(index: Int, icon: Int, label: String): View {
        val color = if (tab == index) 0xFF5C6BC0.toInt() else 0xFF888888.toInt()
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.setPadding(0, dp(8), 0, dp(8))

        val img = ImageView(this)
        img.setImageResource(icon)
        img.setColorFilter(color)
        img.layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))

        val txt = TextView(this)
        txt.text = label
        txt.textSize = 12f
        txt.setTextColor(color)
        txt.gravity = Gravity.CENTER

        item.addView(img)
        item.addView(txt)
        item.setOnClickListener {
            tab = index
            getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("tab", index).apply()
            render()
        }
        return item
    }

    // ================= вкладка «Школа» =================

    private fun buildSchoolTab(col: LinearLayout) {
        col.addView(tv("Школьный режим", 26f, true))
        col.addView(hint("Расписание, звук и уведомления"))

        // ---------- Главный выключатель ----------
        val c1 = card("Школьный режим")
        toggleRow(
            c1, "Школьный режим",
            "Пока выключен — звук, уведомления и обои не меняются",
            SchoolSettings.enabled(this)
        ) { on ->
            SchoolSettings.setEnabled(this, on)
            if (on) ensureNotificationPermission()
            onSchoolChanged()
        }
        c1.addView(button("Моё расписание на ближайшие дни") {
            AlertDialog.Builder(this)
                .setTitle("Моё расписание")
                .setMessage(SchoolData.describeUpcoming(this))
                .setPositiveButton("OK", null)
                .show()
        })
        col.addView(c1)

        // ---------- Расписание уроков ----------
        val c2 = card("Расписание уроков")
        val dowNames = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница")
        for (dow in 1..5) {
            c2.addView(dayScheduleCard(dow, dowNames[dow - 1]))
            if (dow < 5) {
                val divider = View(this).apply {
                    setBackgroundColor(0x1F888888)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        setMargins(dp(16), 0, dp(16), 0)
                    }
                }
                c2.addView(divider)
            }
        }
        c2.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)) })
        c2.addView(button("Загрузить расписание из .txt файла") { pickTxtFile() })
        col.addView(c2)

        // ---------- Уведомления ----------
        val notifyMaster = sb("notify", true)
        val c3 = card("Уведомления")
        toggleRow(c3, "Уведомления об уроках", "Главный выключатель всех уведомлений ниже", notifyMaster) { on ->
            SchoolSettings.setB(this, "notify", on)
            render()
        }
        toggleRow(c3, "За 15 мин до первого урока", "Предмет и кабинет первого урока", sb("notify_pre", true), notifyMaster) { on ->
            SchoolSettings.setB(this, "notify_pre", on)
        }
        toggleRow(c3, "Когда начался урок", "Тихое уведомление: предмет, кабинет, до скольки", sb("notify_start", true), notifyMaster) { on ->
            SchoolSettings.setB(this, "notify_start", on)
        }
        toggleRow(c3, "На перемене: какой урок дальше", "Предмет, кабинет, длина перемены и время звонка", sb("notify_break", true), notifyMaster) { on ->
            SchoolSettings.setB(this, "notify_break", on)
        }
        toggleRow(c3, "Когда уроки закончились", null, sb("notify_end", true), notifyMaster) { on ->
            SchoolSettings.setB(this, "notify_end", on)
        }
        col.addView(c3)

        // ---------- Звук ----------
        val soundMaster = SchoolSettings.silent(this)
        val c4 = card("Звук на уроках")
        toggleRow(
            c4, "Менять звук на уроках",
            "На время урока звонок выключается, после урока возвращается как было",
            soundMaster
        ) { on ->
            SchoolSettings.setSilent(this, on)
            onSchoolChanged()
        }
        val ringOptions = listOf("Беззвучный", "Вибрация")
        val ring = si("ring_mode", 0).coerceIn(0, 1)
        choiceRow(c4, "Режим звонка на уроке", ringOptions, ring, ringOptions[ring], soundMaster) { which ->
            SchoolSettings.setI(this, "ring_mode", which)
            onSchoolChanged()
        }
        toggleRow(
            c4, "«Не беспокоить» на уроках",
            "Нужен доступ к режиму «Не беспокоить» (см. вкладку «Разрешения»)",
            SchoolSettings.dnd(this),
            soundMaster
        ) { on ->
            SchoolSettings.setDnd(this, on)
            onSchoolChanged()
        }
        val gapValues = listOf(0, 10, 20, 60)
        val gapNames = listOf(
            "Возвращать на каждой перемене",
            "Не возвращать на коротких (до 10 мин)",
            "Не возвращать на переменах до 20 мин",
            "Держать весь учебный день"
        )
        val gapCurrent = gapValues.indexOf(si("keep_gap", 0)).let { if (it < 0) 0 else it }
        choiceRow(c4, "Звук на переменах", gapNames, gapCurrent, gapNames[gapCurrent], soundMaster) { which ->
            SchoolSettings.setI(this, "keep_gap", gapValues[which])
            onSchoolChanged()
        }
        col.addView(c4)

        // ---------- Обои ----------
        val wpMaster = sb("wp_enabled", false)
        val c5 = card("Расписание на обоях", "Приложение рисует расписание на день картинкой и ставит на обои")
        toggleRow(
            c5, "Расписание на обоях",
            "Автоматически обновлять обои при выходе нового расписания",
            wpMaster
        ) { on ->
            SchoolSettings.setB(this, "wp_enabled", on)
            if (on) ensureNotificationPermission()
            onSchoolChanged()
        }
        val modeNames = listOf("Спросить уведомлением", "Ставить сразу сами")
        val mode = si("wp_mode", 0).coerceIn(0, 1)
        choiceRow(c5, "Как ставить", modeNames, mode, modeNames[mode], wpMaster) { which ->
            SchoolSettings.setI(this, "wp_mode", which)
        }
        val targetNames = listOf("Экран блокировки", "Рабочий стол", "Блокировка и рабочий стол")
        val target = si("wp_target", 0).coerceIn(0, 2)
        choiceRow(c5, "Куда ставить", targetNames, target, targetNames[target], wpMaster) { which ->
            SchoolSettings.setI(this, "wp_target", which)
        }
        val themeNames = listOf("Тёмный", "Светлый", "Синий")
        val theme = si("wp_theme", 0).coerceIn(0, 2)
        choiceRow(c5, "Цвет", themeNames, theme, themeNames[theme], wpMaster) { which ->
            SchoolSettings.setI(this, "wp_theme", which)
            render()
        }
        val sizeNames = listOf("Мелкий", "Средний", "Крупный")
        val size = si("wp_size", 1).coerceIn(0, 2)
        choiceRow(c5, "Размер текста", sizeNames, size, sizeNames[size], wpMaster) { which ->
            SchoolSettings.setI(this, "wp_size", which)
            render()
        }
        val posNames = listOf("Ниже часов", "По центру", "Выше")
        val pos = si("wp_pos", 0).coerceIn(0, 2)
        choiceRow(c5, "Положение текста", posNames, pos, posNames[pos], wpMaster) { which ->
            SchoolSettings.setI(this, "wp_pos", which)
            render()
        }
        toggleRow(c5, "Показывать время уроков", null, sb("wp_times", true), wpMaster) { on ->
            SchoolSettings.setB(this, "wp_times", on)
        }
        toggleRow(c5, "Показывать кабинеты", null, sb("wp_rooms", true), wpMaster) { on ->
            SchoolSettings.setB(this, "wp_rooms", on)
        }
        c5.addView(button("Предпросмотр") { previewWallpaper() })
        c5.addView(button("Поставить обои сейчас") {
            val app = applicationContext
            Thread {
                val result = SchoolWallpaper.applyNow(app)
                runOnUiThread { toast(result) }
            }.start()
        })
        c5.addView(button("Сбросить обои") { toast(SchoolWallpaper.reset(this)) })
        col.addView(c5)
    }

    private fun dayScheduleCard(dow: Int, dowName: String): View {
        val saved = SchoolData.loadLocalLessonsForDow(this, dow)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, dp(10), 0, dp(10))
        row.background = selectableBg()

        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this)
        title.text = dowName
        title.textSize = 16f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val count = TextView(this)
        count.text = if (saved.isNullOrEmpty()) "нет уроков ›" else "${saved.size} ур. ›"
        count.textSize = 14f
        count.setTextColor(if (saved.isNullOrEmpty()) 0xFF888888.toInt() else 0xFF7986CB.toInt())

        titleRow.addView(title)
        titleRow.addView(count)
        row.addView(titleRow)

        val summary = TextView(this)
        if (!saved.isNullOrEmpty()) {
            summary.text = saved.joinToString(", ") { "${it.first}. ${it.second}" }
            summary.setTextColor(0xFFCCCCCC.toInt())
        } else {
            summary.text = "нажмите, чтобы задать уроки"
            summary.setTextColor(0xFF777777.toInt())
        }
        summary.textSize = 13f
        summary.setPadding(0, dp(4), 0, 0)
        row.addView(summary)

        row.setOnClickListener { openTextEditor(dow, dowName) }
        return row
    }

    private fun pickTxtFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/*"))
        }
        try {
            startActivityForResult(intent, REQ_PICK_TXT)
        } catch (e: Exception) {
            toast("Не удалось открыть выбор файла")
        }
    }

    // ================= вкладка «Разрешения» =================

    private fun buildPermissionsTab(col: LinearLayout) {
        col.addView(tv("Разрешения", 26f, true))
        col.addView(hint("Разрешения для работы уведомлений, беззвучного режима и фоновой активности"))

        val cp = card("Системные разрешения", "Для правильной работы предоставьте все разрешения ниже")
        val nm = getSystemService(NotificationManager::class.java)

        val notifOk = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        permissionStatusRow(
            cp, notifOk, "Уведомления",
            "Напоминания перед уроками, информация о перемене и следующем кабинете"
        ) { ensureNotificationPermission() }

        val dndOk = nm.isNotificationPolicyAccessGranted
        permissionStatusRow(
            cp, dndOk, "Доступ к «Не беспокоить»",
            "Требуется для отключения звука звонка во время урока"
        ) { openSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }

        val alarmOk = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }
        permissionStatusRow(
            cp, alarmOk, "Точные будильники",
            "Чтобы таймеры и смена уроков срабатывали ровно секунда в секунду"
        ) {
            if (Build.VERSION.SDK_INT >= 31) {
                openSafely(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
        }
        col.addView(cp)

        val cb = card("Фоновая активность и автозапуск", "Особенно важно на Origin OS, MIUI, HyperOS, OneUI")
        cb.addView(tv("Чтобы система не усыпляла приложение:", 14f, true))
        cb.addView(hint("1. Включите «Автозапуск» в настройках приложения."))
        cb.addView(hint("2. В разделе «Батарея» выберите «Без ограничений»."))
        cb.addView(hint("3. Зафиксируйте SleepTile в недавних приложениях (значок замочка)."))
        cb.addView(button("Настройки приложения") {
            openSafely(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        cb.addView(button("Оптимизация батареи") {
            openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        col.addView(cb)
    }

    // ---------- Редактор расписания текстом с заготовками ----------

    private fun openTextEditor(dow: Int, dowName: String) {
        val saved = SchoolData.loadLocalLessonsForDow(this, dow)
        val existing = if (saved != null && saved.isNotEmpty()) {
            saved.joinToString("\n") { (n, s, r) ->
                if (r.isNotEmpty()) "$n. $s - $r" else "$n. $s"
            }
        } else ""

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        val input = EditText(this).apply {
            hint = "Номер. Предмет - кабинет\n(например: 1. Алгебра - 206)"
            minLines = 6
            setText(existing)
            textSize = 14f
            setSelection(text.length)
        }
        container.addView(input)

        val templateBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }

        val btnInsertTemplate = Button(this).apply {
            text = "Вставить шаблон"
            isAllCaps = false
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(4), 0)
            }
            setOnClickListener {
                val tpl = SchoolData.loadTemplate(this@MainActivity, dow)
                if (tpl != null) {
                    input.setText(tpl)
                    input.setSelection(tpl.length)
                    toast("Шаблон вставлен")
                } else {
                    toast("Шаблон пока не сохранён.\nНапишите уроки и нажмите «Сохранить шаблон»")
                }
            }
        }

        val btnSaveTemplate = Button(this).apply {
            text = "Сохранить шаблон"
            isAllCaps = false
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, 0, 0)
            }
            setOnClickListener {
                val current = input.text.toString().trim()
                if (current.isNotEmpty()) {
                    SchoolData.saveTemplate(this@MainActivity, dow, current)
                    toast("Шаблон для $dowName сохранён!")
                } else {
                    toast("Сначала введите уроки")
                }
            }
        }

        templateBar.addView(btnInsertTemplate)
        templateBar.addView(btnSaveTemplate)
        container.addView(templateBar)

        AlertDialog.Builder(this)
            .setTitle("$dowName — уроки")
            .setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                val text = input.text.toString()
                if (text.isBlank()) {
                    SchoolData.saveLocalLessons(this, dow, emptyList())
                    toast("$dowName: расписание очищено")
                } else {
                    val parsed = SchoolData.parseTextSchedule(text)
                    if (parsed.isEmpty()) {
                        toast("Не удалось распознать уроки. Формат:\n1. Предмет - кабинет")
                    } else {
                        SchoolData.saveLocalLessons(this, dow, parsed)
                        toast("$dowName: сохранено ${parsed.size} ур.")
                    }
                }
                onSchoolChanged()
            }
            .setNeutralButton("Очистить") { _, _ ->
                SchoolData.saveLocalLessons(this, dow, emptyList())
                toast("$dowName: расписание очищено")
                onSchoolChanged()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun onSchoolChanged() {
        SchoolSettings.setLastRun(this, System.currentTimeMillis())
        SchoolScheduler.tick(this)
        render()
    }

    private fun previewWallpaper() {
        val data = SchoolData.load(this)
        val rel = SchoolData.anyRelevantDay(this, data)
        if (rel == null) {
            toast("Нет уроков в расписании. Введите уроки на вкладке «Школа» выше.")
            return
        }
        val bmp = SchoolWallpaper.render(this, rel.first, rel.second)
        val img = ImageView(this)
        img.setImageBitmap(bmp)
        val wpx = dp(240)
        val hpx = (wpx.toFloat() * bmp.height / bmp.width).toInt()
        img.layoutParams = LinearLayout.LayoutParams(wpx, hpx)
        val box = LinearLayout(this)
        box.gravity = Gravity.CENTER
        box.setPadding(dp(8), dp(8), dp(8), dp(8))
        box.addView(img)
        AlertDialog.Builder(this)
            .setTitle("Предпросмотр: " + SchoolData.dayTitle(rel.first))
            .setView(box)
            .setPositiveButton("OK", null)
            .show()
    }

    // ================= вкладка «Сон» (НЕ ТРОГАЛИ) =================

    private fun buildSleepTab(col: LinearLayout) {
        col.addView(tv("Сон", 26f, true))
        col.addView(hint("Таймер сна и свои правила по времени"))

        val c1 = card("Таймер сна", "Плитка в шторке останавливает любое аудио: музыку, видео, подкасты.")
        val endAt = SleepTimer.endAt(this)
        if (endAt != 0L) {
            c1.addView(
                tv("Таймер идёт, сработает в " + DateFormat.getTimeFormat(this).format(Date(endAt)), 16f, true)
            )
        }
        val minutes = SleepTimer.defaultMinutes(this)
        val minutesLabel = tv("Время по нажатию на плитку: $minutes мин")
        val seek = SeekBar(this)
        seek.max = 23
        seek.progress = (minutes / 5 - 1).coerceIn(0, 23)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val m = (progress + 1) * 5
                minutesLabel.text = "Время по нажатию на плитку: $m мин"
                if (fromUser) SleepTimer.setDefaultMinutes(this@MainActivity, m)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        c1.addView(minutesLabel)
        c1.addView(seek)
        c1.addView(button("Добавить плитку в шторку") { addTile() })
        c1.addView(
            button(if (SleepTimer.isRunning(this)) "Отменить таймер" else "Запустить таймер сейчас") {
                if (SleepTimer.isRunning(this)) {
                    SleepTimer.cancel(this)
                } else {
                    SleepTimer.start(this, SleepTimer.defaultMinutes(this))
                }
                render()
            }
        )
        col.addView(c1)

        val c2 = card("Свои правила по времени", "Например: «Режим без звука» с 08:00 до 14:00 каждый день.")
        val rules = RuleStore.load(this)
        if (rules.isEmpty()) c2.addView(hint("Правил пока нет."))
        for (r in rules) c2.addView(ruleRow(r))
        c2.addView(button("+ Добавить правило") { editRule(null) })
        col.addView(c2)
    }

    private fun ruleRow(r: Rule): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(8), 0, dp(8))
        val info = TextView(this)
        info.text = r.action.title + "\n" + describe(r)
        info.textSize = 16f
        info.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        info.setOnClickListener { editRule(r) }
        val sw = Switch(this)
        sw.isChecked = r.enabled
        sw.setOnCheckedChangeListener { _, on -> commit(r, r.copy(enabled = on)) }
        row.addView(info)
        row.addView(sw)
        return row
    }

    private fun commit(old: Rule?, updated: Rule) {
        if (old != null) RuleExecutor.end(this, old)
        val list = RuleStore.load(this)
        val i = list.indexOfFirst { it.id == updated.id }
        if (i >= 0) list[i] = updated else list.add(updated)
        RuleStore.save(this, list)
        RuleScheduler.sync(this, updated)
        render()
    }

    private fun delete(r: Rule) {
        RuleExecutor.end(this, r)
        RuleScheduler.cancelAlarms(this, r.id)
        RuleStore.save(this, RuleStore.load(this).filter { it.id != r.id })
        render()
    }

    private fun editRule(existing: Rule?) {
        var action = existing?.action ?: RuleAction.SILENT
        var start = existing?.startMin ?: (8 * 60)
        var end = if (existing != null && existing.endMin >= 0) existing.endMin else 14 * 60
        var days = existing?.days ?: ALL_DAYS

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            RuleAction.values().map { it.title }
        )
        spinner.setSelection(action.ordinal)

        val startBtn = Button(this)
        startBtn.isAllCaps = false
        val endBtn = Button(this)
        endBtn.isAllCaps = false
        val daysBtn = Button(this)
        daysBtn.isAllCaps = false

        fun refresh() {
            startBtn.text = if (action.hasEnd) "Начало: ${fmt(start)}" else "Время: ${fmt(start)}"
            endBtn.text = "Конец: ${fmt(end)}"
            endBtn.visibility = if (action.hasEnd) View.VISIBLE else View.GONE
            daysBtn.text = "Дни: ${daysText(days)}"
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                action = RuleAction.values()[pos]
                refresh()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        startBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                start = h * 60 + m
                refresh()
            }, start / 60, start % 60, true).show()
        }
        endBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                end = h * 60 + m
                refresh()
            }, end / 60, end % 60, true).show()
        }
        daysBtn.setOnClickListener {
            val checked = BooleanArray(7) { ((days shr it) and 1) == 1 }
            AlertDialog.Builder(this)
                .setTitle("Дни недели")
                .setMultiChoiceItems(DAY_FULL, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    var d = 0
                    for (i in 0..6) if (checked[i]) d = d or (1 shl i)
                    days = if (d == 0) ALL_DAYS else d
                    refresh()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        box.addView(spinner)
        box.addView(startBtn)
        box.addView(endBtn)
        box.addView(daysBtn)
        refresh()

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Новое правило" else "Правило")
            .setView(box)
            .setPositiveButton("Сохранить") { _, _ ->
                if (action.hasEnd && start == end) {
                    toast("Начало и конец совпадают — правило не сохранено")
                } else {
                    val rule = Rule(
                        id = existing?.id ?: RuleStore.nextId(this),
                        action = action,
                        startMin = start,
                        endMin = if (action.hasEnd) end else -1,
                        days = days,
                        enabled = existing?.enabled ?: true
                    )
                    commit(existing, rule)
                }
            }
            .setNegativeButton("Отмена", null)
        if (existing != null) {
            builder.setNeutralButton("Удалить") { _, _ -> delete(existing) }
        }
        builder.show()
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val sbm = getSystemService(StatusBarManager::class.java)
                sbm.requestAddTileService(
                    ComponentName(this, SleepTileService::class.java),
                    "Таймер сна",
                    Icon.createWithResource(this, R.drawable.ic_tile),
                    mainExecutor
                ) { }
            } catch (e: Exception) {
            }
        }
        toast("Если плитка не появилась: потяните шторку, нажмите ✎ и перетащите «Таймер сна» наверх")
    }

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Не удалось открыть этот экран настроек")
        }
    }

    // ================= вкладка «Журнал» =================

    private fun buildLogTab(col: LinearLayout) {
        col.addView(tv("Журнал", 26f, true))
        col.addView(hint("Когда на самом деле срабатывали таймер, правила, уроки и обои"))
        val c = card(null)
        val log = EventLog.all(this)
        if (log.isEmpty()) {
            c.addView(tv("Пока пусто."))
        } else {
            for (entry in log) c.addView(tv(entry, 13f))
        }
        c.addView(button("Очистить журнал") {
            EventLog.clear(this)
            render()
        })
        col.addView(c)
    }
}
