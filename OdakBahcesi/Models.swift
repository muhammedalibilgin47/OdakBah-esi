import SwiftUI

// MARK: - Bitkiler

enum PlantType: String, CaseIterable, Codable, Identifiable {
    case papatya, lale, kaktus, aycicegi, gul, mese, cam, kiraz, palmiye, mantar

    var id: String { rawValue }

    var label: String {
        switch self {
        case .papatya: return "Papatya"
        case .lale: return "Lale"
        case .kaktus: return "Kaktüs"
        case .aycicegi: return "Ayçiçeği"
        case .gul: return "Gül"
        case .mese: return "Meşe"
        case .cam: return "Çam"
        case .kiraz: return "Kiraz Çiçeği"
        case .palmiye: return "Palmiye"
        case .mantar: return "Sihirli Mantar"
        }
    }

    var stages: [String] {
        switch self {
        case .papatya: return ["🌰", "🌱", "🌿", "🌼"]
        case .lale: return ["🌰", "🌱", "🌿", "🌷"]
        case .kaktus: return ["🌰", "🌱", "🌵"]
        case .aycicegi: return ["🌰", "🌱", "🌿", "🌻"]
        case .gul: return ["🌰", "🌱", "🌿", "🌹"]
        case .mese: return ["🌰", "🌱", "🌿", "🪴", "🌳"]
        case .cam: return ["🌰", "🌱", "🌿", "🌲"]
        case .kiraz: return ["🌰", "🌱", "🌿", "🌸"]
        case .palmiye: return ["🌰", "🌱", "🌿", "🌴"]
        case .mantar: return ["🌰", "🟤", "🍄"]
        }
    }

    var price: Int {
        switch self {
        case .papatya, .lale: return 0
        case .kaktus: return 40
        case .aycicegi: return 80
        case .gul: return 120
        case .mese: return 160
        case .cam: return 200
        case .kiraz: return 260
        case .palmiye: return 350
        case .mantar: return 500
        }
    }

    /// Tamamen büyümüş hali
    var grown: String { stages.last ?? "🌱" }

    /// 0...1 ilerlemeye göre büyüme aşaması
    func stage(for progress: Double) -> String {
        let i = Int(progress * Double(stages.count))
        return stages[min(max(i, 0), stages.count - 1)]
    }
}

let deadPlant = "🥀"
let subjects = ["Matematik", "Fizik", "Kimya", "Biyoloji", "Türkçe",
                "Tarih", "Coğrafya", "İngilizce", "Kodlama", "Diğer"]
let focusOptions = [10, 15, 25, 40, 50, 60, 90]
let breakOptions = [5, 10, 15, 20]

// MARK: - Temalar

enum AppTheme: String, CaseIterable, Codable, Identifiable {
    case orman, okyanus, gunbatimi, lavanta, gece

    var id: String { rawValue }

    var label: String {
        switch self {
        case .orman: return "Orman"
        case .okyanus: return "Okyanus"
        case .gunbatimi: return "Gün Batımı"
        case .lavanta: return "Lavanta"
        case .gece: return "Gece"
        }
    }

    var emoji: String {
        switch self {
        case .orman: return "🌲"
        case .okyanus: return "🌊"
        case .gunbatimi: return "🌅"
        case .lavanta: return "💜"
        case .gece: return "🌙"
        }
    }

    var premium: Bool { self != .orman }
    var dark: Bool { self == .gece }

    var primary: Color {
        switch self {
        case .orman: return Color(red: 0.18, green: 0.49, blue: 0.20)
        case .okyanus: return Color(red: 0.01, green: 0.47, blue: 0.74)
        case .gunbatimi: return Color(red: 0.90, green: 0.32, blue: 0.00)
        case .lavanta: return Color(red: 0.42, green: 0.11, blue: 0.60)
        case .gece: return Color(red: 0.30, green: 0.71, blue: 0.67)
        }
    }

    var secondary: Color {
        switch self {
        case .orman: return Color(red: 0.51, green: 0.78, blue: 0.52)
        case .okyanus: return Color(red: 0.31, green: 0.76, blue: 0.97)
        case .gunbatimi: return Color(red: 1.00, green: 0.72, blue: 0.30)
        case .lavanta: return Color(red: 0.73, green: 0.41, blue: 0.78)
        case .gece: return Color(red: 0.20, green: 0.40, blue: 0.38)
        }
    }

    var background: Color {
        switch self {
        case .orman: return Color(red: 0.95, green: 0.97, blue: 0.91)
        case .okyanus: return Color(red: 0.88, green: 0.96, blue: 1.00)
        case .gunbatimi: return Color(red: 1.00, green: 0.95, blue: 0.88)
        case .lavanta: return Color(red: 0.95, green: 0.90, blue: 0.96)
        case .gece: return Color(red: 0.06, green: 0.08, blue: 0.09)
        }
    }

    var card: Color { dark ? Color(white: 0.14) : .white }
}

// MARK: - Profil ve kayıtlar

