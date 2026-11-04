#!/bin/bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}}"
TASK_JAVA="${LAB_JAVA_HOME:-${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}}"
export JAVA_HOME="$TASK_JAVA"
export PATH="$TASK_JAVA/bin:$PATH"
TASK_TOOLS="$TASK_SDK/build-tools/35.0.0"
TASK_ANDROID="$TASK_SDK/platforms/android-34/android.jar"
mkdir -p "$TASK_ROOT/keys" "$TASK_ROOT/outputs"
chmod 700 "$TASK_ROOT/keys"
for module in probe fixture; do
    task_build="$TASK_ROOT/build/$module"
    rm -rf "$task_build"
    mkdir -p "$task_build/classes" "$task_build/dex"
    "$TASK_TOOLS/aapt2" compile --dir "$TASK_ROOT/$module/res" -o "$task_build/resources.zip"
    "$TASK_TOOLS/aapt2" link -o "$task_build/base.apk" -I "$TASK_ANDROID" --manifest "$TASK_ROOT/$module/AndroidManifest.xml" --version-code 2 --version-name 1.1 "$task_build/resources.zip"
    find "$TASK_ROOT/$module/src" -name '*.java' > "$task_build/sources.txt"
    javac -source 8 -target 8 -Xlint:-options -bootclasspath "$TASK_ANDROID:$TASK_TOOLS/core-lambda-stubs.jar" -d "$task_build/classes" @"$task_build/sources.txt"
    jar cf "$task_build/classes.jar" -C "$task_build/classes" .
    "$TASK_TOOLS/d8" --min-api 34 --lib "$TASK_ANDROID" --output "$task_build/dex" "$task_build/classes.jar"
    cp "$task_build/base.apk" "$task_build/unsigned.apk"
    (cd "$task_build/dex" && zip -q "$task_build/unsigned.apk" classes.dex)
    "$TASK_TOOLS/zipalign" -f -p 4 "$task_build/unsigned.apk" "$task_build/aligned.apk"
    if [ ! -f "$TASK_ROOT/keys/$module.jks" ]; then
        keytool -genkeypair -keystore "$TASK_ROOT/keys/$module.jks" -storepass android -keypass android -alias lab -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=IVI Lab $module, O=Local testing" >/dev/null 2>&1
        chmod 600 "$TASK_ROOT/keys/$module.jks"
    fi
    "$TASK_TOOLS/apksigner" sign --ks "$TASK_ROOT/keys/$module.jks" --ks-key-alias lab --ks-pass pass:android --key-pass pass:android --out "$TASK_ROOT/outputs/ivi-lab-$module.apk" "$task_build/aligned.apk"
    "$TASK_TOOLS/apksigner" verify --verbose "$TASK_ROOT/outputs/ivi-lab-$module.apk"
done
(cd "$TASK_ROOT/outputs" && shasum -a 256 ivi-lab-probe.apk ivi-lab-fixture.apk > SHA256SUMS.txt)
