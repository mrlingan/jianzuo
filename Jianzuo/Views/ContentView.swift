import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @EnvironmentObject private var library: LibraryStore
    @State private var showSettings = false
    @State private var columnVisibility: NavigationSplitViewVisibility = .all
    @AppStorage("editorTheme") private var editorTheme = "classic"

    var body: some View {
        Group {
            #if os(macOS)
            HStack(spacing: 0) {
                if !library.focusMode {
                    LibrarySidebar(showSettings: $showSettings).frame(width: 208)
                    Divider()
                    DocumentListView().frame(width: 288)
                    Divider()
                }
                EditorWorkspace()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .background(PaperTheme.background)
            #else
            NavigationSplitView(columnVisibility: $columnVisibility, preferredCompactColumn: $library.preferredCompactColumn) {
                LibrarySidebar(showSettings: $showSettings)
                    .navigationTitle("简作")
            } content: {
                DocumentListView()
                    .navigationTitle(library.filter.title)
                    .navigationBarTitleDisplayMode(.inline)
            } detail: {
                EditorWorkspace()
                    .navigationTitle("")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button { showSettings = true } label: { Image(systemName: "slider.horizontal.3") }
                        }
                    }
            }
            .onChange(of: library.focusMode) { _, focused in
                columnVisibility = focused ? .detailOnly : .all
            }
            #endif
        }
        .animation(.easeInOut(duration: 0.2), value: library.focusMode)
        .sheet(isPresented: $showSettings) { WritingSettings() }
        .sheet(isPresented: $library.showBackup) { BackupView() }
        .sheet(isPresented: $library.showImportPreview) { ImportPreviewView() }
        .alert("需要留意", isPresented: Binding(get: { library.errorMessage != nil }, set: { if !$0 { library.errorMessage = nil } })) {
            Button("知道了", role: .cancel) { library.errorMessage = nil }
        } message: { Text(library.errorMessage ?? "") }
        .fileImporter(isPresented: $library.showImporter, allowedContentTypes: [.plainText, UTType(filenameExtension: "md") ?? .plainText], allowsMultipleSelection: true) { result in
            switch result {
            case .success(let urls): library.importURLs(urls)
            case .failure(let error): library.errorMessage = error.localizedDescription
            }
        }
        .fileExporter(isPresented: $library.showExporter, document: TextExport(text: library.exportingDocument?.body ?? ""), contentType: UTType(filenameExtension: "md") ?? .plainText, defaultFilename: (library.exportingDocument?.displayTitle ?? "文稿") + ".md") { result in
            if case .failure(let error) = result { library.errorMessage = error.localizedDescription }
        }
        #if os(macOS)
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.willTerminateNotification)) { _ in library.saveNow() }
        #endif
    }
}

struct LibrarySidebar: View {
    @EnvironmentObject private var library: LibraryStore
    @Binding var showSettings: Bool
    @State private var sheet: SidebarSheet?
    @AppStorage("editorTheme") private var editorTheme = "classic"

    private enum SidebarSheet: String, Identifiable {
        case notebooks, dashboard, scratch
        var id: String { rawValue }
    }

