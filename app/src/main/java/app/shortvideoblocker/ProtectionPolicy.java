package app.shortvideoblocker;

import java.time.LocalTime;

record ProtectionPolicy(Mode mode, int start, int end) {
    static final int DEFAULT_START = 22 * 60;
    static final int DEFAULT_END = 7 * 60;

    enum Mode {
        ON, OFF, SCHEDULED
    }

    boolean appliesAt(LocalTime time) {
        int minute = time.getHour() * 60 + time.getMinute();
        return switch (mode) {
            case ON -> true;
            case OFF -> false;
            case SCHEDULED -> containsMinute(minute);
        };
    }

    private boolean containsMinute(int minute) {
        if (start == end) {
            return false;
        }
        if (start < end) {
            return minute >= start && minute < end;
        }
        return minute >= start || minute < end;
    }
}
