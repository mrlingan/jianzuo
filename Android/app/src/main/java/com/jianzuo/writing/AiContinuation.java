package com.jianzuo.writing;

import org.json.JSONObject;

/** Immutable insertion anchor: generated text can never overwrite edits made meanwhile. */
final class AiContinuation {
    final String id, title, body;
    final int cursor;
    private boolean applied;
    AiContinuation(Document doc, int position) {
        id = doc.id; title = doc.title; body = doc.body;
        cursor = boundary(body, Math.max(0, Math.min(position, body.length())));
    }
    static int boundary(String text, int position) {
        if (position > 0 && position < text.length() && Character.isHighSurrogate(text.charAt(position - 1)) && Character.isLowSurrogate(text.charAt(position))) return position - 1;
        return position;
    }
    JSONObject context(String instruction, int length) throws Exception {
        int start = boundary(body, Math.max(0, cursor - 12000));
        int end = boundary(body, Math.min(body.length(), cursor + 2000));
        return new JSONObject().put("title", title.substring(0, boundary(title, Math.min(300, title.length()))))
            .put("before_cursor", body.substring(start, cursor)).put("after_cursor", body.substring(cursor, end))
            .put("earlier_text_omitted", start > 0).put("later_text_omitted", end < body.length())
            .put("direction", instruction).put("target_length", length);
    }
    String previewText(String generated) {
        // Models sometimes omit paragraph separators even when prompted. Correct only clear sentence boundaries;
        // the user can edit the preview, and apply() still inserts exactly what the preview contains.
        if (generated.isEmpty()) return generated;
        if (cursor > 0 && !Character.isWhitespace(body.charAt(cursor - 1)) && !Character.isWhitespace(generated.charAt(0)) &&
                ".!?。！？".indexOf(body.charAt(cursor - 1)) >= 0) generated = "\n\n" + generated;
        if (cursor < body.length() && !Character.isWhitespace(body.charAt(cursor)) && !Character.isWhitespace(generated.charAt(generated.length() - 1)) &&
                ".!?".indexOf(generated.charAt(generated.length() - 1)) >= 0 && Character.UnicodeScript.of(body.codePointAt(cursor)) == Character.UnicodeScript.LATIN) generated += " ";
        return generated;
    }
    boolean matches(Document doc) {
        return !applied && doc != null && id.equals(doc.id) && title.equals(doc.title) && body.equals(doc.body) && !doc.readOnly && doc.deletedAt == null;
    }
    boolean apply(Document doc, String generated) {
        if (!matches(doc) || generated == null || generated.trim().isEmpty()) return false;
        doc.checkpoint(true); doc.body = body.substring(0, cursor) + generated + body.substring(cursor); doc.touch(); applied = true; return true;
    }
}
