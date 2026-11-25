package br.dev.rose.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

final class Catalog {
    static final class Entry {
        final ResolveInfo info;
        final String name, key, search;
        Entry(ResolveInfo info, PackageManager pm) {
            this.info = info;
            name = info.loadLabel(pm).toString();
            key = new ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString();
            search = normalize(name);
        }
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    final LruCache<String, Drawable.ConstantState> icons = new LruCache<>(256);
    ArrayList<Entry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        ArrayList<Entry> entries = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            if (info.activityInfo.packageName.equals(context.getPackageName())) continue;
            Entry entry = new Entry(info, pm);
            if (seen.add(entry.key)) entries.add(entry);
        }
        Collator collator = Collator.getInstance();
        entries.sort((a, b) -> collator.compare(a.name, b.name));
        return entries;
    }

    Drawable icon(Context context, Entry entry) {
        Drawable.ConstantState cached = icons.get(entry.key);
        if (cached != null) return cached.newDrawable(context.getResources());
        Drawable icon = entry.info.loadIcon(context.getPackageManager());
        if (icon.getConstantState() != null) icons.put(entry.key, icon.getConstantState());
        return icon;
    }
}
