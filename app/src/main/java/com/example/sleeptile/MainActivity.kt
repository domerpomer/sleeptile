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

    private var tab = 0
    private var scrollView: ScrollView? = null
    private var renderedTab = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = getSharedPreferences("ui", MODE_PRIVATE).getInt("tab", 0)
    }

    override fun onResume() {
        super.onResume()
        render()
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

    private fun button(s: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = s
            isAllCaps = false
            setOnClickListener { onClick() }
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
        parent: LinearLayout, title: String, hintText: String?, value: Boolean, onChange: (Boolean) -> Unit
    ) {
        val sw = Switch(this)
        sw.text = title
        sw.textSize = 16f
        sw.isChecked = value
        sw.setPadding(0, dp(10), 0, dp(2))
        sw.setOnCheckedChangeListener { _, on -> onChange(on) }
        parent.addView(sw)
        if (hintText != null) parent.addView(hint(hintText))
    }

    private fun choiceRow(
        parent: LinearLayout, title: String, options: List<String>, current: Int,
        valueText: String, onPick: (Int) -> Unit
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        row.background = selectableBg()

        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val v = TextView(this)
        v.text = "$valueText  ›"
        v.textSize = 15f
        v.setTextColor(0xFF7986CB.toInt())
        row.addView(t)
        row.addView(v)

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
        parent.addView(row)
    }

    private fun permissionRow(
        parent: LinearLayout, ok: Boolean, text: String, actionLabel: String, action: () -> Unit
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(6), 0, dp(6))
        val t = TextView(this)
        t.text = (if (ok) "✓ " else "✗ ") + text
        t.textSize = 15f
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(t)
        if (!ok) {
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
        col.setPadding(dp(14), dp(8), dp(14), dp(24))

        val scroll = ScrollView(this)
        scroll.addView(col)
        root.addView(
            scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        when (tab) {
            0 -> buildSchoolTab(col)
            1 -> buildSleepTab(col)
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
        bar.addView(tabButton(2, R.drawable.ic_list, "Журнал"), tabParams())
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
        col.addView(tv("Мои уроки", 26f, true))
        col.addView(hint("Расписание, звук и уведомления для школы"))

        // ---------- Мой класс ----------
        val klass = SchoolSettings.klass(this)
        val group = SchoolSettings.group(this)
        val c1 = card("Мой класс")
        choiceRow(
            c1, "Класс", SchoolSettings.CLASSES, SchoolSettings.CLASSES.indexOf(klass),
            if (klass.isEmpty()) "не выбран" else klass
        ) { which ->
            SchoolSettings.setKlass(this, SchoolSettings.CLASSES[which])
            onSchoolChanged()
        }
        choiceRow(
            c1, "Подгруппа", listOf("1 подгруппа", "2 подгруппа"), group - 1, "$group"
        ) { which ->
            SchoolSettings.setGroup(this, which + 1)
            onSchoolChanged()
        }
        toggleRow(
            c1, "Школьный режим",
            "Главный выключатель: пока он выключен, уведомления, звук и обои не работают",
            SchoolSettings.enabled(this)
        ) { on ->
            SchoolSettings.setEnabled(this, on)
            if (on) ensureNotificationPermission()
            onSchoolChanged()
        }
        c1.addView(button("Моё расписание на ближайшие дни") {
            AlertDialog.Builder(this)
                .setTitle("Расписание " + SchoolSettings.klass(this))
                .setMessage(SchoolData.describeUpcoming(this))
                .setPositiveButton("OK", null)
                .show()
        })
        col.addView(c1)

        // ---------- Ввод расписания текстом ----------
        val c2 = card("Расписание уроков",
            "Введите список уроков простым текстом.\n" +
            "Формат: «1. Алгебра - 206» (кабинет необязателен).\n" +
            "Парсер принимает разные тире (-, –, —) и точку/скобку после номера.")

        val dowNames = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница")
        for (dow in 1..5) {
            val saved = SchoolData.loadLocalLessonsForDow(this, dow)
            val label = dowNames[dow - 1]
            val info = if (saved != null && saved.isNotEmpty()) {
                "$label (${saved.size} ур.)"
            } else {
                "$label — не задано"
            }
            c2.addView(button(info) { openTextEditor(dow, dowNames[dow - 1]) })
        }
        c2.addView(hint("Расписание по дню недели действует каждую неделю. Вводить каждый день заново не нужно."))
        col.addView(c2)

        // ---------- Разрешения ----------
        val cp = card("Разрешения", "Там, где стоит ✗, нажмите «Выдать»")
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            permissionRow(cp, ok, "Уведомления", "Выдать") { ensureNotificationPermission() }
        }
        permissionRow(
            cp, nm.isNotificationPolicyAccessGranted,
            "Доступ к «Не беспокоить» (нужен для беззвучного режима)", "Выдать"
        ) { openSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            permissionRow(cp, am.canScheduleExactAlarms(), "Точные будильники", "Выдать") {
                openSafely(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
        }
        cp.addView(hint("Чтобы Origin OS не выгружала приложение: включите автозапуск и работу без ограничений батареи."))
        cp.addView(button("Настройки приложения") {
            openSafely(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        cp.addView(button("Оптимизация батареи") {
            openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        col.addView(cp)

        // ---------- Уведомления ----------
        val c3 = card("Уведомления")
        toggleRow(c3, "Уведомления о парах", "Общий выключатель всех уведомлений ниже", sb("notify", true)) { on ->
            SchoolSettings.setB(this, "notify", on)
        }
        toggleRow(c3, "За 15 мин до первого урока", "Предмет и кабинет первого урока", sb("notify_pre", true)) { on ->
            SchoolSettings.setB(this, "notify_pre", on)
        }
        toggleRow(c3, "Когда начался урок", "Тихое уведомление: предмет, кабинет, до скольки", sb("notify_start", true)) { on ->
            SchoolSettings.setB(this, "notify_start", on)
        }
        toggleRow(c3, "На перемене: какой урок дальше", "Предмет, кабинет, длина перемены и время звонка", sb("notify_break", true)) { on ->
            SchoolSettings.setB(this, "notify_break", on)
        }
        toggleRow(c3, "Когда уроки закончились", null, sb("notify_end", true)) { on ->
            SchoolSettings.setB(this, "notify_end", on)
        }
        col.addView(c3)

        // ---------- Звук ----------
        val c4 = card("Звук на уроках")
        toggleRow(
            c4, "Менять звук на уроках",
            "На время урока звонок выключается, после урока возвращается как было",
            SchoolSettings.silent(this)
        ) { on ->
            SchoolSettings.setSilent(this, on)
            onSchoolChanged()
        }
        val ringOptions = listOf("Беззвучный", "Вибрация")
        val ring = si("ring_mode", 0).coerceIn(0, 1)
        choiceRow(c4, "Режим звонка на уроке", ringOptions, ring, ringOptions[ring]) { which ->
            SchoolSettings.setI(this, "ring_mode", which)
            onSchoolChanged()
        }
        toggleRow(
            c4, "«Не беспокоить» на уроках",
            "Нужен доступ к режиму «Не беспокоить» (см. «Разрешения»)",
            SchoolSettings.dnd(this)
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
        choiceRow(c4, "Звук на переменах", gapNames, gapCurrent, gapNames[gapCurrent]) { which ->
            SchoolSettings.setI(this, "keep_gap", gapValues[which])
            onSchoolChanged()
        }
        col.addView(c4)

        // ---------- Обои ----------
        val c5 = card("Обои экрана блокировки", "Приложение рисует расписание на день картинкой и ставит её на экран блокировки")
        toggleRow(
            c5, "Расписание на обоях",
            "Когда выходит новое расписание, приложение предложит (или само поставит) обои",
            sb("wp_enabled", false)
        ) { on ->
            SchoolSettings.setB(this, "wp_enabled", on)
            if (on) ensureNotificationPermission()
            onSchoolChanged()
        }
        val modeNames = listOf("Спросить уведомлением", "Ставить сразу сами")
        val mode = si("wp_mode", 0).coerceIn(0, 1)
        choiceRow(c5, "Как ставить", modeNames, mode, modeNames[mode]) { which ->
            SchoolSettings.setI(this, "wp_mode", which)
        }
        val targetNames = listOf("Экран блокировки", "Блокировка и главный экран")
        val target = si("wp_target", 0).coerceIn(0, 1)
        choiceRow(c5, "Куда ставить", targetNames, target, targetNames[target]) { which ->
            SchoolSettings.setI(this, "wp_target", which)
        }
        val themeNames = listOf("Тёмный", "Светлый", "Синий")
        val theme = si("wp_theme", 0).coerceIn(0, 2)
        choiceRow(c5, "Цвет", themeNames, theme, themeNames[theme]) { which ->
            SchoolSettings.setI(this, "wp_theme", which)
            render()
        }
        val sizeNames = listOf("Мелкий", "Средний", "Крупный")
        val size = si("wp_size", 1).coerceIn(0, 2)
        choiceRow(c5, "Размер текста", sizeNames, size, sizeNames[size]) { which ->
            SchoolSettings.setI(this, "wp_size", which)
            render()
        }
        val posNames = listOf("Ниже часов", "По центру", "Выше")
        val pos = si("wp_pos", 0).coerceIn(0, 2)
        choiceRow(c5, "Положение текста", posNames, pos, posNames[pos]) { which ->
            SchoolSettings.setI(this, "wp_pos", which)
            render()
        }
        toggleRow(c5, "Показывать время уроков", null, sb("wp_times", true)) { on ->
            SchoolSettings.setB(this, "wp_times", on)
        }
        toggleRow(c5, "Показывать кабинеты", null, sb("wp_rooms", true)) { on ->
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
        c5.addView(button("Сбросить обои экрана блокировки") { toast(SchoolWallpaper.reset(this)) })
        c5.addView(hint("Приложение заменяет текущие обои экрана блокировки. «Сбросить» возвращает обои по умолчанию."))
        col.addView(c5)

        // ---------- Данные ----------
        val cd = card("Данные расписания")
        val data = SchoolData.load(this)
        val source = if (data.fromNetwork) "загружено из интернета" else "встроенный пример (12 и 16 ноября)"
        cd.addView(tv("Источник: $source", 14f))
        val status = SchoolSettings.lastStatus(this)
        if (status.isNotEmpty()) cd.addView(hint(status))
        cd.addView(button("Обновить расписание сейчас") {
            toast("Загружаю…")
            val app = applicationContext
            Thread {
                val result = SchoolFetcher.fetch(app)
                SchoolScheduler.tick(app)
                runOnUiThread {
                    toast(result)
                    render()
                }
            }.start()
        })
        cd.addView(button("Источник данных (адрес)") { editSource() })
        cd.addView(
            button(if (SchoolData.testActive(this)) "Остановить проверку" else "Проверка за 5 минут") {
                if (SchoolData.testActive(this)) {
                    SchoolData.stopTest(this)
                } else {
                    SchoolData.startTest(this)
                    ensureNotificationPermission()
                }
                onSchoolChanged()
            }
        )
        cd.addView(hint("Проверка: через минуту начнётся «урок 1» (звук выключится), потом «перемена», потом «урок 2». Всё закончится за 5 минут."))
        col.addView(cd)
    }

    // ---------- Редактор расписания текстом ----------

    private fun openTextEditor(dow: Int, dowName: String) {
        val saved = SchoolData.loadLocalLessonsForDow(this, dow)
        val existing = if (saved != null && saved.isNotEmpty()) {
            saved.joinToString("\n") { (n, s, r) ->
                if (r.isNotEmpty()) "$n. $s - $r" else "$n. $s"
            }
        } else ""

        val input = EditText(this)
        input.hint = "1. Алгебра - 206\n2. Русский язык - 316\n3. Физика"
        input.minLines = 6
        input.setText(existing)
        input.textSize = 14f
        input.setPadding(dp(16), dp(8), dp(16), dp(8))

        AlertDialog.Builder(this)
            .setTitle("$dowName — уроки")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val text = input.text.toString()
                if (text.isBlank()) {
                    // Очистить
                    SchoolData.saveLocalLessons(this, dow, emptyList())
                    toast("$dowName: расписание очищено")
                } else {
                    val parsed = SchoolData.parseTextSchedule(text)
                    if (parsed.isEmpty()) {
                        toast("Не удалось распознать ни одного урока. Формат:\n1. Предмет - кабинет")
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
        val rel = SchoolData.relevantDay(this, data, System.currentTimeMillis())
        if (rel == null) {
            toast("Нет расписания для выбранного класса. Выберите класс.")
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
            .setTitle("Предпросмотр обоев")
            .setView(box)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun editSource() {
        val input = EditText(this)
        input.setText(SchoolSettings.baseUrl(this))
        AlertDialog.Builder(this)
            .setTitle("Адрес файлов расписания")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                SchoolSettings.setBaseUrl(this, input.text.toString().trim())
                render()
            }
            .setNeutralButton("По умолчанию") { _, _ ->
                SchoolSettings.setBaseUrl(this, SchoolSettings.DEFAULT_BASE_URL)
                render()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ================= вкладка «Сон» =================

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
