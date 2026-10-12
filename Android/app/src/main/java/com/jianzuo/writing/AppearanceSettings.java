package com.jianzuo.writing;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import java.util.function.BooleanSupplier;

/** A draft inside Writing Settings; changing either picker only updates this preview. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed with a preference draft and live mode supplier, never from XML.
final class AppearanceSettings extends LinearLayout {
    private final Spinner theme, paper;
    private final PaperTextView sample;
    private final BooleanSupplier dark;
    AppearanceSettings(Context context, SharedPreferences preferences, BooleanSupplier dark) {
        super(context); this.dark = dark; setOrientation(VERTICAL);
        theme = picker(R.string.color_theme, R.array.color_themes, WritingAppearance.index(preferences.getString("colorTheme", "classic")));
        paper = picker(R.string.paper_style, R.array.paper_styles, preferences.getBoolean("ruledPaper", false) ? 1 : 0);
        sample = new PaperTextView(context); sample.setText(R.string.theme_sample); sample.setContentDescription(context.getString(R.string.theme_preview));
        sample.setTextSize(17); sample.setTypeface(Typeface.SERIF); sample.setPadding(dp(16), dp(12), dp(16), dp(12)); sample.setLineSpacing(dp(9), 1); sample.setMinLines(4);
        addView(sample, new LayoutParams(-1, -2));
        TextView note = new TextView(context); note.setText(R.string.paper_note); note.setTextSize(12); note.setPadding(0, dp(8), 0, dp(8)); addView(note);
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { refresh(); }
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        theme.setOnItemSelectedListener(listener); paper.setOnItemSelectedListener(listener); refresh();
    }
    String themeId() { return WritingAppearance.IDS[theme.getSelectedItemPosition()]; }
    boolean ruled() { return paper.getSelectedItemPosition() == 1; }
    void refresh() {
        WritingAppearance.Palette colors = WritingAppearance.palette(themeId(), dark.getAsBoolean());
        sample.setBackgroundColor(colors.paper()); sample.setTextColor(colors.ink()); sample.configurePaper(ruled(), colors.line());
    }
    private Spinner picker(int label, int choices, int selected) {
        TextView title = new TextView(getContext()); title.setText(label); title.setTextSize(13); title.setPadding(0, dp(14), 0, dp(4)); addView(title);
        Spinner spinner = new Spinner(getContext()); spinner.setContentDescription(getContext().getString(label));
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(), choices, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); spinner.setSelection(selected);
        addView(spinner, new LayoutParams(-1, dp(48))); return spinner;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
