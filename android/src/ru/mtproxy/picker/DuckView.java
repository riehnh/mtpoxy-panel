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
        canvas.rotate(180);
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

        paint.setColor(0xffffe4a0);
        canvas.drawOval(57, 108, 121, 145, paint);
        paint.setColor(0xff271a13);
        canvas.drawCircle(139, 57, 4.5f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(140.3f, 55.5f, 1.4f, paint);
        canvas.save();
        canvas.translate(138,43);canvas.scale(.6f,.6f);
        paint.setColor(0xff28a8ea);shape.reset();
        shape.moveTo(24.5f,52.8f);
        shape.cubicTo(40.2f,46f,50.6f,41.5f,56f,39.3f);
        shape.cubicTo(70.9f,33.2f,74f,32.1f,76f,32.1f);
        shape.cubicTo(76.4f,32.1f,77.4f,32.2f,78f,32.7f);
        shape.cubicTo(78.5f,33.1f,78.6f,33.7f,78.7f,34.1f);
        shape.cubicTo(78.8f,34.6f,78.8f,35.5f,78.7f,36.3f);
        shape.cubicTo(77.9f,44.8f,74.4f,65.3f,72.7f,74.7f);
        shape.cubicTo(71.9f,78.7f,70.4f,80f,69f,80.1f);
        shape.cubicTo(65.9f,80.4f,63.6f,78f,60.6f,76f);
        shape.cubicTo(55.9f,73f,53.3f,71.1f,48.8f,68.1f);
        shape.cubicTo(43.6f,64.6f,47f,62.7f,49.9f,59.6f);
        shape.cubicTo(50.7f,58.8f,64.2f,46.5f,64.5f,45.4f);
        shape.cubicTo(64.6f,45.3f,64.6f,44.9f,64.3f,44.7f);
        shape.cubicTo(64.1f,44.4f,63.7f,44.5f,63.4f,44.6f);
        shape.cubicTo(63f,44.7f,56.3f,49.1f,43.2f,58f);
        shape.cubicTo(41.3f,59.3f,39.6f,59.9f,38f,59.9f);
        shape.cubicTo(36.3f,59.9f,33f,58.9f,30.6f,58.1f);
        shape.cubicTo(27.6f,57.2f,25.3f,56.7f,25.5f,55f);
        shape.cubicTo(25.6f,54.1f,25.3f,53.7f,24.5f,52.8f);
        shape.close();
        canvas.drawPath(shape,paint);canvas.restore();
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
