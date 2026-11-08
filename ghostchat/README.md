# Ghostchat

Messenger Android (Android 8+), Kotlin + Jetpack Compose, backend Supabase + LiveKit.

Funzioni: registrazione con email (codice monouso) e tag, foto profilo, PIN 4/6/8 cifre,
chat 1:1 con archiviate, vocali, chiamate e videochiamate, dimensione del testo scelta per ogni
messaggio, nuvolette a destra/sinistra, temi personalizzati, lingue IT/EN/ES/FR/DE + lingue create dall'utente.

## 1. Supabase (gratis)
1. Crea un progetto su supabase.com.
2. SQL Editor → incolla ed esegui `supabase/schema.sql`.
3. Authentication → Email Templates → modifica **Magic Link** e **Confirm signup** in modo che contengano `{{ .Token }}`
   (es. "Il tuo codice è {{ .Token }}"), altrimenti arriva un link invece del codice.
4. Settings → API: copia **Project URL** e la chiave **anon public**.

## 2. LiveKit (gratis) per chiamate e videochiamate
1. Crea un progetto su livekit.io (Cloud). Copia URL (`wss://...`), API Key e API Secret.
2. Con la Supabase CLI:
   ```
   supabase link --project-ref <id>
   supabase secrets set LIVEKIT_URL=wss://... LIVEKIT_API_KEY=... LIVEKIT_API_SECRET=...
   supabase functions deploy livekit-token
   ```

## 3. GitHub → APK scaricabile
1. Crea un repository e carica questa cartella.
2. Crea la chiave di firma (una volta sola, conservala):
   ```
   keytool -genkeypair -v -keystore ghost.jks -alias ghost -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 ghost.jks
   ```
3. Repo → Settings → Secrets and variables → Actions, aggiungi:
   `SUPABASE_URL`, `SUPABASE_KEY`, `KEYSTORE_BASE64` (output di base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (ghost), `KEY_PASSWORD`.
4. Crea una release: `git tag v1.0.0 && git push --tags`.
   L'APK compare in Releases come `Ghostchat.apk` (oppure Actions → Build APK → Run workflow per un APK negli artifact).

## Build locale
Aggiungi `SUPABASE_URL` e `SUPABASE_KEY` in `~/.gradle/gradle.properties`, apri in Android Studio e premi Run.
