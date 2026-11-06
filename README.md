# Robot Arkadaşım (Pamuk)

Android app (package `com.robot.arkadas`): a talking robot friend for a child. The UI is a web page
(`assets/index.html` + `robot.js`, `alive.js`, `sfx.js`, `bubble.html`, `player.html`) shown in a WebView;
Java (`src/com/robot/arkadas/`) provides the microphone, voices, YouTube/Spotify panel, floating robot,
notifications and the Claude API calls.

## Two kinds of updates

1. **Web updates (no reinstall).** Change files in `assets/`, raise `"version"` in `assets/version.json`,
   push to branch `robot`. Phones download them from
   `https://raw.githubusercontent.com/gokhangeo/robot/robot/assets/` (the old `gokhangeo/trend` URL baked
   into older APKs redirects here). `"minApp"` is the lowest APK `versionCode` allowed to take that web
   version: web code that calls new Java bridge methods must set it to the APK version that has them.
   Current: web 33, `minApp` 10 (needs the v3.0 APK). Web 32 (`minApp` 6) was the last one for old APKs.
2. **APK updates.** Change Java/resources/manifest, raise `android:versionCode` (and `versionName`) in
   `AndroidManifest.xml`, push to `robot`.

## Building the APK (GitHub Actions)

`.github/workflows/build-apk.yml` runs on every push to `robot` that touches `src/`, `res/`,
`AndroidManifest.xml`, `build.sh`, `tools/`, `assets/` (so the bundled offline copy of the web UI stays current) or the workflow (it can also be started by hand from the
Actions tab). It:

1. installs `platforms;android-34` and `build-tools;34.0.0` on the runner,
2. runs `tools/EdgeTtsTest.java` (checks the Edge voice token against edge-tts and does a live synthesis),
3. runs `build.sh` (aapt2 → javac → d8 → zipalign → apksigner, no Gradle),
4. uploads the APK as a run artifact and commits it to **`apk/robot-arkadas.apk`**.

Download link for the phone:
`https://github.com/gokhangeo/robot/raw/robot/apk/robot-arkadas.apk`

Local build: JDK 17 + Android SDK with the two packages above, then
`KEYSTORE=/path/release.p12 KEYSTORE_PASSWORD=... ./build.sh`.

## Signing key (keep it!)

Android only installs an update over an existing app when it is signed with the same key. The original
key was lost in 2026, so v3.0 (versionCode 10) was signed with a **new key** and had to be installed
once after uninstalling the old app.

- The new keystore (PKCS12, alias `robot`, certificate SHA-256
  `578b50d341cbb6227550ee5d03dd7e981cd124034cf76872d3f8a79efa82211e`) is stored as encrypted
  repository **Actions secrets**: `KEYSTORE_B64` (base64 of the .p12) and `KEYSTORE_PASSWORD`.
  Secrets cannot be read back from GitHub, so a copy of the keystore and password is also kept
  outside the repo by the owner. The repository is public: never commit the keystore.
- If the secrets are ever deleted, restore them from that copy (Settings → Secrets and variables →
  Actions). A different key means every phone must uninstall and reinstall again.

## Features added in v3.0 (APK 10, web 33)

- **Backup / restore:** Parent settings → Yedek → "Yedeği kopyala" (web 32 already has this for old
  APKs) and "Yedeği yapıştır" → "Bu yedeği geri yükle".
- **Free natural voice:** Microsoft Edge read-aloud neural voices (`EdgeTts.java`, `SecMsGec.java`:
  the same WebSocket protocol and Sec-MS-GEC token as the open-source edge-tts project), voices
  `tr-TR-EmelNeural` (default) and `tr-TR-AhmetNeural`, with speed/pitch sliders. The next sentence
  is synthesized while the current one plays. Falls back to the phone's TTS on any error.
  ElevenLabs / Google stay available; if they fail, Edge is used before the phone voice.
  If Microsoft changes the token, update the constants from edge-tts `src/edge_tts/constants.py` and
  `drm.py` and the test vectors in `tools/EdgeTtsTest.java`.
- **Hearing:** `Hearing.java` records with `VOICE_COMMUNICATION` + NoiseSuppressor / AcousticEchoCanceler /
  AutomaticGainControl. With a key in settings it is the primary ear (priority Deepgram Nova-3 →
  Groq Whisper `whisper-large-v3-turbo` → Google Gemini, default model `gemini-3.5-flash-lite`):
  local energy VAD, 16 kHz mono WAV, REST transcription, back through `onHeard`. Without a key,
  Android 13+ feeds the cleaned audio to SpeechRecognizer via `EXTRA_AUDIO_SOURCE` (automatic fallback
  to its own microphone if the recognizer rejects it), plus `EXTRA_BIASING_STRINGS` and 3 results.
- **Push-to-talk:** "Basılı tut, konuş" (hold the robot or the 🎤 button). Default when "TV açıkken" is on;
  no automatic listening in this mode.
- **YouTube / Spotify inside the app:** model tags `[YOUTUBE: …]` / `[SPOTIFY: …]`, a fuzzy matcher for
  misheard commands ("ağaç aç" → Spotify), and 🎵 Müzik / ▶️ Video buttons. `MediaSearch.java` searches
  YouTube's results page without a key (Restricted Mode cookie) and Spotify through optional
  Client ID/Secret or a keyless web search + oEmbed. The panel (`player.html`) is loaded with
  `loadDataWithBaseURL("https://gokhangeo.github.io/pamuk/", …)` so YouTube gets a Referer
  (avoids embed errors 152/153). Spotify login opens accounts.spotify.com in the panel's WebView.
