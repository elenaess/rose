package br.dev.rose.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import static br.dev.rose.launcher.Ui.*;

final class NowPlaying {
    final Activity host;
    final Ui u;
    final LinearLayout view;
    final ImageView cover;
    final TextView title, artist, status;
    final MediaSessionManager manager;
    final ComponentName listener;
    final Handler main = new Handler(Looper.getMainLooper());
    MediaController controller;
    boolean observing;
    boolean registered;
    final MediaSessionManager.OnActiveSessionsChangedListener changes = this::select;
    final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m){render();}
        @Override public void onPlaybackStateChanged(PlaybackState s){refresh();}
        @Override public void onSessionDestroyed(){refresh();}
    };
    final Runnable accessChanged = this::refresh;
    NowPlaying(Activity activity,Ui ui,int accent,int surface){
        host=activity;u=ui;manager=host.getSystemService(MediaSessionManager.class);
        listener=new ComponentName(host,MediaAccess.class);
        view=u.box(true);view.setBackground(u.shape(surface,0,BORDER));
        LinearLayout header=u.box(false);u.pad(header,6,0,3,0);header.setBackground(u.titleBar(accent));
        header.addView(u.glyph(R.drawable.ic_heart,WINE),u.lp(16,16));u.gap(header,5);
        header.addView(u.text("Em reprodução",12,WINE,"sans-serif-medium"),new LinearLayout.LayoutParams(0,WRAP,1));
        header.addView(u.iconButton(R.drawable.ic_ellipsis,"Permitir acesso à mídia",WINE,accent,14,this::settings),u.lp(28,30));
        view.addView(header,u.lp(FULL,30));
        LinearLayout body=u.box(false);u.pad(body,6,7,6,7);
        cover=new ImageView(host);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(u.shape(accent,0,BORDER));cover.setContentDescription("Capa da mídia");
        body.addView(cover,u.lp(48,48));u.gap(body,6);
        LinearLayout labels=u.box(true);
        title=u.text("Sem reprodução",12,INK,"sans-serif-medium");title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        artist=u.text("",11,WINE,"sans-serif");u.single(artist);
        status=u.text("",10,MUTE,"monospace");u.single(status);
        labels.addView(title);u.gap(labels,3);labels.addView(artist);labels.addView(status);
        body.addView(labels,new LinearLayout.LayoutParams(0,WRAP,1));view.addView(body,u.lp(FULL,WRAP));
        body.setOnClickListener(v->{if(controller==null){if(!registered)settings();}else{try{android.app.PendingIntent p=controller.getSessionActivity();if(p!=null)p.send();}catch(android.app.PendingIntent.CanceledException ignored){}}});
        empty(false);
    }
    void settings(){try{host.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(RuntimeException ignored){}}
    void start(){if(observing)return;observing=true;MediaAccess.changed=accessChanged;refresh();}
    void stop(){observing=false;if(MediaAccess.changed==accessChanged)MediaAccess.changed=null;
        if(registered){manager.removeOnActiveSessionsChangedListener(changes);registered=false;}
        detach();main.removeCallbacksAndMessages(null);
    }
    void detach(){if(controller!=null){controller.unregisterCallback(callback);controller=null;}}
    void refresh(){
        if(!observing)return;
        try{
            if(!registered){manager.addOnActiveSessionsChangedListener(changes,listener,main);registered=true;}
            select(manager.getActiveSessions(listener));
        }catch(SecurityException e){if(registered){manager.removeOnActiveSessionsChangedListener(changes);registered=false;}detach();empty(true);}
    }
    void select(List<MediaController> sessions){
        if(!observing)return;
        MediaController best=null;
        if(sessions!=null)for(MediaController c:sessions){
            if(best==null)best=c;
            PlaybackState s=c.getPlaybackState();
            if(s!=null&&s.getState()==PlaybackState.STATE_PLAYING){best=c;break;}
        }
        if(best==null){detach();empty(false);return;}
        if(controller==null||!controller.getSessionToken().equals(best.getSessionToken())){
            detach();controller=best;controller.registerCallback(callback,main);
        }
        render();
    }
    void empty(boolean permission){
        cover.setImageResource(R.drawable.sakura_music);
        title.setText(permission?"Ativar mídia":"Sem reprodução");
        artist.setText(permission?"Toque para permitir":"Nenhuma faixa");
        status.setText(permission?"ACESSO":"STANDBY");
    }
    void render(){
        if(!observing||controller==null)return;
        MediaMetadata m=controller.getMetadata();
        CharSequence name=m==null?null:m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if((name==null||name.length()==0)&&m!=null)name=m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        CharSequence by=m==null?null:m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if((by==null||by.length()==0)&&m!=null)by=m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if(by==null||by.length()==0){try{by=host.getPackageManager().getApplicationLabel(host.getPackageManager().getApplicationInfo(controller.getPackageName(),0));}catch(Exception e){by="Mídia";}}
        title.setText(name==null||name.length()==0?"Reprodução":name);artist.setText(by);
        Bitmap art=m==null?null:m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if(art==null&&m!=null)art=m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if(art==null&&m!=null)art=m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if(art==null)cover.setImageResource(R.drawable.sakura_music);else cover.setImageBitmap(art);
        PlaybackState state=controller.getPlaybackState();status.setText(state!=null&&state.getState()==PlaybackState.STATE_PLAYING?"PLAYING":"PAUSADO");
    }
}
