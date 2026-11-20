package ru.mtproxy.picker;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 static final String[] SOURCE_NAMES={"MTProxy List", "SoliSpirit", "TGMTProxy"};
 static final String[] SOURCE_URLS={"https://iwizard7.github.io/MTProxy_list/working.txt", "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt", "https://raw.githubusercontent.com/tgmtproxy/mtproxy/main/proxies.txt"};
 boolean[] selected={true,false,false}; Button sources;
 View manualScreen;
 ProgressBar manualProgress;
 TextView manualStatus;
 LinearLayout automaticResults;
 DiceView dice;
 Button retry;
 Screen screen=Screen.HOME;
 int candidateIndex, candidateCount;
 String failedSources="";
 enum Screen { HOME, MANUAL, AUTOMATIC }
 final ArrayList<Proxy> proxies=new ArrayList<>();
 final ExecutorService io=Executors.newSingleThreadExecutor();
 ExecutorService probes; boolean busy=false, ascending=true; volatile boolean stopped=false;
 TextView status,summary; Button load,ping,sort; LinearLayout rows; ProgressBar progress;
 static class Proxy { String host,secret; int port; volatile long ms=-2; Proxy(String h,int p,String s){host=h;port=p;secret=s;} }
 int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
 android.graphics.drawable.GradientDrawable rounded(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 void space(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
 Button button(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(null,1);b.setTextColor(primary?0xff14100d:0xffff8a2b);b.setBackgroundTintList(null);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ff8a2b),rounded(primary?0xffff8a2b:0xff302218,16),null));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));return b;}
 @Override public void onCreate(Bundle b){super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(16),dp(20),0);root.setBackgroundColor(0xff101010);
  root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(20),i.getSystemWindowInsetTop()+dp(16),dp(20),i.getSystemWindowInsetBottom());return i;});
  LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);logo.setImageResource(getResources().getIdentifier("ic_launcher","drawable",getPackageName()));brand.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout title=new LinearLayout(this);title.setOrientation(1);title.setPadding(dp(12),0,0,0);TextView name=text("Ёкси",24,0xfff6f0ea);name.setTypeface(null,1);title.addView(name);title.addView(text("Это вам не это",13,0xffb2aaa4));brand.addView(title);root.addView(brand);space(root,20);
  LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(20),dp(18),dp(20),dp(18));hero.setBackground(rounded(0xff302218,24));TextView heading=text("Тыкни.\nПотыкай ещё раз.",27,0xffffa45e);heading.setTypeface(null,1);hero.addView(heading);space(hero,8);hero.addView(text("Найди лучший вариант тыкания\nи тыкни в тыкалку",14,0xffd6c0b0));space(hero,16);load=button("↓  Получить список",true);hero.addView(load,new LinearLayout.LayoutParams(-1,dp(50)));root.addView(hero);space(root,14);
  for(int i=0;i<selected.length;i++)selected[i]=getPreferences(0).getBoolean("source_"+i,i==0); sources=button("Источники · "+sourceLabel()+"  ▾",false);sources.setOnClickListener(v->chooseSources(sources));root.addView(sources,new LinearLayout.LayoutParams(-1,-2));space(root,10);
  LinearLayout actions=new LinearLayout(this);ping=button("◉  Проверить пинг",false);sort=button("По пингу ↑",false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.setMarginEnd(dp(8));actions.addView(ping,ap);actions.addView(sort,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(actions);space(root,12);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress);progress.setVisibility(View.GONE);
  status=text("Начните с загрузки списка — мы найдём доступные адреса.",13,0xffb2aaa4);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);space(root,14);summary=text("ВАШИ ПРОКСИ",12,0xffb2aaa4);summary.setLetterSpacing(0.10f);summary.setTypeface(null,1);root.addView(summary);space(root,8);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff101010);rows=new LinearLayout(this);rows.setOrientation(1);root.addView(rows,new LinearLayout.LayoutParams(-1,-2));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  load.setOnClickListener(v->fetch());ping.setOnClickListener(v->probe());sort.setOnClickListener(v->{ascending=!ascending;order();render();});
  String cache=getPreferences(0).getString("list","");parse(cache,proxies);render();if(!proxies.isEmpty())status.setText("Сохранённый список: "+proxies.size()+". Обновите перед проверкой.");enable();
  manualScreen=scroll;
  manualProgress=progress;
  manualStatus=status;
  showHome();
 }
 LinearLayout modePage(){
  ScrollView scroll=new ScrollView(this);
  scroll.setFillViewport(true);
  scroll.setBackgroundColor(0xff101010);
  LinearLayout page=new LinearLayout(this);
  page.setOrientation(1);
  page.setOnApplyWindowInsetsListener((v,i)->{
   v.setPadding(dp(20),dp(20)+i.getSystemWindowInsetTop(),dp(20),dp(20)+i.getSystemWindowInsetBottom());
   return i;
  });
  scroll.addView(page);
  setContentView(scroll);
  scroll.requestApplyInsets();
  TextView name=text("Ёкси",30,0xffff8a2b);
  name.setTypeface(null,1);
  ImageView mark=new ImageView(this);mark.setImageResource(getResources().getIdentifier("ic_launcher","drawable",getPackageName()));page.addView(mark,new LinearLayout.LayoutParams(dp(52),dp(52)));
  page.addView(name);
  page.addView(text("Это вам не это",14,0xffb2aaa4));
  space(page,24);
  return page;
 }
 void showHome(){
  screen=Screen.HOME;
  LinearLayout page=modePage();
  Button sourcePicker=button("Источники · "+sourceLabel()+"  ▾",false);
  sourcePicker.setOnClickListener(v->chooseSources(sourcePicker));
  page.addView(sourcePicker);
  LinearLayout center=new LinearLayout(this);
  center.setOrientation(1);
  center.setGravity(Gravity.CENTER);
  center.setPadding(0,dp(20),0,dp(20));
  page.addView(center,new LinearLayout.LayoutParams(-1,0,1));
  DuckView duck=new DuckView(this);
  duck.setContentDescription("Жупень. Панель моего VPS");
  duck.setOnClickListener(v->openPanel());
  center.addView(duck,new LinearLayout.LayoutParams(dp(184),dp(184)));
  TextView duckName=text("Жупень",27,0xffff8a2b);
  duckName.setTypeface(null,1);
  duckName.setGravity(Gravity.CENTER);
  duckName.setPadding(0,dp(10),0,dp(10));
  duckName.setOnClickListener(v->openPanel());
  center.addView(duckName);
  Button free=button("Халява",false);
  free.setOnClickListener(v->showAutomatic());
  page.addView(free);
  space(page,8);
  Button manual=button("Манул",false);
  manual.setOnClickListener(v->{
   screen=Screen.MANUAL;
   status=manualStatus;
   progress=manualProgress;
   render();
   enable();
   if(!proxies.isEmpty()) status.setText("Прокси в списке · "+proxies.size());
   setContentView(manualScreen);
   manualScreen.requestApplyInsets();
  });
  page.addView(manual);
 }
 void openPanel(){
  startActivity(new Intent(this,PanelActivity.class));
 }
 void showAutomatic(){
  screen=Screen.AUTOMATIC;
  LinearLayout page=modePage();
  Button back=button("← На главный экран",false);
  back.setOnClickListener(v->onBackPressed());
  page.addView(back);
  space(page,16);
  status=text("",14,0xffb2aaa4);
  status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  page.addView(status);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
  page.addView(progress);
  dice=new DiceView();
  page.addView(dice,new LinearLayout.LayoutParams(-1,dp(156)));
  automaticResults=new LinearLayout(this);
  automaticResults.setOrientation(1);
  page.addView(automaticResults);
  retry=button("Проверить заново",false);
  retry.setOnClickListener(v->startAutomatic());
  page.addView(retry);
  startAutomatic();
 }
 void startAutomatic(){
  automaticResults.removeAllViews();
  retry.setVisibility(View.GONE);
  dice.setVisibility(View.VISIBLE);
  candidateIndex=0;
  candidateCount=0;
  fetch();
 }
 final class DiceView extends View {
  private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final int[][] faces={{4},{0,8},{0,4,8},{0,2,6,8},{0,2,4,6,8},{0,2,3,5,6,8}};
  DiceView(){
   super(MainActivity.this);
   setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  }
  @Override protected void onDraw(android.graphics.Canvas canvas){
   super.onDraw(canvas);
   long tick=SystemClock.uptimeMillis();
   float phase=(tick%1200)/1200f;
   drawDie(canvas,getWidth()/2f-dp(47),getHeight()/2f,phase,0xffff8a2b,0xff20140c,(int)(tick/145%6));
   drawDie(canvas,getWidth()/2f+dp(47),getHeight()/2f,(phase+.43f)%1f,0xffedddc4,0xff312013,(int)((tick/173+2)%6));
   if(isShown()&&getWindowVisibility()==View.VISIBLE&&android.animation.ValueAnimator.areAnimatorsEnabled()){
    postInvalidateOnAnimation();
   }
  }
  private void drawDie(android.graphics.Canvas canvas,float x,float y,float phase,int fill,int dots,int face){
   float jump=(float)Math.sin(Math.PI*phase);
   float half=dp(30);
   canvas.save();
   canvas.translate(x,y-dp(22)*jump);
   canvas.rotate(-18+36*phase);
   paint.setColor(0xff382518);
   canvas.drawRoundRect(-half,-half+dp(5),half,half+dp(5),dp(9),dp(9),paint);
   paint.setColor(fill);
   canvas.drawRoundRect(-half,-half,half,half,dp(9),dp(9),paint);
   paint.setColor(dots);
   for(int pip:faces[face]){
    canvas.drawCircle((pip%3-1)*dp(15),(pip/3-1)*dp(15),dp(4),paint);
   }
   canvas.restore();
  }
 }
 void showCandidate(){
  automaticResults.removeAllViews();
  if(candidateCount==0)return;
  Proxy candidate=proxies.get(candidateIndex);
  LinearLayout card=new LinearLayout(this);
  card.setOrientation(1);
  card.setPadding(dp(16),dp(16),dp(16),dp(16));
  card.setBackground(rounded(0xff1c1b1a,20));
  card.addView(text("Вариант "+(candidateIndex+1)+" из "+candidateCount,13,0xffb2aaa4));
  space(card,8);
  card.addView(text(candidate.host+" · порт "+candidate.port,17,0xfff6f0ea));
  card.addView(text(candidate.ms+" мс",26,0xffff8a2b));
  space(card,16);
  Button push=button("Пихаем",true);
  push.setOnClickListener(v->connect(candidate));
  card.addView(push);
  space(card,8);
  Button next=button("Следующий",false);
  boolean hasNext=candidateIndex+1<candidateCount;
  next.setEnabled(hasNext);
  next.setAlpha(hasNext?1f:.45f);
  next.setOnClickListener(v->{candidateIndex++;showCandidate();});
  card.addView(next);
  if(!hasNext){
   space(card,8);
   card.addView(text("Это последний доступный вариант.",13,0xffb2aaa4));
  }
  automaticResults.addView(card);
 }
 @Override public void onBackPressed(){
  if(busy){
   Toast.makeText(this,"Дождитесь окончания операции",Toast.LENGTH_SHORT).show();
   return;
  }
  if(screen==Screen.HOME)super.onBackPressed();
  else showHome();
 }
 static String query(java.net.URI u,String name)throws Exception {String q=u.getRawQuery();if(q==null)return null;for(String part:q.split("&")){String[] pair=part.split("=",2);if(pair.length==2&&name.equals(pair[0]))return java.net.URLDecoder.decode(pair[1],"UTF-8");}return null;}
 static boolean validSecret(String s){if(s==null||s.length()>1024)return false;byte[] bytes;try{if(s.matches("(?i)[a-f0-9]+")&&s.length()%2==0){bytes=new byte[s.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);}else{if(!s.matches("[A-Za-z0-9_-]+={0,2}"))return false;bytes=java.util.Base64.getUrlDecoder().decode(s);}return bytes.length==16||(bytes.length==17&&(bytes[0]&255)==221)||(bytes.length>=18&&(bytes[0]&255)==238);}catch(Exception e){return false;}}
 static void parse(String data,List<Proxy> out){Set<String> seen=new HashSet<>();for(Proxy old:out)seen.add(old.host+":"+old.port+":"+old.secret);for(String line:data.split("\\r?\\n")){try{java.net.URI u=new java.net.URI(line.trim());if(!(("tg".equals(u.getScheme())&&"proxy".equals(u.getHost()))||("https".equals(u.getScheme())&&"t.me".equals(u.getHost())&&"/proxy".equals(u.getPath()))))continue;String h=query(u,"server"),s=query(u,"secret");int p=Integer.parseInt(query(u,"port"));if(h==null||h.isEmpty()||h.length()>253||!h.matches("[A-Za-z0-9.:-]+")||!validSecret(s)||p<1||p>65535)continue;h=h.toLowerCase(Locale.ROOT);if(h.endsWith("."))h=h.substring(0,h.length()-1);if(out.size()>=3000)break;if(seen.add(h+":"+p+":"+s))out.add(new Proxy(h,p,s));}catch(Exception ignored){}}}
 String sourceLabel(){ArrayList<String> names=new ArrayList<>();for(int i=0;i<selected.length;i++)if(selected[i])names.add(SOURCE_NAMES[i]);return android.text.TextUtils.join(" + ",names);}
 void chooseSources(Button trigger){final boolean[] draft=selected.clone();AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Источники прокси").setMultiChoiceItems(SOURCE_NAMES,draft,(d,index,checked)->draft[index]=checked).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{boolean any=false;for(boolean b:draft)any|=b;if(!any){Toast.makeText(this,"Выберите хотя бы один источник",Toast.LENGTH_SHORT).show();return;}if(!Arrays.equals(selected,draft)){selected=draft;android.content.SharedPreferences.Editor editor=getPreferences(0).edit();for(int i=0;i<selected.length;i++)editor.putBoolean("source_"+i,selected[i]);editor.remove("list").apply();proxies.clear();render();manualStatus.setText("Источники выбраны. Тыкните «Получить список».");enable();}sources.setText("Источники · "+sourceLabel()+"  ▾");trigger.setText(sources.getText());dialog.dismiss();}));dialog.show();}
 void ui(Runnable r){if(!stopped)runOnUiThread(()->{if(!stopped)r.run();});}
 void enable(){sources.setEnabled(!busy);sources.setAlpha(busy?.45f:1f);load.setEnabled(!busy);ping.setEnabled(!busy&&!proxies.isEmpty());sort.setEnabled(!busy&&!proxies.isEmpty());load.setAlpha(busy?.45f:1f);ping.setAlpha(ping.isEnabled()?1f:.4f);sort.setAlpha(sort.isEnabled()?1f:.4f);load.setText(proxies.isEmpty()?"↓  Получить список":"↻  Обновить список");}
 static String download(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();try{c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","Yoxy/1.6");if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>1024*1024)throw new IOException("Список слишком большой");}}return bytes.toString("UTF-8");}finally{c.disconnect();}}
 void fetch(){
  busy=true;
  enable();
  progress.setIndeterminate(true);
  progress.setVisibility(View.VISIBLE);
  status.setText("Собираем варианты для тыкания…");
  final boolean[] requested=selected.clone();
  io.execute(()->{
   ArrayList<Proxy> next=new ArrayList<>();
   ArrayList<String> failed=new ArrayList<>();
   for(int i=0;i<requested.length;i++){
    if(stopped)return;
    if(!requested[i])continue;
    final String name=SOURCE_NAMES[i];
    ui(()->status.setText("Загружаем · "+name));
    try{
     ArrayList<Proxy> entries=new ArrayList<>();
     String raw=download(SOURCE_URLS[i]);
     parse(raw,entries);
     if(entries.isEmpty())throw new IOException("Пустой список");
     parse(raw,next);
    }catch(Exception e){failed.add(name);}
   }
   StringBuilder cache=new StringBuilder();
   for(Proxy proxy:next)cache.append(proxyUri(proxy)).append('\n');
   ui(()->{
    busy=false;
    progress.setVisibility(View.GONE);
    progress.setIndeterminate(false);
    boolean automatic=screen==Screen.AUTOMATIC;
    failedSources=android.text.TextUtils.join(", ",failed);
    if(next.isEmpty()){
     status.setText("Не удалось загрузить: "+failedSources+". Попробуйте ещё раз.");
     if(automatic){
      dice.setVisibility(View.GONE);
      retry.setVisibility(View.VISIBLE);
     }
     enable();
     return;
    }
    proxies.clear();
    proxies.addAll(next);
    getPreferences(0).edit().putString("list",cache.toString()).apply();
    if(automatic){
     probe();
    }else{
     render();
     status.setText("Найдено: "+next.size()+". Потыкайте проверку пинга."+sourceErrors());
     enable();
    }
   });
  });
 }
 String sourceErrors(){return failedSources.isEmpty()?"":" Не загрузились: "+failedSources+".";}
 static long measure(Proxy p){long best=Long.MAX_VALUE;try{InetAddress[] addresses=InetAddress.getAllByName(p.host);for(InetAddress a:addresses){if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return -1;}for(int i=0;i<2;i++){long start=System.nanoTime();try(Socket s=new Socket()){s.connect(new InetSocketAddress(addresses[i%addresses.length],p.port),2000);best=Math.min(best,Math.max(1,(System.nanoTime()-start)/1000000));}catch(IOException ignored){}}}catch(Exception ignored){}return best==Long.MAX_VALUE?-1:best;}
 void probe(){
  busy=true;
  enable();
  boolean automatic=screen==Screen.AUTOMATIC;
  for(Proxy proxy:proxies)proxy.ms=-2;
  if(!automatic)render();
  final int total=proxies.size();
  progress.setMax(total);
  progress.setProgress(0);
  progress.setVisibility(View.VISIBLE);
  status.setText("Проверка 0 / "+total);
  probes=Executors.newFixedThreadPool(12);
  int[] completed={0};
  for(Proxy proxy:new ArrayList<>(proxies)){
   probes.execute(()->{
    if(stopped)return;
    proxy.ms=measure(proxy);
    ui(()->{
     int done=++completed[0];
     progress.setProgress(done);
     status.setText("Проверено "+done+" / "+total);
     if(done!=total)return;
     busy=false;
     ascending=true;
     order();
     enable();
     progress.setVisibility(View.GONE);
     int available=(int)proxies.stream().filter(p->p.ms>=0).count();
     if(automatic){
      dice.setVisibility(View.GONE);
      candidateCount=available;
      status.setText((available==0?"Ни один прокси не ответил.":"Доступно "+available+" из "+total+". Сначала самые быстрые.")+sourceErrors());
      if(available==0)retry.setVisibility(View.VISIBLE);
      showCandidate();
     }else{
      render();
      status.setText("Готово: доступны "+available+" из "+total+". Самые быстрые сверху.");
     }
    });
   });
  }
  probes.shutdown();
 }
 void order(){Collections.sort(proxies,(a,b)->{if(a.ms<0||b.ms<0){if(a.ms<0&&b.ms<0)return a.host.compareTo(b.host);return a.ms<0?1:-1;}return ascending?Long.compare(a.ms,b.ms):Long.compare(b.ms,a.ms);});sort.setText(ascending?"По пингу ↑":"По пингу ↓");}
 void render(){rows.removeAllViews();summary.setText(proxies.isEmpty()?"ВАШИ ПРОКСИ":"ПРОКСИ · "+proxies.size());if(proxies.isEmpty()){LinearLayout empty=new LinearLayout(this);empty.setOrientation(1);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(28),dp(24),dp(28));empty.setBackground(rounded(0xff1c1b1a,20));TextView symbol=text("◎",40,0xffff8a2b);empty.addView(symbol);TextView label=text("Здесь появятся прокси",18,0xfff6f0ea);label.setTypeface(null,1);empty.addView(label);space(empty,8);TextView hint=text("Получите список, проверьте пинг\nи выберите самый быстрый.",14,0xffb2aaa4);hint.setGravity(Gravity.CENTER);empty.addView(hint);rows.addView(empty);return;}for(Proxy p:proxies){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(16),dp(16),dp(14));card.setBackground(rounded(0xff1c1b1a,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);rows.addView(card,lp);TextView host=text(p.host,16,0xfff6f0ea);host.setTypeface(null,1);host.setMaxLines(2);host.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(host);space(card,6);LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.addView(text("MTProto · порт "+p.port,12,0xffb2aaa4),new LinearLayout.LayoutParams(0,-2,1));TextView badge=text(p.ms==-2?"Не проверен":p.ms<0?"Не отвечает":p.ms+" мс",12,p.ms>=0?0xffff8a2b:p.ms==-1?0xffffb69c:0xffb2aaa4);badge.setPadding(dp(10),dp(5),dp(10),dp(5));badge.setBackground(rounded(p.ms>=0?0xff302218:0xff282522,10));meta.addView(badge);card.addView(meta);space(card,12);Button connect=button("Подключить  ↗",false);connect.setOnClickListener(v->connect(p));card.addView(connect,new LinearLayout.LayoutParams(-1,dp(46)));}TextView foot=text("Пинг измеряет TCP-соединение, а не работу MTProto. Добавление прокси подтвердите в Telegram.\n\nИсточники выбираются кнопкой выше.",12,0xffb2aaa4);foot.setPadding(dp(4),dp(6),dp(4),dp(20));rows.addView(foot);}
 Uri proxyUri(Proxy p){return new Uri.Builder().scheme("tg").authority("proxy").appendQueryParameter("server",p.host).appendQueryParameter("port",""+p.port).appendQueryParameter("secret",p.secret).build();} void connect(Proxy p){try{startActivity(new Intent(Intent.ACTION_VIEW,proxyUri(p)));}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setTitle("Telegram не найден").setMessage("Установите Telegram или приложение, которое поддерживает ссылки tg://proxy.").setPositiveButton("OK",null).show();}}
 @Override protected void onDestroy(){stopped=true;io.shutdownNow();if(probes!=null)probes.shutdownNow();super.onDestroy();}
}




