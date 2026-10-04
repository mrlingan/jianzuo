import XCTest
@testable import Jianzuo

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
