import SwiftUI

struct StatsView: View {
    @EnvironmentObject var store: AppStore

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.dateFormat = "d MMM, HH:mm"
        return f
    }()

    var body: some View {
        let st = computeStats(store.sessions)
        let th = store.theme

        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("İstatistikler").font(.largeTitle.bold())

                HStack(spacing: 12) {
                    statCard("⏱️ Bugün", "\(st.todayMin) dk")
                    statCard("🔥 Seri", "\(st.streak) gün")
                }
                HStack(spacing: 12) {
                    statCard("📚 Toplam", String(format: "%.1f sa", Double(st.totalMin) / 60))
                    statCard("🎯 Başarı", "%\(st.successRate)")
                }

                // Son 7 gün
                CardBox {
                    Text("Son 7 gün").font(.headline)
                    let maxV = max(st.week.map { $0.1 }.max() ?? 0, 30)
                    HStack(alignment: .bottom, spacing: 0) {
                        ForEach(0..<st.week.count, id: \.self) { i in
                            let item = st.week[i]
                            VStack(spacing: 4) {
                                Text(item.1 > 0 ? "\(item.1)" : " ").font(.caption2)
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(th.primary.opacity(i == st.week.count - 1 ? 1 : 0.5))
                                    .frame(width: 26, height: max(4, CGFloat(item.1) / CGFloat(maxV) * 110))
                                Text(item.0).font(.caption)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .frame(height: 160, alignment: .bottom)
                    Text("En iyi günün: \(st.bestDayMin) dk")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                // Ders bazlı — Premium
                CardBox {
                    Text("Ders bazlı dağılım").font(.headline)
                    if !store.profile.premium {
                        Text("🔒 Detaylı istatistikler Premium üyelere özel. Mağaza sekmesinden açabilirsin.")
                            .font(.subheadline)
                    } else if st.bySubject.isEmpty {
                        Text("Henüz veri yok.")
                    } else {
                        let total = max(st.bySubject.reduce(0) { $0 + $1.1 }, 1)
                        ForEach(0..<st.bySubject.count, id: \.self) { i in
                            let item = st.bySubject[i]
                            HStack {
                                Text(item.0).frame(width: 92, alignment: .leading)
                                ProgressView(value: Double(item.1), total: Double(total))
                                    .tint(th.primary)
                                Text("\(item.1) dk").font(.footnote)
                            }
                        }
                    }
                }

                // Son seanslar
                CardBox {
                    Text("Son seanslar").font(.headline)
                    if store.sessions.isEmpty {
                        Text("Henüz seans yok.")
                    }
                    ForEach(Array(store.sessions.prefix(10))) { s in
                        HStack(spacing: 10) {
                            Text(s.success ? s.plant.grown : deadPlant).font(.title2)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(s.subject) · \(s.minutes) dk").fontWeight(.semibold)
                                Text(Self.dateFormatter.string(from: s.endAt))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(s.success ? "Tamamlandı" : "Soldu")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.top, 4)
                    }
                }
            }
            .padding()
        }
        .background(th.background.ignoresSafeArea())
        .adBanner(store.showAds)
    }

    private func statCard(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
            Text(value).font(.title2.bold())
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(store.theme.secondary.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
