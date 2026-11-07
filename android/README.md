# 🌱 Odak Bahçesi — Android

Sınava hazırlanan öğrenciler için Pomodoro + oyunlaştırma tabanlı odaklanma uygulaması.
Ders çalıştıkça bitkin büyür, uygulamadan çıkarsan solar. Her seans bahçene bir bitki ekler.

## Telefona nasıl kurulur?

1. **Android Studio**'yu indir ve kur (ücretsiz): https://developer.android.com/studio
2. Bu zip'i bir klasöre çıkar.
3. Android Studio → **Open** → `OdakBahcesi` klasörünü seç.
4. İlk açılışta Gradle gerekli kütüphaneleri indirir (birkaç dakika sürebilir, internet gerekir).
5. Telefonda **Geliştirici seçenekleri → USB hata ayıklama**'yı aç, telefonu USB ile bağla.
   *(Geliştirici seçenekleri: Ayarlar → Telefon hakkında → "Yapım numarası"na 7 kez dokun.)*
6. Üstteki yeşil ▶ **Run** düğmesine bas. Uygulama telefona kurulur ve açılır.

**APK dosyası almak için:** Build → Build App Bundle(s) / APK(s) → Build APK(s).
Dosya `app/build/outputs/apk/debug/app-debug.apk` konumunda oluşur; telefona atıp kurabilirsin.

> Gereksinim: Android 8.0 (API 26) ve üzeri.

## Özellikler

| Ekran | Ne yapar |
|---|---|
| ⏱️ **Odak** | Ders seç, süre seç (10–90 dk), bitki seç ve başla. Halka dolarken bitki 🌰 → 🌱 → 🌿 → 🌳 diye büyür. Bittiğinde mola önerir. |
| 🌳 **Bahçe** | Tamamlanan her seans bir bitki, yarıda kalanlar 🥀 olarak bahçede görünür. |
| 📊 **İstatistik** | Bugün, seri (art arda gün), toplam saat, başarı oranı, son 7 gün grafiği, son seanslar. Ders bazlı dağılım Premium. |
| 🛍️ **Mağaza** | Altınla yeni bitkiler aç, temalar, ödüllü reklam, Premium, mola süresi ayarı. |

### Dikkat dağıtıcı koruması
- Odak sırasında **başka bir uygulamaya geçersen** "Bitkin solmak üzere!" bildirimi gelir.
  **10 saniye** içinde dönmezsen bitki solar ve seans başarısız sayılır.
- **Ekranı kilitlemek serbesttir**, ceza yok.
- Geri tuşu odak sırasında uygulamayı kapatmaz; ekran açık kalır.
- Uygulama kapansa bile sayaç kaldığı yerden devam eder; süre dolunca bildirim gelir.

### Oyunlaştırma
- Her odak dakikası = **1 altın + 10 XP**. 45+ dakikalık seanslara +10 altın bonus.
- Her 300 XP'de **seviye atlarsın**: Tohum → Filiz Yetiştirici → Bahçıvan → Orman Bekçisi → Odak Ustası.
- 10 farklı bitki (2'si ücretsiz, diğerleri 40–500 altın).

## Gelir modeli (uygulamada nerede?)

| Model | Şu anki durum | Gerçek sürümde |
|---|---|---|
| Premium abonelik (temalar, detaylı istatistik, arkadaş grupları) | Mağaza'da **demo** düğmesiyle açılıp kapanıyor | Google Play Billing Library ile aylık abonelik |
| Ödüllü reklam (+15 altın) | 5 sn'lik **sahte** reklam penceresi | Google AdMob Rewarded Ads |

## Proje yapısı

```
app/src/main/java/com/tiyzi/odakbahcesi/
├── MainActivity.kt          Uygulamaya giriş, uygulamadan çıkış/dönüş takibi
├── FocusViewModel.kt        Sayaç mantığı, ödüller, solma kuralları, mağaza işlemleri
├── data/
│   ├── Database.kt          Room veritabanı (Session tablosu + DAO)
│   ├── Prefs.kt             Profil (altın, XP, sahip olunan bitkiler, ayarlar) + sayaç durumu
│   ├── Plants.kt            Bitki türleri, büyüme aşamaları, fiyatlar, dersler
│   ├── Stats.kt             Seri, haftalık grafik, ders dağılımı hesapları
│   └── Messages.kt          Motivasyon ve bildirim metinleri
├── notify/Notifier.kt       Bildirimler + seans bitiş alarmı
└── ui/                      Jetpack Compose ekranları ve temalar
```

### Veritabanı şeması (`sessions` tablosu)

| Alan | Tip | Açıklama |
|---|---|---|
| id | Long (PK) | Otomatik |
| startAt / endAt | Long | Başlangıç / bitiş zamanı (epoch ms) |
| minutes | Int | Çalışılan dakika |
| subject | String | Ders (Matematik, Fizik…) |
| plant | String | Bitki türü |
| success | Boolean | Tamamlandı mı, soldu mu |

## Sonraki adımlar (yol haritası)

1. **AdMob** ve **Google Play Billing** entegrasyonu (demo düğmelerin yerine).
2. **Arkadaş grupları:** Firebase Auth + Firestore ile grup kurma, ortak bahçe, haftalık liderlik tablosu.
3. **Daha güçlü engelleme:** `UsageStatsManager` ile hangi uygulamaya geçildiğini görmek, izin verilen uygulamalar listesi (ör. sözlük, hesap makinesi).
4. **Günlük hatırlatıcı:** WorkManager ile "Bugün henüz çalışmadın 🌱" bildirimi.
5. Gerçek bitki illüstrasyonları ve büyüme animasyonları (Lottie).
6. Ana ekran widget'ı: bugünkü dakika ve seri.

## Teknolojiler
Kotlin 2.0 · Jetpack Compose (Material 3) · Room · ViewModel + StateFlow · AlarmManager · minSdk 26 / targetSdk 35
