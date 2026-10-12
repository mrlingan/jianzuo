package com.jianzuo.writing;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.view.Menu;
import android.view.SubMenu;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.time.LocalDate;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private static final int IMPORT = 101, EXPORT = 102, BACKUP_IMPORT = 103, UNLOCK = 104, ZIP_IMPORT = 105, FONT_IMPORT = 106, FOLDER_IMPORT = 107;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Library library = new Library();
    private LibraryRepository repository;
    private SharedPreferences preferences;
    private String pendingNotebook;
    private String secondId;
    private boolean dual, activeSecond, secondPreview;
    private DualEditorView dualEditor;
    private View dualShortcuts;
    private String sectionFilter = "*", tagFilter = "";
    private String selectedId, filter = Library.ALL, search = "", saveState = "", exportBody;
    private boolean loaded, dirty, preview, focus, phoneEditor, rebuilding, unlockInProgress, locked;
    private String exportKind = "text", exportTitle = "";
    private String exportSnapshot;
    private boolean exportPreparing;
    private boolean incomingHandled, incomingReading;
    private ImportPreview.Session importSession;
    private AlertDialog importDialog;
    private FolderScan folderScan;
    private AlertDialog scanDialog;
    private TextView scanProgress;
    private RecoveryDialog recoveryDialog;
    private AiDialogs aiDialogs;
    private final Runnable importTick = this::resumeImport;
    private record RetainedImport(ImportPreview.Session session, boolean incomingHandled, FolderScan scan) {}
    private android.speech.tts.TextToSpeech speech;
    private boolean speechReady;
    private int revision, panes, paper, sidebar, ink, muted, accent, line, card;
    private LinearLayout root, documentRows, statusRow;
    private EditText titleInput, bodyInput;
    private TextView statusText, metricsText, listCount, directorySummary;
    private String countedBody;
    private WritingMetrics.Mode countedMode;
    private int countedTotal, countedWords;
    private ScrollView editorScroll;
    private int editorScrollY, cursorStart = -1, cursorEnd = -1;
    private final Runnable delayedSave = () -> saveAsync(false);
    private final WritingTime writingTime = new WritingTime();
    private boolean resumed;
    private final Runnable timeTick = new Runnable() {
        @Override public void run() {
            recordWritingTime(writingTime.collect(android.os.SystemClock.elapsedRealtime()));
            if (writingTime.running()) handler.postDelayed(this, 10_000);
        }
    };
    private void writingEdited() {
        if (rebuilding || !resumed || !hasWindowFocus() || !loaded || locked || !repository.isWritable() || !preferences.getBoolean("recordWritingTime", true)) return;
        recordWritingTime(writingTime.edit(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), java.time.ZoneId.systemDefault()));
        handler.removeCallbacks(timeTick); handler.postDelayed(timeTick, 10_000);
    }
    private void recordWritingTime(java.util.Map<String, Long> times) {
        if (times.isEmpty()) return;
        times.forEach((day, millis) -> library.dailyWritingMillis.merge(day, millis, (a, b) -> Math.min(WritingTime.MAX_DAY_MILLIS, a + b)));
        changed();
    }
    private void pauseWritingTime() {
        handler.removeCallbacks(timeTick);
        recordWritingTime(writingTime.pause(android.os.SystemClock.elapsedRealtime()));
    }

    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(AppLanguage.wrap(base)); }

    @Override public void onCreate(Bundle state) {
        saveState = s(R.string.ui_loading_86b6d0);
        preferences = getSharedPreferences("writing", MODE_PRIVATE);
        locked = preferences.getBoolean("appLock", false);
        boolean dark = isDark();
        setTheme(dark ? R.style.Theme_Jianzuo_Dark : R.style.Theme_Jianzuo);
        int overlay = switch (WritingAppearance.index(preferences.getString("colorTheme", "classic"))) {
            case 1 -> dark ? R.style.ThemeOverlayForestDark : R.style.ThemeOverlayForestLight;
            case 2 -> dark ? R.style.ThemeOverlayOceanDark : R.style.ThemeOverlayOceanLight;
            case 3 -> dark ? R.style.ThemeOverlayRoseDark : R.style.ThemeOverlayRoseLight;
            case 4 -> dark ? R.style.ThemeOverlayMonoDark : R.style.ThemeOverlayMonoLight;
            default -> 0;
        };
        if (overlay != 0) getTheme().applyStyle(overlay, true);
        super.onCreate(state);
        if (getLastNonConfigurationInstance() instanceof RetainedImport retained) { importSession = retained.session(); incomingHandled = retained.incomingHandled(); folderScan = retained.scan(); }
        if (state == null) {
            selectedId = preferences.getString("lastSelected", null); filter = preferences.getString("lastFilter", Library.ALL);
            secondId = preferences.getString("lastSecond", null); dual = preferences.getBoolean("lastDual", false); activeSecond = preferences.getBoolean("lastActiveSecond", false);
            if (dual) { preview = preferences.getBoolean("lastDualPreview", false); secondPreview = preferences.getBoolean("lastSecondPreview", false); }
            phoneEditor = preferences.getBoolean("lastPhoneEditor", false);
            editorScrollY = preferences.getInt("scroll:" + selectedId, 0); cursorStart = preferences.getInt("cursor:" + selectedId, -1); cursorEnd = preferences.getInt("cursorEnd:" + selectedId, cursorStart);
        }
        if (state != null) {
            secondId = state.getString("secondId"); dual = state.getBoolean("dual"); activeSecond = state.getBoolean("activeSecond"); secondPreview = state.getBoolean("secondPreview");
            selectedId = state.getString("selection"); filter = state.getString("filter", Library.ALL);
            search = state.getString("search", ""); sectionFilter = state.getString("sectionFilter", "*"); tagFilter = state.getString("tagFilter", ""); preview = state.getBoolean("preview"); focus = state.getBoolean("focus");
            phoneEditor = state.getBoolean("phoneEditor"); exportSnapshot = state.getString("exportSnapshot");
            if (importSession == null) incomingHandled = state.getBoolean("incomingHandled");
            editorScrollY = state.getInt("scroll"); cursorStart = state.getInt("cursorStart", -1); cursorEnd = state.getInt("cursorEnd", -1);
        }
        if (state == null) pendingNotebook = NotebookShortcuts.requested(getIntent());
        palette(); panes = getResources().getInteger(R.integer.pane_count);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::navigateBack);
        repository = new LibraryRepository(getFilesDir());
        render();
        io.execute(() -> {
            try {
                Library restored = repository.load();
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    library = restored; loaded = true; saveState = s(R.string.ui_saved_on_this_device_e78374);
                    if (selectedId == null || library.find(selectedId) == null) selectFirst();
                    applyNotebookIntent(); render(); resumeImport();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    loaded = true; saveState = s(R.string.ui_load_failed_original_file_preserved_08aee1); render();
                    showError(s(R.string.ui_cannot_load_the_library_874473), s(R.string.ui_the_original_file_has_been_preserved_without_956c39) + UiText.error(this, error));
                });
            }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); pendingNotebook = NotebookShortcuts.requested(intent); incomingHandled = false;
        if (loaded) { rememberEditor(); applyNotebookIntent(); render(); resumeImport(); }
    }
    private void applyNotebookIntent() {
        if (pendingNotebook == null) return;
        String name = pendingNotebook; pendingNotebook = null;
        if (library.notebooks.contains(name)) { rememberEditor(); dual = false; activeSecond = false; filter = name; search = ""; sectionFilter = "*"; tagFilter = ""; phoneEditor = false; focus = false; selectFirst(); editorScrollY = 0; cursorStart = -1; }
        else toast(s(R.string.ui_the_notebook_for_this_shortcut_no_longer_exis_00fa78));
    }
    private boolean isDark() {
        String appearance = preferences.getString("appearance", "system");
        return appearance.equals("dark") || (appearance.equals("system") &&
            (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }
    private void palette() {
        WritingAppearance.Palette colors = WritingAppearance.palette(preferences.getString("colorTheme", "classic"), isDark());
        paper = colors.paper(); sidebar = colors.sidebar(); ink = colors.ink(); muted = colors.muted();
        accent = colors.accent(); line = colors.line(); card = colors.card();
    }
    private void updateSystemBars() {
        boolean dark = isDark();
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                dark ? 0 : android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            int flags = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (!dark && Build.VERSION.SDK_INT >= 27) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }
    private void render() {
        pauseWritingTime();
        if (dualEditor != null) dualEditor.remember();
        dualEditor = null; dualShortcuts = null;
        rebuilding = true;
        if (preferences.getBoolean("keepScreen", false)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        titleInput = null; bodyInput = null; editorScroll = null; statusText = null; metricsText = null; statusRow = null; documentRows = null;
        root = vertical(); root.setBackgroundColor(paper); root.setFocusableInTouchMode(true);
        setContentView(root);
        root.post(this::updateSystemBars);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard)); return insets;
            }); root.requestApplyInsets();
        }
        if (preferences.getBoolean("appLock", false)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        if (locked) {
            LinearLayout gate = vertical(); gate.setGravity(Gravity.CENTER); gate.addView(text(s(R.string.ui_jianzuo_documents_locked_5642bd), 22, ink));
            gate.addView(quiet(s(R.string.ui_unlock_with_device_credentials_c16ac3), this::unlock)); root.addView(gate, new LinearLayout.LayoutParams(-1, -1)); rebuilding = false; return;
        }
        if (!loaded) {
            TextView loading = text(s(R.string.ui_jianzuo_opening_your_writing_space_264feb), 22, ink); loading.setGravity(Gravity.CENTER);
            root.addView(loading, new LinearLayout.LayoutParams(-1, -1)); rebuilding = false; return;
        }
        if (dual && (library.find(selectedId) == null || library.find(secondId) == null || selectedId.equals(secondId)
            || library.find(selectedId).deletedAt != null || library.find(secondId).deletedAt != null)) {
            dual = false; activeSecond = false; editorScrollY = preferences.getInt("scroll:" + selectedId, 0);
            cursorStart = preferences.getInt("cursor:" + selectedId, -1); cursorEnd = preferences.getInt("cursorEnd:" + selectedId, cursorStart);
        }
        LinearLayout content = horizontal(); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!dual && !focus && panes == 3) { content.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(196), -1)); dividerVertical(content); }
        if (!dual && !focus && (panes > 1 || !phoneEditor)) {
            content.addView(buildList(), panes > 1 ? new LinearLayout.LayoutParams(dp(panes == 3 ? 280 : 256), -1) : new LinearLayout.LayoutParams(-1, -1));
            if (panes > 1) dividerVertical(content);
        }
        if (dual) content.addView(buildDualEditor(), new LinearLayout.LayoutParams(-1, -1));
        else if (focus || panes > 1 || phoneEditor) content.addView(buildEditor(), new LinearLayout.LayoutParams(0, -1, 1));
        rebuilding = false; updateStatus();
        if (!dual && editorScroll != null) { ScrollView scroll = editorScroll; int y = editorScrollY; scroll.post(() -> scroll.scrollTo(0, y)); }
    }
    private View buildSidebar() {
        LinearLayout panel = vertical(); panel.setBackgroundColor(sidebar); panel.setPadding(dp(16), dp(24), dp(16), dp(14));
        TextView brand = text(s(R.string.app_name), 30, ink); brand.setTypeface(Typeface.SERIF, Typeface.BOLD); panel.addView(brand);
        TextView subtitle = text(s(R.string.ui_give_words_some_space_8e4172), 12, muted); subtitle.setPadding(0, dp(6), 0, dp(28)); panel.addView(subtitle);
        panel.addView(primary(s(R.string.ui_start_a_new_page_2e9085), this::createDocument));
        addSpace(panel, 24);
        for (String item : library.filters()) {
            Button row = quiet(UiText.filter(this, item) + "    " + library.count(item), () -> changeFilter(item)); row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            if (filter.equals(item)) row.setBackground(background(card, 8)); panel.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        }
        panel.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        panel.addView(quiet(s(R.string.ui_import_markdown_txt_b369a4), this::importDocument));
        panel.addView(quiet(s(R.string.ui_writing_settings_2020b1), this::settings));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(panel); return scroll;
    }
    private LinearLayout buildList() {
        LinearLayout panel = vertical(); panel.setPadding(dp(16), 0, dp(16), dp(10));
        LinearLayout toolbar = horizontal(); toolbar.setGravity(Gravity.CENTER_VERTICAL); panel.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));
        TextView heading = text(panes == 1 ? s(R.string.app_name) : UiText.filter(this, filter), panes == 1 ? 26 : 19, ink); heading.setTypeface(Typeface.SERIF, Typeface.BOLD);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(icon("plus", s(R.string.ui_new_document_748571), this::createDocument));
        toolbar.addView(icon("more", s(R.string.ui_document_list_menu_bf67bc), () -> listMenu(toolbar)));
        LinearLayout filterRow = horizontal(); filterRow.setGravity(Gravity.CENTER_VERTICAL);
        Button choose = quiet(UiText.filter(this, filter) + "  ▾", () -> filterMenu(filterRow)); choose.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        filterRow.addView(choose, new LinearLayout.LayoutParams(0, dp(48), 1));
        listCount = text("", 12, muted); filterRow.addView(listCount); panel.addView(filterRow);
        EditText searchInput = edit(s(R.string.ui_search_titles_and_text_e7d934), 14); searchInput.setSingleLine(true); searchInput.setText(search);
        searchInput.setContentDescription(s(R.string.ui_search_documents_18c542)); searchInput.setBackground(background(sidebar, 8)); searchInput.setPadding(dp(12), 0, dp(12), 0);
        panel.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(48)));
        searchInput.addTextChangedListener(watcher(() -> { search = searchInput.getText().toString(); refreshRows(); }));
        Button directory = quiet(s(R.string.ui_contents_and_filters_45b865), this::directoryFilter); directory.setContentDescription(s(R.string.ui_contents_and_filters_45b865)); panel.addView(directory);
        directorySummary = text("", 11, muted); directorySummary.setPadding(0, 0, 0, dp(6)); panel.addView(directorySummary);
        ScrollView scroll = new ChapterDirectoryScrollView(this, state -> state instanceof ChapterDrag); scroll.setFillViewport(true); scroll.setContentDescription(s(R.string.ui_chapter_list_scroll_area_6ebbb3));
        documentRows = vertical(); documentRows.setPadding(0, dp(12), 0, 0); scroll.addView(documentRows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); refreshRows();
        if (panes == 1) panel.addView(primary(s(R.string.ui_start_a_new_page_2e9085), this::createDocument));
        return panel;
    }
    private void refreshRows() {
        if (documentRows == null) return;
        documentRows.removeAllViews(); List<Document> visible = visible();
        if (listCount != null) listCount.setText(getString(R.string.document_count, visible.size()));
        if (directorySummary != null) {
            long words = visible.stream().mapToLong(Document::wordCount).sum();
            String scope = sectionFilter.equals("*") ? s(R.string.ui_all_volumes_5309cd) : sectionFilter.isEmpty() ? s(R.string.ui_no_volume_212370) : library.section(sectionFilter).name;
            long book = library.documents.stream().filter(d -> d.deletedAt == null && d.notebook.equals(filter)).mapToLong(Document::wordCount).sum();
            directorySummary.setText(getString(R.string.directory_summary, library.notebooks.contains(filter) ? getString(R.string.directory_book_total, book) : "", words, scope, tagFilter.isEmpty() ? s(R.string.ui_all_tags_582523) : "#" + tagFilter));
        }
        if (visible.isEmpty() && !(library.notebooks.contains(filter) && preferences.getBoolean("tree:" + filter, false) && search.isEmpty() && tagFilter.isEmpty())) {
            TextView empty = text(search.trim().isEmpty() ? filter.equals(Library.TRASH) ? s(R.string.ui_trash_is_empty_d820f4) : s(R.string.ui_no_documents_yet_start_a_new_page_dd03d2) : s(R.string.ui_no_matching_documents_ecd745), 16, muted);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(8), dp(48), dp(8), dp(48)); documentRows.addView(empty); return;
        }
        if (library.notebooks.contains(filter) && preferences.getBoolean("tree:" + filter, false)) directoryRows(visible);
        else for (Document doc : visible) documentRows.addView(documentRow(doc));
    }
    private View documentRow(Document doc) {
        LinearLayout row = vertical(); row.setPadding(dp(14), dp(16), dp(14), dp(16));
        row.setBackground(background(doc.id.equals(selectedId) && panes > 1 ? card : paper, 10));
        LinearLayout heading = horizontal(); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text((doc.favorite ? "★  " : "") + UiText.title(this, doc.title), 18, ink); title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (library.notebooks.contains(filter) && doc.deletedAt == null) {
            TextView handle = text("≡", 24, muted); handle.setGravity(Gravity.CENTER); handle.setContentDescription(s(R.string.ui_drag_chapter_00f239) + UiText.title(this, doc.title)); handle.setFocusable(true);
            heading.addView(handle, new LinearLayout.LayoutParams(dp(48), dp(48)));
            handle.setOnClickListener(v -> toast(s(R.string.ui_long_press_to_drag_before_a_chapter_or_into_a_d3b915)));
            handle.setOnLongClickListener(v -> {
                if (!canEdit()) return false; hideKeyboard(); rememberEditor();
                return v.startDragAndDrop(android.content.ClipData.newPlainText(s(R.string.ui_jianzuo_chapter_16f6af), doc.id), new View.DragShadowBuilder(row), new ChapterDrag(doc.id), 0);
            });
            dropTarget(row, doc, null, doc.notebook);
        }
        row.addView(heading);
        TextView excerpt = text(doc.excerpt().isEmpty() ? s(R.string.ui_waiting_for_your_first_sentence_bd1d17) : doc.excerpt(), 14, muted);
        excerpt.setMaxLines(2); excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END); excerpt.setPadding(0, dp(8), 0, dp(12)); row.addView(excerpt);
        TextView metadata = text(doc.notebook + (doc.section.isEmpty() ? "" : " / " + doc.section) + (doc.readOnly ? s(R.string.ui_read_only_74139b) : "") + " · " + s(R.string.words_label, doc.wordCount()) + " · " + DateFormat.getDateInstance(DateFormat.SHORT, AppLanguage.locale(this)).format(new Date(doc.updatedAt)), 11, muted); row.addView(metadata);
        row.setContentDescription(UiText.title(this, doc.title) + "，" + doc.notebook + "，" + doc.wordCount() + s(R.string.ui_words_457271)); row.setFocusable(true);
        row.setOnClickListener(v -> openDocument(doc)); row.setOnLongClickListener(v -> { documentMenu(row, doc); return true; });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8); row.setLayoutParams(params); return row;
    }
    private record ChapterDrag(String id) {}
    private void dropTarget(View target, Document before, Section section, String notebook) {
        target.setOnDragListener((view, event) -> {
            if (!(event.getLocalState() instanceof ChapterDrag drag)) return false;
            Document source = library.find(drag.id());
            if (source == null || source.deletedAt != null || !source.notebook.equals(notebook)) return false;
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED: return true;
                case android.view.DragEvent.ACTION_DRAG_ENTERED: view.setAlpha(.55f); return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED: case android.view.DragEvent.ACTION_DRAG_ENDED: view.setAlpha(1); return true;
                case android.view.DragEvent.ACTION_DROP:
                    view.setAlpha(1); if (!canEdit()) return false;
                    try {
                        if (before != null) library.placeBefore(source, before);
                        else { library.assignSection(source, section); source.rank = library.documents.stream().mapToLong(d -> d.rank).max().orElse(-1) + 1; }
                        preferences.edit().putString("sort:" + notebook, "manual").apply(); changed();
                        handler.post(this::render); return true;
                    } catch (IllegalArgumentException error) { showError(s(R.string.ui_cannot_move_the_chapter_93c594), UiText.error(this, error)); return false; }
                default: return true;
            }
        });
    }
    private void directoryRows(List<Document> visible) {
        List<Section> groups = new ArrayList<>(); groups.add(null); groups.addAll(library.sections(filter));
        for (Section section : groups) {
            String id = section == null ? "" : section.id;
            if (!sectionFilter.equals("*") && !sectionFilter.equals(id)) continue;
            List<Document> docs = new ArrayList<>(); for (Document doc : visible) if (doc.sectionId.equals(id)) docs.add(doc);
            if (docs.isEmpty() && (!search.isEmpty() || !tagFilter.isEmpty())) continue;
            boolean collapsed = preferences.getBoolean("collapsed:" + filter + ":" + id, false);
            long words = docs.stream().mapToLong(Document::wordCount).sum(); String name = section == null ? s(R.string.ui_no_volume_212370) : section.name;
            Button heading = quiet((collapsed ? "▸ " : "▾ ") + name + " · " + docs.size() + s(R.string.ui_documents_76af09) + words + s(R.string.ui_words_457271), () -> {
                preferences.edit().putBoolean("collapsed:" + filter + ":" + id, !collapsed).apply(); refreshRows();
            }); heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); heading.setContentDescription(s(R.string.ui_volume_contents_0992dd) + name); heading.setBackground(background(sidebar, 6));
            if (section != null) heading.setOnLongClickListener(v -> { if (canEdit()) organizer().section(section); return true; });
            dropTarget(heading, null, section, filter); documentRows.addView(heading, new LinearLayout.LayoutParams(-1, -2));
            if (!collapsed) for (Document doc : docs) documentRows.addView(documentRow(doc));
        }
    }
    private OrganizationDialogs organizer() { return new OrganizationDialogs(this, library, () -> { changed(); rememberEditor(); render(); }, this::canEdit); }
    private void chooseOrganizationBook(boolean numbering) {
        if (!canEdit()) return;
        if (library.notebooks.contains(filter)) { if (numbering) organizer().numbering(filter); else organizer().manage(filter); return; }
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_choose_a_notebook_716fea)).setItems(library.notebooks.toArray(new String[0]), (dialog, which) -> {
            String notebook = library.notebooks.get(which); if (numbering) organizer().numbering(notebook); else organizer().manage(notebook);
        }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private void directoryFilter() {
        LinearLayout panel = vertical(); panel.setPadding(dp(20), dp(10), dp(20), dp(10));
        CheckBox tree = check(panel, s(R.string.ui_show_collapsible_contents_by_volume_c2c14d), library.notebooks.contains(filter) && preferences.getBoolean("tree:" + filter, false)); tree.setEnabled(library.notebooks.contains(filter));
        List<Section> sections = library.notebooks.contains(filter) ? library.sections(filter) : new ArrayList<>();
        List<String> names = new ArrayList<>(), ids = new ArrayList<>(); names.add(s(R.string.ui_all_volumes_5309cd)); ids.add("*"); names.add(s(R.string.ui_no_volume_212370)); ids.add("");
        for (Section section : sections) { names.add(section.name); ids.add(section.id); }
        Spinner section = settingSpinner(panel, s(R.string.ui_volume_scope_ab16d0), names.toArray(new String[0]), Math.max(0, ids.indexOf(sectionFilter))); section.setEnabled(library.notebooks.contains(filter));
        java.util.TreeSet<String> tags = new java.util.TreeSet<>(); for (Document doc : library.documents) if (library.matches(doc, filter)) tags.addAll(Library.tags(doc.tags));
        List<String> tagNames = new ArrayList<>(); tagNames.add(s(R.string.ui_all_tags_582523)); tagNames.addAll(tags);
        Spinner tag = settingSpinner(panel, s(R.string.ui_tag_exact_match_e80814), tagNames.toArray(new String[0]), tagFilter.isEmpty() ? 0 : Math.max(0, tagNames.indexOf(tagFilter)));
        panel.addView(text(s(R.string.ui_long_press_a_volume_title_to_manage_it_long_p_9e7523), 13, muted));
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_contents_and_filters_45b865)).setView(panel).setNeutralButton(s(R.string.ui_clear_filters_657d9c), (d, which) -> { sectionFilter = "*"; tagFilter = ""; refreshRows(); })
            .setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_apply_63c73c), (d, which) -> {
                sectionFilter = library.notebooks.contains(filter) ? ids.get(section.getSelectedItemPosition()) : "*"; tagFilter = tag.getSelectedItemPosition() == 0 ? "" : tagNames.get(tag.getSelectedItemPosition());
                preferences.edit().putBoolean("tree:" + filter, tree.isChecked()).apply(); refreshRows();
            }).show();
    }
    private LinearLayout buildEditor() {
        LinearLayout panel = vertical(); Document doc = current();
        LinearLayout toolbar = horizontal(); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(4), 0, dp(4), 0);
        panel.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));
        if (panes == 1 || focus) toolbar.addView(icon("back", focus ? s(R.string.ui_return_to_writing_layout_c96b4b) : s(R.string.ui_back_to_document_list_0b22a7), this::navigateBack));
        TextView label = text(doc == null ? s(R.string.ui_writing_space_4f77b5) : doc.notebook, 13, muted);
        toolbar.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        if (doc != null) {
            toolbar.addView(icon(preview ? "edit" : "preview", preview ? s(R.string.ui_edit_text_2746d2) : s(R.string.ui_markdown_preview_e1abc2), () -> {
                rememberEditor(); hideKeyboard(); preview = !preview; render();
            }));
            if (doc.deletedAt == null) toolbar.addView(icon(doc.favorite ? "starFilled" : "star", doc.favorite ? s(R.string.ui_remove_favorite_dca608) : s(R.string.ui_add_to_favorites_ed91f2), () -> {
                rememberEditor(); doc.favorite = !doc.favorite; changed(); render();
            }));
            toolbar.addView(icon(focus ? "unfocus" : "focus", focus ? s(R.string.ui_leave_focus_mode_3f6477) : s(R.string.ui_focus_on_writing_b35534), () -> {
                rememberEditor(); hideKeyboard(); focus = !focus; render();
            }));
            toolbar.addView(icon("more", s(R.string.ui_document_menu_ccbbfa), () -> documentMenu(toolbar, doc)));
        }
        dividerHorizontal(panel);
        if (doc == null) {
            LinearLayout empty = vertical(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20), dp(30), dp(20), dp(30));
            TextView title = text(s(R.string.ui_give_words_some_space_8e4172), 26, ink); title.setTypeface(Typeface.SERIF); empty.addView(title);
            TextView subtitle = text(repository.isWritable() ? s(R.string.ui_choose_a_document_or_start_a_new_page_143e33) : s(R.string.ui_library_could_not_be_loaded_original_file_pre_15a4fe), 14, muted);
            subtitle.setPadding(0, dp(16), 0, dp(24)); empty.addView(subtitle);
            if (repository.isWritable()) empty.addView(primary(s(R.string.ui_start_a_new_page_f8056f), this::createDocument));
            empty.addView(quiet(s(R.string.ui_writing_settings_2020b1), this::settings)); panel.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            if (doc.deletedAt != null) {
                LinearLayout trashBar = horizontal(); trashBar.setGravity(Gravity.CENTER_VERTICAL); trashBar.setBackgroundColor(sidebar);
                TextView notice = text(s(R.string.ui_document_is_in_trash_6658c8), 13, muted); notice.setPadding(dp(16), 0, 0, 0);
                trashBar.addView(notice, new LinearLayout.LayoutParams(0, -2, 1)); trashBar.addView(quiet(s(R.string.ui_restore_e0534b), () -> restore(doc)));
                panel.addView(trashBar);
            }
            editorScroll = new ScrollView(this); editorScroll.setFillViewport(true);
            LinearLayout writing = vertical(); int padding = panes == 1 ? 24 : 32;
            // Center long-form writing on wide tablet windows, while keeping it usable in split screen.
            int available = getResources().getConfiguration().screenWidthDp - (focus ? 0 : panes == 3 ? 476 : panes == 2 ? 256 : 0);
            int horizontalPadding = Math.max(padding, (available - 760) / 2);
            writing.setPadding(dp(horizontalPadding), dp(26), dp(horizontalPadding), dp(32));
            editorScroll.addView(writing); panel.addView(editorScroll, new LinearLayout.LayoutParams(-1, 0, 1));
            boolean editable = !preview && !doc.readOnly && doc.deletedAt == null && repository.isWritable();
            if (editable) {
                titleInput = edit(s(R.string.ui_untitled_document_5c7cd4), 28); titleInput.setTypeface(Typeface.SERIF, Typeface.BOLD);
                titleInput.setText(doc.title); titleInput.setContentDescription(s(R.string.ui_document_title_5c4f95)); titleInput.setSelectAllOnFocus(false);
                titleInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                writing.addView(titleInput, new LinearLayout.LayoutParams(-1, -2));
                addSpace(writing, 12);
                WritingEditText field = new WritingEditText(this); field.setHint(s(R.string.ui_write_your_first_sentence_here_8ec108)); field.setTextColor(ink); field.setHintTextColor(muted); field.setTextSize(preferences.getInt("fontSize", 17));
                field.setBackgroundColor(Color.TRANSPARENT); field.setPadding(0, dp(4), 0, dp(4));
                field.configure(preferences.getBoolean("pairs", true), preferences.getBoolean("autoIndent", false), preferences.getBoolean("typewriter", false), muted);
                field.configureEditing(preferences.getBoolean("deleteIndent", false), preferences.getBoolean("commaOnNewline", false));
                field.configurePaper(preferences.getBoolean("ruledPaper", false), line);
                bodyInput = field; bodyInput.setText(doc.body); field.onSelectionChanged(this::updateStatus);
                bodyInput.setTypeface(editorTypeface()); bodyInput.setGravity(Gravity.TOP | Gravity.START); bodyInput.setMinLines(8);
                bodyInput.setLineSpacing(dp(preferences.getInt("lineSpacing", 9)), 1);
                bodyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                bodyInput.setContentDescription(s(R.string.ui_document_text_b83436)); bodyInput.setHorizontallyScrolling(false);
                writing.addView(bodyInput, new LinearLayout.LayoutParams(-1, -2));
                titleInput.addTextChangedListener(watcher(() -> { String next = titleInput.getText().toString(); if (doc.title.equals(next)) return;
                    writingEdited(); doc.checkpoint(false); doc.title = next; doc.touch(); changed(); }));
                bodyInput.addTextChangedListener(watcher(() -> { String next = bodyInput.getText().toString(); if (doc.body.equals(next)) return; writingEdited();
                    int added = Math.max(0, WritingMetrics.count(next) - doc.wordCount());
                    if (added > 0) library.dailyWords.merge(LocalDate.now().toString(), added, Integer::sum);
                    doc.checkpoint(false); doc.body = next; doc.touch(); changed(); }));
                if (cursorStart >= 0) {
                    int start = Math.min(cursorStart, doc.body.length()), end = Math.max(0, Math.min(cursorEnd, doc.body.length()));
                    bodyInput.setSelection(start, end);
                }
            } else {
                TextView title = text(UiText.title(this, doc.title), 28, ink); title.setTypeface(Typeface.SERIF, Typeface.BOLD); title.setTextIsSelectable(true); writing.addView(title);
                addSpace(writing, 24);
                if (preview) markdown(writing, doc.body);
                else {
                    PaperTextView body = new PaperTextView(this); body.setText(doc.body); body.setTextSize(preferences.getInt("fontSize", 17)); body.setTextColor(ink); body.setIncludeFontPadding(false);
                    body.configurePaper(preferences.getBoolean("ruledPaper", false), line); body.setContentDescription(s(R.string.ui_document_text_read_only_c7a0d1)); body.setTypeface(editorTypeface());
                    body.setTextIsSelectable(true); body.setLineSpacing(dp(preferences.getInt("lineSpacing", 9)), 1); writing.addView(body);
                }
            }
        }
        if (doc != null && doc.deletedAt == null && !doc.readOnly && !preview && preferences.getBoolean("shortcuts", true)) panel.addView(shortcutBar());
        dividerHorizontal(panel);
        statusRow = vertical(); statusRow.setPadding(dp(16), dp(10), dp(16), dp(10)); panel.addView(statusRow);
        LinearLayout footer = horizontal(); footer.setGravity(Gravity.CENTER_VERTICAL); statusRow.addView(footer);
        statusText = text(saveState, 11, muted); footer.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1));
        metricsText = statusMetrics(); footer.addView(metricsText, new LinearLayout.LayoutParams(0, -2, 2));
        updateStatus(); return panel;
    }
    private View buildDualEditor() {
        LinearLayout panel = vertical(), toolbar = horizontal(); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(icon("back", s(R.string.ui_close_dual_editor_da03dd), this::closeDualEditor));
        TextView heading = text(s(R.string.ui_dual_editor_ca94ac), 18, ink); heading.setTypeface(Typeface.SERIF, Typeface.BOLD); toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button linked = quiet(preferences.getBoolean("dualLinked", false) ? s(R.string.ui_linked_on_8155d0) : s(R.string.ui_linked_off_684168), () -> {
            boolean enabled = !preferences.getBoolean("dualLinked", false); preferences.edit().putBoolean("dualLinked", enabled).apply();
            rememberEditor(); hideKeyboard(); render();
            DualEditorView view = dualEditor; if (view != null && enabled) view.post(() -> view.linked(true));
        }); linked.setContentDescription(s(R.string.ui_linked_scrolling_d244e4)); toolbar.addView(linked);
        toolbar.addView(icon("settings", s(R.string.ui_dual_editor_proportions_475cde), () -> new AlertDialog.Builder(this).setTitle(s(R.string.ui_primary_pane_width_486dba))
            .setSingleChoiceItems(new String[]{"40%", "50%", "60%"}, (preferences.getInt("dualRatio", 50) - 40) / 10, (dialog, index) -> {
                rememberEditor(); hideKeyboard(); preferences.edit().putInt("dualRatio", 40 + index * 10).apply(); dialog.dismiss(); render();
            }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show()));
        toolbar.addView(icon("more", s(R.string.ui_document_menu_ccbbfa), () -> documentMenu(toolbar, current()))); panel.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56))); dividerHorizontal(panel);
        DualEditorView.Style style = new DualEditorView.Style(ink, muted, accent, paper, sidebar, line, editorTypeface(), preferences.getInt("fontSize", 17), preferences.getInt("lineSpacing", 9),
            preferences.getBoolean("pairs", true), preferences.getBoolean("autoIndent", false), preferences.getBoolean("typewriter", false), preferences.getBoolean("deleteIndent", false), preferences.getBoolean("commaOnNewline", false), repository.isWritable(), preferences.getBoolean("ruledPaper", false));
        dualEditor = new DualEditorView(this, library.find(selectedId), library.find(secondId), preview, secondPreview,
            getResources().getConfiguration().screenWidthDp >= 600, Math.max(40, Math.min(60, preferences.getInt("dualRatio", 50))), style, preferences, new DualEditorView.Listener() {
                public void activated(DualEditorView.Pane pane) {
                    if (activeSecond != pane.second) pauseWritingTime();
                    activeSecond = pane.second; titleInput = pane.title; bodyInput = pane.body; editorScroll = pane.scroll;
                    View focused = getCurrentFocus();
                    if (focused instanceof EditText && focused != titleInput && focused != bodyInput) focused.clearFocus();
                    if (dualShortcuts != null) dualShortcuts.setVisibility(bodyInput != null ? View.VISIBLE : View.GONE); updateStatus();
                }
                public void titleChanged(Document doc, String title) {
                    if (rebuilding || !canEdit() || doc.readOnly || doc.deletedAt != null || doc.title.equals(title)) return;
                    writingEdited(); doc.checkpoint(false); doc.title = title; doc.touch(); changed();
                }
                public void bodyChanged(Document doc, String body) {
                    if (rebuilding || !canEdit() || doc.readOnly || doc.deletedAt != null || doc.body.equals(body)) return;
                    writingEdited();
                    int added = Math.max(0, WritingMetrics.count(body) - doc.wordCount());
                    if (added > 0) library.dailyWords.merge(LocalDate.now().toString(), added, Integer::sum);
                    doc.checkpoint(false); doc.body = body; doc.touch(); changed();
                }
                public void selectionChanged() { updateStatus(); }
                public void choose(boolean secondary) { chooseDualDocument(secondary); }
                public void preview(boolean secondary) { rememberEditor(); hideKeyboard(); if (secondary) secondPreview = !secondPreview; else preview = !preview; render(); }
                public void markdown(LinearLayout target, String body) { MainActivity.this.markdown(target, body); }
            });
        panel.addView(dualEditor, new LinearLayout.LayoutParams(-1, 0, 1));
        if (preferences.getBoolean("shortcuts", true)) { dualShortcuts = shortcutBar(); panel.addView(dualShortcuts); }
        dividerHorizontal(panel); LinearLayout footer = horizontal(); footer.setGravity(Gravity.CENTER_VERTICAL); footer.setPadding(dp(16), dp(10), dp(16), dp(10));
        statusText = text(saveState, 11, muted); footer.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1)); metricsText = statusMetrics(); footer.addView(metricsText, new LinearLayout.LayoutParams(0, -2, 2)); panel.addView(footer);
        dualEditor.activate(activeSecond); dualEditor.linked(preferences.getBoolean("dualLinked", false)); return panel;
    }
    private boolean activePreview() { return dual && activeSecond ? secondPreview : preview; }
    private void activePreview(boolean value) { if (dual && activeSecond) secondPreview = value; else preview = value; }
    private void closeDualEditor() {
        rememberEditor(); hideKeyboard(); dual = false; activeSecond = false;
        editorScrollY = preferences.getInt("scroll:" + selectedId, 0); cursorStart = preferences.getInt("cursor:" + selectedId, -1); cursorEnd = preferences.getInt("cursorEnd:" + selectedId, cursorStart); render();
    }
    private void chooseDualDocument(boolean secondary) {
        rememberEditor(); hideKeyboard(); List<Document> choices = library.visible(Library.ALL, "");
        String exclude = secondary ? selectedId : secondId; choices.removeIf(doc -> doc.id.equals(exclude));
        String[] labels = choices.stream().map(doc -> UiText.title(this, doc.title) + " · " + doc.notebook).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(secondary ? s(R.string.ui_choose_reference_document_d4b70c) : s(R.string.ui_choose_primary_document_c8ed1a)).setItems(labels, (dialog, which) -> {
            if (secondary) { secondId = choices.get(which).id; secondPreview = false; } else { selectedId = choices.get(which).id; preview = false; }
            activeSecond = secondary; render();
        }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private void markdown(LinearLayout writing, String body) {
        if (body.isEmpty()) writing.addView(text(s(R.string.ui_no_text_yet_switch_to_editing_to_start_writin_1b0c6e), 15, muted));
        int font = preferences.getInt("fontSize", 17);
        for (Markdown.Block block : Markdown.parse(body)) {
            if (block.kind().equals("divider")) { dividerHorizontal(writing); addSpace(writing, 18); continue; }
            if (block.kind().equals("table")) { markdownTable(writing, block.text(), font); continue; }
            TextView view = text((block.kind().equals("list") || block.kind().equals("task")) ? block.marker() + "  " + block.text() : block.text(), font, ink);
            view.setTextIsSelectable(true); view.setTypeface(editorTypeface()); view.setLineSpacing(dp(preferences.getInt("lineSpacing", 9)), 1);
            if (!block.kind().equals("code")) { view.setText(MarkdownViews.inline(view.getText().toString())); view.setMovementMethod(LinkMovementMethod.getInstance()); view.setLinkTextColor(accent); }
            switch (block.kind()) {
                case "heading": view.setTextSize(font + Math.max(0, 5 - block.level()) * 3); view.setTypeface(editorTypeface(), Typeface.BOLD); break;
                case "quote": view.setTextColor(muted); view.setTypeface(editorTypeface(), Typeface.ITALIC); view.setPadding(dp(16), dp(12), dp(16), dp(12)); view.setBackground(background(sidebar, 4)); break;
                case "code": view.setTypeface(Typeface.MONOSPACE); view.setTextSize(Math.max(12, font - 3)); view.setText(block.text()); view.setPadding(dp(16), dp(12), dp(16), dp(12)); view.setBackground(background(sidebar, 6)); break;
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(18); writing.addView(view, params);
        }
    }
    private void markdownTable(LinearLayout writing, String source, int font) {
        android.widget.TableLayout table = new android.widget.TableLayout(this); String[] rows = source.split("\n");
        for (int i = 0; i < rows.length; i++) {
            if (i == 1) continue;
            android.widget.TableRow row = new android.widget.TableRow(this); table.addView(row);
            String text = rows[i].trim(); if (text.startsWith("|")) text = text.substring(1); if (text.endsWith("|")) text = text.substring(0, text.length() - 1);
            for (String cell : text.split("\\|", -1)) {
                TextView view = text(cell.trim(), font, ink); view.setText(MarkdownViews.inline(cell.trim())); view.setTextIsSelectable(true);
                view.setPadding(dp(12), dp(10), dp(12), dp(10)); view.setBackground(background(i == 0 ? card : sidebar, 0)); if (i == 0) view.setTypeface(editorTypeface(), Typeface.BOLD);
                row.addView(view);
            }
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.addView(table); writing.addView(scroll); addSpace(writing, 18);
    }
    private void openDocument(Document doc) {
        rememberEditor(); hideKeyboard(); dual = false; activeSecond = false; selectedId = doc.id; phoneEditor = true; preview = false; focus = false;
        editorScrollY = preferences.getInt("scroll:" + doc.id, 0); cursorStart = preferences.getInt("cursor:" + doc.id, -1); cursorEnd = preferences.getInt("cursorEnd:" + doc.id, cursorStart); render();
    }
    private Document current() { return library.find(dual && activeSecond ? secondId : selectedId); }
    private void selectFirst() { List<Document> visible = visible(); selectedId = visible.isEmpty() ? null : visible.get(0).id; }
    private void changeFilter(String next) {
        rememberEditor(); hideKeyboard(); dual = false; activeSecond = false; filter = next; phoneEditor = false; focus = false; search = ""; sectionFilter = "*"; tagFilter = ""; selectFirst(); editorScrollY = 0; cursorStart = -1; render();
    }
    private void createDocument() {
        if (!canEdit()) return;
        String notebook = library.notebooks.contains(filter) ? filter : library.notebooks.get(0);
        Document doc = library.create("", "", notebook);
        Section target = library.section(sectionFilter); if (target != null && target.notebook.equals(notebook)) library.assignSection(doc, target);
        doc.tags = tagFilter; filter = notebook; search = ""; changed(); openDocument(doc);
        if (titleInput != null) { titleInput.requestFocus(); titleInput.post(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT)); }
    }
    private boolean canEdit() {
        if (locked) return false;
        if (!loaded || !repository.isWritable()) { showError(s(R.string.ui_library_is_not_editable_a39077), s(R.string.ui_loading_failed_original_file_preserved_keep_t_9cc212)); return false; }
        return true;
    }
    private void filterMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        List<String> filters = library.filters();
        for (int i = 0; i < filters.size(); i++) menu.getMenu().add(0, i, i, UiText.filter(this, filters.get(i)));
        menu.setOnMenuItemClickListener(item -> { changeFilter(filters.get(item.getItemId())); return true; }); menu.show();
    }
    private List<Document> visible() {
        if (!sectionFilter.equals("*") && !sectionFilter.isEmpty() && (library.section(sectionFilter) == null || !library.section(sectionFilter).notebook.equals(filter))) sectionFilter = "*";
        List<Document> docs = library.visible(filter, search, preferences.getString("sort:" + filter, "updated"), preferences.getBoolean("searchAllTerms", false));
        docs.removeIf(doc -> library.notebooks.contains(filter) && !sectionFilter.equals("*") && !doc.sectionId.equals(sectionFilter) || !tagFilter.isEmpty() && !Library.tags(doc.tags).contains(tagFilter));
        return docs;
    }
    private void listMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        for (int action : new int[]{R.string.ui_import_markdown_txt_b369a4, R.string.ui_import_zip_documents_667b48, R.string.ui_import_folder_9592c9, R.string.ui_new_from_template_79fd4b, R.string.ui_manage_notebooks_ef1831, R.string.ui_manage_volumes_3349d9, R.string.ui_number_chapters_96ea06, R.string.ui_sort_order_604aad, R.string.ui_export_as_a_book_2a018a, R.string.ui_export_zip_archive_b87d63, R.string.ui_find_and_replace_across_library_f69a0d, R.string.ui_backup_and_restore_6beda4, R.string.ui_scratchpad_c92820, R.string.ui_daily_statistics_9f3465, R.string.ui_writing_settings_2020b1}) menu.getMenu().add(0, action, 0, action);
        menu.setOnMenuItemClickListener(item -> {
            switch (getResources().getResourceEntryName(item.getItemId())) {
                case "ui_import_markdown_txt_b369a4": importDocument(); break;
                case "ui_import_zip_documents_667b48": importZip(); break;
                case "ui_import_folder_9592c9": importFolder(); break;
                case "ui_new_from_template_79fd4b": templates(); break;
                case "ui_manage_notebooks_ef1831": notebooks(); break;
                case "ui_manage_volumes_3349d9": chooseOrganizationBook(false); break;
                case "ui_number_chapters_96ea06": chooseOrganizationBook(true); break;
                case "ui_sort_order_604aad": sorting(); break;
                case "ui_export_as_a_book_2a018a": exportBook(); break;
                case "ui_export_zip_archive_b87d63": exportArchive(); break;
                case "ui_find_and_replace_across_library_f69a0d": findReplace(true); break;
                case "ui_backup_and_restore_6beda4": backupMenu(); break;
                case "ui_scratchpad_c92820": scratch(); break;
                case "ui_daily_statistics_9f3465": dailyStats(); break;
                default: settings();
            }
            return true;
        }); menu.show();
    }
    private void documentMenu(View anchor, Document doc) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        if (doc.deletedAt != null) { menu.getMenu().add(0, R.string.ui_restore_document_dbf164, 0, R.string.ui_restore_document_dbf164); menu.getMenu().add(0, R.string.ui_delete_permanently_7aa6d0, 0, R.string.ui_delete_permanently_7aa6d0); }
        else {
            menu.getMenu().add(0, doc.favorite ? R.string.ui_remove_favorite_dca608 : R.string.ui_add_to_favorites_ed91f2, 0, doc.favorite ? R.string.ui_remove_favorite_dca608 : R.string.ui_add_to_favorites_ed91f2); menu.getMenu().add(0, R.string.ui_create_a_copy_f6825f, 0, R.string.ui_create_a_copy_f6825f); menu.getMenu().add(0, R.string.ui_move_to_notebook_6db666, 0, R.string.ui_move_to_notebook_6db666);
            menu.getMenu().add(0, R.string.ui_move_to_trash_fa5e19, 0, R.string.ui_move_to_trash_fa5e19);
        }
        menu.getMenu().add(0, R.string.ui_export_markdown_ac8b02, 0, R.string.ui_export_markdown_ac8b02); menu.getMenu().add(0, R.string.ui_export_txt_bc911e, 0, R.string.ui_export_txt_bc911e); menu.getMenu().add(0, R.string.ui_document_statistics_92f22f, 0, R.string.ui_document_statistics_92f22f); menu.getMenu().add(0, R.string.ui_writing_settings_2020b1, 0, R.string.ui_writing_settings_2020b1);
        SubMenu tools = menu.getMenu().addSubMenu(0, R.string.ui_writing_tools_e42610, 0, R.string.ui_writing_tools_e42610);
        for (int action : new int[]{R.string.ai_continue, R.string.ui_find_and_replace_d688f8, R.string.ui_revision_history_6fd295, R.string.ui_document_outline_3e55aa, R.string.ui_format_text_d8d425, R.string.ui_quick_undo_48333c, R.string.ui_quick_redo_3cc02e, R.string.ui_shortcut_toolbar_3d54ce, R.string.ui_move_paragraph_up_8b62a6, R.string.ui_move_paragraph_down_e50b27, R.string.ui_volume_and_tags_937eea, doc.readOnly ? R.string.ui_make_editable_d959d8 : R.string.ui_make_read_only_70be9c, R.string.ui_second_editor_d2a1c9, R.string.ui_scratchpad_c92820, R.string.ui_check_keywords_7e1c14, R.string.ui_read_text_aloud_2aa1cd, R.string.ui_stop_reading_6113d2, R.string.ui_daily_statistics_9f3465}) tools.add(0, action, 0, action);
        SubMenu exports = menu.getMenu().addSubMenu(0, R.string.ui_more_exports_and_sharing_63141b, 0, R.string.ui_more_exports_and_sharing_63141b);
        for (int action : new int[]{R.string.ui_export_pdf_e42c87, R.string.ui_export_png_image_1c7575, R.string.ui_export_html_aa02b7, R.string.ui_export_docx_75a725, R.string.ui_export_epub_f54571, R.string.ui_copy_rich_text_81c382, R.string.ui_copy_title_and_text_571351, R.string.ui_share_with_another_app_13a1c6}) exports.add(0, action, 0, action);
        SubMenu order = menu.getMenu().addSubMenu(0, R.string.ui_reorder_chapters_c41faa, 0, R.string.ui_reorder_chapters_c41faa); order.add(0, R.string.ui_move_chapter_up_6019b4, 0, R.string.ui_move_chapter_up_6019b4); order.add(0, R.string.ui_move_chapter_down_555d7d, 0, R.string.ui_move_chapter_down_555d7d); order.add(0, R.string.ui_new_chapter_before_this_832a24, 0, R.string.ui_new_chapter_before_this_832a24); order.add(0, R.string.ui_new_chapter_after_this_8b72a6, 0, R.string.ui_new_chapter_after_this_8b72a6);
        menu.setOnMenuItemClickListener(item -> {
            String action = getResources().getResourceEntryName(item.getItemId()); rememberEditor();
            switch (action) {
                case "ui_add_to_favorites_ed91f2": case "ui_remove_favorite_dca608": if (canEdit()) { doc.favorite = !doc.favorite; changed(); render(); } break;
                case "ui_create_a_copy_f6825f": if (canEdit()) { Document copy = library.duplicate(doc); copy.title = s(R.string.copy_title, UiText.title(this, doc.title)); filter = copy.notebook; search = ""; changed(); openDocument(copy); } break;
                case "ui_move_to_notebook_6db666": if (canEdit()) new AlertDialog.Builder(this).setTitle(s(R.string.ui_move_to_notebook_5bc9be)).setItems(library.notebooks.toArray(new String[0]), (dialog, which) -> {
                    library.moveToNotebook(doc, library.notebooks.get(which)); changed(); filter = doc.notebook; search = ""; openDocument(doc);
                }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show(); break;
                case "ui_move_to_trash_fa5e19": if (canEdit()) { library.trash(doc); changed(); if (doc.id.equals(selectedId)) { selectFirst(); phoneEditor = false; focus = false; editorScrollY = 0; cursorStart = -1; } render(); } break;
                case "ui_restore_document_dbf164": restore(doc); break;
                case "ui_delete_permanently_7aa6d0": if (canEdit()) new AlertDialog.Builder(this).setTitle(s(R.string.ui_delete_this_document_permanently_8a6952)).setMessage(s(R.string.permanent_delete_message, UiText.title(this, doc.title)))
                    .setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_delete_permanently_7aa6d0), (dialog, which) -> {
                        library.permanentlyDelete(doc); changed(); if (doc.id.equals(selectedId)) { selectFirst(); phoneEditor = false; focus = false; } render();
                    }).show(); break;
                case "ui_export_markdown_ac8b02": exportDocument(doc, "md"); break;
                case "ui_export_txt_bc911e": exportDocument(doc, "txt"); break;
                case "ui_document_statistics_92f22f": documentStats(doc); break;
                case "ui_writing_settings_2020b1": settings(); break;
                case "ai_continue": aiContinue(doc); break;
                case "ui_find_and_replace_d688f8": findReplace(false); break;
                case "ui_revision_history_6fd295": history(doc); break;
                case "ui_document_outline_3e55aa": outline(doc); break;
                case "ui_format_text_d8d425": format(doc); break;
                case "ui_quick_undo_48333c": undo(false); break;
                case "ui_shortcut_toolbar_3d54ce": customShortcuts(); break;
                case "ui_quick_redo_3cc02e": undo(true); break;
                case "ui_move_paragraph_up_8b62a6": moveParagraph(-1); break;
                case "ui_move_paragraph_down_e50b27": moveParagraph(1); break;
                case "ui_volume_and_tags_937eea": metadata(doc); break;
                case "ui_make_read_only_70be9c": case "ui_make_editable_d959d8": if (canEdit() && doc.deletedAt == null) { doc.readOnly = !doc.readOnly; changed(); render(); } break;
                case "ui_second_editor_d2a1c9": secondEditor(doc); break;
                case "ui_scratchpad_c92820": scratch(); break;
                case "ui_check_keywords_7e1c14": keywords(doc); break;
                case "ui_read_text_aloud_2aa1cd": speak(doc); break;
                case "ui_stop_reading_6113d2": if (speech != null) speech.stop(); break;
                case "ui_daily_statistics_9f3465": dailyStats(); break;
                case "ui_export_pdf_e42c87": exportRich(doc, "pdf"); break;
                case "ui_export_png_image_1c7575": exportRich(doc, "png"); break;
                case "ui_export_html_aa02b7": exportRich(doc, "html"); break;
                case "ui_export_docx_75a725": exportRich(doc, "docx"); break;
                case "ui_export_epub_f54571": exportRich(doc, "epub"); break;
                case "ui_copy_rich_text_81c382": ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newHtmlText(UiText.title(this, doc.title), UiText.title(this, doc.title) + "\n\n" + doc.body, PublicationExport.html(UiText.title(this, doc.title), doc.body))); toast(s(R.string.ui_rich_text_copied_a77ca3)); break;
                case "ui_copy_title_and_text_571351": ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText(UiText.title(this, doc.title), UiText.title(this, doc.title) + "\n\n" + doc.body)); toast(s(R.string.ui_copied_8f6f8d)); break;
                case "ui_share_with_another_app_13a1c6": startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, UiText.title(this, doc.title)).putExtra(Intent.EXTRA_TEXT, UiText.title(this, doc.title) + "\n\n" + doc.body), s(R.string.ui_share_document_16cefe))); break;
                case "ui_move_chapter_up_6019b4": case "ui_move_chapter_down_555d7d": if (canEdit()) { library.move(doc, action.equals("ui_move_chapter_up_6019b4") ? -1 : 1); preferences.edit().putString("sort:" + doc.notebook, "manual").apply(); changed(); render(); } break;
                case "ui_new_chapter_before_this_832a24": case "ui_new_chapter_after_this_8b72a6": if (canEdit()) {
                    try {
                        Document chapter = library.createAdjacent(doc, action.equals("ui_new_chapter_after_this_8b72a6")); filter = chapter.notebook; search = ""; sectionFilter = "*"; tagFilter = "";
                        preferences.edit().putString("sort:" + filter, "manual").apply(); changed(); openDocument(chapter);
                        if (titleInput != null) { titleInput.requestFocus(); titleInput.post(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT)); }
                    } catch (IllegalArgumentException error) { showError(s(R.string.ui_cannot_create_the_chapter_ff43a5), UiText.error(this, error)); }
                } break;
            }
            return true;
        }); menu.show();
    }
    private void restore(Document doc) { if (canEdit()) { library.restore(doc); filter = Library.ALL; search = ""; changed(); openDocument(doc); } }
    private void changed() {
        if (rebuilding) return;
        dirty = true; revision++; saveState = s(R.string.ui_saving_6bdb44); updateStatus(); handler.removeCallbacks(delayedSave); handler.postDelayed(delayedSave, 450);
    }
    private void updateStatus() {
        if (statusText != null) statusText.setText(saveState);
        Document doc = current();
        if (metricsText != null) {
            int goal = preferences.getInt("wordGoal", 800);
            WritingMetrics.Mode mode = WritingMetrics.Mode.at(preferences.getInt("countMode", 0));
            String summary = "";
            if (doc != null) {
                String body = doc.body;
                // Cursor moves reuse document totals; only the selected slice needs recounting.
                if (countedBody != body || countedMode != mode) {
                    countedBody = body; countedMode = mode; countedTotal = mode.count(body);
                    countedWords = mode == WritingMetrics.Mode.MIXED ? countedTotal : WritingMetrics.count(body);
                }
                int anchor = bodyInput == null ? 0 : bodyInput.getSelectionStart(), cursor = bodyInput == null ? 0 : bodyInput.getSelectionEnd();
                int start = Math.max(0, Math.min(body.length(), Math.min(anchor, cursor)));
                int end = Math.max(start, Math.min(body.length(), Math.max(anchor, cursor)));
                boolean selected = end > start;
                summary = (selected ? s(R.string.ui_selected_7b777f) + mode.count(body.substring(start, end)) + s(R.string.ui_total_552f34) : "") + countedTotal + " " + UiText.unit(this, mode);
                if (goal > 0) summary += (selected || mode != WritingMetrics.Mode.MIXED ? s(R.string.ui_words_b32925) + countedWords : "") + s(R.string.ui_goal_c7a5a4) + goal;
            }
            metricsText.setText(summary);
        }
    }
    private void countMode() {
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_status_bar_counting_mode_b61e57))
            .setSingleChoiceItems(java.util.Arrays.stream(WritingMetrics.Mode.values()).map(mode -> UiText.mode(this, mode)).toArray(String[]::new), WritingMetrics.Mode.at(preferences.getInt("countMode", 0)).ordinal(), (dialog, which) -> {
                preferences.edit().putInt("countMode", which).apply(); updateStatus(); dialog.dismiss();
            }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private TextView statusMetrics() {
        TextView view = text("", 11, muted); view.setGravity(Gravity.END); view.setContentDescription(s(R.string.ui_counts_and_counting_mode_bee70c)); view.setOnClickListener(v -> countMode());
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setContentDescription(s(R.string.ui_counts_caab0a) + ((TextView) host).getText() + s(R.string.ui_tap_to_change_counting_mode_8b51a1));
            }
        }); return view;
    }
    private void saveAsync(boolean wait) {
        handler.removeCallbacks(delayedSave);
        if (!dirty || !loaded || !repository.isWritable()) return;
        int savingRevision = revision;
        final String snapshot;
        try { snapshot = LibraryCodec.encode(library); }
        catch (Exception error) { saveState = s(R.string.ui_save_failed_6309a3); updateStatus(); showError(s(R.string.ui_save_failed_6309a3), UiText.error(this, error)); return; }
        Future<?> save = io.submit(() -> {
            try {
                repository.write(snapshot);
                runOnUiThread(() -> {
                    if (revision == savingRevision) { dirty = false; saveState = s(R.string.ui_saved_on_this_device_e78374); }
                    if (!isDestroyed()) { updateStatus(); refreshRows(); }
                });
            } catch (Exception error) { runOnUiThread(() -> {
                saveState = s(R.string.ui_save_failed_export_a_backup_f2790c); if (!isDestroyed()) { updateStatus(); showError(s(R.string.ui_document_could_not_be_saved_a29e01), s(R.string.ui_check_available_storage_and_export_the_curren_59b5c5) + UiText.error(this, error)); }
            }); }
        });
        if (wait) try { save.get(); } catch (Exception error) { saveState = s(R.string.ui_save_failed_6309a3); }
    }
    private void rememberEditor() {
        if (dualEditor != null) { dualEditor.remember(); return; }
        if (editorScroll != null) editorScrollY = editorScroll.getScrollY();
        if (bodyInput != null) { cursorStart = bodyInput.getSelectionStart(); cursorEnd = bodyInput.getSelectionEnd(); }
        if (selectedId != null) preferences.edit().putInt("scroll:" + selectedId, editorScrollY).putInt("cursor:" + selectedId, cursorStart).putInt("cursorEnd:" + selectedId, cursorEnd).apply();
    }
    private void hideKeyboard() {
        View view = getCurrentFocus(); if (view != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    private void navigateBack() {
        rememberEditor(); hideKeyboard();
        if (dual) closeDualEditor();
        else if (focus) { focus = false; render(); }
        else if (panes == 1 && phoneEditor) { phoneEditor = false; render(); }
        else { saveAsync(true); finish(); }
    }
    // Android 33+ uses the native OnBackInvokedCallback registered in onCreate; this is the API 26–32 fallback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { navigateBack(); }
    @Override protected void onResume() { super.onResume(); resumed = true; if (locked && !unlockInProgress) unlock(); else resumeImport(); }
    @Override protected void onPause() { resumed = false; pauseWritingTime(); super.onPause(); rememberEditor(); preferences.edit().putString("lastSelected", selectedId).putString("lastFilter", filter).putBoolean("lastPhoneEditor", phoneEditor).putString("lastSecond", secondId).putBoolean("lastDual", dual).putBoolean("lastActiveSecond", activeSecond).putBoolean("lastDualPreview", preview).putBoolean("lastSecondPreview", secondPreview).apply(); saveAsync(true); if (speech != null) speech.stop(); }
    @Override protected void onStop() {
        super.onStop();
        if (preferences.getBoolean("appLock", false) && !unlockInProgress && !isChangingConfigurations()) { rememberEditor(); locked = true; if (importDialog != null) { importDialog.dismiss(); importDialog = null; } dismissScan(); if (recoveryDialog != null) recoveryDialog.close(); if (aiDialogs != null) aiDialogs.close(); render(); }
    }
    @SuppressWarnings("deprecation")
    private void unlock() {
        if (!locked || unlockInProgress) return;
        android.app.KeyguardManager manager = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (!manager.isDeviceSecure()) { locked = false; preferences.edit().putBoolean("appLock", false).apply(); render(); return; }
        Intent intent = manager.createConfirmDeviceCredentialIntent(s(R.string.ui_unlock_jianzuo_31c94d), s(R.string.ui_use_device_credentials_to_view_documents_d4d8e5));
        if (intent != null) { unlockInProgress = true; startActivityForResult(intent, UNLOCK); }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); if (!hasFocus) pauseWritingTime();
    }
    @Override public Object onRetainNonConfigurationInstance() { return new RetainedImport(importSession, incomingHandled, folderScan); }
    @Override protected void onDestroy() { handler.removeCallbacks(importTick); handler.removeCallbacks(timeTick); handler.removeCallbacks(delayedSave); if (importDialog != null) importDialog.dismiss(); dismissScan(); if (!isChangingConfigurations() && folderScan != null) folderScan.cancel(); if (recoveryDialog != null) recoveryDialog.close(); if (speech != null) speech.shutdown(); if (aiDialogs != null) aiDialogs.destroy(); io.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        rememberEditor(); state.putString("secondId", secondId); state.putBoolean("dual", dual); state.putBoolean("activeSecond", activeSecond); state.putBoolean("secondPreview", secondPreview); state.putString("selection", selectedId); state.putString("filter", filter); state.putString("search", search); state.putString("sectionFilter", sectionFilter); state.putString("tagFilter", tagFilter);
        state.putBoolean("preview", preview); state.putBoolean("focus", focus); state.putBoolean("phoneEditor", phoneEditor);
        state.putString("exportSnapshot", exportSnapshot); state.putBoolean("incomingHandled", incomingHandled); state.putInt("scroll", editorScrollY); state.putInt("cursorStart", cursorStart); state.putInt("cursorEnd", cursorEnd);
        super.onSaveInstanceState(state);
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.isCtrlPressed()) {
            if (keyCode == KeyEvent.KEYCODE_F) { findReplace(false); return true; }
            if (keyCode == KeyEvent.KEYCODE_Z) { undo(event.isShiftPressed()); return true; }
            if (keyCode == KeyEvent.KEYCODE_N) { createDocument(); return true; }
            if (keyCode == KeyEvent.KEYCODE_S) { saveAsync(false); return true; }
            if (keyCode == KeyEvent.KEYCODE_P && current() != null) { rememberEditor(); activePreview(!activePreview()); render(); return true; }
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && (dual || focus)) { navigateBack(); return true; }
        return super.onKeyUp(keyCode, event);
    }
    private void importDocument() {
        if (!canEdit()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/markdown", "text/x-markdown", "application/octet-stream"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, IMPORT);
    }
    private void exportDocument(Document doc, String extension) {
        exportBody = doc.body; exportTitle = UiText.title(this, doc.title); exportKind = "text";
        createExport(UiText.title(this, doc.title) + "." + extension, extension.equals("md") ? "text/markdown" : "text/plain");
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == UNLOCK) {
            unlockInProgress = false;
            if (result == RESULT_OK) { locked = false; render(); resumeImport(); } else finish();
            return;
        }
        if (result != RESULT_OK || data == null || (data.getData() == null && !(request == IMPORT && data.getClipData() != null))) {
            if (request == EXPORT) { ExportSnapshot.delete(getCacheDir(), exportSnapshot); exportSnapshot = null; } return;
        }
        Uri uri = data.getData();
        if (request == BACKUP_IMPORT) { readBackup(uri); return; }
        if (request == ZIP_IMPORT) { readZip(uri); return; }
        if (request == FOLDER_IMPORT) { readFolder(uri); return; }
        if (request == FONT_IMPORT) { readFont(uri); return; }
        if (request == IMPORT) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri picked = data.getClipData().getItemAt(i).getUri(); if (picked != null && !uris.contains(picked)) uris.add(picked);
            } else if (uri != null) uris.add(uri);
            readDocuments(uris);
        } else if (request == EXPORT && exportSnapshot != null) {
            String snapshot = exportSnapshot; exportSnapshot = null;
            io.execute(() -> {
                java.io.File prepared = null;
                try {
                    ExportSnapshot.Payload payload = ExportSnapshot.read(getCacheDir(), snapshot);
                    String body = payload.body(), kind = payload.kind(), title = payload.title();
                    if (PreparedExport.supported(kind)) prepared = PreparedExport.render(getCacheDir(), payload, editorTypeface());
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new java.io.IOException(s(R.string.ui_cannot_write_the_file_bc86fe));
                        if (prepared != null) java.nio.file.Files.copy(prepared.toPath(), output);
                        else switch (kind) {
                            case "zip": exportZip(body, output); break;
                            case "docx": PublicationExport.docx(title, body, output); break;
                            case "epub": PublicationExport.epub(title, body, output); break;
                            default: output.write(body.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this, s(R.string.ui_exported_ed1940), Toast.LENGTH_SHORT).show(); });
                } catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed()) showError(s(R.string.ui_export_failed_6e4445), UiText.error(this, error)); }); }
                finally { if (prepared != null) prepared.delete(); ExportSnapshot.delete(getCacheDir(), snapshot); }
            });
        }
    }
    private void settings() {
        rememberEditor(); hideKeyboard();
        LinearLayout controls = vertical(); controls.setPadding(dp(24), dp(12), dp(24), dp(12));
        controls.addView(text(s(R.string.ui_words_hold_time_write_it_down_and_keep_it_73d999), 16, ink)); addSpace(controls, 16);
        controls.addView(quiet(s(R.string.ai_settings), () -> ai().settings()));
        Spinner language = settingSpinner(controls, getString(R.string.language), getResources().getStringArray(R.array.language_options), AppLanguage.index(this));
        Spinner font = settingSpinner(controls, s(R.string.ui_text_font_34f1a6), new String[]{s(R.string.ui_serif_b9b686), s(R.string.ui_system_font_96a2a0), s(R.string.ui_monospace_df5743), s(R.string.ui_custom_font_c26502)}, preferences.getInt("font", 0));
        SeekBar size = settingSlider(controls, s(R.string.ui_font_size_f71561), 14, 26, preferences.getInt("fontSize", 17));
        SeekBar spacing = settingSlider(controls, s(R.string.ui_line_spacing_40026a), 4, 18, preferences.getInt("lineSpacing", 9));
        int[] goals = {0, 300, 800, 1500, 3000}; int goalIndex = 2;
        for (int i = 0; i < goals.length; i++) if (goals[i] == preferences.getInt("wordGoal", 800)) goalIndex = i;
        Spinner counting = settingSpinner(controls, s(R.string.ui_status_bar_counting_mode_b61e57), java.util.Arrays.stream(WritingMetrics.Mode.values()).map(mode -> UiText.mode(this, mode)).toArray(String[]::new), WritingMetrics.Mode.at(preferences.getInt("countMode", 0)).ordinal());
        controls.addView(text(s(R.string.ui_document_goals_contents_and_daily_statistics_5d81d1), 12, muted));
        Spinner goal = settingSpinner(controls, s(R.string.ui_goal_per_document_2034a2), new String[]{s(R.string.ui_no_goal_c40c04), s(R.string.ui_300_words_3efa8c), s(R.string.ui_800_words_c25472), s(R.string.ui_1_500_words_681151), s(R.string.ui_3_000_words_0be38e)}, goalIndex);
        String appearance = preferences.getString("appearance", "system");
        Spinner theme = settingSpinner(controls, s(R.string.ui_appearance_86a63f), new String[]{s(R.string.ui_follow_system_217cfe), s(R.string.ui_light_aa0819), s(R.string.ui_dark_a6b75d)}, appearance.equals("dark") ? 2 : appearance.equals("light") ? 1 : 0);
        AppearanceSettings colors = new AppearanceSettings(this, preferences, () -> theme.getSelectedItemPosition() == 2 || theme.getSelectedItemPosition() == 0 &&
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        controls.addView(colors);
        theme.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { colors.refresh(); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        controls.addView(quiet(s(R.string.ui_import_custom_font_ttf_otf_0fd89b), this::importFont));
        controls.addView(quiet(s(R.string.ui_shortcut_toolbar_layout_and_content_74649b), this::customShortcuts));
        CheckBox shortcuts = check(controls, s(R.string.ui_show_shortcut_toolbar_1ea29e), preferences.getBoolean("shortcuts", true));
        CheckBox pairs = check(controls, s(R.string.ui_complete_and_delete_matching_punctuation_1f05e2), preferences.getBoolean("pairs", true));
        CheckBox autoIndent = check(controls, s(R.string.ui_continue_previous_indentation_on_a_new_line_579118), preferences.getBoolean("autoIndent", false));
        CheckBox deleteIndent = check(controls, s(R.string.ui_delete_leading_indentation_with_one_backspace_daaf64), preferences.getBoolean("deleteIndent", false));
        CheckBox commaOnNewline = check(controls, s(R.string.ui_change_a_trailing_comma_to_a_period_on_a_new_940990), preferences.getBoolean("commaOnNewline", false));
        CheckBox searchAllTerms = check(controls, s(R.string.ui_search_multiple_space_separated_terms_match_a_2b6aa8), preferences.getBoolean("searchAllTerms", false));
        CheckBox typewriter = check(controls, s(R.string.ui_typewriter_mode_highlight_the_current_paragra_f25946), preferences.getBoolean("typewriter", false));
        CheckBox recordTime = check(controls, s(R.string.ui_record_writing_time_8791ae), preferences.getBoolean("recordWritingTime", true));
        controls.addView(text(s(R.string.ui_timing_starts_when_you_edit_a_title_or_text_a_eaac1b), 12, muted));
        CheckBox keepScreen = check(controls, s(R.string.ui_keep_screen_awake_while_writing_09a18c), preferences.getBoolean("keepScreen", false));
        CheckBox appLock = check(controls, s(R.string.ui_lock_with_device_credentials_when_leaving_2d4e00), preferences.getBoolean("appLock", false));
        addSpace(controls, 16);
        controls.addView(quiet(s(R.string.ui_backup_and_restore_6beda4), this::backupMenu));
        controls.addView(quiet(s(R.string.ui_manage_notebooks_ef1831), this::notebooks));
        controls.addView(quiet(s(R.string.ui_daily_statistics_9f3465), this::dailyStats));
        TextView about = text(getString(R.string.about_app, getString(R.string.app_version)), 12, muted); controls.addView(about);
        ScrollView scroll = new ScrollView(this); scroll.addView(controls);
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_writing_settings_2020b1)).setView(scroll).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_done_c0b3fb), (dialog, which) -> {
            preferences.edit().putInt("font", font.getSelectedItemPosition()).putInt("fontSize", size.getProgress() + 14)
                .putInt("lineSpacing", spacing.getProgress() + 4).putInt("wordGoal", goals[goal.getSelectedItemPosition()]).putInt("countMode", counting.getSelectedItemPosition())
                .putString("appearance", new String[]{"system", "light", "dark"}[theme.getSelectedItemPosition()])
                .putString("colorTheme", colors.themeId()).putBoolean("ruledPaper", colors.ruled())
                .putBoolean("pairs", pairs.isChecked()).putBoolean("autoIndent", autoIndent.isChecked()).putBoolean("typewriter", typewriter.isChecked())
                .putBoolean("deleteIndent", deleteIndent.isChecked()).putBoolean("commaOnNewline", commaOnNewline.isChecked()).putBoolean("searchAllTerms", searchAllTerms.isChecked())
                .putBoolean("shortcuts", shortcuts.isChecked()).putBoolean("keepScreen", keepScreen.isChecked()).putBoolean("recordWritingTime", recordTime.isChecked())
                .putBoolean("appLock", appLock.isChecked() && ((android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isDeviceSecure()).apply();
            if (appLock.isChecked() && !((android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isDeviceSecure()) toast(s(R.string.ui_set_a_device_password_in_system_settings_firs_4c228d));
            boolean languageChanged = AppLanguage.set(this, language.getSelectedItemPosition());
            if (!languageChanged || Build.VERSION.SDK_INT < 33) recreate();
        }).show();
    }
    private Spinner settingSpinner(LinearLayout panel, String label, String[] choices, int selected) {
        TextView title = text(label, 13, muted); title.setPadding(0, dp(14), 0, dp(4)); panel.addView(title);
        Spinner spinner = new Spinner(this); spinner.setContentDescription(label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selected); panel.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48))); return spinner;
    }
    private SeekBar settingSlider(LinearLayout panel, String label, int min, int max, int value) {
        TextView title = text(label + " " + value, 13, muted); title.setPadding(0, dp(14), 0, 0); panel.addView(title);
        SeekBar slider = new SeekBar(this); slider.setMax(max - min); slider.setProgress(value - min); slider.setContentDescription(label);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) { title.setText(getString(R.string.setting_value, label, progress + min)); }
            public void onStartTrackingTouch(SeekBar bar) {} public void onStopTrackingTouch(SeekBar bar) {}
        }); panel.addView(slider, new LinearLayout.LayoutParams(-1, dp(48))); return slider;
    }
    private Typeface editorTypeface() {
        if (preferences.getInt("font", 0) == 3) {
            java.io.File file = new java.io.File(getFilesDir(), preferences.getString("fontFile", "missing-font"));
            try { if (file.exists()) return Typeface.createFromFile(file); } catch (RuntimeException ignored) { /* The system font remains usable if a font is removed. */ }
        }
        return switch (preferences.getInt("font", 0)) { case 1 -> Typeface.DEFAULT; case 2 -> Typeface.MONOSPACE; default -> Typeface.SERIF; };
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private CheckBox check(LinearLayout panel, String title, boolean checked) {
        CheckBox box = new CheckBox(this); box.setText(title); box.setTextColor(ink); box.setChecked(checked); panel.addView(box); return box;
    }
    private boolean editable(Document doc) {
        if (!canEdit() || doc == null) return false;
        if (doc.deletedAt != null || doc.readOnly) { toast(s(R.string.ui_restore_the_document_or_make_it_editable_firs_1848a0)); return false; }
        return true;
    }
    private AiDialogs ai() {
        if (aiDialogs == null) aiDialogs = new AiDialogs(this, this::applyAi);
        return aiDialogs;
    }
    boolean applyAi(AiContinuation draft, String generated) {
        if (!canEdit()) return false;
        Document target = library.find(draft.id);
        if (!draft.matches(target)) return false;
        rememberEditor();
        EditText input = null;
        if (dualEditor != null) {
            DualEditorView.Pane pane = draft.id.equals(dualEditor.primary.document.id) ? dualEditor.primary : dualEditor.secondary;
            if (draft.id.equals(pane.document.id)) input = pane.body;
        } else if (draft.id.equals(selectedId)) input = bodyInput;
        if (!draft.apply(target, generated)) return false;
        // Update the live Editable as one insertion so Android's Undo still works. The model was updated first,
        // so existing equality guards keep generated text out of human writing time and daily typing totals.
        if (input != null) {
            input.beginBatchEdit();
            try { input.getText().insert(draft.cursor, generated); input.setSelection(draft.cursor + generated.length()); }
            finally { input.endBatchEdit(); }
            input.requestFocus(); rememberEditor(); changed(); return true;
        }
        changed(); dualEditor = null;
        if (dual && current() != null && target.id.equals(current().id)) {
            preferences.edit().putInt("cursor:" + target.id, draft.cursor + generated.length()).putInt("cursorEnd:" + target.id, draft.cursor + generated.length()).apply(); render();
        } else {
            dual = false; activeSecond = false; selectedId = target.id; phoneEditor = true; preview = false;
            cursorStart = cursorEnd = draft.cursor + generated.length(); render();
        }
        return true;
    }
    private void aiContinue(Document doc) {
        if (!editable(doc)) return;
        rememberEditor(); hideKeyboard();
        int position = doc.body.length();
        if (current() != null && doc.id.equals(current().id) && !activePreview()) {
            position = dual ? preferences.getInt("cursorEnd:" + doc.id, doc.body.length()) : cursorEnd;
            if (position < 0) position = doc.body.length();
        }
        ai().continuation(doc, position);
    }
    private void setBody(Document doc, String body) {
        if (!editable(doc) || doc.body.equals(body)) return;
        doc.checkpoint(true); doc.body = body; doc.touch(); changed(); rememberEditor(); render();
    }
    private ShortcutConfig shortcutConfig() { return ShortcutConfig.load(preferences.getString("shortcutConfig", ""), preferences.getString("customShortcuts", "")); }
    private View shortcutBar() {
        LinearLayout rows = vertical(); rows.setBackgroundColor(sidebar); rows.setContentDescription(s(R.string.ui_shortcut_toolbar_3d54ce));
        int index = 0;
        for (List<ShortcutConfig.Item> items : shortcutConfig().visibleRows()) {
            HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); scroll.setContentDescription(s(R.string.shortcut_row, ++index));
            LinearLayout bar = horizontal(); bar.setBackgroundColor(sidebar); scroll.addView(bar);
            for (ShortcutConfig.Item entry : items) {
                Button button = quiet(UiText.shortcut(this, entry), () -> useShortcut(entry)); button.setContentDescription(s(R.string.ui_shortcut_3a7f2b) + UiText.shortcut(this, entry));
                button.setOnLongClickListener(v -> { customShortcuts(); return true; }); bar.addView(button);
            }
            rows.addView(scroll);
        }
        return rows;
    }
    private void useShortcut(ShortcutConfig.Item item) {
        switch (item.id()) {
            case "undo" -> undo(false);
            case "redo" -> undo(true);
            case "quotes", "parentheses", "bookmarks", "bold", "code", "brackets" -> insertPair(item.value());
            case "date" -> insert(LocalDate.now().toString());
            case "find" -> findReplace(false);
            default -> insert(item.value());
        }
    }
    private void undo(boolean redo) {
        if (!editable(current())) return;
        View focused = getCurrentFocus(); EditText input = focused instanceof EditText ? (EditText) focused : bodyInput;
        if (input == null) { toast(s(R.string.ui_enter_editing_mode_first_6be2c5)); return; }
        input.requestFocus(); if (!input.onTextContextMenuItem(redo ? android.R.id.redo : android.R.id.undo)) toast(s(R.string.ui_no_edits_to_ea6786) + (redo ? s(R.string.ui_redo_03717b) : s(R.string.ui_undo_926a50)) + s(R.string.ui_you_can_check_revision_history_db318a));
    }
    private void insert(String value) {
        if (!editable(current()) || bodyInput == null) return;
        int start = Math.max(0, Math.min(bodyInput.getSelectionStart(), bodyInput.getSelectionEnd())), end = Math.max(start, Math.max(bodyInput.getSelectionStart(), bodyInput.getSelectionEnd()));
        bodyInput.getText().replace(start, end, value); bodyInput.setSelection(start + value.length()); bodyInput.requestFocus();
    }
    private void insertPair(String pair) {
        if (!editable(current()) || bodyInput == null) return;
        int start = Math.max(0, Math.min(bodyInput.getSelectionStart(), bodyInput.getSelectionEnd())), end = Math.max(start, Math.max(bodyInput.getSelectionStart(), bodyInput.getSelectionEnd()));
        String selection = bodyInput.getText().subSequence(start, end).toString();
        bodyInput.getText().replace(start, end, pair.substring(0, 1) + selection + pair.substring(1));
        bodyInput.setSelection(start + 1, start + 1 + selection.length()); bodyInput.requestFocus();
    }
    private void templates() {
        if (!canEdit()) return;
        String[] names = {s(R.string.ui_blank_document_bcda08), s(R.string.ui_daily_journal_98b36e), s(R.string.ui_novel_chapter_9a5b9e), s(R.string.ui_article_outline_17088e), s(R.string.ui_to_do_list_2b2cdd)};
        String[] bodies = {"", "# " + LocalDate.now() + s(R.string.ui_what_happened_today_what_i_am_grateful_for_wh_a79d20), s(R.string.ui_scene_time_place_characters_text_hints_and_ne_26d136), s(R.string.ui_article_title_main_idea_opening_arguments_and_5d736d), s(R.string.ui_today_s_tasks_2b60f4)};
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_new_from_template_79fd4b)).setItems(names, (dialog, which) -> {
            String notebook = library.notebooks.contains(filter) ? filter : library.notebooks.get(0);
            Document doc = library.create(which == 0 ? "" : which == 1 ? LocalDate.now().toString() : names[which], bodies[which], notebook);
            filter = notebook; search = ""; changed(); openDocument(doc);
        }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private void notebooks() {
        if (!canEdit()) return;
        String[] choices = new String[library.notebooks.size() + 1]; choices[0] = s(R.string.ui_new_notebook_03cc61);
        for (int i = 0; i < library.notebooks.size(); i++) choices[i + 1] = library.notebooks.get(i);
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_manage_notebooks_ef1831)).setItems(choices, (dialog, which) -> {
            if (which == 0) notebookName(null);
            else {
                String name = choices[which];
                new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{s(R.string.ui_open_c77124), s(R.string.ui_rename_0d0cba), s(R.string.ui_add_to_home_screen_47ee0a), s(R.string.ui_delete_empty_notebook_816a87)}, (d, index) -> {
                    if (index == 0) changeFilter(name);
                    else if (index == 1) notebookName(name);
                    else if (index == 2) { try { toast(NotebookShortcuts.pin(this, name) ? s(R.string.ui_confirm_adding_the_shortcut_on_your_home_scre_0e117e) : s(R.string.ui_your_launcher_does_not_support_pinned_shortcu_6b29dc)); } catch (RuntimeException e) { showError(s(R.string.ui_cannot_add_the_shortcut_ab0a73), e.getMessage()); } }
                    else new AlertDialog.Builder(this).setTitle(s(R.string.delete_notebook_title, name)).setMessage(s(R.string.ui_only_notebooks_containing_no_documents_can_be_c63c8b)).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_delete_2f9daa), (confirm, button) -> {
                        try { library.deleteNotebook(name); try { NotebookShortcuts.deleted(this, name); } catch (RuntimeException ignored) { /* Launcher may no longer be available. */ } changed(); changeFilter(Library.ALL); }
                        catch (Exception error) { showError(s(R.string.ui_cannot_delete_8200c3), UiText.error(this, error)); }
                    }).show();
                }).show();
            }
        }).setNegativeButton(s(R.string.ui_done_c0b3fb), null).show();
    }
    private void notebookName(String old) {
        EditText input = edit(s(R.string.ui_notebook_name_05f293), 18); input.setSingleLine(true); input.setText(old == null ? "" : old);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(old == null ? s(R.string.ui_new_notebook_9c3307) : s(R.string.ui_rename_notebook_4fc19e)).setView(input).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_save_a3030b), null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            try {
                String name = input.getText().toString().trim(); if (old == null) library.addNotebook(name); else { library.renameNotebook(old, name); try { NotebookShortcuts.renamed(this, old, name); } catch (RuntimeException ignored) { toast(s(R.string.ui_home_screen_shortcut_was_not_updated_you_can_b7ae15)); } }
                changed(); dialog.dismiss(); changeFilter(name);
            } catch (Exception error) { input.setError(UiText.error(this, error)); }
        })); dialog.show();
    }
    private void sorting() {
        String[] labels = {s(R.string.ui_recently_edited_c18fdf), s(R.string.ui_recently_created_ee34c7), s(R.string.ui_title_a_z_c836b5), s(R.string.ui_manual_chapter_order_79ca70), s(R.string.ui_search_relevance_8d0f86)}, values = {"updated", "created", "title", "manual", "relevance"};
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_sort_order_1eb55e) + UiText.filter(this, filter)).setItems(labels, (dialog, which) -> {
            preferences.edit().putString("sort:" + filter, values[which]).apply(); refreshRows();
        }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private void metadata(Document doc) {
        if (!editable(doc)) return;
        LinearLayout panel = vertical(); panel.setPadding(dp(24), dp(12), dp(24), dp(12));
        panel.addView(text(s(R.string.ui_volume_chapter_group_1146d4), 13, muted)); EditText section = edit(s(R.string.ui_for_example_volume_one_e511bf), 17); section.setSingleLine(true); section.setText(doc.section); panel.addView(section);
        panel.addView(text(s(R.string.ui_tags_searchable_4cc0b9), 13, muted)); EditText tags = edit(s(R.string.ui_for_example_essay_draft_7505c4), 17); tags.setSingleLine(true); tags.setText(doc.tags); panel.addView(tags);
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_volume_and_tags_937eea)).setView(panel).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_save_a3030b), (dialog, which) -> {
            String name = section.getText().toString().trim();
            if (name.length() > 80) { toast(s(R.string.ui_volume_names_can_have_up_to_80_characters_abbe92)); return; }
            library.assignSection(doc, library.namedSection(doc.notebook, name)); doc.tags = tags.getText().toString().trim(); doc.touch(); changed(); render();
        }).show();
    }
    private void history(Document doc) {
        if (doc.history.isEmpty()) { showError(s(R.string.ui_revision_history_6fd295), s(R.string.ui_old_versions_are_saved_automatically_when_you_64d4b4)); return; }
        List<Document.Revision> versions = new ArrayList<>(doc.history); String[] labels = new String[versions.size()];
        for (int i = 0; i < versions.size(); i++) { Document.Revision r = versions.get(i); labels[i] = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, AppLanguage.locale(this)).format(new Date(r.time())) + " · " + WritingMetrics.count(r.body()) + s(R.string.ui_words_457271); }
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_revision_history_fd5a10) + UiText.title(this, doc.title)).setItems(labels, (dialog, which) -> {
            Document.Revision version = versions.get(which);
            TextView body = text(version.title() + "\n\n" + version.body(), 16, ink); body.setTextIsSelectable(true); body.setPadding(dp(20), dp(16), dp(20), dp(16));
            LinearLayout panel = vertical(); panel.addView(quiet(s(R.string.ui_compare_with_current_version_13eccf), () -> historyDiff(version, doc))); panel.addView(body);
            ScrollView scroll = new ScrollView(this); scroll.addView(panel);
            AlertDialog.Builder detail = new AlertDialog.Builder(this).setTitle(labels[which]).setView(scroll).setNegativeButton(s(R.string.ui_close_3fd47e), null)
                .setNeutralButton(s(R.string.ui_restore_as_a_copy_37cd83), (d, button) -> {
                    if (!canEdit()) return; Document copy = library.create(UiText.title(this, version.title()) + s(R.string.ui_history_copy_2cabee), version.body(), doc.notebook);
                    changed(); filter = copy.notebook; search = ""; openDocument(copy);
                });
            if (!doc.readOnly && doc.deletedAt == null) detail.setPositiveButton(s(R.string.ui_restore_this_version_655f0b), (d, button) -> new AlertDialog.Builder(this).setTitle(s(R.string.ui_restore_this_revision_cc63c2))
                .setMessage(s(R.string.ui_the_current_content_will_be_saved_in_revision_84fb97)).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_restore_e0534b), (confirm, ok) -> {
                    if (!editable(doc)) return; doc.restoreRevision(version); changed(); openDocument(doc);
                }).show());
            detail.show();
        }).setNegativeButton(s(R.string.ui_done_c0b3fb), null).show();
    }
    private void outline(Document doc) {
        List<TextTools.Heading> headings = TextTools.outline(doc.body);
        if (headings.isEmpty()) { showError(s(R.string.ui_document_outline_3e55aa), s(R.string.ui_use_markdown_headings_such_as_and_to_build_an_eb0a31)); return; }
        String[] labels = new String[headings.size()]; for (int i = 0; i < headings.size(); i++) labels[i] = new String(new char[headings.get(i).level() - 1]).replace('\0', '　') + headings.get(i).title();
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_document_outline_3e55aa)).setItems(labels, (dialog, which) -> {
            TextTools.Heading heading = headings.get(which); activePreview(false); cursorStart = heading.offset(); cursorEnd = cursorStart;
            render(); if (bodyInput != null) bodyInput.post(() -> { bodyInput.requestFocus(); bodyInput.setSelection(Math.min(heading.offset(), bodyInput.length())); });
        }).setNegativeButton(s(R.string.ui_close_3fd47e), null).show();
    }
    private void format(Document doc) {
        if (!editable(doc)) return;
        String[] names = {s(R.string.ui_remove_leading_indentation_f275a0), s(R.string.ui_indent_by_two_full_width_spaces_37ff4d), s(R.string.ui_keep_one_blank_line_between_paragraphs_2a4c4e), s(R.string.ui_remove_blank_lines_between_paragraphs_34b2aa), s(R.string.ui_add_spaces_between_chinese_and_english_06175c)};
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_format_text_plain_text_bab7c9)).setItems(names, (dialog, which) -> {
            String formatted = TextTools.format(doc.body, which);
            if (formatted.equals(doc.body)) { toast(s(R.string.ui_no_changes_needed_40f9ee)); return; }
            TextView sample = text(formatted.substring(0, Math.min(2000, formatted.length())), 15, ink); sample.setPadding(dp(20), dp(12), dp(20), dp(12));
            ScrollView scroll = new ScrollView(this); scroll.addView(sample);
            new AlertDialog.Builder(this).setTitle(names[which] + s(R.string.ui_preview_63ba24)).setView(scroll).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_apply_63c73c), (d, button) -> setBody(doc, formatted)).show();
        }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private record Replacement(Document doc, String original, String replacement, int matches) {}
    private String replacementNotebook() {
        return current() != null ? current().notebook : library.notebooks.contains(filter) ? filter : "";
    }
    private void findReplace(boolean global) {
        if (current() == null && !global) return;
        LinearLayout panel = vertical(); panel.setPadding(dp(24), dp(12), dp(24), dp(12));
        EditText query = edit(s(R.string.ui_find_text_d95072), 17), replacement = edit(s(R.string.ui_replace_with_leave_empty_to_delete_b7439b), 17); query.setSingleLine(true); replacement.setSingleLine(true);
        query.setContentDescription(s(R.string.ui_find_text_d95072)); replacement.setContentDescription(s(R.string.ui_replacement_text_f57e4d)); panel.addView(query); panel.addView(replacement);
        CheckBox sensitive = check(panel, s(R.string.ui_case_sensitive_8c7e34), false), regex = check(panel, s(R.string.ui_regular_expression_af33e6), false);
        Spinner scope = settingSpinner(panel, s(R.string.ui_search_scope_document_text_e1c2bf), new String[]{s(R.string.ui_current_document_8ee6eb), s(R.string.ui_current_notebook_0f91f3), s(R.string.ui_all_documents_e61e3e)}, global ? 2 : 0);
        TextView result = text(s(R.string.ui_revisions_are_saved_before_replacing_trash_an_8a9721), 13, muted); panel.addView(result);
        int[] matchIndex = {-1}; String[] lastQuery = {""};
        LinearLayout navigation = horizontal(); panel.addView(navigation);
        java.util.function.IntConsumer navigate = direction -> {
            try {
                Document doc = current(); if (doc == null) return;
                Pattern pattern = TextTools.pattern(query.getText().toString(), regex.isChecked(), sensitive.isChecked());
                List<TextTools.Match> matches = TextTools.matches(doc.body, pattern);
                String signature = pattern.pattern() + ":" + pattern.flags();
                if (!signature.equals(lastQuery[0])) { matchIndex[0] = -1; lastQuery[0] = signature; }
                if (matches.isEmpty()) { result.setText(s(R.string.ui_no_matching_text_adc8e6)); return; }
                matchIndex[0] = Math.floorMod(matchIndex[0] + direction, matches.size()); TextTools.Match match = matches.get(matchIndex[0]);
                if (activePreview()) { activePreview(false); render(); }
                if (bodyInput != null) { bodyInput.setSelection(match.start(), match.end()); bodyInput.requestFocus(); }
                result.setText(getString(R.string.find_navigation, matchIndex[0] + 1, matches.size()));
            } catch (Exception error) { result.setText(UiText.error(this, error)); }
        };
        navigation.addView(quiet(s(R.string.ui_previous_match_b7e4aa), () -> navigate.accept(-1))); navigation.addView(quiet(s(R.string.ui_next_match_72ac88), () -> navigate.accept(1)));
        ScrollView scroll = new ScrollView(this); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(s(R.string.ui_find_and_replace_d688f8)).setView(scroll).setNegativeButton(s(R.string.ui_close_3fd47e), null).setNeutralButton(s(R.string.ui_count_matches_75fdc3), null).setPositiveButton(s(R.string.ui_replace_all_2918d1), null).create();
        panel.addView(quiet(s(R.string.ui_select_matches_to_replace_ea99b7), () -> {
            try { selectedReplacement(TextTools.pattern(query.getText().toString(), regex.isChecked(), sensitive.isChecked()), replacement.getText().toString(), scope.getSelectedItemPosition(), dialog); }
            catch (Exception error) { result.setText(UiText.error(this, error)); }
        }));
        java.util.function.Supplier<List<Replacement>> plans = () -> {
            Pattern pattern = TextTools.pattern(query.getText().toString(), regex.isChecked(), sensitive.isChecked());
            List<Replacement> changes = new ArrayList<>();
            for (Document doc : library.documents) {
                if (doc.deletedAt != null || doc.readOnly) continue;
                if (scope.getSelectedItemPosition() == 0 && (current() == null || !doc.id.equals(current().id))) continue;
                if (scope.getSelectedItemPosition() == 1 && !doc.notebook.equals(replacementNotebook())) continue;
                int count = TextTools.matches(doc.body, pattern).size();
                if (count > 0) changes.add(new Replacement(doc, doc.body, TextTools.replace(doc.body, pattern, replacement.getText().toString()), count));
            }
            return changes;
        };
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
                try { List<Replacement> changes = plans.get(); result.setText(getString(R.string.find_totals, changes.size(), changes.stream().mapToInt(Replacement::matches).sum())); }
                catch (Exception error) { result.setText(UiText.error(this, error)); }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                try {
                    if (!canEdit()) return; List<Replacement> changes = plans.get();
                    int count = changes.stream().mapToInt(Replacement::matches).sum();
                    if (count == 0) { result.setText(s(R.string.ui_no_replaceable_text_5485bf)); return; }
                    new AlertDialog.Builder(this).setTitle(s(R.string.ui_replace_13e146) + changes.size() + s(R.string.ui_documents_with_a1a596) + count + s(R.string.ui_matches_bec226))
                        .setMessage(s(R.string.ui_the_current_version_will_be_kept_in_each_docu_72b032)).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_replace_131b13), (confirm, which) -> {
                            int applied = 0;
                            for (Replacement plan : changes) if (!plan.doc().readOnly && plan.doc().deletedAt == null && plan.doc().body.equals(plan.original())) {
                                plan.doc().checkpoint(true); plan.doc().body = plan.replacement(); plan.doc().touch(); applied += plan.matches();
                            }
                            changed(); rememberEditor(); dialog.dismiss(); render(); toast(s(R.string.ui_replaced_1bddf8) + applied + s(R.string.ui_matches_af9560));
                        }).show();
                } catch (Exception error) { result.setText(UiText.error(this, error)); }
            });
        }); dialog.show();
    }
    private void secondEditor(Document primary) {
        if (dual) { chooseDualDocument(true); return; }
        if (primary.deletedAt != null) { toast(s(R.string.ui_restore_the_document_before_opening_the_dual_874828)); return; }
        List<Document> others = library.visible(Library.ALL, ""); others.remove(primary);
        if (others.isEmpty()) { toast(s(R.string.ui_create_another_document_to_open_a_second_edit_b51b26)); return; }
        String[] names = others.stream().map(doc -> UiText.title(this, doc.title) + " · " + doc.notebook).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_choose_a_second_document_1c74b4)).setItems(names, (dialog, which) -> {
            rememberEditor(); hideKeyboard(); selectedId = primary.id; secondId = others.get(which).id;
            dual = true; activeSecond = false; secondPreview = false; phoneEditor = true; render();
        }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private void scratch() {
        if (!canEdit()) return;
        EditText input = edit(s(R.string.ui_write_down_an_idea_reference_or_your_next_sen_90ccfe), 17); input.setText(library.scratch); input.setGravity(Gravity.TOP); input.setMinLines(8); input.setPadding(dp(20), dp(12), dp(20), dp(12));
        input.setContentDescription(s(R.string.ui_scratchpad_text_289f51)); input.addTextChangedListener(watcher(() -> { library.scratch = input.getText().toString(); changed(); }));
        ScrollView scroll = new ScrollView(this); scroll.addView(input);
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_scratchpad_saved_automatically_b543d1)).setView(scroll).setNegativeButton(s(R.string.ui_close_3fd47e), null).setPositiveButton(s(R.string.ui_save_as_a_document_6289f4), (dialog, which) -> {
            if (library.scratch.trim().isEmpty()) { toast(s(R.string.ui_write_something_first_768cda)); return; }
            String notebook = library.notebooks.contains("灵感") ? "灵感" : library.notebooks.get(0);
            Document doc = library.create(s(R.string.ui_idea_ee06e5) + LocalDate.now(), library.scratch, notebook); changed(); filter = notebook; search = ""; openDocument(doc);
        }).show();
    }
    private void keywords(Document doc) {
        LinearLayout panel = vertical(); panel.setPadding(dp(24), dp(12), dp(24), dp(12));
        panel.addView(text(s(R.string.ui_enter_one_keyword_per_line_checked_on_this_de_b38fd5), 13, muted));
        EditText input = edit(s(R.string.ui_character_names_commonly_misspelled_words_wor_faa634), 16); input.setMinLines(4); input.setText(preferences.getString("keywords", "")); panel.addView(input);
        TextView result = text("", 14, ink); panel.addView(result);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(s(R.string.ui_check_keywords_7e1c14)).setView(panel).setNegativeButton(s(R.string.ui_close_3fd47e), null).setPositiveButton(s(R.string.ui_check_978e27), null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String terms = input.getText().toString(); preferences.edit().putString("keywords", terms).apply(); StringBuilder report = new StringBuilder();
            for (String term : terms.split("\n")) if (!term.trim().isEmpty()) {
                try { int count = TextTools.matches(doc.body, TextTools.pattern(term.trim(), false, true)).size(); report.append(term.trim()).append("：").append(count).append(s(R.string.ui_matches_8bd554)); }
                catch (Exception error) { report.append(term.trim()).append(s(R.string.ui_too_many_matches_07a30b)); }
            }
            result.setText(report.length() == 0 ? s(R.string.ui_enter_at_least_one_keyword_d496e4) : report.toString());
        })); dialog.show();
    }
    private void speak(Document doc) {
        if (doc.body.trim().isEmpty()) return;
        if (speech == null) {
            speech = new android.speech.tts.TextToSpeech(this, status -> {
                speechReady = status == android.speech.tts.TextToSpeech.SUCCESS;
                if (speechReady) { int result = speech.setLanguage(AppLanguage.locale(this));
                    if (result < 0) { toast(s(R.string.ui_no_voice_is_installed_for_this_language_4e8b46)); return; } speak(doc);
                } else toast(s(R.string.ui_system_text_to_speech_service_is_unavailable_e4d8bf));
            }); return;
        }
        if (!speechReady) { toast(s(R.string.ui_starting_text_to_speech_480929)); return; }
        speech.stop(); int max = android.speech.tts.TextToSpeech.getMaxSpeechInputLength();
        for (int start = 0; start < doc.body.length();) {
            int end = Math.min(doc.body.length(), start + max - 1);
            if (end < doc.body.length() && Character.isHighSurrogate(doc.body.charAt(end - 1))) end--;
            speech.speak(doc.body.substring(start, end), android.speech.tts.TextToSpeech.QUEUE_ADD, null, doc.id + ":" + start); start = end;
        }
    }
    private TextView statSummary(String label, String value) {
        TextView view = text(value, 16, ink); view.setContentDescription(label);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setContentDescription(label + "，" + ((TextView) host).getText());
            }
        }); return view;
    }
    private void dailyStats() {
        pauseWritingTime();
        int today = library.dailyWords.getOrDefault(LocalDate.now().toString(), 0), goal = preferences.getInt("dailyGoal", 800);
        long total = library.dailyWords.values().stream().mapToLong(Integer::longValue).sum(), active = library.dailyWords.values().stream().filter(n -> n > 0).count();
        LinearLayout panel = vertical(); panel.setPadding(dp(24), dp(12), dp(24), dp(12));
        panel.addView(text(s(R.string.daily_summary, today, total, active), 19, ink));
        long todayMillis = library.dailyWritingMillis.getOrDefault(LocalDate.now().toString(), 0L), totalMillis = library.dailyWritingMillis.values().stream().mapToLong(Long::longValue).sum();
        panel.addView(statSummary(s(R.string.ui_writing_time_overview_534ebc), s(R.string.time_summary, UiText.time(this, todayMillis), UiText.time(this, totalMillis))));
        panel.addView(text(preferences.getBoolean("recordWritingTime", true) ? s(R.string.ui_timing_is_on_up_to_60_seconds_after_the_last_168a78) : s(R.string.ui_timing_is_off_existing_records_are_kept_96d225), 12, muted));
        panel.addView(text(goal == 0 ? s(R.string.ui_no_daily_goal_899186) : s(R.string.ui_daily_goal_23e9db) + goal + s(R.string.ui_words_8ee179) + (today >= goal ? s(R.string.ui_reached_261f86) : s(R.string.ui_remaining_6937b5) + (goal - today) + s(R.string.ui_words_457271)), 14, accent));
        if (goal > 0) { ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(goal); progress.setProgress(Math.min(goal, today)); panel.addView(progress); }
        TextView explanation = text(s(R.string.ui_words_count_positive_changes_in_primary_and_r_4f06cf), 12, muted); explanation.setPadding(0, dp(12), 0, dp(16)); panel.addView(explanation);
        LinearLayout grid = vertical(); panel.addView(grid); LocalDate start = LocalDate.now().minusDays(27);
        for (int week = 0; week < 4; week++) {
            LinearLayout row = horizontal(); grid.addView(row);
            for (int day = 0; day < 7; day++) {
                LocalDate date = start.plusDays(week * 7L + day); int words = library.dailyWords.getOrDefault(date.toString(), 0);
                int level = WritingStats.intensity(words, goal > 0 ? goal : 800); float mix = level / 4f;
                int color = Color.rgb(Math.round(Color.red(sidebar) * (1 - mix) + Color.red(accent) * mix), Math.round(Color.green(sidebar) * (1 - mix) + Color.green(accent) * mix), Math.round(Color.blue(sidebar) * (1 - mix) + Color.blue(accent) * mix));
                TextView cell = text(date.getDayOfMonth() + "\n" + words, 11, WritingAppearance.foregroundOn(color)); cell.setGravity(Gravity.CENTER); cell.setBackground(background(color, 4));
                long millis = library.dailyWritingMillis.getOrDefault(date.toString(), 0L);
                cell.setContentDescription(date + s(R.string.ui_added_dfbdb8) + words + s(R.string.ui_words_writing_time_123c9e) + UiText.time(this, millis)); cell.setOnClickListener(v -> showError(date.toString(), s(R.string.ui_added_d763c8) + words + s(R.string.ui_words_writing_time_ea7e33) + UiText.time(this, millis)));
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(48), 1); size.setMargins(dp(2), dp(2), dp(2), dp(2)); row.addView(cell, size);
            }
        }
        panel.addView(quiet(s(R.string.ui_annual_review_4b3376), this::chooseYear));
        ScrollView scroll = new ScrollView(this); scroll.addView(panel);
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_daily_statistics_last_28_days_def49f)).setView(scroll).setNeutralButton(s(R.string.ui_daily_goal_d70f5d), (dialog, which) -> dailyGoal()).setPositiveButton(s(R.string.ui_done_c0b3fb), null).show();
    }
    private void dailyGoal() {
        EditText input = edit(s(R.string.ui_0_means_no_goal_771265), 18); input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); input.setText(String.valueOf(preferences.getInt("dailyGoal", 800)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(s(R.string.ui_daily_word_goal_9be76b)).setMessage(s(R.string.ui_0_1_000_000_words_only_positive_text_word_cou_797f62)).setView(input).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_save_a3030b), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int goal; try { goal = Integer.parseInt(input.getText().toString()); } catch (NumberFormatException e) { goal = -1; }
            if (goal < 0 || goal > 1_000_000) { input.setError(s(R.string.ui_enter_a_number_from_0_to_1_000_000_ffcbd2)); return; }
            preferences.edit().putInt("dailyGoal", goal).apply(); dialog.dismiss(); dailyStats();
        })); dialog.show();
    }
    private void chooseYear() {
        java.util.TreeSet<Integer> years = new java.util.TreeSet<>(java.util.Comparator.reverseOrder()); years.add(LocalDate.now().getYear());
        java.util.Set<String> recordedDays = new java.util.HashSet<>(library.dailyWords.keySet()); recordedDays.addAll(library.dailyWritingMillis.keySet());
        for (String day : recordedDays) { try { years.add(LocalDate.parse(day).getYear()); } catch (java.time.DateTimeException ignored) { /* Ignore invalid legacy dates. */ } }
        Integer[] values = years.toArray(new Integer[0]); String[] labels = years.stream().map(y -> s(R.string.year_label, y)).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_choose_a_year_fd25cd)).setItems(labels, (dialog, which) -> yearReview(values[which])).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private void yearReview(int year) {
        WritingStats.Year stats = WritingStats.year(library.dailyWords, year); LinearLayout panel = vertical(); panel.setPadding(dp(24), dp(12), dp(24), dp(12));
        panel.addView(text(s(R.string.year_words, year, stats.words()), 22, ink));
        panel.addView(text(s(R.string.ui_words_were_added_on_ba041e) + stats.activeDays() + s(R.string.ui_days_longest_streak_c4c58e) + stats.bestStreak() + s(R.string.ui_days_3cd654) + (stats.bestDay() == null ? s(R.string.ui_no_word_count_growth_was_recorded_this_year_307eae) : s(R.string.ui_most_productive_day_a13d53) + stats.bestDay() + " · " + stats.bestDayWords() + s(R.string.ui_words_457271)), 15, muted));
        WritingStats.TimeYear time = WritingStats.timeYear(library.dailyWritingMillis, year);
        panel.addView(statSummary(s(R.string.ui_annual_writing_time_dd3aa7), s(R.string.year_time, UiText.time(this, time.millis()), time.activeDays())));
        long max = java.util.Arrays.stream(stats.months()).max().orElse(0); addSpace(panel, 16);
        for (int month = 0; month < 12; month++) {
            panel.addView(text(s(R.string.month_summary, AppLanguage.locale(this).getLanguage().equals("zh") ? String.valueOf(month + 1) : java.time.Month.of(month + 1).getDisplayName(java.time.format.TextStyle.FULL, AppLanguage.locale(this)), stats.months()[month], UiText.time(this, time.months()[month])), 14, ink));
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(1000); bar.setProgress(max == 0 ? 0 : (int) (stats.months()[month] * 1000 / max)); panel.addView(bar);
        }
        panel.addView(text(s(R.string.ui_summarizes_daily_records_in_the_library_writi_3c25b1), 12, muted));
        ScrollView scroll = new ScrollView(this); scroll.addView(panel); new AlertDialog.Builder(this).setTitle(s(R.string.ui_annual_review_4b3376)).setView(scroll).setPositiveButton(s(R.string.ui_done_c0b3fb), null).show();
    }
    private void customShortcuts() {
        rememberEditor(); hideKeyboard();
        ShortcutSettings.show(this, shortcutConfig(), config -> {
            preferences.edit().putString("shortcutConfig", config.encode()).apply(); rememberEditor(); render();
        });
    }
    private void importFont() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, FONT_IMPORT);
    }
    private void readFont(Uri uri) {
        io.execute(() -> {
            java.io.File candidate = new java.io.File(getFilesDir(), "font-" + java.util.UUID.randomUUID() + ".ttf");
            try (InputStream input = getContentResolver().openInputStream(uri); java.io.FileOutputStream output = new java.io.FileOutputStream(candidate)) {
                if (input == null) throw new java.io.IOException(s(R.string.ui_cannot_read_the_font_file_f28c6e)); int total = 0, read; byte[] bytes = new byte[8192];
                while ((read = input.read(bytes)) != -1) { total += read; if (total > 32 * 1024 * 1024) throw new java.io.IOException(s(R.string.ui_font_files_must_be_32_mb_or_smaller_a3ca3a)); output.write(bytes, 0, read); }
                output.getFD().sync(); Typeface.createFromFile(candidate);
                preferences.edit().putString("fontFile", candidate.getName()).putInt("font", 3).apply();
                runOnUiThread(() -> { if (!isDestroyed()) { toast(s(R.string.ui_font_imported_3dc232)); rememberEditor(); recreate(); } });
            } catch (Exception error) { candidate.delete(); runOnUiThread(() -> { if (!isDestroyed()) showError(s(R.string.ui_font_import_failed_4cc7d0), s(R.string.ui_choose_a_valid_ttf_or_otf_font_ba46a3) + UiText.error(this, error)); }); }
        });
    }
    private void importZip() {
        if (!canEdit()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream"}); startActivityForResult(intent, ZIP_IMPORT);
    }
    private void importFolder() {
        if (!canEdit()) return;
        if (folderScan != null) { toast(s(R.string.ui_folder_scan_is_still_running_please_wait_f6d5cf)); return; }
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_import_folder_9592c9))
            .setMessage(s(R.string.ui_scan_utf_8_markdown_txt_in_the_selected_folde_e0af5f))
            .setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_choose_a_folder_0d6fad), (d, which) -> {
                try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), FOLDER_IMPORT); }
                catch (RuntimeException error) { showError(s(R.string.ui_cannot_choose_a_folder_b611e6), UiText.error(this, error)); }
            }).show();
    }
    private void readFolder(Uri uri) {
        if (folderScan != null) { toast(s(R.string.ui_folder_scan_is_still_running_please_wait_f6d5cf)); return; }
        String notebook = library.notebooks.contains(filter) ? filter : library.notebooks.get(0);
        try { folderScan = FolderScan.start(getApplicationContext().getContentResolver(), uri, notebook); }
        catch (java.util.concurrent.RejectedExecutionException error) { showError(s(R.string.ui_cannot_scan_right_now_ff1610), s(R.string.ui_the_file_provider_still_has_an_unfinished_rea_521423)); return; }
        resumeImport();
    }
    private void dismissScan() {
        if (scanDialog != null) { scanDialog.dismiss(); scanDialog = null; scanProgress = null; }
    }
    private void cancelScan() {
        if (folderScan != null) { folderScan.cancel(); folderScan = null; }
        dismissScan(); handler.removeCallbacks(importTick); toast(s(R.string.ui_scan_cancelled_no_documents_imported_03aa66)); resumeImport();
    }
    private void showScan() {
        if (scanDialog == null) {
            LinearLayout panel = vertical(); panel.setPadding(dp(24), dp(12), dp(24), dp(12));
            panel.addView(new ProgressBar(this)); scanProgress = text("", 14, ink); panel.addView(scanProgress);
            scanDialog = new AlertDialog.Builder(this).setTitle(s(R.string.ui_scanning_folder_4d7fda)).setView(panel)
                    .setNegativeButton(s(R.string.ui_cancel_scan_323d82), (dialog, which) -> cancelScan()).create();
            scanDialog.setOnCancelListener(dialog -> cancelScan()); scanDialog.setCanceledOnTouchOutside(false); scanDialog.show();
        }
        FolderImport.Control progress = folderScan.control;
        scanProgress.setText(getString(R.string.folder_scan_progress, progress.entries, progress.files, progress.bytes / 1048576.0));
    }
    private void resumeImport() {
        if (!loaded || locked || isDestroyed() || isFinishing()) return;
        if (importSession != null) { showImportSession(); return; }
        if (folderScan != null) {
            handler.removeCallbacks(importTick);
            if (!folderScan.complete) { showScan(); handler.postDelayed(importTick, 200); return; }
            FolderScan scan = folderScan; folderScan = null;
            dismissScan();
            if (scan.error != null) showError(s(R.string.ui_folder_import_failed_53accf), s(R.string.ui_no_documents_were_imported_14847a) + UiText.error(this, scan.error));
            else previewImport(scan.items);
            return;
        }
        Intent source = getIntent();
        if (incomingHandled || incomingReading || !IncomingText.accepts(source) || !repository.isWritable()) return;
        incomingReading = true;
        String notebook = library.notebooks.contains(filter) ? filter : library.notebooks.get(0);
        io.execute(() -> {
            try {
                List<TextImport.Item> items = IncomingText.read(this, source, notebook);
                runOnUiThread(() -> {
                    incomingReading = false; if (isDestroyed()) return;
                    if (getIntent() != source) { resumeImport(); return; }
                    incomingHandled = true; previewImport(items);
                });
            } catch (Exception error) { runOnUiThread(() -> {
                incomingReading = false; if (isDestroyed()) return;
                if (getIntent() != source) { resumeImport(); return; }
                incomingHandled = true; showError(s(R.string.ui_share_import_failed_d95f33), s(R.string.ui_no_documents_were_imported_14847a) + UiText.error(this, error));
            }); }
        });
    }
    private void readZip(Uri uri) {
        io.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException(s(R.string.ui_cannot_open_the_zip_file_6892c9)); List<TextImport.Item> items = TextImport.zip(input);
                runOnUiThread(() -> { if (!isDestroyed()) previewImport(items); });
            } catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed()) showError(s(R.string.ui_zip_import_failed_e20fa6), UiText.error(this, error)); }); }
        });
    }
    private void previewImport(List<TextImport.Item> items) {
        importSession = new ImportPreview.Session(items); showImportSession();
    }
    private void showImportSession() {
        if (importSession == null || !loaded || locked || isFinishing() || isDestroyed() || importDialog != null) return;
        if (!canEdit()) return;
        importDialog = ImportPreview.show(this, importSession, selected -> {
            importSession = null; importDialog = null;
            if (!canEdit()) return; Document first = null;
            for (TextImport.Item item : selected) {
                String notebook = item.notebook();
                if (notebook.isEmpty() || notebook.equals(Library.ALL) || notebook.equals(Library.FAVORITES) || notebook.equals(Library.TRASH)) notebook = library.notebooks.get(0);
                if (!library.notebooks.contains(notebook)) library.addNotebook(notebook);
                Document doc = library.create(item.title(), item.body(), notebook); if (first == null) first = doc;
            }
            if (first != null) { changed(); filter = first.notebook; search = ""; sectionFilter = "*"; tagFilter = ""; preferences.edit().putString("sort:" + filter, "manual").apply(); openDocument(first); }
            toast(s(R.string.ui_imported_2a9010) + selected.size() + s(R.string.ui_documents_fb56f5));
            resumeImport();
        }, () -> { importSession = null; importDialog = null; resumeImport(); });
    }

    private void exportRich(Document doc, String kind) {
        if (kind.equals("pdf") || kind.equals("png")) {
            LinearLayout panel = vertical(); panel.setPadding(dp(24), dp(8), dp(24), dp(8));
            panel.addView(text(s(R.string.ui_markdown_layout_supports_headings_bold_italic_38a5bc), 14, muted));
            android.widget.RadioGroup modes = new android.widget.RadioGroup(this);
            android.widget.RadioButton rich = new android.widget.RadioButton(this); rich.setId(View.generateViewId()); rich.setText(R.string.print_markdown); modes.addView(rich);
            android.widget.RadioButton source = new android.widget.RadioButton(this); source.setId(View.generateViewId()); source.setText(R.string.print_source); modes.addView(source); modes.check(rich.getId()); panel.addView(modes);
            new AlertDialog.Builder(this).setTitle(kind.equals("pdf") ? s(R.string.ui_pdf_layout_0f32cc) : s(R.string.ui_image_layout_5796d5)).setView(panel).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null)
                    .setPositiveButton(s(R.string.ui_choose_a_save_location_edfa33), (dialog, which) -> prepareRichExport(doc, kind + (source.isChecked() ? "-source" : ""))).show();
            return;
        }
        prepareRichExport(doc, kind);
    }
    private void prepareRichExport(Document doc, String kind) {
        exportTitle = UiText.title(this, doc.title); exportKind = kind; exportBody = kind.equals("html") ? PublicationExport.html(UiText.title(this, doc.title), doc.body) : doc.body;
        String extension = kind.replace("-source", ""); createExport(exportTitle + "." + extension, exportMime(extension));
    }
    private void createExport(String name, String mime) {
        if (exportPreparing || exportSnapshot != null) { exportBody = null; toast(s(R.string.ui_complete_or_cancel_the_current_export_first_c6ce74)); return; }
        exportPreparing = true;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-"));
        ExportSnapshot.Payload payload = new ExportSnapshot.Payload(exportTitle, exportKind, exportBody); exportBody = null;
        io.execute(() -> {
            try {
                String id = ExportSnapshot.write(getCacheDir(), payload);
                runOnUiThread(() -> {
                    exportPreparing = false;
                    if (isDestroyed() || isFinishing()) { ExportSnapshot.delete(getCacheDir(), id); return; }
                    ExportSnapshot.delete(getCacheDir(), exportSnapshot); exportSnapshot = id;
                    try { startActivityForResult(intent, EXPORT); }
                    catch (RuntimeException error) { ExportSnapshot.delete(getCacheDir(), id); exportSnapshot = null; showError(s(R.string.ui_cannot_export_d4f1b0), UiText.error(this, error)); }
                });
            } catch (Exception error) { runOnUiThread(() -> { exportPreparing = false; if (!isDestroyed()) showError(s(R.string.ui_cannot_prepare_the_export_e55c78), UiText.error(this, error)); }); }
        });
    }
    private void exportBook() {
        String[] books = library.notebooks.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_choose_a_notebook_to_export_as_a_book_1fe13e)).setItems(books, (dialog, which) -> {
            if (library.count(books[which]) == 0) { toast(s(R.string.ui_this_notebook_has_no_documents_to_export_5d3937)); return; }
            String name = books[which];
            new AlertDialog.Builder(this).setTitle(s(R.string.ui_book_export_format_1edca3)).setItems(new String[]{"Markdown", "DOCX", "EPUB"}, (choice, format) -> {
                String extension = new String[]{"md", "docx", "epub"}[format];
                exportBody = library.bookText(name); exportKind = format == 0 ? "text" : extension; exportTitle = name;
                createExport(name + "." + extension, format == 0 ? "text/markdown" : exportMime(extension));
            }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
        }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    private void exportArchive() {
        try { exportBody = LibraryCodec.encode(library); exportKind = "zip"; createExport(s(R.string.ui_jianzuo_documents_48a64f) + LocalDate.now() + ".zip", "application/zip"); }
        catch (Exception error) { showError(s(R.string.ui_cannot_export_d4f1b0), UiText.error(this, error)); }
    }
    private void exportZip(String snapshot, OutputStream output) throws Exception {
        Library source = LibraryCodec.decode(snapshot);
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Document doc : source.documents) {
                if (doc.deletedAt != null) continue;
                String folder = doc.notebook.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-");
                String title = UiText.title(this, doc.title).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-");
                // A UUID suffix guarantees unique entries even for duplicate titles or sanitized names.
                zip.putNextEntry(new java.util.zip.ZipEntry(folder + "/" + title.substring(0, Math.min(80, title.length())) + "-" + doc.id + ".md"));
                zip.write(doc.body.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
    }
    private void backupMenu() {
        pauseWritingTime();
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_backup_and_restore_6beda4)).setItems(new String[]{s(R.string.ui_export_library_backup_with_history_99a658), s(R.string.ui_merge_from_a_backup_ec29f0), s(R.string.ui_browse_local_automatic_backups_9f70d1), s(R.string.ui_find_lost_content_by_keyword_aff56e)}, (dialog, which) -> {
            if (which == 0) {
                try { exportBody = BackupCodec.encode(library); exportKind = "text"; createExport(s(R.string.ui_jianzuo_backup_9286a4) + LocalDate.now() + ".jzb", "application/octet-stream"); }
                catch (Exception error) { showError(s(R.string.ui_backup_failed_f3b414), UiText.error(this, error)); }
            } else if (which == 1) {
                if (!canEdit()) return;
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), BACKUP_IMPORT);
            } else if (which == 2) localBackups(); else recoverContent();
        }).setNegativeButton(s(R.string.ui_done_c0b3fb), null).show();
    }
    private void recoverContent() {
        if (!canEdit()) return; rememberEditor();
        if (recoveryDialog != null) recoveryDialog.close();
        recoveryDialog = new RecoveryDialog(this, repository, () -> { rememberEditor(); return RecoverySearch.current(library); }, value -> {
            if (!canEdit()) return;
            Document restored = RecoverySearch.restore(library, value); changed();
            filter = restored.notebook; search = ""; sectionFilter = "*"; tagFilter = ""; openDocument(restored); toast(s(R.string.ui_saved_as_a_new_document_source_content_preser_3e11f0));
        });
        recoveryDialog.show();
    }
    private void readBackup(Uri uri) {
        io.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException(s(R.string.ui_cannot_read_the_backup_96d303));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) != -1) { if (bytes.size() + read > 64 * 1024 * 1024) throw new java.io.IOException(s(R.string.ui_backups_must_be_64_mb_or_smaller_37190d)); bytes.write(buffer, 0, read); }
                Library imported = BackupCodec.decode(LibraryRepository.decodeUtf8(bytes.toByteArray()));
                runOnUiThread(() -> { if (!isDestroyed()) previewMerge(imported); });
            } catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed()) showError(s(R.string.ui_backup_restore_failed_417945), UiText.error(this, error)); }); }
        });
    }
    private void previewMerge(Library imported) {
        if (!canEdit()) return;
        int words = imported.documents.stream().mapToInt(Document::wordCount).sum();
        LinearLayout options = vertical(); options.setPadding(dp(24), 0, dp(24), 0);
        CheckBox statistics = check(options, s(R.string.ui_restore_daily_words_and_writing_time_too_c4bd42), false);
        options.addView(text(s(R.string.ui_for_each_date_keep_the_larger_value_instead_o_cc385d), 12, muted));
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_preview_backup_merge_fd7d79)).setView(options).setMessage(imported.notebooks.size() + s(R.string.ui_notebooks_0cefdb) + imported.documents.size() + s(R.string.ui_documents_including_trash_total_9e3610) + words + s(R.string.ui_words_history_volumes_tags_and_favorites_are_928eb0))
            .setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_merge_and_restore_bcc7bb), (dialog, which) -> {
                if (!canEdit()) return; int count = library.merge(imported, statistics.isChecked()); changed(); changeFilter(Library.ALL); toast(s(R.string.ui_merged_3d03d2) + count + s(R.string.ui_documents_fb56f5));
            }).show();
    }
    private void localBackups() {
        java.io.File[] files = repository.backups();
        if (files.length == 0) { showError(s(R.string.ui_automatic_backup_ba3dfa), s(R.string.ui_a_previous_library_snapshot_is_kept_when_edit_9ad322)); return; }
        String[] labels = new String[files.length]; for (int i = 0; i < files.length; i++) labels[i] = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, AppLanguage.locale(this)).format(new Date(files[i].lastModified()));
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_local_automatic_backups_600d83)).setItems(labels, (dialog, which) -> io.execute(() -> {
            try { Library imported = LibraryCodec.decode(LibraryRepository.decodeUtf8(RecoverySearch.read(files[which], RecoverySearch.MAX_SNAPSHOT_BYTES))); runOnUiThread(() -> { if (!isDestroyed()) previewMerge(imported); }); }
            catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed()) showError(s(R.string.ui_cannot_read_the_snapshot_508c65), UiText.error(this, error)); }); }
        })).setNegativeButton(s(R.string.ui_close_3fd47e), null).show();
    }
    private static String exportMime(String kind) {
        return switch (kind) {
            case "pdf" -> "application/pdf"; case "png" -> "image/png";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "epub" -> "application/epub+zip"; default -> "text/html";
        };
    }
    private void readDocuments(List<Uri> uris) {
        if (uris.isEmpty() || uris.size() > 1000) { showError(s(R.string.ui_cannot_import_751d82), s(R.string.ui_choose_1_1_000_markdown_txt_files_21f492)); return; }
        String notebook = library.notebooks.contains(filter) ? filter : library.notebooks.get(0);
        io.execute(() -> {
            try {
                List<TextImport.Item> items = new ArrayList<>(); TextImport.Budget budget = new TextImport.Budget();
                for (Uri uri : uris) {
                    String name = null;
                    try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                    }
                    if (!TextImport.isText(name)) throw new java.io.IOException(s(R.string.ui_choose_a_markdown_or_txt_file_cfc8be) + name);
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        items.add(new TextImport.Item(TextImport.title(name), budget.read(input), notebook));
                    }
                }
                runOnUiThread(() -> { if (!isDestroyed()) previewImport(items); });
            } catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed()) showError(s(R.string.ui_import_failed_01aaeb), s(R.string.ui_no_files_were_imported_use_utf_8_text_2023ea) + UiText.error(this, error)); }); }
        });
    }
    private void moveParagraph(int direction) {
        Document doc = current(); if (!editable(doc) || bodyInput == null) return;
        TextTools.Edit edit = TextTools.moveParagraph(doc.body, bodyInput.getSelectionStart(), direction);
        if (edit.text().equals(doc.body)) { toast(s(R.string.ui_the_paragraph_is_at_a_boundary_or_the_cursor_3c8f6c)); return; }
        doc.checkpoint(true); doc.body = edit.text(); doc.touch(); changed(); rememberEditor(); cursorStart = cursorEnd = edit.cursor(); render();
        if (bodyInput != null) bodyInput.requestFocus();
    }
    private void historyDiff(Document.Revision version, Document doc) {
        String before = version.title() + "\n\n" + version.body(), after = doc.title + "\n\n" + doc.body;
        io.execute(() -> {
            RevisionDiff.Result diff = RevisionDiff.compare(before, after);
            runOnUiThread(() -> {
                if (isDestroyed() || locked) return;
                android.text.SpannableStringBuilder display = new android.text.SpannableStringBuilder(); int added = 0, removed = 0;
                for (RevisionDiff.Line entry : diff.lines()) { if (entry.kind() == '+') added++; if (entry.kind() == '-') removed++; }
                display.append(s(R.string.ui_history_current_version_added_abc631) + added + s(R.string.ui_lines_removed_736e4c) + removed + s(R.string.ui_lines_07b447));
                if (diff.grouped()) display.append(s(R.string.ui_large_changes_are_shown_as_whole_blocks_096e86));
                if (added == 0 && removed == 0) display.append(s(R.string.ui_the_two_versions_are_identical_7c476f));
                display.append("\n");
                for (RevisionDiff.Line entry : diff.lines()) {
                    if (display.length() + entry.text().length() > 200_000) { display.append(s(R.string.ui_diff_preview_truncated_restore_a_history_copy_3864b6)); break; }
                    int start = display.length(); display.append(entry.kind() + " " + entry.text());
                    if (!entry.text().endsWith("\n")) display.append(s(R.string.ui_no_newline_2a4559));
                    if (entry.kind() != ' ') display.setSpan(new android.text.style.ForegroundColorSpan(entry.kind() == '+' ? (isDark() ? Color.rgb(135,210,151) : Color.rgb(33,112,54)) : (isDark() ? Color.rgb(250,155,144) : Color.rgb(177,47,40))), start, display.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                TextView view = text("", 14, ink); view.setText(display); view.setTypeface(Typeface.MONOSPACE); view.setTextIsSelectable(true); view.setPadding(dp(20), dp(16), dp(20), dp(16));
                ScrollView scroll = new ScrollView(this); scroll.addView(view);
                new AlertDialog.Builder(this).setTitle(s(R.string.ui_revision_differences_4e1122)).setView(scroll).setPositiveButton(s(R.string.ui_close_3fd47e), null).show();
            });
        });
    }
    private record MatchDocument(Document doc, String original, List<TextTools.Match> matches) {}
    private void selectedReplacement(Pattern pattern, String replacement, int scope, AlertDialog owner) {
        if (!canEdit()) return;
        List<MatchDocument> sources = new ArrayList<>(); List<String> labels = new ArrayList<>();
        for (Document doc : library.documents) {
            if (doc.deletedAt != null || doc.readOnly || scope == 0 && (current() == null || !doc.id.equals(current().id))) continue;
            if (scope == 1 && !doc.notebook.equals(replacementNotebook())) continue;
            List<TextTools.Match> matches = TextTools.matches(doc.body, pattern); if (matches.isEmpty()) continue;
            if (labels.size() + matches.size() > 2000) throw new IllegalArgumentException(s(R.string.ui_select_up_to_2_000_matches_narrow_the_search_9ec360));
            sources.add(new MatchDocument(doc, doc.body, matches)); int index = 0;
            for (TextTools.Match match : matches) {
                int start = Math.max(0, match.start() - 18), end = Math.min(doc.body.length(), match.end() + 18);
                String context = doc.body.substring(start, Math.min(end, start + 120)).replace('\n', ' ');
                labels.add(UiText.title(this, doc.title) + s(R.string.ui_match_567404) + (++index) + s(R.string.ui_matches_8bd554) + context);
            }
        }
        if (labels.isEmpty()) { toast(s(R.string.ui_no_replaceable_text_5485bf)); return; }
        boolean[] selected = new boolean[labels.size()]; java.util.Arrays.fill(selected, true);
        AlertDialog chooser = new AlertDialog.Builder(this).setTitle(s(R.string.ui_select_replacements_2223e4) + labels.size() + s(R.string.ui_matches_af9560))
            .setMultiChoiceItems(labels.toArray(new String[0]), selected, (dialog, which, checked) -> selected[which] = checked)
            .setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setNeutralButton(s(R.string.ui_select_all_clear_baf299), null).setPositiveButton(s(R.string.ui_replace_selected_matches_e11473), null).create();
        chooser.setOnShowListener(d -> {
            chooser.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                boolean all = true; for (boolean value : selected) all &= value;
                for (int i = 0; i < selected.length; i++) { selected[i] = !all; chooser.getListView().setItemChecked(i, !all); }
            });
            chooser.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int count = 0; for (boolean value : selected) if (value) count++;
                if (count == 0) { toast(s(R.string.ui_select_at_least_one_match_401ad2)); return; }
                final int total = count;
                new AlertDialog.Builder(this).setTitle(s(R.string.ui_replace_the_selected_c9a483) + total + s(R.string.ui_matches_f49d39)).setMessage(s(R.string.ui_replacement_text_is_inserted_literally_the_cu_2b23c1))
                    .setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_replace_131b13), (confirm, button) -> {
                        if (!canEdit()) return;
                        for (MatchDocument source : sources) if (source.doc().readOnly || source.doc().deletedAt != null || !source.doc().body.equals(source.original())) { showError(s(R.string.ui_document_has_changed_2e32a0), s(R.string.ui_close_the_preview_and_search_again_nothing_wa_18d49a)); return; }
                        int offset = 0;
                        for (MatchDocument source : sources) {
                            boolean[] picks = java.util.Arrays.copyOfRange(selected, offset, offset + source.matches().size()); offset += picks.length;
                            String next = TextTools.replaceSelected(source.original(), source.matches(), picks, replacement);
                            if (!next.equals(source.original())) { source.doc().checkpoint(true); source.doc().body = next; source.doc().touch(); }
                        }
                        changed(); rememberEditor(); chooser.dismiss(); owner.dismiss(); render(); toast(s(R.string.ui_replaced_1bddf8) + total + s(R.string.ui_matches_af9560));
                    }).show();
            });
        }); chooser.show();
    }
    private void documentStats(Document doc) {
        long book = 0, section = 0;
        for (Document chapter : library.documents) if (chapter.deletedAt == null && chapter.notebook.equals(doc.notebook)) {
            book += chapter.wordCount(); if (chapter.section.equals(doc.section)) section += chapter.wordCount();
        }
        long nonspace = WritingMetrics.Mode.NONSPACE.count(doc.body);
        long han = WritingMetrics.Mode.HAN.count(doc.body);
        String message = s(R.string.ui_mixed_word_count_4c0538) + doc.wordCount() + s(R.string.ui_characters_including_whitespace_26e1ac) + doc.body.codePointCount(0, doc.body.length()) + s(R.string.ui_characters_excluding_whitespace_055786) + nonspace + s(R.string.ui_han_characters_70f677) + han
            + s(R.string.ui_current_notebook_ae82fb) + book + s(R.string.ui_words_66b078) + (doc.section.isEmpty() ? s(R.string.ui_documents_without_a_volume_3c4c67) : s(R.string.ui_current_volume_c17789) + doc.section + "」") + "：" + section + s(R.string.ui_words_excluding_trash_3dfc4f)
            + s(R.string.ui_estimated_reading_time_a5e718) + Math.max(1, (int) Math.ceil(doc.wordCount() / 300.0)) + s(R.string.ui_minutes_created_cc9304) + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, AppLanguage.locale(this)).format(new Date(doc.createdAt));
        new AlertDialog.Builder(this).setTitle(s(R.string.ui_document_statistics_92f22f)).setMessage(message).setPositiveButton(s(R.string.ui_done_c0b3fb), null).show();
    }

    private void showError(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(s(R.string.ui_ok_de32e2), null).show(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout horizontal() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private TextView text(String value, float size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setIncludeFontPadding(false); return view; }
    private EditText edit(String hint, float size) {
        EditText view = new EditText(this); view.setHint(hint); view.setTextSize(size); view.setTextColor(ink); view.setHintTextColor(muted);
        view.setBackgroundColor(Color.TRANSPARENT); view.setPadding(0, dp(4), 0, dp(4)); view.setSelectAllOnFocus(false); return view;
    }
    private Button quiet(String value, Runnable action) {
        Button button = new Button(this); button.setText(value); button.setTextSize(14); button.setTextColor(ink); button.setAllCaps(false);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48)); button.setPadding(dp(12), 0, dp(12), 0); button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(v -> action.run()); return button;
    }
    private Button primary(String value, Runnable action) { Button button = quiet(value, action); button.setTextColor(WritingAppearance.foregroundOn(accent)); button.setBackground(background(accent, 8)); button.setEnabled(loaded && repository.isWritable()); return button; }
    private View icon(String icon, String label, Runnable action) { IconView view = new IconView(this, icon, label, accent); view.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48))); view.setOnClickListener(v -> action.run()); return view; }
    private GradientDrawable background(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private void addSpace(LinearLayout panel, int height) { panel.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void dividerHorizontal(LinearLayout panel) { View divider = new View(this); divider.setBackgroundColor(line); panel.addView(divider, new LinearLayout.LayoutParams(-1, dp(1))); }
    private void dividerVertical(LinearLayout panel) { View divider = new View(this); divider.setBackgroundColor(line); panel.addView(divider, new LinearLayout.LayoutParams(dp(1), -1)); }
    private TextWatcher watcher(Runnable action) { return new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {} public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable value) { if (!rebuilding) action.run(); }
    }; }

    private String s(int id, Object... args) { return args.length == 0 ? this.getString(id) : this.getString(id, args); }
}
