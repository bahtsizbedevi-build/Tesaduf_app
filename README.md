# TESADÜF

Anonim, rastgele 15 dakikalık sohbet uygulaması. Android (Kotlin + Jetpack Compose) + Supabase.

## Klasör yapısı

```
app/src/main/java/com/tesaduf/app/
  model/        Sunucu veri modelleri (Profile, Match, ChatMessage …)
  network/      HTTP, hata eşleme (AppError), sunucu saati (ServerClock)
  data/         Anonim oturum (SessionManager), Edge Function istemcisi (TesadufApi),
                Realtime istemcisi, ağ durumu
  repository/   UI'ın tek giriş noktası (TesadufRepository)
  navigation/   Ekran akışı
  ui/           theme, components, splash, home, matchmaking, chat, history
supabase/
  migrations/   Veritabanı şeması + RLS + iş mantığı (SQL)
  functions/    11 Edge Function (bootstrap, matchmaker, match-status, destiny-decision,
                end-match, heartbeat, messages, send-message, my-chats, block-user, report-user)
  tests/        Veritabanı akış testi (PGlite)
store/          Play Store ikonu (512×512)
```

## Supabase kurulumu (bir kez)

1. **Anonim girişi aç:** Dashboard → Authentication → Sign In / Providers →
   *Allow anonymous sign-ins* → açık.
2. **Veritabanı:** Dashboard → SQL Editor → `supabase/migrations/20261008000000_tesaduf_init.sql`
   dosyasının tamamını yapıştır → Run.
   (ya da CLI: `npx supabase link --project-ref <ref>` ve `npx supabase db push`)
3. **Edge Functions:** `npx supabase login` → `npx supabase functions deploy --project-ref <ref>`
   (`supabase/config.toml` içindeki `verify_jwt = false` ayarları da birlikte yüklenir.
   Kimlik doğrulaması her fonksiyonda kullanıcı JWT'si ile veritabanında yapılır.)
4. **Android anahtarı:** Proje kökünde `supabase.properties` oluştur (git'e girmez):

   ```
   SUPABASE_URL=https://<ref>.supabase.co
   SUPABASE_ANON_KEY=<anon / publishable key>
   ```

   ⚠️ Buraya **asla** `service_role` / `sb_secret_` anahtarı koyma. Build bunu reddeder.

## Build (Android Studio gerekmez)

Bilgisayardaki Unity'nin JDK 17 + Gradle 9.3.1 + Android SDK'sı kullanılır:

```
gradlew-unity.cmd assembleDebug        # APK: app\build\outputs\apk\debug\app-debug.apk
gradlew-unity.cmd testDebugUnitTest    # unit testler
gradlew-unity.cmd lintDebug            # lint
```

Windows kullanıcı klasöründe Türkçe karakter (Ö, ç, ü) olduğu için betik, build'i ASCII bir
junction (`C:\Users\Public\TesadufBuild`, proje klasörünün kısayolu) ve
`C:\Users\Public\gradle-home` üzerinden çalıştırır. Proje taşınmaz.

## Veritabanı testi

```
npm i @electric-sql/pglite@0.3.7
node supabase/tests/pglite_flow_test.mjs
```

## Güvenlik özeti

- İstemciler tablolara **yazamaz**. Tüm değişiklikler `auth.uid()` kontrol eden SECURITY DEFINER
  fonksiyonlarla yapılır.
- RLS: kullanıcı yalnızca kendi profilini, kendi eşleşmelerini ve yalnızca açık
  (aktif / karar / destiny) eşleşmelerinin mesajlarını okuyabilir.
- Karşı tarafın gerçek kullanıcı kimliği (uuid) API ile hiç gönderilmez, sadece anonim ID ve avatar gider.
- 15 dakika ve karar süresi sunucuda belirlenir. Telefon saati değiştirilse de etkisi olmaz.
- Destiny kararı satır kilidiyle atomiktir. Eşleştirme, advisory lock ile tek sıraya alınır.