    var body: some View {
        VStack(spacing: 0) {
          ScrollView {
            VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "pencil.and.outline")
                    .font(.system(size: 25, weight: .light))
                    .foregroundStyle(PaperTheme.accent)
                VStack(alignment: .leading, spacing: 3) {
                    Text("简作").font(.system(size: 23, weight: .semibold, design: .serif))
                    Text("给文字一点空间").font(.system(size: 10)).foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 42)
            .padding(.bottom, 32)

            Button { library.createDocument() } label: {
                HStack(spacing: 9) {
                    Image(systemName: "plus")
                    Text("开始新的一页").font(.system(size: PaperTheme.navigationSize, weight: .medium))
                    Spacer()
                    #if os(macOS)
                    Text("⌘N").font(.system(size: 10)).opacity(0.65)
                    #endif
                }
                .padding(.horizontal, 13).padding(.vertical, 12)
                .foregroundStyle(PaperTheme.accentForeground)
                .background(PaperTheme.accent, in: RoundedRectangle(cornerRadius: 8))
            }.quietButton().padding(.horizontal, 16).padding(.bottom, 26)

            sidebarRow(.all, symbol: "square.stack", title: "全部文稿")
            sidebarRow(.favorites, symbol: "star", title: "我的收藏")
            HStack {
                Text("我的笔记本").font(.system(size: 10, weight: .medium)).foregroundStyle(.secondary)
                Spacer()
                Button { sheet = .notebooks } label: { Image(systemName: "folder.badge.plus") }.buttonStyle(.plain).accessibilityLabel("管理笔记本")
            }.padding(.horizontal, 27).padding(.top, 22).padding(.bottom, 10)
            ForEach(library.notebooks) { notebook in
                sidebarRow(.notebook(notebook), symbol: notebook.symbol, title: notebook.rawValue)
            }
            VStack(alignment: .leading, spacing: 14) {
                Button("速记", systemImage: "square.and.pencil") { sheet = .scratch }
                Button("每日写作", systemImage: "chart.bar") { sheet = .dashboard }
                Button("备份与恢复", systemImage: "externaldrive.badge.timemachine") { library.showBackup = true }
            }.font(.system(size: PaperTheme.navigationSize)).buttonStyle(.plain).padding(.horizontal, 27).padding(.top, 22)
            Spacer(minLength: 16)
            sidebarRow(.trash, symbol: "trash", title: "废纸篓")
            }
          }
            Divider().padding(.horizontal, 22).padding(.vertical, 14)
            HStack {
                Image(systemName: "internaldrive").font(.system(size: 11))
                Text("本地文稿库").font(.system(size: 10))
                Spacer()
                Button { showSettings = true } label: { Image(systemName: "slider.horizontal.3").font(.system(size: 13)) }
                    .quietButton().help("写作设置").accessibilityLabel("写作设置")
            }.foregroundStyle(.secondary).padding(.horizontal, 24).padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PaperTheme.sidebar)
        .sheet(item: $sheet) { sheet in
            switch sheet {
            case .notebooks: NotebookManagerView()
            case .dashboard: WritingDashboard()
            case .scratch: ScratchpadView()
            }
        }
    }

    private func sidebarRow(_ filter: LibraryFilter, symbol: String, title: String) -> some View {
        Button { library.selectFilter(filter) } label: {
            HStack(spacing: 11) {
                Image(systemName: symbol).font(.system(size: 14)).frame(width: 18)
                Text(title).font(.system(size: PaperTheme.navigationSize, weight: library.filter == filter ? .medium : .regular))
                Spacer()
                Text("\(library.count(in: filter))").font(.system(size: PaperTheme.captionSize, design: .monospaced)).foregroundStyle(.secondary)
            }
            .foregroundStyle(library.filter == filter ? PaperTheme.accent : PaperTheme.ink.opacity(0.7))
            .padding(.horizontal, 13).padding(.vertical, 11)
            .background(library.filter == filter ? PaperTheme.accent.opacity(0.09) : .clear, in: RoundedRectangle(cornerRadius: 7))
            .contentShape(Rectangle())
        }.quietButton().padding(.horizontal, 14).padding(.vertical, 2)
    }
}

