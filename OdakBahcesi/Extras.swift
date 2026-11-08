import SwiftUI
import UIKit

// MARK: - Günlük görevler

struct Quest: Identifiable {
    let id: String
    let emoji: String
    let title: String
    let reward: Int
    let current: Int
    let target: Int
    var done: Bool { current >= target }
}

enum Quests {
    static let pool = ["s2", "s3", "m60", "m90", "long40", "morning", "clean"]

    /// Her gün aynı 3 görev (Android ile aynı seçim)
    static func today(sessions: [Session]) -> [Quest] {
        let cal = Calendar.current
        let start = cal.startOfDay(for: Date())
        let todays = sessions.filter { $0.endAt >= start }
        let ok = todays.filter { $0.success }
        let fails = todays.count - ok.count
        let mins = ok.reduce(0) { $0 + $1.minutes }
        let longest = ok.map { $0.minutes }.max() ?? 0
        let morning = ok.contains { cal.component(.hour, from: $0.endAt) < 12 }

        let c = cal.dateComponents([.year, .month, .day], from: Date())
        var x = (c.year ?? 2026) * 10000 + (c.month ?? 1) * 100 + (c.day ?? 1)
        var remaining = pool
        var picked: [String] = []
        for _ in 0..<3 {
            x = (x &* 1103515245 &+ 12345) & 0x7FFFFFFF
            picked.append(remaining.remove(at: x % remaining.count))
        }

        return picked.map { id in
            switch id {
            case "s2": return Quest(id: id, emoji: "🌱", title: "2 seans tamamla", reward: 20, current: ok.count, target: 2)
            case "s3": return Quest(id: id, emoji: "🌿", title: "3 seans tamamla", reward: 30, current: ok.count, target: 3)
            case "m60": return Quest(id: id, emoji: "⏱️", title: "Bugün 60 dk çalış", reward: 25, current: mins, target: 60)
            case "m90": return Quest(id: id, emoji: "🔥", title: "Bugün 90 dk çalış", reward: 40, current: mins, target: 90)
            case "long40": return Quest(id: id, emoji: "🏃", title: "40+ dakikalık bir seans bitir", reward: 30, current: longest >= 40 ? 1 : 0, target: 1)
            case "morning": return Quest(id: id, emoji: "☀️", title: "Öğleden önce bir seans bitir", reward: 25, current: morning ? 1 : 0, target: 1)
            default: return Quest(id: id, emoji: "💎", title: "Hiç vazgeçmeden 2 seans bitir", reward: 35, current: fails == 0 ? min(ok.count, 2) : 0, target: 2)
            }
        }
    }
}

// MARK: - Şans çarkı

enum Wheel {
    static let prizes = [5, 10, 15, 20, 25, 30, 50, 100]
    static let weights = [20, 20, 18, 15, 10, 8, 6, 3]
    static let colors: [Color] = [.red, .orange, .yellow, .green, .mint, .blue, .purple, .pink]

    static func pick() -> Int {
        let total = weights.reduce(0, +)
        var r = Int.random(in: 0..<total)
        for (i, w) in weights.enumerated() {
            if r < w { return i }
            r -= w
        }
        return 0
    }
}

private struct WheelSlice: Shape {
    let start: Angle
    let end: Angle
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let c = CGPoint(x: rect.midX, y: rect.midY)
        p.move(to: c)
        p.addArc(center: c, radius: min(rect.width, rect.height) / 2, startAngle: start, endAngle: end, clockwise: false)
        p.closeSubpath()
        return p
    }
}

struct WheelView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var rotation: Double = 0
    @State private var spinning = false
    @State private var result: Int? = nil

    var body: some View {
        let n = Wheel.prizes.count
        let seg = 360.0 / Double(n)
        VStack(spacing: 20) {
            Text("🎡 Şans Çarkı").font(.largeTitle.bold())
            Text("Bugünkü ilk seansını bitirdin, çarkı çevir!").foregroundStyle(.secondary)
            ZStack {
                ZStack {
                    ForEach(0..<n, id: \.self) { i in
                        WheelSlice(start: .degrees(Double(i) * seg - 90), end: .degrees(Double(i + 1) * seg - 90))
                            .fill(Wheel.colors[i].opacity(0.85))
                        Text("\(Wheel.prizes[i])")
                            .font(.headline.bold())
                            .foregroundColor(.white)
                            .offset(y: -105)
                            .rotationEffect(.degrees(Double(i) * seg + seg / 2))
                    }
                }
                .frame(width: 280, height: 280)
                .rotationEffect(.degrees(rotation))
                Circle().fill(store.theme.card).frame(width: 56, height: 56)
                Text("🪙").font(.title)
                Image(systemName: "arrowtriangle.down.fill")
                    .font(.system(size: 34))
                    .foregroundColor(store.theme.primary)
                    .offset(y: -152)
            }
            .frame(height: 320)

            if let r = result {
                Text("🎉 +\(Wheel.prizes[r]) altın kazandın!")
                    .font(.title2.bold())
                    .foregroundColor(store.theme.primary)
                Button("Harika!") { dismiss() }
                    .buttonStyle(.borderedProminent)
                    .tint(store.theme.primary)
            } else {
                Button {
                    guard !spinning, let idx = store.spinWheel() else { return }
                    spinning = true
                    // Oku seçilen dilimin ortasına getir
                    let target = 360.0 * 6 + (360.0 - (Double(idx) * seg + seg / 2))
                    withAnimation(.timingCurve(0.15, 0.85, 0.2, 1, duration: 4)) { rotation = target }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 4.1) {
                        result = idx
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                    }
                } label: {
                    Text(spinning ? "Dönüyor…" : "Çevir!")
                        .font(.headline)
                        .frame(maxWidth: 220)
                        .padding(.vertical, 14)
                        .background(store.theme.primary)
                        .foregroundColor(.white)
                        .clipShape(Capsule())
                }
                .disabled(spinning)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(store.theme.background.ignoresSafeArea())
    }
}

