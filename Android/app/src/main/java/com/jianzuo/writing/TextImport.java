package com.jianzuo.writing;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class TextImport {
    static final int MAX_FILES = 1000, MAX_FILE_BYTES = 2 * 1024 * 1024, MAX_ENTRIES = 5000;
    static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    record Item(String title, String body, String notebook) {}
    record Entry(String id, String name, boolean directory) {}
    interface Tree {
        List<Entry> children(String id) throws Exception;
        InputStream open(String id) throws Exception;
    }
    interface Monitor {
        default void check() throws java.io.InterruptedIOException {
            if (Thread.currentThread().isInterrupted()) throw new UserError.Cancelled("ui_scan_cancelled_0e17a0", "扫描已取消");
        }
        default void progress(int entries, int files, long bytes) {}
    }
    /** Iterative traversal also bounds directories/non-text entries and rejects provider cycles. */
    static List<Item> folder(Tree tree, String root, String notebook) throws Exception {
        return folder(tree, root, notebook, new Monitor() {});
    }
    static List<Item> folder(Tree tree, String root, String notebook, Monitor monitor) throws Exception {
        record Directory(String id, String notebook) {}
        java.util.ArrayDeque<Directory> pending = new java.util.ArrayDeque<>();
        java.util.Set<String> visited = new java.util.HashSet<>(); visited.add(root);
        pending.add(new Directory(root, notebook));
        List<Item> result = new ArrayList<>(); Budget budget = new Budget(monitor); int entries = 0;
        while (!pending.isEmpty()) {
            monitor.check();
            Directory directory = pending.removeFirst();
            List<Entry> children = new ArrayList<>(tree.children(directory.id()));
            if (children.size() > MAX_ENTRIES - entries) throw new UserError.Io("ui_folder_has_more_than_5_000_entries_4e4289", "文件夹条目超过 5,000 个");
            children.sort((a, b) -> { int order = naturalOrder(a.name(), b.name()); return order != 0 ? order : a.id().compareTo(b.id()); });
            for (Entry entry : children) {
                monitor.check(); entries++; monitor.progress(entries, result.size(), budget.total);
                if (entry.name().startsWith(".") || entry.name().equals("__MACOSX")) continue;
                if (!visited.add(entry.id())) throw new UserError.Io("ui_folder_contains_duplicate_or_circular_referen_8ca2ed", "文件夹包含重复或循环引用");
                if (entry.directory()) {
                    String book = directory.id().equals(root) ? notebookName(entry.name(), notebook) : directory.notebook();
                    pending.addLast(new Directory(entry.id(), book));
                } else if (isText(entry.name())) {
                    if (result.size() >= MAX_FILES) throw new UserError.Io("ui_import_up_to_1_000_documents_at_a_time_cd02ea", "一次最多导入 1,000 篇文稿");
                    try (InputStream input = tree.open(entry.id())) {
                        result.add(new Item(title(entry.name()), budget.read(input), directory.notebook()));
                        monitor.check(); monitor.progress(entries, result.size(), budget.total);
                    }
                }
            }
        }
        monitor.check();
        if (result.isEmpty()) throw new UserError.Io("ui_no_markdown_or_txt_documents_in_this_folder_dcc103", "文件夹内没有 Markdown 或 TXT 文稿");
        return result;
    }
    /** Compare arbitrary-length ASCII number runs without integer overflow. */
    static int naturalOrder(String a, String b) {
        int i = 0, j = 0, padding = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (x >= '0' && x <= '9' && y >= '0' && y <= '9') {
                int ai = i, bj = j;
                while (i < a.length() && a.charAt(i) >= '0' && a.charAt(i) <= '9') i++;
                while (j < b.length() && b.charAt(j) >= '0' && b.charAt(j) <= '9') j++;
                int az = ai, bz = bj;
                while (az < i && a.charAt(az) == '0') az++;
                while (bz < j && b.charAt(bz) == '0') bz++;
                int order = Integer.compare(i - az, j - bz);
                if (order != 0) return order;
                for (int k = 0; k < i - az; k++) if (a.charAt(az + k) != b.charAt(bz + k)) return Character.compare(a.charAt(az + k), b.charAt(bz + k));
                if (padding == 0) padding = Integer.compare(i - ai, j - bj);
            } else {
                if (x != y) return Character.compare(x, y);
                i++; j++;
            }
        }
        int remaining = Integer.compare(a.length() - i, b.length() - j);
        return remaining != 0 ? remaining : padding;
    }
    static boolean isText(String name) { return name != null && name.matches("(?i).+\\.(md|txt|markdown)$"); }
    static String title(String name) { return name.replaceFirst("(?i)\\.(md|txt|markdown)$", ""); }
    private static String notebookName(String name, String fallback) {
        name = name.trim();
        if (name.isEmpty() || name.equals(Library.ALL) || name.equals(Library.FAVORITES) || name.equals(Library.TRASH)) return fallback;
        int end = Math.min(40, name.length());
        if (end < name.length() && Character.isHighSurrogate(name.charAt(end - 1))) end--;
        return name.substring(0, end);
    }
    static final class Budget {
        private long total;
        private final Monitor monitor;
        Budget() { this(new Monitor() {}); }
        Budget(Monitor monitor) { this.monitor = monitor; }
        String read(InputStream input) throws Exception {
            monitor.check();
            if (input == null) throw new UserError.Io("ui_cannot_open_the_document_b9c30e", "无法打开文稿");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] bytes = new byte[8192]; int read;
            while ((read = input.read(bytes)) != -1) {
                monitor.check();
                total += read;
                if (buffer.size() + read > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) throw new UserError.Io("ui_limits_2_mb_per_document_and_64_mb_per_import_2c9ab6", "单篇上限 2 MB，一次导入总量上限 64 MB");
                buffer.write(bytes, 0, read);
            }
            monitor.check();
            return LibraryRepository.decodeUtf8(buffer.toByteArray());
        }
    }
    static List<Item> zip(InputStream input) throws Exception {
        List<Item> result = new ArrayList<>(); long total = 0; int entries = 0;
        try (ZipInputStream archive = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++entries > 5000) throw new UserError.Io("ui_zip_has_more_than_5_000_entries_a52470", "ZIP 文件条目超过 5,000 个");
                String path = entry.getName().replace('\\', '/');
                if (path.startsWith("/") || java.util.Arrays.asList(path.split("/")).contains("..")) throw new UserError.Io("ui_zip_contains_an_invalid_path_520cf4", "ZIP 包含无效路径");
                if (entry.isDirectory()) continue;
                boolean text = path.matches("(?i).+\\.(md|txt|markdown)$");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] bytes = new byte[8192]; int read;
                while ((read = archive.read(bytes)) != -1) {
                    total += read; if (total > 64L * 1024 * 1024) throw new UserError.Io("ui_uncompressed_zip_exceeds_64_mb_911f3a", "ZIP 解压后超过 64 MB");
                    if (text) { if (buffer.size() + read > 2 * 1024 * 1024) throw new UserError.Io("ui_each_imported_document_must_be_2_mb_or_smalle_63ab09", "单篇导入上限为 2 MB"); buffer.write(bytes, 0, read); }
                }
                if (!text || path.startsWith("__MACOSX/") || path.contains("/.")) continue;
                if (result.size() >= 1000) throw new UserError.Io("ui_import_up_to_1_000_documents_at_a_time_cd02ea", "一次最多导入 1,000 篇文稿");
                String[] segments = path.split("/"); String file = segments[segments.length - 1];
                String title = file.replaceFirst("(?i)\\.(md|txt|markdown)$", "").replaceFirst("-[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}$", "");
                String notebook = segments.length > 1 ? segments[0].trim() : "";
                if (notebook.length() > 40) notebook = notebook.substring(0, 40);
                result.add(new Item(title, LibraryRepository.decodeUtf8(buffer.toByteArray()), notebook));
            }
        }
        if (result.isEmpty()) throw new UserError.Io("ui_no_markdown_or_txt_documents_in_this_zip_a5fc04", "ZIP 内没有 Markdown 或 TXT 文稿");
        return result;
    }
    private TextImport() {}
}
