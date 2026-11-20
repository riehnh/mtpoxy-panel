package ru.mtproxy.picker;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
/** The approved duck asset, rotated clockwise independently of its caption. */
public final class DuckView extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final Bitmap duck;
 public DuckView(Context context){super(context);setClickable(true);setFocusable(true);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;duck=BitmapFactory.decodeResource(getResources(),getResources().getIdentifier("ic_launcher","drawable",context.getPackageName()),options);}
 @Override public CharSequence getAccessibilityClassName(){return "android.widget.Button";}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(duck==null)return;canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);if(android.animation.ValueAnimator.areAnimatorsEnabled())canvas.rotate((SystemClock.uptimeMillis()%6000)*360f/6000f);float size=Math.min(getWidth(),getHeight())*.70f;float scale=size/Math.max(duck.getWidth(),duck.getHeight());float w=duck.getWidth()*scale,h=duck.getHeight()*scale;canvas.drawBitmap(duck,null,new RectF(-w/2,-h/2,w/2,h/2),paint);canvas.restore();if(isShown()&&hasWindowFocus()&&android.animation.ValueAnimator.areAnimatorsEnabled())postInvalidateOnAnimation();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)invalidate();}
}
