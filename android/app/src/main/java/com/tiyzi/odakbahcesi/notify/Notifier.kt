package com.tiyzi.odakbahcesi.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tiyzi.odakbahcesi.MainActivity
import com.tiyzi.odakbahcesi.R
import com.tiyzi.odakbahcesi.data.Messages
import com.tiyzi.odakbahcesi.data.PlantType
import com.tiyzi.odakbahcesi.data.Prefs

object Notifier {
    private const val CHANNEL = "odak_kanali"
    private const val CHANNEL_LIVE = "odak_canli"
    const val ID_DONE = 1
    const val ID_WARN = 2
    const val ID_LIVE = 3

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Odak bildirimleri", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Seans bitişi ve odak uyarıları"
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(CHANNEL_LIVE) == null) {
            val live = NotificationChannel(CHANNEL_LIVE, "Canlı sayaç", NotificationManager.IMPORTANCE_LOW)
            live.description = "Seans sürerken kilit ekranında ve bildirim panelinde geri sayım"
            live.setShowBadge(false)
            live.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(live)
        }
    }

    /** iPhone'daki Canlı Etkinlik / Dynamic Island karşılığı: geri sayan sabit bildirim. */
    @SuppressLint("MissingPermission")
    fun showLive(c: Context, title: String, text: String, startAt: Long, endAt: Long, emoji: String) {
        if (!canPost(c)) return
        ensureChannel(c)
        val open = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            c, ID_LIVE, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val total = ((endAt - startAt) / 1000).toInt().coerceAtLeast(1)
        val done = ((System.currentTimeMillis() - startAt) / 1000).toInt().coerceIn(0, total)
        val n = NotificationCompat.Builder(c, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$emoji  $title")
            .setContentText(text)
            .setSubText("Odak Bahçesi")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setProgress(total, done, false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(c).notify(ID_LIVE, n)
    }

    private fun canPost(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(c: Context, id: Int, title: String, text: String) {
        if (!canPost(c)) return
        ensureChannel(c)
        val open = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            c, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(id, n)
    }

    fun cancel(c: Context, id: Int) {
        NotificationManagerCompat.from(c).cancel(id)
    }
}

/** Seans bitiş anında (uygulama arka planda / ekran kapalıyken) bildirim gösterir. */
object TimerAlarm {
    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 10, Intent(c, TimerReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(c: Context, atMillis: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(c))
    }

    fun cancel(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(pending(c))
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val t = Prefs(context).readTimer() ?: return
        // Kullanıcı başka uygulamaya geçtiyse seans zaten solacak; tebrik etme.
        if (t.leftAt != 0L) return
        if (t.mode == "FOCUS") {
            Notifier.show(
                context, Notifier.ID_DONE,
                "Seans tamamlandı! ${PlantType.of(t.plant).final}",
                Messages.success.random()
            )
        } else if (t.mode == "BREAK") {
            Notifier.show(context, Notifier.ID_DONE, "Mola bitti ⏰", Messages.afterBreak.random())
        }
    }
}
