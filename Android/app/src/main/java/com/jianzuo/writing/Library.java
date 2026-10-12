package com.jianzuo.writing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory library; Activity owns mutations, Repository owns disk snapshots. */
public final class Library {
    public static final String ALL = "全部文稿", FAVORITES = "我的收藏", TRASH = "废纸篓";
    public final List<Document> documents = new ArrayList<>();
    public final List<Section> sections = new ArrayList<>();
    public final List<String> notebooks = new ArrayList<>(Arrays.asList(Document.NOTEBOOKS));
    public final Map<String, Integer> dailyWords = new LinkedHashMap<>();
    public final Map<String, Long> dailyWritingMillis = new LinkedHashMap<>();
    public String scratch = "";
    public List<String> filters() {
        List<String> result = new ArrayList<>(Arrays.asList(ALL, FAVORITES));
        result.addAll(notebooks); result.add(TRASH); return result;
    }
    public void addNotebook(String name) {
        name = name.trim();
        if (name.isEmpty() || name.length() > 40 || filters().contains(name)) throw new UserError.Invalid("ui_notebook_name_is_empty_duplicated_or_too_long_c1791d", "笔记本名称为空、重复或过长");
        notebooks.add(name);
    }
    public void renameNotebook(String old, String name) {
        int index = notebooks.indexOf(old);
        if (index < 0) throw new UserError.Invalid("ui_notebook_does_not_exist_2c2aa6", "笔记本不存在");
        name = name.trim(); if (old.equals(name)) return;
        addNotebook(name); notebooks.remove(name); notebooks.set(index, name);
        for (Document doc : documents) if (doc.notebook.equals(old)) doc.notebook = name;
        for (Section section : sections) if (section.notebook.equals(old)) section.notebook = name;
    }
    public void deleteNotebook(String name) {
        if (notebooks.size() <= 1 || documents.stream().anyMatch(d -> d.notebook.equals(name))) throw new UserError.Invalid("ui_move_all_documents_out_first_including_trash_d3e3d9", "请先移出所有文稿（含废纸篓），并保留至少一个笔记本");
        notebooks.remove(name); sections.removeIf(s -> s.notebook.equals(name));
    }
    public Document find(String id) {
        for (Document doc : documents) if (doc.id.equals(id)) return doc;
        return null;
    }
    public boolean matches(Document doc, String filter) {
        if (TRASH.equals(filter)) return doc.deletedAt != null;
        if (doc.deletedAt != null) return false;
        if (FAVORITES.equals(filter)) return doc.favorite;
        return ALL.equals(filter) || doc.notebook.equals(filter);
    }
    public List<Document> visible(String filter, String search) {
        return visible(filter, search, "updated");
    }
    public List<Document> visible(String filter, String search, String sort) {
        return visible(filter, search, sort, false);
    }
    public List<Document> visible(String filter, String search, String sort, boolean allTerms) {
        String query = search.trim().toLowerCase(Locale.ROOT);
        String[] terms = query.isEmpty() ? new String[0] : allTerms ? query.split("\\s+") : new String[]{query};
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) if (matches(doc, filter) && searchScore(doc, terms) >= 0) result.add(doc);
        Comparator<Document> order = switch (sort) {
            case "relevance" -> Comparator.comparingInt((Document doc) -> searchScore(doc, terms)).reversed().thenComparing(Comparator.comparingLong((Document doc) -> doc.updatedAt).reversed());
            case "created" -> Comparator.comparingLong((Document doc) -> doc.createdAt).reversed();
            case "title" -> Comparator.comparing(Document::displayTitle);
            case "manual" -> Comparator.comparingLong((Document doc) -> doc.rank);
            default -> Comparator.comparingLong((Document doc) -> doc.updatedAt).reversed();
        };
        result.sort(order.thenComparing(doc -> doc.id));
        return result;
    }
    private int searchScore(Document doc, String[] terms) {
        String title = doc.title.toLowerCase(Locale.ROOT), body = doc.body.toLowerCase(Locale.ROOT), tags = doc.tags.toLowerCase(Locale.ROOT), section = doc.section.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            int weight = (title.contains(term) ? 6 : 0) + (tags.contains(term) ? 4 : 0) + (section.contains(term) ? 3 : 0) + (body.contains(term) ? 1 : 0);
            if (weight == 0) return -1; score += weight;
        }
        return score;
    }
    public int count(String filter) {
        int count = 0;
        for (Document doc : documents) if (matches(doc, filter)) count++;
        return count;
    }
    public Document create(String title, String body, String notebook) {
        Document doc = new Document();
        doc.title = title; doc.body = body; doc.notebook = notebook;
        doc.rank = documents.stream().mapToLong(d -> d.rank).max().orElse(-1) + 1;
        documents.add(0, doc);
        return doc;
    }
    public Document duplicate(Document doc) {
        Document copy = create(doc.displayTitle() + " · 副本", doc.body, doc.notebook);
        assignSection(copy, namedSection(doc.notebook, doc.section)); copy.tags = doc.tags; return copy;
    }
    public Document createAdjacent(Document anchor, boolean after) {
        if (!documents.contains(anchor) || anchor.deletedAt != null || !notebooks.contains(anchor.notebook)) throw new UserError.Invalid("ui_select_a_valid_chapter_first_2b7941", "请先选择有效章节");
        reconcileSections(); List<Document> ordered = visible(anchor.notebook, "", "manual"); int index = ordered.indexOf(anchor);
        Document chapter = create("", "", anchor.notebook); assignSection(chapter, section(anchor.sectionId)); chapter.tags = anchor.tags;
        ordered.add(index + (after ? 1 : 0), chapter); for (int i = 0; i < ordered.size(); i++) ordered.get(i).rank = i;
        return chapter;
    }
    public void move(Document doc, int delta) {
        reconcileSections();
        List<Document> all = visible(doc.notebook, "", "manual"), ordered = new ArrayList<>();
        for (Document chapter : all) if (chapter.sectionId.equals(doc.sectionId)) ordered.add(chapter);
        int index = ordered.indexOf(doc), next = index + delta;
        if (index < 0 || next < 0 || next >= ordered.size()) return;
        java.util.Collections.swap(ordered, index, next);
        int groupIndex = 0;
        for (int i = 0; i < all.size(); i++) { Document chapter = all.get(i); if (chapter.sectionId.equals(doc.sectionId)) chapter = ordered.get(groupIndex++); chapter.rank = i; }
    }
    public int merge(Library incoming) {
        return merge(incoming, false);
    }
    public int merge(Library incoming, boolean includeStatistics) {
        incoming.reconcileSections();
        for (String name : incoming.notebooks) if (!notebooks.contains(name)) notebooks.add(name);
        Map<String, Section> mapped = new LinkedHashMap<>();
        for (String notebook : incoming.notebooks) for (Section section : incoming.sections(notebook)) mapped.put(section.id, namedSection(notebook, section.name));
        List<Document> originals = new ArrayList<>(incoming.documents); originals.sort(Comparator.comparingLong(d -> d.rank));
        for (Document original : originals) {
            Document copy = create(original.title, original.body, original.notebook);
            assignSection(copy, mapped.get(original.sectionId)); copy.tags = original.tags; copy.favorite = original.favorite;
            copy.readOnly = original.readOnly; copy.deletedAt = original.deletedAt;
            copy.createdAt = original.createdAt; copy.updatedAt = original.updatedAt; copy.history.addAll(original.history);
        }
        if (!incoming.scratch.trim().isEmpty()) scratch += (scratch.trim().isEmpty() ? "" : "\n\n") + incoming.scratch;
        if (includeStatistics) {
            incoming.dailyWords.forEach((day, words) -> dailyWords.merge(day, words, Math::max));
            incoming.dailyWritingMillis.forEach((day, millis) -> dailyWritingMillis.merge(day, millis, Math::max));
        }
        return incoming.documents.size();
    }
    public String bookText(String notebook) {
        StringBuilder text = new StringBuilder(); String section = "";
        for (Document doc : bookDocuments(notebook)) {
            if (!section.equals(doc.sectionId)) { section = doc.sectionId; if (!doc.section.isEmpty()) text.append("# ").append(doc.section).append("\n\n"); }
            text.append("## ").append(doc.displayTitle()).append("\n\n").append(doc.body).append("\n\n");
        }
        return text.toString();
    }
    public Section section(String id) {
        for (Section section : sections) if (section.id.equals(id)) return section;
        return null;
    }
    public List<Section> sections(String notebook) {
        List<Section> result = new ArrayList<>(); for (Section section : sections) if (section.notebook.equals(notebook)) result.add(section);
        result.sort(Comparator.comparingLong((Section s) -> s.rank).thenComparing(s -> s.id)); return result;
    }
    public Section addSection(String notebook, String name) {
        name = name.trim();
        if (!notebooks.contains(notebook) || name.isEmpty() || name.length() > 80) throw new UserError.Invalid("ui_use_a_volume_name_of_1_80_characters_in_an_ex_ccb0a0", "卷名须为 1–80 字符，且笔记本必须存在");
        for (Section section : sections) if (section.notebook.equals(notebook) && section.name.equals(name)) throw new UserError.Invalid("ui_a_volume_with_this_name_already_exists_in_the_228d85", "此笔记本已有同名分卷");
        Section result = new Section(notebook, name, sections.stream().mapToLong(s -> s.rank).max().orElse(-1) + 1); sections.add(result); return result;
    }
    public Section namedSection(String notebook, String name) {
        if (name.isEmpty()) return null;
        for (Section section : sections) if (section.notebook.equals(notebook) && section.name.equals(name)) return section;
        if (!notebooks.contains(notebook)) throw new UserError.Invalid("ui_notebook_does_not_exist_2c2aa6", "笔记本不存在");
        Section result = new Section(notebook, name, sections.stream().mapToLong(s -> s.rank).max().orElse(-1) + 1); sections.add(result); return result;
    }
    /** Bridge legacy volume names and callers that only assign the legacy string. */
    public void reconcileSections() {
        List<Document> ordered = new ArrayList<>(documents);
        ordered.sort(Comparator.comparingLong((Document d) -> d.rank).thenComparing(d -> d.id));
        for (Document doc : ordered) {
            Section section = section(doc.sectionId);
            if (section != null && section.notebook.equals(doc.notebook) && section.name.equals(doc.section)) continue;
            assignSection(doc, namedSection(doc.notebook, doc.section));
        }
    }
    public void assignSection(Document doc, Section section) {
        if (section != null && (!sections.contains(section) || !section.notebook.equals(doc.notebook))) throw new UserError.Invalid("ui_volume_and_document_are_not_in_the_same_noteb_c807bb", "分卷与文稿不属于同一笔记本");
        doc.sectionId = section == null ? "" : section.id; doc.section = section == null ? "" : section.name;
    }
    public List<Document> chapters(Section section, boolean includeTrash) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) if (doc.sectionId.equals(section.id) && (includeTrash || doc.deletedAt == null)) result.add(doc);
        result.sort(Comparator.comparingLong((Document d) -> d.rank).thenComparing(d -> d.id)); return result;
    }
    public void renameSection(Section section, String name) {
        name = name.trim(); if (!sections.contains(section)) throw new UserError.Invalid("ui_volume_does_not_exist_2bb75a", "分卷不存在");
        if (name.equals(section.name)) return;
        if (name.isEmpty() || name.length() > 80) throw new UserError.Invalid("ui_use_a_volume_name_of_1_80_characters_6ac18b", "卷名须为 1–80 字符");
        for (Section other : sections) if (other != section && other.notebook.equals(section.notebook) && other.name.equals(name)) throw new UserError.Invalid("ui_a_volume_with_this_name_already_exists_in_the_228d85", "此笔记本已有同名分卷");
        section.name = name; for (Document doc : chapters(section, true)) doc.section = name;
    }
    public void dissolveSection(Section section) {
        for (Document doc : chapters(section, true)) assignSection(doc, null); sections.remove(section);
    }
    public void moveSection(Section section, int delta) {
        List<Section> ordered = sections(section.notebook); int index = ordered.indexOf(section), next = index + delta;
        if (index < 0 || next < 0 || next >= ordered.size()) return;
        java.util.Collections.swap(ordered, index, next); for (int i = 0; i < ordered.size(); i++) ordered.get(i).rank = i;
    }
    public void moveSectionToNotebook(Section section, String notebook) {
        if (!sections.contains(section) || !notebooks.contains(notebook)) throw new UserError.Invalid("ui_notebook_or_volume_does_not_exist_3dabec", "笔记本或分卷不存在");
        if (notebook.equals(section.notebook)) return;
        for (Section other : sections(notebook)) if (other.name.equals(section.name)) throw new UserError.Invalid("ui_the_target_notebook_has_a_volume_with_this_na_e190df", "目标笔记本已有同名分卷，请先重命名");
        List<Document> chapters = chapters(section, true); section.notebook = notebook;
        section.rank = sections(notebook).stream().filter(s -> s != section).mapToLong(s -> s.rank).max().orElse(-1) + 1;
        for (Document doc : chapters) { doc.notebook = notebook; doc.rank = documents.stream().mapToLong(d -> d.rank).max().orElse(-1) + 1; }
    }
    public void moveToNotebook(Document doc, String notebook) {
        if (!notebooks.contains(notebook)) throw new UserError.Invalid("ui_notebook_does_not_exist_2c2aa6", "笔记本不存在");
        String name = doc.section; Section target = namedSection(notebook, name); doc.notebook = notebook; assignSection(doc, target);
        doc.rank = documents.stream().mapToLong(d -> d.rank).max().orElse(-1) + 1; doc.touch();
    }
    public void placeBefore(Document doc, Document target) {
        if (doc == target) return;
        if (!documents.contains(doc) || !documents.contains(target) || doc.deletedAt != null || target.deletedAt != null || !doc.notebook.equals(target.notebook)) throw new UserError.Invalid("ui_only_valid_documents_in_the_same_notebook_can_e02c55", "只能整理同一笔记本中的有效文稿");
        List<Document> ordered = visible(doc.notebook, "", "manual"); ordered.remove(doc); ordered.add(ordered.indexOf(target), doc);
        assignSection(doc, section(target.sectionId)); for (int i = 0; i < ordered.size(); i++) ordered.get(i).rank = i;
    }
    public List<Document> bookDocuments(String notebook) {
        reconcileSections(); List<Document> ordered = visible(notebook, "", "manual"), result = new ArrayList<>();
        for (Document doc : ordered) if (doc.sectionId.isEmpty()) result.add(doc);
        for (Section section : sections(notebook)) for (Document doc : ordered) if (doc.sectionId.equals(section.id)) result.add(doc);
        return result;
    }
    public static List<String> tags(String source) {
        java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
        for (String tag : source.split("[\\s,，;；]+")) { tag = tag.replaceFirst("^#+", "").trim(); if (!tag.isEmpty()) tags.add(tag); }
        return new ArrayList<>(tags);
    }

    public void trash(Document doc) { doc.deletedAt = System.currentTimeMillis(); doc.touch(); }
    public void restore(Document doc) { doc.deletedAt = null; doc.touch(); }
    public void permanentlyDelete(Document doc) { if (doc.deletedAt != null) documents.remove(doc); }
    public void seed() {
        Document first = create("把日子写成诗", "# 把日子写成诗\n\n清晨的光落在窗边，咖啡还冒着热气。城市尚未完全醒来，而这一刻，只属于你和眼前的文字。\n\n我们总以为，写作需要一个郑重其事的开始。一个完美的题目，一段漂亮的开场，或是一整块不被打扰的时间。\n\n但有时候，写作只是记下此刻。\n\n## 从一个小小的观察开始\n\n窗外的树又长高了一点。楼下的面包店换了新的招牌。昨天读到的一句话，还在心里轻轻回响。\n\n这些细小的事物，是生活留给我们的线索。把它们拾起来，放在纸上，就有了故事。\n\n> 不必等灵感到来。先写下第一句，剩下的会慢慢发生。\n\n今天想写的事：\n\n- 一次没有目的的散步\n- 一个许久未见的人\n- 一段想要留住的时光\n\n写下来吧。从这里开始。", "文稿");
        first.favorite = true;
        Document ideas = create("一些闪闪发光的念头", "# 一些闪闪发光的念头\n\n- 写一封给十年后自己的信\n- 收集雨天的声音\n- 一个住在灯塔里的人的故事\n\n灵感不必完整，先把它留下。", "灵感");
        ideas.updatedAt = first.updatedAt - 3600000;
        Document journal = create("今天，也有值得记录的事", "今天最想记住的一个瞬间：\n\n\n让我心生感谢的三件小事：\n\n1. \n2. \n3. \n\n明天想尝试的事：\n", "日记");
        journal.updatedAt = first.updatedAt - 7200000;
    }
}
