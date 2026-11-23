package app.shortvideoblocker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class IndexedPagerSessionTest {
    private final IndexedPagerSession session = new IndexedPagerSession();

    @Test
    public void finderCanConfirmFirstSwipeWithoutPixelDeltasOrText() {
        sample(1, 2, null, page(2, 85, 100), 100);
        sample(1, 2, null, page(2, 65, 100), 210);
        sample(2, 2, page(2, 0, 100), null, 500);
        assertTrue(session.finish());
        assertFalse(session.finish());
    }

    @Test
    public void finderCanConfirmSwipeBackToThePreviousVideo() {
        sample(1, 2, page(1, 0, 17), null, 100);
        sample(1, 2, page(1, 0, 57), null, 220);
        sample(1, 1, page(1, 0, 100), null, 500);
        assertTrue(session.finish());
    }

    @Test
    public void finderCanConfirmThroughTheOutgoingVideoWhenIncomingLayoutIsHidden() {
        sample(2, 3, page(2, 0, 82), null, 100);
        sample(2, 3, page(2, 0, 71), null, 190);
        sample(2, 3, page(2, 0, 6), null, 430);
        sample(3, 3, null, null, 610);
        assertTrue(session.finish());
    }

    @Test
    public void settlingBackOnOriginDoesNotExit() {
        sample(1, 2, null, page(2, 85, 100), 100);
        sample(1, 2, null, page(2, 20, 100), 200);
        sample(2, 2, page(2, 0, 100), null, 350);
        sample(1, 2, null, page(2, 90, 100), 460);
        sample(1, 1, page(1, 0, 100), null, 540);
        assertFalse(session.finish());
    }

    @Test
    public void indexJumpAloneDoesNotExit() {
        sample(2, 2, page(2, 0, 100), null, 500);
        assertFalse(session.finish());
    }

    @Test
    public void hiddenItemsAndIndexChangesWithoutVerticalTravelDoNotExit() {
        sample(2, 3, page(2, 0, 82), null, 100);
        sample(2, 3, null, null, 190);
        sample(3, 3, null, null, 400);
        assertFalse(session.finish());
    }

    @Test
    public void invisiblePagesCanUseAnEarlierSettledIndex() {
        sample(0, 0, null, null, 100);
        assertFalse(session.finish());
        sample(0, 1, null, null, 600);
        sample(0, 1, null, null, 710);
        sample(1, 1, null, null, 850);
        assertTrue(session.finish());
    }

    @Test
    public void invisiblePagesThatReturnToTheirBaselineDoNotExit() {
        sample(0, 0, null, null, 100);
        assertFalse(session.finish());
        sample(0, 1, null, null, 600);
        sample(0, 1, null, null, 710);
        sample(0, 0, null, null, 850);
        assertFalse(session.finish());
    }

    @Test
    public void firstSwipeCanReportOnlyTheSettledNeighbor() {
        session.seedSettledPage(0);
        sample(1, 1, null, null, 380);
        assertTrue(session.finish());
    }

    @Test
    public void unrelatedScrollClearsTheCandidateButKeepsTheBaseline() {
        session.seedSettledPage(0);
        sample(1, 1, null, null, 300);
        session.cancelTransition();
        assertFalse(session.finish());
        sample(0, 1, null, null, 600);
        sample(1, 1, null, null, 800);
        assertTrue(session.finish());
    }

    @Test
    public void incompleteEventDoesNotEraseTheLastStablePage() {
        session.seedSettledPage(0);
        sample(0, 1, null, null, 100);
        assertFalse(session.finish());
        sample(1, 1, null, null, 500);
        assertTrue(session.finish());
    }

    @Test
    public void newSharedPlayerCanReportFirstSwipeBeforeAnInitialSettledEvent() {
        session.observe(new IndexedPagerSession.Sample(0, 1, null, null), 200, true);
        sample(1, 1, null, null, 450);
        assertTrue(session.finish());
    }

    @Test
    public void newSharedPlayerDragsAndReturnsToFirstPageWithoutExiting() {
        session.observe(new IndexedPagerSession.Sample(0, 1, null, null), 200, true);
        sample(0, 0, null, null, 420);
        assertFalse(session.finish());
    }

    @Test
    public void settledEventFollowedImmediatelyBySwipeStillProvidesBaseline() {
        sample(0, 0, null, null, 200);
        sample(0, 1, null, null, 250);
        sample(1, 1, null, null, 500);
        assertTrue(session.finish());
    }

    @Test
    public void incompleteOrStaleGestureDoesNotExit() {
        sample(1, 2, null, page(2, 85, 100), 100);
        sample(1, 2, null, page(2, 20, 100), 250);
        sample(2, 2, page(2, 0, 100), null, 2_000);
        assertFalse(session.finish());
    }

    private void sample(int first, int last, IndexedPagerSession.Page firstPage,
            IndexedPagerSession.Page lastPage, long now) {
        session.observe(new IndexedPagerSession.Sample(first, last, firstPage, lastPage), now, false);
    }

    private static IndexedPagerSession.Page page(int index, int top, int bottom) {
        return new IndexedPagerSession.Page(index, top, bottom);
    }
}
