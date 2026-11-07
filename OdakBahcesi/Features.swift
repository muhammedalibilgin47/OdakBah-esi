import AVFoundation
import SwiftUI
import UserNotifications

// MARK: - Rozetler

struct Achievement: Identifiable {
    let id: String
    let emoji: String
    let title: String
    let detail: String
    let reward: Int
}

enum Achievements {
    static let all: [Achievement] = [
        Achievement(id: "ilk", emoji: "🌱", title: "İlk Filiz", detail: "İlk odak seansını tamamla", reward: 20),
        Achievement(id: "bes", emoji: "🖐️", title: "Isınma Turu", detail: "5 seans tamamla", reward: 30),
        Achievement(id: "yirmibes", emoji: "🌿", title: "Bahçıvan Çırağı", detail: "25 seans tamamla", reward: 60),
        Achievement(id: "yuz", emoji: "🌳", title: "Orman Kurucu", detail: "100 seans tamamla", reward: 200),
        Achievement(id: "seri3", emoji: "🔥", title: "Üç Gün Üst Üste", detail: "3 günlük seri yap", reward: 30),
        Achievement(id: "seri7", emoji: "☄️", title: "Haftalık Seri", detail: "7 günlük seri yap", reward: 80),
        Achievement(id: "seri30", emoji: "🏆", title: "Ay Boyu Disiplin", detail: "30 günlük seri yap", reward: 300),
        Achievement(id: "saat10", emoji: "⏳", title: "10 Saat", detail: "Toplam 10 saat çalış", reward: 60),
        Achievement(id: "saat50", emoji: "⌛", title: "50 Saat", detail: "Toplam 50 saat çalış", reward: 200),
        Achievement(id: "maraton", emoji: "🏃", title: "Maraton", detail: "90 dakikalık bir seans tamamla", reward: 50),
        Achievement(id: "gece", emoji: "🦉", title: "Gece Kuşu", detail: "23:00 sonrası bir seans bitir", reward: 25),
        Achievement(id: "sabah", emoji: "🐦", title: "Erkenci Kuş", detail: "08:00 öncesi bir seans bitir", reward: 25),
        Achievement(id: "hedef", emoji: "🎯", title: "Hedef Avcısı", detail: "Günlük hedefine ulaş", reward: 40),
        Achievement(id: "koleksiyon", emoji: "🎒", title: "Koleksiyoncu", detail: "5 farklı bitkiye sahip ol", reward: 50),
        Achievement(id: "botanik", emoji: "👑", title: "Botanik Uzmanı", detail: "Tüm bitkileri topla", reward: 300),
        Achievement(id: "sosyal", emoji: "🤝", title: "Sosyal Kelebek", detail: "Bir arkadaş ekle", reward: 30),
    ]

    static func earned(sessions: [Session], profile: Profile, friends: [Friend]) -> Set<String> {
        let ok = sessions.filter { $0.success }
        let st = computeStats(sessions)
        let cal = Calendar.current
        var e = Set<String>()
        if ok.count >= 1 { e.insert("ilk") }
        if ok.count >= 5 { e.insert("bes") }
        if ok.count >= 25 { e.insert("yirmibes") }
        if ok.count >= 100 { e.insert("yuz") }
        if st.streak >= 3 { e.insert("seri3") }
        if st.streak >= 7 { e.insert("seri7") }
        if st.streak >= 30 { e.insert("seri30") }
        if st.totalMin >= 600 { e.insert("saat10") }
        if st.totalMin >= 3000 { e.insert("saat50") }
        if ok.contains(where: { $0.minutes >= 90 }) { e.insert("maraton") }
        if ok.contains(where: { let h = cal.component(.hour, from: $0.endAt); return h >= 23 || h < 4 }) { e.insert("gece") }
        if ok.contains(where: { let h = cal.component(.hour, from: $0.endAt); return h >= 4 && h < 8 }) { e.insert("sabah") }
        var byDay: [Date: Int] = [:]
        for s in ok { byDay[cal.startOfDay(for: s.endAt), default: 0] += s.minutes }
        if byDay.values.contains(where: { $0 >= profile.dailyGoal }) { e.insert("hedef") }
        if profile.owned.count >= 5 { e.insert("koleksiyon") }
        if profile.owned.count >= PlantType.allCases.count { e.insert("botanik") }
        if friends.contains(where: { !$0.isDemo }) { e.insert("sosyal") }
        return e
    }
}

// MARK: - Günlük ödül

struct DailyRewardInfo: Identifiable {
    let id = UUID()
    let coins: Int
    let streak: Int
}

