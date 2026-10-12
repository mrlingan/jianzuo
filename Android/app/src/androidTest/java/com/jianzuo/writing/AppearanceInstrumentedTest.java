package com.jianzuo.writing;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppearanceInstrumentedTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final SharedPreferences prefs = context.getSharedPreferences("writing", Context.MODE_PRIVATE);
    private Map<String, ?> original;
    private String book, firstId, secondId;
    private static final String BODY = "清晨的光落在书页上。\n中文、补充汉字𠀀与表情😀保持原样。\nA quiet place to write.";
    @Before public void prepare() throws Exception {
        original = new HashMap<>(prefs.getAll());
        prefs.edit().clear().putString("appearance", "light").putString("colorTheme", "classic").putBoolean("ruledPaper", false).commit();
        Library library = new LibraryRepository(context.getFilesDir()).load();
        book = "外观测试-" + UUID.randomUUID().toString().substring(0, 8); library.addNotebook(book);
        Document first = library.create("外观主稿", BODY, book), second = library.create("只读参考", BODY, book); second.readOnly = true;
        firstId = first.id; secondId = second.id; new LibraryRepository(context.getFilesDir()).write(LibraryCodec.encode(library));
        prefs.edit().putString("sort:" + book, "manual").commit();
    }
    @After public void restore() {
        SharedPreferences.Editor editor = prefs.edit().clear();
        original.forEach((key, value) -> {
            if (value instanceof String s) editor.putString(key, s);
            else if (value instanceof Integer i) editor.putInt(key, i);
            else if (value instanceof Boolean b) editor.putBoolean(key, b);
            else if (value instanceof Long l) editor.putLong(key, l);
            else if (value instanceof Float f) editor.putFloat(key, f);
        }); editor.commit();
    }
    private ActivityScenario<MainActivity> launch(String title, String id) throws Exception {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(NotebookShortcuts.intent(context, book).setFlags(0)); ready(scenario, "目录与筛选");
        Document doc = new LibraryRepository(context.getFilesDir()).load().find(id);
        String row = title + "，" + book + "，" + doc.wordCount() + " 字";
        ready(scenario, row); onView(withContentDescription(row)).perform(scrollTo(), click()); return scenario;
    }
    private View find(View view, String label) {
        if (label.equals(view.getContentDescription()) || view instanceof TextView text && label.contentEquals(text.getText())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View result = find(group.getChildAt(i), label); if (result != null) return result; }
        return null;
    }
    private void ready(ActivityScenario<MainActivity> scenario, String label) {
        AtomicBoolean ready = new AtomicBoolean(); long end = SystemClock.uptimeMillis() + 6000;
        do { scenario.onActivity(a -> {
            View view = find(a.getWindow().getDecorView(), label);
            ready.set(a.getWindow().getDecorView().hasWindowFocus() && view != null && view.isShown() && view.getGlobalVisibleRect(new Rect()));
        }); if (ready.get()) return; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < end);
        fail("Missing UI: " + label);
    }
    private void settings() { onView(withContentDescription("文稿菜单")).perform(click()); onView(withText("写作设置")).perform(click()); }
    private void choose(String label, String value) {
        onView(withContentDescription(label)).inRoot(isDialog()).perform(scrollTo(), click());
        onData(is(value)).inRoot(isPlatformPopup()).perform(click());
    }
    @Test public void previewCancelSaveSelectionAndColdLaunchKeepTextUnchanged() throws Exception {
        String originalLibrary = LibraryCodec.encode(new LibraryRepository(context.getFilesDir()).load());
        try (ActivityScenario<MainActivity> scenario = launch("外观主稿", firstId)) {
            ready(scenario, "文稿正文"); scenario.onActivity(a -> ((EditText)find(a.getWindow().getDecorView(), "文稿正文")).setSelection(2, 7));
            settings(); choose("配色主题", "山林"); choose("稿纸样式", "横线"); choose("外观", "深色");
            onView(withContentDescription("主题预览")).inRoot(isDialog()).perform(scrollTo()).check((view, error) -> {
                if (error != null) throw error; PaperTextView sample = (PaperTextView)view;
                assertEquals(WritingAppearance.palette("forest", true).ink(), sample.getCurrentTextColor()); assertTrue(sample.paperLines.enabled());
            });
            assertEquals("classic", prefs.getString("colorTheme", "")); assertFalse(prefs.getBoolean("ruledPaper", true)); assertEquals("light", prefs.getString("appearance", ""));
            onView(withText("取消")).inRoot(isDialog()).perform(click());
            onView(withContentDescription("文稿正文")).check(matches(withText(BODY)));
            settings(); choose("配色主题", "海雾"); choose("稿纸样式", "横线"); choose("外观", "深色"); onView(withText("完成")).inRoot(isDialog()).perform(click());
            ready(scenario, "文稿正文"); assertAppearance(scenario, "文稿正文", "ocean", true);
            scenario.onActivity(a -> { EditText body = (EditText)find(a.getWindow().getDecorView(), "文稿正文"); assertEquals(2, body.getSelectionStart()); assertEquals(7, body.getSelectionEnd()); });
            scenario.recreate(); ready(scenario, "文稿正文"); assertAppearance(scenario, "文稿正文", "ocean", true);
        }
        try (ActivityScenario<MainActivity> cold = ActivityScenario.launch(MainActivity.class)) { ready(cold, "文稿正文"); assertAppearance(cold, "文稿正文", "ocean", true); }
        assertEquals(BODY, new LibraryRepository(context.getFilesDir()).load().find(firstId).body);
        assertEquals(originalLibrary, LibraryCodec.encode(new LibraryRepository(context.getFilesDir()).load()));
        assertEquals("ocean", prefs.getString("colorTheme", "")); assertTrue(prefs.getBoolean("ruledPaper", false));
    }
    private void assertAppearance(ActivityScenario<MainActivity> scenario, String label, String theme, boolean dark) {
        scenario.onActivity(a -> {
            TextView body = (TextView)find(a.getWindow().getDecorView(), label); assertNotNull(body);
            assertEquals(BODY, body.getText().toString()); assertEquals(WritingAppearance.palette(theme, dark).ink(), body.getCurrentTextColor());
            assertTrue(body instanceof WritingEditText input ? input.paperLines.enabled() : ((PaperTextView)body).paperLines.enabled());
        });
    }
    @Test public void dualAndReadOnlyPaperSurviveRecreationAndPreviewSwitch() throws Exception {
        prefs.edit().putString("colorTheme", "forest").putBoolean("ruledPaper", true).commit();
        try (ActivityScenario<MainActivity> scenario = launch("外观主稿", firstId)) {
            onView(withContentDescription("文稿菜单")).perform(click()); onView(withText("写作工具")).perform(click()); onView(withText("第二编辑器")).perform(click());
            onView(withText("只读参考 · " + book)).inRoot(isDialog()).perform(click()); ready(scenario, "关闭双编辑器");
            assertAppearance(scenario, "主稿正文", "forest", false); assertAppearance(scenario, "参考稿正文（只读）", "forest", false);
            scenario.recreate(); ready(scenario, "关闭双编辑器"); assertAppearance(scenario, "主稿正文", "forest", false); assertAppearance(scenario, "参考稿正文（只读）", "forest", false);
            onView(withContentDescription("参考稿预览")).perform(click()); onView(withContentDescription("参考稿正文（只读）")).check(doesNotExist());
            onView(withContentDescription("参考稿编辑正文")).perform(click()); assertAppearance(scenario, "参考稿正文（只读）", "forest", false);
        }
        Library disk = new LibraryRepository(context.getFilesDir()).load(); assertEquals(BODY, disk.find(firstId).body); assertEquals(BODY, disk.find(secondId).body); assertTrue(disk.find(secondId).readOnly);
    }
    @Test public void singleReadOnlyUsesTheSameThemeAndRules() throws Exception {
        prefs.edit().putString("colorTheme", "rose").putBoolean("ruledPaper", true).commit();
        try (ActivityScenario<MainActivity> scenario = launch("只读参考", secondId)) {
            ready(scenario, "文稿正文（只读）"); assertAppearance(scenario, "文稿正文（只读）", "rose", false);
            scenario.recreate(); ready(scenario, "文稿正文（只读）"); assertAppearance(scenario, "文稿正文（只读）", "rose", false);
        }
    }
    @Test public void everyThemeVariantMatchesNativeControlsAndWindowBackground() {
        for (String id : WritingAppearance.IDS) for (boolean dark : new boolean[]{false, true}) {
            prefs.edit().putString("colorTheme", id).putString("appearance", dark ? "dark" : "light").commit();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(NotebookShortcuts.intent(context, book).setFlags(0))) {
                ready(scenario, "目录与筛选"); scenario.onActivity(a -> {
                    android.util.TypedValue accent = new android.util.TypedValue(); assertTrue(a.getTheme().resolveAttribute(android.R.attr.colorAccent, accent, true));
                    var p = WritingAppearance.palette(id, dark); assertEquals(p.accent(), accent.data);
                    assertEquals(p.paper(), ((ColorDrawable)a.getWindow().getDecorView().getBackground()).getColor());
                    assertEquals(WritingAppearance.foregroundOn(p.accent()), ((TextView)find(a.getWindow().getDecorView(), "＋  开始新的一页")).getCurrentTextColor());
                });
            }
        }
    }
    @Test public void rulesRenderBelowWrappedTextAndFollowChangedSpacing() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PaperTextView view = new PaperTextView(context); view.setText("中文与 English 自动换行。".repeat(8) + "\n第二段\n第三段"); view.setTextColor(Color.BLACK);
            view.setTextSize(20); view.setPadding(12, 13, 12, 13); view.setLineSpacing(12, 1); view.configurePaper(true, Color.MAGENTA);
            List<Integer> first = ruleRows(view, 320, 700); assertTrue(first.size() > 4);
            view.setLineSpacing(27, 1); List<Integer> second = ruleRows(view, 320, 700); assertNotEquals(first, second);
            String text = view.getText().toString(); view.configurePaper(false, Color.MAGENTA); assertTrue(ruleRows(view, 320, 700).isEmpty()); assertEquals(text, view.getText().toString());
        });
    }
    @Test public void emptyWritingAreaAndLargeClippedDocumentsDrawWithoutChangingText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WritingEditText empty = new WritingEditText(context); empty.setBackgroundColor(Color.WHITE); empty.setTextSize(20); empty.setGravity(android.view.Gravity.TOP | android.view.Gravity.START); empty.setMinLines(8); empty.configurePaper(true, Color.MAGENTA);
            assertTrue(ruleRows(empty, 320, 700).size() >= 8); assertEquals("", empty.getText().toString());
            PaperTextView longText = new PaperTextView(context); String text = "长文稿\n".repeat(10000); longText.setText(text); longText.setTextSize(20); longText.configurePaper(true, Color.MAGENTA);
            // Only a small viewport is drawn even though the native layout has 10,001 lines.
            longText.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1_000_000, View.MeasureSpec.EXACTLY));
            longText.layout(0, 0, 320, 1_000_000);
            Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            try { longText.draw(new Canvas(bitmap)); } finally { bitmap.recycle(); }
            assertEquals(text, longText.getText().toString()); assertTrue(longText.getLayout().getLineCount() >= 10000);
        });
    }
    private List<Integer> ruleRows(TextView view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); List<Integer> rows = new ArrayList<>();
        try {
            bitmap.eraseColor(Color.WHITE); view.draw(new Canvas(bitmap)); boolean previous = false;
            for (int y = 0; y < height; y++) { int count = 0; for (int x = 0; x < width; x++) if (bitmap.getPixel(x, y) == Color.MAGENTA) count++;
                boolean line = count > width * .7; if (line && !previous) rows.add(y); previous = line;
            }
            return rows;
        } finally { bitmap.recycle(); }
    }
    @Test public void hardwareRulesFollowLongDocumentScrolling() throws Exception {
        prefs.edit().putString("colorTheme", "ocean").putBoolean("ruledPaper", true).commit();
        String text = "长文稿的每一行都保留原样。\n".repeat(3000);
        Library library = new LibraryRepository(context.getFilesDir()).load(); library.find(firstId).body = text;
        new LibraryRepository(context.getFilesDir()).write(LibraryCodec.encode(library));
        try (ActivityScenario<MainActivity> scenario = launch("外观主稿", firstId)) {
            ready(scenario, "文稿正文");
            for (int target : new int[]{0, 1200, 8000, 0}) {
                Rect bounds = new Rect();
                scenario.onActivity(a -> {
                    TextView body = (TextView)find(a.getWindow().getDecorView(), "文稿正文"); assertTrue(body.isHardwareAccelerated());
                    android.view.ViewParent parent = body.getParent(); while (!(parent instanceof ScrollView)) parent = parent.getParent();
                    ((ScrollView)parent).scrollTo(0, target);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync(); SystemClock.sleep(150);
                scenario.onActivity(a -> {
                    TextView body = (TextView)find(a.getWindow().getDecorView(), "文稿正文"); assertTrue(body.getGlobalVisibleRect(bounds)); assertEquals(text, body.getText().toString());
                    android.view.ViewParent parent = body.getParent(); while (!(parent instanceof ScrollView)) parent = parent.getParent();
                    assertEquals(target, ((ScrollView)parent).getScrollY());
                });
                Bitmap screen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(screen);
                try {
                    int rows = 0, color = WritingAppearance.palette("ocean", false).line(); boolean previous = false;
                    int left = Math.max(0, bounds.left + 40), right = Math.min(screen.getWidth(), bounds.right - 40);
                    for (int y = Math.max(0, bounds.top + 10); y < Math.min(screen.getHeight(), bounds.bottom - 10); y++) {
                        int count = 0; for (int x = left; x < right; x++) if (screen.getPixel(x, y) == color) count++;
                        boolean rule = count > (right - left) * .7; if (rule && !previous) rows++; previous = rule;
                    }
                    assertTrue("Missing visible rules after scrolling to " + target + ": " + rows, rows >= 3);
                } finally { screen.recycle(); }
            }
        }
        assertEquals(text, new LibraryRepository(context.getFilesDir()).load().find(firstId).body);
    }
}
