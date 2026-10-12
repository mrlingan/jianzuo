package com.jianzuo.writing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Literal, read-only search of immutable live values and bounded local snapshots. */
final class RecoverySearch {
    static final int MAX_RESULTS = 200, MAX_SNAPSHOTS = 24;
    static final long MAX_SNAPSHOT_BYTES = 64L * 1024 * 1024, MAX_SCAN_BYTES = 256L * 1024 * 1024;
    record Candidate(String documentId, String title, String body, String notebook, String source, long time) {
        String displayTitle() { return title.trim().isEmpty() ? "无标题文稿" : title; }
    }
    record Hit(Candidate candidate, String excerpt) {}
    record Result(List<Hit> hits, int snapshots, int skipped, boolean truncated) {}
    static List<Candidate> current(Library library) {
        List<Candidate> values = new ArrayList<>();
        for (Document doc : library.documents) values.add(new Candidate(doc.id, doc.title, doc.body, doc.notebook, doc.deletedAt == null ? "当前文稿" : "废纸篓", doc.updatedAt));
        for (Document doc : library.documents) for (Document.Revision revision : doc.history)
            values.add(new Candidate(doc.id, revision.title(), revision.body(), doc.notebook, doc.deletedAt == null ? "历史版本" : "废纸篓历史", revision.time()));
        return values;
    }
    static Result search(String query, List<Candidate> current, File[] snapshots) throws Exception {
        query = query.trim();
        if (query.isEmpty()) throw new UserError.Invalid("ui_enter_a_keyword_from_the_title_or_text_to_rec_25172b", "请输入要找回的标题或正文关键词");
        Collector collector = new Collector(query);
        for (Candidate value : current) { check(); collector.add(value); }
        int count = 0, skipped = 0; long bytes = 0;
        for (File file : Arrays.copyOf(snapshots, Math.min(snapshots.length, MAX_SNAPSHOTS))) {
            check();
            try {
                long size = file.length();
                if (size > MAX_SNAPSHOT_BYTES || size > MAX_SCAN_BYTES - bytes) { skipped++; continue; }
                // Count actual bytes, including malformed snapshots, against the total budget.
                long allowance = Math.min(MAX_SNAPSHOT_BYTES, MAX_SCAN_BYTES - bytes); bytes += allowance;
                byte[] data = read(file, allowance); bytes -= allowance - data.length;
                Library snapshot = LibraryCodec.decode(LibraryRepository.decodeUtf8(data)); count++;
                for (Candidate value : current(snapshot)) {
                    check(); collector.add(new Candidate(value.documentId(), value.title(), value.body(), value.notebook(), "自动备份 · " + value.source(), value.time()));
                }
            } catch (InterruptedIOException error) { throw error; }
            catch (Exception error) { skipped++; }
        }
        return new Result(List.copyOf(collector.hits), count, skipped, collector.truncated);
    }
    static byte[] read(File file, long limit) throws IOException {
        if (file.length() > limit) throw new UserError.Io("ui_snapshot_exceeds_the_reading_limit_a7c917", "快照超过读取上限");
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) {
                check(); if (bytes.size() + (long)n > limit) throw new UserError.Io("ui_snapshot_exceeds_the_reading_limit_a7c917", "快照超过读取上限"); bytes.write(buffer, 0, n);
            }
            return bytes.toByteArray();
        }
    }
    private static void check() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new UserError.Cancelled("ui_search_cancelled_b66582", "搜索已取消");
    }
    private static final class Collector {
        final Pattern pattern;
        final List<Hit> hits = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        boolean truncated;
        Collector(String query) { pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE); }
        void add(Candidate value) throws Exception {
            java.util.regex.Matcher body = pattern.matcher(value.body());
            boolean matchesBody = body.find();
            if (!matchesBody && !pattern.matcher(value.title()).find()) return;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : List.of(value.documentId(), value.title(), value.body())) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
            String key = Base64.getEncoder().encodeToString(digest.digest());
            if (seen.contains(key)) return;
            if (hits.size() == MAX_RESULTS) { truncated = true; return; }
            seen.add(key);
            int start = matchesBody ? Math.max(0, body.start() - 45) : 0;
            int end = Math.min(value.body().length(), start + 180);
            if (start > 0 && Character.isLowSurrogate(value.body().charAt(start))) start--;
            if (end < value.body().length() && end > 0 && Character.isHighSurrogate(value.body().charAt(end - 1))) end++;
            String excerpt = (start > 0 ? "…" : "") + value.body().substring(start, end).replaceAll("\\s+", " ") + (end < value.body().length() ? "…" : "");
            hits.add(new Hit(value, excerpt));
        }
    }
    static Document restore(Library library, Candidate value) {
        if (!library.notebooks.contains(value.notebook())) library.addNotebook(value.notebook());
        return library.create(value.title(), value.body(), value.notebook());
    }
    private RecoverySearch() {}
}
