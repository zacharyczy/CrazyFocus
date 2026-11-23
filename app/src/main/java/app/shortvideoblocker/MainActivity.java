package app.shortvideoblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refreshStatus;
    private ProtectionSettings settings;
    private TextView status;
    private TextView scheduleSummary;
    private Button permissions;
    private TextView permissionHelp;
    private LinearLayout schedule;
    private Button startTime;
    private Button endTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new ProtectionSettings(this);
        setContentView(createPage());
        setTitle(R.string.app_name);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private View createPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.background));
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        } else {
            scroll.setFitsSystemWindows(true);
        }

        LinearLayout page = column();
        page.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(page);

        add(page, createHeader(), 0);
        add(page, createStatusCard(), 18);
        if (!BuildConfig.ALWAYS_ON) {
            add(page, createModeCard(), 12);
        }
        add(page, createCoverageCard(), 12);
        add(page, createBatteryCard(), 12);

        TextView version = text(R.string.version_label, 13, R.color.text_secondary, false);
        version.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
        version.setGravity(Gravity.CENTER_HORIZONTAL);
        add(page, version, 18);
        return scroll;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_focus_mark);
        mark.setBackground(shape(R.color.brand_tile, 11));
        mark.setPadding(dp(6), dp(6), dp(6), dp(6));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView title = text(R.string.app_name, 24, R.color.text_primary, true);
        title.setAccessibilityHeading(true);
        var titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMarginStart(dp(12));
        header.addView(title, titleParams);
        return header;
    }

    private View createStatusCard() {
        LinearLayout card = card(R.color.surface_status);
        add(card, heading(R.string.section_status, 14, R.color.text_secondary), 0);
        status = text(R.string.status_permission, 26, R.color.text_primary, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(card, status, 4);
        scheduleSummary = text(R.string.schedule_status, 15, R.color.text_secondary, false);
        scheduleSummary.setVisibility(View.GONE);
        add(card, scheduleSummary, 5);

        permissionHelp = text(R.string.permission_help, 15, R.color.text_secondary, false);
        add(card, permissionHelp, 8);
        if (BuildConfig.ALWAYS_ON) {
            add(card, text(R.string.always_help, 15, R.color.text_secondary, false), 8);
        }
        permissions = button(R.string.permission_button, true);
        permissions.setOnClickListener(view -> openAccessibilitySettings());
        add(card, permissions, 12);
        return card;
    }

    private View createModeCard() {
        LinearLayout card = card(R.color.surface);
        add(card, heading(R.string.section_mode, 19, R.color.text_primary), 0);

        RadioGroup modes = new RadioGroup(this);
        RadioButton on = radio(R.string.mode_on);
        RadioButton off = radio(R.string.mode_off);
        RadioButton scheduled = radio(R.string.mode_scheduled);
        modes.addView(on);
        modes.addView(off);
        modes.addView(scheduled);
        add(card, modes, 8);

        schedule = column();
        add(card, schedule, 8);
        startTime = button(R.string.schedule_start, false);
        endTime = button(R.string.schedule_end, false);
        startTime.setOnClickListener(view -> chooseTime(true));
        endTime.setOnClickListener(view -> chooseTime(false));
        add(schedule, startTime, 0);
        add(schedule, endTime, 4);
        add(schedule, text(R.string.schedule_help, 14, R.color.text_secondary, false), 7);

        modes.check(switch (settings.read().mode()) {
            case ON -> on.getId();
            case OFF -> off.getId();
            case SCHEDULED -> scheduled.getId();
        });
        modes.setOnCheckedChangeListener((group, checked) -> {
            ProtectionPolicy.Mode mode = ProtectionPolicy.Mode.ON;
            if (checked == off.getId()) {
                mode = ProtectionPolicy.Mode.OFF;
            } else if (checked == scheduled.getId()) {
                mode = ProtectionPolicy.Mode.SCHEDULED;
            }
            ProtectionPolicy old = settings.read();
            settings.write(new ProtectionPolicy(mode, old.start(), old.end()));
            refreshStatus();
        });
        return card;
    }

    private View createCoverageCard() {
        LinearLayout card = card(R.color.surface);
        add(card, heading(R.string.section_coverage, 19, R.color.text_primary), 0);
        add(card, heading(R.string.coverage_wechat, 16, R.color.text_primary), 12);
        add(card, text(R.string.coverage_wechat_detail, 15, R.color.text_secondary, false), 3);

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider));
        var line = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        line.topMargin = dp(12);
        card.addView(divider, line);

        add(card, heading(R.string.coverage_bilibili, 16, R.color.text_primary), 12);
        add(card, text(R.string.coverage_bilibili_detail, 15, R.color.text_secondary, false), 3);
        return card;
    }

    private View createBatteryCard() {
        LinearLayout card = card(R.color.surface);
        add(card, heading(R.string.section_battery, 19, R.color.text_primary), 0);
        add(card, text(R.string.battery_help, 15, R.color.text_secondary, false), 6);
        Button batterySettings = button(R.string.battery_button, false);
        batterySettings.setOnClickListener(view -> openAppSettings());
        add(card, batterySettings, 12);
        return card;
    }

    private void chooseTime(boolean start) {
        ProtectionPolicy old = settings.read();
        int value = start ? old.start() : old.end();
        TimePickerDialog dialog = new TimePickerDialog(this, (view, hour, minute) -> {
            int selected = hour * 60 + minute;
            int newStart = start ? selected : old.start();
            int newEnd = start ? old.end() : selected;
            if (newStart == newEnd) {
                Toast.makeText(this, R.string.schedule_same_time, Toast.LENGTH_SHORT).show();
                return;
            }
            settings.write(new ProtectionPolicy(old.mode(), newStart, newEnd));
            refreshStatus();
        }, value / 60, value % 60, true);
        dialog.setTitle(start ? R.string.schedule_start : R.string.schedule_end);
        dialog.show();
    }

    private void refreshStatus() {
        ProtectionPolicy policy = settings.read();
        boolean granted = hasAccessibilityPermission();
        LocalTime now = LocalTime.now();
        int statusLabel = R.string.status_permission;
        if (granted) {
            if (policy.appliesAt(now)) {
                statusLabel = R.string.status_on;
            } else if (policy.mode() == ProtectionPolicy.Mode.SCHEDULED) {
                statusLabel = R.string.status_scheduled_off;
            } else {
                statusLabel = R.string.status_off;
            }
        }
        if (!status.getText().equals(getText(statusLabel))) {
            status.setText(statusLabel);
        }
        permissions.setVisibility(granted ? View.GONE : View.VISIBLE);
        permissionHelp.setVisibility(granted ? View.GONE : View.VISIBLE);
        boolean scheduled = policy.mode() == ProtectionPolicy.Mode.SCHEDULED;
        scheduleSummary.setVisibility(scheduled ? View.VISIBLE : View.GONE);
        if (scheduled) {
            String end = clockTime(policy.end());
            if (policy.end() < policy.start()) {
                end = getString(R.string.schedule_next_day, end);
            }
            scheduleSummary.setText(getString(R.string.schedule_status, clockTime(policy.start()), end));
        }
        if (schedule != null) {
            schedule.setVisibility(scheduled ? View.VISIBLE : View.GONE);
            startTime.setText(scheduleLabel(R.string.schedule_start, policy.start()));
            endTime.setText(scheduleLabel(R.string.schedule_end, policy.end()));
        }
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 60_000L - (System.currentTimeMillis() % 60_000L));
    }

    private boolean hasAccessibilityPermission() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName service = new ComponentName(this, ShortVideoService.class);
        for (String entry : enabled.split(":")) {
            if (service.equals(ComponentName.unflattenFromString(entry))) {
                return true;
            }
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.permission_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        } catch (ActivityNotFoundException | SecurityException exception) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.section_battery)
                    .setMessage(R.string.battery_unavailable)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private String scheduleLabel(int label, int value) {
        return getString(R.string.schedule_value, getString(label), clockTime(value));
    }

    private String clockTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60).format(CLOCK);
    }

    private RadioButton radio(int label) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setTextSize(16);
        button.setTextColor(getColor(R.color.text_primary));
        button.setButtonTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        return button;
    }

    private Button button(int label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setTextSize(16);
        button.setTextColor(getColor(primary ? R.color.on_accent : R.color.text_primary));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.ripple)),
                shape(primary ? R.color.accent : R.color.surface_control, 12), null));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        return button;
    }

    private TextView heading(int label, int size, int color) {
        TextView heading = text(label, size, color, true);
        heading.setAccessibilityHeading(true);
        return heading;
    }

    private TextView text(int label, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(size);
        text.setTextColor(getColor(color));
        text.setLineSpacing(dp(2), 1);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private LinearLayout card(int color) {
        LinearLayout card = column();
        GradientDrawable background = shape(color, 18);
        background.setStroke(dp(1), getColor(R.color.divider));
        card.setBackground(background);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        return card;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(getColor(color));
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private void add(LinearLayout parent, View child, int marginTop) {
        var params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginTop);
        parent.addView(child, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
