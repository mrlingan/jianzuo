package com.jianzuo.writing;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A recycled, editable import list; importing happens only after the final review. */
final class ImportPreview {
    private static final class Draft {
        String title; final TextImport.Item source; boolean selected = true;
        Draft(TextImport.Item source) { this.source = source; title = source.title(); }
    }
    static final class Session {
        final List<Draft> drafts = new ArrayList<>();
        Session(List<TextImport.Item> items) { for (TextImport.Item item : items) drafts.add(new Draft(item)); }
    }
    static void show(Activity activity, List<TextImport.Item> items, Consumer<List<TextImport.Item>> accept) {
        show(activity, new Session(items), accept, () -> {});
    }
    static AlertDialog show(Activity activity, Session session, Consumer<List<TextImport.Item>> accept, Runnable cancel) {
        List<Draft> drafts = session.drafts;
        final AlertDialog[] detailDialog = {null};
        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(padding, padding, padding, 0);
        TextView hint = new TextView(activity); hint.setText(activity.getString(R.string.ui_select_documents_to_import_tap_edit_to_previe_0749e3)); panel.addView(hint);
        int height = Math.min(Math.min(360, Math.max(120, activity.getResources().getConfiguration().screenHeightDp / 2)), Math.max(120, drafts.size() * 80));
        ListView list = new ListView(activity); list.setDividerHeight(0); panel.addView(list, new LinearLayout.LayoutParams(-1, Math.round(height * activity.getResources().getDisplayMetrics().density)));
        BaseAdapter adapter = new BaseAdapter() {
            public int getCount() { return drafts.size(); }
            public Object getItem(int position) { return drafts.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View recycled, ViewGroup parent) {
                Draft draft = drafts.get(position); LinearLayout row;
                if (recycled instanceof LinearLayout) row = (LinearLayout) recycled;
                else { row = new LinearLayout(activity); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    CheckBox selected = new CheckBox(activity); selected.setMaxLines(3); selected.setEllipsize(android.text.TextUtils.TruncateAt.END); row.addView(selected, new LinearLayout.LayoutParams(0, -2, 1));
                    Button edit = new Button(activity); edit.setText(activity.getString(R.string.ui_edit_051836)); row.addView(edit);
                }
                CheckBox selected = (CheckBox) row.getChildAt(0); selected.setOnCheckedChangeListener(null); selected.setChecked(draft.selected);
                selected.setText(activity.getString(R.string.import_item, position + 1, draft.title, WritingMetrics.count(draft.source.body()), draft.source.notebook()));
                selected.setOnCheckedChangeListener((button, checked) -> draft.selected = checked);
                row.getChildAt(1).setOnClickListener(v -> {
                    LinearLayout detail = new LinearLayout(activity); detail.setOrientation(LinearLayout.VERTICAL); detail.setPadding(padding, 0, padding, 0);
                    EditText title = new EditText(activity); title.setSingleLine(true); title.setHint(activity.getString(R.string.ui_title_after_import_d0c311)); title.setContentDescription(activity.getString(R.string.ui_import_title_a5644f)); title.setText(draft.title); detail.addView(title);
                    TextView label = new TextView(activity); label.setText(activity.getString(R.string.import_order, drafts.size())); detail.addView(label);
                    EditText order = new EditText(activity); order.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); order.setContentDescription(activity.getString(R.string.ui_import_order_cd2dce)); order.setText(String.valueOf(drafts.indexOf(draft) + 1)); detail.addView(order);
                    TextView preview = new TextView(activity); String body = draft.source.body();
                    preview.setText(body.length() > 1600 ? body.substring(0, 1600) + activity.getString(R.string.ui_preview_truncated_import_keeps_all_text_1544ab) : body); preview.setTextIsSelectable(true); preview.setTypeface(Typeface.SERIF); detail.addView(preview);
                    ScrollView scroll = new ScrollView(activity); scroll.addView(detail);
                    AlertDialog editDialog = new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.ui_import_document_preview_286459)).setView(scroll).setNegativeButton(activity.getString(R.string.ui_cancel_2cd0f3), null).setPositiveButton(activity.getString(R.string.ui_save_a3030b), null).create();
                    editDialog.setOnShowListener(d -> editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                        String value = title.getText().toString().trim(); if (value.isEmpty()) { title.setError(activity.getString(R.string.ui_enter_a_title_433da1)); return; }
                        int target; try { target = Integer.parseInt(order.getText().toString()); } catch (NumberFormatException e) { target = 0; }
                        if (target < 1 || target > drafts.size()) { order.setError(activity.getString(R.string.ui_order_is_outside_the_allowed_range_8a7c3d)); return; }
                        draft.title = value; drafts.remove(draft); drafts.add(target - 1, draft); notifyDataSetChanged(); editDialog.dismiss();
                    })); detailDialog[0] = editDialog; editDialog.show();
                }); return row;
            }
        }; list.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.ui_import_preview_ffc931) + drafts.size() + activity.getString(R.string.ui_documents_785486)).setView(panel).setNegativeButton(activity.getString(R.string.ui_cancel_2cd0f3), (d, which) -> cancel.run()).setNeutralButton(activity.getString(R.string.ui_select_all_clear_baf299), null).setPositiveButton(activity.getString(R.string.ui_import_selected_documents_ccce7a), null).create();
        dialog.setOnCancelListener(d -> cancel.run());
        dialog.setOnDismissListener(d -> { if (detailDialog[0] != null) detailDialog[0].dismiss(); });
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { boolean all = drafts.stream().allMatch(x -> x.selected); for (Draft draft : drafts) draft.selected = !all; adapter.notifyDataSetChanged(); });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                List<TextImport.Item> selected = new ArrayList<>();
                for (Draft draft : drafts) if (draft.selected) selected.add(new TextImport.Item(draft.title, draft.source.body(), draft.source.notebook()));
                if (selected.isEmpty()) { Toast.makeText(activity, activity.getString(R.string.ui_select_at_least_one_document_2e0485), Toast.LENGTH_SHORT).show(); return; }
                dialog.dismiss(); accept.accept(selected);
            });
        }); dialog.show(); return dialog;
    }
    private ImportPreview() {}
}
