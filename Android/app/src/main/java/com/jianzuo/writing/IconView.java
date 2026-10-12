package com.jianzuo.writing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small vector toolbar icons with a full 48 dp accessible touch target. */
final class IconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String icon;
    private final Path drawingPath = new Path();
    public IconView(Context context) { this(context, "edit", "简作", 0xffb05c3d); }
    IconView(Context context, String icon, String label, int color) {
        super(context); this.icon = icon;
        setContentDescription(label); setFocusable(true); setClickable(true);
        setTooltipText(label);
        paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        android.util.TypedValue value = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        setBackgroundResource(value.resourceId);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save(); float size = Math.min(getWidth(), getHeight()) * .47f;
        canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2); canvas.scale(size / 24, size / 24);
        switch (icon) {
            case "back": path(canvas, 15, 5, 8, 12, 15, 19); break;
            case "plus": canvas.drawLine(12, 4, 12, 20, paint); canvas.drawLine(4, 12, 20, 12, paint); break;
            case "more":
                paint.setStyle(Paint.Style.FILL);
                for (int y = 5; y < 21; y += 7) canvas.drawCircle(12, y, 1.4f, paint);
                paint.setStyle(Paint.Style.STROKE); break;
            case "preview":
                Path eye = drawingPath; eye.reset(); eye.moveTo(2, 12); eye.cubicTo(7, 2, 17, 2, 22, 12); eye.cubicTo(17, 22, 7, 22, 2, 12);
                canvas.drawPath(eye, paint); canvas.drawCircle(12, 12, 3, paint); break;
            case "edit": path(canvas, 5, 16, 16, 5, 20, 9, 9, 20, 4, 21, 5, 16); break;
            case "star": case "starFilled":
                if (icon.equals("starFilled")) paint.setStyle(Paint.Style.FILL);
                Path star = drawingPath; star.reset();
                for (int i = 0; i < 10; i++) {
                    double angle = -Math.PI / 2 + i * Math.PI / 5;
                    float radius = i % 2 == 0 ? 10 : 4.5f;
                    float x = 12 + (float) Math.cos(angle) * radius, y = 12 + (float) Math.sin(angle) * radius;
                    if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
                }
                star.close(); canvas.drawPath(star, paint); paint.setStyle(Paint.Style.STROKE); break;
            case "focus": path(canvas, 8, 3, 3, 3, 3, 8); path(canvas, 16, 3, 21, 3, 21, 8);
                path(canvas, 3, 16, 3, 21, 8, 21); path(canvas, 21, 16, 21, 21, 16, 21); break;
            case "unfocus": path(canvas, 3, 8, 8, 8, 8, 3); path(canvas, 21, 8, 16, 8, 16, 3);
                path(canvas, 3, 16, 8, 16, 8, 21); path(canvas, 21, 16, 16, 16, 16, 21); break;
            case "settings":
                for (int y = 5; y <= 19; y += 7) canvas.drawLine(3, y, 21, y, paint);
                canvas.drawCircle(8, 5, 2.5f, paint); canvas.drawCircle(16, 12, 2.5f, paint); canvas.drawCircle(10, 19, 2.5f, paint); break;
            default: canvas.drawRect(5, 3, 19, 21, paint); canvas.drawLine(8, 8, 16, 8, paint); canvas.drawLine(8, 12, 16, 12, paint);
        }
        canvas.restore();
    }
    private void path(Canvas canvas, float... points) {
        Path path = drawingPath; path.reset(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        canvas.drawPath(path, paint);
    }
}
