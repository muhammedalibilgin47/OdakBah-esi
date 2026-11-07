package com.tiyzi.odakbahcesi.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tiyzi.odakbahcesi.data.Messages
import com.tiyzi.odakbahcesi.data.Prefs
import com.tiyzi.odakbahcesi.data.todayKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Akıllı hatırlatıcı: her gün seçilen saatte; o gün çalışıldıysa sessiz kalır. */
object Reminders {
    const val ID_REMIND = 4

    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 20, Intent(c, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(pending(c))
        val p = Prefs(c).profile.value
        if (!p.reminderOn) return
        val zone = ZoneId.systemDefault()
        var at = LocalDateTime.of(LocalDate.now(), LocalTime.of(p.reminderHour, 0))
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(c))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val p = prefs.profile.value
        if (p.reminderOn && prefs.lastStudyDay != todayKey() && prefs.readTimer() == null) {
            Notifier.show(context, Reminders.ID_REMIND, "🌱 Bahçen seni bekliyor", Messages.reminder.random())
        }
        Reminders.schedule(context)
    }
}
