package com.jianzuo.writing;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EditorInstrumentedTest {
    private void awaitReady(ActivityScenario<MainActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 6000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> ready.set(findDescription(activity.getWindow().getDecorView(), "新建文稿") || findDescription(activity.getWindow().getDecorView(), "返回文稿列表")));
            if (ready.get()) {
                AtomicBoolean inEditor = new AtomicBoolean(); scenario.onActivity(a -> inEditor.set(a.getResources().getInteger(R.integer.pane_count) == 1 && findDescription(a.getWindow().getDecorView(), "返回文稿列表")));
                if (inEditor.get()) onView(withContentDescription("返回文稿列表")).perform(click()); return;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Library did not finish loading");
    }
    private boolean findDescription(View view, String label) {
        if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return true;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) if (findDescription(group.getChildAt(i), label)) return true; }
        return false;
    }
    @Test public void chineseEditingPreviewFocusRecreationAndDiskPersistence() throws Exception {
        String title = "安卓测试文稿", body = "# 中文写作\n\n你好，世界！ Hello world.\n\n- 保留这一刻";
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitReady(scenario);
            scenario.onActivity(activity -> {
                int width = activity.getResources().getConfiguration().screenWidthDp;
                assertEquals(width >= 1100 ? 3 : width >= 600 ? 2 : 1, activity.getResources().getInteger(R.integer.pane_count));
                assertEquals(width >= 600, findDescription(activity.getWindow().getDecorView(), "文稿正文"));
            });
            onView(withContentDescription("新建文稿")).perform(click());
            onView(withContentDescription("文稿标题")).perform(replaceText(title));
            onView(withContentDescription("文稿正文")).perform(replaceText(body)); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withContentDescription("Markdown 预览")).perform(click());
            onView(withText("中文写作")).check(matches(isDisplayed()));
            onView(withContentDescription("编辑正文")).perform(click());
            onView(withContentDescription("文稿正文")).check(matches(withText(body)));
            onView(withContentDescription("专注写作")).perform(click());
            onView(withContentDescription("退出专注模式")).check(matches(isDisplayed()));
            onView(withContentDescription("文稿正文")).check(matches(withText(body)));
            scenario.recreate();
            // Focus hides the list, so wait for the editor rather than the new-document action.
            long deadline = SystemClock.uptimeMillis() + 6000; AtomicBoolean editor = new AtomicBoolean();
            do { scenario.onActivity(activity -> editor.set(findDescription(activity.getWindow().getDecorView(), "文稿正文"))); if (editor.get()) break; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < deadline);
            assertTrue(editor.get()); onView(withContentDescription("文稿正文")).check(matches(withText(body)));
            Library saved = new LibraryRepository(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir()).load();
            assertTrue(saved.documents.stream().anyMatch(doc -> doc.title.equals(title) && doc.body.equals(body)));
            onView(withContentDescription("退出专注模式")).perform(click());
            scenario.onActivity(activity -> editor.set(activity.getResources().getInteger(R.integer.pane_count) == 1));
            if (editor.get()) onView(withContentDescription("返回文稿列表")).perform(click());
            onView(withContentDescription("搜索文稿")).perform(replaceText("no-match-" + java.util.UUID.randomUUID()));
            onView(withText("没有找到匹配的文稿")).check(matches(isDisplayed()));

        }
    }
}