func dayKey(_ date: Date) -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd"
    return f.string(from: date)
}

// MARK: - Akıllı hatırlatıcı

enum Reminders {
    /// Önümüzdeki 7 gün için hatırlatma kurar; bugün çalışıldıysa bugünü atlar.
    static func reschedule(on: Bool, hour: Int, studiedToday: Bool) {
        let center = UNUserNotificationCenter.current()
        let ids = (0..<7).map { "remind-\($0)" }
        center.removePendingNotificationRequests(withIdentifiers: ids)
        guard on else { return }
        let cal = Calendar.current
        let now = Date()
        for i in 0..<7 {
            if i == 0 && studiedToday { continue }
            guard let day = cal.date(byAdding: .day, value: i, to: now) else { continue }
            var comps = cal.dateComponents([.year, .month, .day], from: day)
            comps.hour = hour
            comps.minute = 0
            guard let fire = cal.date(from: comps), fire > now else { continue }
            let content = UNMutableNotificationContent()
            content.title = "🌱 Bahçen seni bekliyor"
            content.body = Messages.random(Messages.reminder)
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            center.add(UNNotificationRequest(identifier: ids[i], content: content, trigger: trigger))
        }
    }
}

// MARK: - Odak sesleri (cihazda üretilen gürültü, dosya gerekmez)

enum SoundKind: String, CaseIterable, Identifiable {
    case off, rain, waves, white, deep

    var id: String { rawValue }

    var label: String {
        switch self {
        case .off: return "🔇 Sessiz"
        case .rain: return "🌧️ Yağmur"
        case .waves: return "🌊 Dalga"
        case .white: return "🤍 Beyaz gürültü"
        case .deep: return "🟤 Derin"
        }
    }
}

private final class NoiseState {
    var b0: Float = 0, b1: Float = 0, b2: Float = 0, b3: Float = 0, b4: Float = 0, b5: Float = 0, b6: Float = 0
    var brown: Float = 0
    var t: Double = 0
}

final class NoisePlayer {
    static let shared = NoisePlayer()
    private var engine: AVAudioEngine?
    private(set) var current: SoundKind = .off

    func play(_ kind: SoundKind) {
        stop()
        guard kind != .off else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? session.setActive(true)

        let engine = AVAudioEngine()
        let hwRate = engine.outputNode.outputFormat(forBus: 0).sampleRate
        let rate = hwRate > 0 ? hwRate : 44_100
        guard let format = AVAudioFormat(standardFormatWithSampleRate: rate, channels: 1) else { return }
        let st = NoiseState()
        let step = 1.0 / rate

        let node = AVAudioSourceNode(format: format) { _, _, frameCount, audioBufferList -> OSStatus in
            let buffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
            for frame in 0..<Int(frameCount) {
                let white = Float.random(in: -1...1)
                var v: Float = 0
                switch kind {
                case .white:
                    v = white * 0.22
                case .rain:
                    // Pembe gürültü (Paul Kellet filtresi) — yağmura benzer
                    st.b0 = 0.99886 * st.b0 + white * 0.0555179
                    st.b1 = 0.99332 * st.b1 + white * 0.0750759
                    st.b2 = 0.96900 * st.b2 + white * 0.1538520
                    st.b3 = 0.86650 * st.b3 + white * 0.3104856
                    st.b4 = 0.55000 * st.b4 + white * 0.5329522
                    st.b5 = -0.7616 * st.b5 - white * 0.0168980
                    v = (st.b0 + st.b1 + st.b2 + st.b3 + st.b4 + st.b5 + st.b6 + white * 0.5362) * 0.07
                    st.b6 = white * 0.115926
                case .deep:
                    st.brown = (st.brown + 0.02 * white) / 1.02
                    v = st.brown * 2.8
                case .waves:
                    st.brown = (st.brown + 0.02 * white) / 1.02
                    st.t += step
                    let lfo = Float(0.5 + 0.5 * sin(2 * Double.pi * 0.09 * st.t))
                    v = st.brown * 3.2 * (0.25 + 0.75 * lfo)
                case .off:
                    v = 0
                }
                for buffer in buffers {
                    buffer.mData?.assumingMemoryBound(to: Float.self)[frame] = v
                }
            }
            return noErr
        }

        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        engine.mainMixerNode.outputVolume = 0.8
        do {
            try engine.start()
            self.engine = engine
            current = kind
        } catch {
            current = .off
        }
    }

    func stop() {
        engine?.stop()
        engine = nil
        current = .off
    }
}
