package com.jianzuo.writing;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public class RecoverySearchTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File snapshot(Library library) throws Exception {
        File file = temporary.newFile(); Files.writeString(file.toPath(), LibraryCodec.encode(library)); return file;
    }
    @Test public void searchesLiveTrashHistoryAndOldSnapshotsWithoutChangingThem() throws Exception {
        Library library = new Library(); Document live = library.create("当前", "现在已删掉片段", "文稿");
        live.history.add(new Document.Revision(123, "旧标题", "找回片段𠀀😀"));
        Document trash = library.create("废纸篓", "找回片段二", "灵感"); library.trash(trash);
        Library old = new Library(); old.create("快照", "找回片段三", "日记"); File backup = snapshot(old);
        String before = LibraryCodec.encode(library), disk = Files.readString(backup.toPath());
        var result = RecoverySearch.search("找回片段", RecoverySearch.current(library), new File[]{backup});
        assertEquals(3, result.hits().size()); assertEquals(1, result.snapshots()); assertEquals(0, result.skipped());
        assertTrue(result.hits().stream().anyMatch(hit -> hit.candidate().source().equals("历史版本")));
        assertTrue(result.hits().stream().anyMatch(hit -> hit.candidate().source().equals("废纸篓")));
        assertTrue(result.hits().stream().anyMatch(hit -> hit.candidate().source().startsWith("自动备份")));
        assertEquals(before, LibraryCodec.encode(library)); assertEquals(disk, Files.readString(backup.toPath()));
    }
    @Test public void equalVersionsCoalesceButSeparateDocumentsAndChangedTitlesRemain() throws Exception {
        Library library = new Library(); Document doc = library.create("标题", "相同正文", "文稿"); doc.checkpoint(true);
        File same = snapshot(library); Library old = LibraryCodec.decode(LibraryCodec.encode(library)); old.documents.get(0).title = "旧标题";
        old.create("另一篇", "相同正文", "文稿");
        var result = RecoverySearch.search("正文", RecoverySearch.current(library), new File[]{same, snapshot(old)});
        assertEquals(3, result.hits().size()); assertEquals("当前文稿", result.hits().get(0).candidate().source());
    }
    @Test public void searchesAreLiteralCaseInsensitiveAndCapturedValuesAreImmutable() throws Exception {
        Library library = new Library(); Document doc = library.create("HELLO [a.*]", "中".repeat(80) + "😀匹配𠀀" + "尾".repeat(200), "文稿");
        var captured = RecoverySearch.current(library); doc.body = "修改后";
        assertEquals(1, RecoverySearch.search("hello [a.*]", captured, new File[0]).hits().size());
        assertEquals(0, RecoverySearch.search("H.*O", captured, new File[0]).hits().size());
        String excerpt = RecoverySearch.search("😀匹配", captured, new File[0]).hits().get(0).excerpt(); assertTrue(excerpt.startsWith("…")); assertTrue(excerpt.endsWith("…")); assertTrue(excerpt.contains("😀匹配𠀀"));
        assertThrows(IllegalArgumentException.class, () -> RecoverySearch.search(" ", captured, new File[0]));
    }
    @Test public void damagedSnapshotsDoNotHideUsableOnesAndReadsAreBounded() throws Exception {
        File corrupt = temporary.newFile(); Files.writeString(corrupt.toPath(), "broken");
        Library old = new Library(); old.create("保存", "有效正文", "文稿"); File good = snapshot(old);
        var result = RecoverySearch.search("正文", List.of(), new File[]{corrupt, good});
        assertEquals(1, result.hits().size()); assertEquals(1, result.skipped()); assertEquals(1, result.snapshots());
        assertThrows(IOException.class, () -> RecoverySearch.read(good, 3));
        File[] many = new File[25]; Arrays.fill(many, good); assertEquals(24, RecoverySearch.search("正文", List.of(), many).snapshots());
    }
    @Test public void resultLimitsAndInterruptsAreVisibleAndSafe() throws Exception {
        Library library = new Library(); for (int i = 0; i < 202; i++) library.create("篇" + i, "关键词", "文稿");
        var result = RecoverySearch.search("关键词", RecoverySearch.current(library), new File[0]); assertEquals(200, result.hits().size()); assertTrue(result.truncated());
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class, () -> RecoverySearch.search("关键词", RecoverySearch.current(library), new File[0])); }
        finally { Thread.interrupted(); }
    }
    @Test public void restoringCreatesAnEditableCopyAndPreservesSourceState() {
        Library library = new Library(); Document source = library.create("原文", "新正文", "文稿"); source.readOnly = true; library.trash(source); source.history.add(new Document.Revision(123, "旧标题", "旧正文𠀀😀"));
        var value = new RecoverySearch.Candidate(source.id, "旧标题", "旧正文𠀀😀", "找回笔记本", "历史版本", 123);
        Document restored = RecoverySearch.restore(library, value);
        assertNotEquals(source.id, restored.id); assertEquals("旧标题", restored.title); assertEquals("旧正文𠀀😀", restored.body); assertFalse(restored.readOnly); assertNull(restored.deletedAt);
        assertTrue(library.notebooks.contains("找回笔记本")); assertEquals("新正文", source.body); assertNotNull(source.deletedAt); assertTrue(source.readOnly); assertEquals(1, source.history.size());
    }
}
