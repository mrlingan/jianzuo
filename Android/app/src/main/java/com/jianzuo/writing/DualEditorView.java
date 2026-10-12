package com.jianzuo.writing;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Two live document views; all writing mutations remain owned by the Activity. */
@android.annotation.SuppressLint("ViewConstructor") // Created with live documents and callbacks, never inflated from XML.
final class DualEditorView extends LinearLayout {
    record Style(int ink, int muted, int accent, int paper, int sidebar, int line, Typeface font,
                 int fontSize, int spacing, boolean pairs, boolean indent, boolean typewriter,
                 boolean deleteIndent, boolean commaOnNewline, boolean writable, boolean ruledPaper) {}
    interface Listener {
        void activated(Pane pane);
        void titleChanged(Document document, String title);
        void bodyChanged(Document document, String body);
        void selectionChanged();
        void choose(boolean secondary);
        void preview(boolean secondary);
        void markdown(LinearLayout target, String body);
    }
    final Pane primary, secondary;
    private final Style style;
    private final Listener listener;
    private final SharedPreferences preferences;
    private boolean linked, synchronizing, restoring = true;
    private Pane active;

    DualEditorView(Context context, Document first, Document second, boolean firstPreview, boolean secondPreview,
                   boolean wide, int ratio, Style style, SharedPreferences preferences, Listener listener) {
        super(context); this.style = style; this.listener = listener; this.preferences = preferences;
        setOrientation(wide ? HORIZONTAL : VERTICAL); setContentDescription(wide ? s(R.string.ui_side_by_side_dual_editor_14e68a) : s(R.string.ui_stacked_dual_editor_53852d));
        primary = new Pane(first, false, firstPreview); secondary = new Pane(second, true, secondPreview);
        addView(primary.container, new LayoutParams(wide ? 0 : -1, wide ? -1 : 0, ratio));
        View divider = new View(context); divider.setBackgroundColor(style.line());
        addView(divider, new LayoutParams(wide ? dp(1) : -1, wide ? -1 : dp(1)));
        addView(secondary.container, new LayoutParams(wide ? 0 : -1, wide ? -1 : 0, 100 - ratio));
        post(() -> {
            primary.restorePosition(); secondary.restorePosition(); restoring = false;
        });
    }
    void activate(boolean second) {
        active = second ? secondary : primary;
        primary.badge.setTextColor(second ? style.muted() : style.accent());
        secondary.badge.setTextColor(second ? style.accent() : style.muted());
        primary.container.setBackgroundColor(second ? style.paper() : style.sidebar());
        secondary.container.setBackgroundColor(second ? style.sidebar() : style.paper());
        listener.activated(active);
    }
    void linked(boolean enabled) { linked = enabled; if (enabled && !restoring && active != null) synchronize(active); }
    void remember() {
        SharedPreferences.Editor editor = preferences.edit(); primary.remember(editor); secondary.remember(editor); editor.apply();
    }
    private void synchronize(Pane source) {
        if (synchronizing || restoring) return;
        Pane target = source == primary ? secondary : primary;
        int sourceRange = source.range(), targetRange = target.range();
        synchronizing = true;
        try { target.scroll.scrollTo(0, sourceRange == 0 ? 0 : (int) Math.round((double) source.scroll.getScrollY() / sourceRange * targetRange)); }
        finally { synchronizing = false; }
    }
    final class Pane {
        final Document document;
        final boolean second;
        final LinearLayout container;
        final ScrollView scroll;
        final Button badge;
        EditText title, body;
        Pane(Document document, boolean second, boolean preview) {
            this.document = document; this.second = second;
            String label = second ? s(R.string.secondary_draft) : s(R.string.primary_draft);
            container = new LinearLayout(getContext()); container.setOrientation(VERTICAL);
            LinearLayout toolbar = new LinearLayout(getContext()); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(8), 0, dp(8), 0);
            badge = new Button(getContext()); badge.setText(second ? R.string.secondary_draft : R.string.primary_draft);
            badge.setTextSize(13); badge.setAllCaps(false); badge.setContentDescription(s(R.string.pane_activate, label));
            badge.setBackgroundColor(Color.TRANSPARENT); badge.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); badge.setPadding(dp(8), 0, dp(8), 0);
            badge.setOnClickListener(v -> { activate(second); if (body != null) body.requestFocus(); });
            toolbar.addView(badge, new LayoutParams(0, dp(48), 1));
            IconView choose = new IconView(getContext(), "more", s(R.string.pane_choose, label), style.accent());
            choose.setOnClickListener(v -> { activate(second); listener.choose(second); }); toolbar.addView(choose, new LayoutParams(dp(48), dp(48)));
            IconView mode = new IconView(getContext(), preview ? "edit" : "preview", s(preview ? R.string.pane_edit : R.string.pane_preview, label), style.accent());
            mode.setOnClickListener(v -> { activate(second); listener.preview(second); }); toolbar.addView(mode, new LayoutParams(dp(48), dp(48)));
            container.addView(toolbar);
            scroll = new ScrollView(getContext()) {
                @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) activate(second);
                    return super.onInterceptTouchEvent(event);
                }
            }; scroll.setFillViewport(true); scroll.setContentDescription(s(R.string.pane_scroll, label));
            LinearLayout writing = new LinearLayout(getContext()); writing.setOrientation(VERTICAL); writing.setPadding(dp(24), dp(16), dp(24), dp(24)); scroll.addView(writing);
            container.addView(scroll, new LayoutParams(-1, 0, 1));
            boolean editable = !preview && !document.readOnly && document.deletedAt == null && style.writable();
            if (editable) {
                title = new EditText(getContext()); configure(title, 24); title.setTypeface(Typeface.SERIF, Typeface.BOLD); title.setHint(s(R.string.ui_untitled_document_5c7cd4)); title.setText(document.title); title.setContentDescription(s(R.string.pane_title, label));
                writing.addView(title, new LayoutParams(-1, -2));
                WritingEditText input = new WritingEditText(getContext()); body = input; configure(input, style.fontSize()); input.setTypeface(style.font()); input.setHint(s(R.string.ui_write_your_first_sentence_here_8ec108));
                input.configure(style.pairs(), style.indent(), style.typewriter(), style.muted()); input.configureEditing(style.deleteIndent(), style.commaOnNewline());
                input.configurePaper(style.ruledPaper(), style.line());
                input.setMinLines(8); input.setLineSpacing(dp(style.spacing()), 1); input.setText(document.body); input.setContentDescription(s(R.string.pane_body, label));
                writing.addView(input, new LayoutParams(-1, -2));
                input.onSelectionChanged(() -> { if (active == this) listener.selectionChanged(); });
                title.setOnFocusChangeListener((v, focused) -> { if (focused) activate(second); });
                body.setOnFocusChangeListener((v, focused) -> { if (focused) activate(second); });
                title.addTextChangedListener(watch(() -> listener.titleChanged(document, title.getText().toString())));
                body.addTextChangedListener(watch(() -> listener.bodyChanged(document, body.getText().toString())));
            } else {
                TextView heading = new TextView(getContext()); heading.setText(UiText.title(getContext(), document.title)); heading.setTextColor(style.ink()); heading.setTextSize(24); heading.setTypeface(Typeface.SERIF, Typeface.BOLD); heading.setTextIsSelectable(true);
                heading.setContentDescription(s(R.string.pane_readonly_title, label)); heading.setOnFocusChangeListener((v, focused) -> { if (focused) activate(second); }); writing.addView(heading);
                if (preview) listener.markdown(writing, document.body);
                else {
                    PaperTextView text = new PaperTextView(getContext()); text.configurePaper(style.ruledPaper(), style.line()); text.setText(document.body); text.setTextColor(style.ink()); text.setTextSize(style.fontSize()); text.setTypeface(style.font());
                    text.setLineSpacing(dp(style.spacing()), 1); text.setPadding(0, dp(16), 0, 0); text.setTextIsSelectable(true); text.setContentDescription(s(R.string.pane_readonly_body, label));
                    text.setOnFocusChangeListener((v, focused) -> { if (focused) activate(second); }); writing.addView(text);
                }
            }
            scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
                if (synchronizing || restoring) return;
                if (linked && active == this) synchronize(this);
            });
        }
        private int range() { return Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight()); }
        private void remember(SharedPreferences.Editor editor) {
            editor.putInt("scroll:" + document.id, scroll.getScrollY());
            if (body != null) editor.putInt("cursor:" + document.id, body.getSelectionStart()).putInt("cursorEnd:" + document.id, body.getSelectionEnd());
        }
        private void restorePosition() {
            if (body != null && !body.hasFocus()) {
                int start = Math.max(0, Math.min(body.length(), preferences.getInt("cursor:" + document.id, 0)));
                int end = Math.max(0, Math.min(body.length(), preferences.getInt("cursorEnd:" + document.id, start))); body.setSelection(start, end);
            }
            scroll.scrollTo(0, preferences.getInt("scroll:" + document.id, 0));
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void configure(EditText input, int size) {
        input.setTextSize(size); input.setTextColor(style.ink()); input.setHintTextColor(style.muted()); input.setGravity(Gravity.TOP | Gravity.START); input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, dp(8), 0, dp(8)); input.setHorizontallyScrolling(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }
    private TextWatcher watch(Runnable changed) { return new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) { changed.run(); }
    }; }

    private String s(int id, Object... args) { return args.length == 0 ? getContext().getString(id) : getContext().getString(id, args); }
}
