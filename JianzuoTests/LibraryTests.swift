import XCTest
@testable import Jianzuo
#if os(iOS)
import SwiftUI
import UIKit
#endif

final class LibraryTests: XCTestCase {
    func testMixedLanguageWordCount() {
        XCTAssertEqual(WritingMetrics.count("你好，世界！"), 4)
        XCTAssertEqual(WritingMetrics.count("Hello world, it's a good-day."), 5)
        XCTAssertEqual(WritingMetrics.count("hello世界 Swift写作 2026"), 7)
        XCTAssertEqual(WritingMetrics.count("# \n ** > — !"), 0)
    }

    func testPersistenceRoundTripPreservesUnicodeAndMetadata() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("library.json")
        let document = WritingDocument(title: "雨天 ☔️", body: "你好，世界\n\n**Hello**", notebook: .journal, isFavorite: true, deletedAt: Date())
        try LibraryPersistence.write([document], to: url)
        XCTAssertEqual(try LibraryPersistence.read(from: url), [document])
    }

    @MainActor
    func testTrashRestoreAndPermanentDeletion() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("library.json")
        let library = LibraryStore(storageURL: url, seed: false)
        let id = library.createDocument(title: "正文", body: "不能丢失", notebook: .ideas)
        library.permanentlyDelete(id)
        XCTAssertEqual(library.documents.count, 1, "Active documents must not be permanently deleted")
        library.moveToTrash(id)
        XCTAssertEqual(library.count(in: .all), 0)
        XCTAssertEqual(library.count(in: .trash), 1)
        library.restore(id)
        XCTAssertEqual(library.currentDocument?.body, "不能丢失")
        XCTAssertEqual(library.count(in: .notebook(.ideas)), 1)
        library.moveToTrash(id)
        library.permanentlyDelete(id)
        library.saveNow()
        XCTAssertTrue(try LibraryPersistence.read(from: url).isEmpty)
    }

    @MainActor
    func testSearchAndFavoriteFiltering() {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        let library = LibraryStore(storageURL: folder.appendingPathComponent("library.json"), seed: false)
        let first = library.createDocument(title: "Morning", body: "咖啡和雨", notebook: .journal)
        library.createDocument(title: "Ideas", body: "A small story")
        library.search = "morning"
        XCTAssertEqual(library.visibleDocuments.map(\.id), [first])
        library.search = "咖啡"
        XCTAssertEqual(library.visibleDocuments.map(\.id), [first])
        library.search = ""
        library.toggleFavorite(first)
        library.selectFilter(.favorites)
        XCTAssertEqual(library.selection, first)
        library.saveNow()
    }

    @MainActor
    func testCorruptLibraryIsNeverOverwritten() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let url = folder.appendingPathComponent("library.json")
        let original = Data("corrupt but recoverable".utf8)
        try original.write(to: url)
        let library = LibraryStore(storageURL: url)
        XCTAssertNotNil(library.errorMessage)
        library.createDocument(title: "New text")
        library.saveNow()
        XCTAssertEqual(try Data(contentsOf: url), original)
    }

    func testMarkdownCodeBlockRetainsWhitespaceAndIncompleteFence() {
        let blocks = MarkdownBlock.parse("# 标题\n\n```swift\n  let x = 1\n\n  print(x)")
        XCTAssertEqual(blocks.count, 2)
        XCTAssertEqual(blocks[1].text, "  let x = 1\n\n  print(x)")
        guard case .code = blocks[1].kind else { return XCTFail("Expected code block") }
    }
}

