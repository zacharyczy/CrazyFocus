package app.shortvideoblocker;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Records a short, content-free trace of the selected foreground video player's accessibility tree. */
@SuppressWarnings("deprecation")
public final class PagerProbe extends Instrumentation {
    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    @Override
    public void onStart() {
        String pkg = arguments.getString("player", "");
        if (!pkg.equals(FeedInspector.WECHAT) && !pkg.equals(FeedInspector.BILIBILI)) {
            finish(Activity.RESULT_CANCELED, new Bundle());
            return;
        }
        UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        StringBuilder report = new StringBuilder();
        automation.setOnAccessibilityEventListener(event -> {
            if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED
                    || !pkg.contentEquals(event.getPackageName())) {
                return;
            }
            synchronized (report) {
                if (report.length() < 16_000) {
                    report.append("\nscroll range=").append(event.getFromIndex()).append("..")
                            .append(event.getToIndex()).append(" delta=").append(event.getScrollDeltaY());
                    AccessibilityNodeInfo source = event.getSource();
                    if (source != null) {
                        describe(source, report, 1);
                        source.recycle();
                    }
                }
            }
        });

        long delay = Math.min(10_000L, Math.max(0L, Long.parseLong(arguments.getString("waitMs", "4000"))));
        for (int sample = 0; sample < 2; sample++) {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            synchronized (report) {
                if (root != null && pkg.contentEquals(root.getPackageName())) {
                    report.append("\nSample ").append(sample);
                    describe(root, report, 0);
                }
            }
            if (root != null) {
                root.recycle();
            }
            if (sample == 0) {
                SystemClock.sleep(delay);
            }
        }
        automation.setOnAccessibilityEventListener(null);
        Bundle result = new Bundle();
        result.putString("tree", report.toString());
        finish(Activity.RESULT_OK, result);
    }

    private void describe(AccessibilityNodeInfo node, StringBuilder report, int depth) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        report.append('\n');
        for (int level = 0; level < depth; level++) {
            report.append(' ');
        }
        report.append(node.getClassName()).append(" ").append(node.getViewIdResourceName()).append(" ")
                .append(bounds.toShortString())
                .append(node.isScrollable() ? " scrollable" : "")
                .append(node.isVisibleToUser() ? " visible" : "")
                .append(" children=").append(node.getChildCount());
        if (node.getCollectionItemInfo() != null) {
            report.append(" row=").append(node.getCollectionItemInfo().getRowIndex());
        }
        if (node.getText() != null && node.getText().length() > 0) {
            report.append(" text-length=").append(node.getText().length());
        }
        for (int index = 0; index < node.getChildCount() && report.length() < 16_000 && depth < 18; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                describe(child, report, depth + 1);
                child.recycle();
            }
        }
    }
}
