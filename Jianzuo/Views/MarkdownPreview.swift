import SwiftUI

struct MarkdownBlock: Identifiable {
    enum Kind { case paragraph, heading(Int), quote, bullet(String), code, divider }
    let id: Int
    let kind: Kind
    let text: String

    static func parse(_ source: String) -> [MarkdownBlock] {
        var blocks: [MarkdownBlock] = []
        var paragraph: [String] = []
        var code: [String] = []
        var inCode = false
        func append(_ kind: Kind, _ text: String) { blocks.append(MarkdownBlock(id: blocks.count, kind: kind, text: text)) }
        func flush() {
            if !paragraph.isEmpty { append(.paragraph, paragraph.joined(separator: "\n")); paragraph.removeAll() }
        }
        for line in source.components(separatedBy: .newlines) {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("```") {
                flush()
                if inCode { append(.code, code.joined(separator: "\n")); code.removeAll() }
                inCode.toggle()
            } else if inCode {
                code.append(line)
            } else if trimmed.isEmpty {
                flush()
            } else if trimmed == "---" || trimmed == "***" || trimmed == "___" {
                flush(); append(.divider, "")
            } else if trimmed.hasPrefix("#"), let space = trimmed.firstIndex(of: " ") {
                let prefix = trimmed[..<space]
                if prefix.allSatisfy({ $0 == "#" }) && prefix.count <= 6 {
                    flush(); append(.heading(prefix.count), String(trimmed[trimmed.index(after: space)...]))
                } else { paragraph.append(line) }
            } else if trimmed.hasPrefix("> ") {
                flush(); append(.quote, String(trimmed.dropFirst(2)))
            } else if trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") || trimmed.hasPrefix("+ ") {
                flush(); append(.bullet("•"), String(trimmed.dropFirst(2)))
            } else if let range = trimmed.range(of: #"^\d+\. "#, options: .regularExpression) {
                flush(); append(.bullet(String(trimmed[range]).trimmingCharacters(in: .whitespaces)), String(trimmed[range.upperBound...]))
            } else {
                paragraph.append(line)
            }
        }
        flush()
        if inCode { append(.code, code.joined(separator: "\n")) }
        return blocks
    }
}

struct MarkdownPreview: View {
    let text: String
    let fontSize: Double
    let fontStyle: String
    let lineSpacing: Double
    private var design: Font.Design { fontStyle == "serif" ? .serif : fontStyle == "mono" ? .monospaced : .default }
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            if text.isEmpty { Text("还没有正文，切换到编辑模式开始写作。").foregroundStyle(.tertiary) }
            ForEach(MarkdownBlock.parse(text)) { block in
                switch block.kind {
                case .paragraph:
                    inline(block.text).font(.system(size: fontSize, design: design)).lineSpacing(lineSpacing)
                case .heading(let level):
                    inline(block.text).font(.system(size: max(fontSize, fontSize + Double(5 - level) * 3), weight: .semibold, design: design))
                        .padding(.top, 8)
                case .quote:
                    HStack(alignment: .top, spacing: 16) {
                        RoundedRectangle(cornerRadius: 2).fill(PaperTheme.accent.opacity(0.5)).frame(width: 3)
                        inline(block.text).italic().foregroundStyle(.secondary).lineSpacing(lineSpacing).padding(.vertical, 8)
                    }.fixedSize(horizontal: false, vertical: true).font(.system(size: fontSize, design: design))
                case .bullet(let marker):
                    HStack(alignment: .top, spacing: 12) {
                        Text(marker).foregroundStyle(PaperTheme.accent)
                        inline(block.text).frame(maxWidth: .infinity, alignment: .leading)
                    }.font(.system(size: fontSize, design: design)).lineSpacing(lineSpacing)
                case .code:
                    Text(block.text).font(.system(size: max(12, fontSize - 3), design: .monospaced))
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(PaperTheme.sidebar, in: RoundedRectangle(cornerRadius: 6))
                case .divider: Divider().padding(.vertical, 8)
                }
            }
        }.textSelection(.enabled)
    }
    private func inline(_ text: String) -> Text {
        let parsed = try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))
        return Text(parsed ?? AttributedString(text))
    }
}