final class AppleWritingFeaturesTests: XCTestCase {
    private func temporaryURL() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent("library.json") }

    func testVersionOneMigrationAndUnsupportedVersionProtection() throws {
        let url = temporaryURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let document = WritingDocument(title: "旧文稿 ☔️", body: "原有内容", notebook: .journal, isFavorite: true)
        let data = try JSONEncoder().encode(LibrarySnapshot(documents: [document]))
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        json["version"] = 1
        json.removeValue(forKey: "notebooks"); json.removeValue(forKey: "dailyWords"); json.removeValue(forKey: "scratch")
        var docs = try XCTUnwrap(json["documents"] as? [[String: Any]])
        docs[0].removeValue(forKey: "history"); docs[0].removeValue(forKey: "tags"); docs[0].removeValue(forKey: "isReadOnly")
        json["documents"] = docs
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONSerialization.data(withJSONObject: json).write(to: url)
        let snapshot = try LibraryPersistence.readSnapshot(from: url)
        XCTAssertEqual(snapshot.documents.first, document)
        XCTAssertEqual(snapshot.notebooks, Notebook.allCases)
        try LibraryPersistence.write(snapshot, to: url)
        XCTAssertEqual(try LibraryPersistence.readSnapshot(from: url).documents, [document])
        XCTAssertEqual(try LibraryPersistence.readSnapshot(from: url).version, 2)
        json["version"] = 99
        try JSONSerialization.data(withJSONObject: json).write(to: url)
        XCTAssertThrowsError(try LibraryPersistence.readSnapshot(from: url))
    }

    func testBackupIntegrityAndSnapshotValidation() throws {
        var document = WritingDocument(title: "备份", body: "重要文字 👩🏽‍💻")
        document.tags = ["随笔"]; document.checkpoint(force: true)
        let snapshot = LibrarySnapshot(documents: [document], dailyWords: ["2026-10-09": 42], scratch: "灵感")
        let data = try BackupCodec.encode(snapshot)
        let decoded = try BackupCodec.decode(data)
        XCTAssertEqual(decoded.documents, [document]); XCTAssertEqual(decoded.scratch, "灵感")
        var envelope = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        envelope["payload"] = (envelope["payload"] as? String ?? "") + " "
        XCTAssertThrowsError(try BackupCodec.decode(JSONSerialization.data(withJSONObject: envelope)))
        XCTAssertThrowsError(try LibrarySnapshot(documents: [document, document]).validated())
        XCTAssertThrowsError(try LibrarySnapshot(documents: [], dailyWords: ["2026-02-30": 3]).validated())
        XCTAssertThrowsError(try LibrarySnapshot(documents: [], notebooks: [Notebook(rawValue: "废纸篓")]).validated())
    }

    @MainActor
    func testNotebookRenameIncludesTrashAndTagFilterIsExact() throws {
        let url = temporaryURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let store = LibraryStore(storageURL: url, seed: false)
        try store.addNotebook("小说")
        let notebook = Notebook(rawValue: "小说")
        let first = store.createDocument(title: "第一章", notebook: notebook)
        let second = store.createDocument(title: "第二章", notebook: notebook)
        store.setTags(first, source: "#旅行, 旅途；旅行")
        store.setTags(second, source: "旅行记")
        store.selectFilter(.notebook(notebook)); store.tagFilter = "旅行"
        XCTAssertEqual(store.visibleDocuments.map(\.id), [first])
        store.moveToTrash(second)
        try store.renameNotebook(notebook, to: "故事")
        XCTAssertTrue(store.documents.allSatisfy { $0.notebook.rawValue == "故事" })
        XCTAssertThrowsError(try store.deleteNotebook(Notebook(rawValue: "故事")))
        store.saveNow()
        let loaded = LibraryStore(storageURL: url, seed: false)
        XCTAssertTrue(loaded.notebooks.contains(Notebook(rawValue: "故事")))
        XCTAssertEqual(loaded.documents.first { $0.id == first }?.tags, ["旅行", "旅途"])
    }

    @MainActor
    func testHistoryReadOnlyAndRestoreDoNotInflateDailyWriting() throws {
        let url = temporaryURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let store = LibraryStore(storageURL: url, seed: false)
        let id = store.createDocument(title: "正文", body: "原稿")
        store.update(id, body: "原稿，新增内容")
        let revision = try XCTUnwrap(store.currentDocument?.history.first)
        XCTAssertEqual(revision.body, "原稿")
        let words = store.dailyWords
        store.toggleReadOnly(id)
        store.update(id, title: "不能更改", body: "不应写入")
        XCTAssertEqual(store.currentDocument?.body, "原稿，新增内容")
        store.toggleReadOnly(id)
        store.restoreRevision(revision, to: id)
        XCTAssertEqual(store.currentDocument?.body, "原稿")
        XCTAssertTrue(store.currentDocument?.history.contains { $0.body == "原稿，新增内容" } == true)
        XCTAssertEqual(store.dailyWords, words)
        store.restoreRevision(revision, to: id, asCopy: true)
        XCTAssertEqual(store.documents.count, 2)
        XCTAssertEqual(store.dailyWords, words)
    }

    func testHistoryIsBoundedAndCoalescesTyping() {
        var document = WritingDocument(title: "", body: "初稿")
        let start = Date()
        document.checkpoint(now: start)
        document.body = "第二稿"; document.checkpoint(now: start.addingTimeInterval(1))
        XCTAssertEqual(document.history.count, 1)
        for index in 0..<50 { document.body = "第 \(index) 稿"; document.checkpoint(force: true) }
        XCTAssertEqual(document.history.count, 40)
        XCTAssertEqual(document.history.first?.body, "第 49 稿")
    }

    @MainActor
    func testBackupMergePreservesOriginalAndUsesMaxDailyCount() throws {
        let url = temporaryURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let store = LibraryStore(storageURL: url, seed: false)
        let original = store.createDocument(title: "原文", body: "本机内容")
        let incoming = LibrarySnapshot(documents: [WritingDocument(title: "备份原文", body: "备份内容", notebook: .journal, isFavorite: true)], dailyWords: ["2026-10-08": 20], scratch: "随手记")
        try store.mergeBackup(incoming, includeStatistics: true)
        try store.mergeBackup(incoming, includeStatistics: true)
        XCTAssertEqual(store.documents.count, 3)
        XCTAssertEqual(Set(store.documents.map(\.id)).count, 3)
        XCTAssertEqual(store.documents.first { $0.id == original }?.body, "本机内容")
        XCTAssertEqual(store.dailyWords["2026-10-08"], 20)
        XCTAssertEqual(try LibraryPersistence.readSnapshot(from: url).documents.count, 3)
        XCTAssertFalse(store.automaticBackups.isEmpty)
        let backup = try BackupCodec.decode(Data(contentsOf: XCTUnwrap(store.automaticBackups.first)))
        XCTAssertEqual(backup.documents.count, 2)
    }

    func testSelectiveReplacementIsUnicodeSafeLiteralAndRejectsStaleSource() throws {
        let source = "👩🏽‍💻 雨天 rain 雨天\nRAIN"
        let matches = try WritingTools.matches(in: source, query: "雨天", regex: false, caseSensitive: true)
        let result = try WritingTools.replace(source: source, expected: source, matches: matches, selected: [matches[1].id], replacement: "$1\\n")
        XCTAssertEqual(result, "👩🏽‍💻 雨天 rain $1\\n\nRAIN")
        XCTAssertThrowsError(try WritingTools.replace(source: source + "!", expected: source, matches: matches, selected: [matches[0].id], replacement: "晴天"))
        XCTAssertEqual(try WritingTools.matches(in: source, query: "rain", regex: false, caseSensitive: false).count, 2)
        XCTAssertThrowsError(try WritingTools.matches(in: source, query: "[", regex: true, caseSensitive: false))
        let zero = try WritingTools.matches(in: "ab", query: "(?=b)", regex: true, caseSensitive: true)
        XCTAssertEqual(try WritingTools.replace(source: "ab", expected: "ab", matches: zero, selected: Set(zero.map(\.id)), replacement: "!"), "a!b")
    }

    func testOutlineSkipsFencesAndOffsetsMatchNativeUTF16Selection() {
        let source = "😀\n# 标题\n~~~swift\n## 不进大纲\n~~~\n```\n## 也不进\n```\n### 结尾 ###"
        let headings = WritingTools.outline(source)
        XCTAssertEqual(headings.map(\.title), ["标题", "结尾"])
        XCTAssertEqual(headings.first?.range.location, 3)
        XCTAssertEqual((source as NSString).substring(with: headings[1].range), "### 结尾 ###")
        XCTAssertEqual(WritingTools.format("  你好Swift\n\n\n世界", operation: .mixed), "  你好 Swift\n\n\n世界")
        XCTAssertEqual(WritingTools.format("  第一行\n　　第二行", operation: .indent), "　　第一行\n　　第二行")
    }

    @MainActor
    func testImportPreviewDoesNotWriteUntilCommittedAndClearsHiddenFilters() throws {
        let url = temporaryURL()
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let folder = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let second = folder.appendingPathComponent("章节10.md")
        let first = folder.appendingPathComponent("章节2.txt")
        try "第十章".write(to: second, atomically: true, encoding: .utf8)
        try "第二章".write(to: first, atomically: true, encoding: .utf8)
        let store = LibraryStore(storageURL: url, seed: false)
        store.selectFilter(.favorites); store.search = "隐藏"; store.tagFilter = "隐藏"
        store.importURLs([second, first])
        XCTAssertTrue(store.documents.isEmpty)
        XCTAssertEqual(store.importCandidates.map(\.title), ["章节2", "章节10"])
        store.importCandidates[1].included = false
        store.importCandidates[0].title = "第二章 · 改名"
        store.commitImport(to: .ideas)
        XCTAssertEqual(store.visibleDocuments.count, 1)
        XCTAssertEqual(store.currentDocument?.title, "第二章 · 改名")
        XCTAssertEqual(store.currentDocument?.notebook, .ideas)
        XCTAssertTrue(store.dailyWords.isEmpty)
    }

    #if os(iOS)
    @MainActor
    func testNativeEditorCreatesConnectedLayoutAndCanEditRuledPaper() throws {
        var body = "第一行，Hello 👩🏽‍💻\n第二行"
        let controller = EditorController()
        let binding = Binding(get: { body }, set: { body = $0 })
        let host = UIHostingController(rootView: NativeWritingEditor(text: binding, fontSize: 17, fontStyle: "serif", lineSpacing: 9, isEditable: true, controller: controller, ruledPaper: true))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = scene.screen.bounds
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previous?.makeKeyAndVisible() }
        host.view.layoutIfNeeded()
        func findEditor(_ view: UIView) -> UITextView? {
            if let editor = view as? UITextView { return editor }
            return view.subviews.lazy.compactMap { findEditor($0) }.first
        }
        let editor = try XCTUnwrap(findEditor(host.view))
        XCTAssertEqual(editor.text, body)
        XCTAssertTrue(editor.textContainer.layoutManager === editor.layoutManager)
        XCTAssertTrue(editor.layoutManager.textStorage === editor.textStorage)
        XCTAssertNil(editor.textLayoutManager, "Ruled paper uses the TextKit 1 layout stack")
        editor.layoutManager.ensureLayout(for: editor.textContainer)
        XCTAssertGreaterThan(editor.layoutManager.numberOfGlyphs, 0)
        editor.selectedRange = NSRange(location: (body as NSString).length, length: 0)
        editor.insertText("\n继续写作")
        XCTAssertTrue(body.hasSuffix("\n继续写作"))
        let image = UIGraphicsImageRenderer(bounds: editor.bounds).image { _ in editor.drawHierarchy(in: editor.bounds, afterScreenUpdates: true) }
        XCTAssertGreaterThan(image.size.height, 0)
    }
    #endif

}
