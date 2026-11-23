package app.shortvideoblocker;

/** Confirms a vertical page replacement from visible item indexes and their clipped bounds. */
final class IndexedPagerSession {
    private static final long MAX_TRANSITION_MS = 1_800;

    record Page(int index, int top, int bottom) {
        int visiblePercent() {
            return bottom - top;
        }
    }

    record Sample(int first, int last, Page firstPage, Page lastPage) {}

    private int settledPage = -1;
    private int lastSinglePage = -1;
    private int origin = -1;
    private int target = -1;
    private int trackedPage;
    private int initialSize;
    private long startedAt;
    private boolean tracksIncoming;
    private boolean sawVerticalTravel;
    private boolean targetSettled;

    void observe(Sample sample, long now, boolean firstScrollInSharedPlayer) {
        if (target >= 0 && now - startedAt > MAX_TRANSITION_MS) {
            clearTransition();
        }
        int first = sample.first();
        int last = sample.last();
        if (first < 0 || last < first || last - first > 1) {
            clear();
            return;
        }
        if (first == last) {
            Page page = sample.firstPage();
            lastSinglePage = page == null || page.visiblePercent() >= 94 ? first : -1;
            boolean adjacentToBaseline = lastSinglePage >= 0 && settledPage >= 0
                    && Math.abs(lastSinglePage - settledPage) == 1;
            targetSettled = adjacentToBaseline
                    || (lastSinglePage == target && target >= 0 && sawVerticalTravel);
            return;
        }

        if (settledPage < 0 && lastSinglePage >= 0 && target < 0) {
            settledPage = lastSinglePage;
        }
        if (firstScrollInSharedPlayer && first == 0 && last == 1) {
            // A newly opened shared-video playlist starts at its selected clip, at index zero.
            seedSettledPage(0);
        }
        targetSettled = false;
        lastSinglePage = -1;
        if (target >= 0 && (Math.min(origin, target) != first || Math.max(origin, target) != last)) {
            clearTransition();
        }
        if (target < 0) {
            Page bottom = sample.lastPage();
            Page top = sample.firstPage();
            if (isLarge(top) && top.top() <= 4 && top.bottom() <= 97) {
                start(first, last, top, false, now);
            } else if (isLarge(bottom) && bottom.bottom() >= 96 && bottom.top() >= 3) {
                start(last, first, bottom, false, now);
            } else if (isSmall(bottom) && bottom.bottom() >= 96) {
                start(first, last, bottom, true, now);
            } else if (isSmall(top) && top.top() <= 4) {
                start(last, first, top, true, now);
            }
        }
        if (target >= 0) {
            Page tracked = trackedPage == first ? sample.firstPage()
                    : trackedPage == last ? sample.lastPage() : null;
            if (tracked != null) {
                int currentSize = tracked.visiblePercent();
                int travel = tracksIncoming ? currentSize - initialSize : initialSize - currentSize;
                sawVerticalTravel |= travel >= 10;
            }
        }
    }

    boolean finish() {
        boolean switched = targetSettled;
        if (lastSinglePage >= 0) {
            settledPage = lastSinglePage;
        }
        clearTransition();
        return switched;
    }

    void seedSettledPage(int index) {
        if (settledPage < 0 && lastSinglePage < 0 && target < 0) {
            settledPage = index;
        }
    }

    void cancelTransition() {
        clearTransition();
    }

    void clear() {
        settledPage = -1;
        clearTransition();
    }

    private void clearTransition() {
        lastSinglePage = -1;
        origin = -1;
        target = -1;
        sawVerticalTravel = false;
        targetSettled = false;
    }

    private void start(int originIndex, int targetIndex, Page measured, boolean incoming, long now) {
        origin = originIndex;
        target = targetIndex;
        trackedPage = measured.index();
        tracksIncoming = incoming;
        initialSize = measured.visiblePercent();
        startedAt = now;
    }

    private static boolean isSmall(Page page) {
        return page != null && page.visiblePercent() >= 3 && page.visiblePercent() <= 44;
    }

    private static boolean isLarge(Page page) {
        return page != null && page.visiblePercent() >= 56;
    }
}
