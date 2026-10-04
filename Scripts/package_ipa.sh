#!/bin/bash
set -euo pipefail

# Produce an unencrypted device IPA from our own source, without distribution signing.
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"
release_version="${1:-1.0.0}"
if [[ ! "$release_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Usage: $0 <major.minor.patch> [--skip-build]" >&2
    exit 1
fi
release_dir="$project_root/Build/Releases/v$release_version"
ios_archive="$project_root/Build/Archives/Jianzuo-iOS.xcarchive"
mkdir -p "$release_dir"
if [[ "${2:-}" != "--skip-build" ]]; then
    xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo -configuration Release \
        -sdk iphoneos -destination 'generic/platform=iOS' \
        -archivePath "$ios_archive" ARCHS=arm64 ONLY_ACTIVE_ARCH=NO \
        CODE_SIGNING_ALLOWED=NO archive
fi
ios_app="$ios_archive/Products/Applications/Jianzuo.app"
lipo "$ios_app/Jianzuo" -verify_arch arm64
if [[ "$(/usr/libexec/PlistBuddy -c 'Print :DTPlatformName' "$ios_app/Info.plist")" != "iphoneos" ]]; then
    echo 'Expected an iPhoneOS device build, not a simulator build.' >&2
    exit 1
fi
if codesign -d "$ios_app" >/dev/null 2>&1; then
    echo 'Expected an unsigned device archive.' >&2
    exit 1
fi
if otool -l "$ios_app/Jianzuo" | awk '/cryptid/ && $2 != 0 { encrypted = 1 } END { exit !encrypted }'; then
    echo 'Encrypted executables cannot be packaged by this script.' >&2
    exit 1
fi
staging_dir="$(mktemp -d "$project_root/Build/ipa-stage.XXXXXX")"
trap 'rm -rf "$staging_dir"' EXIT
mkdir -p "$staging_dir/Payload"
ditto --norsrc --noextattr "$ios_app" "$staging_dir/Payload/Jianzuo.app"
ipa_name="Jianzuo-$release_version-iOS-unsigned.ipa"
(
    cd "$staging_dir"
    zip -q -r -X "$staging_dir/$ipa_name" Payload
)
mv "$staging_dir/$ipa_name" "$release_dir/$ipa_name"
(
    cd "$release_dir"
    shasum -a 256 "$ipa_name" > "$ipa_name.sha256"
)
echo "Unsigned IPA: $release_dir/$ipa_name"
