import SwiftUI
#if os(macOS)
import AppKit
#else
import UIKit
#endif

enum WritingPalette: String, CaseIterable, Identifiable {
    case classic, forest, ocean, rose, mono
    var id: String { rawValue }
    var title: String {
        switch self { case .classic: "经典纸色"; case .forest: "林间"; case .ocean: "海雾"; case .rose: "蔷薇"; case .mono: "黑白" }
    }
    func colors(dark: Bool) -> [UInt32] {
        switch self {
        case .classic: dark ? [0x1D1C1A,0x252320,0xEDE7DD,0xD18561] : [0xFAF7F1,0xF3EFE8,0x302E29,0xB05C3D]
        case .forest: dark ? [0x19221D,0x202C25,0xE3EDE4,0x8DB99A] : [0xF1F6EE,0xE6EEE2,0x29382D,0x3F7450]
        case .ocean: dark ? [0x19222B,0x202D38,0xE3EBF2,0x8AB4D1] : [0xF0F5FA,0xE5EDF4,0x293746,0x3D6B91]
        case .rose: dark ? [0x291E24,0x34272E,0xF0E4E9,0xD7A0B7] : [0xFCF2F4,0xF3E7EB,0x443039,0x97516F]
        case .mono: dark ? [0x191919,0x242424,0xF0F0F0,0xD0D0D0] : [0xFFFFFF,0xF3F3F3,0x242424,0x444444]
        }
    }
    func color(_ index: Int, dark: Bool) -> Color {
        let values = colors(dark: dark)
        let accent = values[3]
        func channel(_ shift: UInt32) -> Double {
            let c = Double((accent >> shift) & 255) / 255
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        let luminance = channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
        let value: UInt32 = index == 4 ? (luminance >= 0.179 ? 0x000000 : 0xFFFFFF) : values[index]
        return Color(red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255)
    }
    static var current: Self { Self(rawValue: UserDefaults.standard.string(forKey: "editorTheme") ?? "classic") ?? .classic }
    static func adaptive(_ index: Int) -> Color {
        #if os(macOS)
        Color(nsColor: NSColor(name: nil) { appearance in
            let dark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
            return NSColor(Self.current.color(index, dark: dark))
        })
        #else
        Color(uiColor: UIColor { traits in UIColor(Self.current.color(index, dark: traits.userInterfaceStyle == .dark)) })
        #endif
    }
}

@MainActor
final class EditorController: ObservableObject {
    struct Request {
        var id = UUID()
        var range: NSRange?
        var insertion: String?
    }
    @Published var selection = NSRange(location: 0, length: 0)
    @Published var selectedText = ""
    @Published var request: Request?
    func insert(_ text: String) { request = Request(insertion: text) }
    func reveal(_ range: NSRange) { request = Request(range: range) }
    func acknowledge(_ id: UUID) {
        DispatchQueue.main.async { [weak self] in
            if self?.request?.id == id { self?.request = nil }
        }
    }
}

struct InputShortcut: Identifiable, Codable, Equatable {
    var id = UUID()
    var title: String
    var text: String
    static let defaults = [InputShortcut(title: "“ ”", text: "“”"), InputShortcut(title: "——", text: "——"), InputShortcut(title: "……", text: "……"), InputShortcut(title: "标题", text: "## "), InputShortcut(title: "引用", text: "> "), InputShortcut(title: "列表", text: "- "), InputShortcut(title: "分隔线", text: "\n\n---\n\n")]
    static func load(_ data: Data) -> [Self] { (try? JSONDecoder().decode([Self].self, from: data)) ?? defaults }
}
