package com.jianzuo.writing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WritingMetrics {
    enum Mode {
        MIXED("字", "混合字数（汉字逐字，英文按词）"), CHARACTERS("字符", "字符数（含空白与标点）"),
        NONSPACE("字符（不含空白）", "字符数（不含空白，保留标点）"), HAN("汉字", "汉字数（仅汉字）");
        final String unit, description;
        Mode(String unit, String description) { this.unit = unit; this.description = description; }
        static Mode at(int index) { return index >= 0 && index < values().length ? values()[index] : MIXED; }
        int count(String text) {
            return switch (this) {
                case MIXED -> WritingMetrics.count(text);
                case CHARACTERS -> text.codePointCount(0, text.length());
                case NONSPACE -> (int) text.codePoints().filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)).count();
                case HAN -> (int) text.codePoints().filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN).count();
            };
        }
    }
    private static final String CJK = "\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}";
    private static final String WORD = "[\\p{L}\\p{N}&&[^" + CJK + "]]";
    private static final Pattern TOKENS = Pattern.compile("[" + CJK + "]|" + WORD + "+(?:['’\\-]" + WORD + "+)*");
    public static int count(String text) {
        Matcher matcher = TOKENS.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
    private WritingMetrics() {}
}