struct Profile: Codable {
    var coins = 0
    var xp = 0
    var owned: [PlantType] = [.papatya, .lale]
    var selectedPlant: PlantType = .papatya
    var theme: AppTheme = .orman
    var premium = false
    var focusMin = 25
    var breakMin = 5
    var subject = "Matematik"
    // v1.1
    var name = "Öğrenci"
    var userId = UUID().uuidString
    var strictMode = true
    var groupName = ""
    var groupGoal = 600
    var sessionsSinceAd = 0
    // v1.3
    var dailyGoal = 120
    var reminderOn = true
    var reminderHour = 20
    var sound = "off"
    var autoCycle = false
    var cycleCount = 0
    var lastRewardDay = ""
    var rewardStreak = 0
    var examName = "YKS"
    var examDate: Double = 0
    var badges: [String] = []
    // v1.5
    var questDay = ""
    var questClaimed: [String] = []
    var wheelDay = ""
    var shiny: [Int] = []

    static let xpPerLevel = 300

    init() {}

    /// Eski sürümden kalan kayıtlar silinmesin diye eksik alanlar varsayılanla doldurulur.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = Profile()
        coins = try c.decodeIfPresent(Int.self, forKey: .coins) ?? d.coins
        xp = try c.decodeIfPresent(Int.self, forKey: .xp) ?? d.xp
        owned = try c.decodeIfPresent([PlantType].self, forKey: .owned) ?? d.owned
        selectedPlant = try c.decodeIfPresent(PlantType.self, forKey: .selectedPlant) ?? d.selectedPlant
        theme = try c.decodeIfPresent(AppTheme.self, forKey: .theme) ?? d.theme
        premium = try c.decodeIfPresent(Bool.self, forKey: .premium) ?? d.premium
        focusMin = try c.decodeIfPresent(Int.self, forKey: .focusMin) ?? d.focusMin
        breakMin = try c.decodeIfPresent(Int.self, forKey: .breakMin) ?? d.breakMin
        subject = try c.decodeIfPresent(String.self, forKey: .subject) ?? d.subject
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? d.name
        userId = try c.decodeIfPresent(String.self, forKey: .userId) ?? d.userId
        strictMode = try c.decodeIfPresent(Bool.self, forKey: .strictMode) ?? d.strictMode
        groupName = try c.decodeIfPresent(String.self, forKey: .groupName) ?? d.groupName
        groupGoal = try c.decodeIfPresent(Int.self, forKey: .groupGoal) ?? d.groupGoal
        sessionsSinceAd = try c.decodeIfPresent(Int.self, forKey: .sessionsSinceAd) ?? d.sessionsSinceAd
        dailyGoal = try c.decodeIfPresent(Int.self, forKey: .dailyGoal) ?? d.dailyGoal
        reminderOn = try c.decodeIfPresent(Bool.self, forKey: .reminderOn) ?? d.reminderOn
        reminderHour = try c.decodeIfPresent(Int.self, forKey: .reminderHour) ?? d.reminderHour
        sound = try c.decodeIfPresent(String.self, forKey: .sound) ?? d.sound
        autoCycle = try c.decodeIfPresent(Bool.self, forKey: .autoCycle) ?? d.autoCycle
        cycleCount = try c.decodeIfPresent(Int.self, forKey: .cycleCount) ?? d.cycleCount
        lastRewardDay = try c.decodeIfPresent(String.self, forKey: .lastRewardDay) ?? d.lastRewardDay
        rewardStreak = try c.decodeIfPresent(Int.self, forKey: .rewardStreak) ?? d.rewardStreak
        examName = try c.decodeIfPresent(String.self, forKey: .examName) ?? d.examName
        examDate = try c.decodeIfPresent(Double.self, forKey: .examDate) ?? d.examDate
        badges = try c.decodeIfPresent([String].self, forKey: .badges) ?? d.badges
        questDay = try c.decodeIfPresent(String.self, forKey: .questDay) ?? d.questDay
        questClaimed = try c.decodeIfPresent([String].self, forKey: .questClaimed) ?? d.questClaimed
        wheelDay = try c.decodeIfPresent(String.self, forKey: .wheelDay) ?? d.wheelDay
        shiny = try c.decodeIfPresent([Int].self, forKey: .shiny) ?? d.shiny
    }

    var level: Int { 1 + xp / Self.xpPerLevel }
    var levelProgress: Double { Double(xp % Self.xpPerLevel) / Double(Self.xpPerLevel) }
    var title: String {
        switch level {
        case 20...: return "Odak Ustası 🏆"
        case 12...: return "Orman Bekçisi 🌲"
        case 7...: return "Bahçıvan 🧑‍🌾"
        case 3...: return "Filiz Yetiştirici 🌿"
        default: return "Tohum 🌰"
        }
    }
}

/// Bir odak seansı. success == false ise bitki solmuştur.
struct Session: Codable, Identifiable {
    var id = UUID()
    var startAt: Date
    var endAt: Date
    var minutes: Int
    var subject: String
    var plant: PlantType
    var success: Bool
}

