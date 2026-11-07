package com.tiyzi.odakbahcesi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tiyzi.odakbahcesi.data.AppDatabase
import com.tiyzi.odakbahcesi.data.DemoFriends
import com.tiyzi.odakbahcesi.data.Friend
import com.tiyzi.odakbahcesi.data.Messages
import com.tiyzi.odakbahcesi.data.PlantType
import com.tiyzi.odakbahcesi.data.Prefs
import com.tiyzi.odakbahcesi.data.Profile
import com.tiyzi.odakbahcesi.data.SavedTimer
import com.tiyzi.odakbahcesi.data.Session
import com.tiyzi.odakbahcesi.data.ShareCode
import com.tiyzi.odakbahcesi.data.SharePayload
import com.tiyzi.odakbahcesi.data.computeStats
import com.tiyzi.odakbahcesi.notify.Notifier
import com.tiyzi.odakbahcesi.notify.TimerAlarm
import com.tiyzi.odakbahcesi.ui.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Mode { IDLE, FOCUS, BREAK }

data class TimerState(
    val mode: Mode = Mode.IDLE,
    val totalSec: Int = 0,
    val remainingSec: Int = 0,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val subject: String = "",
    val plant: PlantType = PlantType.PAPATYA,
) {
    val progress: Float
        get() = if (totalSec == 0) 0f else (1f - remainingSec / totalSec.toFloat()).coerceIn(0f, 1f)
}

sealed interface Outcome {
    data class Grown(
        val plant: PlantType,
        val minutes: Int,
        val coins: Int,
        val xp: Int,
        val newLevel: Int?,
        val message: String,
    ) : Outcome

    data class Withered(val reason: String, val message: String) : Outcome
}

class FocusViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private val prefs = Prefs(app)
    private val dao = AppDatabase.get(app).sessionDao()

    val profile: StateFlow<Profile> = prefs.profile
    val friends: StateFlow<List<Friend>> = prefs.friends
    val sessions: StateFlow<List<Session>> =
        dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private val _outcome = MutableStateFlow<Outcome?>(null)
    val outcome: StateFlow<Outcome?> = _outcome.asStateFlow()

    private val _quote = MutableStateFlow(Messages.idle.random())
    val quote: StateFlow<String> = _quote.asStateFlow()

    private val _showInterstitial = MutableStateFlow(false)
    val showInterstitial: StateFlow<Boolean> = _showInterstitial.asStateFlow()
    private var pendingInterstitial = false

    private var tickJob: Job? = null
    private var leftAt: Long = 0L

    init {
        val t = prefs.readTimer()
        if (t != null) {
            leftAt = t.leftAt
            _timer.value = TimerState(
                mode = runCatching { Mode.valueOf(t.mode) }.getOrDefault(Mode.IDLE),
                totalSec = t.totalSec,
                remainingSec = remainingUntil(t.endAt),
                startAt = t.startAt,
                endAt = t.endAt,
                subject = t.subject,
                plant = PlantType.of(t.plant),
            )
            if (_timer.value.mode != Mode.IDLE) startTicking()
        } else {
            Notifier.cancel(app, Notifier.ID_LIVE)
        }
    }

    val showAds: Boolean get() = !profile.value.premium

    // ---------------------------------------------------------------- Sayaç

    fun startFocus() {
        val p = profile.value
        begin(Mode.FOCUS, p.focusMin, p.subject, PlantType.of(p.selectedPlant))
        _quote.value = Messages.start.random()
    }

    fun startBreak() {
        val p = profile.value
        begin(Mode.BREAK, p.breakMin, "", PlantType.of(p.selectedPlant))
        _quote.value = "Gözlerini dinlendir, su iç, biraz esne. ☕"
    }

    /** Hızlı Ayarlar kutucuğundan gelen istek */
    fun startFocusFromTile() {
        if (_timer.value.mode != Mode.IDLE) return
        _outcome.value = null
        startFocus()
    }

    private fun begin(mode: Mode, minutes: Int, subject: String, plant: PlantType) {
        val now = System.currentTimeMillis()
        val total = minutes * 60
        val end = now + total * 1000L
        leftAt = 0L
        _timer.value = TimerState(mode, total, total, now, end, subject, plant)
        prefs.saveTimer(SavedTimer(mode.name, end, total, now, subject, plant.name, 0L))
        TimerAlarm.schedule(ctx, end)
        Notifier.cancel(ctx, Notifier.ID_DONE)
        if (mode == Mode.FOCUS) {
            Notifier.showLive(ctx, "Odak · $subject", "${plant.label} büyüyor 🌱", now, end, plant.final)
        } else {
            Notifier.showLive(ctx, "Mola zamanı", "Biraz dinlen 🧘", now, end, "☕")
        }
        startTicking()
    }

    private fun remainingUntil(endAt: Long): Int =
        ((endAt - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val t = _timer.value
                if (t.mode == Mode.IDLE) break
                val r = remainingUntil(t.endAt)
                _timer.value = t.copy(remainingSec = r)
                if (r <= 0 && leftAt == 0L) {
                    finish()
                    break
                }
                delay(250)
            }
        }
    }

    private fun clearRunning() {
        tickJob?.cancel()
        TimerAlarm.cancel(ctx)
        prefs.saveTimer(null)
        leftAt = 0L
        Notifier.cancel(ctx, Notifier.ID_LIVE)
        Notifier.cancel(ctx, Notifier.ID_WARN)
    }

    private fun finish() {
        val t = _timer.value
        clearRunning()
        if (t.mode == Mode.FOCUS) {
            val minutes = t.totalSec / 60
            val coins = minutes + (if (minutes >= 45) 10 else 0)
            val xp = minutes * 10
            val oldLevel = profile.value.level
            prefs.update { it.copy(coins = it.coins + coins, xp = it.xp + xp) }
            val newLevel = profile.value.level
            viewModelScope.launch {
                dao.insert(
                    Session(
                        startAt = t.startAt, endAt = t.endAt, minutes = minutes,
                        subject = t.subject, plant = t.plant.name, success = true
                    )
                )
            }
            _outcome.value = Outcome.Grown(
                plant = t.plant, minutes = minutes, coins = coins, xp = xp,
                newLevel = if (newLevel > oldLevel) newLevel else null,
                message = Messages.success.random(),
            )
            _quote.value = Messages.idle.random()
            countSessionForAd()
        } else {
            _quote.value = Messages.afterBreak.random()
        }
        _timer.value = TimerState()
    }

    fun giveUp() = wither("Seansı erken bitirdin.")

    fun stopBreak() {
        clearRunning()
        _timer.value = TimerState()
        _quote.value = Messages.afterBreak.random()
    }

    private fun wither(reason: String) {
        val t = _timer.value
        if (t.mode != Mode.FOCUS) return
        clearRunning()
        val end = minOf(System.currentTimeMillis(), t.endAt)
        val minutes = ((end - t.startAt) / 60_000L).toInt()
        viewModelScope.launch {
            dao.insert(
                Session(
                    startAt = t.startAt, endAt = end, minutes = minutes,
                    subject = t.subject, plant = t.plant.name, success = false
                )
            )
        }
        _outcome.value = Outcome.Withered(reason, Messages.fail.random())
        _timer.value = TimerState()
    }

    // ------------------------------------------------- Sonuç penceresi + reklam

    private fun countSessionForAd() {
        if (!showAds) return
        val next = profile.value.sessionsSinceAd + 1
        if (next >= SESSIONS_PER_INTERSTITIAL) {
            prefs.update { it.copy(sessionsSinceAd = 0) }
            pendingInterstitial = true
        } else {
            prefs.update { it.copy(sessionsSinceAd = next) }
        }
    }

    fun dismissOutcome() {
        _outcome.value = null
        if (pendingInterstitial) {
            pendingInterstitial = false
            _showInterstitial.value = true
        }
    }

    fun closeInterstitial() {
        _showInterstitial.value = false
    }

    // ------------------------------------------------ Dikkat dağıtıcı koruması

    fun onAppLeft() {
        if (_timer.value.mode != Mode.FOCUS || !profile.value.strictMode) return
        leftAt = System.currentTimeMillis()
        prefs.setLeftAt(leftAt)
        Notifier.show(ctx, Notifier.ID_WARN, "🌱 Bitkin solmak üzere!", Messages.warning.random())
    }

    fun onAppReturned() {
        Notifier.cancel(ctx, Notifier.ID_WARN)
        val t = _timer.value
        val left = leftAt
        if (t.mode != Mode.FOCUS || left == 0L) {
            leftAt = 0L
            return
        }
        leftAt = 0L
        prefs.setLeftAt(0L)
        val away = System.currentTimeMillis() - left
        val endedWithinGrace = t.endAt <= left + GRACE_MS
        if (away > GRACE_MS && !endedWithinGrace) {
            wither("Odak sırasında ${away / 1000} saniye başka uygulamadaydın.")
        }
    }

    // ---------------------------------------------------------- Mağaza / ayar

    fun buyOrSelectPlant(p: PlantType): Boolean {
        val pr = profile.value
        if (p.name in pr.owned) {
            prefs.update { it.copy(selectedPlant = p.name) }
            return true
        }
        if (pr.coins < p.price) return false
        prefs.update {
            it.copy(coins = it.coins - p.price, owned = it.owned + p.name, selectedPlant = p.name)
        }
        return true
    }

    fun selectTheme(theme: AppTheme): Boolean {
        if (theme.premium && !profile.value.premium) return false
        prefs.update { it.copy(theme = theme.name) }
        return true
    }

    fun setPremium(on: Boolean) = prefs.update {
        it.copy(premium = on, theme = if (!on && AppTheme.of(it.theme).premium) AppTheme.ORMAN.name else it.theme)
    }

    fun rewardAd() = prefs.update { it.copy(coins = it.coins + AD_REWARD) }
    fun setFocusMin(m: Int) = prefs.update { it.copy(focusMin = m) }
    fun setBreakMin(m: Int) = prefs.update { it.copy(breakMin = m) }
    fun setSubject(s: String) = prefs.update { it.copy(subject = s) }
    fun setStrictMode(on: Boolean) = prefs.update { it.copy(strictMode = on) }
    fun setLiquidGlass(on: Boolean) = prefs.update { it.copy(liquidGlass = on) }
    fun setName(n: String) = prefs.update { it.copy(name = n.take(24)) }

    // ---------------------------------------------------------- Arkadaşlar

    fun myWeekMin(): Int = computeStats(sessions.value).weekTotal

    fun myShareCode(): String {
        val list = sessions.value
        val st = computeStats(list)
        val ok = list.filter { it.success }
        val p = profile.value
        return ShareCode.make(
            SharePayload(
                u = p.userId, n = p.name, l = p.level, w = st.weekTotal, t = st.totalMin, s = st.streak,
                g = ok.size, p = ok.take(12).map { PlantType.of(it.plant).final },
                d = System.currentTimeMillis() / 1000.0,
            )
        )
    }

    fun shareMessage(): String =
        "🌱 Odak Bahçesi'nde beni arkadaş olarak ekle! Bu hafta ${myWeekMin()} dakika çalıştım. Kodum:\n${myShareCode()}"

    private fun canAddFriend(): Boolean =
        profile.value.premium || friends.value.count { !it.isDemo } < FREE_FRIEND_LIMIT

    /** Koddan arkadaş ekler veya günceller; kullanıcıya gösterilecek mesajı döndürür. */
    fun addFriend(code: String): String {
        val p = ShareCode.parse(code) ?: return "Kod okunamadı. Tamamını kopyaladığından emin ol."
        if (p.u == profile.value.userId) return "Bu senin kendi kodun 🙂"
        val f = Friend(p.u, p.n, p.l, p.w, p.t, p.s, p.g, p.p, (p.d * 1000).toLong(), false)
        if (friends.value.any { it.id == p.u }) {
            prefs.updateFriends { list -> list.map { if (it.id == p.u) f else it } }
            return "${p.n} güncellendi ✅"
        }
        if (!canAddFriend()) {
            return "Ücretsiz sürümde en fazla $FREE_FRIEND_LIMIT arkadaş eklenebilir. Premium ile sınırsız 👑"
        }
        prefs.updateFriends { it + f }
        return "${p.n} arkadaşlarına eklendi 🎉"
    }

    fun addDemoFriends() = prefs.updateFriends { list ->
        val ids = list.map { it.id }.toSet()
        list + DemoFriends.make().filter { it.id !in ids }
    }

    fun removeFriend(id: String) = prefs.updateFriends { list -> list.filter { it.id != id } }

    fun createGroup(name: String, goal: Int) = prefs.update { it.copy(groupName = name.trim(), groupGoal = goal) }
    fun leaveGroup() = prefs.update { it.copy(groupName = "") }

    companion object {
        const val GRACE_MS = 10_000L
        const val AD_REWARD = 15
        const val FREE_FRIEND_LIMIT = 3
        const val SESSIONS_PER_INTERSTITIAL = 2
    }
}
