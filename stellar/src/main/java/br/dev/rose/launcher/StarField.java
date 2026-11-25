package br.dev.rose.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Random;

// Static decorative sky; generated once, no animation loop, no network.
final class StarField extends View {
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    final float[] x=new float[108],y=new float[108],size=new float[108];
    final float[][] constellation={{.20f,.46f},{.34f,.38f},{.48f,.49f},{.62f,.42f},{.73f,.55f},{.84f,.49f}};
    Shader sky,glow;
    StarField(Context context){
        super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Random random=new Random(418);
        for(int i=0;i<x.length;i++){x[i]=random.nextFloat();y[i]=random.nextFloat();size[i]=.5f+random.nextFloat()*1.1f;}
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        if(w<=0||h<=0)return;
        sky=new LinearGradient(0,0,w*.8f,h,new int[]{0xff061224,0xff102c46,0xff071524},null,Shader.TileMode.CLAMP);
        glow=new RadialGradient(w*.83f,h*.37f,Math.max(w,h)*.55f,new int[]{0x6042b9d6,0x00172e4d},null,Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas c){
        int w=getWidth(),h=getHeight();
        p.setShader(sky);p.setColor(Color.WHITE);c.drawRect(0,0,w,h,p);
        p.setShader(glow);c.drawRect(0,0,w,h,p);p.setShader(null);
        float density=getResources().getDisplayMetrics().density;
        for(int i=0;i<x.length;i++){p.setColor(i%7==0?0xb6bde8ff:0x5698c6e5);c.drawCircle(x[i]*w,y[i]*h,size[i]*density,p);}
        p.setColor(0x4476c7ed);p.setStrokeWidth(density*.7f);
        for(int i=1;i<constellation.length;i++){float[] a=constellation[i-1],b=constellation[i];c.drawLine(a[0]*w,a[1]*h,b[0]*w,b[1]*h,p);}
        for(float[] a:constellation){float sx=a[0]*w,sy=a[1]*h;p.setColor(0x3069cfff);c.drawCircle(sx,sy,density*5,p);p.setColor(0xffd7efff);c.drawCircle(sx,sy,density*1.6f,p);}
    }
}