struct DocumentListView: View {
    @EnvironmentObject private var library: LibraryStore
    @AppStorage("editorTheme") private var editorTheme = "classic"

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 5) {
                    Text(library.filter.title).font(.system(size: 20, weight: .semibold, design: .serif))
                    Text("\(library.visibleDocuments.count) 篇文稿 · 按最近编辑排序").font(.system(size: PaperTheme.captionSize)).foregroundStyle(.secondary)
                }
                Spacer()
                Menu {
                    Button("新建文稿", systemImage: "square.and.pencil") { library.createDocument() }
                    Button("导入文稿…", systemImage: "square.and.arrow.down") { library.importDocuments() }
                } label: { Image(systemName: "ellipsis").font(.system(size: 17)).frame(width: 24, height: 28) }
                .menuStyle(.borderlessButton)
                .fixedSize().accessibilityLabel("文稿操作")
            }.padding(.horizontal, 22).padding(.top, 42).padding(.bottom, 22)
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("搜索文字与灵感", text: $library.search).textFieldStyle(.plain).font(.system(size: PaperTheme.bodySmallSize))
                if !library.search.isEmpty {
                    Button { library.search = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }.quietButton()
                }
            }.padding(11).background(PaperTheme.sidebar.opacity(0.7), in: RoundedRectangle(cornerRadius: 7))
                .padding(.horizontal, 20).padding(.bottom, 20)
            if !library.allTags.isEmpty {
                Menu {
                    Button("全部标签") { library.tagFilter = "" }
                    ForEach(library.allTags, id: \.self) { tag in
                        Button("#" + tag) { library.tagFilter = tag; library.selection = library.visibleDocuments.first?.id }
                    }
                } label: { Label(library.tagFilter.isEmpty ? "按标签筛选" : "#" + library.tagFilter, systemImage: "tag") }
                    .font(.system(size: PaperTheme.bodySmallSize)).padding(.horizontal, 20).padding(.bottom, 14)
            }
            Divider().padding(.horizontal, 20)
            if library.visibleDocuments.isEmpty {
                ContentUnavailableView(library.search.isEmpty ? "这里还是空白" : "没有找到文稿", systemImage: library.filter == .trash ? "trash" : "doc.text.magnifyingglass", description: Text(library.search.isEmpty ? "让一个想法，成为第一行文字。" : "试试其他关键词。"))
                    .frame(maxHeight: .infinity)
            } else {
                #if os(macOS)
                ScrollView {
                    LazyVStack(spacing: 9) {
                        ForEach(library.visibleDocuments) { document in
                            Button { library.selection = document.id } label: { DocumentCard(document: document, selected: library.selection == document.id) }
                                .quietButton().contextMenu { documentMenu(document) }
                        }
                    }.padding(14)
                }
                #else
                List(selection: $library.selection) {
                    ForEach(library.visibleDocuments) { document in
                        NavigationLink(value: document.id) {
                            DocumentCard(document: document, selected: library.selection == document.id)
                        }
                        .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                        .listRowSeparator(.hidden)
                        .listRowBackground(PaperTheme.card)
                        .contextMenu { documentMenu(document) }
                    }
                }.listStyle(.plain).scrollContentBackground(.hidden)
                #endif
            }
            HStack {
                Circle().fill(PaperTheme.green).frame(width: 5, height: 5)
                Text("文字在这里，安心留下。").font(.system(size: 10)).foregroundStyle(.secondary)
                Spacer()
            }.padding(20)
        }.background(PaperTheme.card)
    }

    @ViewBuilder private func documentMenu(_ document: WritingDocument) -> some View {
        if document.deletedAt == nil {
            Button(document.isFavorite ? "取消收藏" : "收藏文稿", systemImage: "star") { library.toggleFavorite(document.id) }
            Button("创建副本", systemImage: "doc.on.doc") { library.duplicate(document) }
            Button("在第二编辑器打开", systemImage: "rectangle.split.2x1") { library.secondarySelection = document.id }
            Button("移到废纸篓", systemImage: "trash", role: .destructive) { library.moveToTrash(document.id) }
        } else {
            Button("恢复文稿", systemImage: "arrow.uturn.backward") { library.restore(document.id) }
        }
    }
}

struct DocumentCard: View {
    let document: WritingDocument
    let selected: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(document.displayTitle).font(.system(size: PaperTheme.cardTitleSize, weight: .semibold, design: .serif)).lineLimit(1)
                Spacer(minLength: 0)
                if document.isFavorite { Image(systemName: "star.fill").font(.system(size: 9)).foregroundStyle(PaperTheme.accent) }
            }
            Text(document.excerpt.isEmpty ? "空白的一页，等待你的第一句话。" : document.excerpt)
                .font(.system(size: PaperTheme.bodySmallSize)).foregroundStyle(.secondary).lineSpacing(4).lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack {
                Text(document.updatedAt, format: .dateTime.month().day())
                Text("·")
                Text("\(document.wordCount) 字")
                Spacer()
                Text(document.notebook.rawValue).padding(.horizontal, 6).padding(.vertical, 3)
                    .background(PaperTheme.green.opacity(0.08), in: RoundedRectangle(cornerRadius: 3))
            }.font(.system(size: PaperTheme.captionSize)).foregroundStyle(.secondary)
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(selected ? PaperTheme.accent.opacity(0.065) : .clear, in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(selected ? PaperTheme.accent.opacity(0.28) : .primary.opacity(0.055), lineWidth: 1))
            .contentShape(Rectangle())
    }
}
