package ru.mtproxy.picker;
import android.content.Context;import android.graphics.*;import android.os.SystemClock;import android.view.View;
/** Draw the supplied circular button, clipping away the JPEG checkerboard. */
public final class FreeButtonView extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final Bitmap bitmap;private final Matrix matrix=new Matrix();private long started;
 public FreeButtonView(Context context){super(context);setClickable(true);setFocusable(true);setContentDescription("Халява. Открыть бесплатные прокси");BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inSampleSize=2;bitmap=BitmapFactory.decodeResource(getResources(),getResources().getIdentifier("free_button","drawable",context.getPackageName()),options);if(bitmap!=null)paint.setShader(new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));}
 @Override public CharSequence getAccessibilityClassName(){return "android.widget.Button";}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();started=SystemClock.uptimeMillis();}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(bitmap==null)return;float r=Math.min(getWidth(),getHeight())*.47f;matrix.setRectToRect(new RectF(bitmap.getWidth()*.051f,bitmap.getHeight()*.527f,bitmap.getWidth()*.474f,bitmap.getHeight()*.950f),new RectF(-r,-r,r,r),Matrix.ScaleToFit.FILL);paint.getShader().setLocalMatrix(matrix);paint.setAlpha(isPressed()?210:255);canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);if(android.animation.ValueAnimator.areAnimatorsEnabled())canvas.rotate(((SystemClock.uptimeMillis()-started)%7000)*360f/7000f);canvas.drawCircle(0,0,r,paint);canvas.restore();if(isShown()&&hasWindowFocus()&&android.animation.ValueAnimator.areAnimatorsEnabled())postInvalidateOnAnimation();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)invalidate();}
}
