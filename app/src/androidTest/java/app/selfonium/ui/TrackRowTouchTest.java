package app.selfonium.ui;

import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import app.selfonium.core.Track;
import java.util.ArrayList;
import java.util.List;

/** Device tests: dispatch actual down/up events through ListView, not performItemClick. */
@SuppressWarnings("deprecation")
public final class TrackRowTouchTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private MainActivity activity;
    private ListView list;
    private final List<String> actions = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();

    public TrackRowTouchTest() { super(MainActivity.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = getActivity();
        getInstrumentation().waitForIdleSync();
        for (int i = 0; i < 30; i++) tracks.add(track("track-" + i));
        onMain(() -> {
            list = new ListView(activity);
            list.setDivider(null);
            list.setItemsCanFocus(true);
            list.setFastScrollEnabled(false);
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return tracks.size(); }
                @Override public Track getItem(int position) { return tracks.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View old, ViewGroup parent) {
                    TrackRowView row = old instanceof TrackRowView ? (TrackRowView) old : new TrackRowView(activity);
                    row.bind(tracks.get(position), () -> actions.add("play:" + tracks.get(position).id),
                            value -> actions.add("menu:" + value.id));
                    return row;
                }
            });
            // A regression to item-click delegation would create a duplicate or wrong callback.
            list.setOnItemClickListener((parent, view, position, id) -> actions.add("unexpected-item-click"));
            activity.setContentView(list);
        });
        getInstrumentation().waitForIdleSync();
        assertTrue("The test needs at least two visible rows", list.getChildCount() >= 2);
    }

    public void testTitleTapPlaysTheSelectedTrackExactlyOnce() {
        TrackRowView row = row(1);
        tap(row, Ui.dp(activity, 100));
        assertEquals(List.of("play:track-1"), actions);
    }

    public void testArtworkTapAlsoPlaysTheTrack() {
        tap(row(0), Ui.dp(activity, 24));
        assertEquals(List.of("play:track-0"), actions);
    }

    public void testOverflowHasFullTouchTargetAndDoesNotPlay() {
        TrackRowView row = row(1);
        assertTrue(row.overflow.getWidth() >= Ui.dp(activity, 48));
        assertTrue(row.overflow.getHeight() >= Ui.dp(activity, 48));
        tap(row, row.overflow.getLeft() + row.overflow.getWidth() / 2f);
        assertEquals(List.of("menu:track-1"), actions);
    }

    public void testReboundRowUsesCurrentTrackForBothActions() {
        TrackRowView row = row(0);
        onMain(() -> row.bind(track("replacement"),
                () -> actions.add("play:replacement"), value -> actions.add("menu:" + value.id)));
        tap(row, Ui.dp(activity, 100));
        tap(row, row.overflow.getLeft() + row.overflow.getWidth() / 2f);
        assertEquals(List.of("play:replacement", "menu:replacement"), actions);
    }

    public void testLongPressOffersActionsWithoutPlayback() {
        onMain(() -> assertTrue(row(0).performLongClick()));
        assertEquals(List.of("menu:track-0"), actions);
    }

    public void testScrollGestureDoesNotPlayOrOpenTheMenu() {
        TrackRowView row = row(1);
        float x = Ui.dp(activity, 100), y = row.getTop() + row.getHeight() / 2f;
        onMain(() -> {
            long start = SystemClock.uptimeMillis();
            event(start, start, MotionEvent.ACTION_DOWN, x, y);
            event(start, start + 30, MotionEvent.ACTION_MOVE, x, y - Ui.dp(activity, 40));
            event(start, start + 60, MotionEvent.ACTION_MOVE, x, y - Ui.dp(activity, 80));
            event(start, start + 90, MotionEvent.ACTION_UP, x, y - Ui.dp(activity, 80));
        });
        getInstrumentation().waitForIdleSync();
        assertTrue("Scrolling must cancel the pending row tap", actions.isEmpty());
    }

    private void onMain(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("UI-thread assertion failed", failure.get());
    }

    private TrackRowView row(int visibleIndex) { return (TrackRowView) list.getChildAt(visibleIndex); }

    private void tap(TrackRowView row, float rowX) {
        onMain(() -> {
            float x = row.getLeft() + rowX, y = row.getTop() + row.getHeight() / 2f;
            long start = SystemClock.uptimeMillis();
            event(start, start, MotionEvent.ACTION_DOWN, x, y);
            event(start, start + 30, MotionEvent.ACTION_UP, x, y);
        });
        getInstrumentation().waitForIdleSync();
    }

    private void event(long start, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, time, action, x, y, 0);
        try { assertTrue(list.dispatchTouchEvent(event)); }
        finally { event.recycle(); }
    }

    private static Track track(String id) {
        // No server access or real user-library mutation is needed for these interaction tests.
        return new Track(id, "radio", id, id, "Test artist", "Test album", "", 180000, 1);
    }
}
