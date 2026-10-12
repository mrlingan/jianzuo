import SwiftUI

struct EditorView: View {
    @EnvironmentObject private var library: LibraryStore
    var documentID: UUID?
    var isSecondary = false
    @AppStorage("editorFontSize") private var fontSize = 17.0
    @AppStorage("editorFont") private var editorFont = "serif"
    @AppStorage("lineSpacing") private var lineSpacing = 9.0
    @AppStorage("wordGoal") private var wordGoal = 800
    @AppStorage("editorTheme") private var editorTheme = "classic"
    @AppStorage("ruledPaper") private var ruledPaper = false
    @AppStorage("shortcutRows") private var shortcutRows = 1
    @AppStorage("inputShortcuts") private var shortcutData = Data()
    @AppStorage("countMode") private var countMode = "words"
    @StateObject private var controller = EditorController()
    @State private var secondaryPreview = false
    @State private var sheet: EditorSheet?
    @State private var showStatistics = false
    @State private var confirmDelete = false
    @FocusState private var titleFocused: Bool

    private var document: WritingDocument? {
        if let documentID { return library.documents.first { $0.id == documentID } }
        return library.currentDocument
    }
    private var isPreview: Bool { isSecondary ? secondaryPreview : library.showPreview }
    private func togglePreview() { if isSecondary { secondaryPreview.toggle() } else { library.showPreview.toggle() } }
    private func openTool(_ tool: EditorSheet) {
        if tool == .outline || tool == .find { if isSecondary { secondaryPreview = false } else { library.showPreview = false } }
        sheet = tool
    }

