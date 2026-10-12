# 截图 / Screenshots

截图来自简作 v1.0.0 的真实运行界面，展示随项目提供的示例文稿。

These screenshots show the actual Jianzuo v1.0.0 interface and the sample documents included in the project.

| 文件 / File | 场景 / View | 环境 / Environment |
| --- | --- | --- |
| `mac-writing.jpg` | 三栏编辑器 / Three-column editor | macOS |
| `mac-preview.jpg` | Markdown 预览 / Markdown preview | macOS |
| `mac-focus.jpg` | 专注模式 / Focus mode | macOS |
| `iphone-writing.png` | 正文编辑 / Document editing | iPhone 18 Pro, iOS 27 Simulator |
| `ipad-writing.png` | 并排浏览与编辑 / Side-by-side browsing and editing | iPad Pro 13-inch (M5), iPadOS 27 Simulator |

移动端图片由模拟器截图功能导出，不代表真机安装验证。文档语言可在 README 顶部切换，App 界面目前为中文。

Mobile images were exported using the simulator's screenshot control; they do not imply physical-device installation testing. The README has a language switcher; the app interface is currently in Chinese.

## Android

`android-phone-writing.png`、`android-tablet-writing.png`、`android-tablet-portrait.png`、`android-tablet-dark.png` 是 2026-10-05 从 Android API 37 模拟器截取的实际界面，使用内置示例文稿。平板截图使用 1600 × 2560 / 2560 × 1600 px、320 dpi 的显示配置；详见 [验证记录](../Android-validation.md)。

Android 1.1.0 新增功能截图：`android-features-writing.png`、`android-features-tools.png`、`android-features-history.png`、`android-features-backup.png`。来自 API 37 模拟器的 1280 dp 横屏窗口；使用为视觉检查准备的「秋日来信」演示文稿及历史数据，不是用户私人文稿。竞品检查截图只保留在本机 Build 目录，不使用竞品视觉资产。

Android 1.3.0 新增 `android-1.3-directory.png`、`android-1.3-phone-directory.png`：2026-10-07 在 API 37 模拟器拍摄，分别为 2560 × 1600 / 320 dpi 三栏窗口和 1080 × 2400 / 420 dpi 手机窗口。使用独立的「秋日来信」演示数据，展示折叠目录、空卷、章节拖动入口与常驻计数；完成后已恢复原始文稿库与设置。

Android 1.4.0 新增 `android-1.4-dual-editor.png`、`android-1.4-phone-dual-editor.png`：2026-10-07 在同一 API 37 模拟器拍摄，分别为 2560 × 1600 / 320 dpi 平板和 1080 × 2400 / 420 dpi 手机配置。独立演示数据展示主稿编辑、参考稿 Markdown 预览、60% 主稿比例，以及左右／上下自适应；不含用户私人文稿或竞品素材。完成后恢复原文稿库与设置，图片已与设备端文件校验一致。

Android 1.5.0 新增 `android-1.5-phone-shortcuts.png`、`android-1.5-shortcut-settings.png`、`android-1.5-tablet-shortcuts-dark.png`：2026-10-08 拍摄于同一 API 37 模拟器，手机为 1080 × 2400 / 420 dpi，平板为 2560 × 1600 / 320 dpi。独立「秋日来信」演示数据展示三行快捷输入、项目管理、字符统计和深色双稿；不含用户私人文稿或竞品素材。图片与设备端文件 SHA-256 一致，拍摄后恢复原始文稿库与设置。

## Android 1.7.0 导入功能

- `android-1.7-folder-import.png`：系统文件夹导入说明，真实手机尺寸 1080 × 2400 / 420 dpi。
- `android-1.7-shared-import.png`：系统分享的导入预览，长标题、勾选和操作按钮完整显示。

截图来自最终 1.7.0 调试 APK；背景为自带示例，分享文字为专用演示数据。安装包 SHA-256 与交付 APK 一致，见 [验证记录](../Android-validation.md)。

## Android 1.8.0 跨来源找回

