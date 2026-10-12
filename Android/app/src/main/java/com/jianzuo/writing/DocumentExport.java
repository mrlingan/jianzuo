package com.jianzuo.writing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.OutputStream;

/** Native text typesetting; no WebView and no network assets. */
final class DocumentExport {
    static void pdf(String title, String body, OutputStream output) throws Exception { pdf(title, body, output, android.graphics.Typeface.SERIF); }
    static void pdf(String title, String body, OutputStream output, android.graphics.Typeface font) throws Exception {
        pdf(title, body, output, font, true);
    }
    static void pdf(String title, String body, OutputStream output, android.graphics.Typeface font, boolean markdown) throws Exception {
        PrintLayout layout = new PrintLayout(title, body, 499, 13, font, markdown);
        java.util.List<PrintLayout.Page> pages = layout.pages(720, 1000);
        PdfDocument document = new PdfDocument();
        try {
            int pageNumber = 0;
            for (PrintLayout.Page content : pages) {
                pageNumber++;
                PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
                try {
                    Canvas canvas = page.getCanvas(); canvas.drawColor(Color.WHITE);
                    for (PrintLayout.Slice slice : content.slices()) slice.element().draw(canvas, 48, 48 + slice.y(), slice.start(), slice.end());
                    canvas.drawText("简作 · " + pageNumber, 48, 812, PrintLayout.paint(9, android.graphics.Typeface.SANS_SERIF));
                } finally { document.finishPage(page); }
            }
            document.writeTo(output);
        } finally { document.close(); }
    }
    static void png(String title, String body, OutputStream output) throws Exception { png(title, body, output, android.graphics.Typeface.SERIF); }
    static void png(String title, String body, OutputStream output, android.graphics.Typeface font) throws Exception {
        png(title, body, output, font, true);
    }
    static void png(String title, String body, OutputStream output, android.graphics.Typeface font, boolean markdown) throws Exception {
        PrintLayout layout = new PrintLayout(title, body, 944, 30, font, markdown);
        int height = layout.height(15820) + 180;
        Bitmap bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.parseColor("#FAF7F1")); layout.draw(canvas, 68, 68);
            canvas.drawText("简作 · 给文字一点空间", 68, height - 45, PrintLayout.paint(22, android.graphics.Typeface.SERIF));
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new UserError.Io("ui_image_export_failed_2ce917", "长图生成失败");
        } finally { bitmap.recycle(); }
    }
    static String html(String title, String body) {
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>" + escape(title)
            + "</title><style>body{max-width:760px;margin:48px auto;padding:0 24px;color:#302e29;background:#faf7f1;font-family:serif}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:18px/1.8 serif}</style><h1>"
            + escape(title) + "</h1><pre>" + escape(body) + "</pre></html>";
    }
    private static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private DocumentExport() {}
}
