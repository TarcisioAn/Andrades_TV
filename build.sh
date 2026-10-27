#!/usr/bin/env bash
# Compila o AndradesTV.apk sem Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Precisa de um JDK (17+) e do Android SDK (build-tools 35+, platform android-34). Ver README.md.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [ -d "$d/build-tools" ]; then SDK="$d"; break; fi
  done
fi
[ -n "$SDK" ] && [ -d "$SDK/build-tools" ] || { echo "Android SDK não encontrado. Defina ANDROID_HOME (ver README)."; exit 1; }
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AJ="$(ls -d "$SDK"/platforms/android-*/android.jar | sort -V | tail -1)"

rm -rf out && mkdir -p out/gen out/classes

"$BT/aapt2" compile --dir app/res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$AJ" --manifest app/AndroidManifest.xml --java out/gen \
  --min-sdk-version 26 --target-sdk-version 34 out/res.zip

javac -nowarn -Xlint:-options --release 11 -XDstringConcat=inline -classpath "$AJ" -d out/classes \
  $(find app/src out/gen -name '*.java')
"$BT/d8" --release --min-api 26 --lib "$AJ" --output out $(find out/classes -name '*.class')

(cd out && zip -q base.apk classes.dex)
"$BT/zipalign" -f -p 4 out/base.apk out/aligned.apk

# A chave fica só neste computador (fora do git). Guarde-a: atualizar o app na TV exige a mesma chave.
if [ ! -f andradestv.keystore ]; then
  keytool -genkeypair -keystore andradestv.keystore -alias andradestv -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass andradestv -keypass andradestv -dname "CN=Andrades TV" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks andradestv.keystore --ks-pass pass:andradestv --key-pass pass:andradestv \
  --out AndradesTV.apk out/aligned.apk

echo "Pronto: $(pwd)/AndradesTV.apk"
