package com.tiyzi.odakbahcesi.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Stats(
    val todayMin: Int,
    val streak: Int,
    val totalMin: Int,
    val successRate: Int,
    val grown: Int,
    val withered: Int,
    val week: List<Pair<String, Int>>,
    val bySubject: List<Pair<String, Int>>,
    val bestDayMin: Int,
) {
    val weekTotal: Int get() = week.sumOf { it.second }
}

private val DAY_NAMES = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

fun computeStats(sessions: List<Session>): Stats {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    fun dayOf(s: Session): LocalDate = Instant.ofEpochMilli(s.endAt).atZone(zone).toLocalDate()

    val ok = sessions.filter { it.success }
    val byDay: Map<LocalDate, Int> = ok.groupBy(::dayOf).mapValues { e -> e.value.sumOf { it.minutes } }

    // Seri: bugünden (veya bugün henüz çalışılmadıysa dünden) geriye ardışık günler
    var streak = 0
    var d = if (byDay.containsKey(today)) today else today.minusDays(1)
    while (byDay.containsKey(d)) {
        streak++
        d = d.minusDays(1)
    }

    val week = (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        DAY_NAMES[day.dayOfWeek.value - 1] to (byDay[day] ?: 0)
    }

    val bySubject = ok.groupBy { it.subject }
        .map { (subject, list) -> subject to list.sumOf { it.minutes } }
        .sortedByDescending { it.second }

    return Stats(
        todayMin = byDay[today] ?: 0,
        streak = streak,
        totalMin = ok.sumOf { it.minutes },
        successRate = if (sessions.isEmpty()) 0 else ok.size * 100 / sessions.size,
        grown = ok.size,
        withered = sessions.size - ok.size,
        week = week,
        bySubject = bySubject,
        bestDayMin = byDay.values.maxOrNull() ?: 0,
    )
}
