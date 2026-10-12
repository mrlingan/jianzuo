import Foundation
import CryptoKit

struct ImportCandidate: Identifiable {
    var id = UUID()
    var title: String
    var body: String
    var filename: String
    var included = true
}


enum WritingDay {
    static func key(_ date: Date = Date()) -> String {
        let parts = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
    static func isValid(_ key: String) -> Bool {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        guard let date = formatter.date(from: key) else { return false }
        return formatter.string(from: date) == key
    }
}

enum WritingToolError: LocalizedError {
    case invalid(String)
    var errorDescription: String? { switch self { case .invalid(let message): message } }
}

enum BackupCodec {
    private struct Envelope: Codable {
        var format = "jianzuo-apple-backup"
        var version = 1
        var createdAt = Date()
        var sha256: String
        var payload: String
    }
    static func encode(_ snapshot: LibrarySnapshot) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .prettyPrinted]
        let data = try encoder.encode(snapshot.validated())
        return try encoder.encode(Envelope(sha256: digest(data), payload: String(decoding: data, as: UTF8.self)))
    }
    static func decode(_ data: Data) throws -> LibrarySnapshot {
        let envelope = try JSONDecoder().decode(Envelope.self, from: data)
        guard envelope.format == "jianzuo-apple-backup", envelope.version == 1 else {
            throw WritingToolError.invalid("请选择 Apple 版简作的 .jzb 备份；安卓备份请先导出 Markdown / TXT。")
        }
        let payload = Data(envelope.payload.utf8)
        guard digest(payload) == envelope.sha256 else { throw WritingToolError.invalid("备份校验失败，文件可能不完整。文稿库未改变。") }
        return try JSONDecoder().decode(LibrarySnapshot.self, from: payload).validated()
    }
    private static func digest(_ data: Data) -> String { SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() }
}

struct TextMatch: Identifiable {
    var id: Int { range.location }
    let range: NSRange
    let excerpt: String
}

struct OutlineHeading: Identifiable {
    var id: Int { range.location }
    let range: NSRange
    let level: Int
    let title: String
}

enum WritingTools {
    static func matches(in source: String, query: String, regex: Bool, caseSensitive: Bool) throws -> [TextMatch] {
        guard !query.isEmpty else { return [] }
        let expression = try NSRegularExpression(pattern: regex ? query : NSRegularExpression.escapedPattern(for: query), options: caseSensitive ? [] : [.caseInsensitive])
        let text = source as NSString
        var matches: [TextMatch] = []
        var exceeded = false
        var timedOut = false
        let deadline = Date().addingTimeInterval(1)
        expression.enumerateMatches(in: source, options: [.reportProgress], range: NSRange(location: 0, length: text.length)) { result, _, stop in
            if Date() > deadline { timedOut = true; stop.pointee = true; return }
            guard let range = result?.range else { return }
            if matches.count >= 10_000 { exceeded = true; stop.pointee = true; return }
            let start = max(0, range.location - 24)
            let end = min(text.length, NSMaxRange(range) + 40)
            matches.append(TextMatch(range: range, excerpt: text.substring(with: NSRange(location: start, length: end - start)).replacingOccurrences(of: "\n", with: " ↵ ")))
        }
        if exceeded { throw WritingToolError.invalid("匹配超过 10,000 处，请缩小查找范围。") }
        if timedOut { throw WritingToolError.invalid("查找耗时过长，请简化正则表达式或缩小文稿。") }
        return matches
    }

    /// Apply ranges only to the snapshot that produced them. Replacements are literal, including $ and backslashes.
    static func replace(source: String, expected: String, matches: [TextMatch], selected: Set<Int>, replacement: String) throws -> String {
        guard source == expected else { throw WritingToolError.invalid("正文已变化，请重新查找后再替换。") }
        let text = source as NSString
        var cursor = 0
        var result = ""
        for match in matches {
            guard match.range.location >= cursor, NSMaxRange(match.range) <= text.length else { throw WritingToolError.invalid("匹配位置已失效。") }
            result += text.substring(with: NSRange(location: cursor, length: match.range.location - cursor))
            result += selected.contains(match.id) ? replacement : text.substring(with: match.range)
            cursor = NSMaxRange(match.range)
        }
        return result + text.substring(from: cursor)
    }

    static func outline(_ source: String) -> [OutlineHeading] {
        let expression = try! NSRegularExpression(pattern: #"^ {0,3}(#{1,6})\s+(.+?)\s*#*\s*$"#)
        var headings: [OutlineHeading] = []
        var offset = 0
        var fence: Character?
        var fenceLength = 0
        for line in source.components(separatedBy: "\n") {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if let marker = trimmed.first, marker == "`" || marker == "~" {
                let length = trimmed.prefix(while: { $0 == marker }).count
                if length >= 3 {
                    if fence == nil { fence = marker; fenceLength = length }
                    else if fence == marker && length >= fenceLength && trimmed.dropFirst(length).trimmingCharacters(in: .whitespaces).isEmpty { fence = nil }
                }
            } else if fence == nil {
                let text = line as NSString
                if let match = expression.firstMatch(in: line, range: NSRange(location: 0, length: text.length)) {
                    headings.append(OutlineHeading(range: NSRange(location: offset, length: text.length), level: match.range(at: 1).length, title: text.substring(with: match.range(at: 2))))
                }
            }
            offset += (line as NSString).length + 1
        }
        return headings
    }

    static func tags(_ source: String) -> [String] {
        var result: [String] = []
        for value in source.components(separatedBy: CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: ",，;；"))) {
            let tag = String(value.drop(while: { $0 == "#" }))
            if !tag.isEmpty && !result.contains(tag) { result.append(tag) }
        }
        return result
    }

    enum Format: String, CaseIterable, Identifiable {
        case unindent = "清除段首缩进", indent = "段首空两格", spaced = "段间空一行", compact = "清除空行", mixed = "中英之间加空格"
        var id: String { rawValue }
    }
    static func format(_ source: String, operation: Format) -> String {
        let text = source.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        switch operation {
        case .unindent: return text.replacingOccurrences(of: #"(?m)^[ \t　]+"#, with: "", options: .regularExpression)
        case .indent:
            return format(text, operation: .unindent).replacingOccurrences(of: #"(?m)^([^\s].*)$"#, with: "　　$1", options: .regularExpression)
        case .spaced: return text.replacingOccurrences(of: #"\n{3,}"#, with: "\n\n", options: .regularExpression).replacingOccurrences(of: #"(?<!\n)\n(?!\n)"#, with: "\n\n", options: .regularExpression)
        case .compact: return text.replacingOccurrences(of: #"\n{2,}"#, with: "\n", options: .regularExpression)
        case .mixed: return text.replacingOccurrences(of: #"([\p{Han}])([A-Za-z0-9])"#, with: "$1 $2", options: .regularExpression).replacingOccurrences(of: #"([A-Za-z0-9])([\p{Han}])"#, with: "$1 $2", options: .regularExpression)
        }
    }
}
