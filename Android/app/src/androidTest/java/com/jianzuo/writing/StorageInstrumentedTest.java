package com.jianzuo.writing;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StorageInstrumentedTest {
    private File directory() {
        File folder = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "storage-test-" + UUID.randomUUID());
        assertTrue(folder.mkdirs()); return folder;
    }
    @Test public void corruptLibraryIsNeverOverwritten() throws Exception {
        File folder = directory(); File file = new File(folder, "library.json"); byte[] original = "corrupt but recoverable".getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(file.toPath(), original); LibraryRepository repo = new LibraryRepository(folder);
            assertThrows(Exception.class, repo::load); assertFalse(repo.isWritable());
            assertThrows(Exception.class, () -> repo.write(LibraryCodec.encode(new Library())));
            assertArrayEquals(original, Files.readAllBytes(file.toPath()));
        } finally { file.delete(); folder.delete(); }
    }
    @Test public void atomicWriteRoundTripAndEmptyLibrary() throws Exception {
        File folder = directory(); File file = new File(folder, "library.json");
        try {
            LibraryRepository repo = new LibraryRepository(folder); Library library = repo.load(); assertEquals(3, library.documents.size());
            Document doc = library.create("中文 ☔️", "# 𠀀\n\nHello", "日记"); doc.favorite = true;
            repo.write(LibraryCodec.encode(library)); Document restored = new LibraryRepository(folder).load().find(doc.id);
            assertEquals(doc.body, restored.body); assertTrue(restored.favorite);
            repo.write(LibraryCodec.encode(new Library())); assertTrue(new LibraryRepository(folder).load().documents.isEmpty());
        } finally { file.delete(); folder.delete(); }
    }
    @Test public void interruptedLegacyWriteRecoversBackup() throws Exception {
        File folder = directory(); File file = new File(folder, "library.json"), backup = new File(folder, "library.json.bak");
        try {
            Library library = new Library(); Document doc = library.create("备份", "不能丢失", "文稿");
            Files.writeString(backup.toPath(), LibraryCodec.encode(library)); Files.writeString(file.toPath(), "partial");
            assertEquals("不能丢失", new LibraryRepository(folder).load().find(doc.id).body);
        } finally { file.delete(); backup.delete(); folder.delete(); }
    }
    @Test public void utf8RejectsInvalidBytesAndAcceptsBom() throws Exception {
        assertEquals("你好", LibraryRepository.decodeUtf8("\uFEFF你好".getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> LibraryRepository.decodeUtf8(new byte[]{(byte) 0xff}));
    }
}
