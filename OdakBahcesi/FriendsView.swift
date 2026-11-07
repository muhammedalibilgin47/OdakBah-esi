import SwiftUI
import UIKit

struct FriendsView: View {
    @EnvironmentObject var store: AppStore
    @State private var codeInput = ""
    @State private var message = ""
    @State private var showMessage = false
    @State private var newGroupName = ""
    @State private var newGroupGoal = 600
    @State private var confirmLeave = false

    private struct Row: Identifiable {
        let id: String
        let name: String
        let level: Int
        let weekMin: Int
        let streak: Int
        let isMe: Bool
        let isDemo: Bool
    }

    private var leaderboard: [Row] {
        var rows = store.friends.map {
            Row(id: $0.id, name: $0.name, level: $0.level, weekMin: $0.weekMin, streak: $0.streak, isMe: false, isDemo: $0.isDemo)
        }
        rows.append(Row(id: "me", name: "\(store.profile.name) (sen)", level: store.profile.level,
                        weekMin: store.myWeekMin, streak: computeStats(store.sessions).streak, isMe: true, isDemo: false))
        return rows.sorted { $0.weekMin > $1.weekMin }
    }

    var body: some View {
        let th = store.theme
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Arkadaşlar").font(.largeTitle.bold())

                myProfileCard
                addFriendCard
                leaderboardCard
                groupCard

                if !store.friends.contains(where: { $0.isDemo }) {
                    Button("🧪 Denemek için demo arkadaşlar ekle") { store.addDemoFriends() }
                        .font(.footnote)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding()
        }
        .background(th.background.ignoresSafeArea())
        .adBanner(store.showAds)
        .alert(message, isPresented: $showMessage) {
            Button("Tamam", role: .cancel) { }
        }
        .alert("Gruptan çıkılsın mı?", isPresented: $confirmLeave) {
            Button("Çık", role: .destructive) { store.leaveGroup() }
            Button("Vazgeç", role: .cancel) { }
        }
    }

    // MARK: Profilim

