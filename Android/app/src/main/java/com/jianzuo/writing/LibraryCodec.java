package com.jianzuo.writing;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Android snapshot uses Unix milliseconds; Markdown/TXT exchanges content with Apple apps. */
public final class LibraryCodec {
    public static String encode(Library library) throws JSONException {
        library.reconcileSections();
        JSONArray sections = new JSONArray();
        for (Section section : library.sections) sections.put(new JSONObject().put("id", section.id).put("name", section.name).put("notebook", section.notebook).put("rank", section.rank));
        JSONArray documents = new JSONArray();
        for (Document doc : library.documents) {
            documents.put(new JSONObject().put("id", doc.id).put("title", doc.title).put("body", doc.body)
                .put("notebook", doc.notebook).put("createdAt", doc.createdAt).put("updatedAt", doc.updatedAt)
                .put("sectionId", doc.sectionId).put("section", doc.section).put("tags", doc.tags).put("rank", doc.rank).put("readOnly", doc.readOnly).put("history", history(doc))
                .put("favorite", doc.favorite).put("deletedAt", doc.deletedAt == null ? JSONObject.NULL : doc.deletedAt));
        }
        return new JSONObject().put("version", 4).put("sections", sections).put("notebooks", new JSONArray(library.notebooks)).put("scratch", library.scratch)
            .put("dailyWords", new JSONObject(library.dailyWords)).put("dailyWritingMillis", new JSONObject(library.dailyWritingMillis)).put("documents", documents).toString(2);
    }
    public static Library decode(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        int version = root.getInt("version");
        if (version < 1 || version > 4) throw new UserError.Json("ui_unsupported_library_version_868b38", "不支持的文稿库版本");
        JSONArray documents = root.getJSONArray("documents");
        Library library = new Library();
        Set<String> ids = new HashSet<>();
        if (version >= 2) {
            JSONArray names = root.getJSONArray("notebooks"); library.notebooks.clear();
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                if (name.trim().isEmpty() || name.length() > 40 || library.filters().contains(name)) throw new UserError.Json("ui_invalid_notebook_3ff57f", "笔记本无效");
                library.notebooks.add(name);
            }
            if (library.notebooks.isEmpty()) throw new UserError.Json("ui_notebook_missing_0649d8", "缺少笔记本");
            library.scratch = requireString(root, "scratch");
            JSONObject days = root.getJSONObject("dailyWords");
            java.util.Iterator<String> keys = days.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!key.matches("\\d{4}-\\d{2}-\\d{2}") || days.getInt(key) < 0) throw new UserError.Json("ui_invalid_daily_statistics_517d92", "每日统计无效");
                library.dailyWords.put(key, days.getInt(key));
            }
        }
        if (version >= 4) {
            JSONObject times = root.getJSONObject("dailyWritingMillis"); java.util.Iterator<String> keys = times.keys();
            while (keys.hasNext()) {
                String key = keys.next(); Object value = times.get(key);
                try { if (!key.matches("\\d{4}-\\d{2}-\\d{2}") || !java.time.LocalDate.parse(key).toString().equals(key)) throw new IllegalArgumentException(); }
                catch (java.time.DateTimeException | IllegalArgumentException error) { throw new UserError.Json("ui_invalid_writing_time_date_bfa6fd", "写作时长日期无效"); }
                if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0 || ((Number) value).longValue() > WritingTime.MAX_DAY_MILLIS) throw new UserError.Json("ui_invalid_writing_time_e3e780", "写作时长无效");
                library.dailyWritingMillis.put(key, ((Number) value).longValue());
            }
        }
        if (version >= 3) {
            Set<String> sectionIds = new HashSet<>(), sectionNames = new HashSet<>(); JSONArray sections = root.getJSONArray("sections");
            for (int i = 0; i < sections.length(); i++) {
                JSONObject json = sections.getJSONObject(i);
                Section section = new Section(requireString(json, "notebook"), requireString(json, "name"), json.getLong("rank"));
                section.id = requireString(json, "id");
                try { UUID.fromString(section.id); } catch (IllegalArgumentException error) { throw new UserError.Json("ui_invalid_volume_id_7c9248", "分卷编号无效"); }
                String nameKey = section.notebook.length() + ":" + section.notebook + section.name;
                if (!sectionIds.add(section.id) || !sectionNames.add(nameKey) || !library.notebooks.contains(section.notebook) || section.name.isEmpty() || section.rank < 0) throw new UserError.Json("ui_invalid_or_duplicate_volume_e76465", "分卷无效或重复");
                library.sections.add(section);
            }
        }
        for (int i = 0; i < documents.length(); i++) {
            JSONObject json = documents.getJSONObject(i);
            Document doc = new Document();
            doc.id = json.getString("id");
            try { UUID.fromString(doc.id); } catch (IllegalArgumentException error) { throw new UserError.Json("ui_invalid_document_id_53b237", "文稿编号无效"); }
            if (!ids.add(doc.id)) throw new UserError.Json("ui_duplicate_document_id_6f10c7", "文稿编号重复");
            doc.title = requireString(json, "title"); doc.body = requireString(json, "body");
            doc.notebook = requireString(json, "notebook");
            if (!library.notebooks.contains(doc.notebook)) throw new UserError.Json("ui_invalid_notebook_3ff57f", "笔记本无效");
            doc.createdAt = json.getLong("createdAt"); doc.updatedAt = json.getLong("updatedAt");
            doc.favorite = json.getBoolean("favorite");
            if (!json.has("deletedAt")) throw new UserError.Json("ui_deletion_time_missing_6562dd", "缺少删除时间");
            doc.deletedAt = json.isNull("deletedAt") ? null : json.getLong("deletedAt");
            if (doc.createdAt < 0 || doc.updatedAt < 0 || (doc.deletedAt != null && doc.deletedAt < 0)) throw new UserError.Json("ui_invalid_time_25b9d2", "时间无效");
            if (version >= 2) {
                doc.section = requireString(json, "section"); doc.tags = requireString(json, "tags");
                doc.rank = json.getLong("rank"); doc.readOnly = json.getBoolean("readOnly");
                JSONArray revisions = json.getJSONArray("history");
                if (revisions.length() > 40) throw new UserError.Json("ui_too_many_revisions_7bb748", "历史记录数量超限");
                for (int j = 0; j < revisions.length(); j++) {
                    JSONObject h = revisions.getJSONObject(j); long time = h.getLong("time");
                    if (time < 0) throw new UserError.Json("ui_invalid_revision_time_4349de", "历史记录时间无效");
                    doc.history.add(new Document.Revision(time, requireString(h, "title"), requireString(h, "body")));
                }
            } else doc.rank = i;
            if (version >= 3) {
                doc.sectionId = requireString(json, "sectionId"); Section section = library.section(doc.sectionId);
                if (doc.sectionId.isEmpty() ? !doc.section.isEmpty() : section == null || !section.name.equals(doc.section) || !section.notebook.equals(doc.notebook)) throw new UserError.Json("ui_invalid_document_volume_reference_c9a91f", "文稿分卷引用无效");
            }
            library.documents.add(doc);
        }
        if (version < 3) library.reconcileSections();
        return library;
    }
    private static JSONArray history(Document doc) throws JSONException {
        JSONArray result = new JSONArray();
        for (Document.Revision r : doc.history) result.put(new JSONObject().put("time", r.time()).put("title", r.title()).put("body", r.body()));
        return result;
    }
    private static String requireString(JSONObject json, String key) throws JSONException {
        Object value = json.get(key);
        if (!(value instanceof String)) throw new UserError.Json("ui_must_be_text_5b4104", key + " 必须为文字");
        return (String) value;
    }
    private LibraryCodec() {}
}
