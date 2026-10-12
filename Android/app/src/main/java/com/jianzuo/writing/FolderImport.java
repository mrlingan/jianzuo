package com.jianzuo.writing;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.CancellationSignal;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Reads only the tree explicitly granted by the system folder picker. */
final class FolderImport {
    static final class Control implements TextImport.Monitor {
        final CancellationSignal signal = new CancellationSignal();
        volatile boolean cancelled;
        volatile InputStream input;
        volatile int entries, files;
        volatile long bytes;
        @Override public void check() throws java.io.InterruptedIOException {
            TextImport.Monitor.super.check();
            if (cancelled) throw new UserError.Cancelled("ui_scan_cancelled_0e17a0", "扫描已取消");
        }
        @Override public void progress(int entries, int files, long bytes) { this.entries = entries; this.files = files; this.bytes = bytes; }
        void release() {
            InputStream active = input;
            if (active != null) try { active.close(); } catch (IOException ignored) {}
            signal.cancel();
        }
    }
    static List<TextImport.Item> read(ContentResolver resolver, Uri tree, String notebook) throws Exception {
        return read(resolver, tree, notebook, new Control());
    }
    static List<TextImport.Item> read(ContentResolver resolver, Uri tree, String notebook, Control control) throws Exception {
        return TextImport.folder(new TextImport.Tree() {
            public List<TextImport.Entry> children(String id) throws Exception {
                control.check();
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
                String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
                List<TextImport.Entry> result = new ArrayList<>();
                try (Cursor cursor = resolver.query(children, columns, null, null, null, control.signal)) {
                    if (cursor == null) throw new UserError.Io("ui_cannot_read_the_folder_check_access_permissio_9b7406", "无法读取文件夹，请检查访问权限");
                    while (cursor.moveToNext()) {
                        control.check();
                        if (result.size() >= TextImport.MAX_ENTRIES) throw new UserError.Io("ui_folder_has_more_than_5_000_entries_4e4289", "文件夹条目超过 5,000 个");
                        String childId = cursor.getString(0), name = cursor.getString(1);
                        if (childId == null || name == null) throw new UserError.Io("ui_folder_contains_an_invalid_entry_bb0df1", "文件夹包含无效条目");
                        result.add(new TextImport.Entry(childId, name, DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))));
                    }
                }
                return result;
            }
            public InputStream open(String id) throws Exception {
                control.check();
                android.content.res.AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(DocumentsContract.buildDocumentUriUsingTree(tree, id), "r", control.signal);
                if (descriptor == null) throw new UserError.Io("ui_cannot_open_the_document_b9c30e", "无法打开文稿");
                try {
                    InputStream stream = descriptor.createInputStream(); control.input = stream;
                    try { control.check(); return stream; }
                    catch (Exception error) { stream.close(); throw error; }
                } catch (Exception error) { descriptor.close(); throw error; }
            }
        }, DocumentsContract.getTreeDocumentId(tree), notebook, control);
    }
    private FolderImport() {}
}
