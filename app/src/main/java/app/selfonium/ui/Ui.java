package app.selfonium.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG=0xFF141218,SURFACE=0xFF211F26,RAISED=0xFF2B2930,ACCENT=0xFFD0BCFE,TEXT=0xFFE6E0E9,MUTED=0xFFCAC4D0,ON_ACCENT=0xFF381E72;
    static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static GradientDrawable shape(Context c,int color,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(c,radius));return drawable;}
    static TextView text(Context c,String value,int size,int color){TextView text=new TextView(c);text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setFontFeatureSettings("kern");text.setIncludeFontPadding(false);return text;}
    static TextView title(Context c,String value,int size){TextView text=text(c,value,size,TEXT);text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return text;}
    static LinearLayout column(Context c){LinearLayout layout=new LinearLayout(c);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    static LinearLayout row(Context c){LinearLayout layout=new LinearLayout(c);layout.setGravity(Gravity.CENTER_VERTICAL);return layout;}
    static void pad(View view,int horizontal,int vertical){view.setPadding(dp(view.getContext(),horizontal),dp(view.getContext(),vertical),dp(view.getContext(),horizontal),dp(view.getContext(),vertical));}
    static TextView button(Context c,String label,boolean accent,Runnable action){
        TextView view=text(c,label,14,accent?ON_ACCENT:TEXT);view.setGravity(Gravity.CENTER);pad(view,16,10);view.setMinHeight(dp(c,48));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),shape(c,accent?ACCENT:SURFACE,12),null));
        view.setOnClickListener(v->action.run());view.setContentDescription(label);return view;
    }
    static RippleDrawable ripple(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x22D0BCFE),shape(c,color,radius),null);}
    static android.app.Dialog screen(android.app.Activity activity){
        android.app.Dialog dialog=new android.app.Dialog(activity,android.R.style.Theme_Material_NoActionBar);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        dialog.getWindow().getDecorView().setPadding(0,0,0,0);edgeToEdge(dialog.getWindow());return dialog;
    }
    static void edgeToEdge(android.view.Window window){
        window.setStatusBarColor(Color.TRANSPARENT);window.setNavigationBarColor(Color.TRANSPARENT);
        if(android.os.Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false);
        else window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    static void insets(View view){view.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});}
    static void space(LinearLayout layout,int height){View v=new View(layout.getContext());layout.addView(v,new LinearLayout.LayoutParams(1,dp(layout.getContext(),height)));}
}
