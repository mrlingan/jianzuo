package com.jianzuo.writing;

public final class ChapterNaming {
    public record Change(Document document, String before, String after) {}
    public static java.util.List<Change> plan(java.util.List<Document> documents, String pattern, int start, int width, boolean strip) {
        java.util.List<Change> result = new java.util.ArrayList<>(); int number = start;
        for (Document doc : documents) if (!doc.readOnly && doc.deletedAt == null) result.add(new Change(doc, doc.title, title(pattern, number++, width, doc.title, strip)));
        return result;
    }
    public static int apply(Library library, java.util.List<Change> plan) {
        for (Change change : plan) if (!library.documents.contains(change.document()) || change.document().deletedAt != null || change.document().readOnly || !change.document().title.equals(change.before())) throw new UserError.Invalid("ui_documents_have_changed_preview_again_no_title_1d1fe8", "文稿已变化，请重新预览；本次未修改标题");
        int count = 0;
        for (Change change : plan) if (!change.before().equals(change.after())) {
            change.document().checkpoint(true); change.document().title = change.after(); change.document().touch(); count++;
        }
        return count;
    }
    public static String title(String pattern, int number, int width, String original, boolean strip) {
        if (!pattern.contains("{n}") || pattern.length() > 200 || number < 1 || number > 1_000_000 || width < 1 || width > 7)
            throw new UserError.Invalid("ui_the_template_must_include_n_numbers_must_be_1_e25b49", "模板须包含 {n}，序号范围 1–1,000,000");
        if (strip) original = original.replaceFirst("^第[0-9０-９一二三四五六七八九十百千万零〇两]+章[\\s\\u3000·:：-]*", "");
        String digits = String.format(java.util.Locale.ROOT, "%0" + width + "d", number);
        // Substitute in one pass: placeholders inside an existing title remain literal text.
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{n\\}|\\{title\\}").matcher(pattern);
        StringBuilder result = new StringBuilder(); int end = 0;
        while (matcher.find()) { result.append(pattern, end, matcher.start()).append(matcher.group().equals("{n}") ? digits : original); end = matcher.end(); }
        String title = result.append(pattern.substring(end)).toString().trim();
        if (title.isEmpty() || title.length() > 1000) throw new UserError.Invalid("ui_generated_title_is_empty_or_exceeds_1_000_cha_ee70ef", "生成的标题为空或超过 1,000 字符");
        return title;
    }
    private ChapterNaming() {}
}
