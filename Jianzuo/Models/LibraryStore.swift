import SwiftUI
import UniformTypeIdentifiers
#if os(macOS)
import AppKit
#endif

@MainActor
final class LibraryStore: ObservableObject {
    @Published private(set) var documents: [WritingDocument] = []
    @Published private(set) var notebooks = Notebook.allCases
    @Published private(set) var dailyWords: [String: Int] = [:]
    @Published var scratch = "" { didSet { if scratch != oldValue { scheduleSave() } } }
    @Published var tagFilter = ""
    @Published var secondarySelection: UUID?
    @Published var showBackup = false
    @Published var importCandidates: [ImportCandidate] = []
    @Published var showImportPreview = false
    @Published var selection: UUID?
    @Published var filter: LibraryFilter = .all
    @Published var search = ""
    @Published var errorMessage: String?
    @Published private(set) var saveState = "已保存到本机"
    @Published var focusMode = false
    @Published var showPreview = false
    @Published var showImporter = false
    @Published var showExporter = false
    @Published private(set) var exportingDocument: WritingDocument?
    @Published var preferredCompactColumn: NavigationSplitViewColumn = .content
    private var pendingSave: Task<Void, Never>?
    private let storageURL: URL
    private var canSave = true
    private var lastBackupAt = Date.distantPast
    private var lastSavedSnapshot: LibrarySnapshot?

