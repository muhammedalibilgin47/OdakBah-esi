# 🌱 Odak Bahçesi — iPhone (Sideloadly ile kurulum)

Mac'in yoksa sorun değil: .ipa dosyasını **GitHub'ın ücretsiz Mac sunucuları** senin için derler.
Sonra Windows (veya Mac) bilgisayarda **Sideloadly** ile iPhone'a kurarsın.

---

## 1) .ipa dosyasını GitHub'da oluştur (bir kere, ~10 dakika)

1. https://github.com adresinde ücretsiz hesap aç ve giriş yap.
2. Sağ üstte **+ → New repository**. İsim: `OdakBahcesi`. **Public** seç (Public depolarda derleme tamamen ücretsiz). **Create repository**.
3. Açılan sayfada **"uploading an existing file"** bağlantısına tıkla.
4. Zip'ten çıkardığın `OdakBahcesi-iOS` klasörünün **içindekileri** sürükle bırak:
   `OdakBahcesi` klasörü, `project.yml`, `README.md`. Alttan **Commit changes**.
5. Derleme dosyası gizli klasörde olduğu için ayrıca ekle:
   **Add file → Create new file** → dosya adı kutusuna tam olarak
   `.github/workflows/build-ipa.yml` yaz → zip içindeki aynı dosyanın içeriğini kopyalayıp yapıştır → **Commit changes**.
6. Üstteki **Actions** sekmesine geç. "iOS IPA oluştur" otomatik başlar (başlamadıysa soldan seçip **Run workflow**).
7. 5–10 dakika sonra yeşil ✅ olur. Ona tıkla, en altta **Artifacts → OdakBahcesi-ipa**'yı indir.
   İnen zip'i aç, içinden **OdakBahcesi.ipa** çıkar.

> Kırmızı ❌ olursa: tıkla, hatalı adımı aç, ekran görüntüsünü Claude'a gönder.

## 2) Sideloadly ile iPhone'a kur

1. Bilgisayara **iTunes** (Windows için, Microsoft Store değil Apple sitesinden olanı önerilir) ve **Sideloadly** (sideloadly.io) kur.
2. iPhone'u USB ile bağla, telefonda **"Bu bilgisayara güven"** de.
3. Sideloadly'de: **OdakBahcesi.ipa**'yı pencereye sürükle → Apple ID'ni yaz → **Start**.
4. iPhone'da: **Ayarlar → Genel → VPN ve Cihaz Yönetimi** → Apple ID'ne dokun → **Güven**.
5. iOS 16+: **Ayarlar → Gizlilik ve Güvenlik → Geliştirici Modu**'nu aç (telefon yeniden başlar).
6. Uygulamayı aç, bildirim iznine **İzin Ver** de. 🎉

> **Önemli:** Ücretsiz Apple ID ile kurulan uygulamalar **7 günde bir** süresi dolar.
> Sideloadly'de "Auto refresh" açarsan, telefon ve bilgisayar aynı Wi‑Fi'deyken otomatik yenilenir.
> Yenilemede verilerin (bahçe, altın) silinmez.

---

## Özellikler

- ⏱️ **Odak:** Ders + süre (10–90 dk) + bitki seç. Halka dolarken bitki 🌰 → 🌱 → 🌿 → 🌳 büyür.
- 🔒 **Dikkat koruması:** Başka uygulamaya geçersen uyarı bildirimi gelir, 10 sn içinde dönmezsen bitki 🥀 solar. Ekranı kilitlemek serbest. Odak sırasında ekran kendiliğinden kapanmaz.
- 🌳 **Bahçe:** Her seans bir bitki; yarıda kalanlar solmuş görünür.
- 📊 **İstatistik:** Bugün, seri, toplam saat, başarı oranı, son 7 gün grafiği. Ders dağılımı Premium.
- 🛍️ **Mağaza:** 10 bitki, 5 tema, ödüllü reklam (demo), Premium (demo), mola süresi.
- 🪙 Her dakika = 1 altın + 10 XP; 300 XP'de seviye atla.

## Proje yapısı

```
project.yml                  Xcode projesi tarifi (XcodeGen)
.github/workflows/           GitHub'da .ipa üreten otomatik derleme
OdakBahcesi/
├── OdakBahcesiApp.swift     Giriş noktası, sekmeler, ortak bileşenler
├── AppStore.swift           Sayaç, ödüller, solma kuralı, bildirimler, kayıt
├── Models.swift             Bitkiler, temalar, profil, seans, istatistik, mesajlar
├── FocusView.swift          Odak ekranı
├── GardenView.swift         Bahçe ekranı
├── StatsView.swift          İstatistik ekranı
├── ShopView.swift           Mağaza ekranı
└── Assets.xcassets          Uygulama simgesi
```

Mac'in varsa: `brew install xcodegen && xcodegen generate`, sonra `OdakBahcesi.xcodeproj`'yi Xcode'da aç ve telefonuna doğrudan çalıştır (Signing kısmında kendi Apple ID'ni seç).

## v1.1 yenilikleri

- 👥 **Arkadaşlar sekmesi:** profil kodunu paylaş, arkadaşının kodunu ekle, haftalık sıralama (🥇🥈🥉). Ücretsiz 3 arkadaş, Premium sınırsız.
- 🌳 **Çalışma grubu (Premium):** grup adı, haftalık ortak hedef, herkesin bitkilerinden oluşan ortak bahçe.
- 📺 **Demo reklamlar:** alt banner, her 2 seansta bir tam ekran reklam, ödüllü reklam. Premium'da gizlenir. Markalar uydurmadır.
- 🔓 **Kilit ekranı + Dynamic Island:** seans başlayınca canlı sayaç görünür.
- 🎛️ **Denetim Merkezi (iOS 18+):** Ayarlar değil, Denetim Merkezi'ni aç → sol üst **+** → **Denetim ekle** → "Odak Başlat".
- 🔒 **Katı mod** anahtarı: kapatırsan diğer uygulamaları kullanırken bitki solmaz, sayaç Dynamic Island'da kalır.

> Arkadaş verileri şimdilik sunucusuz, profil kodlarıyla aktarılıyor. Canlı senkronizasyon için ileride Firebase eklenebilir.
