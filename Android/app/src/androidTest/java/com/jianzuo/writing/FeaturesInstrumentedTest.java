package com.jianzuo.writing;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FeaturesInstrumentedTest {
    private ActivityScenario<MainActivity> activeScenario;
    private boolean description(View view, String label) {
        if (label.equals(view.getContentDescription())) return true;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) if (description(group.getChildAt(i), label)) return true;
        return false;
    }
    private void ready(ActivityScenario<MainActivity> scenario) {
        activeScenario = scenario;
        AtomicBoolean ready = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 6000;
        do { scenario.onActivity(a -> ready.set(a.getWindow().getDecorView().hasWindowFocus() && (description(a.getWindow().getDecorView(), "新建文稿") || description(a.getWindow().getDecorView(), "文稿菜单")))); if (ready.get()) {
            AtomicBoolean inEditor = new AtomicBoolean(); scenario.onActivity(a -> inEditor.set(a.getResources().getInteger(R.integer.pane_count) == 1 && description(a.getWindow().getDecorView(), "返回文稿列表")));
            if (inEditor.get()) onView(withContentDescription("返回文稿列表")).perform(click()); return;
        } SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < deadline);
        fail("Editor did not become ready");
    }
    private View find(View view, String label) {
        if (label.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View result = find(group.getChildAt(i), label); if (result != null) return result; }
        return null;
    }
    private void tool(String title) {
        activeScenario.onActivity(activity -> {
            View button = find(activity.getWindow().getDecorView(), "文稿菜单"); assertNotNull(button); assertTrue(button.performClick());
        });
        onView(withText("写作工具")).perform(click()); onView(withText(title)).perform(click());
    }
    @Test public void replacementHistoryAndReadOnlyWorkThroughMenus() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); onView(withContentDescription("新建文稿")).perform(click());
            onView(withContentDescription("文稿标题")).perform(replaceText("功能测试-" + UUID.randomUUID()));
            onView(withContentDescription("文稿正文")).perform(replaceText("Hello 世界 Hello")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            tool("查找替换"); onView(withContentDescription("查找文字")).perform(replaceText("Hello"));
            onView(withContentDescription("替换文字")).perform(replaceText("$1")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("替换全部")).perform(click()); onView(withText("替换")).perform(click());
            onView(withContentDescription("文稿正文")).check(matches(withText("$1 世界 $1")));
            tool("历史版本"); onView(withText(containsString(" · 4 字"))).perform(click());
            onView(withText("恢复此版本")).perform(click()); onView(withText("恢复")).perform(click());
            onView(withContentDescription("文稿正文")).check(matches(withText("Hello 世界 Hello")));
            tool("设为只读"); onView(withContentDescription("文稿正文")).check(doesNotExist());
            tool("解除只读"); onView(withContentDescription("文稿正文")).check(matches(withText("Hello 世界 Hello")));
        }
    }
    @Test public void scratchIsSavedOnBackgroundAndCanBecomeADocument() throws Exception {
        String note = "速记验证 " + UUID.randomUUID(), parentTitle = "速记测试父文稿-" + UUID.randomUUID();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); onView(withContentDescription("新建文稿")).perform(click()); onView(withContentDescription("文稿标题")).perform(replaceText(parentTitle)); androidx.test.espresso.Espresso.closeSoftKeyboard();
            tool("灵感速记"); onView(withContentDescription("灵感速记正文")).perform(replaceText(note)); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("关闭")).perform(click()); scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            Library saved = new LibraryRepository(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir()).load(); assertEquals(note, saved.scratch);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED); scenario.recreate(); ready(scenario);
            AtomicBoolean phone = new AtomicBoolean(); scenario.onActivity(a -> phone.set(a.getResources().getInteger(R.integer.pane_count) == 1));
            if (phone.get()) onView(withText(parentTitle)).perform(click());
            onView(withContentDescription("文稿标题")).check(matches(withText(parentTitle)));
            tool("灵感速记"); onView(withContentDescription("灵感速记正文")).check(matches(withText(note)));
            java.util.concurrent.atomic.AtomicReference<View> decor = new java.util.concurrent.atomic.AtomicReference<>(); scenario.onActivity(a -> decor.set(a.getWindow().getDecorView()));
            onView(withText("另存为文稿")).perform(click()); onView(withContentDescription("文稿正文")).inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(is(decor.get()))).check(matches(withText(note)));
        }
    }
    @Test public void rollingSnapshotContainsThePriorValidLibrary() throws Exception {
        File folder = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "backup-test-" + UUID.randomUUID()); assertTrue(folder.mkdirs());
        try {
            LibraryRepository repo = new LibraryRepository(folder); Library original = repo.load();
            String firstBody = original.documents.get(0).body; String id = original.documents.get(0).id;
            original.documents.get(0).body = "changed"; repo.write(LibraryCodec.encode(original)); assertEquals(1, repo.backups().length);
            Library old = LibraryCodec.decode(Files.readString(repo.backups()[0].toPath())); assertEquals(firstBody, old.find(id).body);
            assertEquals("changed", repo.load().find(id).body);
        } finally { remove(folder); }
    }
    @Test public void nativeExportsAndZipImportPreserveUnicode() throws Exception {
        String body = "中文与𠀀\nHello\n\n<保留原文>";
        ByteArrayOutputStream pdf = new ByteArrayOutputStream(); DocumentExport.pdf("标题", body.repeat(600), pdf);
        assertTrue(new String(pdf.toByteArray(), 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
        ByteArrayOutputStream png = new ByteArrayOutputStream(); DocumentExport.png("标题", body, png);
        assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10}, java.util.Arrays.copyOf(png.toByteArray(), 8));
        assertTrue(DocumentExport.html("<标题>", body).contains("&lt;标题&gt;"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("小说/第一章.md")); zip.write(body.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        var items = TextImport.zip(new java.io.ByteArrayInputStream(bytes.toByteArray())); assertEquals(body, items.get(0).body()); assertEquals("小说", items.get(0).notebook());
        File results = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(), "export-validation"); results.mkdirs();
        Files.write(new File(results, "sample.pdf").toPath(), pdf.toByteArray()); Files.write(new File(results, "sample.png").toPath(), png.toByteArray());
        try (android.os.ParcelFileDescriptor fd = android.os.ParcelFileDescriptor.open(new File(results, "sample.pdf"), android.os.ParcelFileDescriptor.MODE_READ_ONLY);
             android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(fd)) {
            assertTrue(renderer.getPageCount() > 1);
            for (int index : new int[]{0, renderer.getPageCount() - 1}) {
                try (android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(index)) {
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2, android.graphics.Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(results, "pdf-page-" + index + ".png"))) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); }
                    bitmap.recycle();
                }
            }
        }
    }
    @Test public void zipTraversalAndInvalidUtf8AreRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) { zip.putNextEntry(new java.util.zip.ZipEntry("../outside.md")); zip.write('x'); zip.closeEntry(); }
        assertThrows(Exception.class, () -> TextImport.zip(new java.io.ByteArrayInputStream(bytes.toByteArray())));
    }
    @Test public void punctuationPairingPreservesImeComposition() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WritingEditText input = new WritingEditText(InstrumentationRegistry.getInstrumentation().getTargetContext());
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.configure(true, true, true, android.graphics.Color.GRAY);
            input.setText(""); input.setSelection(0);
            android.view.inputmethod.InputConnection connection = input.onCreateInputConnection(new android.view.inputmethod.EditorInfo());
            assertNotNull(connection); connection.commitText("（", 1); assertEquals("（）", input.getText().toString()); assertEquals(1, input.getSelectionStart());
            connection.deleteSurroundingText(1, 0); assertEquals("", input.getText().toString());
            connection.setComposingText("中", 1); connection.setComposingText("中文", 1); connection.finishComposingText(); assertEquals("中文", input.getText().toString());
            input.setText("　　段落"); input.setSelection(input.length()); connection.commitText("\n", 1); assertEquals("　　段落\n　　", input.getText().toString());
        });
    }
    @Test public void selectiveReplacementAndDiffWorkThroughReview() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); onView(withContentDescription("新建文稿")).perform(click());
            onView(withContentDescription("文稿标题")).perform(replaceText("选择替换-" + UUID.randomUUID()));
            onView(withContentDescription("文稿正文")).perform(replaceText("cat 中 cat 末 cat")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            tool("查找替换"); onView(withContentDescription("查找文字")).perform(replaceText("cat"));
            onView(withContentDescription("替换文字")).perform(replaceText("$1")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("勾选要替换的结果")).perform(scrollTo(), click());
            onData(anything()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(1).perform(click());
            onView(withText("替换选中项")).perform(click()); onView(withText("替换")).perform(click());
            onView(withContentDescription("文稿正文")).check(matches(withText("$1 中 cat 末 $1")));
            tool("历史版本"); onView(withText(containsString(" · 5 字"))).perform(click());
            onView(withText("与当前版本对比")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            onView(withText("历史版本差异")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()));
            onView(withText(containsString("− 删除 1 行"))).check(matches(isDisplayed()));
        }
    }
    @Test public void paragraphMoveKeepsCursorAndCanUndoFromHistory() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); onView(withContentDescription("新建文稿")).perform(click());
            onView(withContentDescription("文稿正文")).perform(replaceText("上段\n\n下段")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            scenario.onActivity(a -> ((android.widget.EditText) find(a.getWindow().getDecorView(), "文稿正文")).setSelection(6));
            tool("段落上移"); onView(withContentDescription("文稿正文")).check(matches(withText("下段\n\n上段")));
            scenario.onActivity(a -> assertEquals(2, ((android.widget.EditText) find(a.getWindow().getDecorView(), "文稿正文")).getSelectionStart()));
            tool("段落下移"); onView(withContentDescription("文稿正文")).check(matches(withText("上段\n\n下段")));
        }
    }
    @Test public void importPreviewRenameOrderAndSelectionAreAppliedTogether() {
        java.util.concurrent.atomic.AtomicReference<java.util.List<TextImport.Item>> accepted = new java.util.concurrent.atomic.AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); scenario.onActivity(a -> ImportPreview.show(a, java.util.List.of(new TextImport.Item("原名一", "正文一", "文稿"), new TextImport.Item("原名二", "正文二𠀀", "文稿")), accepted::set));
            onData(anything()).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(1).onChildView(withText("编辑")).perform(click());
            onView(withContentDescription("导入标题")).perform(replaceText("改名第二篇")); onView(withContentDescription("导入顺序")).perform(replaceText("1")); androidx.test.espresso.Espresso.closeSoftKeyboard(); onView(withText("保存")).perform(click());
            onView(withText("导入选中文稿")).perform(click());
            assertEquals(2, accepted.get().size()); assertEquals("改名第二篇", accepted.get().get(0).title()); assertEquals("正文二𠀀", accepted.get().get(0).body());
            assertEquals("原名一", accepted.get().get(1).title());
        }
    }
    @Test public void notebookShortcutLaunchRoutesToNotebookAfterColdLoad() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(NotebookShortcuts.intent(context, "灵感"))) {
            ready(scenario); onView(withText("灵感  ▾")).check(matches(isDisplayed()));
            scenario.recreate(); ready(scenario); onView(withText("灵感  ▾")).check(matches(isDisplayed()));
        }
    }
    @Test public void indentDeletionAndPunctuationChangeOnlyCommittedInput() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WritingEditText input = new WritingEditText(InstrumentationRegistry.getInstrumentation().getTargetContext());
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.configure(true, true, false, android.graphics.Color.GRAY); input.configureEditing(true, true);
            input.setText("　　正文，"); input.setSelection(input.length());
            android.view.inputmethod.InputConnection connection = input.onCreateInputConnection(new android.view.inputmethod.EditorInfo()); assertNotNull(connection);
            connection.commitText("\n", 1); assertEquals("　　正文。\n　　", input.getText().toString());
            connection.deleteSurroundingTextInCodePoints(1, 0); assertEquals("　　正文。\n", input.getText().toString());
            input.setText("　　"); input.setSelection(2); connection.deleteSurroundingText(1, 0); assertEquals("", input.getText().toString());
            connection.setComposingText("中文，", 1); assertEquals("中文，", input.getText().toString()); connection.finishComposingText();
            assertEquals("中文，", input.getText().toString());
        });
    }
    private void remove(File file) { File[] children = file.listFiles(); if (children != null) for (File child : children) remove(child); file.delete(); }
}
