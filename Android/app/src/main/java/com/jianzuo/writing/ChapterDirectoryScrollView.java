package com.jianzuo.writing;

import android.content.Context;
import android.view.DragEvent;
import android.widget.ScrollView;
import java.util.function.Predicate;

/** Keeps native child drop targets available while a local chapter is held near a viewport edge. */
final class ChapterDirectoryScrollView extends ScrollView {
    private final Predicate<Object> accepts;
    private int step;
    private boolean scheduled;
    private final Runnable scrollFrame = new Runnable() {
        public void run() {
            scheduled = false;
            if (step == 0 || !isAttachedToWindow() || !canScrollVertically(step > 0 ? 1 : -1)) return;
            scrollBy(0, step); scheduled = true; postDelayed(this, 32);
        }
    };
    public ChapterDirectoryScrollView(Context context) { this(context, state -> false); }
    ChapterDirectoryScrollView(Context context, Predicate<Object> accepts) {
        super(context); this.accepts = accepts;
        setOnDragListener((v, event) -> accepts.test(event.getLocalState()) && event.getAction() != DragEvent.ACTION_DROP);
    }
    @Override public boolean dispatchDragEvent(DragEvent event) {
        if (accepts.test(event.getLocalState())) {
            if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
                float edge = Math.min(getHeight() / 3f, 64 * getResources().getDisplayMetrics().density), y = event.getY();
                float intensity = y < edge ? (y - edge) / edge : y > getHeight() - edge ? (y - (getHeight() - edge)) / edge : 0;
                step = Math.round(Math.max(-1, Math.min(1, intensity)) * 14 * getResources().getDisplayMetrics().density);
                if (step != 0 && !scheduled) { scheduled = true; post(scrollFrame); }
            } else if (event.getAction() == DragEvent.ACTION_DRAG_EXITED || event.getAction() == DragEvent.ACTION_DROP || event.getAction() == DragEvent.ACTION_DRAG_ENDED) stop();
        }
        return super.dispatchDragEvent(event);
    }
    private void stop() { step = 0; scheduled = false; removeCallbacks(scrollFrame); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
