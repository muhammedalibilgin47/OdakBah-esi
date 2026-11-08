import ActivityKit
import SwiftUI
import UIKit
import UserNotifications

extension Notification.Name {
    static let odakDeviceLocked = Notification.Name("odak.deviceLocked")
}

@MainActor
final class AppStore: ObservableObject {
    static let graceSeconds: TimeInterval = 10
    static let adReward = 15
    static let freeFriendLimit = 3
    static let sessionsPerInterstitial = 2

    @Published var profile: Profile {
        didSet {
            persist()
            if oldValue.reminderOn != profile.reminderOn || oldValue.reminderHour != profile.reminderHour {
                rescheduleReminders()
            }
            if oldValue.sound != profile.sound, mode == .focus {
                NoisePlayer.shared.play(SoundKind(rawValue: profile.sound) ?? .off)
            }
        }
    }
    @Published private(set) var sessions: [Session] { didSet { persist() } }
    @Published private(set) var friends: [Friend] { didSet { persist() } }
    @Published private(set) var timer: TimerData? { didSet { persist() } }
    @Published private(set) var now = Date()
    @Published private(set) var outcome: Outcome? = nil
    @Published var showInterstitial = false
    @Published var dailyReward: DailyRewardInfo? = nil
    @Published private(set) var badgeToast: Achievement? = nil
    private var badgeQueue: [Achievement] = []

    // Hesap
    @Published private(set) var accountEmail: String? = nil
    @Published private(set) var accountBusy = false
    @Published var accountMessage: String? = nil
    @Published private(set) var lastSync: Date? = nil
    private var lastAutoSync = Date.distantPast
    @Published var quote: String

    private var pendingInterstitial = false
    /// Son tam ekran reklam zamanı (dakikada en fazla bir reklam)
    private var lastAdAt = Date()
    static let adInterval: TimeInterval = 60
    private var ticker: Timer?
    private var leftAt: Date?
    private var lockedWhileAway = false
    private var lastLockAt: Date?
    private var observers: [NSObjectProtocol] = []

    init() {
        let d = UserDefaults.standard
        let dec = JSONDecoder()
        profile = d.data(forKey: "profile").flatMap { try? dec.decode(Profile.self, from: $0) } ?? Profile()
        sessions = d.data(forKey: "sessions").flatMap { try? dec.decode([Session].self, from: $0) } ?? []
        friends = d.data(forKey: "friends").flatMap { try? dec.decode([Friend].self, from: $0) } ?? []
        timer = d.data(forKey: "timer").flatMap { try? dec.decode(TimerData.self, from: $0) }
        quote = Messages.random(Messages.idle)
        leftAt = d.object(forKey: "leftAt") as? Date
        lockedWhileAway = d.bool(forKey: "lockedAway")
        if timer == nil { LiveActivityManager.endAll() }
        if let u = Cloud.user { accountEmail = u.email ?? u.displayName ?? "" }
        startTicker()
        registerObservers()
    }

    // MARK: Kayıt

    private func persist() {
        let d = UserDefaults.standard
        let enc = JSONEncoder()
        d.set(try? enc.encode(profile), forKey: "profile")
        d.set(try? enc.encode(sessions), forKey: "sessions")
        d.set(try? enc.encode(friends), forKey: "friends")
        if let t = timer { d.set(try? enc.encode(t), forKey: "timer") } else { d.removeObject(forKey: "timer") }
    }

    private func saveAwayState() {
        let d = UserDefaults.standard
        if let l = leftAt { d.set(l, forKey: "leftAt") } else { d.removeObject(forKey: "leftAt") }
        d.set(lockedWhileAway, forKey: "lockedAway")
    }

    // MARK: Hesaplanan değerler

    var theme: AppTheme { profile.theme }
    var mode: Mode { timer?.mode ?? .idle }
    var showAds: Bool { !profile.premium }

    var remainingSec: Int {
        guard let t = timer else { return 0 }
        return max(0, Int(ceil(t.endAt.timeIntervalSince(now))))
    }

