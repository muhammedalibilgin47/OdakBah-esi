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

    @Published var profile: Profile { didSet { persist() } }
    @Published private(set) var sessions: [Session] { didSet { persist() } }
    @Published private(set) var friends: [Friend] { didSet { persist() } }
    @Published private(set) var timer: TimerData? { didSet { persist() } }
    @Published private(set) var now = Date()
    @Published private(set) var outcome: Outcome? = nil
    @Published var showInterstitial = false
    @Published var quote: String

    private var pendingInterstitial = false
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
    }

    private func clearRunning() {
        timer = nil
        leftAt = nil
        lockedWhileAway = false
        saveAwayState()
        UIApplication.shared.isIdleTimerDisabled = false
        Notifier.cancelAll()
        LiveActivityManager.endAll()
    }

    private func finish() {
        guard let t = timer else { return }
        clearRunning()
        if t.mode == .focus {
            let minutes = t.totalSec / 60
            let coins = minutes + (minutes >= 45 ? 10 : 0)
            let xp = minutes * 10
            let oldLevel = profile.level
            profile.coins += coins
            profile.xp += xp
            sessions.insert(Session(startAt: t.startAt, endAt: t.endAt, minutes: minutes,
                                    subject: t.subject, plant: t.plant, success: true), at: 0)
            outcome = .grown(plant: t.plant, minutes: minutes, coins: coins, xp: xp,
                             newLevel: profile.level > oldLevel ? profile.level : nil,
                             message: Messages.random(Messages.success))
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            quote = Messages.random(Messages.idle)
            countSessionForAd()
        } else {
            quote = Messages.random(Messages.afterBreak)
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
        if pendingInterstitial {
            pendingInterstitial = false
            showInterstitial = true
        }
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
        defer { checkPendingStart() }
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
            return true
        }
        guard profile.coins >= plant.price else { return false }
        profile.coins -= plant.price
        profile.owned.append(plant)
        profile.selectedPlant = plant
        return true
    }

    func selectTheme(_ th: AppTheme) -> Bool {
        if th.premium && !profile.premium { return false }
        profile.theme = th
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
