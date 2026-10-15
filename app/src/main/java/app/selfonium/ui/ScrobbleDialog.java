package app.selfonium.ui;

import android.app.*;
import android.view.*;
import android.widget.*;
import app.selfonium.R;
import app.selfonium.data.ScrobbleRepository;

final class ScrobbleDialog {
    static Dialog show(Activity activity,ScrobbleRepository repository){
        Dialog dialog=Ui.screen(activity);ScrollView scroll=new ScrollView(activity);Ui.insets(scroll);LinearLayout page=Ui.column(activity);Ui.pad(page,16,8);scroll.addView(page);
        LinearLayout top=Ui.row(activity);top.addView(new IconButton(activity,"back",activity.getString(R.string.ui_back),dialog::dismiss));top.addView(Ui.title(activity,activity.getString(R.string.scrobble_title),20));page.addView(top);Ui.space(page,16);
        LinearLayout card=Ui.column(activity);Ui.pad(card,16,16);card.setBackground(Ui.shape(activity,Ui.SURFACE,16));page.addView(card);
        Switch enabled=new Switch(activity);enabled.setText(activity.getString(R.string.scrobble_enabled));enabled.setTextColor(Ui.TEXT);enabled.setMinHeight(Ui.dp(activity,56));Ui.tint(enabled);enabled.setChecked(repository.enabled());enabled.setOnCheckedChangeListener((v,on)->repository.enabled(on));card.addView(enabled);
        for(int id:new int[]{R.string.scrobble_hint,R.string.scrobble_policy,R.string.scrobble_limits}){Ui.space(card,16);TextView note=Ui.text(activity,activity.getString(id),14,Ui.MUTED);note.setLineSpacing(Ui.dp(activity,3),1);card.addView(note);}
        Ui.space(card,20);TextView pending=Ui.title(activity,"",16);pending.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(pending);Ui.space(card,16);
        TextView retry=Ui.button(activity,activity.getString(R.string.scrobble_retry),false,repository::retryNow);card.addView(retry);Ui.space(card,8);
        card.addView(Ui.button(activity,activity.getString(R.string.scrobble_clear),false,()->new AlertDialog.Builder(activity).setTitle(R.string.scrobble_clear).setMessage(R.string.scrobble_clear_confirm).setNegativeButton(R.string.ui_cancel,null).setPositiveButton(R.string.ui_delete_86,(d,w)->repository.clear(count->{})).show()));Ui.space(card,16);card.addView(Ui.text(activity,activity.getString(R.string.scrobble_note),13,Ui.MUTED));
        Runnable changed=()->{pending.setText(activity.getString(R.string.scrobble_pending,repository.pendingCount));retry.setEnabled(repository.enabled()&&repository.pendingCount>0);retry.setAlpha(retry.isEnabled()?1:.5f);};
        dialog.setContentView(scroll);dialog.setOnDismissListener(d->repository.unlisten(changed));dialog.show();dialog.getWindow().setLayout(-1,-1);repository.listen(changed);changed.run();return dialog;
    }
}
