package ru.mtproxy.picker;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

/** Native client for the self-hosted HTTPS panel. */
public class PanelActivity extends Activity {
    final Handler handler=new Handler();
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    ConnectionVault vault; PanelClient client;
    LinearLayout page, sites; TextView status;
    EditText address,password,email,token; Button create;
    boolean running,active,resumed; String savedAddress="",savedPassword="",lastSites="";
    final Runnable poll=()->refresh();
    interface Request { JSONObject run() throws Exception; }
    interface Result { void accept(JSONObject result) throws Exception; }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(16,16,16));getWindow().setNavigationBarColor(Color.rgb(16,16,16));
        vault=new ConnectionVault(this);
        try{JSONObject stored=vault.load();if(stored!=null){savedAddress=stored.getString("address");savedPassword=stored.getString("password");}}catch(Exception e){toast("Введите настройки заново");}
        form();if(!savedAddress.isEmpty())connect(savedAddress,savedPassword);
    }
    int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,dp(9),0,dp(9));return v;}
    void base(String heading){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(16,16,16));
        page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(24),dp(28),dp(24),dp(32));
        page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(24),dp(20)+i.getSystemWindowInsetTop(),dp(24),dp(20)+i.getSystemWindowInsetBottom());return i;});
        scroll.addView(page);setContentView(scroll);scroll.requestApplyInsets();
        ImageView logo=new ImageView(this);logo.setImageResource(getResources().getIdentifier("ic_launcher","drawable",getPackageName()));page.addView(logo,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView title=text("Ёкси · "+heading,30);title.setTextColor(Color.rgb(255,147,45));page.addView(title);status=text("",14);page.addView(status);
    }
    EditText field(String hint,String value,boolean secret){
        EditText v=new EditText(this);v.setTextColor(Color.WHITE);v.setHintTextColor(Color.LTGRAY);v.setHint(hint);v.setText(value);v.setSingleLine(true);
        v.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        page.addView(v,new LinearLayout.LayoutParams(-1,dp(58)));return v;
    }
    Button button(String title,Runnable action){
        Button v=new Button(this);v.setText(title);v.setAllCaps(false);v.setTextColor(Color.BLACK);v.setTextSize(17);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(255,147,45));bg.setCornerRadius(dp(18));v.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(54));params.topMargin=dp(12);page.addView(v,params);v.setOnClickListener(w->action.run());return v;
    }
    void form(){
        active=false;handler.removeCallbacks(poll);base("Жупень · мой VPS");page.addView(text("Адрес и пароль панели из установщика",16));
        address=field("IP VPS или https://домен/manage",savedAddress,false);password=field("Пароль панели (не пароль SSH)",savedPassword,true);
        button("Открыть панель",()->connect(address.getText().toString(),password.getText().toString()));
        button("чё?",()->new AlertDialog.Builder(this).setTitle("Как завести свою Жупень")
            .setMessage("1. Арендуйте VPS с публичным IPv4: Ubuntu 22.04+ или Debian 12+ (amd64).\n\n2. Подключитесь по SSH и выполните команду из README проекта. Установщик выдаст HTTPS-адрес и пароль панели.\n\n3. Зарегистрируйтесь на dynv6.com и создайте HTTP Token.\n\n4. Введите здесь адрес и пароль панели. В панели сохраните HTTP Token dynv6 и вашу почту для HTTPS.\n\n5. Нажмите «Создать WEB-proxy», дождитесь выпуска и подключитесь в Telegram. Для нового прокси нажмите снова.\n\nПри первом входе по IP сравните отпечаток сертификата с выданным установщиком.")
            .setPositiveButton("Проект и установка",(d,w)->open("https://github.com/riehnh/mtpoxy-panel#readme"))
            .setNeutralButton("dynv6",(d,w)->open("https://dynv6.com/")) .setNegativeButton("Понятно",null).show());
    }
    void connect(String value,String pass){
        if(running)return;
        try{if(pass.isEmpty())throw new IllegalArgumentException("Введите пароль панели");String origin=PanelClient.normalizeAddress(value);client=new PanelClient(origin,vault.pin(origin));
            request(()->{client.login(pass);vault.save(origin,pass);return client.call("state",null);},data->{savedAddress=origin;savedPassword=pass;dashboard(data);});
        }catch(Exception e){status.setText(e.getMessage());}
    }
    void request(Request task,Result done){
        if(running)return;running=true;status.setText("На связи с VPS…");
        worker.execute(()->{try{JSONObject result=task.run();handler.post(()->{
            running=false;if(isFinishing()||isDestroyed())return;try{status.setText("");done.accept(result);}catch(Exception e){status.setText(e.getMessage());}
        });}catch(Exception e){handler.post(()->{
            running=false;if(isFinishing()||isDestroyed())return;
            if(e instanceof PanelClient.CertificateApproval){String fingerprint=((PanelClient.CertificateApproval)e).fingerprint;
                new AlertDialog.Builder(this).setTitle("Сертификат вашего VPS").setMessage(client.origin+"\n\nSHA-256:\n"+fingerprint+"\n\nСравните с отпечатком установщика. Подтвердите только при совпадении.")
                    .setPositiveButton("Совпадает",(d,w)->{client.pin=fingerprint;vault.pin(client.origin,fingerprint);request(task,done);}).setNegativeButton("Отмена",null).show();
            }else{
                if(e instanceof PanelClient.HttpFailure && ((PanelClient.HttpFailure)e).code==401 && active)form();
                status.setText("Не удалось: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));schedule(6000);
            }
        });}});
    }
    void dashboard(JSONObject data)throws Exception{
        active=true;lastSites="";base("Жупень · панель");page.addView(text(client.origin,14));JSONObject settings=data.getJSONObject("settings");
        email=field("Почта для HTTPS",settings.optString("email"),false);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);token=field("HTTP Token dynv6","",true);
        button("Сохранить API и почту",()->{
            final String mail=email.getText().toString().trim(),key=token.getText().toString().trim();
            request(()->client.call("settings",new JSONObject().put("email",mail).put("token",key)),r->{token.setText("");refresh();});
        });
        create=button("Создать WEB-proxy",()->request(()->client.call("create",new JSONObject().put("slug","")),r->refresh()));
        sites=new LinearLayout(this);sites.setOrientation(1);page.addView(sites);
        button("Полная веб-панель",()->open(client.origin+"/manage"));button("Изменить VPS",()->{if(!running)form();});render(data);
    }
    void refresh(){handler.removeCallbacks(poll);if(!active)return;if(running){schedule(1500);return;}request(()->client.call("state",null),r->render(r));}
    void schedule(int delay){handler.removeCallbacks(poll);if(active&&resumed)handler.postDelayed(poll,delay);}
    void render(JSONObject data)throws Exception{
        boolean busy=data.optBoolean("busy"),configured=data.optBoolean("token_configured");token.setHint(configured?"Токен сохранён · пусто = оставить":"HTTP Token dynv6");
        create.setEnabled(!busy&&configured&&!data.getJSONObject("settings").optString("email").isEmpty());create.setText(busy?"Создаётся…":"Создать WEB-proxy");
        JSONArray rows=data.getJSONArray("sites");String snapshot=rows.toString();
        if(!snapshot.equals(lastSites)){lastSites=snapshot;sites.removeAllViews();if(rows.length()==0)sites.addView(text("Здесь появятся ваши прокси",16));
            for(int i=rows.length()-1;i>=0;i--){JSONObject row=rows.getJSONObject(i);String state=row.optString("status");sites.addView(text(row.optString("hostname"),20));
                sites.addView(text(row.optString("warning").isEmpty()?row.optString("message"):row.optString("warning"),14));Button action=new Button(this);action.setAllCaps(false);sites.addView(action);
                if(state.equals("active")){action.setText("Пихаем в Telegram");action.setOnClickListener(v->{Uri link=Uri.parse(row.optString("link"));open("tg://webproxy?"+link.getEncodedQuery());});}
                else if(state.equals("error")){action.setText("Повторить");action.setOnClickListener(v->request(()->client.call("create",new JSONObject().put("retry_id",row.getInt("id"))),r->refresh()));}
                else{action.setText("Создаётся…");action.setEnabled(false);}
            }
        }schedule(3000);
    }
    void open(String link){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}catch(ActivityNotFoundException e){toast("Установите Telegram или браузер");}}
    void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();resumed=true;schedule(1000);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){active=false;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
