import SwiftUI


struct ImportPreviewView: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @State private var destination = Notebook.drafts
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("导入到", selection: $destination) {
                        ForEach(library.notebooks) { Text($0.rawValue).tag($0) }
                    }
                    Text("勾选文件，可修改标题及拖动调整顺序。仅导入 UTF-8 正文。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                ForEach($library.importCandidates) { $candidate in
                    HStack(alignment: .top) {
                        Toggle("导入 \(candidate.filename)", isOn: $candidate.included).labelsHidden()
                            #if os(iOS)
                            .toggleStyle(.checkboxButton)
                            #endif
                        VStack(alignment: .leading, spacing: 8) {
                            TextField("标题", text: $candidate.title)
                            Text("\(candidate.filename) · \(WritingMetrics.count(candidate.body)) 字").font(.caption).foregroundStyle(.secondary)
                            Text(candidate.body.prefix(240)).font(.footnote).foregroundStyle(.secondary).lineLimit(4)
                        }
                    }
                }.onMove { library.importCandidates.move(fromOffsets: $0, toOffset: $1) }
            }
            .navigationTitle("导入预览")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { library.importCandidates = []; dismiss() } }
                #if os(iOS)
                ToolbarItem(placement: .primaryAction) { EditButton() }
                #endif
                ToolbarItem(placement: .confirmationAction) {
                    Button("导入 \(library.importCandidates.filter(\.included).count) 篇") { library.commitImport(to: destination) }
                        .disabled(!library.importCandidates.contains(where: \.included))
                }
            }
        }.onAppear { destination = library.notebooks.first ?? .drafts }
        .featureSheetSize()
    }
}

#if os(iOS)
private struct CheckboxButtonStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square").font(.title3).frame(width: 32, height: 32)
        }.buttonStyle(.plain).accessibilityLabel("导入此文件").accessibilityValue(configuration.isOn ? "已选" : "未选")
    }
}
private extension ToggleStyle where Self == CheckboxButtonStyle { static var checkboxButton: Self { Self() } }
#endif

struct NotebookManagerView: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @State private var newName = ""
    @State private var editing: Notebook?
    @State private var editedName = ""
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                Section("我的笔记本") {
                    ForEach(library.notebooks) { notebook in
                        HStack {
                            Label(notebook.rawValue, systemImage: notebook.symbol)
                            Spacer()
                            Button("重命名") { editing = notebook; editedName = notebook.rawValue }
                            Button(role: .destructive) { perform { try library.deleteNotebook(notebook) } } label: { Image(systemName: "trash") }
                                .accessibilityLabel("删除 \(notebook.rawValue)")
                        }
                    }
                    Text("删除笔记本前，须移出其中的文稿，包括废纸篓中的文稿。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("新建笔记本") {
                    TextField("名称（最多 40 字）", text: $newName)
                    Button("创建") { perform { try library.addNotebook(newName); newName = "" } }.disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                if let error { Text(error).foregroundStyle(.red) }
            }.formStyle(.grouped).navigationTitle("管理笔记本")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
                .alert("重命名笔记本", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
                    TextField("笔记本名称", text: $editedName)
                    Button("取消", role: .cancel) { editing = nil }
                    Button("保存") { if let editing { perform { try library.renameNotebook(editing, to: editedName) } }; editing = nil }
                }
        }.featureSheetSize()
    }
    private func perform(_ action: () throws -> Void) { do { try action(); error = nil } catch { self.error = error.localizedDescription } }
}

enum EditorSheet: String, Identifiable {
    case find, outline, format, tags, history, second
    var id: String { rawValue }
}

