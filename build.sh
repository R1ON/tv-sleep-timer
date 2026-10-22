#!/bin/bash
# Сборка APK без Gradle: aapt2 → javac → d8 → zipalign → apksigner
set -euo pipefail
cd "$(dirname "$0")"

SDK=/opt/homebrew/share/android-commandlinetools
BT=$SDK/build-tools/34.0.0
ANDROID_JAR=$SDK/platforms/android-34/android.jar
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

rm -rf build
mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 34 \
  --java build/gen -o build/unsigned.apk build/res.zip

javac -source 11 -target 11 -classpath "$ANDROID_JAR" -d build/classes \
  $(find src build/gen -name '*.java')
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)

"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

if [ ! -f release.keystore ]; then
  keytool -genkeypair -keystore release.keystore -storepass android -keypass android \
    -alias tvsleep -dname "CN=tvsleep" -keyalg RSA -keysize 2048 -validity 10000
fi
"$BT/apksigner" sign --ks release.keystore --ks-pass pass:android --out build/tvsleep.apk build/aligned.apk

echo "build/tvsleep.apk"
