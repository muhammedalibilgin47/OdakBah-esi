import SwiftUI

/// Demo reklamlar. Markaların hepsi uydurmadır; gerçek sürümde yerine AdMob gelir.
struct FakeAd {
    let emoji: String
    let brand: String
    let text: String
    let cta: String
    let color: Color
}

enum FakeAds {
    static let all: [FakeAd] = [
        FakeAd(emoji: "📘", brand: "Tohum Yayınları", text: "Yeni TYT deneme seti çıktı! 40 deneme, video çözümlü.",
               cta: "İncele", color: Color(red: 0.13, green: 0.39, blue: 0.80)),
        FakeAd(emoji: "🥤", brand: "Fıstık Fizz", text: "Ders arasına buz gibi serinlik. Şimdi limon aromalı!",
               cta: "Keşfet", color: Color(red: 0.95, green: 0.55, blue: 0.10)),
        FakeAd(emoji: "🎧", brand: "SusPus Kulaklık", text: "Gürültüyü kapat, odağı aç. Öğrencilere %25 indirim.",
               cta: "Satın al", color: Color(red: 0.25, green: 0.25, blue: 0.30)),
        FakeAd(emoji: "🍕", brand: "Pizzakule", text: "Gece çalışanlara özel: 23:00'ten sonra ikinci pizza bizden.",
               cta: "Sipariş ver", color: Color(red: 0.80, green: 0.15, blue: 0.15)),
        FakeAd(emoji: "☕", brand: "Bulutkahve", text: "Sınav haftası kampanyası: tüm filtre kahveler 29 TL.",
               cta: "Haritada gör", color: Color(red: 0.45, green: 0.30, blue: 0.20)),
        FakeAd(emoji: "🧮", brand: "Soru Avcısı", text: "Günde 10 soruyla netlerini artır. İlk ay ücretsiz!",
               cta: "İndir", color: Color(red: 0.40, green: 0.20, blue: 0.65)),
    ]
}

/// Alt kısımdaki küçük banner reklam (8 saniyede bir değişir)
struct FakeBannerAd: View {
    @State private var showInfo = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 8)) { ctx in
            let ad = FakeAds.all[Int(ctx.date.timeIntervalSince1970 / 8) % FakeAds.all.count]
            Button { showInfo = true } label: {
                HStack(spacing: 10) {
                    Text(ad.emoji).font(.system(size: 26))
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 6) {
                            Text("Reklam")
                                .font(.system(size: 9, weight: .bold))
                                .padding(.horizontal, 4).padding(.vertical, 1)
                                .background(Color.yellow)
                                .foregroundColor(.black)
                                .clipShape(RoundedRectangle(cornerRadius: 3))
                            Text(ad.brand).font(.caption.bold()).foregroundColor(.white)
                        }
                        Text(ad.text).font(.caption2).foregroundColor(.white.opacity(0.9)).lineLimit(1)
                    }
                    Spacer(minLength: 4)
                    Text(ad.cta)
                        .font(.caption.bold())
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(Color.white)
                        .foregroundColor(ad.color)
                        .clipShape(Capsule())
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(ad.color)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, 12)
                .padding(.bottom, 6)
            }
            .buttonStyle(.plain)
        }
        .alert("Demo reklam", isPresented: $showInfo) {
            Button("Tamam", role: .cancel) { }
        } message: {
            Text("Bu bir deneme reklamıdır. Premium ile reklamları kaldırabilirsin.")
        }
    }
}

extension View {
    /// Premium değilse alt kısma banner reklam ekler.
    func adBanner(_ show: Bool) -> some View {
        safeAreaInset(edge: .bottom) {
            if show { FakeBannerAd() }
        }
    }
}

/// Seans sonrası tam ekran reklam (5 sn sonra kapatılabilir)
struct InterstitialAdView: View {
    let onClose: () -> Void
    @State private var remaining = 5
    @State private var ad = FakeAds.all.randomElement() ?? FakeAds.all[0]

    init(onClose: @escaping () -> Void) {
        self.onClose = onClose
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            LinearGradient(colors: [ad.color, ad.color.opacity(0.6), .black],
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            VStack(spacing: 18) {
                Spacer()
                Text(ad.emoji).font(.system(size: 110))
                Text(ad.brand).font(.largeTitle.bold()).foregroundColor(.white)
                Text(ad.text)
                    .font(.title3)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.white.opacity(0.9))
                    .padding(.horizontal, 30)
                Text(ad.cta)
                    .font(.headline)
                    .padding(.horizontal, 40).padding(.vertical, 14)
                    .background(Color.white)
                    .foregroundColor(ad.color)
                    .clipShape(Capsule())
                    .padding(.top, 10)
                Spacer()
                Text("Demo reklam · Premium ile reklamsız kullan")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.bottom, 20)
            }
            .frame(maxWidth: .infinity)

            Button {
                if remaining == 0 { onClose() }
            } label: {
                Group {
                    if remaining > 0 {
                        Text("\(remaining)").font(.headline.monospacedDigit())
                    } else {
                        Image(systemName: "xmark").font(.headline)
                    }
                }
                .foregroundColor(.white)
                .frame(width: 38, height: 38)
                .background(Color.black.opacity(0.5))
                .clipShape(Circle())
            }
            .padding(20)
        }
        .task {
            while remaining > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                remaining -= 1
            }
        }
    }
}
