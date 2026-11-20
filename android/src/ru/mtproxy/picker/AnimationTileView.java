package ru.mtproxy.picker;
import android.content.Context;import android.net.Uri;import android.view.*;import android.widget.*;
/** Bundled, silent looping clip; no network or playback controls. */
final class AnimationTileView extends FrameLayout {
 private final VideoView video;private boolean attached;
 AnimationTileView(Context context,Runnable click){super(context);setBackgroundColor(0xfffdfdfd);setContentDescription("Халява. Открыть бесплатные прокси");setClickable(true);setFocusable(true);setOnClickListener(v->click.run());video=new VideoView(context);video.setFocusable(false);video.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_UP){performClick();}return true;});addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));video.setOnPreparedListener(player->{player.setLooping(true);player.setVolume(0,0);if(attached&&getWindowVisibility()==VISIBLE)video.start();});video.setOnErrorListener((player,what,extra)->{video.setVisibility(GONE);return true;});}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;int resource=getResources().getIdentifier("free_animation","raw",getContext().getPackageName());video.setVideoURI(Uri.parse("android.resource://"+getContext().getPackageName()+"/"+resource));}
 @Override protected void onDetachedFromWindow(){attached=false;video.stopPlayback();super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(video==null)return;if(visibility!=VISIBLE)video.pause();else if(attached)video.start();}
}
