import SwiftUI

struct EditorView: View {
    @EnvironmentObject private var library: LibraryStore
    var documentID: UUID?
    @AppStorage("editorFontSize") private var fontSize = 17.0
    @AppStorage("editorFont") private var editorFont = "serif"
    @AppStorage("lineSpacing") private var lineSpacing = 9.0
    @AppStorage("wordGoal") private var wordGoal = 800
    @State private var showStatistics = false
    @State private var confirmDelete = false
    @FocusState private var titleFocused: Bool

    private var document: WritingDocument? {
        if let documentID { return library.documents.first { $0.id == documentID } }
        return library.currentDocument
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
                        VStack(alignment: .leading, spacing: 0) {
                            HStack(spacing: 8) {
                                Text(document.notebook.rawValue.uppercased()).tracking(2)
                                Text("/")
                                Text(document.createdAt, format: .dateTime.year().month().day())
                            }.font(.system(size: 10)).foregroundStyle(.tertiary)
                                .padding(.top, geometry.size.width > 650 ? 44 : 26).padding(.bottom, 16)
                            TextField("无标题文稿", text: Binding(get: { document.title }, set: { library.update(document.id, title: $0) }), axis: .vertical)
                                .font(.system(size: geometry.size.width > 650 ? 31 : 27, weight: .semibold, design: .serif))
                                .textFieldStyle(.plain).lineLimit(1...3).focused($titleFocused)
                                .disabled(document.deletedAt != nil)
                                .padding(.bottom, 16)
                            HStack(spacing: 9) {
                                Image(systemName: "clock").font(.system(size: 10))
                                Text("最近编辑 \(document.updatedAt.formatted(.dateTime.month().day().hour().minute()))")
                                Circle().frame(width: 2, height: 2)
                                Text("\(document.wordCount) 字")
                            }.font(.system(size: 10)).foregroundStyle(.tertiary).padding(.bottom, 24)
                            Rectangle().fill(.primary.opacity(0.07)).frame(height: 1)
                            if library.showPreview {
                                ScrollView {
                                    MarkdownPreview(text: document.body, fontSize: fontSize, fontStyle: editorFont, lineSpacing: lineSpacing)
                                        .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 24)
                                }
                            } else {
                                ZStack(alignment: .topLeading) {
                                    NativeWritingEditor(text: Binding(get: { document.body }, set: { library.update(document.id, body: $0) }), fontSize: fontSize, fontStyle: editorFont, lineSpacing: lineSpacing, isEditable: document.deletedAt == nil)
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
                    editorFooter(document)
                }
                .confirmationDialog("彻底删除这篇文稿？此操作无法撤销。", isPresented: $confirmDelete, titleVisibility: .visible) {
                    Button("彻底删除", role: .destructive) { library.permanentlyDelete(document.id) }
                    Button("取消", role: .cancel) {}
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
        HStack(spacing: 18) {
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
            Button { library.showPreview.toggle() } label: {
                Image(systemName: library.showPreview ? "pencil" : "eye")
            }.help("编辑 / 预览 Markdown ⇧⌘P").accessibilityLabel(library.showPreview ? "编辑 Markdown" : "预览 Markdown")
            if document.deletedAt == nil {
                Button { library.toggleFavorite(document.id) } label: {
                    Image(systemName: document.isFavorite ? "star.fill" : "star")
                        .foregroundStyle(document.isFavorite ? PaperTheme.accent : .secondary)
                }.help("收藏文稿").accessibilityLabel(document.isFavorite ? "取消收藏" : "收藏文稿")
            }
            Rectangle().fill(.primary.opacity(0.1)).frame(width: 1, height: 16)
            Button { library.selection = document.id; library.exportCurrentDocument() } label: { Image(systemName: "square.and.arrow.up") }
                .help("导出文稿 ⇧⌘E").accessibilityLabel("导出文稿")
            Menu {
                Button("文稿统计", systemImage: "chart.bar") { showStatistics = true }
                if document.deletedAt == nil {
                    Menu("移到笔记本") {
                        ForEach(Notebook.allCases) { notebook in
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
            .padding(.horizontal, 26).frame(height: 67)
            .popover(isPresented: $showStatistics) { DocumentStatistics(document: document) }
    }

    private func editorFooter(_ document: WritingDocument) -> some View {
        VStack(spacing: 0) {
            Divider().opacity(0.6)
            HStack(spacing: 7) {
                Circle().fill(library.saveState == "保存失败" || library.saveState == "读取失败" ? .red : PaperTheme.green).frame(width: 5, height: 5)
                Text(library.saveState)
                Spacer()
                Button { showStatistics = true } label: { Text("\(document.wordCount) 字") }.quietButton()
                if wordGoal > 0 {
                    Text("/").foregroundStyle(.tertiary)
                    Text("目标 \(wordGoal)")
                    ProgressView(value: min(Double(document.wordCount) / Double(wordGoal), 1))
                        .tint(PaperTheme.green).frame(width: 54)
                }
                #if os(macOS)
                Text("·").padding(.horizontal, 4)
                Text(library.showPreview ? "预览" : "Markdown")
                #endif
            }.font(.system(size: PaperTheme.captionSize)).foregroundStyle(.secondary).padding(.horizontal, 26).frame(height: 43)
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
