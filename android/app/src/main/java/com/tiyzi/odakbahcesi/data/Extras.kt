package com.tiyzi.odakbahcesi.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

data class Quest(
    val id: String, val emoji: String, val title: String, val reward: Int, val current: Int, val target: Int,
) {
    val done: Boolean get() = current >= target
}

object Quests {
    private val POOL = listOf("s2", "s3", "m60", "m90", "long40", "morning", "clean")

    /** Her gün aynı 3 görev (iPhone ile aynı seçim) */
    fun today(sessions: List<Session>): List<Quest> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todays = sessions.filter { Instant.ofEpochMilli(it.endAt).atZone(zone).toLocalDate() == today }
        val ok = todays.filter { it.success }
        val fails = todays.size - ok.size
        val mins = ok.sumOf { it.minutes }
        val longest = ok.maxOfOrNull { it.minutes } ?: 0
        val morning = ok.any { Instant.ofEpochMilli(it.endAt).atZone(zone).hour < 12 }

        var x = (today.year * 10000 + today.monthValue * 100 + today.dayOfMonth).toLong()
        val remaining = POOL.toMutableList()
        val picked = mutableListOf<String>()
        repeat(3) {
            x = (x * 1103515245L + 12345L) and 0x7FFFFFFFL
            picked += remaining.removeAt((x % remaining.size).toInt())
        }
        return picked.map { id ->
            when (id) {
                "s2" -> Quest(id, "🌱", "2 seans tamamla", 20, ok.size, 2)
                "s3" -> Quest(id, "🌿", "3 seans tamamla", 30, ok.size, 3)
                "m60" -> Quest(id, "⏱️", "Bugün 60 dk çalış", 25, mins, 60)
                "m90" -> Quest(id, "🔥", "Bugün 90 dk çalış", 40, mins, 90)
                "long40" -> Quest(id, "🏃", "40+ dakikalık bir seans bitir", 30, if (longest >= 40) 1 else 0, 1)
                "morning" -> Quest(id, "☀️", "Öğleden önce bir seans bitir", 25, if (morning) 1 else 0, 1)
                else -> Quest(id, "💎", "Hiç vazgeçmeden 2 seans bitir", 35, if (fails == 0) minOf(ok.size, 2) else 0, 2)
            }
        }
    }
}

object Wheel {
    val PRIZES = listOf(5, 10, 15, 20, 25, 30, 50, 100)
    private val WEIGHTS = listOf(20, 20, 18, 15, 10, 8, 6, 3)
    val COLORS = listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF26A69A, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60)

    fun pick(): Int {
        var r = Random.nextInt(WEIGHTS.sum())
        WEIGHTS.forEachIndexed { i, w ->
            if (r < w) return i
            r -= w
        }
        return 0
    }
}