    private var myProfileCard: some View {
        CardBox {
            Text("Senin profilin").font(.headline)
            HStack {
                Text("İsmin:")
                TextField("İsmin", text: $store.profile.name)
                    .textFieldStyle(.roundedBorder)
            }
            Text("Arkadaşlarının seni eklemesi için profil kodunu gönder. Yeni seanslardan sonra tekrar gönderirsen sıralamada güncellenirsin.")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack(spacing: 10) {
                ShareLink(item: store.shareMessage) {
                    Label("Kodu paylaş", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(store.theme.primary)

                Button {
                    UIPasteboard.general.string = store.myShareCode
                    message = "Profil kodun kopyalandı 📋"
                    showMessage = true
                } label: {
                    Label("Kopyala", systemImage: "doc.on.doc")
                }
                .buttonStyle(.bordered)
            }
        }
    }

    // MARK: Arkadaş ekle

    private var addFriendCard: some View {
        CardBox {
            Text("Arkadaş ekle").font(.headline)
            TextField("ODAK-... kodunu buraya yapıştır", text: $codeInput, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(1...3)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            HStack(spacing: 10) {
                Button {
                    codeInput = UIPasteboard.general.string ?? ""
                } label: {
                    Label("Yapıştır", systemImage: "doc.on.clipboard")
                }
                .buttonStyle(.bordered)

                Button {
                    message = store.addFriend(code: codeInput)
                    showMessage = true
                    if message.contains("eklendi") || message.contains("güncellendi") { codeInput = "" }
                } label: {
                    Label("Ekle", systemImage: "person.badge.plus")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(store.theme.primary)
                .disabled(codeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            if !store.profile.premium {
                Text("Ücretsiz sürümde en fazla \(AppStore.freeFriendLimit) arkadaş. Premium ile sınırsız 👑")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: Sıralama

    private var leaderboardCard: some View {
        CardBox {
            Text("🏆 Bu hafta sıralama").font(.headline)
            let rows = leaderboard
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                HStack(spacing: 10) {
                    Text(medal(index))
                        .font(.title3)
                        .frame(width: 34)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.name).fontWeight(row.isMe ? .bold : .semibold)
                        Text("Seviye \(row.level) · 🔥 \(row.streak) gün")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text("\(row.weekMin) dk")
                        .font(.headline.monospacedDigit())
                        .foregroundColor(row.isMe ? store.theme.primary : .primary)
                    if !row.isMe {
                        Button {
                            store.removeFriend(row.id)
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 4)
                .padding(.horizontal, 6)
                .background(row.isMe ? store.theme.secondary.opacity(0.25) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            if store.friends.isEmpty {
                Text("Henüz arkadaşın yok. Kodunu paylaş veya bir arkadaşının kodunu ekle!")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func medal(_ i: Int) -> String {
        switch i {
        case 0: return "🥇"
        case 1: return "🥈"
        case 2: return "🥉"
        default: return "\(i + 1)."
        }
    }

    // MARK: Grup + ortak bahçe

    private var groupCard: some View {
        CardBox {
            Text("👥 Çalışma grubu").font(.headline)
            if !store.profile.premium {
                Text("🔒 Grup kurma, ortak hedef ve ortak bahçe Premium'a özel. Mağaza sekmesinden açabilirsin.")
                    .font(.subheadline)
            } else if store.profile.groupName.isEmpty {
                Text("Arkadaşlarınla bir grup kur, haftalık ortak hedef belirle ve bahçelerinizi birleştirin.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("Grup adı (ör. TYT Savaşçıları)", text: $newGroupName)
                    .textFieldStyle(.roundedBorder)
                Text("Haftalık ortak hedef").font(.subheadline.weight(.semibold))
                ChipRow {
                    ForEach([300, 600, 1000, 1500, 2500], id: \.self) { g in
                        Chip(title: "\(g) dk", selected: newGroupGoal == g) { newGroupGoal = g }
                    }
                }
                Button {
                    store.createGroup(name: newGroupName, goal: newGroupGoal)
                } label: {
                    Text("Grubu kur").fontWeight(.semibold).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(store.theme.primary)
                .disabled(newGroupName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            } else {
                groupDetails
            }
        }
    }

    @ViewBuilder
    private var groupDetails: some View {
        let total = store.myWeekMin + store.friends.reduce(0) { $0 + $1.weekMin }
        let goal = max(store.profile.groupGoal, 1)
        HStack {
            Text(store.profile.groupName).font(.title3.bold())
            Spacer()
            Text("\(store.friends.count + 1) üye").font(.caption).foregroundStyle(.secondary)
        }
        Text("Haftalık hedef: \(total) / \(goal) dk")
            .font(.subheadline)
        ProgressView(value: Double(min(total, goal)), total: Double(goal))
            .tint(store.theme.primary)
        if total >= goal {
            Text("🎉 Hedefe ulaştınız! Harika takım çalışması.")
                .font(.subheadline.weight(.semibold))
                .foregroundColor(store.theme.primary)
        }

        Text("🌳 Ortak bahçe").font(.subheadline.weight(.semibold)).padding(.top, 6)
        let myGarden = store.sessions.filter { $0.success }.prefix(12).map { $0.plant.grown }
        let all = Array(myGarden) + store.friends.flatMap { $0.garden }
        if all.isEmpty {
            Text("Henüz bitki yok. İlk seansı tamamlayan kazanır! 🌱")
                .font(.caption)
                .foregroundStyle(.secondary)
        } else {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 6) {
                ForEach(0..<all.count, id: \.self) { i in
                    Text(all[i])
                        .font(.system(size: 24))
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(Color.brown.opacity(0.2))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        Button("Gruptan çık", role: .destructive) { confirmLeave = true }
            .font(.footnote)
            .padding(.top, 4)
    }
}