    init(storageURL: URL? = nil, seed: Bool = true) {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        #if DEBUG
        let previewURL = ProcessInfo.processInfo.environment["JIANZUO_LIBRARY_PATH"].map { URL(fileURLWithPath: $0) }
        #else
        let previewURL: URL? = nil
        #endif
        self.storageURL = storageURL ?? previewURL ?? support.appendingPathComponent("Jianzuo/library.json")
        do {
            let exists = FileManager.default.fileExists(atPath: self.storageURL.path)
            let snapshot = try LibraryPersistence.readSnapshot(from: self.storageURL)
            documents = snapshot.documents
            notebooks = snapshot.notebooks
            dailyWords = snapshot.dailyWords
            scratch = snapshot.scratch
            lastSavedSnapshot = exists ? snapshot : nil
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
    var isWritable: Bool { canSave }
    var snapshot: LibrarySnapshot { LibrarySnapshot(documents: documents, notebooks: notebooks, dailyWords: dailyWords, scratch: scratch) }
    var allTags: [String] { Array(Set(documents.filter { $0.deletedAt == nil }.flatMap(\.tags))).sorted() }
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
            return matchesFilter && (tagFilter.isEmpty || document.tags.contains(tagFilter)) && (query.isEmpty || document.title.localizedStandardContains(query) || document.body.localizedStandardContains(query) || document.tags.contains(where: { $0.localizedStandardContains(query) }))
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
        tagFilter = ""
        selection = visibleDocuments.first?.id
        preferredCompactColumn = .content
    }

    @discardableResult
    func createDocument(title: String = "", body: String = "", notebook: Notebook? = nil) -> UUID {
        guard canSave else { errorMessage = "当前文稿库读取失败，原文件已保留。请先修复原库，再创建或导入文稿。"; return UUID() }
        let destination: Notebook
        if let notebook { destination = notebook }
        else if case .notebook(let current) = filter { destination = current }
        else { destination = notebooks.first ?? .drafts }
        guard notebooks.contains(destination) else { return UUID() }
        let document = WritingDocument(title: title, body: body, notebook: destination)
        documents.insert(document, at: 0)
        if filter == .trash || filter == .favorites { filter = .all }
        search = ""
        tagFilter = ""
        selection = document.id
        preferredCompactColumn = .detail
        showPreview = false
        scheduleSave()
        return document.id
    }

    func update(_ id: UUID, title: String? = nil, body: String? = nil, notebook: Notebook? = nil, countWriting: Bool = true) {
        guard let index = documents.firstIndex(where: { $0.id == id }), documents[index].deletedAt == nil else { return }
        if documents[index].isReadOnly && (title != nil || body != nil) { return }
        if let notebook, !notebooks.contains(notebook) { return }
        let contentChanged = (title != nil && title != documents[index].title) || (body != nil && body != documents[index].body)
        guard contentChanged || (notebook != nil && notebook != documents[index].notebook) else { return }
        if contentChanged { documents[index].checkpoint() }
        if countWriting, let body {
            let added = max(0, WritingMetrics.count(body) - documents[index].wordCount)
            if added > 0 { dailyWords[WritingDay.key()] = min(1_000_000_000, (dailyWords[WritingDay.key()] ?? 0) + min(added, 1_000_000_000)) }
        }
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
        if secondarySelection == id { secondarySelection = nil }
        selection = visibleDocuments.first?.id
        scheduleSave()
    }

    func restore(_ id: UUID) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        documents[index].deletedAt = nil
        documents[index].updatedAt = Date()
        filter = .all
        search = ""
        tagFilter = ""
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
        let id = createDocument(title: document.displayTitle + " · 副本", body: document.body, notebook: document.notebook)
        if let index = documents.firstIndex(where: { $0.id == id }) { documents[index].tags = document.tags }
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
            if let previous = lastSavedSnapshot, previous.documents != documents || previous.notebooks != notebooks || previous.scratch != scratch || previous.dailyWords != dailyWords {
                if Date().timeIntervalSince(lastBackupAt) >= 60 {
                    let folder = storageURL.deletingLastPathComponent().appendingPathComponent("Backups")
                    try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                    let url = folder.appendingPathComponent("\(Int(Date().timeIntervalSince1970 * 1000))-\(UUID().uuidString.prefix(6)).jzb")
                    try BackupCodec.encode(previous).write(to: url, options: .atomic)
                    lastBackupAt = Date()
                    for old in automaticBackups.dropFirst(10) { try FileManager.default.removeItem(at: old) }
                }
            }
            try LibraryPersistence.write(snapshot, to: storageURL)
            lastSavedSnapshot = snapshot
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
        guard canSave else { errorMessage = "文稿库读取失败，暂时无法导入。原文件已保留。"; return }
        var candidates: [ImportCandidate] = []
        var failures: [String] = []
        for url in urls {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                let body = try String(contentsOf: url, encoding: .utf8)
                candidates.append(ImportCandidate(title: url.deletingPathExtension().lastPathComponent, body: body, filename: url.lastPathComponent))
            } catch {
                failures.append(url.lastPathComponent)
            }
        }
        importCandidates = candidates.sorted { $0.filename.localizedStandardCompare($1.filename) == .orderedAscending }
        showImportPreview = !candidates.isEmpty
        if !failures.isEmpty { errorMessage = "以下文件无法读取，请使用 UTF-8 文本：\n" + failures.joined(separator: "\n") }
    }

    func commitImport(to notebook: Notebook) {
        guard notebooks.contains(notebook) else { return }
        let selected = importCandidates.filter(\.included)
        guard !selected.isEmpty else { return }
        for candidate in selected.reversed() { createDocument(title: candidate.title, body: candidate.body, notebook: notebook) }
        filter = .notebook(notebook)
        importCandidates = []
        showImportPreview = false
        saveNow()
    }

    func addNotebook(_ name: String) throws {
        let notebook = Notebook(rawValue: name.trimmingCharacters(in: .whitespacesAndNewlines))
        guard LibrarySnapshot.validNotebookName(notebook.rawValue), !notebooks.contains(notebook) else { throw WritingToolError.invalid("笔记本名称须为 1–40 个字符，且不能重复或使用导航名称。") }
        notebooks.append(notebook)
        scheduleSave()
    }

    func renameNotebook(_ notebook: Notebook, to name: String) throws {
        let value = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value != notebook.rawValue else { return }
        guard let index = notebooks.firstIndex(of: notebook) else { return }
        let replacement = Notebook(rawValue: value)
        guard LibrarySnapshot.validNotebookName(value), !notebooks.contains(replacement) else { throw WritingToolError.invalid("笔记本名称为空、重复或过长。") }
        notebooks[index] = replacement
        for index in documents.indices where documents[index].notebook == notebook { documents[index].notebook = replacement }
        if filter == .notebook(notebook) { filter = .notebook(replacement) }
        scheduleSave()
    }

    func deleteNotebook(_ notebook: Notebook) throws {
        guard notebooks.count > 1, !documents.contains(where: { $0.notebook == notebook }) else { throw WritingToolError.invalid("请先移出所有文稿（含废纸篓），并保留至少一个笔记本。") }
        notebooks.removeAll { $0 == notebook }
        if filter == .notebook(notebook) { selectFilter(.all) }
        scheduleSave()
    }

    func setTags(_ id: UUID, source: String) {
        guard let index = documents.firstIndex(where: { $0.id == id && $0.deletedAt == nil }) else { return }
        documents[index].tags = WritingTools.tags(source)
        scheduleSave()
    }

    func toggleReadOnly(_ id: UUID) {
        guard let index = documents.firstIndex(where: { $0.id == id && $0.deletedAt == nil }) else { return }
        documents[index].isReadOnly.toggle()
        scheduleSave()
    }

    func checkpoint(_ id: UUID) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        documents[index].checkpoint(force: true)
        scheduleSave()
    }

    func restoreRevision(_ revision: DocumentRevision, to id: UUID, asCopy: Bool = false) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        if asCopy {
            createDocument(title: revision.title + " · 恢复", body: revision.body, notebook: documents[index].notebook)
        } else {
            guard documents[index].deletedAt == nil, !documents[index].isReadOnly else { return }
            checkpoint(id)
            update(id, title: revision.title, body: revision.body, countWriting: false)
        }
        saveNow()
    }

    var automaticBackups: [URL] {
        let folder = storageURL.deletingLastPathComponent().appendingPathComponent("Backups")
        return ((try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)) ?? []).filter { $0.pathExtension == "jzb" }.sorted { $0.lastPathComponent > $1.lastPathComponent }
    }

    func mergeBackup(_ incoming: LibrarySnapshot, includeStatistics: Bool) throws {
        guard canSave else { throw WritingToolError.invalid("当前文稿库读取失败，请先保留并修复原库，避免覆盖。") }
        let incoming = try incoming.validated()
        for notebook in incoming.notebooks where !notebooks.contains(notebook) { notebooks.append(notebook) }
        var copies = incoming.documents
        for index in copies.indices { copies[index].id = UUID() }
        documents.insert(contentsOf: copies, at: 0)
        if !incoming.scratch.isEmpty { scratch += (scratch.isEmpty ? "" : "\n\n") + incoming.scratch }
        if includeStatistics { for (day, count) in incoming.dailyWords { dailyWords[day] = max(dailyWords[day] ?? 0, count) } }
        filter = .all; search = ""; tagFilter = ""
        selection = copies.first(where: { $0.deletedAt == nil })?.id ?? selection
        saveNow()
    }

    func exportCurrentDocument() {
        guard let document = currentDocument else { return }
        export(document)
    }

    func export(_ document: WritingDocument) {
        exportingDocument = document
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