struct FindReplaceView: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    let document: WritingDocument
    let controller: EditorController
    @State private var query = ""
    @State private var replacement = ""
    @State private var regex = false
    @State private var caseSensitive = false
    @State private var source = ""
    @State private var matches: [TextMatch] = []
    @State private var selected: Set<Int> = []
    @State private var error: String?
    @State private var resultMessage: String?

    var body: some View {
        NavigationStack {
            List {
                Section("查找与替换") {
                    LabeledContent("查找") { TextField("查找文字", text: $query) }
                    LabeledContent("替换为") { TextField("留空可删除", text: $replacement) }
                    Toggle("正则表达式", isOn: $regex)
                    Toggle("区分大小写", isOn: $caseSensitive)
                    if let error { Text(error).foregroundStyle(.red) }
                    if let resultMessage { Text(resultMessage).foregroundStyle(.secondary) }
                }
                Section {
                    HStack {
                        Text("找到 \(matches.count) 处，已选 \(selected.count) 处")
                        Spacer()
                        Button(selected.count == matches.count ? "全不选" : "全选") { selected = selected.count == matches.count ? [] : Set(matches.map(\.id)) }
                    }.font(.footnote)
                    ForEach(matches) { match in
                        HStack {
                            Button { if !selected.insert(match.id).inserted { selected.remove(match.id) } } label: {
                                Image(systemName: selected.contains(match.id) ? "checkmark.square.fill" : "square")
                            }.buttonStyle(.plain).accessibilityLabel("选择此处替换")
                            Button {
                                guard library.documents.first(where: { $0.id == document.id })?.body == source else { error = "正文已变化，请重新查找。"; return }
                                controller.reveal(match.range); dismiss()
                            } label: { Text(match.excerpt).font(.body).lineLimit(3).frame(maxWidth: .infinity, alignment: .leading) }.buttonStyle(.plain)
                        }
                    }
                }
            }.navigationTitle("查找替换")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("替换所选") { replace() }.disabled(selected.isEmpty || document.isReadOnly || document.deletedAt != nil || error != nil)
                    }
                }
        }
        .onAppear { refresh() }
        .onChange(of: query) { _, _ in refresh() }
        .onChange(of: regex) { _, _ in refresh() }
        .onChange(of: caseSensitive) { _, _ in refresh() }
        .featureSheetSize()
    }
    private func refresh() {
        source = library.documents.first(where: { $0.id == document.id })?.body ?? document.body
        do { matches = try WritingTools.matches(in: source, query: query, regex: regex, caseSensitive: caseSensitive); selected = Set(matches.map(\.id)); error = nil }
        catch { matches = []; selected = []; self.error = error.localizedDescription }
    }
    private func replace() {
        do {
            let current = library.documents.first(where: { $0.id == document.id })?.body ?? ""
            let text = try WritingTools.replace(source: current, expected: source, matches: matches, selected: selected, replacement: replacement)
            let count = selected.count
            library.checkpoint(document.id)
            library.update(document.id, body: text, countWriting: false)
            refresh(); resultMessage = "已替换 \(count) 处。可在历史记录中恢复替换前的正文。"
        } catch { self.error = error.localizedDescription }
    }
}

struct OutlineView: View {
    @Environment(\.dismiss) private var dismiss
    let document: WritingDocument
    let controller: EditorController
    var body: some View {
        NavigationStack {
            List {
                let headings = WritingTools.outline(document.body)
                if headings.isEmpty { Text("正文中使用 #、## 等 Markdown 标题，即可生成大纲。").foregroundStyle(.secondary) }
                ForEach(headings) { heading in
                    Button { controller.reveal(heading.range); dismiss() } label: {
                        Text(heading.title).padding(.leading, CGFloat(heading.level - 1) * 14).frame(maxWidth: .infinity, alignment: .leading)
                    }.buttonStyle(.plain)
                }
            }.navigationTitle("文稿大纲")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }.featureSheetSize()
    }
}

struct FormatPreviewView: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    let document: WritingDocument
    @State private var operation = WritingTools.Format.unindent
    @State private var error: String?
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Picker("排版方式", selection: $operation) { ForEach(WritingTools.Format.allCases) { Text($0.rawValue).tag($0) } }
                Text("手动处理纯文本段落，可能改变 Markdown 排版。应用前请检查预览；原文会存入历史。").font(.footnote).foregroundStyle(.secondary)
                ScrollView { Text(WritingTools.format(document.body, operation: operation)).frame(maxWidth: .infinity, alignment: .leading).textSelection(.enabled) }
                if let error { Text(error).foregroundStyle(.red) }
            }.padding().navigationTitle("排版预览")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("应用") {
                        guard library.documents.first(where: { $0.id == document.id })?.body == document.body else { error = "正文已变化，请重新打开排版预览。"; return }
                        library.checkpoint(document.id)
                        library.update(document.id, body: WritingTools.format(document.body, operation: operation), countWriting: false)
                        dismiss()
                    }.disabled(document.isReadOnly || document.deletedAt != nil) }
                }
        }.featureSheetSize()
    }
}

struct TagsView: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    let document: WritingDocument
    @State private var tags = ""
    var body: some View {
        NavigationStack {
            Form {
                TextField("标签，以空格或逗号分隔", text: $tags, axis: .vertical)
                Text("标签可用于列表筛选，也会参与全文搜索。").font(.footnote).foregroundStyle(.secondary)
            }.formStyle(.grouped).navigationTitle("文稿标签")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("保存") { library.setTags(document.id, source: tags); dismiss() } }
                }
        }.onAppear { tags = document.tags.joined(separator: " ") }.featureSheetSize()
    }
}

