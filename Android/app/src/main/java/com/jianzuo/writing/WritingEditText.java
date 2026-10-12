package com.jianzuo.writing;

import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

/** Composition is delegated unchanged to the IME; pairing happens only on committed punctuation. */
final class WritingEditText extends EditText {
    final PaperLines paperLines = new PaperLines();
    void configurePaper(boolean ruled, int color) { paperLines.configure(this, ruled, color); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); paperLines.attach(this); }
    @Override protected void onDetachedFromWindow() { paperLines.detach(); super.onDetachedFromWindow(); }
    @Override protected void onDraw(android.graphics.Canvas canvas) { paperLines.draw(canvas, this); super.onDraw(canvas); }
    private boolean pairs, indent, typewriter, highlighting, deleteIndent, commaOnNewline;
    private ForegroundColorSpan beforeColor, afterColor;
    private Runnable selectionChanged;
    void onSelectionChanged(Runnable listener) { selectionChanged = listener; }
    WritingEditText(Context context) { super(context); }
    void configureEditing(boolean deleteIndent, boolean commaOnNewline) { this.deleteIndent = deleteIndent; this.commaOnNewline = commaOnNewline; }
    void configure(boolean pairs, boolean indent, boolean typewriter, int faded) {
        this.pairs = pairs; this.indent = indent; this.typewriter = typewriter;
        beforeColor = new ForegroundColorSpan(faded); afterColor = new ForegroundColorSpan(faded); highlight();
    }
    @Override protected void onSelectionChanged(int start, int end) { super.onSelectionChanged(start, end); highlight(); if (selectionChanged != null) selectionChanged.run(); }
    @Override protected void onTextChanged(CharSequence text, int start, int before, int count) { super.onTextChanged(text, start, before, count); highlight(); }
    private void highlight() {
        if (highlighting || !typewriter || beforeColor == null || getText() == null) return;
        highlighting = true;
        try {
            Editable text = getText(); text.removeSpan(beforeColor); text.removeSpan(afterColor);
            int cursor = Math.max(0, Math.min(getSelectionStart(), text.length())); String source = text.toString();
            int start = source.lastIndexOf('\n', Math.max(-1, cursor - 1)) + 1, end = source.indexOf('\n', cursor);
            if (end < 0) end = text.length();
            if (start > 0) text.setSpan(beforeColor, 0, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (end < text.length()) text.setSpan(afterColor, end, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } finally { highlighting = false; }
    }
    private String closing(String text) {
        return switch (text) { case "“" -> "”"; case "‘" -> "’"; case "（" -> "）"; case "(" -> ")"; case "《" -> "》"; case "[" -> "]"; case "【" -> "】"; case "{" -> "}"; default -> ""; };
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection target = super.onCreateInputConnection(info); if (target == null) return null;
        return new InputConnectionWrapper(target, false) {
            @Override public boolean commitText(CharSequence text, int cursorPosition) {
                String value = text.toString(), close = closing(value);
                int cursor = getSelectionStart();
                if (pairs && !close.isEmpty() && cursor == getText().length() && cursor == getSelectionEnd()) {
                    boolean result = super.commitText(value + close, cursorPosition); WritingEditText.this.setSelection(Math.min(cursor + value.length(), getText().length())); return result;
                }
                if ((indent || commaOnNewline) && value.equals("\n") && cursor >= 0 && cursor == getSelectionEnd() && !composing()) {
                    String source = getText().toString(); int start = source.lastIndexOf('\n', Math.max(-1, cursor - 1)) + 1, end = start;
                    while (end < cursor && (source.charAt(end) == '　' || source.charAt(end) == ' ' || source.charAt(end) == '\t')) end++;
                    beginBatchEdit();
                    try {
                        if (commaOnNewline && cursor > 0 && (source.charAt(cursor - 1) == '，' || source.charAt(cursor - 1) == ',')) getText().replace(cursor - 1, cursor, source.charAt(cursor - 1) == '，' ? "。" : ".");
                        return super.commitText("\n" + (indent ? source.substring(start, end) : ""), cursorPosition);
                    } finally { endBatchEdit(); }
                }
                return super.commitText(text, cursorPosition);
            }
            private boolean emptyPair() {
                int cursor = getSelectionStart(); Editable text = getText();
                return !composing() && pairs && cursor > 0 && cursor == getSelectionEnd() && cursor < text.length() && closing(text.subSequence(cursor - 1, cursor).toString()).equals(text.subSequence(cursor, cursor + 1).toString());
            }
            private int indentation() {
                int cursor = getSelectionStart(); if (!deleteIndent || composing() || cursor <= 0 || cursor != getSelectionEnd()) return 0;
                String source = getText().toString(); int start = source.lastIndexOf('\n', cursor - 1) + 1;
                for (int i = start; i < cursor; i++) if (source.charAt(i) != ' ' && source.charAt(i) != '　' && source.charAt(i) != '\t') return 0;
                return cursor - start;
            }
            @Override public boolean deleteSurroundingText(int before, int after) {
                if (before == 1 && after == 0 && indentation() > 0) return super.deleteSurroundingText(indentation(), 0);
                return emptyPair() && before == 1 && after == 0 ? super.deleteSurroundingText(1, 1) : super.deleteSurroundingText(before, after);
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
                if (before == 1 && after == 0 && indentation() > 0) return super.deleteSurroundingTextInCodePoints(indentation(), 0);
                return emptyPair() && before == 1 && after == 0 ? super.deleteSurroundingTextInCodePoints(1, 1) : super.deleteSurroundingTextInCodePoints(before, after);
            }
        };
    }
    private boolean composing() { return android.view.inputmethod.BaseInputConnection.getComposingSpanStart(getText()) >= 0; }
}
