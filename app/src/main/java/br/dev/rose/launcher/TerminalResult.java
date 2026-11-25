package br.dev.rose.launcher;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.UUID;

// All entry points run on the app's main thread. Command history stays in process memory.
public final class TerminalResult extends BroadcastReceiver {
    static final String PKG = "com.termux";
    static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    static String token;
    static long started;
    static String transcript = "";
    static Runnable onChange;

    static void send(Activity from, String command) {
        if (token != null || command.trim().isEmpty()) return;
        token = UUID.randomUUID().toString();
        started = SystemClock.elapsedRealtime();
        append("$ " + command);
        Intent reply = new Intent(from, TerminalResult.class).setData(Uri.parse("rose:command/" + token));
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent result = PendingIntent.getBroadcast(from, 0, reply, flags);
        Intent run = new Intent("com.termux.RUN_COMMAND").setClassName(PKG, "com.termux.app.RunCommandService");
        run.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        run.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[] { "-c", command });
        run.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        run.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", result);
        try {
            if (from.startService(run) == null) throw new IllegalStateException("Serviço do Termux indisponível.");
        } catch (RuntimeException error) {
            result.cancel();
            token = null;
            append("Falha ao chamar o Termux. Confira a conexão em Configurar. " + error.getClass().getSimpleName());
        }
        changed();
    }

    static void stopWaiting() {
        token = null;
        append("Espera encerrada. O comando pode continuar no Termux.");
        changed();
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (token == null || !("rose:command/" + token).equals(intent.getDataString())) return;
        token = null;
        Bundle result = intent.getBundleExtra("result");
        if (result == null) {
            append("O Termux retornou uma resposta vazia.");
        } else {
            String out = result.getString("stdout", "").trim();
            String err = result.getString("stderr", "").trim();
            String message = result.getString("errmsg", "").trim();
            if (!out.isEmpty()) append(out);
            if (!err.isEmpty()) append(err);
            if (!message.isEmpty()) append(message);
            if (result.getInt("err", Activity.RESULT_OK) != Activity.RESULT_OK) append("Falha na execução pelo Termux.");
            else append("Saída " + result.getInt("exitCode", 0));
        }
        changed();
    }
    private static void append(String line) {
        String joined = transcript.isEmpty() ? line : transcript + "\n" + line;
        transcript = joined.length() > 24000 ? "…\n" + joined.substring(joined.length() - 23998) : joined;
    }
    static void changed() { if (onChange != null) onChange.run(); }
}
