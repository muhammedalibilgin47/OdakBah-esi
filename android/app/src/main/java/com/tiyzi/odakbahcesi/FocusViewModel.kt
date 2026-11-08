package com.tiyzi.odakbahcesi

import android.app.Activity
import android.app.Application
import com.tiyzi.odakbahcesi.data.Cloud
import com.tiyzi.odakbahcesi.data.SignInCancelled
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

/** Hesap durumu */
data class AccountState(
    val signedIn: Boolean = false,
    val email: String = "",
    val busy: Boolean = false,
    val lastSync: Long = 0L,
    val message: String? = null,
)

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
        val shiny: Boolean = false,
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

    private val _account = MutableStateFlow(
        Cloud.user?.let { AccountState(signedIn = true, email = it.email ?: it.displayName ?: "") } ?: AccountState()
    )
    val account: StateFlow<AccountState> = _account.asStateFlow()

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
            val isShiny = Math.random() < 0.05
            val bonus = if (isShiny) 25 else 0
            prefs.update {
                it.copy(
                    coins = it.coins + coins + bonus, xp = it.xp + xp,
                    shiny = if (isShiny) it.shiny + (t.startAt / 1000).toString() else it.shiny,
                )
            }
            val newLevel = profile.value.level
            val session = Session(
                startAt = t.startAt, endAt = t.endAt, minutes = minutes,
                subject = t.subject, plant = t.plant.name, success = true
            )
            viewModelScope.launch { dao.insert(session) }
            prefs.lastStudyDay = todayKey()
            prefs.update { it.copy(cycleCount = it.cycleCount + 1) }
            checkAchievements(extra = session)
            viewModelScope.launch { delay(1500); syncQuiet() }
            _outcome.value = Outcome.Grown(
                plant = t.plant, minutes = minutes, coins = coins + bonus, xp = xp,
                newLevel = if (newLevel > oldLevel) newLevel else null,
                message = if (isShiny) "✨ İnanılmaz! Nadir PARLAK bir bitki yetiştirdin! (+25 bonus)" else Messages.success.random(),
                shiny = isShiny,
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

    private var lastAutoSync = 0L

    fun onAppReturned() {
        isForeground = true
        if (Cloud.user != null && System.currentTimeMillis() - lastAutoSync > 10 * 60_000L) {
            lastAutoSync = System.currentTimeMillis()
            viewModelScope.launch { syncQuiet(); refreshFriends() }
        }
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

    // ---------------------------------------------------------- Hesap + bulut

    fun signInWithGoogle(activity: Activity) = runAccount {
        val u = Cloud.signInWithGoogle(activity)
        afterSignIn(u.email ?: "", u.displayName)
    }

    fun signInWithEmail(email: String, password: String, create: Boolean) = runAccount {
        val u = Cloud.signInWithEmail(email, password, create)
        afterSignIn(u.email ?: email, null)
    }

    fun resetPassword(email: String) = runAccount {
        Cloud.resetPassword(email)
        _account.value = _account.value.copy(message = "Şifre sıfırlama bağlantısı e-postana gönderildi 📧")
    }

    fun signOut(activity: Activity?) = runAccount {
        Cloud.signOut(activity)
        _account.value = AccountState(message = "Çıkış yapıldı. Verilerin bu telefonda kalmaya devam ediyor.")
    }

    fun syncNow() = runAccount {
        sync()
        _account.value = _account.value.copy(message = "Bulutla eşitlendi ☁️✅")
    }

    fun clearAccountMessage() {
        _account.value = _account.value.copy(message = null)
    }

    private fun runAccount(block: suspend () -> Unit) {
        if (_account.value.busy) return
        _account.value = _account.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                block()
            } catch (e: SignInCancelled) {
                // kullanıcı vazgeçti
            } catch (e: Exception) {
                _account.value = _account.value.copy(message = Cloud.message(e))
            } finally {
                _account.value = _account.value.copy(busy = false)
            }
        }
    }

    private suspend fun afterSignIn(email: String, displayName: String?) {
        val uid = Cloud.user?.uid ?: return
        prefs.update { p ->
            val name = if (p.name == "Öğrenci" && !displayName.isNullOrBlank()) displayName.split(" ").first() else p.name
            p.copy(userId = uid, name = name)
        }
        _account.value = AccountState(signedIn = true, email = email)
        sync()
        refreshFriends()
        _account.value = _account.value.copy(message = "Giriş yapıldı! İlerlemen artık bulutta yedekleniyor ☁️")
    }

    private suspend fun syncQuiet() {
        if (Cloud.user == null) return
        runCatching { sync() }
    }

    /** Buluttaki ve telefondaki veriyi birleştirip buluta yazar. */
    private suspend fun sync() {
        val uid = Cloud.user?.uid ?: return
        val cloud = Cloud.download(uid)
        val local = dao.allOnce()

        @Suppress("UNCHECKED_CAST")
        val backup = cloud?.get("backup") as? Map<String, Any?>
        if (backup != null) {
            val have = local.map { it.startAt / 1000 }.toSet()
            @Suppress("UNCHECKED_CAST")
            val remote = (backup["sessions"] as? List<Map<String, Any?>>).orEmpty()
            val missing = remote.mapNotNull { m ->
                val s = (Cloud.num(m["s"]) * 1000).toLong()
                if (s / 1000 in have) null else Session(
                    startAt = s,
                    endAt = (Cloud.num(m["e"]) * 1000).toLong(),
                    minutes = Cloud.num(m["m"]).toInt(),
                    subject = m["sub"] as? String ?: "",
                    plant = Cloud.plantName(m["p"] as? String ?: "papatya"),
                    success = m["ok"] as? Boolean ?: true,
                )
            }
            if (missing.isNotEmpty()) dao.insertAll(missing)
            @Suppress("UNCHECKED_CAST")
            val owned = (backup["owned"] as? List<String>).orEmpty().map { Cloud.plantName(it) }
            @Suppress("UNCHECKED_CAST")
            val badges = (backup["badges"] as? List<String>).orEmpty()
            prefs.update { p ->
                p.copy(
                    coins = maxOf(p.coins, Cloud.num(backup["coins"]).toInt()),
                    xp = maxOf(p.xp, Cloud.num(backup["xp"]).toInt()),
                    owned = p.owned + owned.filter { n -> PlantType.entries.any { it.name == n } },
                    badges = p.badges + badges,
                    name = if (p.name == "Öğrenci") (cloud?.get("name") as? String ?: p.name) else p.name,
                )
            }
        }

        val all = dao.allOnce().sortedByDescending { it.endAt }
        val st = computeStats(all)
        val ok = all.filter { it.success }
        val p = profile.value
        val data = mapOf(
            "name" to p.name,
            "level" to p.level,
            "weekMin" to st.weekTotal,
            "totalMin" to st.totalMin,
            "streak" to st.streak,
            "grown" to ok.size,
            "garden" to ok.take(12).map { PlantType.of(it.plant).final },
            "updatedAt" to System.currentTimeMillis() / 1000.0,
            "platform" to "android",
            "backup" to mapOf(
                "coins" to p.coins,
                "xp" to p.xp,
                "owned" to p.owned.map { Cloud.plantKey(it) },
                "badges" to p.badges.toList(),
                "sessions" to all.take(2000).map {
                    mapOf(
                        "s" to it.startAt / 1000.0, "e" to it.endAt / 1000.0, "m" to it.minutes,
                        "sub" to it.subject, "p" to Cloud.plantKey(it.plant), "ok" to it.success,
                    )
                },
            ),
        )
        Cloud.upload(uid, data)
        _account.value = _account.value.copy(lastSync = System.currentTimeMillis())
    }

    /** Hesabı olan arkadaşların bilgilerini buluttan tazeler (canlı sıralama). */
    fun refreshFriends() {
        if (Cloud.user == null) return
        val ids = friends.value.filter { !it.isDemo }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val remote = runCatching { Cloud.fetchUsers(ids) }.getOrNull() ?: return@launch
            prefs.updateFriends { list ->
                list.map { f ->
                    val d = remote[f.id] ?: return@map f
                    @Suppress("UNCHECKED_CAST")
                    f.copy(
                        name = d["name"] as? String ?: f.name,
                        level = Cloud.num(d["level"]).toInt().coerceAtLeast(1),
                        weekMin = Cloud.num(d["weekMin"]).toInt(),
                        totalMin = Cloud.num(d["totalMin"]).toInt(),
                        streak = Cloud.num(d["streak"]).toInt(),
                        grown = Cloud.num(d["grown"]).toInt(),
                        garden = (d["garden"] as? List<String>) ?: f.garden,
                        updatedAt = (Cloud.num(d["updatedAt"]) * 1000).toLong(),
                    )
                }
            }
        }
    }

    // ---------------------------------------------- Görevler, çark, parlak bitki

    fun isQuestClaimed(id: String): Boolean =
        profile.value.questDay == todayKey() && id in profile.value.questClaimed

    fun claimQuest(q: com.tiyzi.odakbahcesi.data.Quest) {
        if (!q.done || isQuestClaimed(q.id)) return
        val today = todayKey()
        prefs.update { p ->
            val claimed = if (p.questDay == today) p.questClaimed else emptySet()
            p.copy(questDay = today, questClaimed = claimed + q.id, coins = p.coins + q.reward)
        }
    }

    fun canSpinWheel(): Boolean = profile.value.wheelDay != todayKey() && todayMin() > 0

    /** Çarkı çevirir; kazanılan dilimin sırası */
    fun spinWheel(): Int? {
        if (!canSpinWheel()) return null
        val idx = com.tiyzi.odakbahcesi.data.Wheel.pick()
        prefs.update { it.copy(wheelDay = todayKey(), coins = it.coins + com.tiyzi.odakbahcesi.data.Wheel.PRIZES[idx]) }
        return idx
    }

    fun isShiny(s: Session): Boolean = (s.startAt / 1000).toString() in profile.value.shiny

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
