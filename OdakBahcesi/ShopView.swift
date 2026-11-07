import SwiftUI

struct ShopView: View {
    @EnvironmentObject var store: AppStore
    @State private var showAd = false
    @State private var showPremium = false
    @State private var alertText = ""
    @State private var showAlert = false

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        let th = store.theme
        let p = store.profile

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Mağaza").font(.largeTitle.bold())

                // Bakiye + ödüllü reklam
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Bakiyen")
                        Text("🪙 \(p.coins)").font(.system(size: 30, weight: .bold))
                        Text("Her odak dakikası = 1 altın").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        showAd = true
                    } label: {
                        Text("🎬 Reklam izle\n+\(AppStore.adReward) 🪙")
                            .multilineTextAlignment(.center)
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(th.primary)
                }
                .padding(16)
                .background(th.secondary.opacity(0.3))
                .clipShape(RoundedRectangle(cornerRadius: 18))

                // Premium
                VStack(alignment: .leading, spacing: 6) {
                    Text(p.premium ? "👑 Premium üyesin" : "👑 Odak Bahçesi Premium")
                        .font(.title3.bold())
                    Text("🎨 4 özel tema (Okyanus, Gün Batımı, Lavanta, Gece)")
                    Text("📊 Ders bazlı detaylı istatistikler")
                    Text("👥 Sınırsız arkadaş, çalışma grubu ve ortak bahçe")
                    Text("🚫 Reklamsız deneyim")
                    if p.premium {
                        Button("Premium'u kapat (demo)") { store.setPremium(false) }
                            .buttonStyle(.bordered)
                            .padding(.top, 6)
                    } else {
                        Button {
                            showPremium = true
                        } label: {
                            Text("Premium'a geç · ₺49,99/ay")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(th.primary)
                                .foregroundColor(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .padding(.top, 6)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(th.card)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(th.primary, lineWidth: 2))
                .clipShape(RoundedRectangle(cornerRadius: 18))

                // Bitkiler
                SectionTitle("Bitkiler")
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(PlantType.allCases) { plant in
                        plantCard(plant)
                    }
                }

                // Temalar
                SectionTitle("Temalar")
                ChipRow {
                    ForEach(AppTheme.allCases) { t in
                        let locked = t.premium && !p.premium
                        Chip(title: "\(t.emoji) \(t.label)\(locked ? " 🔒" : "")", selected: p.theme == t) {
                            if !store.selectTheme(t) {
                                alertText = "Bu tema Premium'a özel 👑"
                                showAlert = true
                            }
                        }
                    }
                }

                // Mola süresi
                SectionTitle("Mola süresi (dakika)")
                ChipRow {
                    ForEach(breakOptions, id: \.self) { m in
                        Chip(title: "\(m)", selected: p.breakMin == m) { store.profile.breakMin = m }
                    }
                }
                Spacer(minLength: 24)
            }
            .padding()
            .alert(alertText, isPresented: $showAlert) {
                Button("Tamam", role: .cancel) { }
            }
        }
        .background(th.background.ignoresSafeArea())
        .alert("👑 Premium (demo)", isPresented: $showPremium) {
            Button("Premium'u dene") { store.setPremium(true) }
            Button("Vazgeç", role: .cancel) { }
        } message: {
            Text("Gerçek sürümde burada App Store ödeme ekranı açılır. Şimdilik Premium özelliklerini ücretsiz deneyebilirsin.")
        }
        .overlay {
            if showAd {
                AdOverlay {
                    showAd = false
                    store.rewardAd()
                    alertText = "+\(AppStore.adReward) altın kazandın! 🪙"
                    showAlert = true
                }
            }
        }
    }

    private func plantCard(_ plant: PlantType) -> some View {
        let p = store.profile
        let owned = p.owned.contains(plant)
        let selected = p.selectedPlant == plant
        return Button {
            let ok = store.buyOrSelect(plant)
            if !ok {
                alertText = "Yetersiz altın. \(plant.price - p.coins) 🪙 daha lazım."
                showAlert = true
            } else if !owned {
                alertText = "\(plant.label) satın alındı! 🎉"
                showAlert = true
            }
        } label: {
            VStack(spacing: 4) {
                Text(plant.grown).font(.system(size: 40))
                Text(plant.label).fontWeight(.semibold)
                Text(plant.stages.joined(separator: " → ")).font(.system(size: 11))
                Text(selected ? "✓ Seçili" : (owned ? "Sahipsin" : "🪙 \(plant.price)"))
                    .fontWeight(.bold)
                    .foregroundColor(store.theme.primary)
                    .padding(.top, 2)
            }
            .frame(maxWidth: .infinity)
            .padding(12)
            .background(store.theme.card)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(store.theme.primary, lineWidth: selected ? 2 : 0))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

/// Ödüllü reklam simülasyonu (gerçek sürümde AdMob Rewarded Ad)
struct AdOverlay: View {
    @EnvironmentObject var store: AppStore
    let onDone: () -> Void
    @State private var remaining = 5

    var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(spacing: 12) {
                Text("🎬 Reklam (demo)").font(.title2.bold())
                Text("Gerçek uygulamada burada ödüllü bir video reklam oynar.")
                    .multilineTextAlignment(.center)
                Text("\(remaining)")
                    .font(.system(size: 54, weight: .bold, design: .rounded))
                    .foregroundColor(store.theme.primary)
                Text("saniye sonra ödülün hesabına eklenecek")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(24)
            .frame(maxWidth: 320)
            .background(store.theme.card)
            .clipShape(RoundedRectangle(cornerRadius: 24))
        }
        .task {
            while remaining > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                remaining -= 1
            }
            onDone()
        }
    }
}