    var progress: Double {
        guard let t = timer, t.totalSec > 0 else { return 0 }
        return min(max(1 - Double(remainingSec) / Double(t.totalSec), 0), 1)
    }

    var displaySeconds: Int { timer == nil ? profile.focusMin * 60 : remainingSec }

    var currentEmoji: String {
        guard let t = timer else { return profile.selectedPlant.stages.first ?? "🌰" }
        return t.mode == .focus ? t.plant.stage(for: progress) : "☕"
    }

    // MARK: Sayaç

    private func startTicker() {
        ticker?.invalidate()
        let t = Timer(timeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        RunLoop.main.add(t, forMode: .common)
        ticker = t
    }

    private func tick() {
        now = Date()
        guard let t = timer else { return }
        if now >= t.endAt && leftAt == nil { finish() }
    }

    func startFocus() {
        begin(.focus, minutes: profile.focusMin, subject: profile.subject, plant: profile.selectedPlant)
        quote = Messages.random(Messages.start)
    }

    func startBreak() {
        begin(.rest, minutes: profile.breakMin, subject: "", plant: profile.selectedPlant)
        quote = "Gözlerini dinlendir, su iç, biraz esne. ☕"
    }

    private func begin(_ mode: Mode, minutes: Int, subject: String, plant: PlantType) {
        let start = Date()
        let total = minutes * 60
        leftAt = nil
        lockedWhileAway = false
        saveAwayState()
        let t = TimerData(mode: mode, startAt: start, endAt: start.addingTimeInterval(TimeInterval(total)),
                          totalSec: total, subject: subject, plant: plant)
        timer = t
        now = start
        UIApplication.shared.isIdleTimerDisabled = (mode == .focus)
        Notifier.cancelAll()
        Notifier.scheduleCompletion(after: TimeInterval(total), focus: mode == .focus, plant: plant)
        LiveActivityManager.start(t)
        if mode == .focus {
            NoisePlayer.shared.play(SoundKind(rawValue: profile.sound) ?? .off)
        } else {
            NoisePlayer.shared.stop()
        }
    }

    private func clearRunning() {
        timer = nil
        leftAt = nil
        lockedWhileAway = false
        saveAwayState()
        UIApplication.shared.isIdleTimerDisabled = false
        Notifier.cancelAll()
        LiveActivityManager.endAll()
        NoisePlayer.shared.stop()
    }

    private func finish() {
        guard let t = timer else { return }
        clearRunning()
        if t.mode == .focus {
            let minutes = t.totalSec / 60
            let coins = minutes + (minutes >= 45 ? 10 : 0)
            let xp = minutes * 10
            let oldLevel = profile.level
            let isShiny = Double.random(in: 0..<1) < 0.05
            var p = profile
            p.coins += coins + (isShiny ? 25 : 0)
            p.xp += xp
            if isShiny { p.shiny.append(Int(t.startAt.timeIntervalSince1970)) }
            profile = p
            sessions.insert(Session(startAt: t.startAt, endAt: t.endAt, minutes: minutes,
                                    subject: t.subject, plant: t.plant, success: true), at: 0)
            outcome = .grown(plant: t.plant, minutes: minutes, coins: coins + (isShiny ? 25 : 0), xp: xp,
                             newLevel: profile.level > oldLevel ? profile.level : nil,
                             message: isShiny ? "✨ İnanılmaz! Nadir PARLAK bir bitki yetiştirdin! (+25 bonus)" : Messages.random(Messages.success),
                             shiny: isShiny)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            quote = Messages.random(Messages.idle)
            countSessionForAd()
            profile.cycleCount += 1
            checkAchievements()
            rescheduleReminders()
            if Cloud.user != nil {
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 1_500_000_000)
                    try? await self.sync()
                }
            }
            if profile.autoCycle {
                let long = profile.cycleCount % 4 == 0
                begin(.rest, minutes: long ? 15 : profile.breakMin, subject: "", plant: t.plant)
                quote = long ? "4 seans tamam! 15 dakikalık uzun mola hak ettin 🎉" : "Otomatik mola başladı ☕"
            }
        } else {
            quote = Messages.random(Messages.afterBreak)
            if profile.autoCycle && UIApplication.shared.applicationState == .active && outcome == nil {
                startFocus()
            }
        }
    }

    func giveUp() { wither("Seansı erken bitirdin.") }

    func stopBreak() {
        clearRunning()
        quote = Messages.random(Messages.afterBreak)
    }

    private func wither(_ reason: String) {
        guard let t = timer, t.mode == .focus else { return }
        clearRunning()
        let end = min(Date(), t.endAt)
        let minutes = Int(end.timeIntervalSince(t.startAt) / 60)
        sessions.insert(Session(startAt: t.startAt, endAt: end, minutes: minutes,
                                subject: t.subject, plant: t.plant, success: false), at: 0)
        outcome = .withered(reason: reason, message: Messages.random(Messages.fail))
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }

    // MARK: Sonuç penceresi ve reklam

    private func countSessionForAd() {
        guard showAds else { return }
        profile.sessionsSinceAd += 1
        if profile.sessionsSinceAd >= Self.sessionsPerInterstitial {
            profile.sessionsSinceAd = 0
            pendingInterstitial = true
        }
    }

    func dismissOutcome() {
        outcome = nil
        pendingInterstitial = false
        adAction()
    }

    /// Kullanıcı bir işlem yaptığında çağrılır: son reklamdan 1 dk geçtiyse tam ekran reklam.
    func adAction() {
        guard showAds, mode != .focus, !showInterstitial, outcome == nil, dailyReward == nil else { return }
        guard Date().timeIntervalSince(lastAdAt) >= Self.adInterval else { return }
        lastAdAt = Date()
        showInterstitial = true
    }

    func closeInterstitial() {
        showInterstitial = false
        lastAdAt = Date()
    }

    // MARK: Dikkat dağıtıcı koruması

    func scenePhaseChanged(_ phase: ScenePhase) {
        switch phase {
        case .background: appDidLeave()
        case .active: handleForeground()
        default: break
        }
    }

    private func appDidLeave() {
        guard mode == .focus, profile.strictMode, leftAt == nil else { return }
        let at = Date()
        leftAt = at
        if let l = lastLockAt, at.timeIntervalSince(l) < 3 {
            lockedWhileAway = true
        } else {
            lockedWhileAway = false
            Notifier.cancelCompletion()
            Notifier.scheduleWarning()
        }
        saveAwayState()
    }

    private func deviceDidLock() {
        lastLockAt = Date()
        guard leftAt != nil, let t = timer, t.mode == .focus else { return }
        lockedWhileAway = true
        saveAwayState()
        Notifier.cancelWarning()
        rescheduleCompletion(t)
    }

    /// Uygulama öne geldiğinde (açılışta da çağrılır)
    func handleForeground() {
        Notifier.cancelWarning()
        now = Date()
        defer {
            checkPendingStart()
            checkDailyReward()
            checkAchievements()
            rescheduleReminders()
            if Cloud.user != nil && Date().timeIntervalSince(lastAutoSync) > 600 {
                lastAutoSync = Date()
                Task { @MainActor in
                    try? await self.sync()
                    await self.refreshFriendsAsync()
                }
            }
        }
        guard let left = leftAt else { return }
        let wasLocked = lockedWhileAway
        leftAt = nil
        lockedWhileAway = false
        saveAwayState()
        guard let t = timer, t.mode == .focus else { return }
        if wasLocked { return }

        let away = Date().timeIntervalSince(left)
        let endedWithinGrace = t.endAt <= left.addingTimeInterval(Self.graceSeconds)
        if away > Self.graceSeconds && !endedWithinGrace {
            wither("Odak sırasında \(Int(away)) saniye başka uygulamadaydın.")
        } else {
            rescheduleCompletion(t)
        }
    }

    /// Denetim Merkezi düğmesinden gelen başlatma isteği
    private func checkPendingStart() {
        let d = UserDefaults.standard
        guard d.bool(forKey: "pendingStartFocus") else { return }
        d.set(false, forKey: "pendingStartFocus")
        if mode == .idle {
            outcome = nil
            startFocus()
        }
    }

    private func rescheduleCompletion(_ t: TimerData) {
        let remaining = t.endAt.timeIntervalSinceNow
        Notifier.cancelCompletion()
        if remaining > 1 {
            Notifier.scheduleCompletion(after: remaining, focus: t.mode == .focus, plant: t.plant)
        }
    }

    private func registerObservers() {
        let callback: CFNotificationCallback = { _, _, _, _, _ in
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: .odakDeviceLocked, object: nil)
            }
        }
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(), nil, callback,
            "com.apple.springboard.lockcomplete" as CFString, nil, .deliverImmediately
        )
        let nc = NotificationCenter.default
        observers.append(nc.addObserver(forName: .odakDeviceLocked, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.deviceDidLock() }
        })
        observers.append(nc.addObserver(forName: UIApplication.protectedDataWillBecomeUnavailableNotification,
                                        object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.deviceDidLock() }
        })
        observers.append(nc.addObserver(forName: .odakStartFromControl, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.checkPendingStart() }
        })
    }

    // MARK: Mağaza

    func buyOrSelect(_ plant: PlantType) -> Bool {
        if profile.owned.contains(plant) {
            profile.selectedPlant = plant
            adAction()
            return true
        }
        guard profile.coins >= plant.price else { return false }
        profile.coins -= plant.price
        profile.owned.append(plant)
        profile.selectedPlant = plant
        checkAchievements()
        adAction()
        return true
    }

    func selectTheme(_ th: AppTheme) -> Bool {
        if th.premium && !profile.premium { return false }
        profile.theme = th
        adAction()
        return true
    }

    func setPremium(_ on: Bool) {
        profile.premium = on
        if !on && profile.theme.premium { profile.theme = .orman }
    }

    func rewardAd() { profile.coins += Self.adReward }

    // MARK: Arkadaşlar

    var myWeekMin: Int { computeStats(sessions).weekTotal }

    var myShareCode: String {
        let st = computeStats(sessions)
        let garden = sessions.filter { $0.success }.prefix(12).map { $0.plant.grown }
        let payload = SharePayload(u: profile.userId, n: profile.name, l: profile.level, w: st.weekTotal,
                                   t: st.totalMin, s: st.streak, g: sessions.filter { $0.success }.count,
                                   p: Array(garden), d: Date().timeIntervalSince1970)
        return ShareCode.make(payload)
    }

    var shareMessage: String {
        "🌱 Odak Bahçesi'nde beni arkadaş olarak ekle! Bu hafta \(myWeekMin) dakika çalıştım. Kodum:\n\(myShareCode)"
    }

    var canAddFriend: Bool { profile.premium || friends.filter { !$0.isDemo }.count < Self.freeFriendLimit }

    /// Koddan arkadaş ekler veya günceller. Kullanıcıya gösterilecek mesajı döndürür.
    func addFriend(code: String) -> String {
        guard let p = ShareCode.parse(code) else { return "Kod okunamadı. Tamamını kopyaladığından emin ol." }
        if p.u == profile.userId { return "Bu senin kendi kodun 🙂" }
        let f = Friend(id: p.u, name: p.n, level: p.l, weekMin: p.w, totalMin: p.t, streak: p.s, grown: p.g,
                       garden: p.p, updatedAt: Date(timeIntervalSince1970: p.d), isDemo: false)
        if let i = friends.firstIndex(where: { $0.id == p.u }) {
            friends[i] = f
            return "\(p.n) güncellendi ✅"
        }
        guard canAddFriend else {
            return "Ücretsiz sürümde en fazla \(Self.freeFriendLimit) arkadaş eklenebilir. Premium ile sınırsız 👑"
        }
        friends.append(f)
        checkAchievements()
        adAction()
        return "\(p.n) arkadaşlarına eklendi 🎉"
    }

    func addDemoFriends() {
        let existing = Set(friends.map { $0.id })
        friends.append(contentsOf: DemoFriends.make().filter { !existing.contains($0.id) })
    }

    func removeFriend(_ id: String) {
        friends.removeAll { $0.id == id }
    }

    func createGroup(name: String, goal: Int) {
        profile.groupName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.groupGoal = goal
    }

    func leaveGroup() { profile.groupName = "" }

    // MARK: Günlük hedef, rozetler, günlük ödül, hatırlatıcı

    var todayMin: Int { computeStats(sessions).todayMin }

    var examDaysLeft: Int? {
        guard profile.examDate > 0 else { return nil }
        let cal = Calendar.current
        let exam = cal.startOfDay(for: Date(timeIntervalSince1970: profile.examDate))
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: Date()), to: exam).day ?? 0
        return max(days, 0)
    }

    func checkAchievements() {
        let earned = Achievements.earned(sessions: sessions, profile: profile, friends: friends)
        let fresh = Achievements.all.filter { earned.contains($0.id) && !profile.badges.contains($0.id) }
        guard !fresh.isEmpty else { return }
        profile.badges.append(contentsOf: fresh.map { $0.id })
        profile.coins += fresh.reduce(0) { $0 + $1.reward }
        badgeQueue.append(contentsOf: fresh)
        showNextBadge()
    }

    private func showNextBadge() {
        guard badgeToast == nil, !badgeQueue.isEmpty else { return }
        badgeToast = badgeQueue.removeFirst()
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3_200_000_000)
            self.badgeToast = nil
            try? await Task.sleep(nanoseconds: 350_000_000)
            self.showNextBadge()
        }
    }

    private func checkDailyReward() {
        let today = dayKey(Date())
        guard profile.lastRewardDay != today else { return }
        let yesterday = dayKey(Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date())
        let streak = profile.lastRewardDay == yesterday ? profile.rewardStreak + 1 : 1
        let coins = 5 + min(streak, 7) * 5
        var p = profile
        p.lastRewardDay = today
        p.rewardStreak = streak
        p.coins += coins
        profile = p
        dailyReward = DailyRewardInfo(coins: coins, streak: streak)
    }

    // MARK: Hesap + bulut

    func signInWithGoogle() {
        runAccount {
            let u = try await Cloud.signInWithGoogle()
            try await self.afterSignIn(email: u.email ?? "", displayName: u.displayName)
        }
    }

    func signInWithEmail(_ email: String, password: String, create: Bool) {
        runAccount {
            let u = try await Cloud.signInWithEmail(email, password: password, create: create)
            try await self.afterSignIn(email: u.email ?? email, displayName: nil)
        }
    }

    func resetPassword(_ email: String) {
        runAccount {
            try await Cloud.resetPassword(email)
            self.accountMessage = "Şifre sıfırlama bağlantısı e-postana gönderildi 📧"
        }
    }

    func signOut() {
        Cloud.signOut()
        accountEmail = nil
        lastSync = nil
        accountMessage = "Çıkış yapıldı. Verilerin bu telefonda kalmaya devam ediyor."
    }

    func syncNow() {
        runAccount {
            try await self.sync()
            await self.refreshFriendsAsync()
            self.accountMessage = "Bulutla eşitlendi ☁️✅"
        }
    }

    private func runAccount(_ block: @escaping () async throws -> Void) {
        guard !accountBusy else { return }
        accountBusy = true
        accountMessage = nil
        Task { @MainActor in
            do {
                try await block()
            } catch {
                if !Cloud.isCancel(error) { self.accountMessage = Cloud.message(error) }
            }
            self.accountBusy = false
        }
    }

    private func afterSignIn(email: String, displayName: String?) async throws {
        guard let uid = Cloud.user?.uid else { return }
        var p = profile
        p.userId = uid
        if p.name == "Öğrenci", let dn = displayName, let first = dn.split(separator: " ").first {
            p.name = String(first)
        }
        profile = p
        accountEmail = email
        try await sync()
        await refreshFriendsAsync()
        accountMessage = "Giriş yapıldı! İlerlemen artık bulutta yedekleniyor ☁️"
    }

    /// Buluttaki ve telefondaki veriyi birleştirip buluta yazar.
    private func sync() async throws {
        guard let uid = Cloud.user?.uid else { return }
        let cloud = try await Cloud.download(uid: uid)
        if let backup = cloud?["backup"] as? [String: Any] {
            let have = Set(sessions.map { Int($0.startAt.timeIntervalSince1970) })
            let remote = backup["sessions"] as? [[String: Any]] ?? []
            var added: [Session] = []
            for m in remote {
                let start = Cloud.num(m["s"])
                if have.contains(Int(start)) { continue }
                added.append(Session(
                    startAt: Date(timeIntervalSince1970: start),
                    endAt: Date(timeIntervalSince1970: Cloud.num(m["e"])),
                    minutes: Int(Cloud.num(m["m"])),
                    subject: m["sub"] as? String ?? "",
                    plant: PlantType(rawValue: m["p"] as? String ?? "papatya") ?? .papatya,
                    success: m["ok"] as? Bool ?? true
                ))
            }
            if !added.isEmpty {
                sessions = (sessions + added).sorted { $0.endAt > $1.endAt }
            }
            var p = profile
            p.coins = max(p.coins, Int(Cloud.num(backup["coins"])))
            p.xp = max(p.xp, Int(Cloud.num(backup["xp"])))
            for key in backup["owned"] as? [String] ?? [] {
                if let plant = PlantType(rawValue: key), !p.owned.contains(plant) { p.owned.append(plant) }
            }
            for b in backup["badges"] as? [String] ?? [] where !p.badges.contains(b) {
                p.badges.append(b)
            }
            if p.name == "Öğrenci", let n = cloud?["name"] as? String { p.name = n }
            profile = p
        }

        let st = computeStats(sessions)
        let ok = sessions.filter { $0.success }
        let sessionData: [[String: Any]] = sessions.prefix(2000).map {
            ["s": $0.startAt.timeIntervalSince1970, "e": $0.endAt.timeIntervalSince1970, "m": $0.minutes,
             "sub": $0.subject, "p": $0.plant.rawValue, "ok": $0.success]
        }
        let backupData: [String: Any] = [
            "coins": profile.coins,
            "xp": profile.xp,
            "owned": profile.owned.map { $0.rawValue },
            "badges": profile.badges,
            "sessions": sessionData,
        ]
        let data: [String: Any] = [
            "name": profile.name,
            "level": profile.level,
            "weekMin": st.weekTotal,
            "totalMin": st.totalMin,
            "streak": st.streak,
            "grown": ok.count,
            "garden": ok.prefix(12).map { $0.plant.grown },
            "updatedAt": Date().timeIntervalSince1970,
            "platform": "ios",
            "backup": backupData,
        ]
        try await Cloud.upload(uid: uid, data: data)
        lastSync = Date()
    }

    func refreshFriends() {
        Task { @MainActor in await self.refreshFriendsAsync() }
    }

    /// Hesabı olan arkadaşların bilgilerini buluttan tazeler (canlı sıralama).
    private func refreshFriendsAsync() async {
        guard Cloud.user != nil else { return }
        let ids = friends.filter { !$0.isDemo }.map { $0.id }
        guard !ids.isEmpty, let remote = try? await Cloud.fetchUsers(ids) else { return }
        friends = friends.map { f in
            guard let d = remote[f.id] else { return f }
            var n = f
            n.name = d["name"] as? String ?? f.name
            n.level = max(1, Int(Cloud.num(d["level"])))
            n.weekMin = Int(Cloud.num(d["weekMin"]))
            n.totalMin = Int(Cloud.num(d["totalMin"]))
            n.streak = Int(Cloud.num(d["streak"]))
            n.grown = Int(Cloud.num(d["grown"]))
            n.garden = d["garden"] as? [String] ?? f.garden
            n.updatedAt = Date(timeIntervalSince1970: Cloud.num(d["updatedAt"]))
            return n
        }
    }

    // MARK: Görevler, çark, parlak bitki

    func isQuestClaimed(_ id: String) -> Bool {
        profile.questDay == dayKey(Date()) && profile.questClaimed.contains(id)
    }

    func claimQuest(_ q: Quest) {
        guard q.done, !isQuestClaimed(q.id) else { return }
        var p = profile
        let today = dayKey(Date())
        if p.questDay != today {
            p.questDay = today
            p.questClaimed = []
        }
        p.questClaimed.append(q.id)
        p.coins += q.reward
        profile = p
        adAction()
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    var canSpinWheel: Bool {
        profile.wheelDay != dayKey(Date()) && todayMin > 0
    }

    /// Çarkı çevirir; kazanılan dilimin sırasını döndürür.
    func spinWheel() -> Int? {
        guard canSpinWheel else { return nil }
        let idx = Wheel.pick()
        var p = profile
        p.wheelDay = dayKey(Date())
        p.coins += Wheel.prizes[idx]
        profile = p
        return idx
    }

    func isShiny(_ s: Session) -> Bool {
        profile.shiny.contains(Int(s.startAt.timeIntervalSince1970))
    }

    func rescheduleReminders() {
        Reminders.reschedule(on: profile.reminderOn, hour: profile.reminderHour, studiedToday: todayMin > 0)
    }
}

