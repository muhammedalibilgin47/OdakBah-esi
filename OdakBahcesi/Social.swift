import Foundation

/// Profil koduyla eklenen arkadaş. Kod her paylaşıldığında güncellenir.
struct Friend: Codable, Identifiable {
    var id: String
    var name: String
    var level: Int
    var weekMin: Int
    var totalMin: Int
    var streak: Int
    var grown: Int
    var garden: [String]
    var updatedAt: Date
    var isDemo: Bool
}

/// Profil kodunun içeriği (kısa anahtarlar = kısa kod)
struct SharePayload: Codable {
    var u: String       // kullanıcı id
    var n: String       // isim
    var l: Int          // seviye
    var w: Int          // bu haftaki dakika
    var t: Int          // toplam dakika
    var s: Int          // seri
    var g: Int          // büyüyen bitki sayısı
    var p: [String]     // son bitkiler
    var d: Double       // oluşturulma zamanı
}

enum ShareCode {
    static let prefix = "ODAK-"

    static func make(_ p: SharePayload) -> String {
        guard let data = try? JSONEncoder().encode(p) else { return "" }
        return prefix + data.base64EncodedString()
    }

    /// Kod bir mesajın içinde gelse bile bulur.
    static func parse(_ raw: String) -> SharePayload? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if let r = s.range(of: prefix) { s = String(s[r.upperBound...]) }
        s = String(s.prefix { !$0.isWhitespace })
        guard let data = Data(base64Encoded: s) else { return nil }
        return try? JSONDecoder().decode(SharePayload.self, from: data)
    }
}

enum DemoFriends {
    static func make() -> [Friend] {
        let now = Date()
        return [
            Friend(id: "demo-elif", name: "Elif (demo)", level: 4, weekMin: 340, totalMin: 1020, streak: 5,
                   grown: 38, garden: ["🌷", "🌻", "🌷", "🌳", "🌼", "🌷"], updatedAt: now, isDemo: true),
            Friend(id: "demo-mert", name: "Mert (demo)", level: 2, weekMin: 125, totalMin: 410, streak: 2,
                   grown: 15, garden: ["🌵", "🌼", "🌵"], updatedAt: now, isDemo: true),
            Friend(id: "demo-zeynep", name: "Zeynep (demo)", level: 7, weekMin: 610, totalMin: 2100, streak: 12,
                   grown: 80, garden: ["🌸", "🌲", "🌸", "🌴", "🌹", "🌸", "🌲"], updatedAt: now, isDemo: true),
        ]
    }
}
