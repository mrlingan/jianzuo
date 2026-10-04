#!/bin/bash
set -euo pipefail

# Build and package a local, ad-hoc signed release. No signing credentials required.
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"
release_version="${1:-1.0.0}"
if [[ ! "$release_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Usage: $0 <major.minor.patch> [--skip-build]" >&2
    exit 1
fi
release_dir="$project_root/Build/Releases/v$release_version"
mac_archive="$project_root/Build/Archives/Jianzuo.xcarchive"
simulator_dir="$project_root/Build/Release-iOS"
mkdir -p "$release_dir"

if [[ "${2:-}" != "--skip-build" ]]; then
    xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo -configuration Release \
        -destination 'generic/platform=macOS' -archivePath "$mac_archive" \
        ARCHS='arm64 x86_64' ONLY_ACTIVE_ARCH=NO \
        CODE_SIGN_IDENTITY=- CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM= \
        ENABLE_HARDENED_RUNTIME=YES archive
    xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo -configuration Release \
        -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath "$simulator_dir" ARCHS='arm64 x86_64' \
        ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO build
fi

mac_app="$mac_archive/Products/Applications/Jianzuo.app"
simulator_app="$simulator_dir/Build/Products/Release-iphonesimulator/Jianzuo.app"
codesign --verify --deep --strict "$mac_app"
for release_arch in arm64 x86_64; do
    lipo "$mac_app/Contents/MacOS/Jianzuo" -verify_arch "$release_arch"
    lipo "$simulator_app/Jianzuo" -verify_arch "$release_arch"
done

# Use a fresh staging directory; preserve completed packages outside it.
staging_dir="$(mktemp -d "$project_root/Build/package-stage.XXXXXX")"
trap 'rm -rf "$staging_dir"' EXIT
ditto "$mac_app" "$staging_dir/简作.app"
cat > "$staging_dir/安装说明.txt" <<'NOTES'
简作 / Jianzuo

将「简作.app」拖入 Applications 文件夹即可安装。
最低 macOS 14，支持 Apple Silicon 和 Intel Mac。

此开源发行包使用 ad-hoc 签名，未使用 Developer ID 签名，也未经过 Apple 公证。
首次打开时，macOS 可能显示安全提示；请确认下载来源为官方仓库并按系统提示操作。

文稿保存在本机。当前版本不包含 iCloud 同步。
源代码与许可证：https://github.com/mrlingan/jianzuo
NOTES
ditto -c -k --sequesterRsrc --keepParent "$staging_dir/简作.app" \
    "$release_dir/Jianzuo-$release_version-macOS-universal.zip"
ln -s /Applications "$staging_dir/Applications"
hdiutil create -ov -volname "简作 $release_version" -srcfolder "$staging_dir" \
    -format UDZO "$release_dir/Jianzuo-$release_version-macOS-universal.dmg"
ditto -c -k --sequesterRsrc --keepParent "$simulator_app" \
    "$release_dir/Jianzuo-$release_version-iOS-Simulator.zip"
(
    cd "$release_dir"
    shasum -a 256 "Jianzuo-$release_version-macOS-universal.dmg" \
        "Jianzuo-$release_version-macOS-universal.zip" \
        "Jianzuo-$release_version-iOS-Simulator.zip" > SHA256SUMS.txt
)
echo "Release packages: $release_dir"
