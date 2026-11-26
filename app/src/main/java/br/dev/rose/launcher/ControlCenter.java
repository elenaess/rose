package br.dev.rose.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import static br.dev.rose.launcher.Ui.*;

final class ControlCenter {
    final Activity host;
    final Ui u;
    final int accent,surface;
    ControlCenter(Activity a,Ui ui,int ac,int s){host=a;u=ui;accent=ac;surface=s;}
    void open(String action,String fallback){
        try{host.startActivity(new Intent(action));}
        catch(RuntimeException e){try{host.startActivity(new Intent(fallback));}catch(RuntimeException ignored){Toast.makeText(host,"Configuração indisponível neste aparelho.",Toast.LENGTH_SHORT).show();}}
    }
    void heading(LinearLayout body,String name){
        TextView t=u.text(name,12,WINE,"sans-serif-medium");u.pad(t,3,5,3,5);body.addView(t,u.lp(FULL,WRAP));
    }
    void populate(LinearLayout body,boolean quick){
        LinearLayout top=u.box(true);u.pad(top,10,9,10,9);top.setBackground(u.shape(MILK,0,BORDER));
        LinearLayout line=u.box(false);
        TextClock time=new TextClock(host);time.setFormat24Hour("HH:mm");time.setFormat12Hour("h:mm a");time.setTextSize(34);time.setTextColor(INK);time.setIncludeFontPadding(false);line.addView(time,new LinearLayout.LayoutParams(0,WRAP,1));
        TextView battery=u.text(batteryText(),14,WINE,"sans-serif-medium");line.addView(battery);top.addView(line);
        TextClock date=new TextClock(host);date.setFormat24Hour("EEEE, dd/MM");date.setFormat12Hour("EEEE, dd/MM");date.setTextColor(WINE);date.setTextSize(13);top.addView(date);body.addView(top);u.gap(body,8);
        if(!quick){notices(body);return;}
        heading(body,"CONEXÕES");
        LinearLayout network=u.box(true);network.setBackground(u.shape(MILK,0,BORDER));
        network.addView(u.commandRow(R.drawable.ic_sakura_wifi,"Wi-Fi","Abrir painel de internet",()->open(Settings.Panel.ACTION_INTERNET_CONNECTIVITY,Settings.ACTION_WIFI_SETTINGS)));
        network.addView(u.commandRow(R.drawable.ic_sakura_data,"Dados móveis","Configurações de rede",()->open(Settings.ACTION_DATA_ROAMING_SETTINGS,Settings.ACTION_WIRELESS_SETTINGS)));
        network.addView(u.commandRow(R.drawable.ic_sakura_bluetooth,"Bluetooth","Dispositivos e conexões",()->open(Settings.ACTION_BLUETOOTH_SETTINGS,Settings.ACTION_SETTINGS)));
        body.addView(network);u.gap(body,8);
        heading(body,"VOLUME E TELA");
        LinearLayout sliders=u.box(true);u.pad(sliders,10,8,10,9);sliders.setBackground(u.shape(MILK,0,BORDER));
        AudioManager audio=host.getSystemService(AudioManager.class);
        if(audio!=null){
            TextView volume=u.text("Volume de mídia",14,INK,"sans-serif-medium");sliders.addView(volume);
            SeekBar v=new SeekBar(host);v.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));v.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));style(v);
            v.setContentDescription("Volume de mídia");v.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onStartTrackingTouch(SeekBar b){}
                public void onStopTrackingTouch(SeekBar b){}
                public void onProgressChanged(SeekBar b,int n,boolean user){if(user)try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);}catch(RuntimeException e){open(Settings.ACTION_SOUND_SETTINGS,Settings.ACTION_SETTINGS);}}
            });sliders.addView(v,u.lp(FULL,38));
        }
        TextView brightness=u.text("Brilho do Sakura",14,INK,"sans-serif-medium");sliders.addView(brightness);
        SeekBar b=new SeekBar(host);b.setMax(100);float current=host.getWindow().getAttributes().screenBrightness;
        int system=Settings.System.getInt(host.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128);
        b.setProgress(current<0?Math.max(0,Math.min(100,Math.round(system*100f/255f))):Math.round(current*100));
        style(b);b.setContentDescription("Brilho somente dentro do Sakura");
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int n,boolean user){if(user){WindowManager.LayoutParams p=host.getWindow().getAttributes();p.screenBrightness=Math.max(.05f,n/100f);host.getWindow().setAttributes(p);}}
        });sliders.addView(b,u.lp(FULL,38));
        LinearLayout buttons=u.box(false);
        TextView reset=u.pill("Automático",surface,WINE,()->{WindowManager.LayoutParams p=host.getWindow().getAttributes();p.screenBrightness=-1;host.getWindow().setAttributes(p);});
        buttons.addView(reset,new LinearLayout.LayoutParams(0,u.dp(40),1));u.gap(buttons,5);
        buttons.addView(u.pill("Brilho do sistema",surface,WINE,()->open(Settings.ACTION_DISPLAY_SETTINGS,Settings.ACTION_SETTINGS)),new LinearLayout.LayoutParams(0,u.dp(40),1));
        sliders.addView(buttons);body.addView(sliders);u.gap(body,8);
        heading(body,"ATALHOS DO SISTEMA");
        tiles(body,new int[]{R.drawable.ic_sakura_plane,R.drawable.ic_sakura_bell},new String[]{"Modo avião","Não perturbe"},new String[]{Settings.ACTION_AIRPLANE_MODE_SETTINGS,"android.settings.ZEN_MODE_SETTINGS"});
        tiles(body,new int[]{R.drawable.ic_battery,R.drawable.ic_settings_2},new String[]{"Bateria","Ajustes Android"},new String[]{Settings.ACTION_BATTERY_SAVER_SETTINGS,Settings.ACTION_SETTINGS});
        tiles(body,new int[]{R.drawable.ic_sakura_data,R.drawable.ic_sakura_wifi},new String[]{"Uso de dados","Roteador"},new String[]{Settings.ACTION_DATA_USAGE_SETTINGS,"android.settings.TETHER_SETTINGS"});
    }
    void style(SeekBar b){b.setProgressTintList(ColorStateList.valueOf(WINE));b.setThumbTintList(ColorStateList.valueOf(WINE));b.setProgressBackgroundTintList(ColorStateList.valueOf(accent));}
    void tiles(LinearLayout body,int[] icons,String[] labels,String[] actions){
        LinearLayout row=u.box(false);
        for(int i=0;i<labels.length;i++){final String action=actions[i];LinearLayout tile=u.box(true);tile.setGravity(Gravity.CENTER);u.pad(tile,3,5,3,5);u.action(tile,accent,0,()->open(action,Settings.ACTION_SETTINGS));tile.addView(u.glyph(icons[i],WINE),u.lp(24,24));u.gap(tile,5);TextView text=u.text(labels[i],12,INK,"sans-serif-medium");text.setGravity(Gravity.CENTER);tile.addView(text,u.lp(FULL,WRAP));row.addView(tile,new LinearLayout.LayoutParams(0,u.dp(68),1));if(i+1<labels.length)u.gap(row,6);}
        body.addView(row);u.gap(body,6);
    }
    void notices(LinearLayout body){
        heading(body,"CENTRAL DO ANDROID");
        LinearLayout card=u.box(true);u.pad(card,12,13,12,13);card.setBackground(u.shape(MILK,0,BORDER));
        card.addView(u.glyph(R.drawable.ic_sakura_bell,WINE),u.lp(26,26));u.gap(card,8);
        card.addView(u.text("Notificações do sistema",17,INK,"sans-serif-medium"));u.gap(card,7);
        card.addView(u.text("Para ler mensagens e alertas reais, deslize a barra de status original do Android. O Sakura não lê o conteúdo das suas notificações.",14,MUTE,"sans-serif"));
        body.addView(card);u.gap(body,8);
        body.addView(u.commandRow(R.drawable.ic_sakura_bell,"Configurações de notificações","Gerenciar alertas dos aplicativos",()->open(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS,Settings.ACTION_SETTINGS)));
        body.addView(u.commandRow(R.drawable.ic_clock_3,"Histórico de notificações","Abrir histórico, quando disponível",()->open("android.settings.NOTIFICATION_HISTORY",Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS)));
        body.addView(u.commandRow(R.drawable.ic_sakura_bell,"Não perturbe","Configurar interrupções",()->open("android.settings.ZEN_MODE_SETTINGS",Settings.ACTION_SETTINGS)));
    }
    String batteryText(){
        try{Intent i=host.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i!=null){int n=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),max=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);if(n>=0&&max>0)return Math.round(n*100f/max)+"%";}}catch(RuntimeException ignored){}
        return "Bateria";
    }
}
