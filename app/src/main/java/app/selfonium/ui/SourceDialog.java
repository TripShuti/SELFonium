package app.selfonium.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import app.selfonium.core.Source;
import java.util.UUID;

/** A scrollable source editor with persistent labels and full-width touch targets. */
final class SourceDialog {
    interface Save { void save(Source source) throws Exception; }
    private static final String[] NAMES={"Subsonic / Navidrome","Jellyfin","Emby","Plex","WebDAV","Kodi","Audiobookshelf"};

    static void show(Activity activity,Source existing,Save save){
        Dialog dialog=Ui.screen(activity);
        LinearLayout root=Ui.column(activity);root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int bottom=insets.getSystemWindowInsetBottom();
            if(Build.VERSION.SDK_INT>=30)bottom=Math.max(bottom,insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),bottom);return insets;
        });
        LinearLayout header=Ui.row(activity);Ui.pad(header,16,12);
        TextView title=Ui.title(activity,existing==null?"Нове джерело":"Редагувати джерело",22);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        IconButton close=new IconButton(activity,"close","Закрити",dialog::dismiss);
        header.addView(close,new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)));root.addView(header);

        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout form=Ui.column(activity);Ui.pad(form,24,12);scroll.addView(form,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView intro=Ui.text(activity,"Під’єднайте свою музичну бібліотеку. Вкажіть адресу сервера та облікові дані.",15,Ui.MUTED);intro.setLineSpacing(Ui.dp(activity,3),1);form.addView(intro);Ui.space(form,24);

        label(form,"Тип сервера");
        Spinner type=new Spinner(activity);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);type.setAdapter(adapter);
        type.setBackground(Ui.shape(activity,Ui.SURFACE,12));Ui.pad(type,12,4);
        form.addView(type,new LinearLayout.LayoutParams(-1,Ui.dp(activity,56)));Ui.space(form,20);

        EditText name=field(form,"Назва джерела","Наприклад: Домашня музика",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText url=field(form,"Адреса сервера","https://music.example.org",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        TextView help=Ui.text(activity,"",13,Ui.MUTED);help.setLineSpacing(Ui.dp(activity,3),1);form.addView(help);Ui.space(form,20);
        EditText user=field(form,"Ім’я користувача","Ваш логін",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText password=field(form,"Пароль","Пароль сервера",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTransformationMethod(PasswordTransformationMethod.getInstance());
        CheckBox reveal=new CheckBox(activity);reveal.setText("Показати пароль");reveal.setTextColor(Ui.TEXT);reveal.setTextSize(14);reveal.setMinHeight(Ui.dp(activity,48));Ui.tint(reveal);
        reveal.setOnCheckedChangeListener((button,checked)->{int at=password.getSelectionStart();password.setTransformationMethod(checked?null:PasswordTransformationMethod.getInstance());password.setSelection(Math.max(0,Math.min(at,password.length())));});form.addView(reveal);
        TextView error=Ui.text(activity,"",14,0xFFFFB4AB);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);error.setVisibility(View.GONE);form.addView(error);Ui.space(form,12);
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> parent){}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(view instanceof TextView text){text.setTextColor(Ui.TEXT);text.setTextSize(16);}
                Source.Type selected=Source.Type.values()[position];
                help.setText(switch(selected){
                    case SUBSONIC -> "Коренева адреса Subsonic або Navidrome, без /rest. Додайте порт, якщо він потрібний.";
                    case JELLYFIN,EMBY -> "Адреса сервера з портом, без /web. Для домашнього HTTPS встановіть CA сервера в налаштуваннях Android.";
                    case PLEX -> "Адреса Plex Media Server. У полі пароля вкажіть X-Plex-Token; логін не потрібний.";
                    case WEBDAV -> "Повна адреса WebDAV-папки з музикою.";
                    case KODI -> "Адреса HTTP-сервера Kodi. У Kodi має бути дозволене керування через HTTP.";
                    case AUDIOBOOKSHELF -> "Коренева адреса Audiobookshelf і дані вашого облікового запису.";
                });
                user.setEnabled(selected!=Source.Type.PLEX);user.setAlpha(selected==Source.Type.PLEX?.5f:1f);
                password.setHint(selected==Source.Type.PLEX?"X-Plex-Token":"Пароль сервера");
            }
        });
        if(existing!=null){name.setText(existing.name);url.setText(existing.baseUrl);user.setText(existing.username);password.setText(existing.password);type.setSelection(existing.type.ordinal());type.setEnabled(false);}

        LinearLayout footer=Ui.column(activity);Ui.pad(footer,24,12);
        footer.addView(Ui.button(activity,"Зберегти й синхронізувати",true,()->{
            name.setError(null);url.setError(null);error.setVisibility(View.GONE);
            if(name.getText().toString().trim().isEmpty()){name.setError("Введіть назву джерела");name.requestFocus();return;}
            Source source;
            try{source=new Source(existing==null?UUID.randomUUID().toString():existing.id,name.getText().toString().trim(),Source.Type.values()[type.getSelectedItemPosition()],url.getText().toString(),user.getText().toString().trim(),password.getText().toString());}
            catch(IllegalArgumentException exception){url.setError("Вкажіть повну адресу http:// або https://");url.requestFocus();return;}
            try{save.save(source);dialog.dismiss();}
            catch(Exception exception){error.setText("Не вдалося зберегти джерело. Спробуйте ще раз.");error.setVisibility(View.VISIBLE);}
        }),new LinearLayout.LayoutParams(-1,-2));root.addView(footer);
        dialog.setContentView(root);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();dialog.getWindow().setLayout(-1,-1);root.requestApplyInsets();
    }
    private static void label(LinearLayout form,String text){form.addView(Ui.title(form.getContext(),text,14));Ui.space(form,8);}
    private static EditText field(LinearLayout form,String title,String hint,int inputType){
        Activity activity=(Activity)form.getContext();label(form,title);
        EditText field=new EditText(activity);field.setSingleLine(true);field.setTextSize(16);field.setTextColor(Ui.TEXT);field.setHintTextColor(Ui.MUTED);field.setHint(hint);field.setInputType(inputType);
        field.setBackgroundTintList(null);
        StateListDrawable background=new StateListDrawable();
        GradientDrawable focused=Ui.shape(activity,Ui.SURFACE,12);focused.setStroke(Ui.dp(activity,2),Ui.ACCENT);
        GradientDrawable normal=Ui.shape(activity,Ui.SURFACE,12);normal.setStroke(Ui.dp(activity,1),Ui.RAISED);
        background.addState(new int[]{android.R.attr.state_focused},focused);background.addState(new int[]{},normal);field.setBackground(background);
        field.setBackgroundTintList(null);Ui.pad(field,16,14);field.setMinHeight(Ui.dp(activity,56));
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        form.addView(field,new LinearLayout.LayoutParams(-1,-2));Ui.space(form,20);return field;
    }
}
