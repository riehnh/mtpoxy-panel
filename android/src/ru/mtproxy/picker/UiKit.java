package ru.mtproxy.picker;
import android.app.Activity;import android.graphics.Typeface;import android.graphics.drawable.*;import android.content.res.ColorStateList;import android.widget.*;
final class UiKit {
 static Button homeButton(Activity activity,Runnable click){float density=activity.getResources().getDisplayMetrics().density;Button b=new Button(activity);b.setText("На главный экран");b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);b.setTextColor(0xfff8f8f8);b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffb44d),new ColorDrawable(0xff555555),null));b.setPadding((int)(12*density),0,(int)(12*density),0);b.setMinHeight((int)(48*density));b.setMinimumHeight((int)(48*density));b.setOnClickListener(v->click.run());b.setLayoutParams(new LinearLayout.LayoutParams(-1,(int)(48*density)));return b;}
}
