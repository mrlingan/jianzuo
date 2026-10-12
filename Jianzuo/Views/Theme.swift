import SwiftUI

enum PaperTheme {
    static var accent: Color { WritingPalette.adaptive(3) }
    static var accentForeground: Color { WritingPalette.adaptive(4) }
    static let green = Color(red: 0.36, green: 0.43, blue: 0.34)
    static var background: Color { WritingPalette.adaptive(0) }
    static var sidebar: Color { WritingPalette.adaptive(1) }
    static var card: Color { WritingPalette.adaptive(0) }
    static var ink: Color { WritingPalette.adaptive(2) }
    static let muted = Color.secondary
    #if os(macOS)
    static let navigationSize = 12.0
    static let captionSize = 10.0
    static let bodySmallSize = 11.0
    static let cardTitleSize = 14.0
    #else
    static let navigationSize = 16.0
    static let captionSize = 12.0
    static let bodySmallSize = 14.0
    static let cardTitleSize = 18.0
    #endif
}

struct TextExport: FileDocument {
    static var readableContentTypes: [UTType] { [UTType(filenameExtension: "md") ?? .plainText, .plainText] }
    var text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

import UniformTypeIdentifiers

extension View {
    func quietButton() -> some View {
        self.buttonStyle(.plain)
    }
}
