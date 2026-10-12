package com.jianzuo.writing;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DualEditorInstrumentedTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private String firstId, secondId, thirdId, book;
    private LibraryRepository repository() { return new LibraryRepository(context.getFilesDir()); }
    private ActivityScenario<MainActivity> launch(boolean longText) throws Exception {
        return launch(longText, false);
    }
    private ActivityScenario<MainActivity> launch(boolean longText, boolean separateBook) throws Exception {
        Library library = repository().load(); book = "双稿测试-" + UUID.randomUUID().toString().substring(0, 8); library.addNotebook(book);
        Document first = library.create("对照主稿", longText ? "第一行\n".repeat(200) : "主稿原文", book), second = library.create("对照参考稿", longText ? "参考段落\n".repeat(320) : "参考原文", book);
        Document third = library.create("只读资料", "不能改写的资料", book); third.readOnly = true;
        if (separateBook) { String referenceBook = "参考资料-" + UUID.randomUUID().toString().substring(0, 8); library.addNotebook(referenceBook); library.moveToNotebook(second, referenceBook); }
        firstId = first.id; secondId = second.id; thirdId = third.id;
        repository().write(LibraryCodec.encode(library));
        context.getSharedPreferences("writing", Context.MODE_PRIVATE).edit().putBoolean("lastDual", false).putBoolean("dualLinked", false).putInt("dualRatio", 50).putString("sort:" + book, "manual").apply();
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(NotebookShortcuts.intent(context, book).setFlags(0)); ready(scenario, "目录与筛选");
        onView(withContentDescription("对照主稿，" + book + "，" + first.wordCount() + " 字")).perform(click());
        onView(withContentDescription("文稿菜单")).perform(click()); onView(withText("写作工具")).perform(click()); onView(withText("第二编辑器")).perform(click());
        onView(withText("对照参考稿 · " + second.notebook)).inRoot(isDialog()).perform(click()); ready(scenario, "关闭双编辑器"); return scenario;
    }
    private View find(View view, String label) {
        if (label.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View result = find(group.getChildAt(i), label); if (result != null) return result; }
        return null;
    }
    private void ready(ActivityScenario<MainActivity> scenario, String label) {
        AtomicBoolean ready = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 6000;
        do { scenario.onActivity(a -> ready.set(a.getWindow().getDecorView().hasWindowFocus() && find(a.getWindow().getDecorView(), label) != null)); if (ready.get()) return; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < deadline);
        fail("Missing UI: " + label);
    }
    private void tool(String item) { onView(withContentDescription("文稿菜单")).perform(click()); onView(withText("写作工具")).perform(click()); onView(withText(item)).perform(click()); }
    @After public void leaveSingleEditorForOtherTests() { context.getSharedPreferences("writing", Context.MODE_PRIVATE).edit().putBoolean("lastDual", false).apply(); }
    @Test public void bothEditorsSaveHistoryMetricsCursorAndColdLaunchState() throws Exception {
        int previous = repository().load().dailyWords.getOrDefault(LocalDate.now().toString(), 0);
        try (ActivityScenario<MainActivity> scenario = launch(false)) {
            onView(withContentDescription("主稿正文")).perform(replaceText("主稿继续写下新的段落"));
            onView(withContentDescription("参考稿正文")).perform(replaceText("参考稿补充独立资料")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withContentDescription("参考稿标题")).perform(replaceText("改名后的参考稿")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            scenario.onActivity(a -> { ((EditText) find(a.getWindow().getDecorView(), "主稿正文")).setSelection(2, 4); ((EditText) find(a.getWindow().getDecorView(), "参考稿正文")).setSelection(1, 3); });
            scenario.recreate(); ready(scenario, "关闭双编辑器");
            onView(withContentDescription("主稿正文")).check(matches(withText("主稿继续写下新的段落"))); onView(withContentDescription("参考稿正文")).check(matches(withText("参考稿补充独立资料")));
            scenario.onActivity(a -> {
                EditText first = (EditText) find(a.getWindow().getDecorView(), "主稿正文"), second = (EditText) find(a.getWindow().getDecorView(), "参考稿正文");
                assertEquals(2, first.getSelectionStart()); assertEquals(4, first.getSelectionEnd()); assertEquals(1, second.getSelectionStart()); assertEquals(3, second.getSelectionEnd());
            });
            Library disk = repository().load(); assertEquals("主稿继续写下新的段落", disk.find(firstId).body); assertEquals("改名后的参考稿", disk.find(secondId).title);
            assertEquals("主稿原文", disk.find(firstId).history.get(0).body()); assertEquals("参考原文", disk.find(secondId).history.get(0).body());
            assertEquals(previous + 11, (int) disk.dailyWords.get(LocalDate.now().toString()));
        }
        try (ActivityScenario<MainActivity> reopened = ActivityScenario.launch(MainActivity.class)) {
            ready(reopened, "关闭双编辑器"); onView(withContentDescription("参考稿标题")).check(matches(withText("改名后的参考稿")));
            onView(withContentDescription("主稿正文")).check(matches(withText("主稿继续写下新的段落")));
            onView(withContentDescription("关闭双编辑器")).perform(click()); ready(reopened, "文稿菜单");
        }
    }
    @Test public void referencePreviewSwitchAndReadOnlyToolsDoNotModifyTheMainDraft() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch(false)) {
            onView(withContentDescription("参考稿预览")).perform(click()); onView(withContentDescription("参考稿正文")).check(doesNotExist());
            onView(withContentDescription("主稿正文")).check(matches(withText("主稿原文"))); onView(withContentDescription("参考稿编辑正文")).perform(click());
            tool("查找替换"); onView(withContentDescription("查找文字")).perform(replaceText("参考")); onView(withContentDescription("替换文字")).perform(replaceText("对照")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("替换全部")).perform(click()); onView(withText("替换")).perform(click());
            onView(withContentDescription("参考稿正文")).check(matches(withText("对照原文"))); onView(withContentDescription("主稿正文")).check(matches(withText("主稿原文")));
            onView(withContentDescription("激活参考稿")).perform(click()); androidx.test.espresso.Espresso.closeSoftKeyboard(); tool("设为只读");
            onView(withContentDescription("参考稿正文")).check(doesNotExist()); onView(withContentDescription("参考稿正文（只读）")).check(matches(withText("对照原文")));
            onView(withContentDescription("切换参考稿文稿")).perform(click()); onView(withText("对照主稿 · " + book)).check(doesNotExist());
            onView(withText("只读资料 · " + book)).perform(click()); onView(withContentDescription("参考稿正文（只读）")).check(matches(withText("不能改写的资料")));
            scenario.recreate(); ready(scenario, "关闭双编辑器"); Library disk = repository().load(); assertEquals("主稿原文", disk.find(firstId).body); assertTrue(disk.find(secondId).readOnly); assertTrue(disk.find(thirdId).readOnly);
            onView(withContentDescription("关闭双编辑器")).perform(click()); onView(withContentDescription("文稿正文")).check(matches(withText("主稿原文")));
        }
    }
    @Test public void linkedScrollingUsesRelativeProgressAndRatioAdaptsToWindowWidth() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch(true)) {
            onView(withContentDescription("双编辑器比例")).perform(click()); onView(withText("60%")).perform(click());
            onView(withContentDescription("联动滚动")).perform(click());
            scenario.onActivity(a -> {
                View root = a.getWindow().getDecorView(); ScrollView first = (ScrollView) find(root, "主稿滚动区域"), second = (ScrollView) find(root, "参考稿滚动区域");
                boolean wide = a.getResources().getConfiguration().screenWidthDp >= 600;
                assertNotNull(find(root, wide ? "左右双编辑器" : "上下双编辑器"));
                int primarySize = wide ? first.getWidth() : ((View) first.getParent()).getHeight(), secondarySize = wide ? second.getWidth() : ((View) second.getParent()).getHeight();
                assertEquals(.60, (double) primarySize / (primarySize + secondarySize), .02);
                int range = first.getChildAt(0).getHeight() - first.getHeight(); assertTrue(range > 0); first.scrollTo(0, range / 2);
                int otherRange = second.getChildAt(0).getHeight() - second.getHeight(); assertTrue(otherRange > 0); assertEquals(.5, (double) second.getScrollY() / otherRange, .01);
            });
            onView(withContentDescription("联动滚动")).perform(click());
            scenario.onActivity(a -> {
                View root = a.getWindow().getDecorView(); ScrollView first = (ScrollView) find(root, "主稿滚动区域"), second = (ScrollView) find(root, "参考稿滚动区域");
                int original = first.getScrollY(); second.scrollTo(0, (second.getChildAt(0).getHeight() - second.getHeight()) / 4); assertEquals(original, first.getScrollY());
            });
            scenario.recreate(); ready(scenario, "关闭双编辑器");
            scenario.onActivity(a -> {
                View root = a.getWindow().getDecorView(); ScrollView first = (ScrollView) find(root, "主稿滚动区域"), second = (ScrollView) find(root, "参考稿滚动区域");
                assertEquals(.5, (double) first.getScrollY() / (first.getChildAt(0).getHeight() - first.getHeight()), .02);
                assertEquals(.25, (double) second.getScrollY() / (second.getChildAt(0).getHeight() - second.getHeight()), .02);
            });
            onView(withContentDescription("关闭双编辑器")).perform(click());
        }
    }
    @Test public void selectiveAndNotebookReplacementsFollowAReferenceFromAnotherBook() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch(false, true)) {
            onView(withContentDescription("激活参考稿")).perform(click()); androidx.test.espresso.Espresso.closeSoftKeyboard(); tool("查找替换");
            onView(withContentDescription("查找文字")).perform(replaceText("参考")); onView(withContentDescription("替换文字")).perform(replaceText("对照")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("勾选要替换的结果")).perform(scrollTo(), click()); onView(withText("勾选替换 · 1 处")).check(matches(isDisplayed()));
            onView(withText("替换选中项")).perform(click()); onView(withText("替换")).perform(click()); onView(withContentDescription("参考稿正文")).check(matches(withText("对照原文")));
            tool("查找替换"); onView(withContentDescription("查找文字")).perform(replaceText("对照")); onView(withContentDescription("替换文字")).perform(replaceText("资料")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withContentDescription("查找范围（正文）")).perform(click()); onData(is("当前笔记本")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
            onView(withText("替换全部")).perform(click()); onView(withText("替换")).perform(click()); onView(withContentDescription("参考稿正文")).check(matches(withText("资料原文")));
            onView(withContentDescription("主稿正文")).check(matches(withText("主稿原文"))); scenario.recreate(); ready(scenario, "关闭双编辑器");
            Library disk = repository().load(); assertEquals("资料原文", disk.find(secondId).body); assertEquals("主稿原文", disk.find(firstId).body);
            onView(withContentDescription("关闭双编辑器")).perform(click());
        }
    }
}
