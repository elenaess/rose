package br.dev.rose.launcher;

import static br.dev.rose.launcher.Ui.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HomeActivity extends Activity {
    private Ui u;
    private FrameLayout base, safe, desktop, overlay;
    private View navFill, homeChoice;
    private NowPlaying media;
    private android.appwidget.AppWidgetHost widgetHost;
    private android.appwidget.AppWidgetManager widgetManager;
    private LinearLayout sheet, rail;
    private ListView homeList, drawerList;
    private HomeList homeAdapter;
    private AlphabetIndex alphabet;
    private static final long MOTION=90L;
    private SharedPreferences prefs;
    private final Catalog catalog = new Catalog();
    private final ArrayList<Catalog.Entry> apps = new ArrayList<>();
    private final ArrayList<String> pins = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int revision;
    private boolean loaded;
    private String page = "home", filter = "all";
    private EditText query, input;
    private AppList adapter;
    private TextView allTab, pinTab, termOutput, termStatus;
    private ScrollView log;
    private View send, abandon;
    private String draft = "";
    private int accent, surface;
    private int taskbar;
    private boolean resumed;
    private final Runnable terminalListener = () -> { if ("term".equals(page)) updateTerminal(); };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { catalog.icons.evictAll(); loadApps(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        u = new Ui(this); prefs = getSharedPreferences("desktop",MODE_PRIVATE);
        widgetHost=new android.appwidget.AppWidgetHost(this,2701);
        widgetManager=android.appwidget.AppWidgetManager.getInstance(this);
        String saved = prefs.getString("pins","");
        if (!saved.isEmpty()) pins.addAll(Arrays.asList(saved.split("\n")));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        base = new FrameLayout(this);
        base.setBackgroundColor(Color.TRANSPARENT);
        safe = new FrameLayout(this);base.addView(safe);
        navFill = new View(this);base.addView(navFill,new FrameLayout.LayoutParams(-1,0,Gravity.BOTTOM));
        navFill.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        base.setOnApplyWindowInsetsListener((v, in) -> {
            Insets bounds = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            safe.setPadding(bounds.left,bounds.top,bounds.right,bounds.bottom);
            FrameLayout.LayoutParams bar=(FrameLayout.LayoutParams)navFill.getLayoutParams();
            int bottom=in.getInsets(WindowInsets.Type.navigationBars()).bottom;
            if(bar.height!=bottom){bar.height=bottom;navFill.setLayoutParams(bar);}
            navFill.setVisibility(in.isVisible(WindowInsets.Type.ime())?View.GONE:View.VISIBLE);
            return in;
        });
        setContentView(base);
        showHome();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED); f.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packages,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(packages,f);
        loadApps();
        if (state != null) {
            String prev = state.getString("page","home");
            draft = state.getString("draft","");
            if ("apps".equals(prev)) openApps(false);
            else if ("term".equals(prev)) openTerm();
            else if ("settings".equals(prev)) openSettings();
        }
    }
    @Override protected void onResume() { super.onResume(); resumed=true; TerminalResult.onChange = terminalListener; terminalListener.run(); updateHomeChoice(); if(media!=null)media.start(); }
    @Override protected void onPause() { resumed=false;if(media!=null)media.stop(); if (TerminalResult.onChange == terminalListener) TerminalResult.onChange = null; super.onPause(); }
    @Override protected void onDestroy() { if(media!=null)media.stop();widgetHost.stopListening();unregisterReceiver(packages);worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);state.putString("page",page);
        state.putString("draft","term".equals(page) && input != null ? input.getText().toString() : draft);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent);setIntent(intent);closeSheet(); }
    @Override public void onBackPressed() {
        if (!page.equals("home")) closeSheet();
        else if (!getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME)) super.onBackPressed();
    }
    private void loadApps() {
        int sequence=++revision;
        worker.execute(() -> {
            ArrayList<Catalog.Entry> list=catalog.load(this);
            for(Catalog.Entry e:list){if(Thread.currentThread().isInterrupted())return;catalog.icon(this,e);}
            main.post(() -> {
                if (isDestroyed() || sequence != revision) return;
                apps.clear();apps.addAll(list);loaded=true;
                if (!prefs.contains("pins")) seedPins();
                drawRail();
                if ("apps".equals(page)) refreshApps();
            });
        });
    }
    private void seedPins() {
        for (String category : new String[]{Intent.CATEGORY_APP_BROWSER,Intent.CATEGORY_APP_MESSAGING,Intent.CATEGORY_APP_MUSIC}) {
            android.content.pm.ResolveInfo pick=getPackageManager().resolveActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,category),PackageManager.MATCH_DEFAULT_ONLY);
            if (pick==null) continue;
            for (Catalog.Entry e:apps) if (e.info.activityInfo.packageName.equals(pick.activityInfo.packageName)) {
                if (!pins.contains(e.key)) pins.add(e.key);break;
            }
        }
        savePins();
    }
    private void savePins(){prefs.edit().putString("pins",TextUtils.join("\n",pins)).apply();}
    private void colors(){
        boolean berry=prefs.getBoolean("berry",false);
        accent=berry?0xfff48ab8:ROSE; surface=berry?0xffffd9e8:0xffffe6ee;
        taskbar=berry?0xffe99abd:0xfff5a8c9;
    }
        private void showHome() {
        colors();if(media!=null){media.stop();media=null;}safe.removeAllViews();page="home";overlay=null;
        navFill.setBackgroundColor(taskbar);
        if(getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        desktop=new FrameLayout(this);safe.addView(desktop);
        boolean compact=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        int screen=getResources().getConfiguration().screenWidthDp;
        int widgetWidth=Math.max(136,Math.min(184,screen/2-10));
        int railWidth=Math.max(120,Math.min(240,screen-widgetWidth-24));
        rail=u.box(true);u.pad(rail,3,3,3,3);
        u.place(desktop,rail,railWidth,FULL,Gravity.TOP|Gravity.START,6,8,0,58);
        homeList=new ListView(this);homeList.setDivider(null);homeList.setDividerHeight(0);
        homeList.setCacheColorHint(Color.TRANSPARENT);homeList.setVerticalScrollBarEnabled(false);
        homeList.setSelector(new android.graphics.drawable.ColorDrawable(0x30ffb5d1));
        homeList.setClipToPadding(false);homeList.setPadding(0,u.dp(3),0,u.dp(5));
        homeAdapter=new HomeList();homeList.setAdapter(homeAdapter);
        rail.addView(homeList,u.flex(true));
        homeList.setOnItemClickListener((a,v,pos,id)->{if(pos==0)openApps(false);else if(pos==1)openTerm();else launch(homeAdapter.items.get(pos-2));});
        homeList.setOnItemLongClickListener((a,v,pos,id)->{if(pos<2)return false;pinDialog(homeAdapter.items.get(pos-2));return true;});
        drawRail();
        LinearLayout info=u.box(true);info.setBackground(u.shape(surface,0,BORDER));
        LinearLayout title=u.box(false);title.setBackground(u.titleBar(accent));u.pad(title,7,0,4,0);
        ImageView titleLogo=new ImageView(this);titleLogo.setImageResource(R.drawable.rose_logo);title.addView(titleLogo,u.lp(22,22));u.gap(title,5);
        title.addView(u.text("Sakura",13,INK,"sans-serif-medium"),new LinearLayout.LayoutParams(0,WRAP,1));
        title.addView(u.iconButton(R.drawable.ic_ellipsis,"Configurações",INK,surface,14,this::openSettings),u.lp(30,30));
        info.addView(title,u.lp(FULL,32));
        LinearLayout infoBody=u.box(true);u.pad(infoBody,9,7,9,8);
        if(prefs.getBoolean("clock",true)) {
            TextClock hour=new TextClock(this);hour.setFormat24Hour("HH:mm");hour.setFormat12Hour("h:mm a");
            hour.setTypeface(Typeface.MONOSPACE);hour.setTextColor(INK);hour.setTextSize(29);hour.setIncludeFontPadding(false);
            infoBody.addView(hour);
            TextClock date=new TextClock(this);date.setFormat12Hour("EEE, dd/MM");date.setFormat24Hour("EEE, dd/MM");
            date.setTextColor(WINE);date.setTextSize(12);infoBody.addView(date);
        }
        infoBody.addView(u.text("SAKURA  /  0.4",11,WINE,"monospace"));
        homeChoice=u.pill("Fixar como launcher",accent,WINE,this::chooseHome);infoBody.addView(homeChoice,u.lp(FULL,32));updateHomeChoice();
        info.addView(infoBody);
        LinearLayout widgets=u.box(true);widgets.addView(info,u.lp(FULL,WRAP));
        if(prefs.getBoolean("media",true)){
            u.gap(widgets,8);media=new NowPlaying(this,u,accent,surface,widgetHost,widgetManager,prefs);widgets.addView(media.view,u.lp(FULL,WRAP));if(resumed)media.start();
        }
        u.place(desktop,widgets,widgetWidth,WRAP,Gravity.TOP|Gravity.END,0,12,8,0);
        LinearLayout dock=u.box(false);u.pad(dock,3,3,3,3);dock.setBackgroundColor(taskbar);
        LinearLayout menu=u.box(false);u.pad(menu,9,0,9,0);menu.setGravity(Gravity.CENTER);
        u.flatAction(menu,0x35ffffff,()->openApps(false));ImageView menuLogo=new ImageView(this);menuLogo.setImageResource(R.drawable.rose_logo);menu.addView(menuLogo,u.lp(24,24));u.gap(menu,5);
        menu.addView(u.text("Menu",14,INK,"sans-serif-medium"));dock.addView(menu,u.lp(WRAP,46));u.gap(dock,3);
        dock.addView(u.flatIcon(R.drawable.ic_search,"Buscar aplicativos",WINE,20,()->openApps(true)),u.lp(42,46));
        dock.addView(u.flatIcon(R.drawable.ic_terminal,"Terminal",WINE,20,this::openTerm),u.lp(42,46));
        dock.addView(u.flatIcon(R.drawable.ic_sliders_horizontal,"Ajustes",WINE,20,this::openSettings),u.lp(42,46));
        u.grow(dock);
        TextClock clock=new TextClock(this);clock.setFormat24Hour("HH:mm");clock.setFormat12Hour("h:mm");clock.setTextColor(INK);clock.setTextSize(13);clock.setTypeface(Typeface.MONOSPACE);clock.setGravity(Gravity.CENTER);clock.setBackgroundColor(Color.TRANSPARENT);
        dock.addView(clock,u.lp(58,46));
        u.place(desktop,dock,FULL,WRAP,Gravity.BOTTOM,0,0,0,0);
        desktop.setOnLongClickListener(v->{openSettings();return true;});
    }
    private View shortcut(String label,android.graphics.drawable.Drawable icon,Runnable action) {
        LinearLayout item=u.box(true);item.setGravity(Gravity.CENTER);u.pad(item,1,5,1,5);
        ImageView image=new ImageView(this);image.setImageDrawable(icon);item.addView(image,u.lp(36,36));u.gap(item,4);
        TextView name=u.text(label,11,Color.WHITE,"sans-serif-medium");name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setShadowLayer(u.dp(3),0,u.dp(1),Color.BLACK);name.setBackgroundColor(0x7030202c);u.pad(name,3,1,3,1);item.addView(name,u.lp(FULL,WRAP));
        u.action(item,0,0,action);item.setContentDescription(label);return item;
    }
    private android.graphics.drawable.Drawable styledIcon(Catalog.Entry item){
        android.graphics.drawable.Drawable d=catalog.icon(this,item).mutate();
        if(prefs.getBoolean("pink_icons",true)){
            // Remap luminance to a rose palette, preserving recognizable icon details.
            android.graphics.ColorMatrix m=new android.graphics.ColorMatrix(new float[]{.155f,.520f,.053f,0,69,.133f,.447f,.046f,0,34,.136f,.455f,.046f,0,61,0,0,0,1,0});
            d.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
        }else d.clearColorFilter();return d;
    }
    private void updateHomeChoice(){if(homeChoice!=null)homeChoice.setVisibility(getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME)?View.GONE:View.VISIBLE);}
    private void drawRail() {
        if(homeAdapter==null)return;
        homeAdapter.items.clear();
        HashSet<String> seen=new HashSet<>();
        for(String key:pins){Catalog.Entry e=entry(key);if(e!=null&&seen.add(key))homeAdapter.items.add(e);}
        for(Catalog.Entry e:apps)if(seen.add(e.key))homeAdapter.items.add(e);
        homeAdapter.notifyDataSetChanged();
    }
    final class HomeList extends BaseAdapter {
        final ArrayList<Catalog.Entry> items=new ArrayList<>();
        public int getCount(){return items.size()+2;}
        public Object getItem(int i){return i<2?null:items.get(i-2);}
        public long getItemId(int i){return i;}
        public View getView(int i,View recycled,android.view.ViewGroup parent){
            LinearLayout row=(LinearLayout)recycled;
            if(row==null){
                row=u.box(false);u.pad(row,5,4,5,4);row.setLayoutParams(new AbsListView.LayoutParams(FULL,u.dp(54)));
                ImageView img=new ImageView(HomeActivity.this);img.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(img,u.lp(34,34));u.gap(row,7);
                TextView label=u.text("",14,Color.WHITE,"sans-serif-medium");u.single(label);
                label.setShadowLayer(u.dp(3),0,u.dp(1),Color.BLACK);
                label.setBackground(u.shape(0x7030202c,0,0));u.pad(label,4,3,4,3);
                row.addView(label,new LinearLayout.LayoutParams(0,WRAP,1));
            }
            ImageView img=(ImageView)row.getChildAt(0);TextView label=(TextView)row.getChildAt(2);
            if(i<2){img.setImageResource(i==0?R.drawable.sakura_folder:R.drawable.sakura_terminal);label.setText(i==0?"Aplicativos":"Terminal");}
            else{Catalog.Entry e=items.get(i-2);img.setImageDrawable(styledIcon(e));label.setText(e.name);}
            return row;
        }
    }
    private Catalog.Entry entry(String key) {for (Catalog.Entry a:apps) if(a.key.equals(key))return a;return null;}
    private void pinDialog(Catalog.Entry item) {
        boolean exists=pins.contains(item.key);
        new AlertDialog.Builder(this).setTitle(item.name)
            .setItems(new String[]{exists?"Remover dos favoritos":"Fixar no topo","Informações do app"},(d,which)->{
                if(which==1) {external(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+item.info.activityInfo.packageName)));return;}
                pins.removeIf(key->entry(key)==null);
                if(exists)pins.remove(item.key);
                else pins.add(item.key);
                savePins();drawRail();if(page.equals("apps"))refreshApps();
            }).show();
    }
    private void launch(Catalog.Entry item) {
        Intent launch=Intent.makeMainActivity(ComponentName.unflattenFromString(item.key));
        if(external(launch))closeSheet(false);
    }
    private boolean external(Intent intent) {
        try {startActivity(intent);return true;}
        catch(ActivityNotFoundException|SecurityException e) {toast("O Android não encontrou um app disponível para essa ação.");return false;}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void keyboardOff(){
        if(getCurrentFocus()!=null)getSystemService(InputMethodManager.class).hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),0);
    }
    private void closeSheet(){closeSheet(true);}
    private void closeSheet(boolean animate){
        if(page.equals("term")&&input!=null)draft=input.getText().toString();
        FrameLayout old=overlay;LinearLayout oldSheet=sheet;
        overlay=null;page="home";keyboardOff();
        if(old!=null){
            if(oldSheet!=null)oldSheet.animate().cancel();
            if(animate&&oldSheet!=null&&android.animation.ValueAnimator.areAnimatorsEnabled()){
                old.setOnClickListener(null);
                oldSheet.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                oldSheet.animate().alpha(0f).translationY(u.dp(10)).setDuration(MOTION)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(()->safe.removeView(old)).start();
            }else safe.removeView(old);
        }
        desktop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    private void panel(String id,String title,int icon) {
        closeSheet(false);page=id;
        desktop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        overlay=new FrameLayout(this);safe.addView(overlay);
        overlay.setBackgroundColor(Color.TRANSPARENT);overlay.setOnClickListener(v->closeSheet());
        sheet=u.box(true);sheet.setClickable(true);sheet.setFocusableInTouchMode(true);
        sheet.setBackground(u.shape(surface,0,WINE));u.pad(sheet,5,5,5,5);
        FrameLayout.LayoutParams p=u.frame(FULL,FULL,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        int available=getResources().getConfiguration().screenWidthDp;
        if(available>548)p.width=u.dp(516);
        p.setMargins(u.dp(7),u.dp(16),u.dp(7),u.dp(59));overlay.addView(sheet,p);sheet.requestFocus();
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){sheet.setAlpha(.65f);sheet.setTranslationY(u.dp(12));sheet.animate().alpha(1f).translationY(0f).setDuration(MOTION).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
        LinearLayout head=u.box(false);u.pad(head,7,0,3,0);head.setBackground(u.titleBar(accent));
        head.addView(u.glyph(icon,WINE),u.lp(22,22));u.gap(head,10);
        head.addView(u.text(title+" — Sakura",17,INK,"sans-serif-medium"),new LinearLayout.LayoutParams(0,WRAP,1));
        head.addView(u.iconButton(R.drawable.ic_x,"Fechar",INK,surface,17,this::closeSheet),u.lp(42,42));
        sheet.addView(head,u.lp(FULL,44));u.gap(sheet,5);
    }
    private void openApps(boolean type){
        panel("apps","Aplicativos",R.drawable.ic_layout_grid);
        LinearLayout search=u.box(false);search.setBackground(u.shape(MILK,15,BORDER));u.pad(search,12,0,8,0);
        search.addView(u.glyph(R.drawable.ic_search,MUTE),u.lp(19,19));u.gap(search,9);
        query=new EditText(this);query.setSingleLine();query.setHint("Buscar aplicativo");query.setContentDescription("Buscar aplicativo");
        query.setTextSize(16);query.setTextColor(INK);query.setHintTextColor(MUTE);query.setBackgroundColor(Color.TRANSPARENT);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.addView(query,u.flex(false));sheet.addView(search,u.lp(FULL,52));
        LinearLayout tabs=u.box(false);u.pad(tabs,0,5,0,5);
        allTab=u.pill("Todos",accent,INK,()->{filter="all";refreshApps();});
        pinTab=u.pill("Favoritos",accent,INK,()->{filter="pins";refreshApps();});
        tabs.addView(allTab,u.lp(WRAP,48));u.gap(tabs,5);tabs.addView(pinTab,u.lp(WRAP,48));sheet.addView(tabs);
        ListView list=new ListView(this);drawerList=list;list.setDivider(null);list.setDividerHeight(0);list.setFastScrollEnabled(false);
        list.setCacheColorHint(Color.TRANSPARENT);list.setVerticalScrollBarEnabled(false);
        TextView empty=u.text("",15,MUTE,"sans-serif");empty.setGravity(Gravity.CENTER);empty.setTag("empty");
        LinearLayout scrolling=u.box(false);sheet.addView(scrolling,u.flex(true));
        FrameLayout space=new FrameLayout(this);scrolling.addView(space,u.flex(false));space.addView(empty);space.addView(list);list.setEmptyView(empty);
        alphabet=new AlphabetIndex(this);scrolling.addView(alphabet,u.lp(32,FULL));
        adapter=new AppList();list.setAdapter(adapter);filter="all";refreshApps();
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int n,int after){}
            public void afterTextChanged(Editable s){}
            public void onTextChanged(CharSequence s,int st,int before,int n){refreshApps();}
        });
        list.setOnItemClickListener((a,v,pos,key)->launch(adapter.visible.get(pos)));
        list.setOnItemLongClickListener((a,v,pos,key)->{pinDialog(adapter.visible.get(pos));return true;});
        query.setOnEditorActionListener((v,action,e)->{if(adapter.visible.size()==1)launch(adapter.visible.get(0));else keyboardOff();return true;});
        if(type){query.requestFocus();query.post(()->{if(page.equals("apps"))getSystemService(InputMethodManager.class).showSoftInput(query,InputMethodManager.SHOW_IMPLICIT);});}
    }
    private void refreshApps(){
        if(adapter==null||query==null)return;
        String term=Catalog.normalize(query.getText().toString());
        adapter.visible.clear();
        for(Catalog.Entry e:apps)if((filter.equals("all")||pins.contains(e.key))&&e.search.contains(term))adapter.visible.add(e);
        allTab.setSelected(filter.equals("all"));pinTab.setSelected(filter.equals("pins"));
        allTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(allTab.isSelected()?accent:surface));
        pinTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(pinTab.isSelected()?accent:surface));
        TextView empty=sheet.findViewWithTag("empty");
        if(empty!=null)empty.setText(!loaded?"Lendo os apps…":!term.isEmpty()?"Nenhum aplicativo encontrado.":filter.equals("pins")?"Seus favoritos aparecem aqui. Toque no alfinete de um app em Todos.":"Nenhum aplicativo disponível neste perfil.");
        adapter.notifyDataSetChanged();if(alphabet!=null)alphabet.invalidate();
    }
    final class AppList extends BaseAdapter {
        final ArrayList<Catalog.Entry> visible=new ArrayList<>();
        public int getCount(){return visible.size();}
        public Object getItem(int i){return visible.get(i);}
        public long getItemId(int i){return i;}
        public View getView(int i,View recycle,android.view.ViewGroup parent) {
            LinearLayout row=(LinearLayout)recycle;
            if(row==null) {
                row=u.box(false);u.pad(row,7,0,0,0);row.setMinimumHeight(u.dp(56));row.setLayoutParams(new AbsListView.LayoutParams(FULL,u.dp(56)));
                row.addView(new ImageView(HomeActivity.this),u.lp(34,34));u.gap(row,13);
                TextView label=u.text("",16,INK,"sans-serif");u.single(label);
                row.addView(label,new LinearLayout.LayoutParams(0,WRAP,1));
                ImageButton pin=new ImageButton(HomeActivity.this);u.pad(pin,12,12,12,12);
                pin.setScaleType(ImageView.ScaleType.CENTER_INSIDE);pin.setBackgroundColor(Color.TRANSPARENT);
                pin.setFocusable(false);row.addView(pin,u.lp(48,48));
            }
            Catalog.Entry e=visible.get(i);
            ((ImageView)row.getChildAt(0)).setImageDrawable(styledIcon(e));
            ((TextView)row.getChildAt(2)).setText(e.name);
            ImageButton pin=(ImageButton)row.getChildAt(3);pin.setImageResource(R.drawable.ic_pin);
            pin.setColorFilter(pins.contains(e.key)?0xffaa245d:0xff876c77);
            pin.setContentDescription("Opções de "+e.name);pin.setOnClickListener(v->pinDialog(e));
            return row;
        }
    }
    final class AlphabetIndex extends View {
        final android.graphics.Paint paint=new android.graphics.Paint(3);
        final String letters="#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        char active=0;
        AlphabetIndex(Context c){super(c);setContentDescription("Índice alfabético: deslize para localizar aplicativos");setFocusable(false);}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float step=getHeight()/27f;
            paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            paint.setTextSize(Math.min(u.dp(12),step*.83f));paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            for(int i=0;i<27;i++){
                char letter=letters.charAt(i);float cy=step*(i+.5f);
                if(active==letter){paint.setColor(accent);canvas.drawCircle(getWidth()/2f,cy,Math.min(getWidth()/2f-1,step*.49f),paint);}
                paint.setColor(active==letter?WINE:MUTE);
                canvas.drawText(String.valueOf(letter),getWidth()/2f,cy-(paint.ascent()+paint.descent())/2,paint);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){
                getParent().requestDisallowInterceptTouchEvent(true);
                int index=Math.max(0,Math.min(26,(int)(e.getY()*27/Math.max(1,getHeight()))));
                char target=letters.charAt(index);
                if(active!=target){active=target;invalidate();jump(target);}
                return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){active=0;invalidate();getParent().requestDisallowInterceptTouchEvent(false);if(action==MotionEvent.ACTION_UP)performClick();return true;}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        void jump(char target){
            if(adapter==null||drawerList==null)return;
            int fallback=-1;
            for(int i=0;i<adapter.visible.size();i++){
                String name=adapter.visible.get(i).search;
                char letter=name.isEmpty()?'#':Character.toUpperCase(name.charAt(0));
                if(letter<'A'||letter>'Z')letter='#';
                if(letter==target){drawerList.setSelectionFromTop(i,0);return;}
                if(fallback<0&&letter>target)fallback=i;
            }
            if(fallback>=0)drawerList.setSelectionFromTop(fallback,0);
        }
    }
    private LinearLayout scrollingContent(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);sheet.addView(scroll,u.flex(true));
        LinearLayout body=u.box(true);scroll.addView(body);return body;
    }
    private void openSettings(){
        panel("settings","Aparência",R.drawable.ic_sliders_horizontal);
        LinearLayout body=scrollingContent();u.gap(body,7);
        LinearLayout palette=u.box(true);u.pad(palette,14,15,14,12);palette.setBackground(u.shape(MILK,17,BORDER));
        palette.addView(u.text("Cor do painel",17,INK,"sans-serif-medium"));u.gap(palette,10);
        boolean berry=prefs.getBoolean("berry",false);
        LinearLayout choices=u.box(false);
        TextView light=u.pill("Pétala",berry?surface:INK,berry?INK:MILK,()->selectColor(false));
        TextView deep=u.pill("Framboesa",berry?INK:surface,berry?MILK:INK,()->selectColor(true));
        light.setSelected(!berry);deep.setSelected(berry);choices.addView(light,u.lp(WRAP,48));u.gap(choices,6);choices.addView(deep,u.lp(WRAP,48));
        palette.addView(choices);body.addView(palette);u.gap(body,8);
        body.addView(u.commandRow(R.drawable.ic_image,"Papel de parede","Escolher no Android",this::chooseWallpaper));
        body.addView(u.commandRow(R.drawable.ic_clock_3,"Relógio na área de trabalho",prefs.getBoolean("clock",true)?"Visível · toque para ocultar":"Oculto · toque para mostrar",()->{
            prefs.edit().putBoolean("clock",!prefs.getBoolean("clock",true)).apply();showHome();openSettings();
        }));
        boolean isDefault=getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME);
        body.addView(u.commandRow(R.drawable.ic_house,"Tela inicial padrão",isDefault?"Sakura está ativo":"Escolher o Sakura",this::chooseHome));
        body.addView(u.commandRow(R.drawable.ic_palette,"Ícones rosa",prefs.getBoolean("pink_icons",true)?"Ativos · tocar para usar os originais":"Originais · tocar para usar rosa",()->{prefs.edit().putBoolean("pink_icons",!prefs.getBoolean("pink_icons",true)).apply();showHome();openSettings();}));
        body.addView(u.commandRow(R.drawable.ic_heart,"Quadro de mídia",prefs.getBoolean("media",true)?"Visível · tocar para ocultar":"Oculto · tocar para mostrar",()->{prefs.edit().putBoolean("media",!prefs.getBoolean("media",true)).apply();showHome();openSettings();}));
        body.addView(u.commandRow(R.drawable.ic_settings_2,"Escolher widget de mídia","Usar o widget fornecido pelo seu player",()->{closeSheet();if(media!=null)media.choose();else toast("Ative o quadro de mídia primeiro.");}));
        body.addView(u.commandRow(R.drawable.ic_terminal,"Conexão com Termux","Configurar comandos na home",this::termHelp));
    }
    private void selectColor(boolean berry){prefs.edit().putBoolean("berry",berry).apply();showHome();openSettings();}
    private void chooseHome(){
        RoleManager rm=getSystemService(RoleManager.class);
        if(rm.isRoleAvailable(RoleManager.ROLE_HOME)&&!rm.isRoleHeld(RoleManager.ROLE_HOME)){
            try{startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),12);return;}
            catch(RuntimeException ignored){}
        }
        external(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private void chooseWallpaper(){
        try {startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));}
        catch(ActivityNotFoundException|SecurityException e){
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
            try{startActivityForResult(pick,14);}catch(ActivityNotFoundException missing){toast("Seletor de imagens indisponível.");}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==NowPlaying.BIND||request==NowPlaying.CONFIGURE){
            if(media!=null)media.result(request,result,data);
            else if(result!=RESULT_OK){int pending=prefs.getInt("widget_pending",-1);if(pending!=-1)widgetHost.deleteAppWidgetId(pending);prefs.edit().remove("widget_pending").apply();}
            return;
        }
        if(request==12){updateHomeChoice();if(page.equals("settings"))openSettings();}
        if(request==14&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try{external(WallpaperManager.getInstance(this).getCropAndSetWallpaperIntent(data.getData()));}
            catch(RuntimeException e){toast("O sistema não conseguiu abrir o editor de papel de parede.");}
        }
    }
    private boolean hasTermux(){
        try {getPackageManager().getApplicationInfo(TerminalResult.PKG,0);return true;}
        catch(PackageManager.NameNotFoundException e){return false;}
    }
    private void fullTermux(){
        Intent i=getPackageManager().getLaunchIntentForPackage(TerminalResult.PKG);
        if(i==null) {toast("Instale e abra o Termux para iniciar uma sessão.");return;}external(i);
    }
    private void termHelp(){
        boolean installed=hasTermux();
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("Conectar ao Termux");
        if (!installed){
            b.setMessage("Instale o Termux pelo F-Droid e abra o app uma vez. Depois, volte aqui para conectar o painel.")
                .setPositiveButton("Ver Termux",(d,w)->external(new Intent(Intent.ACTION_VIEW,Uri.parse("https://f-droid.org/packages/com.termux/"))));
        }else{
            b.setMessage("1. No Termux, abra ~/.termux/termux.properties e defina allow-external-apps=true. Rode termux-reload-settings.\n\n2. Autorize o Sakura a executar comandos no Termux. Se o Android já recusou a permissão, ela fica em Apps › Sakura › Permissões.\n\n3. Teste pwd no painel. Cada envio abre um shell novo na sua pasta inicial.")
                .setNeutralButton("Abrir Termux",(d,w)->fullTermux())
                .setPositiveButton("Permissão",(d,w)->{
                    if(checkSelfPermission(TerminalResult.PERMISSION)==PackageManager.PERMISSION_GRANTED)toast("Permissão concedida.");
                    else requestPermissions(new String[]{TerminalResult.PERMISSION},18);
                });
        }
        b.setNegativeButton("Fechar",null).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==18){if(results.length>0&&results[0]!=PackageManager.PERMISSION_GRANTED)
            toast("Para autorizar depois, abra as permissões do Sakura em Ajustes.");terminalListener.run();}
    }
    private void openTerm(){
        panel("term","Terminal",R.drawable.ic_terminal);
        LinearLayout tools=u.box(false);
        tools.addView(u.pill("Configurar",MILK,INK,this::termHelp),u.lp(WRAP,48));u.gap(tools,6);
        tools.addView(u.pill("Abrir Termux",INK,MILK,this::fullTermux),u.lp(WRAP,48));
        u.grow(tools);
        tools.addView(u.iconButton(R.drawable.ic_trash_2,"Limpar saída",WINE,0,18,()->{TerminalResult.transcript="";updateTerminal();}),u.lp(48,48));
        sheet.addView(tools);u.gap(sheet,8);
        termStatus=u.text("",13,MUTE,"sans-serif-medium");u.pad(termStatus,2,0,2,5);sheet.addView(termStatus);
        log=new ScrollView(this);log.setBackground(u.shape(INK,16,0));u.pad(log,14,12,14,12);
        termOutput=u.text("",14,MILK,"monospace");termOutput.setTextIsSelectable(true);log.addView(termOutput);
        sheet.addView(log,u.flex(true));u.gap(sheet,6);
        abandon=u.pill("Parar de aguardar a resposta",MILK,WINE,TerminalResult::stopWaiting);sheet.addView(abandon,u.lp(FULL,48));
        LinearLayout prompt=u.box(false);prompt.setBackground(u.shape(MILK,15,BORDER));u.pad(prompt,10,2,3,2);
        prompt.addView(u.text("$",19,WINE,"monospace"));u.gap(prompt,5);
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(15);input.setTextColor(INK);input.setHintTextColor(MUTE);
        input.setTypeface(Typeface.MONOSPACE);input.setHint("Comando");input.setContentDescription("Comando do Termux");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTypeface(Typeface.MONOSPACE);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4096)});
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);input.setBackgroundColor(Color.TRANSPARENT);input.setText(draft);input.setSelection(input.length());
        input.setOnEditorActionListener((v,a,e)->{if(e==null||e.getAction()==KeyEvent.ACTION_DOWN)execute();return true;});
        prompt.addView(input,u.flex(false));
        send=u.iconButton(R.drawable.ic_arrow_up,"Executar comando",MILK,WINE,21,this::execute);prompt.addView(send,u.lp(48,48));
        sheet.addView(prompt,u.lp(FULL,52));updateTerminal();
    }
    private void execute(){
        if(TerminalResult.token!=null)return;
        String cmd=input.getText().toString().trim();if(cmd.isEmpty())return;
        if(!hasTermux()||checkSelfPermission(TerminalResult.PERMISSION)!=PackageManager.PERMISSION_GRANTED){termHelp();return;}
        draft="";input.setText("");TerminalResult.send(this,cmd);
    }
    private void updateTerminal(){
        if(!page.equals("term")||termOutput==null)return;
        boolean waiting=TerminalResult.token!=null;
        termStatus.setText(waiting?"Aguardando resposta do Termux…":!hasTermux()?"Termux ainda não instalado":checkSelfPermission(TerminalResult.PERMISSION)!=PackageManager.PERMISSION_GRANTED?"Permissão de execução pendente":"Comandos avulsos · Termux");
        termOutput.setText(TerminalResult.transcript.isEmpty()?"A saída dos seus comandos aparece aqui.":TerminalResult.transcript);
        send.setEnabled(!waiting);send.setAlpha(waiting?.45f:1f);
        abandon.setVisibility(waiting?View.VISIBLE:View.GONE);
        log.post(()->log.fullScroll(View.FOCUS_DOWN));
    }
}
