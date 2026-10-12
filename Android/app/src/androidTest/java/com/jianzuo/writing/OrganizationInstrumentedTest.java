package com.jianzuo.writing;

import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
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
public class OrganizationInstrumentedTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private String book, firstId, secondId, lockedId, trashId, volumeId, emptyId;
    private LibraryRepository repository() { return new LibraryRepository(context.getFilesDir()); }
    private ActivityScenario<MainActivity> launch() throws Exception {
        return launch(0);
    }
    private ActivityScenario<MainActivity> launch(int extraChapters) throws Exception {
        Library library = repository().load(); book = "组织测试-" + UUID.randomUUID().toString().substring(0, 8); library.addNotebook(book);
        Section volume = library.addSection(book, "卷一"), empty = library.addSection(book, "空卷"); volumeId = volume.id; emptyId = empty.id;
        Document first = library.create("章节甲", "正文甲", book), second = library.create("章节乙", "正文乙", book);
        library.assignSection(first, volume); library.assignSection(second, volume); first.tags = "#草稿"; second.tags = "#草稿二";
        Document locked = library.create("锁定章", "只读内容", book); locked.readOnly = true; library.assignSection(locked, volume);
        Document trash = library.create("回收章", "回收内容", book); library.assignSection(trash, volume); library.trash(trash);
        for (int i = 0; i < extraChapters; i++) library.assignSection(library.create("长目录章节" + i, "用于检查拖动时目录滚屏的段落", book), volume);
        firstId = first.id; secondId = second.id; lockedId = locked.id; trashId = trash.id;
        repository().write(LibraryCodec.encode(library));
        context.getSharedPreferences("writing", Context.MODE_PRIVATE).edit().putBoolean("tree:" + book, true).putString("sort:" + book, "manual").apply();
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(NotebookShortcuts.intent(context, book).setFlags(0)); ready(scenario); return scenario;
    }
    private View find(View view, String label) {
        if (label.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), label); if (found != null) return found; }
        return null;
    }
    private void ready(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean ready = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 6000;
        do {
            scenario.onActivity(a -> ready.set(a.getWindow().getDecorView().hasWindowFocus() && find(a.getWindow().getDecorView(), "目录与筛选") != null));
            if (ready.get()) return; SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Directory did not become ready");
    }
    private String row(String title) { return title + "，" + book + "，3 字"; }
    private Library saved(ActivityScenario<MainActivity> scenario) throws Exception {
        scenario.recreate(); ready(scenario); return repository().load();
    }
    @Test public void emptyVolumesCollapseAndRenamePersistWithoutChangingWriting() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            onView(withContentDescription("分卷目录：空卷")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withContentDescription("分卷目录：卷一")).perform(scrollTo(), click());
            onView(withContentDescription(row("章节甲"))).check(doesNotExist());
            scenario.recreate(); ready(scenario); onView(withContentDescription(row("章节甲"))).check(doesNotExist());
            onView(withContentDescription("分卷目录：卷一")).perform(scrollTo(), click(), longClick());
            onView(withText("重命名分卷")).perform(click());
            onView(withContentDescription("分卷名称")).perform(replaceText("新卷名")); androidx.test.espresso.Espresso.closeSoftKeyboard(); onView(withText("保存")).perform(click());
            onView(withContentDescription("分卷目录：新卷名")).check(matches(isDisplayed()));
            Library disk = saved(scenario); assertEquals("新卷名", disk.section(volumeId).name); assertNotNull(disk.section(emptyId));
            assertEquals(volumeId, disk.find(firstId).sectionId); assertEquals("正文甲", disk.find(firstId).body); assertEquals("新卷名", disk.find(trashId).section);
        }
    }
    @Test public void numberingRequiresPreviewAndRetainsPriorTitleInHistory() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            onView(withContentDescription("文稿列表菜单")).perform(click()); onView(withText("批量章节编号")).perform(click());
            onView(withContentDescription("起始章节序号")).perform(replaceText("3")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withText("预览")).perform(click()); onView(withText("编号预览 · 2 篇")).check(matches(isDisplayed()));
            onView(withText(containsString("→ 第3章 章节甲"))).check(matches(isDisplayed())); onView(withText("应用编号")).perform(click());
            Library disk = saved(scenario); Document first = disk.find(firstId);
            assertEquals("第3章 章节甲", first.title); assertEquals("第4章 章节乙", disk.find(secondId).title);
            assertEquals("正文甲", first.body); assertEquals("章节甲", first.history.get(0).title()); assertEquals("正文甲", first.history.get(0).body());
            assertEquals("锁定章", disk.find(lockedId).title); assertEquals("回收章", disk.find(trashId).title);
        }
    }
    @Test public void tagFilterMatchesWholeTagAndSurvivesRecreation() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            onView(withContentDescription("目录与筛选")).perform(click());
            onView(withContentDescription("标签（精确匹配）")).inRoot(isDialog()).perform(click());
            onData(is("草稿")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
            onView(withText("应用")).inRoot(isDialog()).perform(click());
            onView(withContentDescription(row("章节甲"))).check(matches(isDisplayed())); onView(withContentDescription(row("章节乙"))).check(doesNotExist());
            scenario.recreate(); ready(scenario); onView(withContentDescription(row("章节甲"))).check(matches(isDisplayed())); onView(withContentDescription(row("章节乙"))).check(doesNotExist());
            onView(withContentDescription("目录与筛选")).perform(click()); onView(withText("清除筛选")).perform(click());
            onView(withContentDescription(row("章节乙"))).check(matches(isDisplayed()));
        }
    }
    @Test public void nativeLongPressDragMovesChapterIntoEmptyVolume() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            // Bring the destination into view while keeping the source handle visible.
            onView(withContentDescription("拖动章节：章节甲")).perform(scrollTo());
            onView(withContentDescription("分卷目录：空卷")).perform(scrollTo());
            onView(withContentDescription("拖动章节：章节甲")).perform(dragTo("分卷目录：空卷"));
            Library disk = saved(scenario); assertEquals(emptyId, disk.find(firstId).sectionId); assertEquals("正文甲", disk.find(firstId).body);
            assertEquals(volumeId, disk.find(secondId).sectionId); assertEquals("正文乙", disk.find(secondId).body);
        }
    }
    @Test public void insertionMenuCreatesAnEditableChapterBeforeTheAnchor() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch()) {
            onView(withContentDescription(row("章节甲"))).perform(longClick());
            onData(hasToString("调整章节顺序")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click()); onView(withText("在此章前新建")).perform(click());
            onView(withContentDescription("文稿标题")).perform(replaceText("插入章")); onView(withContentDescription("文稿正文")).perform(replaceText("新正文")); androidx.test.espresso.Espresso.closeSoftKeyboard();
            scenario.recreate(); Library disk = new LibraryRepository(context.getFilesDir()).load();
            assertEquals(java.util.List.of("插入章", "章节甲", "章节乙", "锁定章"), disk.bookDocuments(book).stream().map(d -> d.title).toList());
            Document inserted = disk.bookDocuments(book).get(0); assertEquals(volumeId, inserted.sectionId); assertEquals("#草稿", inserted.tags); assertEquals("新正文", inserted.body); assertEquals("正文甲", disk.find(firstId).body);
        }
    }
    @Test public void nativeDragScrollsTheLongDirectoryToAnInitiallyHiddenVolume() throws Exception {
        try (ActivityScenario<MainActivity> scenario = launch(6)) {
            onView(withContentDescription("拖动章节：章节甲")).perform(scrollTo(), dragToHiddenVolume());
            Library disk = saved(scenario); assertEquals(emptyId, disk.find(firstId).sectionId); assertEquals("正文甲", disk.find(firstId).body);
        }
    }
    private ViewAction dragToHiddenVolume() {
        return new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "drag to bottom edge until the hidden empty volume scrolls into view"; }
            public void perform(UiController controller, View source) {
                View root = source.getRootView(); View target = find(root, "分卷目录：空卷"), viewport = find(root, "章节目录滚动区域"); assertNotNull(target); assertNotNull(viewport);
                int[] from = new int[2], bounds = new int[2], to = new int[2]; source.getLocationOnScreen(from); viewport.getLocationOnScreen(bounds); target.getLocationOnScreen(to);
                int top = bounds[1], bottom = top + viewport.getHeight(); assertTrue(to[1] > bottom);
                float x = from[0] + source.getWidth() / 2f, y = from[1] + source.getHeight() / 2f, edgeY = bottom - 3;
                long down = SystemClock.uptimeMillis(); inject(controller, down, MotionEvent.ACTION_DOWN, x, y); controller.loopMainThreadForAtLeast(ViewConfiguration.getLongPressTimeout() + 150);
                for (int i = 1; i <= 8; i++) { inject(controller, down, MotionEvent.ACTION_MOVE, x, y + (edgeY - y) * i / 8f); controller.loopMainThreadForAtLeast(40); }
                boolean visible = false;
                for (int i = 0; i < 40; i++) {
                    controller.loopMainThreadForAtLeast(250); target.getLocationOnScreen(to);
                    if (to[1] >= top && to[1] + target.getHeight() <= bottom) { visible = true; break; }
                }
                assertTrue("Edge drag did not scroll to the destination", visible); assertTrue(viewport.getScrollY() > 0);
                float tx = to[0] + target.getWidth() / 2f, ty = to[1] + target.getHeight() / 2f;
                inject(controller, down, MotionEvent.ACTION_MOVE, tx, ty); controller.loopMainThreadForAtLeast(80); inject(controller, down, MotionEvent.ACTION_UP, tx, ty); controller.loopMainThreadForAtLeast(500);
            }
        };
    }
    private ViewAction dragTo(String targetLabel) {
        return new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "long press and drag chapter to " + targetLabel; }
            public void perform(UiController controller, View source) {
                View target = find(source.getRootView(), targetLabel); assertNotNull(target);
                int[] from = new int[2], to = new int[2]; source.getLocationOnScreen(from); target.getLocationOnScreen(to);
                float x = from[0] + source.getWidth() / 2f, y = from[1] + source.getHeight() / 2f;
                float tx = to[0] + target.getWidth() / 2f, ty = to[1] + target.getHeight() / 2f;
                long down = SystemClock.uptimeMillis(); inject(controller, down, MotionEvent.ACTION_DOWN, x, y);
                controller.loopMainThreadForAtLeast(ViewConfiguration.getLongPressTimeout() + 150);
                for (int i = 1; i <= 12; i++) { inject(controller, down, MotionEvent.ACTION_MOVE, x + (tx - x) * i / 12f, y + (ty - y) * i / 12f); controller.loopMainThreadForAtLeast(40); }
                inject(controller, down, MotionEvent.ACTION_UP, tx, ty); controller.loopMainThreadForAtLeast(500);
            }
        };
    }
    private void inject(UiController controller, long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try { assertTrue(controller.injectMotionEvent(event)); } catch (androidx.test.espresso.InjectEventSecurityException error) { throw new AssertionError(error); } finally { event.recycle(); }
    }
}
