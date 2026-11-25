package br.dev.rose.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static br.dev.rose.launcher.Ui.*;

final class NowPlaying {
    static final int BIND=701, CONFIGURE=702;
    final Activity host;
    final Ui u;
    final LinearLayout view,body,header;
    final AppWidgetHost widgets;
    final AppWidgetManager manager;
    final SharedPreferences prefs;
    final int accent;
    boolean listening;
    NowPlaying(Activity activity,Ui ui,int accent,int surface,AppWidgetHost widgetHost,AppWidgetManager widgetManager,SharedPreferences preferences){
        host=activity;u=ui;this.accent=accent;widgets=widgetHost;manager=widgetManager;prefs=preferences;
        view=u.box(true);view.setBackground(u.shape(surface,0,BORDER));
        header=u.box(false);u.pad(header,6,0,3,0);header.setBackground(u.titleBar(accent));
        header.addView(u.glyph(R.drawable.ic_heart,WINE),u.lp(16,16));u.gap(header,5);
        header.addView(u.text("Em reprodução",12,WINE,"sans-serif-medium"),new LinearLayout.LayoutParams(0,WRAP,1));
        header.addView(u.iconButton(R.drawable.ic_ellipsis,"Escolher widget",WINE,accent,14,this::options),u.lp(28,30));
        view.addView(header,u.lp(FULL,30));
        body=u.box(true);view.addView(body,u.lp(FULL,WRAP));
        render();
    }
    void start(){if(listening)return;try{widgets.startListening();listening=true;}catch(RuntimeException ignored){}}
    void stop(){if(listening){widgets.stopListening();listening=false;}}
    void render(){
        body.removeAllViews();
        int id=prefs.getInt("media_widget",-1);
        AppWidgetProviderInfo info=id<0?null:manager.getAppWidgetInfo(id);
        if(info!=null){
            try{
                AppWidgetHostView w=widgets.createView(host,id,info);
                w.setPadding(0,0,0,0);
                int width=Math.max(150,Math.min(218,host.getResources().getConfiguration().screenWidthDp-18));
                int h=Math.max(76,Math.min(150,(int)(info.minHeight/host.getResources().getDisplayMetrics().density)));
                body.addView(w,u.lp(FULL,h));
                w.updateAppWidgetSize(null,width,h,width,h);
                return;
            }catch(RuntimeException ignored){}
        }
        LinearLayout row=u.box(false);u.pad(row,6,8,6,8);
        ImageView art=new ImageView(host);art.setImageResource(R.drawable.sakura_music);row.addView(art,u.lp(42,42));u.gap(row,7);
        LinearLayout words=u.box(true);words.addView(u.text("Adicionar mídia",12,INK,"sans-serif-medium"));
        words.addView(u.text("Widget do player",11,WINE,"sans-serif"));row.addView(words,new LinearLayout.LayoutParams(0,WRAP,1));
        body.addView(row);row.setOnClickListener(v->choose());
    }
    void options(){
        new AlertDialog.Builder(host).setTitle("Quadro de mídia").setItems(new String[]{"Escolher widget","Remover widget"},(d,i)->{
            if(i==0)choose();else{int old=prefs.getInt("media_widget",-1);if(old>=0)widgets.deleteAppWidgetId(old);prefs.edit().remove("media_widget").apply();render();}
        }).show();
    }
    void choose(){
        List<AppWidgetProviderInfo> all=manager.getInstalledProviders();
        ArrayList<AppWidgetProviderInfo> list=new ArrayList<>(all);
        if(list.isEmpty()){new AlertDialog.Builder(host).setTitle("Nenhum widget disponível").setMessage("Instale um player que ofereça um widget Android de mídia.").setPositiveButton("OK",null).show();return;}
        Collections.sort(list,(a,b)->a.loadLabel(host.getPackageManager()).compareToIgnoreCase(b.loadLabel(host.getPackageManager())));
        String[] labels=new String[list.size()];
        for(int i=0;i<list.size();i++){
            AppWidgetProviderInfo item=list.get(i);
            String app=item.provider.getPackageName();
            try{app=host.getPackageManager().getApplicationLabel(host.getPackageManager().getApplicationInfo(app,0)).toString();}catch(Exception ignored){}
            labels[i]=app+" — "+item.loadLabel(host.getPackageManager());
        }
        new AlertDialog.Builder(host).setTitle("Escolha o widget do player").setItems(labels,(d,i)->bind(list.get(i))).setNegativeButton("Cancelar",null).show();
    }
    void clearPending(){
        int old=prefs.getInt("widget_pending",-1);
        if(old>=0)widgets.deleteAppWidgetId(old);
        prefs.edit().remove("widget_pending").apply();
    }
    void bind(AppWidgetProviderInfo info){
        clearPending();
        int id=widgets.allocateAppWidgetId();prefs.edit().putInt("widget_pending",id).apply();
        try{
            if(manager.bindAppWidgetIdIfAllowed(id,info.provider)){configure(id);return;}
            Intent intent=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider);
            host.startActivityForResult(intent,BIND);
        }catch(RuntimeException e){clearPending();Toast.makeText(host,"Não foi possível adicionar esse widget.",Toast.LENGTH_SHORT).show();}
    }
    void configure(int id){
        AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        if(info==null){clearPending();return;}
        if(info.configure!=null){
            try{widgets.startAppWidgetConfigureActivityForResult(host,id,0,CONFIGURE,null);return;}
            catch(RuntimeException e){clearPending();Toast.makeText(host,"Configuração do widget indisponível.",Toast.LENGTH_SHORT).show();return;}
        }
        finish(id);
    }
    void result(int request,int result,Intent data){
        int id=prefs.getInt("widget_pending",-1);
        if(id<0)return;
        if(result!=Activity.RESULT_OK){clearPending();return;}
        if(request==BIND)configure(id);else finish(id);
    }
    void finish(int id){
        int old=prefs.getInt("media_widget",-1);
        if(old>=0&&old!=id)widgets.deleteAppWidgetId(old);
        prefs.edit().putInt("media_widget",id).remove("widget_pending").apply();
        render();
    }
}
