package com.jianzuo.writing;

import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AiInstrumentedTest {
    private static final String KEY="sk-instrumented-placeholder-0000";
    final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs; Map<String,?> before, beforeAi; String language, payload; LibraryRepository repo; Document doc; AiDialogs dialogs;
    @Before public void prepare() throws Exception {
        prefs=context.getSharedPreferences("writing",Context.MODE_PRIVATE); before=new HashMap<>(prefs.getAll()); beforeAi=new HashMap<>(context.getSharedPreferences("ai_credentials",0).getAll()); language=AppLanguage.selection(context);
        repo=new LibraryRepository(context.getFilesDir()); Library library=repo.load(); payload=LibraryCodec.encode(library);
        doc=library.create("AI测试", "前🙂后",library.notebooks.get(0)); repo.write(LibraryCodec.encode(library));
        prefs.edit().putBoolean("appLock",false).putBoolean("lastDual",false).putBoolean("lastPhoneEditor",true).putBoolean("recordWritingTime",false).putString("lastSelected",doc.id).putString("appearance","light").apply(); AppLanguage.set(context,1);
        new AiKeyStore(context).save(KEY,"deepseek-flash");
    }
    private void preferences(SharedPreferences p,Map<String,?> values) {
        var edit=p.edit().clear(); values.forEach((k,v)->{if(v instanceof Boolean b)edit.putBoolean(k,b);else if(v instanceof Integer i)edit.putInt(k,i);else if(v instanceof Long l)edit.putLong(k,l);else if(v instanceof Float f)edit.putFloat(k,f);else if(v instanceof String s)edit.putString(k,s);});edit.commit();
    }
    @After public void restore() throws Exception {
        if(dialogs!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->dialogs.destroy());
        repo.write(payload); preferences(prefs,before); preferences(context.getSharedPreferences("ai_credentials",0),beforeAi); AppLanguage.set(context,language.equals("zh-CN")?1:language.equals("en")?2:0);
    }
    private View find(View root,String description) {
        if(description.equals(root.getContentDescription()))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),description);if(result!=null)return result;}return null;
    }
    private void ready(ActivityScenario<MainActivity> scenario,String label) {
        AtomicBoolean found=new AtomicBoolean(); long limit=SystemClock.uptimeMillis()+8000;
        do {scenario.onActivity(a->found.set(a.hasWindowFocus()&&find(a.getWindow().getDecorView(),label)!=null));if(found.get())return;SystemClock.sleep(80);}while(SystemClock.uptimeMillis()<limit);fail("Missing "+label);
    }
    private void result(String label,String content) {
        long until=SystemClock.uptimeMillis()+7000;
        while(true)try{onView(withContentDescription(label)).inRoot(isDialog()).check(matches(withText(content)));return;}catch(AssertionError e){if(SystemClock.uptimeMillis()>=until)throw e;SystemClock.sleep(50);}
    }
    private DeepSeekClient.Reply reply() { return new DeepSeekClient.Reply(200,"{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"生成🙂\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":4}}"); }
    private AiDialogs owned(MainActivity activity, DeepSeekClient.Transport transport) {
        dialogs = new AiDialogs(activity, new AiKeyStore(activity), () -> new DeepSeekClient(transport), activity::applyAi);
        try { var owner = MainActivity.class.getDeclaredField("aiDialogs"); owner.setAccessible(true); owner.set(activity, dialogs); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        return dialogs;
    }
    private void preview(ActivityScenario<MainActivity> scenario,DeepSeekClient.Transport transport) {
        ready(scenario,"文稿正文"); scenario.onActivity(a->{owned(a,transport);dialogs.continuation(doc,3);});
    }
    @Test public void encryptedKeySurvivesStoreRecreationAndNeverEntersBackup() throws Exception {
        AiKeyStore store=new AiKeyStore(context);assertEquals(KEY,new AiKeyStore(context).read());
        String raw=context.getSharedPreferences("ai_credentials",0).getAll().toString();assertFalse(raw.contains(KEY));assertFalse(LibraryCodec.encode(repo.load()).contains(KEY));
        assertFalse(new AiKeyStore(context).read().equals(context.getSharedPreferences("ai_credentials",0).getString("ciphertext","")));
        store.clear();assertFalse(new AiKeyStore(context).configured());assertEquals("",store.read());
    }
    @Test public void settingsTestConnectionKeepsKeyMaskedAndCanRemoveIt() throws Exception {
        try(var scenario=ActivityScenario.launch(MainActivity.class)) {
            ready(scenario,"文稿正文");scenario.onActivity(a->{owned(a,(p,k,b)->new DeepSeekClient.Reply(200,"{\"data\":[{\"id\":\"deepseek-flash\"}]}"));dialogs.settings();});
            onView(withContentDescription("DeepSeek API Key")).inRoot(isDialog()).check(matches(withText("")));
            onView(withText("测试连接")).inRoot(isDialog()).perform(scrollTo(),click()); result("AI 状态","连接成功。可用模型：deepseek-flash"); assertEquals(KEY,new AiKeyStore(context).read());
            onView(withText("移除已保存 Key")).inRoot(isDialog()).perform(scrollTo(),click());assertFalse(new AiKeyStore(context).configured());
        }
    }
    @Test public void generationOnlyChangesTextAfterInsertAndUndoRestoresOriginal() throws Exception {
        try(var scenario=ActivityScenario.launch(MainActivity.class)) {
            preview(scenario,(p,k,b)->reply());onView(withText("插入光标位置")).inRoot(isDialog()).check(matches(not(isEnabled())));
            onView(withText("生成 / 重新生成")).inRoot(isDialog()).perform(scrollTo(),click());result("续写结果预览","生成🙂");
            scenario.onActivity(a->assertEquals("前🙂后",((EditText)find(a.getWindow().getDecorView(),"文稿正文")).getText().toString()));
            onView(withContentDescription("续写结果预览")).inRoot(isDialog()).perform(scrollTo(),replaceText("修改后的结果"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withText("插入光标位置")).inRoot(isDialog()).perform(scrollTo());
            android.graphics.Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try(var output=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"ai-preview.png"))){assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));}finally{screenshot.recycle();}
            onView(withText("插入光标位置")).inRoot(isDialog()).perform(scrollTo(),click());ready(scenario,"文稿正文");onView(withContentDescription("文稿正文")).check(matches(withText("前🙂修改后的结果后")));
            onView(withContentDescription("快捷输入：撤销")).perform(click());onView(withContentDescription("文稿正文")).check(matches(withText("前🙂后")));
        }
    }
    @Test public void cancellationDropsLateResultsAndKeepsOriginal() throws Exception {
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        try(var scenario=ActivityScenario.launch(MainActivity.class)) {
            preview(scenario,(p,k,b)->{started.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}return reply();});
            onView(withText("生成 / 重新生成")).inRoot(isDialog()).perform(scrollTo(),click());assertTrue(started.await(3,TimeUnit.SECONDS));
            onView(withText("取消生成")).inRoot(isDialog()).perform(scrollTo(),click());release.countDown();SystemClock.sleep(200);
            onView(withContentDescription("续写结果预览")).inRoot(isDialog()).check(matches(withText("")));onView(withText("插入光标位置")).inRoot(isDialog()).perform(scrollTo()).check(matches(not(isEnabled())));
            onView(withText("关闭")).inRoot(isDialog()).perform(click());ready(scenario,"文稿正文");onView(withContentDescription("文稿正文")).check(matches(withText("前🙂后")));
        } finally{release.countDown();}
    }
    @Test public void recreationCancelsOwnedRequestAndDropsOldPreview() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1); AtomicBoolean cancelled = new AtomicBoolean();
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            preview(scenario, new DeepSeekClient.Transport() {
                public DeepSeekClient.Reply request(String p, String k, String b) {
                    started.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} return reply();
                }
                public void cancel() { cancelled.set(true); }
            });
            onView(withText("生成 / 重新生成")).inRoot(isDialog()).perform(scrollTo(), click()); assertTrue(started.await(3, TimeUnit.SECONDS));
            scenario.recreate(); ready(scenario, "文稿正文"); assertTrue(cancelled.get()); release.countDown(); SystemClock.sleep(150);
            onView(withContentDescription("续写结果预览")).check(doesNotExist()); onView(withContentDescription("文稿正文")).check(matches(withText("前🙂后")));
        } finally { release.countDown(); }
    }
    @Test public void changedDocumentCannotBeOverwrittenByPreview() throws Exception {
        try(var scenario=ActivityScenario.launch(MainActivity.class)) {
            preview(scenario,(p,k,b)->reply());onView(withText("生成 / 重新生成")).inRoot(isDialog()).perform(scrollTo(),click());result("续写结果预览","生成🙂");
            scenario.onActivity(a->((EditText)find(a.getWindow().getDecorView(),"文稿正文")).setText("用户的新编辑"));
            onView(withText("插入光标位置")).inRoot(isDialog()).perform(scrollTo(),click());onView(withContentDescription("AI 状态")).inRoot(isDialog()).check(matches(withText(containsString("原文已变化"))));
            onView(withText("关闭")).inRoot(isDialog()).perform(click());ready(scenario,"文稿正文");onView(withContentDescription("文稿正文")).check(matches(withText("用户的新编辑")));
        }
    }
    @Test public void englishWorkflowAndNetworkErrorAreLocalized() throws Exception {
        AppLanguage.set(context,2);
        try(var scenario=ActivityScenario.launch(MainActivity.class)) {
            ready(scenario,"Document text");scenario.onActivity(a->{owned(a,(p,k,b)->{throw new java.io.IOException();});dialogs.continuation(doc,3);});
            onView(withText("Generate / regenerate")).inRoot(isDialog()).perform(scrollTo(),click());result("AI status","Cannot connect. Check your network and try again.");
            onView(withText("Insert at cursor")).inRoot(isDialog()).perform(scrollTo()).check(matches(not(isEnabled())));
        }
    }
    @Test public void realMenusOpenConfigurationWhenKeyIsMissing() throws Exception {
        new AiKeyStore(context).clear();
        try(var scenario=ActivityScenario.launch(MainActivity.class)) {
            ready(scenario,"文稿正文");onView(withContentDescription("文稿菜单")).perform(click());onView(withText("写作工具")).perform(click());onData(hasToString("AI 续写")).inRoot(isPlatformPopup()).perform(click());
            onView(withText("DeepSeek AI 设置")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withContentDescription("DeepSeek API Key")).inRoot(isDialog()).perform(replaceText(KEY),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());onView(withText("保存 AI 设置")).inRoot(isDialog()).perform(scrollTo(),click());
            onView(withText("AI 续写")).inRoot(isDialog()).check(matches(isDisplayed()));onView(withText("插入光标位置")).inRoot(isDialog()).perform(scrollTo()).check(matches(not(isEnabled())));
        }
    }
    @Test public void secondaryEditorInsertionKeepsPrimaryAndCursor() throws Exception {
        Library library=repo.load();Document primary=library.create("参考","主稿保持",library.notebooks.get(0));repo.write(LibraryCodec.encode(library));
        prefs.edit().putBoolean("lastDual",true).putBoolean("lastActiveSecond",true).putString("lastSelected",primary.id).putString("lastSecond",doc.id).putInt("cursor:"+doc.id,3).putInt("cursorEnd:"+doc.id,3).apply();
        try(var scenario=ActivityScenario.launch(MainActivity.class)) {
            ready(scenario,"参考稿正文");scenario.onActivity(a->{owned(a,(p,k,b)->reply());dialogs.continuation(doc,3);});
            onView(withText("生成 / 重新生成")).inRoot(isDialog()).perform(scrollTo(),click());result("续写结果预览","生成🙂");onView(withText("插入光标位置")).inRoot(isDialog()).perform(scrollTo(),click());ready(scenario,"参考稿正文");
            onView(withContentDescription("主稿正文")).check(matches(withText("主稿保持")));onView(withContentDescription("参考稿正文")).check(matches(withText("前🙂生成🙂后")));
            scenario.onActivity(a->assertEquals(7,((EditText)find(a.getWindow().getDecorView(),"参考稿正文")).getSelectionEnd()));
        }
    }
}
