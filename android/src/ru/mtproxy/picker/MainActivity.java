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
 enum Screen { HOME, MANUAL, FREE, AUTOMATIC }
 boolean loadingFree=false;
 volatile CancellableOperation operation;
 LinearLayout freeRows;Button refreshFree,checkFree,bestFree;int visibleFree=60;
 final ArrayList<Proxy> candidates=new ArrayList<>();
 final ArrayList<Proxy> proxies=new ArrayList<>();
 final ExecutorService io=Executors.newSingleThreadExecutor();
 ExecutorService probes; boolean busy=false, ascending=true; volatile boolean stopped=false;
 TextView status,summary; Button load,ping,sort; LinearLayout rows; ProgressBar progress;
 static class Proxy { String host,secret; int port; volatile long ms=-2; volatile long checkedAt=0; Proxy(String h,int p,String s){host=h;port=p;secret=s;} }
 int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
 android.graphics.drawable.GradientDrawable rounded(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(0);return d;}
 void space(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
 Button button(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(null,1);b.setTextColor(primary?0xff343434:0xfff8f8f8);b.setBackgroundTintList(null);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ff8a2b),rounded(primary?0xffffb44d:0xff555555,16),null));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));return b;}
 @Override public void onCreate(Bundle b){super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(16),dp(20),0);root.setBackgroundColor(0xff343434);
  root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(20),i.getSystemWindowInsetTop()+dp(16),dp(20),i.getSystemWindowInsetBottom());return i;});
  LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);logo.setImageResource(getResources().getIdentifier("ic_launcher","drawable",getPackageName()));brand.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout title=new LinearLayout(this);title.setOrientation(1);title.setPadding(dp(12),0,0,0);TextView name=text("Ёкси",24,0xfff6f0ea);name.setTypeface(null,1);title.addView(name);title.addView(text("Это вам не это",13,0xffdedede));brand.addView(title);root.addView(brand);space(root,20);
  LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(20),dp(18),dp(20),dp(18));hero.setBackground(rounded(0xffffb44d,0));TextView heading=text("Ручной выбор",24,0xff343434);heading.setTypeface(null,1);hero.addView(heading);space(hero,8);hero.addView(text("Загрузите список и проверьте соединение",14,0xff454545));space(hero,16);load=button("↓  Получить список",true);hero.addView(load,new LinearLayout.LayoutParams(-1,dp(50)));root.addView(hero);space(root,14);
  for(int i=0;i<selected.length;i++)selected[i]=getPreferences(0).getBoolean("source_"+i,i==0); sources=button("Источники · "+sourceLabel()+"  ▾",false);sources.setOnClickListener(v->chooseSources(sources));root.addView(sources,new LinearLayout.LayoutParams(-1,-2));space(root,10);
  LinearLayout actions=new LinearLayout(this);ping=button("◉  Проверить пинг",false);sort=button("По пингу ↑",false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.setMarginEnd(dp(8));actions.addView(ping,ap);actions.addView(sort,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(actions);space(root,12);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress);progress.setVisibility(View.GONE);
  status=text("Начните с загрузки списка — мы найдём доступные адреса.",13,0xffdedede);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);space(root,14);summary=text("ВАШИ ПРОКСИ",12,0xffdedede);summary.setLetterSpacing(0.10f);summary.setTypeface(null,1);root.addView(summary);space(root,8);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff343434);rows=new LinearLayout(this);rows.setOrientation(1);root.addView(rows,new LinearLayout.LayoutParams(-1,-2));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  load.setOnClickListener(v->fetch());ping.setOnClickListener(v->probe());sort.setOnClickListener(v->{ascending=!ascending;order();render();});
  String cache=getPreferences(0).getString("list","");parse(cache,proxies);restorePings();render();if(!proxies.isEmpty())status.setText("Сохранённый список: "+proxies.size()+". Обновите перед проверкой.");enable();
  manualScreen=scroll;
  manualProgress=progress;
  manualStatus=status;
  showHome();
 }
 LinearLayout modePage(){
  ScrollView scroll=new ScrollView(this);
  scroll.setFillViewport(true);
  scroll.setBackgroundColor(0xff343434);
  LinearLayout page=new LinearLayout(this);
  page.setOrientation(1);
  page.setOnApplyWindowInsetsListener((v,i)->{
   v.setPadding(dp(20),dp(20)+i.getSystemWindowInsetTop(),dp(20),dp(20)+i.getSystemWindowInsetBottom());
   return i;
  });
  scroll.addView(page);
  setContentView(scroll);
  scroll.requestApplyInsets();
  LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
  ImageView mark=new ImageView(this);mark.setImageResource(getResources().getIdentifier("ic_launcher","drawable",getPackageName()));brand.addView(mark,new LinearLayout.LayoutParams(dp(44),dp(44)));
  TextView name=text("Ёкси",23,0xfff4f4f5);name.setTypeface(null,1);name.setPadding(dp(12),0,0,0);brand.addView(name);page.addView(brand);
  space(page,24);
  return page;
 }
 void showHome(){
  screen=Screen.HOME;LinearLayout page=modePage();
  TextView greeting=text("Подключение к Telegram",21,0xfff8f8f8);greeting.setTypeface(null,1);page.addView(greeting);space(page,6);page.addView(text("Свой сервер или бесплатный прокси — выберите способ.",14,0xffd7d7d7));space(page,20);
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setGravity(Gravity.CENTER);panel.setPadding(dp(16),dp(14),dp(16),dp(16));panel.setBackgroundColor(0xffffb44d);page.addView(panel,new LinearLayout.LayoutParams(-1,-2));
  TextView cap=text("МОЙ VPS",12,0xff454545);cap.setTypeface(null,1);panel.addView(cap);
  DuckView duck=new DuckView(this);duck.setContentDescription("Жупень. Открыть свой VPS");duck.setOnClickListener(v->openPanel());panel.addView(duck,new LinearLayout.LayoutParams(dp(160),dp(160)));
  Button own=button("Жупень  →",false);own.setOnClickListener(v->openPanel());panel.addView(own,new LinearLayout.LayoutParams(-1,dp(50)));space(panel,8);panel.addView(text("Создать прокси на своём сервере",13,0xff454545));space(page,16);
  LinearLayout choices=new LinearLayout(this);LinearLayout free=tile("Халява","Автопоиск лучшего","↗",0xffff825d,()->showAutomatic());LinearLayout manual=tile("Манул","Выбрать из списка","≡",0xff94d9e8,()->openManual());LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(8);choices.addView(free,left);choices.addView(manual,new LinearLayout.LayoutParams(0,-2,1));page.addView(choices);space(page,16);
  TextView sourceHeading=text("ИСТОЧНИКИ БЕСПЛАТНЫХ ПРОКСИ",11,0xffdedede);sourceHeading.setLetterSpacing(.08f);page.addView(sourceHeading);space(page,8);Button sourcePicker=button(sourceLabel()+"  ▾",false);sourcePicker.setOnClickListener(v->chooseSources(sourcePicker));page.addView(sourcePicker);
 }
 LinearLayout tile(String title,String detail,String symbol,int color,Runnable action){LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setPadding(dp(16),dp(14),dp(16),dp(16));tile.setBackgroundColor(color);TextView icon=text(symbol,25,0xff343434);tile.addView(icon);TextView label=text(title,20,0xff343434);label.setTypeface(null,1);tile.addView(label);space(tile,4);tile.addView(text(detail,12,0xff343434));tile.setClickable(true);tile.setFocusable(true);tile.setContentDescription(title+". "+detail);tile.setOnClickListener(v->action.run());return tile;}
 void openManual(){screen=Screen.MANUAL;status=manualStatus;progress=manualProgress;render();enable();if(!proxies.isEmpty())status.setText("Прокси в списке · "+proxies.size());setContentView(manualScreen);manualScreen.requestApplyInsets();}
 void openPanel(){
  startActivity(new Intent(this,PanelActivity.class));
 }
 void showAutomatic(){showAutomatic(true);}
 void showAutomatic(boolean loadIfEmpty){
  screen=Screen.FREE;visibleFree=60;LinearLayout page=modePage();Button back=button("← На главный экран",false);back.setOnClickListener(v->onBackPressed());page.addView(back);space(page,12);page.addView(text("Халява",25,0xfff8f8f8));space(page,12);
  refreshFree=button("Обновить список",true);refreshFree.setOnClickListener(v->{if(!busy)fetch();});page.addView(refreshFree);space(page,8);
  LinearLayout actions=new LinearLayout(this);checkFree=button("Проверить",false);bestFree=button("Давай!",false);bestFree.setTextColor(0xff102519);bestFree.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff63d28a));checkFree.setOnClickListener(v->{if(!busy)probe();});bestFree.setOnClickListener(v->openCandidates(null));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.rightMargin=dp(8);actions.addView(checkFree,lp);actions.addView(bestFree,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(actions);space(page,12);
  status=text("",13,0xffdedede);page.addView(status);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setVisibility(View.GONE);page.addView(progress);dice=new DiceView();dice.setVisibility(View.GONE);page.addView(dice,new LinearLayout.LayoutParams(-1,dp(100)));freeRows=new LinearLayout(this);freeRows.setOrientation(1);page.addView(freeRows);renderFree();enable();if(proxies.isEmpty()&&loadIfEmpty)fetch();else status.setText("Сохранённые результаты · "+proxies.size()+". «Проверить» обновит пинг.");
 }
 void showFreeLoading(){if(loadingFree)return;loadingFree=true;LinearLayout page=modePage();page.addView(text("Халява",25,0xfff8f8f8));space(page,20);LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(16),dp(16),dp(16),dp(16));panel.setBackgroundColor(0xff454545);page.addView(panel);status=text("Подготовка…",16,0xfff8f8f8);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);panel.addView(status);dice=new DiceView();panel.addView(dice,new LinearLayout.LayoutParams(-1,dp(140)));progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setVisibility(View.VISIBLE);panel.addView(progress);space(page,16);Button stop=button("Стопэ",true);stop.setOnClickListener(v->cancelOperation());page.addView(stop);}
 void cancelOperation(){CancellableOperation current=operation;operation=null;if(current!=null)current.cancel();if(probes!=null)probes.shutdownNow();busy=false;finishFreeLoading();enable();status.setText("Остановлено. Предыдущий список и результаты сохранены.");}
 void operationUi(CancellableOperation run,Runnable action){ui(()->{if(operation==run&&!run.isCancelled())action.run();});}
 void finishFreeLoading(){if(loadingFree){loadingFree=false;showAutomatic(false);}}
 void renderFree(){
  if(freeRows==null||loadingFree)return;freeRows.removeAllViews();int count=Math.min(visibleFree,proxies.size());
  for(int i=0;i<count;i++){Proxy p=proxies.get(i);LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackgroundColor(0xff454545);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);freeRows.addView(row,rp);row.addView(text(p.host+":"+p.port,16,0xfff8f8f8));row.addView(text(p.ms>=0?PingQuality.label(p.ms)+" · "+p.ms+" мс · "+android.text.format.DateFormat.format("dd.MM HH:mm",p.checkedAt):PingQuality.label(p.ms),13,PingQuality.color(p.ms)));Button want=button("Хочу!",false);want.setEnabled(!busy);want.setOnClickListener(v->openCandidates(p));row.addView(want);}
  if(count<proxies.size()){Button more=button("Показать ещё · "+(proxies.size()-count),false);more.setOnClickListener(v->{visibleFree+=60;renderFree();});freeRows.addView(more);}
 }
 static ArrayList<Proxy> candidateList(List<Proxy> list,Proxy wanted){ArrayList<Proxy> result=new ArrayList<>();for(Proxy p:list)if(p.ms>=0&&p!=wanted)result.add(p);result.sort((a,b)->Long.compare(a.ms,b.ms));if(wanted!=null)result.add(0,wanted);return result;}
 void openCandidates(Proxy wanted){
  if(busy)return;candidates.clear();candidates.addAll(candidateList(proxies,wanted));
  if(candidates.isEmpty()){Toast.makeText(this,"Сначала нажмите «Проверить»: нет доступных вариантов",Toast.LENGTH_LONG).show();return;}
  screen=Screen.AUTOMATIC;candidateIndex=0;candidateCount=candidates.size();LinearLayout page=modePage();Button back=button("← К списку",false);back.setOnClickListener(v->onBackPressed());page.addView(back);space(page,16);status=text("",14,0xffdedede);page.addView(status);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setVisibility(View.GONE);page.addView(progress);automaticResults=new LinearLayout(this);automaticResults.setOrientation(1);page.addView(automaticResults);recheckCandidate();
 }
 void recheckCandidate(){
  Proxy p=candidates.get(candidateIndex);busy=true;automaticResults.removeAllViews();status.setText("Проверяем "+p.host+"…");progress.setIndeterminate(true);progress.setVisibility(View.VISIBLE);io.execute(()->{ProbeEngine.refreshDns();long value=ProbeEngine.measure(p.host,p.port,2000);ui(()->{p.ms=value;p.checkedAt=System.currentTimeMillis();busy=false;progress.setVisibility(View.GONE);progress.setIndeterminate(false);savePings();showCandidate();});});
 }
 void restorePings(){try{org.json.JSONObject saved=new org.json.JSONObject(getPreferences(0).getString("pings","{}"));for(Proxy p:proxies){org.json.JSONArray row=saved.optJSONArray(proxyUri(p).toString());if(row!=null){p.ms=row.getLong(0);p.checkedAt=row.getLong(1);}}}catch(Exception ignored){}}
 void savePings(){try{org.json.JSONObject saved=new org.json.JSONObject();for(Proxy p:proxies)saved.put(proxyUri(p).toString(),new org.json.JSONArray().put(p.ms).put(p.checkedAt));getPreferences(0).edit().putString("pings",saved.toString()).apply();}catch(Exception ignored){}}
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
  Proxy candidate=candidates.get(candidateIndex);
  LinearLayout card=new LinearLayout(this);
  card.setOrientation(1);
  card.setPadding(dp(16),dp(16),dp(16),dp(16));
  card.setBackground(rounded(0xff454545,20));
  card.addView(text("Вариант "+(candidateIndex+1)+" из "+candidateCount,13,0xffdedede));
  space(card,8);
  card.addView(text(candidate.host+" · порт "+candidate.port,17,0xfff6f0ea));
  card.addView(text(candidate.ms>=0?PingQuality.label(candidate.ms)+" · "+candidate.ms+" мс":PingQuality.label(candidate.ms),26,PingQuality.color(candidate.ms)));
  space(card,16);
  Button push=button("Пихаем",true);
  push.setEnabled(candidate.ms>=0);
  push.setOnClickListener(v->connect(candidate));
  card.addView(push);
  space(card,8);
  Button next=button("Следующий",false);
  boolean hasNext=candidateIndex+1<candidateCount;
  next.setEnabled(hasNext);
  next.setAlpha(hasNext?1f:.45f);
  next.setOnClickListener(v->{candidateIndex++;recheckCandidate();});
  card.addView(next);
  if(!hasNext){
   space(card,8);
   card.addView(text("Это последний доступный вариант.",13,0xffdedede));
  }
  automaticResults.addView(card);
 }
 @Override public void onBackPressed(){
  if(busy&&loadingFree){cancelOperation();return;}
  if(busy){
   Toast.makeText(this,"Дождитесь окончания операции",Toast.LENGTH_SHORT).show();
   return;
  }
  if(screen==Screen.HOME)super.onBackPressed();
  else if(screen==Screen.AUTOMATIC)showAutomatic();
  else showHome();
 }
 static String query(java.net.URI u,String name)throws Exception {String q=u.getRawQuery();if(q==null)return null;for(String part:q.split("&")){String[] pair=part.split("=",2);if(pair.length==2&&name.equals(pair[0]))return java.net.URLDecoder.decode(pair[1],"UTF-8");}return null;}
 static boolean validSecret(String s){if(s==null||s.length()>1024)return false;byte[] bytes;try{if(s.matches("(?i)[a-f0-9]+")&&s.length()%2==0){bytes=new byte[s.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);}else{if(!s.matches("[A-Za-z0-9_-]+={0,2}"))return false;bytes=java.util.Base64.getUrlDecoder().decode(s);}return bytes.length==16||(bytes.length==17&&(bytes[0]&255)==221)||(bytes.length>=18&&(bytes[0]&255)==238);}catch(Exception e){return false;}}
 static void parse(String data,List<Proxy> out){Set<String> seen=new HashSet<>();for(Proxy old:out)seen.add(old.host+":"+old.port+":"+old.secret);for(String line:data.split("\\r?\\n")){try{java.net.URI u=new java.net.URI(line.trim());if(!(("tg".equals(u.getScheme())&&"proxy".equals(u.getHost()))||("https".equals(u.getScheme())&&"t.me".equals(u.getHost())&&"/proxy".equals(u.getPath()))))continue;String h=query(u,"server"),s=query(u,"secret");int p=Integer.parseInt(query(u,"port"));if(h==null||h.isEmpty()||h.length()>253||!h.matches("[A-Za-z0-9.:-]+")||!validSecret(s)||p<1||p>65535)continue;h=h.toLowerCase(Locale.ROOT);if(h.endsWith("."))h=h.substring(0,h.length()-1);if(out.size()>=3000)break;if(seen.add(h+":"+p+":"+s))out.add(new Proxy(h,p,s));}catch(Exception ignored){}}}
 String sourceLabel(){ArrayList<String> names=new ArrayList<>();for(int i=0;i<selected.length;i++)if(selected[i])names.add(SOURCE_NAMES[i]);return android.text.TextUtils.join(" + ",names);}
 void chooseSources(Button trigger){final boolean[] draft=selected.clone();AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Источники прокси").setMultiChoiceItems(SOURCE_NAMES,draft,(d,index,checked)->draft[index]=checked).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{boolean any=false;for(boolean b:draft)any|=b;if(!any){Toast.makeText(this,"Выберите хотя бы один источник",Toast.LENGTH_SHORT).show();return;}if(!Arrays.equals(selected,draft)){selected=draft;android.content.SharedPreferences.Editor editor=getPreferences(0).edit();for(int i=0;i<selected.length;i++)editor.putBoolean("source_"+i,selected[i]);editor.remove("list").remove("pings").apply();proxies.clear();render();manualStatus.setText("Источники выбраны. Тыкните «Получить список».");enable();}sources.setText("Источники · "+sourceLabel()+"  ▾");trigger.setText(sources.getText());dialog.dismiss();}));dialog.show();}
 void ui(Runnable r){if(!stopped)runOnUiThread(()->{if(!stopped)r.run();});}
 void enable(){if(refreshFree!=null){refreshFree.setEnabled(!busy);checkFree.setEnabled(!busy&&!proxies.isEmpty());bestFree.setEnabled(!busy&&proxies.stream().anyMatch(p->p.ms>=0));}sources.setEnabled(!busy);sources.setAlpha(busy?.45f:1f);load.setEnabled(!busy);ping.setEnabled(!busy&&!proxies.isEmpty());sort.setEnabled(!busy&&!proxies.isEmpty());load.setAlpha(busy?.45f:1f);ping.setAlpha(ping.isEnabled()?1f:.4f);sort.setAlpha(sort.isEnabled()?1f:.4f);load.setText(proxies.isEmpty()?"↓  Получить список":"↻  Обновить список");}
 static String download(String url,CancellableOperation run)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();run.track(c);try{if(run.isCancelled())throw new InterruptedException();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","Yoxy/1.12");if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(run.isCancelled())throw new InterruptedException();bytes.write(buffer,0,n);if(bytes.size()>1024*1024)throw new IOException("Список слишком большой");}}return bytes.toString("UTF-8");}finally{c.disconnect();}}
 void fetch(){
  if(busy)return;CancellableOperation run=new CancellableOperation();operation=run;busy=true;enable();if(screen==Screen.FREE)showFreeLoading();progress.setIndeterminate(false);progress.setProgress(0);progress.setVisibility(View.VISIBLE);status.setText("Загрузка источников…");final boolean[] requested=selected.clone();int count=0;for(boolean checked:requested)if(checked)count++;progress.setMax(count);
  run.track(io.submit(()->{ArrayList<Proxy> next=new ArrayList<>();ArrayList<String> failed=new ArrayList<>();ExecutorService downloads=Executors.newFixedThreadPool(3);ArrayList<Future<String>> futures=new ArrayList<>();ArrayList<Integer> names=new ArrayList<>();for(int i=0;i<requested.length;i++)if(requested[i]){final int index=i;names.add(i);Future<String> future=downloads.submit(()->download(SOURCE_URLS[index],run));run.track(future);futures.add(future);}long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
   try{for(int i=0;i<futures.size();i++){if(run.isCancelled())return;try{long remaining=deadline-System.nanoTime();if(remaining<=0)throw new TimeoutException();String raw=futures.get(i).get(remaining,TimeUnit.NANOSECONDS);ArrayList<Proxy> entries=new ArrayList<>();parse(raw,entries);if(entries.isEmpty())throw new IOException();parse(raw,next);}catch(Exception e){if(run.isCancelled())return;failed.add(SOURCE_NAMES[names.get(i)]);}final int done=i+1;final String name=SOURCE_NAMES[names.get(i)];operationUi(run,()->{progress.setProgress(done);status.setText("Источники: "+done+" / "+futures.size()+" · "+name);});}}finally{downloads.shutdownNow();}
   operationUi(run,()->{failedSources=android.text.TextUtils.join(", ",failed);if(next.isEmpty()){operation=null;busy=false;progress.setVisibility(View.GONE);finishFreeLoading();status.setText("Не удалось обновить список. Прежние результаты сохранены."+sourceErrors());enable();return;}if(screen==Screen.FREE)probeList(next,run);else{operation=null;proxies.clear();proxies.addAll(next);persistList();busy=false;progress.setVisibility(View.GONE);render();status.setText("Найдено: "+next.size()+". Нажмите проверку пинга."+sourceErrors());enable();}});
  }));
 }
 void persistList(){StringBuilder cache=new StringBuilder();for(Proxy p:proxies)cache.append(proxyUri(p)).append('\n');getPreferences(0).edit().putString("list",cache.toString()).apply();savePings();}
 String sourceErrors(){return failedSources.isEmpty()?"":" Не загрузились: "+failedSources+".";}
 static long measure(Proxy p){return ProbeEngine.measure(p.host,p.port,1500);}
 void probe(){if(busy||proxies.isEmpty())return;CancellableOperation run=new CancellableOperation();operation=run;busy=true;enable();if(screen==Screen.FREE)showFreeLoading();probeList(new ArrayList<>(proxies),run);}
 void probeList(List<Proxy> input,CancellableOperation run){
  final boolean free=screen==Screen.FREE;final ArrayList<Proxy> batch=new ArrayList<>();for(Proxy p:input)batch.add(new Proxy(p.host,p.port,p.secret));final int total=batch.size();progress.setIndeterminate(false);progress.setMax(total);progress.setProgress(0);progress.setVisibility(View.VISIBLE);status.setText("Проверено 0 / "+total);ProbeEngine.refreshDns();probes=Executors.newFixedThreadPool(32);final java.util.concurrent.atomic.AtomicInteger completed=new java.util.concurrent.atomic.AtomicInteger();final ConcurrentHashMap<String,CompletableFuture<Long>> endpoints=new ConcurrentHashMap<>();
  for(Proxy p:batch)run.track(probes.submit(()->{if(stopped||run.isCancelled())return;String key=p.host+":"+p.port;CompletableFuture<Long> own=new CompletableFuture<>();CompletableFuture<Long> previous=endpoints.putIfAbsent(key,own);long ms=-1;if(previous==null){ms=measure(p);own.complete(ms);}else try{ms=previous.get(4,TimeUnit.SECONDS);}catch(Exception ignored){}if(run.isCancelled())return;p.ms=ms;p.checkedAt=System.currentTimeMillis();int done=completed.incrementAndGet();if(done==total||done%8==0)operationUi(run,()->{if(done<progress.getProgress())return;progress.setProgress(done);status.setText("Проверено "+done+" / "+total+" · "+(done*100/total)+"%");if(done!=total)return;operation=null;proxies.clear();proxies.addAll(batch);busy=false;ascending=true;order();persistList();enable();progress.setVisibility(View.GONE);int available=(int)proxies.stream().filter(v->v.ms>=0).count();if(free){finishFreeLoading();renderFree();}else render();status.setText("Доступно "+available+" из "+total+" · результаты сохранены."+sourceErrors());});}));probes.shutdown();
 }
 void order(){Collections.sort(proxies,(a,b)->{if(a.ms<0||b.ms<0){if(a.ms<0&&b.ms<0)return a.host.compareTo(b.host);return a.ms<0?1:-1;}return ascending?Long.compare(a.ms,b.ms):Long.compare(b.ms,a.ms);});sort.setText(ascending?"По пингу ↑":"По пингу ↓");}
 void render(){rows.removeAllViews();summary.setText(proxies.isEmpty()?"ВАШИ ПРОКСИ":"ПРОКСИ · "+proxies.size());if(proxies.isEmpty()){LinearLayout empty=new LinearLayout(this);empty.setOrientation(1);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(28),dp(24),dp(28));empty.setBackground(rounded(0xff454545,20));TextView symbol=text("◎",40,0xffff8a2b);empty.addView(symbol);TextView label=text("Здесь появятся прокси",18,0xfff6f0ea);label.setTypeface(null,1);empty.addView(label);space(empty,8);TextView hint=text("Получите список, проверьте пинг\nи выберите самый быстрый.",14,0xffdedede);hint.setGravity(Gravity.CENTER);empty.addView(hint);rows.addView(empty);return;}for(Proxy p:proxies){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(16),dp(16),dp(14));card.setBackground(rounded(0xff454545,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);rows.addView(card,lp);TextView host=text(p.host,16,0xfff6f0ea);host.setTypeface(null,1);host.setMaxLines(2);host.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(host);space(card,6);LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.addView(text("MTProto · порт "+p.port,12,0xffdedede),new LinearLayout.LayoutParams(0,-2,1));TextView badge=text(p.ms>=0?PingQuality.label(p.ms)+" · "+p.ms+" мс":PingQuality.label(p.ms),12,PingQuality.color(p.ms));badge.setPadding(dp(10),dp(5),dp(10),dp(5));badge.setBackground(rounded(p.ms>=0?0xff454545:0xff282522,10));meta.addView(badge);card.addView(meta);space(card,12);Button connect=button("Подключить  ↗",false);connect.setOnClickListener(v->connect(p));card.addView(connect,new LinearLayout.LayoutParams(-1,dp(46)));}TextView foot=text("Пинг измеряет TCP-соединение, а не работу MTProto. Добавление прокси подтвердите в Telegram.\n\nИсточники выбираются кнопкой выше.",12,0xffdedede);foot.setPadding(dp(4),dp(6),dp(4),dp(20));rows.addView(foot);}
 Uri proxyUri(Proxy p){return new Uri.Builder().scheme("tg").authority("proxy").appendQueryParameter("server",p.host).appendQueryParameter("port",""+p.port).appendQueryParameter("secret",p.secret).build();} void connect(Proxy p){try{startActivity(new Intent(Intent.ACTION_VIEW,proxyUri(p)));}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setTitle("Telegram не найден").setMessage("Установите Telegram или приложение, которое поддерживает ссылки tg://proxy.").setPositiveButton("OK",null).show();}}
 @Override protected void onDestroy(){stopped=true;if(operation!=null)operation.cancel();io.shutdownNow();if(probes!=null)probes.shutdownNow();super.onDestroy();}
}




