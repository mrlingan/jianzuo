package com.jianzuo.writing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Portable, self-contained backup. Integrity is checked before decoding or modifying the library. */
public final class BackupCodec {
    public static String encode(Library library) throws Exception {
        String payload = LibraryCodec.encode(library);
        return new JSONObject().put("format", "jianzuo-backup").put("version", 1)
            .put("createdAt", System.currentTimeMillis()).put("sha256", digest(payload)).put("payload", payload).toString(2);
    }
    public static Library decode(String source) throws Exception {
        JSONObject backup = new JSONObject(source);
        if (!"jianzuo-backup".equals(backup.getString("format")) || backup.getInt("version") != 1) throw new UserError.Invalid("ui_choose_a_jianzuo_jzb_backup_a7a4b0", "请选择简作 .jzb 备份");
        String payload = backup.getString("payload");
        if (!MessageDigest.isEqual(digest(payload).getBytes(StandardCharsets.UTF_8), backup.getString("sha256").getBytes(StandardCharsets.UTF_8))) throw new UserError.Invalid("ui_backup_validation_failed_the_file_may_be_inco_87232d", "备份校验失败，文件可能不完整");
        return LibraryCodec.decode(payload);
    }
    private static String digest(String source) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(); for (byte value : bytes) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }
    private BackupCodec() {}
}
