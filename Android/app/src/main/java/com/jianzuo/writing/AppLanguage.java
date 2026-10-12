package com.jianzuo.writing;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;

/** Android 13+ owns the selection; older devices persist a local override. */
final class AppLanguage {
    static final String SYSTEM = "system", CHINESE = "zh-CN", ENGLISH = "en";
    static String normalize(String value) { return CHINESE.equals(value) || ENGLISH.equals(value) ? value : SYSTEM; }
    static Locale resolve(String selection, Locale system) {
        String value = normalize(selection);
        if (value.equals(CHINESE)) return Locale.SIMPLIFIED_CHINESE;
        if (value.equals(ENGLISH)) return Locale.ENGLISH;
        return system.getLanguage().equals("zh") ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
    }
    static String selection(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleList locales = context.getSystemService(LocaleManager.class).getApplicationLocales();
            if (locales.isEmpty()) return SYSTEM;
            return locales.get(0).getLanguage().equals("zh") ? CHINESE : ENGLISH;
        }
        return normalize(context.getSharedPreferences("writing", Context.MODE_PRIVATE).getString("language", SYSTEM));
    }
    static Context wrap(Context context) {
        LocaleList system = Build.VERSION.SDK_INT >= 33 ? context.getSystemService(LocaleManager.class).getSystemLocales() : context.getResources().getConfiguration().getLocales();
        return localized(context, resolve(selection(context), system.isEmpty() ? Locale.ENGLISH : system.get(0)));
    }
    static Context localized(Context context, Locale locale) {
        Configuration config = new Configuration(context.getResources().getConfiguration()); config.setLocales(new LocaleList(locale)); config.setLayoutDirection(locale);
        return context.createConfigurationContext(config);
    }
    static Locale locale(Context context) { return context.getResources().getConfiguration().getLocales().get(0); }
    static int index(Context context) { return switch (selection(context)) { case CHINESE -> 1; case ENGLISH -> 2; default -> 0; }; }
    static boolean set(Context context, int index) {
        String value = switch (index) { case 1 -> CHINESE; case 2 -> ENGLISH; default -> SYSTEM; };
        if (value.equals(selection(context))) return false;
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager.class).setApplicationLocales(value.equals(SYSTEM) ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(value));
        else context.getSharedPreferences("writing", Context.MODE_PRIVATE).edit().putString("language", value).apply();
        return true;
    }
    private AppLanguage() {}
}
