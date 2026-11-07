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

    @Published var profile: Profile { didSet { persist() } }
    @Published private(set) var sessions: [Session] { didSet { persist() } }
    @Published private(set) var timer: TimerData? { didSet { persist() } }
    @Published private(set) var now = Date()
    @Published var outcome: Outcome? = nil
    @Published var quote: String

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
        timer = d.data(forKey: "timer").flatMap { try? dec.decode(TimerData.self, from: $0) }
        quote = Messages.random(Messages.idle)
        leftAt = d.object(forKey: "leftAt") as? Date
        lockedWhileAway = d.bool(forKey: "lockedAway")
        startTicker()
        registerLockObservers()
    }

    // MARK: Kayıt

    private func persist() {
        let d = UserDefaults.standard
        let enc = JSONEncoder()
        d.set(try? enc.encode(profile), forKey: "profile")
        d.set(try? enc.encode(sessions), forKey: "sessions")
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
        // Kullanıcı dışarıdayken süre dolarsa karar dönüşte verilir
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
        timer = TimerData(mode: mode, startAt: start, endAt: start.addingTimeInterval(TimeInterval(total)),
                          totalSec: total, subject: subject, plant: plant)
        now = start
        UIApplication.shared.isIdleTimerDisabled = (mode == .focus)
        Notifier.cancelAll()
        Notifier.scheduleCompletion(after: TimeInterval(total), focus: mode == .focus, plant: plant)
    }

    private func clearRunning() {
        timer = nil
        leftAt = nil
        lockedWhileAway = false
        saveAwayState()
        UIApplication.shared.isIdleTimerDisabled = false
        Notifier.cancelAll()
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

    // MARK: Dikkat dağıtıcı koruması

    func scenePhaseChanged(_ phase: ScenePhase) {
        switch phase {
        case .background: appDidLeave()
        case .active: handleForeground()
        default: break
        }
    }

    private func appDidLeave() {
        guard mode == .focus, leftAt == nil else { return }
        let at = Date()
        leftAt = at
        if let l = lastLockAt, at.timeIntervalSince(l) < 3 {
            lockedWhileAway = true          // ekran kilitlendi: ceza yok
        } else {
            lockedWhileAway = false
            Notifier.cancelCompletion()      // başka uygulamada "tebrikler" bildirimi gelmesin
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
        guard let left = leftAt else { return }
        let wasLocked = lockedWhileAway
        leftAt = nil
        lockedWhileAway = false
        saveAwayState()
        guard let t = timer, t.mode == .focus else { return }
        if wasLocked { return }  // süre dolduysa tick() tamamlar

        let away = Date().timeIntervalSince(left)
        let endedWithinGrace = t.endAt <= left.addingTimeInterval(Self.graceSeconds)
        if away > Self.graceSeconds && !endedWithinGrace {
            wither("Odak sırasında \(Int(away)) saniye başka uygulamadaydın.")
        } else {
            rescheduleCompletion(t)
        }
    }

    private func rescheduleCompletion(_ t: TimerData) {
        let remaining = t.endAt.timeIntervalSinceNow
        Notifier.cancelCompletion()
        if remaining > 1 {
            Notifier.scheduleCompletion(after: remaining, focus: t.mode == .focus, plant: t.plant)
        }
    }

    private func registerLockObservers() {
        // Ekran kilitlendiğinde iOS'un yaydığı sistem sinyali
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
    }

    // MARK: Mağaza

    /// true: satın alındı veya seçildi, false: yetersiz altın
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
