# 简作 · Jianzuo

**中文** · [English](README.en.md)

一个安静的原生写作空间。用 SwiftUI 编写，同一份 Xcode 工程支持 **Mac、iPhone 和 iPad**，无第三方依赖。

[下载发行版](https://github.com/mrlingan/jianzuo/releases) · [发行说明](Docs/Releases/v1.0.0.md) · [提交建议](https://github.com/mrlingan/jianzuo/issues) · [参与贡献](CONTRIBUTING.md)

## 界面截图

### Mac：笔记本、文稿列表与编辑器

左侧按笔记本或收藏浏览，中间搜索和切换文稿，右侧编辑正文。底部显示保存状态、字数和本文目标。

![Mac 三栏写作界面：笔记本、文稿列表、Markdown 编辑器与字数目标](Docs/Screenshots/mac-writing.jpg)

<details>
<summary>查看 Markdown 预览和专注模式</summary>

### Markdown 预览

点击工具栏眼睛图标，或按 `⇧⌘P`，将 Markdown 原文切换为标题、段落、列表和引用的排版预览。

![Mac Markdown 预览：标题层级、段落与引用](Docs/Screenshots/mac-preview.jpg)

### 专注模式

点击工具栏展开图标，或按 `⇧⌘F`，收起侧栏和文稿列表，将空间留给当前文稿。再次点击可退出；Mac 也可按 `Esc`。

![Mac 专注模式：隐藏侧栏和列表，居中显示当前文稿](Docs/Screenshots/mac-focus.jpg)

</details>

### iPhone / iPad：随屏幕调整的写作空间

iPhone 使用文稿列表与编辑页的分层导航；iPad 在较宽的窗口中并排显示列表与正文，侧栏可按需展开。

<table>
  <tr><th>iPhone · 正文编辑</th><th>iPad · 并排浏览与编辑</th></tr>
  <tr>
    <td align="center"><img src="Docs/Screenshots/iphone-writing.png" width="240" alt="iPhone 正文编辑，包含预览、收藏、导出和字数目标"></td>
    <td align="center"><img src="Docs/Screenshots/ipad-writing.png" width="620" alt="iPad 横屏界面，并排显示文稿列表和编辑器"></td>
  </tr>
</table>

截图来自 v1.0.0 的实际界面，使用项目自带的示例文稿；移动端截图来自 iPhone / iPad 模拟器。**App 界面目前为中文，README 提供中英文版本。**

## 功能与使用方法

| 功能 | 如何使用 |
| --- | --- |
| 原生写作 | 新建文稿后分别填写标题和正文。Mac 使用 NSTextView，iOS 使用 UITextView，支持中文输入、文本选择和原生撤销。 |
| 笔记本与搜索 | 用「文稿」「灵感」「日记」三个笔记本整理内容；搜索匹配标题和正文，列表按最近编辑时间排序。 |
| Markdown 预览 | 眼睛图标切换编辑 / 预览，支持标题、粗体、斜体、链接、引用、有序 / 无序列表、代码块和分隔线。 |
| 收藏与整理 | 星标收藏文稿；「更多」菜单支持创建副本、移动笔记本和移到废纸篓。Mac 列表也支持右键操作。 |
| 废纸篓 | 删除的文稿先保留在废纸篓，可恢复；彻底删除需要确认，执行后不可撤销。 |
| 导入与导出 | 列表「更多」菜单导入 UTF-8 Markdown / TXT；编辑器分享图标导出正文。Mac 可选 Markdown 或 TXT，iOS 导出 Markdown。 |
| 本地自动保存 | 停止编辑约 450 毫秒后保存，离开前台时立即保存。采用原子写入；读取失败时保留原文件并提示错误。 |
| 字数与目标 | 中文逐字、英文按单词统计；「更多 → 文稿统计」查看字符数和预计阅读时间。设置中可选每篇文稿目标或关闭目标。 |
| 写作设置 | 调整宋体 / 系统 / 等宽字体、14–26 号字号、行距，以及跟随系统 / 浅色 / 深色外观。 |
| 专注写作 | 收起导航栏位，保留当前文稿与保存状态；可与 Markdown 预览一起使用。 |

### 快速开始

1. 点击「开始新的一页」，输入标题和正文，内容会自动保存。
2. 用笔记本整理文稿，或点击星标收藏常用内容。
3. 点击眼睛图标检查排版；点击展开图标进入专注模式。
4. 写完后通过导出按钮保存一份 Markdown 文件，也可将它导入其他设备。

### Mac 快捷键

| 快捷键 | 操作 |
| --- | --- |
| `⌘N` | 新建文稿 |
| `⌘O` | 导入文稿 |
| `⌘S` | 立即保存 |
| `⇧⌘E` | 导出当前文稿 |
| `⇧⌘P` | 切换 Markdown 编辑 / 预览 |
| `⇧⌘F` | 切换专注模式 |
| `Esc` | 退出专注模式 |

## 下载与安装

前往 [GitHub Releases](https://github.com/mrlingan/jianzuo/releases) 获取安装包及校验文件。

| 下载类型 | 适用场景与要求 |
| --- | --- |
| Mac DMG / ZIP | macOS 14 起，包含 Apple Silicon（arm64）与 Intel（x86_64）。将「简作.app」移到 Applications。 |
| iOS 未签名 IPA | iOS / iPadOS 17 起，arm64 真机包，源码直接构建、未加密、未签名。**需使用自己的有效证书和描述文件重新签名后安装**。 |
| iOS Simulator ZIP | 仅适用于 Xcode 中的模拟器，不能安装到真机；当前发行包使用 iOS 27 SDK 构建。 |

Mac 发行包使用 ad-hoc 签名，尚未经过 Developer ID 签名或 Apple 公证。首次打开可能出现 macOS 安全提示，请核实下载来源并按系统提示操作。未签名 IPA 尚未在真实 iPhone / iPad 上安装验证。

在解压模拟器包的目录中，启动一个模拟器后运行：

```sh
xcrun simctl install booted Jianzuo.app
xcrun simctl launch booted com.jianzuo.writing
```

## 数据与当前范围

- 文稿保存在当前设备的 `Application Support/Jianzuo/library.json`；Mac 沙盒版位于应用自己的沙盒容器内。
- 各设备独立保存文稿，**当前没有 iCloud 同步**。通过导入 / 导出交换文件；重要文稿请定期导出备份。
- 导出的是正文，标题用于文件名；笔记本、收藏和创建时间等库内信息不写入导出的 Markdown / TXT。
- Markdown 预览暂不渲染图片、表格或 HTML，这些内容仍按原文保留和导出。
- 字数目标按当前文稿计算，不是每日累计目标。
- 本次多语言支持针对项目文档，App 的其他界面语言尚未实现。

## 在 Xcode 运行

最低系统版本为 macOS 14、iOS / iPadOS 17，无需安装第三方库。

1. 克隆仓库，打开 `Jianzuo.xcodeproj`。
2. 选择 `Jianzuo` Scheme 和 `My Mac`、iPhone 或 iPad 模拟器，按 `⌘R`。
3. 如需运行到真机，在 Signing & Capabilities 中选择自己的开发团队，连接设备后运行。

```sh
git clone https://github.com/mrlingan/jianzuo.git
cd jianzuo
open Jianzuo.xcodeproj
```

### 构建与测试

```sh
# Mac 单元测试
xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo \
  -destination 'platform=macOS' -derivedDataPath Build/Mac \
  CODE_SIGNING_ALLOWED=NO test

# iOS 模拟器构建
xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath Build/iOS CODE_SIGNING_ALLOWED=NO build
```

现有 6 项单元测试覆盖中英文字数、Unicode 存储往返、搜索与收藏、废纸篓恢复和删除、损坏文件保护及未闭合代码块。v1.0.0 已在 Mac 和 iOS 模拟器分别通过这 6 项测试。

### 发行打包

```sh
# Mac DMG / ZIP、模拟器 ZIP、未签名真机 IPA 和校验文件
bash Scripts/package_release.sh 1.0.0

# 仅打包未签名真机 IPA
bash Scripts/package_ipa.sh 1.0.0
```

输出目录为 `Build/Releases/v1.0.0`。`SHA256SUMS.txt` 校验 Mac 和模拟器包，IPA 使用独立的 `.ipa.sha256` 文件。

### 项目结构

| 目录 / 文件 | 内容 |
| --- | --- |
| `Jianzuo/Models` | 文稿模型、统计、本地存储与文稿操作 |
| `Jianzuo/Views` | 跨平台界面、原生编辑器、Markdown 预览与设置 |
| `JianzuoTests` | 存储与文稿行为测试 |
| `Scripts` | 工程生成、图标绘制和发行打包脚本 |
| `Docs/Screenshots` | README 使用的真实界面截图 |
| `Docs/Releases` | 发行说明 |

`Scripts/create_project.py` 可重新生成 Xcode 工程与颜色资源；`Scripts/make_icon.swift` 为应用图标的原生绘图源代码。

## 参与和许可

欢迎通过 [Issues](https://github.com/mrlingan/jianzuo/issues) 报告问题和提出建议，通过 Pull Request 提交改进。开发约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。

本项目采用 [MIT License](LICENSE)。
