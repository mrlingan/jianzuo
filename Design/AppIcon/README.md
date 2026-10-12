# 简作应用图标

新版以陶土红底色、暖白纸页和墨绿钢笔尖表达安静的写作空间。采用矢量轮廓、两行正文提示和轻微的折角、投影层次。

- `Jianzuo-Icon-Design.sketch`：已在 Sketch 中打开、检查并保存的可编辑设计稿，包含两套平台图标、配色和小尺寸预览。
- `Jianzuo-Icon-Design.svg`：初始设计稿的 SVG 快照。
- `AppIcon-iOS.svg` / `AppIcon-macOS.svg`：独立矢量快照。
- `Exports/AppIcon-iOS.png`：1024 × 1024，不含透明通道，由系统处理外轮廓。
- `Exports/AppIcon-macOS.png`：1024 × 1024，透明外边距、圆角底板和轻微投影。
- `Exports/Jianzuo-Icon-Design.png`：完整展示图。

修改 Sketch 源文件后保存，运行：

```sh
bash Scripts/export_sketch_icons.sh
```

脚本使用 Sketch 自带的 `sketchtool` 导出设计图层，规范化为 1024 像素母版，再生成 Xcode 资源目录所声明的全部尺寸。请保留 `AppIcon-iOS`、`AppIcon-macOS` 和 `Jianzuo-Icon-Design` 图层名称及两个图标组内左上角 1024 × 1024 的图标区域。可用 `SKETCHTOOL` 环境变量指定其他 Sketch 安装路径。

仅重新生成资源，无需启动 Sketch：

```sh
swift Scripts/make_icon.swift --install
```

仍支持原有单图导出方式，并可指定平台和尺寸：

```sh
swift Scripts/make_icon.swift /tmp/jianzuo-icon.png
swift Scripts/make_icon.swift /tmp/jianzuo-mac.png --macos --size 256
```

安卓图标位于 `Android/app/src/main/res/drawable/ic_launcher*.xml`：自适应背景、透明前景与单色主题图标分别保存。前景内缩以适配不同启动器遮罩；纸页和笔尖使用同色系的纯色矢量。安卓矢量和 SVG 快照不会随上述导出脚本自动更新，修改设计后应同步调整它们。

已验证：macOS 与 iOS 资源均通过 `actool` 编译，安卓通过 `:app:processDebugResources`。未重新打包安装应用。
