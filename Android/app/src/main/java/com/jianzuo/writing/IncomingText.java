package com.jianzuo.writing;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class IncomingText {
    static boolean accepts(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        return Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action);
    }
    static List<TextImport.Item> read(android.content.Context context, Intent intent, String notebook) throws Exception {
        return read(context.getContentResolver(), intent, notebook, context.getString(R.string.ui_shared_text_556758));
    }
    static List<TextImport.Item> read(ContentResolver resolver, Intent intent, String notebook) throws Exception {
        return read(resolver, intent, notebook, "分享的文字");
    }
    @SuppressWarnings("deprecation")
    private static List<TextImport.Item> read(ContentResolver resolver, Intent intent, String notebook, String sharedTitle) throws Exception {
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) add(uris, intent.getData());
        else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) for (Uri uri : streams) add(uris, uri);
        } else add(uris, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        if (uris.isEmpty() && intent.getClipData() != null) for (int i = 0; i < intent.getClipData().getItemCount(); i++) add(uris, intent.getClipData().getItemAt(i).getUri());
        TextImport.Budget budget = new TextImport.Budget(); List<TextImport.Item> items = new ArrayList<>();
        for (Uri uri : uris) {
            if (!"content".equals(uri.getScheme())) throw new UserError.Io("ui_provide_a_text_file_through_the_system_file_p_9555d3", "请通过系统文件或分享入口提供文本文件");
            String name = null;
            try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
            if (!TextImport.isText(name)) throw new UserError.Io("ui_use_utf_8_files_with_a_md_markdown_or_txt_ext_29f0b9", "仅支持有 .md、.markdown 或 .txt 扩展名的 UTF-8 文本文件");
            try (InputStream input = resolver.openInputStream(uri)) { items.add(new TextImport.Item(TextImport.title(name), budget.read(input), notebook)); }
        }
        if (items.isEmpty() && Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.toString().isBlank()) {
                String body = shared.toString();
                if (body.length() > TextImport.MAX_FILE_BYTES) throw new UserError.Io("ui_each_imported_document_must_be_2_mb_or_smalle_63ab09", "单篇导入上限为 2 MB");
                body = budget.read(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                CharSequence subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
                String title = subject == null || subject.toString().isBlank() ? sharedTitle : subject.toString().trim();
                int end = Math.min(200, title.length()); if (end < title.length() && Character.isHighSurrogate(title.charAt(end - 1))) end--;
                items.add(new TextImport.Item(title.substring(0, end), body, notebook));
            }
        }
        if (items.isEmpty()) throw new UserError.Io("ui_no_text_or_markdown_txt_files_to_import_1487ea", "没有可导入的文字或 Markdown / TXT 文件");
        return items;
    }
    private static void add(List<Uri> uris, Uri uri) throws IOException {
        if (uri != null && !uris.contains(uri)) {
            if (uris.size() >= TextImport.MAX_FILES) throw new UserError.Io("ui_import_up_to_1_000_documents_at_a_time_cd02ea", "一次最多导入 1,000 篇文稿");
            uris.add(uri);
        }
    }
    private IncomingText() {}
}
