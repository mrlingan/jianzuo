package com.jianzuo.writing;

import android.content.Context;
import java.util.Locale;

final class UiText {
    static String error(Context context, Throwable error) {
        if (error instanceof UserError.Tagged tagged) {
            int id = resource(tagged.key());
            String raw = error.getMessage();
            String source = AppLanguage.localized(context, Locale.SIMPLIFIED_CHINESE).getString(id);
            int index = raw.indexOf(source);
            return index < 0 ? context.getString(id) : raw.substring(0, index) + context.getString(id) + raw.substring(index + source.length());
        }
        if (error.getCause() != null) return error(context, error.getCause());
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    private static int resource(String key) {
        return switch (key) {
            case "ui_a_volume_with_this_name_already_exists_in_the_228d85" -> R.string.ui_a_volume_with_this_name_already_exists_in_the_228d85;
            case "ui_backup_validation_failed_the_file_may_be_inco_87232d" -> R.string.ui_backup_validation_failed_the_file_may_be_inco_87232d;
            case "ui_built_in_shortcut_missing_9d9393" -> R.string.ui_built_in_shortcut_missing_9d9393;
            case "ui_cannot_create_the_backup_folder_84da05" -> R.string.ui_cannot_create_the_backup_folder_84da05;
            case "ui_cannot_open_the_document_b9c30e" -> R.string.ui_cannot_open_the_document_b9c30e;
            case "ui_cannot_paginate_this_content_export_separate_f25c4e" -> R.string.ui_cannot_paginate_this_content_export_separate_f25c4e;
            case "ui_cannot_prepare_the_export_file_16295b" -> R.string.ui_cannot_prepare_the_export_file_16295b;
            case "ui_cannot_prepare_the_rendered_file_cdc189" -> R.string.ui_cannot_prepare_the_rendered_file_cdc189;
            case "ui_cannot_read_the_folder_check_access_permissio_9b7406" -> R.string.ui_cannot_read_the_folder_check_access_permissio_9b7406;
            case "ui_choose_1_3_rows_e2da4a" -> R.string.ui_choose_1_3_rows_e2da4a;
            case "ui_choose_a_jianzuo_jzb_backup_a7a4b0" -> R.string.ui_choose_a_jianzuo_jzb_backup_a7a4b0;
            case "ui_current_library_validation_failed_nothing_was_07654a" -> R.string.ui_current_library_validation_failed_nothing_was_07654a;
            case "ui_deletion_time_missing_6562dd" -> R.string.ui_deletion_time_missing_6562dd;
            case "ui_document_exceeds_1_000_pages_export_separate_000f0c" -> R.string.ui_document_exceeds_1_000_pages_export_separate_000f0c;
            case "ui_documents_have_changed_preview_again_no_title_1d1fe8" -> R.string.ui_documents_have_changed_preview_again_no_title_1d1fe8;
            case "ui_duplicate_document_id_6f10c7" -> R.string.ui_duplicate_document_id_6f10c7;
            case "ui_duplicate_shortcut_5681d5" -> R.string.ui_duplicate_shortcut_5681d5;
            case "ui_each_imported_document_must_be_2_mb_or_smalle_63ab09" -> R.string.ui_each_imported_document_must_be_2_mb_or_smalle_63ab09;
            case "ui_enter_a_keyword_from_the_title_or_text_to_rec_25172b" -> R.string.ui_enter_a_keyword_from_the_title_or_text_to_rec_25172b;
            case "ui_enter_text_to_find_aa7fce" -> R.string.ui_enter_text_to_find_aa7fce;
            case "ui_enter_text_to_insert_a1739e" -> R.string.ui_enter_text_to_insert_a1739e;
            case "ui_folder_contains_an_invalid_entry_bb0df1" -> R.string.ui_folder_contains_an_invalid_entry_bb0df1;
            case "ui_folder_contains_duplicate_or_circular_referen_8ca2ed" -> R.string.ui_folder_contains_duplicate_or_circular_referen_8ca2ed;
            case "ui_folder_has_more_than_5_000_entries_4e4289" -> R.string.ui_folder_has_more_than_5_000_entries_4e4289;
            case "ui_generated_title_is_empty_or_exceeds_1_000_cha_ee70ef" -> R.string.ui_generated_title_is_empty_or_exceeds_1_000_cha_ee70ef;
            case "ui_image_exceeds_16_000_pixels_split_into_chapte_2674d0" -> R.string.ui_image_exceeds_16_000_pixels_split_into_chapte_2674d0;
            case "ui_image_export_failed_2ce917" -> R.string.ui_image_export_failed_2ce917;
            case "ui_import_up_to_1_000_documents_at_a_time_cd02ea" -> R.string.ui_import_up_to_1_000_documents_at_a_time_cd02ea;
            case "ui_invalid_daily_statistics_517d92" -> R.string.ui_invalid_daily_statistics_517d92;
            case "ui_invalid_document_id_53b237" -> R.string.ui_invalid_document_id_53b237;
            case "ui_invalid_document_volume_reference_c9a91f" -> R.string.ui_invalid_document_volume_reference_c9a91f;
            case "ui_invalid_export_snapshot_id_3261c7" -> R.string.ui_invalid_export_snapshot_id_3261c7;
            case "ui_invalid_match_position_a31f09" -> R.string.ui_invalid_match_position_a31f09;
            case "ui_invalid_notebook_3ff57f" -> R.string.ui_invalid_notebook_3ff57f;
            case "ui_invalid_or_duplicate_volume_e76465" -> R.string.ui_invalid_or_duplicate_volume_e76465;
            case "ui_invalid_revision_time_4349de" -> R.string.ui_invalid_revision_time_4349de;
            case "ui_invalid_time_25b9d2" -> R.string.ui_invalid_time_25b9d2;
            case "ui_invalid_volume_id_7c9248" -> R.string.ui_invalid_volume_id_7c9248;
            case "ui_invalid_writing_time_date_bfa6fd" -> R.string.ui_invalid_writing_time_date_bfa6fd;
            case "ui_invalid_writing_time_e3e780" -> R.string.ui_invalid_writing_time_e3e780;
            case "ui_library_loading_failed_original_file_preserve_b33994" -> R.string.ui_library_loading_failed_original_file_preserve_b33994;
            case "ui_limits_2_mb_per_document_and_64_mb_per_import_2c9ab6" -> R.string.ui_limits_2_mb_per_document_and_64_mb_per_import_2c9ab6;
            case "ui_more_than_10_000_matches_narrow_the_search_d62f33" -> R.string.ui_more_than_10_000_matches_narrow_the_search_d62f33;
            case "ui_move_all_documents_out_first_including_trash_d3e3d9" -> R.string.ui_move_all_documents_out_first_including_trash_d3e3d9;
            case "ui_must_be_text_5b4104" -> R.string.ui_must_be_text_5b4104;
            case "ui_no_markdown_or_txt_documents_in_this_folder_dcc103" -> R.string.ui_no_markdown_or_txt_documents_in_this_folder_dcc103;
            case "ui_no_markdown_or_txt_documents_in_this_zip_a5fc04" -> R.string.ui_no_markdown_or_txt_documents_in_this_zip_a5fc04;
            case "ui_no_text_or_markdown_txt_files_to_import_1487ea" -> R.string.ui_no_text_or_markdown_txt_files_to_import_1487ea;
            case "ui_notebook_does_not_exist_2c2aa6" -> R.string.ui_notebook_does_not_exist_2c2aa6;
            case "ui_notebook_missing_0649d8" -> R.string.ui_notebook_missing_0649d8;
            case "ui_notebook_name_is_empty_duplicated_or_too_long_c1791d" -> R.string.ui_notebook_name_is_empty_duplicated_or_too_long_c1791d;
            case "ui_notebook_or_volume_does_not_exist_3dabec" -> R.string.ui_notebook_or_volume_does_not_exist_3dabec;
            case "ui_only_valid_documents_in_the_same_notebook_can_e02c55" -> R.string.ui_only_valid_documents_in_the_same_notebook_can_e02c55;
            case "ui_provide_a_text_file_through_the_system_file_p_9555d3" -> R.string.ui_provide_a_text_file_through_the_system_file_p_9555d3;
            case "ui_scan_cancelled_0e17a0" -> R.string.ui_scan_cancelled_0e17a0;
            case "ui_search_cancelled_b66582" -> R.string.ui_search_cancelled_b66582;
            case "ui_select_a_valid_chapter_first_2b7941" -> R.string.ui_select_a_valid_chapter_first_2b7941;
            case "ui_selection_count_does_not_match_3d3eff" -> R.string.ui_selection_count_does_not_match_3d3eff;
            case "ui_snapshot_exceeds_the_reading_limit_a7c917" -> R.string.ui_snapshot_exceeds_the_reading_limit_a7c917;
            case "ui_the_target_notebook_has_a_volume_with_this_na_e190df" -> R.string.ui_the_target_notebook_has_a_volume_with_this_na_e190df;
            case "ui_the_template_must_include_n_numbers_must_be_1_e25b49" -> R.string.ui_the_template_must_include_n_numbers_must_be_1_e25b49;
            case "ui_too_many_revisions_7bb748" -> R.string.ui_too_many_revisions_7bb748;
            case "ui_too_many_shortcuts_f487ca" -> R.string.ui_too_many_shortcuts_f487ca;
            case "ui_uncompressed_zip_exceeds_64_mb_911f3a" -> R.string.ui_uncompressed_zip_exceeds_64_mb_911f3a;
            case "ui_unknown_shortcut_2836a9" -> R.string.ui_unknown_shortcut_2836a9;
            case "ui_unknown_shortcut_toolbar_format_09692e" -> R.string.ui_unknown_shortcut_toolbar_format_09692e;
            case "ui_unsupported_library_version_868b38" -> R.string.ui_unsupported_library_version_868b38;
            case "ui_unsupported_rendering_format_accffa" -> R.string.ui_unsupported_rendering_format_accffa;
            case "ui_up_to_30_custom_shortcuts_with_8_000_characte_158f5a" -> R.string.ui_up_to_30_custom_shortcuts_with_8_000_characte_158f5a;
            case "ui_use_a_name_of_1_24_characters_without_newline_93a157" -> R.string.ui_use_a_name_of_1_24_characters_without_newline_93a157;
            case "ui_use_a_volume_name_of_1_80_characters_6ac18b" -> R.string.ui_use_a_volume_name_of_1_80_characters_6ac18b;
            case "ui_use_a_volume_name_of_1_80_characters_in_an_ex_ccb0a0" -> R.string.ui_use_a_volume_name_of_1_80_characters_in_an_ex_ccb0a0;
            case "ui_use_utf_8_files_with_a_md_markdown_or_txt_ext_29f0b9" -> R.string.ui_use_utf_8_files_with_a_md_markdown_or_txt_ext_29f0b9;
            case "ui_volume_and_document_are_not_in_the_same_noteb_c807bb" -> R.string.ui_volume_and_document_are_not_in_the_same_noteb_c807bb;
            case "ui_volume_does_not_exist_2bb75a" -> R.string.ui_volume_does_not_exist_2bb75a;
            case "ui_zip_contains_an_invalid_path_520cf4" -> R.string.ui_zip_contains_an_invalid_path_520cf4;
            case "ui_zip_has_more_than_5_000_entries_a52470" -> R.string.ui_zip_has_more_than_5_000_entries_a52470;
            default -> throw new IllegalArgumentException("Unknown presentation key");
        };
    }

    static String title(Context context, String title) { return title.trim().isEmpty() ? context.getString(R.string.untitled_document) : title; }
    static String filter(Context context, String key) {
        return key.equals(Library.ALL) ? context.getString(R.string.filter_all) : key.equals(Library.FAVORITES) ? context.getString(R.string.filter_favorites) : key.equals(Library.TRASH) ? context.getString(R.string.filter_trash) : key;
    }
    static String unit(Context context, WritingMetrics.Mode mode) { return context.getResources().getStringArray(R.array.count_units)[mode.ordinal()]; }
    static String mode(Context context, WritingMetrics.Mode mode) { return context.getResources().getStringArray(R.array.count_modes)[mode.ordinal()]; }
    static String time(Context context, long millis) {
        long seconds = Math.max(0, millis) / 1000, minutes = seconds / 60;
        return seconds < 60 ? context.getString(R.string.duration_seconds, seconds) : minutes < 60 ? context.getString(R.string.duration_minutes, minutes, seconds % 60) : context.getString(R.string.duration_hours, minutes / 60, minutes % 60);
    }
    static String shortcut(Context context, ShortcutConfig.Item item) {
        if (item.custom()) return item.label();
        return switch (item.id()) {
            case "undo" -> context.getString(R.string.shortcut_undo);
            case "redo" -> context.getString(R.string.shortcut_redo);
            case "indent" -> context.getString(R.string.ui_indent_66c740);
            case "date" -> context.getString(R.string.ui_date_70d0c1);
            case "heading" -> context.getString(R.string.ui_heading_326c5b);
            case "todo" -> context.getString(R.string.ui_task_52fdbb);
            case "find" -> context.getString(R.string.ui_find_300331);
            default -> item.label();
        };
    }
    static String source(Context context, String source) {
        return switch (source) {
            case "当前文稿" -> context.getString(R.string.ui_current_document_8ee6eb);
            case "废纸篓历史" -> context.getString(R.string.ui_trash_history_bf6f4b);
            case "废纸篓" -> context.getString(R.string.ui_trash_a9ca81);
            case "历史版本" -> context.getString(R.string.ui_revision_history_6fd295);
            default -> source.startsWith("自动备份 · ") ? context.getString(R.string.automatic_backup_source, source(context, source.substring(7))) : source;
        };
    }
    private UiText() {}
}