    var body: some View {
        Group {
            if let document {
                VStack(spacing: 0) {
                    editorToolbar(document)
                    Divider().opacity(0.6)
                    if document.deletedAt != nil {
                        HStack {
                            Label("这篇文稿在废纸篓中", systemImage: "trash").font(.system(size: 12))
                            Spacer()
                            Button("恢复文稿") { library.restore(document.id) }
                            Button("彻底删除", role: .destructive) { confirmDelete = true }
                        }.padding(14).background(PaperTheme.accent.opacity(0.08))
                    }
                    GeometryReader { geometry in
                        let horizontalPadding = geometry.size.width > 650 ? max(48, (geometry.size.width - 660) / 2) : 24.0
                        let compactHeight = geometry.size.height < 340
                        VStack(alignment: .leading, spacing: 0) {
                          if !compactHeight {
                            HStack(spacing: 8) {
                                Text(document.notebook.rawValue.uppercased()).tracking(2)
                                Text("/")
                                Text(document.createdAt, format: .dateTime.year().month().day())
                            }.font(.system(size: 10)).foregroundStyle(.tertiary)
                                .padding(.top, geometry.size.width > 650 ? 44 : 26).padding(.bottom, 16)
                          }
                            TextField("无标题文稿", text: Binding(get: { document.title }, set: { library.update(document.id, title: $0) }), axis: .vertical)
                                .font(.system(size: compactHeight ? 23 : geometry.size.width > 650 ? 31 : 27, weight: .semibold, design: .serif))
                                .textFieldStyle(.plain).lineLimit(1...(compactHeight ? 1 : 3)).focused($titleFocused)
                                .disabled(document.deletedAt != nil || document.isReadOnly)
                                .padding(.top, compactHeight ? 12 : 0).padding(.bottom, compactHeight ? 8 : 16)
                          if !compactHeight {
                            HStack(spacing: 9) {
                                Image(systemName: "clock").font(.system(size: 10))
                                Text("最近编辑 \(document.updatedAt.formatted(.dateTime.month().day().hour().minute()))")
                                Circle().frame(width: 2, height: 2)
                                Text("\(document.wordCount) 字")
                            }.font(.system(size: 10)).foregroundStyle(.tertiary).padding(.bottom, 24)
                          }
                            Rectangle().fill(.primary.opacity(0.07)).frame(height: 1)
                            if !document.tags.isEmpty || document.isReadOnly {
                                HStack {
                                    if document.isReadOnly { Label("只读", systemImage: "lock").font(.caption) }
                                    Text(document.tags.map { "#" + $0 }.joined(separator: "  ")).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                }.padding(.vertical, 8)
                            }
                            if isPreview {
                                ScrollView {
                                    MarkdownPreview(text: document.body, fontSize: fontSize, fontStyle: editorFont, lineSpacing: lineSpacing)
                                        .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 24)
                                }
                            } else {
                                ZStack(alignment: .topLeading) {
                                    NativeWritingEditor(text: Binding(get: { document.body }, set: { library.update(document.id, body: $0) }), fontSize: fontSize, fontStyle: editorFont, lineSpacing: lineSpacing, isEditable: document.deletedAt == nil && !document.isReadOnly, controller: controller, ruledPaper: ruledPaper)
                                        .id(document.id)
                                    if document.body.isEmpty {
                                        Text("写下第一句，让故事慢慢发生…")
                                            .font(.system(size: fontSize, design: editorFont == "serif" ? .serif : .default))
                                            .foregroundStyle(.tertiary).padding(.top, 24).allowsHitTesting(false)
                                    }
                                }
                            }
                        }.padding(.horizontal, horizontalPadding)
                    }
                    if !isPreview && document.deletedAt == nil && !document.isReadOnly && shortcutRows > 0 {
                        shortcutBar
                    }
                    editorFooter(document)
                }
                .confirmationDialog("彻底删除这篇文稿？此操作无法撤销。", isPresented: $confirmDelete, titleVisibility: .visible) {
                    Button("彻底删除", role: .destructive) { library.permanentlyDelete(document.id) }
                    Button("取消", role: .cancel) {}
                }
                .sheet(item: $sheet) { tool in
                    switch tool {
                    case .find: FindReplaceView(document: document, controller: controller)
                    case .outline: OutlineView(document: document, controller: controller)
                    case .format: FormatPreviewView(document: document)
                    case .tags: TagsView(document: document)
                    case .history: HistoryView(document: document)
                    case .second: SecondDocumentPicker()
                    }
                }
            } else {
                VStack(spacing: 18) {
                    Image(systemName: "pencil.and.outline").font(.system(size: 48, weight: .ultraLight)).foregroundStyle(PaperTheme.accent)
                    Text("给文字一点空间").font(.system(size: 26, design: .serif))
                    Text("选择一篇文稿，或开始新的一页。").font(.system(size: 13)).foregroundStyle(.secondary)
                    Button("新建文稿") { library.createDocument() }.buttonStyle(.borderedProminent)
                }.frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(PaperTheme.background)
        #if os(macOS)
        .onExitCommand { library.focusMode = false }
        #endif
    }

    private func editorToolbar(_ document: WritingDocument) -> some View {
        HStack(spacing: 12) {
            #if os(macOS)
            Text("\(document.notebook.rawValue)  /  \(document.displayTitle)")
                .font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
            #else
            Text(document.notebook.rawValue).font(.system(size: 11)).foregroundStyle(.secondary)
            #endif
            Spacer(minLength: 8)
            Button { library.focusMode.toggle() } label: {
                Image(systemName: library.focusMode ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
            }.help(library.focusMode ? "退出专注模式 ⇧⌘F" : "专注模式 ⇧⌘F").accessibilityLabel("切换专注模式")
            Button { togglePreview() } label: {
                Image(systemName: isPreview ? "pencil" : "eye")
            }.help("编辑 / 预览 Markdown ⇧⌘P").accessibilityLabel(isPreview ? "编辑 Markdown" : "预览 Markdown")
            if document.deletedAt == nil {
                Button { library.toggleFavorite(document.id) } label: {
                    Image(systemName: document.isFavorite ? "star.fill" : "star")
                        .foregroundStyle(document.isFavorite ? PaperTheme.accent : .secondary)
                }.help("收藏文稿").accessibilityLabel(document.isFavorite ? "取消收藏" : "收藏文稿")
            }
            Rectangle().fill(.primary.opacity(0.1)).frame(width: 1, height: 16)
            Button { library.export(document) } label: { Image(systemName: "square.and.arrow.up") }
                .help("导出文稿 ⇧⌘E").accessibilityLabel("导出文稿")
            Menu {
                Button("文稿统计", systemImage: "chart.bar") { showStatistics = true }
                Button("查找替换", systemImage: "magnifyingglass") { openTool(.find) }
                Button("文稿大纲", systemImage: "list.bullet.indent") { openTool(.outline) }
                Button("历史记录", systemImage: "clock.arrow.circlepath") { openTool(.history) }
                if isSecondary {
                    Button("切换第二篇文稿", systemImage: "rectangle.split.2x1") { openTool(.second) }
                    Button("关闭第二编辑器") { library.secondarySelection = nil }
                } else {
                    Button("第二编辑器", systemImage: "rectangle.split.2x1") { openTool(.second) }
                }
                if document.deletedAt == nil {
                    Button("编辑标签", systemImage: "tag") { openTool(.tags) }
                    Button(document.isReadOnly ? "允许编辑" : "设为只读", systemImage: document.isReadOnly ? "lock.open" : "lock") { library.toggleReadOnly(document.id) }
                    Button("手动保存版本", systemImage: "clock.badge.checkmark") { library.checkpoint(document.id) }
                    Button("正文排版", systemImage: "text.alignleft") { openTool(.format) }.disabled(document.isReadOnly)
                    Menu("移到笔记本") {
                        ForEach(library.notebooks) { notebook in
                            Button(notebook.rawValue) { library.update(document.id, notebook: notebook) }
                        }
                    }
                    Button("创建副本", systemImage: "doc.on.doc") { library.duplicate(document) }
                    Divider()
                    Button("移到废纸篓", systemImage: "trash", role: .destructive) { library.moveToTrash(document.id) }
                }
            } label: { Image(systemName: "ellipsis") }
            .menuStyle(.borderlessButton).fixedSize().accessibilityLabel("更多文稿操作")
        }.font(.system(size: 14)).foregroundStyle(.secondary).buttonStyle(.plain)
            .padding(.horizontal, 18).frame(height: 58)
            .popover(isPresented: $showStatistics) { DocumentStatistics(document: document) }
    }

    private func editorFooter(_ document: WritingDocument) -> some View {
        VStack(spacing: 0) {
            Divider().opacity(0.6)
            GeometryReader { geometry in
              HStack(spacing: 7) {
                Circle().fill(library.saveState == "保存失败" || library.saveState == "读取失败" ? .red : PaperTheme.green).frame(width: 5, height: 5)
                Text(library.saveState).lineLimit(1).minimumScaleFactor(0.75)
                Spacer()
                Menu {
                    Picker("计数方式", selection: $countMode) {
                        Text("中英文字数").tag("words")
                        Text("全部字符").tag("characters")
                        Text("不含空白字符").tag("nonspace")
                        Text("中文字数").tag("cjk")
                    }
                    Button("详细统计") { showStatistics = true }
                } label: { Text(countLabel(document.body)) }.menuStyle(.borderlessButton).fixedSize()
                if !controller.selectedText.isEmpty && !isPreview { Text("选中 \(countLabel(controller.selectedText))").lineLimit(1) }
                if wordGoal > 0 && geometry.size.width > 480 {
                    Text("/").foregroundStyle(.tertiary)
                    Text("\(wordGoal)")
                    ProgressView(value: min(Double(document.wordCount) / Double(wordGoal), 1))
                        .tint(PaperTheme.green).frame(width: 54)
                }
                #if os(macOS)
                if geometry.size.width > 600 {
                    Text("·").padding(.horizontal, 4)
                    Text(isPreview ? "预览" : "Markdown")
                }
                #endif
            }.font(.system(size: PaperTheme.captionSize)).foregroundStyle(.secondary).padding(.horizontal, 18).frame(height: 43)
            }.frame(height: 43)
        }
    }

    private func countLabel(_ text: String) -> String {
        switch countMode {
        case "characters": "\(text.count) 字符"
        case "nonspace": "\(text.filter { !$0.isWhitespace }.count) 字符"
        case "cjk": "\(text.unicodeScalars.filter { $0.properties.isIdeographic }.count) 中文"
        default: "\(WritingMetrics.count(text)) 字"
        }
    }

    private var shortcutBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHGrid(rows: Array(repeating: GridItem(.fixed(30)), count: min(3, max(1, shortcutRows))), spacing: 8) {
                ForEach(InputShortcut.load(shortcutData)) { shortcut in
                    Button(shortcut.title) { controller.insert(shortcut.text) }
                        .font(.system(size: PaperTheme.bodySmallSize)).buttonStyle(.bordered).accessibilityLabel("插入 \(shortcut.title)")
                }
            }.padding(.horizontal, 18).padding(.vertical, 6)
        }.frame(height: CGFloat(min(3, max(1, shortcutRows))) * 30 + 12).background(PaperTheme.sidebar.opacity(0.6))
    }
}

