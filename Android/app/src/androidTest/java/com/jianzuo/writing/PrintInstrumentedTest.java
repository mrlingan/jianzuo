package com.jianzuo.writing;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.text.Spanned;
import android.text.style.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrintInstrumentedTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static Object field(Object value, String name) {
        try { Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void ready(ActivityScenario<MainActivity> scenario) {
        for (int i = 0; i < 100; i++) {
            AtomicBoolean loaded = new AtomicBoolean(); scenario.onActivity(activity -> loaded.set((boolean)field(activity, "loaded")));
            if (loaded.get()) return; SystemClock.sleep(50);
        }
        fail("Library did not load");
    }
    private File results() { File result = new File(context.getFilesDir(), "print-validation"); result.mkdirs(); return result; }
    private static final String SAMPLE = "# 文字的形状\n\n这一页包含 **粗体**、*斜体*、__强调__ 与 `inline_code()`。中文排版，Hello 世界。\n\n"
        + "> 写下来，就是给时间留下一个坐标。\n\n- 第一项清单，长内容会自动换行并对齐正文。\n- 第二项清单\n1. 有序列表第一项\n2. 有序列表第二项\n- [x] 已完成的任务\n- [ ] 等待完成的任务\n\n"
        + "## 表格与链接\n\n| 项目 | 说明 |\n| --- | --- |\n| **标题** | 按层级排版，长内容自动换行。 |\n| 正文 | 保留中文、英文与标点。 |\n\n"
        + "查看 [Android 文档](https://developer.android.com)。\n\n```java\n// 代码中的 **标记** 与 <标签> 保持原样\nString message = \"Hello 中文\";\nprintln(message);\n```\n\n---\n\n### 尾声\n\n文字仍在这里，排版改变的是阅读的节奏。";

    @Test public void inlineStylesPreserveLiteralCodeImagesUnsafeLinksAndUnicode() {
        Spanned value = (Spanned)MarkdownViews.printInline("**粗体** 与 *斜体*、`**literal**`、[文档](https://example.com)、𠀀😀");
        assertEquals("粗体 与 斜体、**literal**、文档 (https://example.com)、𠀀😀", value.toString());
        assertEquals(2, value.getSpans(0, value.length(), StyleSpan.class).length);
        assertEquals(1, value.getSpans(0, value.length(), TypefaceSpan.class).length);
        assertEquals(1, value.getSpans(0, value.length(), URLSpan.class).length);
        String raw = "![图片](https://example.com/a.png) [危险](javascript:alert) <b>HTML</b> $x_i$";
        assertEquals(raw, MarkdownViews.printInline(raw).toString()); assertEquals(raw, MarkdownViews.inline(raw).toString());
        assertEquals("文档", MarkdownViews.inline("[文档](https://example.com)").toString());
        assertEquals("https://example.com", MarkdownViews.printInline("[https://example.com](https://example.com)").toString());
        PrintLayout literal = new PrintLayout("**原始标题**", "# 保留\n**标记**\n```code```", 499, 13, Typeface.SERIF, false);
        assertEquals("**原始标题**", literal.elements.get(0).layouts.get(0).getText().toString());
        assertEquals("# 保留\n**标记**\n```code```", literal.elements.get(1).layouts.get(0).getText().toString());
    }
    @Test public void pageSlicesCoverEveryLineAndRepeatHeadersWithoutClippingRows() {
        StringBuilder body = new StringBuilder("# 跨页表格\n\n| 章节 | 内容 |\n| --- | --- |\n");
        for (int i = 0; i < 70; i++) body.append("| 行").append(i).append(" | 正文 ").append("长段落 ".repeat(i == 35 ? 500 : 3)).append("|\n");
        body.append("\n## 跨页代码\n\n```\n").append("literal **标记** 中英混排\n".repeat(150)).append("```\n\n# 最后的标题\n\n不能遗漏的结尾");
        PrintLayout layout = new PrintLayout("分页验证", body.toString(), 499, 13, Typeface.SERIF, true);
        var pages = layout.pages(720, 1000); assertTrue(pages.size() > 4);
        Map<PrintLayout.Element, Integer> next = new IdentityHashMap<>(); int headers = 0;
        for (PrintLayout.Page page : pages) {
            float bottom = 0;
            for (PrintLayout.Slice slice : page.slices()) {
                assertTrue(slice.y() >= bottom); assertTrue(slice.y() + slice.height() <= 720.01); assertTrue(slice.end() > slice.start());
                bottom = slice.y() + slice.height();
                if (slice.element().kind.equals("table-header")) { headers++; continue; }
                assertEquals((int)next.getOrDefault(slice.element(), 0), slice.start()); next.put(slice.element(), slice.end());
            }
            var last = page.slices().get(page.slices().size() - 1);
            assertFalse("Orphan heading", last.element().kind.equals("heading") && last.end() == last.element().lines());
        }
        for (PrintLayout.Element element : layout.elements) if (!element.kind.equals("table-header")) assertEquals(element.lines(), (int)next.get(element));
        assertTrue(headers > 1);
        assertThrows(IllegalArgumentException.class, () -> layout.pages(720, 2));
    }
    @Test public void richAndSourceExportsRenderWithPageCountsAndEnforcePngLimits() throws Exception {
        File folder = results();
        for (boolean rich : new boolean[]{true, false}) {
            String prefix = rich ? "rich" : "source";
            try (OutputStream out = new FileOutputStream(new File(folder, prefix + ".pdf"))) { DocumentExport.pdf("秋日书页 · 排版示例", SAMPLE, out, Typeface.SERIF, rich); }
            try (OutputStream out = new FileOutputStream(new File(folder, prefix + ".png"))) { DocumentExport.png("秋日书页 · 排版示例", SAMPLE, out, Typeface.SERIF, rich); }
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(folder, prefix + ".pdf"), ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer renderer = new PdfRenderer(fd)) {
                assertTrue(renderer.getPageCount() >= 1); assertTrue(renderer.getPageCount() <= 3);
            }
            android.graphics.Bitmap image = android.graphics.BitmapFactory.decodeFile(new File(folder, prefix + ".png").getPath());
            assertNotNull(image); assertEquals(1080, image.getWidth()); assertTrue(image.getHeight() > 1000); assertTrue(image.getHeight() <= 16000); image.recycle();
        }
        String manyRows = "| 章节 | 描述 |\n| --- | --- |\n" + "| 段落 | 中英混排，自动换行，保持每一行。 |\n".repeat(100);
        String longBody = SAMPLE + "\n\n## 分页压力检查\n\n" + manyRows + "\n```\n" + "line **literal** <code>\n".repeat(150) + "```\n\n最终结尾 END-OF-DOCUMENT";
        try (OutputStream out = new FileOutputStream(new File(folder, "multipage.pdf"))) { DocumentExport.pdf("跨页表格与代码", longBody, out); }
        StringBuilder longCell = new StringBuilder(); for (int i = 0; i < 400; i++) longCell.append(String.format(Locale.ROOT, "ROW%04d ", i));
        String oversized = "| 跨页长行 | 第二列 |\n| --- | --- |\n| " + longCell + " | TAIL-CELL END-OF-ROW |\n\n最终结尾 END-OF-TABLE";
        try (OutputStream out = new FileOutputStream(new File(folder, "oversized-row.pdf"))) { DocumentExport.pdf("单行超过一页的表格", oversized, out); }
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(folder, "multipage.pdf"), ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer renderer = new PdfRenderer(fd)) { assertTrue(renderer.getPageCount() > 5); }
        ByteArrayOutputStream failed = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class, () -> DocumentExport.png("过长", "正文\n".repeat(1000), failed)); assertEquals(0, failed.size());
        File cache = new File(context.getCacheDir(), "prepared-test-" + UUID.randomUUID()); cache.mkdirs();
        try {
            assertThrows(IllegalArgumentException.class, () -> PreparedExport.render(cache, new ExportSnapshot.Payload("过长", "png", "正文\n".repeat(1000)), Typeface.SERIF));
            assertEquals(0, Objects.requireNonNull(new File(cache, "pending-exports").list()).length);
        } finally { new File(cache, "pending-exports").delete(); cache.delete(); }
    }
    @Test public void layoutChooserDefaultsToMarkdownAndCancelKeepsTheLibrary() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);
            scenario.onActivity(activity -> {
                try {
                    Library library = (Library)field(activity, "library"); Document doc = library.documents.get(0);
                    Method method = MainActivity.class.getDeclaredMethod("exportRich", Document.class, String.class); method.setAccessible(true); method.invoke(activity, doc, "pdf");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            onView(withText("Markdown 排版")).inRoot(isDialog()).check(matches(isChecked()));
            onView(withText("原文排版（保留标记）")).perform(click()); onView(withText("取消")).perform(click());
            scenario.onActivity(activity -> assertNull(field(activity, "exportSnapshot")));
        }
    }
    @Test public void failedTypesettingPreservesExistingDestinationAndSourceModeSurvivesRecreation() throws Exception {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(); automation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS");
        Uri parent = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents");
        Uri file = DocumentsContract.createDocument(context.getContentResolver(), parent, "image/png", "jianzuo-print-" + UUID.randomUUID() + ".png"); assertNotNull(file);
        byte[] original = "original destination content".getBytes(StandardCharsets.UTF_8);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); try (OutputStream out = context.getContentResolver().openOutputStream(file, "wt")) { out.write(original); }
            String id = ExportSnapshot.write(context.getCacheDir(), new ExportSnapshot.Payload("长图", "png-source", "正文\n".repeat(1000)));
            scenario.onActivity(activity -> setSnapshot(activity, id)); scenario.recreate(); ready(scenario);
            assertEquals("png-source", ExportSnapshot.read(context.getCacheDir(), id).kind());
            scenario.onActivity(activity -> activity.onActivityResult(102, Activity.RESULT_OK, new Intent().setData(file)));
            waitDeleted(id);
            try (InputStream input = context.getContentResolver().openInputStream(file)) { assertArrayEquals(original, input.readAllBytes()); }
            onView(withText("导出失败")).inRoot(isDialog()).check(matches(isDisplayed()));
        } finally { try { DocumentsContract.deleteDocument(context.getContentResolver(), file); } finally { automation.dropShellPermissionIdentity(); } }
    }
    @Test public void selectedModeIsCapturedWithTheRightMimeAndExtensionBeforeThePicker() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var owner = new java.util.concurrent.atomic.AtomicReference<MainActivity>();
        var captured = new java.util.concurrent.atomic.AtomicReference<ExportSnapshot.Payload>();
        var intent = new java.util.concurrent.atomic.AtomicReference<Intent>();
        var snapshot = new java.util.concurrent.atomic.AtomicReference<String>();
        android.app.Instrumentation.ActivityMonitor monitor = new android.app.Instrumentation.ActivityMonitor() {
            @Override public android.app.Instrumentation.ActivityResult onStartActivity(Intent request) {
                if (!Intent.ACTION_CREATE_DOCUMENT.equals(request.getAction())) return null;
                try {
                    String id = (String)field(owner.get(), "exportSnapshot"); snapshot.set(id);
                    captured.set(ExportSnapshot.read(context.getCacheDir(), id)); intent.set(new Intent(request));
                } catch (Exception error) { throw new AssertionError(error); }
                return new android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); scenario.onActivity(owner::set); instrumentation.addMonitor(monitor);
            for (String kind : List.of("pdf", "pdf-source", "png", "png-source")) {
                captured.set(null); intent.set(null); snapshot.set(null);
                scenario.onActivity(activity -> {
                    try {
                        Document doc = ((Library)field(activity, "library")).documents.get(0);
                        Method method = MainActivity.class.getDeclaredMethod("exportRich", Document.class, String.class); method.setAccessible(true); method.invoke(activity, doc, kind.replace("-source", ""));
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                });
                if (kind.endsWith("-source")) onView(withText("原文排版（保留标记）")).inRoot(isDialog()).perform(click());
                onView(withText("选择保存位置")).inRoot(isDialog()).perform(click());
                for (int i = 0; i < 100 && intent.get() == null; i++) SystemClock.sleep(50);
                assertNotNull(intent.get()); assertEquals(kind, captured.get().kind());
                assertEquals(kind.startsWith("pdf") ? "application/pdf" : "image/png", intent.get().getType());
                assertTrue(intent.get().getStringExtra(Intent.EXTRA_TITLE).endsWith(kind.startsWith("pdf") ? ".pdf" : ".png"));
                waitDeleted(snapshot.get());
            }
        } finally { instrumentation.removeMonitor(monitor); }
    }
    @Test public void preparedRichAndSourceContentReachSystemDocumentsInBothFormats() throws Exception {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(); automation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS");
        Uri parent = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents"); List<Uri> files = new ArrayList<>();
        Map<String, byte[]> output = new HashMap<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);
            for (String kind : List.of("pdf", "pdf-source", "png", "png-source")) {
                String mime = kind.startsWith("pdf") ? "application/pdf" : "image/png";
                Uri file = DocumentsContract.createDocument(context.getContentResolver(), parent, mime, "jianzuo-print-" + UUID.randomUUID()); assertNotNull(file); files.add(file);
                String id = ExportSnapshot.write(context.getCacheDir(), new ExportSnapshot.Payload("排版", kind, SAMPLE)); scenario.onActivity(activity -> setSnapshot(activity, id));
                scenario.onActivity(activity -> activity.onActivityResult(102, Activity.RESULT_OK, new Intent().setData(file))); waitDeleted(id);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = context.getContentResolver().openInputStream(file)) { byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n); }
                byte[] data = bytes.toByteArray(); output.put(kind, data);
                if (kind.startsWith("pdf")) assertEquals("%PDF-", new String(data, 0, 5, StandardCharsets.US_ASCII));
                else { var bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length); assertNotNull(bitmap); assertEquals(1080, bitmap.getWidth()); bitmap.recycle(); }
            }
            assertFalse(Arrays.equals(output.get("pdf"), output.get("pdf-source"))); assertFalse(Arrays.equals(output.get("png"), output.get("png-source")));
        } finally { try { for (Uri file : files) DocumentsContract.deleteDocument(context.getContentResolver(), file); } finally { automation.dropShellPermissionIdentity(); } }
    }
    private static void setSnapshot(MainActivity activity, String id) {
        try { Field field = MainActivity.class.getDeclaredField("exportSnapshot"); field.setAccessible(true); field.set(activity, id); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private void waitDeleted(String id) throws Exception {
        for (int i = 0; i < 200; i++) {
            try { ExportSnapshot.read(context.getCacheDir(), id); } catch (IOException error) { return; }
            SystemClock.sleep(50);
        }
        fail("Export did not finish");
    }
}
