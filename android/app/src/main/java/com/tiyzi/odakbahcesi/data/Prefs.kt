package com.tiyzi.odakbahcesi.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Kullanıcının oyunlaştırma profili ve ayarları. */
data class Profile(
    val coins: Int = 0,
    val xp: Int = 0,
    val owned: Set<String> = Prefs.DEFAULT_OWNED,
    val selectedPlant: String = "PAPATYA",
    val theme: String = "ORMAN",
    val premium: Boolean = false,
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val subject: String = "Matematik",
    val name: String = "Öğrenci",
    val userId: String = "",
    val strictMode: Boolean = true,
    val groupName: String = "",
    val groupGoal: Int = 600,
    val sessionsSinceAd: Int = 0,
    val liquidGlass: Boolean = true,   // yalnızca Android: yüzen cam alt çubuk
    val dailyGoal: Int = 120,
    val reminderOn: Boolean = true,
    val reminderHour: Int = 20,
    val sound: String = "off",
    val autoCycle: Boolean = false,
    val cycleCount: Int = 0,
    val lastRewardDay: String = "",
    val rewardStreak: Int = 0,
    val examName: String = "YKS",
    val examDate: Long = 0L,
    val badges: Set<String> = emptySet(),
) {
    val level: Int get() = 1 + xp / XP_PER_LEVEL
    val levelProgress: Float get() = (xp % XP_PER_LEVEL) / XP_PER_LEVEL.toFloat()
    val title: String
        get() = when {
            level >= 20 -> "Odak Ustası 🏆"
            level >= 12 -> "Orman Bekçisi 🌲"
            level >= 7 -> "Bahçıvan 🧑‍🌾"
            level >= 3 -> "Filiz Yetiştirici 🌿"
            else -> "Tohum 🌰"
        }

    companion object {
        const val XP_PER_LEVEL = 300
    }
}

