import Foundation

struct Notebook: RawRepresentable, Codable, Hashable, Identifiable {
    var rawValue: String
    static let drafts = Notebook(rawValue: "文稿")
    static let ideas = Notebook(rawValue: "灵感")
    static let journal = Notebook(rawValue: "日记")
    static let allCases: [Notebook] = [.drafts, .ideas, .journal]
    init(rawValue: String) { self.rawValue = rawValue }
    init(from decoder: Decoder) throws { rawValue = try decoder.singleValueContainer().decode(String.self) }
    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    var id: String { rawValue }
    var symbol: String {
        switch self {
        case .drafts: "doc.text"
        case .ideas: "lightbulb"
        case .journal: "book.closed"
        default: "folder"
        }
    }
}

enum LibraryFilter: Hashable, Identifiable {
    case all, notebook(Notebook), favorites, trash

    var id: String { title }
    var title: String {
        switch self {
        case .all: "全部文稿"
        case .notebook(let notebook): notebook.rawValue
        case .favorites: "我的收藏"
        case .trash: "废纸篓"
        }
    }
}

struct WritingDocument: Identifiable, Codable, Equatable {
    var id = UUID()
    var title: String
    var body: String
    var notebook: Notebook = .drafts
    var createdAt = Date()
    var updatedAt = Date()
    var isFavorite = false
    var deletedAt: Date?
    var tags: [String] = []
    var isReadOnly = false
    var history: [DocumentRevision] = []

    enum CodingKeys: String, CodingKey {
        case id, title, body, notebook, createdAt, updatedAt, isFavorite, deletedAt, tags, isReadOnly, history
    }

    init(title: String, body: String, notebook: Notebook = .drafts, createdAt: Date = Date(), updatedAt: Date = Date(), isFavorite: Bool = false, deletedAt: Date? = nil) {
        self.title = title; self.body = body; self.notebook = notebook
        self.createdAt = createdAt; self.updatedAt = updatedAt; self.isFavorite = isFavorite; self.deletedAt = deletedAt
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(UUID.self, forKey: .id)
        title = try values.decode(String.self, forKey: .title)
        body = try values.decode(String.self, forKey: .body)
        notebook = try values.decode(Notebook.self, forKey: .notebook)
        createdAt = try values.decode(Date.self, forKey: .createdAt)
        updatedAt = try values.decode(Date.self, forKey: .updatedAt)
        isFavorite = try values.decode(Bool.self, forKey: .isFavorite)
        deletedAt = try values.decodeIfPresent(Date.self, forKey: .deletedAt)
        tags = try values.decodeIfPresent([String].self, forKey: .tags) ?? []
        isReadOnly = try values.decodeIfPresent(Bool.self, forKey: .isReadOnly) ?? false
        history = try values.decodeIfPresent([DocumentRevision].self, forKey: .history) ?? []
    }

    mutating func checkpoint(force: Bool = false, now: Date = Date()) {
        if let previous = history.first {
            guard previous.title != title || previous.body != body else { return }
            if !force && now.timeIntervalSince(previous.createdAt) < 30 { return }
        }
        history.insert(DocumentRevision(title: title, body: body, createdAt: now), at: 0)
        history = Array(history.prefix(40))
    }

    var displayTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "无标题文稿" : title }
    var wordCount: Int { WritingMetrics.count(body) }
    var excerpt: String {
        let cleaned = body.replacingOccurrences(of: #"[#>*`_\[\]]"#, with: "", options: .regularExpression)
        return cleaned.split(whereSeparator: \.isNewline).joined(separator: " ").trimmingCharacters(in: .whitespaces)
    }
}

struct DocumentRevision: Identifiable, Codable, Equatable {
    var id = UUID()
    var title: String
    var body: String
    var createdAt = Date()
}

enum WritingMetrics {
    /// CJK characters count individually; Latin words and numbers count as words.
    static func count(_ text: String) -> Int {
        let wordCharacter = #"[\p{L}\p{N}&&[^\p{Han}\p{Hiragana}\p{Katakana}\p{Hangul}]]"#
        let pattern = #"[\p{Han}\p{Hiragana}\p{Katakana}\p{Hangul}]|"# + wordCharacter + "+(?:['’\\-]" + wordCharacter + "+)*"
        guard let expression = try? NSRegularExpression(pattern: pattern) else { return 0 }
        return expression.numberOfMatches(in: text, range: NSRange(text.startIndex..., in: text))
    }
}

struct LibrarySnapshot: Codable {
    var version = 2
    var documents: [WritingDocument]
    var notebooks: [Notebook] = Notebook.allCases
    var dailyWords: [String: Int] = [:]
    var scratch = ""

    enum CodingKeys: String, CodingKey { case version, documents, notebooks, dailyWords, scratch }
    init(documents: [WritingDocument], notebooks: [Notebook] = Notebook.allCases, dailyWords: [String: Int] = [:], scratch: String = "") {
        self.documents = documents; self.notebooks = notebooks; self.dailyWords = dailyWords; self.scratch = scratch
    }
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        version = try values.decode(Int.self, forKey: .version)
        documents = try values.decode([WritingDocument].self, forKey: .documents)
        if version == 1 {
            notebooks = Notebook.allCases
            for document in documents where !notebooks.contains(document.notebook) { notebooks.append(document.notebook) }
        } else {
            notebooks = try values.decode([Notebook].self, forKey: .notebooks)
            dailyWords = try values.decode([String: Int].self, forKey: .dailyWords)
            scratch = try values.decode(String.self, forKey: .scratch)
        }
    }

    func validated() throws -> Self {
        guard (1...2).contains(version), !notebooks.isEmpty,
              Set(notebooks).count == notebooks.count,
              notebooks.allSatisfy({ Self.validNotebookName($0.rawValue) }),
              Set(documents.map(\.id)).count == documents.count,
              documents.allSatisfy({ notebooks.contains($0.notebook) && $0.history.count <= 40 && Set($0.history.map(\.id)).count == $0.history.count }),
              dailyWords.allSatisfy({ WritingDay.isValid($0.key) && (0...1_000_000_000).contains($0.value) }) else {
            throw CocoaError(.coderReadCorrupt)
        }
        return self
    }

    static func validNotebookName(_ name: String) -> Bool {
        !name.isEmpty && name == name.trimmingCharacters(in: .whitespacesAndNewlines) && name.count <= 40 && !["全部文稿", "我的收藏", "废纸篓"].contains(name)
    }
}

enum LibraryPersistence {
    static func read(from url: URL) throws -> [WritingDocument] {
        try readSnapshot(from: url).documents
    }

    static func readSnapshot(from url: URL) throws -> LibrarySnapshot {
        guard FileManager.default.fileExists(atPath: url.path) else { return LibrarySnapshot(documents: []) }
        return try JSONDecoder().decode(LibrarySnapshot.self, from: Data(contentsOf: url)).validated()
    }

    static func write(_ documents: [WritingDocument], to url: URL) throws {
        var notebooks = Notebook.allCases
        for document in documents where !notebooks.contains(document.notebook) { notebooks.append(document.notebook) }
        try write(LibrarySnapshot(documents: documents, notebooks: notebooks), to: url)
    }

    static func write(_ snapshot: LibrarySnapshot, to url: URL) throws {
        var snapshot = try snapshot.validated()
        snapshot.version = 2
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(snapshot.validated()).write(to: url, options: .atomic)
    }
}
