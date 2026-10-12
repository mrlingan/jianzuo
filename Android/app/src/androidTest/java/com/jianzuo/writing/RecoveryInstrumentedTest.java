package com.jianzuo.writing;

import android.app.AlertDialog;
import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecoveryInstrumentedTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void invoke(MainActivity activity, String name) {
        try { Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void ready(ActivityScenario<MainActivity> scenario) {
        for (int i = 0; i < 100; i++) {
            AtomicBoolean loaded = new AtomicBoolean(); scenario.onActivity(activity -> loaded.set((boolean)field(activity, "loaded")));
            if (loaded.get()) return; SystemClock.sleep(50);
        }
        fail("Library did not load");
    }
    private static void searchReady(ActivityScenario<MainActivity> scenario) {
        for (int i = 0; i < 100; i++) {
            AtomicBoolean done = new AtomicBoolean(); scenario.onActivity(activity -> {
                Object recovery = field(activity, "recoveryDialog"); android.widget.TextView status = (android.widget.TextView)field(recovery, "status");
                done.set(status.getText().toString().startsWith("找到"));
            });
            if (done.get()) return; SystemClock.sleep(50);
        }
        fail("Recovery search did not finish");
    }
    @Test public void historicalContentRestoresAsNewDocumentAndSurvivesReload() throws Exception {
        String needle = "找回-" + UUID.randomUUID(), originalBody = "当前正文保留", recoveredBody = "历史片段 " + needle + " 𠀀😀";
        AtomicReference<String> originalId = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); scenario.onActivity(activity -> {
                Library library = (Library)field(activity, "library"); Document doc = library.create("历史测试", originalBody, "文稿"); originalId.set(doc.id);
                doc.history.add(new Document.Revision(123, "旧标题", recoveredBody)); invoke(activity, "changed"); invoke(activity, "recoverContent");
            });
            onView(withContentDescription("找回关键词")).inRoot(isDialog()).perform(replaceText(needle)); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("搜索内容")).perform(click()); searchReady(scenario);
            onView(allOf(withText(containsString("历史版本")), isAssignableFrom(android.widget.Button.class))).perform(click());
            onView(withText("找回内容预览")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("保存为新文稿")).perform(click());
            onView(withContentDescription("文稿正文")).check(matches(withText(recoveredBody)));
            scenario.recreate(); ready(scenario); scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            Library saved = new LibraryRepository(context.getFilesDir()).load();
            assertEquals(originalBody, saved.find(originalId.get()).body); assertEquals(1, saved.find(originalId.get()).history.size());
            assertEquals(1, saved.documents.stream().filter(doc -> !doc.id.equals(originalId.get()) && doc.title.equals("旧标题") && doc.body.equals(recoveredBody)).count());
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        }
    }
    @Test public void backupOnlyContentCanBePreviewedAndCancelledWithoutImporting() throws Exception {
        String needle = "快照找回-" + UUID.randomUUID(); Library old = new Library(); old.create("快照独有", needle + " 中文", "日记");
        File directory = new File(context.getFilesDir(), "backups"); directory.mkdirs(); File backup = new File(directory, System.currentTimeMillis() + "-recovery-test.json");
        String encoded = LibraryCodec.encode(old); Files.writeString(backup.toPath(), encoded);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); scenario.onActivity(activity -> invoke(activity, "recoverContent"));
            onView(withContentDescription("找回关键词")).inRoot(isDialog()).perform(replaceText(needle)); androidx.test.espresso.Espresso.closeSoftKeyboard(); onView(withText("搜索内容")).perform(click()); searchReady(scenario);
            onView(allOf(withText(containsString("自动备份")), isAssignableFrom(android.widget.Button.class))).perform(click());
            onView(withText("返回")).perform(click()); onView(withText("关闭")).inRoot(isDialog()).perform(click());
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            assertFalse(new LibraryRepository(context.getFilesDir()).load().documents.stream().anyMatch(doc -> doc.body.contains(needle)));
            assertEquals(encoded, Files.readString(backup.toPath())); scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        } finally { backup.delete(); }
    }
    @Test public void scanProgressSurvivesRotationAndCancellationDropsLateResults() throws Exception {
        FolderScan scan = new FolderScan(); scan.control.progress(57, 8, 1048576);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); scenario.onActivity(activity -> {
                try { Field field = MainActivity.class.getDeclaredField("folderScan"); field.setAccessible(true); field.set(activity, scan); invoke(activity, "resumeImport"); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            onView(withText("正在扫描文件夹")).inRoot(isDialog()).check(matches(isDisplayed()));
            scenario.recreate(); ready(scenario);
            onView(withText(containsString("已检查 57 个条目"))).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("取消扫描")).perform(click()); assertTrue(scan.control.cancelled);
            scan.items = java.util.List.of(new TextImport.Item("过期结果", "不应导入", "文稿")); scan.complete = true;
            scenario.onActivity(activity -> { invoke(activity, "resumeImport"); assertNull(field(activity, "folderScan")); assertNull(field(activity, "importSession")); });
            onView(withText("导入预览 · 1 篇")).check(doesNotExist());
        }
    }
}