enum Mode: String, Codable { case idle, focus, rest }

struct TimerData: Codable {
    var mode: Mode
    var startAt: Date
    var endAt: Date
    var totalSec: Int
    var subject: String
    var plant: PlantType
}

enum Outcome {
    case grown(plant: PlantType, minutes: Int, coins: Int, xp: Int, newLevel: Int?, message: String, shiny: Bool)
    case withered(reason: String, message: String)
}

// MARK: - İstatistik

struct Stats {
    var todayMin = 0
    var streak = 0
    var totalMin = 0
    var successRate = 0
    var week: [(String, Int)] = []
    var bySubject: [(String, Int)] = []
    var bestDayMin = 0
    var weekTotal: Int { week.reduce(0) { $0 + $1.1 } }
}

func computeStats(_ sessions: [Session]) -> Stats {
    let cal = Calendar.current
    let today = cal.startOfDay(for: Date())
    let ok = sessions.filter { $0.success }

    var byDay: [Date: Int] = [:]
    for s in ok { byDay[cal.startOfDay(for: s.endAt), default: 0] += s.minutes }

    var streak = 0
    var d = byDay[today] != nil ? today : (cal.date(byAdding: .day, value: -1, to: today) ?? today)
    while byDay[d] != nil {
        streak += 1
        guard let prev = cal.date(byAdding: .day, value: -1, to: d) else { break }
        d = prev
    }

    let names = ["Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"] // weekday: 1 = Pazar
    var week: [(String, Int)] = []
    for back in stride(from: 6, through: 0, by: -1) {
        let day = cal.date(byAdding: .day, value: -back, to: today) ?? today
        week.append((names[cal.component(.weekday, from: day) - 1], byDay[day] ?? 0))
    }

    var subj: [String: Int] = [:]
    for s in ok { subj[s.subject, default: 0] += s.minutes }

    var st = Stats()
    st.todayMin = byDay[today] ?? 0
    st.streak = streak
    st.totalMin = ok.reduce(0) { $0 + $1.minutes }
    st.successRate = sessions.isEmpty ? 0 : ok.count * 100 / sessions.count
    st.week = week
    st.bySubject = subj.map { ($0.key, $0.value) }.sorted { $0.1 > $1.1 }
    st.bestDayMin = byDay.values.max() ?? 0
    return st
}

func formatTime(_ total: Int) -> String {
    String(format: "%02d:%02d", total / 60, total % 60)
}

// MARK: - Motivasyon metinleri

enum Messages {
    static let idle = [
        "Bugün küçük bir adım, sınavda büyük bir fark. 🌱",
        "Bir tohum ek, gerisini odak halleder.",
        "Başlamak işin yarısıdır. Diğer yarısı 25 dakika. ⏱️",
        "Telefon bekleyebilir, hedeflerin bekleyemez.",
        "Her seans bahçene bir bitki, beynine bir bilgi ekler.",
        "Disiplin, motivasyonun bittiği yerde başlar.",
    ]
    static let start = [
        "Harika! Tohum toprağa düştü. Şimdi sadece sen ve kitabın. 📚",
        "Odak modu açık. Bitkin seninle birlikte büyüyor. 🌱",
        "Telefonu bırak, kalemi al. Bitkin sana güveniyor!",
        "Bu seans bitince kendinle gurur duyacaksın. 💪",
    ]
    static let success = [
        "Muhteşem! Bahçen bir bitki daha kazandı. 🌳",
        "Bir seans daha tamam! Beynin de bahçen de büyüyor. 🧠🌿",
        "Odak şampiyonu! Kısa bir mola hak ettin. ☕",
        "İşte bu! Hedefine bir adım daha yaklaştın. 🎯",
        "Süpersin! Bu tempoyla sınav senden korksun. 🔥",
    ]
    static let fail = [
        "Bitkin soldu ama sen solma. Hemen yeni bir tohum ek! 🌱",
        "Herkes düşer, önemli olan tekrar kalkmak. Bir daha dene!",
        "Bu sefer olmadı. Bir sonraki seans senin! 💪",
    ]
    static let warning = [
        "Bitkin seni bekliyor! 10 saniye içinde dönmezsen solacak. 🥀",
        "Hey! Odak seansın devam ediyor. Hemen geri dön! ⏳",
        "Sosyal medya bekleyebilir, bitkin bekleyemez! 🌱",
    ]
    static let reminder = [
        "Bugün henüz tohum ekmedin. 25 dakikan var mı? 🌱",
        "Serini bozma! Kısa bir odak seansı yeter. 🔥",
        "Bahçen susadı… Bir seansla can ver! 💧",
        "Hedefine bir seans uzaktasın. Hadi başlayalım! 🎯",
    ]
    static let afterBreak = [
        "Mola bitti! Yeni bir tohum ekelim mi? 🌱",
        "Dinlendin, şimdi zirveye oynama zamanı! 🚀",
    ]

    static func random(_ list: [String]) -> String { list.randomElement() ?? "" }
}
