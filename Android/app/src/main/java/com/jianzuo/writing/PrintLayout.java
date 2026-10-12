package com.jianzuo.writing;

import android.graphics.*;
import android.text.*;
import android.text.style.LeadingMarginSpan;
import java.util.*;

/** One native typesetter for PDF pages and PNG. Splits exclusively at text line boundaries. */
final class PrintLayout {
    static final int INK = Color.rgb(48, 46, 41), ACCENT = Color.rgb(153, 76, 49);
    static final int SHADE = Color.rgb(244, 241, 235), BORDER = Color.rgb(195, 186, 173);
    final List<Element> elements = new ArrayList<>();
    final int width;
    final float size;
    private final Typeface font;
    record Slice(Element element, int start, int end, float y, float height) {}
    record Page(List<Slice> slices) {}

    PrintLayout(String title, String body, int width, float size, Typeface font, boolean markdown) {
        this.width = width; this.size = size; this.font = font;
        elements.add(text("title", title, 0, 0, size));
        if (!markdown) { elements.add(text("source", body.replace("\r\n", "\n").replace('\r', '\n'), 0, 0, 0)); return; }
        for (Markdown.Block block : Markdown.parse(body)) {
            switch (block.kind()) {
                case "table" -> table(block.text());
                case "list", "task" -> {
                    String prefix = block.marker() + "  ";
                    SpannableStringBuilder value = new SpannableStringBuilder(prefix).append(MarkdownViews.printInline(block.text()));
                    value.setSpan(new LeadingMarginSpan.Standard(0, (int)Math.ceil(paint(size, font).measureText(prefix))), 0, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    elements.add(element(block.kind(), List.of(value), size, font, 0, 0, size * .25f));
                }
                case "divider" -> elements.add(new Element("divider", List.of(), 0, size * .5f, size * .5f, width));
                default -> elements.add(text(block.kind(), block.text(), block.level(), block.kind().equals("heading") ? size * .7f : 0, size * .7f));
            }
        }
    }
    private Element text(String kind, String value, int level, float before, float after) {
        float textSize = kind.equals("title") ? size * 2 : kind.equals("heading") ? size * Math.max(1.04f, 1.85f - level * .14f) : kind.equals("code") ? size * .92f : size;
        Typeface face = kind.equals("code") ? Typeface.MONOSPACE : kind.equals("title") || kind.equals("heading") ? Typeface.create(font, Typeface.BOLD) : font;
        boolean literal = kind.equals("code") || kind.equals("source") || kind.equals("title");
        return element(kind, List.of(literal ? value : MarkdownViews.printInline(value)), textSize, face,
                kind.equals("code") || kind.equals("quote") ? size * .7f : 0, before, after);
    }
    private Element element(String kind, List<? extends CharSequence> values, float textSize, Typeface face, float padding, float before, float after) {
        int cellWidth = width / values.size();
        List<StaticLayout> layouts = new ArrayList<>();
        for (CharSequence value : values) {
            TextPaint paint = paint(textSize, face);
            layouts.add(StaticLayout.Builder.obtain(value, 0, value.length(), paint, Math.max(1, (int)(cellWidth - 2 * padding)))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(size * .25f, 1).build());
        }
        return new Element(kind, layouts, padding, before, after, width);
    }
    private void table(String source) {
        String[] rows = source.split("\n"); int columns = 0;
        for (int i = 0; i < rows.length; i++) if (i != 1) columns = Math.max(columns, PublicationExport.cells(rows[i]).length);
        // Preserve every cell in very wide tables rather than shrinking it into unreadable columns.
        if (columns > 8) { elements.add(text("code", source, 0, 0, size * .7f)); return; }
        Element header = null;
        for (int i = 0; i < rows.length; i++) {
            if (i == 1) continue;
            List<CharSequence> values = new ArrayList<>(); String[] cells = PublicationExport.cells(rows[i]);
            for (int j = 0; j < columns; j++) values.add(MarkdownViews.printInline(j < cells.length ? cells[j].trim() : ""));
            Element row = element(i == 0 ? "table-header" : "table-row", values, size * .9f, i == 0 ? Typeface.create(font, Typeface.BOLD) : font,
                    size * .45f, 0, i == rows.length - 1 ? size * .7f : 0);
            if (i == 0) header = row; else row.header = header;
            elements.add(row);
        }
    }
    int height(int maximum) {
        double height = 0;
        for (Element element : elements) {
            height += element.before + element.height(0, element.lines()) + element.after;
            if (height > maximum) throw new UserError.Invalid("ui_image_exceeds_16_000_pixels_split_into_chapte_2674d0", "长图超过 16,000 像素，请分章或改用 PDF");
        }
        return (int)Math.ceil(height);
    }
    void draw(Canvas canvas, float x, float y) {
        for (Element element : elements) {
            y += element.before; element.draw(canvas, x, y, 0, element.lines()); y += element.height(0, element.lines()) + element.after;
        }
    }
    List<Page> pages(int pageHeight, int maximumPages) {
        List<Page> pages = new ArrayList<>(); List<Slice> slices = new ArrayList<>(); float y = 0;
        pages.add(new Page(slices));
        for (int index = 0; index < elements.size(); index++) {
            Element element = elements.get(index); int lines = element.lines(), start = 0;
            float keep = element.height(0, lines);
            if ((element.kind.equals("heading") || element.kind.equals("title") || element.kind.equals("table-header")) && index + 1 < elements.size()) {
                Element next = elements.get(index + 1); float nextHeight = next.height(0, next.lines());
                keep += element.after + next.before + (keep + element.after + next.before + nextHeight <= pageHeight ? nextHeight : next.height(0, Math.min(2, next.lines())));
            }
            float repeat = repeatHeight(element, pageHeight);
            Element previous = slices.isEmpty() ? null : slices.get(slices.size() - 1).element();
            boolean keepsNext = element.kind.equals("heading") || element.kind.equals("title") || element.kind.equals("table-header");
            boolean followsHeading = !keepsNext && previous != null && (previous.kind.equals("heading") || previous.kind.equals("title") || previous.kind.equals("table-header"));
            if (y > 0 && !followsHeading && keep + element.before <= pageHeight - repeat && y + element.before + keep > pageHeight) {
                slices = nextPage(pages, maximumPages); y = 0;
            }
            if (y == 0 && repeat > 0) { slices.add(new Slice(element.header, 0, element.header.lines(), 0, repeat)); y = repeat; }
            if (y > 0) y += element.before;
            while (start < lines) {
                int end = start;
                while (end < lines && element.height(start, end + 1) <= pageHeight - y) end++;
                if (end == start) {
                    if (y == 0 || y == repeat) throw new UserError.Invalid("ui_cannot_paginate_this_content_export_separate_f25c4e", "内容无法分页，请分章导出");
                    slices = nextPage(pages, maximumPages); y = 0;
                } else {
                    float height = element.height(start, end); slices.add(new Slice(element, start, end, y, height)); y += height; start = end;
                    if (start < lines) { slices = nextPage(pages, maximumPages); y = 0; }
                }
                if (start < lines && y == 0 && repeat > 0) { slices.add(new Slice(element.header, 0, element.header.lines(), 0, repeat)); y = repeat; }
            }
            y += element.after;
        }
        return pages;
    }
    private float repeatHeight(Element element, int pageHeight) {
        if (element.header == null) return 0;
        float height = element.header.height(0, element.header.lines());
        return height <= pageHeight / 3f && height + element.height(0, 1) <= pageHeight ? height : 0;
    }
    private static List<Slice> nextPage(List<Page> pages, int maximum) {
        if (pages.size() >= maximum) throw new UserError.Invalid("ui_document_exceeds_1_000_pages_export_separate_000f0c", "文稿超过 1,000 页，请分章导出");
        List<Slice> slices = new ArrayList<>(); pages.add(new Page(slices)); return slices;
    }
    static TextPaint paint(float size, Typeface font) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setColor(INK); paint.linkColor = ACCENT; paint.setTextSize(size); paint.setTypeface(font); return paint;
    }
    static final class Element {
        final String kind; final List<StaticLayout> layouts;
        final float padding, before, after; final int width;
        Element header;
        Element(String kind, List<StaticLayout> layouts, float padding, float before, float after, int width) {
            this.kind = kind; this.layouts = layouts; this.padding = padding; this.before = before; this.after = after; this.width = width;
        }
        int lines() { return layouts.stream().mapToInt(Layout::getLineCount).max().orElse(1); }
        float height(int start, int end) {
            if (kind.equals("divider")) return 1;
            float height = 0;
            for (StaticLayout layout : layouts) if (start < layout.getLineCount()) height = Math.max(height, layout.getLineBottom(Math.min(end, layout.getLineCount()) - 1) - layout.getLineTop(start));
            return height + padding * 2;
        }
        void draw(Canvas canvas, float x, float y, int start, int end) {
            float height = height(start, end); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean table = kind.startsWith("table-");
            if (kind.equals("divider")) { paint.setColor(BORDER); canvas.drawRect(x, y, x + width, y + 1, paint); return; }
            if (kind.equals("code") || kind.equals("quote") || kind.equals("table-header")) { paint.setColor(SHADE); canvas.drawRect(x, y, x + width, y + height, paint); }
            if (kind.equals("quote")) { paint.setColor(ACCENT); canvas.drawRect(x, y, x + 3, y + height, paint); }
            float cellWidth = width / (float)layouts.size();
            for (int i = 0; i < layouts.size(); i++) {
                float left = x + cellWidth * i;
                if (table) { paint.setColor(BORDER); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.6f); canvas.drawRect(left, y, left + cellWidth, y + height, paint); }
                StaticLayout layout = layouts.get(i); if (start >= layout.getLineCount()) continue;
                canvas.save(); canvas.clipRect(left + padding, y + padding, left + cellWidth - padding, y + height - padding);
                canvas.translate(left + padding, y + padding - layout.getLineTop(start)); layout.draw(canvas); canvas.restore();
            }
        }
    }
}
