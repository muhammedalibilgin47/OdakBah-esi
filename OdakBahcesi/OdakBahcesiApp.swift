import SwiftUI
import UIKit

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

enum AppTab: Int, CaseIterable, Identifiable {
    case odak, bahce, arkadaslar, istatistik, magaza

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .odak: return "Odak"
        case .bahce: return "Bahçe"
        case .arkadaslar: return "Arkadaşlar"
        case .istatistik: return "İstatistik"
        case .magaza: return "Mağaza"
        }
    }

    var icon: String {
        switch self {
        case .odak: return "timer"
        case .bahce: return "leaf.fill"
        case .arkadaslar: return "person.2.fill"
        case .istatistik: return "chart.bar.fill"
        case .magaza: return "bag.fill"
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab: AppTab = .odak

    var body: some View {
        ZStack {
            tabs

            if let o = store.outcome {
                OutcomeOverlay(outcome: o)
                    .transition(.opacity)
            }

            if store.showInterstitial {
                InterstitialAdView { store.showInterstitial = false }
                    .transition(.move(edge: .bottom))
                    .zIndex(10)
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

    @ViewBuilder
    private func screen(_ t: AppTab) -> some View {
        switch t {
        case .odak: FocusView()
        case .bahce: GardenView()
        case .arkadaslar: FriendsView()
        case .istatistik: StatsView()
        case .magaza: ShopView()
        }
    }

    /// iOS 26+: Apple'ın gerçek Liquid Glass sekme çubuğu (yüzen, kaydırınca küçülen).
    /// Daha eski iOS: aynı görünümde yüzen buzlu cam kapsül.
    @ViewBuilder
    private var tabs: some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            TabView(selection: $tab) {
                ForEach(AppTab.allCases) { t in
                    screen(t)
                        .tabItem { Label(t.title, systemImage: t.icon) }
                        .tag(t)
                }
            }
            .tint(store.theme.primary)
            .tabBarMinimizeBehavior(.onScrollDown)
        } else {
            floatingTabs
        }
        #else
        floatingTabs
        #endif
    }

    private var floatingTabs: some View {
        TabView(selection: $tab) {
            ForEach(AppTab.allCases) { t in
                screen(t)
                    .toolbar(.hidden, for: .tabBar)
                    .tag(t)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            FloatingTabBar(selection: $tab)
        }
    }
}

// MARK: - Yüzen cam sekme çubuğu (iOS 16–18)

struct FloatingTabBar: View {
    @EnvironmentObject var store: AppStore
    @Binding var selection: AppTab
    @Namespace private var ns

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases) { t in
                let selected = selection == t
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.78)) { selection = t }
                    UISelectionFeedbackGenerator().selectionChanged()
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: t.icon)
                            .font(.system(size: 19, weight: .semibold))
                            .scaleEffect(selected ? 1.12 : 1)
                        Text(t.title)
                            .font(.system(size: 10, weight: .medium))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .foregroundColor(selected ? store.theme.primary : Color.primary.opacity(0.7))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background {
                        if selected {
                            Capsule()
                                .fill(store.theme.primary.opacity(0.16))
                                .matchedGeometryEffect(id: "secili", in: ns)
                        }
                    }
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .liquidGlass(in: Capsule())
        .padding(.horizontal, 18)
        .padding(.bottom, 6)
    }
}

extension View {
    /// iOS 26'da gerçek Liquid Glass, öncesinde buzlu cam + parlak kenar + gölge.
    @ViewBuilder
    func liquidGlass<S: Shape>(in shape: S) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self.glassEffect(.regular.interactive(), in: shape)
        } else {
            self.frostedGlass(in: shape)
        }
        #else
        self.frostedGlass(in: shape)
        #endif
    }

    func frostedGlass<S: Shape>(in shape: S) -> some View {
        self
            .background(.ultraThinMaterial, in: shape)
            .overlay(
                shape.stroke(
                    LinearGradient(colors: [.white.opacity(0.7), .white.opacity(0.1)],
                                   startPoint: .top, endPoint: .bottom),
                    lineWidth: 0.8
                )
            )
            .shadow(color: .black.opacity(0.18), radius: 18, x: 0, y: 8)
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
                .onTapGesture { store.dismissOutcome() }

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
                        Button("Tamam") { store.dismissOutcome() }
                            .buttonStyle(.bordered)
                        Button("Mola ver ☕") {
                            store.dismissOutcome()
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
                        Button("Kapat") { store.dismissOutcome() }
                            .buttonStyle(.bordered)
                        Button("Tekrar dene 🌱") {
                            store.dismissOutcome()
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
