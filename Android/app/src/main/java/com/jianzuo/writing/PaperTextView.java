package com.jianzuo.writing;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.TextView;

final class PaperTextView extends TextView {
    final PaperLines paperLines = new PaperLines();
    PaperTextView(Context context) { super(context); }
    void configurePaper(boolean ruled, int color) { paperLines.configure(this, ruled, color); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); paperLines.attach(this); }
    @Override protected void onDetachedFromWindow() { paperLines.detach(); super.onDetachedFromWindow(); }
    @Override protected void onDraw(Canvas canvas) { paperLines.draw(canvas, this); super.onDraw(canvas); }
}
