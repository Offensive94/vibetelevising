#!/usr/bin/env bash
set -euo pipefail

RP_PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
RP_SDK_ROOT="${RP_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
if [[ -n "$RP_SDK_ROOT" ]]; then
  RP_BUILD_TOOLS="${RP_BUILD_TOOLS:-$RP_SDK_ROOT/build-tools/35.0.0}"
  RP_PLATFORM="${RP_PLATFORM:-$RP_SDK_ROOT/platforms/android-28/android.jar}"
else
  RP_TOOL_CACHE="${RP_TOOL_CACHE:-/home/joost/.cache/android-sdk-minimal}"
  RP_BUILD_TOOLS="${RP_BUILD_TOOLS:-$RP_TOOL_CACHE/android-15}"
  RP_PLATFORM="${RP_PLATFORM:-$RP_TOOL_CACHE/android-9/android.jar}"
fi
RP_JAVA_BIN="${RP_JAVA_BIN:-${JAVA_HOME:+$JAVA_HOME/bin}}"
if [[ -z "$RP_JAVA_BIN" ]]; then
  if command -v javac >/dev/null 2>&1; then
    RP_JAVA_BIN="$(dirname "$(command -v javac)")"
  else
    RP_JAVA_BIN="/opt/pycharm-2025.2/jbr/bin"
  fi
fi
RP_BUILD_DIR="$RP_PROJECT_DIR/build"

if [[ ! -f "$RP_PLATFORM" || ! -x "$RP_BUILD_TOOLS/aapt2" || ! -x "$RP_JAVA_BIN/javac" ]]; then
  echo "Android SDK or JDK files are missing. See README.md." >&2
  exit 1
fi

mkdir -p "$RP_BUILD_DIR/classes" "$RP_BUILD_DIR/dex" "$RP_BUILD_DIR/keys" "$RP_BUILD_DIR/res"
"$RP_JAVA_BIN/javac" -encoding UTF-8 -source 8 -target 8 \
  -classpath "$RP_PLATFORM" -d "$RP_BUILD_DIR/classes" \
  "$RP_PROJECT_DIR/src/com/redpanel/tv/MainActivity.java"

"$RP_JAVA_BIN/java" -cp "$RP_BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 \
  --min-api 28 --lib "$RP_PLATFORM" --output "$RP_BUILD_DIR/dex" \
  "$RP_BUILD_DIR"/classes/com/redpanel/tv/MainActivity*.class

"$RP_BUILD_TOOLS/aapt2" compile -o "$RP_BUILD_DIR/res" \
  "$RP_PROJECT_DIR/res/drawable/ic_launcher.xml"
"$RP_BUILD_TOOLS/aapt2" link -o "$RP_BUILD_DIR/unsigned.apk" \
  --manifest "$RP_PROJECT_DIR/AndroidManifest.xml" \
  -A "$RP_PROJECT_DIR/assets" \
  -I "$RP_PLATFORM" -R "$RP_BUILD_DIR/res/drawable_ic_launcher.xml.flat" \
  --min-sdk-version 28 --target-sdk-version 28
python3 -c 'import sys, zipfile; archive = zipfile.ZipFile(sys.argv[1], "a"); archive.write(sys.argv[2], "classes.dex"); archive.close()' \
  "$RP_BUILD_DIR/unsigned.apk" "$RP_BUILD_DIR/dex/classes.dex"
"$RP_BUILD_TOOLS/zipalign" -f -p 4 \
  "$RP_BUILD_DIR/unsigned.apk" "$RP_BUILD_DIR/aligned.apk"

RP_KEYSTORE="$RP_BUILD_DIR/keys/red-panel.jks"
if [[ ! -f "$RP_KEYSTORE" ]]; then
  echo "Creating a new signing key. It cannot update an APK signed with the old key." >&2
  "$RP_JAVA_BIN/keytool" -genkeypair -keystore "$RP_KEYSTORE" \
    -storepass android -keypass android -alias redpanel -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Red Panel,O=Local,C=RU" >/dev/null
fi
"$RP_JAVA_BIN/java" -jar "$RP_BUILD_TOOLS/lib/apksigner.jar" sign \
  --ks "$RP_KEYSTORE" --ks-key-alias redpanel \
  --ks-pass pass:android --key-pass pass:android \
  --out "$RP_BUILD_DIR/red-panel.apk" "$RP_BUILD_DIR/aligned.apk"
"$RP_JAVA_BIN/java" -jar "$RP_BUILD_TOOLS/lib/apksigner.jar" verify \
  "$RP_BUILD_DIR/red-panel.apk"
echo "$RP_BUILD_DIR/red-panel.apk"
