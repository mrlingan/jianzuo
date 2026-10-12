package com.jianzuo.writing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Matching and replacement use the same immutable source; replacement text is always literal. */
public final class TextTools {
    public record Match(int start, int end) {}
    public record Heading(int offset, int level, String title) {}
    public static Pattern pattern(String query, boolean regex, boolean caseSensitive) {
        if (query.isEmpty()) throw new UserError.Invalid("ui_enter_text_to_find_aa7fce", "请输入查找文字");
        return Pattern.compile(regex ? query : Pattern.quote(query), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
    public static List<Match> matches(String source, Pattern pattern) {
        List<Match> result = new ArrayList<>(); Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            if (result.size() >= 10000) throw new UserError.Invalid("ui_more_than_10_000_matches_narrow_the_search_d62f33", "匹配超过 10,000 处，请缩小查找范围");
            result.add(new Match(matcher.start(), matcher.end()));
        }
        return result;
    }
    public static String replace(String source, Pattern pattern, String replacement) {
        return pattern.matcher(source).replaceAll(Matcher.quoteReplacement(replacement));
    }
    public static String replaceSelected(String source, List<Match> matches, boolean[] selected, String replacement) {
        if (selected.length != matches.size()) throw new UserError.Invalid("ui_selection_count_does_not_match_3d3eff", "选择数量不匹配");
        StringBuilder result = new StringBuilder(); int cursor = 0;
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            if (match.start() < cursor || match.end() < match.start() || match.end() > source.length()) throw new UserError.Invalid("ui_invalid_match_position_a31f09", "匹配位置无效");
            result.append(source, cursor, match.start());
            result.append(selected[i] ? replacement : source.substring(match.start(), match.end())); cursor = match.end();
        }
        return result.append(source.substring(cursor)).toString();
    }
    public record Edit(String text, int cursor) {}
    public static Edit moveParagraph(String source, int cursor, int direction) {
        cursor = Math.max(0, Math.min(cursor, source.length()));
        String[] lines = source.split("\n", -1); int row = 0, offset = 0;
        while (row < lines.length - 1 && offset + lines[row].length() < cursor) offset += lines[row++].length() + 1;
        if (lines[row].trim().isEmpty()) return new Edit(source, cursor);
        int target = row + (direction < 0 ? -1 : 1);
        while (target >= 0 && target < lines.length && lines[target].trim().isEmpty()) target += direction < 0 ? -1 : 1;
        if (target < 0 || target >= lines.length) return new Edit(source, cursor);
        String value = lines[row]; lines[row] = lines[target]; lines[target] = value;
        int next = cursor - offset;
        for (int i = 0; i < target; i++) next += lines[i].length() + 1;
        return new Edit(String.join("\n", lines), next);
    }
    public static List<Heading> outline(String source) {
        List<Heading> result = new ArrayList<>(); boolean code = false; int offset = 0;
        for (String line : source.split("\n", -1)) {
            if (line.trim().startsWith("```")) code = !code;
            else if (!code) {
                Matcher matcher = Pattern.compile("^\\s*(#{1,6})\\s+(.+)$").matcher(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
                if (matcher.matches()) result.add(new Heading(offset, matcher.group(1).length(), matcher.group(2)));
            }
            offset += line.length() + 1;
        }
        return result;
    }
    public static String format(String source, int operation) {
        source = source.replace("\r\n", "\n").replace('\r', '\n');
        // Formatters operate on plain text explicitly; they are never run automatically on Markdown.
        return switch (operation) {
            case 0 -> source.replaceAll("(?m)^[ \\t\\u3000]+", "");
            case 1 -> source.replaceAll("(?m)^([^\\s].*)$", "　　$1");
            case 2 -> source.replaceAll("\\n{3,}", "\n\n").replaceAll("(?<!\\n)\\n(?!\\n)", "\n\n");
            case 3 -> source.replaceAll("\\n{2,}", "\n");
            case 4 -> source.replaceAll("([\\p{IsHan}])([A-Za-z0-9])", "$1 $2").replaceAll("([A-Za-z0-9])([\\p{IsHan}])", "$1 $2");
            default -> source;
        };
    }
    private TextTools() {}
}
