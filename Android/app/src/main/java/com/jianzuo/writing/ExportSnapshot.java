package com.jianzuo.writing;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

/** Saved state contains only this snapshot's ID, never the manuscript or whole library. */
final class ExportSnapshot {
    record Payload(String title, String kind, String body) {}
    static String write(File cache, Payload payload) throws Exception {
        File folder = new File(cache, "pending-exports");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new UserError.Io("ui_cannot_prepare_the_export_file_16295b", "无法准备导出文件");
        // Abandoned picker sessions expire after a week. Active sessions are removed after use/cancel.
        File[] old = folder.listFiles();
        if (old != null) for (File file : old) if (System.currentTimeMillis() - file.lastModified() > 7L * 24 * 60 * 60 * 1000) file.delete();
        String id = UUID.randomUUID().toString(); AtomicFile file = file(cache, id);
        FileOutputStream output = null;
        try {
            byte[] bytes = new JSONObject().put("title", payload.title()).put("kind", payload.kind()).put("body", payload.body()).toString().getBytes(StandardCharsets.UTF_8);
            output = file.startWrite(); output.write(bytes); file.finishWrite(output); return id;
        } catch (Exception error) { file.failWrite(output); throw error; }
    }
    static Payload read(File cache, String id) throws Exception {
        JSONObject json = new JSONObject(LibraryRepository.decodeUtf8(file(cache, id).readFully()));
        return new Payload(json.getString("title"), json.getString("kind"), json.getString("body"));
    }
    static void delete(File cache, String id) { if (id != null) file(cache, id).delete(); }
    private static AtomicFile file(File cache, String id) {
        if (id == null || !id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) throw new UserError.Invalid("ui_invalid_export_snapshot_id_3261c7", "导出快照编号无效");
        return new AtomicFile(new File(new File(cache, "pending-exports"), id + ".json"));
    }
    private ExportSnapshot() {}
}
