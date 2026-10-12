package com.jianzuo.writing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Owns request lifetime; only an explicit Insert action is allowed to change a manuscript. */
final class AiDialogs {
    interface Apply { boolean insert(AiContinuation draft, String text); }
    private final Activity activity;
    private final AiKeyStore store;
    private final Supplier<DeepSeekClient> clients;
    private final Apply apply;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AlertDialog dialog;
    private DeepSeekClient client;
    private Future<?> task;
    private int generation;
    AiDialogs(Activity activity, Apply apply) { this(activity, new AiKeyStore(activity), DeepSeekClient::new, apply); }
    AiDialogs(Activity activity, AiKeyStore store, Supplier<DeepSeekClient> clients, Apply apply) {
        this.activity = activity; this.store = store; this.clients = clients; this.apply = apply;
    }
    private String s(int resource, Object... args) { return activity.getString(resource, args); }
    private int dp(int value) { return Math.round(activity.getResources().getDisplayMetrics().density * value); }
    private LinearLayout panel() { LinearLayout p = new LinearLayout(activity); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(24), dp(12), dp(24), dp(12)); return p; }
    private TextView label(LinearLayout panel, int resource) {
        TextView label = new TextView(activity); label.setText(s(resource)); label.setPadding(0, dp(8), 0, dp(8)); panel.addView(label); return label;
    }
    private EditText edit(LinearLayout panel, int resource, int limit) {
        EditText field = new EditText(activity); field.setHint(s(resource)); field.setContentDescription(s(resource)); field.setSaveEnabled(false);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)}); panel.addView(field); return field;
    }
    private Button button(LinearLayout panel, int resource, Runnable action) {
        Button button = new Button(activity); button.setText(s(resource)); button.setAllCaps(false); panel.addView(button); button.setOnClickListener(v -> action.run()); return button;
    }
    private AlertDialog show(int title, LinearLayout content) {
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        AlertDialog shown = new AlertDialog.Builder(activity).setTitle(s(title)).setView(scroll).setNegativeButton(s(R.string.ai_close), null).create();
        dialog = shown; shown.setOnDismissListener(d -> { if (dialog == shown) { cancel(); dialog = null; } });
        shown.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        shown.show(); return shown;
    }
    private void cancel() { generation++; if (client != null) client.cancel(); if (task != null) task.cancel(true); task = null; client = null; }
    void close() { cancel(); AlertDialog previous = dialog; dialog = null; if (previous != null && previous.isShowing() && previous.getWindow().getDecorView().isAttachedToWindow()) previous.dismiss(); }
    void destroy() { close(); executor.shutdownNow(); }
    private interface Work<T> { T run(DeepSeekClient client) throws Exception; }
    private <T> void request(Work<T> work, Consumer<T> success, Consumer<Exception> failure) {
        cancel(); int token = generation; DeepSeekClient requestClient = clients.get(); client = requestClient;
        task = executor.submit(() -> {
            try { T result = work.run(requestClient); handler.post(() -> { if (token == generation && dialog != null && !activity.isDestroyed()) { task = null; client = null; success.accept(result); } }); }
            catch (Exception error) { handler.post(() -> { if (token == generation && dialog != null && !activity.isDestroyed()) { task = null; client = null; failure.accept(error); } }); }
        });
    }
    private String error(Exception exception) {
        if (!(exception instanceof DeepSeekClient.Error e)) return s(R.string.ai_storage_error);
        return s(switch (e.failure) {
            case KEY -> R.string.ai_key_error; case BALANCE -> R.string.ai_balance_error; case RATE -> R.string.ai_rate_error;
            case NETWORK -> R.string.ai_network_error; case TIMEOUT -> R.string.ai_timeout_error; case CANCELLED -> R.string.ai_cancelled;
            case INPUT -> R.string.ai_input_error; case SERVER -> R.string.ai_server_error; case RESPONSE -> R.string.ai_response_error;
        });
    }
    void settings() { settings(null); }
    private void settings(Runnable saved) {
        close(); LinearLayout panel = panel(); label(panel, R.string.ai_provider); label(panel, R.string.ai_key_privacy);
        EditText key = edit(panel, R.string.ai_api_key, 220); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if (store.configured()) key.setHint(s(R.string.ai_key_saved));
        EditText model = edit(panel, R.string.ai_model, 100); model.setSingleLine(); model.setText(store.model());
        TextView state = label(panel, R.string.ai_test_hint); state.setContentDescription(s(R.string.ai_status));
        Button test = button(panel, R.string.ai_test, () -> {});
        Button save = button(panel, R.string.ai_save, () -> {});
        Button clear = button(panel, R.string.ai_clear_key, () -> {}); clear.setEnabled(store.configured());
        AlertDialog shown = show(R.string.ai_settings, panel); shown.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Runnable idle = () -> { test.setEnabled(true); save.setEnabled(true); clear.setEnabled(store.configured()); key.setEnabled(true); model.setEnabled(true); };
        test.setOnClickListener(v -> {
            try {
                String value = key.getText().toString().trim().isEmpty() ? store.read() : key.getText().toString(); DeepSeekClient.key(value);
                test.setEnabled(false); save.setEnabled(false); clear.setEnabled(false); key.setEnabled(false); model.setEnabled(false); state.setText(s(R.string.ai_testing));
                request(c -> c.models(value), models -> { idle.run(); state.setText(s(R.string.ai_test_ok, String.join(", ", models))); }, e -> { idle.run(); state.setText(error(e)); });
            } catch (Exception e) { state.setText(error(e)); }
        });
        save.setOnClickListener(v -> {
            try {
                String name = model.getText().toString().trim(); if (!name.matches("deepseek-[A-Za-z0-9_-]{1,80}")) throw new DeepSeekClient.Error(DeepSeekClient.Failure.INPUT);
                String value = key.getText().toString().trim().isEmpty() ? store.read() : key.getText().toString(); store.save(value, name);
                key.setText(""); shown.dismiss(); if (saved != null) saved.run();
            } catch (Exception e) { state.setText(error(e)); }
        });
        clear.setOnClickListener(v -> { try { store.clear(); key.setText(""); key.setHint(s(R.string.ai_api_key)); clear.setEnabled(false); state.setText(s(R.string.ai_key_cleared)); } catch (Exception e) { state.setText(error(e)); } });
        shown.setOnDismissListener(d -> { key.setText(""); if (dialog == shown) { cancel(); dialog = null; } });
    }
    void continuation(Document document, int cursor) {
        AiContinuation draft = new AiContinuation(document, cursor);
        if (!store.configured()) { settings(() -> continuation(document, cursor)); return; }
        close(); LinearLayout panel = panel(); label(panel, R.string.ai_context_notice);
        EditText direction = edit(panel, R.string.ai_direction, 2000); direction.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); direction.setMinLines(2);
        Spinner length = new Spinner(activity); length.setContentDescription(s(R.string.ai_length));
        length.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, activity.getResources().getStringArray(R.array.ai_lengths))); panel.addView(length);
        TextView state = label(panel, R.string.ai_ready); state.setContentDescription(s(R.string.ai_status));
        Button generate = button(panel, R.string.ai_generate, () -> {}); Button stop = button(panel, R.string.ai_cancel_request, () -> {}); stop.setEnabled(false);
        EditText result = edit(panel, R.string.ai_result, 50000); result.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); result.setMinLines(5); result.setGravity(android.view.Gravity.TOP); result.setEnabled(false);
        Button insert = button(panel, R.string.ai_insert, () -> {}); insert.setEnabled(false);
        Button copy = button(panel, R.string.ai_copy, () -> {}); copy.setEnabled(false);
        show(R.string.ai_continue, panel);
        Runnable idle = () -> { generate.setEnabled(true); stop.setEnabled(false); direction.setEnabled(true); length.setEnabled(true); };
        generate.setOnClickListener(v -> {
            try {
                if (!draft.matches(document)) { state.setText(s(R.string.ai_conflict)); return; }
                String key = store.read(), model = store.model(), hint = direction.getText().toString(); int size = new int[]{300,800,1500}[length.getSelectedItemPosition()];
                DeepSeekClient.payload(draft, model, hint, size); DeepSeekClient.key(key);
                generate.setEnabled(false); stop.setEnabled(true); direction.setEnabled(false); length.setEnabled(false); result.setText(""); result.setEnabled(false); insert.setEnabled(false); copy.setEnabled(false); state.setText(s(R.string.ai_generating));
                request(c -> c.continueText(key, model, draft, hint, size), response -> {
                    idle.run(); result.setText(response.text()); result.setEnabled(true); insert.setEnabled(true); copy.setEnabled(true);
                    state.setText(s(response.truncated() ? R.string.ai_truncated : R.string.ai_generated, response.inputTokens(), response.outputTokens()));
                }, e -> { idle.run(); state.setText(error(e)); });
            } catch (Exception e) { state.setText(error(e)); }
        });
        stop.setOnClickListener(v -> { cancel(); idle.run(); state.setText(s(R.string.ai_cancelled)); });
        insert.setOnClickListener(v -> {
            String value = result.getText().toString();
            if (value.trim().isEmpty()) { state.setText(s(R.string.ai_response_error)); return; }
            if (apply.insert(draft, value)) close(); else state.setText(s(R.string.ai_conflict));
        });
        copy.setOnClickListener(v -> { ((ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(s(R.string.ai_result), result.getText().toString())); state.setText(s(R.string.ai_copied)); });
    }
}
