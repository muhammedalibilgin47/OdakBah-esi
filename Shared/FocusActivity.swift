import ActivityKit
import AppIntents
import Foundation

/// Kilit ekranı ve Dynamic Island'da gösterilen canlı etkinlik verisi.
/// Hem uygulamada hem widget eklentisinde derlenir.
struct FocusActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var startAt: Date
        var endAt: Date
        var isBreak: Bool
    }

    var plantEmoji: String
    var plantName: String
    var subject: String
}

extension Notification.Name {
    static let odakStartFromControl = Notification.Name("odak.startFromControl")
}

/// Denetim Merkezi düğmesinin çalıştırdığı işlem: uygulamayı açar ve odak seansı başlatır.
struct StartFocusIntent: AppIntent {
    static var title: LocalizedStringResource = "Odak seansı başlat"
    static var description = IntentDescription("Odak Bahçesi'nde yeni bir odak seansı başlatır.")
    static var openAppWhenRun: Bool = true

    func perform() async throws -> some IntentResult {
        UserDefaults.standard.set(true, forKey: "pendingStartFocus")
        await MainActor.run {
            NotificationCenter.default.post(name: .odakStartFromControl, object: nil)
        }
        return .result()
    }
}
