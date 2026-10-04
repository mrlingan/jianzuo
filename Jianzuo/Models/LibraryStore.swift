import SwiftUI
import UniformTypeIdentifiers
#if os(macOS)
import AppKit
#endif

@MainActor
final class LibraryStore: ObservableObject {
    @Published private(set) var documents: [WritingDocument] = []
    @Published var selection: UUID?
    @Published var filter: LibraryFilter = .all
    @Published var search = ""
    @Published var errorMessage: String?
    @Published private(set) var saveState = "已保存到本机"
    @Published var focusMode = false
    @Published var showPreview = false
    @Published var showImporter = false
    @Published var showExporter = false
    @Published var preferredCompactColumn: NavigationSplitViewColumn = .content
    private var pendingSave: Task<Void, Never>?
    private let storageURL: URL
    private var canSave = true

    init(storageURL: URL? = nil, seed: Bool = true) {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.storageURL = storageURL ?? support.appendingPathComponent("Jianzuo/library.json")
        do {
            let exists = FileManager.default.fileExists(atPath: self.storageURL.path)
            documents = try LibraryPersistence.read(from: self.storageURL)
            if !exists && seed {
                documents = Self.welcomeDocuments
                saveNow()
            }
            selection = documents.first(where: { $0.deletedAt == nil })?.id
        } catch {
            canSave = false
            saveState = "读取失败"
            errorMessage = "无法读取文稿库，原文件已保留，未做覆盖。\n\(self.storageURL.path)\n\(error.localizedDescription)"
        }
    }

    var currentDocument: WritingDocument? { documents.first { $0.id == selection } }
    var visibleDocuments: [WritingDocument] {
        documents.filter { document in
            let matchesFilter: Bool
            switch filter {
            case .all: matchesFilter = document.deletedAt == nil
            case .notebook(let notebook): matchesFilter = document.deletedAt == nil && document.notebook == notebook
            case .favorites: matchesFilter = document.deletedAt == nil && document.isFavorite
            case .trash: matchesFilter = document.deletedAt != nil
            }
            let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
            return matchesFilter && (query.isEmpty || document.title.localizedStandardContains(query) || document.body.localizedStandardContains(query))
        }.sorted { $0.updatedAt > $1.updatedAt }
    }

    var totalWords: Int { documents.filter { $0.deletedAt == nil }.reduce(0) { $0 + $1.wordCount } }
    func count(in filter: LibraryFilter) -> Int {
        documents.filter {
            switch filter {
            case .all: $0.deletedAt == nil
            case .favorites: $0.deletedAt == nil && $0.isFavorite
            case .trash: $0.deletedAt != nil
            case .notebook(let notebook): $0.deletedAt == nil && $0.notebook == notebook
            }
        }.count
    }

    func selectFilter(_ newFilter: LibraryFilter) {
        filter = newFilter
        selection = visibleDocuments.first?.id
        preferredCompactColumn = .content
    }

    @discardableResult
    func createDocument(title: String = "", body: String = "", notebook: Notebook? = nil) -> UUID {
        let destination: Notebook
        if let notebook { destination = notebook }
        else if case .notebook(let current) = filter { destination = current }
        else { destination = .drafts }
        let document = WritingDocument(title: title, body: body, notebook: destination)
        documents.insert(document, at: 0)
        if filter == .trash || filter == .favorites { filter = .all }
        search = ""
        selection = document.id
        preferredCompactColumn = .detail
        showPreview = false
        scheduleSave()
        return document.id
    }

