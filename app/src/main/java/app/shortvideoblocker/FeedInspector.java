package app.shortvideoblocker;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

@SuppressWarnings("deprecation") // Accessibility nodes still need recycling on API 29–32.
final class FeedInspector {
    static final String BILIBILI = "tv.danmaku.bili";
    static final String WECHAT = "com.tencent.mm";

    static boolean isStoryMode(AccessibilityNodeInfo root, Rect window) {
        String pkg = string(root.getPackageName());
        if (!BILIBILI.equals(pkg) && !(BuildConfig.DEBUG && BuildConfig.APPLICATION_ID.equals(pkg))) {
            return false;
        }
        AccessibilityNodeInfo pager = visibleNode(root, pkg, "story_view_pager");
        if (pager == null) {
            return false;
        }
        try {
            return similarSize(bounds(pager), window);
        } finally {
            pager.recycle();
        }
    }

    static boolean clickStoryBack(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo button = visibleNode(root, string(root.getPackageName()), "container_top_back");
        if (button == null) {
            return false;
        }
        try {
            return button.isClickable() && button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } finally {
            button.recycle();
        }
    }

    static IndexedPagerSession.Sample inspectFinder(AccessibilityNodeInfo source, Rect window, int first,
            int last) {
        Rect pager = bounds(source);
        if (!isFinderPager(source, pager, window)) {
            return null;
        }

        IndexedPagerSession.Page firstPage = null;
        IndexedPagerSession.Page lastPage = null;
        for (int index = 0; index < Math.min(12, source.getChildCount()); index++) {
            AccessibilityNodeInfo child = childAt(source, index);
            if (child == null) {
                continue;
            }
            try {
                IndexedPagerSession.Page page = finderPage(child, pager);
                if (page == null) {
                    continue;
                }
                if (page.index() == first) {
                    firstPage = page;
                }
                if (page.index() == last) {
                    lastPage = page;
                }
            } finally {
                child.recycle();
            }
        }
        return new IndexedPagerSession.Sample(first, last, firstPage, lastPage);
    }

    static IndexedPagerSession.Page visibleFinderPage(AccessibilityNodeInfo root, Rect window) {
        AccessibilityNodeInfo source = visibleNode(root, WECHAT, "la3");
        if (source == null) {
            return null;
        }
        try {
            Rect pager = bounds(source);
            if (!isFinderPager(source, pager, window)) {
                return null;
            }
            for (int index = 0; index < Math.min(12, source.getChildCount()); index++) {
                AccessibilityNodeInfo child = childAt(source, index);
                if (child == null) {
                    continue;
                }
                try {
                    IndexedPagerSession.Page page = finderPage(child, pager);
                    if (page != null && page.visiblePercent() >= 94) {
                        return page;
                    }
                } finally {
                    child.recycle();
                }
            }
            return null;
        } finally {
            source.recycle();
        }
    }

    private static boolean isFinderPager(AccessibilityNodeInfo source, Rect pager, Rect window) {
        return id(WECHAT, "la3").equals(source.getViewIdResourceName())
                && string(source.getClassName()).endsWith("RecyclerView")
                && source.isScrollable() && similarSize(pager, window);
    }

    private static IndexedPagerSession.Page finderPage(AccessibilityNodeInfo child, Rect pager) {
        var info = child.getCollectionItemInfo();
        if (info == null || !child.isVisibleToUser()
                || !id(WECHAT, "ums").equals(child.getViewIdResourceName())) {
            return null;
        }
        Rect row = bounds(child);
        if (!row.intersect(pager) || row.width() < pager.width() * 9 / 10) {
            return null;
        }
        return new IndexedPagerSession.Page(info.getRowIndex(), percent(row.top - pager.top, pager.height()),
                percent(row.bottom - pager.top, pager.height()));
    }

    private static AccessibilityNodeInfo childAt(AccessibilityNodeInfo source, int index) {
        return Build.VERSION.SDK_INT >= 33 ? source.getChild(index, 0) : source.getChild(index);
    }

    private static AccessibilityNodeInfo visibleNode(AccessibilityNodeInfo parent, String pkg, String name) {
        List<AccessibilityNodeInfo> nodes = parent.findAccessibilityNodeInfosByViewId(id(pkg, name));
        AccessibilityNodeInfo match = null;
        for (AccessibilityNodeInfo node : nodes) {
            if (match == null && node.isVisibleToUser()) {
                match = node;
            } else {
                node.recycle();
            }
        }
        return match;
    }

    private static boolean similarSize(Rect bounds, Rect page) {
        return !page.isEmpty() && bounds.height() >= page.height() * 9 / 10
                && bounds.width() >= page.width() * 9 / 10 && Rect.intersects(bounds, page);
    }

    private static int percent(int value, int full) {
        return (int) (100L * value / full);
    }

    private static Rect bounds(AccessibilityNodeInfo node) {
        Rect result = new Rect();
        node.getBoundsInScreen(result);
        return result;
    }

    private static String id(String pkg, String name) {
        return pkg + ":id/" + name;
    }

    private static String string(CharSequence value) {
        return value == null ? "" : value.toString();
    }

}
