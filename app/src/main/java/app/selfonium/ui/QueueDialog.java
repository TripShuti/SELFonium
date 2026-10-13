package app.selfonium.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import app.selfonium.SelfoniumApp;
import app.selfonium.core.Track;

/** The adapter follows the live Media3 timeline, including removals and moves. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class QueueDialog {
    static Dialog show(Activity activity,MediaController player){
        Dialog dialog=Ui.screen(activity);SelfoniumApp app=(SelfoniumApp)activity.getApplication();
        LinearLayout page=Ui.column(activity);Ui.insets(page);Ui.pad(page,0,0);
        LinearLayout top=Ui.row(activity);top.addView(new IconButton(activity,"back","Закрити чергу",dialog::dismiss));
        LinearLayout labels=Ui.column(activity);labels.addView(Ui.title(activity,"Черга відтворення",20));Ui.space(labels,4);TextView count=Ui.text(activity,"",12,Ui.MUTED);labels.addView(count);top.addView(labels,new LinearLayout.LayoutParams(0,-2,1));page.addView(top);Ui.space(page,12);
        FrameLayout body=new FrameLayout(activity);Ui.pad(body,16,0);page.addView(body,new LinearLayout.LayoutParams(-1,0,1));ListView list=new ListView(activity);list.setDivider(null);list.setItemsCanFocus(true);body.addView(list);
        TextView empty=Ui.text(activity,"Черга порожня\nОберіть композицію у бібліотеці.",15,Ui.MUTED);empty.setGravity(Gravity.CENTER);body.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return player.getMediaItemCount();}public MediaItem getItem(int at){return player.getMediaItemAt(at);}public long getItemId(int at){return at;}
            public View getView(int at,View old,ViewGroup parent){
                LinearLayout row;if(old instanceof LinearLayout)row=(LinearLayout)old;else{
                    row=Ui.row(activity);Ui.pad(row,0,6);row.setMinimumHeight(Ui.dp(activity,64));
                    row.addView(new CoverView(activity),new LinearLayout.LayoutParams(Ui.dp(activity,44),Ui.dp(activity,44)));
                    LinearLayout text=Ui.column(activity);Ui.pad(text,12,0);TextView title=Ui.title(activity,"",14),artist=Ui.text(activity,"",12,Ui.MUTED);for(TextView label:new TextView[]{title,artist}){label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);}text.addView(title);Ui.space(text,4);text.addView(artist);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                    row.addView(new IconButton(activity,"more","Порядок у черзі",()->{}),new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)));
                    row.addView(new IconButton(activity,"close","Видалити з черги",()->{}),new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)));
                }
                MediaItem item=getItem(at);Track track=app.library.get(item.mediaId);((CoverView)row.getChildAt(0)).bind(track);LinearLayout text=(LinearLayout)row.getChildAt(1);
                boolean current=at==player.getCurrentMediaItemIndex();TextView title=(TextView)text.getChildAt(0);title.setText(Ui.name(item.mediaMetadata.title));title.setTextColor(current?Ui.ACCENT:Ui.TEXT);((TextView)text.getChildAt(2)).setText((current?"Зараз · ":"")+ (item.mediaMetadata.artist==null?"":item.mediaMetadata.artist));
                row.setBackground(Ui.ripple(activity,current?Ui.SURFACE:Ui.BG,10));row.setContentDescription(item.mediaMetadata.title+", "+item.mediaMetadata.artist+(current?", поточна композиція":""));
                row.setOnClickListener(v->{if(at<player.getMediaItemCount()){player.seekTo(at,0);player.prepare();player.play();dialog.dismiss();}});
                IconButton order=(IconButton)row.getChildAt(2);order.setContentDescription("Змінити порядок: "+item.mediaMetadata.title);order.setOnClickListener(v->{
                    new AlertDialog.Builder(activity).setTitle("Порядок у черзі").setItems(new String[]{"Перемістити вище","Перемістити нижче"},(d,choice)->{int target=at+(choice==0?-1:1);if(at<player.getMediaItemCount()&&target>=0&&target<player.getMediaItemCount())player.moveMediaItem(at,target);}).show();
                });
                IconButton remove=(IconButton)row.getChildAt(3);remove.setContentDescription("Видалити з черги: "+item.mediaMetadata.title);remove.setOnClickListener(v->{if(at<player.getMediaItemCount())player.removeMediaItem(at);});return row;
            }
        };
        Runnable refresh=()->{count.setText(Ui.tracks(player.getMediaItemCount()));adapter.notifyDataSetChanged();};
        Player.Listener listener=new Player.Listener(){@Override public void onEvents(Player p,Player.Events events){if(events.contains(Player.EVENT_TIMELINE_CHANGED)||events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))refresh.run();}};
        list.setAdapter(adapter);player.addListener(listener);dialog.setOnDismissListener(d->player.removeListener(listener));dialog.setContentView(page);dialog.show();dialog.getWindow().setLayout(-1,-1);refresh.run();if(player.getCurrentMediaItemIndex()>=0)list.setSelection(player.getCurrentMediaItemIndex());return dialog;
    }
}
