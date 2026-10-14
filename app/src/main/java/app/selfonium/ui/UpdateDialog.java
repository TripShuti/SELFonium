package app.selfonium.ui;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.View;
import android.widget.*;
import androidx.core.content.FileProvider;
import app.selfonium.R;
import app.selfonium.update.*;
import java.io.File;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

/** Uses the same surfaces, typography and controls as the existing full-screen settings. */
final class UpdateDialog {
    static UpdateDialog show(Activity activity,UpdateManager manager){UpdateDialog screen=new UpdateDialog(activity,manager);screen.open();return screen;}
    private final Activity activity;private final UpdateManager manager;private final Dialog dialog;
    private final Consumer<UpdateManager.State> changed=this::render;
    private TextView last,status,check,download,details,notes,progressLabel,cancel;
    private LinearLayout available;private ProgressBar progress;private boolean awaitingPermission,foreground=true;private ReleaseInfo shown;
    private UpdateDialog(Activity activity,UpdateManager manager){this.activity=activity;this.manager=manager;dialog=Ui.screen(activity);}
    private String text(int id,Object...args){return activity.getString(id,args);}
    private String date(long when){return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(when));}
    private void open(){
        ScrollView scroll=new ScrollView(activity);Ui.insets(scroll);LinearLayout page=Ui.column(activity);Ui.pad(page,16,8);scroll.addView(page);
        LinearLayout top=Ui.row(activity);top.addView(new IconButton(activity,"back",text(R.string.updates_close),dialog::dismiss));top.addView(Ui.title(activity,text(R.string.updates_title),20));page.addView(top);Ui.space(page,16);
        LinearLayout card=Ui.column(activity);card.setBackground(Ui.shape(activity,Ui.SURFACE,16));Ui.pad(card,16,16);page.addView(card);
        card.addView(Ui.title(activity,text(R.string.updates_current,manager.installedVersion,manager.installedCode),16));Ui.space(card,12);
        last=Ui.text(activity,"",12,Ui.MUTED);card.addView(last);Ui.space(card,12);
        Switch automatic=new Switch(activity);automatic.setText(text(R.string.updates_automatic));automatic.setTextColor(Ui.TEXT);automatic.setMinHeight(Ui.dp(activity,56));Ui.tint(automatic);automatic.setChecked(manager.state().automatic());automatic.setOnCheckedChangeListener((v,checked)->manager.automatic(checked));card.addView(automatic);
        card.addView(Ui.text(activity,text(R.string.updates_automatic_hint),12,Ui.MUTED));Ui.space(card,16);
        check=Ui.button(activity,text(R.string.updates_check),false,()->manager.check(true));card.addView(check);Ui.space(card,12);
        status=Ui.text(activity,"",14,Ui.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(status);
        Ui.space(page,16);available=Ui.column(activity);available.setBackground(Ui.shape(activity,Ui.SURFACE,16));Ui.pad(available,16,16);page.addView(available);
        details=Ui.title(activity,"",17);available.addView(details);Ui.space(available,16);
        available.addView(Ui.title(activity,text(R.string.updates_notes),16));Ui.space(available,8);
        notes=Ui.text(activity,"",14,Ui.MUTED);notes.setTextIsSelectable(true);notes.setMaxLines(8);notes.setEllipsize(android.text.TextUtils.TruncateAt.END);available.addView(notes);Ui.space(available,8);
        TextView expand=Ui.button(activity,text(R.string.updates_notes_expand),false,()->{});expand.setOnClickListener(v->{boolean full=notes.getMaxLines()==8;notes.setMaxLines(full?Integer.MAX_VALUE:8);notes.setEllipsize(full?null:android.text.TextUtils.TruncateAt.END);expand.setText(text(full?R.string.updates_notes_collapse:R.string.updates_notes_expand));});available.addView(expand);Ui.space(available,16);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));available.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(activity,8)));Ui.space(available,8);
        progressLabel=Ui.text(activity,"",13,Ui.MUTED);available.addView(progressLabel);Ui.space(available,12);
        download=Ui.button(activity,"",true,()->{if(manager.state().stage()==UpdateManager.Stage.READY)install();else manager.download();});available.addView(download);Ui.space(available,8);
        cancel=Ui.button(activity,text(R.string.updates_cancel),false,manager::cancel);available.addView(cancel);Ui.space(available,8);
        available.addView(Ui.button(activity,text(R.string.updates_later),false,dialog::dismiss));Ui.space(available,8);
        available.addView(Ui.button(activity,text(R.string.updates_skip),false,()->{manager.skip();dialog.dismiss();}));Ui.space(page,24);
        dialog.setContentView(scroll);dialog.setOnDismissListener(d->manager.unlisten(changed));dialog.show();dialog.getWindow().setLayout(-1,-1);manager.listen(changed);
    }
    private void render(UpdateManager.State state){
        if(activity.isDestroyed())return;var stage=state.stage();boolean busy=stage==UpdateManager.Stage.LOADING||stage==UpdateManager.Stage.CHECKING||stage==UpdateManager.Stage.DOWNLOADING||stage==UpdateManager.Stage.VERIFYING;
        last.setText(state.lastSuccess()==0?text(R.string.updates_never):text(R.string.updates_last,date(state.lastSuccess())));check.setEnabled(!busy);check.setAlpha(busy?.5f:1);
        String message=switch(stage){case LOADING->text(R.string.updates_loading);case CHECKING->text(R.string.updates_checking);case VERIFYING->text(R.string.updates_verifying);case READY->text(R.string.updates_ready);case AVAILABLE,DOWNLOADING->text(R.string.updates_available,state.release().version);case IDLE->text(state.lastSuccess()==0?R.string.updates_idle:R.string.updates_current_latest);};
        if(state.error()!=null)message=error(state.error());if(!message.contentEquals(status.getText()))status.setText(message);available.setVisibility(state.release()==null?View.GONE:View.VISIBLE);if(state.release()==null)return;
        ReleaseInfo release=state.release();if(shown!=release){shown=release;details.setText(text(R.string.updates_release_info,manager.installedVersion,release.version,date(Instant.parse(release.published).toEpochMilli()),Formatter.formatShortFileSize(activity,release.size)));
        // Literal text only: no HTML, WebView, JavaScript or externally supplied clickable spans.
        notes.setText(release.notes.isBlank()?text(R.string.updates_notes_empty):release.notes);}
        boolean downloading=stage==UpdateManager.Stage.DOWNLOADING;progress.setVisibility(downloading?View.VISIBLE:View.GONE);progressLabel.setVisibility(downloading?View.VISIBLE:View.GONE);
        if(downloading){int percent=(int)(state.downloaded()*100/release.size);progress.setProgress(percent);progressLabel.setText(text(R.string.updates_progress,percent,Formatter.formatShortFileSize(activity,state.downloaded()),Formatter.formatShortFileSize(activity,release.size)));}
        download.setText(text(stage==UpdateManager.Stage.READY?R.string.updates_install:R.string.updates_download));download.setEnabled(!busy);download.setAlpha(busy?.5f:1);cancel.setVisibility(downloading?View.VISIBLE:View.GONE);
    }
    private String error(UpdatePolicy.Failure error){return switch(error.reason){
        case NETWORK->text(R.string.updates_error_network);case HTTP->text(R.string.updates_error_http,error.status);
        case NOT_FOUND,NO_ASSET->text(R.string.updates_error_missing);case RATE_LIMIT->text(R.string.updates_error_rate,date(error.retryAt));
        case INVALID_RELEASE->text(R.string.updates_error_metadata);case INTEGRITY->text(R.string.updates_error_integrity);
        case PACKAGE->text(R.string.updates_error_package);case VERSION->text(R.string.updates_error_version);case SIGNATURE->text(R.string.updates_error_signature);
        case SPACE->text(R.string.updates_error_space);case STORAGE->text(R.string.updates_error_storage);case CANCELED->text(R.string.updates_error_canceled);
        case THROTTLED->text(R.string.updates_error_throttled,date(error.retryAt));case INSTALL->text(R.string.updates_installer_unavailable);
    };}
    boolean isShowing(){return dialog.isShowing();}
    void dismiss(){dialog.dismiss();}
    void onStop(){foreground=false;}
    void onResume(){foreground=true;if(!awaitingPermission)return;awaitingPermission=false;if(activity.getPackageManager().canRequestPackageInstalls())install();else Toast.makeText(activity,text(R.string.updates_permission_denied),Toast.LENGTH_LONG).show();}
    private void install(){
        manager.prepareInstall(file->{if(activity.isDestroyed()||!dialog.isShowing()||!foreground)return;
            if(!activity.getPackageManager().canRequestPackageInstalls()){
                new AlertDialog.Builder(activity).setTitle(text(R.string.updates_permission_title)).setMessage(text(R.string.updates_permission)).setNegativeButton(text(R.string.updates_later),null).setPositiveButton(text(R.string.updates_open_settings),(d,w)->{
                    try{awaitingPermission=true;activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(ActivityNotFoundException|SecurityException e){awaitingPermission=false;unavailable();}
                }).show();return;
            }
            try{
                Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",file);
                Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("SELFonium APK",uri));activity.startActivity(intent);
            }catch(ActivityNotFoundException|SecurityException|IllegalArgumentException e){unavailable();}
        });
    }
    private void unavailable(){Toast.makeText(activity,text(R.string.updates_installer_unavailable),Toast.LENGTH_LONG).show();}
}