// MARK: - Görevler + çark kartı (Odak ekranı)

struct QuestsCard: View {
    @EnvironmentObject var store: AppStore
    @State private var showWheel = false

    var body: some View {
        let quests = Quests.today(sessions: store.sessions)
        VStack(alignment: .leading, spacing: 10) {
            Text("📋 Günlük görevler").font(.headline)
            ForEach(quests) { q in
                let claimed = store.isQuestClaimed(q.id)
                HStack(spacing: 10) {
                    Text(q.emoji).font(.title3)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(q.title).font(.subheadline.weight(.semibold))
                            .strikethrough(claimed)
                        ProgressView(value: Double(min(q.current, q.target)), total: Double(q.target))
                            .tint(q.done ? .orange : store.theme.primary)
                    }
                    if claimed {
                        Text("✅").font(.title3)
                    } else if q.done {
                        Button("🪙 \(q.reward)") { store.claimQuest(q) }
                            .buttonStyle(.borderedProminent)
                            .tint(.orange)
                            .font(.caption.bold())
                    } else {
                        Text("🪙 \(q.reward)").font(.caption.bold()).foregroundStyle(.secondary)
                    }
                }
            }
            if store.canSpinWheel {
                Button {
                    showWheel = true
                } label: {
                    Text("🎡 Şans çarkını çevir!")
                        .fontWeight(.bold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(LinearGradient(colors: [.orange, .pink], startPoint: .leading, endPoint: .trailing))
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
        }
        .padding(14)
        .background(store.theme.card)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .sheet(isPresented: $showWheel) { WheelView() }
    }
}

// MARK: - Isı haritası + rekorlar (İstatistik)

struct HeatmapCard: View {
    @EnvironmentObject var store: AppStore

    var body: some View {
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        var byDay: [Date: Int] = [:]
        for s in store.sessions where s.success { byDay[cal.startOfDay(for: s.endAt), default: 0] += s.minutes }
        let days = (0..<84).reversed().compactMap { cal.date(byAdding: .day, value: -$0, to: today) }
        let active = days.filter { (byDay[$0] ?? 0) > 0 }.count

        return CardBox {
            Text("🗓️ Son 12 hafta").font(.headline)
            HStack(alignment: .top, spacing: 4) {
                ForEach(0..<12, id: \.self) { w in
                    VStack(spacing: 4) {
                        ForEach(0..<7, id: \.self) { d in
                            let day = days[w * 7 + d]
                            let m = byDay[day] ?? 0
                            RoundedRectangle(cornerRadius: 3)
                                .fill(m == 0 ? Color.gray.opacity(0.15) : store.theme.primary.opacity(level(m)))
                                .frame(height: 18)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 3)
                                        .stroke(day == today ? Color.orange : .clear, lineWidth: 1.5)
                                )
                        }
                    }
                }
            }
            HStack {
                Text("\(active) aktif gün").font(.caption).foregroundStyle(.secondary)
                Spacer()
                Text("Az").font(.caption2).foregroundStyle(.secondary)
                ForEach([0.3, 0.5, 0.75, 1.0], id: \.self) { o in
                    RoundedRectangle(cornerRadius: 2).fill(store.theme.primary.opacity(o)).frame(width: 12, height: 12)
                }
                Text("Çok").font(.caption2).foregroundStyle(.secondary)
            }
        }
    }

    private func level(_ m: Int) -> Double {
        switch m {
        case ..<30: return 0.3
        case ..<60: return 0.5
        case ..<120: return 0.75
        default: return 1.0
        }
    }
}

struct RecordsCard: View {
    @EnvironmentObject var store: AppStore

    var body: some View {
        let ok = store.sessions.filter { $0.success }
        let st = computeStats(store.sessions)
        let cal = Calendar.current
        var byWeek: [String: Int] = [:]
        for s in ok {
            let c = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: s.endAt)
            byWeek["\(c.yearForWeekOfYear ?? 0)-\(c.weekOfYear ?? 0)", default: 0] += s.minutes
        }
        let longest = ok.map { $0.minutes }.max() ?? 0
        let bestWeek = byWeek.values.max() ?? 0
        let report = """
        🌱 Odak Bahçesi haftalık raporum
        ⏱️ Bu hafta: \(st.weekTotal) dk (\(String(format: "%.1f", Double(st.weekTotal) / 60)) saat)
        🔥 Seri: \(st.streak) gün
        🌳 Toplam bitki: \(ok.count)
        🏅 Rozet: \(store.profile.badges.count)/\(Achievements.all.count)
        Sen de benimle çalış! 💪
        """

        return CardBox {
            Text("🏆 Kişisel rekorlar").font(.headline)
            HStack(spacing: 10) {
                record("En uzun seans", "\(longest) dk")
                record("En iyi gün", "\(st.bestDayMin) dk")
                record("En iyi hafta", "\(bestWeek) dk")
            }
            ShareLink(item: report) {
                Label("Haftalık raporu paylaş", systemImage: "square.and.arrow.up")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(store.theme.primary)
        }
    }

    private func record(_ title: String, _ value: String) -> some View {
        VStack(spacing: 4) {
            Text(value).font(.headline.bold())
            Text(title).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(store.theme.secondary.opacity(0.25))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
