package br.dev.rose.launcher;

import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.util.*;

final class IconPack {
    static final String[] PINK={};
    static final String[] ACTIONS={"org.adw.launcher.THEMES","com.novalauncher.THEME","com.anddoes.launcher.THEME","com.teslacoilsw.launcher.THEME"};
    static final class Option {
        final String pkg,label;
        Option(String p,String l){pkg=p;label=l;}
    }
    final String pkg,label;
    final Resources resources;
    final HashMap<String,String> mappings=new HashMap<>();
    final LruCache<String,Drawable.ConstantState> cache=new LruCache<>(256);
    final HashSet<String> missing=new HashSet<>();
    private IconPack(String p,String l,Resources r){pkg=p;label=l;resources=r;}
    static IconPack empty(){return new IconPack("","",null);}
    static ArrayList<Option> installed(Context c){
        PackageManager pm=c.getPackageManager();
        Set<String> candidates=new LinkedHashSet<>(Arrays.asList(PINK));
        for(String action:ACTIONS)for(ResolveInfo i:pm.queryIntentActivities(new Intent(action),0))if(i.activityInfo!=null)candidates.add(i.activityInfo.packageName);
        ArrayList<Option> out=new ArrayList<>();
        for(String p:candidates)try{
            Resources r=pm.getResourcesForApplication(p);
            if(r.getIdentifier("appfilter","xml",p)==0){
                try(InputStream in=r.getAssets().open("appfilter.xml")){}catch(Exception e){continue;}
            }
            out.add(new Option(p,pm.getApplicationLabel(pm.getApplicationInfo(p,0)).toString()));
        }catch(Exception ignored){}
        out.sort((x,y)->x.label.compareToIgnoreCase(y.label));
        return out;
    }
    static IconPack load(Context c,String selected){
        if(selected==null||selected.isEmpty())return empty();
        if(selected.equals("auto")){
            for(String p:PINK){IconPack pack=load(c,p);if(!pack.mappings.isEmpty())return pack;}
            return empty();
        }
        try{
            PackageManager pm=c.getPackageManager();
            Resources r=pm.getResourcesForApplication(selected);
            IconPack pack=new IconPack(selected,pm.getApplicationLabel(pm.getApplicationInfo(selected,0)).toString(),r);
            int id=r.getIdentifier("appfilter","xml",selected);
            if(id!=0){try(XmlResourceParser parser=r.getXml(id)){pack.parse(parser);}}
            else{try(InputStream in=r.getAssets().open("appfilter.xml")){XmlPullParser parser=Xml.newPullParser();parser.setInput(in,"UTF-8");pack.parse(parser);}}
            return pack.mappings.isEmpty()?empty():pack;
        }catch(Exception ignored){return empty();}
    }
    private void parse(XmlPullParser p)throws Exception{
        for(int type=p.getEventType();type!=XmlPullParser.END_DOCUMENT;type=p.next()){
            if(type!=XmlPullParser.START_TAG||!"item".equals(p.getName()))continue;
            String component=p.getAttributeValue(null,"component"),drawable=p.getAttributeValue(null,"drawable");
            if(component==null||drawable==null||drawable.isEmpty())continue;
            if(component.startsWith("ComponentInfo{")&&component.endsWith("}"))component=component.substring(14,component.length()-1);
            ComponentName cn=ComponentName.unflattenFromString(component);
            if(cn==null)continue;
            mappings.put(cn.flattenToString(),drawable);
        }
    }
    synchronized Drawable icon(String key){
        if(resources==null||missing.contains(key))return null;
        Drawable.ConstantState state=cache.get(key);
        if(state!=null)return state.newDrawable(resources);
        String name=mappings.get(key);
        if(name==null){ComponentName cn=ComponentName.unflattenFromString(key);if(cn!=null)name=mappings.get(cn.flattenToString());}
        if(name!=null)try{
            int id=resources.getIdentifier(name,"drawable",pkg);
            if(id==0)id=resources.getIdentifier(name,"mipmap",pkg);
            if(id!=0){Drawable d=resources.getDrawable(id,null);if(d.getConstantState()!=null)cache.put(key,d.getConstantState());return d;}
        }catch(RuntimeException ignored){}
        missing.add(key);return null;
    }
}
