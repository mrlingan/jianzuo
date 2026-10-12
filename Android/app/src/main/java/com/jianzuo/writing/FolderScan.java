package com.jianzuo.writing;

import android.content.ContentResolver;
import android.net.Uri;
import java.util.List;
import java.util.concurrent.*;

/** Retains only application resources, never an Activity. Unresponsive providers cannot grow the pool. */
final class FolderScan {
    private static final ThreadPoolExecutor WORKERS = pool("folder-scan");
    private static final ThreadPoolExecutor CLOSERS = pool("folder-cancel");
    private static ThreadPoolExecutor pool(String name) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), task -> { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; });
        return executor;
    }
    final FolderImport.Control control = new FolderImport.Control();
    List<TextImport.Item> items;
    Exception error;
    volatile boolean complete;
    private Future<?> future;
    static FolderScan start(ContentResolver resolver, Uri uri, String notebook) {
        FolderScan scan = new FolderScan();
        scan.future = WORKERS.submit(() -> {
            try { scan.items = FolderImport.read(resolver, uri, notebook, scan.control); }
            catch (Exception error) { scan.error = error; }
            finally { scan.control.input = null; scan.complete = true; }
        });
        return scan;
    }
    void cancel() {
        control.cancelled = true;
        if (future != null) future.cancel(true);
        // Provider cancellation/stream closure can perform IPC, so keep it off the UI thread.
        try { CLOSERS.execute(control::release); } catch (RejectedExecutionException ignored) {}
    }
}
