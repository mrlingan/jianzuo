package com.jianzuo.writing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Document {
    public static final String[] NOTEBOOKS = {"文稿", "灵感", "日记"};
    public record Revision(long time, String title, String body) {}
    public String id = UUID.randomUUID().toString();
    public String title = "", body = "", notebook = NOTEBOOKS[0], section = "", sectionId = "", tags = "";
    public long createdAt = System.currentTimeMillis(), updatedAt = createdAt, rank;
    public boolean favorite, readOnly;
    public Long deletedAt;
    public final List<Revision> history = new ArrayList<>();
    public void checkpoint(boolean force) {
        Revision previous = history.isEmpty() ? null : history.get(0);
        if (previous != null && previous.title().equals(title) && previous.body().equals(body)) return;
        long now = System.currentTimeMillis();
        if (!force && previous != null && now - previous.time() < 30_000) return;
        history.add(0, new Revision(now, title, body));
        while (history.size() > 40) history.remove(history.size() - 1);
    }
    public void restoreRevision(Revision revision) {
        checkpoint(true); title = revision.title(); body = revision.body(); touch();
    }
    public String displayTitle() { return title.trim().isEmpty() ? "无标题文稿" : title; }
    public String excerpt() { return body.replaceAll("[#>*`_\\[\\]]", "").replaceAll("\\s+", " ").trim(); }
    public int wordCount() { return WritingMetrics.count(body); }
    public void touch() { updatedAt = System.currentTimeMillis(); }
}
