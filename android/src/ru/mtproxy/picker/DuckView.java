package ru.mtproxy.picker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** A clockwise-spinning rubber duck. Its text label is a separate, static view. */
public final class DuckView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();

    public DuckView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
    }

    @Override public CharSequence getAccessibilityClassName() {
        return "android.widget.Button";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth(), getHeight()) / 200f;
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            canvas.rotate((SystemClock.uptimeMillis() % 5000) * 360f / 5000f);
        }
        canvas.scale(scale, scale);
        canvas.translate(-100, -100);

        paint.setColor(0xffffae3e);
        shape.reset();
        shape.moveTo(48, 113);
        shape.quadTo(15, 109, 16, 79);
        shape.quadTo(36, 95, 64, 94);
        shape.close();
        canvas.drawPath(shape, paint);

        paint.setShader(new LinearGradient(45, 95, 150, 169,
                0xffffcf61, 0xffff8a2b, Shader.TileMode.CLAMP));
        canvas.drawOval(35, 91, 164, 169, paint);
        canvas.drawRoundRect(104, 69, 152, 134, 24, 24, paint);
        canvas.drawCircle(127, 65, 35, paint);
        paint.setShader(null);

        paint.setColor(0xffe96b24);
        shape.reset();
        shape.moveTo(153, 65);
        shape.quadTo(173, 65, 187, 76);
        shape.quadTo(173, 88, 151, 84);
        shape.close();
        canvas.drawPath(shape, paint);
        paint.setColor(0xffffe4a0);
        canvas.drawOval(57, 108, 121, 145, paint);
        paint.setColor(0xff271a13);
        canvas.drawCircle(139, 57, 4.5f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(140.3f, 55.5f, 1.4f, paint);
        canvas.restore();

        if (isShown() && getWindowVisibility() == VISIBLE && hasWindowFocus()
                && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            postInvalidateOnAnimation();
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) invalidate();
    }
}
