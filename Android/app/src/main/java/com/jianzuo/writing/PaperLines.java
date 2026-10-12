package com.jianzuo.writing;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.view.ViewTreeObserver;
import android.widget.TextView;

/** Rules follow native text baselines, including wrapped lines, without entering the text. */
final class PaperLines {
    private final Paint paint = new Paint();
    private final Rect clip = new Rect();
    private final Rect visible = new Rect();
    private ViewTreeObserver observer;
    private ViewTreeObserver.OnScrollChangedListener scrollListener;
    private boolean enabled;
    void attach(TextView view) {
        detach();
        observer = view.getViewTreeObserver();
        // Hardware display lists must be refreshed when an ancestor exposes new lines.
        scrollListener = () -> { if (enabled) view.invalidate(); };
        observer.addOnScrollChangedListener(scrollListener);
    }
    void detach() {
        if (observer != null && observer.isAlive()) observer.removeOnScrollChangedListener(scrollListener);
        observer = null; scrollListener = null;
    }
    void configure(TextView view, boolean enabled, int color) {
        this.enabled = enabled; paint.setColor(color);
        paint.setStrokeWidth(view.getResources().getDisplayMetrics().density);
        view.invalidate();
    }
    boolean enabled() { return enabled; }
    void draw(Canvas canvas, TextView view) {
        Layout layout = view.getLayout();
        if (!enabled || layout == null || !canvas.getClipBounds(clip)) return;
        // A hardware recording canvas can cover the entire long text render node.
        if (view.isAttachedToWindow() && (!view.getLocalVisibleRect(visible) || !clip.intersect(visible))) return;
        int offset = view.getTotalPaddingTop(), count = layout.getLineCount();
        int first = layout.getLineForVertical(Math.max(0, clip.top - offset));
        int last = layout.getLineForVertical(Math.max(0, clip.bottom - offset));
        float belowBaseline = view.getPaint().getFontMetrics().descent + 2 * view.getResources().getDisplayMetrics().density;
        float left = view.getCompoundPaddingLeft(), right = view.getWidth() - view.getCompoundPaddingRight();
        for (int i = first; i <= last; i++) {
            float y = offset + layout.getLineBaseline(i) + belowBaseline;
            if (y >= clip.top && y < clip.bottom) canvas.drawLine(left, y, right, y, paint);
        }
        // Fill the remaining minimum-height writing area with the same native line spacing.
        float step = Math.max(1, view.getLineHeight());
        float y = offset + layout.getLineBaseline(count - 1) + belowBaseline + step;
        if (y < clip.top) y += Math.ceil((clip.top - y) / step) * step;
        float bottom = Math.min(clip.bottom, view.getHeight() - view.getCompoundPaddingBottom());
        for (; y < bottom; y += step) canvas.drawLine(left, y, right, y, paint);
    }
}
