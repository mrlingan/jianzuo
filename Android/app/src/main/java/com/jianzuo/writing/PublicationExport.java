package com.jianzuo.writing;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Self-contained EPUB 3 and OOXML, using the same supported Markdown blocks as the preview. */
public final class PublicationExport {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern INLINE = Pattern.compile("`([^`\\n]+)`|\\*\\*(.+?)\\*\\*|__(.+?)__|(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)|(?<![!\\\\])\\[([^]\\n]+)\\]\\(([^)\\s]+)\\)");
    private static final String CSS = "body{max-width:48em;margin:2em auto;padding:0 1em;color:#302e29;font-family:serif;line-height:1.8;overflow-wrap:anywhere}pre{white-space:pre-wrap;background:#f3efe8;padding:1em}blockquote{border-left:3px solid #b05c3d;padding-left:1em}table{border-collapse:collapse}th,td{border:1px solid #b5ada0;padding:.4em .8em}h1,h2,h3{line-height:1.4}";
    public static String xml(String source) {
        StringBuilder clean = new StringBuilder();
        source.codePoints().forEach(cp -> {
            if (cp == 9 || cp == 10 || cp == 13 || cp >= 32 && cp <= 0xD7FF || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF) clean.appendCodePoint(cp);
            else clean.append('\uFFFD');
        });
        return clean.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    public static String html(String title, String body) { return "<!DOCTYPE html>" + xhtml(title, htmlBlocks(body)); }
    private static String xhtml(String title, String content) {
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"zh-CN\" xml:lang=\"zh-CN\"><head><meta charset=\"utf-8\"/><title>" + xml(title) + "</title><style>" + CSS + "</style></head><body><h1 id=\"title\">" + xml(title) + "</h1>" + content + "</body></html>";
    }
    private static String inline(String source, int depth) {
        if (depth > 8) return xml(source);
        Matcher matcher = INLINE.matcher(source); StringBuilder out = new StringBuilder(); int end = 0;
        while (matcher.find()) {
            out.append(xml(source.substring(end, matcher.start())));
            if (matcher.group(1) != null) out.append("<code>").append(xml(matcher.group(1))).append("</code>");
            else if (matcher.group(2) != null || matcher.group(3) != null) out.append("<strong>").append(inline(matcher.group(2) != null ? matcher.group(2) : matcher.group(3), depth + 1)).append("</strong>");
            else if (matcher.group(4) != null) out.append("<em>").append(inline(matcher.group(4), depth + 1)).append("</em>");
            else {
                String url = matcher.group(6); boolean safe = url.matches("(?i)^(https?://|mailto:).+");
                if (safe) out.append("<a href=\"").append(xml(url)).append("\">").append(inline(matcher.group(5), depth + 1)).append("</a>");
                else out.append(xml(matcher.group()));
            }
            end = matcher.end();
        }
        return out.append(xml(source.substring(end))).toString().replace("\n", "<br/>");
    }
    public static String htmlBlocks(String source) {
        StringBuilder out = new StringBuilder(); int heading = 0;
        for (Markdown.Block block : Markdown.parse(source)) {
            switch (block.kind()) {
                case "heading" -> out.append("<h").append(block.level()).append(" id=\"h").append(++heading).append("\">").append(inline(block.text(), 0)).append("</h").append(block.level()).append('>');
                case "divider" -> out.append("<hr/>");
                case "code" -> out.append("<pre><code>").append(xml(block.text())).append("</code></pre>");
                case "quote" -> out.append("<blockquote>").append(inline(block.text(), 0)).append("</blockquote>");
                case "table" -> {
                    out.append("<table>"); String[] rows = block.text().split("\n");
                    for (int i = 0; i < rows.length; i++) { if (i == 1) continue; String cell = i == 0 ? "th" : "td"; out.append("<tr>");
                        for (String value : cells(rows[i])) out.append('<').append(cell).append('>').append(inline(value.trim(), 0)).append("</").append(cell).append('>');
                        out.append("</tr>");
                    } out.append("</table>");
                }
                default -> out.append("<p>").append(block.kind().equals("list") || block.kind().equals("task") ? xml(block.marker()) + " " : "").append(inline(block.text(), 0)).append("</p>");
            }
        }
        return out.toString();
    }
    static String[] cells(String row) {
        row = row.trim(); if (row.startsWith("|")) row = row.substring(1); if (row.endsWith("|")) row = row.substring(0, row.length() - 1);
        return row.split("\\|", -1);
    }
    public static void epub(String title, String body, OutputStream output) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            byte[] mimetype = "application/epub+zip".getBytes(StandardCharsets.US_ASCII); CRC32 crc = new CRC32(); crc.update(mimetype);
            ZipEntry entry = new ZipEntry("mimetype"); entry.setMethod(ZipEntry.STORED); entry.setSize(mimetype.length); entry.setCompressedSize(mimetype.length); entry.setCrc(crc.getValue());
            zip.putNextEntry(entry); zip.write(mimetype); zip.closeEntry();
            put(zip, "META-INF/container.xml", XML + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"EPUB/package.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            put(zip, "EPUB/package.opf", XML + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"book-id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"book-id\">urn:uuid:" + UUID.randomUUID() + "</dc:identifier><dc:title>" + xml(title) + "</dc:title><dc:language>zh-CN</dc:language><meta property=\"dcterms:modified\">" + Instant.now().truncatedTo(ChronoUnit.SECONDS) + "</meta></metadata><manifest><item id=\"content\" href=\"content.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/></manifest><spine><itemref idref=\"content\"/></spine></package>");
            StringBuilder nav = new StringBuilder("<nav epub:type=\"toc\" id=\"toc\"><h2>目录</h2><ol><li><a href=\"content.xhtml#title\">").append(xml(title)).append("</a></li>"); int index = 0;
            for (Markdown.Block block : Markdown.parse(body)) if (block.kind().equals("heading")) nav.append("<li><a href=\"content.xhtml#h").append(++index).append("\">").append(xml(block.text())).append("</a></li>");
            put(zip, "EPUB/nav.xhtml", XML + xhtml("目录", nav.append("</ol></nav>").toString()));
            put(zip, "EPUB/content.xhtml", XML + xhtml(title, htmlBlocks(body)));
        }
    }
    public static void docx(String title, String body, OutputStream output) throws Exception {
        StringBuilder content = new StringBuilder(XML).append("<w:document xmlns:w=\"").append(W).append("\"><w:body>");
        content.append(paragraph(title, "<w:pStyle w:val=\"Title\"/>", false));
        for (Markdown.Block block : Markdown.parse(body)) {
            if (block.kind().equals("table")) {
                content.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>");
                for (String edge : List.of("top", "left", "bottom", "right", "insideH", "insideV")) content.append("<w:").append(edge).append(" w:val=\"single\" w:sz=\"4\" w:color=\"B5ADA0\"/>");
                content.append("</w:tblBorders></w:tblPr><w:tblGrid/>"); String[] rows = block.text().split("\n");
                for (int i = 0; i < rows.length; i++) { if (i == 1) continue; content.append("<w:tr>");
                    for (String cell : cells(rows[i])) content.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>").append(paragraph(cell.trim(), "", true)).append("</w:tc>");
                    content.append("</w:tr>");
                } content.append("</w:tbl>"); continue;
            }
            String props = switch (block.kind()) {
                case "heading" -> "<w:pStyle w:val=\"Heading" + block.level() + "\"/>";
                case "quote" -> "<w:ind w:left=\"480\"/>";
                case "code" -> "<w:pStyle w:val=\"Code\"/>";
                case "divider" -> "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"1\"/></w:pBdr>";
                default -> "";
            };
            String value = block.kind().equals("list") || block.kind().equals("task") ? block.marker() + " " + block.text() : block.text();
            content.append(paragraph(value, props, !block.kind().equals("code")));
        }
        content.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>");
        StringBuilder styles = new StringBuilder(XML).append("<w:styles xmlns:w=\"").append(W).append("\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:eastAsia=\"SimSun\"/><w:sz w:val=\"24\"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"360\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>");
        styles.append(style("Title", "Title", 40, -1));
        for (int i = 1; i <= 6; i++) styles.append(style("Heading" + i, "heading " + i, Math.max(24, 38 - i * 2), i - 1));
        styles.append("<w:style w:type=\"paragraph\" w:styleId=\"Code\"><w:name w:val=\"Code\"/><w:rPr><w:rFonts w:ascii=\"Consolas\"/><w:sz w:val=\"20\"/></w:rPr></w:style></w:styles>");
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/></Types>");
            put(zip, "_rels/.rels", relationships("officeDocument", "word/document.xml"));
            put(zip, "word/_rels/document.xml.rels", relationships("styles", "styles.xml"));
            put(zip, "word/document.xml", content.toString()); put(zip, "word/styles.xml", styles.toString());
        }
    }
    private static String style(String id, String name, int size, int level) {
        return "<w:style w:type=\"paragraph\" w:styleId=\"" + id + "\"><w:name w:val=\"" + name + "\"/><w:pPr><w:keepNext/>" + (level < 0 ? "" : "<w:outlineLvl w:val=\"" + level + "\"/>") + "</w:pPr><w:rPr><w:b/><w:sz w:val=\"" + size + "\"/></w:rPr></w:style>";
    }
    private static String relationships(String kind, String target) {
        return XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/" + kind + "\" Target=\"" + target + "\"/></Relationships>";
    }
    private static String paragraph(String text, String props, boolean markdown) {
        return "<w:p><w:pPr>" + props + "</w:pPr>" + (markdown ? runs(text, "", 0) : run(text, "")) + "</w:p>";
    }
    private static String runs(String source, String props, int depth) {
        if (depth > 8) return run(source, props);
        Matcher matcher = INLINE.matcher(source); StringBuilder out = new StringBuilder(); int end = 0;
        while (matcher.find()) {
            out.append(run(source.substring(end, matcher.start()), props));
            if (matcher.group(1) != null) out.append(run(matcher.group(1), props + "<w:rFonts w:ascii=\"Consolas\"/>"));
            else if (matcher.group(2) != null || matcher.group(3) != null) out.append(runs(matcher.group(2) != null ? matcher.group(2) : matcher.group(3), props + "<w:b/>", depth + 1));
            else if (matcher.group(4) != null) out.append(runs(matcher.group(4), props + "<w:i/>", depth + 1));
            else out.append(run(matcher.group(5) + " (" + matcher.group(6) + ")", props));
            end = matcher.end();
        }
        return out.append(run(source.substring(end), props)).toString();
    }
    private static String run(String text, String props) {
        return "<w:r><w:rPr>" + props + "</w:rPr><w:t xml:space=\"preserve\">" + xml(text).replace("\n", "</w:t><w:br/><w:t xml:space=\"preserve\">").replace("\t", "</w:t><w:tab/><w:t xml:space=\"preserve\">") + "</w:t></w:r>";
    }
    private static void put(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private PublicationExport() {}
}
