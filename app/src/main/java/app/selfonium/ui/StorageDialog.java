package app.selfonium.ui;

import android.app.*;
import android.content.res.ColorStateList;
import android.os.*;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;
import app.selfonium.R;
import app.selfonium.locale.AppLanguage;
import app.selfonium.SelfoniumApp;
import app.selfonium.data.*;
import app.selfonium.core.Track;
import java.util.*;

/** Existing settings surfaces; measurements and file operations run off the main thread. */
final class StorageDialog {
    static Dialog show(Activity activity,Runnable offline){StorageDialog screen=new StorageDialog(activity,offline);screen.open();return screen.dialog;}
    private final Activity activity;private final SelfoniumApp app;private final Runnable offline;private final Dialog dialog;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<Integer,TextView> sizes=new HashMap<>();private LinearLayout queue;private TextView used,free,audioLimit,downloaded,operation,clearAudioAction,clearArtAction,cleanupAction,removeAction;
    private ProgressBar deviceUsage;private StorageRepository.Snapshot snapshot;private boolean scanning,busy,closed,deferredClear;private long queueUpdated;private int queuePage;private String queueStructure="";
    private record QueueViews(TextView details,ProgressBar progress){}
    private final Map<String,QueueViews> queueViews=new HashMap<>();
    private final Runnable downloadsChanged=()->{if(System.currentTimeMillis()-queueUpdated>250){queueUpdated=System.currentTimeMillis();renderQueue();}};
    private final Runnable poll=new Runnable(){public void run(){if(closed)return;refresh();renderQueue();handler.postDelayed(this,3000);}};
    private StorageDialog(Activity activity,Runnable offline){this.activity=activity;this.offline=offline;app=(SelfoniumApp)activity.getApplication();dialog=Ui.screen(activity);}
    private String text(int id,Object...args){return args.length==0?activity.getString(id):activity.getString(id,args);}
    private String size(long bytes){return Formatter.formatShortFileSize(activity,Math.max(0,bytes));}
    private TextView note(LinearLayout parent,String value){TextView label=Ui.text(activity,value,13,Ui.MUTED);label.setLineSpacing(Ui.dp(activity,3),1);parent.addView(label);Ui.space(parent,10);return label;}
    private LinearLayout card(LinearLayout page,int title){LinearLayout card=Ui.column(activity);card.setBackground(Ui.shape(activity,Ui.SURFACE,16));Ui.pad(card,16,16);page.addView(card,new LinearLayout.LayoutParams(-1,-2));card.addView(Ui.title(activity,text(title),18));Ui.space(card,16);Ui.space(page,16);return card;}
    private TextView action(LinearLayout parent,int title,Runnable click){TextView button=Ui.button(activity,text(title),false,()->{if(!busy||title==R.string.ui_cancel||title==R.string.storage_cancel_all)click.run();});button.setSingleLine(false);parent.addView(button,new LinearLayout.LayoutParams(-1,-2));Ui.space(parent,8);return button;}
    private void measureRow(LinearLayout card,int label){LinearLayout row=Ui.row(activity);TextView title=Ui.text(activity,text(label),14,Ui.TEXT);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView value=Ui.text(activity,"…",14,Ui.MUTED);Ui.pad(value,8,8);row.addView(value);card.addView(row);sizes.put(label,value);}
    private void open(){
        ScrollView scroll=new ScrollView(activity);Ui.insets(scroll);LinearLayout page=Ui.column(activity);Ui.pad(page,16,8);scroll.addView(page);
        LinearLayout top=Ui.row(activity);top.addView(new IconButton(activity,"back",text(R.string.ui_back),dialog::dismiss));top.addView(Ui.title(activity,text(R.string.storage_title),20),new LinearLayout.LayoutParams(0,-2,1));top.addView(new IconButton(activity,"refresh",text(R.string.storage_refresh),this::refresh));page.addView(top);Ui.space(page,16);
        LinearLayout overview=card(page,R.string.storage_overview);used=note(overview,"…");free=note(overview,"…");deviceUsage=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);deviceUsage.setMax(1000);deviceUsage.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));overview.addView(deviceUsage,new LinearLayout.LayoutParams(-1,Ui.dp(activity,6)));Ui.space(overview,12);
        for(int label:new int[]{R.string.storage_downloaded,R.string.storage_audio,R.string.storage_artwork,R.string.storage_database,R.string.storage_temporary,R.string.storage_other})measureRow(overview,label);
        operation=note(page,"");operation.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout music=card(page,R.string.storage_downloaded);downloaded=note(music,"…");action(music,R.string.storage_manage,()->{dialog.dismiss();offline.run();});
        Switch wifi=new Switch(activity);wifi.setText(text(R.string.storage_wifi));wifi.setTextColor(Ui.TEXT);wifi.setMinHeight(Ui.dp(activity,56));Ui.tint(wifi);wifi.setChecked(app.downloads.wifiOnly());wifi.setOnCheckedChangeListener((v,on)->app.downloads.wifiOnly(on));music.addView(wifi);note(music,text(R.string.storage_wifi_hint));
        removeAction=action(music,R.string.storage_remove_downloads,()->confirm(R.string.storage_remove_downloads,snapshot==null?0:snapshot.offline(),R.string.storage_downloads_note,()->{start();app.downloads.cancelAll();app.storage.removeAll(result->{finish(text(R.string.storage_cleared,size(result.bytes())));if(result.retained()>0&&!closed)new AlertDialog.Builder(activity).setMessage(text(R.string.storage_retained,result.retained())).setPositiveButton(R.string.ui_close,null).show();});}));
        LinearLayout cache=card(page,R.string.storage_cache);audioLimit=action(cache,R.string.storage_audio_limit,this::chooseLimit);updateLimit();
        Switch prefetch=new Switch(activity);prefetch.setText(text(R.string.storage_prefetch));prefetch.setTextColor(Ui.TEXT);prefetch.setMinHeight(Ui.dp(activity,56));Ui.tint(prefetch);prefetch.setChecked(activity.getSharedPreferences("storage",0).getBoolean("prefetch",true));prefetch.setOnCheckedChangeListener((v,on)->{start();app.storage.configureAudio(app.audio.limitBytes(),on,()->{updateLimit();finish("");});});cache.addView(prefetch);note(cache,text(R.string.storage_cache_hint));
        clearAudioAction=action(cache,R.string.storage_clear_audio,()->confirm(R.string.storage_clear_audio,snapshot==null?0:snapshot.audio(),R.string.storage_cache_note,()->{start();app.storage.clearAudio(result->{deferredClear=result.deferred();finish(result.deferred()?text(R.string.storage_deferred):text(R.string.storage_cleared,size(result.bytes())));});}));
        clearArtAction=action(cache,R.string.storage_clear_artwork,()->confirm(R.string.storage_clear_artwork,snapshot==null?0:snapshot.artwork(),R.string.storage_cache_note,()->{start();app.storage.clearArtwork(bytes->finish(text(R.string.storage_cleared,size(bytes))));}));
        LinearLayout maintenance=card(page,R.string.storage_maintenance);action(maintenance,R.string.storage_verify,()->{start();app.storage.verify(result->{List<Track> tracks=result.damaged();String retained=result.retained()>0?text(R.string.storage_retained,result.retained()):"";finish(tracks.isEmpty()?(result.retained()==0?text(R.string.storage_verify_ok):retained):retained);if(!tracks.isEmpty()&&!closed)new AlertDialog.Builder(activity).setTitle(R.string.storage_verify).setMessage(text(R.string.storage_verify_bad,tracks.size())+(retained.isEmpty()?"":"\n\n"+retained)).setNegativeButton(R.string.ui_cancel,null).setPositiveButton(R.string.storage_retry,(d,w)->app.downloads.enqueue(tracks.stream().map(t->t.id).toList())).show();},error->finish(text(R.string.storage_operation_failed)));});note(maintenance,text(R.string.storage_verify_hint));
        cleanupAction=action(maintenance,R.string.storage_cleanup,()->confirm(R.string.storage_cleanup,snapshot==null?0:snapshot.temporary(),R.string.storage_cleanup_hint,()->{start();app.storage.cleanup(bytes->finish(text(R.string.storage_cleared,size(bytes))));}));note(maintenance,text(R.string.storage_location));
        LinearLayout downloads=card(page,R.string.storage_queue);action(downloads,R.string.storage_retry_all,()->app.downloads.enqueue(app.downloads.entries().stream().filter(e->e.status()==DownloadRepository.Status.FAILED).map(DownloadRepository.Entry::id).toList()));action(downloads,R.string.storage_cancel_all,app.downloads::cancelAll);action(downloads,R.string.storage_clear_history,app.downloads::forgetHistory);queue=Ui.column(activity);downloads.addView(queue);
        dialog.setContentView(scroll);dialog.setOnDismissListener(d->{closed=true;handler.removeCallbacksAndMessages(null);app.downloads.unlisten(downloadsChanged);});dialog.show();dialog.getWindow().setLayout(-1,-1);app.downloads.listen(downloadsChanged);handler.post(poll);
    }
    private void start(){busy=true;operation.setText(text(R.string.storage_working));}
    private void finish(String message){busy=false;if(closed)return;operation.setText(message);refresh();renderQueue();}
    private void confirm(int title,long bytes,int explanation,Runnable run){if(snapshot==null)return;new AlertDialog.Builder(activity).setTitle(title).setMessage(text(R.string.storage_confirm_bytes,size(bytes))+"\n\n"+text(explanation)).setNegativeButton(R.string.ui_cancel,null).setPositiveButton(R.string.ui_delete_86,(d,w)->run.run()).show();}
    private void refresh(){if(closed||scanning)return;scanning=true;app.storage.inspect(value->{scanning=false;if(closed)return;snapshot=value;if(deferredClear&&!app.audio.clearPending()){deferredClear=false;operation.setText(text(R.string.storage_audio_cleared));}used.setText(text(R.string.storage_app_data,size(value.used())));free.setText(text(R.string.storage_free,size(value.free())));deviceUsage.setProgress(value.total()>0?(int)(1000.0*(value.total()-value.free())/value.total()):0);
        sizes.get(R.string.storage_downloaded).setText(size(value.offline()));sizes.get(R.string.storage_audio).setText(size(value.audio()));sizes.get(R.string.storage_artwork).setText(size(value.artwork()));sizes.get(R.string.storage_database).setText(size(value.library()));sizes.get(R.string.storage_other).setText(size(value.other()));sizes.get(R.string.storage_temporary).setText(size(value.temporary()));downloaded.setText(text(R.string.storage_offline_summary,Ui.tracks(activity,value.tracks()),size(value.offline())));
        clearAudioAction.setText(text(R.string.storage_clear_audio)+" · "+size(value.audio()));clearArtAction.setText(text(R.string.storage_clear_artwork)+" · "+size(value.artwork()));cleanupAction.setText(text(R.string.storage_cleanup)+" · "+size(value.temporary()));removeAction.setText(text(R.string.storage_remove_downloads)+" · "+size(value.offline()));
    });}
    private String limitLabel(long bytes){if(bytes==0)return text(R.string.storage_disabled);long mib=bytes/(1024*1024);return java.text.NumberFormat.getIntegerInstance(AppLanguage.locale(activity)).format(mib>=1024?mib/1024:mib)+(mib>=1024?" GiB":" MiB");}
    private void updateLimit(){if(audioLimit!=null)audioLimit.setText(text(R.string.storage_audio_limit)+" · "+limitLabel(app.audio.limitBytes()));}
    private void chooseLimit(){long[] limits={0,64,192,512,1024,2048};String[] labels=new String[limits.length];int selected=0;for(int i=0;i<limits.length;i++){labels[i]=limitLabel(limits[i]*1024*1024);if(app.audio.limitBytes()==limits[i]*1024*1024)selected=i;}
        new AlertDialog.Builder(activity).setTitle(R.string.storage_audio_limit).setSingleChoiceItems(labels,selected,(d,index)->{d.dismiss();start();app.storage.configureAudio(limits[index]*1024*1024,activity.getSharedPreferences("storage",0).getBoolean("prefetch",true),()->{updateLimit();finish("");});}).setNegativeButton(R.string.ui_cancel,null).show();}
    private String status(DownloadRepository.Entry entry){
        if(entry.status()==DownloadRepository.Status.COMPLETE&&!app.offline.available(entry.id()))return text(R.string.storage_removed);
        int resource=switch(entry.status()){case QUEUED->R.string.storage_queued;case RUNNING->R.string.storage_running;case WAITING->app.downloads.wifiOnly()?R.string.storage_wait_wifi:R.string.storage_wait_network;case FAILED->switch(entry.error()){case SPACE->R.string.storage_error_space;case INCOMPLETE->R.string.storage_error_incomplete;case SOURCE->R.string.storage_error_source;case INTERRUPTED->R.string.storage_error_interrupted;default->R.string.storage_failed;};case COMPLETE->R.string.storage_complete;case CANCELLED->R.string.storage_cancelled;};return text(resource);
    }
    private void renderQueue(){
        if(closed||queue==null)return;List<DownloadRepository.Entry> entries=app.downloads.entries();String structure=queuePage+":"+app.downloads.wifiOnly()+":"+entries.stream().map(e->e.id()+":"+e.status()+":"+e.error()).collect(java.util.stream.Collectors.joining(";"));if(structure.equals(queueStructure)){for(DownloadRepository.Entry entry:entries)updateQueueRow(entry);return;}queueStructure=structure;queue.removeAllViews();queueViews.clear();if(entries.isEmpty()){note(queue,text(R.string.storage_queue_empty));return;}
        entries.sort(java.util.Comparator.comparingInt(e->e.status()==DownloadRepository.Status.RUNNING?0:e.status()==DownloadRepository.Status.QUEUED||e.status()==DownloadRepository.Status.WAITING?1:e.status()==DownloadRepository.Status.FAILED?2:3));
        int pages=(entries.size()+24)/25;queuePage=Math.min(queuePage,pages-1);int start=queuePage*25,end=Math.min(entries.size(),start+25);
        if(pages>1){LinearLayout pagesRow=Ui.row(activity);pagesRow.addView(new IconButton(activity,"back",text(R.string.storage_previous),()->{if(queuePage>0){queuePage--;renderQueue();}}));pagesRow.addView(Ui.text(activity,text(R.string.storage_page,queuePage+1,pages),14,Ui.MUTED),new LinearLayout.LayoutParams(0,-2,1));pagesRow.addView(new IconButton(activity,"chevron",text(R.string.storage_next),()->{if(queuePage+1<pages){queuePage++;renderQueue();}}));queue.addView(pagesRow);}
        for(DownloadRepository.Entry entry:entries.subList(start,end)){Track track=app.library.get(entry.id());if(track==null)continue;LinearLayout row=Ui.column(activity);Ui.pad(row,8,12);row.setBackground(Ui.shape(activity,Ui.RAISED,12));TextView title=Ui.title(activity,track.title,15);title.setMaxLines(2);row.addView(title);Ui.space(row,5);
            TextView details=note(row,"");ProgressBar progress=null;
            if(entry.status()==DownloadRepository.Status.RUNNING){progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));row.addView(progress);}
            queueViews.put(entry.id(),new QueueViews(details,progress));updateQueueRow(entry);
            if(entry.status()==DownloadRepository.Status.RUNNING||entry.status()==DownloadRepository.Status.WAITING||entry.status()==DownloadRepository.Status.QUEUED)action(row,R.string.ui_cancel,()->app.downloads.cancel(entry.id()));else if(entry.status()==DownloadRepository.Status.FAILED||entry.status()==DownloadRepository.Status.CANCELLED)action(row,R.string.storage_retry,()->app.downloads.enqueue(List.of(entry.id())));
            queue.addView(row);Ui.space(queue,8);
        }
    }
    private void updateQueueRow(DownloadRepository.Entry entry){
        QueueViews views=queueViews.get(entry.id());if(views==null)return;String details=status(entry);
        if(entry.status()==DownloadRepository.Status.RUNNING){details+=" · "+size(entry.bytes());if(entry.total()>0)details+=" / "+size(entry.total())+" · "+(int)Math.min(100,100.0*entry.bytes()/entry.total())+"%";}
        views.details().setText(details);if(views.progress()!=null){views.progress().setIndeterminate(entry.total()<=0);views.progress().setProgress(entry.total()>0?(int)Math.min(100,100.0*entry.bytes()/entry.total()):0);}
    }

}
