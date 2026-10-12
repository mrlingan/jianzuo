package com.jianzuo.writing;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import java.util.List;
import java.util.UUID;

final class NotebookShortcuts {
    static final String ACTION = "com.jianzuo.writing.OPEN_NOTEBOOK", EXTRA = "notebook";
    static Intent intent(Context context, String name) {
        return new Intent(context, MainActivity.class).setAction(ACTION).putExtra(EXTRA, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    static String requested(Intent intent) { return intent != null && ACTION.equals(intent.getAction()) ? intent.getStringExtra(EXTRA) : null; }
    static boolean pin(Context context, String name) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) return false;
        String id = "book-" + UUID.randomUUID();
        for (ShortcutInfo existing : manager.getPinnedShortcuts()) if (name.equals(requested(existing.getIntent())) && existing.isEnabled()) { id = existing.getId(); break; }
        ShortcutInfo info = new ShortcutInfo.Builder(context, id).setShortLabel(name).setLongLabel(context.getString(R.string.open_notebook, name))
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher)).setIntent(intent(context, name)).build();
        manager.enableShortcuts(List.of(id)); manager.updateShortcuts(List.of(info));
        return manager.requestPinShortcut(info, null);
    }
    static void renamed(Context context, String old, String name) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class); if (manager == null) return;
        for (ShortcutInfo info : manager.getPinnedShortcuts()) if (old.equals(requested(info.getIntent()))) {
            manager.updateShortcuts(List.of(new ShortcutInfo.Builder(context, info.getId()).setShortLabel(name).setLongLabel(context.getString(R.string.open_notebook, name)).setIntent(intent(context, name)).build()));
        }
    }
    static void deleted(Context context, String name) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class); if (manager == null) return;
        for (ShortcutInfo info : manager.getPinnedShortcuts()) if (name.equals(requested(info.getIntent()))) manager.disableShortcuts(List.of(info.getId()), context.getString(R.string.ui_notebook_deleted_e1deed));
    }
    private NotebookShortcuts() {}
}
