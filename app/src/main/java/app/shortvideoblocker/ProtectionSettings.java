package app.shortvideoblocker;

import android.content.Context;
import android.content.SharedPreferences;

final class ProtectionSettings {
    private static final String MODE = "mode";
    private static final String START = "start_minutes";
    private static final String END = "end_minutes";

    private final SharedPreferences preferences;

    ProtectionSettings(Context context) {
        preferences = context.getSharedPreferences("protection", Context.MODE_PRIVATE);
    }

    ProtectionPolicy read() {
        ProtectionPolicy.Mode mode = BuildConfig.ALWAYS_ON
                ? ProtectionPolicy.Mode.ON
                : readMode(preferences.getString(MODE, "ON"));
        return new ProtectionPolicy(mode,
                preferences.getInt(START, ProtectionPolicy.DEFAULT_START),
                preferences.getInt(END, ProtectionPolicy.DEFAULT_END));
    }

    void write(ProtectionPolicy policy) {
        preferences.edit()
                .putString(MODE, policy.mode().name())
                .putInt(START, policy.start())
                .putInt(END, policy.end())
                .apply();
    }

    void register(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    void unregister(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private static ProtectionPolicy.Mode readMode(String mode) {
        return switch (mode) {
            case "OFF" -> ProtectionPolicy.Mode.OFF;
            case "SCHEDULED" -> ProtectionPolicy.Mode.SCHEDULED;
            default -> ProtectionPolicy.Mode.ON;
        };
    }
}
