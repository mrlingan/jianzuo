import SwiftUI

@main
struct JianzuoApp: App {
    @StateObject private var library = LibraryStore()
    @AppStorage("appearance") private var appearance = "system"
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(library)
                .tint(PaperTheme.accent)
                .preferredColorScheme(appearance == "light" ? .light : appearance == "dark" ? .dark : nil)
                #if os(macOS)
                .frame(minWidth: 900, minHeight: 620)
                #endif
        }
        #if os(macOS)
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 1280, height: 820)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("新建文稿") { library.createDocument() }.keyboardShortcut("n")
                Button("导入文稿…") { library.importDocuments() }.keyboardShortcut("o")
            }
            CommandGroup(after: .saveItem) {
                Button("保存") { library.saveNow() }.keyboardShortcut("s")
                Button("导出文稿…") { library.exportCurrentDocument() }
                    .keyboardShortcut("e", modifiers: [.command, .shift])
                    .disabled(library.currentDocument == nil)
            }
            CommandMenu("写作") {
                Button(library.focusMode ? "退出专注模式" : "进入专注模式") { library.focusMode.toggle() }
                    .keyboardShortcut("f", modifiers: [.command, .shift])
                Button(library.showPreview ? "编辑 Markdown" : "预览 Markdown") { library.showPreview.toggle() }
                    .keyboardShortcut("p", modifiers: [.command, .shift])
                Button("收藏 / 取消收藏") {
                    if let id = library.selection { library.toggleFavorite(id) }
                }.disabled(library.currentDocument == nil)
            }
        }
        #endif
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { library.saveNow() }
        }
        #if os(macOS)
        Settings { WritingSettings().tint(PaperTheme.accent) }
        #endif
    }
}
