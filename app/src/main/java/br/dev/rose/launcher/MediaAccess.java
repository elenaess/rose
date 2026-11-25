package br.dev.rose.launcher;
import android.service.notification.NotificationListenerService;
import android.os.Handler;
import android.os.Looper;

// No notifications are stored or transmitted. Only media session metadata is used.
public final class MediaAccess extends NotificationListenerService {
    static Runnable changed;
    private void signal(){new Handler(Looper.getMainLooper()).post(()->{Runnable r=changed;if(r!=null)r.run();});}
    @Override public void onListenerConnected(){signal();}
    @Override public void onListenerDisconnected(){signal();}
}
