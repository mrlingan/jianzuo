package com.jianzuo.writing;

import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryTest {
    @Test public void mixedLanguageWordCount() {
        assertEquals(4, WritingMetrics.count("你好，世界！"));
        assertEquals(5, WritingMetrics.count("Hello world, it's a good-day."));
        assertEquals(7, WritingMetrics.count("hello世界 Swift写作 2026"));
        assertEquals(0, WritingMetrics.count("# \n ** > — !"));
        assertEquals(1, WritingMetrics.count("𠀀"));
        assertEquals(4, WritingMetrics.count("あア한字"));
    }
    @Test public void unicodeSnapshotPreservesAllFields() throws Exception {
        Library library = new Library(); Document doc = library.create("雨天 ☔️", "你好\n\n**Hello** 𠀀", "日记");
        doc.favorite = true; doc.deletedAt = 172800000L;
        Document restored = LibraryCodec.decode(LibraryCodec.encode(library)).documents.get(0);
        assertEquals(doc.id, restored.id); assertEquals(doc.title, restored.title); assertEquals(doc.body, restored.body);
        assertEquals(doc.notebook, restored.notebook); assertEquals(doc.createdAt, restored.createdAt);
        assertEquals(doc.updatedAt, restored.updatedAt); assertEquals(doc.deletedAt, restored.deletedAt); assertTrue(restored.favorite);
    }
    @Test public void trashRestoreAndPermanentDelete() {
        Library library = new Library(); Document doc = library.create("正文", "不能丢失", "灵感");
        library.permanentlyDelete(doc); assertEquals(1, library.documents.size());
        library.trash(doc); assertEquals(0, library.count(Library.ALL)); assertEquals(1, library.count(Library.TRASH));
        library.restore(doc); assertEquals("不能丢失", doc.body); assertEquals(1, library.count("灵感"));
        library.trash(doc); library.permanentlyDelete(doc); assertTrue(library.documents.isEmpty());
    }
    @Test public void searchFavoritesAndSort() {
        Library library = new Library(); Document morning = library.create("Morning", "咖啡和雨", "日记");
        Document ideas = library.create("Ideas", "A small story", "灵感"); ideas.updatedAt = morning.updatedAt + 100;
        assertSame(morning, library.visible(Library.ALL, " MORNING ").get(0));
        assertSame(morning, library.visible(Library.ALL, "咖啡").get(0));
        morning.favorite = true; assertSame(morning, library.visible(Library.FAVORITES, "").get(0));
        assertSame(ideas, library.visible(Library.ALL, "").get(0));
        library.trash(morning); assertTrue(library.visible(Library.FAVORITES, "").isEmpty());
    }
    @Test public void copyHasIndependentIdentityAndNoTrashState() {
        Library library = new Library(); Document doc = library.create("标题", "内容", "日记"); doc.favorite = true;
        Document copy = library.duplicate(doc);
        assertNotEquals(doc.id, copy.id); assertEquals("标题 · 副本", copy.title); assertEquals("内容", copy.body);
        assertEquals("日记", copy.notebook); assertFalse(copy.favorite); assertNull(copy.deletedAt);
    }
    @Test public void emptyLibraryStaysEmptyAfterRoundTrip() throws Exception {
        assertTrue(LibraryCodec.decode(LibraryCodec.encode(new Library())).documents.isEmpty());
    }
    @Test public void invalidSnapshotsAreRejected() throws Exception {
        assertThrows(Exception.class, () -> LibraryCodec.decode("corrupt but recoverable"));
        assertThrows(Exception.class, () -> LibraryCodec.decode("{\"version\":2,\"documents\":[]}"));
        assertThrows(Exception.class, () -> LibraryCodec.decode("{\"version\":1,\"documents\":[{}]}"));
        Library library = new Library(); Document doc = library.create("", "", "文稿");
        String snapshot = LibraryCodec.encode(library);
        assertThrows(Exception.class, () -> LibraryCodec.decode(invalidNotebook(snapshot)));
        library.documents.add(doc); assertThrows(Exception.class, () -> LibraryCodec.decode(LibraryCodec.encode(library)));
    }
    private String invalidNotebook(String snapshot) throws Exception {
        org.json.JSONObject root = new org.json.JSONObject(snapshot);
        root.getJSONArray("documents").getJSONObject(0).put("notebook", "未知笔记本"); return root.toString();
    }
    @Test public void unclosedCodeFenceRetainsWhitespace() {
        var blocks = Markdown.parse("# 标题\n\n```swift\n  let x = 1\n\n  print(x)");
        assertEquals(2, blocks.size()); assertEquals("code", blocks.get(1).kind());
        assertEquals("  let x = 1\n\n  print(x)", blocks.get(1).text());
    }
    @Test public void markdownParagraphsAndListsAndQuotes() {
        var blocks = Markdown.parse("# 标题\r\n\r\n段落一\r\n段落二\r\n\r\n> 引用\r\n- 条目\r\n1. 有序\r\n---\r\n```\r\n# 原样\r\n```");
        assertEquals(7, blocks.size()); assertEquals("段落一\n段落二", blocks.get(1).text());
        assertEquals("quote", blocks.get(2).kind()); assertEquals("•", blocks.get(3).marker());
        assertEquals("1.", blocks.get(4).marker()); assertEquals("divider", blocks.get(5).kind());
        assertEquals("# 原样", blocks.get(6).text());
    }
}
