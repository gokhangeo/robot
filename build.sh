#!/bin/bash
# Builds robot-arkadas.apk with the Android SDK command-line tools (no Gradle):
# aapt2 (resources) -> javac -> d8 (dex) -> zipalign -> apksigner.
# Runs on GitHub Actions (.github/workflows/build-apk.yml); locally it needs JDK 17 and an Android SDK
# with platforms;android-34 and build-tools;34.0.0. Signing: KEYSTORE=path/to/release.p12 KEYSTORE_PASSWORD=... ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
BT=${BUILD_TOOLS:-$SDK/build-tools/34.0.0}
SDK_JAR=${ANDROID_JAR:-$SDK/platforms/android-34/android.jar}
KS=${KEYSTORE:-release.p12}
B=build
rm -rf $B && mkdir -p $B/classes $B/res $B/dex $B/gen

# 1. resources -> R.java + resource APK (assets bundled as the offline fallback of the web UI)
"$BT/aapt2" compile --dir res -o $B/res/res.zip
"$BT/aapt2" link -I "$SDK_JAR" --manifest AndroidManifest.xml -A assets --java $B/gen -o $B/unsigned.apk $B/res/res.zip

# 2. Java -> .class -> classes.dex
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$SDK_JAR" -d $B/classes $(find src $B/gen -name '*.java')
"$BT/d8" --release --min-api 26 --lib "$SDK_JAR" --output $B/dex $(find $B/classes -name '*.class')
(cd $B/dex && zip -q -j ../unsigned.apk classes.dex)

# 3. align + sign (APK signature v2+v3; the key must stay the same for every future build, see README)
"$BT/zipalign" -f -p 4 $B/unsigned.apk $B/aligned.apk
if [ ! -f "$KS" ]; then echo "Keystore $KS not found (set KEYSTORE)"; exit 1; fi
"$BT/apksigner" sign --ks "$KS" --ks-type PKCS12 --ks-pass env:KEYSTORE_PASSWORD --ks-key-alias robot \
  --min-sdk-version 26 --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --out robot-arkadas.apk $B/aligned.apk
"$BT/apksigner" verify --print-certs robot-arkadas.apk
ls -la robot-arkadas.apk
