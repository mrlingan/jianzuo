package com.jianzuo.writing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Markdown {
    public record Block(String kind, String text, int level, String marker) {}
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern LIST = Pattern.compile("^([-*+]|\\d+\\.)\\s+(.*)$");
    public static List<Block> parse(String input) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder(), code = new StringBuilder();
        boolean inCode = false;
        for (String line : input.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                flush(blocks, paragraph);
                if (inCode) { blocks.add(new Block("code", stripLastNewline(code), 0, "")); code.setLength(0); }
                inCode = !inCode;
            } else if (inCode) { code.append(line).append('\n'); }
            else if (trimmed.isEmpty()) { flush(blocks, paragraph); }
            else if (trimmed.matches("(-{3,}|\\*{3,}|_{3,})")) {
                flush(blocks, paragraph); blocks.add(new Block("divider", "", 0, ""));
            } else {
                Matcher heading = HEADING.matcher(trimmed), list = LIST.matcher(trimmed);
                if (heading.matches()) {
                    flush(blocks, paragraph); blocks.add(new Block("heading", heading.group(2), heading.group(1).length(), ""));
                } else if (trimmed.startsWith("> ")) {
                    flush(blocks, paragraph); blocks.add(new Block("quote", trimmed.substring(2), 0, ""));
                } else if (list.matches()) {
                    flush(blocks, paragraph); String marker = list.group(1);
                    String text = list.group(2);
                    if (text.matches("^\\[[ xX]\\] .*")) blocks.add(new Block("task", text.substring(4), 0, text.charAt(1) == ' ' ? "☐" : "☑"));
                    else blocks.add(new Block("list", text, 0, marker.length() == 1 ? "•" : marker));
                } else {
                    if (paragraph.length() > 0) paragraph.append('\n');
                    paragraph.append(line);
                }
            }
        }
        flush(blocks, paragraph);
        if (inCode) blocks.add(new Block("code", stripLastNewline(code), 0, ""));
        return blocks;
    }
    private static void flush(List<Block> blocks, StringBuilder paragraph) {
        if (paragraph.length() > 0) { String source = paragraph.toString(); String[] lines = source.split("\n");
            boolean table = lines.length >= 2 && lines[0].contains("|") && lines[1].trim().matches("\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?");
            blocks.add(new Block(table ? "table" : "paragraph", source, 0, "")); paragraph.setLength(0); }
    }
    private static String stripLastNewline(StringBuilder text) { return text.length() == 0 ? "" : text.substring(0, text.length() - 1); }
    private Markdown() {}
}
