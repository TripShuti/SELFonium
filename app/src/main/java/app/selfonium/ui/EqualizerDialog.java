package app.selfonium.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import app.selfonium.core.GraphicEqualizer;
import app.selfonium.core.EqualizerPresets;
import app.selfonium.playback.EqualizerSettings;
import org.json.*;
import java.util.*;

/** A complete ten-band panel backed by the player's PCM processor. */
final class EqualizerDialog {
    static Dialog show(Activity activity){EqualizerDialog screen=new EqualizerDialog(activity);screen.open();return screen.dialog;}
    private final Activity activity;private final SharedPreferences settings,caps;private final Dialog dialog;
    private final int[] gains=new int[10];private final TextView[] values=new TextView[10];private final VerticalSlider[] sliders=new VerticalSlider[10];
    private TextView presetName,preampValue,headroomNote;private SeekBar preamp;private CurveView curve;private int preampGain;private boolean refreshing;private final SharedPreferences.OnSharedPreferenceChangeListener formatListener=(prefs,key)->{if("sampleRate".equals(key)&&curve!=null)status();};
    private EqualizerDialog(Activity activity){this.activity=activity;settings=activity.getSharedPreferences("player",0);caps=activity.getSharedPreferences("equalizer_caps",0);EqualizerSettings.migrate(settings);dialog=Ui.screen(activity);}
    private int dp(float n){return Ui.dp(activity,n);}
    private void open(){
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);LinearLayout page=Ui.column(activity);Ui.pad(page,16,0);scroll.addView(page);Ui.insets(scroll);
        LinearLayout header=Ui.row(activity);header.addView(new IconButton(activity,"back","Закрити еквалайзер",dialog::dismiss));header.addView(Ui.title(activity,"Еквалайзер",20),new LinearLayout.LayoutParams(0,-2,1));header.addView(new IconButton(activity,"refresh","Скинути еквалайзер",()->apply(new int[10],0,"Рівний")));page.addView(header);
        Switch enabled=new Switch(activity);enabled.setText("Еквалайзер");enabled.setTextColor(Ui.TEXT);enabled.setTextSize(14);enabled.setMinHeight(dp(48));Ui.tint(enabled);enabled.setChecked(settings.getBoolean("eqEnabled",false));enabled.setOnCheckedChangeListener((button,on)->settings.edit().putBoolean("eqEnabled",on).apply());page.addView(enabled);
        LinearLayout presets=Ui.row(activity);presetName=Ui.button(activity,"Пресети",false,this::choosePreset);presets.addView(presetName,new LinearLayout.LayoutParams(0,dp(48),1));presets.addView(new IconButton(activity,"plus","Зберегти власний пресет",this::savePreset),new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(presets);Ui.space(page,12);
        curve=new CurveView(activity);curve.setBackground(Ui.shape(activity,Ui.SURFACE,8));curve.setContentDescription("Частотна характеристика еквалайзера");page.addView(curve,new LinearLayout.LayoutParams(-1,dp(104)));Ui.space(page,12);
        LinearLayout ampLabel=Ui.row(activity);ampLabel.addView(Ui.title(activity,"PREAMP",12),new LinearLayout.LayoutParams(0,-2,1));preampValue=Ui.text(activity,"",12,Ui.ACCENT);preampValue.setMinHeight(dp(40));preampValue.setGravity(Gravity.CENTER);preampValue.setOnClickListener(v->editGain(-1));ampLabel.addView(preampValue);page.addView(ampLabel);
        preamp=new SeekBar(activity);preamp.setMax(96);preamp.setMinimumHeight(dp(40));preamp.setContentDescription("Попереднє підсилення");preamp.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));preamp.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));preamp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){preampGain=value*25-1200;settings.edit().putInt("dspPreamp",preampGain).remove("eqPresetName").apply();status();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});page.addView(preamp);
        LinearLayout panel=Ui.column(activity);Ui.pad(panel,4,12);android.graphics.drawable.GradientDrawable frame=Ui.shape(activity,0xFF15191E,8);frame.setStroke(dp(1),Ui.RAISED);panel.setBackground(frame);
        LinearLayout caption=Ui.row(activity);Ui.pad(caption,6,0);caption.addView(Ui.text(activity,"10 BAND GRAPHIC EQ",11,Ui.MUTED),new LinearLayout.LayoutParams(0,-2,1));caption.addView(Ui.text(activity,"±12 dB",11,Ui.MUTED));panel.addView(caption);Ui.space(panel,8);
        LinearLayout bands=Ui.row(activity);for(int i=0;i<10;i++){
            final int band=i;LinearLayout column=Ui.column(activity);column.setGravity(Gravity.CENTER);values[i]=Ui.text(activity,"",10,Ui.ACCENT);values[i].setGravity(Gravity.CENTER);values[i].setSingleLine(true);values[i].setAutoSizeTextTypeUniformWithConfiguration(7,11,1,android.util.TypedValue.COMPLEX_UNIT_SP);values[i].setOnClickListener(v->editGain(band));column.addView(values[i],new LinearLayout.LayoutParams(-1,dp(28)));
            VerticalSlider slider=new VerticalSlider(activity);sliders[i]=slider;slider.setMax(96);slider.setContentDescription(GraphicEqualizer.FREQUENCIES[i]+" Гц, підсилення");slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){if(user&&!refreshing){gains[band]=value*25-1200;settings.edit().putInt("dspEq_"+band,gains[band]).remove("eqPresetName").apply();status();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});column.addView(slider,new LinearLayout.LayoutParams(-1,dp(180)));TextView frequency=Ui.text(activity,frequency(GraphicEqualizer.FREQUENCIES[i]),10,Ui.MUTED);frequency.setGravity(Gravity.CENTER);column.addView(frequency,new LinearLayout.LayoutParams(-1,-2));bands.addView(column,new LinearLayout.LayoutParams(0,-2,1));
        }panel.addView(bands);page.addView(panel);Ui.space(page,12);
        Switch headroom=new Switch(activity);headroom.setText("Автозапас рівня");headroom.setTextSize(14);headroom.setTextColor(Ui.TEXT);headroom.setMinHeight(dp(48));Ui.tint(headroom);headroom.setChecked(settings.getBoolean("dspHeadroom",true));headroom.setOnCheckedChangeListener((button,on)->{settings.edit().putBoolean("dspHeadroom",on).apply();status();});page.addView(headroom);headroomNote=Ui.text(activity,"",12,Ui.MUTED);page.addView(headroomNote);Ui.space(page,12);page.addView(Ui.text(activity,"Ефект застосовується лише до музики SELFonium. Натисніть значення над смугою, щоб ввести точне підсилення.",12,Ui.MUTED));Ui.space(page,16);
        caps.registerOnSharedPreferenceChangeListener(formatListener);dialog.setOnDismissListener(d->caps.unregisterOnSharedPreferenceChangeListener(formatListener));dialog.setContentView(scroll);dialog.show();dialog.getWindow().setLayout(-1,-1);refresh();
    }
    private static String frequency(int hz){return hz<1000?Integer.toString(hz):(hz/1000)+"k";}
    private static String db(int value){return String.format(Locale.ROOT,"%+.1f dB",value/100.0);}
    private void refresh(){refreshing=true;GraphicEqualizer.Config config=EqualizerSettings.read(settings);for(int i=0;i<10;i++){gains[i]=config.gain(i);sliders[i].setProgress((gains[i]+1200)/25);}preampGain=config.preamp;preamp.setProgress((preampGain+1200)/25);refreshing=false;status();}
    private void status(){
        for(int i=0;i<10;i++){values[i].setText(String.format(Locale.ROOT,"%+.1f",gains[i]/100.0));values[i].setContentDescription(GraphicEqualizer.FREQUENCIES[i]+" Гц: "+db(gains[i]));}preampValue.setText(db(preampGain));
        int match=preampGain==0?EqualizerPresets.match(gains):-1;String name=match<0?"Власний":EqualizerPresets.NAMES[match],saved=settings.getString("eqPresetName","");JSONObject custom=customs().optJSONObject(saved);if(custom!=null&&same(custom))name=saved;presetName.setText("Пресет · "+name+" ▾");
        int rate=caps.getInt("sampleRate",48000);if(rate<8000)rate=48000;GraphicEqualizer.Config config=new GraphicEqualizer.Config(true,gains,preampGain,settings.getBoolean("dspHeadroom",true));double attenuation=GraphicEqualizer.attenuationDb(config,rate);headroomNote.setText(config.headroom?String.format(Locale.ROOT,"Компенсація: −%.1f dB · зменшує ризик перевантаження",attenuation):"Рівень регулюється вручну через Preamp.");curve.update(config,rate,attenuation);
    }
    private JSONObject customs(){try{return new JSONObject(settings.getString("eqUserPresets","{}"));}catch(JSONException e){return new JSONObject();}}
    private boolean same(JSONObject preset){JSONArray array=preset.optJSONArray("gains");if(array==null||array.length()!=10||preset.optInt("preamp")!=preampGain)return false;for(int i=0;i<10;i++)if(array.optInt(i)!=gains[i])return false;return true;}
    private void apply(int[] values,int amp,String name){EqualizerSettings.save(settings,values,amp);settings.edit().putString("eqPresetName",name).apply();refresh();}
    private void choosePreset(){
        List<String> names=new ArrayList<>(Arrays.asList(EqualizerPresets.NAMES)),keys=new ArrayList<>();JSONObject presets=customs();presets.keys().forEachRemaining(keys::add);Collections.sort(keys,String.CASE_INSENSITIVE_ORDER);for(String key:keys)names.add("★ "+key);if(!keys.isEmpty())names.add("Видалити власний пресет…");
        new AlertDialog.Builder(activity).setTitle("Пресети еквалайзера").setItems(names.toArray(new String[0]),(d,at)->{
            if(at<EqualizerPresets.NAMES.length)apply(EqualizerPresets.gains(at),0,EqualizerPresets.NAMES[at]);else if(at<EqualizerPresets.NAMES.length+keys.size()){String key=keys.get(at-EqualizerPresets.NAMES.length);JSONObject preset=presets.optJSONObject(key);if(preset!=null){int[] values=new int[10];JSONArray array=preset.optJSONArray("gains");if(array!=null&&array.length()==10){for(int i=0;i<10;i++)values[i]=array.optInt(i);apply(values,preset.optInt("preamp"),key);}}}else new AlertDialog.Builder(activity).setTitle("Видалити власний пресет").setItems(keys.toArray(new String[0]),(dd,index)->new AlertDialog.Builder(activity).setMessage("Видалити «"+keys.get(index)+"»?").setNegativeButton("Скасувати",null).setPositiveButton("Видалити",(x,y)->{presets.remove(keys.get(index));SharedPreferences.Editor edit=settings.edit().putString("eqUserPresets",presets.toString());if(keys.get(index).equals(settings.getString("eqPresetName","")))edit.remove("eqPresetName");edit.apply();status();}).show()).show();
        }).show();
    }
    private void savePreset(){
        EditText name=new EditText(activity);name.setSingleLine(true);name.setHint("Назва пресета");name.setTextColor(Ui.TEXT);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});Ui.pad(name,20,12);
        AlertDialog prompt=new AlertDialog.Builder(activity).setTitle("Зберегти пресет").setView(name).setNegativeButton("Скасувати",null).setPositiveButton("Зберегти",null).create();prompt.setOnShowListener(d->prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String key=name.getText().toString().trim();if(key.isEmpty()){name.setError("Введіть назву");return;}JSONObject presets=customs();Runnable save=()->{try{JSONObject value=new JSONObject();JSONArray array=new JSONArray();for(int gain:gains)array.put(gain);value.put("gains",array);value.put("preamp",preampGain);presets.put(key,value);settings.edit().putString("eqUserPresets",presets.toString()).putString("eqPresetName",key).apply();prompt.dismiss();status();}catch(JSONException ignored){}};if(presets.has(key))new AlertDialog.Builder(activity).setMessage("Замінити пресет «"+key+"»?").setNegativeButton("Скасувати",null).setPositiveButton("Замінити",(x,y)->save.run()).show();else save.run();}));prompt.show();
    }
    private void editGain(int band){EditText value=new EditText(activity);value.setSingleLine(true);value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);value.setText(String.format(Locale.ROOT,"%.2f",(band<0?preampGain:gains[band])/100.0));value.selectAll();Ui.pad(value,20,12);
        AlertDialog prompt=new AlertDialog.Builder(activity).setTitle((band<0?"Preamp":GraphicEqualizer.FREQUENCIES[band]+" Гц")+" · −12…+12 dB").setView(value).setNegativeButton("Скасувати",null).setPositiveButton("Застосувати",null).create();prompt.setOnShowListener(d->prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{double number=Double.parseDouble(value.getText().toString().replace(',','.'));if(!Double.isFinite(number)||number< -12||number>12)throw new NumberFormatException();int gain=(int)Math.round(number*4)*25;settings.edit().putInt(band<0?"dspPreamp":"dspEq_"+band,gain).remove("eqPresetName").apply();prompt.dismiss();refresh();}catch(NumberFormatException e){value.setError("Вкажіть число від −12 до +12");}}));prompt.show();}
    private final class CurveView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();double[] response=new double[96];
        CurveView(Context context){super(context);}
        void update(GraphicEqualizer.Config config,int rate,double attenuation){for(int i=0;i<response.length;i++)response[i]=GraphicEqualizer.responseDb(config,rate,20*Math.pow(1000,i/(double)(response.length-1)))-attenuation;invalidate();}
        @Override protected void onDraw(Canvas canvas){float left=dp(32),right=getWidth()-dp(12),top=dp(12),bottom=getHeight()-dp(22);paint.setTextSize(dp(9));paint.setStrokeWidth(dp(1));for(int db:new int[]{12,0,-12,-24}){float y=top+(12-db)/36f*(bottom-top);paint.setColor(Ui.RAISED);canvas.drawLine(left,y,right,y,paint);paint.setColor(Ui.MUTED);canvas.drawText(Integer.toString(db),dp(5),y+dp(3),paint);}canvas.drawText("20",left,getHeight()-dp(6),paint);canvas.drawText("1k",left+(right-left)*(float)(Math.log(50)/Math.log(1000)),getHeight()-dp(6),paint);canvas.drawText("20k",right-dp(18),getHeight()-dp(6),paint);path.reset();for(int i=0;i<response.length;i++){float x=left+(right-left)*i/(response.length-1),y=top+(float)(12-Math.max(-24,Math.min(12,response[i])))/36*(bottom-top);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(Ui.ACCENT);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
    }
    private static final class VerticalSlider extends SeekBar {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        VerticalSlider(Context context){super(context);setPadding(0,Ui.dp(context,12),0,Ui.dp(context,12));setSplitTrack(false);setBackground(null);setThumb(null);setProgressDrawable(null);}
        @Override protected synchronized void onMeasure(int width,int height){setMeasuredDimension(resolveSize(Ui.dp(getContext(),28),width),resolveSize(Ui.dp(getContext(),180),height));}
        @Override protected synchronized void onDraw(Canvas canvas){
            float x=getWidth()/2f,top=getPaddingTop(),bottom=getHeight()-getPaddingBottom(),y=bottom-(bottom-top)*getProgress()/Math.max(1,getMax());
            paint.setStrokeWidth(Ui.dp(getContext(),1));paint.setColor(Ui.RAISED);for(int i=0;i<=8;i++){float tick=top+(bottom-top)*i/8;canvas.drawLine(x-Ui.dp(getContext(),8),tick,x+Ui.dp(getContext(),8),tick,paint);}
            paint.setStrokeWidth(Ui.dp(getContext(),3));paint.setColor(0xFF090B0D);canvas.drawLine(x,top,x,bottom,paint);paint.setStrokeWidth(Ui.dp(getContext(),1));paint.setColor(Ui.ACCENT);canvas.drawLine(x,y,x,bottom,paint);
            float half=Math.min(Ui.dp(getContext(),10),getWidth()/2f-2);paint.setColor(Ui.ACCENT);canvas.drawRoundRect(x-half,y-Ui.dp(getContext(),5),x+half,y+Ui.dp(getContext(),5),Ui.dp(getContext(),2),Ui.dp(getContext(),2),paint);paint.setColor(Ui.ON_ACCENT);canvas.drawLine(x-half+2,y,x+half-2,y,paint);
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
