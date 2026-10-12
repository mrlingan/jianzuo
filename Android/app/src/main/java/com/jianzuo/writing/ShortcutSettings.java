package com.jianzuo.writing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** A working copy: editing, dragging and reset are committed only by the final Save. */
final class ShortcutSettings {
    static void show(Activity activity, ShortcutConfig original, Consumer<ShortcutConfig> save) {
        List<ShortcutConfig.Item> entries = new ArrayList<>(original.items);
        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(padding, 0, padding, 0);
        TextView hint = new TextView(activity); hint.setText(activity.getString(R.string.ui_select_shortcuts_to_show_long_press_and_drag_cbf8a0)); panel.addView(hint);
        Spinner rows = new Spinner(activity); rows.setContentDescription(activity.getString(R.string.ui_shortcut_rows_c8742c)); rows.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, new String[]{activity.getString(R.string.ui_1_row_c84a4b), activity.getString(R.string.ui_2_rows_740c22), activity.getString(R.string.ui_3_rows_307337)})); rows.setSelection(original.rows - 1); panel.addView(rows);
        ListView list = new ListView(activity); list.setContentDescription(activity.getString(R.string.ui_shortcut_item_list_7533b0));
        int height = Math.max(120, Math.min(360, activity.getResources().getConfiguration().screenHeightDp / 2));
        panel.addView(list, new LinearLayout.LayoutParams(-1, Math.round(height * activity.getResources().getDisplayMetrics().density)));
        BaseAdapter adapter = new BaseAdapter() {
            public int getCount() { return entries.size(); }
            public Object getItem(int position) { return entries.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View recycled, ViewGroup parent) {
                ShortcutConfig.Item entry = entries.get(position);
                LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
                CheckBox enabled = new CheckBox(activity); enabled.setText(UiText.shortcut(activity, entry)); enabled.setChecked(entry.enabled()); enabled.setContentDescription(activity.getString(R.string.ui_show_shortcut_37a69d) + UiText.shortcut(activity, entry));
                enabled.setOnCheckedChangeListener((button, checked) -> { int index = indexOf(entries, entry.id()); if (index >= 0) entries.set(index, entries.get(index).enabled(checked)); });
                row.addView(enabled, new LinearLayout.LayoutParams(0, -2, 1));
                if (entry.custom()) { Button edit = new Button(activity); edit.setText(activity.getString(R.string.ui_edit_051836)); edit.setContentDescription(activity.getString(R.string.ui_edit_shortcut_ecc031) + UiText.shortcut(activity, entry)); edit.setOnClickListener(v -> edit(activity, entries, entry.id(), this::notifyDataSetChanged)); row.addView(edit); }
                Button handle = new Button(activity); handle.setText("≡"); handle.setContentDescription(activity.getString(R.string.ui_reorder_shortcut_7fd591) + UiText.shortcut(activity, entry)); row.addView(handle);
                handle.setOnLongClickListener(v -> v.startDragAndDrop(ClipData.newPlainText(activity.getString(R.string.ui_jianzuo_shortcut_7d32bd), ""), new View.DragShadowBuilder(v), entry.id(), 0));
                handle.setOnClickListener(v -> {
                    List<String> actions = new ArrayList<>(List.of(activity.getString(R.string.ui_move_to_first_511a29), activity.getString(R.string.ui_move_up_f853a7), activity.getString(R.string.ui_move_down_e75e8b), activity.getString(R.string.ui_move_to_last_327c3b))); if (entry.custom()) actions.add(activity.getString(R.string.ui_delete_this_item_63310b));
                    new AlertDialog.Builder(activity).setTitle(UiText.shortcut(activity, entry)).setItems(actions.toArray(new String[0]), (dialog, which) -> {
                        int from = indexOf(entries, entry.id()); if (from < 0) return;
                        if (which == 4) entries.remove(from);
                        else { int target = switch (which) { case 0 -> 0; case 1 -> Math.max(0, from - 1); case 2 -> Math.min(entries.size() - 1, from + 1); default -> entries.size() - 1; }; entries.add(target, entries.remove(from)); }
                        notifyDataSetChanged();
                    }).show();
                });
                row.setOnDragListener((view, event) -> {
                    if (!(event.getLocalState() instanceof String id) || indexOf(entries, id) < 0) return false;
                    if (event.getAction() == DragEvent.ACTION_DROP) {
                        int from = indexOf(entries, id), to = indexOf(entries, entry.id());
                        if (from != to && to >= 0) { ShortcutConfig.Item moved = entries.remove(from); entries.add(indexOf(entries, entry.id()), moved); notifyDataSetChanged(); }
                    }
                    return true;
                }); return row;
            }
        }; list.setAdapter(adapter);
        Button add = new Button(activity); add.setText(activity.getString(R.string.ui_add_shortcut_172ed2)); add.setOnClickListener(v -> edit(activity, entries, null, adapter::notifyDataSetChanged)); panel.addView(add);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.ui_shortcut_toolbar_3d54ce)).setView(panel).setNegativeButton(activity.getString(R.string.ui_cancel_2cd0f3), null).setNeutralButton(activity.getString(R.string.ui_reset_layout_77ef57), null).setPositiveButton(activity.getString(R.string.ui_save_a3030b), null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                List<ShortcutConfig.Item> custom = entries.stream().filter(ShortcutConfig.Item::custom).collect(java.util.stream.Collectors.toList()); entries.clear(); entries.addAll(ShortcutConfig.BUILTINS); entries.addAll(custom); rows.setSelection(0); adapter.notifyDataSetChanged();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try { ShortcutConfig config = new ShortcutConfig(entries, rows.getSelectedItemPosition() + 1); save.accept(config); dialog.dismiss(); }
                catch (IllegalArgumentException error) { Toast.makeText(activity, UiText.error(activity, error), Toast.LENGTH_SHORT).show(); }
            });
        }); dialog.show();
    }
    private static int indexOf(List<ShortcutConfig.Item> items, String id) { for (int i = 0; i < items.size(); i++) if (items.get(i).id().equals(id)) return i; return -1; }
    private static void edit(Activity activity, List<ShortcutConfig.Item> entries, String id, Runnable refresh) {
        int index = id == null ? -1 : indexOf(entries, id); ShortcutConfig.Item old = index < 0 ? null : entries.get(index);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density); panel.setPadding(padding, 0, padding, 0);
        EditText label = new EditText(activity); label.setSingleLine(true); label.setHint(activity.getString(R.string.ui_name_up_to_24_characters_593cbb)); label.setContentDescription(activity.getString(R.string.ui_shortcut_name_54ad5d)); panel.addView(label);
        EditText content = new EditText(activity); content.setHint(activity.getString(R.string.ui_text_to_insert_newlines_allowed_4abf26)); content.setMinLines(3); content.setMaxLines(6); content.setGravity(Gravity.TOP); content.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); content.setContentDescription(activity.getString(R.string.ui_shortcut_text_e7a1b5)); panel.addView(content);
        if (old != null) { label.setText(old.label()); content.setText(old.value()); }
        ScrollView scroll = new ScrollView(activity); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(old == null ? activity.getString(R.string.ui_add_shortcut_172ed2) : activity.getString(R.string.ui_edit_shortcut_8473a8)).setView(scroll).setNegativeButton(activity.getString(R.string.ui_cancel_2cd0f3), null).setPositiveButton(activity.getString(R.string.ui_done_c0b3fb), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ShortcutConfig.Item next = new ShortcutConfig.Item(old == null ? "custom:" + UUID.randomUUID() : old.id(), label.getText().toString().trim(), content.getText().toString(), old == null || old.enabled());
            List<ShortcutConfig.Item> candidate = new ArrayList<>(entries); if (index < 0) candidate.add(next); else candidate.set(index, next);
            try { new ShortcutConfig(candidate, 1); entries.clear(); entries.addAll(candidate); refresh.run(); dialog.dismiss(); }
            catch (IllegalArgumentException error) { content.setError(UiText.error(activity, error)); }
        })); dialog.show();
    }
    private ShortcutSettings() {}
}
