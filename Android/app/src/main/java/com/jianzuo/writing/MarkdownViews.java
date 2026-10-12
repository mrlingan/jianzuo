package com.jianzuo.writing;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownViews {
    private static final Pattern INLINE = Pattern.compile("`([^`\\n]+)`|\\*\\*(.+?)\\*\\*|__(.+?)__|\\*([^*\\n]+)\\*|_([^_\\n]+)_|(?<![!\\\\])\\[([^]\\n]+)\\]\\((https?://[^\\s)]+|mailto:[^\\s)]+)\\)");
    static CharSequence inline(String source) {
        return inline(source, false, 0);
    }
    static CharSequence printInline(String source) {
        return inline(source, true, 0);
    }
    private static CharSequence inline(String source, boolean print, int depth) {
        if (depth >= 8) return source;
        SpannableStringBuilder result = new SpannableStringBuilder();
        Matcher matcher = INLINE.matcher(source); int position = 0;
        while (matcher.find()) {
            result.append(source, position, matcher.start()); int start = result.length();
            if (matcher.group(1) != null) {
                result.append(matcher.group(1)); span(result, new TypefaceSpan("monospace"), start);
            } else if (matcher.group(6) != null) {
                result.append(inline(matcher.group(6), print, depth + 1)); span(result, new URLSpan(matcher.group(7)), start);
                if (print && !matcher.group(6).equals(matcher.group(7))) result.append(" (").append(matcher.group(7)).append(")");
            } else {
                String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3) != null ? matcher.group(3)
                    : matcher.group(4) != null ? matcher.group(4) : matcher.group(5);
                result.append(inline(value, print, depth + 1));
                span(result, new StyleSpan(matcher.group(2) != null || matcher.group(3) != null ? Typeface.BOLD : Typeface.ITALIC), start);
            }
            position = matcher.end();
        }
        result.append(source, position, source.length()); return result;
    }
    private static void span(SpannableStringBuilder result, Object span, int start) {
        result.setSpan(span, start, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
