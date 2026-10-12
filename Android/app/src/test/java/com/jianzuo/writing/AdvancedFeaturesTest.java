package com.jianzuo.writing;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;

public class AdvancedFeaturesTest {
    @Test public void diffCanReconstructBothVersionsIncludingTrailingNewlines() {
        for (String before : List.of("", "一\n二\n三", "𠀀\n", "行\r\n尾\n", "重复\n重复\n")) {
            for (String after : List.of("", "一\n插入\n三", "𠀀", "行\r\n另一个尾\n", "重复\n")) {
                var result = RevisionDiff.compare(before, after); assertFalse(result.grouped()); assertDiff(before, after, result);
            }
        }
        Random random = new Random(19);
        for (int sample = 0; sample < 80; sample++) {
            StringBuilder before = new StringBuilder(), after = new StringBuilder();
            for (int i = 0; i < 20; i++) { before.append(random.nextInt(4)).append('\n'); after.append(random.nextInt(4)).append('\n'); }
            assertDiff(before.toString(), after.toString(), RevisionDiff.compare(before.toString(), after.toString()));
        }
    }
    @Test public void longDiffUsesBoundedFallbackWithoutLosingText() {
        String before = "old\n".repeat(1500), after = "new\n".repeat(1500);
        var result = RevisionDiff.compare(before, after); assertTrue(result.grouped()); assertDiff(before, after, result);
        assertFalse(RevisionDiff.compare(before, before).grouped());
    }
    private void assertDiff(String before, String after, RevisionDiff.Result result) {
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (var line : result.lines()) { if (line.kind() != '+') a.append(line.text()); if (line.kind() != '-') b.append(line.text()); }
        assertEquals(before, a.toString()); assertEquals(after, b.toString());
    }
    @Test public void selectedReplacementUsesOriginalOffsetsAndLiteralText() {
        String body = "𠀀 cat cat cat"; var matches = TextTools.matches(body, TextTools.pattern("cat", false, true));
        assertEquals("𠀀 $1 cat $1", TextTools.replaceSelected(body, matches, new boolean[]{true, false, true}, "$1"));
        assertEquals(body, TextTools.replaceSelected(body, matches, new boolean[]{false, false, false}, ""));
        var zero = TextTools.matches("ab", TextTools.pattern("(?=.)", true, true));
        assertEquals("a!b", TextTools.replaceSelected("ab", zero, new boolean[]{false, true}, "!"));
        assertThrows(IllegalArgumentException.class, () -> TextTools.replaceSelected(body, matches, new boolean[1], ""));
    }
    @Test public void paragraphMovementKeepsBlankLinesAndCursor() {
        String source = "第一段\n\n𠀀第二段\n最后";
        TextTools.Edit up = TextTools.moveParagraph(source, 8, -1);
        assertEquals("𠀀第二段\n\n第一段\n最后", up.text()); assertEquals(3, up.cursor());
        assertEquals(source, TextTools.moveParagraph(up.text(), up.cursor(), 1).text());
        assertEquals(source, TextTools.moveParagraph(source, 1, -1).text());
        assertEquals(source, TextTools.moveParagraph(source, source.length(), 1).text());
        assertEquals(source, TextTools.moveParagraph(source, 4, 1).text());
    }
    @Test public void searchCanRequireTermsAcrossFieldsAndRankTitlesFirst() {
        Library library = new Library(); Document title = library.create("森林 雨", "", "文稿");
        Document body = library.create("草稿", "森林", "文稿"); body.tags = "雨";
        library.create("雨", "无其他关键词", "文稿");
        assertEquals(List.of(title, body), library.visible(Library.ALL, "森林 雨", "relevance", true));
        assertEquals(List.of(title), library.visible(Library.ALL, "森林 雨", "relevance", false));
        library.trash(title); assertEquals(List.of(body), library.visible(Library.ALL, "森林 雨", "relevance", true));
    }
    @Test public void yearlyStatisticsHandleLeapDayAndDoNotCountOtherYears() {
        Map<String, Integer> days = new HashMap<>(); days.put("2024-02-28", 100); days.put("2024-02-29", 200); days.put("2024-03-01", 300); days.put("2024-03-03", 0); days.put("2025-01-01", 900);
        var year = WritingStats.year(days, 2024); assertEquals(600, year.words()); assertEquals(3, year.activeDays()); assertEquals(3, year.bestStreak());
        assertEquals(LocalDate.of(2024, 3, 1), year.bestDay()); assertEquals(300, year.months()[1]); assertEquals(300, year.months()[2]);
        assertEquals(0, WritingStats.intensity(0, 800)); assertEquals(1, WritingStats.intensity(1, 800)); assertEquals(2, WritingStats.intensity(200, 800)); assertEquals(3, WritingStats.intensity(400, 800)); assertEquals(4, WritingStats.intensity(800, 800));
        assertNull(WritingStats.year(Map.of(), 2026).bestDay());
    }
    static final String BODY = "# 第一章\n\n中文与𠀀 **加粗** *斜体* [官网](https://example.org/?a=1&b=2)\n\n> 引用文字\n\n- [x] 已完成\n\n| 名称 | 数量 |\n| --- | --- |\n| 咖啡 | 2 |\n\n```\n  <script>& code\n\t下一行\n```\n\n## 尾声\n\n<原始 HTML> 应保留为文字。\n";
    @Test public void docxIsWellFormedAndKeepsCjkStructureAndStyles() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); PublicationExport.docx("标题 & <测试>", BODY, output);
        Map<String, byte[]> files = archive(output.toByteArray()); assertTrue(files.containsKey("[Content_Types].xml"));
        for (var file : files.entrySet()) parse(file.getValue());
        var doc = parse(files.get("word/document.xml"));
        assertTrue(doc.getDocumentElement().getTextContent().contains("中文与𠀀")); assertTrue(doc.getDocumentElement().getTextContent().contains("<script>& code"));
        assertEquals(1, doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "tbl").getLength());
        assertTrue(doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "b").getLength() > 0);
        assertFalse(doc.getDocumentElement().getTextContent().contains("**加粗**"));
        fixture("sample.docx", output.toByteArray());
    }
    @Test public void epubHasStoredMimetypeResolvableNavigationAndXml() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); PublicationExport.epub("标题 & <测试>", BODY, output);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) { ZipEntry first = zip.getNextEntry(); assertEquals("mimetype", first.getName()); assertEquals(ZipEntry.STORED, first.getMethod()); assertEquals("application/epub+zip", new String(zip.readAllBytes(), StandardCharsets.US_ASCII)); }
        Map<String, byte[]> files = archive(output.toByteArray());
        for (var file : files.entrySet()) if (!file.getKey().equals("mimetype")) parse(file.getValue());
        String nav = new String(files.get("EPUB/nav.xhtml"), StandardCharsets.UTF_8), content = new String(files.get("EPUB/content.xhtml"), StandardCharsets.UTF_8);
        assertTrue(nav.contains("content.xhtml#h1")); assertTrue(content.contains("id=\"h1\"")); assertTrue(content.contains("<strong>加粗</strong>")); assertTrue(content.contains("&lt;原始 HTML&gt;"));
        assertTrue(content.contains("href=\"https://example.org/?a=1&amp;b=2\""));
        fixture("sample.epub", output.toByteArray()); fixture("sample.html", PublicationExport.html("标题 & <测试>", BODY).getBytes(StandardCharsets.UTF_8));
    }
    @Test public void unsafeMarkupCannotExecuteAndXmlControlCharactersAreReplaced() throws Exception {
        String html = PublicationExport.html("<script>", "[点我](javascript:alert)\n\n![图片](file:///private/image.png)\n\n<script>alert(1)</script>\u0000");
        assertFalse(html.contains("href=\"javascript:")); assertFalse(html.contains("<script>")); assertFalse(html.contains("<img")); assertTrue(html.contains("\uFFFD"));
        assertEquals("𠀀�", PublicationExport.xml("𠀀\uD800"));
        parse(html.substring("<!DOCTYPE html>".length()).getBytes(StandardCharsets.UTF_8));
    }
    private Map<String, byte[]> archive(byte[] bytes) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) { ZipEntry entry; while ((entry = zip.getNextEntry()) != null) result.put(entry.getName(), zip.readAllBytes()); }
        return result;
    }
    private org.w3c.dom.Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private void fixture(String name, byte[] bytes) throws Exception { File folder = new File("build/feature-export"); folder.mkdirs(); Files.write(new File(folder, name).toPath(), bytes); }
}
