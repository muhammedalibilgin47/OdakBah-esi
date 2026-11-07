package com.tiyzi.odakbahcesi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tiyzi.odakbahcesi.data.Achievement
import com.tiyzi.odakbahcesi.data.Achievements
import com.tiyzi.odakbahcesi.data.AppDatabase
import com.tiyzi.odakbahcesi.data.NoisePlayer
import com.tiyzi.odakbahcesi.data.SoundKind
import com.tiyzi.odakbahcesi.data.todayKey
import com.tiyzi.odakbahcesi.data.yesterdayKey
import com.tiyzi.odakbahcesi.notify.Reminders
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

    /** Günlük ödül: (altın, seri) */
    private val _dailyReward = MutableStateFlow<Pair<Int, Int>?>(null)
    val dailyReward: StateFlow<Pair<Int, Int>?> = _dailyReward.asStateFlow()

    private val _badgeToast = MutableStateFlow<Achievement?>(null)
    val badgeToast: StateFlow<Achievement?> = _badgeToast.asStateFlow()
    private val badgeQueue = ArrayDeque<Achievement>()

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
        if (mode == Mode.FOCUS) NoisePlayer.play(SoundKind.of(profile.value.sound)) else NoisePlayer.stop()
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
        NoisePlayer.stop()
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
            val session = Session(
                startAt = t.startAt, endAt = t.endAt, minutes = minutes,
                subject = t.subject, plant = t.plant.name, success = true
            )
            viewModelScope.launch { dao.insert(session) }
            prefs.lastStudyDay = todayKey()
            prefs.update { it.copy(cycleCount = it.cycleCount + 1) }
            checkAchievements(extra = session)
            _outcome.value = Outcome.Grown(
                plant = t.plant, minutes = minutes, coins = coins, xp = xp,
                newLevel = if (newLevel > oldLevel) newLevel else null,
                message = Messages.success.random(),
            )
            _quote.value = Messages.idle.random()
            countSessionForAd()
            _timer.value = TimerState()
            if (profile.value.autoCycle) {
                val long = profile.value.cycleCount % 4 == 0
                begin(Mode.BREAK, if (long) 15 else profile.value.breakMin, "", t.plant)
                _quote.value = if (long) "4 seans tamam! 15 dakikalık uzun mola hak ettin 🎉" else "Otomatik mola başladı ☕"
            }
            return
        } else {
            _quote.value = Messages.afterBreak.random()
        }
        _timer.value = TimerState()
        if (profile.value.autoCycle && _outcome.value == null && isForeground) startFocus()
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

    private var isForeground = true

    fun onAppLeft() {
        isForeground = false
        if (_timer.value.mode != Mode.FOCUS || !profile.value.strictMode) return
        leftAt = System.currentTimeMillis()
        prefs.setLeftAt(leftAt)
        Notifier.show(ctx, Notifier.ID_WARN, "🌱 Bitkin solmak üzere!", Messages.warning.random())
    }

    fun onAppReturned() {
        isForeground = true
        checkDailyReward()
        checkAchievements()
        Reminders.schedule(ctx)
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
        checkAchievements()
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
        checkAchievements()
        return "${p.n} arkadaşlarına eklendi 🎉"
    }

    fun addDemoFriends() = prefs.updateFriends { list ->
        val ids = list.map { it.id }.toSet()
        list + DemoFriends.make().filter { it.id !in ids }
    }

    fun removeFriend(id: String) = prefs.updateFriends { list -> list.filter { it.id != id } }

    fun createGroup(name: String, goal: Int) = prefs.update { it.copy(groupName = name.trim(), groupGoal = goal) }
    fun leaveGroup() = prefs.update { it.copy(groupName = "") }

    // -------------------------------------- Günlük hedef, rozet, ödül, ayarlar

    fun todayMin(): Int = computeStats(sessions.value).todayMin

    fun examDaysLeft(): Long? {
        val d = profile.value.examDate
        if (d <= 0L) return null
        val exam = Instant.ofEpochMilli(d).atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(LocalDate.now(), exam).coerceAtLeast(0)
    }

    fun checkAchievements(extra: Session? = null) {
        val list = if (extra != null) listOf(extra) + sessions.value else sessions.value
        val earned = Achievements.earned(list, profile.value, friends.value)
        val fresh = Achievements.all.filter { it.id in earned && it.id !in profile.value.badges }
        if (fresh.isEmpty()) return
        prefs.update { p -> p.copy(badges = p.badges + fresh.map { it.id }, coins = p.coins + fresh.sumOf { it.reward }) }
        badgeQueue.addAll(fresh)
        showNextBadge()
    }

    private fun showNextBadge() {
        if (_badgeToast.value != null || badgeQueue.isEmpty()) return
        _badgeToast.value = badgeQueue.removeFirst()
        viewModelScope.launch {
            delay(3_200)
            _badgeToast.value = null
            delay(350)
            showNextBadge()
        }
    }

    private fun checkDailyReward() {
        val p = profile.value
        val today = todayKey()
        if (p.lastRewardDay == today) return
        val streak = if (p.lastRewardDay == yesterdayKey()) p.rewardStreak + 1 else 1
        val coins = 5 + minOf(streak, 7) * 5
        prefs.update { it.copy(lastRewardDay = today, rewardStreak = streak, coins = it.coins + coins) }
        _dailyReward.value = coins to streak
    }

    fun closeDailyReward() { _dailyReward.value = null }

    fun setDailyGoal(m: Int) = prefs.update { it.copy(dailyGoal = m) }
    fun setAutoCycle(on: Boolean) = prefs.update { it.copy(autoCycle = on) }
    fun setExamName(n: String) = prefs.update { it.copy(examName = n.take(20)) }
    fun setExamDate(millis: Long) = prefs.update { it.copy(examDate = millis) }

    fun setReminder(on: Boolean, hour: Int = profile.value.reminderHour) {
        prefs.update { it.copy(reminderOn = on, reminderHour = hour) }
        Reminders.schedule(ctx)
    }

    fun setSound(key: String) {
        prefs.update { it.copy(sound = key) }
        if (_timer.value.mode == Mode.FOCUS) NoisePlayer.play(SoundKind.of(key))
    }

    override fun onCleared() {
        super.onCleared()
        NoisePlayer.stop()
    }

    companion object {
        const val GRACE_MS = 10_000L
        const val AD_REWARD = 15
        const val FREE_FRIEND_LIMIT = 3
        const val SESSIONS_PER_INTERSTITIAL = 2
    }
}
