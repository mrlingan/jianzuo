package com.jianzuo.writing;

import org.junit.Test;
import org.json.JSONObject;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

public class WritingFeaturesTest {
    @Test public void legacyDataUpgradesWithoutLosingIdentityOrText() throws Exception {
        Library original = new Library(); Document doc = original.create("旧文稿", "你好 𠀀\n**文本**", "日记");
        JSONObject root = new JSONObject(LibraryCodec.encode(original)); root.put("version", 1);
        Library restored = LibraryCodec.decode(root.toString());
        assertEquals(doc.body, restored.find(doc.id).body); assertEquals(3, restored.notebooks.size());
        assertEquals(doc.createdAt, restored.find(doc.id).createdAt);
        assertEquals(doc.body, LibraryCodec.decode(LibraryCodec.encode(restored)).find(doc.id).body);
    }
    @Test public void backupDetectsModifiedPayloadBeforeRestore() throws Exception {
        Library library = new Library(); library.create("备份", "不可丢失", "文稿");
        String backup = BackupCodec.encode(library);
        assertEquals("不可丢失", BackupCodec.decode(backup).documents.get(0).body);
        JSONObject damaged = new JSONObject(backup); damaged.put("payload", damaged.getString("payload").replace("不可丢失", "已经被改"));
        assertThrows(Exception.class, () -> BackupCodec.decode(damaged.toString()));
        assertThrows(Exception.class, () -> BackupCodec.decode(LibraryCodec.encode(library)));
    }
    @Test public void historyRecoveryPreservesCurrentVersionAndLimitsGrowth() {
        Document doc = new Document(); doc.body = "旧版本"; doc.checkpoint(true); Document.Revision old = doc.history.get(0);
        doc.body = "新版本"; doc.restoreRevision(old); assertEquals("旧版本", doc.body); assertEquals("新版本", doc.history.get(0).body());
        for (int i = 0; i < 60; i++) { doc.body = "版本" + i; doc.checkpoint(true); }
        assertEquals(40, doc.history.size()); assertEquals("版本59", doc.history.get(0).body());
    }
    @Test public void customNotebooksAndMergeKeepAllMetadataWithoutOverwriting() throws Exception {
        Library library = new Library(); library.addNotebook("长篇小说");
        Document doc = library.create("第一章", "雨来了", "长篇小说"); doc.section = "第一卷"; doc.tags = "#雨"; doc.readOnly = true; doc.favorite = true; doc.checkpoint(true);
        library.scratch = "下一章的线索"; library.dailyWords.put("2026-10-05", 20);
        library.renameNotebook("长篇小说", "小说"); assertEquals("小说", doc.notebook);
        Library imported = BackupCodec.decode(BackupCodec.encode(library));
        assertTrue(imported.find(doc.id).readOnly); assertEquals("第一卷", imported.find(doc.id).section); assertEquals(1, imported.find(doc.id).history.size());
        assertThrows(Exception.class, () -> library.deleteNotebook("小说"));
        assertEquals(1, library.merge(imported)); assertEquals(2, library.documents.size());
        assertNotEquals(doc.id, library.documents.get(0).id); assertEquals("#雨", library.documents.get(0).tags);
        assertEquals(Integer.valueOf(20), library.dailyWords.get("2026-10-05"));
    }
    @Test public void manualChapterOrderControlsBookExport() {
        Library library = new Library(); Document first = library.create("一", "AAA", "文稿"), second = library.create("二", "BBB", "文稿");
        first.section = "卷一"; second.section = "卷一"; library.move(second, -1);
        assertSame(second, library.visible("文稿", "", "manual").get(0));
        String export = library.bookText("文稿"); assertTrue(export.indexOf("BBB") < export.indexOf("AAA"));
        assertEquals(export.indexOf("# 卷一"), export.lastIndexOf("# 卷一"));
        library.trash(second); assertFalse(library.bookText("文稿").contains("BBB"));
    }
    @Test public void replacementUsesLiteralDollarAndSlashAndUnicodeOffsets() {
        Pattern pattern = TextTools.pattern("HELLO", false, false);
        assertEquals("$1\\你好 $1\\你好", TextTools.replace("hello HELLO", pattern, "$1\\你好"));
        var matches = TextTools.matches("𠀀hello", pattern); assertEquals(2, matches.get(0).start()); assertEquals(7, matches.get(0).end());
        assertThrows(Exception.class, () -> TextTools.pattern("[", true, false));
        assertEquals(2, TextTools.matches("Aa", TextTools.pattern("a", false, false)).size());
        assertEquals(1, TextTools.matches("Aa", TextTools.pattern("a", false, true)).size());
    }
    @Test public void outlineIgnoresCodeAndFormattingPreservesWords() {
        String source = "# 标题\n```\n# 代码不是标题\n```\n\n## 章节";
        var outline = TextTools.outline(source); assertEquals(2, outline.size()); assertEquals(source.indexOf("## 章节"), outline.get(1).offset());
        assertEquals("中文 Swift 和 HTML", TextTools.format("中文Swift和HTML", 4));
        assertEquals("第一段\n\n第二段", TextTools.format("第一段\r\n第二段", 2));
        assertEquals(2, TextTools.outline("# 一\r\n\r\n## 二").size());
        assertEquals("正文\n第二段", TextTools.format("　　正文\n  第二段", 0));
    }
    @Test public void taskAndTablePreviewHaveDistinctNativeBlocks() {
        var blocks = Markdown.parse("- [ ] 未完成\n- [x] 已完成\n\n| 名称 | 数量 |\n| --- | --- |\n| 咖啡 | 2 |");
        assertEquals("task", blocks.get(0).kind()); assertEquals("☐", blocks.get(0).marker());
        assertEquals("☑", blocks.get(1).marker()); assertEquals("table", blocks.get(2).kind());
    }
}
