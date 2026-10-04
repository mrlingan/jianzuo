import SwiftUI

struct WritingSettings: View {
    @AppStorage("editorFontSize") private var fontSize = 17.0
    @AppStorage("editorFont") private var editorFont = "serif"
    @AppStorage("lineSpacing") private var lineSpacing = 9.0
    @AppStorage("wordGoal") private var wordGoal = 800
    @AppStorage("appearance") private var appearance = "system"
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("编辑器") {
                    Picker("正文字体", selection: $editorFont) {
                        Text("宋体").tag("serif")
                        Text("系统字体").tag("system")
                        Text("等宽字体").tag("mono")
                    }
                    HStack {
                        Text("字号 \(Int(fontSize))")
                        Slider(value: $fontSize, in: 14...26, step: 1)
                    }
                    HStack {
                        Text("行距 \(Int(lineSpacing))")
                        Slider(value: $lineSpacing, in: 4...18, step: 1)
                    }
                    Text("文字是时间的容器。写下来，就是留下来。")
                        .font(.system(size: fontSize, design: editorFont == "serif" ? .serif : editorFont == "mono" ? .monospaced : .default))
                        .lineSpacing(lineSpacing).padding(.vertical, 12)
                }
                Section("写作习惯") {
                    Picker("每篇文稿目标", selection: $wordGoal) {
                        Text("不设目标").tag(0)
                        Text("300 字").tag(300)
                        Text("800 字").tag(800)
                        Text("1,500 字").tag(1500)
                        Text("3,000 字").tag(3000)
                    }
                    Picker("外观", selection: $appearance) {
                        Text("跟随系统").tag("system")
                        Text("浅色").tag("light")
                        Text("深色").tag("dark")
                    }
                }
                Section("关于简作") {
                    Text("一个安静的写作空间。支持 Markdown，文稿自动保存到当前设备。")
                    Text("Mac、iPhone 和 iPad 各自保存本地文稿；可通过导入、导出交换文件。")
                        .font(.footnote).foregroundStyle(.secondary)
                    #if os(macOS)
                    Text("快捷键：⌘N 新建 · ⌘S 保存 · ⇧⌘F 专注 · ⇧⌘P 预览")
                        .font(.footnote).foregroundStyle(.secondary)
                    #endif
                }
            }
            .formStyle(.grouped)
            .navigationTitle("写作设置")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } }
            }
        }
        #if os(macOS)
        .frame(width: 500, height: 570)
        #endif
    }
}
