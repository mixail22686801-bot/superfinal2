package ru.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Lesson(var name: String, var start: Int, var end: Int)

// цвета виджета: фон и акцент
val PRESETS = listOf(
    0xFF1E3D34 to 0xFFF4D77B,
    0xFF1B2A4A to 0xFF8EC5FF,
    0xFF222226 to 0xFFFF9F43,
    0xFF4A1F2B to 0xFFFFB4A2,
    0xFF35275A to 0xFFD6B8FF,
    0xFF0F4C5C to 0xFF7FE0D0,
    0xFFF3F1E7 to 0xFFB3541E
)

fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

// 0 = понедельник ... 6 = воскресенье
fun today(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

object Store {
    private fun p(c: Context) = c.getSharedPreferences("s", 0)

    fun load(c: Context): Array<MutableList<Lesson>> {
        val r = Array(7) { mutableListOf<Lesson>() }
        try {
            val a = JSONArray(p(c).getString("d", "[]"))
            for (i in 0 until minOf(a.length(), 7)) {
                val d = a.getJSONArray(i)
                for (j in 0 until d.length()) {
                    val o = d.getJSONObject(j)
                    r[i].add(Lesson(o.getString("n"), o.getInt("s"), o.getInt("e")))
                }
            }
        } catch (e: Exception) {
        }
        return r
    }

    fun save(c: Context, d: Array<MutableList<Lesson>>) {
        val a = JSONArray()
        for (day in d) {
            val j = JSONArray()
            for (l in day) j.put(JSONObject().put("n", l.name).put("s", l.start).put("e", l.end))
            a.put(j)
        }
        p(c).edit().putString("d", a.toString()).apply()
    }

    // номер цвета и непрозрачность фона (30..100)
    fun look(c: Context) = p(c).getInt("c", 0) to p(c).getInt("a", 100)

    fun setLook(c: Context, i: Int, a: Int) = p(c).edit().putInt("c", i).putInt("a", a).apply()

    fun custom(c: Context) = p(c).getInt("cb", PRESETS[0].first.toInt()) to p(c).getInt("ca", PRESETS[0].second.toInt())

    fun setCustom(c: Context, b: Int, a: Int) = p(c).edit().putInt("cb", b).putInt("ca", a).apply()
}

object Widget {
    fun update(c: Context) {
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, ScheduleWidget::class.java))
        if (ids.isEmpty()) return

        val now = Calendar.getInstance()
        val sec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND)
        val ls = Store.load(c)[today()].sortedBy { it.start }
        val curI = ls.indexOfFirst { sec >= it.start * 60 && sec < it.end * 60 }
        val nextI = ls.indexOfFirst { it.start * 60 > sec }
        val cur = ls.getOrNull(curI)
        val next = ls.getOrNull(nextI)

        val (head, caption, target) = when {
            cur != null -> Triple(cur.name, "до конца урока", cur.end * 60)
            next != null -> Triple(
                if (sec < ls[0].start * 60) "Уроки ещё не начались" else "Перемена",
                "до начала урока", next.start * 60
            )
            else -> Triple(if (ls.isEmpty()) "Сегодня уроков нет" else "Уроки закончились", "", -1)
        }
        val nextLine =
            if (next != null) "Далее: ${next.name} в ${fmt(next.start)}"
            else if (cur != null) "Это последний урок" else ""

        val (ci, alpha) = Store.look(c)
        val (bg, accent) = if (ci in PRESETS.indices) PRESETS[ci].let { it.first.toInt() to it.second.toInt() } else Store.custom(c)
        val dark = (Color.red(bg) * 299 + Color.green(bg) * 587 + Color.blue(bg) * 114) / 1000 < 140
        val text = if (dark) 0xFFF3F1E7.toInt() else 0xFF1F2A26.toInt()
        val sub = (text and 0xFFFFFF) or 0xB3000000.toInt()
        val hl = (accent and 0xFFFFFF) or 0x4D000000
        val click = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        for (id in ids) {
            // размер виджета -> масштаб текста
            val o = mgr.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).let { if (it > 0) it else 340 }
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).let { if (it > 0) it else 180 }
            val k = minOf(w / 340f, h / 170f).coerceIn(0.6f, 2.4f)

            val v = RemoteViews(c.packageName, R.layout.widget)
            fun sz(i: Int, s: Float) = v.setTextViewTextSize(i, TypedValue.COMPLEX_UNIT_SP, s * k)

            v.setInt(R.id.bg, "setColorFilter", bg)
            v.setInt(R.id.bg, "setImageAlpha", alpha * 255 / 100)
            v.setTextViewText(R.id.t1, head); v.setTextColor(R.id.t1, text); sz(R.id.t1, 24f)
            v.setTextViewText(R.id.t2, caption); v.setTextColor(R.id.t2, sub); sz(R.id.t2, 14f)
            v.setTextViewText(R.id.t3, nextLine); v.setTextColor(R.id.t3, text); sz(R.id.t3, 16f)
            v.setTextColor(R.id.timer, accent)
            v.setViewVisibility(R.id.timer, if (target >= 0) View.VISIBLE else View.GONE)
            if (target >= 0) {
                sz(R.id.timer, if (target - sec >= 3600) 38f else 54f)
                v.setChronometerCountDown(R.id.timer, true)
                v.setChronometer(R.id.timer, SystemClock.elapsedRealtime() + (target - sec) * 1000L, null, true)
            }

            // список уроков дня справа
            v.removeAllViews(R.id.list)
            v.setViewVisibility(R.id.list, if (w < 220 || ls.isEmpty()) View.GONE else View.VISIBLE)
            val avail = h - 26f
            val n = ls.size
            val rowSp = minOf(maxOf((avail / maxOf(n, 1) - 6f) / 1.2f, 10f), 16f * k)
            val rows = maxOf(1, (avail / (rowSp * 1.2f + 6f)).toInt())
            val mid = if (curI >= 0) curI else if (nextI >= 0) nextI else n - 1
            val from = (mid - rows / 2).coerceIn(0, maxOf(0, n - rows))
            for (i in from until minOf(n, from + rows)) {
                val r = RemoteViews(c.packageName, R.layout.row)
                r.setTextViewText(R.id.item, "${i + 1}. ${ls[i].name}")
                r.setTextViewTextSize(R.id.item, TypedValue.COMPLEX_UNIT_SP, rowSp)
                r.setTextColor(R.id.item, if (i == curI) accent else text)
                r.setInt(R.id.item, "setBackgroundColor", if (i == curI) hl else 0)
                // на перемене подсвечена линия между прошедшим и следующим уроком
                r.setInt(R.id.line, "setBackgroundColor", if (curI < 0 && i == nextI) accent else 0)
                v.addView(R.id.list, r)
            }

            v.setOnClickPendingIntent(R.id.root, click)
            mgr.updateAppWidget(id, v)
        }

        // следующее обновление — ровно в момент начала/конца ближайшего урока
        val edge = ls.flatMap { listOf(it.start * 60, it.end * 60) }.filter { it > sec }.minOrNull() ?: 86400
        val at = System.currentTimeMillis() + (edge - sec) * 1000L + 500
        val pi = PendingIntent.getBroadcast(
            c, 0, Intent(c, ScheduleWidget::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = c.getSystemService(AlarmManager::class.java)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class ScheduleWidget : AppWidgetProvider() {
    override fun onReceive(c: Context, i: Intent) = Widget.update(c)
}
