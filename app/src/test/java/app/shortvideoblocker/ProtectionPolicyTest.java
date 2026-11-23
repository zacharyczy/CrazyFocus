package app.shortvideoblocker;

import org.junit.Test;

import java.time.LocalTime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProtectionPolicyTest {
    @Test
    public void manualModesIgnoreSchedule() {
        assertTrue(new ProtectionPolicy(ProtectionPolicy.Mode.ON, 0, 0).appliesAt(LocalTime.NOON));
        assertFalse(new ProtectionPolicy(ProtectionPolicy.Mode.OFF, 0, 1439).appliesAt(LocalTime.NOON));
    }

    @Test
    public void startIsInclusiveAndEndIsExclusive() {
        var policy = new ProtectionPolicy(ProtectionPolicy.Mode.SCHEDULED, 9 * 60, 17 * 60);
        assertFalse(policy.appliesAt(LocalTime.of(8, 59)));
        assertTrue(policy.appliesAt(LocalTime.of(9, 0)));
        assertTrue(policy.appliesAt(LocalTime.of(16, 59)));
        assertFalse(policy.appliesAt(LocalTime.of(17, 0)));
    }

    @Test
    public void overnightIntervalCoversBothSidesOfMidnight() {
        var policy = new ProtectionPolicy(ProtectionPolicy.Mode.SCHEDULED, 22 * 60, 7 * 60);
        assertFalse(policy.appliesAt(LocalTime.of(21, 59)));
        assertTrue(policy.appliesAt(LocalTime.of(22, 0)));
        assertTrue(policy.appliesAt(LocalTime.MIDNIGHT));
        assertTrue(policy.appliesAt(LocalTime.of(6, 59)));
        assertFalse(policy.appliesAt(LocalTime.of(7, 0)));
    }

    @Test
    public void equalTimesDoNotEnableProtection() {
        var policy = new ProtectionPolicy(ProtectionPolicy.Mode.SCHEDULED, 360, 360);
        assertFalse(policy.appliesAt(LocalTime.of(6, 0)));
    }
}
