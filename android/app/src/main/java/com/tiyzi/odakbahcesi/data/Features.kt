package com.tiyzi.odakbahcesi.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// ==================================================================== Rozetler

data class Achievement(val id: String, val emoji: String, val title: String, val detail: String, val reward: Int)

object Achievements {
    val all = listOf(
        Achievement("ilk", "🌱", "İlk Filiz", "İlk odak seansını tamamla", 20),
        Achievement("bes", "🖐️", "Isınma Turu", "5 seans tamamla", 30),
        Achievement("yirmibes", "🌿", "Bahçıvan Çırağı", "25 seans tamamla", 60),
        Achievement("yuz", "🌳", "Orman Kurucu", "100 seans tamamla", 200),
        Achievement("seri3", "🔥", "Üç Gün Üst Üste", "3 günlük seri yap", 30),
        Achievement("seri7", "☄️", "Haftalık Seri", "7 günlük seri yap", 80),
        Achievement("seri30", "🏆", "Ay Boyu Disiplin", "30 günlük seri yap", 300),
        Achievement("saat10", "⏳", "10 Saat", "Toplam 10 saat çalış", 60),
        Achievement("saat50", "⌛", "50 Saat", "Toplam 50 saat çalış", 200),
        Achievement("maraton", "🏃", "Maraton", "90 dakikalık bir seans tamamla", 50),
        Achievement("gece", "🦉", "Gece Kuşu", "23:00 sonrası bir seans bitir", 25),
        Achievement("sabah", "🐦", "Erkenci Kuş", "08:00 öncesi bir seans bitir", 25),
        Achievement("hedef", "🎯", "Hedef Avcısı", "Günlük hedefine ulaş", 40),
        Achievement("koleksiyon", "🎒", "Koleksiyoncu", "5 farklı bitkiye sahip ol", 50),
        Achievement("botanik", "👑", "Botanik Uzmanı", "Tüm bitkileri topla", 300),
        Achievement("sosyal", "🤝", "Sosyal Kelebek", "Bir arkadaş ekle", 30),
    )

    fun earned(sessions: List<Session>, profile: Profile, friends: List<Friend>): Set<String> {
        val ok = sessions.filter { it.success }
        val st = computeStats(sessions)
        val zone = ZoneId.systemDefault()
        fun hour(s: Session) = Instant.ofEpochMilli(s.endAt).atZone(zone).hour
        val e = mutableSetOf<String>()
        if (ok.isNotEmpty()) e += "ilk"
        if (ok.size >= 5) e += "bes"
        if (ok.size >= 25) e += "yirmibes"
        if (ok.size >= 100) e += "yuz"
        if (st.streak >= 3) e += "seri3"
        if (st.streak >= 7) e += "seri7"
        if (st.streak >= 30) e += "seri30"
        if (st.totalMin >= 600) e += "saat10"
        if (st.totalMin >= 3000) e += "saat50"
        if (ok.any { it.minutes >= 90 }) e += "maraton"
        if (ok.any { hour(it) >= 23 || hour(it) < 4 }) e += "gece"
        if (ok.any { hour(it) in 4..7 }) e += "sabah"
        val byDay = ok.groupBy { Instant.ofEpochMilli(it.endAt).atZone(zone).toLocalDate() }
            .mapValues { d -> d.value.sumOf { it.minutes } }
        if (byDay.values.any { it >= profile.dailyGoal }) e += "hedef"
        if (profile.owned.size >= 5) e += "koleksiyon"
        if (profile.owned.size >= PlantType.entries.size) e += "botanik"
        if (friends.any { !it.isDemo }) e += "sosyal"
        return e
    }
}

fun todayKey(): String = LocalDate.now().toString()
fun yesterdayKey(): String = LocalDate.now().minusDays(1).toString()

// ==================================================================== Odak sesleri

enum class SoundKind(val label: String) {
    OFF("🔇 Sessiz"),
    RAIN("🌧️ Yağmur"),
    WAVES("🌊 Dalga"),
    WHITE("🤍 Beyaz gürültü"),
    DEEP("🟤 Derin");

    companion object {
        fun of(key: String): SoundKind = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: OFF
    }
}

/** Cihazda üretilen ortam sesi; ses dosyası gerekmez. */
object NoisePlayer {
    @Volatile private var running = false
    private var thread: Thread? = null

    @Synchronized
    fun play(kind: SoundKind) {
        stop()
        if (kind == SoundKind.OFF) return
        running = true
        thread = Thread {
            val rate = 44_100
            val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val buf = ShortArray(2048)
            var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
            var brown = 0f
            var t = 0.0
            val step = 1.0 / rate
            runCatching {
                track.play()
                while (running) {
                    for (i in buf.indices) {
                        val white = Random.nextFloat() * 2f - 1f
                        val v: Float = when (kind) {
                            SoundKind.WHITE -> white * 0.22f
                            SoundKind.RAIN -> {
                                b0 = 0.99886f * b0 + white * 0.0555179f
                                b1 = 0.99332f * b1 + white * 0.0750759f
                                b2 = 0.96900f * b2 + white * 0.1538520f
                                b3 = 0.86650f * b3 + white * 0.3104856f
                                b4 = 0.55000f * b4 + white * 0.5329522f
                                b5 = -0.7616f * b5 - white * 0.0168980f
                                val out = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f) * 0.07f
                                b6 = white * 0.115926f
                                out
                            }
                            SoundKind.DEEP -> {
                                brown = (brown + 0.02f * white) / 1.02f
                                brown * 2.8f
                            }
                            SoundKind.WAVES -> {
                                brown = (brown + 0.02f * white) / 1.02f
                                t += step
                                val lfo = (0.5 + 0.5 * sin(2 * PI * 0.09 * t)).toFloat()
                                brown * 3.2f * (0.25f + 0.75f * lfo)
                            }
                            SoundKind.OFF -> 0f
                        }
                        buf[i] = (v.coerceIn(-1f, 1f) * Short.MAX_VALUE * 0.8f).toInt().toShort()
                    }
                    track.write(buf, 0, buf.size)
                }
                track.stop()
            }
            track.release()
        }.apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.join(400)
        thread = null
    }
}
