import SwiftUI

@main
struct OdakBahcesiApp: App {
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            TabView {
                FocusView()
                    .tabItem { Label("Odak", systemImage: "timer") }
                GardenView()
                    .tabItem { Label("Bahçe", systemImage: "leaf.fill") }
                StatsView()
                    .tabItem { Label("İstatistik", systemImage: "chart.bar.fill") }
                ShopView()
                    .tabItem { Label("Mağaza", systemImage: "bag.fill") }
            }
            .tint(store.theme.primary)

            if let o = store.outcome {
                OutcomeOverlay(outcome: o)
                    .transition(.opacity)
            }
        }
        .preferredColorScheme(store.theme.dark ? .dark : .light)
        .onChange(of: scenePhase) { phase in
            store.scenePhaseChanged(phase)
        }
        .onAppear {
            Notifier.requestPermission()
            store.handleForeground()
        }
    }
}

// MARK: - Ortak bileşenler

struct Chip: View {
    @EnvironmentObject var store: AppStore
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? store.theme.primary : store.theme.card)
                .foregroundColor(selected ? .white : .primary)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(store.theme.primary.opacity(selected ? 0 : 0.35), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct ChipRow<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) { content }
                .padding(.horizontal, 2)
                .padding(.vertical, 2)
        }
    }
}

struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text)
            .font(.headline)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 6)
    }
}

struct CardBox<Content: View>: View {
    @EnvironmentObject var store: AppStore
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(store.theme.card)
            .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct ProfileHeader: View {
    @EnvironmentObject var store: AppStore

    var body: some View {
        let p = store.profile
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Seviye \(p.level) · \(p.title)").fontWeight(.bold)
                    Text("\(p.xp % Profile.xpPerLevel) / \(Profile.xpPerLevel) XP")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text("🪙 \(p.coins)").font(.title3.bold())
            }
            ProgressView(value: p.levelProgress)
                .tint(store.theme.primary)
        }
        .padding(14)
        .background(store.theme.secondary.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct OutcomeOverlay: View {
    @EnvironmentObject var store: AppStore
    let outcome: Outcome

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { store.outcome = nil }

            VStack(spacing: 12) {
                switch outcome {
                case let .grown(plant, minutes, coins, xp, newLevel, message):
                    Text(plant.grown).font(.system(size: 80))
                    Text("\(plant.label) büyüdü!").font(.title2.bold())
                    Text(message).multilineTextAlignment(.center)
                    Text("⏱️ \(minutes) dk   🪙 +\(coins)   ⭐ +\(xp) XP")
                        .font(.subheadline.weight(.semibold))
                    if let lvl = newLevel {
                        Text("🎉 Seviye \(lvl)'e ulaştın!")
                            .fontWeight(.bold)
                            .foregroundColor(store.theme.primary)
                    }
                    HStack(spacing: 12) {
                        Button("Tamam") { store.outcome = nil }
                            .buttonStyle(.bordered)
                        Button("Mola ver ☕") {
                            store.outcome = nil
                            store.startBreak()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(.top, 6)

                case let .withered(reason, message):
                    Text(deadPlant).font(.system(size: 80))
                    Text("Bitkin soldu").font(.title2.bold())
                    Text(reason).fontWeight(.semibold).multilineTextAlignment(.center)
                    Text(message).multilineTextAlignment(.center)
                    HStack(spacing: 12) {
                        Button("Kapat") { store.outcome = nil }
                            .buttonStyle(.bordered)
                        Button("Tekrar dene 🌱") {
                            store.outcome = nil
                            store.startFocus()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(.top, 6)
                }
            }
            .padding(24)
            .frame(maxWidth: 340)
            .background(store.theme.card)
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .padding()
        }
        .tint(store.theme.primary)
    }
}
