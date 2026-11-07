import SwiftUI

struct FocusView: View {
    @EnvironmentObject var store: AppStore
    @State private var confirmGiveUp = false

    var body: some View {
        let th = store.theme
        ScrollView {
            VStack(spacing: 18) {
                ProfileHeader()

                // Büyüyen bitki + ilerleme halkası
                ZStack {
                    Circle()
                        .stroke(th.primary.opacity(0.15), lineWidth: 16)
                    Circle()
                        .trim(from: 0, to: store.progress)
                        .stroke(th.primary, style: StrokeStyle(lineWidth: 16, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .animation(.linear(duration: 0.5), value: store.progress)
                    VStack(spacing: 4) {
                        Text(store.currentEmoji)
                            .font(.system(size: 84))
                            .id(store.currentEmoji)
                            .transition(.scale(scale: 0.4).combined(with: .opacity))
                        Text(formatTime(store.displaySeconds))
                            .font(.system(size: 46, weight: .bold, design: .rounded))
                            .monospacedDigit()
                        if let t = store.timer {
                            Text(t.mode == .focus ? t.subject : "Mola")
                                .fontWeight(.semibold)
                                .foregroundColor(th.primary)
                        }
                    }
                    .animation(.spring(response: 0.5, dampingFraction: 0.5), value: store.currentEmoji)
                }
                .frame(width: 270, height: 270)
                .padding(.top, 4)

                Text(store.quote)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 8)

                switch store.mode {
                case .idle:
                    idleControls
                case .focus:
                    Text("🔒 Odak modu açık — telefonu bırak, bitkin büyüsün.")
                        .font(.subheadline)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                    Button("Vazgeç") { confirmGiveUp = true }
                        .buttonStyle(.bordered)
                        .tint(.red)
                case .rest:
                    Text("Gözlerini ekrandan ayır, biraz su iç ve esne. 🧘")
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                    Button("Molayı bitir") { store.stopBreak() }
                        .buttonStyle(.bordered)
                }
            }
            .padding()
        }
        .background(th.background.ignoresSafeArea())
        .adBanner(store.showAds && store.mode == .idle)
        .alert("Emin misin? 🥀", isPresented: $confirmGiveUp) {
            Button("Bırak", role: .destructive) { store.giveUp() }
            Button("Devam ediyorum 💪", role: .cancel) { }
        } message: {
            Text("Şimdi bırakırsan bitkin solacak ve bahçene solmuş olarak eklenecek.")
        }
    }

    @ViewBuilder
    private var idleControls: some View {
        let th = store.theme
        VStack(spacing: 10) {
            SectionTitle("Ne çalışıyorsun?")
            ChipRow {
                ForEach(subjects, id: \.self) { s in
                    Chip(title: s, selected: store.profile.subject == s) { store.profile.subject = s }
                }
            }

            SectionTitle("Süre (dakika)")
            ChipRow {
                ForEach(focusOptions, id: \.self) { m in
                    Chip(title: "\(m)", selected: store.profile.focusMin == m) { store.profile.focusMin = m }
                }
            }

            SectionTitle("Hangi bitkiyi yetiştireceksin?")
            ChipRow {
                ForEach(PlantType.allCases.filter { store.profile.owned.contains($0) }) { pl in
                    Chip(title: "\(pl.grown) \(pl.label)", selected: store.profile.selectedPlant == pl) {
                        store.profile.selectedPlant = pl
                    }
                }
            }

            Toggle(isOn: $store.profile.strictMode) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("🔒 Katı mod").fontWeight(.semibold)
                    Text(store.profile.strictMode
                         ? "Başka uygulamaya geçersen bitkin solar."
                         : "Diğer uygulamaları kullanabilirsin, sayaç Dynamic Island'da görünür.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .tint(th.primary)
            .padding(.top, 6)

            Button {
                withAnimation { store.startFocus() }
            } label: {
                Text("🌱  Odaklanmaya Başla")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(th.primary)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .padding(.top, 12)

            Text(store.profile.strictMode
                 ? "Katı modda başka uygulamaya geçip 10 saniye içinde dönmezsen bitkin solar. Ekranı kilitlemek serbest."
                 : "Sayaç kilit ekranında ve Dynamic Island'da görünür.")
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
    }
}
