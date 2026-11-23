package app.shortvideoblocker;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.time.LocalTime;
import java.util.List;

@SuppressWarnings("deprecation") // recycle() is required on API 29–32.
public final class ShortVideoService extends AccessibilityService {
    private static final String TAG = "ShortVideoBlocker";
    private static final String STORY_ACTIVITY = "com.bilibili.video.story.StoryVideoActivity";
    private static final String SHARED_FINDER_ACTIVITY = "com.tencent.mm.plugin.finder.ui.FinderShareFeedRelUI";
    private static final int WINDOW_TRANSITIONS = AccessibilityEvent.WINDOWS_CHANGE_ADDED
            | AccessibilityEvent.WINDOWS_CHANGE_REMOVED | AccessibilityEvent.WINDOWS_CHANGE_ACTIVE
            | AccessibilityEvent.WINDOWS_CHANGE_FOCUSED;
    private static final long MAX_EVENT_AGE_MS = 1_200;
    private static final long FINDER_SETTLE_MS = 180;

    private final IndexedPagerSession finder = new IndexedPagerSession();
    private final Runnable settleFinder = this::finishFinderScroll;
    private final Runnable refreshStory = this::refreshStory;
    private volatile ProtectionSettings settings;
    private HandlerThread finderThread;
    private HandlerThread storyThread;
    private volatile Handler finderHandler;
    private volatile Handler storyHandler;
    private int finderWindow = -1;
    private int lastSharedFinderWindow = -1;
    private int freshSharedFinderWindow = -1;
    private int finderReturnWindow = -1;
    private long finderEventTime;
    private int storyActivityWindow = -1;
    private int storyAttemptWindow = -1;
    private long lastStoryScanLog;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged = (prefs, key) -> {
        post(finderHandler, () -> {
            freshSharedFinderWindow = -1;
            resetFinder();
        });
        post(storyHandler, () -> {
            clearStoryAttempt();
            scheduleStory(0);
        });
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (finderThread == null) {
            settings = new ProtectionSettings(this);
            settings.register(settingsChanged);
            finderThread = new HandlerThread("WeChat pager");
            finderThread.start();
            finderHandler = new Handler(finderThread.getLooper());
            storyThread = new HandlerThread("Bilibili Story");
            storyThread.start();
            storyHandler = new Handler(storyThread.getLooper());
        }
        Log.i(TAG, "Service connected version=" + BuildConfig.VERSION_NAME + " pid=" + Process.myPid()
                + " mode=" + settings.read().mode());
        post(storyHandler, () -> scheduleStory(0));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        int windowId = event.getWindowId();
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            int changes = event.getWindowChanges();
            if ((changes & WINDOW_TRANSITIONS) != 0) {
                post(storyHandler, () -> scheduleStory(0));
            }
            if ((changes & AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0) {
                post(finderHandler, () -> {
                    if (freshSharedFinderWindow == windowId) {
                        freshSharedFinderWindow = -1;
                    }
                    if (finderWindow == windowId) {
                        resetFinder();
                    }
                });
            }
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg == null) {
            return;
        }
        if (FeedInspector.WECHAT.contentEquals(pkg)) {
            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                enqueueFinderScroll(event);
            } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && event.getClassName() != null
                    && event.getClassName().toString().startsWith("com.tencent.mm.plugin.finder.")) {
                boolean sharedActivity = SHARED_FINDER_ACTIVITY.contentEquals(event.getClassName());
                post(finderHandler, () -> seedFinderPage(windowId, sharedActivity));
            }
        } else if (FeedInspector.BILIBILI.contentEquals(pkg)
                || (BuildConfig.DEBUG && BuildConfig.APPLICATION_ID.contentEquals(pkg))) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                boolean storyActivity = event.getClassName() != null
                        && STORY_ACTIVITY.contentEquals(event.getClassName());
                post(storyHandler, () -> {
                    if (storyActivity) {
                        storyActivityWindow = windowId;
                        trace("Story activity window=" + windowId);
                    }
                    scheduleStory(0);
                });
            }
        }
    }

    private void enqueueFinderScroll(AccessibilityEvent event) {
        Handler worker = finderHandler;
        if (worker == null || !isProtectionActive()) {
            return;
        }
        AccessibilityEvent captured = AccessibilityEvent.obtain(event);
        if (!worker.post(() -> {
            try {
                observeFinderScroll(captured);
            } finally {
                captured.recycle();
            }
        })) {
            captured.recycle();
        }
    }

    private void seedFinderPage(int windowId, boolean sharedActivity) {
        if (!isProtectionActive()) {
            return;
        }
        if (sharedActivity && windowId != lastSharedFinderWindow) {
            lastSharedFinderWindow = windowId;
            freshSharedFinderWindow = windowId;
            trace("Finder shared player opened window=" + windowId);
        }
        IndexedPagerSession.Page page = foregroundFinderPage(windowId);
        if (page != null) {
            useFinderWindow(windowId);
            finder.seedSettledPage(page.index());
            trace("Finder baseline window=" + windowId + " page=" + page.index());
        }
    }

    private void observeFinderScroll(AccessibilityEvent event) {
        long age = SystemClock.uptimeMillis() - event.getEventTime();
        if (!isProtectionActive() || age > MAX_EVENT_AGE_MS) {
            trace("Finder skipped stale event ageMs=" + age);
            return;
        }
        int windowId = event.getWindowId();
        Rect bounds = focusedApplicationBounds(windowId);
        if (bounds == null) {
            return;
        }

        AccessibilityNodeInfo source = Build.VERSION.SDK_INT >= 33 ? event.getSource(0) : event.getSource();
        IndexedPagerSession.Sample sample = null;
        int first = event.getFromIndex();
        int last = event.getToIndex();
        if (source == null) {
            // A source-less event can corroborate an existing pager only through its visible row.
            if (finderWindow == windowId && first >= 0 && first == last) {
                IndexedPagerSession.Page page = foregroundFinderPage(windowId);
                if (page != null && page.index() == first) {
                    sample = new IndexedPagerSession.Sample(first, last, page, page);
                }
            }
            trace("Finder source unavailable ageMs=" + age + " recovered=" + (sample != null));
        } else {
            try {
                if (source.getWindowId() == windowId) {
                    sample = FeedInspector.inspectFinder(source, bounds, first, last);
                }
            } finally {
                source.recycle();
            }
            if (sample == null) {
                if (finderWindow == windowId) {
                    cancelFinderTransition();
                }
                return;
            }
        }
        if (sample == null || SystemClock.uptimeMillis() - event.getEventTime() > MAX_EVENT_AGE_MS) {
            return;
        }
        useFinderWindow(windowId);
        finderEventTime = event.getEventTime();
        boolean firstSharedScroll = freshSharedFinderWindow == windowId;
        if (firstSharedScroll) {
            freshSharedFinderWindow = -1;
        }
        finder.observe(sample, finderEventTime, firstSharedScroll);
        trace("Finder sample window=" + windowId + " pages=" + first + ".." + last + " ageMs=" + age);
        Handler worker = finderHandler;
        if (worker != null) {
            worker.removeCallbacks(settleFinder);
            worker.postDelayed(settleFinder, FINDER_SETTLE_MS);
        }
    }

    private IndexedPagerSession.Page foregroundFinderPage(int windowId) {
        Rect bounds = focusedApplicationBounds(windowId);
        if (bounds == null) {
            return null;
        }
        AccessibilityNodeInfo root = activeRoot();
        if (root == null) {
            return null;
        }
        try {
            return isWindowOf(root, windowId, FeedInspector.WECHAT)
                    ? FeedInspector.visibleFinderPage(root, bounds) : null;
        } finally {
            root.recycle();
        }
    }

    private void finishFinderScroll() {
        int windowId = finderWindow;
        if (!finder.finish() || finderReturnWindow == windowId) {
            return;
        }
        long elapsed = SystemClock.uptimeMillis() - finderEventTime;
        trace("Finder replacement confirmed window=" + windowId + " elapsedMs=" + elapsed);
        if (!isProtectionActive() || !isUnlocked() || elapsed > MAX_EVENT_AGE_MS
                || focusedApplicationBounds(windowId) == null) {
            trace("Finder return skipped after focus or timing check");
            return;
        }
        AccessibilityNodeInfo root = activeRoot();
        if (root == null) {
            return;
        }
        try {
            if (isWindowOf(root, windowId, FeedInspector.WECHAT)) {
                if (requestBack("Finder", windowId)) {
                    finderReturnWindow = windowId;
                    Handler worker = finderHandler;
                    if (worker != null) {
                        worker.postDelayed(() -> traceExit("Finder", windowId), 500);
                    }
                }
            }
        } finally {
            root.recycle();
        }
    }

    private void useFinderWindow(int windowId) {
        if (finderWindow != windowId) {
            resetFinder();
            finderWindow = windowId;
        }
    }

    private void cancelFinderTransition() {
        Handler worker = finderHandler;
        if (worker != null) {
            worker.removeCallbacks(settleFinder);
        }
        finder.cancelTransition();
    }

    private void resetFinder() {
        cancelFinderTransition();
        finder.clear();
        finderWindow = -1;
        finderReturnWindow = -1;
    }

    private void scheduleStory(long delay) {
        Handler worker = storyHandler;
        if (worker != null && !worker.hasCallbacks(refreshStory)) {
            worker.postDelayed(refreshStory, delay);
        }
    }

    private void refreshStory() {
        ProtectionSettings current = settings;
        if (current == null || current.read().mode() == ProtectionPolicy.Mode.OFF) {
            return;
        }
        boolean active = isProtectionActive() && isUnlocked();
        try {
            if (active) {
                checkStoryMode();
            } else {
                clearStoryAttempt();
            }
        } finally {
            scheduleStory(active ? (storyAttemptWindow < 0 ? 550 : 350) : 1_500);
        }
    }

    private void checkStoryMode() {
        AccessibilityWindowInfo window = focusedApplicationWindow();
        if (window == null) {
            traceStoryScan("no single focused app window");
            return;
        }
        try {
            int windowId = window.getId();
            if (storyAttemptWindow >= 0 && windowId != storyAttemptWindow) {
                trace("Story target window left window=" + storyAttemptWindow + " next=" + windowId);
                clearStoryAttempt();
            }
            CharSequence title = window.getTitle();
            boolean activityHint = storyActivityWindow == windowId;
            if (!activityHint && !BuildConfig.DEBUG && title != null && !"哔哩哔哩".contentEquals(title)
                    && !"bilibili".equalsIgnoreCase(title.toString())) {
                traceStoryScan("foreground title outside Bilibili");
                return;
            }
            Rect bounds = new Rect();
            window.getBoundsInScreen(bounds);
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                traceStoryScan("Bilibili candidate root unavailable window=" + windowId);
                // The activity event is from the exact Bilibili package and this same focused window.
                if (activityHint) {
                    exitStory(windowId, null);
                }
                return;
            }
            try {
                boolean story = root.getWindowId() == windowId && ((activityHint
                        && isWindowOf(root, windowId, FeedInspector.BILIBILI))
                        || FeedInspector.isStoryMode(root, bounds));
                traceStoryScan("Bilibili candidate window=" + windowId + " match=" + story);
                if (story) {
                    exitStory(windowId, root);
                }
            } finally {
                root.recycle();
            }
        } finally {
            window.recycle();
        }
    }

    private void traceStoryScan(String status) {
        long now = SystemClock.uptimeMillis();
        if (Log.isLoggable(TAG, Log.DEBUG) && now - lastStoryScanLog >= 1_200) {
            lastStoryScanLog = now;
            trace("Story scan " + status);
        }
    }

    private void exitStory(int windowId, AccessibilityNodeInfo root) {
        if (storyAttemptWindow == windowId || !isProtectionActive() || !isUnlocked()
                || focusedApplicationBounds(windowId) == null) {
            return;
        }
        storyAttemptWindow = windowId;
        boolean clicked = root != null && FeedInspector.clickStoryBack(root);
        trace("Story exit requested window=" + windowId + " click=" + clicked);
        if (!clicked) {
            requestBack("Story", windowId);
        }
    }

    private void clearStoryAttempt() {
        storyAttemptWindow = -1;
    }

    private boolean requestBack(String target, int windowId) {
        boolean accepted = performGlobalAction(GLOBAL_ACTION_BACK);
        trace(target + " Back accepted=" + accepted + " window=" + windowId);
        return accepted;
    }

    private void traceExit(String target, int expectedWindow) {
        AccessibilityWindowInfo focused = focusedApplicationWindow();
        if (focused == null) {
            trace(target + " return unverified: no single focused app");
            return;
        }
        try {
            trace(target + " target window left=" + (focused.getId() != expectedWindow));
        } finally {
            focused.recycle();
        }
    }

    private AccessibilityWindowInfo focusedApplicationWindow() {
        List<AccessibilityWindowInfo> windows = getWindows();
        AccessibilityWindowInfo focused = null;
        boolean safe = false;
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    if (window.isInPictureInPictureMode()) {
                        continue;
                    }
                    if (focused != null || !window.isFocused()) {
                        return null;
                    }
                    focused = window;
                } else if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD || window.isFocused()) {
                    return null;
                }
            }
            safe = true;
            return focused;
        } finally {
            for (AccessibilityWindowInfo window : windows) {
                if (!safe || window != focused) {
                    window.recycle();
                }
            }
        }
    }

    private Rect focusedApplicationBounds(int expectedWindow) {
        AccessibilityWindowInfo window = focusedApplicationWindow();
        if (window == null) {
            return null;
        }
        try {
            if (window.getId() != expectedWindow) {
                return null;
            }
            Rect bounds = new Rect();
            window.getBoundsInScreen(bounds);
            return bounds;
        } finally {
            window.recycle();
        }
    }

    private AccessibilityNodeInfo activeRoot() {
        return Build.VERSION.SDK_INT >= 33 ? getRootInActiveWindow(0) : getRootInActiveWindow();
    }

    private static boolean isWindowOf(AccessibilityNodeInfo root, int windowId, String pkg) {
        return root.getWindowId() == windowId && root.getPackageName() != null
                && pkg.contentEquals(root.getPackageName());
    }

    private boolean isUnlocked() {
        return !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private boolean isProtectionActive() {
        ProtectionSettings current = settings;
        return current != null && current.read().appliesAt(LocalTime.now());
    }

    private static void post(Handler worker, Runnable task) {
        if (worker != null) {
            worker.post(task);
        }
    }

    private static void trace(String message) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message);
        }
    }

    @Override
    public void onInterrupt() {
        post(finderHandler, this::cancelFinderTransition);
        post(storyHandler, () -> scheduleStory(0));
    }

    @Override
    public void onDestroy() {
        if (settings != null) {
            settings.unregister(settingsChanged);
            settings = null;
        }
        finderHandler = null;
        storyHandler = null;
        if (finderThread != null) {
            finderThread.quitSafely();
            storyThread.quitSafely();
        }
        super.onDestroy();
    }
}
