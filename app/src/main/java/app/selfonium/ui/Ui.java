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
    static int BG=0xFF101216,SURFACE=0xFF1A1D24,RAISED=0xFF252932,ACCENT=0xFFCDBBFA,TEXT=0xFFF1F0F5,MUTED=0xFFA6A9B5,ON_ACCENT=0xFF30204E,TONAL=0xFF393247;
    static final int DEFAULT_ACCENT=0xFFCDBBFA;
    static void load(Context context){var prefs=context.getSharedPreferences("appearance",0);ACCENT=prefs.getInt("accent",DEFAULT_ACCENT)|0xFF000000;BG=prefs.getBoolean("oled",false)?0xFF000000:0xFF101216;ON_ACCENT=luminance(ACCENT)>.179?0xFF111318:0xFFFFFFFF;TONAL=Color.rgb((Color.red(ACCENT)+26*3)/4,(Color.green(ACCENT)+29*3)/4,(Color.blue(ACCENT)+36*3)/4);}
    static double luminance(int color){double sum=0;double[] weights={.2126,.7152,.0722};int[] rgb={Color.red(color),Color.green(color),Color.blue(color)};for(int i=0;i<3;i++){double v=rgb[i]/255.0;sum+=weights[i]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return sum;}
    static void tint(android.widget.CompoundButton button){ColorStateList colors=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED});if(button instanceof android.widget.Switch toggle){toggle.setThumbTintList(colors);toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{(ACCENT&0xFFFFFF)|0x66000000,RAISED}));}else button.setButtonTintList(colors);}
    static String count(int number,String one,String few,String many){int n=Math.abs(number)%100;return number+" "+(n>=11&&n<=14?many:n%10==1?one:n%10>=2&&n%10<=4?few:many);}
    static String tracks(int number){return count(number,"композиція","композиції","композицій");}
    static String name(CharSequence value){return value==null?"":value.toString().strip().replaceAll("\\s+"," ");}
    static String album(String value){return value==null||value.isBlank()||value.equals("[Unknown Album]")?"Невідомий альбом":value;}
    static GradientDrawable atmosphere(int color){return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{android.graphics.Color.rgb((Color.red(color)+16)/3,(Color.green(color)+18)/3,(Color.blue(color)+22)/3),BG,BG});}
    static void divider(LinearLayout parent){View line=new View(parent.getContext());line.setBackgroundColor(RAISED);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(parent.getContext(),1));size.setMargins(0,dp(parent.getContext(),8),0,dp(parent.getContext(),8));parent.addView(line,size);}
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
    static RippleDrawable ripple(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf((ACCENT&0xFFFFFF)|0x33000000),shape(c,color,radius),null);}
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
