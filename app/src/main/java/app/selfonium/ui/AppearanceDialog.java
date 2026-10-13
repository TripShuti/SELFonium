package app.selfonium.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import android.view.View;

/** Preview is local until Apply. Recreating the activity leaves Media3 playback running. */
final class AppearanceDialog {
    static Dialog show(Activity activity){AppearanceDialog screen=new AppearanceDialog(activity);screen.open();return screen.dialog;}
    private final Activity activity;private final Dialog dialog;private int accent;private TextView preview,colorLabel;private Switch oled;private RadioGroup columns;private int twoId,threeId;
    private AppearanceDialog(Activity activity){this.activity=activity;dialog=Ui.screen(activity);accent=activity.getSharedPreferences("appearance",0).getInt("accent",Ui.DEFAULT_ACCENT);}
    private int dp(int n){return Ui.dp(activity,n);}
    private void open(){
        ScrollView scroll=new ScrollView(activity);Ui.insets(scroll);LinearLayout page=Ui.column(activity);Ui.pad(page,16,0);scroll.addView(page);
        LinearLayout top=Ui.row(activity);top.addView(new IconButton(activity,"back","Закрити вигляд",dialog::dismiss));top.addView(Ui.title(activity,"Вигляд",20));page.addView(top);Ui.space(page,16);
        page.addView(Ui.title(activity,"Акцентний колір",18));Ui.space(page,12);
        int[] colors={Ui.DEFAULT_ACCENT,0xFFA7C7FF,0xFF85DCC6,0xFFB9DC88,0xFFFFD782,0xFFFFB18B,0xFFF6A5CC,0xFFE4E7EE};String[] names={"Бузковий","Блакитний","М’ятний","Зелений","Золотий","Персиковий","Рожевий","Срібний"};
        for(int offset=0;offset<colors.length;offset+=4){LinearLayout row=Ui.row(activity);for(int i=offset;i<offset+4;i++){final int color=colors[i];TextView swatch=Ui.button(activity,"●",false,()->{accent=color;preview();});swatch.setTextSize(26);swatch.setTextColor(color);swatch.setContentDescription("Акцент: "+names[i]);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(56),1);size.setMargins(0,0,dp(6),dp(6));row.addView(swatch,size);}page.addView(row);}
        colorLabel=Ui.button(activity,"",false,this::customColor);colorLabel.setContentDescription("Власний акцентний колір");page.addView(colorLabel);Ui.space(page,16);
        preview=Ui.button(activity,"Приклад активної кнопки",true,()->{});preview.setClickable(false);page.addView(preview);Ui.space(page,24);
        page.addView(Ui.title(activity,"Фон",18));oled=new Switch(activity);oled.setText("Чорний фон AMOLED");oled.setTextColor(Ui.TEXT);oled.setMinHeight(dp(56));Ui.tint(oled);oled.setChecked(activity.getSharedPreferences("appearance",0).getBoolean("oled",false));page.addView(oled);Ui.space(page,16);
        page.addView(Ui.title(activity,"Колонки обкладинок",18));columns=new RadioGroup(activity);columns.setOrientation(LinearLayout.HORIZONTAL);int current=activity.getSharedPreferences("player",0).getInt("gridColumns",2);for(int count:new int[]{2,3}){RadioButton button=new RadioButton(activity);int id=View.generateViewId();if(count==2)twoId=id;else threeId=id;button.setId(id);button.setText(count+" колонки");button.setTextColor(Ui.TEXT);Ui.tint(button);button.setMinHeight(dp(48));columns.addView(button,new RadioGroup.LayoutParams(0,-2,1));}columns.check(current==2?twoId:threeId);page.addView(columns);Ui.space(page,24);
        page.addView(Ui.button(activity,"Застосувати",true,()->{activity.getSharedPreferences("appearance",0).edit().putInt("accent",accent).putBoolean("oled",oled.isChecked()).apply();activity.getSharedPreferences("player",0).edit().putInt("gridColumns",columns.getCheckedRadioButtonId()==twoId?2:3).apply();dialog.dismiss();activity.recreate();}));Ui.space(page,8);
        page.addView(Ui.button(activity,"Відновити стандартний вигляд",false,()->{accent=Ui.DEFAULT_ACCENT;oled.setChecked(false);columns.check(twoId);preview();}));Ui.space(page,20);preview();dialog.setContentView(scroll);dialog.show();dialog.getWindow().setLayout(-1,-1);
    }
    private void preview(){colorLabel.setText(String.format(java.util.Locale.ROOT,"Власний колір · #%06X",accent&0xFFFFFF));preview.setBackground(Ui.shape(activity,accent,12));preview.setTextColor(Ui.luminance(accent)>.179?0xFF111318:0xFFFFFFFF);}
    private void customColor(){
        EditText value=new EditText(activity);value.setSingleLine(true);value.setText(String.format(java.util.Locale.ROOT,"#%06X",accent&0xFFFFFF));value.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});value.selectAll();Ui.pad(value,20,12);
        AlertDialog prompt=new AlertDialog.Builder(activity).setTitle("Колір #RRGGBB").setView(value).setNegativeButton("Скасувати",null).setPositiveButton("Обрати",null).create();prompt.setOnShowListener(d->prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String text=value.getText().toString().trim();if(!text.matches("#?[0-9a-fA-F]{6}")){value.setError("Наприклад, #A7C7FF");return;}int color=Color.parseColor(text.startsWith("#")?text:"#"+text);if((Ui.luminance(color)+.05)/(Ui.luminance(Ui.SURFACE)+.05)<3){value.setError("Оберіть світліший колір для читабельного тексту");return;}accent=color;prompt.dismiss();preview();}));prompt.show();
    }
}