    func update(_ id: UUID, title: String? = nil, body: String? = nil, notebook: Notebook? = nil) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        if let title { documents[index].title = title }
        if let body { documents[index].body = body }
        if let notebook { documents[index].notebook = notebook }
        documents[index].updatedAt = Date()
        scheduleSave()
    }

    func toggleFavorite(_ id: UUID) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        documents[index].isFavorite.toggle()
        scheduleSave()
    }

    func moveToTrash(_ id: UUID) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        documents[index].deletedAt = Date()
        documents[index].updatedAt = Date()
        selection = visibleDocuments.first?.id
        scheduleSave()
    }

    func restore(_ id: UUID) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        documents[index].deletedAt = nil
        documents[index].updatedAt = Date()
        filter = .all
        search = ""
        selection = id
        preferredCompactColumn = .detail
        scheduleSave()
    }

    func permanentlyDelete(_ id: UUID) {
        guard documents.contains(where: { $0.id == id && $0.deletedAt != nil }) else { return }
        documents.removeAll { $0.id == id }
        selection = visibleDocuments.first?.id
        scheduleSave()
    }

    func duplicate(_ document: WritingDocument) {
        createDocument(title: document.displayTitle + " · 副本", body: document.body, notebook: document.notebook)
        filter = .notebook(document.notebook)
    }

    private func scheduleSave() {
        guard canSave else { return }
        saveState = "正在保存…"
        pendingSave?.cancel()
        pendingSave = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(450))
            guard !Task.isCancelled else { return }
            self?.saveNow()
        }
    }

    func saveNow() {
        pendingSave?.cancel()
        guard canSave else { return }
        do {
            try LibraryPersistence.write(documents, to: storageURL)
            saveState = "已保存到本机"
        } catch {
            saveState = "保存失败"
            errorMessage = "文稿保存失败，请检查磁盘空间或文件权限。\n\(error.localizedDescription)"
        }
    }

    func importDocuments() {
        #if os(macOS)
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.plainText, UTType(filenameExtension: "md") ?? .plainText]
        panel.allowsMultipleSelection = true
        panel.message = "导入 Markdown 或纯文本文稿"
        guard panel.runModal() == .OK else { return }
        importURLs(panel.urls)
        #else
        showImporter = true
        #endif
    }

    func importURLs(_ urls: [URL]) {
        for url in urls {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                let body = try String(contentsOf: url, encoding: .utf8)
                createDocument(title: url.deletingPathExtension().lastPathComponent, body: body)
            } catch {
                errorMessage = "无法导入 \(url.lastPathComponent)，请使用 UTF-8 编码的文本。\n\(error.localizedDescription)"
            }
        }
    }

    func exportCurrentDocument() {
        guard let document = currentDocument else { return }
        #if os(macOS)
        let panel = NSSavePanel()
        panel.allowedContentTypes = [UTType(filenameExtension: "md") ?? .plainText, .plainText]
        panel.nameFieldStringValue = document.displayTitle.replacingOccurrences(of: "/", with: "-") + ".md"
        panel.message = "导出文稿正文为 Markdown 或纯文本"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do { try document.body.write(to: url, atomically: true, encoding: .utf8) }
        catch { errorMessage = "导出失败：\(error.localizedDescription)" }
        #else
        _ = document
        showExporter = true
        #endif
    }

    static var welcomeDocuments: [WritingDocument] {
        let now = Date()
        return [
            WritingDocument(title: "把日子写成诗", body: "# 把日子写成诗\n\n清晨的光落在窗边，咖啡还冒着热气。城市尚未完全醒来，而这一刻，只属于你和眼前的文字。\n\n我们总以为，写作需要一个郑重其事的开始。一个完美的题目，一段漂亮的开场，或是一整块不被打扰的时间。\n\n但有时候，写作只是记下此刻。\n\n## 从一个小小的观察开始\n\n窗外的树又长高了一点。楼下的面包店换了新的招牌。昨天读到的一句话，还在心里轻轻回响。\n\n这些细小的事物，是生活留给我们的线索。把它们拾起来，放在纸上，就有了故事。\n\n> 不必等灵感到来。先写下第一句，剩下的会慢慢发生。\n\n今天想写的事：\n\n- 一次没有目的的散步\n- 一个许久未见的人\n- 一段想要留住的时光\n\n写下来吧。从这里开始。", updatedAt: now, isFavorite: true),
            WritingDocument(title: "一些闪闪发光的念头", body: "# 一些闪闪发光的念头\n\n- 写一封给十年后自己的信\n- 收集雨天的声音\n- 一个住在灯塔里的人的故事\n\n灵感不必完整，先把它留下。", notebook: .ideas, updatedAt: now.addingTimeInterval(-3600)),
            WritingDocument(title: "今天，也有值得记录的事", body: "今天最想记住的一个瞬间：\n\n\n让我心生感谢的三件小事：\n\n1. \n2. \n3. \n\n明天想尝试的事：\n", notebook: .journal, updatedAt: now.addingTimeInterval(-7200))
        ]
    }
}
