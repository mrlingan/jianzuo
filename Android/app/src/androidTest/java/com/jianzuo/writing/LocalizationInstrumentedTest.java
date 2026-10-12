package com.jianzuo.writing;

import android.content.*;
import android.os.*;
import android.view.*;
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
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalizationInstrumentedTest {
    final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    final SharedPreferences prefs=context.getSharedPreferences("writing",Context.MODE_PRIVATE);
    Map<String,?> before; String selection, payload, id; LibraryRepository repo;
    @Before public void prepare() throws Exception {
        before=new HashMap<>(prefs.getAll()); selection=AppLanguage.selection(context); repo=new LibraryRepository(context.getFilesDir()); payload=LibraryCodec.encode(repo.load());
        prefs.edit().putBoolean("appLock",false).putBoolean("recordWritingTime",false).putBoolean("lastDual",false).putBoolean("lastPhoneEditor",false).putString("appearance","light").putBoolean("shortcuts",true).apply();
    }
    @After public void restore() throws Exception {
        repo.write(payload); AppLanguage.set(context,selection.equals("zh-CN")?1:selection.equals("en")?2:0);
        SharedPreferences.Editor edit=prefs.edit().clear();
        before.forEach((k,v)->{if(v instanceof Boolean b)edit.putBoolean(k,b);else if(v instanceof Integer i)edit.putInt(k,i);else if(v instanceof Long l)edit.putLong(k,l);else if(v instanceof Float f)edit.putFloat(k,f);else if(v instanceof String s)edit.putString(k,s);});edit.commit();
    }
    private View find(View root,String label) {
        if(label.equals(root.getContentDescription()))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View v=find(group.getChildAt(i),label);if(v!=null)return v;}
        return null;
    }
    private void ready(ActivityScenario<MainActivity> scenario,String label) {
        AtomicBoolean found=new AtomicBoolean();long limit=SystemClock.uptimeMillis()+8000;
        do {scenario.onActivity(a->found.set(a.hasWindowFocus()&&find(a.getWindow().getDecorView(),label)!=null));if(found.get())return;SystemClock.sleep(80);}while(SystemClock.uptimeMillis()<limit);
        fail("Missing localized control: "+label);
    }
    private ActivityScenario<MainActivity> launch(int language,boolean editor) throws Exception {
        AppLanguage.set(context,language);
        Library library=repo.load();library.dailyWritingMillis.clear();Document doc=library.create("无标题文稿","用户原文 English 🙂\n第二行",library.notebooks.get(0));id=doc.id;repo.write(LibraryCodec.encode(library));
        prefs.edit().putString("lastSelected",id).putBoolean("lastPhoneEditor",editor).apply();
        ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class);ready(scenario,editor?(language==1?"文稿菜单":"Document menu"):(language==1?"文稿列表菜单":"Document list menu"));return scenario;
    }
    private void assertDocument(ActivityScenario<MainActivity> scenario, boolean favorite, boolean readOnly) throws Exception {
        // Backgrounding waits for the application's atomic save. A second repository must not read a write in progress.
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
        Document doc=repo.load().find(id);assertEquals(favorite,doc.favorite);assertEquals(readOnly,doc.readOnly);
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        ready(scenario, "Document menu");
    }
    private void settings(String menu,String title) {onView(withContentDescription(menu)).perform(click());onView(withText(title)).perform(click());}
    @Test public void englishMenusUseStableActionsAndKeepCanonicalFilters() throws Exception {
        try(var scenario=launch(2,true)) {
            ready(scenario,"Document text");onView(withContentDescription("Document text")).check(matches(withText("用户原文 English 🙂\n第二行")));
            onView(withContentDescription("Document menu")).perform(click());onView(withText("Add to favorites")).perform(click());
            assertDocument(scenario, true, false);
            onView(withContentDescription("Document menu")).perform(click());onView(withText("Writing tools")).perform(click());onData(hasToString("Make read only")).inRoot(isPlatformPopup()).perform(click());
            assertDocument(scenario, true, true);onView(withContentDescription("Document menu")).perform(click());onView(withText("Writing tools")).perform(click());onData(hasToString("Make editable")).inRoot(isPlatformPopup()).perform(click());
            assertDocument(scenario, true, false);
            onView(withContentDescription("Document menu")).perform(click());onView(withText("Create a copy")).perform(click());
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            assertTrue(repo.load().documents.stream().anyMatch(d -> !d.id.equals(id) && d.title.equals("无标题文稿 · Copy") && d.body.equals("用户原文 English 🙂\n第二行")));
        }
    }
    @Test public void chineseToEnglishCommitPreservesLibraryAndEditorAcrossColdLaunch() throws Exception {
        try(var scenario=launch(1,true)) {
            ready(scenario,"文稿正文");String unchanged=LibraryCodec.encode(repo.load());
            settings("文稿菜单","写作设置");onView(withContentDescription("界面语言")).inRoot(isDialog()).perform(scrollTo(),click());onData(hasToString("English")).inRoot(isPlatformPopup()).perform(click());
            onView(withText("完成")).inRoot(isDialog()).perform(click());ready(scenario,"Document text");
            assertEquals("en",AppLanguage.selection(context));assertEquals(unchanged,LibraryCodec.encode(repo.load()));
            onView(withContentDescription("Document title")).check(matches(withText("无标题文稿")));onView(withContentDescription("Shortcut: Undo")).check(matches(isDisplayed()));
        }
        try(var cold=ActivityScenario.launch(MainActivity.class)){ready(cold,"Document text");assertEquals("en",AppLanguage.selection(context));}
    }
    @Test public void cancellingLanguageSelectionKeepsChinese() throws Exception {
        try(var scenario=launch(1,true)) {
            ready(scenario,"文稿正文");settings("文稿菜单","写作设置");onView(withContentDescription("界面语言")).inRoot(isDialog()).perform(scrollTo(),click());onData(hasToString("English")).inRoot(isPlatformPopup()).perform(click());onView(withText("取消")).inRoot(isDialog()).perform(click());
            assertEquals("zh-CN",AppLanguage.selection(context));scenario.recreate();ready(scenario,"文稿正文");
        }
    }
    @Test public void englishToSystemResolvesDeviceLanguageAndClearsOverride() throws Exception {
        try(var scenario=launch(2,true)) {
            ready(scenario,"Document text");settings("Document menu","Writing settings");onView(withContentDescription("Interface language")).inRoot(isDialog()).perform(scrollTo(),click());onData(hasToString("Follow system")).inRoot(isPlatformPopup()).perform(click());onView(withText("Done")).inRoot(isDialog()).perform(click());
            String language=AppLanguage.resolve("system",Build.VERSION.SDK_INT>=33?context.getSystemService(android.app.LocaleManager.class).getSystemLocales().get(0):context.getResources().getConfiguration().getLocales().get(0)).getLanguage();
            ready(scenario,language.equals("zh")?"文稿正文":"Document text");assertEquals("system",AppLanguage.selection(context));
        }
    }
    @Test public void englishPresentationPreservesUserLabelsAndLocalizesTypedErrors() throws Exception {
        Context en=AppLanguage.localized(context,Locale.ENGLISH),zh=AppLanguage.localized(context,Locale.SIMPLIFIED_CHINESE);
        assertEquals("Untitled document",UiText.title(en," "));assertEquals("无标题文稿",UiText.title(en,"无标题文稿"));
        assertEquals("用户笔记本",UiText.filter(en,"用户笔记本"));assertEquals("Favorites",UiText.filter(en,Library.FAVORITES));
        var custom=ShortcutConfig.item("custom:test","撤销","原文");assertEquals("撤销",UiText.shortcut(en,custom));assertEquals("Undo",UiText.shortcut(en,ShortcutConfig.BUILTINS.get(0)));
        assertEquals("1 min 5 s",UiText.time(en,65000));assertEquals(WritingTime.format(65000),UiText.time(zh,65000));
        Intent shared=new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,"用户原文 🙂");
        assertEquals("Shared text",IncomingText.read(en,shared,"用户笔记本").get(0).title());
        assertEquals("用户原文 🙂",IncomingText.read(en,shared,"用户笔记本").get(0).body());
        shared.putExtra(Intent.EXTRA_SUBJECT,"分享的文字");assertEquals("分享的文字",IncomingText.read(en,shared,"用户笔记本").get(0).title());
        assertEquals("Automatic backup · Current document",UiText.source(en,"自动备份 · 当前文稿"));
        try{new Library().addNotebook("");fail();}catch(IllegalArgumentException error){assertFalse(UiText.error(en,error).codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN));assertEquals(error.getMessage(),UiText.error(zh,error));}
    }
    @Test public void englishDualEditorKeepsReferenceTextAndLocalizesPaneActions() throws Exception {
        Library library=repo.load();Document reference=library.create("Reference notes", "参考原文 🙂", library.notebooks.get(0));repo.write(LibraryCodec.encode(library));
        try(var scenario=launch(2,true)) {
            ready(scenario,"Document text");onView(withContentDescription("Document menu")).perform(click());onView(withText("Writing tools")).perform(click());onData(hasToString("Second editor")).inRoot(isPlatformPopup()).perform(click());
            onView(withText("Reference notes · "+reference.notebook)).inRoot(isDialog()).perform(click());ready(scenario,"Primary text");
            onView(withContentDescription("Reference text")).check(matches(withText("参考原文 🙂")));
            onView(withContentDescription("Activate Reference")).check(matches(isDisplayed()));onView(withContentDescription("Choose Primary document")).check(matches(isDisplayed()));
            scenario.recreate();ready(scenario,"Primary text");onView(withContentDescription("Reference text")).check(matches(withText("参考原文 🙂")));
        }
    }
    @Test public void englishDailyStatisticsAndCountingChoicesAreLocalized() throws Exception {
        try(var scenario=launch(2,true)) {
            ready(scenario,"Document text");onView(withContentDescription("Document menu")).perform(click());onView(withText("Writing tools")).perform(click());onData(hasToString("Daily statistics")).inRoot(isPlatformPopup()).perform(click());
            onView(withContentDescription("Writing time overview")).inRoot(isDialog()).check(matches(withText("Writing today: 0 s · Total: 0 s")));
            onView(withText("Done")).inRoot(isDialog()).perform(click());settings("Document menu","Writing settings");
            onView(withContentDescription("Status bar counting mode")).inRoot(isDialog()).perform(scrollTo(),click());onData(hasToString("Characters (including spaces and punctuation)")).inRoot(isPlatformPopup()).perform(click());onView(withText("Done")).inRoot(isDialog()).perform(click());
            ready(scenario,"Document text");assertEquals(1,prefs.getInt("countMode",0));
        }
    }
    @Test public void nativeSystemSelectionIsAuthoritativeOverStalePreference() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT>=33);
        prefs.edit().putString("language","zh-CN").apply();context.getSystemService(android.app.LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags("en"));
        try(var scenario=ActivityScenario.launch(MainActivity.class)){ready(scenario,"Document list menu");scenario.onActivity(a->{assertEquals("en",AppLanguage.selection(a));assertEquals("en",AppLanguage.locale(a).getLanguage());});}
    }
}
