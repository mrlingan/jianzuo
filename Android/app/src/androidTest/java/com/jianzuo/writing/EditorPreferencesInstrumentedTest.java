package com.jianzuo.writing;

import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.*;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;
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
public class EditorPreferencesInstrumentedTest {
    final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    final SharedPreferences prefs = context.getSharedPreferences("writing", Context.MODE_PRIVATE);
    Map<String, ?> before;
    String primaryId, referenceId, referenceTitle;
    @Before public void backupPreferences() { before = new HashMap<>(prefs.getAll()); }
    @After public void restorePreferences() {
        SharedPreferences.Editor edit = prefs.edit().clear();
        before.forEach((key, value) -> { if (value instanceof Boolean b) edit.putBoolean(key, b); else if (value instanceof Integer i) edit.putInt(key, i); else if (value instanceof Long l) edit.putLong(key, l); else if (value instanceof Float f) edit.putFloat(key, f); else if (value instanceof String s) edit.putString(key, s); }); edit.apply();
    }
    private ActivityScenario<MainActivity> launch() throws Exception {
        LibraryRepository repo = new LibraryRepository(context.getFilesDir()); Library library = repo.load(); String book = "编辑设置-" + UUID.randomUUID().toString().substring(0, 8); library.addNotebook(book);
        Document first = library.create("统计正文", "甲𠀀 A-b\t\u00a0！\n🙂", book), second = library.create("参考统计-" + book, "参考 A 🙂", book);
        primaryId = first.id; referenceId = second.id; referenceTitle = second.title + " · " + book;
        repo.write(LibraryCodec.encode(library)); prefs.edit().putBoolean("lastDual", false).putBoolean("shortcuts", true).putInt("wordGoal", 800).putInt("countMode", 0).apply();
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(NotebookShortcuts.intent(context, book).setFlags(0)); ready(scenario, "目录与筛选");
        onView(withContentDescription(first.displayTitle() + "，" + book + "，" + first.wordCount() + " 字")).perform(click()); ready(scenario, "文稿正文"); return scenario;
    }
    private void tool(String name) { onView(withContentDescription("文稿菜单")).perform(click()); onView(withText("写作工具")).perform(click()); onData(hasToString(name)).inRoot(isPlatformPopup()).perform(click()); }
    private View find(View root, String label) {
        if (label.equals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), label); if (found != null) return found; }
        return null;
    }
    private void ready(ActivityScenario<MainActivity> scenario, String label) {
        AtomicBoolean ready = new AtomicBoolean(); long limit = SystemClock.uptimeMillis() + 6000;
        do { scenario.onActivity(a -> ready.set(a.getWindow().getDecorView().hasWindowFocus() && find(a.getWindow().getDecorView(), label) != null)); if (ready.get()) return; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < limit);
        fail("Missing view: " + label);
    }
    @Test public void legacyShortcutCancelAndMultilineEditingCommitOnlyOnSave() throws Exception {
        prefs.edit().remove("shortcutConfig").putString("customShortcuts", "备注|\\n> 旧文|𠀀").apply();
        try (ActivityScenario<MainActivity> scenario = launch()) {
            tool("快捷输入栏"); onData(anything()).inAdapterView(withContentDescription("快捷输入项目列表")).atPosition(0).onChildView(withContentDescription("显示快捷项：撤销")).perform(click());
            onView(withText("取消")).inRoot(isDialog()).perform(click()); assertFalse(prefs.contains("shortcutConfig"));
            onView(withContentDescription("快捷输入：撤销")).check(matches(isDisplayed()));
            tool("快捷输入栏");
            onData(anything()).inAdapterView(withContentDescription("快捷输入项目列表")).atPosition(13).onChildView(withContentDescription("编辑快捷项：备注")).perform(click());
            onView(withContentDescription("快捷项内容")).check(matches(withText("\n> 旧文|𠀀"))).perform(replaceText("\n> 新文|𠀀\n$1\\n")); androidx.test.espresso.Espresso.closeSoftKeyboard(); onView(withText("完成")).inRoot(isDialog()).perform(click());
            onData(anything()).inAdapterView(withContentDescription("快捷输入项目列表")).atPosition(13).onChildView(withContentDescription("排序快捷项：备注")).perform(click());
            onView(withText("移到首位")).inRoot(isDialog()).perform(click());
            onView(withContentDescription("快捷输入行数")).perform(click()); onData(hasToString("3 行")).inRoot(isPlatformPopup()).perform(click());
            onView(withText("保存")).inRoot(isDialog()).perform(click());
            onView(withContentDescription("快捷输入第3行")).check(matches(isDisplayed()));
            scenario.onActivity(a -> ((EditText)find(a.getWindow().getDecorView(), "文稿正文")).setSelection(0));
            onView(withContentDescription("快捷输入：备注")).perform(click()); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withContentDescription("文稿正文")).check(matches(withText("\n> 新文|𠀀\n$1\\n甲𠀀 A-b\t\u00a0！\n🙂")));
            scenario.recreate(); ready(scenario, "文稿正文"); assertEquals(3, ShortcutConfig.decode(prefs.getString("shortcutConfig", "")).rows);
            tool("快捷输入栏"); onView(withText("恢复默认布局")).inRoot(isDialog()).perform(click()); onView(withText("保存")).inRoot(isDialog()).perform(click());
            ShortcutConfig reset = ShortcutConfig.decode(prefs.getString("shortcutConfig", "")); assertEquals(1, reset.rows); assertEquals("undo", reset.items.get(0).id()); assertEquals("\n> 新文|𠀀\n$1\\n", reset.items.get(13).value());
        }
    }
    @Test public void actualTouchDragChangesToolbarOrderAndHiddenItemsStayHidden() throws Exception {
        prefs.edit().putString("shortcutConfig", new ShortcutConfig(ShortcutConfig.BUILTINS, 1).encode()).apply();
        try (ActivityScenario<MainActivity> scenario = launch()) {
            tool("快捷输入栏"); onView(withContentDescription("排序快捷项：“”")).inRoot(isDialog()).perform(dragBeforeUndo());
            onView(withText("保存")).inRoot(isDialog()).perform(click());
            assertEquals("quotes", ShortcutConfig.decode(prefs.getString("shortcutConfig", "")).items.get(0).id());
            tool("快捷输入栏"); onData(anything()).inAdapterView(withContentDescription("快捷输入项目列表")).atPosition(0).onChildView(withContentDescription("显示快捷项：“”")).perform(click());
            onView(withText("保存")).inRoot(isDialog()).perform(click()); onView(withContentDescription("快捷输入：“”")).check(doesNotExist());
            scenario.recreate(); ready(scenario, "文稿正文"); onView(withContentDescription("快捷输入：“”")).check(doesNotExist());
            assertEquals("甲𠀀 A-b\t\u00a0！\n🙂", new LibraryRepository(context.getFilesDir()).load().find(primaryId).body);
        }
    }
    @Test public void statusCountingAndSelectionFollowTheActiveDraftAndSurviveRecreation() throws Exception {
        prefs.edit().putString("shortcutConfig", new ShortcutConfig(ShortcutConfig.BUILTINS, 1).encode()).apply();
        try (ActivityScenario<MainActivity> scenario = launch()) {
            onView(withContentDescription("字数统计与口径")).check(matches(withText("3 字 / 目标 800"))).perform(click());
            onView(withText("字符数（不含空白，保留标点）")).inRoot(isDialog()).perform(click());
            scenario.onActivity(a -> ((EditText)find(a.getWindow().getDecorView(), "文稿正文")).setSelection(0, 3));
            onView(withContentDescription("字数统计与口径")).check(matches(withText("选中 2 / 全文 7 字符（不含空白）\n字数 3 / 目标 800")));
            scenario.onActivity(a -> assertTrue(find(a.getWindow().getDecorView(), "字数统计与口径").createAccessibilityNodeInfo().getContentDescription().toString().contains("选中 2 / 全文 7")));
            scenario.onActivity(a -> ((EditText)find(a.getWindow().getDecorView(), "文稿正文")).setSelection(3, 0));
            onView(withContentDescription("字数统计与口径")).check(matches(withText(containsString("选中 2 / 全文 7"))));
            scenario.recreate(); ready(scenario, "文稿正文"); onView(withContentDescription("字数统计与口径")).check(matches(withText(containsString("选中 2 / 全文 7"))));
            scenario.onActivity(a -> { EditText input = (EditText)find(a.getWindow().getDecorView(), "文稿正文"); assertEquals(3, input.getSelectionStart()); assertEquals(0, input.getSelectionEnd()); });
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario, "文稿正文"); onView(withContentDescription("字数统计与口径")).check(matches(withText(containsString("选中 2 / 全文 7"))));
            tool("第二编辑器"); onView(withText(referenceTitle)).inRoot(isDialog()).perform(click()); ready(scenario, "参考稿正文");
            scenario.onActivity(a -> { EditText reference = (EditText)find(a.getWindow().getDecorView(), "参考稿正文"); reference.requestFocus(); reference.setSelection(0, 2); });
            onView(withContentDescription("字数统计与口径")).check(matches(withText(containsString("选中 2 / 全文 4 字符（不含空白）"))));
            onView(withContentDescription("激活主稿")).perform(click()); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withContentDescription("字数统计与口径")).check(matches(withText(containsString("选中 2 / 全文 7 字符（不含空白）"))));
            scenario.onActivity(a -> ((EditText)find(a.getWindow().getDecorView(), "主稿正文")).setSelection(3, 0));
            onView(withContentDescription("快捷输入：“”")).perform(click()); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withContentDescription("主稿正文")).check(matches(withText("“甲𠀀” A-b\t\u00a0！\n🙂")));
            onView(withContentDescription("字数统计与口径")).check(matches(withText(containsString("选中 2 / 全文 9 字符（不含空白）"))));
            scenario.onActivity(a -> ((EditText)find(a.getWindow().getDecorView(), "主稿正文")).setSelection(4, 1));
            onView(withContentDescription("快捷输入：缩进")).perform(scrollTo(), click()); androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withContentDescription("主稿正文")).check(matches(withText("“　　” A-b\t\u00a0！\n🙂")));
        }
    }
    private ViewAction dragBeforeUndo() {
        return new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "native long press and drag quotes before Undo"; }
            public void perform(UiController controller, View source) {
                View target = find(source.getRootView(), "显示快捷项：撤销"); assertNotNull(target);
                int[] from = new int[2], to = new int[2]; source.getLocationOnScreen(from); target.getLocationOnScreen(to);
                float x = from[0] + source.getWidth()/2f, y = from[1] + source.getHeight()/2f, tx = to[0] + target.getWidth()/2f, ty = to[1] + target.getHeight()/2f;
                long down = SystemClock.uptimeMillis(); inject(controller,down,MotionEvent.ACTION_DOWN,x,y); controller.loopMainThreadForAtLeast(ViewConfiguration.getLongPressTimeout()+150);
                for(int i=1;i<=12;i++){inject(controller,down,MotionEvent.ACTION_MOVE,x+(tx-x)*i/12f,y+(ty-y)*i/12f);controller.loopMainThreadForAtLeast(40);}
                inject(controller,down,MotionEvent.ACTION_UP,tx,ty);controller.loopMainThreadForAtLeast(300);
            }
        };
    }
    private void inject(UiController controller,long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue(controller.injectMotionEvent(event));}catch(InjectEventSecurityException error){throw new AssertionError(error);}finally{event.recycle();}}
}
