package com.jianzuo.writing;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Device-local toolbar settings; the document library and backups remain unchanged. */
final class ShortcutConfig {
    record Item(String id, String label, String value, boolean enabled) {
        Item enabled(boolean value) { return new Item(id, label, this.value, value); }
        boolean custom() { return id.startsWith("custom:"); }
    }
    static final List<Item> BUILTINS = List.of(
        item("undo", "撤销", ""), item("redo", "重做", ""), item("quotes", "“”", "“”"),
        item("parentheses", "（）", "（）"), item("bookmarks", "《》", "《》"), item("bold", "**", "**"),
        item("code", "``", "``"), item("brackets", "[]", "[]"), item("indent", "缩进", "　　"),
        item("date", "日期", ""), item("heading", "# 标题", "\n## "), item("todo", "待办", "\n- [ ] "), item("find", "查找", ""));
    final List<Item> items;
    final int rows;
    ShortcutConfig(List<Item> items, int rows) {
        if (rows < 1 || rows > 3) throw new UserError.Invalid("ui_choose_1_3_rows_e2da4a", "请选择 1–3 行");
        Set<String> ids = new HashSet<>(); int custom = 0, total = 0;
        for (Item entry : items) {
            if (entry.id() == null || !ids.add(entry.id())) throw new UserError.Invalid("ui_duplicate_shortcut_5681d5", "快捷项重复");
            if (entry.custom()) {
                if (entry.label() == null || entry.label().isBlank() || entry.label().codePointCount(0, entry.label().length()) > 24 || entry.label().contains("\n"))
                    throw new UserError.Invalid("ui_use_a_name_of_1_24_characters_without_newline_93a157", "名称需为 1–24 个字符，不能换行");
                if (entry.value() == null || entry.value().isEmpty()) throw new UserError.Invalid("ui_enter_text_to_insert_a1739e", "请输入插入内容");
                total += entry.value().length(); custom++;
            } else if (BUILTINS.stream().noneMatch(b -> b.id().equals(entry.id()) && b.label().equals(entry.label()) && b.value().equals(entry.value())))
                throw new UserError.Invalid("ui_unknown_shortcut_2836a9", "未知快捷项");
        }
        if (custom > 30 || total > 8000) throw new UserError.Invalid("ui_up_to_30_custom_shortcuts_with_8_000_characte_158f5a", "最多 30 个自定义项，内容合计 8,000 字符");
        if (!ids.containsAll(BUILTINS.stream().map(Item::id).collect(java.util.stream.Collectors.toList()))) throw new UserError.Invalid("ui_built_in_shortcut_missing_9d9393", "内置快捷项缺失");
        this.items = List.copyOf(items); this.rows = rows;
    }
    static Item item(String id, String label, String value) { return new Item(id, label, value, true); }
    static ShortcutConfig load(String json, String legacy) {
        if (json != null && !json.isEmpty()) { try { return decode(json); } catch (Exception ignored) { /* Recover legacy settings if the new preference is damaged. */ } }
        List<Item> entries = new ArrayList<>(BUILTINS); int total = 0, count = 0;
        for (String line : legacy.split("\n")) {
            int split = line.indexOf('|'); if (split <= 0) continue;
            String label = line.substring(0, split).replace("\r", "").trim(), value = line.substring(split + 1).replace("\\n", "\n");
            if (label.isEmpty() || value.isEmpty() || count >= 30 || total + value.length() > 8000) continue;
            if (label.codePointCount(0, label.length()) > 24) label = label.substring(0, label.offsetByCodePoints(0, 24));
            entries.add(item("custom:legacy-" + count++, label, value)); total += value.length();
        }
        return new ShortcutConfig(entries, 1);
    }
    String encode() {
        try {
            JSONArray entries = new JSONArray();
            for (Item item : items) entries.put(new JSONObject().put("id", item.id()).put("label", item.label()).put("value", item.value()).put("enabled", item.enabled()));
            return new JSONObject().put("version", 1).put("rows", rows).put("items", entries).toString();
        } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
    }
    static ShortcutConfig decode(String json) throws org.json.JSONException {
        JSONObject root = new JSONObject(json); if (root.getInt("version") != 1) throw new UserError.Invalid("ui_unknown_shortcut_toolbar_format_09692e", "未知快捷栏格式");
        JSONArray array = root.getJSONArray("items"); if (array.length() > BUILTINS.size() + 30) throw new UserError.Invalid("ui_too_many_shortcuts_f487ca", "快捷项过多");
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i); items.add(new Item(entry.getString("id"), entry.getString("label"), entry.getString("value"), entry.getBoolean("enabled")));
        }
        return new ShortcutConfig(items, root.getInt("rows"));
    }
    List<List<Item>> visibleRows() {
        List<Item> visible = items.stream().filter(Item::enabled).collect(java.util.stream.Collectors.toList()); List<List<Item>> result = new ArrayList<>();
        int actualRows = Math.min(rows, visible.size());
        for (int row = 0; row < actualRows; row++) result.add(visible.subList(row * visible.size() / actualRows, (row + 1) * visible.size() / actualRows));
        return result;
    }
}
