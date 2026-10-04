import Foundation

enum Notebook: String, Codable, CaseIterable, Identifiable {
    case drafts = "文稿"
    case ideas = "灵感"
    case journal = "日记"

    var id: String { rawValue }
    var symbol: String {
        switch self {
        case .drafts: "doc.text"
        case .ideas: "lightbulb"
        case .journal: "book.closed"
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

    var displayTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "无标题文稿" : title }
    var wordCount: Int { WritingMetrics.count(body) }
    var excerpt: String {
        let cleaned = body.replacingOccurrences(of: #"[#>*`_\[\]]"#, with: "", options: .regularExpression)
        return cleaned.split(whereSeparator: \.isNewline).joined(separator: " ").trimmingCharacters(in: .whitespaces)
    }
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
    var version = 1
    var documents: [WritingDocument]
}

enum LibraryPersistence {
    static func read(from url: URL) throws -> [WritingDocument] {
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        let snapshot = try JSONDecoder().decode(LibrarySnapshot.self, from: Data(contentsOf: url))
        guard snapshot.version == 1 else {
            throw CocoaError(.coderReadCorrupt)
        }
        return snapshot.documents
    }

    static func write(_ documents: [WritingDocument], to url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(LibrarySnapshot(documents: documents)).write(to: url, options: .atomic)
    }
}