// MARK: - Canlı Etkinlik (kilit ekranı + Dynamic Island)

enum LiveActivityManager {
    static func start(_ t: TimerData) {
        let old = Activity<FocusActivityAttributes>.activities
        Task {
            for a in old { await a.end(nil, dismissalPolicy: .immediate) }
        }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let attrs = FocusActivityAttributes(plantEmoji: t.plant.grown, plantName: t.plant.label,
                                            subject: t.subject.isEmpty ? "Mola" : t.subject)
        let state = FocusActivityAttributes.ContentState(startAt: t.startAt, endAt: t.endAt, isBreak: t.mode == .rest)
        let content = ActivityContent(state: state, staleDate: t.endAt.addingTimeInterval(60))
        _ = try? Activity.request(attributes: attrs, content: content, pushType: nil)
    }

    static func endAll() {
        let all = Activity<FocusActivityAttributes>.activities
        guard !all.isEmpty else { return }
        Task {
            for a in all { await a.end(nil, dismissalPolicy: .immediate) }
        }
    }
}

// MARK: - Bildirimler

enum Notifier {
    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    static func scheduleCompletion(after seconds: TimeInterval, focus: Bool, plant: PlantType) {
        let c = UNMutableNotificationContent()
        c.title = focus ? "Seans tamamlandı! \(plant.grown)" : "Mola bitti ⏰"
        c.body = focus ? Messages.random(Messages.success) : Messages.random(Messages.afterBreak)
        c.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(seconds, 1), repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "done", content: c, trigger: trigger))
    }

    static func scheduleWarning() {
        let c = UNMutableNotificationContent()
        c.title = "🌱 Bitkin solmak üzere!"
        c.body = Messages.random(Messages.warning)
        c.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1.5, repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "warn", content: c, trigger: trigger))
    }

    static func cancelWarning() {
        let c = UNUserNotificationCenter.current()
        c.removePendingNotificationRequests(withIdentifiers: ["warn"])
        c.removeDeliveredNotifications(withIdentifiers: ["warn"])
    }

    static func cancelCompletion() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["done"])
    }

    static func cancelAll() {
        let c = UNUserNotificationCenter.current()
        c.removePendingNotificationRequests(withIdentifiers: ["done", "warn"])
        c.removeDeliveredNotifications(withIdentifiers: ["warn"])
    }
}