struct HistoryView: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    let document: WritingDocument
    @State private var selected: DocumentRevision?
    @State private var confirmRestore = false
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text("编辑前自动留存，每 30 秒最多一份，保留最近 40 份。恢复前会保存当前内容。").font(.footnote).foregroundStyle(.secondary).padding()
                List(document.history, selection: Binding(get: { selected?.id }, set: { id in selected = document.history.first { $0.id == id } })) { revision in
                    Button { selected = revision } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(revision.createdAt.formatted(date: .abbreviated, time: .standard))
                            Text("\(revision.title.isEmpty ? "无标题文稿" : revision.title) · \(WritingMetrics.count(revision.body)) 字").font(.caption).foregroundStyle(.secondary)
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }.buttonStyle(.plain).listRowBackground(selected?.id == revision.id ? PaperTheme.accent.opacity(0.12) : Color.clear)
                }.frame(maxHeight: 220)
                if let selected {
                    Divider()
                    ScrollView { Text(selected.body.isEmpty ? "（空白正文）" : selected.body).frame(maxWidth: .infinity, alignment: .leading).padding().textSelection(.enabled) }
                    HStack {
                        Button("保存为新文稿") { library.restoreRevision(selected, to: document.id, asCopy: true); dismiss() }
                        Spacer()
                        Button("恢复此版本") { confirmRestore = true }.disabled(document.isReadOnly || document.deletedAt != nil)
                    }.padding()
                } else { ContentUnavailableView("选择一个历史版本", systemImage: "clock.arrow.circlepath", description: Text(document.history.isEmpty ? "正文编辑后会自动留下历史记录，也可手动保存版本。" : "先查看正文，再选择恢复方式。")) }
            }.navigationTitle("历史记录")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
                .confirmationDialog("恢复此版本？当前内容会保留在历史记录中。", isPresented: $confirmRestore, titleVisibility: .visible) {
                    Button("恢复") { if let selected { library.restoreRevision(selected, to: document.id) }; dismiss() }
                }
        }.featureSheetSize()
    }
}

struct SecondDocumentPicker: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Button("关闭第二编辑器") { library.secondarySelection = nil; dismiss() }
                ForEach(library.documents.filter { $0.deletedAt == nil && $0.id != library.selection }) { document in
                    Button { library.secondarySelection = document.id; dismiss() } label: {
                        VStack(alignment: .leading) { Text(document.displayTitle); Text(document.notebook.rawValue).font(.caption).foregroundStyle(.secondary) }
                    }
                }
            }.navigationTitle("选择第二篇文稿")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }.featureSheetSize()
    }
}

struct WritingDashboard: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @AppStorage("dailyGoal") private var dailyGoal = 800
    var body: some View {
        NavigationStack {
            List {
                Section("今天") {
                    LabeledContent("新增字数", value: "\(library.dailyWords[WritingDay.key()] ?? 0) 字")
                    Picker("每日目标", selection: $dailyGoal) {
                        ForEach([0,300,800,1500,3000], id: \.self) { Text($0 == 0 ? "不设目标" : "\($0) 字").tag($0) }
                    }
                    if dailyGoal > 0 { ProgressView(value: min(1, Double(library.dailyWords[WritingDay.key()] ?? 0) / Double(dailyGoal))) }
                    Text("统计正文输入时的字数净增长，删除不扣减；导入、历史恢复及批量排版不计入。粘贴正文计入新增字数。").font(.footnote).foregroundStyle(.secondary)
                }
                Section("今年") {
                    let days = library.dailyWords.filter { $0.key.hasPrefix(String(Calendar(identifier: .gregorian).component(.year, from: Date()))) }
                    LabeledContent("累计新增", value: "\(days.values.reduce(0, +)) 字")
                    LabeledContent("写作天数", value: "\(days.values.filter { $0 > 0 }.count) 天")
                }
                Section("最近记录") {
                    ForEach(library.dailyWords.keys.sorted(by: >).prefix(60), id: \.self) { day in
                        LabeledContent(day, value: "\(library.dailyWords[day] ?? 0) 字")
                    }
                    if library.dailyWords.isEmpty { Text("写下第一句，开始今天的记录。").foregroundStyle(.secondary) }
                }
            }.navigationTitle("每日写作")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }.featureSheetSize()
    }
}

struct ScratchpadView: View {
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading) {
                Text("随手记下念头，内容自动保存在本机。创建文稿后仍保留速记。")
                    .font(.footnote).foregroundStyle(.secondary)
                TextEditor(text: $library.scratch).font(.body).disabled(!library.isWritable)
            }.padding().navigationTitle("速记")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("完成") { library.saveNow(); dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("创建文稿") { library.createDocument(title: "速记 · " + Date().formatted(date: .abbreviated, time: .shortened), body: library.scratch); dismiss() }.disabled(library.scratch.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
                }
        }.featureSheetSize()
    }
}

extension View {
    func featureSheetSize() -> some View {
        #if os(macOS)
        self.frame(width: 620, height: 650)
        #else
        self
        #endif
    }
}
