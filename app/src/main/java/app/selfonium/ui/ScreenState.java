package app.selfonium.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import app.selfonium.R;

/** Save viewport state with platform view IDs; never store an Activity or replay an action. */
final class ScreenState {
    static void save(Bundle screens,String key,Dialog dialog){
        if(dialog==null||!dialog.isShowing())return;
        identify(dialog.getWindow().getDecorView());screens.putBundle(key,dialog.onSaveInstanceState());
    }
    static void restore(Dialog dialog,Bundle state){
        if(dialog==null||state==null)return;
        identify(dialog.getWindow().getDecorView());dialog.onRestoreInstanceState(state);
    }
    private static void identify(View view){
        if(view.getId()==View.NO_ID){
            if(view instanceof ScrollView)view.setId(R.id.screen_scroll);
            else if(view instanceof HorizontalScrollView)view.setId(R.id.screen_horizontal_scroll);
            else if(view instanceof AbsListView)view.setId(R.id.screen_list);
        }
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)identify(group.getChildAt(i));
    }
}
