package ru.schedule

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {
    private lateinit var data: Array<MutableList<Lesson>>
    private lateinit var head: TextView
    private lateinit var list: LinearLayout
    private val chips = ArrayList<TextView>()
    private var day = 0
    private val days = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    private val short = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val green = 0xFF1E3D34.toInt()
    private val chalk = 0xFFF3F1E7.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        data = Store.load(this)
        day = today()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(20), dp(12), dp(8))
            setBackgroundColor(0xFFFAFAF7.toInt())
        }
        root.addView(TextView(this).apply {
            text = "Расписание уроков"; textSize = 24f; setTextColor(green)
            setTypeface(null, Typeface.BOLD); setPadding(dp(4), 0, 0, dp(12))
        })
        val tabs = LinearLayout(this)
        for (i in 0..6) {
            val t = TextView(this).apply {
                text = short[i]; gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(10)); setOnClickListener { day = i; render() }
            }
            tabs.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            chips.add(t)
        }
        root.addView(tabs)
        head = TextView(this).apply { textSize = 18f; setTextColor(green); setPadding(dp(4), dp(16), 0, dp(4)) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(head)
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.addView(Button(this).apply { text = "Добавить"; setOnClickListener { addLesson() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(Button(this).apply { text = "Копировать"; setOnClickListener { copyDay() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(Button(this).apply { text = "Вид виджета"; setOnClickListener { look() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(bar)
        setContentView(root)
        render()
    }

    private fun render() {
        chips.forEachIndexed { i, t ->
            t.setTextColor(if (i == day) chalk else 0xFF444444.toInt())
            t.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (i == day) green else 0xFFECEFEA.toInt())
            }
        }
        head.text = days[day]
        list.removeAllViews()
        val ls = data[day]
        ls.sortBy { it.start }
        if (ls.isEmpty()) list.addView(TextView(this).apply {
            text = "В этот день уроков нет. Нажмите «Добавить»."; setPadding(dp(4), dp(8), 0, 0)
        })
        ls.forEach { list.addView(row(it)) }
    }

    private fun row(l: Lesson) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(EditText(this@MainActivity).apply {
            setText(l.name); hint = "Предмет"; setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { l.name = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(timeBtn(l.start) { l.start = it; if (l.end <= it) l.end = it + 45; render() })
        addView(timeBtn(l.end) {
            if (it > l.start) { l.end = it; render() }
            else Toast.makeText(this@MainActivity, "Конец должен быть позже начала", Toast.LENGTH_SHORT).show()
        })
        addView(Button(this@MainActivity).apply {
            text = "✕"; minWidth = 0; minimumWidth = 0
            setOnClickListener { data[day].remove(l); render() }
        })
    }

    private fun timeBtn(m: Int, onSet: (Int) -> Unit) = Button(this).apply {
        text = fmt(m); minWidth = 0; minimumWidth = 0
        setOnClickListener {
            TimePickerDialog(this@MainActivity, { _, h, min -> onSet(h * 60 + min) }, m / 60, m % 60, true).show()
        }
    }

    private fun addLesson() {
        val ls = data[day]
        val s = minOf(if (ls.isEmpty()) 510 else ls.maxOf { it.end } + 10, 22 * 60)
        ls.add(Lesson("Урок ${ls.size + 1}", s, s + 45))
        render()
    }

    private fun copyDay() {
        val on = BooleanArray(7)
        AlertDialog.Builder(this).setTitle("Копировать «${days[day]}» в:")
            .setMultiChoiceItems(days.toTypedArray(), on) { _, i, c -> on[i] = c }
            .setPositiveButton("Копировать") { _, _ ->
                for (i in 0..6) if (on[i] && i != day) data[i] = data[day].map { it.copy() }.toMutableList()
                Toast.makeText(this, "Готово", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null).show()
    }

    // цвет и прозрачность виджета
    private fun look() {
        val saved = Store.look(this)
        var ci = saved.first
        var al = saved.second
        val start = if (ci in PRESETS.indices) PRESETS[ci].let { it.first.toInt() to it.second.toInt() } else Store.custom(this)
        var cb = start.first
        var ca = start.second
        var tgt = 0
        var last = 0L

        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, dp(16), 0, dp(6)) }
        fun push() {
            Store.setCustom(this, cb, ca)
            Store.setLook(this, ci, al)
            val t = System.currentTimeMillis()
            if (t - last > 150) { last = t; Widget.update(this) }
        }

        val prev = TextView(this).apply {
            text = "12:58  до конца урока"; textSize = 20f; gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD); setPadding(0, dp(14), 0, dp(14))
        }
        val dots = ArrayList<TextView>()
        val colors = LinearLayout(this)
        repeat(PRESETS.size) {
            val d = TextView(this)
            colors.addView(d, LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(dp(3), 0, dp(3), 0) })
            dots.add(d)
        }
        fun refresh() {
            prev.setTextColor(ca)
            prev.background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(cb) }
            dots.forEachIndexed { i, d ->
                d.background = GradientDrawable().apply {
                    setShape(GradientDrawable.OVAL)
                    setColor(PRESETS[i].first.toInt())
                    setStroke(dp(if (i == ci) 4 else 1), if (i == ci) PRESETS[i].second.toInt() else 0xFF999999.toInt())
                }
            }
        }

        val pad = Pad(this, cb) { col ->
            if (tgt == 0) cb = col else ca = col
            ci = PRESETS.size
            push()
            refresh()
        }
        dots.forEachIndexed { i, d ->
            d.setOnClickListener {
                ci = i
                cb = PRESETS[i].first.toInt(); ca = PRESETS[i].second.toInt()
                pad.set(if (tgt == 0) cb else ca)
                push(); refresh()
            }
        }

        val segs = listOf("Фон", "Акцент").map {
            TextView(this).apply { text = it; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
        }
        fun paintSeg() = segs.forEachIndexed { i, t ->
            t.setTextColor(if (i == tgt) chalk else 0xFF444444.toInt())
            t.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (i == tgt) green else 0xFFECEFEA.toInt())
            }
        }
        val segRow = LinearLayout(this)
        segs.forEachIndexed { i, t ->
            t.setOnClickListener { tgt = i; pad.set(if (i == 0) cb else ca); paintSeg() }
            segRow.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }

        val seek = SeekBar(this).apply {
            max = 70; progress = 100 - al
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { al = 100 - p; push() }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        refresh(); paintSeg()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        box.addView(prev)
        box.addView(label("Готовые цвета")); box.addView(colors)
        box.addView(label("Свой цвет")); box.addView(segRow)
        box.addView(pad, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        box.addView(label("Прозрачность фона")); box.addView(seek)
        AlertDialog.Builder(this).setTitle("Вид виджета")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Готово", null)
            .setOnDismissListener { Widget.update(this) }
            .show()
    }

    override fun onPause() {
        super.onPause()
        Store.save(this, data)
        Widget.update(this)
    }
}
