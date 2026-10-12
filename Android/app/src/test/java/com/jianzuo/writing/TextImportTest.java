package com.jianzuo.writing;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class TextImportTest {
    @Test public void naturalOrderHandlesPaddingAndNumbersLargerThanLong() {
        List<String> names = new ArrayList<>(List.of("第10章.md", "第02章.md", "第1章.md", "第2章.md", "第999999999999999999999999章.md", "第1000000000000000000000000章.md"));
        names.sort(TextImport::naturalOrder);
        assertEquals(List.of("第1章.md", "第2章.md", "第02章.md", "第10章.md", "第999999999999999999999999章.md", "第1000000000000000000000000章.md"), names);
    }
    @Test public void cancellationIsCheckedBeforeListingAndDuringRead() throws Exception {
        Tree tree = new Tree(); tree.folders.put("root", List.of(file("a", "a.md"))); tree.file("a", "正文");
        TextImport.Monitor cancelled = new TextImport.Monitor() { public void check() throws InterruptedIOException { throw new InterruptedIOException("cancel"); } };
        assertThrows(InterruptedIOException.class, () -> TextImport.folder(tree, "root", "文稿", cancelled)); assertTrue(tree.opened.isEmpty());
        final boolean[] stop = {false}, closed = {false};
        InputStream blocked = new ByteArrayInputStream(new byte[20000]) {
            @Override public synchronized int read(byte[] b, int off, int len) { stop[0] = true; return super.read(b, off, len); }
            @Override public void close() { closed[0] = true; }
        };
        TextImport.Tree source = new TextImport.Tree() {
            public List<TextImport.Entry> children(String id) { return tree.folders.get(id); }
            public InputStream open(String id) { return blocked; }
        };
        TextImport.Monitor monitor = new TextImport.Monitor() { public void check() throws InterruptedIOException { if (stop[0]) throw new InterruptedIOException("cancel"); } };
        assertThrows(InterruptedIOException.class, () -> TextImport.folder(source, "root", "文稿", monitor)); assertTrue(closed[0]);
    }
    @Test public void progressCountsAllEntriesAndOnlyCompletedTextFiles() throws Exception {
        Tree tree = new Tree(); tree.folders.put("root", List.of(file("ten", "10.md"), file("two", "2.md"), file("image", "image.png")));
        tree.file("two", "中"); tree.file("ten", "文"); final long[] last = new long[3];
        var items = TextImport.folder(tree, "root", "文稿", new TextImport.Monitor() { public void progress(int entries, int files, long bytes) { last[0] = entries; last[1] = files; last[2] = bytes; } });
        assertEquals(List.of("2", "10"), items.stream().map(TextImport.Item::title).toList()); assertArrayEquals(new long[]{3, 2, 6}, last);
    }
    private static final class Tree implements TextImport.Tree {
        final Map<String, List<TextImport.Entry>> folders = new HashMap<>();
        final Map<String, byte[]> files = new HashMap<>();
        final List<String> opened = new ArrayList<>();
        public List<TextImport.Entry> children(String id) throws IOException {
            if (!folders.containsKey(id)) throw new IOException("权限失效"); return folders.get(id);
        }
        public InputStream open(String id) throws IOException {
            opened.add(id); if (!files.containsKey(id)) throw new IOException("文件已移动"); return new ByteArrayInputStream(files.get(id));
        }
        void file(String id, String text) { files.put(id, text.getBytes(StandardCharsets.UTF_8)); }
    }
    private TextImport.Entry file(String id, String name) { return new TextImport.Entry(id, name, false); }
    private TextImport.Entry dir(String id, String name) { return new TextImport.Entry(id, name, true); }
    @Test public void folderImportIsOrderedAndGroupsOnlyTheFirstLevel() throws Exception {
        Tree tree = new Tree();
        tree.folders.put("root", List.of(dir("book", "小说"), file("z", "z.TXT"), file("a", "a.markdown"), dir("hidden", ".私密"), file("image", "图片.png"), file("dot", ".忽略.md")));
        tree.folders.put("book", List.of(dir("deep", "第二卷"), file("chapter", "第一章.md")));
        tree.folders.put("deep", List.of(file("last", "尾声.txt")));
        tree.file("a", "\uFEFF中文𠀀😀\r\n"); tree.file("z", "Z"); tree.file("chapter", "章"); tree.file("last", "终");
        List<TextImport.Item> result = TextImport.folder(tree, "root", "文稿");
        assertEquals(List.of("a", "z", "第一章", "尾声"), result.stream().map(TextImport.Item::title).toList());
        assertEquals(List.of("文稿", "文稿", "小说", "小说"), result.stream().map(TextImport.Item::notebook).toList());
        assertEquals("中文𠀀😀\r\n", result.get(0).body()); assertEquals(4, tree.opened.size());
    }
    @Test public void malformedAndInaccessibleFilesAbortTheWholePlan() {
        Tree tree = new Tree(); tree.folders.put("root", List.of(file("a", "a.md"), file("b", "b.md")));
        tree.file("a", "有效正文"); tree.files.put("b", new byte[]{(byte)0xc3, 0x28});
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> TextImport.folder(tree, "root", "文稿"));
        tree.files.remove("b"); assertThrows(IOException.class, () -> TextImport.folder(tree, "root", "文稿"));
    }
    @Test public void folderCyclesEmptyTreesAndEntryLimitsAreRejected() {
        Tree tree = new Tree(); tree.folders.put("root", List.of(dir("root", "循环")));
        assertThrows(IOException.class, () -> TextImport.folder(tree, "root", "文稿"));
        tree.folders.put("root", List.of(file("image", "image.png")));
        assertThrows(IOException.class, () -> TextImport.folder(tree, "root", "文稿")); assertTrue(tree.opened.isEmpty());
        List<TextImport.Entry> entries = new ArrayList<>(); for (int i = 0; i < 5001; i++) entries.add(file("id" + i, i + ".png"));
        tree.folders.put("root", entries); assertThrows(IOException.class, () -> TextImport.folder(tree, "root", "文稿"));
    }
    @Test public void fileCountAndByteLimitsApplyAcrossTheEntireTree() throws Exception {
        Tree tree = new Tree(); List<TextImport.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 1001; i++) { entries.add(file("id" + i, i + ".md")); tree.file("id" + i, ""); }
        tree.folders.put("root", entries); assertThrows(IOException.class, () -> TextImport.folder(tree, "root", "文稿"));
        TextImport.Budget budget = new TextImport.Budget(); byte[] full = new byte[TextImport.MAX_FILE_BYTES]; Arrays.fill(full, (byte)'a');
        for (int i = 0; i < 32; i++) assertEquals(full.length, budget.read(new ByteArrayInputStream(full)).length());
        assertThrows(IOException.class, () -> budget.read(new ByteArrayInputStream(new byte[]{'a'})));
        assertThrows(IOException.class, () -> new TextImport.Budget().read(new ByteArrayInputStream(new byte[TextImport.MAX_FILE_BYTES + 1])));
    }
    @Test public void notebookNamesAvoidReservedNamesAndSplitSurrogates() throws Exception {
        Tree tree = new Tree(); String longName = "中".repeat(39) + "😀";
        tree.folders.put("root", List.of(dir("book", longName), dir("reserved", Library.TRASH)));
        tree.folders.put("book", List.of(file("a", "a.md"))); tree.file("a", "a");
        tree.folders.put("reserved", List.of(file("b", "b.md"))); tree.file("b", "b");
        List<TextImport.Item> result = TextImport.folder(tree, "root", "日记");
        assertTrue(result.stream().anyMatch(item -> item.notebook().equals("中".repeat(39))));
        assertTrue(result.stream().anyMatch(item -> item.notebook().equals("日记")));
    }
}
