package com.jianzuo.writing;

import org.junit.Test;
import org.json.JSONObject;
import java.util.List;
import static org.junit.Assert.*;

public class OrganizationTest {
    @Test public void oldVolumeNamesMigrateAndLongLegacyNamesArePreserved() throws Exception {
        Library library = new Library(); Document a = library.create("一", "正文𠀀", "文稿"), b = library.create("二", "旧内容", "文稿");
        a.section = "旧卷名"; b.section = "卷".repeat(100); a.checkpoint(true); a.favorite = true;
        JSONObject legacy = new JSONObject(LibraryCodec.encode(library)); legacy.put("version", 2); legacy.remove("sections");
        for (int i = 0; i < legacy.getJSONArray("documents").length(); i++) legacy.getJSONArray("documents").getJSONObject(i).remove("sectionId");
        Library decoded = LibraryCodec.decode(legacy.toString()); assertEquals(2, decoded.sections.size());
        assertEquals(a.section, decoded.sections("文稿").get(0).name);
        assertEquals(a.body, decoded.find(a.id).body); assertEquals(a.history, decoded.find(a.id).history);
        assertEquals(b.section, decoded.find(b.id).section); assertFalse(decoded.find(a.id).sectionId.isEmpty());
        Library roundtrip = LibraryCodec.decode(LibraryCodec.encode(decoded)); assertEquals(decoded.find(a.id).sectionId, roundtrip.find(a.id).sectionId);
        assertEquals(b.section, roundtrip.find(b.id).section); assertTrue(roundtrip.find(a.id).favorite);
    }
    @Test public void emptyVolumesAndStableIdentityRoundTripThroughBackup() throws Exception {
        Library library = new Library(); Section empty = library.addSection("灵感", "空卷"), populated = library.addSection("文稿", "正文卷");
        Document doc = library.create("第一章", "正文", "文稿"); library.assignSection(doc, populated);
        Library restored = BackupCodec.decode(BackupCodec.encode(library)); assertEquals("空卷", restored.section(empty.id).name); assertEquals(populated.id, restored.find(doc.id).sectionId);
        library.renameSection(populated, "改名卷"); assertEquals(populated.id, doc.sectionId); assertEquals("改名卷", doc.section); assertEquals("正文", doc.body);
    }
    @Test public void invalidVolumeReferencesAndDuplicateVolumesAreRejected() throws Exception {
        Library library = new Library(); Section section = library.addSection("文稿", "卷一"); Document doc = library.create("一", "文本", "文稿"); library.assignSection(doc, section);
        JSONObject wrong = new JSONObject(LibraryCodec.encode(library)); wrong.getJSONArray("documents").getJSONObject(0).put("sectionId", "missing");
        assertThrows(Exception.class, () -> LibraryCodec.decode(wrong.toString()));
        JSONObject duplicate = new JSONObject(LibraryCodec.encode(library)); duplicate.getJSONArray("sections").put(duplicate.getJSONArray("sections").getJSONObject(0));
        assertThrows(Exception.class, () -> LibraryCodec.decode(duplicate.toString()));
        assertThrows(Exception.class, () -> library.addSection("文稿", "卷一")); assertThrows(Exception.class, () -> library.addSection("文稿", " "));
        assertThrows(Exception.class, () -> library.assignSection(library.create("二", "", "灵感"), section));
    }
    @Test public void bookExportGroupsEachVolumeOnceAndHonorsVolumeOrder() {
        Library library = new Library(); Section a = library.addSection("文稿", "卷甲"), b = library.addSection("文稿", "卷乙");
        Document one = library.create("一", "AAA", "文稿"), two = library.create("二", "BBB", "文稿"), three = library.create("三", "CCC", "文稿");
        library.assignSection(one, a); library.assignSection(two, b); library.assignSection(three, a);
        assertEquals(List.of(one, three, two), library.bookDocuments("文稿")); library.moveSection(b, -1);
        assertEquals(List.of(two, one, three), library.bookDocuments("文稿")); String text = library.bookText("文稿");
        assertEquals(text.indexOf("# 卷甲"), text.lastIndexOf("# 卷甲")); assertTrue(text.indexOf("BBB") < text.indexOf("AAA"));
        library.trash(one); assertFalse(library.bookText("文稿").contains("AAA"));
    }
    @Test public void dragPlacementCanMoveAcrossVolumesWithoutChangingText() {
        Library library = new Library(); Section a = library.addSection("文稿", "甲"), b = library.addSection("文稿", "乙");
        Document one = library.create("一", "AAA", "文稿"), two = library.create("二", "BBB", "文稿"), three = library.create("三", "CCC", "文稿");
        library.assignSection(one, a); library.assignSection(two, b); library.assignSection(three, b);
        library.placeBefore(one, three); assertEquals(b.id, one.sectionId); assertEquals("AAA", one.body);
        assertEquals(List.of(two, one, three), library.bookDocuments("文稿")); library.move(one, -1);
        assertEquals(List.of(one, two, three), library.bookDocuments("文稿"));
        library.trash(two); assertThrows(Exception.class, () -> library.placeBefore(one, two));
    }
    @Test public void movingWholeVolumeIncludesTrashAndRejectsNameCollisionAtomically() {
        Library library = new Library(); Section section = library.addSection("文稿", "卷一"), collision = library.addSection("灵感", "卷一");
        Document a = library.create("一", "A", "文稿"), b = library.create("二", "B", "文稿"); library.assignSection(a, section); library.assignSection(b, section); library.trash(b);
        assertThrows(Exception.class, () -> library.moveSectionToNotebook(section, "灵感")); assertEquals("文稿", a.notebook); assertEquals("文稿", section.notebook);
        library.renameSection(collision, "另一卷"); library.moveSectionToNotebook(section, "灵感"); assertEquals("灵感", b.notebook); assertNotNull(b.deletedAt); assertEquals(section.id, a.sectionId);
        library.renameNotebook("灵感", "素材"); assertEquals("素材", section.notebook); assertEquals("素材", a.notebook);
    }
    @Test public void dissolutionAndTrashRestorationRetainAllWriting() {
        Library library = new Library(); Section section = library.addSection("文稿", "卷一"); Document doc = library.create("标题", "内容", "文稿"); library.assignSection(doc, section); doc.checkpoint(true);
        library.trash(doc); library.renameSection(section, "新卷"); assertEquals("新卷", doc.section); library.dissolveSection(section);
        assertEquals("", doc.sectionId); assertEquals("", doc.section); library.restore(doc); assertEquals("内容", doc.body); assertEquals(1, doc.history.size());
    }
    @Test public void backupMergePreservesEmptyVolumesAndChapterOrderWithNewIds() throws Exception {
        Library source = new Library(); Section empty = source.addSection("文稿", "空卷"), full = source.addSection("文稿", "正文卷");
        Document a = source.create("一", "A", "文稿"), b = source.create("二", "B", "文稿"); source.assignSection(a, full); source.assignSection(b, full); source.move(b, -1);
        Library target = new Library(); target.addSection("文稿", "正文卷"); target.merge(BackupCodec.decode(BackupCodec.encode(source)));
        assertEquals(2, target.sections.size()); assertNull(target.section(empty.id)); assertEquals("二", target.bookDocuments("文稿").get(0).title); assertEquals("一", target.bookDocuments("文稿").get(1).title);
        assertNull(target.find(a.id)); assertEquals("A", target.bookDocuments("文稿").get(1).body);
    }
    @Test public void numberingRetainsHistoryAndSkipsReadOnlyAndTrash() {
        Library library = new Library(); Document a = library.create("雨夜", "正文A", "文稿"), b = library.create("锁定", "正文B", "文稿"), c = library.create("回收", "正文C", "文稿"); b.readOnly = true; library.trash(c);
        var plan = ChapterNaming.plan(List.of(a, b, c), "第{n}章 {title}", 7, 3, false); assertEquals(1, plan.size()); assertEquals("第007章 雨夜", plan.get(0).after());
        assertEquals(1, ChapterNaming.apply(library, plan)); assertEquals("正文A", a.body); assertEquals("雨夜", a.history.get(0).title()); assertEquals("锁定", b.title); assertEquals("回收", c.title);
        assertEquals("第8章 雨夜", ChapterNaming.title("第{n}章 {title}", 8, 1, a.title, true));
        assertEquals("1 标题{n}", ChapterNaming.title("{n} {title}", 1, 1, "标题{n}", false));
    }
    @Test public void invalidOrStaleNumberingDoesNotPartiallyRename() {
        Library library = new Library(); Document a = library.create("A", "body", "文稿"), b = library.create("B", "body", "文稿");
        assertThrows(Exception.class, () -> ChapterNaming.plan(List.of(a, b), "{n}", 1_000_000, 1, false)); assertEquals("A", a.title); assertTrue(a.history.isEmpty());
        var plan = ChapterNaming.plan(List.of(a, b), "{n} {title}", 1, 1, false); b.title = "改动";
        assertThrows(Exception.class, () -> ChapterNaming.apply(library, plan)); assertEquals("A", a.title); assertTrue(a.history.isEmpty());
        assertThrows(Exception.class, () -> ChapterNaming.title("无序号", 1, 1, "A", false));
    }
    @Test public void tagsAreTokenizedForExactFiltering() {
        assertEquals(List.of("草稿", "人物", "雨夜"), Library.tags("#草稿 #人物，草稿; #雨夜")); assertFalse(Library.tags("#草稿二").contains("草稿"));
    }
    @Test public void adjacentCreationKeepsVolumeTagsAndBothBoundaryOrders() throws Exception {
        Library library = new Library(); Section a = library.addSection("文稿", "甲卷"), b = library.addSection("文稿", "乙卷");
        Document first = library.create("原章", "原正文", "文稿"), other = library.create("后卷", "不变", "文稿");
        library.assignSection(first, a); library.assignSection(other, b); first.tags = "#人物 #草稿"; first.readOnly = true; first.checkpoint(true);
        Document before = library.createAdjacent(first, false), after = library.createAdjacent(first, true);
        assertEquals(List.of(before, first, after, other), library.bookDocuments("文稿")); assertEquals(a.id, before.sectionId); assertEquals(first.tags, after.tags);
        assertFalse(before.readOnly); assertEquals("原正文", first.body); assertEquals(1, first.history.size());
        Library restored = LibraryCodec.decode(LibraryCodec.encode(library)); assertEquals(List.of(before.id, first.id, after.id, other.id), restored.bookDocuments("文稿").stream().map(d -> d.id).toList());
    }
    @Test public void adjacentCreationRejectsTrashAndForeignAnchorsWithoutAddingDocuments() {
        Library library = new Library(); Document trash = library.create("回收", "保留", "文稿"); library.trash(trash);
        assertThrows(Exception.class, () -> library.createAdjacent(trash, true)); assertThrows(Exception.class, () -> library.createAdjacent(new Document(), false));
        assertEquals(1, library.documents.size()); assertEquals("保留", trash.body);
    }
}
