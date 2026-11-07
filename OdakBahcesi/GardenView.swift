import SwiftUI

struct GardenView: View {
    @EnvironmentObject var store: AppStore
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)

    var body: some View {
        let grown = store.sessions.filter { $0.success }.count
        let withered = store.sessions.count - grown

        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Bahçem").font(.largeTitle.bold())
                Text("🌳 \(grown) büyüdü   ·   \(deadPlant) \(withered) soldu")
                    .foregroundStyle(.secondary)

                if store.sessions.isEmpty {
                    VStack(spacing: 12) {
                        Text("🌰").font(.system(size: 64))
                        Text("Bahçen henüz boş.\nİlk odak seansını tamamla, ilk bitkin burada filizlensin!")
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 60)
                } else {
                    LazyVGrid(columns: columns, spacing: 8) {
                        ForEach(Array(store.sessions.reversed())) { s in
                            VStack(spacing: 2) {
                                Text(s.success ? s.plant.grown : deadPlant)
                                    .font(.system(size: 28))
                                Text("\(s.minutes)dk")
                                    .font(.system(size: 10))
                                    .foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 66)
                            .background((s.success ? Color.brown : Color.gray).opacity(0.22))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                    }
                }
            }
            .padding()
        }
        .background(store.theme.background.ignoresSafeArea())
    }
}
