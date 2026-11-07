package com.tiyzi.odakbahcesi.data

/**
 * Yetiştirilebilen bitkiler. Seans ilerledikçe bitki aşama aşama büyür.
 * price = 0 olanlar başlangıçta açıktır, diğerleri mağazadan altınla alınır.
 */
enum class PlantType(val label: String, val stages: List<String>, val price: Int) {
    PAPATYA("Papatya", listOf("🌰", "🌱", "🌿", "🌼"), 0),
    LALE("Lale", listOf("🌰", "🌱", "🌿", "🌷"), 0),
    KAKTUS("Kaktüs", listOf("🌰", "🌱", "🌵"), 40),
    AYCICEGI("Ayçiçeği", listOf("🌰", "🌱", "🌿", "🌻"), 80),
    GUL("Gül", listOf("🌰", "🌱", "🌿", "🌹"), 120),
    AGAC("Meşe", listOf("🌰", "🌱", "🌿", "🪴", "🌳"), 160),
    CAM("Çam", listOf("🌰", "🌱", "🌿", "🌲"), 200),
    KIRAZ("Kiraz Çiçeği", listOf("🌰", "🌱", "🌿", "🌸"), 260),
    PALMIYE("Palmiye", listOf("🌰", "🌱", "🌿", "🌴"), 350),
    MANTAR("Sihirli Mantar", listOf("🌰", "🟤", "🍄"), 500);

    val final: String get() = stages.last()

    /** 0..1 ilerlemeye göre o anki büyüme aşaması. */
    fun stageFor(progress: Float): String {
        val i = (progress * stages.size).toInt().coerceIn(0, stages.size - 1)
        return stages[i]
    }

    companion object {
        fun of(name: String): PlantType = entries.firstOrNull { it.name == name } ?: PAPATYA
    }
}

const val DEAD_PLANT = "🥀"

val SUBJECTS = listOf(
    "Matematik", "Fizik", "Kimya", "Biyoloji", "Türkçe",
    "Tarih", "Coğrafya", "İngilizce", "Kodlama", "Diğer"
)

val FOCUS_OPTIONS = listOf(10, 15, 25, 40, 50, 60, 90)
val BREAK_OPTIONS = listOf(5, 10, 15, 20)