- `android-1.8-phone-recovery.png`：1080 × 2400 / 420 dpi 手机，关键词匹配废纸篓、历史及自动备份，结果列表可滚动。
- `android-1.8-phone-recovery-preview.png`：完整正文预览及「保存为新文稿」入口。
- `android-1.8-tablet-dark-recovery.png`、`android-1.8-tablet-dark-recovery-preview.png`：2560 × 1600 / 320 dpi 平板深色横屏的搜索与预览。

截图来自最终 1.8.0 调试 APK，在只读临时模拟器中使用独立「秋日来信」演示文稿、历史及自动快照；不含个人数据。模拟器缺少部分补充汉字字形，存储／恢复测试已验证其码点保持完整。

## Android 1.9.0 排版导出

- `android-1.9-phone-export-layout.png`、`android-1.9-tablet-dark-export-layout.png`：手机浅色与平板深色的排版选择，默认 Markdown，可切换为保留标记的原文。
- `android-1.9-phone-settings-version.png`、`android-1.9-tablet-dark-settings-version.png`：设置页从构建配置读取的 1.9.0 版号。
- `android-1.9-markdown-pdf.png`：应用原生导出的 PDF 首页，使用 Poppler 渲染。
- `android-1.9-markdown-long-image.png`：应用原生导出的 1080 px 长图，包含标题、行内样式、引用、列表、任务、表格、链接和代码。

界面截图来自 API 37 同一个只读临时模拟器，手机为 1080 × 2400 / 420 dpi，平板为深色横屏 2560 × 1600 / 320 dpi，使用自带示例。导出示例为设备测试中的专用演示文字；均不含个人文稿。安装包校验与验证范围见 [验证记录](../Android-validation.md)。

## Android 1.10.0 配色与稿纸

- `android-1.10-phone-theme-preview.png`：手机浅色设置内预览「山林」与横线，完成前仅样例改变。
- `android-1.10-phone-paper.png`：保存后的山林配色与正文横线。
- `android-1.10-tablet-dark-theme-preview.png`、`android-1.10-tablet-dark-paper.png`：平板深色横屏的海雾预览与正文。
- `android-1.10-tablet-dark-dual-paper.png`：海雾深色双编辑器，两篇正文共用横线样式。

截图拍摄于 2026-10-09，来自最终 1.10.0 调试 APK。使用 API 37 同一个只读临时模拟器及自带示例；手机为 1080 × 2400 / 420 dpi，平板为 2560 × 1600 / 320 dpi。没有使用个人文稿；安装包哈希和验证范围见 [验证记录](../Android-validation.md)。


## Android 1.11.0 中英文界面

- `android-1.11-phone-language.png`、`android-1.11-phone-english.png`：API 32 手机浅色竖屏，界面语言选择及英文快捷栏，中文示例正文保持原文。
- `android-1.11-tablet-dark-language.png`、`android-1.11-tablet-dark-english-dual.png`：API 37 平板深色横屏，语言选择及英文双编辑器。

截图拍摄于 2026-10-11，均来自最终 1.11.0 调试 APK；手机为 1080 × 2400 / 420 dpi，平板为 2560 × 1600 / 320 dpi。使用自带示例，未包含个人文稿；各设备安装包 SHA-256 与交付包一致。完整范围和测试结果见 [验证记录](../Android-validation.md)。


## Android 1.12.0 DeepSeek AI 续写

- `android-1.12-phone-ai-preview.png`：API 37 手机浅色竖屏，续写要求、长度、状态、可编辑结果与插入／复制操作。
- `android-1.12-tablet-ai-preview.png`：API 37 平板横屏，测试夹具使用浅色应用界面，展示相同预览流程。

截图拍摄于 2026-10-11，来自 1.12.0 最终调试 APK。正文和结果为界面回归测试夹具，并非真实 API 输出或个人文稿；真实中文／英文请求另行通过。Key 设置窗口禁止截图，图片不包含 API Key。手机为 1080 × 2400 / 420 dpi；完整测试与安装包哈希见 [验证记录](../Android-validation.md)。
