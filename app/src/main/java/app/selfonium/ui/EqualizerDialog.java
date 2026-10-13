package app.selfonium.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import app.selfonium.core.EqualizerCurve;
import org.json.JSONArray;
import java.util.Locale;

/** Controls the real effect bands and refreshes when the playback audio session changes. */
final class EqualizerDialog {
    static Dialog show(Activity activity){EqualizerDialog screen=new EqualizerDialog(activity);screen.open();return screen.dialog;}
    private final Activity activity;private final SharedPreferences settings,caps;private final Dialog dialog;
    private LinearLayout bands;private CurveView curve;private TextView description,presetName;private final java.util.List<TextView> presetButtons=new java.util.ArrayList<>();
    private int[] frequencies=new int[0],gains=new int[0];private int min,max;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private EqualizerDialog(Activity activity){this.activity=activity;settings=activity.getSharedPreferences("player",0);caps=activity.getSharedPreferences("equalizer_caps",0);dialog=Ui.screen(activity);}
    private int dp(float n){return Ui.dp(activity,n);}
    private void open(){
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);LinearLayout page=Ui.column(activity);Ui.pad(page,16,0);scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=Ui.row(activity);header.addView(new IconButton(activity,"back","Закрити еквалайзер",dialog::dismiss));header.addView(Ui.title(activity,"Еквалайзер",20),new LinearLayout.LayoutParams(0,-2,1));header.addView(new IconButton(activity,"refresh","Скинути еквалайзер",()->applyPreset(0)));page.addView(header);Ui.space(page,12);
        Switch enabled=new Switch(activity);enabled.setText("Еквалайзер увімкнено");enabled.setTextColor(Ui.TEXT);enabled.setMinHeight(dp(48));enabled.setChecked(settings.getBoolean("eqEnabled",false));enabled.setOnCheckedChangeListener((button,on)->{settings.edit().putBoolean("eqEnabled",on).apply();enabled.setText(on?"Еквалайзер увімкнено":"Еквалайзер вимкнено");});enabled.setText(enabled.isChecked()?"Еквалайзер увімкнено":"Еквалайзер вимкнено");page.addView(enabled);
        description=Ui.text(activity,"",13,Ui.MUTED);page.addView(description);Ui.space(page,16);
        curve=new CurveView(activity);curve.setBackground(Ui.shape(activity,Ui.SURFACE,16));curve.setContentDescription("Крива підсилення частот");page.addView(curve,new LinearLayout.LayoutParams(-1,dp(154)));Ui.space(page,16);presetName=Ui.text(activity,"Власний пресет",12,Ui.MUTED);page.addView(presetName);Ui.space(page,8);
        HorizontalScrollView presets=new HorizontalScrollView(activity);presets.setHorizontalScrollBarEnabled(false);LinearLayout buttons=Ui.row(activity);String[] names={"Рівний","Бас","Вокал","Високі"};
        for(int i=0;i<names.length;i++){final int preset=i;TextView button=Ui.button(activity,names[i],false,()->applyPreset(preset));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,-2);size.setMargins(0,0,dp(8),0);buttons.addView(button,size);presetButtons.add(button);}presets.addView(buttons);page.addView(presets);Ui.space(page,20);
        HorizontalScrollView sliders=new HorizontalScrollView(activity);sliders.setFillViewport(true);bands=Ui.row(activity);bands.setGravity(Gravity.CENTER);sliders.addView(bands);page.addView(sliders);Ui.space(page,20);
        page.addView(Ui.text(activity,"Кількість смуг і діапазон визначає аудіоефект пристрою. Налаштування зберігаються для кожної частоти.",12,Ui.MUTED));
        listener=(prefs,key)->refresh();caps.registerOnSharedPreferenceChangeListener(listener);dialog.setOnDismissListener(d->caps.unregisterOnSharedPreferenceChangeListener(listener));dialog.setContentView(scroll);dialog.show();dialog.getWindow().setLayout(-1,-1);refresh();
    }
    private void refresh(){
        bands.removeAllViews();
        try{JSONArray array=new JSONArray(caps.getString("frequencies","[]"));frequencies=new int[caps.getBoolean("available",false)?array.length():0];for(int i=0;i<frequencies.length;i++)frequencies[i]=array.getInt(i);}catch(Exception ignored){frequencies=new int[0];}
        min=caps.getInt("min",-1200);max=caps.getInt("max",1200);gains=new int[frequencies.length];
        if(frequencies.length==0){updatePreset();description.setText("Почніть відтворення, щоб визначити доступні смуги. Якщо вони не з’являться, аудіовихід не надає еквалайзер.");curve.invalidate();return;}
        description.setText(Ui.count(frequencies.length,"смуга","смуги","смуг")+" · "+db(min)+" … "+db(max)+" дБ");
        for(int i=0;i<frequencies.length;i++){
            final int band=i,hz=frequencies[i];int legacy=Math.min(4,i*5/Math.max(1,frequencies.length));gains[i]=EqualizerCurve.clamp(settings.getInt("eqGain_"+hz,settings.getInt("eq"+legacy,0)),min,max);
            LinearLayout column=Ui.column(activity);column.setGravity(Gravity.CENTER);TextView value=Ui.text(activity,db(gains[i]),12,Ui.ACCENT);column.addView(value);
            VerticalSlider slider=new VerticalSlider(activity);slider.setMax(max-min);slider.setProgress(gains[i]-min);slider.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));slider.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));slider.setContentDescription(hz+" Гц, підсилення в децибелах");
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){gains[band]=progress+min;value.setText(db(gains[band]));curve.invalidate();if(user){settings.edit().putInt("eqGain_"+hz,gains[band]).apply();updatePreset();}}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
            column.addView(slider,new LinearLayout.LayoutParams(dp(52),dp(220)));column.addView(Ui.text(activity,hz<1000?Integer.toString(hz):String.format(Locale.ROOT,"%sк",hz%1000==0?Integer.toString(hz/1000):String.format(Locale.ROOT,"%.1f",hz/1000f)),12,Ui.MUTED));
            bands.addView(column,new LinearLayout.LayoutParams(dp(58),-2));
        }curve.invalidate();updatePreset();
    }
    private void updatePreset(){
        int selected=-1;for(int preset=0;preset<4&&frequencies.length>0;preset++){boolean matches=true;for(int i=0;i<frequencies.length;i++)if(gains[i]!=EqualizerCurve.preset(preset,frequencies[i],min,max)){matches=false;break;}if(matches){selected=preset;break;}}
        if(presetName!=null)presetName.setText(selected<0?"Власний пресет":"Пресет · "+presetButtons.get(selected).getText());
        for(int i=0;i<presetButtons.size();i++){TextView button=presetButtons.get(i);boolean active=i==selected;button.setSelected(active);button.setTextColor(active?Ui.ON_ACCENT:Ui.TEXT);button.setBackground(Ui.ripple(activity,active?Ui.ACCENT:Ui.SURFACE,12));button.setEnabled(frequencies.length>0);}
    }
    private static String db(int value){return String.format(Locale.ROOT,"%+.1f",value/100f);}
    private void applyPreset(int preset){SharedPreferences.Editor edit=settings.edit();for(int hz:frequencies)edit.putInt("eqGain_"+hz,EqualizerCurve.preset(preset,hz,min,max));edit.apply();refresh();}
    private final class CurveView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path line=new Path();
        CurveView(Context context){super(context);}
        @Override protected void onDraw(Canvas canvas){
            float left=dp(38),right=getWidth()-dp(20),top=dp(22),bottom=getHeight()-dp(30);paint.setStrokeWidth(dp(1));paint.setColor(0x224D455D);
            for(int i=0;i<5;i++){float y=top+(bottom-top)*i/4;canvas.drawLine(left,y,right,y,paint);}
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(9));paint.setColor(Ui.MUTED);paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(db(max),left-dp(6),top+dp(3),paint);canvas.drawText("0",left-dp(6),(top+bottom)/2+dp(3),paint);canvas.drawText(db(min),left-dp(6),bottom+dp(3),paint);
            paint.setTextAlign(Paint.Align.LEFT);canvas.drawText("20 Гц",left,getHeight()-dp(10),paint);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("1 кГц",left+(right-left)*(float)(Math.log(50)/Math.log(1000)),getHeight()-dp(10),paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText("20 кГц",right,getHeight()-dp(10),paint);paint.setTextAlign(Paint.Align.LEFT);
            if(gains.length==0||max==min)return;
            float zero=bottom-(bottom-top)*(0-min)/(max-min);paint.setColor(Ui.MUTED);canvas.drawLine(left,zero,right,zero,paint);line.reset();
            for(int i=0;i<gains.length;i++){float x=x(i,left,right),y=y(i,top,bottom);if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(Ui.ACCENT);canvas.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<gains.length;i++)canvas.drawCircle(x(i,left,right),y(i,top,bottom),dp(3),paint);
        }
        float x(int i,float left,float right){return left+(right-left)*(float)(Math.log(Math.max(20,Math.min(20000,frequencies[i]))/20.0)/Math.log(1000));}
        float y(int i,float top,float bottom){return bottom-(bottom-top)*(gains[i]-min)/(max-min);}
    }
    private static final class VerticalSlider extends SeekBar {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        VerticalSlider(Context context){super(context);setPadding(0,Ui.dp(context,16),0,Ui.dp(context,16));setSplitTrack(false);setBackground(null);setThumb(null);setProgressDrawable(null);}
        @Override protected synchronized void onMeasure(int width,int height){setMeasuredDimension(resolveSize(Ui.dp(getContext(),52),width),resolveSize(Ui.dp(getContext(),220),height));}
        @Override protected synchronized void onDraw(Canvas canvas){
            float x=getWidth()/2f,top=getPaddingTop(),bottom=getHeight()-getPaddingBottom();
            float y=bottom-(bottom-top)*getProgress()/Math.max(1,getMax());
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(Ui.dp(getContext(),4));paint.setColor(Ui.RAISED);canvas.drawLine(x,top,x,bottom,paint);
            paint.setColor(Ui.ACCENT);canvas.drawLine(x,y,x,bottom,paint);canvas.drawCircle(x,y,Ui.dp(getContext(),8),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(!isEnabled())return false;
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN->{setPressed(true);getParent().requestDisallowInterceptTouchEvent(true);if(observer!=null)observer.onStartTrackingTouch(this);}
                case MotionEvent.ACTION_UP->{performClick();setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);if(observer!=null)observer.onStopTrackingTouch(this);}
                case MotionEvent.ACTION_CANCEL->{setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);if(observer!=null)observer.onStopTrackingTouch(this);return true;}
            }
            int available=getHeight()-getPaddingTop()-getPaddingBottom();float fraction=(getHeight()-getPaddingBottom()-event.getY())/Math.max(1,available);
            int target=Math.max(0,Math.min(getMax(),Math.round(fraction*getMax())));setProgress(target);notifyGain(target);invalidate();return true;
        }
        @Override public boolean onKeyDown(int code,KeyEvent event){
            if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){int target=Math.max(0,Math.min(getMax(),getProgress()+(code==KeyEvent.KEYCODE_DPAD_UP?25:-25)));setProgress(target);notifyGain(target);return true;}
            return super.onKeyDown(code,event);
        }
        private OnSeekBarChangeListener observer;
        @Override public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener){observer=listener;super.setOnSeekBarChangeListener(listener);}
        private void notifyGain(int value){if(observer!=null)observer.onProgressChanged(this,value,true);}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
