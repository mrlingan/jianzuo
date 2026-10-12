import SwiftUI

struct WritingSettings: View {
    @AppStorage("editorFontSize") private var fontSize = 17.0
    @AppStorage("editorFont") private var editorFont = "serif"
    @AppStorage("lineSpacing") private var lineSpacing = 9.0
    @AppStorage("wordGoal") private var wordGoal = 800
    @AppStorage("appearance") private var appearance = "system"
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("editorTheme") private var editorTheme = "classic"
    @AppStorage("ruledPaper") private var ruledPaper = false
    @State private var draftTheme = WritingPalette.classic
    @State private var draftRuled = false
    @State private var draftAppearance = "system"
    @State private var showShortcuts = false

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
                    Picker("外观", selection: $draftAppearance) {
                        Text("跟随系统").tag("system")
                        Text("浅色").tag("light")
                        Text("深色").tag("dark")
                    }
                }
                Section("纸张与配色") {
                    Picker("配色", selection: $draftTheme) {
                        ForEach(WritingPalette.allCases) { Text($0.title).tag($0) }
                    }
                    Toggle("横线稿纸", isOn: $draftRuled)
                    let dark = draftAppearance == "dark" || (draftAppearance == "system" && colorScheme == .dark)
                    VStack(alignment: .leading, spacing: 16) {
                        Text("把日子写成诗").font(.title3.weight(.semibold)).foregroundStyle(draftTheme.color(3, dark: dark))
                        ForEach(["清晨的光落在窗边。", "写下第一句，让故事慢慢发生。"], id: \.self) { line in
                            Text(line).font(.system(size: fontSize, design: editorFont == "serif" ? .serif : editorFont == "mono" ? .monospaced : .default))
                                .padding(.bottom, 5).frame(maxWidth: .infinity, alignment: .leading)
                                .overlay(alignment: .bottom) { if draftRuled { Rectangle().fill(draftTheme.color(2, dark: dark).opacity(0.12)).frame(height: 0.5) } }
                        }
                    }.foregroundStyle(draftTheme.color(2, dark: dark)).padding(18)
                        .background(draftTheme.color(0, dark: dark), in: RoundedRectangle(cornerRadius: 10))
                    Text("配色、稿纸和外观将在点击「完成」后保存。稿纸只影响编辑显示，正文和导出内容保持原样。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("快捷输入") {
                    Button("配置快捷输入栏…") { showShortcuts = true }
                }
                Section("关于简作") {
                    Text("版本 \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.1.0")")
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
                ToolbarItem(placement: .confirmationAction) { Button("完成") { editorTheme = draftTheme.rawValue; ruledPaper = draftRuled; appearance = draftAppearance; dismiss() } }
            }
        }
        .onAppear { draftTheme = WritingPalette(rawValue: editorTheme) ?? .classic; draftRuled = ruledPaper; draftAppearance = appearance }
        .sheet(isPresented: $showShortcuts) { ShortcutSettingsView() }
        #if os(macOS)
        .frame(width: 540, height: 700)
        #endif
    }
}
