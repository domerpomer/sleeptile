package com.example.sleeptile

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
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

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------- маленькие помощники для интерфейса ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float = 16f, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        }

    private fun button(s: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = s
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun divider(): View =
        View(this).apply {
            setBackgroundColor(0x33888888)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { setMargins(0, dp(16), 0, dp(8)) }
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

    // ---------- главный экран ----------

    private fun render() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(32))
        }

        // ===== Таймер сна =====
        col.addView(tv("Таймер сна", 22f, true))
        col.addView(tv("Плитка в шторке останавливает любое аудио: музыку, видео, подкасты."))

        val endAt = SleepTimer.endAt(this)
        if (endAt != 0L) {
            col.addView(
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
        col.addView(minutesLabel)
        col.addView(seek)

        col.addView(button("Добавить плитку в шторку") { addTile() })
        col.addView(
            button(if (SleepTimer.isRunning(this)) "Отменить таймер" else "Запустить таймер сейчас") {
                if (SleepTimer.isRunning(this)) {
                    SleepTimer.cancel(this)
                } else {
                    SleepTimer.start(this, SleepTimer.defaultMinutes(this))
                }
                render()
            }
        )

        // ===== Разрешения =====
        col.addView(divider())
        col.addView(tv("Разрешения", 22f, true))
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            col.addView(tv("✓ Доступ к режиму «Не беспокоить» выдан"))
        } else {
            col.addView(
                tv("✗ Нет доступа к режиму «Не беспокоить». Без него правила «Режим без звука», «Только вибрация» и «Не беспокоить» работать не будут.")
            )
            col.addView(button("Выдать доступ") {
                openSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            })
        }
        col.addView(tv("Чтобы Origin OS не убивала приложение в фоне, разрешите ему автозапуск и работу без ограничений батареи."))
        col.addView(button("Открыть настройки приложения") {
            openSafely(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        col.addView(button("Оптимизация батареи") {
            openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        // ===== Расписание =====
        col.addView(divider())
        col.addView(tv("Расписание", 22f, true))
        val rules = RuleStore.load(this)
        if (rules.isEmpty()) {
            col.addView(tv("Правил пока нет. Например: «Режим без звука» с 08:00 до 14:00 каждый день."))
        }
        for (r in rules) col.addView(ruleRow(r))
        col.addView(button("+ Добавить правило") { editRule(null) })

        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)
    }

    private fun ruleRow(r: Rule): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val info = TextView(this).apply {
            text = r.action.title + "\n" + describe(r)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { editRule(r) }
        }
        val sw = Switch(this)
        sw.isChecked = r.enabled
        sw.setOnCheckedChangeListener { _, on -> commit(r, r.copy(enabled = on)) }
        row.addView(info)
        row.addView(sw)
        return row
    }

    // ---------- сохранение правил ----------

    private fun commit(old: Rule?, updated: Rule) {
        if (old != null) RuleExecutor.end(this, old) // вернуть то, что было до старого правила
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

    // ---------- окно создания / правки правила ----------

    private fun editRule(existing: Rule?) {
        var action = existing?.action ?: RuleAction.SILENT
        var start = existing?.startMin ?: (8 * 60)
        var end = if (existing != null && existing.endMin >= 0) existing.endMin else 14 * 60
        var days = existing?.days ?: ALL_DAYS

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            RuleAction.values().map { it.title }
        )
        spinner.setSelection(action.ordinal)

        val startBtn = Button(this).apply { isAllCaps = false }
        val endBtn = Button(this).apply { isAllCaps = false }
        val daysBtn = Button(this).apply { isAllCaps = false }

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
                    Toast.makeText(this, "Начало и конец совпадают — правило не сохранено", Toast.LENGTH_LONG).show()
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

    // ---------- прочее ----------

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
        Toast.makeText(
            this,
            "Если плитка не появилась: потяните шторку, нажмите ✎ и перетащите «Таймер сна» наверх",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть этот экран настроек", Toast.LENGTH_LONG).show()
        }
    }
}
