package com.jianzuo.writing;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
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
public class TransferInstrumentedTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private View find(View view, String label) {
        if (label.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), label); if (found != null) return found; }
        return null;
    }
    private void ready(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean ready = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            scenario.onActivity(a -> ready.set(find(a.getWindow().getDecorView(), "新建文稿") != null || find(a.getWindow().getDecorView(), "文稿菜单") != null));
            if (ready.get()) return; SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("App did not finish loading");
    }
    private void importReady(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean shown = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            scenario.onActivity(a -> {
                try { Field field = MainActivity.class.getDeclaredField("importDialog"); field.setAccessible(true); shown.set(field.get(a) != null); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            if (shown.get()) return; SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Import preview did not become ready");
    }
    @Test public void sharedTextIsReviewedRotatedAndImportedOnlyOnce() throws Exception {
        String title = "系统分享-" + UUID.randomUUID(), body = "中文𠀀😀\n**分享正文**";
        Intent intent = new Intent(context, MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, body);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            ready(scenario);
            onView(withText("导入预览 · 1 篇")).inRoot(isDialog()).check(matches(isDisplayed()));
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            assertFalse(new LibraryRepository(context.getFilesDir()).load().documents.stream().anyMatch(doc -> doc.title.equals(title)));
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED); scenario.recreate(); ready(scenario);
            onView(withText("导入预览 · 1 篇")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText("导入选中文稿")).perform(click());
            onView(withContentDescription("文稿正文")).check(matches(withText(body)));
            scenario.recreate(); ready(scenario);
            onView(withText("导入预览 · 1 篇")).check(doesNotExist());
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            assertEquals(1, new LibraryRepository(context.getFilesDir()).load().documents.stream().filter(doc -> doc.title.equals(title) && doc.body.equals(body)).count());
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        }
    }
    @Test public void cancelledSharesNeverEnterTheLibraryAndManifestResolvesText() throws Exception {
        String title = "取消分享-" + UUID.randomUUID();
        Intent implicit = new Intent(Intent.ACTION_SEND).setType("text/plain").addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.getPackageName());
        assertFalse(context.getPackageManager().queryIntentActivities(implicit, 0).isEmpty());
        Intent intent = new Intent(implicit).setClass(context, MainActivity.class).putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, "取消的正文");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            ready(scenario); onView(withText("取消")).inRoot(isDialog()).perform(click());
            scenario.recreate(); ready(scenario); onView(withText("导入预览 · 1 篇")).check(doesNotExist());
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            assertFalse(new LibraryRepository(context.getFilesDir()).load().documents.stream().anyMatch(doc -> doc.title.equals(title)));
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        }
        assertThrows(Exception.class, () -> IncomingText.read(context.getContentResolver(), new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "中".repeat(TextImport.MAX_FILE_BYTES / 2)), "文稿"));
    }
    @Test public void sharingIntoAnOpenAppReusesTheExistingLibraryOwner() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario); java.util.concurrent.atomic.AtomicReference<MainActivity> original = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Intent> launchIntent = new java.util.concurrent.atomic.AtomicReference<>();
            scenario.onActivity(activity -> { original.set(activity); launchIntent.set(activity.getIntent()); });
            context.startActivity(new Intent(context, MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "已有窗口中的分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            importReady(scenario); scenario.onActivity(activity -> assertSame(original.get(), activity));
            onView(withText("取消")).inRoot(isDialog()).perform(click());
            // ActivityScenario filters lifecycle events by its launch intent. Restore it for cleanup only.
            scenario.onActivity(activity -> { activity.setIntent(launchIntent.get()); activity.finish(); });
        }
    }
    @Test public void largeExportSnapshotsSurviveRecreationWithoutLargeSavedState() throws Exception {
        String body = "中文𠀀😀\n".repeat(200_000);
        String id = ExportSnapshot.write(context.getCacheDir(), new ExportSnapshot.Payload("大文稿", "text", body));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);
            scenario.onActivity(activity -> {
                try {
                    Field field = MainActivity.class.getDeclaredField("exportSnapshot"); field.setAccessible(true); field.set(activity, id);
                    Bundle state = new Bundle(); activity.onSaveInstanceState(state);
                    Parcel parcel = Parcel.obtain();
                    try { parcel.writeBundle(state); assertTrue("Saved state unexpectedly contains manuscript", parcel.dataSize() < 64 * 1024); }
                    finally { parcel.recycle(); }
                    assertEquals(id, state.getString("exportSnapshot")); assertFalse(state.containsKey("exportBody"));
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            scenario.recreate(); ready(scenario);
            assertEquals(body, ExportSnapshot.read(context.getCacheDir(), id).body());
            scenario.onActivity(activity -> {
                try { Field field = MainActivity.class.getDeclaredField("exportSnapshot"); field.setAccessible(true); assertEquals(id, field.get(activity)); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                activity.onActivityResult(102, android.app.Activity.RESULT_CANCELED, null);
            });
            assertThrows(Exception.class, () -> ExportSnapshot.read(context.getCacheDir(), id));
            assertThrows(IllegalArgumentException.class, () -> ExportSnapshot.read(context.getCacheDir(), "../../library"));
            android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            automation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS");
            android.net.Uri output = null; String writingId = null;
            try {
                android.net.Uri parent = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents");
                output = create(parent, "jianzuo-export-" + UUID.randomUUID() + ".txt", "text/plain", "old data to replace");
                writingId = ExportSnapshot.write(context.getCacheDir(), new ExportSnapshot.Payload("大文稿", "text", body));
                String pending = writingId; android.net.Uri destination = output;
                scenario.onActivity(activity -> {
                    try { Field field = MainActivity.class.getDeclaredField("exportSnapshot"); field.setAccessible(true); field.set(activity, pending); }
                    catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                });
                scenario.recreate(); ready(scenario);
                scenario.onActivity(activity -> activity.onActivityResult(102, android.app.Activity.RESULT_OK, new Intent().setData(destination)));
                File snapshot = new File(new File(context.getCacheDir(), "pending-exports"), writingId + ".json");
                long deadline = SystemClock.uptimeMillis() + 8000;
                while (snapshot.exists() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
                assertFalse("Export did not finish", snapshot.exists());
                try (java.io.InputStream input = context.getContentResolver().openInputStream(output)) { assertEquals(body, new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
            } finally {
                if (output != null) android.provider.DocumentsContract.deleteDocument(context.getContentResolver(), output);
                ExportSnapshot.delete(context.getCacheDir(), writingId); automation.dropShellPermissionIdentity();
            }
        } finally { ExportSnapshot.delete(context.getCacheDir(), id); }
    }
    @Test public void previewEditsAndUncheckedItemsSurviveRotation() {
        String title = "旋转导入-" + UUID.randomUUID();
        Intent intent = new Intent(context, MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "预览正文");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            ready(scenario);
            onData(anything()).inRoot(isDialog()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(0).onChildView(withText("编辑")).perform(click());
            onView(withContentDescription("导入标题")).perform(replaceText(title)); androidx.test.espresso.Espresso.closeSoftKeyboard(); onView(withText("保存")).inRoot(isDialog()).perform(click());
            onData(anything()).inRoot(isDialog()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(0).onChildView(isAssignableFrom(android.widget.CheckBox.class)).perform(click());
            scenario.recreate(); ready(scenario);
            onView(allOf(isAssignableFrom(android.widget.CheckBox.class), withText(containsString(title)))).inRoot(isDialog()).check(matches(isNotChecked()));
            onView(withText("全选 / 清空")).inRoot(isDialog()).perform(click()); onView(withText("导入选中文稿")).perform(click());
            onView(withContentDescription("文稿标题")).check(matches(withText(title)));
        }
    }
    private android.net.Uri create(android.net.Uri parent, String name, String mime, String body) throws Exception {
        android.net.Uri uri = android.provider.DocumentsContract.createDocument(context.getContentResolver(), parent, mime, name);
        assertNotNull(uri);
        if (body != null) try (java.io.OutputStream output = context.getContentResolver().openOutputStream(uri)) { output.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        return uri;
    }
    @Test public void systemDocumentTreeAndSharedFilesReachTheSameImportPreview() throws Exception {
        String folder = "jianzuo-import-" + UUID.randomUUID();
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS");
        String directory = android.provider.DocumentsContract.Document.MIME_TYPE_DIR;
        android.net.Uri parent = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents");
        android.net.Uri root = create(parent, folder, directory, null);
        android.net.Uri tree = android.provider.DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Documents/" + folder);
        try {
            create(root, "root.md", "text/markdown", "中文𠀀😀");
            android.net.Uri book = create(root, "Book", directory, null), deep = create(book, "Deep", directory, null);
            create(book, "one.txt", "text/plain", "chapter"); create(deep, "two.markdown", "text/plain", "ending");
            create(root, "photo.png", "image/png", "ignored");
            var items = FolderImport.read(context.getContentResolver(), tree, "文稿");
            assertEquals(3, items.size()); assertEquals("中文𠀀😀", items.get(0).body());
            assertEquals("Book", items.get(1).notebook()); assertEquals("Book", items.get(2).notebook());
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                ready(scenario); scenario.onActivity(a -> a.onActivityResult(107, android.app.Activity.RESULT_OK, new Intent().setData(tree))); scenario.recreate(); ready(scenario); importReady(scenario);
                onView(withText("导入预览 · 3 篇")).inRoot(isDialog()).check(matches(isDisplayed()));
                onView(withText("取消")).inRoot(isDialog()).perform(click());
            }
            android.net.Uri document = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Documents/" + folder + "/root.md");
            Intent view = new Intent(context, MainActivity.class).setAction(Intent.ACTION_VIEW).setDataAndType(document, "text/markdown");
            assertEquals("中文𠀀😀", IncomingText.read(context.getContentResolver(), view, "日记").get(0).body());
            Intent multiple = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain").putParcelableArrayListExtra(Intent.EXTRA_STREAM, new java.util.ArrayList<>(java.util.List.of(document, document)));
            assertEquals(1, IncomingText.read(context.getContentResolver(), multiple, "日记").size());
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(view)) {
                ready(scenario); onView(withText("导入预览 · 1 篇")).inRoot(isDialog()).check(matches(isDisplayed()));
                onView(withText("导入选中文稿")).perform(click()); onView(withContentDescription("文稿正文")).check(matches(withText("中文𠀀😀")));
            }
        } finally { try { android.provider.DocumentsContract.deleteDocument(context.getContentResolver(), root); } finally { automation.dropShellPermissionIdentity(); } }
    }
}
