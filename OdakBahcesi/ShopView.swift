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
                    Text("💧 Liquid Glass alt çubuk")
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

                // Ayarlar
                SectionTitle("⚙️ Ayarlar")
                VStack(alignment: .leading, spacing: 12) {
                    Text("🎯 Günlük hedef").font(.subheadline.weight(.semibold))
                    ChipRow {
                        ForEach([30, 60, 90, 120, 180, 240, 360], id: \.self) { g in
                            Chip(title: "\(g) dk", selected: p.dailyGoal == g) { store.profile.dailyGoal = g }
                        }
                    }
                    Divider()
                    Toggle(isOn: $store.profile.reminderOn) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("⏰ Akıllı hatırlatıcı").fontWeight(.semibold)
                            Text("O gün çalışmadıysan seçtiğin saatte hatırlatır.").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .tint(th.primary)
                    if p.reminderOn {
                        ChipRow {
                            ForEach([8, 12, 16, 18, 19, 20, 21, 22], id: \.self) { h in
                                Chip(title: String(format: "%02d:00", h), selected: p.reminderHour == h) {
                                    store.profile.reminderHour = h
                                }
                            }
                        }
                    }
                    Divider()
                    Toggle(isOn: Binding(
                        get: { p.premium && p.liquidGlass },
                        set: { v in
                            if p.premium {
                                store.profile.liquidGlass = v
                            } else {
                                alertText = "Liquid Glass alt çubuk Premium'a özel 👑"
                                showAlert = true
                            }
                        }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("💧 Liquid Glass alt çubuk\(p.premium ? "" : " 🔒")").fontWeight(.semibold)
                            Text(p.premium ? "Yüzen cam kapsül sekme çubuğu." : "Premium üyelere özel.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .tint(th.primary)
                    Divider()
                    Toggle(isOn: $store.profile.autoCycle) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("🔁 Otomatik Pomodoro").fontWeight(.semibold)
                            Text("Seans bitince mola kendiliğinden başlar; 4 seansta bir 15 dk uzun mola.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .tint(th.primary)
                    Divider()
                    Text("🎓 Sınav geri sayımı").font(.subheadline.weight(.semibold))
                    TextField("Sınav adı (ör. YKS, LGS, KPSS)", text: $store.profile.examName)
                        .textFieldStyle(.roundedBorder)
                    if p.examDate > 0 {
                        DatePicker("Sınav tarihi", selection: Binding(
                            get: { Date(timeIntervalSince1970: store.profile.examDate) },
                            set: { store.profile.examDate = $0.timeIntervalSince1970 }
                        ), in: Date()..., displayedComponents: .date)
                        Button("Geri sayımı kaldır", role: .destructive) { store.profile.examDate = 0 }
                            .font(.footnote)
                    } else {
                        Button("📅 Sınav tarihi ekle") {
                            store.profile.examDate = (Calendar.current.date(byAdding: .month, value: 6, to: Date()) ?? Date())
                                .timeIntervalSince1970
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(16)
                .background(th.card)
                .clipShape(RoundedRectangle(cornerRadius: 18))

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
