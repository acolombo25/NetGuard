package eu.faircode.netguard.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import eu.faircode.netguard.Theme;

public class DefaultPreferences {

    public static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void registerListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        getPreferences(context).registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unregisterListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        getPreferences(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static Map<String, ?> getAll(Context context) {
        return getPreferences(context).getAll();
    }

    public static boolean getBoolean(Context context, Preference<Boolean> preference) {
        return getPreferences(context).getBoolean(preference.getKey(), preference.getDefaultValue());
    }

    public static boolean getBoolean(Context context, Preference<Boolean> preference, boolean defaultValue) {
        return getPreferences(context).getBoolean(preference.getKey(), defaultValue);
    }

    public static boolean getBooleanNotDefault(Context context, Preference<Boolean> preference) {
        return getPreferences(context).getBoolean(preference.getKey(), !preference.getDefaultValue());
    }

    public static void putBoolean(Context context, Preference<Boolean> preference, boolean value) {
        Editor.get(context).putBoolean(preference.getKey(), value).apply();
    }

    public static void resetBoolean(Context context, Preference<Boolean> preference) {
        putBoolean(context, preference, preference.getDefaultValue());
    }

    public static void toggleBoolean(Context context, Preference<Boolean> preference) {
        putBoolean(context, preference, !getBoolean(context, preference));
    }

    public static boolean isNotDefault(Context context, Preference<Boolean> preference) {
        return getBoolean(context, preference) != preference.getDefaultValue();
    }

    public static long getLong(Context context, Preference<Long> preference) {
        return getPreferences(context).getLong(preference.getKey(), preference.getDefaultValue());
    }

    public static long getBoxedLong(Context context, Preference<Long> preference) {
        return Long.parseLong(getPreferences(context).getString(preference.getKey(), Long.toString(preference.getDefaultValue())));
    }

    public static void putLong(Context context, Preference<Long> preference, long value) {
        Editor.get(context).putLong(preference.getKey(), value).apply();
    }

    public static int getInt(Context context, Preference<Integer> preference) {
        return getPreferences(context).getInt(preference.getKey(), preference.getDefaultValue());
    }

    public static int getBoxedInt(Context context, Preference<Integer> preference) {
        return getBoxedInt(getPreferences(context), preference);
    }

    public static int getBoxedInt(SharedPreferences preferences, Preference<Integer> preference) {
        return Integer.parseInt(preferences.getString(preference.getKey(), Integer.toString(preference.getDefaultValue())));
    }

    public static void putInt(Context context, Preference<Boolean> preference, int value) {
        Editor.get(context).putInt(preference.getKey(), value).apply();
    }

    public static String getString(Context context, Preference<String> preference) {
        return getPreferences(context).getString(preference.getKey(), preference.getDefaultValue());
    }

    public static String getString(Context context, Preference<String> preference, String defaultValue) {
        return getPreferences(context).getString(preference.getKey(), defaultValue);
    }

    public static void putString(Context context, Preference<String> preference, String value) {
        Editor.get(context).putString(preference.getKey(), value).apply();
    }

    public static String getTheme(Context context, Preference<Theme> preference) {
        return getPreferences(context).getString(preference.getKey(), preference.getDefaultValue().getValue());
    }

    public static void putTheme(Context context, Preference<Theme> preference, Theme theme) {
        Editor.get(context).putString(preference.getKey(), theme.getValue()).apply();
    }

    public static void resetTheme(Context context, Preference<Theme> preference) {
        putTheme(context, preference, preference.getDefaultValue());
    }

    public static boolean isThemeDefault(Context context, Preference<Theme> preference) {
        return getTheme(context, preference).equals(preference.getDefaultValue().getValue());
    }

    public static Set<String> getStringSet(Context context, Preference<HashSet<String>> preference) {
        return getPreferences(context).getStringSet(preference.getKey(), preference.getDefaultValue());
    }

    public static String getSort(Context context, Preference<Sort> preference) {
        return getPreferences(context).getString(preference.getKey(), preference.getDefaultValue().getValue());
    }

    public static void putSort(Context context, Preference<Sort> preference, Sort value) {
        Editor.get(context).putString(preference.getKey(), value.getValue()).apply();
    }

    public static void remove(Context context, Preference<?> preference) {
        Editor.get(context).remove(preference.getKey()).apply();
    }

    // Editor

    public static class Editor {

        public static SharedPreferences.Editor get(Context context) {
            return getPreferences(context).edit();
        }

        public static void putBoolean(SharedPreferences.Editor editor, Preference<Boolean> preference, boolean value) {
            editor.putBoolean(preference.getKey(), value);
        }

        public static void resetBoolean(SharedPreferences.Editor editor, Preference<Boolean> preference) {
            editor.putBoolean(preference.getKey(), preference.getDefaultValue());
        }

        public static void putInt(SharedPreferences.Editor editor, Preference<Integer> preference, int value) {
            editor.putInt(preference.getKey(), value);
        }

        public static void remove(SharedPreferences.Editor editor, Preference<?> preference) {
            editor.remove(preference.getKey());
        }

    }

}
