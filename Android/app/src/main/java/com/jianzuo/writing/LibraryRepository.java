package com.jianzuo.writing;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class LibraryRepository {
    private final AtomicFile file;
    private final File backups;
    private long lastBackup;
    private boolean writable = true;
    public LibraryRepository(File directory) { file = new AtomicFile(new File(directory, "library.json")); backups = new File(directory, "backups"); }
    public synchronized Library load() throws Exception {
        try {
            File base = file.getBaseFile();
            if (!base.exists() && !new File(base.getPath() + ".bak").exists() && !new File(base.getPath() + ".new").exists()) {
                Library seeded = new Library(); seeded.seed();
                write(LibraryCodec.encode(seeded));
                return seeded;
            }
            return LibraryCodec.decode(decodeUtf8(file.readFully()));
        } catch (Exception error) { writable = false; throw error; }
    }
    public synchronized void write(String snapshot) throws IOException {
        if (!writable) throw new UserError.Io("ui_library_loading_failed_original_file_preserve_b33994", "文稿库读取失败，原文件已保留，禁止覆盖。");
        FileOutputStream stream = null;
        try {
            // Preserve the previous valid snapshot before committing a new one, at most every 5 minutes.
            if (file.getBaseFile().exists() && System.currentTimeMillis() - lastBackup >= 300_000) archiveCurrent();
            stream = file.startWrite();
            stream.write(snapshot.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (IOException error) { file.failWrite(stream); throw error; }
    }
    public synchronized File[] backups() {
        File[] files = backups.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName).reversed()); return files;
    }
    public synchronized void archiveCurrent() throws IOException {
        if (!writable || !file.getBaseFile().exists()) return;
        byte[] snapshot = file.readFully();
        try { LibraryCodec.decode(decodeUtf8(snapshot)); } catch (Exception error) { throw new UserError.Io("ui_current_library_validation_failed_nothing_was_07654a", "当前库校验失败，未覆盖", error); }
        if (!backups.exists() && !backups.mkdirs()) throw new UserError.Io("ui_cannot_create_the_backup_folder_84da05", "无法创建备份目录");
        AtomicFile backup = new AtomicFile(new File(backups, System.currentTimeMillis() + "-" + java.util.UUID.randomUUID() + ".json"));
        FileOutputStream output = null;
        try { output = backup.startWrite(); output.write(snapshot); backup.finishWrite(output); }
        catch (IOException error) { backup.failWrite(output); throw error; }
        lastBackup = System.currentTimeMillis();
        File[] files = backups(); for (int i = 24; i < files.length; i++) files[i].delete();
    }
    public boolean isWritable() { return writable; }
    public static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
