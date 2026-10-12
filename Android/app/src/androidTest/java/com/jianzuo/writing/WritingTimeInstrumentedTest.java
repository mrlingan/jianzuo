package com.jianzuo.writing;

import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WritingTimeInstrumentedTest {
    final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    final SharedPreferences prefs = context.getSharedPreferences("writing", Context.MODE_PRIVATE);
    Map<String, ?> before;
    String primaryId, referenceTitle, primaryRow;
    ActivityScenario<MainActivity> activeScenario;
    @Before public void backupPreferences() { before = new HashMap<>(prefs.getAll()); }
    @After public void restorePreferences() {
        SharedPreferences.Editor edit = prefs.edit().clear();
        before.forEach((key, value) -> { if (value instanceof Boolean b) edit.putBoolean(key, b); else if (value instanceof Integer i) edit.putInt(key, i); else if (value instanceof Long l) edit.putLong(key, l); else if (value instanceof Float f) edit.putFloat(key, f); else if (value instanceof String s) edit.putString(key, s); }); edit.commit();
    }
    private View find(View root, String label) {
        if (label.equals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), label); if (found != null) return found; }
        return null;
    }
    private void ready(ActivityScenario<MainActivity> scenario, String label) {
        AtomicBoolean ready = new AtomicBoolean(); long limit = SystemClock.uptimeMillis() + 6000;
        do { scenario.onActivity(a -> ready.set(a.hasWindowFocus() && find(a.getWindow().getDecorView(), label) != null)); if (ready.get()) return; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < limit);
        fail("Missing view: " + label);
    }
    private ActivityScenario<MainActivity> launch() throws Exception {
        LibraryRepository repo = new LibraryRepository(context.getFilesDir()); Library library = repo.load(); String book = "时长测试-" + UUID.randomUUID().toString().substring(0, 8); library.addNotebook(book);
        Document first = library.create("时长主稿", "原有正文", book), second = library.create("时长参考稿", "参考正文", book);
        primaryId = first.id; primaryRow = first.displayTitle() + "，" + book + "，" + first.wordCount() + " 字"; referenceTitle = second.title + " · " + book; library.dailyWritingMillis.clear(); library.dailyWords.clear(); repo.write(LibraryCodec.encode(library));
        prefs.edit().putBoolean("lastDual", false).putBoolean("appLock", false).putBoolean("recordWritingTime", true).commit();
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(NotebookShortcuts.intent(context, book).setFlags(0)); activeScenario = scenario; ready(scenario, "目录与筛选");
        onView(withContentDescription(first.displayTitle() + "，" + book + "，" + first.wordCount() + " 字")).perform(click()); ready(scenario, "文稿正文"); return scenario;
    }
    private void menu(String name) { activeScenario.onActivity(a -> { View button = find(a.getWindow().getDecorView(), "文稿菜单"); assertNotNull(button); assertTrue(button.performClick()); }); onView(withText(name)).inRoot(isPlatformPopup()).perform(click()); }
    private void tool(String name) { menu("写作工具"); onData(hasToString(name)).inRoot(isPlatformPopup()).perform(click()); }
    private long persisted() throws Exception { return new LibraryRepository(context.getFilesDir()).load().dailyWritingMillis.values().stream().mapToLong(Long::longValue).sum(); }
    @Test public void deletionCountsButBackgroundResumePreviewAndReadOnlyDoNot() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            SystemClock.sleep(300); assertEquals(0, persisted());
            onView(withContentDescription("文稿正文")).perform(replaceText("原")); androidx.test.espresso.Espresso.closeSoftKeyboard(); SystemClock.sleep(1200);
            scenario.moveToState(Lifecycle.State.CREATED); long written = persisted(); assertTrue("Duration " + written, written >= 1000 && written < 10_000);
            scenario.moveToState(Lifecycle.State.RESUMED); ready(scenario, "文稿正文"); SystemClock.sleep(500); scenario.recreate(); ready(scenario, "文稿正文");
            scenario.moveToState(Lifecycle.State.CREATED); assertEquals(written, persisted());
            scenario.moveToState(Lifecycle.State.RESUMED); ready(scenario, "文稿正文"); onView(withContentDescription("Markdown 预览")).perform(click()); SystemClock.sleep(500);
            scenario.moveToState(Lifecycle.State.CREATED); assertEquals(written, persisted());
            scenario.moveToState(Lifecycle.State.RESUMED); ready(scenario, "编辑正文"); onView(withContentDescription("编辑正文")).perform(click()); tool("设为只读"); SystemClock.sleep(500);
            scenario.moveToState(Lifecycle.State.CREATED); assertEquals(written, persisted());
            scenario.moveToState(Lifecycle.State.RESUMED); ready(scenario, "文稿菜单");
        }
    }
    @Test public void dualEditsRecordTitleAndBodyOnceAndDialogPauses() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            tool("第二编辑器"); onView(withText(referenceTitle)).inRoot(isDialog()).perform(click()); ready(scenario, "主稿正文");
            onView(withContentDescription("主稿标题")).perform(replaceText("修改主稿标题")); androidx.test.espresso.Espresso.closeSoftKeyboard(); SystemClock.sleep(1000);
            onView(withContentDescription("激活参考稿")).perform(click()); onView(withContentDescription("参考稿正文")).perform(replaceText("参考正文新增")); androidx.test.espresso.Espresso.closeSoftKeyboard(); SystemClock.sleep(1000);
            tool("每日统计"); onView(withContentDescription("写作时长概览")).inRoot(isDialog()).check(matches(withText(containsString("今日写作")))); SystemClock.sleep(700);
            onView(withText("完成")).inRoot(isDialog()).perform(click()); ready(scenario, "参考稿正文"); scenario.moveToState(Lifecycle.State.CREATED);
            long written = persisted(); assertTrue("Duration " + written, written >= 2000 && written < 10_000);
            scenario.moveToState(Lifecycle.State.RESUMED); ready(scenario, "参考稿正文"); SystemClock.sleep(700); scenario.moveToState(Lifecycle.State.CREATED); assertEquals(written, persisted());
            scenario.moveToState(Lifecycle.State.RESUMED); ready(scenario, "文稿菜单");
        }
    }
    @Test public void timeSettingCancelAndSavePreserveOldRecordsAndDisableNewTime() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            menu("写作设置"); onView(withText("自动记录写作时长")).inRoot(isDialog()).perform(scrollTo(), click()); onView(withText("取消")).inRoot(isDialog()).perform(click()); assertTrue(prefs.getBoolean("recordWritingTime", false));
            menu("写作设置"); onView(withText("自动记录写作时长")).inRoot(isDialog()).perform(scrollTo(), click()); onView(withText("完成")).inRoot(isDialog()).perform(click()); ready(scenario, "文稿正文"); assertFalse(prefs.getBoolean("recordWritingTime", true));
            onView(withContentDescription("文稿正文")).perform(replaceText("关闭后的修改")); androidx.test.espresso.Espresso.closeSoftKeyboard(); SystemClock.sleep(1100); scenario.moveToState(Lifecycle.State.CREATED); assertEquals(0, persisted());
            scenario.moveToState(Lifecycle.State.RESUMED); ready(scenario, "文稿正文"); tool("每日统计"); onView(withText("时长记录已关闭 · 已有记录会保留")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("完成")).inRoot(isDialog()).perform(click()); ready(scenario, "文稿正文");
        }
    }
    @Test public void localBackupOptInRestoresStatisticsAndAnnualReviewShowsTime() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            Library incoming = new Library(); incoming.dailyWritingMillis.put(LocalDate.now().toString(), 65_000L); incoming.dailyWords.put(LocalDate.now().toString(), 123);
            File fixture = new File(context.getFilesDir(), "backups/9999999999999-duration-test.json"); fixture.getParentFile().mkdirs(); Files.write(fixture.toPath(), LibraryCodec.encode(incoming).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try {
                menu("写作设置"); onView(withText("备份与恢复")).inRoot(isDialog()).perform(scrollTo(), click());
                onData(hasToString("查看本机自动备份")).inRoot(isDialog()).perform(click());
                onData(anything()).inRoot(isDialog()).atPosition(0).perform(click());
                onView(withText("同时恢复每日字数和写作时长")).inRoot(isDialog()).check(matches(isNotChecked())).perform(click());
                onView(withText("合并恢复")).inRoot(isDialog()).perform(click()); onView(withText("取消")).inRoot(isDialog()).perform(click()); ready(scenario, "目录与筛选");
                onView(withContentDescription(primaryRow)).perform(click()); ready(scenario, "文稿正文");
                tool("每日统计"); onView(withContentDescription("写作时长概览")).inRoot(isDialog()).check(matches(withText("今日写作 1 分 5 秒\n累计时长 1 分 5 秒")));
                onView(withText("年度回顾")).inRoot(isDialog()).perform(scrollTo(), click()); onData(hasToString(LocalDate.now().getYear() + " 年")).inRoot(isDialog()).perform(click());
                onView(withContentDescription("年度写作时长")).inRoot(isDialog()).check(matches(withText("全年写作 1 分 5 秒 · 记录时长 1 天")));
                onView(withContentDescription("年度写作时长")).inRoot(isDialog()).check((view, error) -> { assertNotNull(view); assertTrue(view.createAccessibilityNodeInfo().getContentDescription().toString().contains("全年写作 1 分 5 秒")); });
                onView(withText("完成")).inRoot(isDialog()).perform(click()); onView(withText("完成")).inRoot(isDialog()).perform(click()); ready(scenario, "文稿正文");
            } finally { fixture.delete(); }
        }
        assertEquals(65_000, persisted()); assertEquals(123, (int) new LibraryRepository(context.getFilesDir()).load().dailyWords.get(LocalDate.now().toString()));
    }
}
