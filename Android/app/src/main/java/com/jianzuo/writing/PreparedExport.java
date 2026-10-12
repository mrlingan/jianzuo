package com.jianzuo.writing;

import android.graphics.Typeface;
import java.io.*;

/** Finish typesetting in private storage before truncating a user-selected destination. */
final class PreparedExport {
    static boolean supported(String kind) { return kind.equals("pdf") || kind.equals("pdf-source") || kind.equals("png") || kind.equals("png-source"); }
    static File render(File cache, ExportSnapshot.Payload payload, Typeface font) throws Exception {
        File folder = new File(cache, "pending-exports");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new UserError.Io("ui_cannot_prepare_the_rendered_file_cdc189", "无法准备排版文件");
        File file = File.createTempFile("render-", ".tmp", folder);
        try {
            try (OutputStream output = new FileOutputStream(file)) {
                boolean markdown = !payload.kind().endsWith("-source");
                if (payload.kind().startsWith("pdf")) DocumentExport.pdf(payload.title(), payload.body(), output, font, markdown);
                else if (payload.kind().startsWith("png")) DocumentExport.png(payload.title(), payload.body(), output, font, markdown);
                else throw new UserError.Io("ui_unsupported_rendering_format_accffa", "不支持的排版格式");
            }
            return file;
        } catch (Exception | Error error) { file.delete(); throw error; }
    }
    private PreparedExport() {}
}
