package app.shortvideoblocker;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

/** Debug-only example of the full-screen pager used by the real Story Mode rule. */
public final class StorySampleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout pager = new FrameLayout(this);
        pager.setId(R.id.story_view_pager);
        pager.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentView(pager);
    }
}
