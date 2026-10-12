#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
"$project_root/Android/gradlew" -p "$project_root/Android" assembleDebug testDebugUnitTest lintDebug "$@"
output_directory="$project_root/Build/Android"
mkdir -p "$output_directory"
cp "$project_root/Android/app/build/outputs/apk/debug/app-debug.apk" "$output_directory/jianzuo-android-debug.apk"
cd "$output_directory"
if command -v shasum >/dev/null; then
    shasum -a 256 jianzuo-android-debug.apk > SHA256SUMS.txt
else
    sha256sum jianzuo-android-debug.apk > SHA256SUMS.txt
fi
printf 'APK: %s/jianzuo-android-debug.apk\n' "$output_directory"
