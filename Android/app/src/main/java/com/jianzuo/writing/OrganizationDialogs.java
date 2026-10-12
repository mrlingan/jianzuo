package com.jianzuo.writing;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Organizing dialogs share the Activity's library; every mutation uses its write/lock guard. */
final class OrganizationDialogs {
    private final Activity host; private final Library library; private final Runnable changed; private final BooleanSupplier writable;
    OrganizationDialogs(Activity host, Library library, Runnable changed, BooleanSupplier writable) { this.host = host; this.library = library; this.changed = changed; this.writable = writable; }
    private boolean apply(Runnable action) {
        if (!writable.getAsBoolean()) return false;
        try { action.run(); changed.run(); return true; } catch (IllegalArgumentException error) { error(UiText.error(host, error)); return false; }
    }
    private void error(String message) { new AlertDialog.Builder(host).setTitle(s(R.string.ui_cannot_complete_this_action_1f0a01)).setMessage(message).setPositiveButton(s(R.string.ui_ok_de32e2), null).show(); }
    private int dp(int value) { return Math.round(value * host.getResources().getDisplayMetrics().density); }
    private void name(String title, String original, Consumer<String> accept) {
        EditText input = new EditText(host); input.setSingleLine(true); input.setHint(s(R.string.ui_volume_name_b364b6)); input.setContentDescription(s(R.string.ui_volume_name_b364b6)); input.setText(original);
        AlertDialog dialog = new AlertDialog.Builder(host).setTitle(title).setView(input).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_save_a3030b), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { if (apply(() -> accept.accept(input.getText().toString().trim()))) dialog.dismiss(); })); dialog.show();
    }
    void manage(String notebook) {
        library.reconcileSections(); List<Section> sections = library.sections(notebook); String[] labels = new String[sections.size() + 1]; labels[0] = s(R.string.ui_new_volume_8fc74c);
        for (int i = 0; i < sections.size(); i++) { Section s = sections.get(i); labels[i + 1] = s.name + " · " + library.chapters(s, false).size() + s(R.string.ui_documents_785486); }
        new AlertDialog.Builder(host).setTitle(s(R.string.ui_manage_volumes_15d2f7) + notebook).setItems(labels, (dialog, which) -> {
            if (which == 0) name(s(R.string.ui_new_volume_7f2408), "", value -> library.addSection(notebook, value)); else section(sections.get(which - 1));
        }).setNegativeButton(s(R.string.ui_done_c0b3fb), null).show();
    }
    void section(Section section) {
        String[] actions = {s(R.string.ui_rename_volume_4c0e0b), s(R.string.ui_move_volume_up_18b28f), s(R.string.ui_move_volume_down_d17113), s(R.string.ui_move_volume_to_another_notebook_00e842), s(R.string.ui_move_volume_documents_to_trash_630529), s(R.string.ui_restore_trashed_documents_in_this_volume_f920bc), s(R.string.ui_dissolve_volume_keep_documents_b8e9ed)};
        new AlertDialog.Builder(host).setTitle(section.name).setItems(actions, (dialog, which) -> {
            switch (which) {
                case 0 -> name(s(R.string.ui_rename_volume_4c0e0b), section.name, value -> library.renameSection(section, value));
                case 1, 2 -> apply(() -> library.moveSection(section, which == 1 ? -1 : 1));
                case 3 -> new AlertDialog.Builder(host).setTitle(s(R.string.ui_move_volume_to_including_trashed_chapters_5f7f62)).setItems(library.notebooks.toArray(new String[0]), (d, index) -> apply(() -> library.moveSectionToNotebook(section, library.notebooks.get(index)))).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
                case 4 -> new AlertDialog.Builder(host).setTitle(s(R.string.ui_move_all_documents_in_this_volume_to_trash_5a0dd0)).setMessage(s(R.string.ui_the_volume_is_kept_text_and_revision_history_acbc56)).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_move_to_trash_fa5e19), (d, button) -> apply(() -> { for (Document doc : library.chapters(section, false)) library.trash(doc); })).show();
                case 5 -> apply(() -> { for (Document doc : library.chapters(section, true)) if (doc.deletedAt != null) library.restore(doc); });
                case 6 -> new AlertDialog.Builder(host).setTitle(s(R.string.ui_dissolve_fbb8f0) + section.name + "」？").setMessage(s(R.string.ui_all_documents_in_this_volume_including_trash_e77fca)).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_dissolve_5c5510), (d, button) -> apply(() -> library.dissolveSection(section))).show();
            }
        }).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).show();
    }
    void numbering(String notebook) {
        LinearLayout panel = new LinearLayout(host); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView hint = new TextView(host); hint.setText(R.string.chapter_numbering_hint); panel.addView(hint);
        EditText pattern = new EditText(host); pattern.setSingleLine(true); pattern.setContentDescription(s(R.string.ui_chapter_numbering_template_a9e3a4)); pattern.setText(R.string.chapter_numbering_pattern); panel.addView(pattern);
        TextView fromLabel = new TextView(host); fromLabel.setText(s(R.string.ui_starting_number_87fe9c)); panel.addView(fromLabel);
        EditText start = new EditText(host); start.setInputType(InputType.TYPE_CLASS_NUMBER); start.setText("1"); start.setContentDescription(s(R.string.ui_starting_chapter_number_a11576)); panel.addView(start);
        TextView widthLabel = new TextView(host); widthLabel.setText(s(R.string.ui_minimum_number_of_digits_1_7_875fcb)); panel.addView(widthLabel);
        EditText width = new EditText(host); width.setInputType(InputType.TYPE_CLASS_NUMBER); width.setText("1"); panel.addView(width);
        List<Section> sections = library.sections(notebook); List<String> scopes = new ArrayList<>(); scopes.add(s(R.string.ui_entire_notebook_b94949)); scopes.add(s(R.string.ui_no_volume_212370)); for (Section s : sections) scopes.add(s.name);
        Spinner scope = new Spinner(host); ArrayAdapter<String> adapter = new ArrayAdapter<>(host, android.R.layout.simple_spinner_dropdown_item, scopes); scope.setAdapter(adapter); panel.addView(scope);
        CheckBox strip = new CheckBox(host); strip.setText(s(R.string.ui_remove_existing_chapter_numbers_at_the_start_843829)); panel.addView(strip);
        ScrollView scroll = new ScrollView(host); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(host).setTitle(s(R.string.ui_number_chapters_7ebcb1) + notebook).setView(scroll).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_preview_13d61f), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!writable.getAsBoolean()) return;
            try {
                List<Document> docs = library.bookDocuments(notebook); int selection = scope.getSelectedItemPosition();
                if (selection == 1) docs.removeIf(doc -> !doc.sectionId.isEmpty());
                if (selection > 1) docs.removeIf(doc -> !doc.sectionId.equals(sections.get(selection - 2).id));
                List<ChapterNaming.Change> plan = ChapterNaming.plan(docs, pattern.getText().toString(), Integer.parseInt(start.getText().toString()), Integer.parseInt(width.getText().toString()), strip.isChecked());
                if (plan.isEmpty()) { error(s(R.string.ui_no_documents_to_number_in_this_scope_c4f7fc)); return; }
                StringBuilder preview = new StringBuilder();
                for (int i = 0; i < Math.min(30, plan.size()); i++) preview.append(plan.get(i).before().isEmpty() ? s(R.string.ui_untitled_8c974d) : plan.get(i).before()).append("\n→ ").append(plan.get(i).after()).append("\n\n");
                if (plan.size() > 30) preview.append(s(R.string.ui_only_the_first_30_are_shown_confirmation_appl_624ee1)).append(plan.size()).append(s(R.string.ui_documents_6a5c8c));
                preview.append(s(R.string.ui_old_titles_and_text_are_kept_in_each_document_afd3c8));
                TextView text = new TextView(host); text.setText(preview); text.setTextIsSelectable(true); text.setPadding(dp(20), dp(10), dp(20), dp(10)); ScrollView review = new ScrollView(host); review.addView(text);
                new AlertDialog.Builder(host).setTitle(s(R.string.ui_numbering_preview_e08a7e) + plan.size() + s(R.string.ui_documents_785486)).setView(review).setNegativeButton(s(R.string.ui_cancel_2cd0f3), null).setPositiveButton(s(R.string.ui_apply_numbering_df384c), (confirm, button) -> { if (apply(() -> ChapterNaming.apply(library, plan))) dialog.dismiss(); }).show();
            } catch (IllegalArgumentException error) { error(UiText.error(host, error)); }
        })); dialog.show();
    }

    private String s(int id, Object... args) { return args.length == 0 ? host.getString(id) : host.getString(id, args); }
}
