import SwiftUI

struct ShortcutSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("inputShortcuts") private var shortcutData = Data()
    @AppStorage("shortcutRows") private var shortcutRows = 1
    @State private var shortcuts: [InputShortcut] = []
    @State private var editing: InputShortcut?
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("输入栏行数", selection: $shortcutRows) {
                        Text("隐藏").tag(0)
                        ForEach(1...3, id: \.self) { Text("\($0) 行").tag($0) }
                    }
                    Text("点击项目修改标题与多行内容，拖动调整顺序。插入位置为当前光标或选区。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("快捷输入") {
                    ForEach(shortcuts) { shortcut in
                      HStack {
                        Button { editing = shortcut } label: {
                            HStack {
                                Text(shortcut.title).frame(minWidth: 60, alignment: .leading)
                                Text(shortcut.text.replacingOccurrences(of: "\n", with: " ↵ ")).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                                Spacer()
                                Image(systemName: "pencil").foregroundStyle(.secondary)
                            }
                        }.buttonStyle(.plain)
                        Button(role: .destructive) { shortcuts.removeAll { $0.id == shortcut.id }; persist() } label: { Image(systemName: "trash") }
                            .buttonStyle(.plain).accessibilityLabel("删除快捷输入 \(shortcut.title)")
                      }
                    }.onMove { shortcuts.move(fromOffsets: $0, toOffset: $1); persist() }
                        .onDelete { shortcuts.remove(atOffsets: $0); persist() }
                    Button("添加快捷输入", systemImage: "plus") { editing = InputShortcut(title: "", text: "") }
                        .disabled(shortcuts.count >= 60)
                    Button("恢复默认输入项") { shortcuts = InputShortcut.defaults; persist() }
                }
            }.navigationTitle("快捷输入设置")
                .toolbar {
                    #if os(iOS)
                    ToolbarItem(placement: .primaryAction) { EditButton() }
                    #endif
                    ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } }
                }
                .sheet(item: $editing) { shortcut in
                    ShortcutEditor(initial: shortcut) { value in
                        if let index = shortcuts.firstIndex(where: { $0.id == value.id }) { shortcuts[index] = value }
                        else { shortcuts.append(value) }
                        persist()
                    }
                }
        }.onAppear { shortcuts = InputShortcut.load(shortcutData) }.featureSheetSize()
    }
    private func persist() { if let data = try? JSONEncoder().encode(shortcuts) { shortcutData = data } }
}

private struct ShortcutEditor: View {
    @Environment(\.dismiss) private var dismiss
    let initial: InputShortcut
    var save: (InputShortcut) -> Void
    @State private var title = ""
    @State private var text = ""
    var body: some View {
        NavigationStack {
            Form {
                TextField("按钮标题（最多 20 字）", text: $title)
                Section("插入内容（支持多行）") { TextEditor(text: $text).frame(minHeight: 180) }
            }.formStyle(.grouped).navigationTitle("编辑快捷输入")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("保存") {
                        var value = initial; value.title = title.trimmingCharacters(in: .whitespacesAndNewlines); value.text = text
                        save(value); dismiss()
                    }.disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || title.count > 20 || text.isEmpty) }
                }
        }.onAppear { title = initial.title; text = initial.text }.featureSheetSize()
    }
}
