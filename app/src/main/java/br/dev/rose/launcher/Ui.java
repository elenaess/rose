package br.dev.rose.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int INK = 0xff30202c, WINE = 0xff672c47, ROSE = 0xffffb5d1;
    static final int MILK = 0xffffeef3, MUTE = 0xff684957, BORDER = 0xffddb0be;
    static final int FULL = -1, WRAP = -2;
    final Context c;
    Ui(Context context) { c = context; }
    int dp(float x) { return Math.round(x * c.getResources().getDisplayMetrics().density); }
    int dim(int x) { return x < 0 ? x : dp(x); }
    GradientDrawable shape(int color, int radius, int line) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(0);
        if (line != 0) d.setStroke(dp(1), line);
        return d;
    }
    GradientDrawable titleBar(int color) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xffffeef5,color});
        d.setStroke(dp(1),WINE);return d;
    }
    void action(View view, int color, int radius, Runnable run) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x32753851), shape(color,0,color==0?0:BORDER), shape(Color.WHITE,0,0)));
        view.setOnClickListener(v -> run.run());
        view.setFocusable(true);
    }
    void flatAction(View view,int color,Runnable run){
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30753851),shape(color,0,0),shape(Color.WHITE,0,0)));
        view.setOnClickListener(v->run.run());view.setFocusable(true);
        view.setOnTouchListener((v,event)->{
            if(android.animation.ValueAnimator.areAnimatorsEnabled()){
                int a=event.getActionMasked();
                if(a==android.view.MotionEvent.ACTION_DOWN)v.animate().scaleX(.96f).scaleY(.96f).setDuration(90).start();
                else if(a==android.view.MotionEvent.ACTION_UP||a==android.view.MotionEvent.ACTION_CANCEL)v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
            }
            return false;
        });
    }
    FrameLayout flatIcon(int icon,String label,int tint,int size,Runnable click){
        FrameLayout b=new FrameLayout(c);b.setContentDescription(label);b.setTooltipText(label);
        b.addView(glyph(icon,tint),frame(size,size,Gravity.CENTER));flatAction(b,Color.TRANSPARENT,click);return b;
    }
    TextView text(String value, float size, int color, String family) {
        TextView v = new TextView(c);
        v.setText(value); v.setTextColor(color); v.setTextSize(size);
        v.setTypeface(Typeface.create(family,Typeface.NORMAL)); v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        v.setIncludeFontPadding(false);
        return v;
    }
    void single(TextView v) { v.setSingleLine(true); v.setEllipsize(TextUtils.TruncateAt.END); }
    LinearLayout box(boolean vertical) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        if (!vertical) row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    ImageView glyph(int id, int tint) {
        ImageView v = new ImageView(c); v.setImageResource(id); v.setColorFilter(tint);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }
    FrameLayout iconButton(int icon, String label, int tint, int bg, int size, Runnable click) {
        FrameLayout b = new FrameLayout(c); b.setContentDescription(label); b.setTooltipText(label);
        b.addView(glyph(icon,tint), frame(size,size,Gravity.CENTER));
        action(b,bg,14,click); return b;
    }
    void pad(View v,int l,int t,int r,int b) {v.setPadding(dp(l),dp(t),dp(r),dp(b));}
    FrameLayout.LayoutParams frame(int w,int h,int gravity) {
        return new FrameLayout.LayoutParams(dim(w),dim(h),gravity);
    }
    LinearLayout.LayoutParams lp(int w,int h) {return new LinearLayout.LayoutParams(dim(w),dim(h));}
    LinearLayout.LayoutParams flex(boolean vertical) {return new LinearLayout.LayoutParams(vertical?FULL:0,vertical?0:FULL,1);}
    void gap(LinearLayout parent,int size) { parent.addView(new Space(c),lp(size,size)); }
    void grow(LinearLayout row) {row.addView(new Space(c), new LinearLayout.LayoutParams(0,1,1));}
    void place(FrameLayout f,View v,int w,int h,int g,int l,int t,int r,int b) {
        FrameLayout.LayoutParams p=frame(w,h,g);p.setMargins(dp(l),dp(t),dp(r),dp(b));f.addView(v,p);
    }
    LinearLayout commandRow(int icon,String name,String hint,Runnable run) {
        LinearLayout row=box(false); pad(row,12,8,8,8); action(row,0x00ffffff,12,run);
        row.addView(glyph(icon,WINE),lp(21,21));gap(row,13);
        LinearLayout col=box(true);
        col.addView(text(name,16,INK,"sans-serif-medium"));
        if(!hint.isEmpty()) {gap(col,4);col.addView(text(hint,13,MUTE,"sans-serif"));}
        row.addView(col,new LinearLayout.LayoutParams(0,WRAP,1));
        row.addView(glyph(R.drawable.ic_chevron_right,MUTE),lp(16,16));return row;
    }
    TextView pill(String name,int bg,int fg,Runnable run) {
        TextView t=text(name,14,fg,"sans-serif-medium");
        t.setGravity(Gravity.CENTER); pad(t,12,0,12,0); action(t,bg,14,run); return t;
    }
}
