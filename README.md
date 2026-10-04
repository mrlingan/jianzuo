# 简作

Jianzuo — a quiet, native SwiftUI writing app for macOS, iPhone, and iPad.

用 SwiftUI 编写的原生写作 App，同一份 Xcode 工程支持 Mac、iPhone 和 iPad。界面采用暖色纸张、宋体正文和安静的三栏布局，手机使用原生分层导航。

## 在 Xcode 运行

1. 打开 `Jianzuo.xcodeproj`，选择 `Jianzuo` Scheme。
2. 运行设备选择 `My Mac`、iPhone 模拟器或 iPad 模拟器，点击 Run（⌘R）。
3. 如需运行到真实 iPhone / iPad，在 Signing & Capabilities 中选择自己的开发团队，连接设备后运行。

最低系统版本：macOS 14、iOS / iPadOS 17。无第三方依赖。

## 下载发行版

前往 [GitHub Releases](https://github.com/mrlingan/jianzuo/releases) 下载 Mac DMG 或 ZIP。Mac 包包含 Apple Silicon 和 Intel 两种架构。

公开发行的 Mac 包使用 ad-hoc 签名，尚未进行 Apple 公证。iOS 模拟器包仅供开发者使用；另提供源码直接构建的未加密、未签名真机 IPA，安装前需要自行签名。真机运行也可以按上方 Xcode 步骤配置自己的开发团队。

详细发行说明见 [v1.0.0](Docs/Releases/v1.0.0.md)。

## 已实现

- 文稿、灵感、日记三个笔记本；搜索标题和全文。
- 自动保存到设备本地，写入采用原子替换；离开前台时立即保存。
- 原生 NSTextView / UITextView 编辑器，支持撤销与中文输入。
- Markdown 预览：标题、段落、粗体、斜体、链接、引用、列表、代码块、分隔线。
- 收藏、创建副本、移动笔记本、废纸篓、恢复和确认后彻底删除。
- UTF-8 Markdown / TXT 导入与导出。
- 中文字数、英文单词统计、预计阅读时间和每篇文稿字数目标。
- 专注模式、三种字体、字号 / 行距设置、浅色 / 深色外观。
- 首次启动的三篇示例文稿和应用图标。

## 数据

文稿库保存在当前设备的 Application Support/Jianzuo/library.json。Mac 沙盒运行时，位于应用自己的沙盒容器内。请通过 App 的导出功能备份重要文稿。

当前版本为本地存储，尚未接入 iCloud 同步。三个平台的数据独立，可通过系统文件导入、导出交换。Markdown 预览是轻量实现，不包含图片渲染、表格或 HTML；这些内容仍按原文保存和导出。

## 开发与验证

```sh
xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo \
  -destination 'platform=macOS' -derivedDataPath Build/Mac \
  CODE_SIGNING_ALLOWED=NO test

xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath Build/iOS CODE_SIGNING_ALLOWED=NO build
```

测试覆盖混合语言字数统计、Unicode 文稿存储往返、搜索 / 收藏、废纸篓恢复与删除、损坏文件保护，以及未闭合 Markdown 代码块。

`Scripts/create_project.py` 可重新生成 Xcode 工程与颜色资源；`Scripts/make_icon.swift` 是应用图标的原生绘图源代码。主要源代码位于 `Jianzuo/Models` 和 `Jianzuo/Views`。

运行 `bash Scripts/package_release.sh 1.0.0` 可构建并打包 Mac DMG / ZIP、iOS 模拟器 ZIP、未签名真机 IPA 以及 SHA-256 校验文件，输出到 `Build/Releases/v1.0.0`。仅构建真机 IPA 时，运行 `bash Scripts/package_ipa.sh 1.0.0`。

## 参与和许可

欢迎提交 Issue 和 Pull Request，开发约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。

本项目使用 [MIT License](LICENSE)，允许使用、修改和商业使用，需保留版权和许可声明。