/** Uygulama kapansa bile devam eden sayacı geri yükleyebilmek için saklanan durum. */
data class SavedTimer(
    val mode: String,
    val endAt: Long,
    val totalSec: Int,
    val startAt: Long,
    val subject: String,
    val plant: String,
    val leftAt: Long,
)

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("odak_bahcesi", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _friends = MutableStateFlow(readFriends())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private fun readProfile(): Profile {
        var id = sp.getString("userId", null)
        if (id.isNullOrEmpty()) {
            id = UUID.randomUUID().toString().uppercase()
            sp.edit().putString("userId", id).apply()
        }
        return Profile(
            coins = sp.getInt("coins", 0),
            xp = sp.getInt("xp", 0),
            owned = sp.getStringSet("owned", null)?.toSet() ?: DEFAULT_OWNED,
            selectedPlant = sp.getString("selectedPlant", "PAPATYA") ?: "PAPATYA",
            theme = sp.getString("theme", "ORMAN") ?: "ORMAN",
            premium = sp.getBoolean("premium", false),
            focusMin = sp.getInt("focusMin", 25),
            breakMin = sp.getInt("breakMin", 5),
            subject = sp.getString("subject", "Matematik") ?: "Matematik",
            name = sp.getString("name", "Öğrenci") ?: "Öğrenci",
            userId = id,
            strictMode = sp.getBoolean("strictMode", true),
            groupName = sp.getString("groupName", "") ?: "",
            groupGoal = sp.getInt("groupGoal", 600),
            sessionsSinceAd = sp.getInt("sessionsSinceAd", 0),
            liquidGlass = sp.getBoolean("liquidGlass", true),
            dailyGoal = sp.getInt("dailyGoal", 120),
            reminderOn = sp.getBoolean("reminderOn", true),
            reminderHour = sp.getInt("reminderHour", 20),
            sound = sp.getString("sound", "off") ?: "off",
            autoCycle = sp.getBoolean("autoCycle", false),
            cycleCount = sp.getInt("cycleCount", 0),
            lastRewardDay = sp.getString("lastRewardDay", "") ?: "",
            rewardStreak = sp.getInt("rewardStreak", 0),
            examName = sp.getString("examName", "YKS") ?: "YKS",
            examDate = sp.getLong("examDate", 0L),
            badges = sp.getStringSet("badges", null)?.toSet() ?: emptySet(),
        )
    }

    @Synchronized
    fun update(block: (Profile) -> Profile) {
        val p = block(_profile.value)
        sp.edit()
            .putInt("coins", p.coins)
            .putInt("xp", p.xp)
            .putStringSet("owned", HashSet(p.owned))
            .putString("selectedPlant", p.selectedPlant)
            .putString("theme", p.theme)
            .putBoolean("premium", p.premium)
            .putInt("focusMin", p.focusMin)
            .putInt("breakMin", p.breakMin)
            .putString("subject", p.subject)
            .putString("name", p.name)
            .putString("userId", p.userId)
            .putBoolean("strictMode", p.strictMode)
            .putString("groupName", p.groupName)
            .putInt("groupGoal", p.groupGoal)
            .putInt("sessionsSinceAd", p.sessionsSinceAd)
            .putBoolean("liquidGlass", p.liquidGlass)
            .putInt("dailyGoal", p.dailyGoal)
            .putBoolean("reminderOn", p.reminderOn)
            .putInt("reminderHour", p.reminderHour)
            .putString("sound", p.sound)
            .putBoolean("autoCycle", p.autoCycle)
            .putInt("cycleCount", p.cycleCount)
            .putString("lastRewardDay", p.lastRewardDay)
            .putInt("rewardStreak", p.rewardStreak)
            .putString("examName", p.examName)
            .putLong("examDate", p.examDate)
            .putStringSet("badges", HashSet(p.badges))
            .apply()
        _profile.value = p
    }

    // ---------------------------------------------------------------- Arkadaşlar

    private fun readFriends(): List<Friend> {
        val raw = sp.getString("friends", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Friend.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun updateFriends(block: (List<Friend>) -> List<Friend>) {
        val list = block(_friends.value)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("friends", arr.toString()).apply()
        _friends.value = list
    }

    // ---------------------------------------------------------------- Sayaç

    fun saveTimer(t: SavedTimer?) {
        val e = sp.edit()
        if (t == null) {
            listOf("t_mode", "t_end", "t_total", "t_start", "t_subject", "t_plant", "t_left")
                .forEach { e.remove(it) }
        } else {
            e.putString("t_mode", t.mode)
                .putLong("t_end", t.endAt)
                .putInt("t_total", t.totalSec)
                .putLong("t_start", t.startAt)
                .putString("t_subject", t.subject)
                .putString("t_plant", t.plant)
                .putLong("t_left", t.leftAt)
        }
        e.apply()
    }

    fun readTimer(): SavedTimer? {
        val mode = sp.getString("t_mode", null) ?: return null
        return SavedTimer(
            mode = mode,
            endAt = sp.getLong("t_end", 0L),
            totalSec = sp.getInt("t_total", 0),
            startAt = sp.getLong("t_start", 0L),
            subject = sp.getString("t_subject", "") ?: "",
            plant = sp.getString("t_plant", "PAPATYA") ?: "PAPATYA",
            leftAt = sp.getLong("t_left", 0L),
        )
    }

    var lastStudyDay: String
        get() = sp.getString("lastStudyDay", "") ?: ""
        set(v) { sp.edit().putString("lastStudyDay", v).apply() }

    fun setLeftAt(value: Long) {
        sp.edit().putLong("t_left", value).apply()
    }

    companion object {
        val DEFAULT_OWNED = setOf("PAPATYA", "LALE")
    }
}

// ==================================================================== Sosyal

/** Profil koduyla eklenen arkadaş. */
data class Friend(
    val id: String,
    val name: String,
    val level: Int,
    val weekMin: Int,
    val totalMin: Int,
    val streak: Int,
    val grown: Int,
    val garden: List<String>,
    val updatedAt: Long,
    val isDemo: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("level", level).put("weekMin", weekMin)
        .put("totalMin", totalMin).put("streak", streak).put("grown", grown)
        .put("garden", JSONArray(garden)).put("updatedAt", updatedAt).put("isDemo", isDemo)

    companion object {
        fun fromJson(o: JSONObject): Friend {
            val g = o.optJSONArray("garden") ?: JSONArray()
            return Friend(
                id = o.getString("id"),
                name = o.optString("name"),
                level = o.optInt("level", 1),
                weekMin = o.optInt("weekMin"),
                totalMin = o.optInt("totalMin"),
                streak = o.optInt("streak"),
                grown = o.optInt("grown"),
                garden = (0 until g.length()).map { g.getString(it) },
                updatedAt = o.optLong("updatedAt"),
                isDemo = o.optBoolean("isDemo"),
            )
        }
    }
}

/** Profil kodu içeriği. iPhone sürümüyle birebir aynı biçim: iki platform birbirini ekleyebilir. */
data class SharePayload(
    val u: String, val n: String, val l: Int, val w: Int, val t: Int,
    val s: Int, val g: Int, val p: List<String>, val d: Double,
)

object ShareCode {
    const val PREFIX = "ODAK-"

    fun make(p: SharePayload): String {
        val o = JSONObject()
            .put("u", p.u).put("n", p.n).put("l", p.l).put("w", p.w).put("t", p.t)
            .put("s", p.s).put("g", p.g).put("p", JSONArray(p.p)).put("d", p.d)
        val b64 = android.util.Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        return PREFIX + b64
    }

    fun parse(raw: String): SharePayload? = runCatching {
        var s = raw.trim()
        val i = s.indexOf(PREFIX)
        if (i >= 0) s = s.substring(i + PREFIX.length)
        s = s.takeWhile { !it.isWhitespace() }
        val json = String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8)
        val o = JSONObject(json)
        val arr = o.optJSONArray("p") ?: JSONArray()
        SharePayload(
            u = o.getString("u"), n = o.optString("n", "Arkadaş"), l = o.optInt("l", 1),
            w = o.optInt("w"), t = o.optInt("t"), s = o.optInt("s"), g = o.optInt("g"),
            p = (0 until arr.length()).map { arr.getString(it) }, d = o.optDouble("d", 0.0),
        )
    }.getOrNull()
}

object DemoFriends {
    fun make(): List<Friend> {
        val now = System.currentTimeMillis()
        return listOf(
            Friend("demo-elif", "Elif (demo)", 4, 340, 1020, 5, 38, listOf("🌷", "🌻", "🌷", "🌳", "🌼", "🌷"), now, true),
            Friend("demo-mert", "Mert (demo)", 2, 125, 410, 2, 15, listOf("🌵", "🌼", "🌵"), now, true),
            Friend("demo-zeynep", "Zeynep (demo)", 7, 610, 2100, 12, 80, listOf("🌸", "🌲", "🌸", "🌴", "🌹", "🌸", "🌲"), now, true),
        )
    }
}