struct EditorWorkspace: View {
    @EnvironmentObject private var library: LibraryStore
    @AppStorage("dualEditorRatio") private var ratio = 0.5
    @State private var secondActive = false
    var body: some View {
        GeometryReader { geometry in
            if let second = library.secondarySelection, second != library.selection,
               library.documents.contains(where: { $0.id == second && $0.deletedAt == nil }) {
                VStack(spacing: 0) {
                    HStack {
                        if geometry.size.width < 760 {
                            Picker("当前编辑器", selection: $secondActive) { Text("主文稿").tag(false); Text("第二篇").tag(true) }.pickerStyle(.segmented)
                        } else {
                            Text("双稿比例").font(.caption).foregroundStyle(.secondary)
                            Slider(value: $ratio, in: max(0.35, 340 / geometry.size.width)...min(0.65, 1 - 340 / geometry.size.width)).frame(maxWidth: 160)
                        }
                        Spacer()
                        Button { library.secondarySelection = nil } label: { Image(systemName: "xmark") }.buttonStyle(.plain).accessibilityLabel("关闭第二编辑器")
                    }.padding(.horizontal, 18).padding(.vertical, 8).background(PaperTheme.sidebar)
                    if geometry.size.width >= 760 {
                        HStack(spacing: 0) {
                            EditorView().id(library.selection).frame(width: max(340, min(geometry.size.width - 341, (geometry.size.width - 1) * ratio)))
                            Divider()
                            EditorView(documentID: second, isSecondary: true).id(second)
                        }
                    } else {
                        ZStack {
                            EditorView().id(library.selection).opacity(secondActive ? 0 : 1).allowsHitTesting(!secondActive).accessibilityHidden(secondActive)
                            EditorView(documentID: second, isSecondary: true).id(second).opacity(secondActive ? 1 : 0).allowsHitTesting(secondActive).accessibilityHidden(!secondActive)
                        }
                    }
                }
            } else { EditorView().id(library.selection) }
        }
    }
}

struct DocumentStatistics: View {
    let document: WritingDocument
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("文字的足迹").font(.system(size: 20, weight: .semibold, design: .serif))
            HStack(spacing: 30) {
                metric("字数", value: "\(document.wordCount)")
                metric("字符", value: "\(document.body.count)")
                metric("阅读约", value: "\(max(1, Int(ceil(Double(document.wordCount) / 300)))) 分钟")
            }
            Divider()
            Text("中文逐字统计，英文按单词统计。").font(.system(size: 11)).foregroundStyle(.secondary)
            Text("创建于 \(document.createdAt.formatted(date: .abbreviated, time: .shortened))")
                .font(.system(size: 11)).foregroundStyle(.secondary)
        }.padding(28).background(PaperTheme.background)
    }
    private func metric(_ label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(value).font(.system(size: 23, design: .serif)).foregroundStyle(PaperTheme.accent)
            Text(label).font(.system(size: 10)).foregroundStyle(.secondary)
        }
    }
}
