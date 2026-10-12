package com.jianzuo.writing;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Owns one cancellable search; dismissed dialogs cannot deliver or restore stale results. */
final class RecoveryDialog {
    private final Activity host;
    private final LibraryRepository repository;
    private final Supplier<List<RecoverySearch.Candidate>> current;
    private final Consumer<RecoverySearch.Candidate> restore;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AlertDialog dialog, detail;
    private Future<?> task;
    private int generation;
    private boolean closed;
    private TextView status;
    private LinearLayout results;
    RecoveryDialog(Activity host, LibraryRepository repository, Supplier<List<RecoverySearch.Candidate>> current, Consumer<RecoverySearch.Candidate> restore) {
        this.host = host; this.repository = repository; this.current = current; this.restore = restore;
    }
    void show() {
        LinearLayout panel = column(); panel.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView hint = text(s(R.string.ui_search_documents_trash_revision_history_and_t_d8650b)); panel.addView(hint);
        EditText query = new EditText(host); query.setSingleLine(true); query.setHint(s(R.string.ui_keyword_in_title_or_text_db30e9)); query.setContentDescription(s(R.string.ui_recovery_keyword_8ca354)); panel.addView(query);
        Button search = new Button(host); search.setText(s(R.string.ui_search_content_b445ac)); panel.addView(search);
        status = text(s(R.string.ui_enter_a_remembered_fragment_to_start_searchin_c98c59)); status.setContentDescription(s(R.string.ui_recovery_search_status_407504)); status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE); panel.addView(status);
        results = column(); ScrollView scroll = new ScrollView(host); scroll.addView(results);
        int height = Math.min(dp(300), Math.max(dp(120), Math.round(host.getResources().getDisplayMetrics().heightPixels * .34f)));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, height));
        dialog = new AlertDialog.Builder(host).setTitle(s(R.string.ui_find_lost_content_30a702)).setView(panel).setNegativeButton(s(R.string.ui_close_3fd47e), null).create();
        dialog.setOnDismissListener(ignored -> close()); search.setOnClickListener(view -> search(query.getText().toString()));
        query.setOnEditorActionListener((view, action, event) -> { search(query.getText().toString()); return true; }); dialog.show();
    }
    private void search(String query) {
        if (query.trim().isEmpty()) { status.setText(s(R.string.ui_enter_a_keyword_from_the_title_or_text_e6180e)); return; }
        if (task != null) task.cancel(true);
        int token = ++generation; List<RecoverySearch.Candidate> values = current.get(); java.io.File[] files = repository.backups();
        results.removeAllViews(); status.setText(s(R.string.ui_searching_close_this_window_to_cancel_6d2850));
        task = worker.submit(() -> {
            try {
                RecoverySearch.Result found = RecoverySearch.search(query, values, files);
                handler.post(() -> {
                    if (closed || token != generation || host.isDestroyed() || host.isFinishing()) return;
                    status.setText(host.getString(R.string.recovery_summary, found.hits().isEmpty() ? s(R.string.ui_no_matching_content_8f8d97) : host.getString(R.string.recovery_found, found.hits().size()), found.snapshots(),
                            found.truncated() ? s(R.string.ui_showing_up_to_200_versions_narrow_your_keywor_617ce4) : "",
                            found.skipped() > 0 ? host.getString(R.string.recovery_skipped, found.skipped()) : ""));
                    for (RecoverySearch.Hit hit : found.hits()) {
                        Button row = new Button(host); row.setAllCaps(false); row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL); row.setMaxLines(6);
                        RecoverySearch.Candidate value = hit.candidate();
                        row.setText(host.getString(R.string.recovery_result, UiText.title(host, value.title()), UiText.source(host, value.source()), value.notebook(), DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, AppLanguage.locale(host)).format(new Date(value.time())), hit.excerpt()));
                        row.setOnClickListener(view -> preview(value)); results.addView(row, new LinearLayout.LayoutParams(-1, -2));
                    }
                });
            } catch (Exception error) {
                handler.post(() -> { if (!closed && token == generation && !host.isDestroyed()) status.setText(host.getString(R.string.recovery_failed, UiText.error(host, error))); });
            }
        });
    }
    private void preview(RecoverySearch.Candidate value) {
        TextView content = text(UiText.title(host, value.title()) + "\n" + UiText.source(host, value.source()) + " · " + value.notebook() + "\n\n" + value.body());
        content.setTextIsSelectable(true); content.setPadding(dp(20), dp(12), dp(20), dp(12));
        ScrollView scroll = new ScrollView(host); scroll.addView(content);
        detail = new AlertDialog.Builder(host).setTitle(s(R.string.ui_recovered_content_preview_7f50d7)).setView(scroll).setNegativeButton(s(R.string.ui_back_572cf4), null)
                .setPositiveButton(s(R.string.ui_save_as_a_new_document_92d6f5), (ignored, which) -> { if (!closed) { restore.accept(value); close(); } }).show();
    }
    void close() {
        if (closed) return; closed = true; generation++;
        if (task != null) task.cancel(true); worker.shutdownNow(); handler.removeCallbacksAndMessages(null);
        if (detail != null) detail.dismiss(); if (dialog != null) dialog.dismiss();
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(host); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String value) { TextView text = new TextView(host); text.setText(value); text.setTextSize(14); return text; }
    private int dp(float value) { return Math.round(value * host.getResources().getDisplayMetrics().density); }

    private String s(int id, Object... args) { return args.length == 0 ? host.getString(id) : host.getString(id, args); }
}
